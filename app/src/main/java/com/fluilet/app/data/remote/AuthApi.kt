package com.fluilet.app.data.remote

import com.fluilet.app.data.remote.dto.AuthMessageDto
import com.fluilet.app.data.remote.dto.ForgotPasswordRequestDto
import com.fluilet.app.data.remote.dto.RefreshRequestDto
import com.fluilet.app.data.remote.dto.RegisterRequestDto
import com.fluilet.app.data.remote.dto.ResetCodeVerificationDto
import com.fluilet.app.data.remote.dto.ResetPasswordRequestDto
import com.fluilet.app.data.remote.dto.TokenDto
import com.fluilet.app.data.remote.dto.UserDto
import com.fluilet.app.data.remote.dto.VerifyResetCodeRequestDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Endpoints SIN autenticación Bearer. Usa un cliente OkHttp aparte (sin Authenticator) para que
 * el refresh de token no pueda entrar en recursión.
 */
interface AuthApi {

    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenDto

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequestDto): UserDto

    /** Síncrono: lo invoca el [com.fluilet.app.core.network.TokenAuthenticator] en el hilo de OkHttp. */
    @POST("api/v1/auth/refresh")
    fun refresh(@Body body: RefreshRequestDto): Call<TokenDto>

    // --- Recuperación de contraseña con código de 6 dígitos por correo (públicos, sin Bearer) ---------------

    /** 200 con el mismo mensaje genérico exista o no la cuenta (no revela qué correos están registrados). */
    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequestDto): AuthMessageDto

    /** 200 si el código es válido; 400 si es incorrecto, venció, ya se usó o se bloqueó tras 5 fallos. */
    @POST("api/v1/auth/verify-reset-code")
    suspend fun verifyResetCode(@Body body: VerifyResetCodeRequestDto): ResetCodeVerificationDto

    /** Consume el código, cambia la contraseña y revoca las sesiones anteriores: hay que iniciar sesión de nuevo. */
    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequestDto): AuthMessageDto
}
