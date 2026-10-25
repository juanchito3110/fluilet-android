package com.fluilet.app.domain

import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.ComprehensionResult
import com.fluilet.app.domain.model.ComprehensionScoring
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingRecord
import com.fluilet.app.domain.progress.EducationalProgressCalculator
import com.fluilet.app.domain.quiz.ComprehensionQuiz
import com.fluilet.app.presentation.common.formatExactPercent
import com.fluilet.app.presentation.common.formatPercent
import com.fluilet.app.presentation.common.formatReadingTime
import com.fluilet.app.testutil.sampleQuestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ComprehensionQuizTest {

    private val quiz = ComprehensionQuiz(sampleQuestions()) // q1 correcta = 0, q2 correcta = 1

    @Test fun startsOnTheFirstQuestionWithNothingAnswered() {
        assertEquals("q1", quiz.current!!.id)
        assertEquals(0, quiz.answered)
        assertFalse(quiz.isComplete)
        assertFalse(quiz.canAdvance)
        assertNull(quiz.result.score)
    }

    @Test fun correctAnswerGivesImmediateFeedback() {
        val q = quiz.answer(0)
        assertTrue(q.currentAnswer!!.isCorrect)
        assertEquals(1, q.correct)
        assertTrue(q.canAdvance)
    }

    @Test fun wrongAnswerIsRecordedAsIncorrect() {
        val q = quiz.answer(2)
        assertFalse(q.currentAnswer!!.isCorrect)
        assertEquals(1, q.answered)
        assertEquals(0, q.correct)
    }

    @Test fun aQuestionCanOnlyBeAnsweredOnce() {
        val q = quiz.answer(2).answer(0)
        assertEquals(2, q.currentAnswer!!.selectedIndex)
        assertEquals(0, q.correct)
    }

    @Test fun invalidOptionsAreIgnored() {
        assertSame(quiz, quiz.answer(7))
        assertSame(quiz, quiz.answer(-1))
    }

    @Test fun cannotAdvanceBeforeAnswering() {
        assertSame(quiz, quiz.next())
        assertEquals(1, quiz.answer(0).next().viewIndex)
    }

    @Test fun scoresAre0_50_or_100() {
        fun finish(first: Int, second: Int) = quiz.answer(first).next().answer(second)
        val perfect = finish(0, 1)
        assertTrue(perfect.isComplete)
        assertEquals(ComprehensionResult(2, 2), perfect.result)
        assertEquals(100, perfect.result.score)
        assertEquals(50, finish(0, 0).result.score)
        assertEquals(50, finish(2, 1).result.score)
        assertEquals(0, finish(1, 0).result.score)
    }

    @Test fun lastQuestionDoesNotAdvanceAndIsComplete() {
        val q = quiz.answer(0).next().answer(1)
        assertTrue(q.isLastQuestion)
        assertFalse(q.canAdvance)
        assertSame(q, q.next())
    }

    @Test fun emptyQuizIsNeverComplete() {
        val empty = ComprehensionQuiz(emptyList())
        assertFalse(empty.isComplete)
        assertNull(empty.current)
        assertSame(empty, empty.answer(0))
    }

    @Test fun questionsValidateTheirShape() {
        assertThrows(IllegalArgumentException::class.java) { ComprehensionQuestion("q", "x", listOf("a"), 0) }
        assertThrows(IllegalArgumentException::class.java) { ComprehensionQuestion("q", "x", listOf("a", "b"), 2) }
    }
}

class ComprehensionScoringTest {
    @Test fun matchesTheBackendFormulaIncludingHalfUpRounding() {
        assertEquals(100, ComprehensionScoring.score(2, 2))
        assertEquals(50, ComprehensionScoring.score(2, 1))
        assertEquals(0, ComprehensionScoring.score(2, 0))
        assertEquals(67, ComprehensionScoring.score(3, 2))
        assertEquals(33, ComprehensionScoring.score(3, 1))
        assertEquals(13, ComprehensionScoring.score(8, 1)) // 12.5 -> 13 (mitades hacia arriba)
        assertNull(ComprehensionScoring.score(0, 0))
    }

