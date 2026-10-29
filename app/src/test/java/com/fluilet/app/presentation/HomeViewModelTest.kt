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
import com.fluilet.app.domain.usecase.FetchAdaptationUseCase
import com.fluilet.app.domain.usecase.GetChildLibraryUseCase
import com.fluilet.app.domain.usecase.GetModelAuditUseCase
import com.fluilet.app.testutil.FakeAdaptationRepository
import com.fluilet.app.testutil.FakeModelInfoRepository
import com.fluilet.app.presentation.home.HomeUiState
import com.fluilet.app.presentation.home.HomeViewModel
import com.fluilet.app.testutil.FakeChildRepository
import com.fluilet.app.testutil.FakeReadingSessionRepository
import com.fluilet.app.testutil.FakeSecureStore
import com.fluilet.app.testutil.FakeTutorPin
import com.fluilet.app.testutil.sampleAdaptation
import com.fluilet.app.testutil.sampleChild
import com.fluilet.app.domain.model.NlpLevel
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
    private val adaptations = FakeAdaptationRepository()
    private val modelInfo = FakeModelInfoRepository()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun progress(completed: Int = 2, total: Int = 3) = EducationalProgress(
        totalLessons = total, completedLessons = completed, incompleteLessons = total - completed,
        completionRatePercent = 100.0 * completed / total, totalReadingSeconds = 900,
        averageComprehension = 75.0, recommendedLevel = 3, lessons = emptyList(),
        badges = BadgeCatalog.all.mapIndexed { i, b -> com.fluilet.app.domain.model.EducationalBadge(b.code, b.title, b.description, i == 0) }
    )

    private fun viewModel(role: UserRole, pinConfigured: Boolean = false, email: String = "x@example.com"): HomeViewModel {
        val session = SessionManager(FakeSecureStore()).apply {
            restore()
            save(AuthSession("access", "refresh", role, email))
        }
        coEvery { auth.currentUser() } returns Result.success(User("1", "x@example.com", "Ana Pérez", role, true, false))
        coEvery { auth.logout() } returns Unit
        return HomeViewModel(
            session, holder, auth,
            GetChildrenUseCase(children), SetNlpLevelUseCase(children), DeleteChildUseCase(children),
            GetEducationalProgressUseCase(sessions), IsTutorPinConfiguredUseCase(FakeTutorPin(pinConfigured)),
            LogoutUseCase(auth), holder, SeedDemoDataUseCase(sessions), ClaimDailyMissionUseCase(sessions),
            GetChildLibraryUseCase(adaptations), FetchAdaptationUseCase(adaptations), GetModelAuditUseCase(modelInfo)
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
        // GET /children/c1/progress (nivel del perfil) es la primera consulta; después se piden las tarjetas de los demás niños.
        assertEquals("c1" to 3, sessions.progressRequests.first())
        assertEquals(setOf("c1", "c2"), sessions.progressRequests.map { it.first }.toSet())
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
        assertEquals(setOf("c1", "c2"), sessions.progressRequests.map { it.first }.toSet())
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
        sessions.claimResult = Result.failure(com.fluilet.app.core.network.AppError.Forbidden())
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        vm.claimServerMission("mission_x")
        assertTrue(state().errorMessage!!.contains("permisos"))
        assertEquals(2, state().progress.completedLessons)
    }

    // --- Claim: servidor primero, respaldo local solo si el backend no está disponible ------------

    @Test fun aClaimThatReachesTheServerNeverTriggersTheLocalFallback() = runTest(dispatcher) {
        demoChildren()
        sessions.claimResult = Result.success(DemoData.progress().copy(totalGems = 740))
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        var fallback = 0
        vm.claimServerMission("mission_read_lesson") { fallback++ }
        assertEquals(0, fallback)
        assertEquals(740, state().progress.totalGems)
    }

    @Test fun whenTheBackendIsUnavailableTheLocalFallbackRunsWithoutAnErrorBanner() = runTest(dispatcher) {
        demoChildren()
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        var fallback = 0
        vm.claimServerMission("mission_read_lesson") { fallback++ } // claimResult por omisión: sin conexión
        assertEquals(1, fallback)
        assertEquals(null, state().errorMessage)
    }

    @Test fun aRealServerRejectionIsShownAndDoesNotUseTheFallback() = runTest(dispatcher) {
        demoChildren()
        sessions.claimResult = Result.failure(com.fluilet.app.core.network.AppError.Conflict("La misión aún no está completa."))
        val vm = viewModel(UserRole.TUTOR)
        val state = observe(vm)
        vm.refresh()
        var fallback = 0
        vm.claimServerMission("mission_quiz_master") { fallback++ }
        assertEquals(0, fallback)
        assertEquals("La misión aún no está completa.", state().errorMessage)
    }

    // --- Cuenta Tutor: los 4 niños / 4 fases -------------------------------------------------------------

    private fun kid(id: String, name: String, level: Int, group: String, years: Int) = sampleChild(id, name).copy(
        nlpLevel = com.fluilet.app.domain.model.NlpLevel.fromValue(level), ageGroup = group,
        birthDate = java.time.LocalDate.now().minusYears(years.toLong()).minusMonths(2)
    )

    /** Resumen del servidor de cada niño: gemas, XP y nivel de la tabla de `docs/demo-accounts.md`. */
    private fun withServerKids() {
        children.children = listOf(
            kid("c-sofia", "Sofía", 1, "6-8", 7), kid("c-mateo", "Mateo", 2, "9-11", 10),
            kid("c-diego", "Diego", 4, "9-11", 11), kid("c-valentina", "Valentina", 5, "12+", 13)
        )
        sessions.educationalByChild["c-sofia"] = DemoData.phaseProgress(1, 45, 40, 1, 1)
        sessions.educationalByChild["c-mateo"] = DemoData.phaseProgress(3, 160, 190, 3, 2)
        sessions.educationalByChild["c-diego"] = DemoData.phaseProgress(5, 340, 460, 6, 4)
        sessions.educationalByChild["c-valentina"] = DemoData.phaseProgress(6, 900, 980, 12, 5)
    }

    @Test fun theTutorSeesACardPerChildWithAgeLevelStageAndGems() = runTest(dispatcher) {
        withServerKids()
        val vm = viewModel(UserRole.TUTOR, email = "tutor@fluilet.com")
        val state = observe(vm)
        vm.refresh()

        assertEquals(listOf("Sofía", "Mateo", "Diego", "Valentina"), state().children.map { it.name })
        val s = state().childSummaries
        assertEquals(listOf(45, 160, 340, 900), listOf("c-sofia", "c-mateo", "c-diego", "c-valentina").map { s.getValue(it).gems })
        assertEquals(listOf(1, 2, 3, 4), listOf("c-sofia", "c-mateo", "c-diego", "c-valentina").map { s.getValue(it).stage })
        assertEquals(listOf(1, 2, 4, 5), listOf("c-sofia", "c-mateo", "c-diego", "c-valentina").map { s.getValue(it).level })
        assertEquals("Núcleo de Energía", s.getValue("c-sofia").stageName)
        assertEquals("Sabio Cuántico", s.getValue("c-valentina").stageName)
        assertEquals(listOf("6-8", "9-11", "9-11", "12+"), state().children.map { it.ageGroup })
        assertEquals(listOf(7, 10, 11, 13), state().children.map { it.ageYears() })
        assertFalse(state().offlineDemo)
    }

    @Test fun tappingEachChildSwitchesTheDataOfTheFourSections() = runTest(dispatcher) {
        withServerKids()
        val vm = viewModel(UserRole.TUTOR, email = "tutor@fluilet.com")
        val state = observe(vm)
        vm.refresh()
        assertEquals("c-sofia", state().selectedChildId)
        assertEquals(45, state().progress.totalGems)

        listOf("c-mateo" to (160 to 190), "c-diego" to (340 to 460), "c-valentina" to (900 to 980), "c-sofia" to (45 to 40)).forEach { (id, v) ->
            vm.select(id)
            assertEquals(id, state().selectedChildId)
            assertEquals("gemas de $id", v.first, state().progress.totalGems)
            assertEquals("XP de $id", v.second, state().progress.currentXp)
            assertEquals(id, state().ownerKey) // FluiBot, Colección y Estadísticas se enlazan por perfil
        }
        assertEquals("la lectura lista y el selector siguen al niño elegido", null, state().readingForSelection)
    }

    @Test fun theSelectedCardFollowsLiveProgressChanges() = runTest(dispatcher) {
        withServerKids()
        val vm = viewModel(UserRole.TUTOR, email = "tutor@fluilet.com")
        val state = observe(vm)
        vm.refresh()
        vm.select("c-mateo")

        vm.loadDemoData() // 620 💎 / 900 XP para Mateo

        assertEquals(DemoData.BASE_GEMS, state().childSummaries.getValue("c-mateo").gems)
        assertEquals(4, state().childSummaries.getValue("c-mateo").stage)
        assertEquals("los demás no cambian", 45, state().childSummaries.getValue("c-sofia").gems)
    }

    @Test fun withoutTheServerTheDemoTutorKeepsTheSameFourChildrenLocally() = runTest(dispatcher) {
        children.listError = com.fluilet.app.core.network.AppError.NoConnection()
        val vm = viewModel(UserRole.TUTOR, email = "tutor@fluilet.com")
        val state = observe(vm)
        vm.refresh()

        assertTrue(state().offlineDemo)
        assertEquals(listOf("Sofía", "Mateo", "Diego", "Valentina"), state().children.map { it.name })
        val ids = state().children.map { it.id }
        assertEquals(listOf(45, 160, 340, 900), ids.map { state().childSummaries.getValue(it).gems })
        assertEquals(listOf(1, 2, 3, 4), ids.map { state().childSummaries.getValue(it).stage })
        assertEquals(listOf(1, 2, 4, 5), ids.map { state().childSummaries.getValue(it).level })
        assertEquals(ids.first(), state().selectedChildId)
        assertNull(state().errorMessage)

        vm.select("demo-valentina")
        assertEquals(900, state().progress.totalGems)
        assertEquals(980, state().progress.currentXp)
        assertEquals(12, state().progress.streakDays)
        vm.select("demo-sofia")
        assertEquals(40, state().progress.currentXp)
        assertEquals(1, state().progress.lessons.size)
    }

    @Test fun aNormalTutorWithoutServerGetsTheErrorNotTheDemoChildren() = runTest(dispatcher) {
        children.listError = com.fluilet.app.core.network.AppError.NoConnection()
        val vm = viewModel(UserRole.TUTOR, email = "mama@example.com")
        val state = observe(vm)
        vm.refresh()
        assertFalse(state().offlineDemo)
        assertTrue(state().children.isEmpty())
        assertTrue(state().errorMessage!!.contains("conexión"))
    }

    @Test fun theLocalFallbackMatchesTheBackendsPhaseTable() {
        val p = com.fluilet.app.domain.demo.DemoChildren.phases
        assertEquals(listOf("Sofía", "Mateo", "Diego", "Valentina"), p.map { it.name })
        assertEquals(listOf(7, 10, 11, 13), p.map { it.ageYears })
        assertEquals(listOf("6-8", "9-11", "9-11", "12+"), p.map { it.ageGroup })
        assertEquals(listOf(1, 2, 4, 5), p.map { it.level })
        assertEquals(listOf(45, 160, 340, 900), p.map { it.gems })
        assertEquals(listOf(40, 190, 460, 980), p.map { it.xp })
        assertEquals(listOf(1, 3, 6, 12), p.map { it.streakDays })
        assertEquals(listOf(1, 3, 5, 6), p.map { it.lessons })
        assertEquals(
            listOf(1, 2, 3, 4),
            p.map { com.fluilet.app.domain.gamification.FluiBotStage.forXp(it.xp).number }
        )
    }

    // --- Biblioteca del niño (sus lecciones demo) y auditoría del modelo en 📊 Estadísticas -------------------

    private val diegoLessons = listOf(
        com.fluilet.app.domain.model.LibraryItem("doc-negros", "ciencia_espacio", 4, 56.44, 61.84),
        com.fluilet.app.domain.model.LibraryItem("doc-madrid", "deportes", 3, 66.75, 68.12)
    )

    @Test fun selectingAChildLoadsItsLibraryAndSwitchingChildrenNeverShowsAnotherOnesLessons() = runTest(dispatcher) {
        children.children = listOf(sampleChild("diego", "Diego"), sampleChild("sofia", "Sofía"))
        adaptations.libraryResult = Result.success(diegoLessons)
        val vm = viewModel(UserRole.TUTOR, pinConfigured = true, email = "tutor@fluilet.com")
        val state = observe(vm)

        vm.refresh()
        assertEquals(listOf("doc-negros", "doc-madrid"), state().library.map { it.documentId })
        assertEquals("diego", adaptations.libraryRequests.last())

        adaptations.libraryResult = Result.success(emptyList())
        vm.select("sofia")
        assertTrue(state().library.isEmpty())
        assertEquals("sofia", adaptations.libraryRequests.last())
    }

    @Test fun openingALibraryLessonFetchesItCompleteAndLeavesItReadyForTheReader() = runTest(dispatcher) {
        children.children = listOf(sampleChild("diego", "Diego"))
        adaptations.libraryResult = Result.success(diegoLessons)
        val full = sampleAdaptation(2, documentId = "doc-negros", level = 4).copy(
            title = "Agujeros negros", originalText = "Los agujeros negros son regiones del espacio.",
            modelVersion = "context-ranker-es-v2.3", engineVersion = "pedagogical-es-v2.3"
        )
        adaptations.fetchResult = Result.success(full)
        val vm = viewModel(UserRole.TUTOR, pinConfigured = true, email = "tutor@fluilet.com")
        val state = observe(vm)
        vm.refresh()

        var ready = 0
        vm.openLibraryLesson(state().library.first()) { ready++ }

        assertEquals(listOf("doc-negros"), adaptations.fetchRequests)
        assertEquals(1, ready)
        assertNull(state().openingLessonId)
        val reading = holder.current.value!!
        assertEquals("diego", reading.childId)
        assertEquals("Agujeros negros", reading.adaptation.title)
        assertTrue(reading.adaptation.isRetrainedModel)
    }

    @Test fun aLessonThatCannotBeOpenedShowsTheErrorAndDoesNotNavigate() = runTest(dispatcher) {
        children.children = listOf(sampleChild("diego", "Diego"))
        adaptations.libraryResult = Result.success(diegoLessons)
        adaptations.fetchResult = Result.failure(com.fluilet.app.core.network.AppError.NotFound())
        val vm = viewModel(UserRole.TUTOR, pinConfigured = true)
        val state = observe(vm)
        vm.refresh()

        var ready = 0
        vm.openLibraryLesson(state().library.first()) { ready++ }

        assertEquals(0, ready)
        assertNotNull(state().errorMessage)
        assertNull(state().openingLessonId)
        assertNull(holder.current.value)
    }

    @Test fun withoutServerTheDemoTutorKeepsItsFourChildrenAndNoLibraryIsRequested() = runTest(dispatcher) {
        children.listError = com.fluilet.app.core.network.AppError.NoConnection()
        val vm = viewModel(UserRole.TUTOR, email = "tutor@fluilet.com")
        val state = observe(vm)
        vm.refresh()
        assertEquals(listOf("Sofía", "Mateo", "Diego", "Valentina"), state().children.map { it.name })
        assertTrue(state().offlineDemo)
        assertTrue(state().library.isEmpty())
        assertTrue(adaptations.libraryRequests.isEmpty())
    }

    @Test fun theStatisticsTabGetsTheRetrainedModelAuditOnce() = runTest(dispatcher) {
        val vm = viewModel(UserRole.ADULTO_INDEPENDIENTE)
        val state = observe(vm)
        vm.refresh()
        vm.refresh()
        val audit = state().modelAudit!!
        assertTrue(audit.acceptanceCriteriaMet)
        assertEquals("Catálogo 31/31 · EASIER 6/6 · Controlados 6/6 · Demos 15/15", audit.bandsSummary)
        assertEquals(1, modelInfo.calls)
    }

    // --- Cambio de nivel del niño con confirmación ----------------------------------------------------------

    private fun TestScope.tutorWithSofia(): Pair<HomeViewModel, () -> HomeUiState> {
        children.children = listOf(kid("sofia", "Sofía", 1, "6-8", 7), kid("mateo", "Mateo", 2, "9-11", 10))
        val vm = viewModel(UserRole.TUTOR, pinConfigured = true)
        val state = observe(vm)
        vm.refresh()
        vm.select("sofia")
        return vm to state
    }

    @Test fun movingTheLevelSliderOnlyProposesTheChangeUntilTheTutorConfirms() = runTest(dispatcher) {
        val (vm, state) = tutorWithSofia()

        vm.requestNlpLevel(NlpLevel.NIVEL_3)

        assertEquals(NlpLevel.NIVEL_3, state().pendingNlpLevel)
        assertEquals("sin PATCH antes de confirmar", emptyList<Pair<String, NlpLevel>>(), children.levelUpdates)
        assertEquals(NlpLevel.NIVEL_1, state().selectedChild!!.nlpLevel)

        vm.confirmNlpLevel()

        assertEquals(listOf("sofia" to NlpLevel.NIVEL_3), children.levelUpdates)
        assertEquals(NlpLevel.NIVEL_3, state().selectedChild!!.nlpLevel)
        assertNull(state().pendingNlpLevel)
    }

    @Test fun cancellingKeepsTheLevelAndNeverCallsTheServer() = runTest(dispatcher) {
        val (vm, state) = tutorWithSofia()

        vm.requestNlpLevel(NlpLevel.NIVEL_4)
        vm.cancelNlpLevel()

        assertNull("el deslizador vuelve al nivel actual", state().pendingNlpLevel)
        assertEquals(NlpLevel.NIVEL_1, state().selectedChild!!.nlpLevel)
        assertTrue(children.levelUpdates.isEmpty())
        vm.confirmNlpLevel() // sin propuesta pendiente no hace nada
        assertTrue(children.levelUpdates.isEmpty())
    }

    @Test fun theSameLevelOrAnotherChildLeavesNothingPending() = runTest(dispatcher) {
        val (vm, state) = tutorWithSofia()

        vm.requestNlpLevel(NlpLevel.NIVEL_1)
        assertNull("mismo nivel: no hay nada que confirmar", state().pendingNlpLevel)

        vm.requestNlpLevel(NlpLevel.NIVEL_5)
        vm.select("mateo")
        assertNull("al cambiar de niño se descarta la propuesta", state().pendingNlpLevel)
        assertTrue(children.levelUpdates.isEmpty())
    }

    @Test fun aServerErrorAfterConfirmingRestoresThePreviousLevel() = runTest(dispatcher) {
        val (vm, state) = tutorWithSofia()
        children.levelError = com.fluilet.app.core.network.AppError.NoConnection()

        vm.requestNlpLevel(NlpLevel.NIVEL_2)
        vm.confirmNlpLevel()

        assertEquals(listOf("sofia" to NlpLevel.NIVEL_2), children.levelUpdates)
        assertEquals(NlpLevel.NIVEL_1, state().selectedChild!!.nlpLevel)
        assertTrue(state().errorMessage!!.contains("conexión"))
    }
}
