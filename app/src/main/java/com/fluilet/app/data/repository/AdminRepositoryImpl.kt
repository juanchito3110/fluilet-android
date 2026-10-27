package com.fluilet.app.data.repository

import com.fluilet.app.core.network.apiCall
import com.fluilet.app.data.mapper.toDomain
import com.fluilet.app.data.mapper.toDto
import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.data.remote.dto.ResetPinRequestDto
import com.fluilet.app.domain.model.AdminOverview
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.MasterGrant
import com.fluilet.app.domain.model.PinResetResult
import com.fluilet.app.domain.repository.AdminRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Rutas `/api/v1/admin/...` del backend (docs/demo-accounts.md). La autorización la impone el servidor en cada llamada. */
@Singleton
class AdminRepositoryImpl @Inject constructor(private val api: FluiletApi) : AdminRepository {

    override suspend fun overview(): Result<AdminOverview> =
        apiCall { api.adminOverview() }.mapCatching { it.toDomain() }

    override suspend fun ensureDemoAccounts(): Result<AdminOverview> =
        apiCall { api.adminEnsureDemoAccounts() }.mapCatching { it.toDomain() }

    override suspend fun grant(profileId: String, grant: MasterGrant): Result<EducationalProgress> =
        apiCall { api.adminGrant(profileId, grant.toDto()) }.mapCatching { it.toDomain() }

    override suspend fun deleteProfile(profileId: String): Result<Unit> =
        apiCall { api.adminDeleteProfile(profileId) }

    override suspend fun deleteTutor(userId: String): Result<Unit> =
        apiCall { api.adminDeleteTutor(userId) }

    override suspend fun resetPin(userId: String, pin: String): Result<PinResetResult> =
        apiCall { api.adminResetPin(userId, ResetPinRequestDto(pin)) }
            .map { PinResetResult(it.userId.orEmpty().ifBlank { userId }, it.pinReset) }
}
