package com.fluilet.app.presentation.fluibot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.gamification.Scenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val Cyan = Color(0xFF22D3EE)
private val CyanSoft = Color(0xFF67E8F9)
private val Titanium = Color(0xFFF1F5F9)
private val TitaniumShade = Color(0xFFCBD5E1)
private val TitaniumDark = Color(0xFF94A3B8)
private val Steel = Color(0xFF64748B)
private val Screen = Color(0xFF0B1220)
private val Gold = Color(0xFFFBBF24)
private val GoldDeep = Color(0xFFD97706)
private val GoldLight = Color(0xFFFEF08A)
private val Magenta = Color(0xFFF472B6)
private val Violet = Color(0xFFA855F7)
private val Flame = Color(0xFFF97316)
private val FlameBlue = Color(0xFF38BDF8)
private val Blush = Color(0x66FB7185)
private val Paper = Color(0xFFFFFBEB)
private val BookCover = Color(0xFF7B63C6)
private val HatTan = Color(0xFFD6A65A)
private val HatBrown = Color(0xFF8B5E2B)

private const val TWO_PI = (2 * PI).toFloat()

/** Fase fija de las miniaturas sin animación (pose agradable: brazos a los lados, a media altura). */
private const val STILL_PHASE = 0.9f

/**
 * FluiBot 2.0, el compañero robótico evolutivo, dibujado con vectores en `Canvas` (sin imágenes):
 * levita con una onda senoidal, inclina cabeza y torso con un ritmo desfasado, mueve los brazos articulados y
 * proyecta una sombra que se contrae al subir. Su visor LED parpadea de forma orgánica y cambia de expresión con
 * [mood]; la antena emite pulsos de energía y el reactor del pecho late. Cada etapa mejora el chasis (acabado
 * metálico, hombreras, aura) y los [equipped] se acoplan al cuerpo y se mueven con él.
 *
 * Al tocarlo (o cuando [celebrationTick] aumenta) salta con física de resorte, saluda y lanza un destello de
 * estrellas. [animated] = `false` dibuja una miniatura quieta (álbumes, listas) sin coste de animación.
 */
@Composable
fun FluiBotCanvas(
    stage: FluiBotStage,
    equipped: Set<String>,
    modifier: Modifier = Modifier,
    celebrationTick: Int = 0,
    onTap: (() -> Unit)? = null,
    /** Fondo dibujado detrás de FluiBot; `null` = transparente (miniaturas). */
    scenario: Scenario? = null,
    mood: FluiBotMood = FluiBotMood.HAPPY,
    animated: Boolean = true,
    interactive: Boolean = true
) {
    val clock = rememberBotClock(animated)
    val blink = remember { Animatable(1f) }
    val reaction = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }
    var taps by remember { mutableIntStateOf(0) }
    val firstTick = remember { celebrationTick }
    val currentOnTap by rememberUpdatedState(onTap)

    if (animated) {
        LaunchedEffect(Unit) {
            val random = Random(System.nanoTime())
            while (true) {
                delay(FluiBotMotion.nextBlinkDelay(random))
                repeat(if (FluiBotMotion.doubleBlink(random)) 2 else 1) {
                    blink.animateTo(0.08f, tween(70))
                    blink.animateTo(1f, tween(110))
                }
            }
        }
    }

    LaunchedEffect(celebrationTick, taps) {
        if (celebrationTick == firstTick && taps == 0) return@LaunchedEffect
        // Salto alegre: sube rápido y aterriza con un resorte que rebota.
        launch {
            jump.animateTo(-FluiBotMotion.JUMP_HEIGHT, tween(170, easing = FastOutSlowInEasing))
            jump.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
        }
        reaction.snapTo(0f)
        reaction.animateTo(1f, tween(1300, easing = LinearEasing))
        reaction.snapTo(0f)
    }

    val description = buildString {
        append("FluiBot, etapa ${stage.number}: ${stage.displayName}, ${mood.description}")
        scenario?.let { append(", en ${it.displayName}") }
        if (equipped.isNotEmpty()) append(", con ${equipped.mapNotNull { Accessory.fromId(it)?.displayName }.joinToString()}")
    }
    val react = {
        taps++
        currentOnTap?.invoke()
    }
    val touch = if (interactive) {
        Modifier
            .pointerInput(Unit) { detectTapGestures { react() } }
            .semantics { onClick(label = "Hacer saltar a FluiBot") { react(); true } }
    } else Modifier

    Canvas(modifier.semantics { contentDescription = description }.then(touch)) {
        val t = clock.value
        if (scenario != null) drawScenario(scenario, t)
        drawFluiBot(stage, equipped, Pose(t, blink.value, reaction.value, jump.value, mood))
    }
}

@Composable
private fun rememberBotClock(animated: Boolean): State<Float> {
    if (!animated) return remember { mutableFloatStateOf(STILL_PHASE) }
    val transition = rememberInfiniteTransition(label = "fluibot")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "time"
    )
}

/** Estado de un cuadro: fase del ciclo, párpado (1 abierto), progreso de la reacción al toque y salto. */
private class Pose(val t: Float, val blink: Float, val reaction: Float, val jump: Float, val mood: FluiBotMood) {
    val reacting: Boolean get() = reaction > 0f

    /** Envolvente del saludo durante la reacción al toque. */
    val wave: Float get() = if (reacting) sin(PI.toFloat() * reaction) else 0f

    /** La reacción al toque pone cara de celebración. */
    val face: FluiBotMood get() = if (reacting) FluiBotMood.CELEBRATING else mood
}

// --- Marco de dibujo proporcional: el lienzo se trata como una cuadrícula de 100 x 100 unidades --------

private class Frame(val unit: Float, val ox: Float, val oy: Float) {
    fun x(v: Float) = ox + v * unit
    fun y(v: Float) = oy + v * unit
    fun o(x: Float, y: Float) = Offset(x(x), y(y))
    fun u(v: Float) = v * unit
    fun size(w: Float, h: Float) = Size(w * unit, h * unit)
}

/** Puntos de anclaje de cada etapa (en unidades): cuerpo, cabeza, visor y dónde se acoplan los accesorios. */
private class Anchors(
    val core: Boolean,
    val headTop: Float, val headHalfW: Float, val headPivotY: Float,
    val eyeY: Float, val eyeDx: Float, val mouthY: Float,
    val visorX: Float, val visorY: Float, val visorW: Float, val visorH: Float,
    val neckY: Float, val torsoPivotY: Float, val bodyBottom: Float,
    val shoulderDx: Float, val shoulderY: Float, val upperArm: Float, val forearm: Float, val hand: Float,
    val reactorY: Float, val emblemX: Float, val emblemY: Float,
    val antennas: List<Float>, val antennaTipY: Float, val bookY: Float
)

