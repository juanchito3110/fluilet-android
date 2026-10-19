package com.fluilet.app.core

import com.fluilet.app.core.security.TutorPinManager
import com.fluilet.app.domain.model.PinResult
import com.fluilet.app.testutil.FakeSecureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TutorPinManagerTest {

    private lateinit var store: FakeSecureStore
    private var wall = 1_000_000L
    private var mono = 50_000L
    private lateinit var pins: TutorPinManager

    /** Pasa el tiempo real: avanzan ambos relojes. */
    private fun advance(ms: Long) { wall += ms; mono += ms }

    @Before fun setUp() {
        store = FakeSecureStore()
        // Pocas iteraciones PBKDF2 para que la prueba sea rápida; la lógica es la misma.
        pins = TutorPinManager(store, { wall }, { mono }, iterations = 1_000)
    }

    @Test fun notConfiguredAtStart() {
        assertFalse(pins.isConfigured())
        assertEquals(PinResult.NotConfigured, pins.verify("4821"))
    }

    @Test fun setAndVerify() {
        assertTrue(pins.setInitialPin("4821").isSuccess)
        assertTrue(pins.isConfigured())
        assertTrue(pins.verify("4821") is PinResult.Success)
    }

    @Test fun pinIsNeverStoredInClear() {
        pins.setInitialPin("4821")
        assertTrue(store.data.values.none { it.toString().contains("4821") })
        assertNotNull(store.getString("pin.salt"))
        assertNotNull(store.getString("pin.hash"))
    }

    @Test fun saltIsRandomPerPin() {
        pins.setInitialPin("4821")
        val firstSalt = store.getString("pin.salt")
        val firstHash = store.getString("pin.hash")
        val other = TutorPinManager(FakeSecureStore(), { wall }, { mono }, 1_000)
        other.setInitialPin("4821")
        assertTrue(firstSalt != (other.javaClass.getDeclaredField("store").apply { isAccessible = true }.get(other) as FakeSecureStore).getString("pin.salt"))
        assertNotNull(firstHash)
    }

    @Test fun rejectsWeakPinsAndSecondSetup() {
        assertTrue(pins.setInitialPin("1234").isFailure)
        assertTrue(pins.setInitialPin("12").isFailure)
        assertTrue(pins.setInitialPin("4821").isSuccess)
        assertTrue(pins.setInitialPin("7392").isFailure)
    }

    @Test fun wrongPinCountsDownThenLocksOut() {
        pins.setInitialPin("4821")
        (4 downTo 1).forEach { remaining ->
            assertEquals(PinResult.Wrong(remaining), pins.verify("0000"))
        }
        val locked = pins.verify("0000")
        assertTrue(locked is PinResult.LockedOut)
        assertEquals(30L, (locked as PinResult.LockedOut).retryAfterSeconds)
        // Durante el bloqueo ni siquiera el PIN correcto entra.
        assertTrue(pins.verify("4821") is PinResult.LockedOut)
    }

    @Test fun lockoutExpiresAndSuccessResetsCounters() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        advance(31_000)
        assertTrue(pins.verify("4821") is PinResult.Success)
        assertEquals(PinResult.Wrong(4), pins.verify("0000")) // contador reiniciado
    }

    @Test fun lockoutDoublesAndIsCapped() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        advance(31_000)
        repeat(4) { pins.verify("0000") }
        val second = pins.verify("0000") as PinResult.LockedOut
        assertEquals(60L, second.retryAfterSeconds)
        repeat(40) { advance(16 * 60_000); pins.verify("0000") }
        val capped = pins.verify("0000")
        assertTrue(capped is PinResult.LockedOut)
        assertTrue((capped as PinResult.LockedOut).retryAfterSeconds <= 15 * 60)
    }

    @Test fun lockoutSurvivesProcessRestart() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        val restarted = TutorPinManager(store, { wall }, { mono }, 1_000)
        assertTrue(restarted.verify("4821") is PinResult.LockedOut)
    }

    @Test fun changePinRequiresCurrentPin() {
        pins.setInitialPin("4821")
        assertTrue(pins.changePin("0000", "7392") is PinResult.Wrong)
        assertTrue(pins.verify("7392") is PinResult.Wrong)
        assertTrue(pins.changePin("4821", "7392") is PinResult.Success)
        assertTrue(pins.verify("7392") is PinResult.Success)
        assertTrue(pins.verify("4821") is PinResult.Wrong)
    }

    @Test fun resetAfterReauthenticationReplacesPinAndClearsLockout() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        assertTrue(pins.resetAfterReauthentication("7392").isSuccess)
        assertTrue(pins.verify("7392") is PinResult.Success)
        assertTrue(pins.resetAfterReauthentication("1111").isFailure)
    }

    // --- Resistencia a manipular la hora del dispositivo --------------------------------

    @Test fun movingTheWallClockForwardDoesNotShortenTheLockout() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        wall += 3_600_000 // el niño adelanta la hora una hora; el reloj monotónico no cambia
        val result = pins.verify("4821")
        assertTrue(result is PinResult.LockedOut)
        assertEquals(30L, (result as PinResult.LockedOut).retryAfterSeconds)
    }

    @Test fun movingTheWallClockBackwardDoesNotShortenIt() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        advance(20_000)
        wall -= 3_600_000 // retroceder la hora tampoco ayuda
        mono += 0
        val result = pins.verify("4821") as PinResult.LockedOut
        assertTrue(result.retryAfterSeconds in 1..30)
        advance(11_000)
        // Retroceder la hora solo puede alargar la espera, nunca acortarla.
        assertTrue(pins.verify("4821") is PinResult.LockedOut)
    }

    @Test fun lockoutEndsByRealElapsedTime() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        advance(29_000)
        assertTrue(pins.verify("4821") is PinResult.LockedOut)
        advance(2_000)
        assertTrue(pins.verify("4821") is PinResult.Success)
    }

    @Test fun afterRebootTheMonotonicClockRestartsAndPersistedTimeIsUsed() {
        pins.setInitialPin("4821")
        repeat(5) { pins.verify("0000") }
        mono = 1_000          // reinicio: elapsedRealtime vuelve a empezar
        wall += 10_000        // pasaron 10 s según la hora persistida
        val stillLocked = pins.verify("4821") as PinResult.LockedOut
        assertEquals(20L, stillLocked.retryAfterSeconds)
        wall += 21_000
        assertTrue(pins.verify("4821") is PinResult.Success)
    }
}
