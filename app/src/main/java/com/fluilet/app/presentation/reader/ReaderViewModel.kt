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
import com.fluilet.app.domain.gamification.Gamification
import com.fluilet.app.domain.gamification.SessionContribution
import com.fluilet.app.domain.text.VocabularyEntry
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.gamification.SessionLoot
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.PinVerification
import com.fluilet.app.domain.quiz.ComprehensionQuiz
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.text.SentenceSpans
import com.fluilet.app.domain.usecase.GetEducationalProgressUseCase
import com.fluilet.app.domain.usecase.FetchAdaptationUseCase
import com.fluilet.app.domain.usecase.GetModelAuditUseCase
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.ModelAudit
import com.fluilet.app.domain.text.LevelComparison
import com.fluilet.app.domain.text.LevelComparisons
import com.fluilet.app.presentation.fluibot.FluiBotMood
import com.fluilet.app.domain.model.TechnicalSheet
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
import com.fluilet.app.domain.text.ShortLesson

/** Pestañas del Laboratorio de Recompensas (siempre dentro del Modo Enfoque). */
enum class LabTab(val emoji: String, val label: String) {
    PREMIO("🎁", "Tu Premio"),
    FLUIBOT("🤖", "Personalizar a FluiBot"),
    ALBUM("🃏", "Mi Álbum");

    companion object {
        val DEFAULT = PREMIO
    }
}

