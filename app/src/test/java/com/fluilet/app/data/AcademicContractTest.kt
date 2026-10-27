package com.fluilet.app.data

import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.EducationalProgressDto
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.TechnicalSheet
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de revisión académica del backend (f6f09d9): `original_text`, métricas exactas, misiones y `seed-demo`. */
class AcademicContractTest {

    private val gson = Gson()

    private val full = """
        {"id":"ad-123","original_text":"El Real Madrid es un club de fútbol muy laureado con sede en la capital de España.",
         "original_readability_score":66.752,"readability_score":68.122,
         "metrics":{"original_szigriszt_pazos":66.752,"adapted_szigriszt_pazos":68.122,"score_delta":1.369,
           "original_word_count":16,"adapted_word_count":15,"original_sentence_count":1,"adapted_sentence_count":2,
           "original_average_words_per_sentence":16.0,"adapted_average_words_per_sentence":7.5},
         "target_level":1,"age_group":"6-8","meets_readability_target":false,"quality_status":"best_effort",
         "document":{"id":"doc-9","original_szigriszt_score":10.0,"adapted_szigriszt_score":11.0,"category":"deportes","simplification_level":1},
         "adapted_text":"El Real Madrid es un club de fútbol. Tiene su sede en Madrid.","chunks":[],
         "title":"Real Madrid","category":"deportes","simplification_level":1,
         "comprehension_questions":[{"id":"q1","question":"¿Dónde?","options":["a","b","c"],"correct_option_index":1,"hint":"h","explanation":"e"},
                                    {"id":"q2","question":"¿Qué?","options":["a","b","c"],"correct_option_index":0,"hint":"h","explanation":"e"}],
         "question_age_group":"6-8","child_age_years":8,
         "fun_fact":"El club recibió el título de Real en 1920.",
         "key_vocabulary":[{"word":"club","definition":"Grupo deportivo.","example":"Mi club."}],
         "source":{"provider":"wikipedia","title":"Real Madrid Club de Fútbol","url":"https://es.wikipedia.org/wiki/Real_Madrid_Club_de_F%C3%BAtbol","license":"CC BY-SA 4.0","revision_id":1},
         "resolved_topic":"Real Madrid"}
    """.trimIndent()

    private fun adaptation(json: String = full) = gson.fromJson(json, AdaptationReadDto::class.java).toDomain("Lectura", NlpLevel.NIVEL_3)

    @Test fun originalTextIdAndSourceComeFromTheServer() {
        val r = adaptation()
        assertTrue(r.originalText!!.startsWith("El Real Madrid es un club"))
        assertEquals("ad-123", r.adaptationId)
        assertEquals("doc-9", r.documentId)
        assertEquals(1, r.targetLevel)
        assertEquals(false, r.meetsTarget)
        assertTrue(r.bestEffort)
        assertTrue(r.source!!.isWikipedia)
        assertEquals("Real Madrid Club de Fútbol", r.source!!.title)
        assertEquals("Real Madrid", r.topic)
    }

    @Test fun metricsUseTheNestedExactScoresNotTheDocumentOnes() {
        val m = adaptation().metrics
        assertEquals(66.752, m.originalSzigrisztScore, 0.0)
        assertEquals(68.122, m.adaptedSzigrisztScore, 0.0)
        assertEquals(1.369, m.scoreDelta, 0.0)
        assertEquals(16, m.originalWordCount)
        assertEquals(15, m.adaptedWordCount)
        assertEquals(1, m.originalSentenceCount)
        assertEquals(2, m.sentenceCount)
        assertEquals(16.0, m.originalWordsPerSentence, 0.0)
        assertEquals(7.5, m.adaptedWordsPerSentence, 0.0)
    }

    @Test fun serverOriginalAndMetricsFeedTheFichaTecnicaOfAGeneratedLesson() {
        val sheet = TechnicalSheet.of(adaptation())
        assertEquals("Bastante fácil → Bastante fácil · Adaptado al Nivel 1", sheet.headline)
        assertEquals(1, sheet.targetLevel)
        assertTrue(sheet.improved)
        assertTrue(sheet.bestEffort) // quality_status=best_effort: no se inflan los números
        assertEquals(1.37, sheet.delta, 0.01)
        assertEquals(7.5, sheet.adaptedWordsPerSentence, 0.0)
    }

