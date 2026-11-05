package com.fluilet.app.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.presentation.newreading.categoryIcon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Identidad visual de cada categoría de lectura: degradado de fondo, color de acento e ícono. */
object CategoryArt {

    data class Palette(val start: Color, val end: Color, val accent: Color)

    fun palette(code: String?): Palette = when (LessonCategory.fromCode(code)) {
        LessonCategory.CIENCIA_ESPACIO -> Palette(Color(0xFF14123A), Color(0xFF4C1D95), Color(0xFFFDE68A))
        LessonCategory.ANIMALES_NATURALEZA -> Palette(Color(0xFF0F3D2A), Color(0xFF15803D), Color(0xFFBBF7D0))
        LessonCategory.HISTORIA_MISTERIOS -> Palette(Color(0xFF5B2C0A), Color(0xFFC2770E), Color(0xFFFEF3C7))
        LessonCategory.DEPORTES -> Palette(Color(0xFF0B3B5C), Color(0xFF0284C7), Color(0xFFE0F2FE))
        LessonCategory.TECNOLOGIA -> Palette(Color(0xFF0D3B39), Color(0xFF0F9F94), Color(0xFFCCFBF1))
        LessonCategory.CUENTOS_AVENTURAS -> Palette(Color(0xFF6B1240), Color(0xFFDB2777), Color(0xFFFCE7F3))
        null -> Palette(Color(0xFF2E2560), Color(0xFF7B63C6), Color(0xFFEDE9FE))
    }

    fun icon(code: String?): ImageVector = LessonCategory.fromCode(code)?.let(::categoryIcon) ?: Icons.AutoMirrored.Filled.MenuBook
}

/**
 * "Arte" de una categoría: degradado temático con un patrón propio (estrellas y planeta para el cosmos, hojas para la
 * naturaleza, papiro y sol para la historia, cancha para deportes, circuito para tecnología, chispas y luna para los
 * cuentos) y el ícono grande al centro con un halo. Bloqueado: se apaga y muestra un candado.
 */
@Composable
fun CategoryArtwork(code: String?, modifier: Modifier = Modifier, iconSize: Dp = 64.dp, locked: Boolean = false) {
    val palette = CategoryArt.palette(code)
    val category = LessonCategory.fromCode(code)
    Box(modifier.background(Brush.linearGradient(listOf(palette.start, palette.end))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawPattern(category, palette.accent.copy(alpha = if (locked) 0.12f else 0.28f), code.hashCode()) }
        Box(
            Modifier.size(iconSize * 1.7f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(palette.accent.copy(alpha = if (locked) 0.10f else 0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (locked) Icons.Filled.Lock else CategoryArt.icon(code),
                contentDescription = null,
                tint = if (locked) Color.White.copy(alpha = 0.7f) else Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

private fun DrawScope.drawPattern(category: LessonCategory?, accent: Color, seed: Int) {
    val random = Random(seed)
    val w = size.width
    val h = size.height
    when (category) {
        LessonCategory.CIENCIA_ESPACIO, null -> {
            repeat(46) {
                drawCircle(accent, radius = 1f + random.nextFloat() * 2.6f, center = Offset(random.nextFloat() * w, random.nextFloat() * h))
            }
            val planet = Offset(w * 0.82f, h * 0.24f)
            drawCircle(accent.copy(alpha = accent.alpha * 1.4f), radius = h * 0.11f, center = planet)
            drawOval(accent, topLeft = Offset(planet.x - h * 0.2f, planet.y - h * 0.04f), size = Size(h * 0.4f, h * 0.08f), style = Stroke(3f))
        }
        LessonCategory.ANIMALES_NATURALEZA -> repeat(14) {
            val c = Offset(random.nextFloat() * w, random.nextFloat() * h)
            val r = h * (0.05f + random.nextFloat() * 0.08f)
            val angle = random.nextFloat() * 360f
            drawLeaf(c, r, angle, accent)
        }
        LessonCategory.HISTORIA_MISTERIOS -> {
            drawCircle(accent.copy(alpha = accent.alpha * 1.5f), radius = h * 0.16f, center = Offset(w * 0.18f, h * 0.22f))
            var y = h * 0.55f
            while (y < h) {
                drawLine(accent, Offset(0f, y), Offset(w, y + h * 0.04f), strokeWidth = 2f)
                y += h * 0.09f
            }
            // Pirámide al fondo.
            val path = Path().apply {
                moveTo(w * 0.62f, h * 0.92f); lineTo(w * 0.8f, h * 0.48f); lineTo(w * 0.98f, h * 0.92f); close()
            }
            drawPath(path, accent)
        }
        LessonCategory.DEPORTES -> {
            val stroke = Stroke(3f)
            drawRect(accent, topLeft = Offset(w * 0.06f, h * 0.1f), size = Size(w * 0.88f, h * 0.8f), style = stroke)
            drawLine(accent, Offset(w / 2, h * 0.1f), Offset(w / 2, h * 0.9f), strokeWidth = 3f)
            drawCircle(accent, radius = h * 0.16f, center = Offset(w / 2, h / 2), style = stroke)
            drawRect(accent, topLeft = Offset(w * 0.06f, h * 0.32f), size = Size(w * 0.1f, h * 0.36f), style = stroke)
            drawRect(accent, topLeft = Offset(w * 0.84f, h * 0.32f), size = Size(w * 0.1f, h * 0.36f), style = stroke)
        }
        LessonCategory.TECNOLOGIA -> {
            val step = h * 0.16f
            var x = step / 2
            while (x < w) {
                drawLine(accent.copy(alpha = accent.alpha * 0.6f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                x += step
            }
            var y = step / 2
            while (y < h) {
                drawLine(accent.copy(alpha = accent.alpha * 0.6f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
                y += step
            }
            repeat(12) {
                val node = Offset((random.nextInt((w / step).toInt().coerceAtLeast(1)) + 0.5f) * step, (random.nextInt((h / step).toInt().coerceAtLeast(1)) + 0.5f) * step)
                drawCircle(accent.copy(alpha = accent.alpha * 2f), radius = 4.5f, center = node)
            }
        }
        LessonCategory.CUENTOS_AVENTURAS -> {
            repeat(18) { drawSparkle(Offset(random.nextFloat() * w, random.nextFloat() * h), 4f + random.nextFloat() * 8f, accent) }
            val moon = Offset(w * 0.84f, h * 0.22f)
            drawCircle(accent.copy(alpha = accent.alpha * 1.6f), radius = h * 0.12f, center = moon)
            drawCircle(Color.Black.copy(alpha = 0.18f), radius = h * 0.1f, center = Offset(moon.x + h * 0.05f, moon.y - h * 0.03f))
        }
    }
}

private fun DrawScope.drawLeaf(center: Offset, radius: Float, angleDegrees: Float, color: Color) {
    val a = angleDegrees * PI.toFloat() / 180f
    val dx = cos(a) * radius
    val dy = sin(a) * radius
    val path = Path().apply {
        moveTo(center.x - dx, center.y - dy)
        quadraticTo(center.x - dy * 0.8f, center.y + dx * 0.8f, center.x + dx, center.y + dy)
        quadraticTo(center.x + dy * 0.8f, center.y - dx * 0.8f, center.x - dx, center.y - dy)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}
