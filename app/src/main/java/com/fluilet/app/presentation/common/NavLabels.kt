package com.fluilet.app.presentation.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * Tamaño ÚNICO de las 5 etiquetas de la barra inferior ("Inicio", "Lecturas", "Adaptar", "Avatar", "Cartas"): todas a
 * [BASE] o, si la más ancha no cabe en su espacio, todas al mismo tamaño menor (de 0,5 en 0,5 sp, mínimo [MIN]).
 * Así nunca hay una etiqueta grande junto a otras encogidas.
 */
object NavLabels {
    val BASE: TextUnit = 12.sp
    val MIN: TextUnit = 9.sp

    /** Separación entre ítems de la `NavigationBar` de Material 3. */
    val ItemGap: Dp = 8.dp

    /** Aire total a los lados de cada etiqueta. */
    val LabelPadding: Dp = 4.dp

    /** Ancho disponible para la etiqueta de cada uno de los [count] apartados en una barra de [totalWidth]. */
    fun slotWidth(totalWidth: Dp, count: Int): Dp {
        if (count <= 0) return 0.dp
        val slot = (totalWidth - ItemGap * (count - 1)) / count - LabelPadding
        return if (slot > 0.dp) slot else 0.dp
    }

    /** Tamaño común: [BASE] si la etiqueta más ancha ([widestAtBase] px medida a [BASE]) cabe en [available] px. */
    fun uniformSize(widestAtBase: Float, available: Float): TextUnit {
        if (widestAtBase <= 0f || widestAtBase <= available) return BASE
        if (available <= 0f) return MIN
        val scaled = BASE.value * available / widestAtBase
        val stepped = floor(scaled * 2f) / 2f
        return maxOf(stepped, MIN.value).sp
    }
}
