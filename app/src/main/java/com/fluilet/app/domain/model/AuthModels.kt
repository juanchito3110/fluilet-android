package com.fluilet.app.domain.model

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val role: UserRole,
    val email: String
)

data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val isActive: Boolean,
    val isVerified: Boolean
)

data class RegistrationData(
    val email: String,
    val fullName: String,
    val password: String,
    val role: UserRole
)

/** Estado de sesión observable por la navegación. */
sealed interface SessionState {
    /** Aún leyendo el almacén cifrado. */
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val role: UserRole, val email: String) : SessionState
}
