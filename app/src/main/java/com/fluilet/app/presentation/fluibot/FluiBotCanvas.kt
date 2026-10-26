package com.fluilet.app.presentation.fluibot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.gamification.Scenario
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val Cyan = Color(0xFF22D3EE)
private val CyanSoft = Color(0xFF67E8F9)
private val Titanium = Color(0xFFF1F5F9)
private val TitaniumShade = Color(0xFFCBD5E1)
private val TitaniumDark = Color(0xFF94A3B8)
private val Screen = Color(0xFF0B1220)
private val Gold = Color(0xFFFBBF24)
private val GoldDeep = Color(0xFFD97706)
private val Magenta = Color(0xFFF472B6)
private val Flame = Color(0xFFF97316)
private val FlameBlue = Color(0xFF38BDF8)

/**
 * FluiBot, el compañero robótico evolutivo, dibujado con vectores en `Canvas` (sin imágenes).
 * Levita, parpadea y reacciona (ojos ★, salto de propulsión y partículas) al tocarlo o cuando
 * [celebrationTick] aumenta. Los [equipped] se dibujan en vivo y pueden combinarse todos a la vez.
 */
@Composable
fun FluiBotCanvas(
    stage: FluiBotStage,
    equipped: Set<String>,
    modifier: Modifier = Modifier,
    celebrationTick: Int = 0,
    onTap: (() -> Unit)? = null,
    /** Fondo dibujado detrás de FluiBot; `null` = transparente (miniaturas). */
    scenario: Scenario? = null
) {
    val transition = rememberInfiniteTransition(label = "fluibot")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "time"
    )
    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "blink"
    )
    val celebrate = remember { Animatable(0f) }
    var tapTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(celebrationTick, tapTick) {
        if (celebrationTick + tapTick > 0) {
            celebrate.snapTo(0f)
            celebrate.animateTo(1f, tween(1700, easing = LinearEasing))
            celebrate.snapTo(0f)
        }
    }

    val description = "FluiBot, ${stage.displayName}" + (scenario?.let { " en ${it.displayName}" } ?: "") +
        if (equipped.isEmpty()) "" else ", con ${equipped.mapNotNull { Accessory.fromId(it)?.displayName }.joinToString()}"

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures {
                    tapTick++
                    onTap?.invoke()
                }
            }
    ) {
        if (scenario != null) drawScenario(scenario, time)
        drawFluiBot(stage, equipped, time, blinkPhase, celebrate.value)
    }
}

// --- Marco de dibujo proporcional: el lienzo se trata como una cuadrícula de 100 x 100 unidades --------

private class Frame(val unit: Float, val ox: Float, val oy: Float) {
    fun x(v: Float) = ox + v * unit
    fun y(v: Float) = oy + v * unit
    fun o(x: Float, y: Float) = Offset(x(x), y(y))
    fun u(v: Float) = v * unit
    fun size(w: Float, h: Float) = Size(w * unit, h * unit)
}

/** Puntos de anclaje de cada etapa (en unidades) donde se colocan los accesorios. */
private class Anchors(
    val headTop: Float, val eyeY: Float, val eyeDx: Float, val headHalfW: Float, val neckY: Float,
    val bodyBottom: Float, val shoulderY: Float, val emblemX: Float, val emblemY: Float, val antennaX: Float, val antennaTipY: Float
)

private fun anchorsFor(stage: FluiBotStage) = when (stage) {
    FluiBotStage.NUCLEO -> Anchors(28f, 52f, 7f, 25f, 76f, 76f, 60f, 50f, 66f, 50f, 17f)
    FluiBotStage.EXPLORADOR -> Anchors(20f, 35f, 7f, 18f, 48f, 77f, 52f, 50f, 63f, 50f, 11f)
    FluiBotStage.GUARDIAN, FluiBotStage.SABIO -> Anchors(20f, 35f, 7f, 18f, 48f, 77f, 52f, 36f, 65f, 44f, 11f)
}

