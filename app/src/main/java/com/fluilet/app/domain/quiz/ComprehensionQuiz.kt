package com.fluilet.app.domain.quiz

import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.ComprehensionResult

data class QuizAnswer(val selectedIndex: Int, val isCorrect: Boolean)

/**
 * Estado del Mini-Reto de Comprensión. Inmutable y sin Android: la pregunta se responde una sola vez
 * (retroalimentación inmediata) y el resultado se resume en respondidas / acertadas / puntaje.
 *
 * @property viewIndex pregunta que se está mostrando (puede tener ya su retroalimentación visible).
 */
data class ComprehensionQuiz(
    val questions: List<ComprehensionQuestion>,
    val answers: List<QuizAnswer?> = List(questions.size) { null },
    val viewIndex: Int = 0
) {
    init {
        require(answers.size == questions.size) { "answers debe tener una entrada por pregunta" }
    }

    val current: ComprehensionQuestion? get() = questions.getOrNull(viewIndex)
    val currentAnswer: QuizAnswer? get() = answers.getOrNull(viewIndex)
    val answered: Int get() = answers.count { it != null }
    val correct: Int get() = answers.count { it?.isCorrect == true }
    val isComplete: Boolean get() = questions.isNotEmpty() && answered == questions.size
    val isLastQuestion: Boolean get() = viewIndex == questions.lastIndex

    /** `true` cuando la pregunta visible ya se contestó y hay otra pendiente. */
    val canAdvance: Boolean get() = currentAnswer != null && !isLastQuestion

    val result: ComprehensionResult get() = ComprehensionResult(answered, correct)

    /** Responde la pregunta visible. Ignora respuestas repetidas y opciones fuera de rango. */
    fun answer(optionIndex: Int): ComprehensionQuiz {
        val question = current ?: return this
        if (currentAnswer != null || optionIndex !in question.options.indices) return this
        val updated = answers.toMutableList().also {
            it[viewIndex] = QuizAnswer(optionIndex, optionIndex == question.correctOptionIndex)
        }
        return copy(answers = updated)
    }

    fun next(): ComprehensionQuiz = if (canAdvance) copy(viewIndex = viewIndex + 1) else this
}
