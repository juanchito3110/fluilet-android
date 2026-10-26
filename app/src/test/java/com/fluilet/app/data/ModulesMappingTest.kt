package com.fluilet.app.data

import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.EducationalProgressDto
import com.fluilet.app.data.repository.FluiBotInventoryStore
import com.fluilet.app.domain.gamification.FluiBotInventory
import com.fluilet.app.domain.gamification.Scenario
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.testutil.FakeSecureStore
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Campos nuevos del backend (opcionales): `fun_fact`, `key_vocabulary` y `daily_missions`, con y sin ellos. */
class ModulesMappingTest {

    private val gson = Gson()

    private fun adaptation(extra: String = "", text: String = "Hola mundo.") = gson.fromJson(
        """{"document":{"id":"d1"},"adapted_text":"$text","chunks":[],"category":"deportes"$extra}""",
        AdaptationReadDto::class.java
    ).toDomain("Cuento", NlpLevel.NIVEL_2)

    @Test fun funFactAndKeyVocabularyAreMapped() {
        val result = adaptation(
            ""","fun_fact":"Los delfines duermen con medio cerebro.",
               "key_vocabulary":[
                 {"word":"delfín","definition":"Un mamífero marino muy listo.","example":"El delfín saltó."},
                 {"term":"ola","meaning":"Agua que sube y baja.","example":"Una ola grande."}]"""
        )
        assertEquals("Los delfines duermen con medio cerebro.", result.funFact)
        assertEquals("Los delfines duermen con medio cerebro.", result.effectiveFunFact)
        assertEquals(listOf("delfín", "ola"), result.keyVocabulary.map { it.word })
        assertEquals("Agua que sube y baja.", result.keyVocabulary[1].definition)
        assertEquals(result.keyVocabulary, result.effectiveVocabulary)
    }

    @Test fun incompleteVocabularyEntriesAreDropped() {
        val result = adaptation(""","key_vocabulary":[{"word":"","definition":"x"},{"word":"sol"},{"word":"luna","definition":"Satélite de la Tierra."}]""")
        assertEquals(listOf("luna"), result.keyVocabulary.map { it.word })
    }

    @Test fun withoutTheNewFieldsTheLessonFallsBackToLocalContent() {
        val result = adaptation(text = "El volcán lanzó lava y el planeta tembló.")
        assertNull(result.funFact)
        assertTrue(result.keyVocabulary.isEmpty())
        // Dato curioso por categoría (deportes) y glosario local con las palabras del texto.
        assertEquals(com.fluilet.app.domain.gamification.LessonFacts.forCategory("deportes"), result.effectiveFunFact)
    }

    @Test fun localGlossaryUsesTheAdaptedChunks() {
        val result = com.fluilet.app.testutil.sampleAdaptation().copy(
            chunks = listOf(com.fluilet.app.domain.model.TextChunk(0, "El volcán echa lava."), com.fluilet.app.domain.model.TextChunk(1, "Un planeta gira."))
        )
        assertEquals(listOf("volcán", "lava", "planeta"), result.effectiveVocabulary.map { it.word })
    }

    private fun progress(extra: String) = gson.fromJson(
        """{"total_lessons":1,"completed_lessons":1,"incomplete_lessons":0,"completion_rate_percent":100.0,
            "total_reading_seconds":60,"average_comprehension_score":100.0,"current_recommended_level":2,
            "level_progression":[],"badges":[]$extra}""",
        EducationalProgressDto::class.java
    ).toDomain()

    @Test fun dailyMissionsFromTheServerAreMapped() {
        val p = progress(
            ""","daily_missions":[
                {"id":"leer","title":"Lee hoy","description":"d","progress":1,"target":1,"reward_gems":25},
                {"id":"tiempo","name":"Practica","current":120,"target":300,"reward":40},
                {"title":"sin id","target":3}]"""
        )
        val missions = p.serverMissions!!
        assertEquals(listOf("leer", "tiempo"), missions.map { it.id })
        assertTrue(missions[0].completed)
        assertEquals(120, missions[1].current)
        assertEquals(40, missions[1].rewardGems)
        assertEquals("Practica", missions[1].title)
    }

    @Test fun withoutDailyMissionsTheyAreComputedLocally() {
        assertNull(progress("").serverMissions)
    }

    @Test fun scenarioBonusAndClaimedMissionsSurviveARestart() {
        val secure = FakeSecureStore()
        val inventory = FluiBotInventory(
            owned = setOf("mini_dron"), equipped = setOf("mini_dron"), spentGems = 95,
            ownedScenarios = setOf("estadio_campeon"), equippedScenario = "estadio_campeon",
            bonusGems = 55, claimedMissions = setOf("2026-10-25:leer_leccion", "2026-10-25:practicar_tiempo")
        )
        FluiBotInventoryStore(secure).save("child-1", inventory)

        val loaded = FluiBotInventoryStore(secure).load("child-1")

        assertEquals(inventory, loaded)
        assertEquals(Scenario.ESTADIO_CAMPEON, loaded.scenario)
        assertEquals(FluiBotInventory.EMPTY, FluiBotInventoryStore(secure).load("child-2"))
    }
}
