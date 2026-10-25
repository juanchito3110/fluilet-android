package com.fluilet.app.data

import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.EducationalProgressDto
import com.fluilet.app.domain.gamification.CardRarity
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.effectiveHint
import com.fluilet.app.domain.model.explanationFor
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El contrato real de `app/gamification.py` del backend, y la compatibilidad con respuestas anteriores. */
class GamificationMappingTest {

    private val gson = Gson()

    private val serverProgress = """
        {"total_lessons":3,"completed_lessons":2,"incomplete_lessons":1,"completion_rate_percent":66.67,
         "total_reading_seconds":900,"average_comprehension_score":75.0,"current_recommended_level":3,
         "level_progression":[{"lesson_number":1,"reading_id":"r1","recorded_at":"2026-10-01T10:00:00+00:00","category":"deportes","simplification_level":2,"completed":true,"completion_percentage":100,"comprehension_score":100,"duration_seconds":300,"original_readability_score":30.0,"adapted_readability_score":70.0}],
         "badges":[{"code":"primera_leccion","title":"Primera Lección","description":"d","unlocked":true}],
         "streak_days":4,"total_gems":330,"total_xp":450,"total_stars":330,"current_rank_title":"Guardián Cyber-Lector","next_rank_stars":700,
         "mascot":{"name":"FluiBot","species":"robot","stage":3,"stage_name":"Guardián Cyber-Lector","stage_description":"d","current_xp":450,"next_stage_xp":700,"progress_percent":40.0},
         "collectible_cards":[
           {"category":"ciencia_espacio","title":"Explorador del Cosmos","fun_fact":"Dato espacial","rarity":"Bloqueada","unlocked":false,"lessons_completed_in_category":0},
           {"category":"animales_naturaleza","title":"Guardián de la Naturaleza","fun_fact":"Dato animal","rarity":"Bronce","unlocked":true,"lessons_completed_in_category":1},
           {"category":"historia_misterios","title":"Detective del Pasado","fun_fact":"Dato historia","rarity":"Plata","unlocked":true,"lessons_completed_in_category":2},
           {"category":"deportes","title":"Campeón del Juego Limpio","fun_fact":"Dato deporte","rarity":"Oro","unlocked":true,"lessons_completed_in_category":3},
           {"category":"tecnologia","title":"Creador de Tecnología","fun_fact":"Dato tec","rarity":"Bloqueada","unlocked":false,"lessons_completed_in_category":0},
           {"category":"cuentos_aventuras","title":"Viajero de Aventuras","fun_fact":"Dato cuento","rarity":"Bloqueada","unlocked":false,"lessons_completed_in_category":0}],
         "shop_catalog":[{"item_id":"antena_prisma","name":"Antena Prisma Neón","slot":"antenna","description":"d","cost_gems":15,"unlocked":true}],
         "pedagogical_summary":{"headline":"¡Tu hábito lector sigue creciendo!","strength_area":"Constancia lectora","focus_area":"Explorar nuevas categorías de lectura","recommendation":"Para la próxima sesión, usa el nivel 3 y la voz a 1.0x."}}
    """.trimIndent()

    @Test fun serverGamificationIsMappedExactly() {
        val p = gson.fromJson(serverProgress, EducationalProgressDto::class.java).toDomain()

        assertTrue(p.gamificationFromServer)
        assertEquals(330, p.totalGems)
        assertEquals(450, p.currentXp)
        assertEquals(4, p.streakDays)
        assertEquals(FluiBotStage.GUARDIAN, FluiBotStage.forXp(p.currentXp))
        assertEquals("¡Tu hábito lector sigue creciendo!", p.pedagogicalSummary.headline)
        assertEquals("Constancia lectora", p.pedagogicalSummary.strengthArea)
        assertEquals("Explorar nuevas categorías de lectura", p.pedagogicalSummary.focusArea)
        assertTrue(p.pedagogicalSummary.recommendation.contains("nivel 3"))
    }

    @Test fun cardsKeepTheServersRarityTitleCountAndFunFact() {
        val cards = gson.fromJson(serverProgress, EducationalProgressDto::class.java).toDomain().cards.associateBy { it.categoryCode }

        assertEquals(6, cards.size)
        assertNull(cards.getValue("ciencia_espacio").rarity)
        assertFalse(cards.getValue("ciencia_espacio").unlocked)
        assertEquals(CardRarity.BRONCE, cards.getValue("animales_naturaleza").rarity)
        assertEquals(CardRarity.PLATA, cards.getValue("historia_misterios").rarity)
        assertEquals(CardRarity.ORO, cards.getValue("deportes").rarity)
        assertEquals(3, cards.getValue("deportes").lessonsCompleted)
        assertEquals("Dato deporte", cards.getValue("deportes").funFact)
        assertEquals("Campeón del Juego Limpio", cards.getValue("deportes").title)
    }

