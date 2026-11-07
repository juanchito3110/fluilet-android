package com.fluilet.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
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
 * Renglones con aire completo arriba y abajo: OpenDyslexic tiene trazos altos y profundos, y el recorte por defecto
 * (`Trim.Both`) los dejaba "rebanados" en tarjetas y áreas desplazables. Cada renglón mide al menos lo que la fuente
 * necesita (`Mode.Minimum`).
 */
val SafeLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.Minimum)

/** Espaciado entre letras: 0 en textos y títulos; ligeramente compacto en etiquetas, botones y pestañas. */
object LetterSpacing {
    val TEXT: TextUnit = 0.sp
    val LABEL: TextUnit = (-0.2).sp
}

/**
 * Ninguna palabra se parte con guion ni por sílabas ([Hyphens.None]); los títulos reparten sus renglones de forma
 * equilibrada ([LineBreak.Heading]) y los párrafos usan el corte de párrafo, pensado para lectura. OpenDyslexic es
 * ancha: sin espaciado extra entre letras para que las palabras no se ensanchen artificialmente.
 */
private fun style(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    spacing: TextUnit = LetterSpacing.TEXT,
    lineBreak: LineBreak = LineBreak.Paragraph
) = TextStyle(
    fontFamily = OpenDyslexicFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing,
    lineBreak = lineBreak,
    hyphens = Hyphens.None,
    lineHeightStyle = SafeLineHeight
)

private fun heading(size: Int, line: Int) = style(size, line, FontWeight.Bold, LetterSpacing.TEXT, LineBreak.Heading)

/** Etiquetas (botones, insignias, pestañas): compactas para que quepan completas. */
private fun label(size: Int, line: Int) = style(size, line, FontWeight.Bold, LetterSpacing.LABEL, LineBreak.Heading)

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
