package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.TextChunk

/**
 * Motor local de respaldo (reglas, sin IA) para cuando no hay red: divide oraciones largas, sustituye vocabulario
 * por equivalentes más simples y agrupa en bloques digeribles. Igual que el backend, produce las 5 variantes de la
 * lectura (`level_previews`), la ganancia de legibilidad y los reemplazos léxicos, medidos con las MISMAS métricas
 * Szigriszt-Pazos. Cada nivel usa un vocabulario más amplio que el siguiente y, como paso final, [LevelCalibrator]
 * ajusta la estructura de cada variante a la banda de su nivel con `Nivel 1 > 2 > 3 > 4 > 5` estricto.
 * Trabaja solo en memoria.
 */
object TextAdapter {

    const val ENGINE_VERSION = AdaptationResult.LOCAL_ENGINE_VERSION

    /** Vocabulario de cada nivel: −1 sin cambios léxicos; 0 solo conectores; 1, 2 y 3 cada vez más cotidiano. */
    internal fun lexiconTier(level: NlpLevel): Int = when (level) {
        NlpLevel.NIVEL_1 -> 3
        NlpLevel.NIVEL_2 -> 2
        NlpLevel.NIVEL_3 -> 1
        NlpLevel.NIVEL_4 -> 0
        NlpLevel.NIVEL_5 -> -1
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

    // Solo conjunciones que introducen una cláusula nueva. "y"/"e" no se usan para cortar salvo ante un verbo
    // conjugado inequívoco: unen sujetos u objetos y partir ahí cambiaría el sentido de la oración.
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

    // Pronombres átonos que pueden ir delante del verbo de la segunda cláusula ("... y lo utilizaban para ...").
    private val CLITICS = setOf("se", "lo", "la", "le", "les", "los", "las", "me", "te", "nos")

    // Terminaciones de verbo conjugado sin ambigüedad con sustantivos: pretérito, imperfecto y futuro.
    private val FINITE_VERB = Regex("(aba|aban|ábamos|aron|ieron|yeron|ían|ará|erá|irá|arán|erán|irán|ó)$")

    private fun isGerund(word: String) = word.endsWith("ando") || word.endsWith("iendo") || word.endsWith("yendo")
    private fun isParticiple(word: String) = Regex("(ad|id)(o|a|os|as)$").containsMatchIn(word) && word.length > 4
    private fun isFiniteVerb(word: String) = word.length > 3 && FINITE_VERB.containsMatchIn(word)
    private const val MIN_FRAGMENT_WORDS = 4

    /** Una variante armada por [LevelCalibrator]: su texto, oraciones por párrafo y métricas. */
    internal class Variant(
        val paragraphs: List<List<String>>,
        val text: String,
        val replacements: List<LexicalReplacement>,
        val words: Int,
        val syllables: Int,
        val sentences: Int
    ) {
        val score: Double get() = Szigriszt.score(syllables, words, sentences)
    }

    /**
     * Adapta [original] al [level] pedido y devuelve también las 5 variantes calibradas. [glossary] aporta las
     * micro-glosas de los niveles iniciales (definiciones de términos que aparecen en el texto, nunca relleno) y
     * [aligned], la paráfrasis sencilla revisada de cada oración cuando la lectura viene del banco local.
     */
    fun adapt(
        title: String,
        original: String,
        level: NlpLevel,
        glossary: List<VocabularyEntry> = LocalGlossary.find(original),
        aligned: List<String>? = null
    ): AdaptationResult {
        val plan = LevelCalibrator.calibrate(normalize(original), glossary, aligned)
        val chosen = plan.variants.getValue(level)

        val origSyl = SpanishText.syllableCount(original)
        val origWords = SpanishText.wordCount(original)
        val origSent = SpanishText.sentenceCount(original)

        val originalScore = Szigriszt.score(origSyl, origWords, origSent)
        val adaptedScore = chosen.score
        val chunks = TextChunker.build(chosen.paragraphs, TextChunker.maxWordsFor(level))

        return AdaptationResult(
            title = title,
            chunks = chunks.mapIndexed { index, text -> TextChunk(index, text) },
            metrics = ReadabilityMetrics(
                originalSzigrisztScore = originalScore,
                adaptedSzigrisztScore = adaptedScore,
                scoreDelta = Szigriszt.round2(adaptedScore - originalScore),
                originalWordCount = origWords,
                adaptedWordCount = chosen.words,
                syllableCount = chosen.syllables,
                sentenceCount = chosen.sentences,
                originalSyllableCount = origSyl,
                originalSentenceCount = origSent
            ),
            simplificationLevel = level.value,
            targetLevel = level.value,
            readabilityGain = Szigriszt.round2(adaptedScore - originalScore),
            readabilityLevel = Szigriszt.inflesz(adaptedScore),
            lexicalReplacements = chosen.replacements,
            levelPreviews = plan.previews,
            levelProgressionMet = plan.progressive,
            engineVersion = ENGINE_VERSION
        )
    }

    /** Las 5 variantes calibradas de un texto (Nivel 1 a 5), p. ej. para comparar una lectura que no las trae. */
    fun levelPreviews(
        original: String,
        glossary: List<VocabularyEntry> = LocalGlossary.find(original),
        aligned: List<String>? = null
    ): List<LevelPreview> = LevelCalibrator.calibrate(normalize(original), glossary, aligned).previews

    /** Oraciones de cada párrafo del texto normalizado (punto de partida de todos los borradores). */
    internal fun paragraphSentences(normalized: String): List<List<String>> =
        normalized.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
            .map { SpanishText.sentences(it) }.filter { it.isNotEmpty() }

    /**
     * Borrador con oraciones de hasta [maxWords] palabras (cortando solo en puntos seguros) y el vocabulario del
     * nivel [tier]. [memo] reutiliza cortes y reemplazos entre los borradores de una misma calibración.
     */
    internal fun draft(source: List<List<String>>, maxWords: Int, tier: Int, memo: DraftMemo): Pair<List<List<String>>, List<LexicalReplacement>> {
        val found = LinkedHashMap<String, LexicalReplacement>()
        val paragraphs = source.map { sentences ->
            sentences.flatMap { memo.split(it, maxWords) }
                .map { fragment ->
                    val (text, replaced) = memo.replace(fragment, tier)
                    replaced.forEach { r -> found.putIfAbsent(r.original.lowercase(), r) }
                    text
                }
                .map(::finalizeSentence)
                .filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        return paragraphs to found.values.toList()
    }

    /** Cachés de una calibración: los cortes y reemplazos de una oración no dependen del resto del texto. */
    internal class DraftMemo {
        private val splits = HashMap<Pair<String, Int>, List<String>>()
        private val replaced = HashMap<Pair<String, Int>, Pair<String, List<LexicalReplacement>>>()

        fun split(sentence: String, maxWords: Int): List<String> =
            splits.getOrPut(sentence to maxWords) { splitLongSentence(sentence, maxWords) }

        fun replace(sentence: String, tier: Int): Pair<String, List<LexicalReplacement>> =
            replaced.getOrPut(sentence to tier) { replaceVocabulary(sentence, tier) }
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
                    // ", y también para proteger...": sin la "y" quedaría un fragmento sin verbo.
                    next in DROP_AFTER_COMMA -> if (opensFragment(bare(i + 2), bare(i + 3))) null else Cut(i, 1, 2)
                    next in NO_SENTENCE_START || isGerund(next) || isParticiple(next) -> null
                    else -> Cut(i, 1, 1)
                }
                word in CONJUNCTIONS && !tokens[i - 1].endsWith(',') && bare(i - 1) !in NO_SENTENCE_END -> Cut(i, 0, 0)
                // "... sobre las olas y saludaba a ...": la segunda cláusula tiene su propio verbo conjugado.
                word in DROP_AFTER_COMMA && !tokens[i - 1].endsWith(',') && bare(i - 1) !in NO_SENTENCE_END &&
                    tokens[i + 1].first().isLowerCase() && startsWithFiniteVerb(tokens, i + 1, ::bare) -> Cut(i, 0, 1)
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

    // Adverbios que pueden ir delante de la palabra que de verdad abre la cláusula ("también para...").
    private val LEADING_ADVERBS = setOf("también", "además", "luego", "después", "incluso", "sobre")

    private fun opensFragment(first: String, second: String): Boolean =
        first in NO_SENTENCE_START || isGerund(first) || isParticiple(first) ||
            (first in LEADING_ADVERBS && (second in NO_SENTENCE_START || isGerund(second)))

    /** La cláusula que empieza en [start] abre con un verbo conjugado inequívoco, quizá tras un pronombre átono. */
    private fun startsWithFiniteVerb(tokens: List<String>, start: Int, bare: (Int) -> String): Boolean {
        val first = bare(start)
        if (isFiniteVerb(first)) return true
        return first in CLITICS && start + 1 < tokens.size && isFiniteVerb(bare(start + 1))
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

    private fun replaceVocabulary(sentence: String, tier: Int): Pair<String, List<LexicalReplacement>> {
        val lexicon = LEXICONS[tier] ?: return sentence to emptyList()
        val found = ArrayList<LexicalReplacement>()
        val text = LEXICON_REGEX.getValue(tier).replace(sentence) { match ->
            val base = lexicon[match.value.lowercase()] ?: return@replace match.value
            val replacement = if (match.value.first().isUpperCase()) base.replaceFirstChar(Char::uppercaseChar) else base
            found += LexicalReplacement(match.value, replacement)
            replacement
        }
        return text to found
    }

    private fun finalizeSentence(sentence: String): String {
        var s = sentence.trim().trimEnd(',', ';', ':').trim()
        if (s.isEmpty()) return s
        s = s.replaceFirstChar(Char::uppercaseChar)
        return if (s.last() in ".!?…") s else "$s."
    }
}
