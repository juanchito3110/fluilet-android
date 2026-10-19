package com.fluilet.app.core.focusmode

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anclaje nativo de pantalla (Screen Pinning) con [Activity.startLockTask] / [Activity.stopLockTask].
 *
 * Cumple la Política de Familias de Google Play: NO usa el permiso de "No Molestar"
 * (`ACCESS_NOTIFICATION_POLICY`) ni ningún otro permiso especial. Sin Device Owner el sistema
 * muestra su diálogo de confirmación la primera vez, y el usuario puede salir con el gesto del
 * sistema (Atrás + Recientes): la app no puede impedirlo, pero re-ancla al volver a primer plano
 * (ver MainActivity) y el Muro Parental protege toda salida desde la propia app.
 *
 * La regla de negocio "solo el tutor puede salir" vive en [FocusSessionController.end], que exige
 * una prueba de PIN; este manager solo ejecuta la parte de framework.
 */
@Singleton
class FocusModeManager @Inject constructor() {

    fun isScreenPinningActive(activity: Activity): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun startScreenPinning(activity: Activity): FocusModeResult {
        if (isScreenPinningActive(activity)) return FocusModeResult.Success
        return try {
            activity.startLockTask()
            FocusModeResult.Success
        } catch (e: IllegalArgumentException) {
            FocusModeResult.Error("La actividad no está permitida para anclaje de pantalla.", e)
        } catch (e: SecurityException) {
            FocusModeResult.Error("El sistema operativo denegó el anclaje de pantalla.", e)
        }
    }

    /** Solo debe invocarse cuando el controlador ya autorizó la salida (PIN validado). */
    fun stopScreenPinning(activity: Activity): FocusModeResult {
        if (!isScreenPinningActive(activity)) return FocusModeResult.Success
        return try {
            activity.stopLockTask()
            FocusModeResult.Success
        } catch (e: IllegalStateException) {
            FocusModeResult.Error("No fue posible liberar el anclaje de pantalla.", e)
        }
    }
}

/** Resultado tipado de las operaciones de anclaje. */
sealed class FocusModeResult {
    data object Success : FocusModeResult()
    data class Error(val message: String, val cause: Throwable? = null) : FocusModeResult()
}
