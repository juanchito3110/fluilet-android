package com.fluilet.app.data

import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.TimeoutOverrideInterceptor
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.repository.RemoteAdaptationRepository
import com.fluilet.app.data.repository.RemoteReadingSessionRepository
import com.fluilet.app.domain.model.ComprehensionResult
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.testutil.sampleAdaptation
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Generador de lecciones, telemetría educativa y progreso, contra el contrato real del backend. */
class LessonsAndProgressRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var adaptation: RemoteAdaptationRepository
    private lateinit var sessions: RemoteReadingSessionRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(TimeoutOverrideInterceptor()).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(FluiletApi::class.java)
        adaptation = RemoteAdaptationRepository(api)
        sessions = RemoteReadingSessionRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setBody(body).addHeader("Content-Type", "application/json")

    private val lessonJson = """
        {"document":{"id":"doc-9","original_szigriszt_score":40.0,"adapted_szigriszt_score":80.0,"score_delta":40.0,
          "original_word_count":60,"adapted_word_count":58,"original_syllable_count":150,"original_sentence_count":4,
          "syllable_count":120,"sentence_count":9,"maintenance_exception":false,"category":"ciencia_espacio","simplification_level":2},
         "adapted_text":"Los agujeros negros son lugares del espacio. Su fuerza es muy grande. Nada puede escapar de ellos. Por eso no se ven.",
         "chunks":[],"title":"Agujeros negros","category":"ciencia_espacio","simplification_level":2,
         "quality_status":"best_effort","warnings":["readability_target_not_met"],
         "comprehension_questions":[
           {"id":"q1","question":"¿Dónde están los agujeros negros?","options":["en el espacio","en el mar","en casa"],"correct_option_index":0},
           {"id":"q2","question":"¿Qué no puede escapar?","options":["la luz","el agua","el sol"],"correct_option_index":0}]}
    """.trimIndent()

    // --- Generador de lecciones ---------------------------------------------------------------

    @Test fun generateSendsExactlyTheBackendRequestAndMapsTheLesson() = runBlocking {
        server.enqueue(json(lessonJson))

        val result = adaptation.generate(LessonRequest(LessonCategory.CIENCIA_ESPACIO, "  Agujeros   negros ", NlpLevel.NIVEL_2), "child-1").getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/adaptations/generate", request.path)
        assertNull("la cabecera interna de timeout no sale a la red", request.getHeader("X-Read-Timeout-Seconds"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("category", "topic", "simplification_level", "child_id"), body.keySet())
        assertEquals("ciencia_espacio", body["category"].asString)
        assertEquals("Agujeros negros", body["topic"].asString) // espacios normalizados
        assertEquals(2, body["simplification_level"].asInt)
        assertEquals("child-1", body["child_id"].asString)

        assertEquals("Agujeros negros", result.title)
        assertEquals("ciencia_espacio", result.category)
        assertEquals(2, result.simplificationLevel)
        assertEquals("doc-9", result.documentId)
        assertTrue(result.bestEffort)
        assertEquals(2, result.questions.size)
        assertEquals("q1", result.questions[0].id)
        assertEquals(listOf("en el espacio", "en el mar", "en casa"), result.questions[0].options)
        assertEquals(0, result.questions[1].correctOptionIndex)
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test fun generateOmitsEmptyTopicAndChildForAnAdult() = runBlocking {
        server.enqueue(json(lessonJson))
        adaptation.generate(LessonRequest(LessonCategory.DEPORTES, "   ", NlpLevel.NIVEL_3), null).getOrThrow()
        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(setOf("category", "simplification_level"), body.keySet())
        assertEquals("deportes", body["category"].asString)
        assertEquals(3, body["simplification_level"].asInt)
    }

    @Test fun adaptationResponsesStaySafeWhenTheServerOmitsTheNewFields() = runBlocking {
        server.enqueue(json("""{"document":{"id":"d1"},"adapted_text":"El niño lee un cuento corto. Luego juega en el parque con sus amigos. Después come una fruta fresca.","chunks":[]}"""))

        val result = adaptation.adapt(
            com.fluilet.app.domain.model.AdaptationSource("Cuento.txt", com.fluilet.app.domain.model.SourceType.PASTED_TEXT, "hola".toByteArray()),
            NlpLevel.NIVEL_4, null
        ).getOrThrow()

        assertEquals("Cuento", result.title) // título local
        assertEquals(4, result.simplificationLevel) // nivel pedido
        assertTrue("sin mini-reto", result.questions.isEmpty())
        assertFalse(result.bestEffort)
    }

    @Test fun malformedQuestionsAreDroppedInsteadOfBreakingTheLesson() = runBlocking {
        server.enqueue(json("""{"document":{"id":"d1"},"adapted_text":"Una oración corta y clara para leer hoy. Otra oración sencilla para practicar.","chunks":[],
            "comprehension_questions":[{"id":"q1","question":"ok","options":["a","b","c"],"correct_option_index":1},
              {"id":"q2","question":"mala","options":["a"],"correct_option_index":5}]}"""))
        val result = adaptation.generate(LessonRequest(LessonCategory.TECNOLOGIA, null, NlpLevel.NIVEL_2), null).getOrThrow()
        assertEquals(listOf("q1"), result.questions.map { it.id })
    }

    @Test fun generateErrorsAreFriendly() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(adaptation.generate(LessonRequest(LessonCategory.DEPORTES, null, NlpLevel.NIVEL_2), null).exceptionOrNull() is AppError.Busy)
    }

    // --- Telemetría educativa ----------------------------------------------------------------

    @Test fun startSendsTheLessonCategoryAndLevel() = runBlocking {
        server.enqueue(json("""{"id":"r-1"}"""))
        sessions.start("c-1", sampleAdaptation(category = "deportes", level = 3)).getOrThrow()
        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("deportes", body["category"].asString)
        assertEquals(3, body["simplification_level"].asInt)
        assertEquals("doc-1", body["document_id"].asString)
    }

    @Test fun finalPatchCarriesComprehensionAndTtsSpeed() = runBlocking {
        server.enqueue(json("""{"id":"r-1"}"""))
        sessions.progress(
            "r-1",
            ReadingProgress(
                readingTimeSeconds = 120, completionPercent = 100, completed = true,
                category = "ciencia_espacio", simplificationLevel = 2,
                comprehension = ComprehensionResult(answered = 2, correct = 1), ttsSpeed = 1.2
            )
        ).getOrThrow()

        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(120, body["reading_time_seconds"].asInt)
        assertEquals(100.0, body["completion_percent"].asDouble, 0.0)
        assertTrue(body["completed"].asBoolean)
        assertEquals("ciencia_espacio", body["category"].asString)
        assertEquals(2, body["simplification_level"].asInt)
        assertEquals(2, body["questions_answered"].asInt)
        assertEquals(1, body["questions_correct"].asInt)
        assertEquals(50, body["comprehension_score"].asInt) // coincide con el cálculo del servidor
        assertEquals(1.2, body["tts_speed_used"].asDouble, 0.0)
    }

    @Test fun withoutAnswersNoComprehensionFieldsAreSent() = runBlocking {
        server.enqueue(json("""{"id":"r-1"}"""))
        sessions.progress("r-1", ReadingProgress(30, 33, false, ttsSpeed = 1.0, comprehension = ComprehensionResult(0, 0))).getOrThrow()
        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertFalse(body.has("comprehension_score"))
        assertFalse(body.has("questions_answered"))
        assertFalse(body.has("questions_correct"))
        assertEquals(1.0, body["tts_speed_used"].asDouble, 0.0)
    }

    // --- Progreso educativo ------------------------------------------------------------------

    private val progressJson = """
        {"total_lessons":3,"completed_lessons":2,"incomplete_lessons":1,"completion_rate_percent":66.67,
         "total_reading_seconds":900,"average_comprehension_score":75.0,"current_recommended_level":3,
         "level_progression":[
           {"lesson_number":1,"reading_id":"r1","recorded_at":"2026-10-01T10:00:00+00:00","category":"deportes","simplification_level":2,"completed":true,"completion_percentage":100,"comprehension_score":100,"duration_seconds":300,"original_readability_score":30.0,"adapted_readability_score":70.0},
           {"lesson_number":2,"reading_id":"r2","recorded_at":"2026-10-02T10:00:00Z","category":"tecnologia","simplification_level":3,"completed":false,"completion_percentage":40,"comprehension_score":null,"duration_seconds":100,"original_readability_score":null,"adapted_readability_score":null}],
         "badges":[{"code":"primera_leccion","title":"Primera Lección","description":"d","unlocked":true},
                   {"code":"mente_brillante","title":"Mente Brillante","description":"d","unlocked":true}]}
    """.trimIndent()

    @Test fun tutorProgressUsesTheChildEndpointAndMapsEverything() = runBlocking {
        server.enqueue(json(progressJson))

        val p = sessions.educationalProgress("c-1", baselineLevel = 3).getOrThrow()

        assertEquals("/api/v1/children/c-1/progress", server.takeRequest().path)
        assertEquals(3, p.totalLessons)
        assertEquals(2, p.completedLessons)
        assertEquals(1, p.incompleteLessons)
        assertEquals(66.67, p.completionRatePercent, 0.001)
        assertEquals(900, p.totalReadingSeconds)
        assertEquals(75.0, p.averageComprehension, 0.0)
        assertEquals(3, p.recommendedLevel)
        assertFalse(p.isEstimated)
        assertEquals(2, p.lessons.size)
        assertEquals("deportes", p.lessons[0].category)
        assertEquals(40.0, p.lessons[0].readabilityJump!!, 0.001)
        assertNull(p.lessons[1].readabilityJump)
        assertNull(p.lessons[1].comprehensionScore)
        // Las 5 insignias siempre existen; las que el servidor no envía quedan bloqueadas.
        assertEquals(5, p.badges.size)
        assertEquals(listOf(true, true, false, false, false), p.badges.map { it.unlocked })
    }

    @Test fun adultProgressUsesTheReadingsProgressEndpoint() = runBlocking {
        server.enqueue(json(progressJson))
        sessions.educationalProgress(null, baselineLevel = 2).getOrThrow()
        assertEquals("/api/v1/readings/progress", server.takeRequest().path)
    }

    private val readingsJson = """[
        {"id":"r1","child_id":"c-1","started_at":"2026-10-01T10:00:00Z","reading_time_seconds":300,"completion_percent":100.0,"completed":true,
         "category":"deportes","simplification_level":2,"comprehension_score":100,"questions_answered":2,"original_szigriszt_score":30.0,"adapted_szigriszt_score":70.0},
        {"id":"r2","child_id":"c-1","started_at":"2026-10-02T10:00:00Z","reading_time_seconds":60,"completion_percent":20.0,"completed":false,
         "category":"tecnologia","simplification_level":3,"questions_answered":0}]"""

    @Test fun whenTheProgressEndpointFailsItIsComputedFromTheReadings() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(json(readingsJson))

        val p = sessions.educationalProgress("c-1", baselineLevel = 2).getOrThrow()

        assertEquals("/api/v1/children/c-1/progress", server.takeRequest().path)
        assertEquals("/api/v1/children/c-1/readings", server.takeRequest().path)
        assertTrue(p.isEstimated)
        assertEquals(2, p.totalLessons)
        assertEquals(1, p.completedLessons)
        assertEquals(50.0, p.completionRatePercent, 0.001)
        assertEquals(360, p.totalReadingSeconds)
        assertEquals(100.0, p.averageComprehension, 0.001)
        assertEquals(3, p.recommendedLevel)
        assertEquals(listOf(true, true, false, true, false), p.badges.map { it.unlocked })
    }

    @Test fun anOlderServerThatReturnsTheLegacyListAlsoFallsBackToTheReadings() = runBlocking {
        server.enqueue(json("""[{"sessionLabel":"Sesión 1","accumulatedMinutes":5.0,"completionPercent":100}]"""))
        server.enqueue(json(readingsJson))
        val p = sessions.educationalProgress("c-1", 2).getOrThrow()
        assertTrue(p.isEstimated)
        assertEquals(2, p.totalLessons)
    }

    @Test fun adultFallbackUsesTheIndependentReadingsList() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(readingsJson))
        sessions.educationalProgress(null, 2).getOrThrow()
        server.takeRequest()
        assertEquals("/api/v1/readings?offset=0&limit=100", server.takeRequest().path)
    }

    @Test fun ifEverythingFailsTheErrorReachesTheViewModelWhichShowsAnEmptyState() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        val result = sessions.educationalProgress("c-1", 2)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
}
