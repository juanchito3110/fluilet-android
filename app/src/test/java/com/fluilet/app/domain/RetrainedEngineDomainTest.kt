package com.fluilet.app.domain

import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.model.ModelAudit
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityBand
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.TextChunk
import com.fluilet.app.domain.text.LevelComparisons
import com.fluilet.app.domain.text.LexicalDiff
import com.fluilet.app.domain.text.Szigriszt
import com.fluilet.app.domain.text.TextAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motor reentrenado del backend 42482e0 en el dominio de la app y motor local de respaldo con sus 5 variantes. */
class RetrainedEngineDomainTest {

    private val encyclopedic = """
        La aplicación transforma textos complejos mediante un motor de Inteligencia Artificial propio, permitiendo una lectura simplificada que reduce la fatiga visual y el estrés cognitivo, aunque además requiere que los tutores configuren previamente el nivel de simplificación adecuado para cada niño.

        Posteriormente, los usuarios pueden utilizar la herramienta para realizar sesiones de lectura guiada, y los especialistas obtienen métricas de legibilidad que permiten evaluar el progreso, pero solamente cuando la sesión finaliza correctamente.
    """.trimIndent()

    // --- Motor local de respaldo: 5 variantes diferenciadas --------------------------------------------

    private val science = "Los científicos utilizan numerosos instrumentos complejos para observar el universo, aunque posteriormente " +
        "deben comprender los datos que obtienen mediante cálculos fundamentales que realizan durante varias semanas de trabajo."

    @Test fun theLocalEngineProducesFiveDifferentiatedVariantsThatNeverGetHarder() {
        val texts = listOf(encyclopedic, science) + DemoData.lessons().map { it.originalText!! }
        texts.forEach { text ->
            val previews = TextAdapter.levelPreviews(text)
            assertEquals(listOf(1, 2, 3, 4, 5), previews.map { it.level })
            assertEquals("5 textos distintos", 5, previews.map { it.adaptedText }.toSet().size)
            val scores = previews.map { it.readabilityScore }
            // Un sinónimo con las mismas sílabas puede empatar dos niveles vecinos; nunca un nivel más alto es más fácil.
            assertTrue("Nivel 1 ≥ 2 ≥ 3 ≥ 4 ≥ 5: $scores", scores.zipWithNext().all { (a, b) -> a >= b })
            assertTrue("Nivel 1 > Nivel 5: $scores", scores.first() > scores.last())
            assertTrue(previews.none { it.fromServer })
        }
    }

    @Test fun theLocalEngineNeverLeavesDanglingFragments() {
        val texts = listOf(encyclopedic, science) + DemoData.lessons().map { it.originalText!! }
        val sentences = texts.flatMap { TextAdapter.levelPreviews(it) }.flatMap { com.fluilet.app.domain.text.SpanishText.sentences(it.adaptedText) }
        val endings = setOf("que", "y", "de", "en", "la", "el", "los", "las", "con")
        assertTrue(sentences.none { s -> s.trimEnd('.').substringAfterLast(' ').lowercase() in endings })
        assertTrue(sentences.none { it.startsWith("Ya que") || it.startsWith("Así como") || it.startsWith("Situado") })
    }

    @Test fun adaptCarriesPreviewsGainLabelAndTheReplacementsOfTheRequestedLevel() {
        val result = TextAdapter.adapt("t", encyclopedic, NlpLevel.NIVEL_1)
        val m = result.metrics

        assertEquals(5, result.levelPreviews.size)
        assertEquals(m.adaptedSzigrisztScore - m.originalSzigrisztScore, result.gain, 0.011)
        assertTrue("el nivel 1 gana legibilidad", result.gain > 0)
        assertEquals(Szigriszt.inflesz(m.adaptedSzigrisztScore), result.readabilityLevel)
        assertEquals(1, result.targetLevel)
        assertEquals(true, result.levelProgressionMet)
        assertTrue(result.isLocalEngine)
        assertFalse("el motor local nunca se presenta como el modelo reentrenado", result.isRetrainedModel)

        val pairs = result.lexicalReplacements.map { it.original.lowercase() to it.adapted.lowercase() }
        assertTrue(pairs.toString(), "utilizar" to "usar" in pairs)
        assertTrue(pairs.toString(), "posteriormente" to "después" in pairs)
        assertEquals("los reemplazos de la raíz son los del nivel pedido", result.levelPreviews.first().lexicalReplacements, result.lexicalReplacements)
    }

