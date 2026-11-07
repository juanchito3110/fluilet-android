package com.fluilet.app.domain.progress

/**
 * Cifras de la gráfica de lecciones. El porcentaje se calcula siempre a partir de los conteos (nunca se confía en un
 * porcentaje ya hecho): con 0 lecciones es 0 %, jamás "100 %".
 */
data class CompletionStats(val completed: Int, val inProgress: Int) {
    val total: Int get() = completed.coerceAtLeast(0) + inProgress.coerceAtLeast(0)
    val completedPercent: Int get() = percent(completed, total)
    val inProgressPercent: Int get() = percent(inProgress, total)

    companion object {
        /** `if (total > 0) (parte * 100) / total else 0`, acotado a 0–100. */
        fun percent(part: Int, total: Int): Int =
            if (total > 0) ((part.coerceAtLeast(0) * 100) / total).coerceIn(0, 100) else 0
    }
}
