package com.fluilet.app.data.remote

import com.fluilet.app.data.remote.dto.RefreshRequestDto
import com.fluilet.app.data.remote.dto.RegisterRequestDto
import com.fluilet.app.data.remote.dto.TokenDto
import com.fluilet.app.data.remote.dto.UserDto
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
}
