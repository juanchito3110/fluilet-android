package com.fluilet.app.testutil

import com.fluilet.app.core.security.SecureStore
import com.fluilet.app.core.tts.SpeechEngine
import com.fluilet.app.core.tts.SpeechEngineInit
import com.fluilet.app.core.tts.SpeechEngineListener

class FakeSecureStore : SecureStore {
    val data = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = data[key] as? String
    override fun putString(key: String, value: String) { data[key] = value }
    override fun getLong(key: String, default: Long): Long = data[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { data[key] = value }
    override fun remove(vararg keys: String) { keys.forEach(data::remove) }
    override fun clear() = data.clear()
}

/** Motor de voz de prueba: registra lo que se habla y deja disparar eventos a mano. */
class FakeSpeechEngine(private val init: SpeechEngineInit = SpeechEngineInit.Ready) : SpeechEngine {
    data class Spoken(val text: String, val id: String, val rate: Float)

    override var listener: SpeechEngineListener? = null
    val spoken = mutableListOf<Spoken>()
    var stopCount = 0
    var shutdownCalled = false

    override fun initialize(onResult: (SpeechEngineInit) -> Unit) = onResult(init)
    override fun speak(text: String, utteranceId: String, rate: Float) { spoken += Spoken(text, utteranceId, rate) }
    override fun stop() { stopCount++ }
    override fun shutdown() { shutdownCalled = true }

    val last: Spoken get() = spoken.last()
    fun range(start: Int, end: Int, id: String = last.id) = listener!!.onRangeStart(id, start, end)
    fun done(id: String = last.id) = listener!!.onDone(id)
    fun error(id: String = last.id) = listener!!.onError(id)
}

// --- Repositorios falsos para pruebas de ViewModel -------------------------------------------------

class FakeReadingSessionRepository : com.fluilet.app.domain.repository.ReadingSessionRepository {
    val starts = mutableListOf<Pair<String?, com.fluilet.app.domain.model.AdaptationResult>>()
    val progress = mutableListOf<Pair<String, com.fluilet.app.domain.model.ReadingProgress>>()
    val progressRequests = mutableListOf<Pair<String?, Int>>()
    var educational: com.fluilet.app.domain.model.EducationalProgress = com.fluilet.app.domain.model.EducationalProgress.empty()
    var progressFails = false

    /** `POST /profiles/{id}/seed-demo` y `.../claim`: se registran las llamadas y se devuelve lo configurado. */
    val seedRequests = mutableListOf<String>()
    var seedResult: Result<com.fluilet.app.domain.model.EducationalProgress> =
        Result.failure(com.fluilet.app.core.network.AppError.NoConnection())
    val claimRequests = mutableListOf<Pair<String, String>>()
    var claimResult: Result<com.fluilet.app.domain.model.EducationalProgress> =
        Result.failure(com.fluilet.app.core.network.AppError.NoConnection())

    override suspend fun seedDemo(childId: String): Result<com.fluilet.app.domain.model.EducationalProgress> {
        seedRequests += childId
        return seedResult
    }

    override suspend fun claimMission(childId: String, missionId: String): Result<com.fluilet.app.domain.model.EducationalProgress> {
        claimRequests += childId to missionId
        return claimResult
    }

    override suspend fun start(childId: String?, adaptation: com.fluilet.app.domain.model.AdaptationResult): Result<String?> {
        starts += childId to adaptation
        return Result.success("reading-${starts.size}")
    }

    override suspend fun progress(readingId: String, progress: com.fluilet.app.domain.model.ReadingProgress): Result<Unit> {
        this.progress += readingId to progress
        return Result.success(Unit)
    }

