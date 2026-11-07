package com.fluilet.app.domain

import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.text.ShortLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Banco local de respaldo con el estándar del backend `adfba34`: preguntas sobre datos concretos de la lectura (años,
 * nombres, cantidades, causas, lugares), 4 opciones del mismo tipo, explicación que cita la lectura y ninguna frase
 * truncada. Sin conexión, "Bosque Encantado" es un cuento infantil, nunca la reseña de una novela.
 */
class LocalQuizBankTest {

    private val lessons = DemoData.lessons() + listOfNotNull(DemoData.offlineLesson("cuentos_aventuras", NlpLevel.NIVEL_2, "Bosque Encantado"))

    @Test fun everyLessonHasThreeSpecificQuestionsWithFourOptionsAndAnExplanation() {
        assertEquals(4, lessons.size)
        lessons.forEach { lesson ->
            assertEquals("${lesson.title}: 3 preguntas", 3, lesson.questions.size)
            lesson.questions.forEach { q ->
                assertEquals("${q.question}: 4 opciones", 4, q.options.size)
                assertEquals("${q.question}: opciones distintas", 4, q.options.map { it.lowercase() }.toSet().size)
                assertTrue("${q.question}: pregunta delimitada a la lectura", q.question.contains("lectura"))
                assertFalse("${q.question}: explicación", q.explanation.isNullOrBlank())
                assertFalse("${q.question}: pista", q.hint.isNullOrBlank())
                (q.options + q.question + q.explanation!!).forEach { text ->
                    assertFalse("frase truncada: $text", text.contains("…") || text.contains("...") || text.contains("___"))
                }
                // La respuesta correcta se apoya en el texto original de la lección.
                assertTrue("${q.question}: respuesta en la lectura", ShortLesson.grounded(q, lesson.originalText!!))
            }
            // La posición correcta varía (no siempre la A).
            assertTrue("${lesson.title}: posiciones variadas", lesson.questions.map { it.correctOptionIndex }.toSet().size > 1)
        }
    }

    @Test fun yearAndQuantityQuestionsCompareLikeWithLike() {
        val years = lessons.flatMap { it.questions }.filter { it.question.contains("qué año") }
        assertTrue(years.isNotEmpty())
        years.forEach { q -> assertTrue(q.options.all { Regex("\\d{4}").containsMatchIn(it) }) }
        val amounts = lessons.flatMap { it.questions }.filter { it.question.startsWith("¿Cuántos") }
        assertTrue(amounts.isNotEmpty())
        amounts.forEach { q -> assertTrue(q.options.all { it.startsWith("Más de") }) }
    }

    @Test fun offlineEnchantedForestIsAChildrensStoryNotABookReview() {
        val story = DemoData.offlineLesson("cuentos_aventuras", NlpLevel.NIVEL_1, "Bosque Encantado")
        assertNotNull(story)
        story!!
        assertEquals("cuentos_aventuras", story.category)
        val text = story.originalText!!.lowercase()
        assertTrue("ficción identificada", text.contains("cuento original de ficción"))
        listOf("hazel", "wikipedia", "reseña", "novela").forEach { assertFalse("sin «$it»", text.contains(it)) }
        assertEquals(5, story.levelPreviews.size)
        // Un tema sin relato propio cae en la lectura de su categoría.
        assertEquals("Real Madrid", DemoData.offlineLesson("deportes", NlpLevel.NIVEL_3, "Natación")!!.title)
    }
}
