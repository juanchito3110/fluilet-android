package com.fluilet.app.presentation.newreading

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.text.Szigriszt
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.home.NlpLevelSlider
import com.fluilet.app.presentation.theme.OpenDyslexicFontFamily

@Composable
fun NewReadingScreen(onDone: () -> Unit, onCancel: () -> Unit, viewModel: NewReadingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onDone() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onFilePicked(it) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.newreading_title), style = MaterialTheme.typography.headlineMedium)
            if (state.readerName.isNotBlank()) {
                Text(stringResource(R.string.newreading_for, state.readerName), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.newreading_privacy), style = MaterialTheme.typography.bodySmall)

            val result = state.result
            if (result == null) {
                InputSection(state, viewModel, onPick = { picker.launch(arrayOf("text/plain", "application/pdf")) })
                ErrorMessage(state.errorMessage)
                PrimaryButton(
                    text = stringResource(R.string.newreading_adapt),
                    onClick = viewModel::adapt,
                    enabled = state.canAdapt,
                    loading = state.isAdapting,
                    icon = Icons.Filled.AutoFixHigh
                )
                SecondaryButton(stringResource(R.string.action_cancel), onCancel)
            } else {
                ResultSection(result)
                PrimaryButton(stringResource(R.string.newreading_use), viewModel::useReading)
                SecondaryButton(stringResource(R.string.newreading_edit), viewModel::backToEdit)
            }
        }
    }
}

@Composable
private fun InputSection(state: NewReadingUiState, vm: NewReadingViewModel, onPick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.mode == InputMode.PASTE,
            onClick = { vm.onMode(InputMode.PASTE) },
            label = { Text(stringResource(R.string.newreading_mode_paste), style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.heightIn(min = 48.dp)
        )
        FilterChip(
            selected = state.mode == InputMode.FILE,
            onClick = { vm.onMode(InputMode.FILE) },
            label = { Text(stringResource(R.string.newreading_mode_file), style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.heightIn(min = 48.dp)
        )
    }

    if (state.mode == InputMode.PASTE) {
        OutlinedTextField(
            value = state.text,
            onValueChange = vm::onText,
            label = { Text(stringResource(R.string.newreading_text_label)) },
            minLines = 6,
            maxLines = 14,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        SecondaryButton(
            text = state.fileName ?: stringResource(R.string.newreading_pick_file),
            onClick = onPick,
            icon = Icons.Filled.AttachFile
        )
        Text(stringResource(R.string.newreading_file_hint), style = MaterialTheme.typography.bodySmall)
    }

    NlpLevelSlider(state.level, vm::onLevel)
}

@Composable
private fun ResultSection(result: AdaptationResult) {
    val m = result.metrics
    SectionCard(stringResource(R.string.newreading_result_title)) {
        Text(result.title, style = MaterialTheme.typography.titleSmall)
        MetricRow(R.string.newreading_m_original, "%.1f · %s".format(m.originalSzigrisztScore, Szigriszt.level(m.originalSzigrisztScore)))
        MetricRow(R.string.newreading_m_adapted, "%.1f · %s".format(m.adaptedSzigrisztScore, Szigriszt.level(m.adaptedSzigrisztScore)))
        MetricRow(R.string.newreading_m_delta, "%+.1f".format(m.scoreDelta))
        MetricRow(R.string.newreading_m_words, "${m.originalWordCount} → ${m.adaptedWordCount}")
        MetricRow(R.string.newreading_m_sentences, m.sentenceCount.toString())
        MetricRow(R.string.newreading_m_syllables, m.syllableCount.toString())
        MetricRow(R.string.newreading_m_blocks, result.chunks.size.toString())
    }
    SectionCard(stringResource(R.string.newreading_preview_title)) {
        result.chunks.take(2).forEach { chunk ->
            Text(
                chunk.text,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontFamily = OpenDyslexicFontFamily,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun MetricRow(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
