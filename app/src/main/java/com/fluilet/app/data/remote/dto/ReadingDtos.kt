package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Contrato real de `fluilet-backend` (docs/api-contract.md). Los modelos de entrada del servidor
 * usan `extra="forbid"`: no se pueden enviar campos que no estén aquí (p. ej. métricas, que el
 * servidor calcula y el cliente nunca aporta).
 */

/** `POST /api/v1/adaptations/text`. `child_id` nulo se omite al serializar. */
data class AdaptTextRequestDto(
    @SerializedName("text") val text: String,
    @SerializedName("nivel_simplificacion_nlp") val nlpLevel: Int,
    @SerializedName("child_id") val childId: String? = null
)

/** Telemetría numérica compartida por documento y por bloque. Anulable en registros legados. */
data class DocumentDto(
    @SerializedName("id") val id: String,
    @SerializedName("original_szigriszt_score") val originalSzigrisztScore: Double? = null,
    @SerializedName("adapted_szigriszt_score") val adaptedSzigrisztScore: Double? = null,
    @SerializedName("score_delta") val scoreDelta: Double? = null,
    @SerializedName("original_word_count") val originalWordCount: Int? = null,
    @SerializedName("adapted_word_count") val adaptedWordCount: Int? = null,
    @SerializedName("original_syllable_count") val originalSyllableCount: Int? = null,
    @SerializedName("original_sentence_count") val originalSentenceCount: Int? = null,
    @SerializedName("syllable_count") val syllableCount: Int? = null,
    @SerializedName("sentence_count") val sentenceCount: Int? = null,
    @SerializedName("maintenance_exception") val maintenanceException: Boolean = false,
    @SerializedName("file_type") val fileType: String? = null,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long? = null,
    @SerializedName("word_count") val wordCount: Int? = null,
    @SerializedName("chunk_count") val chunkCount: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("adaptation_method") val adaptationMethod: String? = null,
    @SerializedName("uploaded_at") val uploadedAt: String? = null
)

/** Los bloques del servidor son unidades de procesamiento (500–800 palabras) y solo traen métricas. */
data class ChunkMetricsDto(
    @SerializedName("chunk_index") val chunkIndex: Int,
    @SerializedName("original_szigriszt_score") val originalSzigrisztScore: Double? = null,
    @SerializedName("adapted_szigriszt_score") val adaptedSzigrisztScore: Double? = null
)

/** Respuesta de adaptación: texto adaptado completo (solo en RAM) + metadatos/métricas numéricas. */
data class AdaptationReadDto(
    @SerializedName("document") val document: DocumentDto,
    @SerializedName("adapted_text") val adaptedText: String,
    @SerializedName("chunks") val chunks: List<ChunkMetricsDto> = emptyList()
)

data class ReadingCreateDto(
    @SerializedName("document_id") val documentId: String
)

/**
 * `PATCH /api/v1/readings/{id}`: avance monótono. `reading_time_seconds` no puede superar el
 * tiempo real transcurrido desde el POST (+5 s) y `completed=true` exige 100 %.
 */
data class ReadingUpdateDto(
    @SerializedName("reading_time_seconds") val readingTimeSeconds: Long,
    @SerializedName("completion_percent") val completionPercent: Double,
    @SerializedName("completed") val completed: Boolean
)

data class ReadingDto(
    @SerializedName("id") val id: String,
    @SerializedName("child_id") val childId: String? = null,
    @SerializedName("document_id") val documentId: String? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("ended_at") val endedAt: String? = null,
    @SerializedName("reading_time_seconds") val readingTimeSeconds: Long = 0,
    @SerializedName("completion_percent") val completionPercent: Double = 0.0,
    @SerializedName("completed") val completed: Boolean = false
)
