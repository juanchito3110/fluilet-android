package com.fluilet.app.data

import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.TimeoutOverrideInterceptor
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.repository.RemoteAdaptationRepository
import com.fluilet.app.data.repository.RemoteReadingSessionRepository
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.model.SourceType
import com.fluilet.app.domain.model.TextChunk
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

/** Verifica rutas, cuerpos y mapeo contra el contrato real del backend (docs/api-contract.md). */
class RemoteRepositoriesTest {

    private lateinit var server: MockWebServer
    private lateinit var api: FluiletApi
    private lateinit var adaptation: RemoteAdaptationRepository
    private lateinit var sessions: RemoteReadingSessionRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(TimeoutOverrideInterceptor()).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(FluiletApi::class.java)
        adaptation = RemoteAdaptationRepository(api)
        sessions = RemoteReadingSessionRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private val adaptedText = "El niño lee un cuento corto. Luego juega en el parque con sus amigos. Después come una fruta fresca. " +
        "Al final duerme tranquilo en su cama."

    private val adaptationJson = """
        {"document":{"id":"doc-1","original_szigriszt_score":31.2,"adapted_szigriszt_score":78.9,"score_delta":47.7,
          "original_word_count":120,"adapted_word_count":110,"original_syllable_count":300,"original_sentence_count":4,
          "syllable_count":240,"sentence_count":9,"maintenance_exception":false,"file_type":"txt","file_size_bytes":900,
          "word_count":120,"chunk_count":1,"status":"completed","adaptation_method":"mt5","uploaded_at":"2026-01-01T10:00:00Z"},
         "adapted_text":"$adaptedText",
         "chunks":[{"chunk_index":0,"original_szigriszt_score":31.2,"adapted_szigriszt_score":78.9}]}
    """.trimIndent()

    private fun source(type: SourceType, bytes: ByteArray) = AdaptationSource("Cuento.txt", type, bytes)

