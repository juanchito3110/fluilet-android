package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Contrato real de `fluilet-backend` (docs/api-contract.md). Los modelos de entrada del servidor
 * usan `extra="forbid"`: no se pueden enviar campos que no estén aquí (p. ej. métricas, que el
 * servidor calcula y el cliente nunca aporta).
 */

/**
 * `POST /api/v1/adaptations/text`. Se envían `nivel_simplificacion_nlp` y su alias `level` con el MISMO valor (el
 * backend exige que coincidan) y `age_group` del perfil infantil activo. Los nulos se omiten al serializar.
 */
data class AdaptTextRequestDto(
    @SerializedName("text") val text: String,
    @SerializedName("nivel_simplificacion_nlp") val nlpLevel: Int,
    @SerializedName("child_id") val childId: String? = null,
    @SerializedName("level") val level: Int? = nlpLevel,
    @SerializedName("age_group") val ageGroup: String? = null
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
    @SerializedName("category") val category: String? = null,
    @SerializedName("simplification_level") val simplificationLevel: Int? = null,
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
    @SerializedName("chunks") val chunks: List<ChunkMetricsDto> = emptyList(),
    // Lecciones (backend >= f56b71b). Todos opcionales: un servidor anterior simplemente no los envía.
    @SerializedName("title") val title: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("simplification_level") val simplificationLevel: Int? = null,
    @SerializedName("comprehension_questions") val comprehensionQuestions: List<ComprehensionQuestionDto>? = null,
    @SerializedName("quality_status") val qualityStatus: String? = null,
    @SerializedName("warnings") val warnings: List<String>? = null,
    @SerializedName("question_age_group") val questionAgeGroup: String? = null,
    @SerializedName("child_age_years") val childAgeYears: Int? = null,
    // Opcionales (aún no publicados por el backend): sin ellos la app usa dato curioso y glosario locales.
    @SerializedName("fun_fact") val funFact: String? = null,
    @SerializedName("key_vocabulary") val keyVocabulary: List<VocabularyDto>? = null,
    // Contrato de revisión académica (backend >= f6f09d9): adaptación retenida con su original y métricas exactas.
    @SerializedName("id") val id: String? = null,
    @SerializedName("original_text") val originalText: String? = null,
    @SerializedName("original_readability_score") val originalReadabilityScore: Double? = null,
    @SerializedName("readability_score") val readabilityScore: Double? = null,
    @SerializedName("metrics") val metrics: ComparisonMetricsDto? = null,
    @SerializedName("target_level") val targetLevel: Int? = null,
    @SerializedName("meets_readability_target") val meetsReadabilityTarget: Boolean? = null,
    @SerializedName("source") val source: LessonSourceDto? = null,
    @SerializedName("resolved_topic") val resolvedTopic: String? = null,
    @SerializedName("age_group") val ageGroup: String? = null,
    // Motor reentrenado (backend >= 42482e0): ganancia, longitudes medias, reemplazos léxicos, 5 variantes y modelo.
    @SerializedName("readability_gain") val readabilityGain: Double? = null,
    @SerializedName("readability_level") val readabilityLevel: String? = null,
    @SerializedName("original_avg_sentence_length") val originalAvgSentenceLength: Double? = null,
    @SerializedName("adapted_avg_sentence_length") val adaptedAvgSentenceLength: Double? = null,
    @SerializedName("original_syllables_per_word") val originalSyllablesPerWord: Double? = null,
    @SerializedName("adapted_syllables_per_word") val adaptedSyllablesPerWord: Double? = null,
    @SerializedName("lexical_replacements") val lexicalReplacements: List<LexicalReplacementDto>? = null,
    @SerializedName("level_previews") val levelPreviews: List<LevelPreviewDto>? = null,
    @SerializedName("level_progression_met") val levelProgressionMet: Boolean? = null,
    @SerializedName("engine_version") val engineVersion: String? = null,
    @SerializedName("model_version") val modelVersion: String? = null,
    @SerializedName("checkpoint_name") val checkpointName: String? = null
)

data class LexicalReplacementDto(
    @SerializedName("original") val original: String? = null,
    @SerializedName("adapted") val adapted: String? = null
)

/** Una de las 5 variantes (`level_previews`). `syllables_per_word` es opcional: si falta se despeja de la fórmula. */
data class LevelPreviewDto(
    @SerializedName("level") val level: Int? = null,
    @SerializedName("adapted_text") val adaptedText: String? = null,
    @SerializedName("readability_score") val readabilityScore: Double? = null,
    @SerializedName("readability_level") val readabilityLevel: String? = null,
    @SerializedName("avg_sentence_length") val avgSentenceLength: Double? = null,
    @SerializedName("syllables_per_word") val syllablesPerWord: Double? = null,
    @SerializedName("target_met") val targetMet: Boolean? = null,
    @SerializedName("method") val method: String? = null
)

