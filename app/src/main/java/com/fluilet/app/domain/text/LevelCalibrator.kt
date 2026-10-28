package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityBand
import kotlin.math.abs

/**
 * Paso final del motor local: calibración determinista por nivel, con el mismo esquema que el backend 42482e0
 * (`calibration.py` y `pedagogy._choose_levels`).
 *
 * 1. Genera formas de la lectura que solo cambian la estructura y el vocabulario del nivel: oraciones cortadas en
 *    puntos seguros con distintos topes de palabras, oraciones completas vecinas unidas en compuestas con «;» o con
 *    un conector («, y», «; además,», «; asimismo,», «; por otra parte,»), un marcador discursivo al inicio de una
 *    oración («Además,», «Asimismo,», «Por otra parte,») y, en los niveles 1 a 3, micro-glosas que definen términos
 *    que aparecen en el propio texto.
 * 2. Mide cada forma con Szigriszt-Pazos sumando las métricas de sus oraciones (no vuelve a recorrer el texto).
 * 3. Elige con programación dinámica las 5 variantes más cercanas a la banda de su nivel con
 *    `Nivel 1 > Nivel 2 > Nivel 3 > Nivel 4 > Nivel 5` estricto (al menos [MIN_GAP] puntos entre niveles cuando el
 *    texto lo permite) y un vocabulario que nunca se simplifica más en un nivel alto que en uno bajo.
 *
 * La banda manda: cada nivel prefiere su propio vocabulario (el Nivel 5 conserva el original) y sus conectores, pero
 * los cambia si así se acerca a su banda. Nunca inventa datos ni relleno. Si el texto no admite 5 formas distintas
 * (p. ej. una sola oración corta sin cortes posibles) devuelve la mejor aproximación con `progressive = false`.
 */
internal object LevelCalibrator {

    /** Resultado: la variante de cada nivel, sus `level_previews` y si la progresión es estricta. */
    class Plan(val variants: Map<NlpLevel, TextAdapter.Variant>, val previews: List<LevelPreview>, val progressive: Boolean)

    /** Palabras por oración que busca cada nivel (`GOALS` del backend). */
    private val LENGTHS = mapOf(1 to 6.0..9.0, 2 to 9.0..12.0, 3 to 11.0..15.0, 4 to 14.0..18.0, 5 to 16.0..24.0)

    private val SPLITS = listOf(5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 21, 25, 30, Int.MAX_VALUE)
    private val PACKS = listOf(14, 18, 22, 26, 30, 36, 42, 50, 64, Int.MAX_VALUE)

    /** Palabras máximas de una oración compuesta en cada nivel: el Nivel 1 nunca une oraciones. */
    private val MAX_JOINED_WORDS = mapOf(1 to 0, 2 to 18, 3 to 26, 4 to 42, 5 to Int.MAX_VALUE)

    /** Conectores que cada nivel puede usar para unir dos oraciones (los formales, solo en los avanzados). */
    private val LINKS = mapOf(
        1 to emptySet(), 2 to emptySet(), 3 to setOf(Link.Y),
        4 to Link.entries.toSet(), 5 to Link.entries.toSet()
    )

    /** Niveles que pueden abrir una oración con un marcador discursivo. */
    private val MARKER_LEVELS = setOf(4, 5)

    /** Las micro-glosas explicativas son de los niveles iniciales. */
    private const val MAX_GLOSS_LEVEL = 3
    private const val GLOSS_CONVENTION_LEVEL = 2

    private const val MAX_GLOSSES = 3
    private const val GLOSS_PENALTY = 0.3
    private const val LINK_PENALTY = 0.25

    // Aceptar más separación entre niveles si cuesta como mucho estos puntos de banda en total.
    private const val GAP_TOLERANCE = 8.0

    // Convenciones de cada nivel: pesan más que la cercanía al centro de la banda y mucho menos que salirse de ella
    // (1 punto fuera de la banda cuesta 1000).
    private const val TIER_STEP_PENALTY = 5.0
    private const val CONVENTION_PENALTY = 3.0

