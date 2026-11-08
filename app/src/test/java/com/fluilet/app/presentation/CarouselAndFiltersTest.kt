package com.fluilet.app.presentation

import androidx.compose.ui.unit.dp
import com.fluilet.app.domain.gamification.CardRarity
import com.fluilet.app.domain.gamification.CollectibleCard
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.model.AdminUser
import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.presentation.admin.AdminAccountFilter
import com.fluilet.app.presentation.common.CarouselMotion
import com.fluilet.app.presentation.common.ChipPaging
import com.fluilet.app.presentation.fluibot.AlbumFilter
import com.fluilet.app.presentation.fluibot.EvolutionTrack
import com.fluilet.app.presentation.fluibot.ShopCatalog
import com.fluilet.app.presentation.fluibot.StageLayout
import com.fluilet.app.presentation.home.LibraryFilter
import com.fluilet.app.presentation.home.LibraryQuickFilter
import com.fluilet.app.presentation.home.LibraryShelf
import com.fluilet.app.testutil.sampleAdaptation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Carruseles, tiras de chips paginadas y filtros rápidos: la tarjeta central ocupa el foco y las vecinas se asoman más
 * pequeñas y tenues, ningún chip queda cortado en el borde y cada filtro deja ver exactamente lo que dice.
 */
class CarouselAndFiltersTest {

    // --- Carrusel ----------------------------------------------------------------------------------

    @Test fun neighbourCardsShrinkAndFadeWithinTheRequestedRange() {
        assertEquals(1f, CarouselMotion.scale(0f), 1e-6f)
        assertEquals(1f, CarouselMotion.alpha(0f), 1e-6f)
        assertEquals(0.93f, CarouselMotion.scale(1f), 1e-6f)
        assertEquals(0.75f, CarouselMotion.alpha(-1f), 1e-6f)
        // Más lejos de una página no baja de los mínimos; a media página va a medio camino.
        assertEquals(0.93f, CarouselMotion.scale(-2.5f), 1e-6f)
        assertEquals(0.965f, CarouselMotion.scale(0.5f), 1e-6f)
        assertEquals(0.875f, CarouselMotion.alpha(0.5f), 1e-6f)
        assertEquals(24f, CarouselMotion.SidePeek.value, 0f)
        assertEquals(12f, CarouselMotion.PageGap.value, 0f)
    }

    @Test fun dotsShowEveryPageOrAWindowThatFollowsTheCurrentOne() {
        assertEquals(0..5, CarouselMotion.dotWindow(current = 2, count = 6))
        assertEquals(0..6, CarouselMotion.dotWindow(current = 0, count = 12))
        assertEquals(3..9, CarouselMotion.dotWindow(current = 6, count = 12))
        assertEquals(5..11, CarouselMotion.dotWindow(current = 11, count = 12))
    }

    @Test fun shopPreviewIsAThirdOfTheCardAndNeverHuge() {
        assertEquals(97.92f, ShopCatalog.previewWidth(272.dp).value, 0.01f)
        assertEquals(104f, ShopCatalog.previewWidth(420.dp).value, 0.01f)
        assertTrue(ShopCatalog.StackedBelow.value < 272f)
    }

    // --- FluiBot grande y visor a pantalla completa ----------------------------------------------------

    @Test fun mainCardSceneIsBigButLeavesRoomForTheTabs() {
        // Teléfono alto (como 1080x2400): 216 dp; en pantallas más bajas, 200 dp para que las pestañas sigan a la vista.
        assertEquals(216f, StageLayout.cardSceneSize(296.dp, windowHeight = 891.dp).value, 0.01f)
        assertEquals(200f, StageLayout.cardSceneSize(296.dp, windowHeight = 780.dp).value, 0.01f)
        // Tarjeta angosta: nunca más del 80 % de su ancho.
        assertEquals(176f, StageLayout.cardSceneSize(220.dp, windowHeight = 800.dp).value, 0.01f)
        assertTrue(StageLayout.CardSceneMax.value in 200f..220f)
    }

    @Test fun stageButtonsOnlyShareARowWhenBothLabelsFitWhole() {
        // Medidas reales con OpenDyslexic a 15 sp: "Recargar Energía" ~205 dp y "Ver en grande" ~165 dp.
        assertFalse(StageLayout.buttonsSideBySide(205.dp, 165.dp, available = 347.dp))
        assertFalse(StageLayout.buttonsSideBySide(205.dp, 165.dp, available = 296.dp))
        // Tableta: caben los dos con sus textos completos.
        assertTrue(StageLayout.buttonsSideBySide(205.dp, 165.dp, available = 600.dp))
    }