/** `metrics` de la respuesta: puntajes Szigriszt-Pazos sin redondear y conteos de ambos textos. */
data class ComparisonMetricsDto(
    @SerializedName("original_szigriszt_pazos") val originalSzigriszt: Double? = null,
    @SerializedName("adapted_szigriszt_pazos") val adaptedSzigriszt: Double? = null,
    @SerializedName("score_delta") val scoreDelta: Double? = null,
    @SerializedName("original_word_count") val originalWordCount: Int? = null,
    @SerializedName("adapted_word_count") val adaptedWordCount: Int? = null,
    @SerializedName("original_sentence_count") val originalSentenceCount: Int? = null,
    @SerializedName("adapted_sentence_count") val adaptedSentenceCount: Int? = null,
    @SerializedName("original_average_words_per_sentence") val originalAvgWordsPerSentence: Double? = null,
    @SerializedName("adapted_average_words_per_sentence") val adaptedAvgWordsPerSentence: Double? = null
)

data class LessonSourceDto(
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("license") val license: String? = null
)

data class VocabularyDto(
    @SerializedName(value = "word", alternate = ["term"]) val word: String? = null,
    @SerializedName(value = "definition", alternate = ["meaning"]) val definition: String? = null,
    @SerializedName("example") val example: String? = null
)

data class ComprehensionQuestionDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("question") val question: String? = null,
    @SerializedName("options") val options: List<String>? = null,
    @SerializedName("correct_option_index") val correctOptionIndex: Int? = null,
    // Adaptación por edad (opcionales; sin ellos la app usa pista y explicación locales).
    @SerializedName("hint") val hint: String? = null,
    @SerializedName("explanation") val explanation: String? = null
)

/**
 * `POST /api/v1/adaptations/generate`. `level` repite `simplification_level` (el backend exige que coincidan) y
 * `age_group` es el del perfil infantil activo. `topic`, `child_id` y `age_group` nulos se omiten al serializar.
 */
data class LessonGenerateDto(
    @SerializedName("category") val category: String,
    @SerializedName("topic") val topic: String? = null,
    @SerializedName("simplification_level") val simplificationLevel: Int,
    @SerializedName("child_id") val childId: String? = null,
    @SerializedName("level") val level: Int? = simplificationLevel,
    @SerializedName("age_group") val ageGroup: String? = null
)

data class ReadingCreateDto(
    @SerializedName("document_id") val documentId: String,
    @SerializedName("category") val category: String? = null,
    @SerializedName("simplification_level") val simplificationLevel: Int? = null
)

/**
 * `PATCH /api/v1/readings/{id}`: avance monótono. `reading_time_seconds` no puede superar el
 * tiempo real transcurrido desde el POST (+5 s) y `completed=true` exige 100 %.
 */
data class ReadingUpdateDto(
    @SerializedName("reading_time_seconds") val readingTimeSeconds: Long,
    @SerializedName("completion_percent") val completionPercent: Double,
    @SerializedName("completed") val completed: Boolean,
    // Práctica educativa (opcional): el servidor valida que el puntaje coincida con los contadores.
    @SerializedName("category") val category: String? = null,
    @SerializedName("simplification_level") val simplificationLevel: Int? = null,
    @SerializedName("comprehension_score") val comprehensionScore: Int? = null,
    @SerializedName("questions_answered") val questionsAnswered: Int? = null,
    @SerializedName("questions_correct") val questionsCorrect: Int? = null,
    @SerializedName("tts_speed_used") val ttsSpeedUsed: Double? = null
)

/**
 * Sesión de lectura. Desde el commit dbd5901 del backend incluye una instantánea numérica de las
 * métricas del documento (sobrevive a re-adaptaciones y borrados) y `child_id` es nulo para
 * adultos independientes (`reader_user_id` identifica al lector).
 */
data class ReadingDto(
    @SerializedName("id") val id: String,
    @SerializedName("child_id") val childId: String? = null,
    @SerializedName("reader_user_id") val readerUserId: String? = null,
    @SerializedName("document_id") val documentId: String? = null,
    @SerializedName("original_szigriszt_score") val originalSzigrisztScore: Double? = null,
    @SerializedName("adapted_szigriszt_score") val adaptedSzigrisztScore: Double? = null,
    @SerializedName("score_delta") val scoreDelta: Double? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("ended_at") val endedAt: String? = null,
    @SerializedName("reading_time_seconds") val readingTimeSeconds: Long = 0,
    @SerializedName("completion_percent") val completionPercent: Double = 0.0,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("category") val category: String? = null,
    @SerializedName("simplification_level") val simplificationLevel: Int? = null,
    @SerializedName("comprehension_score") val comprehensionScore: Int? = null,
    @SerializedName("questions_answered") val questionsAnswered: Int? = null,
    @SerializedName("questions_correct") val questionsCorrect: Int? = null,
    @SerializedName("tts_speed_used") val ttsSpeedUsed: Double? = null
)
