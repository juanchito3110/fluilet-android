package com.fluilet.app.data.mapper

import com.fluilet.app.core.network.AppError
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.DocumentDto
import com.fluilet.app.data.remote.dto.ReadingDto
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadabilityMetrics
import com.fluilet.app.domain.model.SessionSummary
import com.fluilet.app.domain.text.Szigriszt
import com.fluilet.app.domain.text.TextChunker
import java.time.Instant
import kotlin.math.roundToInt

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

/** El texto adaptado se parte en tarjetas en el cliente; el servidor solo entrega métricas por bloque. */
fun AdaptationReadDto.toDomain(title: String, level: NlpLevel): AdaptationResult {
    if (adaptedText.isBlank()) throw AppError.Server()
    val chunks = TextChunker.chunkAdaptedText(adaptedText, level)
    if (chunks.isEmpty()) throw AppError.Server()
    return AdaptationResult(title = title, chunks = chunks, metrics = document.toMetrics(), documentId = document.id)
}

/** Une cada lectura con las métricas de su documento (si aún existe). */
fun ReadingDto.toSummary(index: Int, document: DocumentDto?): SessionSummary {
    val metrics = document?.toMetrics()
    return SessionSummary(
        id = id,
        label = "Sesión ${index + 1}",
        startedAt = startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
        readingTimeSeconds = readingTimeSeconds,
        completionPercent = completionPercent.roundToInt().coerceIn(0, 100),
        completed = completed,
        originalScore = metrics?.originalSzigrisztScore ?: 0.0,
        adaptedScore = metrics?.adaptedSzigrisztScore ?: 0.0,
        scoreDelta = metrics?.scoreDelta ?: 0.0
    )
}
