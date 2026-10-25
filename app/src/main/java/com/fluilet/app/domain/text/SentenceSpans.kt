package com.fluilet.app.domain.text

/**
 * Oraciones de un bloque de texto como rangos de caracteres. La "Guía de renglón" usa estos rangos
 * para resaltar la oración activa y atenuar el resto, facilitando el seguimiento ocular.
 */
object SentenceSpans {

    private val BOUNDARY = Regex("(?<=[.!?…])\\s+|\\n+")

    /** Rangos [inicio, fin] (fin inclusive) sin espacios sobrantes; vacío solo si el texto es vacío. */
    fun compute(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        val spans = mutableListOf<IntRange>()
        var start = 0
        for (match in BOUNDARY.findAll(text)) {
            addSpan(text, start, match.range.first, spans)
            start = match.range.last + 1
        }
        addSpan(text, start, text.length, spans)
        return spans
    }

    private fun addSpan(text: String, from: Int, toExclusive: Int, out: MutableList<IntRange>) {
        var s = from
        var e = toExclusive
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (e > s) out += s until e
    }

    /** Índice de la oración que contiene [offset] (o la más cercana anterior); -1 si no hay oraciones. */
    fun indexAt(spans: List<IntRange>, offset: Int): Int {
        if (spans.isEmpty()) return -1
        val hit = spans.indexOfFirst { offset in it }
        if (hit >= 0) return hit
        val before = spans.indexOfLast { it.first <= offset }
        return if (before >= 0) before else 0
    }
}
