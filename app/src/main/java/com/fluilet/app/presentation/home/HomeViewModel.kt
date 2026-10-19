package com.fluilet.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.model.SessionSummary
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.DeleteChildUseCase
import com.fluilet.app.domain.usecase.GetChildrenUseCase
import com.fluilet.app.domain.usecase.GetSessionHistoryUseCase
import com.fluilet.app.domain.usecase.IsTutorPinConfiguredUseCase
import com.fluilet.app.domain.usecase.LogoutUseCase
import com.fluilet.app.domain.usecase.SetNlpLevelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val role: UserRole = UserRole.TUTOR,
    val userName: String = "",
    val isLoading: Boolean = true,
    val children: List<ChildProfile> = emptyList(),
    val selectedChildId: String? = null,
    val history: List<SessionSummary> = emptyList(),
    val activeReading: ActiveReading? = null,
    val pinConfigured: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedChild: ChildProfile? get() = children.firstOrNull { it.id == selectedChildId }
    val isTutor: Boolean get() = role.canManageChildren

    /** La lectura lista pertenece al perfil seleccionado (tutor) o al propio adulto. */
    val readingForSelection: ActiveReading?
        get() = activeReading?.takeIf { it.childId == selectedChildId }
}

private data class HomeLocal(
    val userName: String = "",
    val isLoading: Boolean = true,
    val children: List<ChildProfile> = emptyList(),
    val selectedChildId: String? = null,
    val history: List<SessionSummary> = emptyList(),
    val pinConfigured: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionManager: SessionManager,
    holder: ActiveReadingHolder,
    private val auth: AuthRepository,
    private val getChildren: GetChildrenUseCase,
    private val setNlpLevel: SetNlpLevelUseCase,
    private val deleteChild: DeleteChildUseCase,
    private val getHistory: GetSessionHistoryUseCase,
    private val isPinConfigured: IsTutorPinConfiguredUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val readingHolder: ActiveReadingHolder
) : ViewModel() {

    private val local = MutableStateFlow(HomeLocal())

    val uiState: StateFlow<HomeUiState> = combine(sessionManager.state, holder.current, local) { session, reading, l ->
        HomeUiState(
            role = (session as? SessionState.LoggedIn)?.role ?: UserRole.TUTOR,
            userName = l.userName,
            isLoading = l.isLoading,
            children = l.children,
            selectedChildId = l.selectedChildId,
            history = l.history,
            activeReading = reading,
            pinConfigured = l.pinConfigured,
            errorMessage = l.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Recarga datos; se llama al abrir y al volver a primer plano. */
    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(pinConfigured = isPinConfigured(), errorMessage = null) }

            auth.currentUser()
                .onSuccess { user -> local.update { it.copy(userName = user.fullName.substringBefore(' ')) } }

            if (uiState.value.isTutor) {
                getChildren()
                    .onSuccess { list ->
                        local.update { l ->
                            val keep = l.selectedChildId?.takeIf { id -> list.any { it.id == id } }
                            l.copy(children = list, selectedChildId = keep ?: list.firstOrNull()?.id, isLoading = false)
                        }
                    }
                    .onFailure { e -> local.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
            } else {
                local.update { it.copy(isLoading = false) }
            }
            loadHistory()
        }
    }

    fun refreshPin() = local.update { it.copy(pinConfigured = isPinConfigured()) }

    fun select(childId: String) {
        local.update { it.copy(selectedChildId = childId, history = emptyList()) }
        viewModelScope.launch { loadHistory() }
    }

    private suspend fun loadHistory() {
        val childId = if (uiState.value.isTutor) local.value.selectedChildId else null
        if (uiState.value.isTutor && childId == null) return
        getHistory(childId).onSuccess { history ->
            local.update { it.copy(history = history.sortedByDescending(SessionSummary::startedAt)) }
        }
    }

    fun onNlpLevel(level: NlpLevel) {
        val child = uiState.value.selectedChild ?: return
        if (child.nlpLevel == level) return
        viewModelScope.launch {
            // Optimista: la UI refleja el cambio; si falla se revierte.
            replaceChild(child.copy(nlpLevel = level))
            setNlpLevel(child.id, level)
                .onSuccess(::replaceChild)
                .onFailure { e ->
                    replaceChild(child)
                    local.update { it.copy(errorMessage = e.toAppError().userMessage) }
                }
        }
    }

    private fun replaceChild(updated: ChildProfile) = local.update { l ->
        l.copy(children = l.children.map { if (it.id == updated.id) updated else it })
    }

    fun delete(childId: String) {
        viewModelScope.launch {
            deleteChild(childId)
                .onSuccess {
                    if (readingHolder.current.value?.childId == childId) readingHolder.clear()
                    local.update { l ->
                        val remaining = l.children.filterNot { it.id == childId }
                        l.copy(children = remaining, selectedChildId = remaining.firstOrNull()?.id, history = emptyList())
                    }
                    loadHistory()
                }
                .onFailure { e -> local.update { it.copy(errorMessage = e.toAppError().userMessage) } }
        }
    }

    fun logout() {
        viewModelScope.launch {
            readingHolder.clear()
            logoutUseCase()
        }
    }

    fun consumeError() = local.update { it.copy(errorMessage = null) }
}
