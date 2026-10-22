package com.fluilet.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** "Mi avance": cumplimiento de lecciones, evolución por lección e insignias educativas. */
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

        SummaryCards(progress)

        SectionCard(stringResource(R.string.progress_evolution_title)) {
            if (progress.lessons.isEmpty()) {
                Text(stringResource(R.string.progress_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                ReadabilityChart(progress.lessons.takeLast(MAX_CHART_LESSONS))
                progress.lessons.asReversed().take(MAX_LESSON_ROWS).forEach { LessonRow(it) }
            }
        }

        BadgeGallery(progress.badges)
    }
}

private const val MAX_CHART_LESSONS = 8
private const val MAX_LESSON_ROWS = 10

// --- Tarjetas resumen ------------------------------------------------------------------------

@Composable
private fun SummaryCards(progress: EducationalProgress) {
    SectionCard(stringResource(R.string.progress_completion_title)) {
        Text(
            stringResource(R.string.progress_completed_of, progress.completedLessons, progress.totalLessons),
            style = MaterialTheme.typography.headlineSmall
        )
        PercentBar(progress.completionRatePercent, stringResource(R.string.progress_completion_title))
        Text(
            stringResource(
                R.string.progress_completion_detail,
                formatPercent(progress.completionRatePercent),
                progress.incompleteLessons
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    SectionCard(stringResource(R.string.progress_time_title)) {
        Text(formatReadingTime(progress.totalReadingSeconds), style = MaterialTheme.typography.headlineSmall)
    }
    SectionCard(stringResource(R.string.progress_comprehension_title)) {
        val hasScores = progress.averageComprehension > 0.0
        Text(
            if (hasScores) formatPercent(progress.averageComprehension) else stringResource(R.string.progress_no_quiz),
            style = MaterialTheme.typography.headlineSmall
        )
        if (hasScores) PercentBar(progress.averageComprehension, stringResource(R.string.progress_comprehension_title))
    }
    SectionCard(stringResource(R.string.progress_level_title)) {
        val level = NlpLevel.fromValue(progress.recommendedLevel)
        Text(stringResource(R.string.progress_level_value, progress.recommendedLevel), style = MaterialTheme.typography.headlineSmall)
        Text(level.label, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.progress_level_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PercentBar(percent: Double, description: String) {
    val fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    Box(
        Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .semantics { contentDescription = "$description: ${formatPercent(percent)}" }
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary))
    }
}

// --- Evolución por lección -------------------------------------------------------------------

/** Barras pareadas por lección: legibilidad del texto original vs. adaptado (0–100). */
@Composable
private fun ReadabilityChart(lessons: List<LessonProgress>) {
    val withScores = lessons.any { it.originalScore != null || it.adaptedScore != null }
    if (!withScores) return
    Text(stringResource(R.string.progress_chart_title), style = MaterialTheme.typography.titleSmall)
    Row(
        Modifier.fillMaxWidth().height(CHART_HEIGHT + 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        lessons.forEach { lesson ->
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = "Lección ${lesson.lessonNumber}: legibilidad original %.0f, adaptada %.0f".format(
                        lesson.originalScore ?: 0.0, lesson.adaptedScore ?: 0.0
                    )
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.height(CHART_HEIGHT), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    ChartBar(lesson.originalScore, MaterialTheme.colorScheme.outline)
                    ChartBar(lesson.adaptedScore, MaterialTheme.colorScheme.primary)
                }
                Text("L${lesson.lessonNumber}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(MaterialTheme.colorScheme.outline, stringResource(R.string.progress_original))
        LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.progress_adapted))
    }
}

private val CHART_HEIGHT = 120.dp

@Composable
private fun ChartBar(score: Double?, color: androidx.compose.ui.graphics.Color) {
    val fraction = ((score ?: 0.0) / 100.0).coerceIn(0.02, 1.0).toFloat()
    Box(Modifier.width(14.dp).fillMaxHeight(fraction).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LessonRow(lesson: LessonProgress) {
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
private fun BadgeGallery(badges: List<EducationalBadge>) {
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
