package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.ShortLessonInfo
import kotlin.math.ceil

/**
 * Lecciones cortas para niños. Antes el servidor adaptaba hasta 1200 palabras de la introducción de Wikipedia, así que
 * un tema como "béisbol" llegaba con 16 bloques. Una lección generada para un niño se queda con sus primeras oraciones
 * completas hasta el tope de su edad (unos 4 a 6 bloques). Ahora el backend ya recorta la fuente con los mismos topes;
 * esto cubre las lecciones guardadas antes de ese cambio y cualquier servidor que no recorte.
 *
 * Todo se recorta en el mismo punto: la lectura, el original y los 5 niveles del comparador (mismo porcentaje del
 * texto, en oraciones completas). Sus métricas Szigriszt-Pazos se vuelven a medir sobre el texto que se muestra y el
 * mini-reto conserva solo las preguntas cuya respuesta sigue en la lectura. Los textos pegados o subidos no se tocan.
 */
object ShortLesson {

    /** Siempre al menos 3 oraciones, aunque la primera sea larga. */
    private const val MIN_SENTENCES = 3

    /** Una lección que sobrepasa su tope por poco se deja completa (no se corta la última oración). */
    private const val TOLERANCE = 1.25

    /** Palabras de la lectura según la edad didáctica del niño. */
    fun wordBudget(ageGroup: AgeGroup): Int = when (ageGroup) {
        AgeGroup.SIX_TO_EIGHT -> 120
        AgeGroup.NINE_TO_ELEVEN -> 180
        AgeGroup.TWELVE_PLUS -> 240
    }

    /** Recorta una lección generada para un niño de [ageGroup]; sin edad (adulto) o ya corta, la deja igual. */
    fun shorten(result: AdaptationResult, ageGroup: AgeGroup?): AdaptationResult {
        if (ageGroup == null || result.shortLesson != null) return result
        val budget = wordBudget(ageGroup)
        if (cutByServer(result.originalText, budget)) return result
        val full = readingText(result)
        val fullWords = SpanishText.wordCount(full)
        if (fullWords <= budget * TOLERANCE) return result
        val shown = cut(full, budget)
        val shownWords = SpanishText.wordCount(shown)
        if (shownWords >= fullWords) return result
        return applyCut(result, shown, ShortLessonInfo(fullWords, shownWords))
    }

    /**
     * El detalle que el lector pide después al servidor (`GET /adaptations/{id}`) trae el original y los niveles
     * completos: se recortan igual que la [lesson] que se está leyendo.
     */
    fun alignDetail(detail: AdaptationResult, lesson: AdaptationResult): AdaptationResult {
        val info = lesson.shortLesson ?: return detail
        return applyCut(detail, readingText(lesson), info)
    }

    /** Primeras oraciones completas de [text] (sin cruzar párrafos a medias) que caben en [maxWords]. */
    fun cut(text: String, maxWords: Int): String =
        keep(text) { words, sentences, next -> sentences >= MIN_SENTENCES && words + next > maxWords }

    /** Primeras oraciones completas de [text] que cubren al menos [fraction] de sus palabras. */
    fun cutFraction(text: String, fraction: Double): String {
        if (fraction >= 1.0) return text
        val target = ceil(SpanishText.wordCount(text) * fraction)
        return keep(text) { words, sentences, _ -> sentences >= 1 && words >= target }
    }

    /** La pregunta sigue teniendo su respuesta (y su fragmento, si lo cita) dentro de [text]. */
    fun grounded(question: ComprehensionQuestion, text: String): Boolean {
        val haystack = " ${plain(text)} "
        val answer = plain(question.options[question.correctOptionIndex].trim().removePrefix("Dice "))
        if (answer.isEmpty() || " $answer " !in haystack) return false
        // "¿qué completa esta idea: «Los equipos tienen ___ jugadores»?": el fragmento completo debe seguir ahí.
        val quoted = Regex("«([^»]*___[^»]*)»").find(question.question)?.groupValues?.get(1) ?: return true
        return " ${plain(quoted.replace("___", " $answer "))} " in haystack
    }

    /**
     * El backend ya recorta la fuente al mismo tope por edad antes de adaptarla (con un mínimo de 3 oraciones), y sus
     * métricas, niveles y preguntas salen de ese extracto: no se vuelve a cortar aunque el Nivel 1 alargue un poco el
     * texto. Solo las lecciones guardadas antes de ese cambio llegan con el original completo.
     */
    private fun cutByServer(original: String?, budget: Int): Boolean {
        if (original.isNullOrBlank()) return false
        return SpanishText.wordCount(original) <= budget * TOLERANCE || SpanishText.sentenceCount(original) <= MIN_SENTENCES
    }

    private fun readingText(result: AdaptationResult): String = result.chunks.joinToString("\n\n") { it.text }

