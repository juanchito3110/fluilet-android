package com.fluilet.app.data.mapper

import com.fluilet.app.core.network.AppError
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.ComprehensionQuestionDto
import com.fluilet.app.data.remote.dto.DocumentDto
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.ComprehensionQuestion
import com.fluilet.app.domain.text.Szigriszt
import com.fluilet.app.domain.text.TextChunker

fun DocumentDto.toMetrics(): ReadabilityMetrics {
    val original = originalSzigrisztScore ?: 0.0
    val adapted = adaptedSzigrisztScore ?: 0.0
    return ReadabilityMetrics(
        originalSzigrisztScore = original,
        adaptedSzigrisztScore = adapted,
        scoreDelta = scoreDelta ?: Szigriszt.round2(adapted - original),
        originalWordCount = originalWordCount ?: 0,
        adaptedWordCount = adaptedWordCount ?: 0,
        syllableCount = syllableCount ?: 0,
        sentenceCount = sentenceCount ?: 0,
        originalSyllableCount = originalSyllableCount ?: 0,
        originalSentenceCount = originalSentenceCount ?: 0,
        maintenanceException = maintenanceException
    )
}

/**
 * El texto adaptado se parte en tarjetas en el cliente; el servidor solo entrega métricas por bloque.
 * Los campos de lección son opcionales: sin ellos se usan el título/nivel locales y no hay mini-reto.
 */
fun AdaptationReadDto.toDomain(title: String, level: NlpLevel): AdaptationResult {
    if (adaptedText.isBlank()) throw AppError.Server()
    val effectiveLevel = simplificationLevel?.let { NlpLevel.fromValue(it) } ?: level
    val chunks = TextChunker.chunkAdaptedText(adaptedText, effectiveLevel)
    if (chunks.isEmpty()) throw AppError.Server()
    return AdaptationResult(
        title = this.title?.takeIf { it.isNotBlank() } ?: title,
        chunks = chunks,
        metrics = document.toMetrics(),
        documentId = document.id,
        category = category ?: document.category,
        simplificationLevel = simplificationLevel ?: document.simplificationLevel ?: level.value,
        questions = comprehensionQuestions.orEmpty().mapNotNull { it.toDomain() },
        bestEffort = qualityStatus == "best_effort"
    )
}

/** Descarta preguntas mal formadas en lugar de fallar: el mini-reto es un complemento de la lectura. */
fun ComprehensionQuestionDto.toDomain(): ComprehensionQuestion? {
    val text = question?.trim().orEmpty()
    val opts = options.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    val correct = correctOptionIndex ?: return null
    if (text.isEmpty() || opts.size < 2 || correct !in opts.indices) return null
    return ComprehensionQuestion(id ?: "q", text, opts, correct)
}
