package com.fluilet.app.domain.usecase

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.repository.ChildRepository
import com.fluilet.app.domain.validation.Validators
import javax.inject.Inject

class GetChildrenUseCase @Inject constructor(private val repository: ChildRepository) {
    suspend operator fun invoke(): Result<List<ChildProfile>> = repository.list()
}

class SaveChildUseCase @Inject constructor(private val repository: ChildRepository) {
    /** `id == null` crea un perfil; con id lo actualiza. */
    suspend operator fun invoke(id: String?, draft: ChildDraft): Result<ChildProfile> {
        val error = Validators.childName(draft.name) ?: (if (id == null) Validators.childBirthDate(draft.birthDate) else null)
        if (error != null) return Result.failure(AppError.Validation(error))
        return if (id == null) repository.create(draft) else repository.update(id, draft)
    }
}

class DeleteChildUseCase @Inject constructor(private val repository: ChildRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}

class SetNlpLevelUseCase @Inject constructor(private val repository: ChildRepository) {
    suspend operator fun invoke(childId: String, level: NlpLevel): Result<ChildProfile> {
        if (childId.isBlank()) return Result.failure(AppError.Validation("Selecciona un perfil."))
        return repository.updateNlpLevel(childId, level)
    }
}
