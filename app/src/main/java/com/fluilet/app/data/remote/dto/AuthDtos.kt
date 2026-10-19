package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de `/auth/login` y `/auth/refresh`. El backend conserva el login form-urlencoded y
 * expondrá `role` en la respuesta; mientras no lo haga, el rol se obtiene del claim del JWT o de
 * `/users/me` (ver AuthRepositoryImpl).
 */
data class TokenDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("role") val role: String? = null
)

data class RefreshRequestDto(
    @SerializedName("refresh_token") val refreshToken: String
)

data class RegisterRequestDto(
    @SerializedName("email") val email: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("role") val role: String?,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

/** Cuerpo de error estándar de FastAPI: `detail` es texto o una lista de errores de validación. */
data class ErrorBodyDto(
    @SerializedName("detail") val detail: com.google.gson.JsonElement? = null
)