    @Test fun anOlderServerWithoutGamificationFallsBackToTheLocalCalculation() {
        val older = """
            {"total_lessons":1,"completed_lessons":1,"incomplete_lessons":0,"completion_rate_percent":100.0,
             "total_reading_seconds":300,"average_comprehension_score":100.0,"current_recommended_level":3,
             "level_progression":[{"lesson_number":1,"reading_id":"r1","recorded_at":"2026-10-01T10:00:00Z","category":"deportes","simplification_level":2,"completed":true,"completion_percentage":100,"comprehension_score":100,"duration_seconds":300}],
             "badges":[]}
        """.trimIndent()

        val p = gson.fromJson(older, EducationalProgressDto::class.java).toDomain()

        assertFalse(p.gamificationFromServer)
        assertEquals(130, p.totalGems) // 30 + 50 + 2 x 25
        assertEquals(200, p.currentXp) // 100 + 2 x 50
        assertEquals(6, p.cards.size)
        assertEquals(CardRarity.BRONCE, p.cards.first { it.categoryCode == "deportes" }.rarity)
        assertEquals(5, p.badges.size)
        assertTrue(p.pedagogicalSummary.headline.isNotBlank())
    }

    @Test fun aServerThatOmitsSomeCardsGetsTheRestCompletedLocally() {
        val partial = """{"total_lessons":0,"collectible_cards":[{"category":"deportes","title":"X","fun_fact":"d","rarity":"Oro","unlocked":true,"lessons_completed_in_category":3}]}"""
        val p = gson.fromJson(partial, EducationalProgressDto::class.java).toDomain()
        assertEquals(6, p.cards.size)
        assertEquals(CardRarity.ORO, p.cards.first { it.categoryCode == "deportes" }.rarity)
        assertNull(p.cards.first { it.categoryCode == "tecnologia" }.rarity)
    }

    @Test fun emptyOrMalformedJsonNeverCrashesTheMapper() {
        val p = gson.fromJson("{}", EducationalProgressDto::class.java).toDomain()
        assertEquals(0, p.totalLessons)
        assertEquals(30, p.totalGems)
        assertEquals(5, p.badges.size)
        assertEquals(6, p.cards.size)
    }

    // --- Mini-reto adaptado por edad -------------------------------------------------------------------

    private val adaptedText = "El niño lee un cuento corto. Luego juega en el parque con sus amigos. Después come una fruta fresca."

    @Test fun adaptationCarriesHintExplanationAndAgeGroup() {
        val json = """{"document":{"id":"d1"},"adapted_text":"$adaptedText","chunks":[],"question_age_group":"6-8","child_age_years":7,
            "comprehension_questions":[
              {"id":"q1","question":"¿Qué lee el niño?","options":["un cuento","un mapa","un cartel"],"correct_option_index":0,"hint":"Pista: mira la primera oración.","explanation":"¡Bien! El texto dice que lee un cuento."},
              {"id":"q2","question":"¿Dónde juega?","options":["en el mar","en el parque","en la luna"],"correct_option_index":1}]}"""
        val result = gson.fromJson(json, AdaptationReadDto::class.java).toDomain("Cuento", NlpLevel.NIVEL_2)

        assertEquals("6-8", result.questionAgeGroup)
        assertEquals(7, result.childAgeYears)
        assertEquals("Pista: mira la primera oración.", result.questions[0].hint)
        assertEquals("¡Bien! El texto dice que lee un cuento.", result.questions[0].explanationFor(true))
        // La segunda pregunta no trae pista ni explicación: se usan las de respaldo locales.
        assertNull(result.questions[1].hint)
        assertTrue(result.questions[1].effectiveHint.isNotBlank())
        assertTrue(result.questions[1].explanationFor(false).contains("en el parque"))
        assertEquals(AgeGroup.SIX_TO_EIGHT, com.fluilet.app.domain.model.ActiveReading("c", "n", com.fluilet.app.domain.model.ReadingSettings(), result).ageGroup)
    }

    @Test fun adaptationWithoutAgeFieldsIsStillValid() {
        val json = """{"document":{"id":"d1"},"adapted_text":"$adaptedText","chunks":[],
            "comprehension_questions":[{"id":"q1","question":"¿Qué?","options":["a","b","c"],"correct_option_index":2}]}"""
        val result = gson.fromJson(json, AdaptationReadDto::class.java).toDomain("Cuento", NlpLevel.NIVEL_3)
        assertNull(result.questionAgeGroup)
        assertNull(result.childAgeYears)
        assertEquals(1, result.questions.size)
    }
}
