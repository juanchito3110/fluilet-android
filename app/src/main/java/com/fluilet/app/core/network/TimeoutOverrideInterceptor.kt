package com.fluilet.app.core.network

import com.fluilet.app.data.remote.READ_TIMEOUT_HEADER
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Permite que un endpoint concreto (la adaptación NLP) declare su propio timeout de lectura con
 * `@Headers("X-Read-Timeout-Seconds: N")`, sin alargar el timeout del resto de llamadas. La
 * cabecera es interna: se elimina antes de salir a la red.
 */
class TimeoutOverrideInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val seconds = chain.request().header(READ_TIMEOUT_HEADER)?.toIntOrNull()
        val request = chain.request().newBuilder().removeHeader(READ_TIMEOUT_HEADER).build()
        val effective = if (seconds != null && seconds > 0) chain.withReadTimeout(seconds, TimeUnit.SECONDS) else chain
        return effective.proceed(request)
    }
}
