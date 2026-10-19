package com.fluilet.app.presentation.reader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.core.tts.TtsStatus
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.formatDuration
import com.fluilet.app.presentation.parentalgate.GateMode
import com.fluilet.app.presentation.parentalgate.ParentalGateDialog
import com.fluilet.app.presentation.theme.FluiletTheme
import com.fluilet.app.presentation.theme.OpenDyslexicFontFamily

@Composable
fun ReaderScreen(onExit: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // POST_NOTIFICATIONS (API 33+): permite ver la notificación del servicio. La lectura inicia igual.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.start()
    }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.start()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ReaderEvent.Exited -> onExit()
            }
        }
    }

    // Atrás nunca sale directo: abre el Muro Parental cuando hay Modo Enfoque.
    BackHandler(onBack = viewModel::requestExit)

    FluiletTheme(palette = state.settings.palette) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (state.reading == null) {
                EmptyReader(onBack = viewModel::requestExit)
            } else {
                ReaderContent(state, viewModel)
            }
        }

        if (state.showSettings) {
            SettingsSheet(state.settings, viewModel)
        }
        if (state.showGate) {
            ParentalGateDialog(
                mode = GateMode.VERIFY,
                onSuccess = viewModel::onGateVerified,
                onDismiss = viewModel::dismissGate
            )
        }
    }
}

@Composable
private fun EmptyReader(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.reader_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reader_empty_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.reader_back), onBack)
    }
}

@Composable
private fun ReaderContent(state: ReaderUiState, vm: ReaderViewModel) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReaderTopBar(state, vm)
        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                .semantics { contentDescription = "Progreso de lectura ${(state.progressFraction * 100).toInt()} por ciento" }
        )

        Box(Modifier.weight(1f)) {
            ChunkCard(state)
        }

        if (state.completed) {
            CompletionPanel(onAgain = vm::restart, onExit = vm::requestExit)
        } else {
            SpeechUnavailableBanner(state)
            ReaderControls(state, vm)
        }
    }
}

@Composable
private fun ReaderTopBar(state: ReaderUiState, vm: ReaderViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.reader_block_of, state.chunkIndex + 1, state.chunkCount),
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.lockActive) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.reader_focus_on), style = MaterialTheme.typography.labelMedium)
                }
                Text(formatDuration(state.elapsedSeconds), style = MaterialTheme.typography.labelMedium)
            }
        }
        IconButton(onClick = vm::openSettings, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.reader_settings))
        }
        IconButton(onClick = vm::requestExit, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.reader_exit))
        }
    }
}

@Composable
private fun ChunkCard(state: ReaderUiState) {
    val settings = state.settings
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val text = state.currentText
    val highlight = state.tts.highlight
    val scroll = rememberScrollStateKeyed(state.chunkIndex)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // El resaltado va en el AnnotatedString (solo fondo: sin cambiar el grosor para no reflujar el texto).
    val annotated = remember(text, highlight, highlightColor) {
        buildAnnotatedString {
            append(text)
            if (highlight != null && highlight.first in text.indices) {
                addStyle(SpanStyle(background = highlightColor), highlight.first, (highlight.last + 1).coerceAtMost(text.length))
            }
        }
    }

    // Mantiene visible la palabra que se está leyendo en bloques largos.
    LaunchedEffect(highlight) {
        val l = layout
        if (l != null && highlight != null && highlight.first < l.layoutInput.text.length) {
            val top = l.getBoundingBox(highlight.first).top
            scroll.animateScrollTo((top - 160f).toInt().coerceAtLeast(0))
        }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)) {
            androidx.compose.material3.Text(
                text = annotated,
                onTextLayout = { layout = it },
                style = TextStyle(
                    fontFamily = OpenDyslexicFontFamily,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
                    letterSpacing = settings.letterSpacingEm.em,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}

@Composable
private fun rememberScrollStateKeyed(key: Any) = androidx.compose.runtime.key(key) { rememberScrollState() }

@Composable
private fun ReaderControls(state: ReaderUiState, vm: ReaderViewModel) {
    val playing = state.tts.status == TtsStatus.Playing
    val voiceReady = state.tts.status != TtsStatus.Unavailable && state.tts.status != TtsStatus.Initializing &&
        state.tts.status != TtsStatus.Uninitialized

    Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ErrorMessage(state.tts.errorMessage.takeIf { state.tts.status != TtsStatus.Unavailable })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = vm::previous, enabled = state.chunkIndex > 0, modifier = Modifier.size(64.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_previous))
            }
            FilledIconButton(
                onClick = vm::togglePlayPause,
                enabled = voiceReady,
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors()
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        when {
                            playing -> R.string.reader_pause
                            state.tts.status == TtsStatus.Paused -> R.string.reader_resume
                            else -> R.string.reader_play
                        }
                    ),
                    modifier = Modifier.size(40.dp)
                )
            }
            FilledTonalIconButton(
                onClick = vm::stopSpeech,
                enabled = state.tts.status == TtsStatus.Playing || state.tts.status == TtsStatus.Paused,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.reader_stop))
            }
            FilledTonalIconButton(onClick = vm::next, enabled = !state.isLastChunk, modifier = Modifier.size(64.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.reader_next))
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedChip(R.string.reader_speed_slow, 0.7f, state, vm)
            SpeedChip(R.string.reader_speed_normal, 0.85f, state, vm)
            SpeedChip(R.string.reader_speed_fast, 1.1f, state, vm)
        }

        if (state.isLastChunk) {
            PrimaryButton(stringResource(R.string.reader_finish), vm::finishReading, icon = Icons.Filled.Check)
        }
    }
}

