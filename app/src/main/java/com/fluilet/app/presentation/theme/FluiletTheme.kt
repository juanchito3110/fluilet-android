package com.fluilet.app.presentation.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.fluilet.app.domain.model.AccessiblePalette

val LocalAccessiblePalette = staticCompositionLocalOf { AccessiblePalette.DEFAULT }

/**
 * Tema Material 3 derivado de una paleta clínica. Se aplica a TODA la app (no solo al lector) para
 * evitar el blanco puro sobre negro puro; el lector lo reaplica con la paleta elegida por el niño.
 */
@Composable
fun FluiletTheme(
    palette: AccessiblePalette = AccessiblePalette.DEFAULT,
    content: @Composable () -> Unit
) {
    val background = Color(palette.background)
    val onBackground = Color(palette.onBackground)
    val surface = Color(palette.surface)
    val primary = Color(palette.primary)
    val onPrimary = Color(palette.onPrimary)

    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = surface,
        onPrimaryContainer = onBackground,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = surface,
        onSecondaryContainer = onBackground,
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        surfaceVariant = surface,
        onSurfaceVariant = onBackground,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background,
        outline = onBackground.copy(alpha = 0.6f),
        outlineVariant = onBackground.copy(alpha = 0.3f),
        error = if (palette.isDark) Color(0xFFFF9C94) else Color(0xFF9B1C1C),
        onError = if (palette.isDark) Color(0xFF1E1E1E) else Color.White
    )

    // Iconos de la barra de estado/navegación con contraste suficiente según la paleta (no el tema del sistema).
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(palette.isDark) {
            val style = if (palette.isDark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
            (view.context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose { }
        }
    }

    CompositionLocalProvider(LocalAccessiblePalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = fluiletTypography(), content = content)
    }
}
