package com.fluilet.app.domain.usecase

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.DifficultyType
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.RegistrationData
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.repository.ChildRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Cuenta de tutor de demostración para la revisión académica. Viene de `BuildConfig` (preconfigurada en
 * debug, desactivada en release salvo que se definan `fluilet.demoEmail` y `fluilet.demoPassword`).
 */
data class DemoConfig(val email: String, val password: String) {
    val enabled: Boolean get() = email.isNotBlank() && password.isNotBlank()

    companion object {
        val DISABLED = DemoConfig("", "")
    }
}

/**
 * "🚀 Entrar en Modo Demo": inicia sesión con la cuenta de demostración (la crea la primera vez como tutor)
 * y deja un perfil de niño listo para cargar los datos demo. Todo contra el backend real.
 */
class EnterDemoModeUseCase @Inject constructor(
    private val config: DemoConfig,
    private val auth: AuthRepository,
    private val children: ChildRepository
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): Result<Unit> {
        if (!config.enabled) return Result.failure(AppError.Unsupported("El Modo Demo no está habilitado en esta versión."))

        var session = auth.login(config.email, config.password)
        if (session.exceptionOrNull() is AppError.Unauthorized) {
            // Primera vez: se crea la cuenta de demostración y se vuelve a entrar.
            val created = auth.register(RegistrationData(config.email, DEMO_NAME, config.password, UserRole.TUTOR))
            created.exceptionOrNull()?.let { return Result.failure(it) }
            session = auth.login(config.email, config.password)
        }
        session.exceptionOrNull()?.let { return Result.failure(it) }

        // Un perfil de ocho años para que "Cargar Datos Demo" tenga a quién cargárselos (si falla, no bloquea el ingreso).
        if (children.list().getOrNull()?.isEmpty() == true) {
            children.create(demoChild(today))
        }
        return Result.success(Unit)
    }

    companion object {
        const val DEMO_NAME = "Profesor Demo"
        const val DEMO_CHILD_NAME = "Mateo"
        const val DEMO_CHILD_AGE_YEARS = 8L

        fun demoChild(today: LocalDate) = ChildDraft(
            name = DEMO_CHILD_NAME,
            birthDate = today.minusYears(DEMO_CHILD_AGE_YEARS).minusMonths(2),
            difficulty = DifficultyType.DISLEXIA,
            nlpLevel = NlpLevel.NIVEL_3,
            palette = AccessiblePalette.SEPIA,
            fontSizeSp = 20f,
            lineSpacing = 1.8f
        )
    }
}
