package com.fluilet.app.domain.model

import com.fluilet.app.domain.gamification.LessonFacts
import com.fluilet.app.domain.text.LocalGlossary
import com.fluilet.app.domain.text.VocabularyEntry
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
    val maintenanceException: Boolean = false,
    /** Longitud media de oración (palabras por oración) que calcula el servidor; 0 = se deriva de los conteos. */
    val originalAvgWordsPerSentence: Double = 0.0,
    val adaptedAvgWordsPerSentence: Double = 0.0
) {
    val originalWordsPerSentence: Double
        get() = if (originalAvgWordsPerSentence > 0) originalAvgWordsPerSentence
        else if (originalSentenceCount > 0) originalWordCount.toDouble() / originalSentenceCount else 0.0

    val adaptedWordsPerSentence: Double
        get() = if (adaptedAvgWordsPerSentence > 0) adaptedAvgWordsPerSentence
        else if (sentenceCount > 0) adaptedWordCount.toDouble() / sentenceCount else 0.0
}

/** Fuente de una lección generada (`source` del backend): Wikipedia en español o el banco local. */
data class LessonSourceInfo(val provider: String, val title: String, val url: String, val license: String? = null) {
    val isWikipedia: Boolean get() = provider.equals("wikipedia", ignoreCase = true)
}

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
    val childAgeYears: Int? = null,
    /** "¿Sabías que...?" del servidor (`fun_fact`), si lo envía. */
    val funFact: String? = null,
    /** Palabras clave del servidor (`key_vocabulary`), si las envía. */
    val keyVocabulary: List<VocabularyEntry> = emptyList(),
    /** Tema libre con el que se generó la lección (solo para el dato curioso local). */
    val topic: String? = null,
    /** Texto original, SOLO en RAM y solo si se pegó o subió un .txt: permite la vista "Original vs. Adaptado". */
    val originalText: String? = null,
    /** Id de la adaptación retenida en el servidor (`id`): permite recuperarla con `GET /adaptations/{id}`. */
    val adaptationId: String? = null,
    /** Nivel pedido (`target_level`) y fuente de la lección generada. */
    val targetLevel: Int? = null,
    val source: LessonSourceInfo? = null,
    /** `meets_readability_target` del servidor; `null` si no lo envía. */
    val meetsTarget: Boolean? = null
) {
    /** Dato curioso: el del servidor o uno local según tema/categoría. */
    val effectiveFunFact: String? get() = LessonFacts.forLesson(funFact, topic, category)

    /** Glosario: el del servidor o las palabras conocidas que aparecen en el texto adaptado. */
    val effectiveVocabulary: List<VocabularyEntry>
        get() = keyVocabulary.ifEmpty { LocalGlossary.find(chunks.joinToString(" ") { it.text }) }
}

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
