package com.fluilet.app.domain

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.LessonSourceInfo
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.text.LevelComparisons
import com.fluilet.app.domain.text.ShortLesson
import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.text.Szigriszt
import com.fluilet.app.domain.text.TextChunker
import com.fluilet.app.domain.usecase.GenerateLessonUseCase
import com.fluilet.app.testutil.FakeAdaptationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lecciones generadas cortas para niños: "béisbol" ya no llega con 16 bloques. */
class ShortLessonTest {

    private val letters = ('a'..'z').map(Char::toString)

    /** Oración única por índice ("Parte ca: ...") de 12 palabras. */
    private fun sentence(i: Int, sport: String = "béisbol") =
        "Parte ${letters[i / 26]}${letters[i % 26]}: el equipo de $sport corre feliz por el campo verde."

    private fun text(count: Int, from: Int = 0, sport: String = "béisbol") = (from until from + count).joinToString(" ") { sentence(it, sport) }

    /** Una lección de béisbol como la que adapta el servidor: unas 450 palabras al Nivel 1. */
    private fun baseball(level: Int = 1): AdaptationResult {
        val adapted = text(41) + " " + "Parte zz: el gran estadio se llena de gente."
        val original = text(30, sport = "béisbol profesional")
        return AdaptationResult(
            title = "Béisbol",
            chunks = TextChunker.chunkAdaptedText(adapted, NlpLevel.fromValue(level)),
            metrics = ReadabilityMetrics(45.0, 80.0, 35.0, 360, 460, 800, 42, 700, 30),
            documentId = "doc-1",
            category = "deportes",
            simplificationLevel = level,
            targetLevel = level,
            source = LessonSourceInfo("wikipedia", "Béisbol", "https://es.wikipedia.org/wiki/B%C3%A9isbol"),
            originalText = original,
            questions = listOf(
                // Cloze con fragmento: la oración "Parte ab" queda dentro de la lección corta.
                ComprehensionQuestion("q1", "Según la lectura, ¿qué completa esta idea: «Parte ab: el equipo de ___ corre feliz por el campo verde»?", listOf("tenis", "béisbol", "golf"), 1),
                // 6-8: la palabra "estadio" solo aparece al final del artículo completo.
                ComprehensionQuestion("q2", "Ahora, ¿qué palabra encuentras en el texto?", listOf("Dice «estadio»", "Dice «montaña»", "Dice «océano»"), 0)
            ),
            lexicalReplacements = listOf(LexicalReplacement("disciplina", "equipo"), LexicalReplacement("aforo", "estadio")),
            levelPreviews = (1..5).map { lvl ->
                LevelPreview(lvl, if (lvl == level) adapted else text(30 + lvl), 80.0 - lvl * 5, "Fácil", 11.0, 1.9, targetMet = true)
            },
            levelProgressionMet = true
        )
    }

    @Test fun aLongLessonForAYoungChildKeepsOnlyItsFirstWholeSentences() {
        val full = baseball(level = 1)
        assertTrue("antes: ${full.chunks.size} bloques", full.chunks.size >= 14)

        val short = ShortLesson.shorten(full, AgeGroup.SIX_TO_EIGHT)

        val reading = short.chunks.joinToString(" ") { it.text }
        assertTrue(SpanishText.wordCount(reading) <= ShortLesson.wordBudget(AgeGroup.SIX_TO_EIGHT))
        assertTrue("de 4 a 6 bloques: ${short.chunks.size}", short.chunks.size in 4..6)
        assertTrue("empieza por el principio", reading.startsWith(sentence(0)))
        assertTrue("termina en una oración completa", reading.endsWith("."))
        assertEquals(text(SpanishText.sentenceCount(reading)), reading)
        val info = short.shortLesson!!
        assertEquals(SpanishText.wordCount(reading), info.shownWords)
        assertEquals(SpanishText.wordCount(full.chunks.joinToString(" ") { it.text }), info.fullWords)
    }

    @Test fun olderChildrenGetLongerButStillShortLessons() {
        val nine = ShortLesson.shorten(baseball(level = 3), AgeGroup.NINE_TO_ELEVEN)
        val twelve = ShortLesson.shorten(baseball(level = 4), AgeGroup.TWELVE_PLUS)
        assertEquals(180, nine.shortLesson!!.shownWords)
        assertEquals(240, twelve.shortLesson!!.shownWords)
        assertTrue(nine.chunks.size <= 5)
        assertTrue(twelve.chunks.size <= 5)
    }

