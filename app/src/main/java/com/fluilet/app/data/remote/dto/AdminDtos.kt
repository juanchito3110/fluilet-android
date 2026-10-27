package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** `GET /api/v1/admin/overview` (backend >= bac5798). Nunca incluye textos, contraseñas, hashes de PIN ni tokens. */
data class AdminOverviewDto(
    @SerializedName("total_tutors") val totalTutors: Int = 0,
    @SerializedName("total_profiles") val totalProfiles: Int = 0,
    @SerializedName("total_adaptations") val totalAdaptations: Int = 0,
    @SerializedName("total_reading_sessions") val totalReadingSessions: Int = 0,
    @SerializedName("average_comprehension") val averageComprehension: Double = 0.0,
    @SerializedName("users") val users: List<AdminUserDto>? = null,
    @SerializedName("profiles") val profiles: List<AdminProfileDto>? = null
)

data class AdminUserDto(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("is_master_admin") val isMasterAdmin: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("profiles_count") val profilesCount: Int = 0
)

data class AdminProfileDto(
    @SerializedName("profile_id") val profileId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("tutor_id") val tutorId: String? = null,
    @SerializedName("tutor_email") val tutorEmail: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("age_group") val ageGroup: String? = null,
    @SerializedName("current_level") val currentLevel: Int = 1,
    @SerializedName("total_gems") val totalGems: Int = 0,
    @SerializedName("total_xp") val totalXp: Int = 0,
    @SerializedName("evolution_stage") val evolutionStage: Int = 1,
    @SerializedName("evolution_title") val evolutionTitle: String? = null,
    @SerializedName("current_streak_days") val currentStreakDays: Int = 0,
    @SerializedName("completed_lessons_count") val completedLessonsCount: Int = 0,
    @SerializedName("unlocked_badges_count") val unlockedBadgesCount: Int = 0
)

/** `POST /api/v1/admin/profiles/{id}/grant`: los campos `null` se omiten (el backend no admite `null`). */
data class MasterGrantDto(
    @SerializedName("add_gems") val addGems: Int? = null,
    @SerializedName("set_gems") val setGems: Int? = null,
    @SerializedName("add_xp") val addXp: Int? = null,
    @SerializedName("set_evolution_stage") val setEvolutionStage: Int? = null,
    @SerializedName("set_level") val setLevel: Int? = null,
    @SerializedName("set_age_group") val setAgeGroup: String? = null,
    @SerializedName("set_streak_days") val setStreakDays: Int? = null,
    /** Solo se envía cuando es `true`. */
    @SerializedName("unlock_all_rewards") val unlockAllRewards: Boolean? = null
)

/** `POST /api/v1/admin/tutors/{id}/reset-pin`: `pin` de 4 a 6 dígitos (1234 si se omite el cuerpo). */
data class ResetPinRequestDto(@SerializedName("pin") val pin: String = "1234")

data class ResetPinResponseDto(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("pin_reset") val pinReset: Boolean = false
)