    /** Separación preferida entre niveles vecinos, para que cada variante se note distinta. */
    private const val MIN_GAP = 1.0

    /** Dos puntajes redondeados a centésimas son distintos si difieren al menos 0.01. */
    private const val EPSILON = 0.005

    // Una oración que empieza con una condición no se encadena con un conector ("Si llueve; además, ...").
    private val CONDITIONAL = Regex("^(Si|Aunque|Cuando|Siempre que|Solo si|Mientras)\\b")

    // Una oración que ya abre con un conector no recibe otro marcador.
    private val OPENS_WITH_CONNECTOR = Regex(
        "^(Además|Asimismo|También|Pero|Sin embargo|No obstante|Por otra parte|Por eso|Así|Entonces|Luego|Después|Por último|Al final|Y|E|O)\\b"
    )

    // Palabras funcionales: pasan a minúscula dentro de una oración compuesta aunque las siga un nombre propio.
    private val FUNCTION_WORDS = setOf(
        "El", "La", "Los", "Las", "Un", "Una", "Unos", "Unas", "Ese", "Esa", "Esos", "Esas", "Este", "Esta", "Estos",
        "Estas", "Su", "Sus", "Mi", "Mis", "También", "Después", "Luego", "Entonces", "Así", "En", "Al", "Con", "Por",
        "Para", "Desde", "Durante", "Hoy", "Cada", "No", "Hay", "Es", "Son", "Fue", "Era", "Tiene", "Se", "Lo", "Le",
        "Les", "Ya", "Muchos", "Muchas", "Algunos", "Algunas", "Otros", "Otras", "Todo", "Todos", "Todas", "Pero",
        "Aunque", "Porque", "Sin", "Ahí", "Allí", "Aquí", "Ellos", "Ellas", "Él", "Ella", "Nada", "Nadie"
    )

    /** Marcador que abre una oración (sin unirla a la anterior): baja el puntaje sin cambiar el número de oraciones. */
    enum class Marker(val text: String) {
        ADEMAS("Además, "), ASIMISMO("Asimismo, "), POR_OTRA_PARTE("Por otra parte, ");

        val words: Int = SpanishText.wordCount(text)
        val syllables: Int = SpanishText.syllableCount(text)
    }

    enum class Link(val text: String) {
        Y(", y "), ADEMAS("; además, "), ASIMISMO("; asimismo, "), POR_OTRA_PARTE("; por otra parte, ");

        val words: Int = SpanishText.wordCount(text)
        val syllables: Int = SpanishText.syllableCount(text)
    }

    private class Stats(val words: Int, val syllables: Int, val sentences: Int) {
        val score: Double get() = Szigriszt.score(syllables, words, sentences)
        val wordsPerSentence: Double get() = if (sentences > 0) words.toDouble() / sentences else 0.0

        operator fun plus(o: Stats) = Stats(words + o.words, syllables + o.syllables, sentences + o.sentences)

        companion object {
            val ZERO = Stats(0, 0, 0)
            fun of(text: String) = Stats(SpanishText.wordCount(text), SpanishText.syllableCount(text), SpanishText.sentenceCount(text))
        }
    }

    /** Un borrador: oraciones (cortadas a un tope de palabras) con el vocabulario de un nivel. */
    private class Draft(
        val tier: Int,
        val paragraphs: List<List<String>>,
        val replacements: List<com.fluilet.app.domain.model.LexicalReplacement>,
        val units: List<List<Stats>>
    ) {
        val total: Stats = units.flatten().fold(Stats.ZERO) { a, b -> a + b }
    }

    /**
     * Una forma candidata: un borrador, cómo se agrupan sus oraciones en cada párrafo ([groups] = tamaños de grupo;
     * `null` = sin uniones), el conector de la primera unión y cuántas glosas se añaden. El texto solo se arma para
     * las formas elegidas.
     */
    private class Shape(
        val draft: Draft,
        val groups: List<IntArray>?,
        val link: Link?,
        val glosses: Int,
        val stats: Stats,
        val longestJoin: Int,
        val penalty: Double,
        val marker: Marker? = null,
        /** Párrafo y oración donde va el [marker]. */
        val markerAt: Pair<Int, Int>? = null
    )