    @Test fun adultsAndAlreadyShortLessonsAreLeftAlone() {
        val full = baseball()
        assertSame("sin edad (adulto independiente)", full, ShortLesson.shorten(full, null))

        // 12 oraciones = 144 palabras: pasa del tope de 120 por poco, se deja completa.
        val nearly = full.copy(chunks = TextChunker.chunkAdaptedText(text(12), NlpLevel.NIVEL_1))
        assertSame(nearly, ShortLesson.shorten(nearly, AgeGroup.SIX_TO_EIGHT))

        val once = ShortLesson.shorten(full, AgeGroup.SIX_TO_EIGHT)
        assertSame("no se recorta dos veces", once, ShortLesson.shorten(once, AgeGroup.NINE_TO_ELEVEN))
    }

    @Test fun aLessonTheServerAlreadyCutToTheAgeBudgetIsNotCutAgain() {
        // El backend recortó la fuente a 120 palabras (10 oraciones); el Nivel 1 la alargó a 15 oraciones.
        val server = baseball(level = 1).copy(
            chunks = TextChunker.chunkAdaptedText(text(15), NlpLevel.NIVEL_1),
            originalText = text(10, sport = "béisbol")
        )
        assertSame(server, ShortLesson.shorten(server, AgeGroup.SIX_TO_EIGHT))

        // Tres oraciones largas se respetan aunque pasen del tope (mínimo del backend).
        val threeLong = server.copy(originalText = List(3) { "Oración larga ${letters[it]} " + "con muchas palabras ".repeat(20) + "fin." }.joinToString(" "))
        assertSame(threeLong, ShortLesson.shorten(threeLong, AgeGroup.SIX_TO_EIGHT))
    }

    @Test fun aLessonAlreadyShortFromTheServerShowsNoLocalNotesAndKeepsServerMetrics() {
        val server = baseball(level = 1).copy(
            chunks = TextChunker.chunkAdaptedText(text(10), NlpLevel.NIVEL_1),
            originalText = text(9, sport = "béisbol profesional"),
            levelPreviews = (1..5).map { lvl -> LevelPreview(lvl, text(10 - lvl), 80.0 - lvl * 5, "Fácil", 11.0, 1.9, targetMet = true) }
        )

        val shown = ShortLesson.shorten(server, AgeGroup.SIX_TO_EIGHT)

        assertNull("sin aviso de lección corta", shown.shortLesson)
        assertTrue("métricas oficiales del servidor", shown.levelPreviews.all { it.fromServer })
        val comparison = LevelComparisons.compare(shown, shown.levelPreviews, 1, shown.originalText)!!
        assertTrue("sin aviso de medición en el dispositivo", comparison.fromServer)
        assertEquals(75.0, comparison.adaptedScore, 0.0)
        assertTrue("de 4 a 6 bloques", shown.chunks.size in 4..6)
    }

    @Test fun theOriginalAndAllFiveLevelsAreCutAtTheSamePointAndMeasuredAgain() {
        val short = ShortLesson.shorten(baseball(level = 1), AgeGroup.SIX_TO_EIGHT)
        val info = short.shortLesson!!
        val reading = short.chunks.joinToString(" ") { it.text }

        val original = short.originalText!!
        assertTrue(original.startsWith(sentence(0, "béisbol profesional")))
        val originalWords = SpanishText.wordCount(text(30, sport = "béisbol profesional"))
        assertTrue("el original cubre al menos la misma parte", SpanishText.wordCount(original) >= info.fraction * originalWords)
        assertTrue(SpanishText.wordCount(original) < originalWords)

        assertEquals(5, short.levelPreviews.size)
        val own = short.levelPreviews.first { it.level == 1 }.adaptedText
        assertEquals("el nivel pedido es exactamente la lectura", reading, own.replace(Regex("\\s+"), " "))
        assertFalse("sin párrafos artificiales por bloque", own.contains("\n"))
        short.levelPreviews.forEach { p ->
            assertFalse("medido en el dispositivo", p.fromServer)
            assertEquals(Szigriszt.score(p.adaptedText), p.readabilityScore, 1e-9)
            assertEquals(p.inBand, p.targetMet)
        }
        val m = short.metrics
        assertEquals(SpanishText.wordCount(reading), m.adaptedWordCount)
        assertEquals(Szigriszt.score(reading), m.adaptedSzigrisztScore, 1e-9)
        assertEquals(Szigriszt.score(original), m.originalSzigrisztScore, 1e-9)
        assertNull("la ganancia se deriva de las métricas medidas", short.readabilityGain)
    }

