package com.fluilet.app.data.mapper

import com.fluilet.app.core.network.AppError
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.ComprehensionQuestionDto
import com.fluilet.app.data.remote.dto.DocumentDto
import com.fluilet.app.data.remote.dto.LessonSourceDto
import com.fluilet.app.domain.model.LessonSourceInfo
import com.fluilet.app.data.remote.dto.VocabularyDto
import com.fluilet.app.domain.text.VocabularyEntry
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
 * Métricas de la respuesta: prefiere el objeto `metrics` (puntajes sin redondear de ambos textos) y, si el
 * servidor es anterior, cae a los campos del documento y a los puntajes de nivel superior.
 */
fun AdaptationReadDto.toMetrics(): ReadabilityMetrics {
    val base = document.toMetrics()
    val m = metrics
    if (m == null) {
        val original = originalReadabilityScore?.takeIf { base.originalSzigrisztScore == 0.0 } ?: base.originalSzigrisztScore
        val adapted = readabilityScore?.takeIf { base.adaptedSzigrisztScore == 0.0 } ?: base.adaptedSzigrisztScore
        return base.copy(
            originalSzigrisztScore = original,
            adaptedSzigrisztScore = adapted,
            scoreDelta = if (original != base.originalSzigrisztScore || adapted != base.adaptedSzigrisztScore) adapted - original else base.scoreDelta
        )
    }
    val original = m.originalSzigriszt ?: originalReadabilityScore ?: base.originalSzigrisztScore
    val adapted = m.adaptedSzigriszt ?: readabilityScore ?: base.adaptedSzigrisztScore
    return base.copy(
        originalSzigrisztScore = original,
        adaptedSzigrisztScore = adapted,
        scoreDelta = m.scoreDelta ?: (adapted - original),
        originalWordCount = m.originalWordCount ?: base.originalWordCount,
        adaptedWordCount = m.adaptedWordCount ?: base.adaptedWordCount,
        originalSentenceCount = m.originalSentenceCount ?: base.originalSentenceCount,
        sentenceCount = m.adaptedSentenceCount ?: base.sentenceCount,
        originalAvgWordsPerSentence = m.originalAvgWordsPerSentence ?: 0.0,
        adaptedAvgWordsPerSentence = m.adaptedAvgWordsPerSentence ?: 0.0
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
        metrics = toMetrics(),
        documentId = document.id,
        category = category ?: document.category,
        simplificationLevel = simplificationLevel ?: document.simplificationLevel ?: level.value,
        questions = comprehensionQuestions.orEmpty().mapNotNull { it.toDomain() },
        bestEffort = qualityStatus == "best_effort",
        questionAgeGroup = questionAgeGroup,
        childAgeYears = childAgeYears,
        funFact = funFact?.trim()?.takeIf { it.isNotEmpty() },
        keyVocabulary = keyVocabulary.orEmpty().mapNotNull { it.toDomain() },
        // El original que el servidor retiene (`original_text`): solo se mantiene en RAM para la comparación.
        originalText = originalText?.takeIf { it.isNotBlank() },
        adaptationId = id?.takeIf { it.isNotBlank() },
        targetLevel = targetLevel,
        source = source?.toDomain(),
        meetsTarget = meetsReadabilityTarget,
        topic = resolvedTopic?.takeIf { it.isNotBlank() }
    )
}

private fun LessonSourceDto.toDomain(): LessonSourceInfo? {
    val t = title?.trim().orEmpty()
    val u = url?.trim().orEmpty()
    if (t.isEmpty() && u.isEmpty()) return null
    return LessonSourceInfo(provider.orEmpty().ifBlank { "local" }, t, u, license?.takeIf { it.isNotBlank() })
}

/** Una palabra clave necesita palabra y definición; el ejemplo es opcional. */
fun VocabularyDto.toDomain(): VocabularyEntry? {
    val w = word?.trim().orEmpty()
    val d = definition?.trim().orEmpty()
    if (w.isEmpty() || d.isEmpty()) return null
    return VocabularyEntry(w, d, example?.trim().orEmpty())
}

/** Descarta preguntas mal formadas en lugar de fallar: el mini-reto es un complemento de la lectura. */
fun ComprehensionQuestionDto.toDomain(): ComprehensionQuestion? {
    val text = question?.trim().orEmpty()
    val opts = options.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    val correct = correctOptionIndex ?: return null
    if (text.isEmpty() || opts.size < 2 || correct !in opts.indices) return null
    return ComprehensionQuestion(id ?: "q", text, opts, correct, hint?.trim()?.takeIf { it.isNotEmpty() }, explanation?.trim()?.takeIf { it.isNotEmpty() })
}
