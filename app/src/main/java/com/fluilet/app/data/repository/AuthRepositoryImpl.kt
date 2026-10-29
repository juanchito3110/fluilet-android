package com.fluilet.app.data.repository

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.JwtClaims
import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.remote.AuthApi
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.ForgotPasswordRequestDto
import com.fluilet.app.data.remote.dto.RegisterRequestDto
import com.fluilet.app.data.remote.dto.ResetPasswordRequestDto
import com.fluilet.app.data.remote.dto.VerifyResetCodeRequestDto
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
        if (tokens.isMasterAdmin == true) return UserRole.MASTER_ADMIN
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

    override suspend fun requestPasswordReset(email: String): Result<String> =
        apiCall { authApi.forgotPassword(ForgotPasswordRequestDto(email.trim().lowercase())) }.map { it.message }

    override suspend fun verifyResetCode(email: String, code: String): Result<String> =
        apiCall { authApi.verifyResetCode(VerifyResetCodeRequestDto(email.trim().lowercase(), code)) }
            .mapCatching { if (it.valid) it.message else throw AppError.InvalidResetCode(it.message) }
            .invalidCodeOn400()

    override suspend fun resetPassword(email: String, code: String, newPassword: String): Result<String> =
        apiCall { authApi.resetPassword(ResetPasswordRequestDto(email.trim().lowercase(), code, newPassword)) }
            .map { it.message }
            .invalidCodeOn400()

    /** En la recuperación, un 400 siempre significa código incorrecto, vencido, usado o bloqueado (un 422 es otro dato). */
    private fun Result<String>.invalidCodeOn400(): Result<String> {
        val error = exceptionOrNull()
        return if (error is AppError.Validation && error.httpStatus == 400) Result.failure(AppError.InvalidResetCode(error.userMessage)) else this
    }
}
