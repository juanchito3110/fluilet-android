package com.fluilet.app.di

import android.util.Log
import com.fluilet.app.BuildConfig
import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.AuthInterceptor
import com.fluilet.app.core.network.TimeoutOverrideInterceptor
import com.fluilet.app.core.network.TlsConfig
import com.fluilet.app.core.network.TokenAuthenticator
import com.fluilet.app.data.remote.AuthApi
import com.fluilet.app.data.remote.FluiletApi
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val CONNECT_TIMEOUT_SECONDS = 15L

// Timeout general. La adaptación NLP declara el suyo (ver TimeoutOverrideInterceptor).
private const val IO_TIMEOUT_SECONDS = 30L
private const val PLAIN_CLIENT = "plain"
private const val TAG = "NetworkModule"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /**
     * Cliente base: TLS 1.3 + pinning en release. Sin caché HTTP (cero retención local de texto).
     * Sin Authenticator: lo usa [AuthApi] y evita recursión durante el refresh de token.
     */
    @Provides
    @Singleton
    @Named(PLAIN_CLIENT)
    fun providePlainOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionSpecs(TlsConfig.connectionSpecs(BuildConfig.ENFORCE_TLS_ONLY))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(null)

        TlsConfig.certificatePinner(BuildConfig.API_HOST, BuildConfig.CERT_PINS)?.let { pinner ->
            builder.certificatePinner(pinner)
            if (TlsConfig.hasPlaceholderPins(BuildConfig.CERT_PINS)) {
                Log.e(TAG, "Certificate pinning con PLACEHOLDERS: reemplazar fluilet.certPins por los hashes SPKI reales.")
            }
        }

        if (BuildConfig.DEBUG) {
            // HEADERS (no BODY): el cuerpo del login contiene la contraseña.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                    redactHeader("Authorization")
                    redactHeader("Cookie")
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(@Named(PLAIN_CLIENT) client: OkHttpClient, gson: Gson): AuthApi =
        retrofit(client, gson).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthedOkHttpClient(
        @Named(PLAIN_CLIENT) plain: OkHttpClient,
        authInterceptor: AuthInterceptor,
        sessionManager: SessionManager,
        authApi: Lazy<AuthApi>
    ): OkHttpClient = plain.newBuilder()
        .addInterceptor(TimeoutOverrideInterceptor())
        .addInterceptor(authInterceptor)
        .authenticator(TokenAuthenticator(sessionManager) { authApi.get() })
        .build()

    @Provides
    @Singleton
    fun provideFluiletApi(client: OkHttpClient, gson: Gson): FluiletApi =
        retrofit(client, gson).create(FluiletApi::class.java)

    private fun retrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
}
