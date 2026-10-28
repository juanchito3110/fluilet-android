package com.fluilet.app.presentation

import com.fluilet.app.presentation.common.Confetti
import com.fluilet.app.presentation.fluibot.ArmPose
import com.fluilet.app.presentation.fluibot.FluiBotMood
import com.fluilet.app.presentation.fluibot.FluiBotMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Física del Avatar Robot 2.0 (levitación, sombra, brazos, parpadeo, antena) y del confeti de celebración. */
class FluiBotMotionTest {

    private val cycle = (0 until 120).map { it * (2 * PI).toFloat() / 120 }

    /** Posición de la mano (unidades) con la cinemática del lienzo: hombro en (50 ± 16.5, 52), brazo 9 y antebrazo 8. */
    private fun hand(pose: ArmPose, side: Int): Pair<Float, Float> {
        fun rad(d: Float) = d * PI.toFloat() / 180f
        val sx = 50f + side * 16.5f
        val ex = sx + side * 9f * sin(rad(pose.shoulder))
        val ey = 52f + 9f * cos(rad(pose.shoulder))
        return (ex + side * 8f * sin(rad(pose.forearm))) to (ey + 8f * cos(rad(pose.forearm)))
    }

    @Test fun levitationIsASmoothSineWaveWithinItsAmplitude() {
        val values = cycle.map(FluiBotMotion::hover)
        assertTrue(values.all { abs(it) <= FluiBotMotion.HOVER_AMPLITUDE + 1e-4f })
        assertEquals(FluiBotMotion.HOVER_AMPLITUDE, values.max(), 0.01f)
        assertEquals(-FluiBotMotion.HOVER_AMPLITUDE, values.min(), 0.01f)
        // Paso a paso no hay saltos bruscos.
        assertTrue(values.zipWithNext().all { (a, b) -> abs(a - b) < 0.2f })
    }

    @Test fun headAndTorsoTiltRhythmicallyButOutOfPhase() {
        assertTrue(cycle.all { abs(FluiBotMotion.torsoTilt(it)) <= FluiBotMotion.TILT_DEGREES + 1e-4f })
        assertTrue(cycle.all { abs(FluiBotMotion.headTilt(it)) <= FluiBotMotion.HEAD_TILT_DEGREES + 1e-4f })
        assertTrue("la cabeza no copia al torso", cycle.any { abs(FluiBotMotion.headTilt(it) - FluiBotMotion.torsoTilt(it)) > 0.5f })
    }

    @Test fun theGroundShadowShrinksAsFluiBotRises() {
        assertEquals(1f, FluiBotMotion.shadowScale(0f), 0f)
        val lifts = listOf(0f, 2f, 4.4f, 8f, 13.2f)
        val scales = lifts.map(FluiBotMotion::shadowScale)
        assertTrue("más alto, sombra más pequeña: $scales", scales.zipWithNext().all { (a, b) -> b < a })
        assertEquals(0.5f, FluiBotMotion.shadowScale(100f), 0f)
        assertEquals(1f, FluiBotMotion.shadowScale(-3f), 0f)
    }

    @Test fun armsSwingWithANaturalPhaseOffsetAtRest() {
        // En la segunda mitad del ciclo FluiBot feliz no saluda: ambos brazos están en reposo.
        val t = 4.0f
        val left = FluiBotMotion.armPose(FluiBotMood.HAPPY, -1, t, 0f)
        val right = FluiBotMotion.armPose(FluiBotMood.HAPPY, 1, t, 0f)
        assertTrue(left.shoulder < 30f && right.shoulder < 30f)
        assertTrue("desfase entre brazos: ${left.shoulder} vs ${right.shoulder}", abs(left.shoulder - right.shoulder) > 1f)
        val swing = cycle.map { FluiBotMotion.armPose(FluiBotMood.THINKING, -1, it, 0f).shoulder }
        assertTrue("el brazo en reposo se balancea", swing.max() - swing.min() > 10f)
    }

    @Test fun readingHoldsTheBookWithBothHandsInFrontOfTheChest() {
        val (lx, ly) = hand(FluiBotMotion.armPose(FluiBotMood.READING, -1, 0.5f, 0f), -1)
        val (rx, ry) = hand(FluiBotMotion.armPose(FluiBotMood.READING, 1, 0.5f, 0f), 1)
        // El libro va de x 38 a 62 a la altura del pecho (y ≈ 64).
        assertTrue("manos sobre el libro: ($lx, $ly) ($rx, $ry)", lx in 34f..44f && rx in 56f..66f)
        assertTrue(ly in 58f..70f && ry in 58f..70f)
    }

