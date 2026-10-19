package com.fluilet.app.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.focusmode.FocusEndResult
import com.fluilet.app.core.focusmode.FocusSessionController
import com.fluilet.app.core.focusmode.FocusSessionSummary
import com.fluilet.app.core.tts.TtsManager
import com.fluilet.app.core.tts.TtsState
import com.fluilet.app.core.tts.TtsStatus
import com.fluilet.app.di.ApplicationScope
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.PinVerification
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.StartReadingSessionUseCase
import com.fluilet.app.domain.usecase.UpdateReadingProgressUseCase
import com.fluilet.app.domain.usecase.UpdateReadingSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val reading: ActiveReading? = null,
    val settings: ReadingSettings = ReadingSettings(),
    val tts: TtsState = TtsState(),
    val elapsedSeconds: Long = 0,
    val lockActive: Boolean = false,
    val completed: Boolean = false,
    val showSettings: Boolean = false,
    val showGate: Boolean = false
) {
    val chunkCount: Int get() = reading?.adaptation?.chunks?.size ?: 0
    val chunkIndex: Int get() = tts.chunkIndex.coerceIn(0, (chunkCount - 1).coerceAtLeast(0))
    val currentText: String get() = reading?.adaptation?.chunks?.getOrNull(chunkIndex)?.text.orEmpty()
    val isLastChunk: Boolean get() = chunkCount > 0 && chunkIndex == chunkCount - 1
    val progressFraction: Float get() = if (chunkCount == 0) 0f else if (completed) 1f else chunkIndex.toFloat() / chunkCount
}

sealed interface ReaderEvent {
    data object Exited : ReaderEvent
}

