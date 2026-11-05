package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.TextChunk

/**
 * Parte un texto en bloques digeribles (tarjetas) para el lector. El servidor devuelve el texto
 * adaptado completo y solo métricas por bloque de procesamiento (500–800 palabras), así que la
 * presentación en tarjetas pequeñas se calcula aquí, en RAM. Nivel bajo = bloques más cortos.
 */
object TextChunker {

    /** Uno o dos enunciados por bloque: el niño ve poco texto a la vez, con letra grande y sin renglones cortados. */
    const val MAX_SENTENCES_PER_CHUNK = 2

    /** Palabras por bloque (20 a 32): más bloques, pero más cortos, en los niveles más sencillos. */
    fun maxWordsFor(level: NlpLevel): Int = when (level) {
        NlpLevel.NIVEL_1 -> 20
        NlpLevel.NIVEL_2 -> 24
        NlpLevel.NIVEL_3 -> 28
        NlpLevel.NIVEL_4 -> 30
        NlpLevel.NIVEL_5 -> 32
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

    /**
     * Una oración más larga que el tope se reparte sin perder texto: se corta tras una coma, punto y coma o dos puntos
     * cercanos al límite (sin dejar trozos de menos de [MIN_PIECE_WORDS] palabras) y, si no hay, en partes parejas.
     */
    internal fun hardSplit(sentence: String, maxWords: Int): List<String> {
        val tokens = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size <= maxWords) return listOf(sentence.trim())
        val pieces = mutableListOf<List<String>>()
        var rest = tokens
        while (rest.size > maxWords) {
            val clause = (maxWords - 1 downTo MIN_PIECE_WORDS - 1).firstOrNull { i ->
                rest[i].last() in CLAUSE_MARKS && rest.size - (i + 1) >= MIN_PIECE_WORDS
            }
            // Sin pausa natural: partes de tamaño parecido (no una de 32 palabras y otra de 3).
            val parts = (rest.size + maxWords - 1) / maxWords
            val even = (rest.size + parts - 1) / parts
            val cut = clause?.plus(1) ?: even
            pieces += rest.take(cut)
            rest = rest.drop(cut)
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces.map { it.joinToString(" ") }
    }

    private const val MIN_PIECE_WORDS = 4
    private val CLAUSE_MARKS = setOf(',', ';', ':')
}
