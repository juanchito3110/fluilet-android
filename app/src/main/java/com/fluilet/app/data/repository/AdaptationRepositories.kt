package com.fluilet.app.data.repository

import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.apiCall
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.mapper.toLibraryItem
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.AdaptTextRequestDto
import com.fluilet.app.data.remote.dto.LessonGenerateDto
import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.domain.model.ModelAudit
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.SourceType
import com.fluilet.app.domain.repository.AdaptationRepository
import com.fluilet.app.domain.repository.ModelInfoRepository
import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.text.TextAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptación real en el servidor (modelo reentrenado + Szigriszt-Pazos).
 * - Texto pegado: `POST /adaptations/text` (JSON).
 * - Archivo: `POST /adaptations/file` con el PDF/TXT como cuerpo binario crudo; el backend rechaza
 *   multipart por diseño (cero retención, sin spool a disco).
 * Toda petición lleva el nivel (`nivel_simplificacion_nlp` y su alias `level`) y la edad didáctica del perfil
 * infantil activo (`age_group`). Si el perfil viene en la petición, el servidor además asigna el documento a su
 * biblioteca.
 */
@Singleton
class RemoteAdaptationRepository @Inject constructor(
    private val api: FluiletApi
) : AdaptationRepository {

    override suspend fun adapt(source: AdaptationSource, nlpLevel: NlpLevel, childId: String?, ageGroup: AgeGroup?): Result<AdaptationResult> {
        val title = source.fileName.substringBeforeLast('.').ifBlank { "Lectura" }
        val group = ageGroup?.code
        return apiCall {
            when (source.type) {
                SourceType.PASTED_TEXT -> api.adaptText(
                    AdaptTextRequestDto(String(source.bytes, Charsets.UTF_8), nlpLevel.value, childId, ageGroup = group)
                )
                SourceType.TXT -> api.adaptFile(
                    source.bytes.toRequestBody("text/plain; charset=utf-8".toMediaType()), nlpLevel.value, nlpLevel.value, group, childId
                )
                SourceType.PDF -> api.adaptFile(
                    source.bytes.toRequestBody("application/pdf".toMediaType()), nlpLevel.value, nlpLevel.value, group, childId
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
                    childId = childId,
                    ageGroup = request.ageGroup?.code
                )
            )
        }.mapCatching { it.toDomain(title = request.category.displayName, level = request.level).copy(topic = request.cleanTopic) }

    override suspend fun library(childId: String): Result<List<LibraryItem>> =
        apiCall { api.childLibrary(childId) }.map { list -> list.map { it.toLibraryItem() } }
}

/**
 * Motor local de respaldo (reglas en el dispositivo): 5 variantes, ganancia y reemplazos léxicos sin red. Es el
 * repositorio de los builds con `USE_REMOTE_ADAPTATION=false` y el respaldo de [ResilientAdaptationRepository].
 * No produce `documentId`, así que no hay seguimiento de sesión.
 */
@Singleton
class LocalAdaptationRepository @Inject constructor() : AdaptationRepository {

    override suspend fun adapt(source: AdaptationSource, nlpLevel: NlpLevel, childId: String?, ageGroup: AgeGroup?): Result<AdaptationResult> =
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
                ).copy(originalText = text.takeIf { it.length <= MAX_ORIGINAL_CHARS })
            )
        }

    /** Sin servidor solo hay lecciones de las categorías del banco local de respaldo. */
    override suspend fun generate(request: LessonRequest, childId: String?): Result<AdaptationResult> =
        withContext(Dispatchers.Default) {
            DemoData.offlineLesson(request.category.code, request.level)?.let { Result.success(it) }
                ?: Result.failure(AppError.Unsupported("Sin conexión solo hay lecciones de Deportes, Animales y Naturaleza, y Ciencia y Espacio."))
        }

    private companion object {
        /** El original más largo que esto no se retiene en RAM para la comparación. */
        const val MAX_ORIGINAL_CHARS = 100_000
    }
}

/**
 * Servidor primero, motor local si NO hay red: cuando la petición falla por falta de conexión o por tiempo agotado,
 * el texto pegado o el .txt se adapta en el dispositivo y una lección por tema sale del banco local. Cualquier otra
 * respuesta del servidor (validación, permisos, servidor ocupado...) se muestra tal cual: no se disfraza un error.
 */
@Singleton
class ResilientAdaptationRepository @Inject constructor(
    private val remote: RemoteAdaptationRepository,
    private val local: LocalAdaptationRepository
) : AdaptationRepository {

    override suspend fun adapt(source: AdaptationSource, nlpLevel: NlpLevel, childId: String?, ageGroup: AgeGroup?): Result<AdaptationResult> =
        remote.adapt(source, nlpLevel, childId, ageGroup).orOffline { local.adapt(source, nlpLevel, childId, ageGroup) }

    override suspend fun generate(request: LessonRequest, childId: String?): Result<AdaptationResult> =
        remote.generate(request, childId).orOffline { local.generate(request, childId) }

    override suspend fun fetch(id: String): Result<AdaptationResult> = remote.fetch(id)

    override suspend fun library(childId: String): Result<List<LibraryItem>> = remote.library(childId)

    /** Si el respaldo tampoco puede, se conserva el error de red original (más útil para el usuario). */
    private inline fun Result<AdaptationResult>.orOffline(fallback: () -> Result<AdaptationResult>): Result<AdaptationResult> {
        val error = exceptionOrNull()?.toAppError() ?: return this
        if (error !is AppError.NoConnection && error !is AppError.Timeout) return this
        return fallback().takeIf { it.isSuccess } ?: this
    }
}

/** `GET /adaptations/model-info` con caché en memoria; sin servidor, los valores verificados del commit 42482e0. */
@Singleton
class RemoteModelInfoRepository @Inject constructor(
    private val api: FluiletApi
) : ModelInfoRepository {

    @Volatile private var cached: ModelAudit? = null

    override suspend fun audit(): ModelAudit {
        cached?.let { return it }
        return apiCall { api.modelInfo() }.map { it.toDomain() }.getOrNull()?.also { cached = it }
            ?: ModelAudit.VERIFIED_42482E0
    }
}
