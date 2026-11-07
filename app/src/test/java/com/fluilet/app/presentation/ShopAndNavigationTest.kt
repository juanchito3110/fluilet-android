package com.fluilet.app.presentation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.Scenario
import com.fluilet.app.presentation.common.NavLabels
import com.fluilet.app.presentation.common.UnlimitedLines
import com.fluilet.app.presentation.fluibot.PriceTag
import com.fluilet.app.presentation.fluibot.ShopCatalog
import com.fluilet.app.presentation.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tienda de FluiBot (precio fuera del dibujo, nombres completos) y barra inferior con un solo tamaño de letra. */
class ShopAndNavigationTest {

    @Test fun thePriceTagShowsGemsFreeOwnedOrEquipped() {
        assertEquals(PriceTag.Gems(15), ShopCatalog.priceTag(owned = false, equipped = false, cost = 15))
        assertEquals(PriceTag.Free, ShopCatalog.priceTag(owned = false, equipped = false, cost = 0))
        assertEquals(PriceTag.Owned, ShopCatalog.priceTag(owned = true, equipped = false, cost = 35))
        assertEquals(PriceTag.Equipped, ShopCatalog.priceTag(owned = true, equipped = true, cost = 35))
    }

    @Test fun everyShopNameAndDescriptionIsShownWholeWithoutALineLimit() {
        // Los textos de la tienda usan AdaptiveText sin tope de renglones: nunca "Antena Prisma..." ni "Visor Hologr...".
        assertEquals(Int.MAX_VALUE, UnlimitedLines)
        val names = Accessory.entries.map { it.displayName } + Scenario.entries.map { it.displayName }
        listOf("Antena Prisma Neón", "Visor Holográfico", "Corbatín LED de Gala", "Sombrero de Explorador", "Estación Espacial Estelar")
            .forEach { assertTrue("$it completo en el catálogo", it in names) }
        (Accessory.entries.map { it.description } + Scenario.entries.map { it.description }).forEach {
            assertTrue("descripción completa, sin puntos suspensivos: $it", !it.endsWith("...") && !it.endsWith("…"))
        }
    }

    @Test fun theFiveBottomLabelsShareOneSize() {
        assertEquals(listOf("Inicio", "Lecturas", "Adaptar", "Avatar", "Cartas"), HomeTab.entries.map { it.label })
        // Si cabe la más ancha, las 5 van a 12 sp.
        assertEquals(12.sp, NavLabels.uniformSize(widestAtBase = 60f, available = 64f))
        // Si no cabe, todas bajan juntas al mismo tamaño (de 0,5 en 0,5 sp), nunca por debajo de 9 sp.
        assertEquals(10.5.sp, NavLabels.uniformSize(widestAtBase = 80f, available = 70f))
        assertEquals(9.sp, NavLabels.uniformSize(widestAtBase = 200f, available = 60f))
    }

    @Test fun eachLabelSlotDiscountsTheGapsBetweenItems() {
        // 360 dp: (360 - 4 × 8) / 5 - 4 = 61,6 dp por etiqueta.
        assertEquals(61.6f, NavLabels.slotWidth(360.dp, 5).value, 0.01f)
        assertEquals(0f, NavLabels.slotWidth(20.dp, 5).value, 0.01f)
    }
}
