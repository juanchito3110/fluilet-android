package com.fluilet.app.data.mapper

import com.fluilet.app.data.remote.dto.EducationalProgressDto
import com.fluilet.app.data.remote.dto.ReadingDto
import com.fluilet.app.domain.model.BadgeCatalog
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.ReadingRecord
import java.time.Instant

private fun parseInstant(raw: String?): Instant? = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** Tolera campos ausentes: las cinco insignias siempre existen (bloqueadas si el servidor no las envía). */
fun EducationalProgressDto.toDomain(): EducationalProgress {
    val serverBadges = badges.orEmpty().filter { !it.code.isNullOrBlank() }.associateBy { it.code }
    return EducationalProgress(
        totalLessons = totalLessons,
        completedLessons = completedLessons,
        incompleteLessons = incompleteLessons,
        completionRatePercent = completionRatePercent.coerceIn(0.0, 100.0),
        totalReadingSeconds = totalReadingSeconds,
        averageComprehension = averageComprehensionScore.coerceIn(0.0, 100.0),
        recommendedLevel = currentRecommendedLevel.coerceIn(1, 5),
        lessons = levelProgression.orEmpty().mapIndexed { index, l ->
            LessonProgress(
                lessonNumber = if (l.lessonNumber > 0) l.lessonNumber else index + 1,
                readingId = l.readingId.orEmpty(),
                recordedAt = parseInstant(l.recordedAt),
                category = l.category ?: "lectura_libre",
                level = (l.simplificationLevel ?: 2).coerceIn(1, 5),
                completed = l.completed,
                completionPercent = l.completionPercentage.coerceIn(0, 100),
                comprehensionScore = l.comprehensionScore,
                durationSeconds = l.durationSeconds,
                originalScore = l.originalReadabilityScore,
                adaptedScore = l.adaptedReadabilityScore
            )
        },
        badges = BadgeCatalog.all.map { entry ->
            val remote = serverBadges[entry.code]
            EducationalBadge(
                code = entry.code,
                title = remote?.title?.takeIf { it.isNotBlank() } ?: entry.title,
                description = remote?.description?.takeIf { it.isNotBlank() } ?: entry.description,
                unlocked = remote?.unlocked ?: false
            )
        }
    )
}

/** Lectura del servidor -> registro mínimo para el cálculo local de respaldo. */
fun ReadingDto.toRecord(): ReadingRecord = ReadingRecord(
    id = id,
    startedAt = parseInstant(startedAt),
    category = category ?: "lectura_libre",
    level = (simplificationLevel ?: 2).coerceIn(1, 5),
    completed = completed,
    completionPercent = completionPercent,
    readingSeconds = readingTimeSeconds,
    comprehensionScore = comprehensionScore,
    questionsAnswered = questionsAnswered ?: 0,
    originalScore = originalSzigrisztScore,
    adaptedScore = adaptedSzigrisztScore
)
