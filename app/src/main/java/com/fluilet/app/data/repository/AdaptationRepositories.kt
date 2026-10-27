package com.fluilet.app.data.repository

import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.AdaptTextRequestDto
import com.fluilet.app.data.remote.dto.LessonGenerateDto
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.SourceType
import com.fluilet.app.domain.repository.AdaptationRepository
import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.text.TextAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptación real en el servidor (mT5 + Szigriszt-Pazos).
 * - Texto pegado: `POST /adaptations/text` (JSON).
 * - Archivo: `POST /adaptations/file` con el PDF/TXT como cuerpo binario crudo; el backend rechaza
 *   multipart por diseño (cero retención, sin spool a disco).
 * Si el perfil de niño viene en la petición, el servidor además asigna el documento a su biblioteca.
 */
@Singleton
class RemoteAdaptationRepository @Inject constructor(
    private val api: FluiletApi
) : AdaptationRepository {

    override suspend fun adapt(source: AdaptationSource, nlpLevel: NlpLevel, childId: String?): Result<AdaptationResult> {
        val title = source.fileName.substringBeforeLast('.').ifBlank { "Lectura" }
        return apiCall {
            when (source.type) {
                SourceType.PASTED_TEXT -> api.adaptText(
                    AdaptTextRequestDto(String(source.bytes, Charsets.UTF_8), nlpLevel.value, childId)
                )
                SourceType.TXT -> api.adaptFile(
                    source.bytes.toRequestBody("text/plain; charset=utf-8".toMediaType()), nlpLevel.value, childId
                )
                SourceType.PDF -> api.adaptFile(
                    source.bytes.toRequestBody("application/pdf".toMediaType()), nlpLevel.value, childId
                )
            }
        }.mapCatching { it.toDomain(title, nlpLevel) }
    }

    override suspend fun fetch(id: String): Result<AdaptationResult> =
        apiCall { api.adaptation(id) }.mapCatching { it.toDomain("Lectura", NlpLevel.NIVEL_3) }

    /** `POST /adaptations/generate`: el servidor elige un texto del banco y lo adapta al nivel pedido. */
    override suspend fun generate(request: LessonRequest, childId: String?): Result<AdaptationResult> =
        apiCall {
            api.generateLesson(
                LessonGenerateDto(
                    category = request.category.code,
                    topic = request.cleanTopic,
                    simplificationLevel = request.level.value,
                    childId = childId
                )
            )
        }.mapCatching { it.toDomain(title = request.category.displayName, level = request.level).copy(topic = request.cleanTopic) }
}

/**
 * Adaptación local por reglas: solo para builds con `USE_REMOTE_ADAPTATION=false` (modo offline
 * explícito de desarrollo). No produce `documentId`, así que no hay seguimiento de sesión.
 */
@Singleton
class LocalAdaptationRepository @Inject constructor() : AdaptationRepository {

    override suspend fun adapt(source: AdaptationSource, nlpLevel: NlpLevel, childId: String?): Result<AdaptationResult> =
        withContext(Dispatchers.Default) {
            if (source.type == SourceType.PDF) {
                return@withContext Result.failure(AppError.Unsupported("Los PDF se adaptan en el servidor."))
            }
            if (source.bytes.size > AdaptationSource.MAX_BYTES) return@withContext Result.failure(AppError.TooLarge())
            val text = String(source.bytes, Charsets.UTF_8)
            if (SpanishText.wordCount(text) < AdaptationSource.MIN_WORDS) {
                return@withContext Result.failure(AppError.Validation("El texto es demasiado corto o está vacío."))
            }
            Result.success(
                TextAdapter.adapt(
                    title = source.fileName.substringBeforeLast('.').ifBlank { "Lectura" },
                    original = text,
                    level = nlpLevel
                )
            )
        }

    override suspend fun generate(request: LessonRequest, childId: String?): Result<AdaptationResult> =
        Result.failure(AppError.Unsupported("Las lecciones se generan en el servidor."))
}
