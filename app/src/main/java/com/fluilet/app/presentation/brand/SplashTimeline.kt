package com.fluilet.app.presentation.brand

/** Valores animados del Splash en un instante dado (todo en 0..1 salvo los sobreimpulsos). */
data class SplashFrame(
    val squareScale: Float,
    val fProgress: Float,
    val dotScale: Float,
    val nameAlpha: Float,
    /** Desplazamiento vertical restante del nombre, de 1 (abajo) a 0 (en su sitio). */
    val nameOffset: Float,
    val sloganAlpha: Float,
    val lineProgress: Float
)

/**
 * Línea de tiempo del Splash (~2.4 s), sin dependencia de Compose para poder probarla:
 * - Fase 1 (0.0–0.9 s): entra el isotipo con rebote tipo resorte, se traza la "F" y aparece el punto naranja con efecto pop.
 * - Fase 2 (0.9–2.0 s): se revela "Fluilet" con fade + desplazamiento, luego el eslogan y se dibuja la línea menta.
 * - 2.0–2.4 s: reposo antes de pasar a Iniciar Sesión / Crear Cuenta.
 */
object SplashTimeline {
    const val PHASE1_END_MS = 900L
    const val PHASE2_END_MS = 2000L
    const val TOTAL_MS = 2400L

    private fun segment(ms: Long, start: Long, end: Long): Float = ((ms - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    /** Rebote con sobreimpulso, equivalente visual de un `spring` poco amortiguado. */
    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    private fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

    fun frame(ms: Long): SplashFrame {
        val name = easeOutCubic(segment(ms, PHASE1_END_MS, 1_400L))
        return SplashFrame(
            squareScale = easeOutBack(segment(ms, 0L, 450L)),
            fProgress = easeOutCubic(segment(ms, 300L, 750L)),
            dotScale = easeOutBack(segment(ms, 650L, PHASE1_END_MS)),
            nameAlpha = name,
            nameOffset = 1f - name,
            sloganAlpha = easeOutCubic(segment(ms, 1_300L, 1_700L)),
            lineProgress = easeOutCubic(segment(ms, 1_600L, PHASE2_END_MS))
        )
    }
}
