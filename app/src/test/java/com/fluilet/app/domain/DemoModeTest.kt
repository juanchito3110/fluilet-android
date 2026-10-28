package com.fluilet.app.domain

import com.fluilet.app.BuildConfig
import com.fluilet.app.R
import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.gamification.DailyMissions
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.TechnicalSheet
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.usecase.DemoAccounts
import com.fluilet.app.domain.usecase.LoginUseCase
import com.fluilet.app.presentation.auth.LoginViewModel
import com.fluilet.app.presentation.navigation.Routes
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Login de producción sin atajos: las cuentas de la demostración se escriben a mano en el formulario normal.
 * Además, el respaldo local de "🎓 Cargar Datos Demo".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoModeTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val auth = mockk<AuthRepository>()
    private val pin = FakeTutorPin(configured = false)

    private fun session(role: UserRole, email: String) = AuthSession("a", "r", role, email)

    /** Lo que el usuario escribe a mano (credenciales públicas del backend, `docs/demo-accounts.md`). */
    private val tutorPassword = "Tutor2026!"
    private val adminPassword = "Master2026!"

    // --- Pantalla de acceso de producción ----------------------------------------------------------------

    @Test fun theLoginScreenHasNoQuickAccessButtonsFlagsOrStoredPasswords() {
        assertTrue("sin textos de acceso rápido", R.string::class.java.fields.none { it.name.startsWith("auth_quick") })
        assertTrue("sin bandera de build para la demo", BuildConfig::class.java.fields.none { it.name == "DEMO_ACCOUNTS_ENABLED" })
        assertTrue(
            "sin ViewModel ni caso de uso de acceso rápido",
            listOf("com.fluilet.app.presentation.auth.QuickAccessViewModel", "com.fluilet.app.domain.usecase.QuickAccessUseCase")
                .none { runCatching { Class.forName(it) }.isSuccess }
        )
        assertTrue(
            "la app no guarda las contraseñas de las cuentas demo",
            DemoAccounts::class.java.declaredFields.none { it.name.contains("PASSWORD") }
        )
    }

    @Test fun demoAccountsOnlyKnowTheirEmailsAndThePublicTutorPin() {
        assertEquals("tutor@fluilet.com", DemoAccounts.TUTOR_EMAIL)
        assertEquals("admin@fluilet.com", DemoAccounts.ADMIN_EMAIL)
        assertEquals("1234", DemoAccounts.TUTOR_PIN)
        assertTrue(DemoAccounts.isDemoTutor("  Tutor@Fluilet.com "))
        assertFalse(DemoAccounts.isDemoTutor("admin@fluilet.com"))
        assertFalse(DemoAccounts.isDemoTutor(null))
    }

    // --- Login manual de las cuentas demo ---------------------------------------------------------------------

    @Test fun typingTheTutorAccountLogsInNormallyAndLeavesItsPinReady() = runTest(dispatcher) {
        coEvery { auth.login("tutor@fluilet.com", tutorPassword) } returns Result.success(session(UserRole.TUTOR, "tutor@fluilet.com"))
        val vm = LoginViewModel(LoginUseCase(auth, pin))

        vm.onEmail("tutor@fluilet.com")
        vm.onPassword(tutorPassword)
        vm.submit()

        coVerify(exactly = 1) { auth.login("tutor@fluilet.com", tutorPassword) }
        coVerify(exactly = 0) { auth.register(any()) } // no se crean cuentas: las provisiona el backend
        assertNull(vm.state.value.errorMessage)
        assertEquals("", vm.state.value.password)
        assertTrue(pin.isConfigured())
        assertEquals("1234", pin.lastDemoPin)
        assertEquals(Routes.HOME, Routes.homeFor(UserRole.TUTOR))
    }

    @Test fun typingTheMasterAccountOpensTheMasterPanelAndTouchesNoPin() = runTest(dispatcher) {
        coEvery { auth.login("admin@fluilet.com", adminPassword) } returns Result.success(session(UserRole.MASTER_ADMIN, "admin@fluilet.com"))

        val result = LoginUseCase(auth, pin)("admin@fluilet.com", adminPassword)

        assertEquals(UserRole.MASTER_ADMIN, result.getOrThrow().role)
        assertEquals(Routes.ADMIN, Routes.homeFor(result.getOrThrow().role))
        assertFalse(pin.isConfigured())
        assertNull(pin.lastDemoPin)
    }

    @Test fun anyOtherTutorLogsInWithoutTouchingThePin() = runTest(dispatcher) {
        coEvery { auth.login("mama@example.com", "Clave#2026x") } returns Result.success(session(UserRole.TUTOR, "mama@example.com"))
        LoginUseCase(auth, pin)("mama@example.com", "Clave#2026x").getOrThrow()
        assertFalse(pin.isConfigured())
    }

    @Test fun aWrongPasswordOrNoConnectionIsReportedAndNoPinIsStored() = runTest(dispatcher) {
        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.Unauthorized())
        assertTrue(LoginUseCase(auth, pin)("tutor@fluilet.com", "mala").exceptionOrNull() is AppError.Unauthorized)

        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.NoConnection())
        val vm = LoginViewModel(LoginUseCase(auth, pin))
        vm.onEmail("tutor@fluilet.com")
        vm.onPassword(tutorPassword)
        vm.submit()
        assertTrue(vm.state.value.errorMessage!!.contains("conexión"))
        assertFalse(pin.isConfigured())
    }

    @Test fun anEmptyPasswordIsRejectedBeforeCallingTheServer() = runTest(dispatcher) {
        assertTrue(LoginUseCase(auth, pin)("tutor@fluilet.com", "").exceptionOrNull() is AppError.Validation)
        coVerify(exactly = 0) { auth.login(any(), any()) }
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