    @Test fun correctCannotExceedAnswered() {
        assertThrows(IllegalArgumentException::class.java) { ComprehensionScoring.score(1, 2) }
    }
}

class LessonModelsTest {
    @Test fun sixCategoriesWithTheBackendCodesAndSpanishNames() {
        assertEquals(
            listOf("ciencia_espacio", "animales_naturaleza", "historia_misterios", "deportes", "tecnologia", "cuentos_aventuras"),
            LessonCategory.entries.map { it.code }
        )
        assertEquals("Ciencia y Espacio", LessonCategory.CIENCIA_ESPACIO.displayName)
        assertEquals("Animales y Naturaleza", LessonCategory.ANIMALES_NATURALEZA.displayName)
        assertEquals("Historia y Misterios", LessonCategory.HISTORIA_MISTERIOS.displayName)
        assertEquals("Deportes", LessonCategory.DEPORTES.displayName)
        assertEquals("Tecnología", LessonCategory.TECNOLOGIA.displayName)
        assertEquals("Cuentos y Aventuras", LessonCategory.CUENTOS_AVENTURAS.displayName)
    }

    @Test fun categoryLookupAndDisplayNames() {
        assertEquals(LessonCategory.DEPORTES, LessonCategory.fromCode(" Deportes "))
        assertNull(LessonCategory.fromCode("otra"))
        assertEquals("Ciencia y Espacio", LessonCategory.displayNameFor("ciencia_espacio"))
        assertEquals("Lectura libre", LessonCategory.displayNameFor("lectura_libre"))
        assertEquals("Lectura libre", LessonCategory.displayNameFor(null))
        assertEquals("Otra cosa", LessonCategory.displayNameFor("otra_cosa"))
    }

    @Test fun topicIsNormalizedAndBounded() {
        fun topic(raw: String?) = LessonRequest(LessonCategory.DEPORTES, raw, NlpLevel.NIVEL_2).cleanTopic
        assertEquals("Agujeros negros", topic("  Agujeros \n  negros  "))
        assertEquals("Dinosaurios", topic("Dinosaurios"))
        assertNull(topic("   "))
        assertNull(topic(null))
        val hostile = topic("<script>alert(1)</script>")!!
        assertFalse(hostile.contains('<') || hostile.contains('>') || hostile.contains('/'))
        assertEquals(LessonRequest.MAX_TOPIC_LENGTH, topic("a".repeat(500))!!.length)
    }
}

class EducationalProgressCalculatorTest {

    private fun record(
        n: Int, completed: Boolean = true, percent: Double = if (completed) 100.0 else 40.0, level: Int = 2,
        category: String = "ciencia_espacio", score: Int? = null, answered: Int = 0, seconds: Long = 120,
        day: Int = n
    ) = ReadingRecord(
        id = "r$n", startedAt = Instant.parse("2026-10-%02dT10:00:00Z".format(day)), category = category, level = level,
        completed = completed, completionPercent = percent, readingSeconds = seconds, comprehensionScore = score,
        questionsAnswered = answered, originalScore = 30.0, adaptedScore = 70.0
    )

    private fun unlocked(p: EducationalProgress) = p.badges.filter { it.unlocked }.map { it.code }

    @Test fun emptyHistoryHasZeroesBaselineLevelAndFiveLockedBadges() {
        val p = EducationalProgressCalculator.summarize(emptyList(), baselineLevel = 3)
        assertEquals(0, p.totalLessons)
        assertEquals(0.0, p.completionRatePercent, 0.0)
        assertEquals(0.0, p.averageComprehension, 0.0)
        assertEquals(3, p.recommendedLevel)
        assertEquals(5, p.badges.size)
        assertTrue(unlocked(p).isEmpty())
        assertEquals(p.copy(), EducationalProgress.empty(3).copy(badges = p.badges, lessons = p.lessons))
    }

