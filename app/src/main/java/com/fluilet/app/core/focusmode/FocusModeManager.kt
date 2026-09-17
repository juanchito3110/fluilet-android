package com.fluilet.app.core.focusmode

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FocusModeManager
 * ----------------
 * Servicio de infraestructura (capa Data/Framework en Clean Architecture) que encapsula
 * los dos mecanismos nativos del "Modo Enfoque" de Fluilet (RF07):
 *
 *  1. Screen Pinning nativo mediante [Activity.startLockTask] / [Activity.stopLockTask].
 *  2. Supresión de notificaciones ("No Molestar") mediante [NotificationManager].
 *
 * Este componente NO decide reglas de negocio (p. ej. "solo el tutor puede salir");
 * expone resultados tipados ([FocusModeResult]) para que el ViewModel de la capa de
 * Presentación aplique esas reglas y actualice el estado de UI (MVVM + StateFlow).
 *
 * Requisito de manifiesto (obligatorio, aunque es un permiso "normal"):
 * ```xml
 * <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
 * ```
 * Sin esta declaración, `isNotificationPolicyAccessGranted()` siempre evaluará como
 * no otorgado y el intent de configuración no tendrá efecto.
 *
 * Notas de seguridad (OWASP MSTG):
 *  - MSTG-PLATFORM-1: se valida el estado del permiso ANTES de cada operación sensible,
 *    nunca se asume que el permiso otorgado en un momento sigue vigente (condición de
 *    carrera: el usuario puede revocarlo manualmente en Ajustes en cualquier momento).
 *  - MSTG-PLATFORM-11 (equivalente UI redress / tapjacking no aplica aquí, pero se evita
 *    cualquier acción irreversible sin confirmación explícita del Tutor).
 *  - Fail-safe: si falla el anclaje de pantalla después de activar el DND, se revierte
 *    el DND para no dejar al dispositivo en un estado a medias.
 */