    private class Option(val shape: Shape, val score: Double, val cost: Double)

    /**
     * [aligned]: paráfrasis sencilla revisada de cada oración del original (lecciones del banco local, como los
     * planes del catálogo del backend). Si encaja oración por oración, cada nivel puede mezclar oraciones originales y
     * sencillas: primero se simplifican las más difíciles.
     */
    fun calibrate(normalized: String, glossary: List<VocabularyEntry>, aligned: List<String>? = null): Plan {
        val memo = TextAdapter.DraftMemo()
        val unitStats = HashMap<String, Stats>()
        fun stats(unit: String) = unitStats.getOrPut(unit) { Stats.of(unit) }

        val drafts = LinkedHashMap<Pair<Int, List<List<String>>>, Draft>()
        for (source in sources(TextAdapter.paragraphSentences(normalized), aligned)) {
            for (level in NlpLevel.entries) {
                val tier = TextAdapter.lexiconTier(level)
                for (limit in SPLITS) {
                    val (paragraphs, replacements) = TextAdapter.draft(source, limit, tier, memo)
                    drafts.getOrPut(tier to paragraphs) { Draft(tier, paragraphs, replacements, paragraphs.map { p -> p.map(::stats) }) }
                }
            }
        }

        val proper = properNouns(listOf(normalized) + aligned.orEmpty())
        val glosses = glossary.take(MAX_GLOSSES).map(::glossSentence)
        val glossStats = glosses.map(::stats)
        val shapes = drafts.values.flatMap { shapesOf(it, glossStats) }

        val levels = NlpLevel.entries.map { level -> options(shapes, level.value) }
        val spaced = progression(levels, MIN_GAP, orderedVocabulary = true)
        val tight = progression(levels, EPSILON, orderedVocabulary = true)
        // La separación de MIN_GAP se descarta si deja menos niveles en su banda o los aleja demasiado de ella.
        val path = when {
            spaced == null -> tight ?: progression(levels, EPSILON, orderedVocabulary = false)
            tight != null && (inBand(tight) > inBand(spaced) || distance(spaced) > distance(tight) + GAP_TOLERANCE) -> tight
            else -> spaced
        }
        val picks = path ?: levels.map { level -> level.minBy { it.cost } }

        val variants = NlpLevel.entries.zip(picks).associate { (level, option) -> level to materialize(option.shape, glosses, proper) }
        val previews = variants.map { (level, v) -> preview(level, v) }
        return Plan(variants, previews, path != null)
    }

    /** El original y, con una paráfrasis alineada, las mezclas que conservan las `c` oraciones originales más fáciles. */
    private fun sources(original: List<List<String>>, aligned: List<String>?): List<List<List<String>>> {
        val sentences = original.flatten()
        if (aligned == null || aligned.size != sentences.size || aligned.any { it.isBlank() }) return listOf(original)
        val easiestFirst = sentences.indices.sortedByDescending { Szigriszt.score(sentences[it]) }
        return (0..sentences.size).map { kept ->
            val keep = easiestFirst.take(kept).toSet()
            var index = 0
            original.map { paragraph ->
                paragraph.flatMap { sentence ->
                    val i = index++
                    if (i in keep) listOf(sentence) else SpanishText.sentences(aligned[i])
                }
            }
        }
    }