    @Test fun eachLevelUsesAProgressivelySmallerVocabularyChange() {
        val previews = TextAdapter.levelPreviews(encyclopedic)
        val counts = previews.map { it.lexicalReplacements.size }
        assertTrue("más reemplazos en los niveles bajos: $counts", counts.zipWithNext().all { (a, b) -> a >= b })
        assertTrue("el nivel 5 conserva el vocabulario", previews.last().lexicalReplacements.isEmpty())
        assertTrue(previews.last().adaptedText.contains("utilizar"))
        assertFalse(previews.first().adaptedText.contains("utilizar"))
    }

    @Test fun offlineLessonsComeFromTheLocalBankAtTheRequestedLevel() {
        val lesson = DemoData.offlineLesson("deportes", NlpLevel.NIVEL_2)!!
        assertEquals("Real Madrid", lesson.title)
        assertEquals(2, lesson.targetLevel)
        assertEquals(5, lesson.levelPreviews.size)
        assertNotNull(lesson.originalText)
        assertEquals(2, lesson.questions.size)
        assertNull("sin conexión no hay lecciones de categorías fuera del banco local", DemoData.offlineLesson("tecnologia", NlpLevel.NIVEL_2))
    }

    // --- Bandas, etiquetas y fórmula ---------------------------------------------------------------------

    @Test fun theFiveBandsAreTheOnesOfTheBackend() {
        assertEquals(listOf("82–100", "74–84", "65–74", "58–66", "50–65"), ReadabilityBand.entries.map { it.range })
        assertTrue(83.32 in ReadabilityBand.N1)
        assertFalse(69.39 in ReadabilityBand.N2)
        assertTrue(61.84 in ReadabilityBand.N4)
        assertNull(ReadabilityBand.forLevel(6))
    }

    @Test fun infleszLabelsMatchTheBackend() {
        assertEquals("Muy fácil", Szigriszt.inflesz(83.32))
        assertEquals("Bastante fácil", Szigriszt.inflesz(65.0))
        assertEquals("Normal", Szigriszt.inflesz(61.84))
        assertEquals("Algo difícil", Szigriszt.inflesz(44.75))
        assertEquals("Muy difícil", Szigriszt.inflesz(39.9))
    }

    @Test fun syllablesPerWordAreSolvedFromTheScoreAndSentenceLength() {
        // Valores reales de "Agujeros negros" (Diego, Nivel 4) en el backend 42482e0.
        assertEquals(2.076923076923077, Szigriszt.syllablesPerWord(61.84269230769231, 15.6), 1e-9)
        assertEquals(1.8541666666666667, Szigriszt.syllablesPerWord(83.32041666666667, 8.0), 1e-9)
    }

    // --- Transformaciones léxicas ------------------------------------------------------------------------

    @Test fun lexicalDiffFindsTheReplacedTermsLikeTheBackend() {
        val pairs = LexicalDiff.replacements(
            "Los niños utilizan herramientas complejas en la escuela.",
            "Los niños usan herramientas difíciles en la escuela."
        )
        assertEquals(listOf(LexicalReplacement("utilizan", "usan"), LexicalReplacement("complejas", "difíciles")), pairs)
        assertTrue("sin cambios no hay pares", LexicalDiff.replacements("Hola amigo.", "Hola amigo.").isEmpty())
        assertTrue("solo mayúsculas no cuenta", LexicalDiff.replacements("hola Amigo", "Hola amigo").isEmpty())
    }