    @Test fun pastedTextGoesAsJsonToAdaptationsText() = runBlocking {
        server.enqueue(MockResponse().setBody(adaptationJson).addHeader("Content-Type", "application/json"))

        val result = adaptation.adapt(
            source(SourceType.PASTED_TEXT, "hola mundo".toByteArray()), NlpLevel.NIVEL_4, "child-9", com.fluilet.app.domain.model.AgeGroup.TWELVE_PLUS
        ).getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/adaptations/text", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"text\":\"hola mundo\""))
        assertTrue(body, body.contains("\"nivel_simplificacion_nlp\":4"))
        assertTrue(body, body.contains("\"child_id\":\"child-9\""))
        assertTrue("alias level con el mismo nivel: $body", body.contains("\"level\":4"))
        assertTrue("edad didáctica del perfil activo: $body", body.contains("\"age_group\":\"12+\""))
        assertNull("la cabecera interna de timeout no debe salir a la red", request.getHeader("X-Read-Timeout-Seconds"))

        assertEquals("doc-1", result.documentId)
        assertEquals("Cuento", result.title)
        assertTrue(result.chunks.isNotEmpty())
        assertEquals(78.9, result.metrics.adaptedSzigrisztScore, 0.001)
        assertEquals(47.7, result.metrics.scoreDelta, 0.001)
        assertEquals(240, result.metrics.syllableCount)
        assertEquals(300, result.metrics.originalSyllableCount)
        assertEquals(9, result.metrics.sentenceCount)
    }

    @Test fun textWithoutChildOmitsChildId() = runBlocking {
        server.enqueue(MockResponse().setBody(adaptationJson).addHeader("Content-Type", "application/json"))
        adaptation.adapt(source(SourceType.PASTED_TEXT, "hola".toByteArray()), NlpLevel.NIVEL_3, null).getOrThrow()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(!body.contains("child_id"))
        assertTrue("sin perfil no hay age_group", !body.contains("age_group"))
    }

    @Test fun pdfGoesAsRawBinaryBodyNotMultipart() = runBlocking {
        server.enqueue(MockResponse().setBody(adaptationJson).addHeader("Content-Type", "application/json"))
        val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34)

        adaptation.adapt(source(SourceType.PDF, pdf), NlpLevel.NIVEL_2, "child-9", com.fluilet.app.domain.model.AgeGroup.SIX_TO_EIGHT).getOrThrow()

        val request = server.takeRequest()
        assertEquals("/api/v1/adaptations/document?nivel_simplificacion_nlp=2&level=2&age_group=6-8&child_id=child-9", request.path)
        assertEquals("application/pdf", request.getHeader("Content-Type"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/pdf"))
        assertEquals(pdf.toList(), request.body.readByteArray().toList())
    }

    @Test fun txtFileGoesAsTextPlain() = runBlocking {
        server.enqueue(MockResponse().setBody(adaptationJson).addHeader("Content-Type", "application/json"))
        adaptation.adapt(source(SourceType.TXT, "contenido".toByteArray()), NlpLevel.NIVEL_3, null).getOrThrow()
        val request = server.takeRequest()
        assertEquals("/api/v1/adaptations/document?nivel_simplificacion_nlp=3&level=3", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("text/plain"))
    }

    @Test fun serverErrorsBecomeFriendlyAppErrors() = runBlocking {
        suspend fun failure(code: Int, body: String = "") : Throwable? {
            server.enqueue(MockResponse().setResponseCode(code).setBody(body).addHeader("Content-Type", "application/json"))
            return adaptation.adapt(source(SourceType.PASTED_TEXT, "x".toByteArray()), NlpLevel.NIVEL_3, null).exceptionOrNull()
        }
        val unprocessable = failure(422, """{"detail":"No fue posible obtener una adaptación válida para este documento."}""")
        assertTrue(unprocessable is AppError.Validation)
        assertEquals("No fue posible obtener una adaptación válida para este documento.", (unprocessable as AppError).userMessage)
        assertTrue(failure(503) is AppError.Busy)
        assertTrue(failure(413) is AppError.TooLarge)
        assertTrue(failure(415) is AppError.Unsupported)
        assertTrue(failure(429) is AppError.TooManyRequests)
    }

    @Test fun emptyAdaptedTextIsAServerError() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"document":{"id":"d"},"adapted_text":"  ","chunks":[]}""").addHeader("Content-Type", "application/json"))
        assertTrue(adaptation.adapt(source(SourceType.PASTED_TEXT, "x".toByteArray()), NlpLevel.NIVEL_3, null).exceptionOrNull() is AppError.Server)
    }

    // --- Sesiones de lectura -------------------------------------------------------------

    private val adapted = AdaptationResult(
        "t", listOf(TextChunk(0, "a")), ReadabilityMetrics(1.0, 2.0, 1.0, 1, 1, 1, 1), documentId = "doc-1"
    )

    @Test fun startOpensReadingForChildAndDocument() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"r-1","child_id":"c-1","document_id":"doc-1","started_at":"2026-01-01T10:00:00Z","reading_time_seconds":0,"completion_percent":0,"completed":false}""").addHeader("Content-Type", "application/json"))
        val id = sessions.start("c-1", adapted).getOrThrow()
        val request = server.takeRequest()
        assertEquals("/api/v1/children/c-1/readings", request.path)
        assertEquals("""{"document_id":"doc-1"}""", request.body.readUtf8())
        assertEquals("r-1", id)
    }

    @Test fun independentAdultStartsOwnReadingWithoutChild() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"r-9","child_id":null,"reader_user_id":"u-1","document_id":"doc-1","started_at":"2026-01-01T10:00:00Z","reading_time_seconds":0,"completion_percent":0,"completed":false}""").addHeader("Content-Type", "application/json"))
        val id = sessions.start(null, adapted).getOrThrow()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/readings", request.path)
        assertEquals("""{"document_id":"doc-1"}""", request.body.readUtf8())
        assertEquals("r-9", id)
    }

    @Test fun localDocumentsHaveNoRemoteTracking() = runBlocking {
        assertNull(sessions.start("c-1", adapted.copy(documentId = null)).getOrThrow())
        assertNull(sessions.start(null, adapted.copy(documentId = null)).getOrThrow())
        assertEquals(0, server.requestCount)
    }

    @Test fun progressPatchesOnlyAllowedFields() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"r-1"}""").addHeader("Content-Type", "application/json"))
        sessions.progress("r-1", ReadingProgress(125, 40, completed = false)).getOrThrow()
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/readings/r-1", request.path)
        assertEquals("""{"reading_time_seconds":125,"completion_percent":40.0,"completed":false}""", request.body.readUtf8())
    }

    @Test fun completedRequiresFullPercentOnTheClientToo() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"r-1"}""").addHeader("Content-Type", "application/json"))
        sessions.progress("r-1", ReadingProgress(10, 90, completed = true)).getOrThrow()
        assertTrue(server.takeRequest().body.readUtf8().contains("\"completed\":false"))
    }
}
