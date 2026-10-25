package com.fluilet.app.domain

import com.fluilet.app.domain.gamification.CardCatalog
import com.fluilet.app.domain.gamification.CardChange
import com.fluilet.app.domain.gamification.CardRarity
import com.fluilet.app.domain.gamification.CollectibleCard
import com.fluilet.app.domain.gamification.FluiBotProgress
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.gamification.Gamification
import com.fluilet.app.domain.gamification.GamificationRules
import com.fluilet.app.domain.gamification.PedagogicalSummary
import com.fluilet.app.domain.gamification.ServerGamification
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GamificationTest {

    private fun lesson(
        n: Int, category: String = "deportes", completed: Boolean = true, score: Int? = null,
        correct: Int? = null, day: String = "2026-10-0$n", level: Int = 2, seconds: Long = 120, percent: Int = if (completed) 100 else 40
    ) = LessonProgress(n, "r$n", Instant.parse("${day}T10:00:00Z"), category, level, completed, percent, score, seconds, 30.0, 70.0, correct)

    // --- Gemas y XP -------------------------------------------------------------------------------

    @Test fun rewardRulesMatchTheBackend() {
        assertEquals(30, GamificationRules.WELCOME_GEMS)
        assertEquals(100, GamificationRules.gemsForLesson(completed = true, correct = 2)) // +50 y 2 x +25
        assertEquals(25, GamificationRules.gemsForLesson(completed = false, correct = 1))
        assertEquals(200, GamificationRules.xpForLesson(completed = true, correct = 2)) // 100 + 2 x 50
        assertEquals(50, GamificationRules.xpForLesson(completed = false, correct = 1))
        assertEquals(0, GamificationRules.gemsForLesson(false, 0))
    }

    @Test fun totalGemsIncludeTheWelcomeBonusAndAreDerivedFromLessons() {
        assertEquals(30, Gamification.totalGems(emptyList()))
        val lessons = listOf(lesson(1, score = 100), lesson(2, completed = false))
        assertEquals(130, Gamification.totalGems(lessons)) // 30 + 50 + 2 x 25
        assertEquals(200, Gamification.totalXp(lessons))
    }

    @Test fun correctAnswersUseTheRealCountAndOtherwiseEstimateFromTheScore() {
        assertEquals(2, GamificationRules.correctAnswers(lesson(1, score = 100)))
        assertEquals(1, GamificationRules.correctAnswers(lesson(1, score = 50)))
        assertEquals(0, GamificationRules.correctAnswers(lesson(1, score = null)))
        assertEquals(1, GamificationRules.correctAnswers(lesson(1, score = 100, correct = 1)))
    }

    // --- FluiBot: etapas ---------------------------------------------------------------------------

    @Test fun fourStagesWithTheBackendThresholds() {
        assertEquals(listOf(0, 150, 350, 700), FluiBotStage.entries.map { it.minXp })
        assertEquals(listOf(150, 350, 700, 1200), FluiBotStage.entries.map { it.upperXp })
        assertEquals(
            listOf("Núcleo de Energía", "Bot Explorador", "Guardián Cyber-Lector", "Sabio Cuántico"),
            FluiBotStage.entries.map { it.displayName }
        )
        assertEquals("Explorador Inicial", FluiBotStage.NUCLEO.rankTitle)
    }

    @Test fun stageForXpChangesExactlyAtTheThresholds() {
        assertEquals(FluiBotStage.NUCLEO, FluiBotStage.forXp(0))
        assertEquals(FluiBotStage.NUCLEO, FluiBotStage.forXp(149))
        assertEquals(FluiBotStage.EXPLORADOR, FluiBotStage.forXp(150))
        assertEquals(FluiBotStage.EXPLORADOR, FluiBotStage.forXp(349))
        assertEquals(FluiBotStage.GUARDIAN, FluiBotStage.forXp(350))
        assertEquals(FluiBotStage.GUARDIAN, FluiBotStage.forXp(699))
        assertEquals(FluiBotStage.SABIO, FluiBotStage.forXp(700))
        assertEquals(FluiBotStage.SABIO, FluiBotStage.forXp(99_999))
    }

    @Test fun xpBarShowsCurrentOverNextStageXp() {
        val p = FluiBotProgress.of(75)
        assertEquals(FluiBotStage.NUCLEO, p.stage)
        assertEquals(150, p.nextStageXp)
        assertEquals(0.5f, p.fraction, 0.0001f)
        assertEquals("75 / 150 XP", p.label)

        val wise = FluiBotProgress.of(925)
        assertEquals(FluiBotStage.SABIO, wise.stage)
        assertEquals(0.45f, wise.fraction, 0.0001f)
        assertEquals(1f, FluiBotProgress.of(5000).fraction, 0f)
        assertEquals(0f, FluiBotProgress.of(-10).fraction, 0f)
    }

    // --- Cartas --------------------------------------------------------------------------------------

    @Test fun cardRarityByCompletedLessons() {
        assertNull(CardRarity.forLessons(0))
        assertEquals(CardRarity.BRONCE, CardRarity.forLessons(1))
        assertEquals(CardRarity.PLATA, CardRarity.forLessons(2))
        assertEquals(CardRarity.ORO, CardRarity.forLessons(3))
        assertEquals(CardRarity.ORO, CardRarity.forLessons(9))
        assertNull(CardRarity.fromWire("Bloqueada"))
        assertEquals(CardRarity.PLATA, CardRarity.fromWire("Plata"))
        assertEquals(CardRarity.ORO, CardRarity.fromWire("gold"))
    }

    @Test fun sixCardsOnePerCategoryWithFunFactsAndLockedUntilCompleted() {
        val cards = Gamification.cardsFor(listOf(lesson(1, "deportes"), lesson(2, "deportes"), lesson(3, "tecnologia", completed = false)))
        assertEquals(LessonCategory.entries.map { it.code }, cards.map { it.categoryCode })
        val sports = cards.first { it.categoryCode == "deportes" }
        assertEquals(CardRarity.PLATA, sports.rarity)
        assertEquals(2, sports.lessonsCompleted)
        assertEquals("Campeón del Juego Limpio", sports.title)
        assertTrue(sports.funFact.isNotBlank())
        assertFalse("una lección incompleta no desbloquea la carta", cards.first { it.categoryCode == "tecnologia" }.unlocked)
        assertTrue(cards.all { it.funFact == CardCatalog.funFactFor(LessonCategory.fromCode(it.categoryCode)!!) })
    }

    // --- Botín de la sesión ----------------------------------------------------------------------------

    @Test fun lootForACompletedReadingWithBothAnswersRight() {
        val loot = Gamification.lootFor(completed = true, correctAnswers = 2, categoryCode = "deportes", before = null)
        assertEquals(50, loot.gemsForCompleting)
        assertEquals(50, loot.gemsForAnswers)
        assertEquals(100, loot.gems)
        assertEquals(200, loot.xp)
        assertEquals(CardChange.UNLOCKED, loot.cardChange)
        assertEquals(CardRarity.BRONCE, loot.cardAfter!!.rarity)
    }

    @Test fun lootWithoutCompletionOnlyPaysForCorrectAnswers() {
        val loot = Gamification.lootFor(completed = false, correctAnswers = 1, categoryCode = "deportes", before = null)
        assertEquals(0, loot.gemsForCompleting)
        assertEquals(25, loot.gems)
        assertEquals(50, loot.xp)
        assertEquals(CardChange.NONE, loot.cardChange)
    }

    @Test fun cardUpgradesWhenTheCategoryReachesTheNextRarity() {
        fun before(done: Int) = EducationalProgressWith(List(done) { lesson(it + 1, "deportes") })
        assertEquals(CardChange.UPGRADED, Gamification.lootFor(true, 0, "deportes", before(1)).cardChange) // Bronce -> Plata
        assertEquals(CardChange.UPGRADED, Gamification.lootFor(true, 0, "deportes", before(2)).cardChange) // Plata -> Oro
        assertEquals(CardChange.NONE, Gamification.lootFor(true, 0, "deportes", before(3)).cardChange) // ya es Oro
        assertEquals(CardChange.UNLOCKED, Gamification.lootFor(true, 0, "tecnologia", before(3)).cardChange)
    }

    @Test fun freeReadingAndUnknownCategoriesNeverChangeACard() {
        assertEquals(CardChange.NONE, Gamification.lootFor(true, 2, "lectura_libre", null).cardChange)
        assertEquals(CardChange.NONE, Gamification.lootFor(true, 2, null, null).cardChange)
    }

    private fun EducationalProgressWith(lessons: List<LessonProgress>) =
        Gamification.enrich(EducationalProgress.empty().copy(lessons = lessons), today = LocalDate.of(2026, 10, 9), zone = ZoneOffset.UTC)

    // --- Racha --------------------------------------------------------------------------------------------

    @Test fun streakCountsConsecutiveDaysEndingTodayOrYesterday() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals(3, Gamification.streakDays(listOf(today, today.minusDays(1), today.minusDays(2)), today))
        assertEquals(2, Gamification.streakDays(listOf(today.minusDays(1), today.minusDays(2)), today)) // aún vive hoy
        assertEquals(1, Gamification.streakDays(listOf(today), today))
        assertEquals(0, Gamification.streakDays(listOf(today.minusDays(2)), today)) // se rompió
        assertEquals(0, Gamification.streakDays(emptyList(), today))
        assertEquals(2, Gamification.streakDays(listOf(today, today.minusDays(1), today.minusDays(3)), today)) // un hueco corta
        assertEquals(1, Gamification.streakDays(listOf(today, today, today), today)) // duplicados cuentan una vez
    }

    // --- Enriquecimiento y resumen pedagógico ---------------------------------------------------------------

    @Test fun enrichComputesLocallyWhenTheServerSendsNothing() {
        val p = Gamification.enrich(
            EducationalProgress.empty().copy(lessons = listOf(lesson(1, score = 100), lesson(2, completed = false))),
            today = LocalDate.of(2026, 10, 2), zone = ZoneOffset.UTC
        )
        assertEquals(130, p.totalGems)
        assertEquals(200, p.currentXp)
        assertEquals(2, p.streakDays)
        assertEquals(6, p.cards.size)
        assertFalse(p.gamificationFromServer)
    }

    @Test fun serverValuesWinOverTheLocalCalculation() {
        val server = ServerGamification(
            totalGems = 500, currentXp = 300, streakDays = 9,
            summary = PedagogicalSummary("Del servidor", "Fortaleza", "Foco", "Recomendación"),
            cards = listOf(CollectibleCard("deportes", "Campeón", CardRarity.ORO, 3, "dato"))
        )
        val p = Gamification.enrich(EducationalProgress.empty().copy(lessons = listOf(lesson(1))), server)
        assertEquals(500, p.totalGems)
        assertEquals(300, p.currentXp)
        assertEquals(9, p.streakDays)
        assertEquals("Del servidor", p.pedagogicalSummary.headline)
        assertTrue(p.gamificationFromServer)
        assertEquals("siempre 6 cartas: las que falten se completan localmente", 6, p.cards.size)
        assertEquals(CardRarity.ORO, p.cards.first { it.categoryCode == "deportes" }.rarity)
    }

    @Test fun pedagogicalSummaryForANewLearner() {
        val s = Gamification.pedagogicalSummary(emptyList(), 0.0, 2, 0.0)
        assertEquals("¡Empieza tu primera lección!", s.headline)
        assertTrue(s.recommendation.isNotBlank())
    }

    @Test fun pedagogicalSummaryFindsStrengthAndFocus() {
        val lessons = listOf(
            lesson(1, "deportes", score = 100), lesson(2, "deportes", score = 100),
            lesson(3, "tecnologia", completed = false), lesson(4, "tecnologia", completed = false)
        )
        val s = Gamification.pedagogicalSummary(lessons, completionRate = 50.0, recommendedLevel = 3, average = 100.0)
        assertEquals("Deportes", s.strengthArea)
        assertEquals("Tecnología", s.focusArea)
        assertTrue(s.recommendation.contains("Tecnología"))
        assertEquals("Vas por muy buen camino", s.headline)
    }

    @Test fun pedagogicalHeadlineReflectsCompletionAndComprehension() {
        val done = listOf(lesson(1, score = 100), lesson(2, score = 100))
        assertEquals("¡Excelente ritmo lector!", Gamification.pedagogicalSummary(done, 100.0, 3, 100.0).headline)
        val few = listOf(lesson(1, completed = false))
        assertEquals("Cada lectura cuenta", Gamification.pedagogicalSummary(few, 0.0, 2, 0.0).headline)
    }
}
