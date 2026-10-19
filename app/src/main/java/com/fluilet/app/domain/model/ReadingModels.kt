package com.fluilet.app.domain.model

import java.time.Instant

/** Bloque digerible de texto adaptado. Vive SOLO en RAM durante la sesión (cero retención). */
data class TextChunk(val index: Int, val text: String)

/** Métricas clínicas de legibilidad (Szigriszt-Pazos). Solo números: se pueden enviar al backend. */
data class ReadabilityMetrics(
    val originalSzigrisztScore: Double,
    val adaptedSzigrisztScore: Double,
    val scoreDelta: Double,
    val originalWordCount: Int,
    val adaptedWordCount: Int,
    val syllableCount: Int,
    val sentenceCount: Int,
    val originalSyllableCount: Int = 0,
    val originalSentenceCount: Int = 0,
    /** Texto original ya muy legible: el servidor lo adapta solo de forma conservadora. */
    val maintenanceException: Boolean = false
)

data class AdaptationResult(
    val title: String,
    val chunks: List<TextChunk>,
    val metrics: ReadabilityMetrics,
    /** Identificador de documento (metadatos) cuando el backend lo registra; `null` en local. */
    val documentId: String? = null
)

enum class SourceType { TXT, PDF, PASTED_TEXT }

/** Documento de origen en memoria. Nunca se escribe a disco. */
class AdaptationSource(
    val fileName: String,
    val type: SourceType,
    val bytes: ByteArray
) {
    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024 // límite del backend (MAX_BODY_BYTES): 5 MiB
        const val MIN_WORDS = 20 // el backend rechaza textos de menos de 20 palabras
    }
}

/** Avance numérico de una sesión (único dato que sale de la app al leer). Monótono. */
data class ReadingProgress(
    val readingTimeSeconds: Long,
    val completionPercent: Int,
    val completed: Boolean
)

/** Resumen histórico para el panel clínico del tutor: lectura + métricas del documento. */
data class SessionSummary(
    val id: String,
    val label: String,
    val startedAt: Instant,
    val readingTimeSeconds: Long,
    val completionPercent: Int,
    val completed: Boolean,
    val originalScore: Double,
    val adaptedScore: Double,
    val scoreDelta: Double
)

/** Lectura activa: contenido adaptado + sujeto que lee. Solo en RAM. */
data class ActiveReading(
    val childId: String?,
    val readerName: String,
    val settings: ReadingSettings,
    val adaptation: AdaptationResult
)
