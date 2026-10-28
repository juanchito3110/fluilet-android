package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.LexicalReplacement

/**
 * Reemplazos entre un original y una de sus variantes, con el mismo criterio que `pedagogy.replacements` del
 * backend: se alinean las palabras (subsecuencia común más larga) y se devuelven los bloques sustituidos cortos
 * (hasta 12 palabras originales y 16 adaptadas) que cambian algo más que mayúsculas. Sirve para mostrar las
 * transformaciones de los niveles que el servidor no detalla. Textos enormes se omiten (sin coste en RAM).
 */
object LexicalDiff {

    private val WORD = Regex("\\d+(?:[.,]\\d+)*|\\p{L}+(?:['’]\\p{L}+)*")
    private const val MAX_ORIGINAL_WORDS = 12
    private const val MAX_ADAPTED_WORDS = 16
    private const val MAX_CELLS = 1_000_000L
    const val LIMIT = 80

    fun words(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

    /**
     * Rangos de [text] donde aparece alguno de [terms] como palabra o frase completa (sin distinguir mayúsculas):
     * sirven para resaltar en el comparador lo que el motor cambió. Primero los términos más largos.
     */
    fun spans(text: String, terms: Collection<String>, maxTerms: Int = 60): List<IntRange> {
        val clean = terms.map { it.trim() }.filter { it.length >= 2 }.distinct().sortedByDescending { it.length }.take(maxTerms)
        if (clean.isEmpty() || text.isEmpty()) return emptyList()
        val regex = Regex(clean.joinToString("|", "(?<![\\p{L}\\d])(?:", ")(?![\\p{L}\\d])") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.range }.toList()
    }

    fun replacements(source: String, target: String, limit: Int = LIMIT): List<LexicalReplacement> {
        val a = words(source)
        val b = words(target)
        if (a.isEmpty() || b.isEmpty() || a.size.toLong() * b.size > MAX_CELLS) return emptyList()

        // lcs[i][j] = longitud de la subsecuencia común de a[i..] y b[j..] (tabla plana para no fragmentar memoria).
        val width = b.size + 1
        val lcs = IntArray((a.size + 1) * width)
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lcs[i * width + j] = if (a[i] == b[j]) lcs[(i + 1) * width + j + 1] + 1
                else maxOf(lcs[(i + 1) * width + j], lcs[i * width + j + 1])
            }
        }

        val result = mutableListOf<LexicalReplacement>()
        var i = 0
        var j = 0
        var gapA = 0
        var gapB = 0
        fun flush() {
            val removed = i - gapA
            val added = j - gapB
            if (removed in 1..MAX_ORIGINAL_WORDS && added in 1..MAX_ADAPTED_WORDS) {
                val original = a.subList(gapA, i).joinToString(" ")
                val adapted = b.subList(gapB, j).joinToString(" ")
                if (!original.equals(adapted, ignoreCase = true)) result += LexicalReplacement(original, adapted)
            }
        }
        while (i < a.size && j < b.size && result.size < limit) {
            when {
                a[i] == b[j] -> {
                    flush()
                    i++; j++
                    gapA = i; gapB = j
                }
                lcs[(i + 1) * width + j] >= lcs[i * width + j + 1] -> i++
                else -> j++
            }
        }
        if (result.size < limit) {
            i = a.size
            j = b.size
            flush()
        }
        return result.take(limit)
    }
}
