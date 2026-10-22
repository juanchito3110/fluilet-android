package com.fluilet.app.domain.progress

import com.fluilet.app.domain.model.BadgeCatalog
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.ReadingRecord
import java.time.Instant

/**
 * Cálculo local del resumen educativo, espejo de `educational_progress.summarize` del backend. Sirve
 * de respaldo cuando el endpoint de progreso no responde, para que la pantalla nunca quede sin datos.
 */
object EducationalProgressCalculator {

    fun summarize(records: List<ReadingRecord>, baselineLevel: Int = 2): EducationalProgress {
        val sessions = records.sortedWith(compareBy<ReadingRecord> { it.startedAt ?: Instant.EPOCH }.thenBy { it.id })
        val finished = sessions.filter { it.completed }
        val scores = sessions.mapNotNull { it.comprehensionScore }

        val recent = finished.takeLast(3)
        var recommended = baselineLevel
        if (recent.isNotEmpty()) {
            recommended = recent.last().level
            if (recent.all { (it.comprehensionScore ?: -1) >= 50 || it.completionPercent == 100.0 }) {
                recommended = minOf(5, recommended + 1)
            }
        }

        val practicedCategories = sessions
            .filter { it.readingSeconds > 0 || it.completionPercent > 0 || it.questionsAnswered > 0 }
            .map { it.category }
            .toSet()

        val unlocked = listOf(
            finished.isNotEmpty(),
            scores.any { it == 100 },
            finished.size >= 3,
            practicedCategories.size >= 2,
            finished.any { it.level >= 3 }
        )

        return EducationalProgress(
            totalLessons = sessions.size,
            completedLessons = finished.size,
            incompleteLessons = sessions.size - finished.size,
            completionRatePercent = if (sessions.isEmpty()) 0.0 else 100.0 * finished.size / sessions.size,
            totalReadingSeconds = sessions.sumOf { it.readingSeconds },
            averageComprehension = if (scores.isEmpty()) 0.0 else scores.average(),
            recommendedLevel = recommended.coerceIn(1, 5),
            lessons = sessions.mapIndexed { index, s ->
                LessonProgress(
                    lessonNumber = index + 1,
                    readingId = s.id,
                    recordedAt = s.startedAt,
                    category = s.category,
                    level = s.level,
                    completed = s.completed,
                    completionPercent = s.completionPercent.toInt(),
                    comprehensionScore = s.comprehensionScore,
                    durationSeconds = s.readingSeconds,
                    originalScore = s.originalScore,
                    adaptedScore = s.adaptedScore
                )
            },
            badges = BadgeCatalog.all.zip(unlocked) { entry, earned ->
                EducationalBadge(entry.code, entry.title, entry.description, earned)
            }
        )
    }
}
