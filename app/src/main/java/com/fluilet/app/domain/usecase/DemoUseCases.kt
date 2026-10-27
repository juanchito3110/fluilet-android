package com.fluilet.app.domain.usecase

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.repository.TutorPinRepository
import javax.inject.Inject

/**
 * Cuentas públicas de la demostración académica que el backend (>= bac5798) crea al arrancar; ver
 * `docs/demo-accounts.md`. El login es el normal (`POST /auth/login`): no existe ninguna puerta trasera.
 */
object DemoAccounts {
    const val TUTOR_EMAIL = "tutor@fluilet.com"
    const val TUTOR_PASSWORD = "Tutor2026!"
    const val TUTOR_PIN = "1234"
    const val ADMIN_EMAIL = "admin@fluilet.com"
    const val ADMIN_PASSWORD = "Master2026!"
}

/** `true` muestra los accesos rápidos de demostración (siempre en debug; en release solo con `-Pfluilet.demoAccounts=true`). */
data class DemoConfig(val enabled: Boolean) {
    companion object {
        val DISABLED = DemoConfig(false)
    }
}

enum class QuickAccess { TUTOR, MASTER }

/**
 * Accesos rápidos del Splash/Login: entra con la cuenta de demostración elegida. Para el tutor deja además
 * su PIN parental (1234) listo en el dispositivo.
 */
class QuickAccessUseCase @Inject constructor(
    private val config: DemoConfig,
    private val auth: AuthRepository,
    private val pin: TutorPinRepository
) {
    suspend operator fun invoke(access: QuickAccess): Result<UserRole> {
        if (!config.enabled) return Result.failure(AppError.Unsupported("Los accesos de demostración no están habilitados en esta versión."))
        val (email, password) = when (access) {
            QuickAccess.TUTOR -> DemoAccounts.TUTOR_EMAIL to DemoAccounts.TUTOR_PASSWORD
            QuickAccess.MASTER -> DemoAccounts.ADMIN_EMAIL to DemoAccounts.ADMIN_PASSWORD
        }
        val session = auth.login(email, password).getOrElse { return Result.failure(it) }
        if (access == QuickAccess.TUTOR) pin.configureDemoPin(DemoAccounts.TUTOR_PIN)
        return Result.success(session.role)
    }
}
