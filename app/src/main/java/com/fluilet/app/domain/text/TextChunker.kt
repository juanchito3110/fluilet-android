package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.TextChunk

/**
 * Parte un texto en bloques digeribles (tarjetas) para el lector. El servidor devuelve el texto
 * adaptado completo y solo métricas por bloque de procesamiento (500–800 palabras), así que la
 * presentación en tarjetas pequeñas se calcula aquí, en RAM. Nivel bajo = bloques más cortos.
 */
object TextChunker {

    private const val MAX_SENTENCES_PER_CHUNK = 4

    fun maxWordsFor(level: NlpLevel): Int = when (level) {
        NlpLevel.NIVEL_1 -> 28
        NlpLevel.NIVEL_2 -> 38
        NlpLevel.NIVEL_3 -> 50
        NlpLevel.NIVEL_4 -> 62
        NlpLevel.NIVEL_5 -> 75
    }

    /** Divide el texto adaptado respetando párrafos y oraciones. */
    fun chunkAdaptedText(adaptedText: String, level: NlpLevel): List<TextChunk> {
        val maxWords = maxWordsFor(level)
        val paragraphs = TextAdapter.normalize(adaptedText)
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { paragraph -> SpanishText.sentences(paragraph).flatMap { hardSplit(it, maxWords) } }
            .filter { it.isNotEmpty() }
        return build(paragraphs, maxWords).mapIndexed { index, text -> TextChunk(index, text) }
    }

    /** Agrupa oraciones por párrafo sin cruzar límites de párrafo ni superar el tope de palabras. */
    fun build(paragraphs: List<List<String>>, maxWords: Int): List<String> {
        val chunks = mutableListOf<String>()
        for (paragraph in paragraphs) {
            val current = mutableListOf<String>()
            var words = 0
            for (sentence in paragraph) {
                val w = SpanishText.wordCount(sentence)
                if (current.isNotEmpty() && (words + w > maxWords || current.size >= MAX_SENTENCES_PER_CHUNK)) {
                    chunks += current.joinToString(" ")
                    current.clear()
                    words = 0
                }
                current += sentence
                words += w
            }
            if (current.isNotEmpty()) chunks += current.joinToString(" ")
        }
        return chunks
    }

    /** Una "oración" sin puntuación y más larga que el tope se corta por palabras (sin perder texto). */
    private fun hardSplit(sentence: String, maxWords: Int): List<String> {
        val tokens = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size <= maxWords) return listOf(sentence.trim())
        return tokens.chunked(maxWords).map { it.joinToString(" ") }
    }
}
