package com.fluilet.app.data

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.AppError
import com.fluilet.app.data.remote.AuthApi
import com.fluilet.app.data.repository.AuthRepositoryImpl
import com.fluilet.app.testutil.FakeSecureStore
import com.google.gson.JsonParser
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Contrato de los 3 endpoints públicos de recuperación de contraseña del backend a2dd836 (docs/password-recovery.md). */
class PasswordRecoveryApiTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepositoryImpl

    private val generic = "Si el correo está registrado, recibirás un código de recuperación. Revisa tu bandeja de entrada y spam. " +
        "Puedes solicitar otro después de un minuto."
    private val invalid = "El código es incorrecto, venció o ya no está disponible. Solicita uno nuevo si es necesario."

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val authApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(AuthApi::class.java)
        repository = AuthRepositoryImpl(authApi, mockk(), SessionManager(FakeSecureStore()))
    }

    @After fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json")

    private fun lastBody() = server.takeRequest().let { request ->
        assertEquals("POST", request.method)
        assertNull("endpoint público: sin token", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        request.path to JsonParser.parseString(request.body.readUtf8()).asJsonObject
    }

    @Test fun forgotPasswordSendsOnlyTheNormalizedEmail() = runBlocking {
        server.enqueue(json(200, """{"message":"$generic"}"""))

        val message = repository.requestPasswordReset("  Mama@Example.com ").getOrThrow()

        val (path, body) = lastBody()
        assertEquals("/api/v1/auth/forgot-password", path)
        assertEquals(setOf("email"), body.keySet())
        assertEquals("mama@example.com", body["email"].asString)
        assertEquals("el mismo mensaje genérico exista o no la cuenta", generic, message)
    }

    @Test fun verifySendsTheCodeAsTextKeepingLeadingZeros() = runBlocking {
        server.enqueue(json(200, """{"valid":true,"message":"Código válido. Ya puedes establecer tu nueva contraseña."}"""))

        val message = repository.verifyResetCode("mama@example.com", "004821").getOrThrow()

        val (path, body) = lastBody()
        assertEquals("/api/v1/auth/verify-reset-code", path)
        assertEquals(setOf("email", "code"), body.keySet())
        assertTrue("el código viaja como texto", body["code"].asJsonPrimitive.isString)
        assertEquals("004821", body["code"].asString)
        assertTrue(message.startsWith("Código válido"))
    }

    @Test fun resetSendsTheNewPasswordWithTheBackendFieldName() = runBlocking {
        server.enqueue(json(200, """{"message":"Tu contraseña se actualizó. Inicia sesión con tu nueva contraseña."}"""))

        val message = repository.resetPassword("mama@example.com", "004821", "UnaClaveNueva2026!").getOrThrow()

        val (path, body) = lastBody()
        assertEquals("/api/v1/auth/reset-password", path)
        assertEquals(setOf("email", "code", "new_password"), body.keySet())
        assertEquals("UnaClaveNueva2026!", body["new_password"].asString)
        assertEquals("004821", body["code"].asString)
        assertTrue(message.contains("Inicia sesión"))
    }

    @Test fun a400MeansAWrongExpiredUsedOrBlockedCodeWithTheBackendMessage() = runBlocking {
        server.enqueue(json(400, """{"detail":"$invalid"}"""))
        server.enqueue(json(400, """{"detail":"$invalid"}"""))

        val verify = repository.verifyResetCode("mama@example.com", "111111").exceptionOrNull()
        val reset = repository.resetPassword("mama@example.com", "111111", "UnaClaveNueva2026!").exceptionOrNull()

        listOf(verify, reset).forEach { error ->
            assertTrue("$error", error is AppError.InvalidResetCode)
            assertEquals(invalid, (error as AppError).userMessage)
        }
    }

    @Test fun a422PointsAtTheWrongFieldWithoutEchoingIt() = runBlocking {
        val body = """{"detail":"Solicitud inválida.","errors":[{"type":"value_error",
            "message":"La contraseña debe tener de 10 a 72 caracteres.","loc":["body","new_password"]}]}"""
        server.enqueue(json(422, body))
        server.enqueue(json(422, """{"detail":"Solicitud inválida.","errors":[{"type":"string_pattern_mismatch","loc":["body","code"]}]}"""))

        val password = repository.resetPassword("mama@example.com", "004821", "UnaClaveNueva2026!").exceptionOrNull()
        val code = repository.verifyResetCode("mama@example.com", "004821").exceptionOrNull()

        assertTrue("un 422 no es un código inválido: $password", password is AppError.Validation)
        assertTrue((password as AppError).userMessage.contains("contraseña nueva"))
        assertEquals("El código debe contener exactamente 6 dígitos numéricos.", (code as AppError).userMessage)
    }

    @Test fun theAuthRateLimitIsFriendly() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "60").setBody("""{"detail":"Límite de solicitudes alcanzado."}"""))
        assertTrue(repository.requestPasswordReset("mama@example.com").exceptionOrNull() is AppError.TooManyRequests)
    }

    @Test fun anUnexpectedInvalidVerificationIsTreatedAsAnInvalidCode() = runBlocking {
        server.enqueue(json(200, """{"valid":false,"message":"$invalid"}"""))
        assertTrue(repository.verifyResetCode("mama@example.com", "004821").exceptionOrNull() is AppError.InvalidResetCode)
    }
}
