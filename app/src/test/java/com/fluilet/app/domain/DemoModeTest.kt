package com.fluilet.app.domain

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.gamification.DailyMissions
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.TechnicalSheet
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.usecase.DemoAccounts
import com.fluilet.app.domain.usecase.DemoConfig
import com.fluilet.app.domain.usecase.QuickAccess
import com.fluilet.app.domain.usecase.QuickAccessUseCase
import com.fluilet.app.presentation.auth.QuickAccessViewModel
import com.fluilet.app.testutil.FakeTutorPin
import io.mockk.coEvery
import io.mockk.coVerify
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

/** Accesos rápidos de la demostración (tutor y maestro) y el respaldo local de "🎓 Cargar Datos Demo". */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoModeTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val auth = mockk<AuthRepository>()
    private val pin = FakeTutorPin(configured = false)

    private fun session(role: UserRole, email: String) = AuthSession("a", "r", role, email)
    private fun useCase(config: DemoConfig = DemoConfig(true)) = QuickAccessUseCase(config, auth, pin)

    // --- Credenciales públicas del backend (docs/demo-accounts.md) -------------------------------

    @Test fun demoCredentialsAreTheOnesPublishedByTheBackend() {
        assertEquals("tutor@fluilet.com", DemoAccounts.TUTOR_EMAIL)
        assertEquals("Tutor2026!", DemoAccounts.TUTOR_PASSWORD)
        assertEquals("1234", DemoAccounts.TUTOR_PIN)
        assertEquals("admin@fluilet.com", DemoAccounts.ADMIN_EMAIL)
        assertEquals("Master2026!", DemoAccounts.ADMIN_PASSWORD)
    }

    @Test fun theTutorQuickAccessIsTheNormalLoginAndLeavesThePinReady() = runTest(dispatcher) {
        coEvery { auth.login(DemoAccounts.TUTOR_EMAIL, DemoAccounts.TUTOR_PASSWORD) } returns
            Result.success(session(UserRole.TUTOR, DemoAccounts.TUTOR_EMAIL))

        val result = useCase()(QuickAccess.TUTOR)

        assertEquals(UserRole.TUTOR, result.getOrNull())
        assertTrue(pin.isConfigured())
        assertEquals("1234", pin.lastDemoPin)
        coVerify(exactly = 1) { auth.login(any(), any()) }
        coVerify(exactly = 0) { auth.register(any()) } // no se crean cuentas: las provisiona el backend
    }

    @Test fun theMasterQuickAccessLogsInWithTheAdminAccountAndTouchesNoPin() = runTest(dispatcher) {
        coEvery { auth.login(DemoAccounts.ADMIN_EMAIL, DemoAccounts.ADMIN_PASSWORD) } returns
            Result.success(session(UserRole.MASTER_ADMIN, DemoAccounts.ADMIN_EMAIL))

        val result = useCase()(QuickAccess.MASTER)

        assertEquals(UserRole.MASTER_ADMIN, result.getOrNull())
        assertFalse(pin.isConfigured())
    }

    @Test fun aFailedLoginIsReportedAndNoPinIsStored() = runTest(dispatcher) {
        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.Unauthorized())
        assertTrue(useCase()(QuickAccess.TUTOR).exceptionOrNull() is AppError.Unauthorized)
        assertFalse(pin.isConfigured())
    }

    @Test fun quickAccessIsDisabledWithoutTheFlagAndTheButtonsStayHidden() = runTest(dispatcher) {
        assertTrue(useCase(DemoConfig.DISABLED)(QuickAccess.MASTER).exceptionOrNull() is AppError.Unsupported)
        assertFalse(QuickAccessViewModel(DemoConfig.DISABLED, useCase(DemoConfig.DISABLED)).state.value.enabled)
        coVerify(exactly = 0) { auth.login(any(), any()) }
    }

    @Test fun theQuickAccessButtonsEnterInOneTapAndSurfaceErrors() = runTest(dispatcher) {
        coEvery { auth.login(any(), any()) } returns Result.success(session(UserRole.TUTOR, DemoAccounts.TUTOR_EMAIL))
        val vm = QuickAccessViewModel(DemoConfig(true), useCase())
        assertTrue(vm.state.value.enabled)

        vm.enter(QuickAccess.TUTOR)
        assertFalse(vm.state.value.isLoading)
        assertEquals(null, vm.state.value.errorMessage)

        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.NoConnection())
        vm.enter(QuickAccess.MASTER)
        assertTrue(vm.state.value.errorMessage!!.contains("conexión"))
        coVerify(exactly = 1) { auth.login(DemoAccounts.ADMIN_EMAIL, DemoAccounts.ADMIN_PASSWORD) }
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
