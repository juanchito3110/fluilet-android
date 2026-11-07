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
        assertTrue("el Avatar puede abrir el visor con accesorios y escenarios", "onToggleAccessory = onToggleAccessory" in source("home/FluiBotTab.kt"))
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

    /** FluiBot grande y limpio, visor a pantalla completa con su propio botón y selector de lector en una fila. */
    @Test fun avatarShowsACleanBigFluiBotAndAFullScreenViewer() {
        val sections = source("fluibot/FluiBotSections.kt")
        val scene = sections.substringAfter("internal fun FluiBotScene").substringBefore("/** Saldo de gemas")
        assertTrue("nada escrito encima de FluiBot", "Text(" !in scene && "fluibot_live_preview" !in sections)
        assertTrue("tocar a FluiBot le da energía", "onTap = onCelebrate" in scene)
        assertTrue("un botón aparte abre el visor", "ViewBigButton({ viewerOpen = true }" in sections)
        val viewer = source("fluibot/FluiBotViewer.kt")
        assertTrue("visor de ancho completo", "usePlatformDefaultWidth = false" in viewer)
        assertTrue("en el visor también se recarga energía y se cierra", "RechargeButton(onCelebrate" in viewer && "R.string.fluibot_viewer_close" in viewer)
        assertTrue("en el visor se ponen y quitan accesorios y escenarios", "onToggleAccessory(it.accessory)" in viewer && "onEquipScenario(it.scenario)" in viewer)
        val switcher = source("home/HomeSections.kt").substringAfter("internal fun ReaderSwitcher").substringBefore("/** Lectura lista para empezar")
        assertTrue("el lector activo va en una sola fila", "InlineChipStrip(" in switcher && "FlowRow" !in switcher)
    }

    /** El aviso y la confirmación dicen exactamente lo que hace el servidor (fluilet-backend 14accd0). */
    @Test fun privacyTextsMatchThePermanentServerDeletionAndPublicPages() {
        val res = listOf("src/main/res/values/strings_privacy.xml", "app/src/main/res/values/strings_privacy.xml").map(::File).first { it.isFile }
        val xml = res.readText()
        fun string(name: String) = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]

        assertEquals("http://67.205.150.186/privacidad", string("privacy_policy_url"))
        assertEquals("http://67.205.150.186/eliminar-cuenta", string("privacy_delete_url"))
        listOf("privacy_retention_body", "account_delete_body").forEach { name ->
            val text = string(name)
            assertTrue("$name: borrado permanente e inmediato", "permanente e inmediata" in text)
            assertTrue("$name: del servidor y del teléfono", "del servidor y de este teléfono" in text)
            assertTrue("$name: perfiles lectores e historial", "perfiles lectores" in text && "historial" in text)
        }
        assertTrue("el tutor sabe que se borran las cuentas de niño", "cuentas de niño" in string("account_delete_body_tutor"))
        // Nada de la baja lógica anterior ("se dará de baja", "dejarán de estar disponibles").
        listOf("dará de baja", "desactiv", "dejarán de estar disponibles").forEach { old ->
            assertTrue("sin \"$old\" en el aviso", old !in xml)
        }
        // Proveedores técnicos declarados igual que en la política pública.
        assertTrue("DigitalOcean" in string("privacy_share_body") && "Brevo" in string("privacy_share_body") && "Wikipedia" in string("privacy_share_body"))

        val notice = source("common/PrivacyNotice.kt")
        assertTrue("el aviso incluye la conservación", "R.string.privacy_retention_title" in notice)
        assertTrue("el aviso enlaza las páginas públicas", "PrivacyPublicLinks()" in notice && "LinkAnnotation.Url" in notice)
        assertTrue("la tarjeta de cuenta enlaza las páginas públicas", "PrivacyPublicLinks()" in source("home/AccountSection.kt"))
    }
}
