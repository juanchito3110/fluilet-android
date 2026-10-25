package com.fluilet.app.domain

import com.fluilet.app.presentation.fluibot.ConfettiPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfettiTest {
    @Test fun burstIsDeterministicPerSeedAndHasTheRequestedPieces() {
        assertEquals(70, ConfettiPiece.generate(1, 70).size)
        assertEquals(ConfettiPiece.generate(7, 40), ConfettiPiece.generate(7, 40))
        assertNotEquals(ConfettiPiece.generate(1, 40), ConfettiPiece.generate(2, 40))
    }

    @Test fun piecesStayWithinSaneBoundsAndMostFlyUpwards() {
        val pieces = ConfettiPiece.generate(3, 200)
        assertTrue(pieces.all { it.size in 0.5f..3f && kotlin.math.abs(it.vx) <= 1f && kotlin.math.abs(it.vy) <= 1.5f })
        assertTrue("la ráfaga sale hacia arriba", pieces.count { it.vy < 0f } > pieces.size / 2)
    }
}
