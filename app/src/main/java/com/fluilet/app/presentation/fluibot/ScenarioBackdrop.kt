package com.fluilet.app.presentation.fluibot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.fluilet.app.domain.gamification.Scenario
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Escenario estático (sin animación continua) para las tarjetas de la tienda. */
@Composable
fun ScenarioPreview(scenario: Scenario, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) { drawScenario(scenario, time = 1.2f) }
}

/** Escenario animado a pantalla completa (se usa como fondo de FluiBot). */
@Composable
fun AnimatedScenario(scenario: Scenario, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scenario")
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    Canvas(modifier.clearAndSetSemantics { }) { drawScenario(scenario, time) }
}

/** Dibuja el escenario ocupando todo el lienzo. [time] va de 0 a 2π en bucle. */
fun DrawScope.drawScenario(scenario: Scenario, time: Float) {
    when (scenario) {
        Scenario.LABORATORIO_CIAN -> drawLab(time)
        Scenario.ESTACION_ESPACIAL -> drawSpace(time)
        Scenario.OCEANO_SUBMARINO -> drawOcean(time)
        Scenario.ESTADIO_CAMPEON -> drawStadium(time)
        Scenario.BOSQUE_CYBER_NEON -> drawNeonForest(time)
    }
}

private val CyanLine = Color(0xFF22D3EE)

private fun DrawScope.drawLab(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF082F3F), Color(0xFF0E5A6E)), 0f, h))
    // paneles holográficos
    for (side in listOf(0.16f, 0.84f)) {
        val px = w * side
        drawRoundRect(CyanLine.copy(alpha = 0.10f), Offset(px - w * 0.09f, h * 0.14f), Size(w * 0.18f, h * 0.36f), androidx.compose.ui.geometry.CornerRadius(w * 0.02f))
        drawRoundRect(CyanLine.copy(alpha = 0.55f), Offset(px - w * 0.09f, h * 0.14f), Size(w * 0.18f, h * 0.36f), androidx.compose.ui.geometry.CornerRadius(w * 0.02f), style = Stroke(w * 0.004f))
        for (i in 0 until 4) {
            val bar = (0.3f + 0.7f * (0.5f + 0.5f * sin(time * 1.5f + i + side * 6f)))
            drawRoundRect(CyanLine.copy(alpha = 0.7f), Offset(px - w * 0.07f, h * (0.19f + i * 0.07f)), Size(w * 0.14f * bar, h * 0.02f), androidx.compose.ui.geometry.CornerRadius(h * 0.01f))
        }
    }
    // suelo con rejilla en perspectiva
    val floorTop = h * 0.74f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF06222E), Color(0xFF03141C)), floorTop, h), Offset(0f, floorTop), Size(w, h - floorTop))
    for (i in 0..5) {
        val y = floorTop + (h - floorTop) * (i / 5f) * (i / 5f)
        drawLine(CyanLine.copy(alpha = 0.25f), Offset(0f, y), Offset(w, y), 1.2f)
    }
    for (i in -6..6) {
        drawLine(CyanLine.copy(alpha = 0.2f), Offset(w / 2 + i * w * 0.02f, floorTop), Offset(w / 2 + i * w * 0.14f, h), 1.2f)
    }
    // luces del techo
    for (i in 0 until 5) {
        drawCircle(Color.White.copy(alpha = 0.35f + 0.25f * sin(time * 2f + i)), min(w, h) * 0.012f, Offset(w * (0.12f + i * 0.19f), h * 0.05f))
    }
}

