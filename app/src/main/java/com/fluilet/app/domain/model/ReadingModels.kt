package com.fluilet.app.domain.model

import java.time.Instant

/** Bloque digerible de texto adaptado. Vive SOLO en RAM durante la sesión (cero retención). */
data class TextChunk(val index: Int, val text: String)

/** Métricas de legibilidad del texto (Szigriszt-Pazos). Solo números; el servidor las calcula. */
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
    val documentId: String? = null,
    /** Categoría normalizada de la lección (`ciencia_espacio`, `lectura_libre`, ...). */
    val category: String? = null,
    val simplificationLevel: Int? = null,
    /** Mini-reto de comprensión (2 preguntas). Vacío si el servidor no lo envía. */
    val questions: List<ComprehensionQuestion> = emptyList(),
    /** El servidor entregó la mejor adaptación posible sin alcanzar el objetivo de legibilidad. */
    val bestEffort: Boolean = false,
    /** Grupo de edad de las preguntas (`6-8`, `9-11`, `12+`) y edad del niño, si el servidor los envía. */
    val questionAgeGroup: String? = null,
    val childAgeYears: Int? = null
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

/** Resultado del mini-reto de comprensión de una sesión. */
data class ComprehensionResult(val answered: Int, val correct: Int) {
    /** Porcentaje de aciertos (0, 50 o 100 con dos preguntas); `null` si no se contestó nada. */
    val score: Int? get() = ComprehensionScoring.score(answered, correct)
}

/** Misma fórmula del backend: porcentaje de aciertos redondeado hacia arriba en la mitad. */
object ComprehensionScoring {
    fun score(answered: Int, correct: Int): Int? {
        if (answered <= 0) return null
        require(correct in 0..answered) { "correct debe estar entre 0 y answered" }
        return (200 * correct + answered) / (2 * answered)
    }
}

/**
 * Avance numérico de una sesión (único dato que sale de la app al leer). Monótono. Incluye la
 * práctica educativa: comprensión y velocidad de voz usada. Cero retención: no contiene texto.
 */
data class ReadingProgress(
    val readingTimeSeconds: Long,
    val completionPercent: Int,
    val completed: Boolean,
    val category: String? = null,
    val simplificationLevel: Int? = null,
    val comprehension: ComprehensionResult? = null,
    val ttsSpeed: Double? = null
)

/** Lectura activa: contenido adaptado + sujeto que lee. Solo en RAM. */
data class ActiveReading(
    val childId: String?,
    val readerName: String,
    val settings: ReadingSettings,
    val adaptation: AdaptationResult,
    /** Edad del niño (años), si se conoce: adapta el mini-reto. */
    val ageYears: Int? = null,
    /** Clave del perfil para sus datos de juego (FluiBot): id del niño o clave del adulto. */
    val ownerKey: String = childId ?: "adult"
) {
    /** Grupo de edad efectivo: el del servidor, o el calculado con la edad del niño. */
    val ageGroup: AgeGroup
        get() = AgeGroup.fromCode(adaptation.questionAgeGroup) ?: AgeGroup.fromAge(adaptation.childAgeYears ?: ageYears)

    val displayAge: Int? get() = adaptation.childAgeYears ?: ageYears
}
