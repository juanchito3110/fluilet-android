package com.fluilet.app.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.presentation.common.formatExactPercent
import kotlin.math.max
import kotlin.math.min

/** Anima de 0 a 1 cada vez que cambian los datos (entrada suave de las gráficas). */
@Composable
private fun rememberChartProgress(key: Any): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    return progress.value
}

private fun DrawScope.centeredText(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout: TextLayoutResult = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

// --- Gráfica 1: anillo de cumplimiento ---------------------------------------------------------------

/**
 * Anillo animado con extremos redondeados y el porcentaje exacto de cumplimiento en el centro.
 * La leyenda distingue Lecciones Completadas (100 %) de las que siguen En progreso.
 */
@Composable
fun DonutChartCanvas(
    completed: Int,
    inProgress: Int,
    completionPercent: Double,
    modifier: Modifier = Modifier
) {
    val anim = rememberChartProgress(Triple(completed, inProgress, completionPercent))
    val track = MaterialTheme.colorScheme.outlineVariant
    val done = MaterialTheme.colorScheme.primary
    val pending = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurface
    val label = formatExactPercent(completionPercent)
    val total = completed + inProgress

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Canvas(
            Modifier.size(150.dp).semantics { contentDescription = "Cumplimiento de lecciones: $label, $completed completadas y $inProgress en progreso" }
        ) {
            val stroke = size.minDimension * 0.15f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total > 0) {
                val completedSweep = 360f * (completed.toFloat() / total) * anim
                val pendingSweep = 360f * (inProgress.toFloat() / total) * anim
                // Las lecciones en progreso empiezan donde terminan las completadas.
                if (pendingSweep > 0.5f) drawArc(pending, -90f + completedSweep, pendingSweep, false, topLeft, arcSize, style = Stroke(stroke * 0.55f, cap = StrokeCap.Round))
                if (completedSweep > 0.5f) drawArc(done, -90f, completedSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            centeredText(measurer, label, center, TextStyle(fontSize = (size.minDimension * 0.17f).toSp(), color = textColor))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendItem(done, "Lecciones Completadas (100%)", completed)
            LegendItem(pending, "En progreso", inProgress)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("$count", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// --- Gráfica 2: curva de evolución de comprensión y nivel ----------------------------------------------

/**
 * Línea de comprensión (0–100 %) por lección con cuadrícula sutil, área con degradado, puntos marcados
 * (L1, L2…), etiquetas de porcentaje y el nivel de cada lección (N1..N5).
 */
@Composable
fun EvolutionLineChartCanvas(lessons: List<LessonProgress>, modifier: Modifier = Modifier) {
    val anim = rememberChartProgress(lessons.map { Triple(it.lessonNumber, it.comprehensionScore, it.level) })
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val text = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val hasScores = lessons.any { it.comprehensionScore != null }
    val summary = lessons.joinToString("; ") { "Lección ${it.lessonNumber}: ${it.comprehensionScore?.let { s -> "$s %" } ?: "sin reto"}, nivel ${it.level}" }

    if (lessons.isEmpty() || !hasScores) {
        Text("Cuando respondas mini-retos verás aquí la curva de tu comprensión.", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Canvas(modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = "Evolución de la comprensión. $summary" }) {
        val labelStyle = TextStyle(fontSize = 10.sp, color = text)
        val left = 40.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - 40.dp.toPx()
        val plotW = right - left
        val plotH = bottom - top

        // cuadrícula + eje Y (0–100 %)
        for (pct in listOf(0, 25, 50, 75, 100)) {
            val y = bottom - plotH * pct / 100f
            drawLine(grid.copy(alpha = if (pct == 0) 0.8f else 0.35f), Offset(left, y), Offset(right, y), 1.dp.toPx())
            centeredText(measurer, "$pct%", Offset(left - 18.dp.toPx(), y), labelStyle)
        }

        val n = lessons.size
        fun xAt(i: Int) = if (n == 1) left + plotW / 2f else left + plotW * i / (n - 1)
        fun yAt(score: Int) = bottom - plotH * score.coerceIn(0, 100) / 100f

        // etiquetas del eje X: lección y nivel
        lessons.forEachIndexed { i, lesson ->
            centeredText(measurer, "L${lesson.lessonNumber}", Offset(xAt(i), bottom + 12.dp.toPx()), labelStyle)
            centeredText(measurer, "N${lesson.level}", Offset(xAt(i), bottom + 26.dp.toPx()), labelStyle.copy(color = line))
        }

        val points = lessons.mapIndexedNotNull { i, l -> l.comprehensionScore?.let { Triple(i, Offset(xAt(i), yAt(it)), it) } }
        clipRect(right = left + plotW * anim + 12.dp.toPx()) {
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().second.x, points.first().second.y)
                    points.drop(1).forEach { lineTo(it.second.x, it.second.y) }
                }
                val area = Path().apply {
                    addPath(path)
                    lineTo(points.last().second.x, bottom)
                    lineTo(points.first().second.x, bottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.38f), line.copy(alpha = 0.02f)), top, bottom))
                drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.forEach { (_, p, score) ->
                drawCircle(surface, 7.dp.toPx(), p)
                drawCircle(line, 5.dp.toPx(), p)
                centeredText(measurer, "$score%", Offset(p.x, p.y - 14.dp.toPx()), labelStyle)
            }
        }
    }
}

// --- Gráfica 3: barras comparativas de accesibilidad textual -----------------------------------------------

/** Barras pareadas por lección: legibilidad Szigriszt-Pazos del texto Original frente al Adaptado. */
@Composable
fun ReadabilityBarChartCanvas(lessons: List<LessonProgress>, modifier: Modifier = Modifier) {
    val data = lessons.filter { it.originalScore != null || it.adaptedScore != null }
    if (data.isEmpty()) {
        Text("Aún no hay lecciones adaptadas para comparar.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val anim = rememberChartProgress(data.map { Triple(it.lessonNumber, it.originalScore, it.adaptedScore) })
    val originalColor = MaterialTheme.colorScheme.outline
    val adaptedColor = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val text = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val summary = data.joinToString("; ") { "Lección ${it.lessonNumber}: original %.0f, adaptado %.0f".format(it.originalScore ?: 0.0, it.adaptedScore ?: 0.0) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(210.dp).semantics { contentDescription = "Legibilidad original frente a adaptada. $summary" }) {
            val labelStyle = TextStyle(fontSize = 10.sp, color = text)
            val left = 34.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 26.dp.toPx()
            val maxValue = max(100.0, data.maxOf { max(it.originalScore ?: 0.0, it.adaptedScore ?: 0.0) }).toFloat()
            fun h(v: Double?) = ((v ?: 0.0).coerceAtLeast(0.0).toFloat() / maxValue) * (bottom - top) * anim

            for (value in listOf(0f, maxValue / 2f, maxValue)) {
                val y = bottom - (bottom - top) * value / maxValue
                drawLine(grid.copy(alpha = if (value == 0f) 0.8f else 0.35f), Offset(left, y), Offset(right, y), 1.dp.toPx())
                centeredText(measurer, "%.0f".format(value), Offset(left - 14.dp.toPx(), y), labelStyle)
            }

            val groupW = (right - left) / data.size
            val barW = min(22.dp.toPx(), groupW * 0.32f)
            data.forEachIndexed { i, lesson ->
                val cx = left + groupW * (i + 0.5f)
                val oh = h(lesson.originalScore)
                val ah = h(lesson.adaptedScore)
                drawRoundRect(originalColor, Offset(cx - barW - 2.dp.toPx(), bottom - oh), Size(barW, oh), CornerRadius(5.dp.toPx(), 5.dp.toPx()))
                drawRoundRect(adaptedColor, Offset(cx + 2.dp.toPx(), bottom - ah), Size(barW, ah), CornerRadius(5.dp.toPx(), 5.dp.toPx()))
                if (anim > 0.9f) {
                    centeredText(measurer, "%.0f".format(lesson.originalScore ?: 0.0), Offset(cx - barW / 2 - 2.dp.toPx(), bottom - oh - 8.dp.toPx()), labelStyle)
                    centeredText(measurer, "%.0f".format(lesson.adaptedScore ?: 0.0), Offset(cx + barW / 2 + 2.dp.toPx(), bottom - ah - 8.dp.toPx()), labelStyle.copy(color = adaptedColor))
                }
                centeredText(measurer, "L${lesson.lessonNumber}", Offset(cx, bottom + 12.dp.toPx()), labelStyle)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartLegend(originalColor, "Texto original")
            ChartLegend(adaptedColor, "Texto adaptado")
        }
        Text(
            "Puntaje Szigriszt-Pazos: mientras más alto, más fácil de leer. Esta gráfica muestra cuánto se simplificó cada texto.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