private fun DrawScope.drawSpace(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF040A22), Color(0xFF1B1550)), 0f, h))
    val random = Random(7)
    repeat(70) { i ->
        val x = random.nextFloat() * w
        val y = random.nextFloat() * h * 0.9f
        val r = (0.4f + random.nextFloat() * 1.6f) * (min(w, h) / 220f)
        drawCircle(Color.White.copy(alpha = (0.45f + 0.5f * sin(time * 2f + i)).coerceIn(0.1f, 1f)), r, Offset(x, y))
    }
    // planeta con anillos
    val c = Offset(w * 0.78f, h * 0.30f)
    val r = min(w, h) * 0.17f
    rotate(-18f, c) { drawOval(Color(0xFFFDE68A).copy(alpha = 0.55f), Offset(c.x - r * 2f, c.y - r * 0.42f), Size(r * 4f, r * 0.84f), style = Stroke(r * 0.12f)) }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFB923C), Color(0xFF9333EA), Color(0xFF312E81)), Offset(c.x - r * 0.4f, c.y - r * 0.4f), r * 1.6f), r, c)
    rotate(-18f, c) {
        // mitad frontal del anillo, por delante del planeta
        drawArc(Color(0xFFFDE68A).copy(alpha = 0.85f), 0f, 180f, false, Offset(c.x - r * 2f, c.y - r * 0.42f), Size(r * 4f, r * 0.84f), style = Stroke(r * 0.12f))
    }
    // luna
    drawCircle(Color(0xFFE2E8F0), min(w, h) * 0.045f, Offset(w * 0.16f, h * 0.22f))
    drawCircle(Color(0xFF94A3B8), min(w, h) * 0.012f, Offset(w * 0.15f, h * 0.21f))
}

private fun DrawScope.drawOcean(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF0369A1), Color(0xFF082F49)), 0f, h))
    // rayos de luz
    for (i in 0 until 4) {
        val x = w * (0.12f + i * 0.26f) + sin(time + i) * w * 0.02f
        val ray = Path().apply { moveTo(x, 0f); lineTo(x + w * 0.10f, 0f); lineTo(x + w * 0.22f, h * 0.8f); lineTo(x - w * 0.02f, h * 0.8f); close() }
        drawPath(ray, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), 0f, h * 0.8f))
    }
    // ondas marinas
    for (k in 0 until 3) {
        val path = Path().apply {
            moveTo(0f, h * (0.12f + k * 0.07f))
            var x = 0f
            while (x <= w) { lineTo(x, h * (0.12f + k * 0.07f) + sin(x / w * 6f + time * 2f + k) * h * 0.012f); x += w / 40f }
        }
        drawPath(path, Color.White.copy(alpha = 0.25f - k * 0.05f), style = Stroke(2f))
    }
    // burbujas
    val random = Random(11)
    repeat(16) { i ->
        val x = random.nextFloat() * w
        val speed = 0.5f + random.nextFloat()
        val phase = ((time / (2 * PI.toFloat()) * speed + random.nextFloat()) % 1f)
        val y = h * (1.0f - phase)
        val r = (0.6f + random.nextFloat() * 1.8f) * (min(w, h) / 90f)
        drawCircle(Color.White.copy(alpha = 0.45f * (1f - phase * 0.6f)), r, Offset(x + sin(time * 2f + i) * 6f, y), style = Stroke(1.5f))
    }
    // lecho marino
    drawOval(Color(0xFFFDE68A).copy(alpha = 0.35f), Offset(-w * 0.1f, h * 0.9f), Size(w * 1.2f, h * 0.3f))
    for (i in 0 until 5) drawCircle(Color(0xFFF472B6).copy(alpha = 0.55f), min(w, h) * 0.02f, Offset(w * (0.1f + i * 0.2f), h * 0.93f))
}

