package com.fluilet.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.fluilet.app.domain.model.AccessiblePalette

val LocalAccessiblePalette = staticCompositionLocalOf { AccessiblePalette.ESTANDAR }

@Composable
fun FluiletTheme(
    palette: AccessiblePalette = AccessiblePalette.ESTANDAR,
    content: @Composable () -> Unit
) {
    val base = if (palette == AccessiblePalette.ALTO_CONTRASTE) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = Color(palette.primary),
        onPrimary = Color(palette.onPrimary),
        background = Color(palette.background),
        onBackground = Color(palette.onBackground),
        surface = Color(palette.surface),
        onSurface = Color(palette.onBackground)
    )

    CompositionLocalProvider(LocalAccessiblePalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = FluiletTypography, content = content)
    }
}