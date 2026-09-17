package com.fluilet.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private const val MIN_PASSWORD_LENGTH = 6

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isFormValid: Boolean
        get() = EMAIL_REGEX.matches(email) && password.length >= MIN_PASSWORD_LENGTH
}

sealed class AuthEvent {
    data class LoginSuccess(val role: UserRole) : AuthEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onLoginClicked(roleHint: UserRole) {
        val state = _uiState.value
        if (!state.isFormValid) {
            _uiState.update {
                it.copy(errorMessage = "Ingresa un correo válido y una contraseña de al menos $MIN_PASSWORD_LENGTH caracteres.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.login(username = state.email, password = state.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(AuthEvent.LoginSuccess(roleHint))
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Credenciales inválidas") }
                }
        }
    }
}