private fun anchorsFor(stage: FluiBotStage) = when (stage) {
    FluiBotStage.NUCLEO -> Anchors(
        core = true, headTop = 29f, headHalfW = 23f, headPivotY = 52f,
        eyeY = 51f, eyeDx = 7f, mouthY = 56.3f, visorX = 36f, visorY = 44f, visorW = 28f, visorH = 15f,
        neckY = 72f, torsoPivotY = 52f, bodyBottom = 75f,
        shoulderDx = 20f, shoulderY = 58f, upperArm = 6.5f, forearm = 5.5f, hand = 3f,
        reactorY = 67.5f, emblemX = 61f, emblemY = 63f, antennas = listOf(50f), antennaTipY = 18f, bookY = 72f
    )
    else -> Anchors(
        core = false, headTop = 18f, headHalfW = 18f, headPivotY = 47f,
        eyeY = 32.2f, eyeDx = 7f, mouthY = 38.4f, visorX = 36f, visorY = 23f, visorW = 28f, visorH = 19f,
        neckY = 47.5f, torsoPivotY = 62f, bodyBottom = 77f,
        shoulderDx = 16.5f, shoulderY = 52f, upperArm = 9f, forearm = 8f, hand = 3.6f,
        reactorY = 61f, emblemX = 41f, emblemY = 56f,
        antennas = if (stage == FluiBotStage.EXPLORADOR) listOf(50f) else listOf(43f, 57f),
        antennaTipY = 9f, bookY = 64f
    )
}

private fun trimFor(stage: FluiBotStage) = when (stage) {
    FluiBotStage.SABIO -> Gold
    FluiBotStage.GUARDIAN -> CyanSoft
    else -> Cyan
}

private fun rad(degrees: Float) = degrees * PI.toFloat() / 180f

private fun DrawScope.drawFluiBot(stage: FluiBotStage, equipped: Set<String>, p: Pose) {
    val unit = min(size.width, size.height) / 100f
    val f = Frame(unit, (size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f)
    val a = anchorsFor(stage)
    val trim = trimFor(stage)
    val face = p.face
    fun has(accessory: Accessory) = accessory.id in equipped

    // Sombra elíptica del suelo: se contrae y aclara cuando FluiBot sube.
    val offsetY = FluiBotMotion.hover(p.t) + p.jump
    val shadow = FluiBotMotion.shadowScale(FluiBotMotion.HOVER_AMPLITUDE - offsetY)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.30f * shadow), Color.Transparent), f.o(50f, 93f), f.u(16f * shadow)),
        f.o(50f - 16f * shadow, 91f), f.size(32f * shadow, 5f * shadow)
    )

    if (stage.number >= 3) drawStageAura(f, stage, p.t)
    if (has(Accessory.AURA_PARTICULAS)) drawParticleAura(f, p.t, front = false)
    if (has(Accessory.MINI_DRON)) drawDrone(f, p.t, front = false)

    translate(top = offsetY * unit) {
        rotate(FluiBotMotion.torsoTilt(p.t), pivot = f.o(50f, a.torsoPivotY)) {
            if (has(Accessory.CAPA_FOTONICA)) drawCape(f, a, p.t)
            if (has(Accessory.ALAS_CYBER)) drawWings(f, a, p.t)
            if (has(Accessory.JETPACK_PLASMA)) drawJetpackBack(f, a)
            if (a.core) drawCoreRings(f, p.t, front = false)
            drawThruster(f, a, stage, p.t)
            if (!a.core) {
                drawTorso(f, a, stage, p.t)
                drawFrontBody(f, a, stage, equipped, p)
            } else {
                drawArms(f, a, stage, p)
            }
        }
        rotate(FluiBotMotion.headTilt(p.t), pivot = f.o(50f, a.headPivotY)) {
            drawAntennas(f, a, trim, p, prismLift = if (has(Accessory.SOMBRERO_EXPLORADOR)) 4f else 0f)
            if (a.core) drawCoreSphere(f, a, p.t) else drawHead(f, a, stage, p.t)
            drawFace(f, a, trim, face, p)
            if (stage == FluiBotStage.SABIO) drawHalo(f, a, p.t)
            if (has(Accessory.AURICULARES_SONICOS)) drawHeadphones(f, a, p.t)
            if (has(Accessory.VISOR_HOLOGRAFICO)) drawHoloVisor(f, a, p.t)
            if (has(Accessory.SOMBRERO_EXPLORADOR)) drawHat(f, a, p.t)
            if (has(Accessory.ANTENA_PRISMA)) drawPrism(f, a, p.t, lift = if (has(Accessory.SOMBRERO_EXPLORADOR)) 4f else 0f)
            if (has(Accessory.CORONA_CUANTICA)) drawCrown(f, a, p.t, aboveHat = has(Accessory.SOMBRERO_EXPLORADOR))
        }
        if (a.core) {
            rotate(FluiBotMotion.torsoTilt(p.t), pivot = f.o(50f, a.torsoPivotY)) {
                drawCoreRings(f, p.t, front = true)
                drawFrontAccessories(f, a, equipped, p)
            }
        }
        if (face == FluiBotMood.THINKING) drawThoughtBubble(f, a, p.t)
    }

    if (has(Accessory.MINI_DRON)) drawDrone(f, p.t, front = true)
    if (has(Accessory.AURA_PARTICULAS)) drawParticleAura(f, p.t, front = true)
    if (face == FluiBotMood.CELEBRATING) drawSparkles(f, p.t)
    if (p.reacting) drawStarBurst(f, p.reaction)
}

/** Delante del torso: insignias, moño, libro de lectura, brazos, hombreras y llamas del jetpack. */
private fun DrawScope.drawFrontBody(f: Frame, a: Anchors, stage: FluiBotStage, equipped: Set<String>, p: Pose) {
    drawFrontAccessories(f, a, equipped, p, withFlames = false)
    if (p.face == FluiBotMood.READING) drawBook(f, a, p.t)
    drawArms(f, a, stage, p)
    if (stage.number >= 3) drawShoulderPads(f, stage, p.t)
    if (Accessory.JETPACK_PLASMA.id in equipped) drawJetpackFlames(f, a, p.t)
}

private fun DrawScope.drawFrontAccessories(f: Frame, a: Anchors, equipped: Set<String>, p: Pose, withFlames: Boolean = true) {
    if (Accessory.CAPA_FOTONICA.id in equipped) drawCapeClasp(f, a)
    if (Accessory.CORBATIN_LED.id in equipped) drawBowTie(f, a, p.t)
    if (Accessory.EMBLEMA_ESTELAR.id in equipped) drawEmblem(f, a, p.t)
    if (a.core && p.face == FluiBotMood.READING) drawBook(f, a, p.t)
    if (withFlames && Accessory.JETPACK_PLASMA.id in equipped) drawJetpackFlames(f, a, p.t)
}

