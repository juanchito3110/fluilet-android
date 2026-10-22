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
    @SerializedName("badges") val badges: List<BadgeDto>? = null
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
