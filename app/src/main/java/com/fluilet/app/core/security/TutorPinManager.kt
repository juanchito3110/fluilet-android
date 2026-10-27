package com.fluilet.app.core.security

import com.fluilet.app.domain.model.PinResult
import com.fluilet.app.domain.model.PinVerification
import com.fluilet.app.domain.repository.TutorPinRepository
import com.fluilet.app.domain.validation.Validators
import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Muro Parental (Google Play Families). El PIN del tutor se guarda como sal aleatoria de 16 bytes
 * + hash PBKDF2-HMAC-SHA256 en el almacén cifrado AES-256-GCM ([SecureStore]); nunca en claro.
 *
 * Rate-limiting: tras 5 intentos fallidos se bloquea 30 s; cada 5 fallos adicionales duplica el
 * bloqueo (tope 15 min). El contador sobrevive al reinicio del proceso.
 *
 * Anti-manipulación del reloj: el bloqueo se mide con el reloj MONOTÓNICO (`elapsedRealtime`, que
 * no se puede adelantar desde Ajustes) combinado con la hora persistida. Se toma el MENOR de los dos
 * tiempos transcurridos: adelantar la hora del dispositivo no acorta el bloqueo. Si el dispositivo
 * se reinicia (el monotónico vuelve a cero) se usa solo la hora persistida, sin permitir que un
 * retroceso del reloj acorte la espera.
 *
 * Restablecimiento: solo mediante [resetAfterReauthentication], que el caso de uso invoca después
 * de validar la contraseña del tutor contra el backend.
 */
@Singleton
class TutorPinManager internal constructor(
    private val store: SecureStore,
    private val wallClock: () -> Long,
    private val monotonicClock: () -> Long,
    private val iterations: Int
) : TutorPinRepository {

    @Inject
    constructor(store: SecureStore) : this(
        store,
        System::currentTimeMillis,
        SystemClock::elapsedRealtime,
        DEFAULT_ITERATIONS
    )

    private val lock = Any()

    override fun isConfigured(): Boolean = synchronized(lock) {
        store.getString(KEY_HASH) != null && store.getString(KEY_SALT) != null
    }

    override fun verify(pin: String): PinResult = synchronized(lock) {
        if (!isConfigured()) return PinResult.NotConfigured

        val remainingLockMs = remainingLockMillis()
        if (remainingLockMs > 0) return PinResult.LockedOut(secondsFor(remainingLockMs))

        val salt = store.getString(KEY_SALT)!!.decode()
        val expected = store.getString(KEY_HASH)!!.decode()
        val actual = hash(pin, salt)

        if (MessageDigest.isEqual(expected, actual)) {
            store.remove(KEY_FAILS, KEY_LOCK_MS, KEY_LOCK_START_WALL, KEY_LOCK_START_MONO)
            return PinResult.Success(PinVerification())
        }
        registerFailure()
    }

    /** Milisegundos de bloqueo pendientes, resistente a cambios de hora (ver documentación de la clase). */
    private fun remainingLockMillis(): Long {
        val duration = store.getLong(KEY_LOCK_MS)
        if (duration <= 0) return 0
        val wallElapsed = (wallClock() - store.getLong(KEY_LOCK_START_WALL)).coerceAtLeast(0)
        val monoElapsed = monotonicClock() - store.getLong(KEY_LOCK_START_MONO)
        // monoElapsed < 0 => el dispositivo se reinició desde que se fijó el bloqueo.
        val elapsed = if (monoElapsed >= 0) minOf(monoElapsed, wallElapsed) else wallElapsed
        return (duration - elapsed).coerceAtLeast(0)
    }

    private fun registerFailure(): PinResult {
        val fails = store.getLong(KEY_FAILS) + 1
        store.putLong(KEY_FAILS, fails)
        if (fails % ATTEMPTS_BEFORE_LOCK == 0L) {
            val level = (fails / ATTEMPTS_BEFORE_LOCK - 1).coerceAtMost(MAX_BACKOFF_STEPS)
            val lockMs = (BASE_LOCK_MS shl level.toInt()).coerceAtMost(MAX_LOCK_MS)
            store.putLong(KEY_LOCK_MS, lockMs)
            store.putLong(KEY_LOCK_START_WALL, wallClock())
            store.putLong(KEY_LOCK_START_MONO, monotonicClock())
            return PinResult.LockedOut(secondsFor(lockMs))
        }
        return PinResult.Wrong(remainingAttempts = (ATTEMPTS_BEFORE_LOCK - fails % ATTEMPTS_BEFORE_LOCK).toInt())
    }

    override fun setInitialPin(pin: String): Result<Unit> = synchronized(lock) {
        if (isConfigured()) return Result.failure(IllegalStateException("Ya existe un PIN configurado."))
        persist(pin)
    }

    override fun changePin(currentPin: String, newPin: String): PinResult = synchronized(lock) {
        val result = verify(currentPin)
        if (result is PinResult.Success && persist(newPin).isFailure) return PinResult.Wrong(0)
        result
    }

    override fun resetAfterReauthentication(newPin: String): Result<Unit> = synchronized(lock) {
        persist(newPin)
    }

    override fun configureDemoPin(pin: String): Result<Unit> = synchronized(lock) {
        persist(pin, enforceStrength = false)
    }

    private fun persist(pin: String, enforceStrength: Boolean = true): Result<Unit> {
        if (enforceStrength) {
            Validators.tutorPin(pin)?.let { return Result.failure(IllegalArgumentException(it)) }
        } else if (pin.length !in 4..6 || !pin.all(Char::isDigit)) {
            return Result.failure(IllegalArgumentException("El PIN debe tener de 4 a 6 dígitos."))
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        store.putString(KEY_SALT, salt.encode())
        store.putString(KEY_HASH, hash(pin, salt).encode())
        store.remove(KEY_FAILS, KEY_LOCK_MS, KEY_LOCK_START_WALL, KEY_LOCK_START_MONO)
        return Result.success(Unit)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun secondsFor(ms: Long): Long = (ms + 999) / 1000

    private fun ByteArray.encode(): String = Base64.getEncoder().encodeToString(this)
    private fun String.decode(): ByteArray = Base64.getDecoder().decode(this)

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val ATTEMPTS_BEFORE_LOCK = 5L
        private const val BASE_LOCK_MS = 30_000L
        private const val MAX_LOCK_MS = 15 * 60_000L
        private const val MAX_BACKOFF_STEPS = 5L

        private const val KEY_SALT = "pin.salt"
        private const val KEY_HASH = "pin.hash"
        private const val KEY_FAILS = "pin.fails"
        private const val KEY_LOCK_MS = "pin.lock_ms"
        private const val KEY_LOCK_START_WALL = "pin.lock_start_wall"
        private const val KEY_LOCK_START_MONO = "pin.lock_start_mono"
    }
}
