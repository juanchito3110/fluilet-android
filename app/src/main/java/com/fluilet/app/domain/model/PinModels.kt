package com.fluilet.app.domain.model

/**
 * Prueba de que el PIN del tutor fue validado. Solo la implementación de
 * [com.fluilet.app.domain.repository.TutorPinRepository] puede crearla: las operaciones sensibles
 * (salir del Modo Enfoque, cambiar al panel del tutor) exigen una instancia como parámetro, de modo
 * que no se pueda omitir el Muro Parental por descuido.
 */
class PinVerification internal constructor()

sealed interface PinResult {
    data class Success(val proof: PinVerification) : PinResult
    data class Wrong(val remainingAttempts: Int) : PinResult
    data class LockedOut(val retryAfterSeconds: Long) : PinResult
    data object NotConfigured : PinResult
}
