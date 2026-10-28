package com.fluilet.app.domain

import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.text.Szigriszt
import com.fluilet.app.domain.text.TextAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Paso final de calibración del motor local: progresión estricta N1 > N2 > N3 > N4 > N5 y bandas por nivel. */
class LevelCalibrationTest {

    private val encyclopedic = listOf(
        "La aplicación transforma textos complejos mediante un motor de Inteligencia Artificial propio, permitiendo una lectura simplificada que reduce la fatiga visual y el estrés cognitivo, aunque además requiere que los tutores configuren previamente el nivel de simplificación adecuado para cada niño.\n\n" +
            "Posteriormente, los usuarios pueden utilizar la herramienta para realizar sesiones de lectura guiada, y los especialistas obtienen métricas de legibilidad que permiten evaluar el progreso, pero solamente cuando la sesión finaliza correctamente.",
        "La fotosíntesis es el proceso mediante el cual las plantas, las algas y algunas bacterias transforman la energía de la luz solar en energía química. Durante este proceso, los organismos absorben dióxido de carbono del aire y agua del suelo, y liberan oxígeno a la atmósfera como subproducto. La clorofila, un pigmento verde presente en los cloroplastos, es fundamental para captar la luz. Sin este mecanismo, la vida en la Tierra tal como la conocemos no sería posible, ya que la mayoría de las cadenas alimentarias dependen directa o indirectamente de las plantas.",
        "El antiguo Egipto fue una civilización que se desarrolló a lo largo del río Nilo durante más de tres mil años. Sus habitantes construyeron enormes pirámides que servían como tumbas para los faraones, quienes eran considerados gobernantes divinos. Los egipcios desarrollaron un sistema de escritura llamado jeroglífico, que combinaba símbolos fonéticos e ideográficos, y lo utilizaban para registrar acontecimientos históricos, textos religiosos y documentos administrativos. Además, alcanzaron importantes avances en matemáticas, medicina y astronomía, conocimientos que influyeron posteriormente en otras culturas del Mediterráneo.",
        "La inteligencia artificial es una rama de la informática que se ocupa de crear sistemas capaces de realizar tareas que normalmente requieren inteligencia humana, como el reconocimiento del habla, la toma de decisiones o la traducción entre idiomas. Los algoritmos de aprendizaje automático permiten que las computadoras identifiquen patrones en grandes cantidades de datos y mejoren su rendimiento con la experiencia, sin necesidad de ser programadas explícitamente para cada situación.",
        "Los Juegos Olímpicos modernos se celebraron por primera vez en Atenas en 1896, gracias a la iniciativa del pedagogo francés Pierre de Coubertin, quien consideraba que el deporte podía fomentar la paz y el entendimiento entre las naciones. Desde entonces, la competición se ha celebrado cada cuatro años, con excepción de los periodos de las dos guerras mundiales, y ha incorporado progresivamente nuevas disciplinas deportivas, así como la participación de mujeres atletas, que compitieron por primera vez en París en 1900."
    )

    private val story = "Había una vez un pequeño dragón que vivía en una cueva junto al mar. Cada mañana salía a volar sobre las olas y " +
        "saludaba a los pescadores del pueblo. Un día encontró una botella con un mensaje. La abrió con cuidado y leyó una carta de " +
        "una niña que pedía ayuda. El dragón voló durante toda la noche hasta encontrar la isla donde la niña esperaba."

    private val corpus = encyclopedic + story + DemoData.lessons().map { it.originalText!! }

    private fun scores(previews: List<LevelPreview>) = previews.map { it.readabilityScore }

    private fun strictlyDecreasing(values: List<Double>) = values.zipWithNext().all { (a, b) -> a > b }

    @Test fun everyCorpusTextProgressesStrictlyFromLevel1ToLevel5() {
        corpus.forEach { text ->
            val result = TextAdapter.adapt("t", text, NlpLevel.NIVEL_3)
            val values = scores(result.levelPreviews)
            assertEquals(listOf(1, 2, 3, 4, 5), result.levelPreviews.map { it.level })
            assertTrue("N1 > N2 > N3 > N4 > N5: $values", strictlyDecreasing(values))
            assertEquals(true, result.levelProgressionMet)
        }
    }

    @Test fun theOfflineBankLessonsLandEveryLevelInItsBand() {
        listOf("deportes", "animales_naturaleza", "ciencia_espacio").forEach { category ->
            NlpLevel.entries.forEach { level ->
                val lesson = DemoData.offlineLesson(category, level)!!
                val previews = lesson.levelPreviews
                assertTrue("${lesson.title}: ${previews.map { it.level to it.readabilityScore }}", previews.all { it.inBand && it.targetMet })
                assertTrue(strictlyDecreasing(scores(previews)))
                assertEquals(true, lesson.levelProgressionMet)
                // La lectura que se entrega es la variante del nivel pedido.
                val chosen = previews.first { it.level == level.value }
                assertEquals(chosen.readabilityScore, lesson.metrics.adaptedSzigrisztScore, 0.0)
                assertEquals(SpanishText.words(chosen.adaptedText), SpanishText.words(lesson.chunks.joinToString(" ") { it.text }))
            }
        }
    }

