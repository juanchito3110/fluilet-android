package com.fluilet.app.domain

import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.text.TextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    private val sentence = "El niño lee un cuento muy corto en casa."
    private val text = List(3) { List(6) { sentence }.joinToString(" ") }.joinToString("\n\n")

    @Test fun respectsParagraphsAndWordLimits() {
        val chunks = TextChunker.chunkAdaptedText(text, NlpLevel.NIVEL_1)
        assertTrue(chunks.size >= 3)
        chunks.forEachIndexed { i, c -> assertEquals(i, c.index) }
        assertTrue(chunks.all { SpanishText.wordCount(it.text) <= TextChunker.maxWordsFor(NlpLevel.NIVEL_1) + 10 })
        // No se pierde ni se inventa texto.
        assertEquals(SpanishText.wordCount(text), chunks.sumOf { SpanishText.wordCount(it.text) })
    }

    @Test fun lowerLevelsGiveSmallerChunks() {
        val l1 = TextChunker.chunkAdaptedText(text, NlpLevel.NIVEL_1).size
        val l5 = TextChunker.chunkAdaptedText(text, NlpLevel.NIVEL_5).size
        assertTrue(l1 >= l5)
    }

    @Test fun textWithoutPunctuationIsStillSplitWithoutLosingWords() {
        val words = List(300) { "palabra$it".replace(Regex("\\d"), "x") }.joinToString(" ")
        val chunks = TextChunker.chunkAdaptedText(words, NlpLevel.NIVEL_3)
        assertTrue(chunks.size > 1)
        assertEquals(300, chunks.sumOf { SpanishText.wordCount(it.text) })
    }

    @Test fun blankTextGivesNoChunks() {
        assertTrue(TextChunker.chunkAdaptedText("  \n\n ", NlpLevel.NIVEL_3).isEmpty())
    }

    @Test fun blocksAreShortWithAtMostTwoSentences() {
        val text = List(12) { "El equipo de la escuela corre feliz por el campo verde cada tarde." }.joinToString(" ")
        val chunks = TextChunker.chunkAdaptedText(text, NlpLevel.NIVEL_1)
        assertTrue(chunks.all { SpanishText.sentenceCount(it.text) <= TextChunker.MAX_SENTENCES_PER_CHUNK })
        assertTrue(chunks.all { SpanishText.wordCount(it.text) <= TextChunker.maxWordsFor(NlpLevel.NIVEL_1) })
        assertEquals(listOf(20, 24, 28, 30, 32), NlpLevel.entries.map(TextChunker::maxWordsFor))
    }

    @Test fun aLongSentenceIsCutAfterACommaNotInTheMiddleOfAPhrase() {
        val long = "Los jugadores de béisbol entrenan cada mañana en el estadio de la ciudad, " +
            "y después de comer repasan las jugadas con su entrenador favorito mientras el sol se esconde detrás de las montañas lejanas."
        val pieces = TextChunker.hardSplit(long, 20)
        assertTrue(pieces.first().endsWith("ciudad,"))
        assertEquals(SpanishText.wordCount(long), pieces.sumOf { SpanishText.wordCount(it) })
        assertTrue(pieces.all { SpanishText.wordCount(it) <= 20 })
    }

    @Test fun withoutAPauseALongSentenceIsSplitIntoEvenParts() {
        val words = List(45) { "palabra" }.joinToString(" ")
        assertEquals(listOf(15, 15, 15), TextChunker.hardSplit(words, 20).map { SpanishText.wordCount(it) })
    }
}

