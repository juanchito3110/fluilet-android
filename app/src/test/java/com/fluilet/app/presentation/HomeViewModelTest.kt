package com.fluilet.app.presentation

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.BadgeCatalog
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.User
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.DeleteChildUseCase
import com.fluilet.app.domain.usecase.GetChildrenUseCase
import com.fluilet.app.domain.usecase.GetEducationalProgressUseCase
import com.fluilet.app.domain.usecase.IsTutorPinConfiguredUseCase
import com.fluilet.app.domain.usecase.LogoutUseCase
import com.fluilet.app.domain.usecase.SeedDemoDataUseCase
import com.fluilet.app.domain.usecase.ClaimDailyMissionUseCase
import com.fluilet.app.domain.usecase.SetNlpLevelUseCase
import com.fluilet.app.presentation.home.HomeUiState
import com.fluilet.app.presentation.home.HomeViewModel
import com.fluilet.app.testutil.FakeChildRepository
import com.fluilet.app.testutil.FakeReadingSessionRepository
import com.fluilet.app.testutil.FakeSecureStore
import com.fluilet.app.testutil.FakeTutorPin
import com.fluilet.app.testutil.sampleAdaptation
import com.fluilet.app.testutil.sampleChild
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/**
 * Estado de pantalla del panel para los dos roles que lo usan: el tutor (perfiles de niño) y el
 * adulto independiente (sin perfiles; historial propio con `GET /readings`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val sessions = FakeReadingSessionRepository()
    private val children = FakeChildRepository()
    private val holder = ActiveReadingHolder()
    private val auth = mockk<AuthRepository>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun progress(completed: Int = 2, total: Int = 3) = EducationalProgress(
        totalLessons = total, completedLessons = completed, incompleteLessons = total - completed,
        completionRatePercent = 100.0 * completed / total, totalReadingSeconds = 900,
        averageComprehension = 75.0, recommendedLevel = 3, lessons = emptyList(),
        badges = BadgeCatalog.all.mapIndexed { i, b -> com.fluilet.app.domain.model.EducationalBadge(b.code, b.title, b.description, i == 0) }
    )

    private fun viewModel(role: UserRole, pinConfigured: Boolean = false): HomeViewModel {
        val session = SessionManager(FakeSecureStore()).apply {
            restore()
            save(AuthSession("access", "refresh", role, "x@example.com"))
        }
        coEvery { auth.currentUser() } returns Result.success(User("1", "x@example.com", "Ana Pérez", role, true, false))
        coEvery { auth.logout() } returns Unit
        return HomeViewModel(
            session, holder, auth,
            GetChildrenUseCase(children), SetNlpLevelUseCase(children), DeleteChildUseCase(children),
            GetEducationalProgressUseCase(sessions), IsTutorPinConfiguredUseCase(FakeTutorPin(pinConfigured)),
            LogoutUseCase(auth), holder, SeedDemoDataUseCase(sessions), ClaimDailyMissionUseCase(sessions)
        )
    }

    /** Suscribe a `uiState` como lo hace la pantalla (el estado se calcula solo con un colector activo). */
    private fun TestScope.observe(vm: HomeViewModel): () -> HomeUiState {
        backgroundScope.launch(dispatcher) { vm.uiState.collect { } }
        return { vm.uiState.value }
    }

    // --- Adulto independiente --------------------------------------------------------------

    @Test fun adult_loadsOwnProgressWithoutTouchingChildProfiles() = runTest(dispatcher) {
        sessions.educational = progress()
        val vm = viewModel(UserRole.ADULTO_INDEPENDIENTE)
        val state = observe(vm)

        vm.refresh()

        assertEquals(UserRole.ADULTO_INDEPENDIENTE, state().role)
        assertFalse(state().isTutor)
        assertFalse(state().isLoading)
        assertEquals("Ana", state().userName)
        assertEquals("sin llamadas a /children", 0, children.listCalls)
        assertEquals(listOf<Pair<String?, Int>>(null to 2), sessions.progressRequests) // GET /readings/progress, nivel base 2
        assertEquals(2, state().progress.completedLessons)
        assertEquals(5, state().progress.badges.size)
        assertEquals(1, state().progress.badges.count { it.unlocked })
        assertNull(state().progressNote)
        assertNull(state().selectedChildId)
    }

    @Test fun adult_readingReadyIsShownWithoutAChildSelection() = runTest(dispatcher) {
        val vm = viewModel(UserRole.ADULTO_INDEPENDIENTE)
        val state = observe(vm)
        vm.refresh()
        assertNull(state().readingForSelection)

        holder.set(ActiveReading(null, "Ana", ReadingSettings(), sampleAdaptation()))

        assertNotNull(state().readingForSelection)
        assertEquals("Cuento", state().readingForSelection!!.adaptation.title)
    }

    @Test fun adult_hasNoPinWallRequirementInTheState() = runTest(dispatcher) {
        val vm = viewModel(UserRole.ADULTO_INDEPENDIENTE, pinConfigured = false)
        val state = observe(vm)
        vm.refresh()
        // El PIN de tutor solo aplica a sesiones con perfil de niño; el adulto lee sin Modo Enfoque.
        assertFalse(state().pinConfigured)
        assertFalse(state().isTutor)
    }

    @Test fun adult_logoutClearsTheReadingInRam() = runTest(dispatcher) {
        val vm = viewModel(UserRole.ADULTO_INDEPENDIENTE)
        observe(vm)
        holder.set(ActiveReading(null, "Ana", ReadingSettings(), sampleAdaptation()))

        vm.logout()

        assertNull(holder.current.value)
    }

    // --- Tutor (paralelo, mismos escenarios) -------------------------------------------------

    @Test fun tutor_loadsChildrenSelectsTheFirstAndLoadsItsProgress() = runTest(dispatcher) {
        children.children = listOf(sampleChild("c1", "Mateo"), sampleChild("c2", "Lucía"))
        sessions.educational = progress(completed = 1)
        val vm = viewModel(UserRole.TUTOR, pinConfigured = true)
        val state = observe(vm)

        vm.refresh()

        assertTrue(state().isTutor)
        assertEquals(listOf("Mateo", "Lucía"), state().children.map { it.name })
        assertEquals("c1", state().selectedChildId)
        assertEquals(listOf<Pair<String?, Int>>("c1" to 3), sessions.progressRequests) // GET /children/c1/progress, nivel del perfil
        assertEquals(1, state().progress.completedLessons)
        assertTrue(state().pinConfigured)
    }

    @Test fun tutor_readingOnlyAppearsForTheSelectedChild() = runTest(dispatcher) {
        children.children = listOf(sampleChild("c1", "Mateo"), sampleChild("c2", "Lucía"))
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        holder.set(ActiveReading("c1", "Mateo", ReadingSettings(), sampleAdaptation()))
        assertNotNull(state().readingForSelection)

        vm.select("c2")

        assertNull(state().readingForSelection)
        assertEquals(listOf("c1", "c2"), sessions.progressRequests.map { it.first })
    }

    @Test fun progressFailureNeverBreaksTheScreen_emptyStateWithLockedBadgesAndANote() = runTest(dispatcher) {
        sessions.progressFails = true
        val vm = viewModel(UserRole.ADULTO_INDEPENDIENTE)
        val state = observe(vm)

        vm.refresh()

        assertFalse(state().isLoading)
        assertEquals(0, state().progress.totalLessons)
        assertEquals(5, state().progress.badges.size)
        assertTrue(state().progress.badges.none { it.unlocked })
        assertNotNull(state().progressNote)
    }

    @Test fun tutorWithoutChildrenShowsAnEmptyProgressWithoutCallingTheServer() = runTest(dispatcher) {
        children.children = emptyList()
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)

        vm.refresh()

        assertTrue(sessions.progressRequests.isEmpty())
        assertEquals(0, state().progress.totalLessons)
    }

    @Test fun ownerKeyIdentifiesTheProfileForTheFluiBotData() = runTest(dispatcher) {
        children.children = listOf(sampleChild("c1", "Mateo"), sampleChild("c2", "Lucía"))
        val tutor = viewModel(UserRole.TUTOR)
        val tutorState = observe(tutor)
        tutor.refresh()
        assertEquals("c1", tutorState().ownerKey)
        tutor.select("c2")
        assertEquals("cada niño tiene su propio taller y saldo", "c2", tutorState().ownerKey)

        val adult = viewModel(UserRole.ADULTO_INDEPENDIENTE)
        val adultState = observe(adult)
        adult.refresh()
        assertEquals("adult:x@example.com", adultState().ownerKey)
    }

    // --- Navegación de 4 apartados -----------------------------------------------------------

    @Test fun sectionsStartOnLecturasAndSelectTabSwitchesThemIndependently() = runTest(dispatcher) {
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        assertEquals(com.fluilet.app.presentation.home.HomeTab.LECTURAS, state().selectedTab)

        com.fluilet.app.presentation.home.HomeTab.entries.forEach { tab ->
            vm.selectTab(tab)
            assertEquals(tab, state().selectedTab)
        }
        vm.selectTab(com.fluilet.app.presentation.home.HomeTab.ESTADISTICAS)
        vm.refresh() // volver de otra pantalla no pierde el apartado
        assertEquals(com.fluilet.app.presentation.home.HomeTab.ESTADISTICAS, state().selectedTab)
    }

    // --- 🎓 Cargar Datos Demo y misiones del servidor -----------------------------------------

    private fun demoChildren() { children.children = listOf(sampleChild("child-1", "Mateo")) }

    @Test fun demoButtonAppliesTheLocalDataAtOnceAndThenReplacesItWithTheServers() = runTest(dispatcher) {
        demoChildren()
        val server = DemoData.progress().copy(totalGems = 715, demoLessons = DemoData.lessons().take(2))
        sessions.seedResult = Result.success(server)
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()

        vm.loadDemoData()

        assertEquals(listOf("child-1"), sessions.seedRequests)
        assertEquals(715, state().progress.totalGems)
        assertEquals(7, state().progress.lessons.size)
        assertEquals(2, state().demoLessons.size)
        assertEquals("Datos demo cargados desde el servidor.", state().demoNote)
        assertFalse(state().isDemoLoading)
    }

    @Test fun withoutTheServerTheLocalDemoFillsEverythingAndSaysSo() = runTest(dispatcher) {
        demoChildren()
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()

        vm.loadDemoData() // el servidor falla (FakeReadingSessionRepository.seedResult por omisión)

        assertEquals(DemoData.BASE_GEMS, state().progress.totalGems)
        assertTrue(state().progress.totalGems >= 260)
        assertEquals(900, state().progress.currentXp)
        assertEquals(7, state().progress.streakDays)
        assertEquals(listOf("Real Madrid", "Biología Marina", "Agujeros Negros"), state().demoLessons.map { it.title })
        assertTrue(state().demoNote!!.contains("locales"))
        assertFalse(state().isDemoLoading)
    }

    @Test fun refreshingKeepsTheLocalDemoWhileTheServerStillHasFewerLessons() = runTest(dispatcher) {
        demoChildren()
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        vm.loadDemoData()

        sessions.educational = progress(completed = 0, total = 0)
        vm.refresh()

        assertEquals(7, state().progress.lessons.size)
        // Cuando el servidor ya tiene la práctica real, manda el servidor.
        sessions.educational = DemoData.progress().copy(totalGems = 999)
        vm.refresh()
        assertEquals(999, state().progress.totalGems)
    }

    @Test fun demoLessonsBelongToTheProfileTheyWereLoadedFor() = runTest(dispatcher) {
        children.children = listOf(sampleChild("child-1", "Mateo"), sampleChild("child-2", "Lucía"))
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        vm.loadDemoData()
        assertEquals(3, state().demoLessons.size)

        vm.select("child-2")
        assertTrue(state().demoLessons.isEmpty())
        vm.select("child-1")
        assertEquals(3, state().demoLessons.size)
    }

    @Test fun aTutorWithoutProfilesIsAskedToCreateOneFirst() = runTest(dispatcher) {
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        vm.loadDemoData()
        assertTrue(state().errorMessage!!.contains("perfil"))
        assertTrue(sessions.seedRequests.isEmpty())
        assertTrue(state().demoLessons.isEmpty())
    }

    @Test fun anIndependentAdultGetsTheLocalDemoWithoutCallingTheTutorOnlyEndpoint() = runTest(dispatcher) {
        val vm = viewModel(UserRole.ADULTO_INDEPENDIENTE)
        val state = observe(vm)
        vm.refresh()
        vm.loadDemoData()
        assertTrue(sessions.seedRequests.isEmpty())
        assertEquals(7, state().progress.lessons.size)
        assertEquals(3, state().demoLessons.size)
    }

    @Test fun openingADemoLessonLeavesItReadyWithTheProfilesSettings() = runTest(dispatcher) {
        demoChildren()
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        vm.loadDemoData()

        vm.openDemoLesson(state().demoLessons.first())

        val reading = holder.current.value!!
        assertEquals("Real Madrid", reading.adaptation.title)
        assertEquals("child-1", reading.childId)
        assertEquals("Mateo", reading.readerName)
        assertEquals(20f, reading.settings.fontSizeSp, 0.0001f)
        assertNotNull(reading.adaptation.originalText)
        assertNotNull(state().readingForSelection)
    }

    @Test fun confirmingAServerMissionRefreshesTheGemCounterFromTheBackendsSummary() = runTest(dispatcher) {
        demoChildren()
        val mission = com.fluilet.app.domain.gamification.DailyMission("mission_quiz_master", "Mente Brillante", "d", 1, 1, 30, fromServer = true, claimed = true)
        sessions.claimResult = Result.success(DemoData.progress().copy(totalGems = 755, serverMissions = listOf(mission)))
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()

        vm.claimServerMission("mission_quiz_master")

        assertEquals(listOf("child-1" to "mission_quiz_master"), sessions.claimRequests)
        assertEquals(755, state().progress.totalGems)
        assertTrue(state().progress.serverMissions!!.single().claimed)
    }

    @Test fun aFailedClaimShowsTheErrorAndKeepsTheProgress() = runTest(dispatcher) {
        demoChildren()
        sessions.educational = progress()
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        vm.claimServerMission("mission_x")
        assertTrue(state().errorMessage!!.contains("conexión"))
        assertEquals(2, state().progress.completedLessons)
    }
}
