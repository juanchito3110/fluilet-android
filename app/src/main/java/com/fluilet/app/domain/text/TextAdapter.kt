package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityBand
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.TextChunk

/**
 * Motor local de respaldo (reglas, sin IA) para cuando no hay red: divide oraciones largas, sustituye vocabulario
 * por equivalentes más simples y agrupa en bloques digeribles. Igual que el backend, produce las 5 variantes de la
 * lectura (`level_previews`), la ganancia de legibilidad y los reemplazos léxicos, medidos con las MISMAS métricas
 * Szigriszt-Pazos. Cada nivel acorta más las oraciones y aplica un vocabulario más amplio que el siguiente, así que
 * las variantes se diferencian. Trabaja solo en memoria.
 */
object TextAdapter {

    const val ENGINE_VERSION = AdaptationResult.LOCAL_ENGINE_VERSION

    /**
     * [lexiconTier]: −1 sin cambios léxicos; 0 solo conectores; 1, 2 y 3 añaden vocabulario cada vez más cotidiano.
     */
    private data class Profile(val maxWordsPerSentence: Int, val maxWordsPerChunk: Int, val lexiconTier: Int)

    private fun profile(level: NlpLevel) = when (level) {
        NlpLevel.NIVEL_1 -> Profile(8, TextChunker.maxWordsFor(level), 3)
        NlpLevel.NIVEL_2 -> Profile(11, TextChunker.maxWordsFor(level), 2)
        NlpLevel.NIVEL_3 -> Profile(15, TextChunker.maxWordsFor(level), 1)
        NlpLevel.NIVEL_4 -> Profile(21, TextChunker.maxWordsFor(level), 0)
        NlpLevel.NIVEL_5 -> Profile(30, TextChunker.maxWordsFor(level), -1)
    }

    /** Conectores y adverbios formales (desde el Nivel 4): lo mínimo que se simplifica sin tocar el contenido. */
    private val CONNECTORS = mapOf(
        "sin embargo" to "pero", "no obstante" to "pero", "debido a" to "por",
        "con el fin de" to "para", "con la finalidad de" to "para", "en consecuencia" to "por eso",
        "por consiguiente" to "por eso", "asimismo" to "también", "así como" to "y también", "además" to "también",
        "posteriormente" to "después", "previamente" to "antes", "actualmente" to "hoy", "habitualmente" to "normalmente",
        "tradicionalmente" to "por tradición", "únicamente" to "solo", "solamente" to "solo", "probablemente" to "tal vez",
        "frecuentemente" to "a menudo", "aproximadamente" to "más o menos", "generalmente" to "en general",
        "especialmente" to "sobre todo", "principalmente" to "sobre todo", "completamente" to "del todo",
        "totalmente" to "del todo", "finalmente" to "al final", "inicialmente" to "al principio", "posiblemente" to "tal vez",
        "denominado" to "llamado", "denominada" to "llamada", "denominadas" to "llamadas", "denominados" to "llamados"
    )

    /** Verbos y adjetivos académicos frecuentes (desde el Nivel 3). */
    private val COMMON = mapOf(
        "utilizar" to "usar", "utiliza" to "usa", "utilizan" to "usan", "utilizado" to "usado", "utilizada" to "usada",
        "realizar" to "hacer", "realiza" to "hace", "realizan" to "hacen",
        "adquirir" to "conseguir", "obtener" to "conseguir", "obtienen" to "consiguen",
        "comenzar" to "empezar", "comienza" to "empieza", "finalizar" to "terminar", "finaliza" to "termina",
        "numerosos" to "muchos", "numerosas" to "muchas", "diversos" to "varios", "diversas" to "varias",
        "fundamental" to "importante", "fundamentales" to "importantes", "complejo" to "difícil",
        "complejos" to "difíciles", "complejidad" to "dificultad", "mediante" to "con",
        "posee" to "tiene", "poseen" to "tienen", "poseer" to "tener", "observar" to "ver", "observarse" to "verse",
        "comprender" to "entender", "comprenden" to "entienden", "extraordinariamente" to "muy"
    )

