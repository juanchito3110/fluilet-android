package com.fluilet.app.core

import com.fluilet.app.core.focusmode.FocusEndResult
import com.fluilet.app.core.focusmode.FocusServiceLauncher
import com.fluilet.app.core.focusmode.FocusSessionController
import com.fluilet.app.domain.model.PinVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FocusSessionControllerTest {

    private class FakeLauncher : FocusServiceLauncher {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }

    private val launcher = FakeLauncher()
    private var mono = 0L
    private var wall = Instant.parse("2026-01-01T10:00:00Z")
    private val controller = FocusSessionController(launcher, { mono }, { wall })

    @Test fun beginStartsServiceAndTimer() {
        controller.begin("child-1", lockTask = true)
        assertTrue(controller.state.value.isActive)
        assertTrue(controller.state.value.lockRequested)
        assertEquals(1, launcher.starts)
        mono += 65_000
        controller.tick()
        assertEquals(65L, controller.state.value.elapsedSeconds)
    }

    @Test fun beginTwiceDoesNotRestart() {
        controller.begin("c", true)
        controller.begin("c", true)
        assertEquals(1, launcher.starts)
    }

    @Test fun lockedSessionCannotEndWithoutPin() {
        controller.begin("child-1", lockTask = true)
        assertEquals(FocusEndResult.PinRequired, controller.end(null))
        assertTrue(controller.state.value.isActive)
        assertEquals(0, launcher.stops)
    }

    @Test fun lockedSessionEndsWithPinProofAndReportsSummary() {
        controller.begin("child-1", lockTask = true)
        mono += 120_000
        wall = wall.plusSeconds(120)
        val result = controller.end(PinVerification())
        assertTrue(result is FocusEndResult.Ended)
        val summary = (result as FocusEndResult.Ended).summary
        assertEquals(120L, summary.elapsedSeconds)
        assertEquals("child-1", summary.childId)
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), summary.startedAt)
        assertEquals(Instant.parse("2026-01-01T10:02:00Z"), summary.endedAt)
        assertFalse(controller.state.value.isActive)
        assertEquals(1, launcher.stops)
    }

    @Test fun unlockedSessionEndsFreely() {
        controller.begin(null, lockTask = false)
        assertTrue(controller.end(null) is FocusEndResult.Ended)
    }

    @Test fun endWhenNotActive() {
        assertEquals(FocusEndResult.NotActive, controller.end(null))
    }

    @Test fun teardownClosesWithoutPinButKeepsTelemetry() {
        controller.begin("child-1", lockTask = true)
        mono += 10_000
        val summary = controller.abortForTeardown()
        assertNotNull(summary)
        assertEquals(10L, summary!!.elapsedSeconds)
        assertFalse(controller.state.value.isActive)
        assertNull(controller.abortForTeardown())
    }

    @Test fun timerIsMonotonicAndIgnoresWallClockChanges() {
        controller.begin("c", true)
        wall = wall.minusSeconds(3600) // alguien cambia la hora del dispositivo
        mono += 30_000
        controller.tick()
        assertEquals(30L, controller.state.value.elapsedSeconds)
    }
}
