package com.fluilet.app.domain.model

import com.fluilet.app.domain.text.Szigriszt

/**
 * Ficha Técnica del Motor NLP: la comparativa Szigriszt-Pazos original vs. adaptado de una lección, con sus
 * etiquetas de dificultad, conteo de palabras y longitud media de oración. Todo viene de las métricas reales.
 */
data class TechnicalSheet(
    val originalScore: Double,
    val adaptedScore: Double,
    val delta: Double,
    val originalLabel: String,
    val adaptedLabel: String,
    val targetLevel: Int?,
    val originalWords: Int,
    val adaptedWords: Int,
    val originalWordsPerSentence: Double,
    val adaptedWordsPerSentence: Double,
    val improved: Boolean,
    val bestEffort: Boolean
) {
    /** Ej.: `Bastante difícil → Normal · Adaptado al Nivel 3`. */
    val headline: String
        get() = "$originalLabel → $adaptedLabel" + (targetLevel?.let { " · Adaptado al Nivel $it" } ?: "")

    companion object {
        fun of(adaptation: AdaptationResult): TechnicalSheet {
            val m = adaptation.metrics
            val level = adaptation.targetLevel ?: adaptation.simplificationLevel
            return TechnicalSheet(
                originalScore = m.originalSzigrisztScore,
                adaptedScore = m.adaptedSzigrisztScore,
                delta = m.adaptedSzigrisztScore - m.originalSzigrisztScore,
                originalLabel = Szigriszt.level(m.originalSzigrisztScore),
                adaptedLabel = Szigriszt.level(m.adaptedSzigrisztScore),
                targetLevel = level,
                originalWords = m.originalWordCount,
                adaptedWords = m.adaptedWordCount,
                originalWordsPerSentence = m.originalWordsPerSentence,
                adaptedWordsPerSentence = m.adaptedWordsPerSentence,
                improved = m.adaptedSzigrisztScore > m.originalSzigrisztScore,
                bestEffort = adaptation.bestEffort || adaptation.meetsTarget == false
            )
        }
    }
}