    @Test fun eachScoreIsMeasuredOnTheVariantTextItself() {
        (corpus + DemoData.lessons().flatMap { l -> l.levelPreviews.map { it.adaptedText } }).forEach { text ->
            TextAdapter.levelPreviews(text).forEach { p ->
                assertEquals(Szigriszt.score(p.adaptedText), p.readabilityScore, 0.0)
                val words = SpanishText.wordCount(p.adaptedText)
                assertEquals(words.toDouble() / SpanishText.sentenceCount(p.adaptedText), p.avgSentenceLength, 1e-9)
                assertEquals(SpanishText.syllableCount(p.adaptedText).toDouble() / words, p.syllablesPerWord, 1e-9)
                assertEquals(Szigriszt.inflesz(p.readabilityScore), p.readabilityLevel)
            }
        }
    }

    @Test fun anyTextWithTwoOrMoreSentencesProgressesStrictly() {
        val pool = listOf(
            "El sol sale cada mañana por el este.", "Los gatos duermen muchas horas al día.",
            "La biblioteca abre sus puertas a las nueve.", "Los científicos estudian el movimiento de los planetas.",
            "Mi abuela prepara una sopa deliciosa los domingos.", "Las abejas transportan el polen de flor en flor.",
            "El tren llegó tarde a la estación principal.", "Los volcanes expulsan lava, ceniza y gases calientes.",
            "La profesora explicó la lección con mucha paciencia.", "Los delfines se comunican mediante silbidos y chasquidos.",
            "El equipo entrenó durante toda la semana para el torneo.", "Las montañas más altas del mundo están en Asia.",
            "Construyeron un puente nuevo sobre el río.", "Los astronautas viven varios meses en la estación espacial.",
            "Las tortugas marinas ponen sus huevos en la arena.", "La computadora procesa millones de datos en un segundo.",
            "Durante el invierno, los osos descansan en sus cuevas.", "El museo exhibe fósiles de dinosaurios muy antiguos.",
            "Los niños plantaron árboles en el parque del barrio.", "La luna refleja la luz que recibe del sol."
        )
        val random = Random(42)
        repeat(300) {
            val sentences = List(random.nextInt(2, 9)) { pool[random.nextInt(pool.size)] }
            val text = if (random.nextBoolean()) sentences.joinToString(" ")
            else sentences.chunked(3).joinToString("\n\n") { it.joinToString(" ") }
            val values = scores(TextAdapter.levelPreviews(text))
            assertTrue("$values\n$text", strictlyDecreasing(values))
        }
    }

    @Test fun aSingleShortSentenceWithoutCutsIsReportedHonestly() {
        val result = TextAdapter.adapt("t", "Mi gato duerme en el sofá todo el día.", NlpLevel.NIVEL_2)
        val values = scores(result.levelPreviews)
        // Sin cortes, vocabulario ni glosas posibles no hay 5 formas distintas: no se inventa una progresión.
        assertEquals(false, result.levelProgressionMet)
        assertTrue(values.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test fun calibrationIsDeterministic() {
        corpus.forEach { text -> assertEquals(TextAdapter.levelPreviews(text), TextAdapter.levelPreviews(text)) }
    }

    @Test fun initialLevelsSegmentAndAdvancedLevelsJoinClauses() {
        val previews = TextAdapter.levelPreviews(story)
        val first = previews.first()
        val last = previews.last()
        assertTrue(first.avgSentenceLength < last.avgSentenceLength)
        assertFalse("el Nivel 1 nunca une oraciones", first.adaptedText.contains(';'))
        assertTrue("el Nivel 5 usa oraciones compuestas", last.adaptedText.contains(';') || last.adaptedText.contains(", y "))
        // "... sobre las olas y saludaba a ...": la segunda cláusula tiene su propio verbo y se separa sin perder sentido.
        assertTrue(first.adaptedText.contains("Saludaba a los pescadores del pueblo."))
        assertTrue(first.adaptedText.contains("Leyó una carta"))
    }

    @Test fun microGlossesExplainTermsOfTheTextOnlyInInitialLevels() {
        val previews = TextAdapter.levelPreviews(story)
        val gloss = Regex("(?m)^Dragón: ")
        assertTrue("el Nivel 1 define un término del propio texto", gloss.containsMatchIn(previews.first().adaptedText))
        previews.filter { it.level > 3 }.forEach { assertFalse(gloss.containsMatchIn(it.adaptedText)) }
    }

    @Test fun joinedSentencesContinueInLowercaseExceptProperNouns() {
        val texts = DemoData.lessons().flatMap { l -> l.levelPreviews.map { it.adaptedText } } +
            corpus.flatMap { t -> TextAdapter.levelPreviews(t).map { it.adaptedText } }
        val proper = setOf("Madrid", "Santiago", "Sagitario", "Atenas", "París", "Pierre", "Egipto", "Tierra", "Vía", "Real", "Liga", "Copa")
        texts.forEach { text ->
            Regex(";\\s+(\\p{Lu}\\p{L}*)").findAll(text).forEach { m ->
                assertTrue("«${m.value}» en: $text", m.groupValues[1] in proper)
            }
        }
    }

    @Test fun neverLeavesVerblessPurposeFragments() {
        val text = "Los científicos exploran arrecifes de coral para comprender cómo se alimentan los habitantes del mar, " +
            "y también para proteger a las especies que se encuentran amenazadas por la contaminación."
        TextAdapter.levelPreviews(text).flatMap { SpanishText.sentences(it.adaptedText) }.forEach {
            assertFalse(it, it.startsWith("También para") || it.startsWith("Para "))
        }
    }

    @Test fun vocabularyIsNeverSimplerInAHigherLevel() {
        corpus.forEach { text ->
            val counts = TextAdapter.levelPreviews(text).map { it.lexicalReplacements.size }
            assertTrue("$counts", counts.zipWithNext().all { (a, b) -> a >= b })
        }
    }
}