private data class Local(
    val settings: ReadingSettings,
    val completed: Boolean = false,
    val showSettings: Boolean = false,
    val showGate: Boolean = false,
    val maxChunk: Int = 0,
    /** El avance reportado es monótono: terminar una vez cuenta aunque luego se relea ("Leer de nuevo"). */
    val everCompleted: Boolean = false
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val holder: ActiveReadingHolder,
    private val tts: TtsManager,
    private val focus: FocusSessionController,
    private val updateSettings: UpdateReadingSettingsUseCase,
    private val startSession: StartReadingSessionUseCase,
    private val updateProgress: UpdateReadingProgressUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val local = MutableStateFlow(Local(settings = holder.current.value?.settings ?: ReadingSettings()))
    private var started = false

    /** Id de la sesión en el servidor (`null` si no hay seguimiento). Vive en el alcance de la app. */
    private var readingId: Deferred<String?>? = null
    private var progressJob: Job? = null

    private val _events = Channel<ReaderEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<ReaderUiState> = combine(holder.current, tts.state, focus.state, local) { reading, ttsState, focusState, l ->
        ReaderUiState(
            reading = reading,
            settings = l.settings,
            tts = ttsState,
            elapsedSeconds = focusState.elapsedSeconds,
            lockActive = focusState.isActive && focusState.lockRequested,
            completed = l.completed,
            showSettings = l.showSettings,
            showGate = l.showGate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    init {
        // Sigue el avance por bloques (también al leer con voz) y detecta el fin de la lectura.
        viewModelScope.launch {
            tts.state.collect { s ->
                local.update { l ->
                    l.copy(
                        maxChunk = maxOf(l.maxChunk, s.chunkIndex),
                        completed = l.completed || s.finished,
                        everCompleted = l.everCompleted || s.finished
                    )
                }
            }
        }
    }

    /** Idempotente: prepara voz, bloques y la sesión de Modo Enfoque. */
    fun start() {
        val reading = holder.current.value ?: return
        if (started) return
        started = true
        tts.initialize()
        tts.load(reading.adaptation.chunks.map { it.text })
        tts.setSpeechRate(local.value.settings.speechRate)
        // Con perfil de niño: Screen Pinning + PIN del tutor para salir. Adulto independiente: sin bloqueo.
        focus.begin(childId = reading.childId, lockTask = reading.childId != null)

        // Abre la sesión en el servidor; el tiempo reportado no puede superar el transcurrido desde aquí.
        readingId = appScope.async { startSession(reading.childId, reading.adaptation).getOrNull() }
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                reportProgress(focus.state.value.elapsedSeconds, final = false)
            }
        }
    }

    fun togglePlayPause() {
        when (tts.state.value.status) {
            TtsStatus.Playing -> tts.pause()
            TtsStatus.Idle, TtsStatus.Paused -> tts.play()
            else -> Unit
        }
    }

    fun stopSpeech() = tts.stop()
    fun next() = seek(uiState.value.chunkIndex + 1)
    fun previous() = seek(uiState.value.chunkIndex - 1)
    fun restart() {
        local.update { it.copy(completed = false, maxChunk = 0) }
        tts.seekToChunk(0)
    }

    private fun seek(index: Int) {
        if (index in 0 until uiState.value.chunkCount) tts.seekToChunk(index)
    }

    fun finishReading() {
        tts.stop()
        local.update { it.copy(completed = true, everCompleted = true) }
    }

    // --- Preferencias de lectura -----------------------------------------------------------

    fun onFontSize(v: Float) = mutate { updateSettings.fontSize(it, v) }
    fun onLineSpacing(v: Float) = mutate { updateSettings.lineSpacing(it, v) }
    fun onLetterSpacing(v: Float) = mutate { updateSettings.letterSpacing(it, v) }
    fun onPalette(p: AccessiblePalette) = mutate { updateSettings.palette(it, p) }
    fun onSpeechRate(v: Float) {
        mutate { updateSettings.speechRate(it, v) }
        tts.setSpeechRate(local.value.settings.speechRate)
    }

    private fun mutate(change: (ReadingSettings) -> ReadingSettings) =
        local.update { it.copy(settings = change(it.settings)) }

    fun openSettings() = local.update { it.copy(showSettings = true) }
    fun closeSettings() = local.update { it.copy(showSettings = false) }
    fun consumeTtsError() = tts.consumeError()

    // --- Salida (Muro Parental) --------------------------------------------------------------

    fun requestExit() {
        if (uiState.value.lockActive) local.update { it.copy(showGate = true) } else exit(null)
    }

    fun dismissGate() = local.update { it.copy(showGate = false) }

    fun onGateVerified(proof: PinVerification?) {
        local.update { it.copy(showGate = false) }
        exit(proof)
    }

    private fun exit(proof: PinVerification?) {
        when (val result = focus.end(proof)) {
            is FocusEndResult.Ended -> {
                report(result.summary)
                close()
            }
            FocusEndResult.PinRequired -> local.update { it.copy(showGate = true) }
            FocusEndResult.NotActive -> close()
        }
    }

    private fun close() {
        tts.release()
        holder.clear()
        _events.trySend(ReaderEvent.Exited)
    }

    private fun currentPercent(): Int {
        val l = local.value
        val count = (holder.current.value?.adaptation?.chunks?.size ?: 0).coerceAtLeast(1)
        return if (l.everCompleted) 100 else (l.maxChunk * 100 / count).coerceIn(0, 99)
    }

    /** Solo se envían números (cero retención): tiempo y avance. Las métricas las calcula el servidor. */
    private fun reportProgress(elapsedSeconds: Long, final: Boolean) {
        val percent = currentPercent()
        val id = readingId ?: return
        appScope.launch {
            val readingId = id.await() ?: return@launch
            updateProgress(readingId, ReadingProgress(elapsedSeconds, percent, completed = final && percent == 100))
        }
    }

    private fun report(summary: FocusSessionSummary) {
        progressJob?.cancel()
        reportProgress(summary.elapsedSeconds, final = true)
    }

    override fun onCleared() {
        // La Activity se destruye sin pasar por el Muro (cierre de app): no se pierde la telemetría.
        focus.abortForTeardown()?.let(::report)
        tts.release()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 30_000L
    }
}
