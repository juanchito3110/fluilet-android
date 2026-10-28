package com.fluilet.app.data.mapper

import com.fluilet.app.data.remote.dto.AdminOverviewDto
import com.fluilet.app.data.remote.dto.AdminProfileDto
import com.fluilet.app.data.remote.dto.AdminUserDto
import com.fluilet.app.data.remote.dto.MasterGrantDto
import com.fluilet.app.domain.model.AdminOverview
import com.fluilet.app.domain.model.AdminProfile
import com.fluilet.app.domain.model.AdminUser
import com.fluilet.app.domain.model.MasterGrant

fun AdminOverviewDto.toDomain() = AdminOverview(
    totalTutors = totalTutors,
    totalProfiles = totalProfiles,
    totalAdaptations = totalAdaptations,
    totalReadingSessions = totalReadingSessions,
    averageComprehension = averageComprehension.coerceIn(0.0, 100.0),
    users = users.orEmpty().mapNotNull { it.toDomain() },
    profiles = profiles.orEmpty().mapNotNull { it.toDomain() },
    modelAudit = modelInfo?.takeIf { it.modelVersion != null }?.toDomain()
)

private fun AdminUserDto.toDomain(): AdminUser? {
    val id = userId?.takeIf { it.isNotBlank() } ?: return null
    return AdminUser(id, email.orEmpty(), role.orEmpty(), isMasterAdmin, isActive, profilesCount)
}

private fun AdminProfileDto.toDomain(): AdminProfile? {
    val id = profileId?.takeIf { it.isNotBlank() } ?: return null
    return AdminProfile(
        profileId = id,
        name = name.orEmpty().ifBlank { "Sin nombre" },
        tutorId = tutorId.orEmpty(),
        tutorEmail = tutorEmail.orEmpty(),
        isActive = isActive,
        ageGroup = ageGroup.orEmpty().ifBlank { "6-8" },
        currentLevel = currentLevel.coerceIn(1, 5),
        totalGems = totalGems.coerceAtLeast(0),
        totalXp = totalXp.coerceAtLeast(0),
        evolutionStage = evolutionStage.coerceIn(1, 4),
        evolutionTitle = evolutionTitle.orEmpty(),
        streakDays = currentStreakDays.coerceAtLeast(0),
        completedLessons = completedLessonsCount.coerceAtLeast(0),
        unlockedBadges = unlockedBadgesCount.coerceAtLeast(0)
    )
}

fun MasterGrant.toDto() = MasterGrantDto(
    addGems = addGems,
    setGems = setGems,
    addXp = addXp,
    setEvolutionStage = setEvolutionStage,
    setLevel = setLevel,
    setAgeGroup = setAgeGroup,
    setStreakDays = setStreakDays,
    unlockAllRewards = if (unlockAllRewards) true else null
)
