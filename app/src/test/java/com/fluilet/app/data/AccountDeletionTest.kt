package com.fluilet.app.data

import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.repository.AuthRepositoryImpl
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.presentation.home.AccountDeletion
import com.fluilet.app.testutil.FakeSecureStore
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * "Eliminar mi cuenta y datos" (requisito de Google Play): `DELETE /api/v1/users/me` y, solo si el servidor lo
 * confirma, se borra todo lo guardado en el teléfono.
 */
class AccountDeletionTest {

    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: AuthRepositoryImpl

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(FluiletApi::class.java)
        session = SessionManager(FakeSecureStore()).apply {
            restore()
            save(AuthSession("access", "refresh", UserRole.ADULTO_INDEPENDIENTE, "ana@example.com"))
        }
        repository = AuthRepositoryImpl(mockk(), api, session)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun deletingTheAccountCallsTheServerAndThenWipesThePhone() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.deleteAccount()

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/users/me", request.path)
        assertEquals(SessionState.LoggedOut, session.state.value)
        assertNull("sin tokens en el teléfono", session.accessToken)
    }

    @Test fun ifTheServerFailsNothingIsDeleted() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"Servicio no disponible"}"""))

        val result = repository.deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(session.state.value is SessionState.LoggedIn)
        assertEquals("access", session.accessToken)
    }

    @Test fun theConfirmationNeedsTheWordEliminar() {
        assertTrue(AccountDeletion.confirmed("ELIMINAR"))
        assertTrue(AccountDeletion.confirmed("  eliminar "))
        assertFalse(AccountDeletion.confirmed("ELIMINA"))
        assertFalse(AccountDeletion.confirmed(""))
        assertFalse(AccountDeletion.confirmed("no eliminar"))
    }
}
