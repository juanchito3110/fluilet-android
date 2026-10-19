package com.fluilet.app.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    sessionManager: SessionManager,
    private val logoutUseCase: LogoutUseCase,
    private val holder: ActiveReadingHolder
) : ViewModel() {

    val session: StateFlow<SessionState> = sessionManager.state

    fun logout() {
        viewModelScope.launch {
            holder.clear()
            logoutUseCase()
        }
    }
}