    @Test fun celebratingRaisesBothArmsAndThinkingRaisesOneHandBesideTheHead() {
        cycle.forEach { t ->
            listOf(-1, 1).forEach { side -> assertTrue(FluiBotMotion.armPose(FluiBotMood.CELEBRATING, side, t, 0f).shoulder > 130f) }
        }
        val (x, y) = hand(FluiBotMotion.armPose(FluiBotMood.THINKING, 1, 0f, 0f), 1)
        assertTrue("la mano queda junto a la cabeza, fuera del visor: ($x, $y)", x > 68f && y < 46f)
    }

    @Test fun happyFluiBotWavesWithTheRightArmDuringPartOfTheCycle() {
        val raised = cycle.count { FluiBotMotion.armPose(FluiBotMood.HAPPY, 1, it, 0f).shoulder > 100f }
        assertTrue("saluda parte del ciclo: $raised", raised in 20..100)
        assertTrue("el brazo izquierdo no saluda", cycle.none { FluiBotMotion.armPose(FluiBotMood.HAPPY, -1, it, 0f).shoulder > 100f })
    }

    @Test fun aTapMakesTheRightArmWaveWhateverTheMood() {
        FluiBotMood.entries.forEach { mood ->
            assertEquals(150f, FluiBotMotion.armPose(mood, 1, 0.3f, wave = 1f).shoulder, 0f)
        }
    }

    @Test fun blinkingIsIrregularLikeAPersonAndSometimesDouble() {
        val random = Random(7)
        val delays = List(200) { FluiBotMotion.nextBlinkDelay(random) }
        assertTrue(delays.all { it in 2200L..5400L })
        assertTrue("intervalos variados", delays.toSet().size > 150)
        val doubles = List(1000) { FluiBotMotion.doubleBlink(random) }.count { it }
        assertTrue("$doubles", doubles in 120..280)
    }

    @Test fun antennaPulsesAreEvenlySpacedRingsThatKeepExpanding() {
        val pulses = FluiBotMotion.antennaPulses(1.3f)
        assertEquals(3, pulses.size)
        assertTrue(pulses.all { it in 0f..1f })
        val sorted = pulses.sorted()
        assertEquals(1f / 3f, sorted[1] - sorted[0], 1e-4f)
        // Con el tiempo cada anillo crece (su fase avanza) hasta reiniciarse.
        val later = FluiBotMotion.antennaPulses(1.4f)
        assertTrue(later[0] > pulses[0] || later[0] < 0.05f)
    }

    @Test fun readerMoodThinksWhileLoadingAndCelebratesOnSuccess() {
        assertEquals(FluiBotMood.READING, FluiBotMood.forReader(loading = false, celebrating = false))
        assertEquals(FluiBotMood.THINKING, FluiBotMood.forReader(loading = true, celebrating = false))
        assertEquals(FluiBotMood.CELEBRATING, FluiBotMood.forReader(loading = true, celebrating = true))
    }

    // --- Confeti --------------------------------------------------------------------------------

    @Test fun confettiIsDeterministicPerCelebration() {
        assertEquals(Confetti.pieces(seed = 3), Confetti.pieces(seed = 3))
        assertTrue(Confetti.pieces(seed = 3) != Confetti.pieces(seed = 4))
        assertEquals(70, Confetti.pieces(seed = 3).size)
        assertTrue("estrellas, rectángulos y puntos", Confetti.pieces(seed = 3).map { it.shape }.toSet().size == 3)
    }

    @Test fun confettiBurstsUpwardsThenGravityBringsItDown() {
        Confetti.pieces(seed = 11).forEach { piece ->
            assertTrue("sale hacia arriba", piece.vy < 0f)
            val (_, early) = Confetti.position(piece, 0.1f)
            val (_, late) = Confetti.position(piece, 1.7f)
            assertTrue(early < 0.45f)
            assertTrue("la gravedad la hace caer", late > early)
        }
        assertEquals(1f, Confetti.alpha(0.3f), 0f)
        assertTrue(Confetti.alpha(0.9f) < 0.5f)
        assertEquals(0f, Confetti.alpha(1f), 0f)
    }
}