    /** Palabras más largas o técnicas (desde el Nivel 2). Solo pares que conservan género y número. */
    private val PLAIN = mapOf(
        "conquistado" to "ganado", "presenciar" to "ver", "acuden" to "van", "concedido" to "dado",
        "laureados" to "premiados", "alberga" to "tiene", "albergan" to "tienen", "establecen" to "crean",
        "transforma" to "cambia", "transforman" to "cambian", "requiere" to "pide", "especialistas" to "expertos",
        "evaluar" to "medir", "instrumentos" to "aparatos", "fundada" to "creada", "fundado" to "creado",
        "amenazadas" to "en peligro", "amenazados" to "en peligro", "intensa" to "fuerte", "intenso" to "fuerte",
        "escapar" to "salir", "cruzado" to "pasado", "región" to "zona", "regiones" to "zonas",
        "desarrolla" to "crea", "desarrollan" to "crean", "principal" to "más importante", "principales" to "más importantes",
        "rápidamente" to "rápido", "lentamente" to "despacio", "considerado" to "visto como", "considerada" to "vista como",
        "investigadores" to "expertos", "provoca" to "causa", "provocan" to "causan"
    )

    /** Equivalentes infantiles (solo el Nivel 1). */
    private val CHILD = mapOf(
        "enorme" to "muy grande", "enormes" to "muy grandes", "extraordinaria" to "muy grande",
        "diminuto" to "muy pequeño", "diminuta" to "muy pequeña", "diminutos" to "muy pequeños", "diminutas" to "muy pequeñas",
        "reducido" to "pequeño", "reducida" to "pequeña", "aficionados" to "fans", "internacionales" to "de otros países",
        "honorífico" to "de honor", "colapsa" to "se hunde", "detectan" to "notan", "organismos" to "seres vivos",
        "correctamente" to "bien", "progreso" to "avance", "cognitivo" to "mental", "configuren" to "ajusten",
        "habitantes" to "seres", "sobreviven" to "viven", "masiva" to "grande", "masivo" to "grande",
        "supermasivo" to "gigante", "concentrando" to "juntando", "permite" to "deja", "permiten" to "dejan",
        "presenta" to "tiene", "presentan" to "tienen", "contiene" to "tiene", "contienen" to "tienen",
        "durante" to "por", "debe" to "tiene que", "deben" to "tienen que"
    )

    /** Léxico acumulado por nivel: el Nivel 1 usa todo; el 5 no cambia palabras. */
    private val LEXICONS: Map<Int, Map<String, String>> = (0..3).associateWith { tier ->
        buildMap {
            putAll(CONNECTORS)
            if (tier >= 1) putAll(COMMON)
            if (tier >= 2) putAll(PLAIN)
            if (tier >= 3) putAll(CHILD)
        }
    }

    private val LEXICON_REGEX: Map<Int, Regex> = LEXICONS.mapValues { (_, lexicon) ->
        Regex(
            lexicon.keys.sortedByDescending { it.length }.joinToString("|", "(?<![\\p{L}])(", ")(?![\\p{L}])") { Regex.escape(it) },
            RegexOption.IGNORE_CASE
        )
    }

    // Solo conjunciones que introducen una cláusula nueva. "y"/"e" no se usan para cortar: unen
    // sujetos u objetos y partir ahí cambiaría el sentido de la oración.
    private val CONJUNCTIONS = setOf("pero", "aunque", "porque", "pues", "sino")
    private val DROP_AFTER_COMMA = setOf("y", "e")

    // Palabras con las que no debe empezar un fragmento (quedaría una oración incompleta).
    private val NO_SENTENCE_START = setOf(
        "que", "quien", "quienes", "cual", "cuales", "cuyo", "cuya", "de", "del", "en", "con", "sin",
        "para", "por", "a", "al", "como", "o", "u", "ni", "se", "lo", "la", "los", "las"
    )

    // Un fragmento no puede terminar en una palabra que pide continuación ("... y que.").
    private val NO_SENTENCE_END = setOf(
        "que", "y", "e", "o", "u", "ni", "de", "del", "en", "con", "a", "al", "el", "la", "los", "las",
        "un", "una", "su", "sus", "por", "para", "sin", "como"
    )

    // Un inciso entre comas que empieza con un nexo ("nada, ni siquiera la luz, puede...") no se cierra con un punto.
    private val INSERT_START = setOf("que", "quien", "quienes", "cual", "cuales", "cuyo", "cuya", "cuyos", "cuyas", "ni", "donde", "como")

    // Determinantes con los que empieza una aposición corta ("Su estadio, el Santiago Bernabéu, ...").
    private val APPOSITION_START = setOf("el", "la", "los", "las", "un", "una")
    private const val MAX_APPOSITION_WORDS = 4
    private val ENUMERATION_LINKS = setOf("y", "e", "o", "u")

    // Nexos de dos palabras que introducen una subordinada: no pueden abrir una oración nueva.
    private val SUBORDINATE_PAIRS = setOf("ya que", "así como", "puesto que", "dado que", "mientras que", "sino que", "de modo que")