    @Test fun topLevelScoresAreUsedWhenThereIsNoMetricsObjectAndTheDocumentHasNone() {
        val r = adaptation(
            """{"id":"x","original_text":"Texto original.","original_readability_score":40.5,"readability_score":55.25,
               "document":{"id":"d"},"adapted_text":"Texto adaptado.","chunks":[]}"""
        )
        assertEquals(40.5, r.metrics.originalSzigrisztScore, 0.0)
        assertEquals(55.25, r.metrics.adaptedSzigrisztScore, 0.0)
        assertEquals(14.75, r.metrics.scoreDelta, 0.0001)
    }

    @Test fun olderServerResponsesStayValidWithoutAnyNewField() {
        val r = adaptation("""{"document":{"id":"d","original_szigriszt_score":30.0,"adapted_szigriszt_score":60.0},"adapted_text":"Hola mundo.","chunks":[]}""")
        assertNull(r.originalText)
        assertNull(r.adaptationId)
        assertNull(r.source)
        assertNull(r.meetsTarget)
        assertEquals(30.0, r.metrics.originalSzigrisztScore, 0.0)
        assertEquals(0.0, r.metrics.originalWordsPerSentence, 0.0)
    }

    @Test fun wordsPerSentenceFallBackToTheCountsWhenTheServerOmitsTheAverages() {
        val r = adaptation(
            """{"document":{"id":"d","original_word_count":30,"original_sentence_count":3,"adapted_word_count":24,"sentence_count":4},"adapted_text":"Hola.","chunks":[]}"""
        )
        assertEquals(10.0, r.metrics.originalWordsPerSentence, 0.0)
        assertEquals(6.0, r.metrics.adaptedWordsPerSentence, 0.0)
    }

    // --- Misiones, racha y seed-demo ----------------------------------------------------------------

    private fun progress(extra: String) = gson.fromJson(
        """{"total_lessons":7,"completed_lessons":7,"incomplete_lessons":0,"completion_rate_percent":100.0,
            "total_reading_seconds":3000,"average_comprehension_score":85.0,"current_recommended_level":4,
            "level_progression":[],"badges":[],"streak_days":5,"current_streak_days":7,"total_gems":715,"total_xp":900$extra}""",
        EducationalProgressDto::class.java
    ).toDomain()

    @Test fun serverMissionsKeepTheirClaimedFlagAndAreMarkedAsServerSide() {
        val p = progress(
            ""","daily_missions":[
               {"id":"mission_read_lesson","title":"Explorador del Día: Completa 1 lectura","description":"d","reward_gems":25,"progress":1,"target":1,"completed":true,"claimed":true},
               {"id":"mission_quiz_master","title":"Mente Brillante","description":"d","reward_gems":30,"progress":0,"target":1,"completed":false,"claimed":false},
               {"id":"mission_category_variety","title":"Curiosidad Total","description":"d","reward_gems":40,"progress":2,"target":2,"completed":true,"claimed":true}]"""
        )
        val missions = p.serverMissions!!
        assertEquals(listOf("mission_read_lesson", "mission_quiz_master", "mission_category_variety"), missions.map { it.id })
        assertEquals(listOf(25, 30, 40), missions.map { it.rewardGems })
        assertTrue(missions.all { it.fromServer })
        assertEquals(listOf(true, false, true), missions.map { it.claimed })
        assertEquals(715, p.totalGems) // `total_gems` ya incluye las misiones abonadas
    }

    @Test fun currentStreakDaysWinsOverTheLegacyDistinctDatesCount() {
        assertEquals(7, progress("").streakDays)
    }

    @Test fun seedDemoResponseBringsTheDemoLessonsWithOriginalAndVocabulary() {
        val lesson = full.replace("\n", " ")
        val p = progress(""","demo_data":true,"demo_lessons":[$lesson]""")
        assertTrue(p.demoData)
        val l = p.demoLessons.single()
        assertEquals("Real Madrid", l.title)
        assertNotNull(l.originalText)
        assertEquals("El club recibió el título de Real en 1920.", l.funFact)
        assertEquals(listOf("club"), l.keyVocabulary.map { it.word })
        assertEquals(2, l.questions.size)
    }

    @Test fun anOrdinaryProgressResponseHasNoDemoData() {
        val p = progress("")
        assertFalse(p.demoData)
        assertTrue(p.demoLessons.isEmpty())
    }
}