private fun DrawScope.drawFluiBot(stage: FluiBotStage, equipped: Set<String>, time: Float, blinkPhase: Float, celebrate: Float) {
    val unit = min(size.width, size.height) / 100f
    val f = Frame(unit, (size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f)
    val a = anchorsFor(stage)
    val jump = if (celebrate > 0f) -sin(PI.toFloat() * celebrate) * 10f else 0f
    val float = sin(time) * 1.8f
    val star = celebrate in 0.02f..0.9f
    val blink = if (blinkPhase > 0.94f) max(0.1f, abs((blinkPhase - 0.97f) / 0.03f)) else 1f

    // Sombra en el suelo: se encoge cuando FluiBot sube.
    val shadowScale = 1f - (float + jump) / -40f
    drawOval(Color(0x33000000), f.o(50f - 13f * shadowScale, 91f), f.size(26f * shadowScale, 4.5f))

    if (stage == FluiBotStage.SABIO) drawAura(f, time)

    translate(top = (float + jump) * unit) {
        // Capa trasera
        if (Accessory.CAPA_FOTONICA.id in equipped) drawCape(f, a, time)
        if (Accessory.ALAS_CYBER.id in equipped) drawWings(f, a, time)
        if (Accessory.JETPACK_PLASMA.id in equipped) drawJetpackBack(f, a)

        when (stage) {
            FluiBotStage.NUCLEO -> drawCore(f, a, time, blink, star)
            else -> drawBot(f, a, stage, time, blink, star)
        }

        // Capa delantera
        if (Accessory.JETPACK_PLASMA.id in equipped) drawJetpackFlames(f, a, time, 1f)
        else if (stage == FluiBotStage.SABIO) drawJetpackFlames(f, a, time, 0.55f, nozzlesOnly = true)
        if (Accessory.CORBATIN_LED.id in equipped) drawBowTie(f, a, time)
        if (Accessory.EMBLEMA_ESTELAR.id in equipped) drawEmblem(f, a, time)
        if (Accessory.AURICULARES_SONICOS.id in equipped) drawHeadphones(f, a, time)
        if (Accessory.VISOR_HOLOGRAFICO.id in equipped) drawVisor(f, a)
        if (Accessory.ANTENA_PRISMA.id in equipped) drawPrism(f, a, stage, time)
        if (Accessory.CORONA_CUANTICA.id in equipped) drawCrown(f, a, time)
        if (Accessory.MINI_DRON.id in equipped) drawDrone(f, time)
    }

    if (celebrate > 0f) drawEnergyParticles(f, celebrate)
}

// --- Cuerpo por etapa ----------------------------------------------------------------------------

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val ang = (-90.0 + k * 36.0) * PI / 180.0
        val px = cx + (r * cos(ang)).toFloat()
        val py = cy + (r * sin(ang)).toFloat()
        if (k == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

private fun DrawScope.drawEyes(f: Frame, cx: Float, eyeY: Float, dx: Float, blink: Float, star: Boolean) {
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * dx
        drawCircle(Cyan.copy(alpha = 0.28f), f.u(5.5f), f.o(ex, eyeY))
        if (star) {
            drawPath(starPath(f.x(ex), f.y(eyeY), f.u(4.6f), f.u(2f)), Brush.radialGradient(listOf(Color.White, CyanSoft, Cyan), f.o(ex, eyeY), f.u(5f)))
        } else {
            val w = 5.2f
            val h = 7.2f * blink
            drawOval(Brush.verticalGradient(listOf(CyanSoft, Cyan), f.y(eyeY - h / 2), f.y(eyeY + h / 2)), f.o(ex - w / 2, eyeY - h / 2), f.size(w, h))
        }
    }
}

/** Etapa 1: esfera tecnológica flotante con visor LED y anillos magnéticos. */
private fun DrawScope.drawCore(f: Frame, a: Anchors, time: Float, blink: Float, star: Boolean) {
    val c = f.o(50f, 52f)
    // anillos magnéticos (detrás de la esfera)
    for ((i, deg) in listOf(time * 57.3f, -time * 57.3f * 0.7f + 60f).withIndex()) {
        rotate(deg, pivot = c) {
            drawOval(Cyan.copy(alpha = 0.55f - i * 0.15f), f.o(50f - 33f, 52f - 8f), f.size(66f, 16f), style = Stroke(f.u(1.3f)))
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color.White, Titanium, TitaniumDark), f.o(43f, 44f), f.u(30f)), f.u(24f), c)
    drawCircle(Cyan.copy(alpha = 0.35f), f.u(24f), c, style = Stroke(f.u(0.9f)))
    // visor LED
    drawRoundRect(Screen, f.o(35f, 45f), f.size(30f, 15f), CornerRadius(f.u(7.5f)))
    drawRoundRect(Cyan.copy(alpha = 0.4f), f.o(35f, 45f), f.size(30f, 15f), CornerRadius(f.u(7.5f)), style = Stroke(f.u(0.7f)))
    drawEyes(f, 50f, 52.5f, 7f, blink, star)
}

