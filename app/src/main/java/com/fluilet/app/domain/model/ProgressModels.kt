package com.fluilet.app.domain.model

import com.fluilet.app.domain.gamification.CollectibleCard
import com.fluilet.app.domain.gamification.DailyMission
import com.fluilet.app.domain.gamification.Gamification
import com.fluilet.app.domain.gamification.GamificationRules
import com.fluilet.app.domain.gamification.PedagogicalSummary
import java.time.Instant

/** Una lección practicada (sesión de lectura) en la evolución del lector. */
data class LessonProgress(
    val lessonNumber: Int,
    val readingId: String,
    val recordedAt: Instant?,
    val category: String,
    val level: Int,
    val completed: Boolean,
    val completionPercent: Int,
    val comprehensionScore: Int?,
    val durationSeconds: Long,
    val originalScore: Double?,
    val adaptedScore: Double?,
    /** Respuestas acertadas del mini-reto, si se conocen (si no, se estiman con el puntaje). */
    val questionsCorrect: Int? = null
) {
    /** Salto de legibilidad (Szigriszt-Pazos) entre el texto original y el adaptado. */
    val readabilityJump: Double?
        get() = if (originalScore != null && adaptedScore != null) adaptedScore - originalScore else null
}

data class EducationalBadge(
    val code: String,
    val title: String,
    val description: String,
    val unlocked: Boolean
)

/** Resumen educativo: cumplimiento de lecciones, tiempo, comprensión, nivel sugerido e insignias. */
data class EducationalProgress(
    val totalLessons: Int,
    val completedLessons: Int,
    val incompleteLessons: Int,
    val completionRatePercent: Double,
    val totalReadingSeconds: Long,
    val averageComprehension: Double,
    val recommendedLevel: Int,
    val lessons: List<LessonProgress>,
    val badges: List<EducationalBadge>,
    /** `true` si se calculó en el dispositivo a partir de las lecturas (respaldo del endpoint de progreso). */
    val isEstimated: Boolean = false,
    // --- Juego educativo (FluiBot): del servidor si lo envía; si no, se calcula en el dispositivo ---
    val totalGems: Int = GamificationRules.WELCOME_GEMS,
    val currentXp: Int = 0,
    val streakDays: Int = 0,
    val pedagogicalSummary: PedagogicalSummary = Gamification.pedagogicalSummary(emptyList(), 0.0, 2, 0.0),
    val cards: List<CollectibleCard> = Gamification.cardsFor(emptyList()),
    val gamificationFromServer: Boolean = false,
    /** Misiones del día que calculó el servidor (`daily_missions`); `null` => se calculan en el dispositivo. */
    val serverMissions: List<DailyMission>? = null
) {
    val hasLessons: Boolean get() = totalLessons > 0

    companion object {
        /** Estado vacío seguro: sin lecciones y con las cinco insignias bloqueadas. */
        fun empty(baselineLevel: Int = 2) = EducationalProgress(
            totalLessons = 0, completedLessons = 0, incompleteLessons = 0, completionRatePercent = 0.0,
            totalReadingSeconds = 0, averageComprehension = 0.0, recommendedLevel = baselineLevel.coerceIn(1, 5),
            lessons = emptyList(), badges = BadgeCatalog.all.map { EducationalBadge(it.code, it.title, it.description, false) }
        )
    }
}

/** Lectura mínima necesaria para calcular el progreso en el dispositivo (sin texto). */
data class ReadingRecord(
    val id: String,
    val startedAt: Instant?,
    val category: String,
    val level: Int,
    val completed: Boolean,
    val completionPercent: Double,
    val readingSeconds: Long,
    val comprehensionScore: Int?,
    val questionsAnswered: Int,
    val originalScore: Double?,
    val adaptedScore: Double?,
    val questionsCorrect: Int? = null
)

/** Insignias educativas (mismos códigos y textos que el backend). */
object BadgeCatalog {
    data class Entry(val code: String, val title: String, val description: String)

    val all = listOf(
        Entry("primera_leccion", "Primera Lección", "Completaste tu primera lectura al 100 %."),
        Entry("mente_brillante", "Mente Brillante", "Obtuviste 100 % en un reto de comprensión."),
        Entry("habito_en_marcha", "Hábito en Marcha", "Completaste 3 o más lecciones de principio a fin."),
        Entry("explorador_curioso", "Explorador Curioso", "Practicaste lecturas de al menos 2 categorías distintas."),
        Entry("subiendo_nivel", "Subiendo de Nivel", "Completaste una lección en nivel 3 o superior.")
    )
}
