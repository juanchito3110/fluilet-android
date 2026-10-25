package com.fluilet.app.presentation.fluibot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val Wood = Color(0xFF8B5A2B)
private val WoodDark = Color(0xFF5C3A1A)
private val GoldLight = Color(0xFFFEF08A)
private val GoldMid = Color(0xFFFBBF24)
private val GoldDark = Color(0xFFB45309)
private val Confetti = listOf(
    Color(0xFFF472B6), Color(0xFF22D3EE), Color(0xFFFDE047), Color(0xFF4ADE80),
    Color(0xFFA78BFA), Color(0xFFFB923C), Color.White
)

/**
 * Cofre del tesoro dibujado en `Canvas`. Cerrado late (pulso suave) invitando a tocarlo; al abrirse
 * (tras el toque) levanta la tapa, emite un haz de luz y lanza una ráfaga de confeti.
 */
@Composable
fun TreasureChest(
    opened: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "chest")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val glow by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "glow"
    )
    val lid = remember { Animatable(0f) }
    LaunchedEffect(opened) { lid.animateTo(if (opened) 1f else 0f, tween(600, easing = FastOutSlowInEasing)) }

    Box(
        modifier = modifier
            .semantics {
                contentDescription = if (opened) "Cofre de recompensas abierto" else "Cofre de recompensas cerrado. Tócalo para reclamar tus premios"
            }
            .clickable(enabled = !opened, role = Role.Button, onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = if (opened) 1f else pulse
            scale(s, s, pivot = center) { drawChest(lid.value, glow, opened) }
        }
        ConfettiBurst(trigger = if (opened) 1 else 0, modifier = Modifier.fillMaxSize())
    }
}

private fun DrawScope.drawChest(lid: Float, glow: Float, opened: Boolean) {
    val unit = min(size.width, size.height * 1.25f) / 100f
    val ox = (size.width - 100f * unit) / 2f
    val oy = (size.height - 80f * unit) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * unit, oy + y * unit)
    fun sz(w: Float, h: Float) = Size(w * unit, h * unit)

    // resplandor
    drawCircle(Brush.radialGradient(listOf(GoldMid.copy(alpha = glow * 0.55f), Color.Transparent), p(50f, 40f), 55f * unit), 55f * unit, p(50f, 40f))

    // haz de luz al abrir
    if (lid > 0.05f) {
        val beam = Path().apply {
            moveTo(p(32f, 44f).x, p(32f, 44f).y); lineTo(p(14f, -6f).x, p(14f, -6f).y)
            lineTo(p(86f, -6f).x, p(86f, -6f).y); lineTo(p(68f, 44f).x, p(68f, 44f).y); close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, GoldLight.copy(alpha = 0.55f * lid)), p(0f, -6f).y, p(0f, 44f).y))
    }

    // cuerpo
    drawRoundRect(Brush.verticalGradient(listOf(Wood, WoodDark), p(0f, 38f).y, p(0f, 74f).y), p(14f, 38f), sz(72f, 36f), CornerRadius(4f * unit))
    drawRoundRect(GoldMid, p(44f, 38f), sz(12f, 36f))
    drawRoundRect(GoldDark, p(14f, 38f), sz(72f, 36f), CornerRadius(4f * unit), style = Stroke(1.2f * unit))
    // interior visible al abrir (gemas)
    if (lid > 0.4f) {
        for ((i, c) in listOf(Color(0xFF22D3EE), Color(0xFFF472B6), Color(0xFFFDE047), Color(0xFF4ADE80)).withIndex()) {
            drawCircle(c, 3.2f * unit, p(26f + i * 15f, 40f))
        }
    }
    // cerradura
    drawCircle(Brush.verticalGradient(listOf(GoldLight, GoldDark), p(0f, 38f).y, p(0f, 50f).y), 5f * unit, p(50f, 45f))
    drawRoundRect(WoodDark, p(48.6f, 44f), sz(2.8f, 5f), CornerRadius(1f * unit))

    // tapa (gira alrededor de la bisagra trasera)
    rotate(-38f * lid, pivot = p(14f, 38f)) {
        val top = Path().apply {
            moveTo(p(14f, 38f).x, p(14f, 38f).y)
            lineTo(p(14f, 28f).x, p(14f, 28f).y)
            quadraticTo(p(50f, 4f).x, p(50f, 4f).y, p(86f, 28f).x, p(86f, 28f).y)
            lineTo(p(86f, 38f).x, p(86f, 38f).y)
            close()
        }
        drawPath(top, Brush.verticalGradient(listOf(Color(0xFFA9703A), Wood), p(0f, 8f).y, p(0f, 38f).y))
        drawPath(top, GoldDark, style = Stroke(1.2f * unit))
        drawPath(Path().apply {
            moveTo(p(44f, 38f).x, p(44f, 38f).y); lineTo(p(44f, 14f).x, p(44f, 14f).y)
            lineTo(p(56f, 14f).x, p(56f, 14f).y); lineTo(p(56f, 38f).x, p(56f, 38f).y); close()
        }, GoldMid)
    }
    // destellos
    if (!opened) {
        val a = glow
        for ((x, y) in listOf(20f to 20f, 80f to 18f, 90f to 46f)) {
            val c = p(x, y)
            drawLine(Color.White.copy(alpha = a), Offset(c.x - 2f * unit, c.y), Offset(c.x + 2f * unit, c.y), 0.7f * unit)
            drawLine(Color.White.copy(alpha = a), Offset(c.x, c.y - 2f * unit), Offset(c.x, c.y + 2f * unit), 0.7f * unit)
        }
    }
}

/** Ráfaga de confeti en `Canvas`: se lanza cuando [trigger] pasa a un valor distinto de 0. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val pieces = remember(trigger) { ConfettiPiece.generate(trigger, 70) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }
    Canvas(modifier) {
        val t = progress.value
        if (trigger <= 0 || t <= 0f || t >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.45f)
        val unit = min(size.width, size.height) / 100f
        for (piece in pieces) {
            val x = origin.x + piece.vx * t * unit * 60f
            val y = origin.y + (piece.vy * t + 1.35f * t * t) * unit * 60f
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            rotate(piece.spin * t * 360f, pivot = Offset(x, y)) {
                if (piece.round) drawCircle(piece.color.copy(alpha = alpha), piece.size * unit, Offset(x, y))
                else drawRect(piece.color.copy(alpha = alpha), Offset(x - piece.size * unit, y - piece.size * unit * 0.5f), Size(piece.size * unit * 2f, piece.size * unit))
            }
        }
    }
}

/** Pieza de confeti (determinista por [trigger] para poder probarla). */
data class ConfettiPiece(val vx: Float, val vy: Float, val spin: Float, val size: Float, val color: Color, val round: Boolean) {
    companion object {
        fun generate(seed: Int, count: Int): List<ConfettiPiece> {
            val random = Random(seed)
            return List(count) {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val speed = 0.25f + random.nextFloat() * 0.6f
                ConfettiPiece(
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 0.55f, // la ráfaga sale hacia arriba y luego cae
                    spin = (random.nextFloat() - 0.5f) * 4f,
                    size = 0.9f + random.nextFloat() * 1.4f,
                    color = Confetti[random.nextInt(Confetti.size)],
                    round = random.nextBoolean()
                )
            }
        }
    }
}
