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

    /** Anchos reales (a 13 sp, medidos con OpenDyslexic): "📏 Guía" + "🔄 Comparar" y "Guía" + "Comparar". */
    private val emojiLabels = 150.dp
    private val plainLabels = 112.dp

    @Test fun toolbarDropsTheEmojiBeforeAnyLabelCouldBeCut() {
        // Con espacio: emoji y flechas de la guía.
        assertEquals(ReaderToolbar.ToolbarFit(emoji = true, arrows = true), ReaderToolbar.toolbarFit(379.dp, true, emojiLabels, plainLabels))
        // Más angosto: se conservan las flechas y se renuncia al emoji.
        assertEquals(ReaderToolbar.ToolbarFit(emoji = false, arrows = true), ReaderToolbar.toolbarFit(328.dp, true, emojiLabels, plainLabels))
        // Muy angosto: sin flechas (tocar el texto avanza la guía).
        assertEquals(ReaderToolbar.ToolbarFit(emoji = true, arrows = false), ReaderToolbar.toolbarFit(280.dp, true, emojiLabels, plainLabels))
        assertEquals(ReaderToolbar.ToolbarFit(emoji = false, arrows = false), ReaderToolbar.toolbarFit(230.dp, true, emojiLabels, plainLabels))
        // Sin guía activa nunca hay flechas.
        assertFalse(ReaderToolbar.toolbarFit(500.dp, false, emojiLabels, plainLabels).arrows)
    }

    @Test fun compareBarGivesUpTheEmojiBeforeTheViewTogetherWords() {
        val emojiTabs = 215.dp
        val plainTabs = 170.dp
        val button = 90.dp
        assertEquals(ReaderToolbar.CompareFit(emoji = true, buttonText = true), ReaderToolbar.compareFit(360.dp, emojiTabs, plainTabs, button))
        assertEquals(ReaderToolbar.CompareFit(emoji = false, buttonText = true), ReaderToolbar.compareFit(310.dp, emojiTabs, plainTabs, button))
        assertEquals(ReaderToolbar.CompareFit(emoji = true, buttonText = false), ReaderToolbar.compareFit(260.dp, emojiTabs, plainTabs, button))
        assertEquals(ReaderToolbar.CompareFit(emoji = false, buttonText = false), ReaderToolbar.compareFit(220.dp, emojiTabs, plainTabs, button))
        assertEquals(listOf("ADAPTED", "ORIGINAL"), CompareTab.entries.map { it.name })
    }

    @Test fun toolbarAndCompareBarDecideWithTheRealTextWidth() {
        val screen = code("reader/ReaderScreen.kt").substringAfter("private fun GuideBar").substringBefore("private val ToolbarShape")
        assertTrue("la barra mide sus etiquetas", "rememberTextWidths(" in screen && "ReaderToolbar.toolbarFit(" in screen)
        val bar = code("reader/ReaderExtras.kt").substringAfter("private fun CompareBar").substringBefore("private fun AdaptedComparisonText")
        assertTrue("el comparador mide sus etiquetas", "rememberTextWidths(" in bar && "ReaderToolbar.compareFit(" in bar)
    }

    @Test fun listenAndCloseAreAlwaysVisibleInTheDefinitionCard() {
        val card = code("reader/ReaderExtras.kt").substringAfter("private fun WordDefinitionCard").substringBefore("/** Límites del texto de la definición")
        assertTrue("Escuchar y Cerrar van antes del área que se desplaza",
            card.indexOf("onClick = onSpeak") in 0 until card.indexOf("verticalScroll") && card.indexOf("onClick = onClose") in 0 until card.indexOf("verticalScroll"))
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
