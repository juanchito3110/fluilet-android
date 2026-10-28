package com.fluilet.app.presentation.fluibot

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Expresión de FluiBot según lo que pasa en la pantalla. */
enum class FluiBotMood(val description: String) {
    /** Feliz y saludando (Login, Inicio, Tienda). */
    HAPPY("feliz y saludando"),

    /** Leyendo concentrado con un libro (lector). */
    READING("leyendo concentrado"),

    /** Pensando mientras algo carga. */
    THINKING("pensando"),

    /** Celebrando con ojos de estrella y los brazos arriba. */
    CELEBRATING("celebrando");

    companion object {
        /** Expresión del lector: piensa mientras carga, celebra un mini-reto superado y, si no, lee concentrado. */
        fun forReader(loading: Boolean, celebrating: Boolean): FluiBotMood = when {
            celebrating -> CELEBRATING
            loading -> THINKING
            else -> READING
        }
    }
}

/** Ángulos de un brazo articulado: hombro (desde la vertical hacia afuera) y codo, en grados. */
data class ArmPose(val shoulder: Float, val elbow: Float) {
    /** Ángulo absoluto del antebrazo. */
    val forearm: Float get() = shoulder + elbow
}

/**
 * Física de FluiBot, sin dependencias de Compose para poder probarla: levitación senoidal, inclinación rítmica,
 * sombra que se encoge al subir, brazos con desfase natural y el parpadeo orgánico (intervalos irregulares).
 * `t` es la fase del ciclo de 6 s (0..2π); las distancias son unidades del lienzo de 100 x 100.
 */
object FluiBotMotion {

    const val HOVER_AMPLITUDE = 2.2f
    const val TILT_DEGREES = 2.5f
    const val HEAD_TILT_DEGREES = 3.5f
    const val JUMP_HEIGHT = 11f

    /** Desplazamiento vertical por levitación (negativo = arriba). */
    fun hover(t: Float): Float = sin(t) * HOVER_AMPLITUDE

    /** Inclinación del torso; la cabeza va con un pequeño retraso para que el movimiento se sienta orgánico. */
    fun torsoTilt(t: Float): Float = sin(t * 0.5f + 0.4f) * TILT_DEGREES
    fun headTilt(t: Float): Float = sin(t * 0.5f - 0.2f) * HEAD_TILT_DEGREES

    /**
     * Escala de la sombra elíptica del suelo para una altura [lift] (unidades por encima del reposo; ≥ 0): se contrae
     * y se aclara cuando FluiBot sube y vuelve a crecer al bajar.
     */
    fun shadowScale(lift: Float): Float = (1f - max(0f, lift) / 30f).coerceIn(0.5f, 1f)

    /** Pose de cada brazo ([side] = −1 izquierdo, +1 derecho) para la expresión, con desfase entre ambos brazos. */
    fun armPose(mood: FluiBotMood, side: Int, t: Float, wave: Float): ArmPose {
        val phase = t + side * 0.6f
        val swing = sin(phase) * 8f
        val rest = ArmPose(14f + swing, -20f + cos(t * 1.3f + side) * 6f)
        // Saludo del toque: el brazo derecho se levanta y el antebrazo se agita.
        if (wave > 0f && side > 0) return ArmPose(150f, sin(t * 14f) * 30f * wave)
        return when (mood) {
            FluiBotMood.HAPPY -> {
                // Saluda durante la primera mitad del ciclo con el brazo derecho; el izquierdo se balancea.
                val greet = max(0f, sin(t))
                if (side > 0 && greet > 0.15f) ArmPose(120f + 30f * greet, sin(t * 9f) * 25f * greet) else rest
            }
            FluiBotMood.READING -> ArmPose(20f + sin(phase * 0.5f) * 2f, -95f)
            FluiBotMood.THINKING -> if (side > 0) ArmPose(165f, -35f + sin(t * 3f) * 6f) else rest
            FluiBotMood.CELEBRATING -> ArmPose(150f + sin(t * 8f + side) * 10f, -10f)
        }
    }

    /** Milisegundos hasta el próximo parpadeo: irregular, como el de una persona (2.2 a 5.4 s). */
    fun nextBlinkDelay(random: Random): Long = 2200L + random.nextLong(3200L)

    /** A veces parpadea dos veces seguidas. */
    fun doubleBlink(random: Random): Boolean = random.nextFloat() < 0.2f

    /** Fase (0..1) de cada uno de los [count] anillos de energía que emite la antena. */
    fun antennaPulses(t: Float, count: Int = 3, speed: Float = 2f): List<Float> =
        List(count) { k -> ((t / (2f * PI.toFloat()) * speed + k.toFloat() / count) % 1f + 1f) % 1f }
}