// --- Cuerpo ---------------------------------------------------------------------------------------

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int = 5, rotation: Float = -90f): Path = Path().apply {
    for (k in 0 until points * 2) {
        val r = if (k % 2 == 0) outer else inner
        val ang = rad(rotation + k * 180f / points)
        val px = cx + r * cos(ang)
        val py = cy + r * sin(ang)
        if (k == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

/** Etapa 1: esfera tecnológica con visor LED, banda de energía y anillos magnéticos. */
private fun DrawScope.drawCoreSphere(f: Frame, a: Anchors, t: Float) {
    val c = f.o(50f, 52f)
    drawCircle(Brush.radialGradient(listOf(Color.White, Titanium, TitaniumShade, TitaniumDark), f.o(42f, 42f), f.u(34f)), f.u(23f), c)
    drawArc(Steel.copy(alpha = 0.35f), 20f, 140f, false, f.o(28f, 40f), f.size(44f, 30f), style = Stroke(f.u(0.6f)))
    drawCircle(Cyan.copy(alpha = 0.35f), f.u(23f), c, style = Stroke(f.u(0.8f)))
    drawReactor(f, Offset(50f, a.reactorY), FluiBotStage.NUCLEO, t)
    drawVisor(f, a, Cyan, t)
}

private fun DrawScope.drawCoreRings(f: Frame, t: Float, front: Boolean) {
    val c = f.o(50f, 52f)
    for ((i, tilt) in listOf(-14f, 18f).withIndex()) {
        rotate(tilt + sin(t + i) * 4f, pivot = c) {
            drawArc(
                Cyan.copy(alpha = 0.62f - i * 0.17f), if (front) 0f else 180f, 180f, false,
                f.o(17f, 45f), f.size(66f, 14f), style = Stroke(f.u(1.3f), cap = StrokeCap.Round)
            )
            val ang = t * 2f + i * PI.toFloat()
            if ((sin(ang) > 0f) == front) drawCircle(CyanSoft, f.u(1.3f), f.o(50f + 33f * cos(ang), 52f + 7f * sin(ang)))
        }
    }
}

/** Etapas 2-4: torso de titanio; desde la 3, acabado metálico cepillado y líneas de energía; la 4, ribetes dorados. */
private fun DrawScope.drawTorso(f: Frame, a: Anchors, stage: FluiBotStage, t: Float) {
    val advanced = stage.number >= 3
    val master = stage == FluiBotStage.SABIO
    val trim = trimFor(stage)
    val top = 48f
    val corner = CornerRadius(f.u(11f))
    val brush = if (advanced) {
        Brush.linearGradient(listOf(Color.White, Titanium, TitaniumShade, TitaniumDark), f.o(36f, top), f.o(64f, a.bodyBottom))
    } else {
        Brush.verticalGradient(listOf(Titanium, TitaniumShade, TitaniumDark), f.y(top), f.y(a.bodyBottom))
    }
    drawRoundRect(brush, f.o(36f, top), f.size(28f, a.bodyBottom - top), corner)
    if (advanced) {
        // Reflejo de metal cepillado y paneles.
        drawRoundRect(Color.White.copy(alpha = 0.5f), f.o(39f, 51f), f.size(2.6f, 19f), CornerRadius(f.u(1.3f)))
        val flow = 0.45f + 0.35f * sin(t * 3f)
        for (side in listOf(-1f, 1f)) {
            drawLine(trim.copy(alpha = flow), f.o(50f + side * 6f, a.reactorY), f.o(50f + side * 11f, a.reactorY + 6f), f.u(0.7f), cap = StrokeCap.Round)
            drawLine(trim.copy(alpha = flow), f.o(50f + side * 11f, a.reactorY + 6f), f.o(50f + side * 11f, a.reactorY + 10f), f.u(0.7f), cap = StrokeCap.Round)
        }
    }
    drawLine(Steel.copy(alpha = 0.35f), f.o(40f, 70.5f), f.o(60f, 70.5f), f.u(0.5f))
    drawRoundRect(Steel, f.o(38f, 72f), f.size(24f, 2.4f), CornerRadius(f.u(1.2f)))
    drawRoundRect(if (master) Gold else trim.copy(alpha = 0.55f), f.o(36f, top), f.size(28f, a.bodyBottom - top), corner, style = Stroke(f.u(if (master) 1f else 0.6f)))
    // Cuello articulado.
    drawRoundRect(Steel, f.o(45f, 45.5f), f.size(10f, 3.5f), CornerRadius(f.u(1.5f)))
    drawReactor(f, Offset(50f, a.reactorY), stage, t)
}

/** Reactor de energía del pecho: núcleo con gradiente radial que late; anillos que giran en las etapas altas. */
private fun DrawScope.drawReactor(f: Frame, center: Offset, stage: FluiBotStage, t: Float) {
    val pulse = 0.82f + 0.18f * sin(t * 3f)
    val master = stage == FluiBotStage.SABIO
    val glow = if (master) Gold else Cyan
    val core = if (master) listOf(Color.White, GoldLight, Gold) else listOf(Color.White, CyanSoft, Cyan)
    val r = when (stage.number) { 1 -> 3.2f; 2 -> 4f; else -> 4.6f }
    val c = f.o(center.x, center.y)
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * pulse), Color.Transparent), c, f.u(r * 2.8f)), f.u(r * 2.8f), c)
    drawCircle(Steel, f.u(r + 1.5f), c)
    drawCircle(Brush.radialGradient(core, c, f.u(r * pulse)), f.u(r * pulse), c)
    if (stage.number >= 3) {
        val ring = r + 2.7f
        for (i in 0 until 4) {
            drawArc(
                glow.copy(alpha = 0.9f), t * 115f + i * 90f, 52f, false,
                f.o(center.x - ring, center.y - ring), f.size(ring * 2, ring * 2), style = Stroke(f.u(0.8f), cap = StrokeCap.Round)
            )
        }
        if (master) drawCircle(GoldLight, f.u(ring + 1.6f), c, style = Stroke(f.u(0.4f)))
    }
}

private fun DrawScope.drawShoulderPads(f: Frame, stage: FluiBotStage, t: Float) {
    val master = stage == FluiBotStage.SABIO
    val trim = trimFor(stage)
    for (side in listOf(-1f, 1f)) {
        val cx = 50f + side * 16.5f
        val brush = if (master) Brush.verticalGradient(listOf(GoldLight, Gold, GoldDeep), f.y(47f), f.y(56f))
        else Brush.verticalGradient(listOf(Color.White, TitaniumShade, Steel), f.y(47f), f.y(56f))
        drawRoundRect(brush, f.o(cx - 6.5f, 47f), f.size(13f, 8.5f), CornerRadius(f.u(4.2f)))
        drawRoundRect(trim, f.o(cx - 6.5f, 47f), f.size(13f, 8.5f), CornerRadius(f.u(4.2f)), style = Stroke(f.u(0.6f)))
        drawCircle(trim.copy(alpha = 0.6f + 0.4f * sin(t * 4f + side)), f.u(0.9f), f.o(cx, 51.3f))
    }
}

/** Brazos articulados (hombro, codo y mano) con la pose de la expresión. */
private fun DrawScope.drawArms(f: Frame, a: Anchors, stage: FluiBotStage, p: Pose) {
    val trim = trimFor(stage)
    for (side in listOf(-1, 1)) {
        val pose = FluiBotMotion.armPose(p.face, side, p.t, p.wave)
        val sx = 50f + side * a.shoulderDx
        val sy = a.shoulderY
        val ex = sx + side * a.upperArm * sin(rad(pose.shoulder))
        val ey = sy + a.upperArm * cos(rad(pose.shoulder))
        val hx = ex + side * a.forearm * sin(rad(pose.forearm))
        val hy = ey + a.forearm * cos(rad(pose.forearm))
        drawLine(Steel, f.o(sx, sy), f.o(ex, ey), f.u(if (a.core) 3.4f else 4.4f), cap = StrokeCap.Round)
        drawLine(TitaniumShade, f.o(sx, sy), f.o(ex, ey), f.u(if (a.core) 2.2f else 3f), cap = StrokeCap.Round)
        drawLine(Steel, f.o(ex, ey), f.o(hx, hy), f.u(if (a.core) 3f else 3.8f), cap = StrokeCap.Round)
        drawLine(Titanium, f.o(ex, ey), f.o(hx, hy), f.u(if (a.core) 1.9f else 2.6f), cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(Color.White, TitaniumShade, Steel), f.o(ex - 0.6f, ey - 0.6f), f.u(3f)), f.u(if (a.core) 1.8f else 2.4f), f.o(ex, ey))
        drawCircle(Brush.radialGradient(listOf(Color.White, TitaniumShade, TitaniumDark), f.o(sx - 0.8f, sy - 0.8f), f.u(4f)), f.u(if (a.core) 2.4f else 3.1f), f.o(sx, sy))
        drawCircle(Brush.radialGradient(listOf(Color.White, TitaniumShade, TitaniumDark), f.o(hx - 1f, hy - 1f), f.u(a.hand * 1.6f)), f.u(a.hand), f.o(hx, hy))
        drawCircle(trim.copy(alpha = 0.8f), f.u(a.hand), f.o(hx, hy), style = Stroke(f.u(0.6f)))
    }
}