    /** Todas las formas de un borrador: tal cual, con glosas, compactado a varios topes y con un conector. */
    private fun shapesOf(draft: Draft, glosses: List<Stats>): List<Shape> {
        val result = ArrayList<Shape>()
        result += shape(draft, null, null, 0, glosses)
        for (k in 1..glosses.size) result += shape(draft, null, null, k, glosses)

        val packings = PACKS.mapNotNull { limit -> pack(draft, limit) }
            .distinctBy { groups -> groups.joinToString("|") { it.joinToString(",") } }
        for (groups in packings) {
            result += shape(draft, groups, null, 0, glosses)
            Link.entries.forEach { link -> result += shape(draft, groups, link, 0, glosses) }
        }
        firstLinkablePair(draft)?.let { groups -> Link.entries.forEach { link -> result += shape(draft, groups, link, 0, glosses) } }
        firstMarkable(draft)?.let { at ->
            Marker.entries.forEach { marker ->
                val stats = draft.total + Stats(marker.words, marker.syllables, 0)
                result += Shape(draft, null, null, 0, stats, 0, LINK_PENALTY, marker, at)
            }
        }
        return result
    }

    /** La primera oración (que no abre el texto) que puede empezar con un marcador discursivo. */
    private fun firstMarkable(draft: Draft): Pair<Int, Int>? {
        draft.paragraphs.forEachIndexed { p, units ->
            for (i in 1 until units.size) {
                val sentence = units[i]
                if (joinable(units[i - 1], sentence) && !CONDITIONAL.containsMatchIn(sentence) && !OPENS_WITH_CONNECTOR.containsMatchIn(sentence)) {
                    return p to i
                }
            }
        }
        return null
    }

    private fun shape(draft: Draft, groups: List<IntArray>?, link: Link?, glosses: Int, glossStats: List<Stats>): Shape {
        var joins = 0
        var longest = 0
        var linkPending = link
        groups?.forEachIndexed { p, sizes ->
            var cursor = 0
            for (size in sizes) {
                if (size > 1) {
                    joins += size - 1
                    var words = (cursor until cursor + size).sumOf { draft.units[p][it].words }
                    if (linkPending != null) {
                        words += linkPending.words
                        linkPending = null
                    }
                    longest = maxOf(longest, words)
                }
                cursor += size
            }
        }
        var stats = draft.total + Stats(link?.words ?: 0, link?.syllables ?: 0, -joins)
        for (k in 0 until glosses) stats += glossStats[k]
        val penalty = glosses * GLOSS_PENALTY + (if (link != null) LINK_PENALTY else 0.0)
        return Shape(draft, groups, link, glosses, stats, longest, penalty)
    }

    /** Une oraciones completas vecinas (dentro del párrafo) mientras la compuesta no supere [limit] palabras. */
    private fun pack(draft: Draft, limit: Int): List<IntArray>? {
        var joined = false
        val groups = draft.paragraphs.mapIndexed { p, units ->
            val sizes = ArrayList<Int>()
            var size = 1
            var words = draft.units[p][0].words
            for (i in 1 until units.size) {
                val w = draft.units[p][i].words
                if (joinable(units[i - 1], units[i]) && words + w <= limit) {
                    size++
                    words += w
                    joined = true
                } else {
                    sizes += size
                    size = 1
                    words = w
                }
            }
            sizes += size
            sizes.toIntArray()
        }
        return if (joined) groups else null
    }

    /** La primera pareja de oraciones que se puede unir con un conector. */
    private fun firstLinkablePair(draft: Draft): List<IntArray>? {
        draft.paragraphs.forEachIndexed { p, units ->
            for (i in 1 until units.size) {
                if (linkable(units[i - 1], units[i])) {
                    return draft.paragraphs.mapIndexed { q, other ->
                        if (q != p) IntArray(other.size) { 1 }
                        else (0 until other.size - 1).map { if (it == i - 1) 2 else 1 }.toIntArray()
                    }
                }
            }
        }
        return null
    }

    private fun joinable(left: String, right: String): Boolean =
        left.endsWith('.') && !left.endsWith("..") && right.endsWith('.') && right.first() !in "—-«\"'(¿¡"

    private fun linkable(left: String, right: String): Boolean =
        joinable(left, right) && !CONDITIONAL.containsMatchIn(left) && !CONDITIONAL.containsMatchIn(right)