/** Etapas 2–4: robot compacto blanco/titanio con pantalla facial curva, antena(s) y manos flotantes. */
private fun DrawScope.drawBot(f: Frame, a: Anchors, stage: FluiBotStage, time: Float, blink: Float, star: Boolean) {
    val advanced = stage == FluiBotStage.GUARDIAN || stage == FluiBotStage.SABIO
    val master = stage == FluiBotStage.SABIO
    val trim = if (master) Gold else Cyan

    // manos flotantes
    for (side in listOf(-1f, 1f)) {
        val hx = 50f + side * (26f + sin(time + side) * 1.2f)
        val hy = 64f + cos(time * 1.3f + side) * 1.8f
        drawCircle(Brush.radialGradient(listOf(Color.White, TitaniumShade, TitaniumDark), f.o(hx - 1.5f, hy - 1.5f), f.u(6f)), f.u(4.6f), f.o(hx, hy))
        drawCircle(trim.copy(alpha = 0.7f), f.u(4.6f), f.o(hx, hy), style = Stroke(f.u(0.6f)))
    }
    // cuerpo
    drawRoundRect(
        Brush.verticalGradient(listOf(Titanium, TitaniumShade, TitaniumDark), f.y(47f), f.y(78f)),
        f.o(36f, 47f), f.size(28f, 31f), CornerRadius(f.u(12f))
    )
    if (master) drawRoundRect(Gold, f.o(36f, 47f), f.size(28f, 31f), CornerRadius(f.u(12f)), style = Stroke(f.u(0.9f)))
    // hombreras (etapa 3 y 4)
    if (advanced) {
        for (side in listOf(-1f, 1f)) {
            val sx = 50f + side * 17f - 5f
            drawRoundRect(if (master) GoldDeep else TitaniumDark, f.o(sx, 49f), f.size(10f, 8f), CornerRadius(f.u(3.5f)))
            drawRoundRect(trim, f.o(sx, 49f), f.size(10f, 8f), CornerRadius(f.u(3.5f)), style = Stroke(f.u(0.6f)))
        }
        // núcleo de energía en el pecho
        val pulse = 0.75f + 0.25f * sin(time * 3f)
        drawCircle(Cyan.copy(alpha = 0.35f * pulse), f.u(8f), f.o(50f, 62f))
        drawCircle(Brush.radialGradient(listOf(Color.White, CyanSoft, Cyan), f.o(50f, 62f), f.u(5f)), f.u(4.2f * pulse.coerceAtLeast(0.85f)), f.o(50f, 62f))
        if (master) drawCircle(Gold, f.u(6f), f.o(50f, 62f), style = Stroke(f.u(0.8f)))
    }
    // antena(s)
    val antennae = if (advanced) listOf(44f, 56f) else listOf(50f)
    for (ax in antennae) {
        drawLine(TitaniumDark, f.o(ax, 21f), f.o(ax, 12.5f), f.u(1.2f))
        val glow = 0.7f + 0.3f * sin(time * 4f + ax)
        drawCircle(trim.copy(alpha = 0.4f * glow), f.u(4f), f.o(ax, 11f))
        drawCircle(trim, f.u(2.2f), f.o(ax, 11f))
    }
    // cabeza
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White, Titanium, TitaniumShade), f.y(20f), f.y(48f)),
        f.o(32f, 20f), f.size(36f, 28f), CornerRadius(f.u(13f))
    )
    if (master) drawRoundRect(Gold, f.o(32f, 20f), f.size(36f, 28f), CornerRadius(f.u(13f)), style = Stroke(f.u(0.9f)))
    // pantalla facial negra curva
    drawRoundRect(Screen, f.o(36f, 25f), f.size(28f, 19f), CornerRadius(f.u(9.5f)))
    drawArc(Color.White.copy(alpha = 0.12f), 200f, 70f, false, f.o(39f, 26f), f.size(22f, 14f), style = Stroke(f.u(0.8f)))
    drawEyes(f, 50f, 35f, 7f, blink, star)
    // sonrisa LED sutil
    drawArc(Cyan.copy(alpha = 0.6f), 20f, 140f, false, f.o(46f, 36f), f.size(8f, 5f), style = Stroke(f.u(0.7f)))
}

