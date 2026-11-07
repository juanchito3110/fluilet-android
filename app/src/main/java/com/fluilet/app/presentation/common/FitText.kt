package com.fluilet.app.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/**
 * Texto de una sola línea que nunca se corta ni se parte: si no cabe, reduce su tamaño (hasta [minSize]) en lugar de
 * mostrar "Estadísti" o "1.0x Norma". Es [AdaptiveSingleLineText] con el estilo de etiqueta por defecto.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    color: Color = Color.Unspecified,
    minSize: TextUnit = MinReadableSize,
    textAlign: TextAlign = TextAlign.Center
) = AdaptiveSingleLineText(text, modifier, style, color, minSize, textAlign)
