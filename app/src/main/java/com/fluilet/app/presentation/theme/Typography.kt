package com.fluilet.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
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

/**
 * Ninguna palabra se parte con guion ni por sílabas ([Hyphens.None]); los títulos reparten sus renglones de forma
 * equilibrada ([LineBreak.Heading]) y los párrafos usan el corte de párrafo, pensado para lectura.
 */
private fun style(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    spacing: TextUnit = 0.02.em,
    lineBreak: LineBreak = LineBreak.Paragraph
) = TextStyle(
    fontFamily = OpenDyslexicFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing,
    lineBreak = lineBreak,
    hyphens = Hyphens.None
)

private fun heading(size: Int, line: Int) = style(size, line, FontWeight.Bold, 0.01.em, LineBreak.Heading)

/** Etiquetas (botones, insignias, pestañas): sin espaciado extra para que quepan completas. */
private fun label(size: Int, line: Int) = style(size, line, FontWeight.Bold, 0.em, LineBreak.Heading)

/** Toda la escala M3 usa OpenDyslexic con interlineado holgado; jerarquía clara entre títulos, texto y etiquetas. */
fun fluiletTypography(): Typography = Typography(
    displayLarge = heading(44, 56),
    displayMedium = heading(36, 46),
    displaySmall = heading(30, 40),
    headlineLarge = heading(28, 38),
    headlineMedium = heading(24, 34),
    headlineSmall = heading(21, 30),
    titleLarge = heading(20, 28),
    titleMedium = heading(17, 25),
    titleSmall = heading(15, 22),
    bodyLarge = style(17, 28),
    bodyMedium = style(15, 25),
    bodySmall = style(13, 21),
    labelLarge = label(15, 22),
    labelMedium = label(13, 19),
    labelSmall = label(12, 17)
)
