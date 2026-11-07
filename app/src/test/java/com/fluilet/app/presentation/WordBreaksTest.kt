package com.fluilet.app.presentation

import com.fluilet.app.presentation.common.WordBreaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regla global: ningún salto de línea puede caer a media palabra ("Leccione / s", "Com / prar"). */
class WordBreaksTest {

    @Test fun breaksBetweenWordsAreAccepted() {
        val text = "Lecciones completadas"
        assertTrue(WordBreaks.keepsWordsWhole(text, listOf(text.indexOf("completadas"))))
        assertTrue("sin saltos", WordBreaks.keepsWordsWhole("Comprar", emptyList()))
        assertTrue("después de un signo", WordBreaks.keepsWordsWhole("Lecciones (100%)", listOf(10)))
    }

    @Test fun breaksInsideAWordAreRejected() {
        assertFalse(WordBreaks.keepsWordsWhole("Lecciones completadas", listOf(8))) // "Leccione / s"
        assertFalse(WordBreaks.keepsWordsWhole("Comprar", listOf(3))) // "Com / prar"
        assertFalse(WordBreaks.keepsWordsWhole("Bloques de lectura", listOf(5))) // "Bloqu / es"
    }

    @Test fun theLongestWordIsWhatANarrowColumnMustFit() {
        assertEquals("Holográfico", WordBreaks.longestWord("Visor Holográfico"))
        assertEquals("Accesorios", WordBreaks.longestWord("Accesorios"))
    }
}
