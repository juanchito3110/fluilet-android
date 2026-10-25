package com.fluilet.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.fluilet.app.domain.model.BadgeCatalog
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.quiz.ComprehensionQuiz
import com.fluilet.app.presentation.fluibot.FluiBotUiState
import com.fluilet.app.presentation.home.ProgressDashboardContent
import com.fluilet.app.presentation.reader.QuizCard
import com.fluilet.app.presentation.theme.FluiletTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** Pruebas de interfaz de los módulos educativos: Mini-Reto de Comprensión y "Mi avance". */
class EducationalUiTest {

    @get:Rule val rule = createComposeRule()

    /** Captura para revisión visual. Es de mejor esfuerzo: PixelCopy puede fallar en emuladores sin ventana. */
    private fun screenshot(name: String) {
        runCatching {
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val questions = listOf(
        ComprehensionQuestion("q1", "¿Dónde están los agujeros negros?", listOf("En el espacio", "En el mar", "En la casa"), 0),
        ComprehensionQuestion("q2", "¿Qué no puede escapar de ellos?", listOf("El agua", "La luz", "El sol"), 1)
    )

    // --- Mini-Reto de Comprensión ------------------------------------------------------------

    @Test fun quizGivesImmediateFeedbackAndScoresTheChallenge() {
        var quiz by mutableStateOf(ComprehensionQuiz(questions))
        var finished = false
        rule.setContent {
            FluiletTheme {
                QuizCard(quiz, ReadingSettings(), AgeGroup.NINE_TO_ELEVEN, 10, onAnswer = { quiz = quiz.answer(it) }, onNext = { quiz = quiz.next() }, onFinish = { finished = true })
            }
        }
        rule.onNodeWithText("Mini-Reto de Comprensión").assertIsDisplayed()
        rule.onNodeWithText("Pregunta 1 de 2").assertIsDisplayed()
        rule.onNodeWithText("¿Dónde están los agujeros negros?").assertIsDisplayed()
        screenshot("quiz_1_pregunta")

        rule.onNodeWithText("En el espacio").performClick() // correcta
        rule.onNodeWithText("¡Correcto! Muy bien.").assertIsDisplayed()
        rule.onNodeWithText("En el mar").assertIsNotEnabled() // ya respondida: no se puede cambiar
        screenshot("quiz_2_correcta")

        rule.onNodeWithText("Siguiente pregunta").performClick()
        rule.onNodeWithText("Pregunta 2 de 2").assertIsDisplayed()

        rule.onNodeWithText("El agua").performClick() // incorrecta
        rule.onNodeWithText("¡Casi! La respuesta era: La luz").assertIsDisplayed()
        rule.onNodeWithText("Mini-reto: 1 de 2 correctas · 50 %").assertIsDisplayed()
        screenshot("quiz_3_resultado")

        rule.onNodeWithText("Continuar").performClick()
        assertEquals(true, finished)
        assertEquals(50, quiz.result.score)
    }

    // --- Mi avance ---------------------------------------------------------------------------

    private fun lesson(n: Int, category: String, level: Int, completed: Boolean, comprehension: Int?, original: Double, adapted: Double) =
        LessonProgress(n, "r$n", Instant.parse("2026-10-0${n}T10:00:00Z"), category, level, completed, if (completed) 100 else 45, comprehension, 420L * n, original, adapted)

    private val progress = EducationalProgress(
        totalLessons = 3, completedLessons = 2, incompleteLessons = 1, completionRatePercent = 66.67,
        totalReadingSeconds = 3900, averageComprehension = 75.0, recommendedLevel = 3,
        lessons = listOf(
            lesson(1, "ciencia_espacio", 2, true, 100, 31.0, 77.0),
            lesson(2, "deportes", 3, true, 50, 40.0, 72.0),
            lesson(3, "tecnologia", 3, false, null, 28.0, 66.0)
        ),
        badges = BadgeCatalog.all.mapIndexed { i, b -> EducationalBadge(b.code, b.title, b.description, i < 3) }
    )

    @Test fun dashboardShowsSummaryEvolutionAndAllFiveBadges() {
        rule.setContent {
            FluiletTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { ProgressDashboardContent(progress, null, FluiBotUiState(), {}, {}, {}) }
            }
        }
        rule.onNodeWithText("Mi avance").assertIsDisplayed()
        rule.onNodeWithText("Avance y Evolución").assertIsDisplayed()
        rule.onNodeWithText("2 de 3 lecciones").assertIsDisplayed()
        rule.onNodeWithText("67 % de cumplimiento · 1 incompletas").assertIsDisplayed()
        rule.onNodeWithText("1 h 05 min").assertIsDisplayed()
        rule.onNodeWithText("75 %").assertIsDisplayed()
        rule.onNodeWithText("Nivel 3 de 5").assertIsDisplayed()
        screenshot("avance_1_resumen")

        rule.onNodeWithText("Evolución por lección").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Lección 3 · Tecnología · Nivel 3").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Incompleta · 45 %").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Comprensión: sin mini-reto").performScrollTo().assertIsDisplayed()
        screenshot("avance_2_evolucion")

        rule.onNodeWithText("Insignias educativas").performScrollTo().assertIsDisplayed()
        BadgeCatalog.all.forEach { rule.onNodeWithText(it.title).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText("3 de 5 desbloqueadas").performScrollTo().assertIsDisplayed()
        screenshot("avance_3_insignias")
    }

    @Test fun emptyStateStillShowsTheFiveLockedBadgesAndNeverCrashes() {
        rule.setContent {
            FluiletTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    ProgressDashboardContent(EducationalProgress.empty(), "No pudimos cargar el avance ahora.", FluiBotUiState(), {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("0 de 0 lecciones").assertIsDisplayed()
        rule.onNodeWithText("Aún sin mini-retos").assertIsDisplayed()
        rule.onNodeWithText("No pudimos cargar el avance ahora.").assertIsDisplayed()
        rule.onNodeWithText("Insignias educativas").performScrollTo()
        BadgeCatalog.all.forEach { rule.onNodeWithText(it.title).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText("0 de 5 desbloqueadas").performScrollTo().assertIsDisplayed()
        screenshot("avance_4_vacio")
    }
}
