package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.model.ReadabilityBand

/**
 * Lo que muestra el comparador "🔄 Adaptado vs. Original" para el nivel elegido: Szigriszt-Pazos, palabras por
 * oración y sílabas por palabra del original frente a la variante, su banda y los términos reemplazados.
 */
data class LevelComparison(
    val level: Int,
    val adaptedText: String,
    val originalScore: Double,
    val adaptedScore: Double,
    val originalWordsPerSentence: Double,
    val adaptedWordsPerSentence: Double,
    val originalSyllablesPerWord: Double,
    val adaptedSyllablesPerWord: Double,
    /** Etiqueta INFLESZ de la variante (`readability_level`). */
    val readabilityLevel: String,
    val band: ReadabilityBand?,
    val inBand: Boolean,
    val replacements: List<LexicalReplacement>,
    /** Medida por el servidor (`false` = motor local del dispositivo). */
    val fromServer: Boolean
) {
    /** Puntos de facilidad ganados respecto al original. */
    val gain: Double get() = adaptedScore - originalScore
}

object LevelComparisons {

    /**
     * Variantes disponibles, en este orden: las de la lectura (`level_previews`), las del detalle recuperado con
     * `GET /adaptations/{id}`, las del motor local sobre el original y, si no hay original, la propia lectura.
     */
    fun previews(adaptation: AdaptationResult, detail: AdaptationResult?, original: String?): List<LevelPreview> = when {
        adaptation.levelPreviews.isNotEmpty() -> adaptation.levelPreviews
        detail?.levelPreviews?.isNotEmpty() == true -> detail.levelPreviews
        !original.isNullOrBlank() -> TextAdapter.levelPreviews(original)
        else -> listOf(current(adaptation))
    }

    /** Nivel que se muestra al abrir el comparador: el de la lectura, si está entre las variantes. */
    fun defaultLevel(adaptation: AdaptationResult, previews: List<LevelPreview>): Int? {
        val own = adaptation.effectiveLevel
        return if (previews.any { it.level == own }) own else previews.firstOrNull()?.level
    }

    /**
     * Comparativa de [level]. [base] es la adaptación que aporta las métricas del original y los reemplazos del
     * nivel pedido (la lectura, o su detalle si de ahí salieron las variantes).
     */
    fun compare(base: AdaptationResult, previews: List<LevelPreview>, level: Int, original: String?): LevelComparison? {
        val preview = previews.firstOrNull { it.level == level } ?: return null
        val local = !preview.fromServer
        val m = base.metrics

        // El motor local se compara con el original medido en el dispositivo; el servidor, con sus métricas exactas.
        val measureLocally = !original.isNullOrBlank() && (local || m.originalSzigrisztScore == 0.0)
        val originalScore: Double
        val originalWps: Double
        val originalSpw: Double
        if (measureLocally) {
            val words = SpanishText.wordCount(original!!)
            val sentences = SpanishText.sentenceCount(original).coerceAtLeast(1)
            val syllables = SpanishText.syllableCount(original)
            originalScore = Szigriszt.score(syllables, words, sentences)
            originalWps = words.toDouble() / sentences
            originalSpw = if (words > 0) syllables.toDouble() / words else 0.0
        } else {
            originalScore = m.originalSzigrisztScore
            originalWps = m.originalWordsPerSentence
            originalSpw = m.originalSyllablesPerWord.takeIf { it > 0 }
                ?: if (originalWps > 0) Szigriszt.syllablesPerWord(originalScore, originalWps) else 0.0
        }

        val replacements = when {
            level == base.effectiveLevel && base.lexicalReplacements.isNotEmpty() -> base.lexicalReplacements
            preview.lexicalReplacements.isNotEmpty() -> preview.lexicalReplacements
            !original.isNullOrBlank() -> LexicalDiff.replacements(original, preview.adaptedText)
            else -> emptyList()
        }

        return LevelComparison(
            level = preview.level,
            adaptedText = preview.adaptedText,
            originalScore = originalScore,
            adaptedScore = preview.readabilityScore,
            originalWordsPerSentence = originalWps,
            adaptedWordsPerSentence = preview.avgSentenceLength,
            originalSyllablesPerWord = originalSpw,
            adaptedSyllablesPerWord = preview.syllablesPerWord,
            readabilityLevel = preview.readabilityLevel,
            band = preview.band,
            inBand = preview.inBand,
            replacements = replacements,
            fromServer = preview.fromServer
        )
    }

    /** La propia lectura como única variante (lecturas sin `level_previews` ni texto original). */
    private fun current(adaptation: AdaptationResult): LevelPreview {
        val m = adaptation.metrics
        val score = m.adaptedSzigrisztScore
        val wps = m.adaptedWordsPerSentence
        return LevelPreview(
            level = adaptation.effectiveLevel ?: 3,
            adaptedText = adaptation.chunks.joinToString("\n\n") { it.text },
            readabilityScore = score,
            readabilityLevel = adaptation.readabilityLevel ?: Szigriszt.inflesz(score),
            avgSentenceLength = wps,
            syllablesPerWord = m.adaptedSyllablesPerWord.takeIf { it > 0 }
                ?: if (wps > 0) Szigriszt.syllablesPerWord(score, wps) else 0.0,
            targetMet = adaptation.meetsTarget ?: false,
            lexicalReplacements = adaptation.lexicalReplacements,
            fromServer = !adaptation.isLocalEngine
        )
    }
}
