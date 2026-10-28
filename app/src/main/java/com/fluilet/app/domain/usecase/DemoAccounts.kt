package com.fluilet.app.domain.usecase

/**
 * Cuentas de la demostración académica que el backend (>= bac5798) crea al arrancar; ver `docs/demo-accounts.md`.
 * La app NO guarda sus contraseñas ni ofrece atajos: se entra escribiéndolas en el formulario normal de
 * `POST /auth/login`. Solo se conocen los correos (para el respaldo local de los 4 niños) y el PIN parental
 * público del tutor, que el backend también restaura a 1234.
 */
object DemoAccounts {
    const val TUTOR_EMAIL = "tutor@fluilet.com"
    const val TUTOR_PIN = "1234"
    const val ADMIN_EMAIL = "admin@fluilet.com"

    fun isDemoTutor(email: String?): Boolean = email?.trim().equals(TUTOR_EMAIL, ignoreCase = true)
}
