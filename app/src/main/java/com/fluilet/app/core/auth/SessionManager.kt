package com.fluilet.app.core.auth

import com.fluilet.app.core.security.SecureStore
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente única de verdad de la sesión. JWT, rol activo y correo viven cifrados (AES-256-GCM) en
 * [SecureStore]; en memoria solo se cachean para las peticiones de red. Nada se registra en logs.
 */
@Singleton
class SessionManager @Inject constructor(
    private val store: SecureStore
) {
    private val lock = Any()

    @Volatile private var cachedAccess: String? = null
    @Volatile private var cachedRefresh: String? = null
    @Volatile private var loaded = false

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Lee el almacén cifrado. Llamar fuera del hilo principal (primer acceso al Keystore). */
    fun restore() {
        synchronized(lock) {
            ensureLoaded()
            val access = cachedAccess
            val role = UserRole.fromWire(store.getString(KEY_ROLE))
            val email = store.getString(KEY_EMAIL)
            _state.value =
                if (access != null && role != null && email != null) SessionState.LoggedIn(role, email)
                else SessionState.LoggedOut
        }
    }

    val accessToken: String? get() = synchronized(lock) { ensureLoaded(); cachedAccess }
    val refreshToken: String? get() = synchronized(lock) { ensureLoaded(); cachedRefresh }
    val email: String? get() = synchronized(lock) { store.getString(KEY_EMAIL) }

    fun save(session: AuthSession) {
        synchronized(lock) {
            ensureLoaded()
            store.putString(KEY_ACCESS, session.accessToken)
            store.putString(KEY_REFRESH, session.refreshToken)
            store.putString(KEY_ROLE, session.role.wireValue)
            store.putString(KEY_EMAIL, session.email)
            cachedAccess = session.accessToken
            cachedRefresh = session.refreshToken
            _state.value = SessionState.LoggedIn(session.role, session.email)
        }
    }

    fun updateTokens(access: String, refresh: String) {
        synchronized(lock) {
            ensureLoaded()
            store.putString(KEY_ACCESS, access)
            store.putString(KEY_REFRESH, refresh)
            cachedAccess = access
            cachedRefresh = refresh
        }
    }

    /** Actualiza el rol (p. ej. tras consultar `/users/me`). */
    fun updateRole(role: UserRole) {
        synchronized(lock) {
            store.putString(KEY_ROLE, role.wireValue)
            val current = _state.value
            if (current is SessionState.LoggedIn) _state.value = current.copy(role = role)
        }
    }

    /** Cierra la sesión: borra todo el almacén cifrado (tokens, rol y PIN del tutor). */
    fun clear() {
        synchronized(lock) {
            store.clear()
            cachedAccess = null
            cachedRefresh = null
            loaded = true
            _state.value = SessionState.LoggedOut
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        cachedAccess = store.getString(KEY_ACCESS)
        cachedRefresh = store.getString(KEY_REFRESH)
        loaded = true
    }

    private companion object {
        const val KEY_ACCESS = "session.access_token"
        const val KEY_REFRESH = "session.refresh_token"
        const val KEY_ROLE = "session.role"
        const val KEY_EMAIL = "session.email"
    }
}
