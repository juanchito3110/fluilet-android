package com.fluilet.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.model.RegistrationData
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.usecase.DemoConfig
import com.fluilet.app.domain.usecase.EnterDemoModeUseCase
import com.fluilet.app.domain.usecase.LoginUseCase
import com.fluilet.app.domain.usecase.RegisterUseCase
import com.fluilet.app.domain.validation.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isLoading
}

/** Al iniciar sesión, la navegación reacciona al cambio de [com.fluilet.app.domain.model.SessionState]. */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, errorMessage = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, errorMessage = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            login(s.email, s.password)
                .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
                .onSuccess { _state.update { it.copy(isLoading = false, password = "") } }
        }
    }
}

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val role: UserRole = UserRole.TUTOR,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val passwordHint: String? get() = if (password.isEmpty()) null else Validators.strongPassword(password)
    val canSubmit: Boolean
        get() = Validators.fullName(fullName) == null && Validators.email(email) == null &&
            Validators.strongPassword(password) == null && !isLoading
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
    private val login: LoginUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onName(value: String) = _state.update { it.copy(fullName = value, errorMessage = null) }
    fun onEmail(value: String) = _state.update { it.copy(email = value, errorMessage = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, errorMessage = null) }
    fun onRole(value: UserRole) = _state.update { it.copy(role = value) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            register(RegistrationData(s.email, s.fullName, s.password, s.role))
                .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
                .onSuccess {
                    // Cuenta creada: inicia sesión; la navegación reacciona al cambio de sesión.
                    login(s.email, s.password)
                        .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
                        .onSuccess { _state.update { it.copy(isLoading = false, password = "") } }
                }
        }
    }
}

data class DemoUiState(
    val enabled: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** "🚀 Entrar en Modo Demo (Revisión Profesor)": un toque, sin escribir correo ni contraseña. */
@HiltViewModel
class DemoModeViewModel @Inject constructor(
    config: DemoConfig,
    private val enterDemo: EnterDemoModeUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(DemoUiState(enabled = config.enabled))
    val state: StateFlow<DemoUiState> = _state.asStateFlow()

    fun enter() {
        val current = _state.value
        if (!current.enabled || current.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            enterDemo()
                .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
                .onSuccess { _state.update { it.copy(isLoading = false) } }
        }
    }
}
