package com.fluilet.app.presentation.child

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.presentation.common.CheckMark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.DifficultyType
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.FullScreenLoading
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.home.NlpLevelSlider
import com.fluilet.app.presentation.reader.PaletteSwatch
import com.fluilet.app.presentation.theme.OpenDyslexicFontFamily
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildFormScreen(onDone: () -> Unit, viewModel: ChildFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.saved.collect { onDone() } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.isLoading) {
            FullScreenLoading()
            return@Surface
        }
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(if (state.isEditing) R.string.child_edit_title else R.string.child_new_title),
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.child_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            val dateText = state.birthDate?.let {
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")).format(it)
            } ?: stringResource(R.string.child_birth_pick)
            SecondaryButton(
                text = dateText,
                onClick = { showDatePicker = true },
                enabled = !state.isEditing, // el backend no permite cambiar la fecha de nacimiento
                icon = Icons.Filled.CalendarMonth
            )
            ErrorMessage(state.birthDateError)

            Text(stringResource(R.string.child_difficulty), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyType.entries.forEach { type ->
                    SelectableChip(
                        selected = type == state.difficulty,
                        onClick = { viewModel.onDifficulty(type) },
                        label = { Text(type.label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.height(48.dp)
                    )
                }
            }

            NlpLevelSlider(state.nlpLevel, viewModel::onNlp)

            Text(stringResource(R.string.reader_palette), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccessiblePalette.entries.forEach { palette ->
                    PaletteSwatch(palette, selected = palette == state.palette) { viewModel.onPalette(palette) }
                }
            }

            LabeledSlider(R.string.reader_font_size, state.fontSizeSp, ReadingSettings.MIN_FONT_SIZE..ReadingSettings.MAX_FONT_SIZE, "${state.fontSizeSp.toInt()} pt", viewModel::onFontSize)
            LabeledSlider(R.string.reader_line_spacing, state.lineSpacing, ReadingSettings.MIN_LINE_SPACING..ReadingSettings.MAX_LINE_SPACING, "%.1fx".format(state.lineSpacing), viewModel::onLineSpacing)

            // Vista previa con la paleta y tipografía elegidas.
            Text(
                stringResource(R.string.child_preview_text),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Color(state.palette.background)).padding(16.dp),
                style = TextStyle(
                    fontFamily = OpenDyslexicFontFamily,
                    fontSize = state.fontSizeSp.sp,
                    lineHeight = (state.fontSizeSp * state.lineSpacing).sp,
                    color = Color(state.palette.onBackground)
                )
            )

            ErrorMessage(state.errorMessage)
            PrimaryButton(stringResource(R.string.action_save), viewModel::save, enabled = state.canSave, loading = state.isSaving)
            SecondaryButton(stringResource(R.string.action_cancel), onDone)
        }
    }

    if (showDatePicker) {
        val today = LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.birthDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = (today.year - 12)..today.year
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.onBirthDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun LabeledSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
            Text(valueText, style = MaterialTheme.typography.titleSmall)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth().height(48.dp))
    }
}
