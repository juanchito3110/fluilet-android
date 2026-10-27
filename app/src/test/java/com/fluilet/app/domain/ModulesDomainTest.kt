package com.fluilet.app.domain

import com.fluilet.app.domain.gamification.DailyMission
import com.fluilet.app.domain.gamification.DailyMissions
import com.fluilet.app.domain.gamification.FluiBotInventory
import com.fluilet.app.domain.gamification.LessonFacts
import com.fluilet.app.domain.gamification.PurchaseResult
import com.fluilet.app.domain.gamification.Scenario
import com.fluilet.app.domain.gamification.SessionContribution
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.TopicSuggestions
import com.fluilet.app.domain.text.LocalGlossary
import com.fluilet.app.presentation.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Los 4 apartados, los 5 escenarios, las misiones diarias, el glosario, los datos curiosos y las sugerencias. */
class ModulesDomainTest {

    // --- Navegación de 4 apartados ----------------------------------------------------------

    @Test fun thereAreFourIndependentSectionsAndLecturasIsTheDefault() {
        assertEquals(listOf("LECTURAS", "FLUIBOT", "COLECCION", "ESTADISTICAS"), HomeTab.entries.map { it.name })
        assertEquals(HomeTab.LECTURAS, HomeTab.DEFAULT)
        assertEquals(listOf("Lecturas", "Mi FluiBot", "Colección", "Estadísticas"), HomeTab.entries.map { it.label })
        assertEquals("Colección y Misiones", HomeTab.COLECCION.title)
        assertEquals(4, HomeTab.entries.map { it.emoji }.toSet().size)
    }

    // --- Escenarios ---------------------------------------------------------------------------

    @Test fun fiveScenariosWithTheExpectedCosts() {
        assertEquals(5, Scenario.entries.size)
        assertEquals(listOf(0, 20, 30, 45, 60), Scenario.entries.map { it.cost })
        assertEquals(
            listOf("Laboratorio Cian", "Estación Espacial Estelar", "Océano Submarino", "Estadio Campeón", "Bosque Cyber-Neón"),
            Scenario.entries.map { it.displayName }
        )
        assertTrue(Scenario.DEFAULT.isFree)
        assertEquals(Scenario.LABORATORIO_CIAN, Scenario.DEFAULT)
        assertEquals(1, Scenario.entries.count { it.isFree })
        assertEquals(Scenario.OCEANO_SUBMARINO, Scenario.fromId("oceano_submarino"))
        assertNull(Scenario.fromId("no_existe"))
    }

    @Test fun theFreeScenarioIsAlwaysOwnedAndEquippedByDefault() {
        val inv = FluiBotInventory.EMPTY
        assertTrue(inv.ownsScenario(Scenario.LABORATORIO_CIAN))
        assertEquals(Scenario.LABORATORIO_CIAN, inv.scenario)
        assertFalse(inv.ownsScenario(Scenario.ESTACION_ESPACIAL))
    }

    @Test fun buyingAScenarioSpendsGemsAndEquipsIt() {
        val result = FluiBotInventory.EMPTY.purchaseScenario(Scenario.ESTADIO_CAMPEON, totalGems = 100) as PurchaseResult.Bought
        assertEquals(45, result.inventory.spentGems)
        assertEquals(Scenario.ESTADIO_CAMPEON, result.inventory.scenario)
        assertEquals(55, result.inventory.balance(100))
    }

    @Test fun scenarioPurchaseRejectsMissingGemsAndDuplicates() {
        val short = FluiBotInventory.EMPTY.purchaseScenario(Scenario.BOSQUE_CYBER_NEON, totalGems = 50)
        assertEquals(PurchaseResult.NotEnoughGems(10), short)
        assertEquals(PurchaseResult.AlreadyOwned, FluiBotInventory.EMPTY.purchaseScenario(Scenario.LABORATORIO_CIAN, 100))
        val bought = (FluiBotInventory.EMPTY.purchaseScenario(Scenario.OCEANO_SUBMARINO, 100) as PurchaseResult.Bought).inventory
        assertEquals(PurchaseResult.AlreadyOwned, bought.purchaseScenario(Scenario.OCEANO_SUBMARINO, 100))
    }

