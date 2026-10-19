package com.fluilet.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fluilet.app.R

/** OpenDyslexic completa (regular, negrita, cursiva y negrita cursiva) desde los .otf de res/font. */
val OpenDyslexicFontFamily = FontFamily(
    Font(R.font.opendyslexic_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.opendyslexic_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.opendyslexic_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.opendyslexic_bolditalic, FontWeight.Bold, FontStyle.Italic)
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: TextUnit = 0.02.em) =
    TextStyle(
        fontFamily = OpenDyslexicFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = spacing
    )

/** Toda la escala M3 usa OpenDyslexic con interlineado holgado y ligero espaciado entre letras. */
fun fluiletTypography(): Typography = Typography(
    displayLarge = style(44, 56, FontWeight.Bold),
    displayMedium = style(36, 46, FontWeight.Bold),
    displaySmall = style(30, 40, FontWeight.Bold),
    headlineLarge = style(28, 38, FontWeight.Bold),
    headlineMedium = style(24, 34, FontWeight.Bold),
    headlineSmall = style(21, 30, FontWeight.Bold),
    titleLarge = style(20, 30, FontWeight.Bold),
    titleMedium = style(17, 26, FontWeight.Bold),
    titleSmall = style(15, 24, FontWeight.Bold),
    bodyLarge = style(17, 28),
    bodyMedium = style(15, 25),
    bodySmall = style(13, 21),
    labelLarge = style(15, 22, FontWeight.Bold),
    labelMedium = style(13, 20, FontWeight.Bold),
    labelSmall = style(12, 18, FontWeight.Bold)
)