    /** Las opciones de un nivel, una por puntaje y vocabulario (la de menor costo). Las glosas son de N1 a N3. */
    private fun options(shapes: List<Shape>, level: Int): List<Option> {
        val best = LinkedHashMap<Pair<Double, Int>, Option>()
        for (shape in shapes) {
            if (shape.glosses > 0 && level > MAX_GLOSS_LEVEL) continue
            val option = Option(shape, shape.stats.score, cost(shape, level))
            val key = option.score to shape.draft.tier
            val current = best[key]
            if (current == null || option.cost < current.cost) best[key] = option
        }
        return best.values.toList()
    }

    /**
     * Distancia a la banda (dominante), convenciones del nivel (vocabulario, conectores y largo de las compuestas),
     * cercanía al centro de la banda y a la longitud de oración del nivel, más las penalizaciones de la forma.
     */
    private fun cost(shape: Shape, level: Int): Double {
        val band = ReadabilityBand.forLevel(level) ?: return Double.MAX_VALUE
        val score = shape.stats.score
        val center = abs(score - (band.min + band.max) / 2)
        val length = LENGTHS.getValue(level)
        val wps = shape.stats.wordsPerSentence
        val lengthDistance = maxOf(0.0, length.start - wps, wps - length.endInclusive)
        val tier = abs(shape.draft.tier - TextAdapter.lexiconTier(NlpLevel.fromValue(level))) * TIER_STEP_PENALTY
        val link = if (shape.link != null && shape.link !in LINKS.getValue(level)) CONVENTION_PENALTY else 0.0
        val join = if (shape.longestJoin > MAX_JOINED_WORDS.getValue(level)) CONVENTION_PENALTY else 0.0
        val marker = if (shape.marker != null && level !in MARKER_LEVELS) CONVENTION_PENALTY else 0.0
        val gloss = if (shape.glosses > 0 && level > GLOSS_CONVENTION_LEVEL) CONVENTION_PENALTY else 0.0
        return bandDistance(score, level) * 1000 + tier + link + join + marker + gloss + center * 0.01 +
            lengthDistance * 0.002 + shape.penalty
    }

    private fun bandDistance(score: Double, level: Int): Double {
        val band = ReadabilityBand.forLevel(level) ?: return 0.0
        return maxOf(0.0, band.min - score, score - band.max)
    }

    private fun inBand(path: List<Option>): Int = path.withIndex().count { (i, o) -> bandDistance(o.score, i + 1) == 0.0 }

    private fun distance(path: List<Option>): Double = path.withIndex().sumOf { (i, o) -> bandDistance(o.score, i + 1) }

    /**
     * Programación dinámica: el camino de menor costo del Nivel 1 al 5 con puntajes que bajan al menos [gap] puntos
     * de un nivel al siguiente y, si [orderedVocabulary], un vocabulario que nunca es más simple en el nivel siguiente.
     */
    private fun progression(levels: List<List<Option>>, gap: Double, orderedVocabulary: Boolean): List<Option>? {
        class Step(val cost: Double, val path: List<Option>) {
            val last get() = path.last()
        }

        var frontier = levels.first().map { Step(it.cost, listOf(it)) }
        for (k in 1 until levels.size) {
            // Previos por vocabulario, de mayor a menor puntaje, con el mejor costo de cada prefijo.
            val byTier = frontier.groupBy { if (orderedVocabulary) it.last.shape.draft.tier else 0 }.mapValues { (_, steps) ->
                val sorted = steps.sortedByDescending { it.last.score }
                val prefix = ArrayList<Step>(sorted.size)
                sorted.forEach { step -> prefix += prefix.lastOrNull()?.takeIf { it.cost <= step.cost } ?: step }
                sorted to prefix
            }
            frontier = levels[k].mapNotNull { option ->
                val tier = if (orderedVocabulary) option.shape.draft.tier else 0
                byTier.filterKeys { it >= tier }.values.mapNotNull { (sorted, prefix) ->
                    // Los previos que superan a esta opción por al menos `gap` forman un prefijo de `sorted`.
                    var low = 0
                    var high = sorted.size
                    while (low < high) {
                        val mid = (low + high) / 2
                        if (sorted[mid].last.score - option.score >= gap - EPSILON / 2 && sorted[mid].last.score > option.score + EPSILON) low = mid + 1 else high = mid
                    }
                    if (low == 0) null else prefix[low - 1]
                }.minByOrNull { it.cost }?.let { Step(it.cost + option.cost, it.path + option) }
            }
            if (frontier.isEmpty()) return null
        }
        return frontier.minByOrNull { it.cost }?.path
    }

