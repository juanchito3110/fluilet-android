package com.fluilet.app.data.repository

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.JwtClaims
import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.remote.AuthApi
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.RegisterRequestDto
import com.fluilet.app.data.remote.dto.TokenDto
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.RegistrationData
import com.fluilet.app.domain.model.User
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val api: FluiletApi,
    private val sessionManager: SessionManager
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<AuthSession> {
        val normalizedEmail = email.trim().lowercase()
        val tokens = apiCall { authApi.login(normalizedEmail, password) }
            .getOrElse { return Result.failure(it) }

        val refresh = tokens.refreshToken
            ?: return Result.failure(AppError.Unknown())
        val role = resolveRole(tokens)
            ?: return Result.failure(AppError.Unsupported("Tu cuenta tiene un rol que esta versión de la app no soporta."))

        val session = AuthSession(tokens.accessToken, refresh, role, normalizedEmail)
        sessionManager.save(session)
        return Result.success(session)
    }

    /** Orden: `role` de la respuesta de login → claim del JWT → `/users/me`. */
    private suspend fun resolveRole(tokens: TokenDto): UserRole? {
        UserRole.fromWire(tokens.role)?.let { return it }
        JwtClaims.role(tokens.accessToken)?.let { return it }
        return apiCall { api.meWithToken("Bearer ${tokens.accessToken}") }
            .getOrNull()?.toDomain()?.role
    }

    override suspend fun register(data: RegistrationData): Result<User> =
        apiCall {
            authApi.register(
                RegisterRequestDto(
                    email = data.email.trim().lowercase(),
                    fullName = data.fullName.trim(),
                    password = data.password,
                    role = data.role.name
                )
            )
        }.mapCatching { dto ->
            dto.toDomain() ?: throw AppError.Unknown()
        }

    override suspend fun currentUser(): Result<User> =
        apiCall { api.me() }.mapCatching { dto ->
            val user = dto.toDomain() ?: throw AppError.Unsupported("Rol no soportado en esta versión.")
            sessionManager.updateRole(user.role)
            user
        }

    override suspend fun logout() {
        // Mejor esfuerzo: revoca las sesiones en el servidor; sin red igualmente se borra todo lo local.
        if (sessionManager.accessToken != null) apiCall { api.logout() }
        sessionManager.clear()
    }

    override suspend fun reauthenticate(password: String): Result<Unit> {
        val email = sessionManager.email
            ?: return Result.failure(AppError.Unauthorized("No hay una sesión activa."))
        return apiCall { authApi.login(email, password) }.map { tokens ->
            tokens.refreshToken?.let { sessionManager.updateTokens(tokens.accessToken, it) }
        }
    }
}
