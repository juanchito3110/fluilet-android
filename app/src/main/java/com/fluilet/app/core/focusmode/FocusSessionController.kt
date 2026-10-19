package com.fluilet.app.core.focusmode

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.fluilet.app.domain.model.PinVerification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class FocusSessionState(
    val isActive: Boolean = false,
    /** `true` cuando la sesión es de un niño: exige Screen Pinning y PIN del tutor para salir. */
    val lockRequested: Boolean = false,
    val elapsedSeconds: Long = 0,
    val childId: String? = null
)

/** Datos de una sesión cerrada, para la telemetría numérica. */
data class FocusSessionSummary(
    val startedAt: Instant,
    val endedAt: Instant,
    val elapsedSeconds: Long,
    val childId: String?
)

sealed interface FocusEndResult {
    data class Ended(val summary: FocusSessionSummary) : FocusEndResult
    /** Sesión con Modo Enfoque: se requiere la prueba de PIN del tutor. */
    data object PinRequired : FocusEndResult
    data object NotActive : FocusEndResult
}

/** Abstracción del arranque/parada del Foreground Service (permite probar el controlador sin Android). */
interface FocusServiceLauncher {
    fun start()
    fun stop()
}

class AndroidFocusServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : FocusServiceLauncher {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, FocusSessionService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, FocusSessionService::class.java))
    }
}

/**
 * Orquesta la sesión de lectura: cronómetro monotónico (inmune a cambios de hora), servicio en
 * primer plano que mantiene viva la sesión y bandera de anclaje de pantalla que la Activity
 * traduce a `startLockTask()` / `stopLockTask()`.
 *
 * Invariante del Muro Parental: una sesión con [FocusSessionState.lockRequested] solo termina
 * con una [PinVerification] válida ([end]). La única vía sin PIN es [abortForTeardown], reservada
 * para cierre de proceso/Activity (el ViewModel lo usa para no perder la telemetría).
 */
@Singleton
class FocusSessionController internal constructor(
    private val launcher: FocusServiceLauncher,
    private val monotonicClock: () -> Long,
    private val wallClock: () -> Instant
) {
    @Inject
    constructor(launcher: AndroidFocusServiceLauncher) : this(
        launcher,
        { SystemClock.elapsedRealtime() },
        { Instant.now() }
    )

    private val lock = Any()
    private var startedAtMonotonic = 0L
    private var startedAtWall: Instant = Instant.EPOCH

    private val _state = MutableStateFlow(FocusSessionState())
    val state: StateFlow<FocusSessionState> = _state.asStateFlow()

    fun begin(childId: String?, lockTask: Boolean) {
        synchronized(lock) {
            if (_state.value.isActive) return
            startedAtMonotonic = monotonicClock()
            startedAtWall = wallClock()
            _state.value = FocusSessionState(isActive = true, lockRequested = lockTask, childId = childId)
        }
        launcher.start()
    }

    /** Llamado por el servicio cada segundo. */
    fun tick() {
        _state.update { current ->
            if (!current.isActive) current else current.copy(elapsedSeconds = elapsedNow())
        }
    }

    fun end(proof: PinVerification?): FocusEndResult = synchronized(lock) {
        val current = _state.value
        if (!current.isActive) return FocusEndResult.NotActive
        if (current.lockRequested && proof == null) return FocusEndResult.PinRequired
        FocusEndResult.Ended(finish(current))
    }

    /**
     * Cierre incondicional por destrucción de la app/Activity. No es una salida del usuario: solo
     * libera recursos y devuelve el resumen para no perder la telemetría.
     */
    fun abortForTeardown(): FocusSessionSummary? = synchronized(lock) {
        val current = _state.value
        if (!current.isActive) null else finish(current)
    }

    private fun finish(current: FocusSessionState): FocusSessionSummary {
        val summary = FocusSessionSummary(
            startedAt = startedAtWall,
            endedAt = wallClock(),
            elapsedSeconds = elapsedNow(),
            childId = current.childId
        )
        _state.value = FocusSessionState()
        launcher.stop()
        return summary
    }

    private fun elapsedNow(): Long = ((monotonicClock() - startedAtMonotonic) / 1000).coerceAtLeast(0)
}
