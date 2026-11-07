package com.fluilet.app.presentation

import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.explanationFor
import com.fluilet.app.domain.quiz.ComprehensionQuiz
import com.fluilet.app.domain.quiz.QuizAnswer
import com.fluilet.app.presentation.reader.OptionLook
import com.fluilet.app.presentation.reader.QuizAction
import com.fluilet.app.presentation.reader.QuizReview
import com.fluilet.app.presentation.reader.QuizVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** El cuestionario rediseñado: insignias A–D, comprobar, explicación, siguiente y resumen final. */
class QuizReviewTest {

    private fun question(id: String, correct: Int, explanation: String? = "La lectura dice que fue en 1902.") = ComprehensionQuestion(
        id, "¿En qué año se fundó el club según la lectura?",
        listOf("En el año 1902", "En el año 1912", "En el año 1892", "En el año 1952"), correct,
        hint = "Busca el año.", explanation = explanation
    )

    private val quiz = ComprehensionQuiz(listOf(question("q1", 0), question("q2", 2), question("q3", 3)))

    @Test fun optionsGetCircularLettersAToD() {
        assertEquals(listOf("A", "B", "C", "D"), (0..3).map(QuizReview::letter))
    }

    @Test fun selectingThenCheckingPaintsTheRightAndTheWrongOption() {
        assertEquals(OptionLook.IDLE, QuizReview.look(0, selected = null, answer = null, correctIndex = 0))
        assertEquals(OptionLook.SELECTED, QuizReview.look(1, selected = 1, answer = null, correctIndex = 0))
        val wrong = QuizAnswer(selectedIndex = 1, isCorrect = false)
        assertEquals("la correcta en verde", OptionLook.CORRECT, QuizReview.look(0, 1, wrong, 0))
        assertEquals("la elegida en rojo suave", OptionLook.WRONG, QuizReview.look(1, 1, wrong, 0))
        assertEquals(OptionLook.DIMMED, QuizReview.look(2, 1, wrong, 0))
        val right = QuizAnswer(selectedIndex = 0, isCorrect = true)
        assertEquals(OptionLook.CORRECT, QuizReview.look(0, 0, right, 0))
        assertEquals(OptionLook.DIMMED, QuizReview.look(3, 0, right, 0))
    }

    @Test fun theFlowIsCheckNextAndFinallySeeResults() {
        assertEquals(QuizAction.CHECK, QuizReview.action(quiz))
        assertEquals(0f, QuizReview.progress(quiz))

        val first = quiz.answer(0)
        assertEquals(QuizAction.NEXT, QuizReview.action(first))
        assertEquals(1f / 3, QuizReview.progress(first), 1e-6f)

        val last = first.next().answer(2).next().answer(0)
        assertEquals(QuizAction.RESULTS, QuizReview.action(last))
        assertEquals(1f, QuizReview.progress(last))
        assertEquals(2, last.result.correct)
        assertEquals(3, last.result.answered)
    }

    @Test fun theSummaryCelebratesAccordingToTheScore() {
        assertEquals(QuizVerdict.PERFECT, QuizReview.verdict(3, 3))
        assertEquals(QuizVerdict.GOOD, QuizReview.verdict(2, 3))
        assertEquals(QuizVerdict.TRY_AGAIN, QuizReview.verdict(1, 3))
        assertEquals(QuizVerdict.TRY_AGAIN, QuizReview.verdict(0, 0))
    }

    @Test fun theExplanationOfTheReadingIsShownAfterChecking() {
        assertEquals("La lectura dice que fue en 1902.", question("q", 0).explanationFor(wasCorrect = false))
        // Sin explicación del servidor, una local que nombra la respuesta correcta y nunca desanima.
        val local = question("q", 0, explanation = null).explanationFor(wasCorrect = false)
        assertEquals(true, local.contains("En el año 1902"))
    }
}
