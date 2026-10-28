package com.fluilet.app.data

import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.TimeoutOverrideInterceptor
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.repository.AdminRepositoryImpl
import com.fluilet.app.data.repository.LocalAdaptationRepository
import com.fluilet.app.data.repository.RemoteAdaptationRepository
import com.fluilet.app.data.repository.RemoteModelInfoRepository
import com.fluilet.app.data.repository.ResilientAdaptationRepository
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.model.ModelAudit
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.SourceType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Contrato del backend 42482e0 con respuestas REALES capturadas del servidor (`src/test/resources/contracts`):
 * la lección demo "Agujeros negros" de Diego (`GET /adaptations/{id}`) y `GET /adaptations/model-info`.
 */
class RetrainedModelContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: FluiletApi
    private lateinit var adaptation: RemoteAdaptationRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(TimeoutOverrideInterceptor()).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(FluiletApi::class.java)
        adaptation = RemoteAdaptationRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contracts/$name")) { name }.readText()

    private fun json(body: String) = MockResponse().setBody(body).addHeader("Content-Type", "application/json")

    // --- Adaptación completa ------------------------------------------------------------------------------

    @Test fun aRetainedLessonMapsEveryFieldOfTheRetrainedEngine() = runBlocking {
        server.enqueue(json(fixture("adaptation_42482e0.json")))

        val lesson = adaptation.fetch("7c634970-6b33-43a2-90b8-d098aa677faa").getOrThrow()

        assertEquals("/api/v1/adaptations/7c634970-6b33-43a2-90b8-d098aa677faa", server.takeRequest().path)
        assertEquals("Agujeros negros", lesson.title)
        assertTrue(lesson.originalText!!.startsWith("Los agujeros negros son regiones del espacio"))
        assertEquals(56.43970588235297, lesson.metrics.originalSzigrisztScore, 1e-9)
        assertEquals(61.84269230769231, lesson.metrics.adaptedSzigrisztScore, 1e-9)
        assertEquals(5.402986425339343, lesson.readabilityGain!!, 1e-9)
        assertEquals(5.402986425339343, lesson.gain, 1e-9)
        assertEquals("Normal", lesson.readabilityLevel)
        assertEquals(17.0, lesson.metrics.originalWordsPerSentence, 1e-9)
        assertEquals(15.6, lesson.metrics.adaptedWordsPerSentence, 1e-9)
        assertEquals(2.1411764705882352, lesson.metrics.originalSyllablesPerWord, 1e-9)
        assertEquals(2.076923076923077, lesson.metrics.adaptedSyllablesPerWord, 1e-9)
        assertEquals(4, lesson.targetLevel)
        assertEquals(true, lesson.levelProgressionMet)

        assertEquals(LexicalReplacement("absorbe de forma inevitable", "se traga"), lesson.lexicalReplacements[2])
        assertTrue(lesson.lexicalReplacements.size >= 4)

        assertEquals("context-ranker-es-v2.3", lesson.modelVersion)
        assertEquals("context-ranker-v2.3", lesson.checkpointName)
        assertEquals("pedagogical-es-v2.3", lesson.engineVersion)
        assertTrue(lesson.isRetrainedModel)
        assertFalse(lesson.isLocalEngine)
    }

    @Test fun theFiveLevelPreviewsArrivePartiallyAndAreCompletedWithTheFormula() = runBlocking {
        server.enqueue(json(fixture("adaptation_42482e0.json")))
        val previews = adaptation.fetch("doc").getOrThrow().levelPreviews

        assertEquals(listOf(1, 2, 3, 4, 5), previews.map { it.level })
        assertEquals(listOf("Muy fácil", "Bastante fácil", "Bastante fácil", "Normal", "Normal"), previews.map { it.readabilityLevel })
        val scores = previews.map { it.readabilityScore }
        assertTrue("progresión Nivel 1 > ... > Nivel 5: $scores", scores.zipWithNext().all { (a, b) -> a > b })
        assertEquals(8.0, previews[0].avgSentenceLength, 1e-9)
        // El servidor no envía syllables_per_word por variante: se despeja de su puntaje y su longitud de oración.
        assertEquals(1.8541666666666667, previews[0].syllablesPerWord, 1e-9)
        assertEquals(2.076923076923077, previews[3].syllablesPerWord, 1e-9)
        assertTrue(previews[0].adaptedText.startsWith("Un agujero negro es una zona del espacio."))
        assertTrue(previews.all { it.fromServer })
        assertTrue("Nivel 1 dentro de su banda 82–100", previews[0].inBand)
    }

    @Test fun olderServersWithoutTheNewFieldsStillMapSafely() = runBlocking {
        server.enqueue(json("""{"document":{"id":"d1"},"adapted_text":"El niño lee un cuento corto. Luego juega en el parque.","chunks":[]}"""))
        val lesson = adaptation.fetch("d1").getOrThrow()
        assertTrue(lesson.levelPreviews.isEmpty())
        assertTrue(lesson.lexicalReplacements.isEmpty())
        assertNull(lesson.modelVersion)
        assertFalse(lesson.isRetrainedModel)
    }

    @Test fun malformedPreviewsAndEmptyReplacementsAreDropped() = runBlocking {
        server.enqueue(json("""{"document":{"id":"d1"},"adapted_text":"Texto corto para leer.","chunks":[],
            "lexical_replacements":[{"original":"poseía","adapted":"tenía"},{"original":"","adapted":"x"},{"original":"Hola","adapted":"hola"}],
            "level_previews":[{"level":9,"adapted_text":"x","readability_score":80},{"level":2,"adapted_text":"  "},
              {"level":3,"adapted_text":"Una frase simple. Otra frase.","readability_score":70.5,"avg_sentence_length":3.0}],
            "model_version":"legacy_not_retrained"}"""))
        val lesson = adaptation.fetch("d1").getOrThrow()
        assertEquals(listOf(LexicalReplacement("poseía", "tenía")), lesson.lexicalReplacements)
        assertEquals(listOf(3), lesson.levelPreviews.map { it.level })
        assertEquals("Bastante fácil", lesson.levelPreviews.single().readabilityLevel)
        assertFalse("una versión legada no se presenta como el modelo reentrenado", lesson.isRetrainedModel)
    }

    // --- model-info -----------------------------------------------------------------------------------------

    @Test fun modelInfoMapsTheAcceptanceAuditOfTheRetrainedModel() = runBlocking {
        server.enqueue(json(fixture("model_info_42482e0.json")))
        val audit = RemoteModelInfoRepository(api).audit()

        assertEquals("/api/v1/adaptations/model-info", server.takeRequest().path)
        assertTrue(audit.fromServer)
        assertTrue(audit.active)
        assertTrue(audit.acceptanceCriteriaMet)
        assertTrue(audit.allBandsMet)
        assertEquals("context-ranker-es-v2.3", audit.modelVersion)
        assertEquals("context-ranker-v2.3", audit.checkpointName)
        assertEquals(7704, audit.datasetV1Size)
        assertEquals(4738, audit.datasetV2Size)
        assertEquals(36, audit.epochs)
        assertEquals(44.54384786845761, audit.baselineSari, 1e-12)
        assertEquals(44.557636124673856, audit.retrainedSari, 1e-12)
        assertEquals("73.25%", audit.lexicalAccuracyText)
        assertEquals("53.07%", audit.baselineLexicalAccuracyText)
        assertEquals("44.558", audit.retrainedSariText)
        assertEquals("44.544", audit.baselineSariText)
        assertEquals("Catálogo 31/31 · EASIER 6/6 · Controlados 6/6 · Demos 15/15", audit.bandsSummary)
        assertTrue(audit.fiveBandsVerified)
        assertEquals("67e79bf3c13381cc8d000575e6e57b4f9787f895cbfb8cfc4569fb9ae3ba2507", audit.artifactSha256)
        assertEquals("los valores en vivo coinciden con los verificados de 42482e0", ModelAudit.VERIFIED_42482E0.copy(fromServer = true), audit)
    }

    @Test fun modelInfoIsAskedOnceAndFallsBackToTheVerifiedValuesWithoutServer() = runBlocking {
        server.enqueue(json(fixture("model_info_42482e0.json")))
        val repository = RemoteModelInfoRepository(api)
        val first = repository.audit()
        assertSame("en memoria tras la primera respuesta", first, repository.audit())
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(500))
        val offline = RemoteModelInfoRepository(api).audit()
        assertFalse(offline.fromServer)
        assertEquals(ModelAudit.VERIFIED_42482E0, offline)
    }

    @Test fun aServerWithoutAnActiveModelIsShownAsSuchNotAsVerified() = runBlocking {
        server.enqueue(json("""{"model_version":"unavailable","checkpoint_name":null,"active":false}"""))
        val audit = RemoteModelInfoRepository(api).audit()
        assertTrue(audit.fromServer)
        assertFalse(audit.active)
        assertFalse(audit.acceptanceCriteriaMet)
        assertFalse(audit.fiveBandsVerified)
    }

    @Test fun theAdminOverviewCarriesTheSameModelAudit() = runBlocking {
        val modelInfo = fixture("model_info_42482e0.json")
        server.enqueue(json("""{"total_tutors":1,"total_profiles":4,"total_adaptations":24,"total_reading_sessions":15,"average_comprehension":82.5,"users":[],"profiles":[],"model_info":$modelInfo}"""))
        val overview = AdminRepositoryImpl(api).overview().getOrThrow()
        assertTrue(overview.modelAudit!!.acceptanceCriteriaMet)
        assertEquals("4,738", overview.modelAudit!!.datasetPairs)

        server.enqueue(json("""{"total_tutors":1,"total_profiles":0,"total_adaptations":0,"total_reading_sessions":0,"average_comprehension":0,"users":[],"profiles":[]}"""))
        assertNull("servidor anterior sin model_info", AdminRepositoryImpl(api).overview().getOrThrow().modelAudit)
    }

    // --- Biblioteca del niño ------------------------------------------------------------------------------------

    @Test fun theChildLibraryListsItsLessonsForOpening() = runBlocking {
        server.enqueue(json("""[{"id":"doc-1","category":"ciencia_espacio","simplification_level":4,"original_szigriszt_score":56.44,"adapted_szigriszt_score":61.84,"maintenance_exception":false},
            {"id":"doc-2","category":"deportes","simplification_level":3,"maintenance_exception":false}]"""))
        val items = adaptation.library("child-diego").getOrThrow()
        assertEquals("/api/v1/children/child-diego/library", server.takeRequest().path)
        assertEquals(listOf("doc-1", "doc-2"), items.map { it.documentId })
        assertEquals(4, items[0].level)
        assertEquals(61.84, items[0].adaptedScore!!, 1e-9)
        assertNull(items[1].originalScore)
    }

    // --- Respaldo offline ---------------------------------------------------------------------------------------

    private val remote = mockk<RemoteAdaptationRepository>()
    private val resilient by lazy { ResilientAdaptationRepository(remote, LocalAdaptationRepository()) }
    private val pasted = AdaptationSource(
        "Texto pegado", SourceType.PASTED_TEXT,
        ("Los científicos utilizan numerosos instrumentos complejos para observar el universo, aunque posteriormente " +
            "deben comprender los datos que obtienen mediante cálculos fundamentales que realizan durante varias semanas de trabajo.").toByteArray()
    )

    @Test fun withoutNetworkTheTextIsAdaptedOnTheDeviceWithFiveVariants() = runBlocking {
        coEvery { remote.adapt(any(), any(), any(), any()) } returns Result.failure(AppError.NoConnection())

        val result = resilient.adapt(pasted, NlpLevel.NIVEL_2, "child-1", AgeGroup.SIX_TO_EIGHT).getOrThrow()

        assertTrue(result.isLocalEngine)
        assertFalse(result.isRetrainedModel)
        assertNull("sin documento del servidor no hay sesión remota", result.documentId)
        assertEquals(listOf(1, 2, 3, 4, 5), result.levelPreviews.map { it.level })
        assertEquals(5, result.levelPreviews.map { it.adaptedText }.toSet().size)
        assertTrue(result.gain > 0)
        assertTrue(result.lexicalReplacements.any { it.original == "utilizan" && it.adapted == "usan" })
        assertTrue(result.originalText!!.startsWith("Los científicos"))
    }

    @Test fun aTimeoutAlsoFallsBackButServerAnswersAreNeverDisguised() = runBlocking {
        coEvery { remote.adapt(any(), any(), any(), any()) } returns Result.failure(AppError.Timeout())
        assertTrue(resilient.adapt(pasted, NlpLevel.NIVEL_3, null).getOrThrow().isLocalEngine)

        coEvery { remote.adapt(any(), any(), any(), any()) } returns Result.failure(AppError.Validation("El documento no contiene texto válido."))
        assertTrue(resilient.adapt(pasted, NlpLevel.NIVEL_3, null).exceptionOrNull() is AppError.Validation)

        coEvery { remote.adapt(any(), any(), any(), any()) } returns Result.failure(AppError.Busy())
        assertTrue(resilient.adapt(pasted, NlpLevel.NIVEL_3, null).exceptionOrNull() is AppError.Busy)
    }

    @Test fun aPdfWithoutNetworkKeepsTheNetworkError() = runBlocking {
        coEvery { remote.adapt(any(), any(), any(), any()) } returns Result.failure(AppError.NoConnection())
        val pdf = AdaptationSource("a.pdf", SourceType.PDF, byteArrayOf(0x25, 0x50, 0x44, 0x46))
        assertTrue(resilient.adapt(pdf, NlpLevel.NIVEL_3, null).exceptionOrNull() is AppError.NoConnection)
    }

    @Test fun withoutNetworkATopicLessonComesFromTheLocalBankWhenItExists() = runBlocking {
        coEvery { remote.generate(any(), any()) } returns Result.failure(AppError.NoConnection())

        val lesson = resilient.generate(LessonRequest(LessonCategory.ANIMALES_NATURALEZA, null, NlpLevel.NIVEL_1, AgeGroup.SIX_TO_EIGHT), "child-1").getOrThrow()
        assertEquals("Biología Marina", lesson.title)
        assertEquals(1, lesson.targetLevel)
        assertEquals(5, lesson.levelPreviews.size)
        assertTrue(lesson.isLocalEngine)

        assertTrue(
            resilient.generate(LessonRequest(LessonCategory.TECNOLOGIA, null, NlpLevel.NIVEL_1), null).exceptionOrNull() is AppError.NoConnection
        )
    }
}
