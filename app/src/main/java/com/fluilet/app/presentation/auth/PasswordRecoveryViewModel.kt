package com.fluilet.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.usecase.PasswordRecoveryUseCase
import com.fluilet.app.domain.validation.PasswordRequirement
import com.fluilet.app.domain.validation.ResetCode
import com.fluilet.app.domain.validation.Validators
import com.fluilet.app.presentation.fluibot.FluiBotMood
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pasos de "¿Olvidaste tu contraseña?": correo → código de 6 dígitos → contraseña nueva → listo. */
enum class RecoveryStep { EMAIL, CODE, NEW_PASSWORD, DONE }

data class PasswordRecoveryUiState(
    val step: RecoveryStep = RecoveryStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Mensaje del servidor tras enviar o reenviar el código (genérico: no revela si la cuenta existe). */
    val infoMessage: String? = null,
    /** Correo al que se pidió el último código (para no repetir la solicitud durante el enfriamiento). */
    val sentTo: String? = null,
    /** Segundos que faltan para poder reenviar (el backend admite un código por minuto). */
    val resendSeconds: Int = 0,
    /** Sube con cada código rechazado: las casillas tiemblan y se vacían. */
    val codeRejections: Int = 0,
    /** Sube al restablecer la contraseña: FluiBot celebra y cae confeti. */
    val celebration: Int = 0,
    val successMessage: String? = null
) {
    val emailHint: String? get() = if (email.isBlank()) null else Validators.email(email)
    val canSendCode: Boolean get() = !isLoading && Validators.email(email) == null
    val canVerify: Boolean get() = !isLoading && ResetCode.isValid(code)
    val canResend: Boolean get() = !isLoading && resendSeconds == 0
    val requirements: List<Pair<PasswordRequirement, Boolean>> get() = PasswordRequirement.entries.map { it to it.isMet(newPassword) }
    val strength: Float get() = PasswordRequirement.progress(newPassword)
    val passwordsMatch: Boolean get() = confirmPassword.isNotEmpty() && confirmPassword == newPassword
    val passwordHint: String? get() = if (newPassword.isEmpty()) null else Validators.strongPassword(newPassword)
    val confirmHint: String? get() = if (confirmPassword.isEmpty() || passwordsMatch) null else "Las contraseñas no coinciden."
    val canReset: Boolean get() = !isLoading && newPassword.isNotEmpty() && passwordHint == null && passwordsMatch

    /** FluiBot piensa mientras envía o verifica, saluda al pedir datos y celebra cuando la contraseña cambió. */
    val mood: FluiBotMood
        get() = when {
            step == RecoveryStep.DONE -> FluiBotMood.CELEBRATING
            isLoading -> FluiBotMood.THINKING
            else -> FluiBotMood.HAPPY
        }
}

/**
 * Recuperación de contraseña con código de 6 dígitos por correo (`forgot-password` → `verify-reset-code` →
 * `reset-password`). Al completar las 6 casillas, tecleando o pegando el código, se verifica sola.
 */
