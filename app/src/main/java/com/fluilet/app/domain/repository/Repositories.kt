package com.fluilet.app.domain.repository

import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.SessionProgress
import com.fluilet.app.domain.model.UserRole

interface ReaderSettingsRepository {
    suspend fun getSettings(): ReadingSettings
    suspend fun saveSettings(settings: ReadingSettings): Result<Unit>
}

interface TutorRepository {
    suspend fun getSessionProgress(childId: String): Result<List<SessionProgress>>
    suspend fun setNlpLevel(childId: String, level: NlpLevel): Result<Unit>
    suspend fun setRoleEnabled(role: UserRole, enabled: Boolean): Result<Unit>
}

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<AuthSession>
}