/** Propulsión de levitación bajo el cuerpo; el Sabio Cuántico añade toberas con llamas. */
private fun DrawScope.drawThruster(f: Frame, a: Anchors, stage: FluiBotStage, t: Float) {
    val glow = 0.6f + 0.4f * sin(t * 7f)
    val y = a.bodyBottom + 2.5f
    drawOval(
        Brush.radialGradient(listOf(trimFor(stage).copy(alpha = 0.45f * glow), Color.Transparent), f.o(50f, y), f.u(9f)),
        f.o(41f, y - 2.5f), f.size(18f, 5f)
    )
    if (stage == FluiBotStage.SABIO) {
        for (x in listOf(44f, 56f)) {
            drawRoundRect(Screen, f.o(x - 2.5f, a.bodyBottom - 0.8f), f.size(5f, 3f), CornerRadius(f.u(1.2f)))
            drawFlame(f, x, a.bodyBottom + 2f, 9f * (0.8f + 0.2f * sin(t * 9f + x)), 2f)
        }
    }
}

/** Etapa 3: brillo cian detrás del chasis. Etapa 4: aura cian y dorada con partículas en órbita. */
private fun DrawScope.drawStageAura(f: Frame, stage: FluiBotStage, t: Float) {
    val master = stage == FluiBotStage.SABIO
    val pulse = 0.8f + 0.2f * sin(t * 2f)
    val colors = if (master) listOf(Cyan.copy(alpha = 0.34f * pulse), Gold.copy(alpha = 0.12f), Color.Transparent)
    else listOf(Cyan.copy(alpha = 0.18f * pulse), Color.Transparent)
    drawCircle(Brush.radialGradient(colors, f.o(50f, 50f), f.u(48f)), f.u(48f), f.o(50f, 50f))
    if (master) {
        for (i in 0 until 8) {
            val ang = t + i * (PI / 4).toFloat()
            drawCircle(if (i % 2 == 0) Gold else CyanSoft, f.u(1.1f), f.o(50f + 41f * cos(ang), 50f + 31f * sin(ang)))
        }
    }
}

private fun DrawScope.drawHalo(f: Frame, a: Anchors, t: Float) {
    val y = a.headTop - 6f + sin(t * 2f) * 0.6f
    drawOval(Gold.copy(alpha = 0.85f), f.o(39f, y - 2f), f.size(22f, 4.5f), style = Stroke(f.u(1f)))
    drawOval(GoldLight.copy(alpha = 0.35f), f.o(38f, y - 2.6f), f.size(24f, 5.7f), style = Stroke(f.u(0.5f)))
}

// --- Cabeza y rostro LED ---------------------------------------------------------------------------

private fun DrawScope.drawHead(f: Frame, a: Anchors, stage: FluiBotStage, t: Float) {
    val master = stage == FluiBotStage.SABIO
    val trim = trimFor(stage)
    val corner = CornerRadius(f.u(12.5f))
    // Módulos laterales ("orejas") con luz.
    for (side in listOf(-1f, 1f)) {
        val x = 50f + side * a.headHalfW - 2f
        drawRoundRect(Steel, f.o(x, 27f), f.size(4f, 11f), CornerRadius(f.u(2f)))
        drawCircle(trim.copy(alpha = 0.55f + 0.45f * sin(t * 3f + side)), f.u(0.8f), f.o(x + 2f, 32.5f))
    }
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White, Titanium, TitaniumShade), f.y(a.headTop), f.y(a.headTop + 28f)),
        f.o(32f, a.headTop), f.size(36f, 28f), corner
    )
    drawRoundRect(Color.White.copy(alpha = 0.7f), f.o(36f, a.headTop + 1.5f), f.size(14f, 1.6f), CornerRadius(f.u(0.8f)))
    if (stage.number >= 3) {
        drawRoundRect(if (master) Gold else trim.copy(alpha = 0.7f), f.o(32f, a.headTop), f.size(36f, 28f), corner, style = Stroke(f.u(if (master) 1f else 0.6f)))
    }
    drawVisor(f, a, trim, t)
}

/** Visor facial con bisel iluminado, brillo interior, reflejo del cristal y una línea de barrido. */
private fun DrawScope.drawVisor(f: Frame, a: Anchors, trim: Color, t: Float) {
    val x = a.visorX
    val y = a.visorY
    val w = a.visorW
    val h = a.visorH
    val r = h / 2f
    drawRoundRect(
        Brush.linearGradient(listOf(trim, Color.White, trim, Steel), f.o(x, y), f.o(x + w, y + h)),
        f.o(x - 1.3f, y - 1.3f), f.size(w + 2.6f, h + 2.6f), CornerRadius(f.u(r + 1.3f))
    )
    drawRoundRect(Screen, f.o(x, y), f.size(w, h), CornerRadius(f.u(r)))
    drawRoundRect(
        Brush.verticalGradient(listOf(trim.copy(alpha = 0.22f), Color.Transparent), f.y(y), f.y(y + h * 0.7f)),
        f.o(x, y), f.size(w, h), CornerRadius(f.u(r))
    )
    val scan = y + 1.5f + (h - 3f) * (((t / TWO_PI) * 1.5f) % 1f)
    drawLine(trim.copy(alpha = 0.13f), f.o(x + 3f, scan), f.o(x + w - 3f, scan), f.u(0.6f))
    drawArc(Color.White.copy(alpha = 0.16f), 200f, 60f, false, f.o(x + 3f, y + 1.2f), f.size(w * 0.6f, h * 0.75f), style = Stroke(f.u(0.8f)))
}

