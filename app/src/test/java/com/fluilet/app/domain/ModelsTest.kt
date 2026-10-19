package com.fluilet.app.domain

import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.pow

class ModelsTest {

    @Test fun userRole_safeDeserialization() {
        assertEquals(UserRole.TUTOR, UserRole.fromWire("tutor"))
        assertEquals(UserRole.TUTOR, UserRole.fromWire(" TUTOR "))
        assertEquals(UserRole.ADULTO_INDEPENDIENTE, UserRole.fromWire("adulto_independiente"))
        assertEquals(UserRole.NINO, UserRole.fromWire("nino"))
        assertEquals(UserRole.NINO, UserRole.fromWire("niño"))
        assertNull(UserRole.fromWire("superadmin"))
        assertNull(UserRole.fromWire(null))
        assertTrue(UserRole.NINO.isChildView)
        assertFalse(UserRole.NINO.canManageChildren)
        assertTrue(UserRole.TUTOR.canManageChildren)
    }

    @Test fun nlpLevel_acceptsIntegersAndAliases() {
        assertEquals(NlpLevel.NIVEL_1, NlpLevel.fromWire("BASICO"))
        assertEquals(NlpLevel.NIVEL_3, NlpLevel.fromWire("intermedio"))
        assertEquals(NlpLevel.NIVEL_5, NlpLevel.fromWire("AVANZADO"))
        assertEquals(NlpLevel.NIVEL_4, NlpLevel.fromWire("4"))
        assertEquals(NlpLevel.DEFAULT, NlpLevel.fromWire("???"))
        assertEquals(NlpLevel.DEFAULT, NlpLevel.fromValue(99))
        assertEquals(5, NlpLevel.entries.size)
    }

    @Test fun readingSettings_coercedAndValidated() {
        val clamped = ReadingSettings.coerced(fontSizeSp = 99f, lineSpacing = 0.1f, letterSpacingEm = 5f, speechRate = 9f)
        assertEquals(ReadingSettings.MAX_FONT_SIZE, clamped.fontSizeSp)
        assertEquals(ReadingSettings.MIN_LINE_SPACING, clamped.lineSpacing)
        assertEquals(ReadingSettings.MAX_LETTER_SPACING, clamped.letterSpacingEm)
        assertEquals(ReadingSettings.MAX_SPEECH_RATE, clamped.speechRate)
        assertThrows(IllegalArgumentException::class.java) { ReadingSettings(fontSizeSp = 5f) }
    }

    @Test fun palettes_backendMapping() {
        assertEquals(AccessiblePalette.SEPIA, AccessiblePalette.fromBackend("sepia"))
        assertEquals(AccessiblePalette.ALTO_CONTRASTE, AccessiblePalette.fromBackend("alto_contraste"))
        assertEquals(AccessiblePalette.CREMA, AccessiblePalette.fromBackend("estandar"))
        assertEquals(AccessiblePalette.CREMA, AccessiblePalette.fromBackend("otra"))
        val allowed = setOf("estandar", "sepia", "alto_contraste")
        assertTrue(AccessiblePalette.entries.all { it.backendValue in allowed })
    }

    // --- WCAG 2.1 AA y regla de "cero estrés visual" -------------------------------------

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun palettes_meetWcagAA() {
        AccessiblePalette.entries.forEach { p ->
            assertTrue("${p.name}: texto/fondo", contrast(p.onBackground, p.background) >= 4.5)
            assertTrue("${p.name}: texto/superficie", contrast(p.onBackground, p.surface) >= 4.5)
            assertTrue("${p.name}: botón", contrast(p.onPrimary, p.primary) >= 4.5)
            assertTrue("${p.name}: acento/fondo", contrast(p.primary, p.background) >= 3.0)
        }
    }

    @Test fun palettes_neverPureWhiteOrBlack() {
        val pure = setOf(0xFFFFFFFFL, 0xFF000000L)
        AccessiblePalette.entries.forEach { p ->
            assertFalse("${p.name} fondo puro", p.background in pure)
            assertFalse("${p.name} texto puro", p.onBackground in pure)
            assertFalse("${p.name} superficie pura", p.surface in pure)
        }
        assertEquals(0xFFFDFBF7L, AccessiblePalette.CREMA.background)
        assertEquals(0xFFF4ECD8L, AccessiblePalette.SEPIA.background)
        assertEquals(0xFF1E1E1EL, AccessiblePalette.CREMA.onBackground)
    }
}
