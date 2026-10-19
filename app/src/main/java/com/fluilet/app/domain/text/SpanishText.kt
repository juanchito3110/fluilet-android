package com.fluilet.app.domain.text

private val WORD_REGEX = Regex("[\\p{L}]+(?:['’-][\\p{L}]+)*")
private val SENTENCE_END = Regex("(?<=[.!?…])\\s+|\\n{2,}")

private const val STRONG = "aeoáéóàèò"
private const val WEAK = "iuüï"
private const val ACCENTED_WEAK = "íú"
private const val VOWELS = STRONG + WEAK + ACCENTED_WEAK

object SpanishText {

    fun words(text: String): List<String> = WORD_REGEX.findAll(text).map { it.value }.toList()

    fun wordCount(text: String): Int = words(text).size

    /** Divide en oraciones por signos de cierre o por párrafos. Descarta fragmentos vacíos. */
    fun sentences(text: String): List<String> =
        text.trim().split(SENTENCE_END).map { it.trim() }.filter { it.any(Char::isLetterOrDigit) }

    fun sentenceCount(text: String): Int = sentences(text).size

    fun syllableCount(text: String): Int = words(text).sumOf(::syllables)

    /**
     * Cuenta sílabas de una palabra en español con reglas de diptongo/hiato:
     * - dos vocales fuertes (a, e, o) adyacentes forman hiato (2 sílabas);
     * - vocal débil (i, u) + fuerte, o dos débiles, forman diptongo (1 sílaba);
     * - vocal débil acentuada (í, ú) junto a una fuerte rompe el diptongo;
     * - la "u" de que/qui/gue/gui es muda; la "y" final tras vocal es parte del diptongo.
     */
    fun syllables(word: String): Int {
        val w = word.lowercase()
        if (w.isEmpty()) return 0
        if (w == "y") return 1

        val isVowel = BooleanArray(w.length) { i ->
            val c = w[i]
            when {
                c !in VOWELS && !(c == 'y' && i == w.lastIndex && i > 0 && w[i - 1] in VOWELS) -> false
                c == 'u' && i > 0 && i < w.lastIndex && (w[i - 1] == 'q' || w[i - 1] == 'g') && w[i + 1] in "eiéí" -> false
                else -> true
            }
        }

        var count = 0
        var i = 0
        while (i < w.length) {
            if (!isVowel[i]) { i++; continue }
            count++ // inicio de un grupo vocálico
            var j = i + 1
            while (j < w.length && isVowel[j]) {
                if (isHiatus(w[j - 1], w[j])) count++
                j++
            }
            i = j
        }
        return maxOf(count, 1)
    }

    private fun isHiatus(a: Char, b: Char): Boolean {
        val aStrong = a in STRONG
        val bStrong = b in STRONG
        return when {
            aStrong && bStrong -> true
            a in ACCENTED_WEAK && bStrong -> true
            aStrong && b in ACCENTED_WEAK -> true
            else -> false
        }
    }
}