/** Ojos y boca LED según la expresión. El párpado ([Pose.blink]) se cierra de forma orgánica. */
private fun DrawScope.drawFace(f: Frame, a: Anchors, trim: Color, mood: FluiBotMood, p: Pose) {
    val cx = 50f
    val led = Brush.verticalGradient(listOf(CyanSoft, trim), f.y(a.eyeY - 4f), f.y(a.eyeY + 4f))
    when (mood) {
        FluiBotMood.HAPPY -> {
            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * a.eyeDx
                drawCircle(trim.copy(alpha = 0.25f), f.u(5.4f), f.o(ex, a.eyeY))
                val h = 7.4f * p.blink
                drawOval(led, f.o(ex - 2.8f, a.eyeY - h / 2), f.size(5.6f, h))
                if (p.blink > 0.6f) drawCircle(Color.White.copy(alpha = 0.85f), f.u(0.9f), f.o(ex + 1f, a.eyeY - 1.6f))
                drawCircle(Blush, f.u(1.8f), f.o(ex + side * 3.6f, a.mouthY - 0.6f))
            }
            drawArc(trim, 15f, 150f, false, f.o(cx - 5f, a.mouthY - 3.4f), f.size(10f, 5f), style = Stroke(f.u(0.9f), cap = StrokeCap.Round))
        }
        FluiBotMood.READING -> {
            // Concentrado: ojos que miran hacia abajo y recorren el renglón, con el párpado a media altura.
            val scanX = sin(p.t * 2f) * 1.3f
            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * a.eyeDx + scanX
                drawCircle(trim.copy(alpha = 0.2f), f.u(4.8f), f.o(ex, a.eyeY + 1f))
                val h = 6.4f * p.blink
                drawOval(led, f.o(ex - 2.7f, a.eyeY + 1f - h / 2), f.size(5.4f, h))
                drawRect(Screen, f.o(ex - 3.2f, a.eyeY - 3.4f), f.size(6.4f, 3.2f))
                drawLine(trim.copy(alpha = 0.8f), f.o(ex - 3f, a.eyeY - 0.2f), f.o(ex + 3f, a.eyeY - 0.2f), f.u(0.6f), cap = StrokeCap.Round)
            }
            drawLine(trim, f.o(cx - 2.4f, a.mouthY), f.o(cx + 2.4f, a.mouthY), f.u(0.9f), cap = StrokeCap.Round)
        }
        FluiBotMood.THINKING -> {
            // Un ojo mira hacia arriba y el otro se entrecierra; boca pequeña de lado.
            val left = cx - a.eyeDx + 1.2f
            drawCircle(trim.copy(alpha = 0.25f), f.u(5f), f.o(left, a.eyeY - 1f))
            val h = 7f * p.blink
            drawOval(led, f.o(left - 2.6f, a.eyeY - 1f - h / 2), f.size(5.2f, h))
            drawCircle(Color.White.copy(alpha = 0.8f), f.u(0.8f), f.o(left + 1.2f, a.eyeY - 3f))
            val right = cx + a.eyeDx + 1.2f
            drawArc(trim, 200f, 140f, false, f.o(right - 3f, a.eyeY - 2.5f), f.size(6f, 4f), style = Stroke(f.u(1f), cap = StrokeCap.Round))
            drawCircle(trim, f.u(1.1f), f.o(cx + 2.5f, a.mouthY), style = Stroke(f.u(0.8f)))
        }
        FluiBotMood.CELEBRATING -> {
            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * a.eyeDx
                drawCircle(Gold.copy(alpha = 0.3f), f.u(5.6f), f.o(ex, a.eyeY))
                drawPath(
                    starPath(f.x(ex), f.y(a.eyeY), f.u(4.4f), f.u(1.9f), rotation = -90f + sin(p.t * 3f) * 12f),
                    Brush.radialGradient(listOf(Color.White, GoldLight, Gold), f.o(ex, a.eyeY), f.u(4.6f))
                )
            }
            // Sonrisa abierta.
            val mouth = Path().apply {
                moveTo(f.x(cx - 4.6f), f.y(a.mouthY - 1.6f))
                quadraticTo(f.x(cx), f.y(a.mouthY + 4.6f), f.x(cx + 4.6f), f.y(a.mouthY - 1.6f))
                close()
            }
            drawPath(mouth, trim)
            drawOval(Magenta.copy(alpha = 0.85f), f.o(cx - 2f, a.mouthY + 0.4f), f.size(4f, 1.6f))
        }
    }
}

/** Antena(s) con collarín, punta luminosa y pulsos concéntricos de energía. */
private fun DrawScope.drawAntennas(f: Frame, a: Anchors, trim: Color, p: Pose, prismLift: Float) {
    val speed = if (p.face == FluiBotMood.THINKING) 3.5f else 2f
    for (ax in a.antennas) {
        val tip = f.o(ax, a.antennaTipY - prismLift)
        drawLine(Steel, f.o(ax, a.headTop + 0.5f), Offset(tip.x, tip.y + f.u(2f)), f.u(1.3f), cap = StrokeCap.Round)
        drawRoundRect(Steel, f.o(ax - 2f, a.headTop - 1.2f), f.size(4f, 2.4f), CornerRadius(f.u(1f)))
        FluiBotMotion.antennaPulses(p.t, speed = speed).forEach { phase ->
            drawCircle(trim.copy(alpha = (1f - phase) * 0.55f), f.u(2.2f + phase * 9f), tip, style = Stroke(f.u(0.6f)))
        }
        val glow = 0.7f + 0.3f * sin(p.t * 4f + ax)
        drawCircle(trim.copy(alpha = 0.38f * glow), f.u(3.9f), tip)
        drawCircle(Brush.radialGradient(listOf(Color.White, trim), tip, f.u(2.3f)), f.u(2.2f), tip)
    }
}

private fun DrawScope.drawThoughtBubble(f: Frame, a: Anchors, t: Float) {
    val cx = 79f
    val cy = a.headTop - 7f
    drawCircle(Color.White.copy(alpha = 0.9f), f.u(1.1f), f.o(cx - 11f, cy + 11f))
    drawCircle(Color.White.copy(alpha = 0.9f), f.u(1.7f), f.o(cx - 7.5f, cy + 7.5f))
    for ((dx, dy, r) in listOf(Triple(-4f, 0f, 4.4f), Triple(0f, -2.2f, 5f), Triple(4.5f, 0f, 4.4f), Triple(0f, 2f, 4.6f))) {
        drawCircle(Color.White.copy(alpha = 0.95f), f.u(r), f.o(cx + dx, cy + dy))
    }
    for (k in 0 until 3) {
        val alpha = 0.25f + 0.75f * max(0f, sin(t * 4f - k * 0.9f))
        drawCircle(Steel.copy(alpha = alpha), f.u(1.1f), f.o(cx - 3.4f + k * 3.4f, cy))
    }
}

/** Libro abierto que FluiBot sostiene mientras lee. */
private fun DrawScope.drawBook(f: Frame, a: Anchors, t: Float) {
    val cx = 50f
    val cy = a.bookY
    for (side in listOf(-1f, 1f)) {
        val cover = Path().apply {
            moveTo(f.x(cx), f.y(cy - 5.6f)); lineTo(f.x(cx + side * 12f), f.y(cy - 7f))
            lineTo(f.x(cx + side * 12f), f.y(cy + 5.6f)); lineTo(f.x(cx), f.y(cy + 7f)); close()
        }
        drawPath(cover, BookCover)
        val page = Path().apply {
            moveTo(f.x(cx), f.y(cy - 4.6f)); lineTo(f.x(cx + side * 11f), f.y(cy - 6f))
            lineTo(f.x(cx + side * 11f), f.y(cy + 4.6f)); lineTo(f.x(cx), f.y(cy + 6f)); close()
        }
        drawPath(page, Brush.horizontalGradient(listOf(Paper, Color.White), f.x(cx), f.x(cx + side * 11f)))
        for (k in 0 until 4) {
            val y = cy - 3f + k * 2.2f
            drawLine(TitaniumDark.copy(alpha = 0.8f), f.o(cx + side * 2f, y), f.o(cx + side * (9f - (k % 2) * 2f), y - 0.4f), f.u(0.45f))
        }
    }
    drawLine(Steel, f.o(cx, cy - 5f), f.o(cx, cy + 6f), f.u(0.6f))
    // Una chispa de "palabra leída" que avanza por el renglón.
    val read = (t / TWO_PI * 3f) % 1f
    drawCircle(Cyan.copy(alpha = 0.8f), f.u(0.6f), f.o(cx - 9f + read * 18f, cy - 3f))
}

