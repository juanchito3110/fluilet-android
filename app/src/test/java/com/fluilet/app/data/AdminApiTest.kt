package com.fluilet.app.data

import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.TimeoutOverrideInterceptor
import com.fluilet.app.data.mapper.toDto
import com.fluilet.app.data.remote.AuthApi
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.repository.AdminRepositoryImpl
import com.fluilet.app.data.repository.AuthRepositoryImpl
import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.domain.model.MasterGrant
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.testutil.FakeSecureStore
import com.google.gson.Gson
import com.google.gson.JsonParser
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

/** Rutas, verbos, cuerpos y mapeo de `/api/v1/admin/...` y del login con `is_master_admin` (backend bac5798). */
class AdminApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: FluiletApi
    private lateinit var authApi: AuthApi
    private lateinit var admin: AdminRepositoryImpl

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(TimeoutOverrideInterceptor()).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(FluiletApi::class.java)
        authApi = retrofit.create(AuthApi::class.java)
        admin = AdminRepositoryImpl(api)
    }

    @After fun tearDown() = server.shutdown()

    private val overviewJson = """
        {"total_tutors":1,"total_profiles":4,"total_adaptations":24,"total_reading_sessions":15,"average_comprehension":82.5,
         "users":[
           {"user_id":"u-admin","email":"admin@fluilet.com","role":"master_admin","is_master_admin":true,"is_active":true,"profiles_count":0,"created_at":"2026-10-27T10:00:00Z"},
           {"user_id":"u-tutor","email":"tutor@fluilet.com","role":"TUTOR","is_master_admin":false,"is_active":true,"profiles_count":4,"created_at":"2026-10-27T10:00:01Z"}],
         "profiles":[
           {"profile_id":"p1","name":"Sofía","tutor_id":"u-tutor","tutor_email":"tutor@fluilet.com","is_active":true,"age_group":"6-8","current_level":1,"total_gems":45,"total_xp":40,"evolution_stage":1,"evolution_title":"Núcleo de Energía","current_streak_days":1,"completed_lessons_count":1,"unlocked_badges_count":1},
           {"profile_id":"p4","name":"Valentina","tutor_id":"u-tutor","tutor_email":"tutor@fluilet.com","is_active":true,"age_group":"12+","current_level":5,"total_gems":900,"total_xp":980,"evolution_stage":4,"evolution_title":"Sabio Cuántico","current_streak_days":12,"completed_lessons_count":6,"unlocked_badges_count":5}]}
    """.trimIndent()

    private fun json(body: String) = MockResponse().setBody(body).addHeader("Content-Type", "application/json")

    // --- overview / ensure-demo-accounts -----------------------------------------------------------

    @Test fun overviewReadsTheGlobalKpisUsersAndProfilesWithTheExactFieldNames() = runBlocking {
        server.enqueue(json(overviewJson))

        val o = admin.overview().getOrThrow()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/admin/overview", request.path)
        assertEquals(1, o.totalTutors)
        assertEquals(4, o.totalProfiles)
        assertEquals(24, o.totalAdaptations)
        assertEquals(15, o.totalReadingSessions)
        assertEquals(82.5, o.averageComprehension, 0.0)
        assertEquals(listOf("u-tutor"), o.tutors.map { it.userId }) // el maestro no es un tutor
        assertEquals(4, o.tutors.single().profilesCount)
        val valentina = o.profiles.last()
        assertEquals("tutor@fluilet.com", valentina.tutorEmail)
        assertEquals("12+", valentina.ageGroup)
        assertEquals(5, valentina.currentLevel)
        assertEquals(4, valentina.evolutionStage)
        assertEquals("Sabio Cuántico", valentina.evolutionTitle)
        assertEquals(980, valentina.totalXp)
        assertEquals(900, valentina.totalGems)
        assertEquals(12, valentina.streakDays)
    }

    @Test fun ensureDemoAccountsIsAPostThatReturnsTheSameOverviewObject() = runBlocking {
        server.enqueue(json(overviewJson))
        val o = admin.ensureDemoAccounts().getOrThrow()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/admin/ensure-demo-accounts", request.path)
        assertEquals(2, o.profiles.size)
    }

    // --- grant ------------------------------------------------------------------------------------------

    private val progressJson = """
        {"total_lessons":1,"completed_lessons":1,"incomplete_lessons":0,"completion_rate_percent":100.0,"total_reading_seconds":60,
         "average_comprehension_score":100.0,"current_recommended_level":4,"level_progression":[],"badges":[],"total_gems":245,"total_xp":340}
    """.trimIndent()

    private fun grantBody(grant: MasterGrant): String = runBlocking {
        server.enqueue(json(progressJson))
        val p = admin.grant("p1", grant).getOrThrow()
        assertEquals(245, p.totalGems)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/admin/profiles/p1/grant", request.path)
        request.body.readUtf8()
    }

    @Test fun quickGemButtonsSendAddGems() {
        MasterGrant.QUICK_GEMS.forEach { amount ->
            assertEquals("""{"add_gems":$amount}""", grantBody(MasterGrant.gems(amount)))
        }
        assertEquals(listOf(50, 200, 500), MasterGrant.QUICK_GEMS)
    }

    @Test fun exactGemsSendSetGemsAndNeverAddGems() {
        assertEquals("""{"set_gems":1000}""", grantBody(MasterGrant.exactGems(1000)))
    }

    @Test fun evolutionStageAndXpUseTheirOwnFields() {
        (1..4).forEach { stage -> assertEquals("""{"set_evolution_stage":$stage}""", grantBody(MasterGrant.stage(stage))) }
        assertEquals("""{"add_xp":100}""", grantBody(MasterGrant.xp()))
    }

    @Test fun levelAgeGroupAndUnlockAllUseTheDocumentedNames() {
        assertEquals("""{"set_level":4}""", grantBody(MasterGrant.level(4)))
        MasterGrant.AGE_GROUPS.forEach { g -> assertEquals("""{"set_age_group":"$g"}""", grantBody(MasterGrant.ageGroup(g))) }
        assertEquals("""{"unlock_all_rewards":true}""", grantBody(MasterGrant.unlockAll()))
    }

    @Test fun grantNeverSendsNullsOrAFalseUnlockFlag() {
        val json = Gson().toJson(MasterGrant(addGems = 100, setLevel = 4).toDto())
        assertEquals("""{"add_gems":100,"set_level":4}""", json)
        assertFalse(json.contains("null") || json.contains("unlock_all_rewards"))
        assertEquals(setOf("set_evolution_stage", "set_age_group", "set_streak_days"),
            JsonParser.parseString(Gson().toJson(MasterGrant(setEvolutionStage = 3, setAgeGroup = "12+", setStreakDays = 6).toDto())).asJsonObject.keySet())
    }

    // --- bajas y reset-pin ------------------------------------------------------------------------------

    @Test fun deletingAChildIsADeleteThatReturns204() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(admin.deleteProfile("p1").isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/admin/profiles/p1", request.path)
    }

    @Test fun deletingATutorIsADeleteOnTheTutorsPath() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(admin.deleteTutor("u-tutor").isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/admin/tutors/u-tutor", request.path)
    }

    @Test fun deletingTheMasterAccountIsRejectedByTheServerWith400() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"La cuenta maestra no puede eliminarse."}""").addHeader("Content-Type", "application/json"))
        assertTrue(admin.deleteTutor("u-admin").isFailure)
    }

    @Test fun resetPinPostsTheDefault1234AndNeverReturnsIt() = runBlocking {
        server.enqueue(json("""{"user_id":"u-tutor","pin_reset":true}"""))
        val result = admin.resetPin("u-tutor").getOrThrow()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/admin/tutors/u-tutor/reset-pin", request.path)
        assertEquals("""{"pin":"1234"}""", request.body.readUtf8())
        assertEquals("u-tutor", result.userId)
        assertTrue(result.pinReset)
    }

    @Test fun aNonMasterUserGetsForbiddenFromEveryAdminRoute() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Acceso exclusivo del administrador maestro."}""").addHeader("Content-Type", "application/json")) }
        assertTrue(admin.overview().exceptionOrNull() is AppError.Forbidden)
        assertTrue(admin.ensureDemoAccounts().exceptionOrNull() is AppError.Forbidden)
    }

    // --- Login: is_master_admin -------------------------------------------------------------------------

    private fun authRepo() = AuthRepositoryImpl(authApi, api, SessionManager(FakeSecureStore()))

    @Test fun loginWithIsMasterAdminOpensAMasterSession() = runBlocking {
        server.enqueue(json("""{"access_token":"a.b.c","refresh_token":"r","token_type":"bearer","role":"master_admin","is_master_admin":true}"""))
        val session = authRepo().login("admin@fluilet.com", "Master2026!").getOrThrow()
        assertEquals(UserRole.MASTER_ADMIN, session.role)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        assertTrue(request.body.readUtf8().contains("username=admin%40fluilet.com"))
    }

    @Test fun theMasterFlagWinsEvenIfTheRoleStringIsUnexpected() = runBlocking {
        server.enqueue(json("""{"access_token":"a.b.c","refresh_token":"r","role":"superuser","is_master_admin":true}"""))
        assertEquals(UserRole.MASTER_ADMIN, authRepo().login("admin@fluilet.com", "Master2026!").getOrThrow().role)
    }

    @Test fun anOrdinaryTutorLoginIsNotMasterAndTheFlagDefaultsToFalse() = runBlocking {
        server.enqueue(json("""{"access_token":"a.b.c","refresh_token":"r","role":"TUTOR","is_master_admin":false}"""))
        server.enqueue(json("""{"access_token":"a.b.c","refresh_token":"r","role":"TUTOR"}"""))
        assertEquals(UserRole.TUTOR, authRepo().login("tutor@fluilet.com", "Tutor2026!").getOrThrow().role)
        assertEquals(UserRole.TUTOR, authRepo().login("tutor@fluilet.com", "Tutor2026!").getOrThrow().role)
        assertNull(UserRole.fromWire("superuser"))
        assertTrue(UserRole.fromWire("master_admin")!!.isMasterAdmin)
    }
}
