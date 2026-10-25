package com.fluilet.app.data.mapper

import com.fluilet.app.data.remote.dto.CollectibleCardDto
import com.fluilet.app.data.remote.dto.EducationalProgressDto
import com.fluilet.app.data.remote.dto.PedagogicalSummaryDto
import com.fluilet.app.data.remote.dto.ReadingDto
import com.fluilet.app.domain.gamification.CardCatalog
import com.fluilet.app.domain.gamification.CardRarity
import com.fluilet.app.domain.gamification.CollectibleCard
import com.fluilet.app.domain.gamification.Gamification
import com.fluilet.app.domain.gamification.PedagogicalSummary
import com.fluilet.app.domain.gamification.ServerGamification
import com.fluilet.app.domain.model.BadgeCatalog
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.ReadingRecord
import java.time.Instant

private fun parseInstant(raw: String?): Instant? = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** Tolera campos ausentes: las cinco insignias siempre existen (bloqueadas si el servidor no las envía). */
fun EducationalProgressDto.toDomain(): EducationalProgress {
    val base = toBase()
    return Gamification.enrich(base, serverGamification())
}

/** Solo si el servidor envió algún valor de juego; el resto lo calcula la app. */
private fun EducationalProgressDto.serverGamification(): ServerGamification? {
    val summary = pedagogicalSummary?.toDomain()
    val cards = collectibleCards?.mapNotNull { it.toDomain() }
    val xp = totalXp ?: mascot?.currentXp
    if (totalGems == null && xp == null && streakDays == null && summary == null && cards.isNullOrEmpty()) return null
    return ServerGamification(totalGems?.coerceAtLeast(0), xp?.coerceAtLeast(0), streakDays?.coerceAtLeast(0), summary, cards)
}

private fun PedagogicalSummaryDto.toDomain(): PedagogicalSummary? {
    if (headline.isNullOrBlank() && recommendation.isNullOrBlank()) return null
    return PedagogicalSummary(
        headline = headline.orEmpty().ifBlank { "Tu avance lector" },
        strengthArea = strengthArea.orEmpty().ifBlank { "Aún por descubrir" },
        focusArea = focusArea.orEmpty().ifBlank { "Probar nuevas categorías" },
        recommendation = recommendation.orEmpty()
    )
}

private fun CollectibleCardDto.toDomain(): CollectibleCard? {
    val category = LessonCategory.fromCode(category) ?: return null
    val completed = (lessonsCompleted ?: 0).coerceAtLeast(0)
    val rarity = CardRarity.fromWire(rarity) ?: if (unlocked == true || completed > 0) CardRarity.forLessons(maxOf(completed, 1)) else null
    return CollectibleCard(
        categoryCode = category.code,
        title = title?.takeIf { it.isNotBlank() } ?: CardCatalog.titleFor(category),
        rarity = rarity.takeIf { unlocked != false },
        lessonsCompleted = completed,
        funFact = funFact?.takeIf { it.isNotBlank() } ?: CardCatalog.funFactFor(category)
    )
}

private fun EducationalProgressDto.toBase(): EducationalProgress {
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
    adaptedScore = adaptedSzigrisztScore,
    questionsCorrect = questionsCorrect
)
