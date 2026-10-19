package com.fluilet.app.domain.usecase

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.PinResult
import com.fluilet.app.domain.repository.TutorPinRepository
import com.fluilet.app.domain.validation.Validators
import javax.inject.Inject

class VerifyTutorPinUseCase @Inject constructor(private val repository: TutorPinRepository) {
    operator fun invoke(pin: String): PinResult = repository.verify(pin)
}

class SetupTutorPinUseCase @Inject constructor(private val repository: TutorPinRepository) {
    operator fun invoke(pin: String, confirmation: String): Result<Unit> {
        Validators.tutorPin(pin)?.let { return Result.failure(AppError.Validation(it)) }
        if (pin != confirmation) return Result.failure(AppError.Validation("Los PIN no coinciden."))
        return repository.setInitialPin(pin)
    }
}

class ChangeTutorPinUseCase @Inject constructor(private val repository: TutorPinRepository) {
    operator fun invoke(current: String, new: String, confirmation: String): PinResult {
        Validators.tutorPin(new)?.let { throw AppError.Validation(it) }
        if (new != confirmation) throw AppError.Validation("Los PIN no coinciden.")
        return repository.changePin(current, new)
    }
}

class IsTutorPinConfiguredUseCase @Inject constructor(private val repository: TutorPinRepository) {
    operator fun invoke(): Boolean = repository.isConfigured()
}