    @Test fun viewerShowsAGiantFluiBotThatStillFitsTheScreen() {
        assertEquals(328f, StageLayout.viewerSceneSize(328.dp, windowHeight = 780.dp).value, 0.01f)
        assertEquals(360f, StageLayout.viewerSceneSize(420.dp, windowHeight = 900.dp).value, 0.01f)
        // Pantalla baja: no baja de 300 dp (el visor se desplaza); pantalla angosta: nunca más ancho que ella.
        assertEquals(300f, StageLayout.viewerSceneSize(328.dp, windowHeight = 520.dp).value, 0.01f)
        assertEquals(280f, StageLayout.viewerSceneSize(280.dp, windowHeight = 800.dp).value, 0.01f)
    }

    @Test fun readerSwitcherFitsInOneRowAndOnlyAddsAnArrowWhenNeeded() {
        // Diego, Mateo, Sofía y Valentina: con 340 dp caben los 4 en una fila, sin flecha.
        val names = listOf(69f, 73f, 68f, 96f)
        assertEquals(listOf(0..3), ChipPaging.inlinePages(names, available = 340f, gap = 8f, navWidth = 48f))
        // Si no caben, se reserva la flecha y cada página lleva chips enteros.
        val pages = ChipPaging.inlinePages(names, available = 270f, gap = 8f, navWidth = 48f)
        assertEquals(listOf(0..1, 2..3), pages)
        pages.forEach { range ->
            val used = range.sumOf { names[it].toDouble() } + 8.0 * (range.count() - 1)
            assertTrue("la página $range cabe junto a la flecha", used <= 270.0 - 48.0 - 8.0)
        }
    }

    // --- Tiras de chips paginadas ------------------------------------------------------------------

    @Test fun chipPagesNeverCutAChipAtTheRightEdge() {
        val widths = listOf(80f, 100f, 90f, 120f, 60f)
        val pages = ChipPaging.pages(widths, available = 300f, gap = 8f)
        assertEquals(listOf(0..2, 3..4), pages)
        pages.forEach { range ->
            val used = range.sumOf { widths[it].toDouble() } + 8.0 * (range.count() - 1)
            assertTrue("la página $range cabe completa", used <= 300.0)
        }
        assertEquals(listOf(0, 1, 2, 3, 4), pages.flatMap { it.toList() })
        assertEquals(1, ChipPaging.pageOf(4, pages))
        assertEquals(0, ChipPaging.pageOf(99, pages))
    }

    @Test fun inlineLabelOnlyShowsWhenEveryChipStillFits() {
        val names = listOf(73f, 78f, 73f, 114f)
        // Sin la etiqueta caben los 4 en la fila de un teléfono de 411 dp; con la etiqueta, no: la etiqueta cede su lugar.
        assertEquals(listOf(0..3), ChipPaging.inlinePages(names, available = 371f, gap = 6f, navWidth = 48f))
        assertFalse(ChipPaging.labelFits(names, available = 371f, gap = 6f, labelWidth = 110f))
        assertTrue(ChipPaging.labelFits(names, available = 600f, gap = 6f, labelWidth = 110f))
    }

    @Test fun aChipWiderThanTheRowGoesAloneAndEmptyListsHaveNoPages() {
        assertEquals(listOf(0..0, 1..1), ChipPaging.pages(listOf(400f, 50f), available = 300f, gap = 8f))
        assertEquals(listOf(0..1), ChipPaging.pages(listOf(146f, 146f), available = 300f, gap = 8f))
        assertTrue(ChipPaging.pages(emptyList(), available = 300f, gap = 8f).isEmpty())
    }

    // --- Evolución -----------------------------------------------------------------------------------

    @Test fun eachStageShowsHowFarFluiBotIs() {
        assertEquals(0f, EvolutionTrack.stageFraction(FluiBotStage.NUCLEO, 0), 1e-6f)
        assertEquals(1f, EvolutionTrack.stageFraction(FluiBotStage.NUCLEO, 200), 1e-6f)
        assertEquals(0.5f, EvolutionTrack.stageFraction(FluiBotStage.EXPLORADOR, 250), 1e-6f)
        // Etapa bloqueada: el camino recorrido hasta su umbral.
        assertEquals(0.5f, EvolutionTrack.stageFraction(FluiBotStage.GUARDIAN, 175), 1e-6f)
        assertEquals(1f, EvolutionTrack.stageFraction(FluiBotStage.SABIO, 1500), 1e-6f)
        assertEquals(0f, EvolutionTrack.overallFraction(-5), 1e-6f)
        assertEquals(0.5f, EvolutionTrack.overallFraction(600), 1e-6f)
        assertEquals(1f, EvolutionTrack.overallFraction(5000), 1e-6f)
    }

    // --- Álbum ---------------------------------------------------------------------------------------