    @Test fun theQuizKeepsOnlyQuestionsWhoseAnswerIsStillInTheLesson() {
        val short = ShortLesson.shorten(baseball(level = 1), AgeGroup.SIX_TO_EIGHT)
        assertEquals(listOf("q1"), short.questions.map { it.id })
        assertEquals("solo reemplazos que siguen en la lectura", listOf("equipo"), short.lexicalReplacements.map { it.adapted })
    }

    @Test fun groundingChecksTheAnswerAndItsQuotedFragment() {
        val lesson = "Los equipos tienen nueve jugadores. El lanzador tira la pelota."
        fun q(question: String, vararg options: String) = ComprehensionQuestion("q", question, options.toList(), 0)
        assertTrue(ShortLesson.grounded(q("Según la lectura, ¿qué completa esta idea: «Los equipos tienen ___ jugadores»?", "nueve", "diez"), lesson))
        assertFalse(ShortLesson.grounded(q("Según la lectura, ¿qué completa esta idea: «Los árbitros tienen ___ jugadores»?", "nueve", "diez"), lesson))
        assertTrue(ShortLesson.grounded(q("Vamos, ¿qué palabra aparece en la lectura?", "Dice «pelota»", "Dice «río»"), lesson))
        assertFalse(ShortLesson.grounded(q("Vamos, ¿qué palabra aparece en la lectura?", "Dice «estadio»", "Dice «río»"), lesson))
        assertTrue("idea (9-11)", ShortLesson.grounded(q("¿Qué idea aparece en esta lectura?", "El lanzador tira la pelota", "El lanzador no tira la pelota"), lesson))
        assertFalse("palabra suelta dentro de otra", ShortLesson.grounded(q("¿Qué palabra aparece?", "equipo", "río"), lesson))
    }

    @Test fun theDetailFetchedLaterIsCutLikeTheLessonBeingRead() {
        val full = baseball(level = 1)
        val short = ShortLesson.shorten(full, AgeGroup.SIX_TO_EIGHT)

        val detail = ShortLesson.alignDetail(full, short)

        assertEquals(short.originalText, detail.originalText)
        assertEquals(short.levelPreviews.map { it.adaptedText }, detail.levelPreviews.map { it.adaptedText })
        assertSame("una lectura completa no cambia el detalle", full, ShortLesson.alignDetail(full, full))
    }

    @Test fun generatedLessonsForAChildComeShortFromTheUseCase() = runBlocking {
        val repository = FakeAdaptationRepository()
        repository.generateResult = Result.success(baseball(level = 2))
        val generate = GenerateLessonUseCase(repository)

        val forChild = generate(LessonRequest(LessonCategory.DEPORTES, "béisbol", NlpLevel.NIVEL_2, AgeGroup.SIX_TO_EIGHT), "child-1").getOrThrow()
        val forAdult = generate(LessonRequest(LessonCategory.DEPORTES, "béisbol", NlpLevel.NIVEL_2, null), null).getOrThrow()

        assertNotNull(forChild.shortLesson)
        assertTrue(forChild.chunks.size < forAdult.chunks.size)
        assertNull("un adulto independiente lee la lección completa", forAdult.shortLesson)
    }

    @Test fun onlyTopicLessonsCountAsGenerated() {
        assertTrue(baseball().isGeneratedLesson)
        assertTrue(baseball().copy(source = null).isGeneratedLesson)
        assertFalse(baseball().copy(source = null, category = AdaptationResult.FREE_READING_CATEGORY).isGeneratedLesson)
        assertFalse(baseball().copy(source = null, category = null).isGeneratedLesson)
    }
}
