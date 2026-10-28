package com.fluilet.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.presentation.common.SectionCard

/** Botón destacado "🎓 Cargar Datos Demo" (Estadísticas y Lecturas) con el estado de la carga. */
@Composable
internal fun DemoDataCard(state: HomeUiState, onLoad: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.demo_hint), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onLoad,
                enabled = !state.isDemoLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isDemoLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.demo_loading), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(stringResource(R.string.demo_load), style = MaterialTheme.typography.labelLarge)
                }
            }
            state.demoNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

/** Lecturas demo cargadas: Real Madrid, Biología Marina, Agujeros Negros... con su original, adaptación, dato curioso y glosario. */
@Composable
internal fun DemoLessonsCard(lessons: List<AdaptationResult>, onOpen: (AdaptationResult) -> Unit) {
    if (lessons.isEmpty()) return
    SectionCard(stringResource(R.string.demo_lessons_title)) {
        Text(stringResource(R.string.demo_lessons_hint), style = MaterialTheme.typography.bodySmall)
        lessons.forEach { lesson ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lesson.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.demo_lesson_level, lesson.simplificationLevel ?: lesson.targetLevel ?: 0),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(LessonCategory.displayNameFor(lesson.category ?: ""), style = MaterialTheme.typography.bodySmall)
                    lesson.effectiveFunFact?.let { Text("🧠 $it", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        stringResource(R.string.demo_lesson_extras, lesson.effectiveVocabulary.size),
                        style = MaterialTheme.typography.labelSmall
                    )
                    OutlinedButton(onClick = { onOpen(lesson) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.demo_lesson_open), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 📚 Lecciones guardadas en el servidor para el niño (sus lecciones demo incluidas), listas para abrir y comparar. */
@Composable
internal fun LibraryCard(childName: String, items: List<LibraryItem>, openingId: String?, onOpen: (LibraryItem) -> Unit) {
    if (items.isEmpty()) return
    SectionCard(stringResource(R.string.library_title, childName)) {
        Text(stringResource(R.string.library_hint), style = MaterialTheme.typography.bodySmall)
        items.take(MAX_LIBRARY_ROWS).forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.library_row, LessonCategory.displayNameFor(item.category), item.level ?: 0),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (item.originalScore != null && item.adaptedScore != null) {
                        Text(
                            stringResource(
                                R.string.library_scores,
                                String.format(java.util.Locale.US, "%.1f", item.originalScore),
                                String.format(java.util.Locale.US, "%.1f", item.adaptedScore)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val opening = openingId == item.documentId
                    OutlinedButton(
                        onClick = { onOpen(item) },
                        enabled = openingId == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(if (opening) R.string.library_opening else R.string.library_open), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private const val MAX_LIBRARY_ROWS = 12
