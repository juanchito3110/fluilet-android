package com.fluilet.app.domain.text

import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.TextChunk

/**
 * Adaptación local de respaldo (reglas, sin IA): divide oraciones largas, sustituye vocabulario
 * frecuente por equivalentes más simples y agrupa en bloques digeribles. Calcula las MISMAS
 * métricas Szigriszt-Pazos que expone el backend, para que la app sea funcional de extremo a
 * extremo mientras el motor mT5 del servidor se integra. Trabaja solo en memoria.
 */
object TextAdapter {

    private data class Profile(val maxWordsPerSentence: Int, val maxWordsPerChunk: Int, val replaceVocabulary: Boolean)

    private fun profile(level: NlpLevel) = when (level) {
        NlpLevel.NIVEL_1 -> Profile(9, TextChunker.maxWordsFor(level), true)
        NlpLevel.NIVEL_2 -> Profile(13, TextChunker.maxWordsFor(level), true)
        NlpLevel.NIVEL_3 -> Profile(18, TextChunker.maxWordsFor(level), true)
        NlpLevel.NIVEL_4 -> Profile(25, TextChunker.maxWordsFor(level), false)
        NlpLevel.NIVEL_5 -> Profile(32, TextChunker.maxWordsFor(level), false)
    }

    private val LEXICON = mapOf(
        "utilizar" to "usar", "utiliza" to "usa", "utilizan" to "usan", "utilizado" to "usado",
        "adquirir" to "conseguir", "comenzar" to "empezar", "comienza" to "empieza",
        "finalizar" to "terminar", "finaliza" to "termina", "aproximadamente" to "casi",
        "asimismo" to "también", "además" to "también", "fundamental" to "importante",
        "fundamentales" to "importantes", "complejo" to "difícil", "complejos" to "difíciles",
        "complejidad" to "dificultad", "numerosos" to "muchos", "numerosas" to "muchas",
        "sin embargo" to "pero", "no obstante" to "pero", "debido a" to "por",
        "con el fin de" to "para", "con la finalidad de" to "para", "en consecuencia" to "por eso",
        "por consiguiente" to "por eso", "mediante" to "con", "realizar" to "hacer", "realiza" to "hace", "realizan" to "hacen", "obtener" to "conseguir",
        "denominado" to "llamado", "denominada" to "llamada", "habitualmente" to "casi siempre",
        "posteriormente" to "después", "previamente" to "antes", "diversos" to "varios", "diversas" to "varias"
    )

    private val LEXICON_REGEX = Regex(
        LEXICON.keys.sortedByDescending { it.length }.joinToString("|", "\\b(", ")\\b") { Regex.escape(it) },
        RegexOption.IGNORE_CASE
    )

    // Solo conjunciones que introducen una cláusula nueva. "y"/"e" no se usan para cortar: unen
    // sujetos u objetos y partir ahí cambiaría el sentido de la oración.
    private val CONJUNCTIONS = setOf("pero", "aunque", "porque", "pues", "sino")
    private val DROP_AFTER_COMMA = setOf("y", "e")

    // Palabras con las que no debe empezar un fragmento (quedaría una oración incompleta).
    private val NO_SENTENCE_START = setOf(
        "que", "quien", "quienes", "cual", "cuales", "cuyo", "cuya", "de", "del", "en", "con", "sin",
        "para", "por", "a", "al", "como", "o", "u", "ni", "se", "lo", "la", "los", "las"
    )

    private fun isGerund(word: String) = word.endsWith("ando") || word.endsWith("iendo") || word.endsWith("yendo")
    private const val MIN_FRAGMENT_WORDS = 4

    fun adapt(title: String, original: String, level: NlpLevel): AdaptationResult {
        val profile = profile(level)
        val normalized = normalize(original)

        val paragraphs = normalized.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        val adaptedParagraphs = paragraphs.map { paragraph ->
            SpanishText.sentences(paragraph)
                .flatMap { splitLongSentence(it, profile.maxWordsPerSentence) }
                .map { sentence -> if (profile.replaceVocabulary) replaceVocabulary(sentence) else sentence }
                .map(::finalizeSentence)
        }.filter { it.isNotEmpty() }

        val chunks = TextChunker.build(adaptedParagraphs, profile.maxWordsPerChunk)
        val adaptedText = adaptedParagraphs.joinToString("\n\n") { it.joinToString(" ") }

        val origSyl = SpanishText.syllableCount(original)
        val origWords = SpanishText.wordCount(original)
        val origSent = SpanishText.sentenceCount(original)
        val adSyl = SpanishText.syllableCount(adaptedText)
        val adWords = SpanishText.wordCount(adaptedText)
        val adSent = adaptedParagraphs.sumOf { it.size }

        val originalScore = Szigriszt.score(origSyl, origWords, origSent)
        val adaptedScore = Szigriszt.score(adSyl, adWords, adSent)

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
                sentenceCount = adSent
            )
        )
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

        for (i in 1 until tokens.lastIndex) {
            val token = tokens[i]
            val bare = token.trim(',', ';', ':', '.', '¿', '?', '¡', '!').lowercase()
            val next = tokens[i + 1].trim(',', ';', ':', '.', '¿', '?', '¡', '!').lowercase()
            val cut: Cut = when {
                token.endsWith(',') || token.endsWith(';') || token.endsWith(':') -> when {
                    next in DROP_AFTER_COMMA -> Cut(i, 1, 2)
                    next in NO_SENTENCE_START || isGerund(next) -> null
                    else -> Cut(i, 1, 1)
                }
                bare in CONJUNCTIONS && !tokens[i - 1].endsWith(',') -> Cut(i, 0, 0)
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

    private fun replaceVocabulary(sentence: String): String =
        LEXICON_REGEX.replace(sentence) { match ->
            val replacement = LEXICON[match.value.lowercase()] ?: return@replace match.value
            if (match.value.first().isUpperCase()) replacement.replaceFirstChar(Char::uppercaseChar) else replacement
        }

    private fun finalizeSentence(sentence: String): String {
        var s = sentence.trim().trimEnd(',', ';', ':').trim()
        if (s.isEmpty()) return s
        s = s.replaceFirstChar(Char::uppercaseChar)
        return if (s.last() in ".!?…") s else "$s."
    }


}
