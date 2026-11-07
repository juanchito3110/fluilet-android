package com.fluilet.app.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/** Título de ventana emergente: centrado, completo (los renglones que necesite) y sin partir palabras. */
@Composable
fun DialogTitle(text: String, style: TextStyle = MaterialTheme.typography.headlineSmall) {
    AdaptiveText(text, Modifier.fillMaxWidth(), style = style, textAlign = TextAlign.Center)
}

/** Botón de texto de una ventana emergente con su etiqueta completa: si no cabe, baja de renglón sin partir palabras. */
@Composable
fun DialogTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true, color: Color = Color.Unspecified) {
    TextButton(onClick = onClick, enabled = enabled) {
        AdaptiveText(
            text,
            Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
