package com.fluilet.app.data.mapper

import com.fluilet.app.core.network.AppError
import com.fluilet.app.data.remote.dto.AdaptationReadDto
import com.fluilet.app.data.remote.dto.ComprehensionQuestionDto
import com.fluilet.app.data.remote.dto.DocumentDto
import com.fluilet.app.data.remote.dto.LessonSourceDto
import com.fluilet.app.data.remote.dto.LevelPreviewDto
import com.fluilet.app.data.remote.dto.LexicalReplacementDto
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.domain.text.SpanishText
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
fun AdaptationReadDto.toMetrics(): ReadabilityMetrics = baseMetrics().let { b ->
    b.copy(
        originalAvgWordsPerSentence = b.originalAvgWordsPerSentence.takeIf { it > 0 } ?: originalAvgSentenceLength ?: 0.0,
        adaptedAvgWordsPerSentence = b.adaptedAvgWordsPerSentence.takeIf { it > 0 } ?: adaptedAvgSentenceLength ?: 0.0,
        originalAvgSyllablesPerWord = originalSyllablesPerWord ?: 0.0,
        adaptedAvgSyllablesPerWord = adaptedSyllablesPerWord ?: 0.0
    )
}

private fun AdaptationReadDto.baseMetrics(): ReadabilityMetrics {
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
        topic = resolvedTopic?.takeIf { it.isNotBlank() },
        readabilityGain = readabilityGain,
        readabilityLevel = readabilityLevel?.takeIf { it.isNotBlank() },
        lexicalReplacements = lexicalReplacements.orEmpty().mapNotNull { it.toDomain() }.distinct(),
        levelPreviews = levelPreviews.orEmpty().mapNotNull { it.toDomain() }.distinctBy { it.level }.sortedBy { it.level },
        levelProgressionMet = levelProgressionMet,
        modelVersion = modelVersion?.takeIf { it.isNotBlank() },
        checkpointName = checkpointName?.takeIf { it.isNotBlank() },
        engineVersion = engineVersion?.takeIf { it.isNotBlank() }
    )
}

fun LexicalReplacementDto.toDomain(): LexicalReplacement? {
    val from = original?.trim().orEmpty()
    val to = adapted?.trim().orEmpty()
    if (from.isEmpty() || to.isEmpty() || from.equals(to, ignoreCase = true)) return null
    return LexicalReplacement(from, to)
}

/**
 * Una variante del servidor. Si no trae `syllables_per_word` se despeja de su puntaje y longitud de oración
 * (misma fórmula Szigriszt-Pazos del backend); si tampoco hay longitud, se mide el texto en el dispositivo.
 */
fun LevelPreviewDto.toDomain(): LevelPreview? {
    val lvl = level?.takeIf { it in 1..5 } ?: return null
    val text = adaptedText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val score = readabilityScore ?: Szigriszt.score(text)
    val wps = avgSentenceLength?.takeIf { it > 0 } ?: SpanishText.wordCount(text).toDouble() / SpanishText.sentenceCount(text).coerceAtLeast(1)
    val spw = syllablesPerWord?.takeIf { it > 0 }
        ?: if (avgSentenceLength != null && readabilityScore != null) Szigriszt.syllablesPerWord(score, wps)
        else SpanishText.syllableCount(text).toDouble() / SpanishText.wordCount(text).coerceAtLeast(1)
    return LevelPreview(
        level = lvl,
        adaptedText = text,
        readabilityScore = score,
        readabilityLevel = readabilityLevel?.takeIf { it.isNotBlank() } ?: Szigriszt.inflesz(score),
        avgSentenceLength = wps,
        syllablesPerWord = spw,
        targetMet = targetMet ?: false,
        fromServer = true
    )
}

/** Lectura de la biblioteca de un niño: solo metadatos (el texto se pide al abrirla). */
fun DocumentDto.toLibraryItem(): LibraryItem = LibraryItem(
    documentId = id,
    category = category ?: "lectura_libre",
    level = simplificationLevel,
    originalScore = originalSzigrisztScore,
    adaptedScore = adaptedSzigrisztScore
)

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
