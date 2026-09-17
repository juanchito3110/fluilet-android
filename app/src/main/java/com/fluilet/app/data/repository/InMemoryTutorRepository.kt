package com.fluilet.app.data.repository

import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.SessionProgress
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.TutorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryTutorRepository @Inject constructor() : TutorRepository {

    private val fakeProgress = listOf(
        SessionProgress("Sesión 1", accumulatedMinutes = 12f, completionPercent = 60),
        SessionProgress("Sesión 2", accumulatedMinutes = 20f, completionPercent = 85),
        SessionProgress("Sesión 3", accumulatedMinutes = 28f, completionPercent = 100)
    )

    override suspend fun getSessionProgress(childId: String): Result<List<SessionProgress>> =
        Result.success(fakeProgress)

    override suspend fun setNlpLevel(childId: String, level: NlpLevel): Result<Unit> =
        Result.success(Unit)

    override suspend fun setRoleEnabled(role: UserRole, enabled: Boolean): Result<Unit> =
        Result.success(Unit)
}