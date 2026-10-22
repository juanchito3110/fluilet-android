package com.fluilet.app.core.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class TtsStatus { Uninitialized, Initializing, Idle, Playing, Paused, Unavailable }

data class TtsState(
    val status: TtsStatus = TtsStatus.Uninitialized,
    val chunkIndex: Int = 0,
    val chunkCount: Int = 0,
    /** Palabra pronunciada ahora, relativa al texto del bloque actual (para el AnnotatedString). */
    val highlight: IntRange? = null,
    /** Se leyeron todos los bloques con voz. */
    val finished: Boolean = false,
    val speechRate: Float = 1.0f,
    val errorMessage: String? = null,
    val missingVoiceData: Boolean = false
)

/**
 * Lectura guiada por voz con resaltado sincronizado palabra por palabra.
 *
 * `TextToSpeech` no tiene pausa nativa: al pausar se detiene el motor y se recuerda el inicio de la
 * última palabra pronunciada; al reanudar se vuelve a hablar desde esa palabra. Cada locución lleva
 * un id propio y los eventos de locuciones antiguas se ignoran (un `stop()` o un cambio de velocidad
 * no deben avanzar ni cerrar la lectura por error).
 */
@Singleton
class TtsManager internal constructor(
    private val engineFactory: () -> SpeechEngine
) : SpeechEngineListener {

    @Inject
    constructor(@ApplicationContext context: Context) : this({ AndroidSpeechEngine(context) })

    private val lock = Any()
    private var engine: SpeechEngine? = null
    private var chunks: List<String> = emptyList()

    private var currentUtteranceId: String? = null
    private var utteranceBaseOffset = 0   // desplazamiento del texto hablado dentro del bloque
    private var lastWordStart = 0         // último inicio de palabra (absoluto en el bloque)
    private var utteranceCounter = 0L

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    fun initialize() {
        synchronized(lock) {
            if (engine != null) return
            val created = engineFactory()
            created.listener = this
            engine = created
            _state.update { it.copy(status = TtsStatus.Initializing, errorMessage = null) }
            created.initialize { result ->
                synchronized(lock) {
                    if (engine !== created) return@initialize
                    when (result) {
                        SpeechEngineInit.Ready ->
                            _state.update { it.copy(status = TtsStatus.Idle, errorMessage = null, missingVoiceData = false) }
                        is SpeechEngineInit.Unavailable ->
                            _state.update {
                                it.copy(
                                    status = TtsStatus.Unavailable,
                                    errorMessage = result.reason,
                                    missingVoiceData = result.missingVoiceData
                                )
                            }
                    }
                }
            }
        }
    }

    /** Carga los bloques (solo en memoria) y deja la lectura en [startIndex]. */
    fun load(newChunks: List<String>, startIndex: Int = 0) {
        synchronized(lock) {
            haltEngine()
            chunks = newChunks
            val index = startIndex.coerceIn(0, (newChunks.size - 1).coerceAtLeast(0))
            lastWordStart = 0
            utteranceBaseOffset = 0
            _state.update { it.copy(chunkIndex = index, chunkCount = newChunks.size, highlight = null, finished = false) }
        }
    }

    fun play() = synchronized(lock) {
        val s = _state.value
        if (s.status != TtsStatus.Idle && s.status != TtsStatus.Paused) return@synchronized
        if (chunks.isEmpty()) return@synchronized
        val resumeFrom = if (s.status == TtsStatus.Paused) lastWordStart else 0
        val index = if (s.finished) 0 else s.chunkIndex
        speakChunk(index, resumeFrom)
    }

    fun pause() = synchronized(lock) {
        if (_state.value.status != TtsStatus.Playing) return@synchronized
        haltEngine()
        _state.update { it.copy(status = TtsStatus.Paused) }
    }

    fun resume() = play()

    /** Detiene la voz y quita el resaltado; el bloque actual se conserva. */
    fun stop() = synchronized(lock) {
        if (_state.value.status != TtsStatus.Playing && _state.value.status != TtsStatus.Paused) return@synchronized
        haltEngine()
        lastWordStart = 0
        _state.update { it.copy(status = TtsStatus.Idle, highlight = null) }
    }

    /** Cambia de bloque; si estaba hablando, continúa hablando en el nuevo bloque. */
    fun seekToChunk(index: Int) = synchronized(lock) {
        if (chunks.isEmpty()) return@synchronized
        val target = index.coerceIn(0, chunks.lastIndex)
        val wasPlaying = _state.value.status == TtsStatus.Playing
        haltEngine()
        lastWordStart = 0
        _state.update {
            it.copy(
                chunkIndex = target,
                highlight = null,
                finished = false,
                status = if (it.status == TtsStatus.Playing || it.status == TtsStatus.Paused) TtsStatus.Idle else it.status
            )
        }
        if (wasPlaying) speakChunk(target, 0)
    }

    /** `speechRate` adaptado a niños (0.5x–1.5x). Si está hablando, se reaplica desde la palabra actual. */
    fun setSpeechRate(rate: Float) = synchronized(lock) {
        val clamped = rate.coerceIn(MIN_RATE, MAX_RATE)
        _state.update { it.copy(speechRate = clamped) }
        if (_state.value.status == TtsStatus.Playing) {
            val index = _state.value.chunkIndex
            haltEngine()
            speakChunk(index, lastWordStart)
        }
    }

    fun release() = synchronized(lock) {
        haltEngine()
        engine?.shutdown()
        engine = null
        chunks = emptyList()
        _state.value = TtsState(speechRate = _state.value.speechRate)
    }

    // --- Eventos del motor ---------------------------------------------------------------

    override fun onStart(utteranceId: String) = Unit

    override fun onRangeStart(utteranceId: String, start: Int, end: Int) {
        synchronized(lock) {
            if (utteranceId != currentUtteranceId) return
            val absStart = utteranceBaseOffset + start
            val absEnd = utteranceBaseOffset + end
            val textLength = chunks.getOrNull(_state.value.chunkIndex)?.length ?: return
            if (absStart !in 0 until textLength) return
            lastWordStart = absStart
            _state.update { it.copy(highlight = absStart until absEnd.coerceAtMost(textLength)) }
        }
    }

    override fun onDone(utteranceId: String) {
        synchronized(lock) {
            if (utteranceId != currentUtteranceId) return
            currentUtteranceId = null
            lastWordStart = 0
            val next = _state.value.chunkIndex + 1
            if (next < chunks.size) {
                speakChunk(next, 0)
            } else {
                _state.update { it.copy(status = TtsStatus.Idle, highlight = null, finished = true) }
            }
        }
    }

    override fun onError(utteranceId: String) {
        synchronized(lock) {
            if (utteranceId != currentUtteranceId) return
            currentUtteranceId = null
            _state.update {
                it.copy(
                    status = TtsStatus.Idle,
                    highlight = null,
                    errorMessage = "No se pudo reproducir la voz. Inténtalo de nuevo."
                )
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    // --- Internos ------------------------------------------------------------------------

    private fun speakChunk(index: Int, fromOffset: Int) {
        val text = chunks.getOrNull(index) ?: return
        val offset = fromOffset.coerceIn(0, text.length)
        val spoken = text.substring(offset)
        if (spoken.isBlank()) {
            _state.update { it.copy(chunkIndex = index) }
            onDoneNoUtterance(index)
            return
        }
        val id = "utt-${++utteranceCounter}"
        currentUtteranceId = id
        utteranceBaseOffset = offset
        lastWordStart = offset
        _state.update { it.copy(status = TtsStatus.Playing, chunkIndex = index, highlight = null, finished = false) }
        engine?.speak(spoken, id, _state.value.speechRate)
    }

    private fun onDoneNoUtterance(index: Int) {
        val next = index + 1
        if (next < chunks.size) speakChunk(next, 0)
        else _state.update { it.copy(status = TtsStatus.Idle, highlight = null, finished = true) }
    }

    /** Detiene el motor e invalida la locución en curso (sus eventos tardíos se ignoran). */
    private fun haltEngine() {
        currentUtteranceId = null
        engine?.stop()
    }

    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 1.5f
    }
}
