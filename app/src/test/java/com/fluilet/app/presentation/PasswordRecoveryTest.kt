package com.fluilet.app.presentation

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.usecase.LoginUseCase
import com.fluilet.app.domain.usecase.PasswordRecoveryUseCase
import com.fluilet.app.presentation.auth.LoginViewModel
import com.fluilet.app.presentation.auth.PasswordRecoveryViewModel
import com.fluilet.app.presentation.auth.RecoveryStep
import com.fluilet.app.presentation.fluibot.FluiBotMood
import com.fluilet.app.testutil.FakeTutorPin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

/**
 * "¿Olvidaste tu contraseña?" (backend a2dd836): forgot-password → verify-reset-code → reset-password, las 6 casillas
 * (tecleo y pegado), códigos inválidos o vencidos, el enfriamiento del reenvío y los gestos de FluiBot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRecoveryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val auth = mockk<AuthRepository>()
    private val email = "mama@example.com"
    private val newPassword = "Nueva#Clave2026"
    private val generic = "Si el correo está registrado, recibirás un código de recuperación."
    private val invalid = "El código es incorrecto, venció o ya no está disponible. Solicita uno nuevo si es necesario."
    private val updated = "Tu contraseña se actualizó. Inicia sesión con tu nueva contraseña."

    private fun vm() = PasswordRecoveryViewModel(PasswordRecoveryUseCase(auth))

    private fun givenCodeIsSent() {
        coEvery { auth.requestPasswordReset(email) } returns Result.success(generic)
    }

    /** Deja el flujo en el paso 2 con el código enviado. */
    private fun atCodeStep(): PasswordRecoveryViewModel {
        givenCodeIsSent()
        return vm().apply { start(email); sendCode() }
    }

    // --- Flujo completo -------------------------------------------------------------------------------------

    @Test fun theWholeFlowGoesFromEmailToCodeToANewPasswordAndCelebrates() = runTest(dispatcher) {
        givenCodeIsSent()
        coEvery { auth.verifyResetCode(email, "048213") } returns Result.success("Código válido. Ya puedes establecer tu nueva contraseña.")
        coEvery { auth.resetPassword(email, "048213", newPassword) } returns Result.success(updated)
        val vm = vm()

        // Paso 1: el correo llega pre-llenado desde Iniciar Sesión.
        vm.start("  $email ")
        assertEquals(RecoveryStep.EMAIL, vm.state.value.step)
        assertEquals(email, vm.state.value.email)
        assertEquals(FluiBotMood.HAPPY, vm.state.value.mood)
        vm.sendCode()

        // Paso 2: mensaje genérico del servidor y un minuto de espera para reenviar.
        assertEquals(RecoveryStep.CODE, vm.state.value.step)
        assertEquals(generic, vm.state.value.infoMessage)
        assertEquals(PasswordRecoveryViewModel.RESEND_COOLDOWN_SECONDS, vm.state.value.resendSeconds)
        vm.onCode("048213")

        // Paso 3: contraseña nueva con requisitos y confirmación.
        assertEquals(RecoveryStep.NEW_PASSWORD, vm.state.value.step)
        vm.onNewPassword(newPassword)
        vm.onConfirmPassword(newPassword)
        assertTrue(vm.state.value.requirements.all { it.second })
        assertTrue(vm.state.value.canReset)
        vm.resetPassword()

        val done = vm.state.value
        assertEquals(RecoveryStep.DONE, done.step)
        assertEquals(FluiBotMood.CELEBRATING, done.mood)
        assertEquals("confeti y estrellas una vez", 1, done.celebration)
        assertEquals(updated, done.successMessage)
        assertEquals("ni la contraseña ni el código quedan en memoria", "", done.newPassword + done.confirmPassword + done.code)
        coVerifyOrder {
            auth.requestPasswordReset(email)
            auth.verifyResetCode(email, "048213")
            auth.resetPassword(email, "048213", newPassword)
        }
    }

    @Test fun backAtLoginTheEmailIsReadyWithTheConfirmation() {
        val login = LoginViewModel(LoginUseCase(auth, FakeTutorPin()))
        login.onPassword("vieja")
        login.onPasswordRecovered(" $email ", updated)
        assertEquals(email, login.state.value.email)
        assertEquals("", login.state.value.password)
        assertEquals(updated, login.state.value.notice)
        assertFalse("falta escribir la contraseña nueva", login.state.value.canSubmit)
    }

    @Test fun fluiBotThinksWhileSendingAndVerifying() = runTest(dispatcher) {
        val server = CompletableDeferred<Unit>()
        coEvery { auth.requestPasswordReset(email) } coAnswers { server.await(); Result.success(generic) }
        val vm = vm().apply { start(email) }

        vm.sendCode()
        assertTrue(vm.state.value.isLoading)
        assertEquals(FluiBotMood.THINKING, vm.state.value.mood)
        server.complete(Unit)

        assertFalse(vm.state.value.isLoading)
        assertEquals(FluiBotMood.HAPPY, vm.state.value.mood)
    }

    // --- 6 casillas: tecleo y pegado ----------------------------------------------------------------------

    @Test fun typingDigitByDigitVerifiesOnlyWhenTheSixthDigitArrives() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "004821") } returns Result.success("ok")
        val vm = atCodeStep()

        "004821".runningFold("") { acc, c -> acc + c }.drop(1).dropLast(1).forEach { partial ->
            vm.onCode(partial)
            assertEquals(partial, vm.state.value.code)
        }
        coVerify(exactly = 0) { auth.verifyResetCode(any(), any()) }

        vm.onCode("004821")
        coVerify(exactly = 1) { auth.verifyResetCode(email, "004821") }
        assertEquals("los ceros iniciales se conservan", RecoveryStep.NEW_PASSWORD, vm.state.value.step)
    }

    @Test fun onlyDigitsEnterTheBoxesAndNeverMoreThanSix() {
        val vm = atCodeStep()
        vm.onCode("4a")
        assertEquals("4", vm.state.value.code)
        vm.onCode("4-8 ")
        assertEquals("48", vm.state.value.code)
        coEvery { auth.verifyResetCode(any(), any()) } returns Result.failure(AppError.NoConnection())
        vm.onCode("4821390")
        assertEquals("482139", vm.state.value.code)
    }

    @Test fun pastingTheWholeCodeFromTheEmailFillsTheBoxesAndVerifies() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "654321") } returns Result.success("ok")
        val vm = atCodeStep()
        vm.onCode("12")

        // Pegado desde el teclado sobre dos casillas ya escritas: manda el código pegado, no la mezcla.
        vm.onCode("12654321")

        assertEquals(RecoveryStep.NEW_PASSWORD, vm.state.value.step)
        coVerify(exactly = 1) { auth.verifyResetCode(email, "654321") }
    }

    @Test fun thePasteButtonFindsTheCodeInsideTheCopiedEmailText() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "048213") } returns Result.success("ok")
        val vm = atCodeStep()

        vm.pasteCode("Tu código de Fluilet es 048 213. Vence en 10 minutos.")

        coVerify(exactly = 1) { auth.verifyResetCode(email, "048213") }
        assertEquals(RecoveryStep.NEW_PASSWORD, vm.state.value.step)
    }

    @Test fun pastingSomethingWithoutACodeExplainsItAndKeepsTheBoxes() {
        val vm = atCodeStep()
        vm.onCode("48")
        vm.pasteCode("https://fluilet.com/ayuda")
        assertEquals("48", vm.state.value.code)
        assertEquals(PasswordRecoveryViewModel.NO_CODE_IN_CLIPBOARD, vm.state.value.errorMessage)
        vm.pasteCode(null)
        assertEquals("48", vm.state.value.code)
        coVerify(exactly = 0) { auth.verifyResetCode(any(), any()) }
    }

    // --- Códigos inválidos o vencidos ----------------------------------------------------------------------

    @Test fun aWrongCodeShakesAndEmptiesTheBoxesWithTheServerMessage() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "111111") } returns Result.failure(AppError.InvalidResetCode(invalid))
        val vm = atCodeStep()

        vm.onCode("111111")

        val s = vm.state.value
        assertEquals(RecoveryStep.CODE, s.step)
        assertEquals("", s.code)
        assertEquals(1, s.codeRejections)
        assertEquals(invalid, s.errorMessage)
        assertEquals(FluiBotMood.HAPPY, s.mood)
        // Al escribir de nuevo desaparece el aviso.
        vm.onCode("2")
        assertNull(vm.state.value.errorMessage)
    }

    @Test fun aCodeThatExpiresBeforeSavingGoesBackToTheCodeStep() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "048213") } returns Result.success("ok")
        coEvery { auth.resetPassword(email, "048213", newPassword) } returns Result.failure(AppError.InvalidResetCode(invalid))
        val vm = atCodeStep()
        vm.onCode("048213")
        vm.onNewPassword(newPassword)
        vm.onConfirmPassword(newPassword)

        vm.resetPassword()

        val s = vm.state.value
        assertEquals(RecoveryStep.CODE, s.step)
        assertEquals("", s.code)
        assertEquals("", s.newPassword + s.confirmPassword)
        assertEquals(1, s.codeRejections)
        assertEquals(invalid, s.errorMessage)
        assertEquals("sin celebración", 0, s.celebration)
    }

    @Test fun aPasswordRejectedByTheServerKeepsTheUserOnTheSameStep() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "048213") } returns Result.success("ok")
        coEvery { auth.resetPassword(any(), any(), any()) } returns
            Result.failure(AppError.Validation("La contraseña nueva no cumple los requisitos.", 422))
        val vm = atCodeStep()
        vm.onCode("048213")
        vm.onNewPassword(newPassword)
        vm.onConfirmPassword(newPassword)

        vm.resetPassword()

        assertEquals(RecoveryStep.NEW_PASSWORD, vm.state.value.step)
        assertEquals("048213", vm.state.value.code)
        assertTrue(vm.state.value.errorMessage!!.contains("requisitos"))
    }

    @Test fun weakOrMismatchedPasswordsNeverReachTheServer() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "048213") } returns Result.success("ok")
        val vm = atCodeStep()
        vm.onCode("048213")

        vm.onNewPassword("corta1!")
        vm.onConfirmPassword("corta1!")
        assertFalse(vm.state.value.canReset)
        assertTrue(vm.state.value.strength < 1f)
        vm.resetPassword()

        vm.onNewPassword(newPassword)
        vm.onConfirmPassword("$newPassword!")
        assertEquals("Las contraseñas no coinciden.", vm.state.value.confirmHint)
        assertFalse(vm.state.value.canReset)
        vm.resetPassword()

        coVerify(exactly = 0) { auth.resetPassword(any(), any(), any()) }
    }

    @Test fun anInvalidEmailOrANetworkErrorStaysOnTheFirstStep() = runTest(dispatcher) {
        coEvery { auth.requestPasswordReset(any()) } returns Result.failure(AppError.NoConnection())
        val vm = vm().apply { start("mama@") }
        assertFalse(vm.state.value.canSendCode)
        assertTrue(vm.state.value.emailHint != null)

        vm.onEmail(email)
        vm.sendCode()

        assertEquals(RecoveryStep.EMAIL, vm.state.value.step)
        assertTrue(vm.state.value.errorMessage!!.contains("conexión"))
    }

    // --- Reenvío con enfriamiento ---------------------------------------------------------------------------

    @Test fun resendingWaitsOneMinuteLikeTheBackendAndThenSendsANewCode() = runTest(dispatcher) {
        val vm = atCodeStep()
        assertFalse(vm.state.value.canResend)
        vm.resendCode()
        coVerify(exactly = 1) { auth.requestPasswordReset(email) }

        advanceTimeBy(30_001)
        assertEquals(30, vm.state.value.resendSeconds)
        advanceTimeBy(30_500)
        assertEquals(0, vm.state.value.resendSeconds)
        assertTrue(vm.state.value.canResend)

        vm.onCode("12")
        vm.resendCode()
        coVerify(exactly = 2) { auth.requestPasswordReset(email) }
        assertEquals("el código anterior queda invalidado", "", vm.state.value.code)
        assertEquals(PasswordRecoveryViewModel.RESEND_COOLDOWN_SECONDS, vm.state.value.resendSeconds)
    }

    @Test fun changingTheEmailDuringTheCooldownOnlyAsksAgainForADifferentAddress() = runTest(dispatcher) {
        coEvery { auth.requestPasswordReset("papa@example.com") } returns Result.success(generic)
        val vm = atCodeStep()

        assertTrue(vm.back())
        assertEquals(RecoveryStep.EMAIL, vm.state.value.step)
        vm.sendCode() // mismo correo: el código anterior sigue vivo
        assertEquals(RecoveryStep.CODE, vm.state.value.step)
        coVerify(exactly = 1) { auth.requestPasswordReset(email) }

        vm.back()
        vm.onEmail("papa@example.com")
        vm.sendCode()
        coVerify(exactly = 1) { auth.requestPasswordReset("papa@example.com") }
    }

    @Test fun backWalksTheStepsAndThenLeavesTheFlow() = runTest(dispatcher) {
        coEvery { auth.verifyResetCode(email, "048213") } returns Result.success("ok")
        val vm = atCodeStep()
        vm.onCode("048213")
        vm.onNewPassword(newPassword)

        assertTrue(vm.back())
        assertEquals(RecoveryStep.CODE, vm.state.value.step)
        assertEquals("", vm.state.value.newPassword)
        assertTrue(vm.back())
        assertEquals(RecoveryStep.EMAIL, vm.state.value.step)
        assertFalse("desde el primer paso vuelve a Iniciar Sesión", vm.back())
    }
}