// --- Partículas -----------------------------------------------------------------------------------

private fun DrawScope.drawSparkles(f: Frame, t: Float) {
    val spots = listOf(14f to 24f, 86f to 20f, 10f to 62f, 90f to 58f, 24f to 10f, 76f to 84f, 22f to 84f, 82f to 40f)
    spots.forEachIndexed { i, (x, y) ->
        val alpha = max(0f, sin(t * 3f + i * 1.7f))
        if (alpha > 0.05f) {
            drawPath(starPath(f.x(x), f.y(y), f.u(2.2f * alpha + 0.6f), f.u(0.7f), points = 4), (if (i % 2 == 0) Gold else CyanSoft).copy(alpha = alpha))
        }
    }
}

/** Destello de estrellas y chispas que sale del centro al tocar a FluiBot. */
private fun DrawScope.drawStarBurst(f: Frame, progress: Float) {
    val count = 18
    val alpha = (1f - progress).coerceIn(0f, 1f)
    for (i in 0 until count) {
        val ang = i * (TWO_PI / count) + 0.3f
        val dist = (10f + 40f * progress) * (0.7f + 0.3f * ((i * 7) % 5) / 4f)
        val color = when (i % 3) { 0 -> Cyan; 1 -> Gold; else -> Magenta }
        val c = f.o(50f + dist * cos(ang), 46f + dist * sin(ang) * 0.9f)
        if (i % 2 == 0) drawPath(starPath(c.x, c.y, f.u(2.8f * alpha + 0.4f), f.u(1.1f)), color.copy(alpha = alpha))
        else drawCircle(color.copy(alpha = alpha), f.u(1.6f * (1f - 0.5f * progress)), c)
    }
}

/** Aura de Partículas: chispas de colores que orbitan en una elipse inclinada; las de atrás pasan por detrás. */
private fun DrawScope.drawParticleAura(f: Frame, t: Float, front: Boolean) {
    val n = 14
    val colors = listOf(CyanSoft, Magenta, Gold, Violet)
    for (i in 0 until n) {
        val ang = t * 0.9f + i * TWO_PI / n
        val depth = sin(ang)
        if ((depth >= 0f) != front) continue
        val scale = 0.75f + 0.35f * (depth + 1f) / 2f
        val x = 50f + 41f * cos(ang)
        val y = 58f + 12f * depth - 8f * cos(ang) * 0.3f
        val color = colors[i % colors.size]
        for (trail in 1..2) {
            val back = ang - trail * 0.12f
            drawCircle(color.copy(alpha = 0.25f / trail), f.u(1.1f * scale), f.o(50f + 41f * cos(back), 58f + 12f * sin(back) - 8f * cos(back) * 0.3f))
        }
        drawCircle(color.copy(alpha = 0.35f), f.u(2.6f * scale), f.o(x, y))
        drawCircle(color, f.u(1.3f * scale), f.o(x, y))
    }
}

// --- Accesorios -----------------------------------------------------------------------------------

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

/** Capa fotónica: cae desde los hombros y ondea con una curva de Bézier en el borde. */
private fun DrawScope.drawCape(f: Frame, a: Anchors, t: Float) {
    val topY = if (a.core) 42f else a.neckY + 2f
    val bottom = a.bodyBottom + 13f
    val half = if (a.core) 19f else 12f
    val path = Path().apply {
        moveTo(f.x(50f - half), f.y(topY))
        cubicTo(f.x(50f - half - 6f), f.y(topY + 10f), f.x(28f + sin(t) * 1.5f), f.y(bottom - 10f), f.x(26f + sin(t * 1.3f) * 2f), f.y(bottom))
        for (i in 1..10) {
            val px = 26f + (74f - 26f) * i / 10f
            lineTo(f.x(px), f.y(bottom + sin(t * 2.4f + i * 0.9f) * 2.2f))
        }
        cubicTo(f.x(72f + sin(t) * 1.5f), f.y(bottom - 10f), f.x(50f + half + 6f), f.y(topY + 10f), f.x(50f + half), f.y(topY))
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Violet.copy(alpha = 0.9f), Magenta.copy(alpha = 0.8f), Cyan.copy(alpha = 0.55f)), f.y(topY), f.y(bottom)))
    drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent), f.x(30f), f.x(70f)))
    drawPath(path, CyanSoft.copy(alpha = 0.85f), style = Stroke(f.u(0.7f)))
}

private fun DrawScope.drawCapeClasp(f: Frame, a: Anchors) {
    val y = if (a.core) 66f else a.neckY + 2f
    val dx = if (a.core) 17f else 10f
    for (side in listOf(-1f, 1f)) {
        val c = f.o(50f + side * dx, y)
        drawCircle(Brush.radialGradient(listOf(GoldLight, Gold, GoldDeep), c, f.u(2.2f)), f.u(1.8f), c)
        drawCircle(Violet, f.u(0.7f), c)
    }
}

private fun DrawScope.drawWings(f: Frame, a: Anchors, t: Float) {
    val attachY = a.shoulderY + 5f
    val flap = sin(t * 2f) * 5f
    for (side in listOf(-1f, 1f)) {
        val pivot = f.o(50f + side * (a.shoulderDx - 1f), attachY)
        rotate(side * flap, pivot) {
            val tips = listOf(Offset(31f, -18f), Offset(36f, -5f), Offset(31f, 8f))
            for ((i, tip) in tips.withIndex()) {
                val blade = Path().apply {
                    moveTo(pivot.x, pivot.y + f.u(-2f + i * 2.5f))
                    quadraticTo(pivot.x + side * f.u(tip.x * 0.6f), pivot.y + f.u(tip.y - 4f), pivot.x + side * f.u(tip.x), pivot.y + f.u(tip.y))
                    lineTo(pivot.x + side * f.u(tip.x * 0.62f), pivot.y + f.u(tip.y + 6f))
                    close()
                }
                drawPath(blade, Brush.linearGradient(listOf(Cyan.copy(alpha = 0.9f), CyanSoft.copy(alpha = 0.55f), Color.White.copy(alpha = 0.4f)), pivot, Offset(pivot.x + side * f.u(34f), pivot.y)))
                drawPath(blade, Gold.copy(alpha = 0.9f), style = Stroke(f.u(0.6f)))
            }
        }
    }
}

private fun DrawScope.drawJetpackBack(f: Frame, a: Anchors) {
    for (side in listOf(-1f, 1f)) {
        val x = 50f + side * (a.shoulderDx - 3f) - 4f
        val top = a.shoulderY + 1f
        drawRoundRect(Brush.horizontalGradient(listOf(Steel, Titanium, Steel), f.x(x), f.x(x + 8f)), f.o(x, top), f.size(8f, 20f), CornerRadius(f.u(3.5f)))
        drawRoundRect(Cyan.copy(alpha = 0.7f), f.o(x, top), f.size(8f, 20f), CornerRadius(f.u(3.5f)), style = Stroke(f.u(0.5f)))
        drawRoundRect(Cyan.copy(alpha = 0.8f), f.o(x + 1.5f, top + 6f), f.size(5f, 1.4f), CornerRadius(f.u(0.7f)))
        drawRoundRect(Screen, f.o(x + 1.5f, top + 19f), f.size(5f, 3f), CornerRadius(f.u(1f)))
    }
}

