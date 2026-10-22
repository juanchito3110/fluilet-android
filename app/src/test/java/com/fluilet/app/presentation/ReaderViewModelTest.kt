package com.fluilet.app.presentation

import com.fluilet.app.core.focusmode.FocusServiceLauncher
import com.fluilet.app.core.focusmode.FocusSessionController
import com.fluilet.app.core.tts.TtsManager
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.ComprehensionResult
import com.fluilet.app.domain.model.PinVerification
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.StartReadingSessionUseCase
import com.fluilet.app.domain.usecase.UpdateReadingProgressUseCase
import com.fluilet.app.domain.usecase.UpdateReadingSettingsUseCase
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

    private fun viewModel(childId: String?, chunks: Int = 3, withQuiz: Boolean = false): ReaderViewModel {
        holder.set(ActiveReading(childId, "Lector", ReadingSettings(), sampleAdaptation(chunks, withQuiz = withQuiz)))
        return ReaderViewModel(
            holder, tts, focus, UpdateReadingSettingsUseCase(),
            StartReadingSessionUseCase(repo), UpdateReadingProgressUseCase(repo), appScope
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

    @Test fun childQuizEndsByOpeningTheParentalGateAndOnlyThePinClosesTheSession() = readerTest {
        val vm = viewModel(childId = "child-1", withQuiz = true)
        val probe = observe(vm)
        vm.start()
        mono += 60_000
        vm.finishReading()
        vm.answerQuiz(0); vm.nextQuestion(); vm.answerQuiz(1)

        vm.finishQuiz()

        assertTrue("al terminar el mini-reto se abre el Muro Parental", probe.state().showGate)
        assertTrue(focus.state.value.isActive)
        assertTrue(probe.events.isEmpty())

        vm.onGateVerified(PinVerification())

        assertEquals(listOf<ReaderEvent>(ReaderEvent.Exited), probe.events)
        val sent = repo.progress.last().second
        assertEquals(ComprehensionResult(2, 2), sent.comprehension)
        assertEquals(100, sent.comprehension!!.score)
        assertTrue(sent.completed)
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
}
