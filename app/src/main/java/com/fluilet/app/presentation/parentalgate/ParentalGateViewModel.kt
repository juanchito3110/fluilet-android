package com.fluilet.app.presentation.parentalgate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.model.PinResult
import com.fluilet.app.domain.model.PinVerification
import com.fluilet.app.domain.usecase.ChangeTutorPinUseCase
import com.fluilet.app.domain.usecase.ResetTutorPinUseCase
import com.fluilet.app.domain.usecase.SetupTutorPinUseCase
import com.fluilet.app.domain.usecase.VerifyTutorPinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GateMode { VERIFY, SETUP, CHANGE }
enum class GateStep { ENTER_PIN, RESET_PASSWORD }

data class GateUiState(
    val mode: GateMode = GateMode.VERIFY,
    val step: GateStep = GateStep.ENTER_PIN,
    val pin: String = "",
    val newPin: String = "",
    val confirmation: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    val lockedSeconds: Long = 0,
    val isBusy: Boolean = false
) {
    val isLocked: Boolean get() = lockedSeconds > 0
}

sealed interface GateEvent {
    /** [proof] solo existe al verificar el PIN; en alta/cambio/restablecimiento es `null`. */
    data class Success(val proof: PinVerification?) : GateEvent
}

@HiltViewModel
class ParentalGateViewModel @Inject constructor(
    private val verifyPin: VerifyTutorPinUseCase,
    private val setupPin: SetupTutorPinUseCase,
    private val changePin: ChangeTutorPinUseCase,
    private val resetPin: ResetTutorPinUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GateUiState())
    val state: StateFlow<GateUiState> = _state.asStateFlow()

    private val _events = kotlinx.coroutines.channels.Channel<GateEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    fun open(mode: GateMode) {
        countdownJob?.cancel()
        _state.value = GateUiState(mode = mode)
    }

    fun onPin(value: String) = _state.update { it.copy(pin = value.digitsOnly(), errorMessage = null) }
    fun onNewPin(value: String) = _state.update { it.copy(newPin = value.digitsOnly(), errorMessage = null) }
    fun onConfirmation(value: String) = _state.update { it.copy(confirmation = value.digitsOnly(), errorMessage = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, errorMessage = null) }

    fun showReset() = _state.update { it.copy(step = GateStep.RESET_PASSWORD, errorMessage = null, password = "", newPin = "", confirmation = "") }
    fun backFromReset() = _state.update { it.copy(step = GateStep.ENTER_PIN, errorMessage = null) }

    fun submit() {
        val s = _state.value
        if (s.isBusy || s.isLocked) return
        if (s.step == GateStep.RESET_PASSWORD) return submitReset(s)
        when (s.mode) {
            GateMode.VERIFY -> handle(verifyPin(s.pin))
            GateMode.SETUP -> setupPin(s.newPin, s.confirmation)
                .onSuccess { emitSuccess(null) }
                .onFailure(::fail)
            GateMode.CHANGE -> try {
                when (val result = changePin(s.pin, s.newPin, s.confirmation)) {
                    is PinResult.Success -> emitSuccess(null)
                    else -> handle(result)
                }
            } catch (e: AppError) {
                fail(e)
            }
        }
    }

    private fun submitReset(s: GateUiState) {
        if (s.password.isEmpty()) return fail(AppError.Validation("Ingresa tu contraseña de tutor."))
        if (s.newPin != s.confirmation) return fail(AppError.Validation("Los PIN no coinciden."))
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, errorMessage = null) }
            resetPin(s.password, s.newPin)
                .onSuccess { emitSuccess(null) }
                .onFailure { fail(it) }
            _state.update { it.copy(isBusy = false, password = "") }
        }
    }

    private fun handle(result: PinResult) {
        when (result) {
            is PinResult.Success -> emitSuccess(result.proof)
            is PinResult.Wrong -> _state.update {
                it.copy(pin = "", errorMessage = "PIN incorrecto. Intentos restantes: ${result.remainingAttempts}.")
            }
            is PinResult.LockedOut -> startLockout(result.retryAfterSeconds)
            PinResult.NotConfigured -> _state.update { it.copy(errorMessage = "Aún no hay un PIN configurado.") }
        }
    }

    private fun startLockout(seconds: Long) {
        countdownJob?.cancel()
        _state.update { it.copy(pin = "", lockedSeconds = seconds, errorMessage = "Demasiados intentos. Espera para volver a intentar.") }
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _state.update { it.copy(lockedSeconds = remaining) }
            }
            _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun emitSuccess(proof: PinVerification?) {
        _state.update { it.copy(pin = "", newPin = "", confirmation = "", password = "", errorMessage = null) }
        _events.trySend(GateEvent.Success(proof))
    }

    private fun fail(error: Throwable) {
        _state.update { it.copy(errorMessage = error.toAppError().userMessage) }
    }

    private fun String.digitsOnly(): String = filter(Char::isDigit).take(6)
}