    @Test fun highlightSpansMatchWholeWordsCaseInsensitive() {
        val text = "Se usan llamadas cortas. Usan todo."
        val spans = LexicalDiff.spans(text, listOf("usan", "llamadas"))
        assertEquals(listOf("usan", "llamadas", "Usan"), spans.map { text.substring(it) })
        assertTrue("no marca trozos de palabra", LexicalDiff.spans("usando", listOf("usan")).isEmpty())
    }

    // --- Comparativa por nivel ---------------------------------------------------------------------------

    private fun serverLesson(): AdaptationResult {
        fun preview(level: Int, score: Double, wps: Double, text: String) =
            LevelPreview(level, text, score, Szigriszt.inflesz(score), wps, Szigriszt.syllablesPerWord(score, wps))
        return AdaptationResult(
            title = "Agujeros negros",
            chunks = listOf(TextChunk(0, "Los agujeros negros son zonas del espacio.")),
            metrics = ReadabilityMetrics(
                56.43970588235297, 61.84269230769231, 5.402986425339343, 85, 78, 162, 5, 182, 5,
                originalAvgWordsPerSentence = 17.0, adaptedAvgWordsPerSentence = 15.6,
                originalAvgSyllablesPerWord = 2.1411764705882352, adaptedAvgSyllablesPerWord = 2.076923076923077
            ),
            targetLevel = 4,
            simplificationLevel = 4,
            originalText = "Los agujeros negros son regiones del espacio cuya gravedad impide que la luz escape desde su interior.",
            readabilityGain = 5.402986425339343,
            lexicalReplacements = listOf(LexicalReplacement("regiones", "zonas")),
            levelPreviews = listOf(
                preview(1, 83.32041666666667, 8.0, "Un agujero negro es una zona del espacio. Su fuerza de gravedad impide que salga la luz."),
                preview(2, 79.01759705488624, 9.222222222222221, "Un agujero negro es una zona del espacio con mucha gravedad."),
                preview(3, 69.38801204819279, 16.6, "Los agujeros negros son zonas del espacio donde la gravedad impide que la luz salga."),
                preview(4, 61.84269230769231, 15.6, "Los agujeros negros son zonas del espacio cuya gravedad impide que la luz escape."),
                preview(5, 57.06639534883725, 17.2, "Los agujeros negros son regiones del espacio cuya gravedad impide que la luz escape.")
            ),
            levelProgressionMet = true,
            modelVersion = "context-ranker-es-v2.3",
            checkpointName = "context-ranker-v2.3",
            engineVersion = "pedagogical-es-v2.3"
        )
    }

    @Test fun theComparisonOfTheLessonLevelUsesTheServersExactMetricsAndReplacements() {
        val lesson = serverLesson()
        val previews = LevelComparisons.previews(lesson, null, lesson.originalText)
        assertEquals(4, LevelComparisons.defaultLevel(lesson, previews))

        val c = LevelComparisons.compare(lesson, previews, 4, lesson.originalText)!!
        assertEquals(56.4397, c.originalScore, 1e-4)
        assertEquals(61.8427, c.adaptedScore, 1e-4)
        assertEquals(5.403, c.gain, 1e-3)
        assertEquals(17.0, c.originalWordsPerSentence, 1e-9)
        assertEquals(15.6, c.adaptedWordsPerSentence, 1e-9)
        assertEquals(2.1412, c.originalSyllablesPerWord, 1e-4)
        assertEquals(2.0769, c.adaptedSyllablesPerWord, 1e-4)
        assertEquals("Normal", c.readabilityLevel)
        assertTrue(c.inBand)
        assertEquals(listOf(LexicalReplacement("regiones", "zonas")), c.replacements)
        assertTrue(c.fromServer)
        assertTrue(lesson.isRetrainedModel)
    }

