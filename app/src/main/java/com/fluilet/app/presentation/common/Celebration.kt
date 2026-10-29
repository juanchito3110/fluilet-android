package com.fluilet.app.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Una pieza de confeti: forma, color y la velocidad inicial con la que sale disparada. */
data class ConfettiPiece(
    val shape: Shape,
    val color: Long,
    /** Velocidad inicial en fracciones del ancho/alto por segundo. */
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val size: Float
) {
    enum class Shape { RECT, STAR, DOT }
}

/**
 * Física del confeti de celebración (sin dependencias de Compose para poder probarla): las piezas salen de un
 * punto hacia arriba y a los lados, la gravedad las curva y se desvanecen al final de la animación.
 */
object Confetti {

    const val GRAVITY = 2.6f
    private val PALETTE = listOf(0xFF22D3EE, 0xFFFBBF24, 0xFFF472B6, 0xFF48C999, 0xFF7B63C6, 0xFFF5824A)

    /** Piezas deterministas para una misma [seed] (la celebración se ve igual en cada recomposición). */
    fun pieces(seed: Int, count: Int = 70): List<ConfettiPiece> {
        val random = Random(seed)
        return List(count) { i ->
            // Abanico de −150° a −30°: hacia arriba y a los lados.
            val angle = (-150f + random.nextFloat() * 120f) * PI.toFloat() / 180f
            // Altura máxima v²/2g ≤ 0.33 del lienzo: las piezas no se salen por arriba.
            val speed = 0.6f + random.nextFloat() * 0.7f
            ConfettiPiece(
                shape = ConfettiPiece.Shape.entries[i % ConfettiPiece.Shape.entries.size],
                color = PALETTE[random.nextInt(PALETTE.size)],
                vx = cos(angle) * speed * 0.55f,
                vy = sin(angle) * speed,
                spin = (random.nextFloat() - 0.5f) * 720f,
                size = 0.012f + random.nextFloat() * 0.014f
            )
        }
    }

    /** Posición (fracción del lienzo) de una pieza que sale de ([originX], [originY]) tras [seconds]. */
    fun position(piece: ConfettiPiece, seconds: Float, originX: Float = 0.5f, originY: Float = 0.45f): Pair<Float, Float> =
        (originX + piece.vx * seconds) to (originY + piece.vy * seconds + GRAVITY * seconds * seconds / 2f)

    /** Opacidad: entera al principio y se desvanece en el último 35 % de la animación. */
    fun alpha(progress: Float): Float = if (progress < 0.65f) 1f else ((1f - progress) / 0.35f).coerceIn(0f, 1f)
}

/**
 * Explosión suave de confeti y estrellas luminosas dibujada en `Canvas` encima del contenido.
 *
 * Nunca bloquea toques: no declara `pointerInput` ni `clickable`, así que el sistema de entrada de Compose la ignora y
 * los botones de debajo reciben el toque aunque el confeti los cubra; tampoco aparece en la accesibilidad. Se dibuja
 * en su propia capa (`graphicsLayer`) para que cada fotograma redibuje solo el confeti y no la tarjeta de debajo.
 * Se dispara cada vez que [trigger] cambia respecto al valor con el que apareció.
 */
@Composable
fun CelebrationBurst(trigger: Int, modifier: Modifier = Modifier, durationMs: Int = 1700, originY: Float = 0.45f) {
    val first = remember { trigger }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == first) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        progress.snapTo(0f)
    }
    val pieces = remember(trigger) { Confetti.pieces(seed = trigger * 31 + 7) }
    Canvas(modifier.graphicsLayer().clearAndSetSemantics { }) {
        val p = progress.value
        if (p <= 0f) return@Canvas
        val seconds = p * durationMs / 1000f
        val alpha = Confetti.alpha(p)
        // Destello central que se expande al inicio.
        if (p < 0.35f) {
            val glow = 1f - p / 0.35f
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFEF08A).copy(alpha = 0.55f * glow), Color.Transparent), Offset(size.width / 2, size.height * originY), size.minDimension * 0.4f * (0.4f + p)),
                size.minDimension * 0.4f * (0.4f + p), Offset(size.width / 2, size.height * originY)
            )
        }
        pieces.forEach { piece ->
            val (fx, fy) = Confetti.position(piece, seconds, originY = originY)
            val center = Offset(fx * size.width, fy * size.height)
            val s = piece.size * size.minDimension.coerceAtLeast(200f)
            val color = Color(piece.color).copy(alpha = alpha)
            rotate(piece.spin * seconds, pivot = center) {
                when (piece.shape) {
                    ConfettiPiece.Shape.RECT -> drawRect(color, Offset(center.x - s, center.y - s / 2), Size(s * 2, s))
                    ConfettiPiece.Shape.DOT -> drawCircle(color, s * 0.7f, center)
                    ConfettiPiece.Shape.STAR -> {
                        drawCircle(color.copy(alpha = alpha * 0.3f), s * 1.6f, center)
                        drawPath(star(center, s * 1.3f, s * 0.55f), color)
                    }
                }
            }
        }
    }
}

private fun star(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val ang = (-90.0 + k * 36.0) * PI / 180.0
        val x = center.x + (r * cos(ang)).toFloat()
        val y = center.y + (r * sin(ang)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Contador que sube o baja animado (gemas, racha, XP). */
@Composable
fun AnimatedCounter(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, format: (Int) -> String = { "$it" }) {
    val shown by animateIntAsState(value, tween(900), label = "counter")
    Text(format(shown), style = style, color = color, modifier = modifier)
}

/** Barra de progreso que se llena con física de resorte. */
@Composable
fun SpringProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    brush: Brush? = null
) {
    val animated by animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow),
        label = "progress"
    )
    val fill = brush ?: Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)))
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceIn(0f, 1f)).clip(RoundedCornerShape(height / 2)).background(fill))
    }
}