@Composable
private fun SpeedChip(label: Int, rate: Float, state: ReaderUiState, vm: ReaderViewModel) {
    val selected = kotlin.math.abs(state.settings.speechRate - rate) < 0.03f
    FilterChip(
        selected = selected,
        onClick = { vm.onSpeechRate(rate) },
        label = { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.height(48.dp)
    )
}

@Composable
private fun SpeechUnavailableBanner(state: ReaderUiState) {
    if (state.tts.status != TtsStatus.Unavailable) return
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ErrorMessage(state.tts.errorMessage)
        if (state.tts.missingVoiceData) {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    // El dispositivo no ofrece instalador de voces; la lectura sigue sin audio.
                }
            }) { Text(stringResource(R.string.reader_install_voice)) }
        }
    }
}

@Composable
private fun CompletionPanel(onAgain: () -> Unit, onExit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reader_done_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.reader_done_body), style = MaterialTheme.typography.bodyLarge)
            SecondaryButton(stringResource(R.string.reader_again), onAgain)
            PrimaryButton(stringResource(R.string.reader_exit), onExit, icon = Icons.Filled.Lock)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(settings: ReadingSettings, vm: ReaderViewModel) {
    ModalBottomSheet(onDismissRequest = vm::closeSettings, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge)
            SettingSlider(R.string.reader_font_size, settings.fontSizeSp, ReadingSettings.MIN_FONT_SIZE..ReadingSettings.MAX_FONT_SIZE, "${settings.fontSizeSp.toInt()} pt", vm::onFontSize)
            SettingSlider(R.string.reader_line_spacing, settings.lineSpacing, ReadingSettings.MIN_LINE_SPACING..ReadingSettings.MAX_LINE_SPACING, "%.1fx".format(settings.lineSpacing), vm::onLineSpacing)
            SettingSlider(R.string.reader_letter_spacing, settings.letterSpacingEm, ReadingSettings.MIN_LETTER_SPACING..ReadingSettings.MAX_LETTER_SPACING, "%.2f".format(settings.letterSpacingEm), vm::onLetterSpacing)
            SettingSlider(R.string.reader_speech_rate, settings.speechRate, ReadingSettings.MIN_SPEECH_RATE..ReadingSettings.MAX_SPEECH_RATE, "%.2fx".format(settings.speechRate), vm::onSpeechRate)

            Text(stringResource(R.string.reader_palette), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccessiblePalette.entries.forEach { palette ->
                    PaletteSwatch(palette, selected = palette == settings.palette) { vm.onPalette(palette) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
            Text(valueText, style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { stateDescription = valueText }
        )
    }
}

@Composable
fun PaletteSwatch(palette: AccessiblePalette, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier.size(56.dp)
                .clip(CircleShape)
                .background(Color(palette.background))
                .border(BorderStroke(if (selected) 4.dp else 1.dp, Color(palette.primary)), CircleShape)
                .semantics { contentDescription = "Paleta ${palette.displayName}" },
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", color = Color(palette.onBackground), fontFamily = OpenDyslexicFontFamily)
        }
        Text(palette.displayName, style = MaterialTheme.typography.labelSmall)
    }
}
