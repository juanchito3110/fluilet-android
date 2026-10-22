package com.fluilet.app.domain.usecase

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingProgress
import com.fluilet.app.domain.text.SpanishText
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.repository.AdaptationRepository
import com.fluilet.app.domain.repository.ReadingSessionRepository
import javax.inject.Inject

class AdaptTextUseCase @Inject constructor(private val repository: AdaptationRepository) {
    suspend operator fun invoke(source: AdaptationSource, level: NlpLevel, childId: String?): Result<AdaptationResult> {
        if (source.bytes.isEmpty()) return Result.failure(AppError.Validation("El documento está vacío."))
        if (source.bytes.size > AdaptationSource.MAX_BYTES) {
            return Result.failure(AppError.TooLarge())
        }
        if (source.type != com.fluilet.app.domain.model.SourceType.PDF &&
            SpanishText.wordCount(String(source.bytes, Charsets.UTF_8)) < AdaptationSource.MIN_WORDS
        ) {
            return Result.failure(AppError.Validation("El texto debe tener al menos ${AdaptationSource.MIN_WORDS} palabras."))
        }
        return repository.adapt(source, level, childId)
    }
}

class StartReadingSessionUseCase @Inject constructor(private val repository: ReadingSessionRepository) {
    suspend operator fun invoke(childId: String?, adaptation: com.fluilet.app.domain.model.AdaptationResult): Result<String?> =
        repository.start(childId, adaptation)
}

class UpdateReadingProgressUseCase @Inject constructor(private val repository: ReadingSessionRepository) {
    suspend operator fun invoke(readingId: String, progress: ReadingProgress): Result<Unit> =
        repository.progress(
            readingId,
            progress.copy(
                readingTimeSeconds = progress.readingTimeSeconds.coerceAtLeast(0),
                completionPercent = progress.completionPercent.coerceIn(0, 100)
            )
        )
}

/** Nunca falla hacia la UI: si no hay datos ni respaldo devuelve un resumen vacío con insignias bloqueadas. */
class GetEducationalProgressUseCase @Inject constructor(private val repository: ReadingSessionRepository) {
    suspend operator fun invoke(childId: String?, baselineLevel: Int = 2): Result<EducationalProgress> =
        repository.educationalProgress(childId, baselineLevel)
}

class GenerateLessonUseCase @Inject constructor(private val repository: AdaptationRepository) {
    suspend operator fun invoke(request: LessonRequest, childId: String?): Result<AdaptationResult> {
        val topic = request.cleanTopic
        if (request.topic != null && request.topic.isNotBlank() && topic == null) {
            return Result.failure(AppError.Validation("El tema contiene caracteres no válidos."))
        }
        return repository.generate(request.copy(topic = topic), childId)
    }
}

/** Cada actualización ajusta el valor a su rango: nunca confiamos en el valor crudo de la UI. */
class UpdateReadingSettingsUseCase @Inject constructor() {
    fun fontSize(current: ReadingSettings, value: Float) =
        ReadingSettings.coerced(value, current.lineSpacing, current.letterSpacingEm, current.speechRate, current.palette)

    fun lineSpacing(current: ReadingSettings, value: Float) =
        ReadingSettings.coerced(current.fontSizeSp, value, current.letterSpacingEm, current.speechRate, current.palette)

    fun letterSpacing(current: ReadingSettings, value: Float) =
        ReadingSettings.coerced(current.fontSizeSp, current.lineSpacing, value, current.speechRate, current.palette)

    fun speechRate(current: ReadingSettings, value: Float) =
        ReadingSettings.coerced(current.fontSizeSp, current.lineSpacing, current.letterSpacingEm, value, current.palette)

    fun palette(current: ReadingSettings, value: AccessiblePalette) = current.copy(palette = value)
}
