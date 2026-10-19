package com.fluilet.app.core.network

import com.fluilet.app.core.auth.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Adjunta el JWT de acceso a cada petición protegida. */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.accessToken
        val request = if (token != null && chain.request().header("Authorization") == null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
