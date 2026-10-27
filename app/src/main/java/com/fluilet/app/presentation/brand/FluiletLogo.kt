package com.fluilet.app.presentation.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Identidad oficial de Fluilet (de `logo_fluilet.png`). */
object FluiletBrand {
    const val NAME = "Fluilet"
    const val SLOGAN = "Tu ritmo, tus palabras"

    val Purple = Color(0xFF7B63C6)
    val Lavender = Color(0xFFDBD4F3)
    val SloganPurple = Color(0xFF8E79D0)
    val Orange = Color(0xFFF5824A)
    val Mint = Color(0xFF48C999)
    val White = Color(0xFFFFFFFF)
}

/**
 * Geometría del isotipo en coordenadas unitarias del cuadrado (0..1): la "F" de trazo grueso redondeado,
 * su barra central lavanda sobre el trazo vertical y el punto naranja con centro blanco. El ícono vectorial
 * del teléfono (`ic_launcher_foreground.xml`) usa estos mismos valores (x = 20 + 60·u, y = 24 + 60·v).
 */
object IsotypeGeometry {
    const val CORNER = 0.20f
    const val STROKE = 0.097f
    const val BAR_STROKE = 0.099f

    /** Trazo vertical (de abajo a arriba) y barra superior, como una sola polilínea. */
    const val STEM_X = 0.3305f
    const val STEM_BOTTOM_Y = 0.7565f
    const val TOP_Y = 0.2635f
    const val TOP_END_X = 0.6435f

    /** Barra central lavanda. */
    const val BAR_Y = 0.5095f
    const val BAR_END_X = 0.5355f

    /** Punto naranja de la esquina inferior derecha. */
    const val DOT_X = 0.79f
    const val DOT_Y = 0.713f
    const val DOT_RADIUS = 0.072f
    const val DOT_CENTER_RADIUS = 0.031f
}

/**
 * Isotipo cuadrado de Fluilet dibujado en `Canvas`. [fProgress] (0..1) traza la "F" y su barra; [dotScale]
 * hace aparecer el punto naranja (admite sobreimpulso > 1 para el efecto "pop").
 */
@Composable
fun FluiletIsotypeLogo(
    modifier: Modifier = Modifier,
    fProgress: Float = 1f,
    dotScale: Float = 1f,
    withBackground: Boolean = true
) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = size.minDimension
        if (withBackground) {
            drawRoundRect(FluiletBrand.Purple, cornerRadius = CornerRadius(IsotypeGeometry.CORNER * s))
        }

        val f = Path().apply {
            moveTo(IsotypeGeometry.STEM_X * s, IsotypeGeometry.STEM_BOTTOM_Y * s)
            lineTo(IsotypeGeometry.STEM_X * s, IsotypeGeometry.TOP_Y * s)
            lineTo(IsotypeGeometry.TOP_END_X * s, IsotypeGeometry.TOP_Y * s)
        }
        drawTrimmed(f, fProgress.coerceIn(0f, 1f), FluiletBrand.White, IsotypeGeometry.STROKE * s)

        // La barra lavanda se traza al final, cuando la "F" ya casi está completa.
        val bar = Path().apply {
            moveTo(IsotypeGeometry.STEM_X * s, IsotypeGeometry.BAR_Y * s)
            lineTo(IsotypeGeometry.BAR_END_X * s, IsotypeGeometry.BAR_Y * s)
        }
        drawTrimmed(bar, ((fProgress - 0.6f) / 0.4f).coerceIn(0f, 1f), FluiletBrand.Lavender, IsotypeGeometry.BAR_STROKE * s)

        if (dotScale > 0f) {
            val center = Offset(IsotypeGeometry.DOT_X * s, IsotypeGeometry.DOT_Y * s)
            drawCircle(FluiletBrand.Orange, radius = IsotypeGeometry.DOT_RADIUS * s * dotScale, center = center)
            drawCircle(FluiletBrand.White, radius = IsotypeGeometry.DOT_CENTER_RADIUS * s * dotScale, center = center)
        }
    }
}

private fun DrawScope.drawTrimmed(path: Path, fraction: Float, color: Color, width: Float) {
    if (fraction <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val segment = Path()
    measure.getSegment(0f, measure.length * fraction, segment, true)
    drawPath(segment, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Línea horizontal verde menta del logo; [progress] la dibuja de izquierda a derecha. */
@Composable
fun MintLine(modifier: Modifier = Modifier, progress: Float = 1f, thickness: Dp = 4.dp) {
    Box(modifier.height(thickness)) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(thickness)
                .clip(RoundedCornerShape(thickness)).background(FluiletBrand.Mint)
        )
    }
}

/** Logo completo: isotipo + "Fluilet" + "Tu ritmo, tus palabras" + línea menta. */
@Composable
fun FluiletFullLogo(
    modifier: Modifier = Modifier,
    isotypeSize: Dp = 72.dp,
    nameSize: TextUnit = 40.sp,
    sloganSize: TextUnit = 14.sp
) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "${FluiletBrand.NAME}. ${FluiletBrand.SLOGAN}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FluiletIsotypeLogo(Modifier.size(isotypeSize))
        Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                FluiletBrand.NAME,
                color = FluiletBrand.Purple,
                fontSize = nameSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(FluiletBrand.SLOGAN, color = FluiletBrand.SloganPurple, fontSize = sloganSize, fontFamily = FontFamily.SansSerif)
            MintLine(Modifier.fillMaxWidth(), thickness = 3.dp)
        }
    }
}