    override suspend fun educationalProgress(childId: String?, baselineLevel: Int): Result<com.fluilet.app.domain.model.EducationalProgress> {
        progressRequests += childId to baselineLevel
        return if (progressFails) Result.failure(com.fluilet.app.core.network.AppError.NoConnection()) else Result.success(educational)
    }
}

class FakeChildRepository(var children: List<com.fluilet.app.domain.model.ChildProfile> = emptyList()) :
    com.fluilet.app.domain.repository.ChildRepository {
    var listCalls = 0
    override suspend fun list(): Result<List<com.fluilet.app.domain.model.ChildProfile>> { listCalls++; return Result.success(children) }
    override suspend fun create(draft: com.fluilet.app.domain.model.ChildDraft) = Result.failure<com.fluilet.app.domain.model.ChildProfile>(UnsupportedOperationException())
    override suspend fun update(id: String, draft: com.fluilet.app.domain.model.ChildDraft) = Result.failure<com.fluilet.app.domain.model.ChildProfile>(UnsupportedOperationException())
    override suspend fun updateNlpLevel(id: String, level: com.fluilet.app.domain.model.NlpLevel) = Result.failure<com.fluilet.app.domain.model.ChildProfile>(UnsupportedOperationException())
    override suspend fun delete(id: String) = Result.success(Unit)
}

class FakeTutorPin(private var configured: Boolean = false) : com.fluilet.app.domain.repository.TutorPinRepository {
    override fun isConfigured() = configured
    override fun verify(pin: String) = com.fluilet.app.domain.model.PinResult.NotConfigured
    override fun setInitialPin(pin: String): Result<Unit> { configured = true; return Result.success(Unit) }
    override fun changePin(currentPin: String, newPin: String) = com.fluilet.app.domain.model.PinResult.NotConfigured
    override fun resetAfterReauthentication(newPin: String): Result<Unit> = Result.success(Unit)
}

fun sampleQuestions() = listOf(
    com.fluilet.app.domain.model.ComprehensionQuestion("q1", "¿Qué usan los alumnos?", listOf("libros", "carros", "nubes"), 0),
    com.fluilet.app.domain.model.ComprehensionQuestion("q2", "¿Dónde están sus amigos?", listOf("en el mar", "en la escuela", "en la luna"), 1)
)

fun sampleAdaptation(
    chunks: Int = 3,
    documentId: String? = "doc-1",
    withQuiz: Boolean = false,
    category: String? = "ciencia_espacio",
    level: Int? = 2
) = com.fluilet.app.domain.model.AdaptationResult(
    title = "Cuento",
    chunks = List(chunks) { com.fluilet.app.domain.model.TextChunk(it, "Bloque número $it del cuento.") },
    metrics = com.fluilet.app.domain.model.ReadabilityMetrics(37.1, 77.1, 40.0, 42, 42, 78, 3, 105, 3),
    documentId = documentId,
    category = category,
    simplificationLevel = level,
    questions = if (withQuiz) sampleQuestions() else emptyList()
)

fun sampleChild(id: String = "child-1", name: String = "Mateo") = com.fluilet.app.domain.model.ChildProfile(
    id = id, name = name, birthDate = java.time.LocalDate.of(2018, 3, 15),
    difficulty = com.fluilet.app.domain.model.DifficultyType.DISLEXIA,
    nlpLevel = com.fluilet.app.domain.model.NlpLevel.NIVEL_3,
    palette = com.fluilet.app.domain.model.AccessiblePalette.SEPIA,
    fontSizeSp = 20f, lineSpacing = 1.8f, isActive = true
)

class FakeAdaptationRepository : com.fluilet.app.domain.repository.AdaptationRepository {
    val generateRequests = mutableListOf<Pair<com.fluilet.app.domain.model.LessonRequest, String?>>()
    /** Resultado de `adapt`; por omisión una adaptación de ejemplo sin texto original del servidor. */
    var adaptResult: com.fluilet.app.domain.model.AdaptationResult? = null
    var generateResult: Result<com.fluilet.app.domain.model.AdaptationResult> = Result.success(sampleAdaptation(withQuiz = true))
    override suspend fun adapt(source: com.fluilet.app.domain.model.AdaptationSource, nlpLevel: com.fluilet.app.domain.model.NlpLevel, childId: String?) =
        Result.success(adaptResult ?: sampleAdaptation())
    val fetchRequests = mutableListOf<String>()
    var fetchResult: Result<com.fluilet.app.domain.model.AdaptationResult> =
        Result.failure(com.fluilet.app.core.network.AppError.NotFound())

    override suspend fun fetch(id: String): Result<com.fluilet.app.domain.model.AdaptationResult> {
        fetchRequests += id
        return fetchResult
    }

    override suspend fun generate(request: com.fluilet.app.domain.model.LessonRequest, childId: String?): Result<com.fluilet.app.domain.model.AdaptationResult> {
        generateRequests += request to childId
        return generateResult
    }
}