private fun DrawScope.drawStadium(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1B3A), Color(0xFF1E3A5F)), 0f, h * 0.55f), Offset.Zero, Size(w, h * 0.6f))
    // gradas con público
    val random = Random(5)
    drawRect(Color(0xFF111827), Offset(0f, h * 0.30f), Size(w, h * 0.22f))
    repeat(90) {
        val x = random.nextFloat() * w
        val y = h * (0.31f + random.nextFloat() * 0.20f)
        val c = listOf(Color(0xFFF87171), Color(0xFFFBBF24), Color(0xFF60A5FA), Color.White)[random.nextInt(4)]
        drawCircle(c.copy(alpha = 0.7f), min(w, h) * 0.008f, Offset(x, y))
    }
    // césped
    drawRect(Brush.verticalGradient(listOf(Color(0xFF16A34A), Color(0xFF14532D)), h * 0.52f, h), Offset(0f, h * 0.52f), Size(w, h * 0.48f))
    for (i in 0 until 7) {
        if (i % 2 == 0) drawRect(Color.White.copy(alpha = 0.05f), Offset(0f, h * (0.52f + i * 0.07f)), Size(w, h * 0.07f))
    }
    // líneas de cancha luminosas
    val line = Color.White.copy(alpha = 0.75f)
    drawLine(line, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.5f, h), 3f)
    drawOval(line, Offset(w * 0.34f, h * 0.66f), Size(w * 0.32f, h * 0.22f), style = Stroke(3f))
    drawLine(line, Offset(w * 0.04f, h * 0.56f), Offset(w * 0.96f, h * 0.56f), 3f)
    drawLine(line, Offset(0f, h * 0.98f), Offset(w, h * 0.98f), 3f)
    // reflectores
    for (side in listOf(0.08f, 0.92f)) {
        val pulse = 0.18f + 0.07f * sin(time * 2f + side * 5f)
        val towerX = w * side
        val beam = Path().apply {
            moveTo(towerX, h * 0.08f); lineTo(w * (if (side < 0.5f) 0.62f else 0.38f), h * 0.95f); lineTo(w * (if (side < 0.5f) 0.30f else 0.70f), h * 0.95f); close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color(0xFFFEF9C3).copy(alpha = pulse + 0.1f), Color.Transparent), h * 0.08f, h * 0.95f))
        drawCircle(Color(0xFFFEF9C3), min(w, h) * 0.03f, Offset(towerX, h * 0.08f))
        drawLine(Color(0xFF475569), Offset(towerX, h * 0.08f), Offset(towerX, h * 0.34f), 6f)
    }
}

private fun DrawScope.drawNeonForest(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0F0A2E), Color(0xFF064E3B)), 0f, h))
    // aurora de geometría neón
    val ribbons = listOf(Color(0xFF34D399), Color(0xFFA78BFA), Color(0xFF22D3EE))
    ribbons.forEachIndexed { k, color ->
        val path = Path().apply {
            var x = 0f
            moveTo(0f, h * (0.16f + k * 0.07f))
            while (x <= w) { lineTo(x, h * (0.16f + k * 0.07f) + sin(x / w * 5f + time * 1.2f + k * 1.7f) * h * 0.05f); x += w / 50f }
        }
        drawPath(path, color.copy(alpha = 0.40f), style = Stroke(h * 0.045f))
        drawPath(path, color.copy(alpha = 0.85f), style = Stroke(2f))
    }
    // árboles neón (triángulos apilados)
    val treeNeon = listOf(Color(0xFF4ADE80), Color(0xFF22D3EE), Color(0xFFE879F9))
    for (i in 0 until 7) {
        val cx = w * (0.05f + i * 0.15f)
        val base = h * (0.88f + (i % 2) * 0.04f)
        val tw = w * 0.07f
        val th = h * 0.20f
        val neon = treeNeon[i % 3]
        for (layer in 0 until 3) {
            val y0 = base - layer * th * 0.38f
            val tri = Path().apply { moveTo(cx - tw * (1f - layer * 0.18f), y0); lineTo(cx, y0 - th * 0.55f); lineTo(cx + tw * (1f - layer * 0.18f), y0); close() }
            drawPath(tri, neon.copy(alpha = 0.10f))
            drawPath(tri, neon.copy(alpha = 0.8f), style = Stroke(2f))
        }
        drawLine(neon.copy(alpha = 0.7f), Offset(cx, base), Offset(cx, base + h * 0.06f), 3f)
    }
    // luciérnagas
    val random = Random(21)
    repeat(20) { i ->
        val bx = random.nextFloat() * w
        val by = h * (0.35f + random.nextFloat() * 0.5f)
        val x = bx + sin(time + i) * w * 0.03f
        val y = by + sin(time * 1.3f + i * 2f) * h * 0.03f
        val a = (0.4f + 0.6f * (0.5f + 0.5f * sin(time * 3f + i))).coerceIn(0f, 1f)
        drawCircle(Color(0xFFFDE047).copy(alpha = 0.25f * a), min(w, h) * 0.02f, Offset(x, y))
        drawCircle(Color(0xFFFDE047).copy(alpha = a), min(w, h) * 0.006f, Offset(x, y))
    }
}