private fun DrawScope.drawJetpackFlames(f: Frame, a: Anchors, t: Float) {
    val flicker = 0.8f + 0.2f * sin(t * 9f)
    for (side in listOf(-1f, 1f)) {
        drawFlame(f, 50f + side * (a.shoulderDx - 3f), a.shoulderY + 23f, 13f * flicker, 3.2f)
    }
}

/** Corbatín LED: dos lazos con pliegues, nudo central y luces que se encienden en secuencia. */
private fun DrawScope.drawBowTie(f: Frame, a: Anchors, t: Float) {
    val y = a.neckY
    for (side in listOf(-1f, 1f)) {
        val lobe = Path().apply {
            moveTo(f.x(50f), f.y(y))
            quadraticTo(f.x(50f + side * 5f), f.y(y - 6.5f), f.x(50f + side * 9.5f), f.y(y - 4.2f))
            lineTo(f.x(50f + side * 9.5f), f.y(y + 4.2f))
            quadraticTo(f.x(50f + side * 5f), f.y(y + 6.5f), f.x(50f), f.y(y))
            close()
        }
        drawPath(lobe, Brush.horizontalGradient(listOf(Violet, Magenta), f.x(50f), f.x(50f + side * 9.5f)))
        drawLine(Color.White.copy(alpha = 0.35f), f.o(50f + side * 3f, y - 1.5f), f.o(50f + side * 7f, y - 2.5f), f.u(0.5f))
        drawLine(Color.White.copy(alpha = 0.35f), f.o(50f + side * 3f, y + 1.5f), f.o(50f + side * 7f, y + 2.5f), f.u(0.5f))
        for (k in 0 until 3) {
            val on = sin(t * 5f - k * 1.2f + side) > 0.2f
            drawCircle(if (on) CyanSoft else Color.White.copy(alpha = 0.25f), f.u(0.55f), f.o(50f + side * (8.6f), y - 2.6f + k * 2.6f))
        }
    }
    drawRoundRect(Brush.verticalGradient(listOf(CyanSoft, Cyan), f.y(y - 2.8f), f.y(y + 2.8f)), f.o(47.6f, y - 2.8f), f.size(4.8f, 5.6f), CornerRadius(f.u(1.5f)))
}

/** Emblema estelar: escudo dorado con una estrella y un brillo que lo recorre. */
private fun DrawScope.drawEmblem(f: Frame, a: Anchors, t: Float) {
    val cx = a.emblemX
    val cy = a.emblemY
    val shield = Path().apply {
        moveTo(f.x(cx - 4.4f), f.y(cy - 4.6f)); lineTo(f.x(cx + 4.4f), f.y(cy - 4.6f))
        lineTo(f.x(cx + 4.4f), f.y(cy + 0.6f)); quadraticTo(f.x(cx + 4.2f), f.y(cy + 4f), f.x(cx), f.y(cy + 5.8f))
        quadraticTo(f.x(cx - 4.2f), f.y(cy + 4f), f.x(cx - 4.4f), f.y(cy + 0.6f)); close()
    }
    drawCircle(Gold.copy(alpha = 0.3f + 0.1f * sin(t * 3f)), f.u(7f), f.o(cx, cy))
    drawPath(shield, Brush.verticalGradient(listOf(GoldLight, Gold, GoldDeep), f.y(cy - 4.6f), f.y(cy + 5.8f)))
    drawPath(starPath(f.x(cx), f.y(cy), f.u(2.6f), f.u(1.1f)), Color.White.copy(alpha = 0.95f))
    clipPath(shield) {
        val sweep = cx - 8f + 16f * ((t / TWO_PI * 2f) % 1f)
        drawLine(Color.White.copy(alpha = 0.55f), f.o(sweep, cy - 6f), f.o(sweep + 3f, cy + 6f), f.u(1.2f))
    }
    drawPath(shield, GoldDeep, style = Stroke(f.u(0.5f)))
}

private fun DrawScope.drawHeadphones(f: Frame, a: Anchors, t: Float) {
    val left = 50f - a.headHalfW
    val right = 50f + a.headHalfW
    val bandTop = a.headTop - 3f
    drawArc(Steel, 180f, 180f, false, f.o(left, bandTop), f.size(2 * a.headHalfW, 2 * (a.eyeY - bandTop)), style = Stroke(f.u(2.2f), cap = StrokeCap.Round))
    drawArc(Titanium.copy(alpha = 0.8f), 200f, 60f, false, f.o(left, bandTop), f.size(2 * a.headHalfW, 2 * (a.eyeY - bandTop)), style = Stroke(f.u(0.8f), cap = StrokeCap.Round))
    val wave = 0.4f + 0.4f * sin(t * 5f)
    for (x in listOf(left, right)) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A)), f.y(a.eyeY - 6f), f.y(a.eyeY + 6f)), f.o(x - 3.6f, a.eyeY - 6f), f.size(7.2f, 12f), CornerRadius(f.u(3.4f)))
        drawRoundRect(Cyan, f.o(x - 3.6f, a.eyeY - 6f), f.size(7.2f, 12f), CornerRadius(f.u(3.4f)), style = Stroke(f.u(0.7f)))
        drawCircle(Cyan.copy(alpha = 0.5f + 0.5f * wave), f.u(1.2f), f.o(x, a.eyeY))
        val dir = if (x < 50f) -1f else 1f
        for (k in 1..2) {
            drawArc(
                Cyan.copy(alpha = wave / k), if (dir < 0) 140f else -40f, 80f, false,
                f.o(x + dir * (5f + k * 2.2f) - 3f, a.eyeY - 4f), f.size(6f, 8f), style = Stroke(f.u(0.6f))
            )
        }
    }
}

/** Visor holográfico: lente translúcida cian-magenta con líneas de escaneo, reflejo y sujeciones laterales. */
private fun DrawScope.drawHoloVisor(f: Frame, a: Anchors, t: Float) {
    val half = a.headHalfW + 2f
    val top = a.eyeY - 5.5f
    val h = 11f
    val corner = CornerRadius(f.u(5.5f))
    drawRoundRect(Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.4f), Magenta.copy(alpha = 0.32f), Cyan.copy(alpha = 0.4f)), f.x(50f - half), f.x(50f + half)), f.o(50f - half, top), f.size(2 * half, h), corner)
    for (k in 1..3) {
        val y = top + k * h / 4f
        drawLine(Color.White.copy(alpha = 0.18f), f.o(50f - half + 2f, y), f.o(50f + half - 2f, y), f.u(0.35f))
    }
    val glare = 50f - half + 2f * half * ((t / TWO_PI) % 1f)
    drawLine(Color.White.copy(alpha = 0.45f), f.o(glare, top + 1f), f.o(glare + 3f, top + h - 1f), f.u(0.8f))
    drawRoundRect(CyanSoft, f.o(50f - half, top), f.size(2 * half, h), corner, style = Stroke(f.u(0.8f)))
    for (side in listOf(-1f, 1f)) {
        drawRoundRect(Steel, f.o(50f + side * half - 1.2f, a.eyeY - 2f), f.size(2.4f, 4f), CornerRadius(f.u(0.8f)))
    }
}

