package com.fluilet.app.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.AdaptTextUseCase
import com.fluilet.app.domain.usecase.GenerateLessonUseCase
import com.fluilet.app.domain.usecase.GetChildrenUseCase
import com.fluilet.app.domain.usecase.IsTutorPinConfiguredUseCase
import com.fluilet.app.presentation.newreading.InputMode
import com.fluilet.app.presentation.newreading.NewReadingOutcome
import com.fluilet.app.presentation.newreading.NewReadingUiState
import com.fluilet.app.presentation.newreading.NewReadingViewModel
import com.fluilet.app.testutil.FakeAdaptationRepository
import com.fluilet.app.testutil.FakeChildRepository
import com.fluilet.app.testutil.FakeTutorPin
import com.fluilet.app.testutil.sampleAdaptation
import com.fluilet.app.testutil.sampleChild
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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

/** Pantalla de adaptación: modo "Generar lección" (categoría, tema, nivel) y traspaso al lector. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewReadingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val adaptation = FakeAdaptationRepository()
    private val holder = ActiveReadingHolder()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(childId: String? = "child-1"): NewReadingViewModel {
        val handle = SavedStateHandle(if (childId != null) mapOf("childId" to childId) else emptyMap())
        return NewReadingViewModel(
            handle, mockk<Context>(relaxed = true),
            GetChildrenUseCase(FakeChildRepository(listOf(sampleChild("child-1", "Mateo")))),
            AdaptTextUseCase(adaptation), GenerateLessonUseCase(adaptation), holder,
            IsTutorPinConfiguredUseCase(FakeTutorPin(true))
        )
    }

    @Test fun threeInputModesAreAvailable() {
        assertEquals(listOf(InputMode.PASTE, InputMode.FILE, InputMode.GENERATE), InputMode.entries)
    }

    @Test fun generatingNeedsACategoryButNotATopic() {
        val vm = viewModel()
        vm.onMode(InputMode.GENERATE)
        assertFalse(vm.state.value.canAdapt)

        vm.onCategory(LessonCategory.ANIMALES_NATURALEZA)

        assertTrue(vm.state.value.canAdapt)
        assertEquals("", vm.state.value.topic)
    }

    @Test fun topicIsLimitedTo120Characters() {
        val vm = viewModel()
        vm.onTopic("x".repeat(300))
        assertEquals(120, vm.state.value.topic.length)
    }

    @Test fun generateSendsCategoryTopicLevelAndChild() = runTest(dispatcher) {
        val vm = viewModel("child-1")
        vm.onMode(InputMode.GENERATE)
        vm.onCategory(LessonCategory.HISTORIA_MISTERIOS)
        vm.onTopic("  Pirámides  de   Egipto ")
        vm.onLevel(NlpLevel.NIVEL_2)

        vm.adapt()

        val (request, childId) = adaptation.generateRequests.single()
        assertEquals(LessonCategory.HISTORIA_MISTERIOS, request.category)
        assertEquals("Pirámides de Egipto", request.cleanTopic)
        assertEquals(NlpLevel.NIVEL_2, request.level)
        assertEquals("child-1", childId)
        assertNotNull(vm.state.value.result)
        assertFalse(vm.state.value.isAdapting)
        assertNull(vm.state.value.errorMessage)
    }

    @Test fun theChildsLevelIsTheDefaultLevelForTheLesson() = runTest(dispatcher) {
        val vm = viewModel("child-1")
        vm.onMode(InputMode.GENERATE)
        vm.onCategory(LessonCategory.DEPORTES)
        vm.adapt()
        assertEquals(NlpLevel.NIVEL_3, adaptation.generateRequests.single().first.level) // nivel del perfil
    }

    @Test fun adultGeneratesWithoutAChild() = runTest(dispatcher) {
        val vm = viewModel(childId = null)
        vm.onMode(InputMode.GENERATE)
        vm.onCategory(LessonCategory.TECNOLOGIA)
        vm.adapt()
        assertNull(adaptation.generateRequests.single().second)
        assertFalse(vm.state.value.forChild)
    }

    @Test fun serverErrorsAreShownFriendlyAndNothingIsLeftHalfDone() = runTest(dispatcher) {
        adaptation.generateResult = Result.failure(AppError.Busy())
        val vm = viewModel()
        vm.onMode(InputMode.GENERATE)
        vm.onCategory(LessonCategory.CUENTOS_AVENTURAS)

        vm.adapt()

        assertNull(vm.state.value.result)
        assertFalse(vm.state.value.isAdapting)
        assertTrue(vm.state.value.errorMessage!!.contains("procesando"))
    }

    @Test fun changingTheCategoryDiscardsThePreviousResult() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onMode(InputMode.GENERATE)
        vm.onCategory(LessonCategory.DEPORTES)
        vm.adapt()
        assertNotNull(vm.state.value.result)

        vm.onCategory(LessonCategory.TECNOLOGIA)

        assertNull(vm.state.value.result)
    }

    @Test fun startNowLeavesTheLessonWithItsQuizReadyInRamForTheChildsFocusSession() = runTest(dispatcher) {
        adaptation.generateResult = Result.success(sampleAdaptation(withQuiz = true, category = "deportes", level = 2))
        val vm = viewModel("child-1")
        val outcomes = mutableListOf<NewReadingOutcome>()
        backgroundScopeCollect(vm, outcomes)
        vm.onMode(InputMode.GENERATE)
        vm.onCategory(LessonCategory.DEPORTES)
        vm.adapt()

        vm.startNow()

        assertEquals(listOf(NewReadingOutcome.START_NOW), outcomes)
        val reading = holder.current.value!!
        assertEquals("child-1", reading.childId)
        assertEquals("Mateo", reading.readerName)
        assertEquals(2, reading.adaptation.questions.size)
        assertEquals("deportes", reading.adaptation.category)
        // Con la paleta, fuente y tamaño del perfil del niño.
        assertEquals(com.fluilet.app.domain.model.AccessiblePalette.SEPIA, reading.settings.palette)
        assertEquals(20f, reading.settings.fontSizeSp, 0.0001f)
    }

    private fun kotlinx.coroutines.test.TestScope.backgroundScopeCollect(vm: NewReadingViewModel, into: MutableList<NewReadingOutcome>) {
        backgroundScope.launch(dispatcher) { vm.done.collect { into += it } }
    }

    @Test fun stateExposesTheCategoryAndTopicDefaults() {
        val s = NewReadingUiState()
        assertNull(s.category)
        assertEquals("", s.topic)
        assertFalse(s.canAdapt)
    }
}