    @Test fun onlyOneScenarioIsEquippedAndUnownedOnesCannotBeEquipped() {
        assertEquals(FluiBotInventory.EMPTY, FluiBotInventory.EMPTY.equipScenario(Scenario.ESTACION_ESPACIAL))
        val bought = (FluiBotInventory.EMPTY.purchaseScenario(Scenario.ESTACION_ESPACIAL, 100) as PurchaseResult.Bought).inventory
        val back = bought.equipScenario(Scenario.LABORATORIO_CIAN)
        assertEquals(Scenario.LABORATORIO_CIAN, back.scenario)
        assertEquals(Scenario.ESTACION_ESPACIAL, back.equipScenario(Scenario.ESTACION_ESPACIAL).scenario)
    }

    @Test fun corruptedScenarioDataFallsBackToTheFreeOne() {
        val corrupt = FluiBotInventory(ownedScenarios = setOf("fantasma"), equippedScenario = "estadio_campeon").sanitized()
        assertEquals(Scenario.LABORATORIO_CIAN, corrupt.scenario)
        assertTrue(corrupt.ownedScenarios.isEmpty())
    }

    // --- Misiones diarias ---------------------------------------------------------------------

    private val today = LocalDate.of(2026, 10, 25)

    private fun lesson(
        at: String, completed: Boolean = true, seconds: Long = 120, correct: Int? = 2, score: Int? = 100
    ) = LessonProgress(
        lessonNumber = 1, readingId = "r", recordedAt = Instant.parse(at), category = "deportes", level = 2,
        completed = completed, completionPercent = if (completed) 100 else 40, comprehensionScore = score,
        durationSeconds = seconds, originalScore = 30.0, adaptedScore = 70.0, questionsCorrect = correct
    )

    @Test fun threeMissionsWithTheirRewardsStartAtZero() {
        val missions = DailyMissions.compute(emptyList(), today, ZoneOffset.UTC)
        assertEquals(listOf("leer_leccion", "respuestas_correctas", "practicar_tiempo"), missions.map { it.id })
        assertEquals(listOf(25, 30, 40), missions.map { it.rewardGems })
        assertEquals(listOf(1, 2, 300), missions.map { it.target })
        assertTrue(missions.all { it.current == 0 && !it.completed && it.fraction == 0f })
    }

    @Test fun onlyTodaysLessonsCount() {
        val missions = DailyMissions.compute(
            listOf(lesson("2026-10-24T23:59:00Z", seconds = 400), lesson("2026-10-25T09:00:00Z", seconds = 100, correct = 1)),
            today, ZoneOffset.UTC
        ).associateBy { it.id }
        assertTrue(missions.getValue("leer_leccion").completed)
        assertEquals(1, missions.getValue("respuestas_correctas").current)
        assertFalse(missions.getValue("respuestas_correctas").completed)
        assertEquals(100, missions.getValue("practicar_tiempo").current)
    }

    @Test fun missionsAreCappedAndTheSessionAddsToTheDay() {
        val missions = DailyMissions.compute(
            listOf(lesson("2026-10-25T09:00:00Z", seconds = 250, correct = 2)),
            today, ZoneOffset.UTC, session = SessionContribution(completed = true, correctAnswers = 2, seconds = 200)
        ).associateBy { it.id }
        assertEquals(1, missions.getValue("leer_leccion").current) // 2 lecciones, tope 1
        assertEquals(2, missions.getValue("respuestas_correctas").current) // 4 aciertos, tope 2
        assertEquals(300, missions.getValue("practicar_tiempo").current) // 450 s, tope 300
        assertTrue(missions.values.all { it.completed && it.fraction == 1f })
    }