/** Sombrero de explorador: ala ancha, copa con sombras y una banda de luz con insignia. */
private fun DrawScope.drawHat(f: Frame, a: Anchors, t: Float) {
    val brimY = a.headTop + 1.5f
    val brimHalf = a.headHalfW + 6f
    drawOval(Brush.verticalGradient(listOf(HatTan, HatBrown), f.y(brimY - 2.5f), f.y(brimY + 2.5f)), f.o(50f - brimHalf, brimY - 2.5f), f.size(2 * brimHalf, 5f))
    val crown = Path().apply {
        moveTo(f.x(50f - 12f), f.y(brimY))
        lineTo(f.x(50f - 10f), f.y(brimY - 9f))
        quadraticTo(f.x(50f), f.y(brimY - 12.5f), f.x(50f + 10f), f.y(brimY - 9f))
        lineTo(f.x(50f + 12f), f.y(brimY))
        close()
    }
    drawPath(crown, Brush.horizontalGradient(listOf(HatBrown, HatTan, HatBrown), f.x(38f), f.x(62f)))
    drawLine(HatBrown.copy(alpha = 0.6f), f.o(50f, brimY - 11.5f), f.o(50f, brimY - 7f), f.u(0.6f))
    val glow = 0.7f + 0.3f * sin(t * 3f)
    drawRect(Cyan.copy(alpha = glow), f.o(50f - 11.4f, brimY - 3.2f), f.size(22.8f, 2.4f))
    drawPath(starPath(f.x(50f + 6f), f.y(brimY - 2f), f.u(1.8f), f.u(0.8f)), Gold)
}

/** Antena Prisma: gema facetada en la punta de la antena con rayos de arcoíris que giran. */
private fun DrawScope.drawPrism(f: Frame, a: Anchors, t: Float, lift: Float) {
    val cx = f.x(a.antennas.first())
    val cy = f.y(a.antennaTipY - 1.5f - lift)
    val r = f.u(4.2f)
    val colors = listOf(Magenta, CyanSoft, Color(0xFFFDE047), Color(0xFF4ADE80), Violet, FlameBlue)
    rotate(t * 30f, pivot = Offset(cx, cy)) {
        for (i in 0 until 6) {
            val ray = rad(i * 60f)
            drawLine(colors[i].copy(alpha = 0.35f), Offset(cx, cy), Offset(cx + cos(ray) * r * 2f, cy + sin(ray) * r * 2f), f.u(0.5f))
        }
    }
    for (i in 0 until 6) {
        val a1 = rad(-90f + i * 60f)
        val a2 = rad(-90f + (i + 1) * 60f)
        val facet = Path().apply {
            moveTo(cx, cy); lineTo(cx + cos(a1) * r, cy + sin(a1) * r * 1.2f); lineTo(cx + cos(a2) * r, cy + sin(a2) * r * 1.2f); close()
        }
        drawPath(facet, colors[i].copy(alpha = 0.92f))
    }
    drawCircle(Color.White.copy(alpha = 0.4f + 0.3f * sin(t * 4f)), f.u(1.1f), Offset(cx - r * 0.25f, cy - r * 0.35f))
}

/** Corona cuántica: cinco puntas doradas con gemas, banda enjoyada, halo y un destello que la recorre. */
private fun DrawScope.drawCrown(f: Frame, a: Anchors, t: Float, aboveHat: Boolean) {
    val bob = sin(t * 2f) * 0.8f
    val baseY = a.headTop - (if (aboveHat) 11f else 2f) + bob
    val x = 50f
    val tips = listOf(-9f to -6f, -4.5f to -8.5f, 0f to -10.5f, 4.5f to -8.5f, 9f to -6f)
    val path = Path().apply {
        moveTo(f.x(x - 9.5f), f.y(baseY))
        tips.forEachIndexed { i, (dx, dy) ->
            lineTo(f.x(x + dx), f.y(baseY + dy))
            if (i < tips.lastIndex) lineTo(f.x(x + dx + 2.25f), f.y(baseY - 3.5f))
        }
        lineTo(f.x(x + 9.5f), f.y(baseY)); close()
    }
    drawCircle(Gold.copy(alpha = 0.22f + 0.12f * sin(t * 3f)), f.u(12f), f.o(x, baseY - 4f))
    drawPath(path, Brush.verticalGradient(listOf(GoldLight, Gold, GoldDeep), f.y(baseY - 10.5f), f.y(baseY)))
    clipPath(path) {
        val sweep = x - 12f + 24f * ((t / TWO_PI * 1.5f) % 1f)
        drawLine(Color.White.copy(alpha = 0.6f), f.o(sweep, baseY - 11f), f.o(sweep + 3f, baseY + 1f), f.u(1.2f))
    }
    drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(f.u(0.4f)))
    drawRoundRect(GoldDeep, f.o(x - 9.5f, baseY - 1.6f), f.size(19f, 2.2f), CornerRadius(f.u(1f)))
    tips.forEachIndexed { i, (dx, dy) ->
        drawCircle(if (i % 2 == 0) CyanSoft else Magenta, f.u(1f), f.o(x + dx, baseY + dy))
    }
    for (k in 0 until 3) drawCircle(if (k == 1) Magenta else CyanSoft, f.u(0.7f), f.o(x - 5f + k * 5f, baseY - 0.5f))
}

/** Mini-dron en órbita con profundidad: pasa por detrás de FluiBot y se agranda al pasar por delante. */
private fun DrawScope.drawDrone(f: Frame, t: Float, front: Boolean) {
    val ang = t * 1.4f
    val depth = sin(ang)
    if ((depth >= 0f) != front) return
    val scale = 0.8f + 0.3f * (depth + 1f) / 2f
    val x = 50f + 42f * cos(ang)
    val y = 38f + 9f * depth + sin(t * 4f) * 0.8f
    val c = f.o(x, y)
    drawCircle(Cyan.copy(alpha = 0.22f), f.u(6.5f * scale), c)
    for (side in listOf(-1f, 1f)) {
        val rotor = f.o(x + side * 4.6f * scale, y - 3.4f * scale)
        drawLine(Steel, Offset(c.x, c.y - f.u(1f * scale)), rotor, f.u(0.7f))
        rotate(t * 900f * side, pivot = rotor) {
            drawOval(TitaniumDark.copy(alpha = 0.7f), Offset(rotor.x - f.u(3f * scale), rotor.y - f.u(0.5f * scale)), Size(f.u(6f * scale), f.u(1f * scale)))
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color.White, TitaniumShade), Offset(c.x - f.u(1.2f), c.y - f.u(1.2f)), f.u(5f * scale)), f.u(3.8f * scale), c)
    drawCircle(Cyan, f.u(3.8f * scale), c, style = Stroke(f.u(0.5f)))
    drawCircle(if (sin(t * 6f) > 0f) Color(0xFFFB7185) else Color(0xFF7F1D1D), f.u(1f * scale), c)
    drawCircle(Color.White.copy(alpha = 0.8f), f.u(0.35f * scale), Offset(c.x + f.u(0.3f), c.y - f.u(0.3f)))
    if (abs(depth) < 0.1f) drawCircle(CyanSoft.copy(alpha = 0.4f), f.u(1f), c)
}
