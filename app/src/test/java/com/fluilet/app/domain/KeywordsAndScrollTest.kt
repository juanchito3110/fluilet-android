package com.fluilet.app.domain

import com.fluilet.app.domain.model.TextChunk
import com.fluilet.app.domain.text.Keywords
import com.fluilet.app.domain.text.ReadingScroll
import com.fluilet.app.domain.text.VocabularyEntry
import com.fluilet.app.testutil.sampleAdaptation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Palabras clave de esta lectura" sin palabras defectuosas y la barra de desplazamiento del bloque largo. */
class KeywordsAndScrollTest {

    private fun entry(word: String) = VocabularyEntry(word, "Definición de $word.", "")

    @Test fun brokenKeywordsLikeALoneVAreDropped() {
        val cleaned = Keywords.clean(listOf(entry("Sol"), entry("v"), entry(" "), entry("12"), entry("luz"), entry("biología")))
        assertEquals(listOf("Sol", "luz", "biología"), cleaned.map { it.word })
        assertFalse(Keywords.isUsable("v"))
        assertTrue(Keywords.isUsable("fa"))
    }

    @Test fun repeatedKeywordsAppearOnce() {
        val cleaned = Keywords.clean(listOf(entry("Fotosíntesis"), entry("fotosintesis"), entry("  crustáceos  ")))
        assertEquals(listOf("Fotosíntesis", "crustáceos"), cleaned.map { it.word })
    }

    @Test fun ifTheServerOnlySendsBrokenKeywordsTheLocalGlossaryIsUsed() {
        val lesson = sampleAdaptation().copy(
            chunks = listOf(TextChunk(0, "El planeta gira alrededor de una estrella.")),
            keyVocabulary = listOf(entry("v"))
        )
        assertEquals(listOf("planeta", "estrella"), lesson.effectiveVocabulary.map { it.word })
    }

    @Test fun theScrollThumbShowsHowMuchOfTheBlockIsVisible() {
        // Se ve la mitad del bloque: el indicador mide la mitad de la pista.
        assertEquals(100f, ReadingScroll.thumbLength(track = 200f, viewport = 300f, maxScroll = 300f), 0.01f)
        // Bloque muy largo: el indicador conserva un largo mínimo visible.
        assertEquals(24f, ReadingScroll.thumbLength(track = 200f, viewport = 100f, maxScroll = 10_000f), 0.01f)
        // Sin texto oculto: ocupa toda la pista.
        assertEquals(200f, ReadingScroll.thumbLength(track = 200f, viewport = 300f, maxScroll = 0f), 0.01f)
    }

    @Test fun theScrollThumbMovesFromTopToBottom() {
        assertEquals(0f, ReadingScroll.thumbOffset(track = 200f, thumb = 50f, value = 0f, maxScroll = 400f), 0.01f)
        assertEquals(75f, ReadingScroll.thumbOffset(track = 200f, thumb = 50f, value = 200f, maxScroll = 400f), 0.01f)
        assertEquals(150f, ReadingScroll.thumbOffset(track = 200f, thumb = 50f, value = 400f, maxScroll = 400f), 0.01f)
    }
}