    private fun materialize(shape: Shape, glosses: List<String>, proper: Set<String>): TextAdapter.Variant {
        var linkPending = shape.link
        val paragraphs = shape.draft.paragraphs.mapIndexed { p, original ->
            val units = if (shape.markerAt?.first != p) original
            else original.mapIndexed { i, unit -> if (i == shape.markerAt.second) shape.marker!!.text + lowerFirst(unit, proper) else unit }
            val sizes = shape.groups?.get(p) ?: IntArray(units.size) { 1 }
            var cursor = 0
            sizes.map { size ->
                var sentence = units[cursor]
                for (k in 1 until size) {
                    val connector = linkPending?.text ?: "; "
                    linkPending = null
                    sentence = sentence.dropLast(1) + connector + lowerFirst(units[cursor + k], proper)
                }
                cursor += size
                sentence
            }
        } + listOfNotNull(glosses.take(shape.glosses).takeIf { it.isNotEmpty() })
        val text = paragraphs.joinToString("\n\n") { it.joinToString(" ") }
        val s = shape.stats
        return TextAdapter.Variant(paragraphs, text, shape.draft.replacements, s.words, s.syllables, s.sentences)
    }

    private fun preview(level: NlpLevel, v: TextAdapter.Variant): LevelPreview {
        val score = v.score
        return LevelPreview(
            level = level.value,
            adaptedText = v.text,
            readabilityScore = score,
            readabilityLevel = Szigriszt.inflesz(score),
            avgSentenceLength = if (v.sentences > 0) v.words.toDouble() / v.sentences else 0.0,
            syllablesPerWord = if (v.words > 0) v.syllables.toDouble() / v.words else 0.0,
            targetMet = ReadabilityBand.forLevel(level.value)?.contains(score) == true,
            lexicalReplacements = v.replacements,
            fromServer = false
        )
    }

    /** Micro-glosa de un término del texto: «Gravedad: la fuerza invisible que atrae los objetos.» */
    private fun glossSentence(entry: VocabularyEntry): String {
        val definition = entry.definition.trim().replaceFirstChar(Char::lowercaseChar).trimEnd('.', ' ')
        return "${entry.word.replaceFirstChar(Char::uppercaseChar)}: $definition."
    }

    /** Palabras que el texto escribe con mayúscula en mitad de una oración: nombres propios ("Madrid", "Sagitario"). */
    private fun properNouns(texts: List<String>): Set<String> = texts.flatMap { text ->
        SpanishText.sentences(text).flatMap { sentence -> SpanishText.words(sentence).drop(1).filter { it.first().isUpperCase() } }
    }.toSet()

    /**
     * Primera palabra de una oración que queda dentro de una compuesta (o tras un marcador): pasa a minúscula salvo
     * los nombres propios, las siglas y una palabra con mayúscula seguida de otra ("Santiago Bernabéu").
     */
    private fun lowerFirst(sentence: String, proper: Set<String>): String {
        val words = SpanishText.words(sentence)
        val first = words.firstOrNull() ?: return sentence
        if (!sentence.startsWith(first)) return sentence
        val acronym = first.length > 1 && first.all { it.isUpperCase() }
        val namePair = first !in FUNCTION_WORDS && words.getOrNull(1)?.first()?.isUpperCase() == true
        return if (first in proper || acronym || namePair) sentence else sentence.replaceFirstChar(Char::lowercaseChar)
    }
}