    @Test fun summaryCardsAddUpCompletedIncompleteTimeAndAverage() {
        val p = EducationalProgressCalculator.summarize(
            listOf(record(1, score = 100, answered = 2, seconds = 300), record(2, completed = false, seconds = 100), record(3, score = 50, answered = 2, seconds = 200))
        )
        assertEquals(3, p.totalLessons)
        assertEquals(2, p.completedLessons)
        assertEquals(1, p.incompleteLessons)
        assertEquals(66.666, p.completionRatePercent, 0.01)
        assertEquals(600, p.totalReadingSeconds)
        assertEquals(75.0, p.averageComprehension, 0.0) // los lecciones sin reto no entran en el promedio
    }

    @Test fun firstLessonAndBrightMindBadges() {
        val p = EducationalProgressCalculator.summarize(listOf(record(1, score = 100, answered = 2)))
        assertEquals(listOf("primera_leccion", "mente_brillante"), unlocked(p))
    }

    @Test fun habitExplorerAndLevelUpBadgesNeedTheirConditions() {
        val oneCategory = EducationalProgressCalculator.summarize(listOf(record(1), record(2), record(3)))
        assertEquals(listOf("primera_leccion", "habito_en_marcha"), unlocked(oneCategory))

        val full = EducationalProgressCalculator.summarize(
            listOf(record(1, category = "deportes", level = 2, score = 100, answered = 2), record(2, category = "tecnologia", level = 2), record(3, category = "deportes", level = 3))
        )
        assertEquals(BadgeCodes, unlocked(full))
    }

    @Test fun explorerCountsDistinctCategoriesWithAnyPracticeEvenIfIncomplete() {
        val p = EducationalProgressCalculator.summarize(
            listOf(record(1, completed = false, category = "deportes", percent = 10.0), record(2, completed = false, category = "tecnologia", percent = 0.0, seconds = 0))
        )
        assertFalse("la categoría sin práctica no cuenta", "explorador_curioso" in unlocked(p))
        val q = EducationalProgressCalculator.summarize(
            listOf(record(1, completed = false, category = "deportes", percent = 10.0), record(2, completed = false, category = "tecnologia", percent = 5.0))
        )
        assertTrue("explorador_curioso" in unlocked(q))
    }

    @Test fun recommendedLevelRisesOnlyWhenTheRecentLessonsWentWell() {
        val good = EducationalProgressCalculator.summarize(listOf(record(1, level = 2, score = 100, answered = 2)))
        assertEquals(3, good.recommendedLevel)

        val struggling = EducationalProgressCalculator.summarize(
            listOf(record(1, level = 2, percent = 80.0, score = 0, answered = 2))
        )
        assertEquals("sin comprensión ≥ 50 ni avance al 100 % no sube", 2, struggling.recommendedLevel)

        val capped = EducationalProgressCalculator.summarize(listOf(record(1, level = 5, score = 100, answered = 2)))
        assertEquals(5, capped.recommendedLevel)
    }

    @Test fun lessonsAreOrderedByDateAndNumbered() {
        val p = EducationalProgressCalculator.summarize(listOf(record(2, day = 9), record(1, day = 3)))
        assertEquals(listOf("r1", "r2"), p.lessons.map { it.readingId })
        assertEquals(listOf(1, 2), p.lessons.map { it.lessonNumber })
        assertEquals(40.0, p.lessons[0].readabilityJump!!, 0.001)
    }

    private companion object {
        val BadgeCodes = listOf("primera_leccion", "mente_brillante", "habito_en_marcha", "explorador_curioso", "subiendo_nivel")
    }
}

class FormattersTest {
    @Test fun readingTime() {
        assertEquals("0 s", formatReadingTime(-5))
        assertEquals("45 s", formatReadingTime(45))
        assertEquals("12 min", formatReadingTime(12 * 60 + 30))
        assertEquals("1 h 05 min", formatReadingTime(3900))
        assertEquals("2 h 00 min", formatReadingTime(7200))
    }

    @Test fun exactPercentForTheDonutCenter() {
        assertEquals("100%", formatExactPercent(100.0))
        assertEquals("0%", formatExactPercent(0.0))
        assertEquals("66.7%", formatExactPercent(66.666))
        assertEquals("50%", formatExactPercent(50.04))
        assertEquals("33.3%", formatExactPercent(33.33))
    }

    @Test fun percent() {
        assertEquals("67 %", formatPercent(66.67))
        assertEquals("0 %", formatPercent(0.0))
    }
}
