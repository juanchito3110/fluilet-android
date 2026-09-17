package com.fluilet.app.domain.usecase

import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.SessionProgress
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.TutorRepository
import javax.inject.Inject

class GetSessionProgressUseCase @Inject constructor(
    private val repository: TutorRepository
) {
    suspend operator fun invoke(childId: String): Result<List<SessionProgress>> {
        if (childId.isBlank()) return Result.failure(IllegalArgumentException("childId no puede estar vacío"))
        return repository.getSessionProgress(childId)
    }
}

class SetNlpLevelUseCase @Inject constructor(
    private val repository: TutorRepository
) {
    suspend operator fun invoke(childId: String, level: NlpLevel): Result<Unit> {
        if (childId.isBlank()) return Result.failure(IllegalArgumentException("childId no puede estar vacío"))
        return repository.setNlpLevel(childId, level)
    }
}

/** Regla de negocio RBAC: el rol Tutor nunca puede autodesactivarse (perdería el panel de administración). */
class ToggleRbacRoleUseCase @Inject constructor(
    private val repository: TutorRepository
) {
    suspend operator fun invoke(role: UserRole, enabled: Boolean): Result<Unit> {
        if (role == UserRole.TUTOR && !enabled) {
            return Result.failure(IllegalStateException("El rol Tutor no puede desactivarse: perdería el acceso administrativo"))
        }
        return repository.setRoleEnabled(role, enabled)
    }
}