package com.fluilet.app.presentation.reader

import com.fluilet.app.domain.quiz.ComprehensionQuiz
import com.fluilet.app.domain.quiz.QuizAnswer

/** Cómo se ve cada opción del mini-reto antes y después de comprobar la respuesta. */
enum class OptionLook {
    /** Aún sin elegir. */
    IDLE,

    /** Elegida, esperando "Comprobar respuesta". */
    SELECTED,

    /** La respuesta correcta (verde con palomita). */
    CORRECT,

    /** La que eligió el niño y no era (rojo suave con una X). */
    WRONG,

    /** Las demás, atenuadas tras comprobar. */
    DIMMED
}

/** Acción principal del mini-reto en cada momento. */
enum class QuizAction { CHECK, NEXT, RESULTS, NONE }

/** Mensaje del resumen final según los aciertos. */
enum class QuizVerdict { PERFECT, GOOD, TRY_AGAIN }

/** Reglas visuales del mini-reto (sin Android, probadas con pruebas unitarias). */
object QuizReview {

    /** Insignia circular de cada opción: A, B, C, D... */
    fun letter(index: Int): String = ('A' + index).toString()

    fun look(index: Int, selected: Int?, answer: QuizAnswer?, correctIndex: Int): OptionLook = when {
        answer == null -> if (index == selected) OptionLook.SELECTED else OptionLook.IDLE
        index == correctIndex -> OptionLook.CORRECT
        index == answer.selectedIndex -> OptionLook.WRONG
        else -> OptionLook.DIMMED
    }

    /** Avance de la barra animada: preguntas ya comprobadas sobre el total. */
    fun progress(quiz: ComprehensionQuiz): Float =
        if (quiz.questions.isEmpty()) 0f else quiz.answered.toFloat() / quiz.questions.size

    /** "Comprobar respuesta" antes de responder; luego "Siguiente pregunta" o, en la última, "Ver resultados". */
    fun action(quiz: ComprehensionQuiz): QuizAction = when {
        quiz.current == null -> QuizAction.NONE
        quiz.currentAnswer == null -> QuizAction.CHECK
        quiz.canAdvance -> QuizAction.NEXT
        quiz.isComplete -> QuizAction.RESULTS
        else -> QuizAction.NONE
    }

    fun verdict(correct: Int, total: Int): QuizVerdict = when {
        total > 0 && correct >= total -> QuizVerdict.PERFECT
        total > 0 && correct * 2 >= total -> QuizVerdict.GOOD
        else -> QuizVerdict.TRY_AGAIN
    }
}
