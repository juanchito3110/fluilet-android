package com.fluilet.app.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.model.AdminOverview
import com.fluilet.app.domain.model.MasterGrant
import com.fluilet.app.domain.repository.AdminRepository
import com.fluilet.app.domain.model.ModelAudit
import com.fluilet.app.domain.usecase.GetModelAuditUseCase
import com.fluilet.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Secciones del Sistema de Control Maestro. */
enum class AdminTab(val emoji: String, val label: String) {
    CHILDREN("👦", "Gestión y Otorgamiento a Niños"),
    TUTORS("👤", "Gestión de Usuarios / Tutores")
}

data class AdminUiState(
    val overview: AdminOverview = AdminOverview.EMPTY,
    val isLoading: Boolean = true,
    /** Una operación del maestro (grant, baja, restauración...) en curso. */
    val isBusy: Boolean = false,
    val tab: AdminTab = AdminTab.CHILDREN,
    val message: String? = null,
    val errorMessage: String? = null,
    /** Auditoría de `model-info` cuando el overview no la trae (servidor anterior o sin conexión). */
    val fallbackAudit: ModelAudit? = null
) {
    /** 🧠 Auditoría del modelo reentrenado: la de `GET /admin/overview` o, si falta, la de `model-info`. */
    val modelAudit: ModelAudit? get() = overview.modelAudit ?: fallbackAudit
}

/**
 * Sistema de Control Maestro: KPIs (`GET /admin/overview`), otorgamiento a niños (`POST /admin/profiles/{id}/grant`),
 * bajas (`DELETE /admin/profiles/{id}` y `/admin/tutors/{id}`), `reset-pin` y `ensure-demo-accounts`. Tras cada
 * operación se vuelve a leer el overview para que las tarjetas muestren los saldos reales del servidor.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val admin: AdminRepository,
    private val logoutUseCase: LogoutUseCase,
    private val getModelAudit: GetModelAuditUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: AdminTab) = _state.update { it.copy(tab = tab) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            admin.overview()
                .onSuccess { o -> _state.update { it.copy(overview = o, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
            if (_state.value.overview.modelAudit == null && _state.value.fallbackAudit == null) {
                val audit = getModelAudit()
                _state.update { it.copy(fallbackAudit = audit) }
            }
        }
    }

    /** "🔄 Restaurar Cuentas Demo": recrea `tutor@fluilet.com` y sus 4 niños si alguno fue dado de baja. */
    fun restoreDemoAccounts() = operation("Cuentas demo restauradas: tutor@fluilet.com y sus 4 niños.") {
        admin.ensureDemoAccounts().onSuccess { o -> _state.update { it.copy(overview = o) } }.map { }
    }

    /** Otorga gemas, XP, etapa, nivel, edad o todas las recompensas a un niño y refresca la lista. */
    fun grant(profileId: String, grant: MasterGrant, successMessage: String = "Cambios aplicados.") =
        operation(successMessage) {
            admin.grant(profileId, grant).onSuccess { refreshOverview() }.map { }
        }

    fun deleteProfile(profileId: String) = operation("Niño dado de baja.") {
        admin.deleteProfile(profileId).onSuccess { refreshOverview() }
    }

    /** La cuenta maestra no se puede dar de baja: ni siquiera se llama al backend (respondería 400). */
    fun deleteTutor(userId: String) {
        val user = _state.value.overview.users.firstOrNull { it.userId == userId }
        if (user != null && !user.canBeDeleted) {
            _state.update { it.copy(errorMessage = "La cuenta maestra no puede darse de baja.") }
            return
        }
        operation("Usuario dado de baja con sus perfiles.") {
            admin.deleteTutor(userId).onSuccess { refreshOverview() }
        }
    }

    fun resetPin(userId: String) = operation("PIN restablecido a 1234.") {
        admin.resetPin(userId, "1234").map { }
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, errorMessage = null) }

    private suspend fun refreshOverview() {
        admin.overview().onSuccess { o -> _state.update { it.copy(overview = o) } }
    }

    private fun operation(success: String, block: suspend () -> Result<Unit>) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, errorMessage = null) }
            block()
                .onSuccess { _state.update { it.copy(isBusy = false, message = success) } }
                .onFailure { e -> _state.update { it.copy(isBusy = false, errorMessage = e.toAppError().userMessage) } }
        }
    }
}