private fun DrawScope.drawAura(f: Frame, time: Float) {
    val pulse = 0.8f + 0.2f * sin(time * 2f)
    drawCircle(
        Brush.radialGradient(listOf(Cyan.copy(alpha = 0.34f * pulse), Gold.copy(alpha = 0.10f), Color.Transparent), f.o(50f, 52f), f.u(50f)),
        f.u(50f), f.o(50f, 52f)
    )
    for (i in 0 until 6) {
        val ang = time + i * (PI / 3).toFloat()
        drawCircle(if (i % 2 == 0) Gold else CyanSoft, f.u(1.1f), f.o(50f + 40f * cos(ang), 52f + 30f * sin(ang)))
    }
}

// --- Accesorios -------------------------------------------------------------------------------------

private fun DrawScope.drawFlame(f: Frame, x: Float, y: Float, length: Float, width: Float) {
    val outer = Path().apply {
        moveTo(f.x(x - width), f.y(y)); quadraticTo(f.x(x - width * 0.6f), f.y(y + length * 0.6f), f.x(x), f.y(y + length))
        quadraticTo(f.x(x + width * 0.6f), f.y(y + length * 0.6f), f.x(x + width), f.y(y)); close()
    }
    drawPath(outer, Brush.verticalGradient(listOf(FlameBlue, FlameBlue.copy(alpha = 0f)), f.y(y), f.y(y + length)))
    val inner = Path().apply {
        moveTo(f.x(x - width * 0.5f), f.y(y)); lineTo(f.x(x), f.y(y + length * 0.62f)); lineTo(f.x(x + width * 0.5f), f.y(y)); close()
    }
    drawPath(inner, Brush.verticalGradient(listOf(Flame, Gold.copy(alpha = 0f)), f.y(y), f.y(y + length * 0.62f)))
}

private fun DrawScope.drawJetpackBack(f: Frame, a: Anchors) {
    for (side in listOf(-1f, 1f)) {
        val x = 50f + side * 15f - 3.5f
        drawRoundRect(TitaniumDark, f.o(x, a.shoulderY + 2f), f.size(7f, 20f), CornerRadius(f.u(3f)))
        drawRoundRect(Cyan.copy(alpha = 0.6f), f.o(x, a.shoulderY + 2f), f.size(7f, 20f), CornerRadius(f.u(3f)), style = Stroke(f.u(0.5f)))
    }
}

private fun DrawScope.drawJetpackFlames(f: Frame, a: Anchors, time: Float, strength: Float, nozzlesOnly: Boolean = false) {
    val flicker = 0.8f + 0.2f * sin(time * 9f)
    val xs = if (nozzlesOnly) listOf(43f, 57f) else listOf(50f - 15f, 50f + 15f)
    val y = if (nozzlesOnly) 79f else a.shoulderY + 22f
    for (x in xs) {
        if (nozzlesOnly) drawRoundRect(Screen, f.o(x - 2.5f, y - 1.5f), f.size(5f, 3f), CornerRadius(f.u(1.2f)))
        drawFlame(f, x, y, 13f * flicker * strength, 2.8f * strength + 0.4f)
    }
}

private fun DrawScope.drawCape(f: Frame, a: Anchors, time: Float) {
    val topY = a.neckY + 1f
    val bottom = a.bodyBottom + 11f
    val path = Path().apply {
        moveTo(f.x(38f), f.y(topY))
        lineTo(f.x(27f), f.y(bottom))
        for (i in 0..8) {
            val px = 27f + (73f - 27f) * i / 8f
            lineTo(f.x(px), f.y(bottom + sin(time * 2.2f + i * 0.9f) * 2f))
        }
        lineTo(f.x(62f), f.y(topY))
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.75f), Magenta.copy(alpha = 0.45f)), f.y(topY), f.y(bottom)))
    drawPath(path, CyanSoft.copy(alpha = 0.8f), style = Stroke(f.u(0.7f)))
}

