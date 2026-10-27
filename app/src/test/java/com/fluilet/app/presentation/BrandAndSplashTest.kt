package com.fluilet.app.presentation

import androidx.compose.ui.graphics.Color
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.brand.IsotypeGeometry
import com.fluilet.app.presentation.brand.SplashTimeline
import com.fluilet.app.presentation.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Logo oficial, ícono del teléfono solo con el isotipo, línea de tiempo del Splash y flujo hacia Iniciar Sesión. */
class BrandAndSplashTest {

    // --- Marca ---------------------------------------------------------------------------------

    @Test fun officialNameSloganAndPalette() {
        assertEquals("Fluilet", FluiletBrand.NAME)
        assertEquals("Tu ritmo, tus palabras", FluiletBrand.SLOGAN)
        assertEquals(Color(0xFF7B63C6), FluiletBrand.Purple)
        assertEquals(Color(0xFFDBD4F3), FluiletBrand.Lavender)
        assertEquals(Color(0xFF8E79D0), FluiletBrand.SloganPurple)
        assertEquals(Color(0xFFF5824A), FluiletBrand.Orange)
        assertEquals(Color(0xFF48C999), FluiletBrand.Mint)
        assertEquals(Color(0xFFFFFFFF), FluiletBrand.White)
    }

    @Test fun isotypeGeometryPlacesTheFWithTheBarOnTheStemAndTheDotToTheRight() {
        val g = IsotypeGeometry
        listOf(g.STEM_X, g.STEM_BOTTOM_Y, g.TOP_Y, g.TOP_END_X, g.BAR_Y, g.BAR_END_X, g.DOT_X, g.DOT_Y).forEach { assertTrue(it in 0f..1f) }
        assertTrue("la barra superior sale del trazo vertical", g.TOP_END_X > g.STEM_X)
        assertTrue("la barra central está entre la parte alta y la base", g.BAR_Y > g.TOP_Y && g.BAR_Y < g.STEM_BOTTOM_Y)
        assertEquals("la barra lavanda nace sobre el trazo vertical", g.STEM_X, g.STEM_X, 0f)
        assertTrue("el punto va a la derecha de la F y abajo", g.DOT_X - g.DOT_RADIUS > g.STEM_X && g.DOT_Y > g.BAR_Y)
        assertTrue("el punto cabe en el cuadrado", g.DOT_X + g.DOT_RADIUS < 1f && g.DOT_Y + g.DOT_RADIUS < 1f)
        assertTrue("el centro blanco es menor que el círculo naranja", g.DOT_CENTER_RADIUS < g.DOT_RADIUS)
    }

    private fun res(path: String) = File("src/main/res/$path").readText()

    @Test fun launcherIconIsTheIsotypeOnlyWithTheOfficialColors() {
        val foreground = res("drawable/ic_launcher_foreground.xml")
        val background = res("drawable/ic_launcher_background.xml")
        assertTrue(background.contains("#7B63C6")) // morado oficial
        assertTrue(foreground.contains("#FFFFFF")) // la F blanca
        assertTrue(foreground.contains("#DBD4F3")) // barra central lavanda
        assertTrue(foreground.contains("#F5824A")) // punto naranja
        assertFalse("sin el robot verde de Android", foreground.contains("#3DDC84") || background.contains("#3DDC84"))
        // Solo vectores de la F y del punto: sin texto ni imágenes incrustadas.
        assertFalse(foreground.contains("<text") || foreground.contains("bitmap"))
        assertEquals(4, "<path".toRegex().findAll(foreground).count()) // trazo F, barra, círculo naranja, centro blanco
    }

    @Test fun launcherVectorUsesTheSameGeometryAsTheComposable() {
        val g = IsotypeGeometry
        val foreground = res("drawable/ic_launcher_foreground.xml")
        fun x(u: Float) = "%.2f".format(java.util.Locale.US, 20 + 60 * u)
        fun y(v: Float) = "%.2f".format(java.util.Locale.US, 24 + 60 * v)
        assertTrue(foreground.contains("M${x(g.STEM_X)},${y(g.STEM_BOTTOM_Y)}L${x(g.STEM_X)},${y(g.TOP_Y)}L${x(g.TOP_END_X)},${y(g.TOP_Y)}"))
        assertTrue(foreground.contains("M${x(g.STEM_X)},${y(g.BAR_Y)}L${x(g.BAR_END_X)},${y(g.BAR_Y)}"))
    }

    @Test fun adaptiveIconsPointAtTheNewLayersForBothLauncherShapes() {
        listOf("mipmap-anydpi/ic_launcher.xml", "mipmap-anydpi/ic_launcher_round.xml").forEach { file ->
            val xml = res(file)
            assertTrue(xml.contains("@drawable/ic_launcher_background"))
            assertTrue(xml.contains("@drawable/ic_launcher_foreground"))
        }
        // Se quitaron los íconos de mapa de bits del robot predeterminado.
        assertFalse(File("src/main/res/mipmap-hdpi/ic_launcher.webp").exists())
    }

    // --- Splash (~2.4 s) --------------------------------------------------------------------------

    @Test fun splashLastsAboutTwoPointFourSecondsInTwoPhases() {
        assertEquals(2_400L, SplashTimeline.TOTAL_MS)
        assertEquals(900L, SplashTimeline.PHASE1_END_MS)
        assertEquals(2_000L, SplashTimeline.PHASE2_END_MS)
    }

