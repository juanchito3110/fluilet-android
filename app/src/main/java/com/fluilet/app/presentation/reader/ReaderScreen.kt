package com.fluilet.app.presentation.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.presentation.theme.FluiletTheme
import com.fluilet.app.presentation.theme.OpenDyslexicFontFamily

@Composable
fun ReaderScreen(
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
    onToggleFocusModeHardware: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = uiState.settings.palette

    FluiletTheme(palette = palette) {
        Surface(modifier = modifier.fillMaxSize(), color = Color(palette.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                FocusModeBadge(
                    isActive = uiState.isFocusModeActive,
                    onToggle = {
                        val activating = !uiState.isFocusModeActive
                        viewModel.onToggleFocusMode()
                        onToggleFocusModeHardware(activating)
                    },
                    palette = palette
                )

                AdaptiveTextCard(
                    text = uiState.simplifiedText,
                    spokenRange = uiState.spokenRange,
                    settings = uiState.settings,
                    palette = palette
                )

                AccessibilitySliderControl(
                    label = "Interlineado",
                    value = uiState.settings.lineSpacing,
                    valueRange = ReadingSettings.MIN_LINE_SPACING..ReadingSettings.MAX_LINE_SPACING,
                    valueFormatter = { "%.1fx".format(it) },
                    onValueChange = viewModel::onLineSpacingChanged,
                    palette = palette
                )

                AccessibilitySliderControl(
                    label = "Tamaño de Fuente",
                    value = uiState.settings.fontSizeSp,
                    valueRange = ReadingSettings.MIN_FONT_SIZE..ReadingSettings.MAX_FONT_SIZE,
                    valueFormatter = { "${it.toInt()}pt" },
                    onValueChange = viewModel::onFontSizeChanged,
                    palette = palette
                )

                PaletteSelector(selected = palette, onPaletteSelected = viewModel::onPaletteSelected)

                TtsControlButton(uiState.isTtsPlaying, viewModel::onToggleTts, palette)
            }
        }
    }
}

@Composable
private fun FocusModeBadge(isActive: Boolean, onToggle: () -> Unit, palette: AccessiblePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) Color(0xFFB2EBE4) else Color(palette.surface))
            .clickable(
                role = Role.Switch,
                onClickLabel = if (isActive) "Desactivar modo enfoque" else "Activar modo enfoque",
                onClick = onToggle
            )
            .sizeIn(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(if (isActive) Icons.Filled.Lock else Icons.Outlined.LockOpen, contentDescription = null)
        Text(
            if (isActive) "Modo Enfoque Activado" else "Modo Enfoque Desactivado",
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AdaptiveTextCard(
    text: String,
    spokenRange: IntRange?,
    settings: ReadingSettings,
    palette: AccessiblePalette
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(palette.surface)),
        shape = RoundedCornerShape(16.dp)
    ) {
        val annotatedText = remember(text, spokenRange, palette) {
            buildAnnotatedString {
                append(text)
                spokenRange?.let { range ->
                    if (range.first in text.indices && range.last < text.length) {
                        addStyle(
                            style = SpanStyle(
                                background = Color(palette.primary).copy(alpha = 0.35f),
                                fontWeight = FontWeight.Bold
                            ),
                            start = range.first,
                            end = range.last + 1
                        )
                    }
                }
            }
        }
        Text(
            text = annotatedText,
            style = TextStyle(
                fontFamily = OpenDyslexicFontFamily,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
                color = Color(palette.onBackground)
            ),
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Composable
private fun AccessibilitySliderControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    palette: AccessiblePalette
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium, color = Color(palette.onBackground))
            Text(valueFormatter(value), color = Color(palette.onBackground))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 44.dp)
                .semantics { contentDescription = "$label, valor actual ${valueFormatter(value)}" },
            colors = SliderDefaults.colors(
                thumbColor = Color(palette.primary),
                activeTrackColor = Color(palette.primary)
            )
        )
    }
}

@Composable
private fun PaletteSelector(selected: AccessiblePalette, onPaletteSelected: (AccessiblePalette) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AccessiblePalette.entries.forEach { option ->
            val isSelected = option == selected
            OutlinedButton(
                onClick = { onPaletteSelected(option) },
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 44.dp)
                    .semantics { contentDescription = "Paleta ${option.displayName}" },
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, Color(option.primary)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(option.background))
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(option.primary))
                    Spacer(Modifier.width(4.dp))
                }
                Text(option.displayName, color = Color(option.onBackground), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TtsControlButton(isPlaying: Boolean, onClick: () -> Unit, palette: AccessiblePalette) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(palette.primary))
    ) {
        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.VolumeUp, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (isPlaying) "Pausar Lectura Guiada (TTS)" else "Iniciar Lectura Guiada (TTS)")
    }
}