package com.fluilet.app.core

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.AuthInterceptor
import com.fluilet.app.core.network.JwtClaims
import com.fluilet.app.core.network.TlsConfig
import com.fluilet.app.core.network.TokenAuthenticator
import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.remote.AuthApi
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.testutil.FakeSecureStore
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.TlsVersion
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.Base64

class NetworkTest {

    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var api: FluiletApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        session = SessionManager(FakeSecureStore())
        session.restore()
        val plain = OkHttpClient()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create())
        val authApi = retrofit.client(plain).build().create(AuthApi::class.java)
        val authed = plain.newBuilder()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(session) { authApi })
            .build()
        api = retrofit.client(authed).build().create(FluiletApi::class.java)
    }

    @After fun tearDown() = server.shutdown()

    private val userJson = """{"id":"1","email":"a@b.com","full_name":"Ana Pérez","role":"tutor","is_active":true,"is_verified":false}"""

    private fun login() = session.save(AuthSession("old-access", "refresh-1", UserRole.TUTOR, "a@b.com"))

    @Test fun interceptorAddsBearerToken() = runBlocking {
        login()
        server.enqueue(MockResponse().setBody(userJson))
        api.me()
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun expiredTokenIsRefreshedOnceAndRequestRetried() = runBlocking {
        login()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"expirado"}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"new-access","refresh_token":"refresh-2","token_type":"bearer"}"""))
        server.enqueue(MockResponse().setBody(userJson))

        val user = api.me()

        assertEquals("a@b.com", user.email)
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
        val refreshRequest = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refreshRequest.path)
        assertTrue(refreshRequest.body.readUtf8().contains("refresh-1"))
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        assertEquals("new-access", session.accessToken)
        assertEquals("refresh-2", session.refreshToken)
    }

    @Test fun rejectedRefreshClosesTheSession() = runBlocking {
        login()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Tipo de token inválido."}"""))

        val result = apiCall { api.me() }

        assertTrue(result.exceptionOrNull() is AppError.Unauthorized)
        assertEquals(SessionState.LoggedOut, session.state.value)
        assertNull(session.accessToken)
    }

    @Test fun sessionClearWipesEverything() {
        login()
        assertTrue(session.state.value is SessionState.LoggedIn)
        session.clear()
        assertNull(session.accessToken)
        assertNull(session.refreshToken)
        assertNull(session.email)
    }

    @Test fun sessionRestoresFromStore() {
        val store = FakeSecureStore()
        SessionManager(store).apply { restore(); save(AuthSession("a", "r", UserRole.ADULTO_INDEPENDIENTE, "x@y.com")) }
        val restored = SessionManager(store).apply { restore() }
        assertEquals(SessionState.LoggedIn(UserRole.ADULTO_INDEPENDIENTE, "x@y.com"), restored.state.value)
        assertEquals("a", restored.accessToken)
    }

    // --- Errores tipados ---------------------------------------------------------------

    private fun http(code: Int, body: String = "") =
        HttpException(Response.error<Any>(code, body.toResponseBody()))

    @Test fun httpErrorsAreMappedWithBackendDetail() = runBlocking {
        suspend fun mapped(code: Int, body: String) = apiCall<Unit> { throw http(code, body) }.exceptionOrNull()

        assertTrue(mapped(401, """{"detail":"Credenciales inválidas."}""") is AppError.Unauthorized)
        assertEquals("Credenciales inválidas.", (mapped(401, """{"detail":"Credenciales inválidas."}""") as AppError).userMessage)
        assertTrue(mapped(423, """{"detail":"Cuenta bloqueada"}""") is AppError.Locked)
        assertTrue(mapped(403, "") is AppError.Forbidden)
        assertTrue(mapped(404, "") is AppError.NotFound)
        assertTrue(mapped(409, """{"detail":"No fue posible completar el registro."}""") is AppError.Conflict)
        assertTrue(mapped(500, "") is AppError.Server)
        val validation = mapped(422, """{"detail":[{"loc":["body","password"],"msg":"Value error, La contraseña debe incluir al menos un dígito.","type":"value_error"}]}""")
        assertTrue(validation is AppError.Validation)
        assertEquals("La contraseña debe incluir al menos un dígito.", (validation as AppError).userMessage)
    }

    @Test fun backendSanitizedValidationErrorsBecomeFieldMessages() = runBlocking {
        suspend fun mapped(body: String) = apiCall<Unit> { throw http(422, body) }.exceptionOrNull() as AppError
        assertEquals(
            "El correo no es válido o su dominio no está permitido.",
            mapped("""{"detail":"Solicitud inválida.","errors":[{"type":"value_error","loc":["body","email"]}]}""").userMessage
        )
        assertEquals(
            "La contraseña no cumple los requisitos de seguridad.",
            mapped("""{"detail":"Solicitud inválida.","errors":[{"type":"value_error","loc":["body","password"]}]}""").userMessage
        )
        // Campo desconocido: cae al detalle genérico del servidor.
        assertEquals(
            "Solicitud inválida.",
            mapped("""{"detail":"Solicitud inválida.","errors":[{"type":"x","loc":["body","otro"]}]}""").userMessage
        )
    }

    @Test fun networkFailuresAreFriendly() = runBlocking {
        assertTrue(apiCall<Unit> { throw IOException("boom") }.exceptionOrNull() is AppError.NoConnection)
        assertTrue(apiCall<Unit> { throw java.net.SocketTimeoutException() }.exceptionOrNull() is AppError.Timeout)
        assertTrue(apiCall<Unit> { throw javax.net.ssl.SSLPeerUnverifiedException("pin") }.exceptionOrNull() is AppError.InsecureConnection)
    }

    // --- JWT / TLS -----------------------------------------------------------------------

    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"HS256"}""".toByteArray()) + "." +
            enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test fun jwtRoleClaim() {
        assertEquals(UserRole.TUTOR, JwtClaims.role(jwt("""{"sub":"1","role":"tutor"}""")))
        assertEquals(UserRole.NINO, JwtClaims.role(jwt("""{"role":"nino"}""")))
        assertNull(JwtClaims.role(jwt("""{"role":"root"}""")))
        assertNull(JwtClaims.role("demo-access-token"))
        assertNull(JwtClaims.role("a.b.c"))
    }

    @Test fun releaseConnectionSpecIsTls13OnlyAndHasNoCleartext() {
        val specs = TlsConfig.connectionSpecs(enforceTlsOnly = true)
        assertEquals(1, specs.size)
        assertEquals(listOf(TlsVersion.TLS_1_3), specs.single().tlsVersions)
        assertFalse(specs.contains(ConnectionSpec.CLEARTEXT))
    }

    @Test fun debugConnectionSpecAllowsCleartextForEmulatorOnly() {
        assertTrue(TlsConfig.connectionSpecs(enforceTlsOnly = false).contains(ConnectionSpec.CLEARTEXT))
    }

    @Test fun debugAndHttpTestBuildsAcceptTls12And13ToReachANewDroplet() {
        val tls = TlsConfig.connectionSpecs(enforceTlsOnly = false).first { it.isTls }
        assertTrue(tls.tlsVersions!!.containsAll(listOf(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)))
    }

    @Test fun theApiBaseUrlIsAValidRetrofitBaseUrlWithItsHost() {
        // Por defecto (sin FLUILET_API_BASE_URL) la compilación debug apunta al backend local visto desde el emulador.
        val url = java.net.URI(com.fluilet.app.BuildConfig.API_BASE_URL)
        assertTrue(com.fluilet.app.BuildConfig.API_BASE_URL.endsWith("/"))
        assertTrue(url.scheme == "http" || url.scheme == "https")
        assertEquals(url.host, com.fluilet.app.BuildConfig.API_HOST)
    }

    @Test fun certificatePinnerOnlyWhenPinsAreConfigured() {
        assertNull(TlsConfig.certificatePinner("api.fluilet.com", ""))
        val pin = "sha256/" + "A".repeat(43) + "="
        assertNotNull(TlsConfig.certificatePinner("api.fluilet.com", "$pin,$pin"))
        assertTrue(TlsConfig.hasPlaceholderPins("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
        assertFalse(TlsConfig.hasPlaceholderPins("sha256/k3J9xQ2m5Vt7Lw0yZb1N4cPfRdGhUeSaTiOqWvXyKzM="))
    }
}