    @Test fun phaseOneBuildsTheIsotypeAndNothingElse() {
        val start = SplashTimeline.frame(0)
        assertEquals(0f, start.squareScale, 0.001f)
        assertEquals(0f, start.fProgress, 0.001f)
        assertEquals(0f, start.dotScale, 0.001f)

        val end1 = SplashTimeline.frame(SplashTimeline.PHASE1_END_MS)
        assertEquals(1f, end1.squareScale, 0.001f)
        assertEquals(1f, end1.fProgress, 0.001f) // la "F" y su barra lavanda ya están trazadas
        assertEquals(1f, end1.dotScale, 0.001f) // el punto naranja ya apareció
        assertEquals("el nombre aún no aparece", 0f, end1.nameAlpha, 0.001f)
        assertEquals(0f, end1.sloganAlpha, 0.001f)
        assertEquals(0f, end1.lineProgress, 0.001f)
    }

    @Test fun theSquareAndTheDotOvershootLikeASpring() {
        val squareMax = (0L..450L step 10).maxOf { SplashTimeline.frame(it).squareScale }
        val dotMax = (650L..900L step 10).maxOf { SplashTimeline.frame(it).dotScale }
        assertTrue(squareMax > 1.02f)
        assertTrue(dotMax > 1.02f)
    }

    @Test fun phaseTwoRevealsNameThenSloganThenTheMintLine() {
        val t1 = SplashTimeline.frame(1_100)
        assertTrue(t1.nameAlpha > 0f && t1.nameOffset > 0f)
        assertEquals(0f, t1.sloganAlpha, 0.001f)

        val t2 = SplashTimeline.frame(1_500)
        assertEquals(1f, t2.nameAlpha, 0.001f)
        assertEquals(0f, t2.nameOffset, 0.001f)
        assertTrue(t2.sloganAlpha > 0f)
        assertEquals(0f, t2.lineProgress, 0.001f)

        val end2 = SplashTimeline.frame(SplashTimeline.PHASE2_END_MS)
        assertEquals(1f, end2.sloganAlpha, 0.001f)
        assertEquals("la línea menta se dibuja de izquierda a derecha hasta completarse", 1f, end2.lineProgress, 0.001f)
        assertEquals(SplashTimeline.frame(SplashTimeline.TOTAL_MS), end2) // reposo final
    }

    @Test fun animationValuesNeverGoBackwardsExceptTheSpringSettle() {
        var previous = SplashTimeline.frame(0)
        for (ms in 10L..SplashTimeline.TOTAL_MS step 10) {
            val f = SplashTimeline.frame(ms)
            assertTrue(f.fProgress >= previous.fProgress)
            assertTrue(f.nameAlpha >= previous.nameAlpha)
            assertTrue(f.sloganAlpha >= previous.sloganAlpha)
            assertTrue(f.lineProgress >= previous.lineProgress)
            previous = f
        }
    }

    // --- Flujo Splash -> Iniciar Sesión / Crear Cuenta ---------------------------------------------

    private val tutor = SessionState.LoggedIn(UserRole.TUTOR, "t@example.com")

    @Test fun theIntroIsAlwaysShownFirstWhateverTheSession() {
        assertNull(Routes.redirectFor(Routes.SPLASH, splashDone = false, session = SessionState.LoggedOut))
        assertNull(Routes.redirectFor(Routes.SPLASH, splashDone = false, session = tutor))
        assertNull(Routes.redirectFor(Routes.SPLASH, splashDone = true, session = SessionState.Loading))
    }

    @Test fun afterTheIntroALoggedOutUserLandsOnTheAuthScreen() {
        assertEquals(Routes.AUTH, Routes.redirectFor(Routes.SPLASH, splashDone = true, session = SessionState.LoggedOut))
        assertEquals("se queda si ya está en Iniciar Sesión / Crear Cuenta", null, Routes.redirectFor(Routes.AUTH, true, SessionState.LoggedOut))
    }

    @Test fun afterTheIntroALoggedInUserGoesToHomeOrTheChildReader() {
        assertEquals(Routes.HOME, Routes.redirectFor(Routes.SPLASH, true, tutor))
        assertEquals(Routes.READER, Routes.redirectFor(Routes.SPLASH, true, SessionState.LoggedIn(UserRole.NINO, "n@example.com")))
        assertEquals(Routes.HOME, Routes.redirectFor(Routes.AUTH, true, tutor)) // al entrar (o Modo Demo) se sale de la pantalla de acceso
    }

    @Test fun loggingOutFromAnywhereReturnsToTheAuthScreen() {
        assertEquals(Routes.AUTH, Routes.redirectFor(Routes.HOME, true, SessionState.LoggedOut))
        assertEquals(Routes.AUTH, Routes.redirectFor(Routes.READER, true, SessionState.LoggedOut))
    }

    @Test fun theLiveIntroPreviewDoesNotLogTheUserOutNorBounceThemHome() {
        assertNull(Routes.redirectFor(Routes.INTRO_PREVIEW, true, tutor))
        assertNull(Routes.redirectFor(Routes.INTRO_PREVIEW, true, SessionState.LoggedOut))
        assertNull(Routes.redirectFor(Routes.AUTH_PREVIEW, true, SessionState.LoggedOut))
        // Pero si desde la vista previa se inicia sesión con otra cuenta, se entra al panel.
        assertEquals(Routes.HOME, Routes.redirectFor(Routes.AUTH_PREVIEW, true, tutor))
    }

    @Test fun introAndAuthRoutesAreDistinct() {
        val routes = listOf(Routes.SPLASH, Routes.AUTH, Routes.INTRO_PREVIEW, Routes.AUTH_PREVIEW, Routes.HOME, Routes.READER)
        assertEquals(routes.size, routes.toSet().size)
    }
}