    @Test fun claimingPaysOncePerDayAndRequiresACompletedMission() {
        val done = DailyMission("leer_leccion", "t", "d", 1, 1, 25)
        val pending = DailyMission("practicar_tiempo", "t", "d", 10, 300, 40)
        val key = DailyMissions.claimKey(today, done.id)
        assertEquals("2026-10-25:leer_leccion", key)

        val claimed = FluiBotInventory.EMPTY.claimMission(done, key)
        assertEquals(25, claimed.bonusGems)
        assertEquals(125, claimed.balance(100))
        assertEquals(claimed, claimed.claimMission(done, key)) // no se paga dos veces
        assertEquals(FluiBotInventory.EMPTY, FluiBotInventory.EMPTY.claimMission(pending, "x"))
        // Al día siguiente la misma misión vuelve a poder reclamarse.
        assertEquals(50, claimed.claimMission(done, DailyMissions.claimKey(today.plusDays(1), done.id)).bonusGems)
    }

    // --- Datos curiosos, glosario y sugerencias ---------------------------------------------

    @Test fun funFactPrefersTheServerThenTheTopicThenTheCategory() {
        assertEquals("Dato del servidor", LessonFacts.forLesson("Dato del servidor", "Volcanes", "deportes"))
        assertEquals(LessonFacts.forTopic("volcanes"), LessonFacts.forLesson("  ", "VOLCANES", "deportes"))
        assertEquals(LessonFacts.forCategory("deportes"), LessonFacts.forLesson(null, "Tema raro", "deportes"))
        assertNull(LessonFacts.forLesson(null, "Tema raro", null))
    }

    @Test fun topicFactsIgnoreCaseAndAccents() {
        assertNotNull(LessonFacts.forTopic("Biología Marina"))
        assertEquals(LessonFacts.forTopic("biologia marina"), LessonFacts.forTopic("BIOLOGÍA MARINA"))
        LessonCategory.entries.forEach { assertNotNull(LessonFacts.forCategory(it.code)) }
    }

    @Test fun glossaryFindsKeyWordsInOrderWithoutRepeatsIgnoringAccentsAndPlurals() {
        val found = LocalGlossary.find("Un volcan lanzó lava. Los planetas ven el Volcán y la lava desde el planeta.")
        assertEquals(listOf("volcán", "lava", "planeta"), found.map { it.word })
        assertTrue(found.all { it.definition.isNotBlank() && it.example.isNotBlank() })
    }

    @Test fun glossaryRespectsTheLimitAndHandlesTextWithoutKnownWords() {
        val text = "planeta estrella galaxia órbita gravedad telescopio volcán lava"
        assertEquals(3, LocalGlossary.find(text, max = 3).size)
        assertEquals(6, LocalGlossary.find(text).size)
        assertTrue(LocalGlossary.find("").isEmpty())
        assertTrue(LocalGlossary.find("Hola, esto no tiene palabras difíciles.").isEmpty())
    }

    @Test fun everyCategoryHasFourSuggestionsIncludingTheRequestedOnes() {
        LessonCategory.entries.forEach { c ->
            val list = TopicSuggestions.forCategory(c)
            assertEquals(c.name, 4, list.size)
            assertEquals(4, list.toSet().size)
            assertTrue(list.all { it.isNotBlank() })
        }
        assertEquals(listOf("Real Madrid", "Juegos Olímpicos", "Fútbol", "Natación"), TopicSuggestions.forCategory(LessonCategory.DEPORTES))
        assertEquals(
            listOf("Biología Marina", "Tiburón Blanco", "Dinosaurios", "Selva Amazónica"),
            TopicSuggestions.forCategory(LessonCategory.ANIMALES_NATURALEZA)
        )
        assertTrue(TopicSuggestions.forCategory(LessonCategory.CIENCIA_ESPACIO).containsAll(listOf("Agujeros Negros", "Sistema Solar", "Volcanes")))
    }

    @Test fun revokingALocalClaimTakesBackItsGemsOnlyOnce() {
        val mission = DailyMission("m", "t", "d", 1, 1, 25, fromServer = true)
        val key = DailyMissions.claimKey(today, mission.id)
        val claimed = FluiBotInventory.EMPTY.claimMission(mission, key)
        val revoked = claimed.revokeClaim(mission, key)
        assertEquals(0, revoked.bonusGems)
        assertFalse(key in revoked.claimedMissions)
        assertEquals(revoked, revoked.revokeClaim(mission, key))
        assertEquals(FluiBotInventory.EMPTY, FluiBotInventory.EMPTY.revokeClaim(mission, key))
    }
}
