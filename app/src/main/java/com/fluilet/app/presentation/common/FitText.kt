package com.fluilet.app.presentation.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Texto de una sola línea que nunca se corta ni se parte: si no cabe, reduce su tamaño (hasta [minSize]) en lugar de
 * mostrar "Estadísti" o "1.0x Norma". Para etiquetas de la barra inferior, insignias y botones con espacio fijo.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    color: Color = Color.Unspecified,
    minSize: TextUnit = 9.sp,
    textAlign: TextAlign = TextAlign.Center
) {
    val max = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val resolved = if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(textAlign = textAlign, lineHeight = TextUnit.Unspecified),
        color = ColorProducer { resolved },
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = max, stepSize = 0.5.sp)
    )
}