    @Test fun albumFilterSplitsUnlockedAndUndiscoveredCards() {
        val cards = listOf(
            CollectibleCard("deportes", "Campeón del Juego Limpio", CardRarity.ORO, 4, "dato"),
            CollectibleCard("tecnologia", "Inventor Digital", null, 0, "dato"),
            CollectibleCard("ciencia_espacio", "Explorador del Cosmos", CardRarity.BRONCE, 1, "dato")
        )
        assertEquals(3, AlbumFilter.ALL.apply(cards).size)
        assertEquals(listOf("deportes", "ciencia_espacio"), AlbumFilter.UNLOCKED.apply(cards).map { it.categoryCode })
        assertEquals(listOf("tecnologia"), AlbumFilter.LOCKED.apply(cards).map { it.categoryCode })
    }

    // --- Biblioteca ----------------------------------------------------------------------------------

    private val library = listOf(
        LibraryItem("doc-1", "deportes", 1, 45.0, 82.0),
        LibraryItem("doc-2", "ciencia_espacio", 3, 50.0, 70.0),
        LibraryItem("doc-3", "deportes", 3, 40.0, 68.0),
        LibraryItem("doc-4", "tecnologia", null, 30.0, 60.0)
    )
    private val demo = listOf(sampleAdaptation(documentId = null, category = "animales_naturaleza", level = 2))

    @Test fun quickFiltersGroupLevelsAndSavedReadings() {
        val all = LibraryShelf.entries(library, demo, LibraryFilter())
        assertEquals(listOf("Todas", "Inicial (Niv. 1–2)", "Intermedio (Niv. 3)", "Avanzado (Niv. 4–5)", "Guardadas"), LibraryQuickFilter.entries.map { it.label })
        // El chip va en dos renglones: nombre arriba; banda y cantidad abajo.
        assertEquals("Niv. 1–2 · 4", LibraryQuickFilter.INICIAL.detailLine(4))
        assertEquals("6", LibraryQuickFilter.ALL.detailLine(6))
        val counts = LibraryShelf.quickCounts(all)
        assertEquals(5, counts[LibraryQuickFilter.ALL])
        assertEquals(2, counts[LibraryQuickFilter.INICIAL])
        assertEquals(2, counts[LibraryQuickFilter.INTERMEDIO])
        assertEquals(0, counts[LibraryQuickFilter.AVANZADO])
        // "Guardadas" = las del servidor, también la que no trae nivel.
        assertEquals(4, counts[LibraryQuickFilter.GUARDADAS])
        assertEquals(
            listOf("doc-3"),
            LibraryShelf.entries(library, demo, LibraryFilter(category = "deportes", quick = LibraryQuickFilter.INTERMEDIO)).map { it.item!!.documentId }
        )
    }

    @Test fun suggestionsFollowTheReaderLevel() {
        val all = LibraryShelf.entries(library, demo, LibraryFilter())
        val forLevel2 = LibraryShelf.suggested(all, readerLevel = 2)
        // Primero las del mismo nivel, luego las de un nivel arriba o abajo; nunca las que no traen nivel.
        assertEquals(2, forLevel2.first().level)
        assertEquals(setOf(1, 2, 3), forLevel2.mapNotNull { it.level }.toSet())
        assertEquals(4, forLevel2.size)
        assertTrue(LibraryShelf.suggested(all, readerLevel = null).isEmpty())
        assertTrue(LibraryShelf.suggested(all, readerLevel = 5).isEmpty())
        assertEquals(2, LibraryShelf.suggested(all, readerLevel = 2, limit = 2).size)
    }

    // --- Panel de Administración ---------------------------------------------------------------------

    @Test fun adminFiltersAreExclusiveAndCountEveryAccount() {
        val users = listOf(
            AdminUser("1", "ana@correo.com", "TUTOR", isMasterAdmin = false, isActive = true, profilesCount = 2),
            AdminUser("2", "tutor@fluilet.com", "TUTOR", isMasterAdmin = false, isActive = true, profilesCount = 4),
            AdminUser("3", "luis@correo.com", "ADULTO_INDEPENDIENTE", isMasterAdmin = false, isActive = true, profilesCount = 1),
            AdminUser("4", "nina@correo.com", "NINO", isMasterAdmin = false, isActive = false, profilesCount = 0)
        )
        val counts = AdminAccountFilter.counts(users)
        assertEquals(4, counts[AdminAccountFilter.ALL])
        assertEquals(1, counts[AdminAccountFilter.TUTORS])
        assertEquals(1, counts[AdminAccountFilter.ADULTS])
        assertEquals(2, counts[AdminAccountFilter.CHILDREN_DEMO])
        assertEquals(counts[AdminAccountFilter.ALL], AdminAccountFilter.entries.drop(1).sumOf { counts[it] ?: 0 })
        assertEquals(listOf("2", "4"), users.filter(AdminAccountFilter.CHILDREN_DEMO::matches).map { it.userId })
        assertEquals(listOf("Todas", "Tutores", "Adultos", "Niños/Demo"), AdminAccountFilter.entries.map { it.label })
    }
}
