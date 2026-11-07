package com.fluilet.app.presentation

import androidx.compose.ui.unit.dp
import com.fluilet.app.presentation.reader.CompareTab
import com.fluilet.app.presentation.reader.ReaderToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lector compacto: herramientas en UNA fila de 38 dp, "Comparar" con solo una barra de 34 dp dentro de la tarjeta (el
 * texto es el protagonista), "Ver juntos" a pantalla completa y "🔍 Palabras clave" en una sola línea.
 */
class ReaderLayoutTest {

    private val main: File = listOf("src/main", "app/src/main").map(::File).first { it.isDirectory }
    private fun code(path: String) = File(main, "java/com/fluilet/app/presentation/$path").readText()

    // --- Reglas de la barra -------------------------------------------------------------------------

    @Test fun toolbarAndCompareBarHaveTheRequestedHeights() {
        assertEquals(38f, ReaderToolbar.Height.value, 0f)
        assertEquals(34f, ReaderToolbar.CompareBarHeight.value, 0f)
    }

    @Test fun toolbarLabelsDropTheEmojiBeforeTheyCouldBeCut() {
        // Teléfono de 360 dp (328 dp útiles): con o sin flechas de la guía, "📏 Guía" y "🔄 Comparar" caben con emoji.
        assertTrue(ReaderToolbar.showEmoji(328.dp, guideArrows = true))
        assertTrue(ReaderToolbar.showEmoji(328.dp, guideArrows = false))
        // Teléfono de 320 dp (288 dp útiles): con flechas, solo las palabras.
        assertFalse(ReaderToolbar.showEmoji(288.dp, guideArrows = true))
        assertTrue(ReaderToolbar.showEmoji(288.dp, guideArrows = false))
        // Muy angosto: sin flechas (tocar el texto avanza la guía).
        assertTrue(ReaderToolbar.showGuideArrows(288.dp))
        assertFalse(ReaderToolbar.showGuideArrows(260.dp))
    }

    @Test fun compareBarKeepsBothTabsAndTheViewTogetherButtonOnOneLine() {
        assertTrue(ReaderToolbar.compareEmoji(300.dp))
        assertFalse(ReaderToolbar.compareEmoji(270.dp))
        assertFalse(ReaderToolbar.compareButtonIconOnly(260.dp))
        assertTrue(ReaderToolbar.compareButtonIconOnly(240.dp))
        assertEquals(listOf("ADAPTED", "ORIGINAL"), CompareTab.entries.map { it.name })
    }

    // --- Auditoría del código del lector ----------------------------------------------------------------

    @Test fun toolsSitInASingleCompactRow() {
        val bar = code("reader/ReaderScreen.kt").substringAfter("private fun GuideBar").substringBefore("private val ToolbarShape")
        assertTrue("una sola fila de alto fijo", ".height(ReaderToolbar.Height)" in bar && "Row(" in bar)
        assertTrue("nunca baja de renglón", "FlowRow" !in bar)
        assertTrue("A-, A+, Guía y Comparar", listOf("\"A-\"", "\"A+\"", "R.string.reader_guide_short", "R.string.reader_compare_short").all { it in bar })
    }

    @Test fun compareModeKeepsTheStoryAsTheProtagonist() {
        val extras = code("reader/ReaderExtras.kt")
        val card = extras.substringAfter("internal fun ComparisonCard").substringBefore("private fun CompareBar")
        assertTrue("sin el título repetido dentro de la tarjeta", "compare_heading" !in card)
        assertTrue("sin el sello grande ni la ficha técnica dentro de la tarjeta", "ModelSeal(" !in card && "FichaTecnicaCard(" !in card && "LevelSelector(" !in card)
        assertTrue("barra compacta arriba del texto", "CompareBar(" in card)
        val bar = extras.substringAfter("private fun CompareBar").substringBefore("private fun AdaptedComparisonText")
        assertTrue("34 dp y 13 sp", "ReaderToolbar.CompareBarHeight" in bar && "fontSize = 13.sp" in bar)
        assertTrue("Adaptado IA, Original y Ver juntos", listOf("compare_tab_adapted", "compare_tab_original", "compare_view_together").all { it in bar })
        val dialog = extras.substringAfter("private fun CompareTogetherDialog").substringBefore("/** Panel con borde")
        assertTrue("Ver juntos a pantalla completa", "usePlatformDefaultWidth = false" in dialog)
        assertTrue("adaptado arriba y original abajo",
            dialog.indexOf("R.string.compare_adapted_title") in 0 until dialog.indexOf("R.string.compare_original_title"))
        assertTrue("los detalles técnicos siguen disponibles en el diálogo", "FichaTecnicaCard(" in dialog && "ModelSeal(" in dialog)
        assertTrue("el original se muestra completo, sin recortes", "MAX_ORIGINAL_CHARS" !in extras)
    }

    @Test fun keywordsHeaderFitsOnOneCompactLine() {
        val glossary = code("reader/ReaderExtras.kt").substringAfter("internal fun GlossaryBar").substringBefore("private fun WordDefinitionCard")
        assertTrue("encabezado compacto", "compactHeader = true" in glossary)
        val strings = File(main, "res/values/strings_reader.xml").readText()
        assertTrue("título corto", "<string name=\"glossary_title\">🔍 Palabras clave</string>" in strings)
        val carousel = code("common/ChipCarousel.kt").substringAfter("title != null && compactHeader ->").substringBefore("title != null ->")
        assertTrue("una sola línea de 13 sp", "AdaptiveSingleLineText(" in carousel && "fontSize = 13.sp" in carousel)
    }
}
