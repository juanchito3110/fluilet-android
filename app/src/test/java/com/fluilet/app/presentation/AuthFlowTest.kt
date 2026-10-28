package com.fluilet.app.presentation

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.security.TutorPinManager
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.model.User
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.usecase.LoginUseCase
import com.fluilet.app.domain.usecase.RegisterUseCase
import com.fluilet.app.presentation.auth.LoginViewModel
import com.fluilet.app.presentation.auth.RegisterViewModel
import com.fluilet.app.presentation.navigation.Routes
import com.fluilet.app.testutil.FakeSecureStore
import com.fluilet.app.testutil.FakeTutorPin
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Arranque siempre en Splash -> Iniciar Sesión, login manual, registro normal con PIN y ruta del maestro. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val auth = mockk<AuthRepository>()
    private val tutorSession = AuthSession("a", "r", UserRole.TUTOR, "nuevo@example.com")
    private val user = User("1", "nuevo@example.com", "Ana Nueva", UserRole.TUTOR, true, false)

    // --- Arranque en frío ----------------------------------------------------------------------------

    @Test fun aSessionSavedByAPreviousRunIsDiscardedSoTheAppAlwaysOpensOnTheAuthScreen() {
        val store = FakeSecureStore()
        val sessions = SessionManager(store)
        sessions.save(AuthSession("access", "refresh", UserRole.TUTOR, "tutor@fluilet.com"))
        // El PIN parental del tutor vive en el mismo almacén cifrado.
        store.putString("pin.hash", "hash")

        val relaunched = SessionManager(store).apply { restore(); discardPersistedSession() }

        assertEquals(SessionState.LoggedOut, relaunched.state.value)
        assertNull(relaunched.accessToken)
        assertNull(relaunched.refreshToken)
        assertNull(relaunched.email)
        assertEquals("el PIN parental se conserva", "hash", store.getString("pin.hash"))
        // Y la ruta que sigue al Splash es la pantalla de acceso, nunca el panel.
        assertEquals(Routes.AUTH, Routes.redirectFor(Routes.SPLASH, splashDone = true, session = relaunched.state.value))
    }

    @Test fun anExplicitLoginAfterwardsDoesOpenTheRoleScreen() {
        assertEquals(Routes.HOME, Routes.redirectFor(Routes.AUTH, true, SessionState.LoggedIn(UserRole.TUTOR, "tutor@fluilet.com")))
        assertEquals(Routes.ADMIN, Routes.redirectFor(Routes.AUTH, true, SessionState.LoggedIn(UserRole.MASTER_ADMIN, "admin@fluilet.com")))
        assertEquals(Routes.ADMIN, Routes.homeFor(UserRole.MASTER_ADMIN))
        assertEquals(Routes.HOME, Routes.homeFor(UserRole.TUTOR))
    }

    @Test fun theMasterLogsOutBackToTheAuthScreenAndCanEnterAsTutor() {
        assertEquals(Routes.AUTH, Routes.redirectFor(Routes.ADMIN, true, SessionState.LoggedOut))
        assertEquals(Routes.HOME, Routes.redirectFor(Routes.AUTH, true, SessionState.LoggedIn(UserRole.TUTOR, "tutor@fluilet.com")))
    }

    // --- Login manual ----------------------------------------------------------------------------------

    @Test fun manualLoginUsesWhateverTheUserTypes() = runTest(dispatcher) {
        coEvery { auth.login("mama@example.com", "Clave#2026x") } returns Result.success(tutorSession)
        val vm = LoginViewModel(LoginUseCase(auth, FakeTutorPin()))
        vm.onEmail("mama@example.com")
        vm.onPassword("Clave#2026x")
        assertTrue(vm.state.value.canSubmit)
        vm.submit()
        coVerify { auth.login("mama@example.com", "Clave#2026x") }
        assertEquals("", vm.state.value.password)
    }

    @Test fun aWrongPasswordShowsAnErrorOnTheSameScreen() = runTest(dispatcher) {
        coEvery { auth.login(any(), any()) } returns Result.failure(AppError.Unauthorized())
        val vm = LoginViewModel(LoginUseCase(auth, FakeTutorPin()))
        vm.onEmail("mama@example.com")
        vm.onPassword("mala")
        vm.submit()
        assertTrue(vm.state.value.errorMessage!!.contains("Credenciales"))
    }

    // --- Registro normal con PIN parental --------------------------------------------------------------------

    private fun registerVm(pin: FakeTutorPin = FakeTutorPin()): Pair<RegisterViewModel, FakeTutorPin> =
        RegisterViewModel(RegisterUseCase(auth), LoginUseCase(auth, pin), pin) to pin

    private fun RegisterViewModel.fill(pin: String = "4821", role: UserRole = UserRole.TUTOR) {
        onName("Ana Nueva"); onEmail("nuevo@example.com"); onPassword("Clave#2026xyz"); onRole(role); onPin(pin)
    }

    @Test fun aTutorMustChooseAValidParentalPinToRegister() {
        val (vm, _) = registerVm()
        vm.fill(pin = "")
        assertTrue(vm.state.value.needsPin)
        assertFalse(vm.state.value.canSubmit)

        vm.onPin("1234") // secuencia: el Muro Parental la rechaza
        assertTrue(vm.state.value.pinHint!!.contains("secuencias"))
        assertFalse(vm.state.value.canSubmit)

        vm.onPin("48ab21") // solo dígitos
        assertEquals("4821", vm.state.value.pin)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun anIndependentAdultDoesNotNeedAPin() {
        val (vm, _) = registerVm()
        vm.fill(pin = "", role = UserRole.ADULTO_INDEPENDIENTE)
        assertFalse(vm.state.value.needsPin)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun registeringCreatesTheAccountStoresThePinFirstAndThenLogsIn() = runTest(dispatcher) {
        coEvery { auth.register(any()) } returns Result.success(user)
        coEvery { auth.login("nuevo@example.com", "Clave#2026xyz") } returns Result.success(tutorSession)
        val (vm, pins) = registerVm()
        vm.fill()

        vm.submit()

        assertEquals("4821", pins.lastResetPin)
        assertTrue(pins.isConfigured())
        coVerifyOrder {
            auth.register(match { it.email == "nuevo@example.com" && it.role == UserRole.TUTOR })
            auth.login("nuevo@example.com", "Clave#2026xyz")
        }
        assertEquals("", vm.state.value.pin)
    }

    @Test fun aRegistrationErrorStoresNoPin() = runTest(dispatcher) {
        coEvery { auth.register(any()) } returns Result.failure(AppError.Conflict("Ese correo ya está registrado."))
        val (vm, pins) = registerVm()
        vm.fill()
        vm.submit()
        assertNull(pins.lastResetPin)
        assertTrue(vm.state.value.errorMessage!!.contains("registrado"))
    }

    // --- PIN público de la demostración ------------------------------------------------------------------

    @Test fun thePublicDemoPinIsAcceptedOnlyThroughTheDemoPathAndNormalPinsStayStrict() {
        val pins = TutorPinManager(FakeSecureStore(), { 0L }, { 0L }, iterations = 1_000)
        assertTrue("1234 es una secuencia: el camino normal la rechaza", pins.setInitialPin("1234").isFailure)

        assertTrue(pins.configureDemoPin("1234").isSuccess)

        assertTrue(pins.isConfigured())
        assertTrue(pins.verify("1234") is com.fluilet.app.domain.model.PinResult.Success)
        assertTrue(pins.configureDemoPin("12").isFailure) // sigue exigiendo de 4 a 6 dígitos
        assertTrue(pins.configureDemoPin("12ab").isFailure)
    }
}