private fun DrawScope.drawWings(f: Frame, a: Anchors, time: Float) {
    val attachY = a.shoulderY + 6f
    val flap = sin(time * 2f) * 4f
    for (side in listOf(-1f, 1f)) {
        val pivot = f.o(50f + side * 17f, attachY)
        rotate(side * flap, pivot) {
            val tips = listOf(Offset(-31f, -17f), Offset(-35f, -4f), Offset(-30f, 9f))
            for ((i, t) in tips.withIndex()) {
                val blade = Path().apply {
                    moveTo(pivot.x, pivot.y + f.u(-2f + i * 2.5f))
                    lineTo(pivot.x + side * f.u(-t.x) * -1f, pivot.y + f.u(t.y))
                    lineTo(pivot.x + side * f.u(-t.x) * -0.62f, pivot.y + f.u(t.y + 6f))
                    close()
                }
                drawPath(blade, Brush.linearGradient(listOf(Cyan.copy(alpha = 0.85f), CyanSoft.copy(alpha = 0.55f)), pivot, Offset(pivot.x + side * f.u(34f), pivot.y)))
                drawPath(blade, Gold.copy(alpha = 0.9f), style = Stroke(f.u(0.6f)))
            }
        }
    }
}

private fun DrawScope.drawBowTie(f: Frame, a: Anchors, time: Float) {
    val y = a.neckY
    val glow = 0.7f + 0.3f * sin(time * 3f)
    for (side in listOf(-1f, 1f)) {
        val p = Path().apply {
            moveTo(f.x(50f), f.y(y)); lineTo(f.x(50f + side * 9f), f.y(y - 5f)); lineTo(f.x(50f + side * 9f), f.y(y + 5f)); close()
        }
        drawPath(p, Brush.horizontalGradient(listOf(Magenta, Color(0xFFA855F7)), f.x(50f), f.x(50f + side * 9f)))
        drawPath(p, Color.White.copy(alpha = 0.6f * glow), style = Stroke(f.u(0.5f)))
    }
    drawRoundRect(Cyan, f.o(47.8f, y - 2.6f), f.size(4.4f, 5.2f), CornerRadius(f.u(1.4f)))
}

private fun DrawScope.drawEmblem(f: Frame, a: Anchors, time: Float) {
    val pulse = 0.85f + 0.15f * sin(time * 3f)
    drawCircle(Gold.copy(alpha = 0.35f * pulse), f.u(6.5f), f.o(a.emblemX, a.emblemY))
    drawPath(starPath(f.x(a.emblemX), f.y(a.emblemY), f.u(4.6f), f.u(2f)), Brush.verticalGradient(listOf(Color(0xFFFEF08A), Gold, GoldDeep), f.y(a.emblemY - 5f), f.y(a.emblemY + 5f)))
}

private fun DrawScope.drawHeadphones(f: Frame, a: Anchors, time: Float) {
    val cx = 50f
    val left = cx - a.headHalfW
    val right = cx + a.headHalfW
    // arco sobre la cabeza
    drawArc(TitaniumDark, 180f, 180f, false, f.o(left, a.headTop - 2f), f.size(2 * a.headHalfW, 2 * (a.eyeY - a.headTop) + 4f), style = Stroke(f.u(1.8f)))
    val wave = 0.4f + 0.4f * sin(time * 5f)
    for (x in listOf(left, right)) {
        drawCircle(Color(0xFF1E293B), f.u(5f), f.o(x, a.eyeY))
        drawCircle(Cyan, f.u(5f), f.o(x, a.eyeY), style = Stroke(f.u(0.8f)))
        val dir = if (x < cx) -1f else 1f
        for (k in 1..2) {
            drawArc(Cyan.copy(alpha = wave / k), if (dir < 0) 140f else -40f, 80f, false,
                f.o(x + dir * (4f + k * 2.2f) - 3f, a.eyeY - 4f), f.size(6f, 8f), style = Stroke(f.u(0.6f)))
        }
    }
}

private fun DrawScope.drawVisor(f: Frame, a: Anchors) {
    val w = 2 * (a.eyeDx + 7.5f)
    drawRoundRect(Cyan.copy(alpha = 0.22f), f.o(50f - w / 2, a.eyeY - 7f), f.size(w, 14f), CornerRadius(f.u(7f)))
    drawRoundRect(CyanSoft, f.o(50f - w / 2, a.eyeY - 7f), f.size(w, 14f), CornerRadius(f.u(7f)), style = Stroke(f.u(0.8f)))
    drawLine(Color.White.copy(alpha = 0.6f), f.o(50f - w / 2 + 4f, a.eyeY + 4f), f.o(50f - w / 2 + 9f, a.eyeY - 4f), f.u(0.7f))
}

