package com.fluilet.app.domain

import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.effectiveHint
import com.fluilet.app.domain.model.explanationFor
import com.fluilet.app.domain.text.SentenceSpans
import com.fluilet.app.testutil.sampleAdaptation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeAdaptationTest {

    @Test fun ageGroupsFollowTheBackendBuckets() {
        assertEquals(AgeGroup.SIX_TO_EIGHT, AgeGroup.fromAge(6))
        assertEquals(AgeGroup.SIX_TO_EIGHT, AgeGroup.fromAge(8))
        assertEquals(AgeGroup.NINE_TO_ELEVEN, AgeGroup.fromAge(9))
        assertEquals(AgeGroup.NINE_TO_ELEVEN, AgeGroup.fromAge(11))
        assertEquals(AgeGroup.TWELVE_PLUS, AgeGroup.fromAge(12))
        assertEquals(AgeGroup.TWELVE_PLUS, AgeGroup.fromAge(40))
        assertEquals("un adulto sin edad usa el grupo 12+", AgeGroup.TWELVE_PLUS, AgeGroup.fromAge(null))
        assertEquals(listOf("6-8", "9-11", "12+"), AgeGroup.entries.map { it.code })
        assertEquals(AgeGroup.NINE_TO_ELEVEN, AgeGroup.fromCode("9-11"))
        assertNull(AgeGroup.fromCode("otro"))
    }

    @Test fun onlyTheYoungestGroupGetsTheBigFriendlyLayout() {
        assertTrue(AgeGroup.SIX_TO_EIGHT.isYoungReader)
        assertFalse(AgeGroup.NINE_TO_ELEVEN.isYoungReader)
        assertFalse(AgeGroup.TWELVE_PLUS.isYoungReader)
    }

    private fun reading(group: String?, serverAge: Int?, localAge: Int?) = ActiveReading(
        childId = "c1", readerName = "Mateo", settings = ReadingSettings(),
        adaptation = sampleAdaptation().copy(questionAgeGroup = group, childAgeYears = serverAge), ageYears = localAge
    )

    @Test fun theServersAgeGroupWinsThenTheServersAgeThenTheLocalAge() {
        assertEquals(AgeGroup.NINE_TO_ELEVEN, reading("9-11", 7, 7).ageGroup)
        assertEquals(AgeGroup.SIX_TO_EIGHT, reading(null, 7, 15).ageGroup)
        assertEquals(AgeGroup.SIX_TO_EIGHT, reading(null, null, 8).ageGroup)
        assertEquals(AgeGroup.TWELVE_PLUS, reading(null, null, null).ageGroup)
        assertEquals(7, reading(null, 7, 9).displayAge)
        assertEquals(9, reading(null, null, 9).displayAge)
        assertNull(reading(null, null, null).displayAge)
    }

    private val question = ComprehensionQuestion("q1", "¿Qué usan?", listOf("libros", "carros", "nubes"), 0)

    @Test fun hintFallsBackToAGeneralHelpfulOne() {
        assertEquals("Pista del servidor", question.copy(hint = "Pista del servidor").effectiveHint)
        assertTrue(question.effectiveHint.contains("Relee"))
        assertTrue("una pista vacía también usa el respaldo", question.copy(hint = "  ").effectiveHint.contains("Relee"))
    }

    @Test fun explanationsAreAlwaysPositiveAndNeverBlame() {
        assertEquals("Explicación del servidor", question.copy(explanation = "Explicación del servidor").explanationFor(false))
        assertTrue(question.explanationFor(true).contains("libros"))
        val wrong = question.explanationFor(false)
        assertTrue("dice cuál era la respuesta", wrong.contains("libros"))
        assertTrue("tono alentador", wrong.contains("No pasa nada") && wrong.contains("Tú puedes"))
        assertFalse(wrong.lowercase().contains("error") || wrong.lowercase().contains("mal"))
    }
}

class SentenceSpansTest {

    private fun slices(text: String) = SentenceSpans.compute(text).map { text.substring(it.first, it.last + 1) }

    @Test fun splitsOnSentenceEndingsKeepingPunctuation() {
        assertEquals(listOf("Hola mundo.", "¿Cómo estás?", "¡Muy bien!"), slices("Hola mundo. ¿Cómo estás? ¡Muy bien!"))
    }

    @Test fun textWithoutPunctuationIsASingleSentence() {
        assertEquals(listOf("una frase sin punto final"), slices("una frase sin punto final"))
    }

    @Test fun newlinesAlsoSeparateSentencesAndWhitespaceIsTrimmed() {
        assertEquals(listOf("Primera línea.", "Segunda línea sin punto"), slices("  Primera línea.  \n  Segunda línea sin punto  "))
    }

    @Test fun blankTextHasNoSentences() {
        assertTrue(SentenceSpans.compute("   \n ").isEmpty())
        assertEquals(-1, SentenceSpans.indexAt(emptyList(), 3))
    }

    @Test fun indexAtFindsTheSentenceThatContainsAnOffset() {
        val text = "Uno dos. Tres cuatro. Cinco."
        val spans = SentenceSpans.compute(text)
        assertEquals(0, SentenceSpans.indexAt(spans, 0))
        assertEquals(0, SentenceSpans.indexAt(spans, text.indexOf("dos")))
        assertEquals(1, SentenceSpans.indexAt(spans, text.indexOf("cuatro")))
        assertEquals(2, SentenceSpans.indexAt(spans, text.indexOf("Cinco")))
        assertEquals("un offset en el espacio entre oraciones cae en la anterior", 0, SentenceSpans.indexAt(spans, text.indexOf("dos") + 3))
    }

    @Test fun spansDoNotOverlapAndStayInsideTheText() {
        val text = "Una. Dos tres. Cuatro cinco seis."
        val spans = SentenceSpans.compute(text)
        assertTrue(spans.zipWithNext().all { (a, b) -> a.last < b.first })
        assertTrue(spans.all { it.first >= 0 && it.last < text.length })
    }
}
