package com.fluilet.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluilet.app.R
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.newreading.NewReadingContent
import com.fluilet.app.presentation.newreading.NewReadingViewModel

private const val MAX_HISTORY_ROWS = 10

/** 📚 Lecturas: perfil, crear una lectura (pegar / subir / generar), lectura lista e historial limpio. */
@Composable
internal fun ReadingsTab(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onNewChild: () -> Unit,
    onEditChild: (String) -> Unit,
    onDeleteChild: (ChildProfile) -> Unit,
    onNlpLevel: (NlpLevel) -> Unit,
    onStartReading: () -> Unit,
    onReadingSaved: () -> Unit,
    onPin: () -> Unit,
    onNewReadingStart: () -> Unit,
    onLoadDemo: () -> Unit,
    onOpenDemoLesson: (com.fluilet.app.domain.model.AdaptationResult) -> Unit,
    readingViewModel: NewReadingViewModel = hiltViewModel()
) {
    // El creador de lecturas siempre trabaja para el perfil seleccionado en la parte superior.
    LaunchedEffect(state.selectedChildId, state.isTutor) {
        readingViewModel.bindChild(if (state.isTutor) state.selectedChildId else null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.isTutor) {
            ChildrenSection(
                state = state,
                onSelect = onSelect,
                onNew = onNewChild,
                onEdit = onEditChild,
                onDelete = onDeleteChild,
                onNlpLevel = onNlpLevel
            )
        }

        DemoDataCard(state, onLoadDemo)
        DemoLessonsCard(state.demoLessons, onOpenDemoLesson)

        val reading = state.readingForSelection
        if (reading != null) {
            SectionCard(stringResource(R.string.home_reading_title)) {
                Text(reading.adaptation.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.home_reading_ready, reading.adaptation.chunks.size, reading.adaptation.metrics.adaptedSzigrisztScore),
                    style = MaterialTheme.typography.bodyMedium
                )
                PrimaryButton(
                    text = stringResource(if (state.isTutor) R.string.home_start_focus else R.string.home_start),
                    onClick = onStartReading,
                    icon = Icons.Filled.PlayArrow
                )
            }
        }

        if (!state.isTutor || state.selectedChild != null) {
            NewReadingContent(onSaved = onReadingSaved, onStartNow = onNewReadingStart, viewModel = readingViewModel)
        } else {
            SectionCard(stringResource(R.string.newreading_title)) {
                Text(stringResource(R.string.home_children_empty), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard(stringResource(R.string.readings_history_title)) {
            val lessons = state.progress.lessons
            if (lessons.isEmpty()) {
                Text(stringResource(R.string.readings_history_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                lessons.asReversed().take(MAX_HISTORY_ROWS).forEach { LessonRow(it) }
            }
        }

        if (state.isTutor) {
            SectionCard(stringResource(R.string.home_pin_title)) {
                Text(
                    stringResource(if (state.pinConfigured) R.string.home_pin_set else R.string.home_pin_missing),
                    style = MaterialTheme.typography.bodyMedium
                )
                SecondaryButton(
                    text = stringResource(if (state.pinConfigured) R.string.home_pin_change else R.string.home_pin_create),
                    onClick = onPin,
                    icon = Icons.Filled.Lock
                )
            }
        }
    }
}
