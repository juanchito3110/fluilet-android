package com.fluilet.app.presentation

import com.fluilet.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Auditoría del código de las pantallas: ningún texto se esconde tras "...", ninguna fila de chips se corta en el borde
 * derecho del lector, la tienda no usa alturas fijas, y el aviso de privacidad y la baja de cuenta están a la vista.
 */
class LayoutAuditTest {

    private val presentation: File = listOf("src/main/java/com/fluilet/app/presentation", "app/src/main/java/com/fluilet/app/presentation")
        .map(::File).first { it.isDirectory }

    private fun source(path: String) = File(presentation, path).readText()

    @Test fun noScreenHidesTextBehindAnEllipsis() {
        val offenders = presentation.walkTopDown().filter { it.extension == "kt" && it.name != "AdaptiveText.kt" }
            .filter { "TextOverflow.Ellipsis" in it.readText() }.map { it.name }.toList()
        assertEquals("pantallas con puntos suspensivos", emptyList<String>(), offenders)
    }

    @Test fun theReaderKeywordsToolsAndPalettesWrapInsteadOfBeingCutAtTheEdge() {
        listOf("reader/ReaderScreen.kt", "reader/ReaderExtras.kt").forEach { path ->
            val code = source(path)
            assertTrue("$path sin filas deslizables", "horizontalScroll" !in code && "LazyRow" !in code)
        }
        val glossary = source("reader/ReaderExtras.kt").substringAfter("internal fun GlossaryBar").substringBefore("private fun WordDefinitionCard")
        assertTrue("las palabras clave van en una tira paginada de una fila", "ChipCarousel(" in glossary)
        assertTrue("la definición se despliega debajo, no en una ventana", "AnimatedVisibility(" in glossary && "AlertDialog" !in glossary)
    }

    @Test fun longListsBecameCarouselsSubTabsAndCollapsibleCards() {
        val shop = source("fluibot/FluiBotSections.kt")
        // Accesorios, escenarios, etapas de evolución y álbum.
        assertTrue("la tienda, la evolución y el álbum usan carruseles", Regex("FluiletCarousel\\(").findAll(shop).count() >= 4)
        assertTrue("Avatar usa la cabecera compacta de FluiBot", "compact = true" in source("home/FluiBotTab.kt"))
        assertTrue("los logros van en un carrusel", "FluiletCarousel(" in source("home/ProgressDashboard.kt"))
        val cards = source("home/HomeSections.kt").substringAfter("internal fun CartasTab")
        assertTrue("Cartas tiene sub-pestañas animadas", "AnimatedContent(" in cards && "CardsView.LOGROS" in cards && "CardsView.PROGRESO" in cards)
        assertTrue("los temas sugeridos van en una tira deslizable", "TopicSuggestions.forCategory(category)" in source("newreading/NewReadingScreen.kt") &&
            "ChipCarousel(" in source("newreading/NewReadingScreen.kt"))
        assertTrue("la biblioteca tiene filtros rápidos", "LibraryQuickFilter.entries" in source("home/HomeSections.kt"))
        assertTrue("Cuenta y privacidad se pliega", "CollapsibleCard(" in source("home/AccountSection.kt"))
        val admin = source("admin/MasterAdminScreen.kt")
        assertTrue("el panel maestro filtra y pliega las cuentas", "AdminAccountFilter" in admin && "CollapsibleCard(" in admin)
    }

    @Test fun theShopCardsHaveNoFixedHeights() {
        val shop = source("fluibot/FluiBotSections.kt")
        assertTrue("sin .height(...) fijo en la tienda", ".height(" !in shop)
        assertTrue("el precio va fuera del dibujo", "PricePill(" in shop)
    }

    @Test fun privacyNoticeAndAccountDeletionAreReachable() {
        val strings = R.string::class.java.fields.map { it.name }.toSet()
        listOf("privacy_title", "privacy_share_body", "account_delete", "account_delete_title").forEach {
            assertTrue("falta el texto $it", it in strings)
        }
        assertTrue("Login y Registro muestran el aviso", "PrivacyNoticeLink()" in source("auth/AuthScreens.kt"))
        assertTrue("Inicio muestra Cuenta y privacidad", "AccountPrivacyCard(" in source("home/HomeSections.kt"))
    }
}
