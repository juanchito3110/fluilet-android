package com.fluilet.app.domain.text

import kotlin.math.round

/**
 * Índice de legibilidad Szigriszt-Pazos (perspicuidad) para español:
 *   IFSZ = 206.835 − 62.3 · (S / P) − (P / F)
 * con S = sílabas, P = palabras, F = frases. Mayor puntaje = más fácil de leer.
 */
object Szigriszt {

    fun score(syllables: Int, words: Int, sentences: Int): Double {
        if (words == 0 || sentences == 0) return 0.0
        val raw = 206.835 - 62.3 * (syllables.toDouble() / words) - (words.toDouble() / sentences)
        return round2(raw)
    }

    fun score(text: String): Double =
        score(SpanishText.syllableCount(text), SpanishText.wordCount(text), SpanishText.sentenceCount(text))

    fun level(score: Double): String = when {
        score < 15 -> "Muy difícil"
        score < 35 -> "Árido"
        score < 50 -> "Bastante difícil"
        score < 65 -> "Normal"
        score < 75 -> "Bastante fácil"
        score < 85 -> "Fácil"
        else -> "Muy fácil"
    }

    fun round2(value: Double): Double = round(value * 100.0) / 100.0
}