private fun DrawScope.drawPrism(f: Frame, a: Anchors, stage: FluiBotStage, time: Float) {
    val tipX = a.antennaX
    val tipY = a.antennaTipY - 1f
    if (stage == FluiBotStage.NUCLEO) drawLine(TitaniumDark, f.o(tipX, a.headTop), f.o(tipX, tipY + 3f), f.u(1.1f))
    val r = 3.8f
    val cx = f.x(tipX)
    val cy = f.y(tipY)
    val rr = f.u(r)
    val colors = listOf(Magenta, CyanSoft, Color(0xFFFDE047), Color(0xFF4ADE80))
    val pts = listOf(Offset(cx, cy - rr), Offset(cx + rr, cy), Offset(cx, cy + rr), Offset(cx - rr, cy))
    for (i in 0 until 4) {
        val tri = Path().apply { moveTo(cx, cy); lineTo(pts[i].x, pts[i].y); lineTo(pts[(i + 1) % 4].x, pts[(i + 1) % 4].y); close() }
        drawPath(tri, colors[i].copy(alpha = 0.9f))
    }
    drawCircle(Color.White.copy(alpha = 0.35f + 0.3f * sin(time * 4f)), f.u(1.1f), Offset(cx - rr * 0.25f, cy - rr * 0.3f))
}

private fun DrawScope.drawCrown(f: Frame, a: Anchors, time: Float) {
    val bob = sin(time * 2f) * 0.8f
    val baseY = a.headTop - (if (a.antennaTipY < a.headTop) 2.5f else 2f) + bob
    val x = 50f
    val path = Path().apply {
        moveTo(f.x(x - 8f), f.y(baseY)); lineTo(f.x(x - 8f), f.y(baseY - 6f)); lineTo(f.x(x - 4f), f.y(baseY - 3f))
        lineTo(f.x(x), f.y(baseY - 8f)); lineTo(f.x(x + 4f), f.y(baseY - 3f)); lineTo(f.x(x + 8f), f.y(baseY - 6f))
        lineTo(f.x(x + 8f), f.y(baseY)); close()
    }
    drawCircle(Gold.copy(alpha = 0.25f + 0.15f * sin(time * 3f)), f.u(11f), f.o(x, baseY - 3f))
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFEF08A), Gold, GoldDeep), f.y(baseY - 8f), f.y(baseY)))
    drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(f.u(0.4f)))
    for (px in listOf(x - 8f, x, x + 8f)) drawCircle(CyanSoft, f.u(0.9f), f.o(px, baseY - if (px == x) 8f else 6f))
}

private fun DrawScope.drawDrone(f: Frame, time: Float) {
    val ang = time * 1.4f
    val x = 50f + 40f * cos(ang)
    val y = 36f + 9f * sin(ang) + sin(time * 4f) * 0.8f
    val c = f.o(x, y)
    drawCircle(Cyan.copy(alpha = 0.25f), f.u(6.5f), c)
    drawCircle(Brush.radialGradient(listOf(Color.White, TitaniumShade), Offset(c.x - f.u(1.2f), c.y - f.u(1.2f)), f.u(5f)), f.u(3.8f), c)
    drawCircle(Cyan, f.u(3.8f), c, style = Stroke(f.u(0.5f)))
    // hélice
    rotate(time * 900f, pivot = Offset(c.x, c.y - f.u(4.4f))) {
        drawLine(TitaniumDark, Offset(c.x - f.u(4f), c.y - f.u(4.4f)), Offset(c.x + f.u(4f), c.y - f.u(4.4f)), f.u(0.7f))
    }
    drawCircle(if (sin(time * 6f) > 0f) Color(0xFFFB7185) else Color(0xFF7F1D1D), f.u(0.9f), c)
}

private fun DrawScope.drawEnergyParticles(f: Frame, progress: Float) {
    val count = 18
    for (i in 0 until count) {
        val ang = i * (2 * PI / count).toFloat() + 0.3f
        val dist = (10f + 40f * progress) * (0.7f + 0.3f * ((i * 7) % 5) / 4f)
        val color = when (i % 3) { 0 -> Cyan; 1 -> Gold; else -> Color.White }
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val c = f.o(50f + dist * cos(ang), 46f + dist * sin(ang) * 0.9f)
        drawCircle(color.copy(alpha = alpha), f.u(2f * (1f - 0.5f * progress)), c)
        if (i % 2 == 0) drawPath(starPath(c.x, c.y, f.u(2.6f * alpha + 0.4f), f.u(1f)), color.copy(alpha = alpha * 0.8f))
    }
}