data class ReaderUiState(
    val reading: ActiveReading? = null,
    val settings: ReadingSettings = ReadingSettings(),
    val tts: TtsState = TtsState(),
    val elapsedSeconds: Long = 0,
    val lockActive: Boolean = false,
    val completed: Boolean = false,
    val showSettings: Boolean = false,
    val showGate: Boolean = false,
    /** Mini-Reto de Comprensión (2 preguntas) si la lección lo trae. */
    val quiz: ComprehensionQuiz? = null,
    val quizDone: Boolean = false,
    /** Guía de renglón: resalta la oración activa y atenúa el resto. */
    val guideEnabled: Boolean = false,
    val guideRange: IntRange? = null,
    /** Avance antes de esta sesión (base del botín y de las gemas del niño). */
    val baseline: EducationalProgress = EducationalProgress.empty(),
    val chestOpened: Boolean = false,
    /** Vista "Adaptado vs. Original" activa. */
    val showOriginal: Boolean = false,
    /** Palabra del Glosario Mágico seleccionada (con su definición y ejemplo). */
    val selectedWord: VocabularyEntry? = null,
    /** Palabras clave de la lectura (servidor o glosario local). */
    val vocabulary: List<VocabularyEntry> = emptyList(),
    val labTab: LabTab = LabTab.DEFAULT,
    /** Original recuperado con `GET /adaptations/{id}` para lecturas anteriores que no lo traían. */
    val fetchedOriginal: String? = null,
    val originalLoading: Boolean = false,
    /** Las 5 variantes que ofrece el comparador (servidor, detalle recuperado o motor local sobre el original). */
    val levelPreviews: List<LevelPreview> = emptyList(),
    /** Nivel elegido en el selector en vivo del comparador. */
    val comparisonLevel: Int? = null,
    /** Ficha técnica, banda y transformaciones léxicas del nivel elegido. */
    val comparison: LevelComparison? = null,
    /** Auditoría del modelo reentrenado para el sello del comparador (`model-info` o valores verificados). */
    val modelAudit: ModelAudit? = null
) {
    /** "¿Sabías que...?": dato del servidor o local según tema/categoría; se muestra al final del texto. */
    val funFact: String? get() = reading?.adaptation?.effectiveFunFact

    /** Texto original (solo en RAM): el que trae la lección (`original_text`) o el recuperado del servidor al pedir la comparación. */
    val originalText: String? get() = reading?.adaptation?.originalText?.takeIf { it.isNotBlank() } ?: fetchedOriginal

    /** Ficha Técnica del Motor NLP de esta lección (métricas reales Szigriszt-Pazos). */
    val technicalSheet: TechnicalSheet? get() = reading?.adaptation?.let(TechnicalSheet::of)

    /** Aporte de esta sesión a las misiones del día. */
    val session: SessionContribution
        get() = SessionContribution(completed, quiz?.correct ?: 0, elapsedSeconds)

    /** El reto aparece al terminar la lectura, antes del Laboratorio de Recompensas. */
    val showQuiz: Boolean get() = completed && quiz != null && !quizDone

    /**
     * Laboratorio y Rincón de Recompensas: se muestra DENTRO del Modo Enfoque al terminar la lectura (y
     * el mini-reto, si lo hay). Salir sigue exigiendo el botón del tutor y su PIN.
     */
    val showLab: Boolean get() = completed && !showQuiz

    /**
     * Expresión de FluiBot en el lector: lee concentrado, piensa mientras algo carga o mientras se responde el
     * mini-reto, celebra el mini-reto superado y el cofre abierto, y saluda en el laboratorio.
     */
    val botMood: FluiBotMood
        get() = when {
            showQuiz -> if (quiz?.isComplete == true && quiz.correct > 0) FluiBotMood.CELEBRATING else FluiBotMood.THINKING
            showLab -> if (chestOpened) FluiBotMood.CELEBRATING else FluiBotMood.HAPPY
            else -> FluiBotMood.forReader(loading = originalLoading || tts.status == TtsStatus.Initializing, celebrating = false)
        }

    /** El mini-reto terminó con al menos un acierto: dispara la celebración con confeti. */
    val quizSucceeded: Boolean get() = quiz?.isComplete == true && quiz.correct > 0

    /** Botín de esta lectura: +50 💎 por completar, +25 💎 por respuesta correcta, XP y cambio de carta. */
    val loot: SessionLoot
        get() = Gamification.lootFor(completed, quiz?.correct ?: 0, reading?.adaptation?.category, baseline)

    val ageGroup get() = reading?.ageGroup
    val readerName: String get() = reading?.readerName.orEmpty()
    val ownerKey: String get() = reading?.ownerKey ?: "adult"
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
    val everCompleted: Boolean = false,
    val quiz: ComprehensionQuiz? = null,
    val quizDone: Boolean = false,
    val guideEnabled: Boolean = false,
    val guideSentence: Int = 0,
    val lastChunk: Int = 0,
    val baseline: EducationalProgress = EducationalProgress.empty(),
    val chestOpened: Boolean = false,
    val showOriginal: Boolean = false,
    val selectedWord: String? = null,
    val labTab: LabTab = LabTab.DEFAULT,
    val fetchedOriginal: String? = null,
    val originalLoading: Boolean = false,
    /** Adaptación completa recuperada del servidor (aporta sus `level_previews` y reemplazos). */
    val fetchedDetail: AdaptationResult? = null,
    val comparisonLevel: Int? = null,
    val modelAudit: ModelAudit? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val holder: ActiveReadingHolder,
    private val tts: TtsManager,
    private val focus: FocusSessionController,
    private val updateSettings: UpdateReadingSettingsUseCase,
    private val startSession: StartReadingSessionUseCase,
    private val updateProgress: UpdateReadingProgressUseCase,
    private val getProgress: GetEducationalProgressUseCase,
    private val fetchAdaptation: FetchAdaptationUseCase,
    private val getModelAudit: GetModelAuditUseCase,
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
        val words = vocabularyFor(reading?.adaptation)
        val compared = if (l.showOriginal) comparisonFor(reading?.adaptation, l) else null
        ReaderUiState(
            reading = reading,
            settings = l.settings,
            tts = ttsState,
            elapsedSeconds = focusState.elapsedSeconds,
            lockActive = focusState.isActive && focusState.lockRequested,
            completed = l.completed,
            showSettings = l.showSettings,
            showGate = l.showGate,
            quiz = l.quiz,
            quizDone = l.quizDone,
            guideEnabled = l.guideEnabled,
            guideRange = if (l.guideEnabled) activeSentence(reading, ttsState, l.guideSentence) else null,
            baseline = l.baseline,
            chestOpened = l.chestOpened,
            showOriginal = l.showOriginal,
            vocabulary = words,
            selectedWord = words.firstOrNull { it.word == l.selectedWord },
            labTab = l.labTab,
            fetchedOriginal = l.fetchedOriginal,
            originalLoading = l.originalLoading,
            levelPreviews = compared?.first.orEmpty(),
            comparisonLevel = compared?.second?.level ?: l.comparisonLevel,
            comparison = compared?.second,
            modelAudit = l.modelAudit
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
                        everCompleted = l.everCompleted || s.finished,
                        // La guía vuelve a la primera oración al cambiar de bloque.
                        guideSentence = if (s.chunkIndex != l.lastChunk) 0 else l.guideSentence,
                        lastChunk = s.chunkIndex
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
        local.update { l -> l.copy(quiz = reading.adaptation.questions.takeIf { it.isNotEmpty() }?.let(::ComprehensionQuiz)) }
        // Avance previo (para el botín y las gemas); si falla se parte de un resumen vacío.
        viewModelScope.launch {
            getProgress(reading.childId, DEFAULT_BASELINE_LEVEL).onSuccess { progress ->
                local.update { it.copy(baseline = progress) }
            }
        }
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

    // --- Mini-Reto de Comprensión -------------------------------------------------------------

    fun answerQuiz(optionIndex: Int) = local.update { l -> l.copy(quiz = l.quiz?.answer(optionIndex)) }
    fun nextQuestion() = local.update { l -> l.copy(quiz = l.quiz?.next()) }

    /**
     * Cierra el reto y pasa al Laboratorio de Recompensas, siempre DENTRO del Modo Enfoque. Responder el
     * mini-reto nunca abre el PIN ni desbloquea: solo el botón del tutor lo hace ([requestExit]).
     */
    fun finishQuiz() {
        local.update { it.copy(quizDone = true) }
    }

    // --- Original vs. Adaptado y Glosario Mágico -------------------------------------------------

    fun toggleOriginal() {
        val turningOn = !local.value.showOriginal
        local.update { it.copy(showOriginal = turningOn) }
        if (turningOn) {
            loadOriginalIfMissing()
            loadModelAudit()
        }
    }

    /** Selector en vivo de los 5 niveles: cambia el texto y la ficha sin salir de la lectura (ni del Modo Enfoque). */
    fun selectComparisonLevel(level: Int) {
        if (level !in 1..5) return
        local.update { it.copy(comparisonLevel = level) }
    }

    /** Sello del modelo: una sola consulta por lector (el repositorio además la guarda en memoria). */
    private fun loadModelAudit() {
        if (local.value.modelAudit != null) return
        viewModelScope.launch {
            val audit = getModelAudit()
            local.update { it.copy(modelAudit = audit) }
        }
    }

    /** Lecturas anteriores sin `original_text`: se pide al servidor la adaptación retenida (por su id o el del documento). */
    private fun loadOriginalIfMissing() {
        val adaptation = holder.current.value?.adaptation ?: return
        val l = local.value
        if (!adaptation.originalText.isNullOrBlank() || l.fetchedOriginal != null || l.originalLoading) return
        val id = adaptation.adaptationId ?: adaptation.documentId ?: return
        local.update { it.copy(originalLoading = true) }
        viewModelScope.launch {
            fetchAdaptation(id)
                .onSuccess { r ->
                    // En una lección corta, el original y los niveles recuperados se recortan igual que la lectura.
                    val detail = ShortLesson.alignDetail(r, adaptation)
                    local.update {
                        it.copy(fetchedOriginal = detail.originalText?.takeIf { t -> t.isNotBlank() }, fetchedDetail = detail, originalLoading = false)
                    }
                }
                .onFailure { local.update { it.copy(originalLoading = false) } }
        }
    }

    /** Un toque en una palabra clave muestra su definición; otro toque en la misma la cierra. */
    fun selectWord(word: String) = local.update { it.copy(selectedWord = if (it.selectedWord == word) null else word) }
    fun clearWord() = local.update { it.copy(selectedWord = null) }

    /** "🔊 Escuchar palabra": el motor de voz del dispositivo pronuncia solo esa palabra. */
    fun speakWord(word: String) = tts.speakWord(word)

    fun selectLabTab(tab: LabTab) = local.update { it.copy(labTab = tab) }

    /**
     * Variantes y comparativa del nivel elegido. El estado se recombina con cada palabra que lee la voz, así que se
     * memorizan: las variantes por lectura/detalle/original y la comparativa por nivel.
     */
    private var previewsMemo: PreviewsMemo? = null
    private var comparisonMemo: Pair<List<LevelPreview>, LevelComparison>? = null

    private class PreviewsMemo(val adaptation: AdaptationResult, val detail: AdaptationResult?, val original: String?, val previews: List<LevelPreview>)

    private fun comparisonFor(adaptation: AdaptationResult?, l: Local): Pair<List<LevelPreview>, LevelComparison?>? {
        adaptation ?: return null
        val original = adaptation.originalText?.takeIf { it.isNotBlank() } ?: l.fetchedOriginal
        val memo = previewsMemo?.takeIf { it.adaptation === adaptation && it.detail === l.fetchedDetail && it.original == original }
            ?: PreviewsMemo(adaptation, l.fetchedDetail, original, LevelComparisons.previews(adaptation, l.fetchedDetail, original)).also { previewsMemo = it }
        val previews = memo.previews
        val level = l.comparisonLevel?.takeIf { lvl -> previews.any { it.level == lvl } } ?: LevelComparisons.defaultLevel(adaptation, previews)
            ?: return previews to null
        comparisonMemo?.takeIf { it.first === previews && it.second.level == level }?.let { return previews to it.second }
        val base = if (adaptation.levelPreviews.isEmpty() && l.fetchedDetail?.levelPreviews?.isNotEmpty() == true) l.fetchedDetail else adaptation
        val comparison = LevelComparisons.compare(base, previews, level, original) ?: return previews to null
        comparisonMemo = previews to comparison
        return previews to comparison
    }

    /** Glosario de la lectura: se calcula una vez por lectura (no en cada recomposición). */
    private var vocabularyCache: Pair<AdaptationResult, List<VocabularyEntry>>? = null

    private fun vocabularyFor(adaptation: AdaptationResult?): List<VocabularyEntry> {
        adaptation ?: return emptyList()
        vocabularyCache?.takeIf { it.first === adaptation }?.let { return it.second }
        return adaptation.effectiveVocabulary.also { vocabularyCache = adaptation to it }
    }

    // --- Cofre y guía de renglón ----------------------------------------------------------------

    fun claimChest() = local.update { it.copy(chestOpened = true) }

    fun toggleGuide() = local.update { it.copy(guideEnabled = !it.guideEnabled, guideSentence = 0) }

    /** Avanza la guía a la siguiente oración; al terminar el bloque pasa al siguiente. */
    fun guideNext() {
        val state = uiState.value
        val spans = SentenceSpans.compute(state.currentText)
        val current = state.guideRange?.let { r -> spans.indexOf(r) }?.takeIf { it >= 0 } ?: local.value.guideSentence
        if (current + 1 <= spans.lastIndex) local.update { it.copy(guideSentence = current + 1) } else next()
    }

    fun guidePrevious() {
        val state = uiState.value
        val spans = SentenceSpans.compute(state.currentText)
        val current = state.guideRange?.let { r -> spans.indexOf(r) }?.takeIf { it >= 0 } ?: local.value.guideSentence
        if (current > 0) local.update { it.copy(guideSentence = current - 1) } else previous()
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
        val adaptation = holder.current.value?.adaptation
        val quiz = local.value.quiz?.result?.takeIf { it.answered > 0 }
        // Velocidad de voz usada (1.0 = normal), redondeada a 2 decimales.
        val speed = Math.round(local.value.settings.speechRate * 100) / 100.0
        appScope.launch {
            val readingId = id.await() ?: return@launch
            updateProgress(
                readingId,
                ReadingProgress(
                    readingTimeSeconds = elapsedSeconds,
                    completionPercent = percent,
                    completed = final && percent == 100,
                    category = adaptation?.category,
                    simplificationLevel = adaptation?.simplificationLevel,
                    comprehension = quiz,
                    ttsSpeed = speed
                )
            )
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

    /** Oración activa del bloque: la que se está leyendo en voz alta, o la que marca la guía manual. */
    private fun activeSentence(reading: ActiveReading?, ttsState: TtsState, manual: Int): IntRange? {
        val text = reading?.adaptation?.chunks?.getOrNull(ttsState.chunkIndex)?.text ?: return null
        val spans = SentenceSpans.compute(text)
        if (spans.isEmpty()) return null
        val spoken = ttsState.highlight?.first
        val index = if (spoken != null) SentenceSpans.indexAt(spans, spoken) else manual.coerceIn(0, spans.lastIndex)
        return spans[index]
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 30_000L
        const val DEFAULT_BASELINE_LEVEL = 2
    }
}
