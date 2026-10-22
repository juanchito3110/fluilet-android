package com.fluilet.app.presentation.common

/** Duración legible para el avance educativo: "45 s", "12 min", "1 h 05 min". */
fun formatReadingTime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} h ${"%02d".format((seconds % 3600) / 60)} min"
    }
}

/** Porcentaje entero sin decimales, p. ej. 66.6 -> "67 %". */
fun formatPercent(value: Double): String = "${Math.round(value)} %"