    private fun isGerund(word: String) = word.endsWith("ando") || word.endsWith("iendo") || word.endsWith("yendo")
    private fun isParticiple(word: String) = Regex("(ad|id)(o|a|os|as)$").containsMatchIn(word) && word.length > 4
    private const val MIN_FRAGMENT_WORDS = 4

    /** Una variante de la lectura: sus párrafos/oraciones, el texto completo y los reemplazos aplicados. */
    private class Variant(val paragraphs: List<List<String>>, val text: String, val replacements: List<LexicalReplacement>)

    fun adapt(title: String, original: String, level: NlpLevel): AdaptationResult {
        val normalized = normalize(original)
        val variants = NlpLevel.entries.associateWith { variant(normalized, it) }
        val chosen = variants.getValue(level)

        val origSyl = SpanishText.syllableCount(original)
        val origWords = SpanishText.wordCount(original)
        val origSent = SpanishText.sentenceCount(original)
        val adSyl = SpanishText.syllableCount(chosen.text)
        val adWords = SpanishText.wordCount(chosen.text)
        val adSent = chosen.paragraphs.sumOf { it.size }

        val originalScore = Szigriszt.score(origSyl, origWords, origSent)
        val adaptedScore = Szigriszt.score(adSyl, adWords, adSent)
        val previews = variants.map { (lvl, v) -> preview(lvl, v) }
        val chunks = TextChunker.build(chosen.paragraphs, profile(level).maxWordsPerChunk)

        return AdaptationResult(
            title = title,
            chunks = chunks.mapIndexed { index, text -> TextChunk(index, text) },
            metrics = ReadabilityMetrics(
                originalSzigrisztScore = originalScore,
                adaptedSzigrisztScore = adaptedScore,
                scoreDelta = Szigriszt.round2(adaptedScore - originalScore),
                originalWordCount = origWords,
                adaptedWordCount = adWords,
                syllableCount = adSyl,
                sentenceCount = adSent,
                originalSyllableCount = origSyl,
                originalSentenceCount = origSent
            ),
            simplificationLevel = level.value,
            targetLevel = level.value,
            readabilityGain = Szigriszt.round2(adaptedScore - originalScore),
            readabilityLevel = Szigriszt.inflesz(adaptedScore),
            lexicalReplacements = chosen.replacements,
            levelPreviews = previews,
            levelProgressionMet = previews.zipWithNext().all { (a, b) -> a.readabilityScore > b.readabilityScore },
            engineVersion = ENGINE_VERSION
        )
    }

    /** Las 5 variantes de un texto (Nivel 1 a 5), p. ej. para comparar una lectura que no las trae. */
    fun levelPreviews(original: String): List<LevelPreview> {
        val normalized = normalize(original)
        return NlpLevel.entries.map { preview(it, variant(normalized, it)) }
    }

    private fun preview(level: NlpLevel, v: Variant): LevelPreview {
        val words = SpanishText.wordCount(v.text)
        val sentences = v.paragraphs.sumOf { it.size }
        val score = Szigriszt.score(SpanishText.syllableCount(v.text), words, sentences)
        return LevelPreview(
            level = level.value,
            adaptedText = v.text,
            readabilityScore = score,
            readabilityLevel = Szigriszt.inflesz(score),
            avgSentenceLength = if (sentences > 0) words.toDouble() / sentences else 0.0,
            syllablesPerWord = if (words > 0) SpanishText.syllableCount(v.text).toDouble() / words else 0.0,
            targetMet = ReadabilityBand.forLevel(level.value)?.contains(score) == true,
            lexicalReplacements = v.replacements,
            fromServer = false
        )
    }

    private fun variant(normalized: String, level: NlpLevel): Variant {
        val profile = profile(level)
        val found = LinkedHashMap<String, LexicalReplacement>()
        val paragraphs = normalized.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }.map { paragraph ->
            SpanishText.sentences(paragraph)
                .flatMap { splitLongSentence(it, profile.maxWordsPerSentence) }
                .map { sentence -> replaceVocabulary(sentence, profile.lexiconTier, found) }
                .map(::finalizeSentence)
                .filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        return Variant(paragraphs, paragraphs.joinToString("\n\n") { it.joinToString(" ") }, found.values.toList())
    }