@HiltViewModel
class PasswordRecoveryViewModel @Inject constructor(
    private val recovery: PasswordRecoveryUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PasswordRecoveryUiState())
    val state: StateFlow<PasswordRecoveryUiState> = _state.asStateFlow()
    private var cooldown: Job? = null

    /** Abre el flujo con el correo que ya estaba escrito en Iniciar Sesión. */
    fun start(email: String) {
        val previous = _state.value
        // Si se vuelve a abrir durante el enfriamiento para el mismo correo, se conserva para no pedir otro código.
        val keepCooldown = previous.sentTo != null && previous.sentTo == email.trim() && previous.resendSeconds > 0
        if (!keepCooldown) cooldown?.cancel()
        _state.value = PasswordRecoveryUiState(
            email = email.trim(),
            sentTo = previous.sentTo.takeIf { keepCooldown },
            resendSeconds = if (keepCooldown) previous.resendSeconds else 0
        )
    }

    fun onEmail(value: String) = _state.update { it.copy(email = value, errorMessage = null) }

    fun onNewPassword(value: String) = _state.update { it.copy(newPassword = value, errorMessage = null) }

    fun onConfirmPassword(value: String) = _state.update { it.copy(confirmPassword = value, errorMessage = null) }

    /** Cambio en las 6 casillas (tecla a tecla, borrado o pegado desde el teclado). */
    fun onCode(value: String) {
        val s = _state.value
        if (s.isLoading || s.step != RecoveryStep.CODE) return
        setCode(ResetCode.merge(s.code, value), previous = s.code)
    }

    /** "Pegar código": busca el código de 6 dígitos en el texto copiado del correo. */
    fun pasteCode(clipboard: String?) {
        val s = _state.value
        if (s.isLoading || s.step != RecoveryStep.CODE) return
        val code = clipboard?.let(ResetCode::find)
        if (code == null) {
            _state.update { it.copy(errorMessage = NO_CODE_IN_CLIPBOARD) }
            return
        }
        setCode(code, previous = s.code)
    }

    private fun setCode(code: String, previous: String) {
        _state.update { it.copy(code = code, errorMessage = null) }
        if (ResetCode.isValid(code) && code != previous) verifyCode()
    }

    /** Paso 1: pide el código. Con el mismo correo durante el enfriamiento solo avanza (el código anterior sigue vivo). */
    fun sendCode() {
        val s = _state.value
        if (!s.canSendCode) return
        val email = s.email.trim()
        if (s.sentTo == email && s.resendSeconds > 0) {
            _state.update { it.copy(step = RecoveryStep.CODE, errorMessage = null) }
            return
        }
        requestCode(email, RecoveryStep.EMAIL)
    }

    /** Paso 2: reenvía otro código (invalida el anterior en el servidor). */
    fun resendCode() {
        val s = _state.value
        if (s.step != RecoveryStep.CODE || !s.canResend) return
        requestCode(s.email.trim(), RecoveryStep.CODE)
    }

    private fun requestCode(email: String, from: RecoveryStep) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            recovery.requestCode(email)
                .onSuccess { message ->
                    _state.update {
                        it.copy(isLoading = false, step = RecoveryStep.CODE, code = "", infoMessage = message, sentTo = email)
                    }
                    startCooldown()
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, step = from, errorMessage = e.toAppError().userMessage) } }
        }
    }

    fun verifyCode() {
        val s = _state.value
        if (s.step != RecoveryStep.CODE || !s.canVerify) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            recovery.verifyCode(s.email.trim(), s.code)
                .onSuccess { message ->
                    _state.update { it.copy(isLoading = false, step = RecoveryStep.NEW_PASSWORD, infoMessage = message) }
                }
                .onFailure { e -> rejectCode(e.toAppError()) }
        }
    }

    /** Paso 3: guarda la contraseña nueva con el código ya verificado. */
    fun resetPassword() {
        val s = _state.value
        if (s.step != RecoveryStep.NEW_PASSWORD || !s.canReset) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            recovery.resetPassword(s.email.trim(), s.code, s.newPassword)
                .onSuccess { message ->
                    cooldown?.cancel()
                    _state.update {
                        it.copy(
                            isLoading = false, step = RecoveryStep.DONE, code = "", newPassword = "", confirmPassword = "",
                            infoMessage = null, resendSeconds = 0, sentTo = null,
                            successMessage = message, celebration = it.celebration + 1
                        )
                    }
                }
                .onFailure { e ->
                    val error = e.toAppError()
                    // El código venció o se bloqueó mientras se escribía la contraseña: hay que volver a pedirlo.
                    if (error is AppError.InvalidResetCode) rejectCode(error)
                    else _state.update { it.copy(isLoading = false, errorMessage = error.userMessage) }
                }
        }
    }

    private fun rejectCode(error: AppError) = _state.update {
        val invalid = error is AppError.InvalidResetCode
        it.copy(
            isLoading = false,
            step = if (invalid) RecoveryStep.CODE else it.step,
            code = if (invalid) "" else it.code,
            newPassword = if (invalid) "" else it.newPassword,
            confirmPassword = if (invalid) "" else it.confirmPassword,
            codeRejections = if (invalid) it.codeRejections + 1 else it.codeRejections,
            errorMessage = error.userMessage
        )
    }

    /** Atrás dentro del flujo. `false` = ya estaba en el primer paso (o terminó): se vuelve a Iniciar Sesión. */
    fun back(): Boolean {
        val s = _state.value
        if (s.isLoading) return true
        val previous = when (s.step) {
            RecoveryStep.EMAIL, RecoveryStep.DONE -> return false
            RecoveryStep.CODE -> RecoveryStep.EMAIL
            RecoveryStep.NEW_PASSWORD -> RecoveryStep.CODE
        }
        _state.update { it.copy(step = previous, errorMessage = null, newPassword = "", confirmPassword = "") }
        return true
    }

    private fun startCooldown() {
        cooldown?.cancel()
        cooldown = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 1) {
                _state.update { it.copy(resendSeconds = seconds) }
                delay(1_000)
            }
            _state.update { it.copy(resendSeconds = 0) }
        }
    }

    companion object {
        /** Igual que el backend: un código por minuto y cuenta. */
        const val RESEND_COOLDOWN_SECONDS = 60
        const val NO_CODE_IN_CLIPBOARD = "No encontré un código de 6 dígitos en lo que copiaste. Cópialo del correo e inténtalo de nuevo."
    }
}
