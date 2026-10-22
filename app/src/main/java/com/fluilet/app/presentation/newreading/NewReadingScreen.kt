package com.fluilet.app.presentation.newreading

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.PlayArrow
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.presentation.common.CheckMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.text.Szigriszt
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.home.NlpLevelSlider
import com.fluilet.app.presentation.parentalgate.GateMode
import com.fluilet.app.presentation.parentalgate.ParentalGateDialog
import com.fluilet.app.presentation.theme.OpenDyslexicFontFamily

@Composable
fun NewReadingScreen(
    onSaved: () -> Unit,
    onStartNow: () -> Unit,
    onCancel: () -> Unit,
    viewModel: NewReadingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPinSetup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.done.collect { outcome ->
            when (outcome) {
                NewReadingOutcome.SAVED -> onSaved()
                NewReadingOutcome.START_NOW -> onStartNow()
            }
        }
    }

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
                    text = stringResource(if (state.mode == InputMode.GENERATE) R.string.newreading_generate else R.string.newreading_adapt),
                    onClick = viewModel::adapt,
                    enabled = state.canAdapt,
                    loading = state.isAdapting,
                    icon = Icons.Filled.AutoFixHigh
                )
                SecondaryButton(stringResource(R.string.action_cancel), onCancel)
            } else {
                ResultSection(result)
                // Traspaso directo Tutor -> Niño: un toque y el niño empieza a leer en Modo Enfoque.
                PrimaryButton(
                    text = stringResource(if (state.forChild) R.string.newreading_start_focus else R.string.newreading_start),
                    onClick = {
                        // Con perfil de niño el PIN del tutor debe existir antes de bloquear la pantalla.
                        if (state.forChild && !state.pinConfigured) showPinSetup = true else viewModel.startNow()
                    },
                    icon = Icons.Filled.PlayArrow
                )
                SecondaryButton(stringResource(R.string.newreading_save_later), viewModel::saveForLater)
                SecondaryButton(stringResource(R.string.newreading_edit), viewModel::backToEdit)
            }
        }
    }

    PinSetupHost(
        show = showPinSetup,
        onDone = {
            showPinSetup = false
            viewModel.refreshPin()
            viewModel.startNow()
        },
        onDismiss = { showPinSetup = false }
    )
}

@Composable
private fun PinSetupHost(show: Boolean, onDone: () -> Unit, onDismiss: () -> Unit) {
    if (show) {
        ParentalGateDialog(mode = GateMode.SETUP, onSuccess = { onDone() }, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputSection(state: NewReadingUiState, vm: NewReadingViewModel, onPick: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectableChip(
            selected = state.mode == InputMode.PASTE,
            onClick = { vm.onMode(InputMode.PASTE) },
            label = { Text(stringResource(R.string.newreading_mode_paste), style = MaterialTheme.typography.labelLarge) }
        )
        SelectableChip(
            selected = state.mode == InputMode.FILE,
            onClick = { vm.onMode(InputMode.FILE) },
            label = { Text(stringResource(R.string.newreading_mode_file), style = MaterialTheme.typography.labelLarge) }
        )
        SelectableChip(
            selected = state.mode == InputMode.GENERATE,
            onClick = { vm.onMode(InputMode.GENERATE) },
            label = { Text(stringResource(R.string.newreading_mode_generate), style = MaterialTheme.typography.labelLarge) }
        )
    }

    if (state.mode == InputMode.GENERATE) {
        LessonGeneratorFields(state, vm)
        return
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonGeneratorFields(state: NewReadingUiState, vm: NewReadingViewModel) {
    Text(stringResource(R.string.newreading_category_title), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LessonCategory.entries.forEach { category ->
            SelectableChip(
                selected = category == state.category,
                onClick = { vm.onCategory(category) },
                leadingIcon = { Icon(categoryIcon(category), contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(category.displayName, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
    OutlinedTextField(
        value = state.topic,
        onValueChange = vm::onTopic,
        label = { Text(stringResource(R.string.newreading_topic_label)) },
        supportingText = { Text("${state.topic.length}/${LessonRequest.MAX_TOPIC_LENGTH}") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    NlpLevelSlider(state.level, vm::onLevel)
}

fun categoryIcon(category: LessonCategory): ImageVector = when (category) {
    LessonCategory.CIENCIA_ESPACIO -> Icons.Filled.RocketLaunch
    LessonCategory.ANIMALES_NATURALEZA -> Icons.Filled.Pets
    LessonCategory.HISTORIA_MISTERIOS -> Icons.Filled.HistoryEdu
    LessonCategory.DEPORTES -> Icons.Filled.SportsSoccer
    LessonCategory.TECNOLOGIA -> Icons.Filled.Memory
    LessonCategory.CUENTOS_AVENTURAS -> Icons.Filled.AutoStories
}

@Composable
private fun ResultSection(result: AdaptationResult) {
    val m = result.metrics
    SectionCard(stringResource(R.string.newreading_result_title)) {
        Text(result.title, style = MaterialTheme.typography.titleSmall)
        result.category?.let { MetricRow(R.string.newreading_m_category, LessonCategory.displayNameFor(it)) }
        result.simplificationLevel?.let { MetricRow(R.string.newreading_m_level, it.toString()) }
        if (result.questions.isNotEmpty()) MetricRow(R.string.newreading_m_quiz, stringResource(R.string.newreading_quiz_count, result.questions.size))
        if (result.bestEffort) Text(stringResource(R.string.newreading_best_effort), style = MaterialTheme.typography.bodySmall)
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
    // La etiqueta y el valor se reparten el ancho: con tipografía grande el valor baja de línea en vez de pegarse a la etiqueta.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}