    /** Colapsa espacios y saltos preservando los párrafos (líneas en blanco). */
    fun normalize(text: String): String = text
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[ \\t\\u00A0]+"), " ")
        .replace(Regex(" ?\\n ?"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun splitLongSentence(sentence: String, maxWords: Int): List<String> {
        val tokens = sentence.trim().split(' ').filter { it.isNotEmpty() }
        if (tokens.size <= maxWords) return listOf(sentence.trim())

        val cut = bestCut(tokens) ?: return listOf(sentence.trim())
        val left = tokens.subList(0, cut.index + cut.keepLeft).joinToString(" ")
        val right = tokens.subList(cut.index + cut.skipRight, tokens.size).joinToString(" ")
        return splitLongSentence(left, maxWords) + splitLongSentence(right, maxWords)
    }

    private data class Cut(val index: Int, val keepLeft: Int, val skipRight: Int)

    /** Busca el corte (puntuación o conjunción) más cercano al centro dejando fragmentos viables. */
    private fun bestCut(tokens: List<String>): Cut? {
        val middle = tokens.size / 2
        var best: Cut? = null
        var bestDistance = Int.MAX_VALUE

        fun bare(index: Int) = tokens.getOrNull(index)?.trim(',', ';', ':', '.', '¿', '?', '¡', '!', '«', '»', '"')?.lowercase().orEmpty()

        for (i in 1 until tokens.lastIndex) {
            val token = tokens[i]
            val word = bare(i)
            val next = bare(i + 1)
            val cut: Cut = when {
                token.endsWith(',') || token.endsWith(';') || token.endsWith(':') -> when {
                    word in NO_SENTENCE_END -> null
                    insideInsert(tokens, i) -> null
                    "$next ${bare(i + 2)}" in SUBORDINATE_PAIRS -> null
                    // Aposición ("Madrid, España, que...") o inciso que sigue tras la coma.
                    tokens[i + 1].endsWith(',') && (bare(i + 2) in INSERT_START || bare(i + 2) in APPOSITION_START) -> null
                    // "blanco, un color que..." y "Europa, actualmente conocida...": siguen dependiendo de lo anterior.
                    next in APPOSITION_START && bare(i + 3) in INSERT_START -> null
                    next.endsWith("mente") && isParticiple(bare(i + 2)) -> null
                    // Enumeración "coral, fosas abisales y costas rocosas": la lista no se parte.
                    (i + 2..minOf(i + 3, tokens.lastIndex)).any { bare(it) in ENUMERATION_LINKS } -> null
                    next in DROP_AFTER_COMMA -> Cut(i, 1, 2)
                    next in NO_SENTENCE_START || isGerund(next) || isParticiple(next) -> null
                    else -> Cut(i, 1, 1)
                }
                word in CONJUNCTIONS && !tokens[i - 1].endsWith(',') && bare(i - 1) !in NO_SENTENCE_END -> Cut(i, 0, 0)
                else -> null
            } ?: continue

            val leftWords = cut.index + cut.keepLeft
            val rightWords = tokens.size - (cut.index + cut.skipRight)
            if (leftWords < MIN_FRAGMENT_WORDS || rightWords < MIN_FRAGMENT_WORDS) continue

            val distance = kotlin.math.abs(i - middle)
            if (distance < bestDistance) {
                bestDistance = distance
                best = cut
            }
        }
        return best
    }

    /**
     * La coma en [index] cierra un inciso: el tramo desde la coma anterior empieza con un nexo ("ni siquiera la
     * luz,") o es una aposición corta con artículo ("el Santiago Bernabéu,").
     */
    private fun insideInsert(tokens: List<String>, index: Int): Boolean {
        val previousComma = (index - 1 downTo 0).firstOrNull { tokens[it].endsWith(',') } ?: return false
        val first = tokens.getOrNull(previousComma + 1)?.trim(',', '«', '"')?.lowercase() ?: return false
        return first in INSERT_START || isParticiple(first) || (first in APPOSITION_START && index - previousComma <= MAX_APPOSITION_WORDS)
    }

    private fun replaceVocabulary(sentence: String, tier: Int, found: MutableMap<String, LexicalReplacement>): String {
        val lexicon = LEXICONS[tier] ?: return sentence
        return LEXICON_REGEX.getValue(tier).replace(sentence) { match ->
            val base = lexicon[match.value.lowercase()] ?: return@replace match.value
            val replacement = if (match.value.first().isUpperCase()) base.replaceFirstChar(Char::uppercaseChar) else base
            found.getOrPut(match.value.lowercase()) { LexicalReplacement(match.value, replacement) }
            replacement
        }
    }

    private fun finalizeSentence(sentence: String): String {
        var s = sentence.trim().trimEnd(',', ';', ':').trim()
        if (s.isEmpty()) return s
        s = s.replaceFirstChar(Char::uppercaseChar)
        return if (s.last() in ".!?…") s else "$s."
    }
}
