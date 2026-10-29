package com.fluilet.app.domain.usecase

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.model.RegistrationData
import com.fluilet.app.domain.model.User
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.repository.TutorPinRepository
import com.fluilet.app.domain.validation.Validators
import javax.inject.Inject

/**
 * Login normal (`POST /auth/login`) para cualquier cuenta, incluidas las de la demostración escritas a mano.
 * Si entra `tutor@fluilet.com`, deja listo en el dispositivo su PIN parental público (1234, el mismo que guarda
 * el backend) para que el Modo Enfoque de sus 4 niños funcione desde el primer momento.
 */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val pin: TutorPinRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthSession> {
        Validators.email(email)?.let { return Result.failure(AppError.Validation(it)) }
        if (password.isEmpty()) return Result.failure(AppError.Validation("Ingresa tu contraseña."))
        return repository.login(email, password).onSuccess { session ->
            if (session.role == UserRole.TUTOR && DemoAccounts.isDemoTutor(session.email)) {
                pin.configureDemoPin(DemoAccounts.TUTOR_PIN)
            }
        }
    }
}

class RegisterUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(data: RegistrationData): Result<User> {
        val error = Validators.fullName(data.fullName)
            ?: Validators.email(data.email)
            ?: Validators.strongPassword(data.password)
        if (error != null) return Result.failure(AppError.Validation(error))
        return repository.register(data)
    }
}

class LogoutUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.logout()
}

/**
 * Restablece el PIN del tutor: exige reautenticar la contraseña contra el backend y solo entonces
 * guarda el nuevo PIN (sal + hash).
 */
class ResetTutorPinUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val pin: TutorPinRepository
) {
    suspend operator fun invoke(password: String, newPin: String): Result<Unit> {
        Validators.tutorPin(newPin)?.let { return Result.failure(AppError.Validation(it)) }
        return auth.reauthenticate(password).mapCatching {
            pin.resetAfterReauthentication(newPin).getOrThrow()
        }
    }
}

/**
 * Recuperación de contraseña en 3 pasos (backend a2dd836): pedir el código de 6 dígitos por correo, verificarlo y
 * fijar la contraseña nueva. Valida en el dispositivo lo mismo que el servidor antes de gastar uno de los 5 intentos.
 */
class PasswordRecoveryUseCase @Inject constructor(private val repository: AuthRepository) {

    suspend fun requestCode(email: String): Result<String> {
        Validators.email(email)?.let { return Result.failure(AppError.Validation(it)) }
        return repository.requestPasswordReset(email)
    }

    suspend fun verifyCode(email: String, code: String): Result<String> {
        Validators.resetCode(code)?.let { return Result.failure(AppError.Validation(it)) }
        return repository.verifyResetCode(email, code)
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<String> {
        val error = Validators.resetCode(code) ?: Validators.strongPassword(newPassword)
        if (error != null) return Result.failure(AppError.Validation(error))
        return repository.resetPassword(email, code, newPassword)
    }
}
