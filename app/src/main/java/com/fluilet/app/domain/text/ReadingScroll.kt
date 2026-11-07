package com.fluilet.app.domain.text

/**
 * Medidas de la barra de desplazamiento del bloque de lectura (sin Android, probadas con pruebas unitarias): el largo
 * del indicador refleja qué parte del bloque se ve y su posición cuánto se ha leído.
 */
object ReadingScroll {

    /** Largo mínimo del indicador para que siempre se vea y se pueda seguir con la vista. */
    const val MIN_THUMB_FRACTION = 0.12f

    /** Largo del indicador dentro de una pista de [track] px para una ventana de [viewport] px y [maxScroll] px ocultos. */
    fun thumbLength(track: Float, viewport: Float, maxScroll: Float): Float {
        if (track <= 0f) return 0f
        if (maxScroll <= 0f || viewport <= 0f) return track
        val visible = viewport / (viewport + maxScroll)
        return track * visible.coerceIn(MIN_THUMB_FRACTION, 1f)
    }

    /** Posición (desde arriba) del indicador de largo [thumb] cuando se han desplazado [value] de [maxScroll] px. */
    fun thumbOffset(track: Float, thumb: Float, value: Float, maxScroll: Float): Float {
        if (maxScroll <= 0f) return 0f
        return (track - thumb).coerceAtLeast(0f) * (value / maxScroll).coerceIn(0f, 1f)
    }
}
