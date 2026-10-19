package com.fluilet.app.domain

import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.text.Szigriszt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanishTextTest {

    @Test fun syllables_basicWords() {
        val expected = mapOf(
            "casa" to 2, "sol" to 1, "aire" to 2, "país" to 2, "poeta" to 3, "leer" to 2,
            "murciélago" to 4, "queso" to 2, "guitarra" to 3, "muy" to 1, "y" to 1,
            "oído" to 3, "estudiar" to 3, "ciudad" to 2, "adaptación" to 4, "lectura" to 3
        )
        expected.forEach { (word, count) -> assertEquals("sílabas de '$word'", count, SpanishText.syllables(word)) }
    }

    @Test fun wordsAndSentences() {
        val text = "Hola, mundo. ¿Cómo estás? ¡Muy bien!"
        assertEquals(6, SpanishText.wordCount(text))
        assertEquals(3, SpanishText.sentenceCount(text))
    }

    @Test fun szigriszt_matchesFormula() {
        // 206.835 - 62.3 * (90/30) - (30/5) = 13.935
        assertEquals(13.94, Szigriszt.score(syllables = 90, words = 30, sentences = 5), 0.01)
    }

    @Test fun szigriszt_emptyIsZero() {
        assertEquals(0.0, Szigriszt.score(0, 0, 0), 0.0)
        assertEquals(0.0, Szigriszt.score(""), 0.0)
    }

    @Test fun shorterSentencesScoreHigher() {
        val long = "El sistema permite que los usuarios consulten la documentación técnica relacionada con los procedimientos administrativos vigentes."
        val short = "El sistema es simple. Los usuarios leen los textos. Cada texto es corto."
        assertTrue(Szigriszt.score(short) > Szigriszt.score(long))
    }

    @Test fun levels() {
        assertEquals("Normal", Szigriszt.level(60.0))
        assertEquals("Muy fácil", Szigriszt.level(90.0))
        assertEquals("Muy difícil", Szigriszt.level(5.0))
    }
}