    private fun applyCut(result: AdaptationResult, shown: String, info: ShortLessonInfo): AdaptationResult {
        val level = result.effectiveLevel
        val original = result.originalText?.let { cutFraction(it, info.fraction) }
        // El nivel pedido es la propia lectura: las mismas oraciones, con los párrafos de su texto (no los de los bloques).
        val shownSentences = SpanishText.sentenceCount(shown)
        val previews = result.levelPreviews.map { preview ->
            val text = if (preview.level == level) keep(preview.adaptedText) { _, sentences, _ -> sentences >= shownSentences }
            else cutFraction(preview.adaptedText, info.fraction)
            measured(preview, text)
        }
        val metrics = remeasure(result.metrics, shown, original)
        val haystack = " ${plain(shown)} "
        return result.copy(
            chunks = TextChunker.chunkAdaptedText(shown, NlpLevel.fromValue(level)),
            metrics = metrics,
            questions = result.questions.filter { grounded(it, shown) },
            originalText = original,
            keyVocabulary = result.keyVocabulary.filter { " ${plain(it.word)} " in haystack },
            lexicalReplacements = result.lexicalReplacements.filter { " ${plain(it.adapted)} " in haystack },
            levelPreviews = previews,
            levelProgressionMet = if (previews.size >= 2) previews.zipWithNext().all { (a, b) -> a.readabilityScore > b.readabilityScore }
            else result.levelProgressionMet,
            meetsTarget = previews.firstOrNull { it.level == level }?.inBand ?: result.meetsTarget,
            readabilityGain = null,
            readabilityLevel = Szigriszt.inflesz(metrics.adaptedSzigrisztScore),
            shortLesson = info
        )
    }

    /** Variante recortada, medida en el dispositivo sobre el texto que se muestra. */
    private fun measured(preview: LevelPreview, text: String): LevelPreview {
        val (syllables, words, sentences) = counts(text)
        val score = Szigriszt.score(syllables, words, sentences)
        val haystack = " ${plain(text)} "
        val shortened = preview.copy(
            adaptedText = text,
            readabilityScore = score,
            readabilityLevel = Szigriszt.inflesz(score),
            avgSentenceLength = if (sentences > 0) words.toDouble() / sentences else 0.0,
            syllablesPerWord = if (words > 0) syllables.toDouble() / words else 0.0,
            lexicalReplacements = preview.lexicalReplacements.filter { " ${plain(it.adapted)} " in haystack },
            fromServer = false
        )
        return shortened.copy(targetMet = shortened.inBand)
    }

    /** Métricas de la lectura recortada; las del original solo cambian si se tiene su texto. */
    private fun remeasure(m: ReadabilityMetrics, shown: String, original: String?): ReadabilityMetrics {
        val (syllables, words, sentences) = counts(shown)
        val adaptedScore = Szigriszt.score(syllables, words, sentences)
        val o = original?.let(::counts)
        val originalScore = o?.let { (s, w, f) -> Szigriszt.score(s, w, f) } ?: m.originalSzigrisztScore
        return m.copy(
            adaptedSzigrisztScore = adaptedScore,
            originalSzigrisztScore = originalScore,
            scoreDelta = Szigriszt.round2(adaptedScore - originalScore),
            adaptedWordCount = words,
            syllableCount = syllables,
            sentenceCount = sentences,
            adaptedAvgWordsPerSentence = if (sentences > 0) words.toDouble() / sentences else 0.0,
            adaptedAvgSyllablesPerWord = if (words > 0) syllables.toDouble() / words else 0.0,
            originalWordCount = o?.second ?: m.originalWordCount,
            originalSyllableCount = o?.first ?: m.originalSyllableCount,
            originalSentenceCount = o?.third ?: m.originalSentenceCount,
            originalAvgWordsPerSentence = o?.let { (_, w, f) -> if (f > 0) w.toDouble() / f else 0.0 } ?: m.originalAvgWordsPerSentence,
            originalAvgSyllablesPerWord = o?.let { (s, w, _) -> if (w > 0) s.toDouble() / w else 0.0 } ?: m.originalAvgSyllablesPerWord
        )
    }

    private fun counts(text: String): Triple<Int, Int, Int> =
        Triple(SpanishText.syllableCount(text), SpanishText.wordCount(text), SpanishText.sentenceCount(text).coerceAtLeast(1))

    /** Oraciones completas desde el principio hasta que [stop] (palabras y oraciones ya tomadas, palabras de la siguiente). */
    private fun keep(text: String, stop: (words: Int, sentences: Int, next: Int) -> Boolean): String {
        val kept = mutableListOf<String>()
        var words = 0
        var count = 0
        for (paragraph in TextAdapter.paragraphSentences(TextAdapter.normalize(text))) {
            val current = mutableListOf<String>()
            for (sentence in paragraph) {
                val size = SpanishText.wordCount(sentence)
                if (stop(words, count, size)) {
                    if (current.isNotEmpty()) kept += current.joinToString(" ")
                    return kept.joinToString("\n\n")
                }
                current += sentence
                words += size
                count++
            }
            kept += current.joinToString(" ")
        }
        return kept.joinToString("\n\n")
    }

    /** Minúsculas, solo letras y dígitos separados por un espacio (para buscar palabras y frases completas). */
    private fun plain(text: String): String =
        text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ")
}
