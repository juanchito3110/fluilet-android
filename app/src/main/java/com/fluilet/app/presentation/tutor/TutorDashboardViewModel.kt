package com.fluilet.app.presentation.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.RbacRoleState
import com.fluilet.app.domain.model.SessionProgress
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.usecase.GetSessionProgressUseCase
import com.fluilet.app.domain.usecase.SetNlpLevelUseCase
import com.fluilet.app.domain.usecase.ToggleRbacRoleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TutorUiState(
    val sessionProgress: List<SessionProgress> = emptyList(),
    val nlpLevel: NlpLevel = NlpLevel.INTERMEDIO,
    val rbacRoles: List<RbacRoleState> = listOf(
        RbacRoleState(UserRole.TUTOR, "Tutor (Admin)", enabled = true),
        RbacRoleState(UserRole.NINO, "Niño (Usuario)", enabled = true)
    ),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TutorDashboardViewModel @Inject constructor(
    private val getSessionProgress: GetSessionProgressUseCase,
    private val setNlpLevel: SetNlpLevelUseCase,
    private val toggleRbacRole: ToggleRbacRoleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorUiState())
    val uiState: StateFlow<TutorUiState> = _uiState.asStateFlow()

    fun loadDashboard(childId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getSessionProgress(childId)
                .onSuccess { progress -> _uiState.update { it.copy(sessionProgress = progress, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Error al cargar el progreso") } }
        }
    }

    fun onNlpLevelSelected(level: NlpLevel, childId: String) {
        val previous = _uiState.value.nlpLevel
        _uiState.update { it.copy(nlpLevel = level) } // actualización optimista
        viewModelScope.launch {
            setNlpLevel(childId, level).onFailure {
                _uiState.update { it.copy(nlpLevel = previous, errorMessage = "No se pudo actualizar el nivel NLP") }
            }
        }
    }

    fun onRoleToggled(role: UserRole, enabled: Boolean) {
        val previousRoles = _uiState.value.rbacRoles
        _uiState.update { state ->
            state.copy(rbacRoles = state.rbacRoles.map { if (it.role == role) it.copy(enabled = enabled) else it })
        }
        viewModelScope.launch {
            toggleRbacRole(role, enabled).onFailure { e ->
                _uiState.update { it.copy(rbacRoles = previousRoles, errorMessage = e.message ?: "No se pudo actualizar el rol") }
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}