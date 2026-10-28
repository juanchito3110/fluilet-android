package com.fluilet.app.presentation.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import com.fluilet.app.presentation.common.SpringProgressBar
import androidx.compose.foundation.BorderStroke
import com.fluilet.app.domain.gamification.PedagogicalSummary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonProgress
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.common.formatPercent
import com.fluilet.app.presentation.common.formatReadingTime

/**
 * 📊 Estadísticas: solo analítica. Tarjetas resumen (racha, lecciones, tiempo, comprensión), la
 * recomendación pedagógica y 3 gráficas animadas en `Canvas` (anillo, curva de comprensión y barras
 * de legibilidad Szigriszt-Pazos original vs. adaptado). Sin tienda ni juguetes.
 */
@Composable
fun ProgressDashboard(progress: EducationalProgress, note: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.progress_my_progress), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.titleMedium)
            if (progress.isEstimated) {
                Text(stringResource(R.string.progress_estimated), style = MaterialTheme.typography.bodySmall)
            }
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        SummaryTiles(progress)
        PedagogicalCard(progress.pedagogicalSummary)

        SectionCard(stringResource(R.string.progress_completion_title)) {
            DonutChartCanvas(progress.completedLessons, progress.incompleteLessons, progress.completionRatePercent)
        }
        SectionCard(stringResource(R.string.progress_evolution_chart_title)) {
            EvolutionLineChartCanvas(progress.lessons.takeLast(MAX_CHART_LESSONS))
        }
        SectionCard(stringResource(R.string.progress_chart_title)) {
            ReadabilityBarChartCanvas(progress.lessons.takeLast(MAX_CHART_LESSONS))
        }
        LevelCard(progress)
    }
}

private const val MAX_CHART_LESSONS = 8

// --- Cabecera gamificada y recomendación pedagógica ---------------------------------------------------

@Composable
private fun PedagogicalCard(summary: PedagogicalSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.progress_recommendation_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(summary.headline, style = MaterialTheme.typography.titleLarge)
            SummaryLine(stringResource(R.string.progress_strength), summary.strengthArea)
            SummaryLine(stringResource(R.string.progress_focus), summary.focusArea)
            Text(summary.recommendation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

// --- Tarjetas resumen ------------------------------------------------------------------------

@Composable
private fun SummaryTiles(progress: EducationalProgress) {
    val hasScores = progress.averageComprehension > 0.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Racha y lecturas cuentan hacia arriba; la barra de lecturas completadas se llena con resorte.
        val streak by animateIntAsState(progress.streakDays, tween(900), label = "streak")
        val completed by animateIntAsState(progress.completedLessons, tween(900), label = "completed")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("🔥", stringResource(R.string.stats_streak), stringResource(R.string.stats_days, streak), Modifier.weight(1f))
            SummaryTile(
                "📖", stringResource(R.string.stats_lessons), "$completed/${progress.totalLessons}", Modifier.weight(1f),
                fraction = if (progress.totalLessons > 0) progress.completedLessons.toFloat() / progress.totalLessons else 0f
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("⏱️", stringResource(R.string.progress_time_title), formatReadingTime(progress.totalReadingSeconds), Modifier.weight(1f))
            SummaryTile(
                "🧠", stringResource(R.string.progress_comprehension_title),
                if (hasScores) formatPercent(progress.averageComprehension) else stringResource(R.string.progress_no_quiz),
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryTile(emoji: String, label: String, value: String, modifier: Modifier = Modifier, fraction: Float? = null) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
            fraction?.let { SpringProgressBar(it, height = 8.dp) }
        }
    }
}

@Composable
private fun LevelCard(progress: EducationalProgress) {
    SectionCard(stringResource(R.string.progress_level_title)) {
        val level = NlpLevel.fromValue(progress.recommendedLevel)
        Text(stringResource(R.string.progress_level_value, progress.recommendedLevel), style = MaterialTheme.typography.headlineSmall)
        Text(level.label, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.progress_level_hint), style = MaterialTheme.typography.bodySmall)
    }
}

// --- Detalle por lección -----------------------------------------------------------------------

@Composable
internal fun LessonRow(lesson: LessonProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.progress_lesson_header, lesson.lessonNumber, LessonCategory.displayNameFor(lesson.category), lesson.level),
                style = MaterialTheme.typography.titleSmall
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lesson.completed) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (lesson.completed) stringResource(R.string.progress_completed)
                    else stringResource(R.string.progress_incomplete, lesson.completionPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (lesson.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                Text("· ${formatReadingTime(lesson.durationSeconds)}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                lesson.comprehensionScore?.let { stringResource(R.string.progress_lesson_comprehension, it) }
                    ?: stringResource(R.string.progress_lesson_no_quiz),
                style = MaterialTheme.typography.bodyMedium
            )
            val jump = lesson.readabilityJump
            if (jump != null) {
                Text(
                    stringResource(R.string.progress_lesson_jump, lesson.originalScore ?: 0.0, lesson.adaptedScore ?: 0.0, jump),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// --- Insignias ---------------------------------------------------------------------------------

@Composable
internal fun BadgeGallery(badges: List<EducationalBadge>) {
    SectionCard(stringResource(R.string.progress_badges_title)) {
        val unlocked = badges.count { it.unlocked }
        Text(stringResource(R.string.progress_badges_count, unlocked, badges.size), style = MaterialTheme.typography.bodyMedium)
        badges.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { BadgeTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun badgeIcon(code: String): ImageVector = when (code) {
    "primera_leccion" -> Icons.Filled.Flag
    "mente_brillante" -> Icons.Filled.Lightbulb
    "habito_en_marcha" -> Icons.Filled.LocalFireDepartment
    "explorador_curioso" -> Icons.Filled.Explore
    else -> Icons.Filled.TrendingUp // subiendo_nivel
}

@Composable
private fun BadgeTile(badge: EducationalBadge, modifier: Modifier = Modifier) {
    val container = if (badge.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (badge.unlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val state = stringResource(if (badge.unlocked) R.string.progress_badge_unlocked else R.string.progress_badge_locked)
    Card(
        // Bloqueadas en tono tenue; el estado también se comunica con icono y texto (no solo con color).
        modifier = modifier.alpha(if (badge.unlocked) 1f else 0.5f).semantics(mergeDescendants = true) {
            contentDescription = "${badge.title}: $state. ${badge.description}"
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (badge.unlocked) badgeIcon(badge.code) else Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(badge.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(badge.description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text(state, style = MaterialTheme.typography.labelSmall)
        }
    }
}