    @Test fun switchingToAnotherLevelShowsItsOwnTextScoreBandAndTransformations() {
        val lesson = serverLesson()
        val previews = LevelComparisons.previews(lesson, null, lesson.originalText)

        val level1 = LevelComparisons.compare(lesson, previews, 1, lesson.originalText)!!
        assertEquals(83.32, level1.adaptedScore, 0.01)
        assertEquals(26.88, level1.gain, 0.01)
        assertEquals("Muy fácil", level1.readabilityLevel)
        assertEquals(ReadabilityBand.N1, level1.band)
        assertTrue(level1.inBand)
        assertTrue(level1.adaptedText.startsWith("Un agujero negro"))
        assertTrue("el nivel 1 calcula sus propios reemplazos", level1.replacements.isNotEmpty())

        val level5 = LevelComparisons.compare(lesson, previews, 5, lesson.originalText)!!
        assertEquals(0.63, level5.gain, 0.01)
        assertTrue(level5.adaptedText.contains("regiones"))
        assertNull("nivel inexistente", LevelComparisons.compare(lesson, previews, 7, lesson.originalText))
    }

    @Test fun aLessonWithoutPreviewsButWithItsOriginalGetsFiveLocalVariants() {
        val lesson = serverLesson().copy(levelPreviews = emptyList(), originalText = encyclopedic)
        val previews = LevelComparisons.previews(lesson, null, lesson.originalText)
        assertEquals(5, previews.size)
        assertTrue(previews.none { it.fromServer })
        val c = LevelComparisons.compare(lesson, previews, 2, lesson.originalText)!!
        assertFalse(c.fromServer)
        assertEquals("el original se mide con el mismo motor local", Szigriszt.score(encyclopedic), c.originalScore, 0.01)
    }

    @Test fun aLessonWithNothingToCompareStillOffersItsOwnLevel() {
        val lesson = serverLesson().copy(levelPreviews = emptyList(), originalText = null)
        val previews = LevelComparisons.previews(lesson, null, null)
        assertEquals(listOf(4), previews.map { it.level })
        val c = LevelComparisons.compare(lesson, previews, 4, null)!!
        assertEquals(61.84, c.adaptedScore, 0.01)
        assertEquals(2.0769, c.adaptedSyllablesPerWord, 1e-4)
    }

    // --- Auditoría del modelo -----------------------------------------------------------------------------

    @Test fun theVerifiedAuditOfCommit42482e0FormatsLikeTheThesis() {
        val audit = ModelAudit.VERIFIED_42482E0
        assertTrue(audit.acceptanceCriteriaMet)
        assertEquals("4,738", audit.datasetPairs)
        assertEquals(36, audit.epochs)
        assertEquals("73.25%", audit.lexicalAccuracyText)
        assertEquals("53.07%", audit.baselineLexicalAccuracyText)
        assertEquals("44.558", audit.retrainedSariText)
        assertEquals("44.544", audit.baselineSariText)
        assertEquals("Catálogo 31/31 · EASIER 6/6 · Controlados 6/6 · Demos 15/15", audit.bandsSummary)
        assertTrue(audit.fiveBandsVerified)
        assertEquals("4,738 pares · SARI 44.56 · Exactitud 73.25% · 5 Bandas Verificadas", audit.badgeLine)
        assertEquals("context-ranker-es-v2.3 · context-ranker-v2.3", audit.versionLine)
        assertFalse("es el respaldo sin conexión", audit.fromServer)
    }

    @Test fun incompleteBandsAreNeverAdvertisedAsVerified() {
        val audit = ModelAudit.VERIFIED_42482E0.copy(
            cohorts = ModelAudit.VERIFIED_42482E0.cohorts.map { if (it.key == "easier_diagnostic") it.copy(met = 5) else it }
        )
        assertFalse(audit.fiveBandsVerified)
        assertFalse(audit.badgeLine.contains("Bandas"))
        assertTrue(audit.bandsSummary.contains("EASIER 5/6"))
    }
}