@Singleton
class FocusModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /** Filtro de interrupciones previo a activar el Modo Enfoque, para poder restaurarlo. */
    private var previousInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL

    // ---------------------------------------------------------------------
    // 1) VALIDACIÓN DE PERMISOS — ACCESS_NOTIFICATION_POLICY
    // ---------------------------------------------------------------------

    /**
     * Verifica si el usuario ya otorgó el acceso a la política de notificaciones.
     * Debe llamarse SIEMPRE antes de intentar cambiar el `interruptionFilter`.
     */
    fun isNotificationPolicyAccessGranted(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted

    /**
     * Construye el Intent que redirige al usuario a la pantalla de sistema donde puede
     * otorgar el acceso a la política de notificaciones. No existe una versión "directa
     * a la app" para este permiso específico (a diferencia de otros ajustes especiales),
     * por lo que se abre el listado general de "Acceso a No Molestar".
     *
     * Debe lanzarse con `registerForActivityResult`, no con `startActivityForResult` legado.
     */
    fun buildNotificationPolicyAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    // ---------------------------------------------------------------------
    // 2) SUPRESIÓN DE NOTIFICACIONES (NotificationManager)
    // ---------------------------------------------------------------------

    /**
     * Activa el modo "No Molestar" priorizando la lección sobre cualquier notificación.
     * Retorna [FocusModeResult.PermissionRequired] si el permiso no ha sido otorgado,
     * en cuyo lugar de fallar silenciosamente o lanzar una excepción no controlada.
     */
    fun enableFocusDoNotDisturb(): FocusModeResult {
        if (!isNotificationPolicyAccessGranted()) {
            return FocusModeResult.PermissionRequired
        }
        return try {
            previousInterruptionFilter = notificationManager.currentInterruptionFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            FocusModeResult.Success
        } catch (e: SecurityException) {
            // El permiso pudo ser revocado entre la verificación y la llamada real.
            FocusModeResult.Error("El permiso de política de notificaciones fue revocado por el usuario.", e)
        }
    }

    /** Restaura el filtro de interrupciones que el dispositivo tenía antes del Modo Enfoque. */
    fun disableFocusDoNotDisturb(): FocusModeResult {
        if (!isNotificationPolicyAccessGranted()) {
            return FocusModeResult.PermissionRequired
        }
        return try {
            notificationManager.setInterruptionFilter(previousInterruptionFilter)
            FocusModeResult.Success
        } catch (e: SecurityException) {
            FocusModeResult.Error("No fue posible restaurar el filtro de interrupciones original.", e)
        }
    }

    // ---------------------------------------------------------------------
    // 3) ANCLAJE DE PANTALLA (Screen Pinning / startLockTask)
    // ---------------------------------------------------------------------

    /** Indica si la pantalla ya está anclada, para evitar llamadas duplicadas a startLockTask(). */
    fun isScreenPinningActive(activity: Activity): Boolean {
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            activityManager.isInLockTaskMode
        }
    }

    /**
     * Inicia el anclaje nativo de pantalla. El sistema mostrará un diálogo de confirmación
     * la primera vez (comportamiento estándar de Screen Pinning, sin requerir Device Owner).
     */
    fun startScreenPinning(activity: Activity): FocusModeResult {
        if (isScreenPinningActive(activity)) {
            return FocusModeResult.Error("El anclaje de pantalla ya se encuentra activo.")
        }
        return try {
            activity.startLockTask()
            FocusModeResult.Success
        } catch (e: IllegalArgumentException) {
            FocusModeResult.Error("La actividad no está permitida para anclaje de pantalla.", e)
        } catch (e: SecurityException) {
            FocusModeResult.Error("El sistema operativo denegó el anclaje de pantalla.", e)
        }
    }

    /**
     * Detiene el anclaje de pantalla. VALIDACIÓN CRÍTICA: esta operación NUNCA debe
     * ejecutarse sin que la capa de Presentación haya validado previamente las
     * credenciales del Tutor (RF07 — "evitar abandono accidental de la sesión por el niño").
     *
     * El flag [tutorCredentialsValidated] es una salvaguarda explícita en la firma del
     * método: obliga a que quien llame a esta función haya resuelto ese requisito de
     * negocio antes; el manager se niega a desanclar si no se declara validado.
     */
    fun stopScreenPinning(activity: Activity, tutorCredentialsValidated: Boolean): FocusModeResult {
        if (!tutorCredentialsValidated) {
            return FocusModeResult.Error(
                "Se requiere validar las credenciales del Tutor antes de salir del Modo Enfoque."
            )
        }
        if (!isScreenPinningActive(activity)) {
            return FocusModeResult.Error("La aplicación no se encuentra actualmente anclada.")
        }
        return try {
            activity.stopLockTask()
            FocusModeResult.Success
        } catch (e: IllegalStateException) {
            FocusModeResult.Error("No fue posible liberar el anclaje de pantalla.", e)
        }
    }

    // ---------------------------------------------------------------------
    // 4) ORQUESTACIÓN — Activar/Desactivar el Modo Enfoque completo
    // ---------------------------------------------------------------------

    /**
     * Activa ambos mecanismos de forma transaccional: si el anclaje de pantalla falla
     * después de haber activado el DND, se revierte el DND (fail-safe) para no dejar
     * el dispositivo en un estado inconsistente a medio configurar.
     */
    fun activateFocusMode(activity: Activity): FocusModeResult {
        val dndResult = enableFocusDoNotDisturb()
        if (dndResult !is FocusModeResult.Success) return dndResult

        val pinResult = startScreenPinning(activity)
        if (pinResult !is FocusModeResult.Success) {
            disableFocusDoNotDisturb() // rollback
            return pinResult
        }
        return FocusModeResult.Success
    }

    /** Desactiva ambos mecanismos; requiere credenciales de Tutor ya validadas aguas arriba. */
    fun deactivateFocusMode(activity: Activity, tutorCredentialsValidated: Boolean): FocusModeResult {
        val stopResult = stopScreenPinning(activity, tutorCredentialsValidated)
        if (stopResult !is FocusModeResult.Success) return stopResult
        return disableFocusDoNotDisturb()
    }
}

/**
 * Resultado tipado y exhaustivo para toda operación del Modo Enfoque.
 * Se usa con `when` exhaustivo (sin rama `else`) en el ViewModel para forzar el manejo
 * explícito de cada caso, evitando estados de UI no contemplados.
 */
sealed class FocusModeResult {
    data object Success : FocusModeResult()
    data object PermissionRequired : FocusModeResult()
    data class Error(val message: String, val cause: Throwable? = null) : FocusModeResult()
}
