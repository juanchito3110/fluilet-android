package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** `GET /api/v1/readings/progress` (adulto) y `GET /api/v1/children/{id}/progress` (tutor). */
data class EducationalProgressDto(
    @SerializedName("total_lessons") val totalLessons: Int = 0,
    @SerializedName("completed_lessons") val completedLessons: Int = 0,
    @SerializedName("incomplete_lessons") val incompleteLessons: Int = 0,
    @SerializedName("completion_rate_percent") val completionRatePercent: Double = 0.0,
    @SerializedName("total_reading_seconds") val totalReadingSeconds: Long = 0,
    @SerializedName("average_comprehension_score") val averageComprehensionScore: Double = 0.0,
    @SerializedName("current_recommended_level") val currentRecommendedLevel: Int = 2,
    @SerializedName("level_progression") val levelProgression: List<LessonProgressDto>? = null,
    @SerializedName("badges") val badges: List<BadgeDto>? = null,
    // Juego educativo de FluiBot (backend >= app/gamification.py). Todos opcionales: si faltan, la app los calcula.
    @SerializedName(value = "total_gems", alternate = ["gems"]) val totalGems: Int? = null,
    @SerializedName("total_xp") val totalXp: Int? = null,
    @SerializedName("streak_days") val streakDays: Int? = null,
    @SerializedName("current_rank_title") val currentRankTitle: String? = null,
    @SerializedName("mascot") val mascot: FluiBotStateDto? = null,
    @SerializedName("pedagogical_summary") val pedagogicalSummary: PedagogicalSummaryDto? = null,
    @SerializedName(value = "collectible_cards", alternate = ["collectibles", "cards"]) val collectibleCards: List<CollectibleCardDto>? = null,
    @SerializedName(value = "daily_missions", alternate = ["missions"]) val dailyMissions: List<DailyMissionDto>? = null
)

data class DailyMissionDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName(value = "title", alternate = ["name"]) val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName(value = "progress", alternate = ["current"]) val progress: Int? = null,
    @SerializedName("target") val target: Int? = null,
    @SerializedName(value = "reward_gems", alternate = ["reward"]) val rewardGems: Int? = null
)

data class FluiBotStateDto(
    @SerializedName("stage") val stage: Int? = null,
    @SerializedName("stage_name") val stageName: String? = null,
    @SerializedName("stage_description") val stageDescription: String? = null,
    @SerializedName("current_xp") val currentXp: Int? = null,
    @SerializedName("next_stage_xp") val nextStageXp: Int? = null,
    @SerializedName("progress_percent") val progressPercent: Double? = null
)

data class PedagogicalSummaryDto(
    @SerializedName("headline") val headline: String? = null,
    @SerializedName("strength_area") val strengthArea: String? = null,
    @SerializedName("focus_area") val focusArea: String? = null,
    @SerializedName("recommendation") val recommendation: String? = null
)

data class CollectibleCardDto(
    @SerializedName(value = "category", alternate = ["category_code"]) val category: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("rarity") val rarity: String? = null,
    @SerializedName(value = "lessons_completed_in_category", alternate = ["lessons_completed", "completed_lessons"]) val lessonsCompleted: Int? = null,
    @SerializedName("fun_fact") val funFact: String? = null,
    @SerializedName("unlocked") val unlocked: Boolean? = null
)

data class LessonProgressDto(
    @SerializedName("lesson_number") val lessonNumber: Int = 0,
    @SerializedName("reading_id") val readingId: String? = null,
    @SerializedName("recorded_at") val recordedAt: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("simplification_level") val simplificationLevel: Int? = null,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("completion_percentage") val completionPercentage: Int = 0,
    @SerializedName("comprehension_score") val comprehensionScore: Int? = null,
    @SerializedName("duration_seconds") val durationSeconds: Long = 0,
    @SerializedName("original_readability_score") val originalReadabilityScore: Double? = null,
    @SerializedName("adapted_readability_score") val adaptedReadabilityScore: Double? = null
)

data class BadgeDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("unlocked") val unlocked: Boolean = false
)
