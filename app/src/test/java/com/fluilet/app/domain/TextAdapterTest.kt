package com.fluilet.app.domain

import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.text.TextAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAdapterTest {

    private val complex = """
        La aplicación transforma textos complejos mediante un motor de Inteligencia Artificial propio, permitiendo una lectura simplificada que reduce la fatiga visual y el estrés cognitivo, aunque además requiere que los tutores configuren previamente el nivel de simplificación adecuado para cada niño.

        Posteriormente, los usuarios pueden utilizar la herramienta para realizar sesiones de lectura guiada, y los especialistas obtienen métricas de legibilidad que permiten evaluar el progreso, pero solamente cuando la sesión finaliza correctamente.
    """.trimIndent()

    @Test fun splitsLongSentences() {
        val result = TextAdapter.adapt("t", complex, NlpLevel.NIVEL_1)
        val adaptedSentences = result.chunks.flatMap { SpanishText.sentences(it.text) }
        assertTrue("debe haber más oraciones que en el original", adaptedSentences.size > SpanishText.sentenceCount(complex))
        val originalLongest = SpanishText.sentences(complex).maxOf { SpanishText.wordCount(it) }
        val adaptedLongest = adaptedSentences.maxOf { SpanishText.wordCount(it) }
        assertTrue("la oración más larga debe acortarse", adaptedLongest < originalLongest)
        val originalAverage = SpanishText.sentences(complex).map { SpanishText.wordCount(it) }.average()
        val adaptedAverage = adaptedSentences.map { SpanishText.wordCount(it) }.average()
        assertTrue("el promedio debe bajar al menos 25 %", adaptedAverage < originalAverage * 0.75)
    }

    @Test fun improvesReadability() {
        val result = TextAdapter.adapt("t", complex, NlpLevel.NIVEL_1)
        val m = result.metrics
        assertTrue(m.adaptedSzigrisztScore > m.originalSzigrisztScore)
        assertEquals(m.adaptedSzigrisztScore - m.originalSzigrisztScore, m.scoreDelta, 0.011)
        assertTrue(m.syllableCount > 0 && m.sentenceCount > 0 && m.originalWordCount > 0 && m.adaptedWordCount > 0)
    }

    @Test fun lowLevelsReplaceVocabularyHighLevelsKeepIt() {
        val low = TextAdapter.adapt("t", complex, NlpLevel.NIVEL_1).chunks.joinToString(" ") { it.text }
        val high = TextAdapter.adapt("t", complex, NlpLevel.NIVEL_5).chunks.joinToString(" ") { it.text }
        assertFalse(low.contains("utilizar", ignoreCase = true))
        assertTrue(low.contains("usar", ignoreCase = true))
        assertTrue(high.contains("utilizar", ignoreCase = true))
    }

    @Test fun neverStartsFragmentsWithRelativesOrGerunds() {
        val text = "El sistema funciona bien con los niños de primaria, que leen cada texto con calma, permitiendo mejores resultados en todas las sesiones."
        val sentences = TextAdapter.adapt("t", text, NlpLevel.NIVEL_1).chunks.flatMap { SpanishText.sentences(it.text) }
        assertTrue(sentences.none { it.startsWith("Que ") || it.startsWith("Permitiendo") })
    }

    @Test fun lowerLevelMakesSmallerChunks() {
        val level1 = TextAdapter.adapt("t", complex, NlpLevel.NIVEL_1).chunks
        val level5 = TextAdapter.adapt("t", complex, NlpLevel.NIVEL_5).chunks
        assertTrue(level1.size >= level5.size)
    }

    @Test fun chunksAreIndexedAndNonBlank() {
        val chunks = TextAdapter.adapt("t", complex, NlpLevel.NIVEL_3).chunks
        assertTrue(chunks.isNotEmpty())
        chunks.forEachIndexed { i, c ->
            assertEquals(i, c.index)
            assertTrue(c.text.isNotBlank())
            assertTrue(c.text.last() in ".!?…")
        }
    }

    @Test fun normalizeKeepsParagraphs() {
        assertEquals("a b\n\nc", TextAdapter.normalize("﻿a   b\r\n\r\n\r\n\r\nc  "))
    }
}
