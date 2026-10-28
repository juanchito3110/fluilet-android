package com.fluilet.app.presentation

import com.fluilet.app.core.focusmode.FocusServiceLauncher
import com.fluilet.app.core.focusmode.FocusSessionController
import com.fluilet.app.core.tts.TtsManager
import com.fluilet.app.domain.gamification.CardChange
import com.fluilet.app.domain.gamification.Gamification
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.TextChunk
import com.fluilet.app.domain.model.ComprehensionResult
import com.fluilet.app.domain.model.PinVerification
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.GetEducationalProgressUseCase
import com.fluilet.app.domain.usecase.FetchAdaptationUseCase
import com.fluilet.app.domain.usecase.GetModelAuditUseCase
import com.fluilet.app.domain.usecase.StartReadingSessionUseCase
import com.fluilet.app.domain.usecase.UpdateReadingProgressUseCase
import com.fluilet.app.domain.usecase.UpdateReadingSettingsUseCase
import com.fluilet.app.presentation.fluibot.FluiBotMood
import com.fluilet.app.presentation.reader.ReaderEvent
import com.fluilet.app.presentation.reader.ReaderUiState
import com.fluilet.app.presentation.reader.ReaderViewModel
import com.fluilet.app.testutil.FakeReadingSessionRepository
import com.fluilet.app.testutil.FakeSpeechEngine
import com.fluilet.app.testutil.sampleAdaptation
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Estado de pantalla del lector y su telemetría para los dos modos: adulto independiente (sin
 * bloqueo, sin PIN, sin perfil de niño) y niño (Modo Enfoque + Muro Parental).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private class FakeLauncher : FocusServiceLauncher {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val launcher = FakeLauncher()
    private var mono = 0L
    private val focus = FocusSessionController(launcher, { mono }, { Instant.parse("2026-10-19T10:00:00Z") })
    private val repo = FakeReadingSessionRepository()
    private val adaptations = com.fluilet.app.testutil.FakeAdaptationRepository()
    private val modelInfo = com.fluilet.app.testutil.FakeModelInfoRepository()
    private val holder = ActiveReadingHolder()
    private val engine = FakeSpeechEngine()
    private val tts = TtsManager { engine }
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val created = mutableListOf<ReaderViewModel>()

    /** Avance esperado: además de tiempo/porcentaje, la lección de prueba aporta categoría, nivel y velocidad normal. */
    private fun educational(seconds: Long, percent: Int, completed: Boolean) = ReadingProgress(
        seconds, percent, completed, category = "ciencia_espacio", simplificationLevel = 2, ttsSpeed = 1.0
    )

    private fun viewModel(
        childId: String?, chunks: Int = 3, withQuiz: Boolean = false, category: String? = "ciencia_espacio",
        adaptation: AdaptationResult = sampleAdaptation(chunks, withQuiz = withQuiz, category = category)
    ): ReaderViewModel {
        holder.set(ActiveReading(childId, "Lector", ReadingSettings(), adaptation))
        return ReaderViewModel(
            holder, tts, focus, UpdateReadingSettingsUseCase(),
            StartReadingSessionUseCase(repo), UpdateReadingProgressUseCase(repo), GetEducationalProgressUseCase(repo), FetchAdaptationUseCase(adaptations), GetModelAuditUseCase(modelInfo), appScope
        ).also(created::add)
    }

    /**
     * El ViewModel mantiene un bucle periódico de avance (cada 30 s) y un colector del TTS: al terminar
     * cada prueba se cancela su scope (lo que hace Android al destruirlo) para que `runTest` no avance
     * el tiempo virtual indefinidamente.
     */
    private fun readerTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
        }
    }

    private data class Probe(val state: () -> ReaderUiState, val events: List<ReaderEvent>)

    private fun TestScope.observe(vm: ReaderViewModel): Probe {
        val events = mutableListOf<ReaderEvent>()
        backgroundScope.launch(dispatcher) { vm.uiState.collect { } }
        backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
        return Probe({ vm.uiState.value }, events)
    }

    // --- Adulto independiente --------------------------------------------------------------

    @Test fun adult_readsWithoutFocusLockAndOpensAnIndependentSession() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)

        vm.start()

        assertTrue(focus.state.value.isActive)
        assertFalse("el adulto no se ancla", focus.state.value.lockRequested)
        assertFalse(probe.state().lockActive)
        assertEquals(1, launcher.starts)
        assertEquals(listOf<String?>(null), repo.starts.map { it.first }) // POST /readings (sin child_id)
        assertEquals(3, probe.state().chunkCount)
        assertEquals("Bloque número 0 del cuento.", probe.state().currentText)
    }

    @Test fun adult_exitsDirectlyWithoutParentalGateAndReportsFinalProgress() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)
        vm.start()
        mono += 125_000
        focus.tick()

        vm.finishReading()
        vm.requestExit()

        assertFalse("sin Muro Parental", probe.state().showGate)
        assertEquals(listOf<ReaderEvent>(ReaderEvent.Exited), probe.events)
        assertFalse(focus.state.value.isActive)
        assertEquals(1, launcher.stops)
        assertNull("lectura borrada de RAM", holder.current.value)
        assertEquals("reading-1" to educational(125, 100, completed = true), repo.progress.last())
    }

    @Test fun adult_leavingEarlyReportsPartialProgressNotCompleted() = readerTest {
        val vm = viewModel(childId = null)
        observe(vm)
        vm.start()
        mono += 30_000

        vm.next() // bloque 2 de 3
        vm.requestExit()

        assertEquals(educational(30, 33, completed = false), repo.progress.last().second)
    }

    @Test fun adult_progressNeverGoesBackwardsAfterReadingAgain() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)
        vm.start()
        mono += 10_000

        vm.finishReading()
        vm.restart() // "Leer de nuevo": la UI vuelve al bloque 1, el avance reportado no retrocede
        assertFalse(probe.state().completed)
        vm.requestExit()

        assertEquals(educational(10, 100, completed = true), repo.progress.last().second)
    }

    // --- Niño (paralelo) ---------------------------------------------------------------------

    @Test fun child_startsFocusModeAndOpensTheChildSession() = readerTest {
        val vm = viewModel(childId = "child-1")
        val probe = observe(vm)

        vm.start()

        assertTrue(focus.state.value.lockRequested)
        assertTrue(probe.state().lockActive)
        assertEquals(listOf<String?>("child-1"), repo.starts.map { it.first }) // POST /children/child-1/readings
    }

    @Test fun child_exitRequiresTheParentalGateAndStaysLockedWithoutThePin() = readerTest {
        val vm = viewModel(childId = "child-1")
        val probe = observe(vm)
        vm.start()

        vm.requestExit()

        assertTrue("abre el Muro Parental", probe.state().showGate)
        assertTrue(focus.state.value.isActive)
        assertTrue(probe.events.isEmpty())

        vm.dismissGate()
        assertFalse(probe.state().showGate)
        assertTrue("cancelar el Muro no sale de la lectura", focus.state.value.isActive)
    }

    @Test fun child_exitsOnlyWithAValidatedPinAndSendsFinalTelemetry() = readerTest {
        val vm = viewModel(childId = "child-1")
        val probe = observe(vm)
        vm.start()
        mono += 161_000
        focus.tick()
        vm.finishReading()
        vm.requestExit()

        vm.onGateVerified(PinVerification())

        assertEquals(listOf<ReaderEvent>(ReaderEvent.Exited), probe.events)
        assertFalse(focus.state.value.isActive)
        assertEquals("reading-1" to educational(161, 100, completed = true), repo.progress.last())
        assertNull(holder.current.value)
    }

    @Test fun child_missingProofIsRejectedEvenIfTheGateIsBypassed() = readerTest {
        val vm = viewModel(childId = "child-1")
        val probe = observe(vm)
        vm.start()

        vm.onGateVerified(null) // un cierre sin prueba de PIN debe volver a pedir el Muro

        assertTrue(focus.state.value.isActive)
        assertTrue(probe.state().showGate)
        assertTrue(probe.events.isEmpty())
    }

    // --- Mini-Reto de Comprensión ------------------------------------------------------------

    @Test fun lessonWithoutQuestionsGoesStraightToTheCompletionPanel() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)
        vm.start()

        vm.finishReading()

        assertTrue(probe.state().completed)
        assertFalse(probe.state().showQuiz)
        assertNull(probe.state().quiz)
    }

    @Test fun finishingTheReadingShowsTheQuizBeforeClosing() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        val probe = observe(vm)
        vm.start()
        assertFalse("el reto no aparece mientras se lee", probe.state().showQuiz)

        vm.finishReading()

        assertTrue(probe.state().showQuiz)
        assertEquals("q1", probe.state().quiz!!.current!!.id)
        assertTrue("no se cierra sola", probe.events.isEmpty())
    }

    @Test fun fluiBotThinksDuringTheMiniChallengeAndCelebratesWhenItIsPassed() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        val probe = observe(vm)
        vm.start()
        assertTrue(probe.state().botMood in setOf(FluiBotMood.READING, FluiBotMood.THINKING))

        vm.finishReading()
        assertEquals("piensa mientras responde", FluiBotMood.THINKING, probe.state().botMood)
        assertFalse(probe.state().quizSucceeded)

        vm.answerQuiz(0) // correcta
        vm.nextQuestion()
        vm.answerQuiz(0)
        assertTrue("reto superado con al menos un acierto", probe.state().quizSucceeded)
        assertEquals(FluiBotMood.CELEBRATING, probe.state().botMood)

        vm.finishQuiz()
        assertEquals("en el laboratorio saluda hasta abrir el cofre", FluiBotMood.HAPPY, probe.state().botMood)
        vm.claimChest()
        assertEquals(FluiBotMood.CELEBRATING, probe.state().botMood)
    }

    @Test fun fluiBotReadsConcentratedAndThinksWhileTheOriginalLoads() {
        val reading = ReaderUiState(tts = com.fluilet.app.core.tts.TtsState(status = com.fluilet.app.core.tts.TtsStatus.Idle))
        assertEquals(FluiBotMood.READING, reading.botMood)
        assertEquals(FluiBotMood.THINKING, reading.copy(originalLoading = true).botMood)
        assertEquals(FluiBotMood.THINKING, reading.copy(tts = com.fluilet.app.core.tts.TtsState(status = com.fluilet.app.core.tts.TtsStatus.Initializing)).botMood)
    }

    @Test fun aMiniChallengeWithoutCorrectAnswersIsNotCelebrated() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        vm.answerQuiz(1); vm.nextQuestion(); vm.answerQuiz(0) // ambas incorrectas
        assertTrue(probe.state().quiz!!.isComplete)
        assertFalse(probe.state().quizSucceeded)
        assertEquals(FluiBotMood.THINKING, probe.state().botMood)
    }

    @Test fun adultQuizFlowGivesFeedbackScoresAndReportsItInTheFinalProgress() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        val probe = observe(vm)
        vm.start()
        mono += 90_000
        vm.finishReading()

        vm.answerQuiz(0) // correcta
        assertTrue(probe.state().quiz!!.currentAnswer!!.isCorrect)
        vm.nextQuestion()
        vm.answerQuiz(0) // q2: la correcta es la 1
        assertFalse(probe.state().quiz!!.currentAnswer!!.isCorrect)
        assertTrue(probe.state().quiz!!.isComplete)

        vm.finishQuiz()
        assertTrue(probe.state().quizDone)
        assertFalse(probe.state().showQuiz)
        assertFalse("el adulto no pasa por el Muro Parental", probe.state().showGate)

        vm.requestExit()
        val sent = repo.progress.last().second
        assertEquals(ComprehensionResult(answered = 2, correct = 1), sent.comprehension)
        assertEquals(50, sent.comprehension!!.score)
        assertEquals(100, sent.completionPercent)
        assertTrue(sent.completed)
        assertEquals("ciencia_espacio", sent.category)
        assertEquals(2, sent.simplificationLevel)
    }

    @Test fun perfectAndZeroScores() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        observe(vm)
        vm.start()
        vm.finishReading()
        vm.answerQuiz(0); vm.nextQuestion(); vm.answerQuiz(1)
        vm.requestExit()
        assertEquals(100, repo.progress.last().second.comprehension!!.score)
    }

    @Test fun childQuizNeverOpensThePinAndLeadsToTheRewardLabInsideFocusMode() = readerTest {
        val vm = viewModel(childId = "child-1", withQuiz = true)
        val probe = observe(vm)
        vm.start()
        mono += 60_000
        vm.finishReading()
        vm.answerQuiz(0); vm.nextQuestion(); vm.answerQuiz(1)

        vm.finishQuiz()

        assertFalse("responder el mini-reto JAMÁS abre el diálogo de PIN", probe.state().showGate)
        assertTrue("sigue anclado en Modo Enfoque", focus.state.value.isActive && focus.state.value.lockRequested)
        assertTrue(probe.state().lockActive)
        assertTrue("pasa al Laboratorio de Recompensas", probe.state().showLab)
        assertFalse(probe.state().showQuiz)
        assertTrue(probe.events.isEmpty())
        assertEquals("sin cierre ni telemetría final todavía", 0, launcher.stops)
    }

    @Test fun onlyTheTutorButtonAndAValidPinLeaveTheLab() = readerTest {
        val (vm, probe) = viewModelWithLab()

        vm.requestExit() // botón "Entregar al Tutor (Desbloquear con PIN)"
        assertTrue(probe.state().showGate)
        assertTrue(focus.state.value.isActive)

        vm.onGateVerified(null) // sin prueba de PIN no se sale
        assertTrue(focus.state.value.isActive)

        vm.onGateVerified(PinVerification())
        assertEquals(listOf<ReaderEvent>(ReaderEvent.Exited), probe.events)
        val sent = repo.progress.last().second
        assertEquals(ComprehensionResult(2, 2), sent.comprehension)
        assertTrue(sent.completed)
    }

    /** Un solo observador por ViewModel: los eventos viajan por un Channel y cada uno se entrega a un único colector. */
    private fun kotlinx.coroutines.test.TestScope.viewModelWithLab(): Pair<ReaderViewModel, Probe> {
        val vm = viewModel(childId = "child-1", withQuiz = true)
        val probe = observe(vm)
        vm.start()
        mono += 60_000
        vm.finishReading()
        vm.answerQuiz(0); vm.nextQuestion(); vm.answerQuiz(1)
        vm.finishQuiz()
        return vm to probe
    }

    @Test fun leavingMidQuizReportsOnlyWhatWasAnswered() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        observe(vm)
        vm.start()
        vm.finishReading()
        vm.answerQuiz(0)

        vm.requestExit()

        assertEquals(ComprehensionResult(answered = 1, correct = 1), repo.progress.last().second.comprehension)
    }

    @Test fun readingAgainKeepsTheQuizResultAndDoesNotAskAgain() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        vm.answerQuiz(1); vm.nextQuestion(); vm.answerQuiz(1)
        vm.finishQuiz()

        vm.restart()
        vm.finishReading()

        assertFalse("el reto ya se hizo", probe.state().showQuiz)
        vm.requestExit()
        assertEquals(1, repo.progress.last().second.comprehension!!.correct)
    }

    @Test fun noQuizAnswersMeansNoComprehensionIsReported() = readerTest {
        val vm = viewModel(childId = null, withQuiz = true)
        observe(vm)
        vm.start()
        vm.requestExit()
        assertNull(repo.progress.last().second.comprehension)
    }

    // --- Velocidad de voz --------------------------------------------------------------------

    @Test fun defaultSpeedIsNormalAndIsReported() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)
        vm.start()
        assertEquals(1.0f, probe.state().settings.speechRate, 0.0001f)
        vm.requestExit()
        assertEquals(1.0, repo.progress.last().second.ttsSpeed!!, 0.0001)
    }

    @Test fun speedChipsApplyTheRateToTheEngine_pausadoNormalAgil() = readerTest {
        val vm = viewModel(childId = null)
        observe(vm)
        vm.start()

        vm.onSpeechRate(0.8f); vm.togglePlayPause()
        assertEquals(0.8f, engine.last.rate, 0.0001f)
        vm.stopSpeech()

        vm.onSpeechRate(1.0f); vm.togglePlayPause()
        assertEquals(1.0f, engine.last.rate, 0.0001f)
        vm.stopSpeech()

        vm.onSpeechRate(1.2f); vm.togglePlayPause()
        assertEquals(1.2f, engine.last.rate, 0.0001f)
    }

    @Test fun changingTheSpeedWhileSpeakingContinuesFromTheCurrentWord() = readerTest {
        val vm = viewModel(childId = null)
        observe(vm)
        vm.start()
        vm.togglePlayPause()
        engine.range(7, 13) // palabra en curso dentro del bloque

        vm.onSpeechRate(1.2f)

        assertEquals(1.2f, engine.last.rate, 0.0001f)
        assertEquals("Bloque número 0 del cuento.".substring(7), engine.last.text)
    }

    @Test fun lastSelectedSpeedIsTheOneReportedAsTtsSpeedUsed() = readerTest {
        val vm = viewModel(childId = null)
        observe(vm)
        vm.start()
        vm.onSpeechRate(0.8f)
        vm.onSpeechRate(1.2f)
        vm.requestExit()
        assertEquals(1.2, repo.progress.last().second.ttsSpeed!!, 0.0001)
    }

    // --- Laboratorio de recompensas y cofre ------------------------------------------------------

    @Test fun labAppearsAfterFinishingEvenWithoutAQuiz() = readerTest {
        val vm = viewModel(childId = "child-1")
        val probe = observe(vm)
        vm.start()
        assertFalse(probe.state().showLab)

        vm.finishReading()

        assertTrue(probe.state().showLab)
        assertFalse(probe.state().showQuiz)
        assertFalse(probe.state().showGate)
    }

    @Test fun theQuizComesBeforeTheLabAndTheLabIsInsideFocusMode() = readerTest {
        val vm = viewModel(childId = "child-1", withQuiz = true)
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        assertTrue(probe.state().showQuiz)
        assertFalse(probe.state().showLab)

        vm.answerQuiz(0); vm.nextQuestion(); vm.answerQuiz(1)
        assertFalse("aún dentro del reto: no hay laboratorio hasta pulsar Continuar", probe.state().showLab)
        vm.finishQuiz()

        assertTrue(probe.state().showLab)
        assertTrue(probe.state().lockActive)
        assertEquals("Lector", probe.state().readerName)
        assertEquals("child-1", probe.state().ownerKey)
    }

    @Test fun lootPaysFiftyForCompletingAndTwentyFivePerCorrectAnswer() = readerTest {
        val vm = viewModel(childId = "child-1", withQuiz = true)
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        vm.answerQuiz(0); vm.nextQuestion(); vm.answerQuiz(1)
        vm.finishQuiz()

        val loot = probe.state().loot
        assertEquals(50, loot.gemsForCompleting)
        assertEquals(2, loot.correctAnswers)
        assertEquals(50, loot.gemsForAnswers)
        assertEquals(100, loot.gems)
        assertEquals(200, loot.xp)
    }

    @Test fun lootWithOneWrongAnswerOnlyPaysForTheCorrectOne() = readerTest {
        val vm = viewModel(childId = "child-1", withQuiz = true)
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        vm.answerQuiz(0); vm.nextQuestion(); vm.answerQuiz(0) // la segunda era la 1
        vm.finishQuiz()
        assertEquals(75, probe.state().loot.gems) // 50 + 25
        assertEquals(1, probe.state().loot.correctAnswers)
    }

    @Test fun lootWithoutAQuizIsJustTheCompletionBonus() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        assertEquals(50, probe.state().loot.gems)
        assertEquals(100, probe.state().loot.xp)
    }

    @Test fun cardUnlockOrUpgradeIsDetectedFromTheProgressBeforeTheSession() = readerTest {
        fun lesson(n: Int) = LessonProgress(n, "r$n", null, "deportes", 2, true, 100, null, 60, null, null)
        // Antes de esta sesión el niño ya tiene 1 lección de Deportes (carta Bronce).
        repo.educational = Gamification.enrich(EducationalProgress.empty().copy(lessons = listOf(lesson(1))))
        val vm = viewModel(childId = "child-1", category = "deportes")
        val probe = observe(vm)
        vm.start()

        vm.finishReading()

        assertEquals(CardChange.UPGRADED, probe.state().loot.cardChange) // Bronce -> Plata
        assertEquals("Plata", probe.state().loot.cardAfter!!.rarity!!.displayName)
    }

    @Test fun aFirstLessonInACategoryUnlocksItsCard() = readerTest {
        val vm = viewModel(childId = "child-1", category = "tecnologia")
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        assertEquals(CardChange.UNLOCKED, probe.state().loot.cardChange)
    }

    @Test fun theChestStartsClosedAndOpensOnClaim() = readerTest {
        val vm = viewModel(childId = "child-1")
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        assertFalse(probe.state().chestOpened)

        vm.claimChest()

        assertTrue(probe.state().chestOpened)
        assertTrue("reclamar el cofre no sale del Modo Enfoque", focus.state.value.isActive)
        assertFalse(probe.state().showGate)
    }

    @Test fun readingAgainFromTheLabReturnsToTheText() = readerTest {
        val vm = viewModel(childId = "child-1")
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        assertTrue(probe.state().showLab)

        vm.restart()

        assertFalse(probe.state().showLab)
        assertEquals(0, probe.state().chunkIndex)
    }

    @Test fun anAdultSeesTheLabToButItsExitNeedsNoPin() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)
        vm.start()
        vm.finishReading()
        assertTrue(probe.state().showLab)
        assertFalse(probe.state().lockActive)

        vm.requestExit()

        assertFalse(probe.state().showGate)
        assertEquals(listOf<ReaderEvent>(ReaderEvent.Exited), probe.events)
    }

    // --- Guía de renglón ----------------------------------------------------------------------------

    private val guideText = "Primera oración. Segunda oración. Tercera oración."
    private val guideAdaptation = AdaptationResult(
        title = "Guía",
        chunks = listOf(TextChunk(0, guideText), TextChunk(1, "Otro bloque corto.")),
        metrics = ReadabilityMetrics(30.0, 70.0, 40.0, 10, 10, 20, 3),
        documentId = "doc-1", category = "deportes", simplificationLevel = 2
    )

    private fun ReaderUiState.guideSlice() = guideRange?.let { currentText.substring(it.first, it.last + 1) }

    @Test fun guideIsOffByDefaultAndHighlightsTheFirstSentenceWhenEnabled() = readerTest {
        val vm = viewModel(childId = null, adaptation = guideAdaptation)
        val probe = observe(vm)
        vm.start()
        assertFalse(probe.state().guideEnabled)
        assertNull(probe.state().guideRange)

        vm.toggleGuide()

        assertTrue(probe.state().guideEnabled)
        assertEquals("Primera oración.", probe.state().guideSlice())

        vm.toggleGuide()
        assertNull("al apagarla se quita la franja", probe.state().guideRange)
    }

    @Test fun guideStepsThroughSentencesThenIntoTheNextBlock() = readerTest {
        val vm = viewModel(childId = null, adaptation = guideAdaptation)
        val probe = observe(vm)
        vm.start()
        vm.toggleGuide()

        vm.guideNext()
        assertEquals("Segunda oración.", probe.state().guideSlice())
        vm.guideNext()
        assertEquals("Tercera oración.", probe.state().guideSlice())
        vm.guideNext() // fin del bloque: pasa al siguiente

        assertEquals(1, probe.state().chunkIndex)
        assertEquals("Otro bloque corto.", probe.state().guideSlice())
    }

    @Test fun guidePreviousGoesBackAndCrossesToThePreviousBlock() = readerTest {
        val vm = viewModel(childId = null, adaptation = guideAdaptation)
        val probe = observe(vm)
        vm.start()
        vm.toggleGuide()
        vm.guideNext()
        vm.guidePrevious()
        assertEquals("Primera oración.", probe.state().guideSlice())

        vm.next() // bloque 2
        assertEquals(1, probe.state().chunkIndex)
        vm.guidePrevious()
        assertEquals(0, probe.state().chunkIndex)
    }

    @Test fun theSpokenWordMovesTheGuideToItsSentence() = readerTest {
        val vm = viewModel(childId = null, adaptation = guideAdaptation)
        val probe = observe(vm)
        vm.start()
        vm.toggleGuide()
        vm.togglePlayPause()

        engine.range(guideText.indexOf("Tercera"), guideText.indexOf("Tercera") + 7)

        assertEquals("Tercera oración.", probe.state().guideSlice())
    }

    @Test fun guideResetsToTheFirstSentenceWhenTheBlockChanges() = readerTest {
        val vm = viewModel(childId = null, adaptation = guideAdaptation)
        val probe = observe(vm)
        vm.start()
        vm.toggleGuide()
        vm.guideNext()
        vm.next()
        vm.previous()
        assertEquals("Primera oración.", probe.state().guideSlice())
    }

    // --- Original vs. Adaptado, dato curioso, Glosario Mágico y Laboratorio en 3 pestañas ---------

    private fun richAdaptation() = sampleAdaptation(2, withQuiz = true, category = "deportes").copy(
        funFact = "Dato del servidor.",
        originalText = "Texto original pegado por el tutor.",
        keyVocabulary = listOf(
            com.fluilet.app.domain.text.VocabularyEntry("planeta", "Un mundo redondo.", "La Tierra es un planeta."),
            com.fluilet.app.domain.text.VocabularyEntry("lava", "Roca derretida.", "La lava es roja.")
        )
    )

    @Test fun theOriginalViewToggleExposesTheOriginalTextFromRamOnly() = readerTest {
        val vm = viewModel(childId = "child-1", adaptation = richAdaptation())
        val probe = observe(vm)
        vm.start()
        assertFalse(probe.state().showOriginal)

        vm.toggleOriginal()

        assertTrue(probe.state().showOriginal)
        assertEquals("Texto original pegado por el tutor.", probe.state().originalText)
        vm.toggleOriginal()
        assertFalse(probe.state().showOriginal)
    }

    @Test fun lessonsWithoutAnOriginalOnlyOfferTheMetricsComparison() = readerTest {
        val vm = viewModel(childId = null)
        val probe = observe(vm)
        vm.start()
        assertNull(probe.state().originalText)
        assertEquals(77.1, probe.state().reading!!.adaptation.metrics.adaptedSzigrisztScore, 0.001)
    }

    @Test fun funFactIsTheServersOrALocalOneByCategory() = readerTest {
        val server = viewModel(childId = null, adaptation = richAdaptation())
        assertEquals("Dato del servidor.", observe(server).state().funFact)

        val local = viewModel(childId = null, adaptation = sampleAdaptation(2, category = "deportes"))
        assertEquals(com.fluilet.app.domain.gamification.LessonFacts.forCategory("deportes"), observe(local).state().funFact)

        val none = viewModel(childId = null, adaptation = sampleAdaptation(2, category = null))
        assertNull(observe(none).state().funFact)
    }

    @Test fun glossaryWordsAreSelectableAndTappingTwiceClosesThem() = readerTest {
        val vm = viewModel(childId = null, adaptation = richAdaptation())
        val probe = observe(vm)
        vm.start()
        assertEquals(listOf("planeta", "lava"), probe.state().vocabulary.map { it.word })
        assertNull(probe.state().selectedWord)

        vm.selectWord("lava")
        assertEquals("Roca derretida.", probe.state().selectedWord!!.definition)
        vm.selectWord("planeta")
        assertEquals("planeta", probe.state().selectedWord!!.word)
        vm.selectWord("planeta")
        assertNull(probe.state().selectedWord)

        vm.selectWord("inexistente")
        assertNull(probe.state().selectedWord)
        vm.selectWord("lava")
        vm.clearWord()
        assertNull(probe.state().selectedWord)
    }

    @Test fun withoutServerVocabularyTheLocalGlossaryUsesTheTextOfTheLesson() = readerTest {
        val adaptation = sampleAdaptation(1).copy(
            chunks = listOf(com.fluilet.app.domain.model.TextChunk(0, "El volcán echó lava cerca del planeta."))
        )
        val vm = viewModel(childId = null, adaptation = adaptation)
        assertEquals(listOf("volcán", "lava", "planeta"), observe(vm).state().vocabulary.map { it.word })
    }

    @Test fun listenToWordSpeaksThatWordWithoutLeavingTheReader() = readerTest {
        val vm = viewModel(childId = "child-1", adaptation = richAdaptation())
        val probe = observe(vm)
        vm.start()

        vm.speakWord("planeta")

        assertEquals("planeta", engine.last.text)
        assertFalse(probe.state().showGate)
        assertTrue(probe.state().lockActive)
    }

    @Test fun theLabStartsOnTuPremioAndSwitchesBetweenItsThreeTabs() = readerTest {
        val vm = viewModel(childId = "child-1", adaptation = richAdaptation())
        val probe = observe(vm)
        vm.start()
        assertEquals(listOf("🎁 Tu Premio", "🤖 Personalizar a FluiBot", "🃏 Mi Álbum"), com.fluilet.app.presentation.reader.LabTab.entries.map { "${it.emoji} ${it.label}" })
        assertEquals(com.fluilet.app.presentation.reader.LabTab.PREMIO, probe.state().labTab)

        vm.selectLabTab(com.fluilet.app.presentation.reader.LabTab.FLUIBOT)
        assertEquals(com.fluilet.app.presentation.reader.LabTab.FLUIBOT, probe.state().labTab)
        vm.selectLabTab(com.fluilet.app.presentation.reader.LabTab.ALBUM)
        assertEquals(com.fluilet.app.presentation.reader.LabTab.ALBUM, probe.state().labTab)
        // Cambiar de pestaña nunca abre el PIN ni desbloquea el Modo Enfoque.
        assertFalse(probe.state().showGate)
        assertTrue(probe.state().lockActive)
    }

    @Test fun theSessionContributionFeedsTheDailyMissions() = readerTest {
        val vm = viewModel(childId = "child-1", adaptation = richAdaptation())
        val probe = observe(vm)
        vm.start()
        mono = 90_000
        vm.finishReading()
        vm.answerQuiz(0)

        val session = probe.state().session
        assertTrue(session.completed)
        assertEquals(1, session.correctAnswers)
    }

    // --- Comparador para TODAS las lecturas + Ficha Técnica NLP ---------------------------------

    @Test fun anOlderLessonWithoutOriginalFetchesItFromTheServerOnlyOnce() = readerTest {
        adaptations.fetchResult = Result.success(sampleAdaptation().copy(originalText = "Texto original recuperado."))
        val vm = viewModel(childId = null, adaptation = sampleAdaptation(2, documentId = "doc-1"))
        val probe = observe(vm)
        vm.start()
        assertNull(probe.state().originalText)

        vm.toggleOriginal()

        assertEquals(listOf("doc-1"), adaptations.fetchRequests)
        assertEquals("Texto original recuperado.", probe.state().originalText)
        assertFalse(probe.state().originalLoading)
        vm.toggleOriginal()
        vm.toggleOriginal()
        assertEquals("no vuelve a pedirlo", 1, adaptations.fetchRequests.size)
    }

    @Test fun theAdaptationIdIsPreferredOverTheDocumentIdWhenFetching() = readerTest {
        val vm = viewModel(childId = null, adaptation = sampleAdaptation(2, documentId = "doc-1").copy(adaptationId = "ad-7"))
        observe(vm)
        vm.start()
        vm.toggleOriginal()
        assertEquals(listOf("ad-7"), adaptations.fetchRequests)
    }

    @Test fun aLessonThatAlreadyCarriesItsOriginalNeverCallsTheServer() = readerTest {
        val vm = viewModel(childId = null, adaptation = richAdaptation())
        val probe = observe(vm)
        vm.start()
        vm.toggleOriginal()
        assertTrue(adaptations.fetchRequests.isEmpty())
        assertEquals("Texto original pegado por el tutor.", probe.state().originalText)
    }

    @Test fun ifTheServerCannotReturnTheOriginalTheComparisonStillShowsTheMetrics() = readerTest {
        val vm = viewModel(childId = null, adaptation = sampleAdaptation(2, documentId = "doc-1"))
        val probe = observe(vm)
        vm.start()
        vm.toggleOriginal()
        assertNull(probe.state().originalText)
        assertFalse(probe.state().originalLoading)
        assertTrue(probe.state().showOriginal)
        assertNotNull(probe.state().technicalSheet)
    }

    @Test fun aGeneratedLessonLikeBiologiaMarinaGetsTheFichaTecnicaWithItsSource() = readerTest {
        val generated = sampleAdaptation(2, category = "animales_naturaleza", level = 2).copy(
            title = "Biología Marina", topic = "Biología Marina", targetLevel = 2,
            originalText = "Texto enciclopédico original sobre biología marina.",
            source = com.fluilet.app.domain.model.LessonSourceInfo("wikipedia", "Biología marina", "https://es.wikipedia.org/wiki/Biolog%C3%ADa_marina")
        )
        val vm = viewModel(childId = "child-1", adaptation = generated)
        val probe = observe(vm)
        vm.start()
        vm.toggleOriginal()

        val sheet = probe.state().technicalSheet!!
        assertEquals(37.1, sheet.originalScore, 0.001)
        assertEquals(77.1, sheet.adaptedScore, 0.001)
        assertEquals(2, sheet.targetLevel)
        assertTrue(sheet.headline.endsWith("Adaptado al Nivel 2"))
        assertEquals("Texto enciclopédico original sobre biología marina.", probe.state().originalText)
        assertTrue(probe.state().reading!!.adaptation.source!!.isWikipedia)
        assertTrue("sigue dentro del Modo Enfoque", probe.state().lockActive)
    }

    // --- Super-comparador: selector en vivo de los 5 niveles del modelo reentrenado ------------------------

    /** Lección del servidor con sus 5 variantes reales (Agujeros negros de Diego, backend 42482e0). */
    private fun retrainedLesson(): AdaptationResult {
        fun preview(level: Int, score: Double, wps: Double, text: String) = com.fluilet.app.domain.model.LevelPreview(
            level, text, score, com.fluilet.app.domain.text.Szigriszt.inflesz(score), wps,
            com.fluilet.app.domain.text.Szigriszt.syllablesPerWord(score, wps)
        )
        return sampleAdaptation(2, category = "ciencia_espacio", level = 4).copy(
            title = "Agujeros negros",
            metrics = ReadabilityMetrics(
                56.43970588235297, 61.84269230769231, 5.402986425339343, 85, 78, 162, 5, 182, 5,
                originalAvgWordsPerSentence = 17.0, adaptedAvgWordsPerSentence = 15.6
            ),
            targetLevel = 4,
            originalText = "Los agujeros negros son regiones del espacio cuya gravedad impide que la luz escape desde su interior.",
            readabilityGain = 5.402986425339343,
            lexicalReplacements = listOf(com.fluilet.app.domain.model.LexicalReplacement("regiones", "zonas")),
            levelPreviews = listOf(
                preview(1, 83.32041666666667, 8.0, "Un agujero negro es una zona del espacio. Su gravedad no deja salir la luz."),
                preview(2, 79.01759705488624, 9.22, "Un agujero negro es una zona del espacio con mucha gravedad."),
                preview(3, 69.38801204819279, 16.6, "Los agujeros negros son zonas del espacio donde la luz no sale."),
                preview(4, 61.84269230769231, 15.6, "Los agujeros negros son zonas del espacio cuya gravedad impide que la luz escape."),
                preview(5, 57.06639534883725, 17.2, "Los agujeros negros son regiones del espacio cuya gravedad impide que la luz escape.")
            ),
            levelProgressionMet = true,
            modelVersion = "context-ranker-es-v2.3",
            checkpointName = "context-ranker-v2.3",
            engineVersion = "pedagogical-es-v2.3"
        )
    }

    @Test fun theComparatorOpensOnTheLessonLevelWithTheRetrainedModelSeal() = readerTest {
        val vm = viewModel(childId = "child-1", adaptation = retrainedLesson())
        val probe = observe(vm)
        vm.start()
        assertNull("sin comparador no se calcula nada", probe.state().comparison)

        vm.toggleOriginal()

        val s = probe.state()
        assertEquals(listOf(1, 2, 3, 4, 5), s.levelPreviews.map { it.level })
        assertEquals(4, s.comparisonLevel)
        val c = s.comparison!!
        assertEquals(56.44, c.originalScore, 0.01)
        assertEquals(61.84, c.adaptedScore, 0.01)
        assertEquals(5.40, c.gain, 0.01)
        assertEquals(17.0, c.originalWordsPerSentence, 1e-9)
        assertEquals(15.6, c.adaptedWordsPerSentence, 1e-9)
        assertEquals(2.08, c.adaptedSyllablesPerWord, 0.01)
        assertEquals(listOf(com.fluilet.app.domain.model.LexicalReplacement("regiones", "zonas")), c.replacements)
        assertTrue(s.reading!!.adaptation.isRetrainedModel)
        assertEquals("4,738 pares · SARI 44.56 · Exactitud 73.25% · 5 Bandas Verificadas", s.modelAudit!!.badgeLine)
        assertTrue("no se pidió nada al servidor: la lección ya trae todo", adaptations.fetchRequests.isEmpty())
    }

    @Test fun tappingTheFiveLevelChipsSwitchesTextScoreAndBandLiveInsideFocusMode() = readerTest {
        val vm = viewModel(childId = "child-1", adaptation = retrainedLesson())
        val probe = observe(vm)
        vm.start()
        vm.toggleOriginal()

        vm.selectComparisonLevel(1)
        with(probe.state().comparison!!) {
            assertEquals(1, level)
            assertEquals(83.32, adaptedScore, 0.01)
            assertEquals(26.88, gain, 0.01)
            assertEquals("Muy fácil", readabilityLevel)
            assertTrue("82–100", inBand)
            assertEquals(8.0, adaptedWordsPerSentence, 1e-9)
            assertTrue(adaptedText.startsWith("Un agujero negro"))
            assertTrue("sus propias transformaciones", replacements.isNotEmpty())
        }

        vm.selectComparisonLevel(5)
        with(probe.state().comparison!!) {
            assertEquals(5, level)
            assertEquals(57.07, adaptedScore, 0.01)
            assertTrue(adaptedText.contains("regiones"))
        }
        val scores = (1..5).map { lvl -> vm.selectComparisonLevel(lvl); probe.state().comparison!!.adaptedScore }
        assertTrue("Nivel 1 > 2 > 3 > 4 > 5: $scores", scores.zipWithNext().all { (a, b) -> a > b })

        vm.selectComparisonLevel(9) // fuera de rango: se ignora
        assertEquals(5, probe.state().comparisonLevel)
        assertTrue("sigue en Modo Enfoque", probe.state().lockActive)
        assertFalse("cambiar de nivel nunca abre el PIN", probe.state().showGate)
        assertEquals("el modelo se consulta una sola vez", 1, modelInfo.calls)
    }

    @Test fun aPastedTextWithoutPreviewsGetsFiveLocalVariantsWithoutCallingTheServer() = readerTest {
        val pasted = sampleAdaptation(2, level = 2).copy(
            originalText = "Los científicos utilizan numerosos instrumentos complejos para observar el universo, aunque posteriormente " +
                "deben comprender los datos que obtienen mediante cálculos fundamentales que realizan durante varias semanas de trabajo."
        )
        val vm = viewModel(childId = null, adaptation = pasted)
        val probe = observe(vm)
        vm.start()
        vm.toggleOriginal()

        val s = probe.state()
        assertEquals(5, s.levelPreviews.size)
        assertTrue(s.levelPreviews.none { it.fromServer })
        assertEquals(2, s.comparisonLevel)
        assertFalse(s.comparison!!.fromServer)
        assertTrue(adaptations.fetchRequests.isEmpty())
    }

    @Test fun anOlderLessonUsesThePreviewsOfTheRetainedAdaptationFetchedOnce() = readerTest {
        adaptations.fetchResult = Result.success(retrainedLesson())
        val old = sampleAdaptation(2, documentId = "doc-1", level = 4)
        val vm = viewModel(childId = null, adaptation = old)
        val probe = observe(vm)
        vm.start()
        vm.toggleOriginal()

        assertEquals(listOf("doc-1"), adaptations.fetchRequests)
        assertEquals(listOf(1, 2, 3, 4, 5), probe.state().levelPreviews.map { it.level })
        assertTrue(probe.state().levelPreviews.all { it.fromServer })
        assertEquals(5.40, probe.state().comparison!!.gain, 0.01)
        vm.selectComparisonLevel(1)
        assertEquals(83.32, probe.state().comparison!!.adaptedScore, 0.01)
    }
}
