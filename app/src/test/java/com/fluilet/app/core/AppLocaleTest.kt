package com.fluilet.app.core

import com.fluilet.app.core.locale.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLocaleTest {
    @Test fun appLocaleIsMexicanSpanish() {
        assertEquals("es", AppLocale.SPANISH.language)
        assertEquals("MX", AppLocale.SPANISH.country)
    }

    @Test fun spanishDetection() {
        assertTrue(AppLocale.isSpanish(Locale.forLanguageTag("es-MX")))
        assertTrue(AppLocale.isSpanish(Locale.forLanguageTag("es")))
        assertFalse(AppLocale.isSpanish(Locale.ENGLISH))
    }
}
