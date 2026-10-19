package com.fluilet.app.core.network

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.data.remote.AuthApi
import com.fluilet.app.data.remote.dto.RefreshRequestDto
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/**
 * Ante un 401 intenta UN refresh del token y reintenta la petición original. Si el refresh es
 * rechazado, cierra la sesión (la navegación vuelve a Login). Serializado para que varias
 * peticiones concurrentes con 401 no disparen refreshes en paralelo.
 */
class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val authApi: () -> AuthApi
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null

        synchronized(lock) {
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentToken = sessionManager.accessToken

            // Otra petición ya renovó el token mientras esperábamos el lock.
            if (currentToken != null && currentToken != sentToken) {
                return response.request.withToken(currentToken)
            }

            val refresh = sessionManager.refreshToken ?: run {
                sessionManager.clear()
                return null
            }

            val refreshed = try {
                authApi().refresh(RefreshRequestDto(refresh)).execute()
            } catch (_: IOException) {
                return null // sin red: no cerramos sesión, se reintentará luego
            }

            val body = refreshed.body()
            return if (refreshed.isSuccessful && body != null) {
                sessionManager.updateTokens(body.accessToken, body.refreshToken ?: refresh)
                response.request.withToken(body.accessToken)
            } else {
                if (refreshed.code() in 400..499) sessionManager.clear()
                null
            }
        }
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
