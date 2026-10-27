package com.fluilet.app.domain

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.gamification.DailyMissions
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.RegistrationData
import com.fluilet.app.domain.model.TechnicalSheet
import com.fluilet.app.domain.model.User
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.repository.ChildRepository
import com.fluilet.app.domain.usecase.DemoConfig
import com.fluilet.app.domain.usecase.EnterDemoModeUseCase
import com.fluilet.app.presentation.auth.DemoModeViewModel
import com.fluilet.app.testutil.sampleChild
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** "🚀 Entrar en Modo Demo" y el respaldo local de "🎓 Cargar Datos Demo". */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoModeTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val config = DemoConfig("profesor.demo@fluilet.app", "FluiletDemo#2026")
    private val auth = mockk<AuthRepository>()
    private val children = mockk<ChildRepository>()
    private val session = AuthSession("a", "r", UserRole.TUTOR, config.email)
    private val user = User("1", config.email, "Profesor Demo", UserRole.TUTOR, true, false)

    private fun useCase(cfg: DemoConfig = config) = EnterDemoModeUseCase(cfg, auth, children)

    // --- Modo Demo (un toque) ---------------------------------------------------------------------

    @Test fun existingDemoAccountJustLogsInAndKeepsItsProfiles() = runTest(dispatcher) {
        coEvery { auth.login(config.email, config.password) } returns Result.success(session)
        coEvery { children.list() } returns Result.success(listOf(sampleChild()))

        assertTrue(useCase()().isSuccess)

        coVerify(exactly = 0) { auth.register(any()) }
        coVerify(exactly = 0) { children.create(any()) }
    }

    @Test fun firstTimeCreatesTheTutorAccountLogsInAndCreatesAnEightYearOldProfile() = runTest(dispatcher) {
        coEvery { auth.login(config.email, config.password) } returnsMany
            listOf(Result.failure(AppError.Unauthorized()), Result.success(session))
        coEvery { auth.register(any()) } returns Result.success(user)
        coEvery { children.list() } returns Result.success(emptyList())
        val draft = io.mockk.slot<ChildDraft>()
        coEvery { children.create(capture(draft)) } returns Result.success(sampleChild())

        val today = LocalDate.of(2026, 10, 26)
        assertTrue(useCase()(today).isSuccess)

        coVerifyOrder {
            auth.login(config.email, config.password)
            auth.register(RegistrationData(config.email, "Profesor Demo", config.password, UserRole.TUTOR))
            auth.login(config.email, config.password)
            children.list()
            children.create(any())
        }
        assertEquals("Mateo", draft.captured.name)
        assertEquals(8, java.time.Period.between(draft.captured.birthDate, today).years)
    }

    @Test fun aFailedProfileCreationNeverBlocksTheLogin() = runTest(dispatcher) {
        coEvery { auth.login(any(), any()) } returns Result.success(session)
        coEvery { children.list() } returns Result.success(emptyList())
        coEvery { children.create(any()) } returns Result.failure(AppError.NoConnection())
        assertTrue(useCase()().isSuccess)
    }

    @Test fun aRegistrationConflictOrNetworkErrorIsReportedAndNothingElseRuns() = runTest(dispatcher) {
        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.Unauthorized())
        coEvery { auth.register(any()) } returns Result.failure(AppError.Conflict("Ese correo ya existe."))
        val result = useCase()()
        assertTrue(result.exceptionOrNull() is AppError.Conflict)
        coVerify(exactly = 0) { children.list() }

        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.NoConnection())
        assertTrue(useCase()().exceptionOrNull() is AppError.NoConnection)
    }

    @Test fun demoIsDisabledWithoutCredentialsAndTheButtonStaysHidden() = runTest(dispatcher) {
        assertFalse(DemoConfig.DISABLED.enabled)
        assertTrue(useCase(DemoConfig.DISABLED)().exceptionOrNull() is AppError.Unsupported)
        assertFalse(DemoModeViewModel(DemoConfig.DISABLED, useCase(DemoConfig.DISABLED)).state.value.enabled)
        coVerify(exactly = 0) { auth.login(any(), any()) }
    }

    @Test fun theDemoButtonLogsInInOneTapAndSurfacesErrors() = runTest(dispatcher) {
        coEvery { auth.login(any(), any()) } returns Result.success(session)
        coEvery { children.list() } returns Result.success(listOf(sampleChild()))
        val vm = DemoModeViewModel(config, useCase())
        assertTrue(vm.state.value.enabled)
        vm.enter()
        assertFalse(vm.state.value.isLoading)
        assertEquals(null, vm.state.value.errorMessage)
        coVerify(exactly = 1) { auth.login(config.email, config.password) }

        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.NoConnection())
        vm.enter()
        assertTrue(vm.state.value.errorMessage!!.contains("conexión"))
    }

    // --- Respaldo local de los datos demo --------------------------------------------------------

    private val today = LocalDate.of(2026, 10, 26)

    @Test fun localDemoMatchesTheServersSevenDayScenario() {
        val p = DemoData.progress(today, ZoneOffset.UTC)
        assertEquals(7, p.lessons.size)
        assertTrue(p.demoData)
        assertEquals(7, p.streakDays)
        assertEquals(900, p.currentXp)
        assertEquals(FluiBotStage.SABIO, FluiBotStage.forXp(p.currentXp))
        assertTrue("260+ 💎 para comprar accesorios y los 5 escenarios", p.totalGems >= 260)
        assertEquals(listOf(1, 2, 3, 3, 4, 4, 4), p.lessons.map { it.level })
        assertEquals(listOf(65, 75, 80, 85, 90, 100, 100), p.lessons.map { it.comprehensionScore })
        assertTrue(p.badges.all { it.unlocked })
        assertEquals(6, p.cards.size)
    }

    @Test fun localDemoCoversSevenConsecutiveDaysEndingToday() {
        val days = DemoData.progress(today, ZoneOffset.UTC).lessons.map { it.recordedAt!!.atZone(ZoneOffset.UTC).toLocalDate() }
        assertEquals((6L downTo 0L).map { today.minusDays(it) }, days)
    }

    @Test fun localDemoFillsTheThreeChartsWithRealisticScores() {
        val lessons = DemoData.progress(today, ZoneOffset.UTC).lessons
        assertTrue(lessons.all { it.originalScore != null && it.adaptedScore != null })
        assertTrue("las barras comparativas muestran mejora en todas las lecciones", lessons.all { it.readabilityJump!! > 0 })
        assertEquals(100.0, DemoData.progress(today, ZoneOffset.UTC).completionRatePercent, 0.0)
    }

    @Test fun todaysDemoMissionsAreClaimableLiveForNinetyFiveMoreGems() {
        val p = DemoData.progress(today, ZoneOffset.UTC)
        val missions = DailyMissions.compute(p.lessons, today, ZoneOffset.UTC)
        assertTrue(missions.all { it.completed })
        assertEquals(95, missions.sumOf { it.rewardGems })
        assertEquals(715, p.totalGems + missions.sumOf { it.rewardGems }) // 715 = valor del seed del servidor
    }

    @Test fun demoLessonsIncludeRealMadridBiologiaMarinaAndAgujerosNegrosWithEverything() {
        val lessons = DemoData.lessons()
        assertEquals(listOf("Real Madrid", "Biología Marina", "Agujeros Negros"), lessons.map { it.title })
        lessons.forEach { l ->
            assertTrue("${l.title}: original", !l.originalText.isNullOrBlank())
            assertTrue("${l.title}: adaptado", l.chunks.isNotEmpty())
            assertTrue("${l.title}: dato curioso", !l.funFact.isNullOrBlank())
            assertTrue("${l.title}: glosario", l.keyVocabulary.size >= 3)
            assertEquals("${l.title}: mini-reto", 2, l.questions.size)
            assertNotNull(l.category)
            assertTrue(l.questions.all { it.hint != null && it.explanation != null })
        }
        assertEquals(listOf("deportes", "animales_naturaleza", "ciencia_espacio"), lessons.map { it.category })
    }

    @Test fun demoLessonsHaveAFichaTecnicaWithRealMeasuredMetrics() {
        DemoData.lessons().forEach { l ->
            val sheet = TechnicalSheet.of(l)
            assertTrue("${l.title}: palabras", sheet.originalWords > 0 && sheet.adaptedWords > 0)
            assertTrue("${l.title}: oraciones", sheet.originalWordsPerSentence > 0 && sheet.adaptedWordsPerSentence > 0)
            assertTrue("${l.title}: Szigriszt", sheet.originalScore != 0.0 && sheet.adaptedScore != 0.0)
            assertTrue("${l.title}: oraciones más cortas o iguales", sheet.adaptedWordsPerSentence <= sheet.originalWordsPerSentence)
            assertTrue("${l.title}: legibilidad no empeora (${sheet.originalScore} -> ${sheet.adaptedScore})", sheet.adaptedScore >= sheet.originalScore)
        }
    }
}
