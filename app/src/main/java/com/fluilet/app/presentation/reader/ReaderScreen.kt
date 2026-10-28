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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.presentation.common.CheckMark
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.effectiveHint
import com.fluilet.app.domain.model.explanationFor
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.domain.quiz.ComprehensionQuiz
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

        if (state.showQuiz) {
            // Mini-Reto de Comprensión: al terminar la lectura, antes del Laboratorio. Nunca abre el PIN.
            Box(Modifier.weight(1f)) {
                state.quiz?.let {
                    QuizCard(
                        quiz = it,
                        settings = state.settings,
                        ageGroup = state.ageGroup ?: AgeGroup.TWELVE_PLUS,
                        ageYears = state.reading?.displayAge,
                        onAnswer = vm::answerQuiz,
                        onNext = vm::nextQuestion,
                        onFinish = vm::finishQuiz
                    )
                }
            }
        } else if (state.showLab) {
            // Laboratorio y Rincón de Recompensas, dentro del Modo Enfoque: solo el tutor (PIN) puede salir.
            Box(Modifier.weight(1f)) {
                RewardLab(state, onClaimChest = vm::claimChest, onReadAgain = vm::restart, onDeliver = vm::requestExit, onLabTab = vm::selectLabTab)
            }
        } else {
            GuideBar(state, vm)
            Box(Modifier.weight(1f)) {
                if (state.showOriginal) ComparisonCard(state, onLevel = vm::selectComparisonLevel) else ChunkCard(state, onGuideTap = vm::guideNext)
            }
            GlossaryBar(state, vm)
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

/** Chip "Guía de renglón" (con flechas para mover la franja cuando está activa). */
@Composable
private fun GuideBar(state: ReaderUiState, vm: ReaderViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectableChip(
            selected = state.guideEnabled,
            onClick = vm::toggleGuide,
            label = { Text(stringResource(R.string.reader_guide), style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.semantics { contentDescription = "Guía de renglón, ${if (state.guideEnabled) "activada" else "desactivada"}" }
        )
        SelectableChip(
            selected = state.showOriginal,
            onClick = vm::toggleOriginal,
            label = { Text(stringResource(R.string.reader_compare), style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.semantics {
                contentDescription = "Adaptado contra original, ${if (state.showOriginal) "activado" else "desactivado"}"
            }
        )
        if (state.guideEnabled && !state.showOriginal) {
            IconButton(onClick = vm::guidePrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.reader_guide_previous))
            }
            IconButton(onClick = vm::guideNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.reader_guide_next))
            }
        }
    }
}

@Composable
private fun ChunkCard(state: ReaderUiState, onGuideTap: () -> Unit) {
    val settings = state.settings
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val text = state.currentText
    val highlight = state.tts.highlight
    val scroll = rememberScrollStateKeyed(state.chunkIndex)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // El resaltado va en el AnnotatedString (solo fondo: sin cambiar el grosor para no reflujar el texto).
    val guide = state.guideRange
    val textColor = MaterialTheme.colorScheme.onBackground
    val guideBand = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val annotated = remember(text, highlight, highlightColor, guide, textColor, guideBand) {
        buildAnnotatedString {
            append(text)
            // Guía de renglón: se atenúa todo el bloque y la oración activa recupera el contraste y una franja suave.
            if (guide != null && guide.last < text.length) {
                addStyle(SpanStyle(color = textColor.copy(alpha = 0.42f)), 0, text.length)
                addStyle(SpanStyle(color = textColor, background = guideBand), guide.first, guide.last + 1)
            }
            if (highlight != null && highlight.first in text.indices) {
                addStyle(SpanStyle(background = highlightColor), highlight.first, (highlight.last + 1).coerceAtMost(text.length))
            }
        }
    }

    // Mantiene visible la palabra que se está leyendo en bloques largos.
    LaunchedEffect(guide) {
        val l = layout
        if (guide != null && l != null && guide.first < l.layoutInput.text.length) {
            scroll.animateScrollTo((l.getBoundingBox(guide.first).top - 160f).toInt().coerceAtLeast(0))
        }
    }
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
        Box(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp)
                .clickable(enabled = state.guideEnabled, onClickLabel = "Siguiente renglón", onClick = onGuideTap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                // "¿Sabías que...?" cierra la lectura: solo al final del último bloque.
                val fact = state.funFact
                if (state.isLastChunk && fact != null) FunFactCard(fact)
            }
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
            SpeedChip(R.string.reader_speed_slow, 0.8f, state, vm)
            SpeedChip(R.string.reader_speed_normal, 1.0f, state, vm)
            SpeedChip(R.string.reader_speed_fast, 1.2f, state, vm)
        }

        if (state.isLastChunk) {
            PrimaryButton(stringResource(R.string.reader_finish), vm::finishReading, icon = Icons.Filled.Check)
        }
    }
}

@Composable
private fun SpeedChip(label: Int, rate: Float, state: ReaderUiState, vm: ReaderViewModel) {
    val selected = kotlin.math.abs(state.settings.speechRate - rate) < 0.03f
    SelectableChip(
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

// --- Mini-Reto de Comprensión ------------------------------------------------------------------

/**
 * Estado elevado: la tarjeta dibuja el [ComprehensionQuiz] y avisa de cada acción (probable con pruebas).
 * Adaptado por edad: insignia "Adaptado para X años", pista sin penalización y explicación positiva; los
 * lectores de 6 a 8 años ven botones más grandes, bordes más redondeados y tipografía más amplia.
 */
@Composable
internal fun QuizCard(
    quiz: ComprehensionQuiz,
    settings: ReadingSettings,
    ageGroup: AgeGroup,
    ageYears: Int?,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val question = quiz.current ?: return
    val answer = quiz.currentAnswer
    val young = ageGroup.isYoungReader
    val bump = if (young) 4f else 0f
    var hintVisible by remember(question.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(if (young) 32.dp else 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(if (young) 18.dp else 14.dp)
        ) {
            Text(stringResource(R.string.quiz_title), style = MaterialTheme.typography.headlineSmall)
            AgeBadge(ageGroup, ageYears)
            Text(
                stringResource(R.string.quiz_question_of, quiz.viewIndex + 1, quiz.questions.size),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                question.question,
                style = TextStyle(
                    fontFamily = OpenDyslexicFontFamily,
                    fontSize = (settings.fontSizeSp + bump).sp,
                    lineHeight = ((settings.fontSizeSp + bump) * settings.lineSpacing).sp,
                    letterSpacing = (settings.letterSpacingEm + if (young) 0.02f else 0f).em,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            // Pista pedagógica: no penaliza al niño ni cambia el puntaje.
            if (answer == null) {
                SecondaryButton(
                    text = stringResource(if (hintVisible) R.string.quiz_hide_hint else R.string.quiz_show_hint),
                    onClick = { hintVisible = !hintVisible }
                )
                if (hintVisible) HintCard(question.effectiveHint)
            }

            question.options.forEachIndexed { index, option ->
                QuizOption(
                    text = option,
                    answered = answer != null,
                    isCorrectOption = index == question.correctOptionIndex,
                    isSelected = answer?.selectedIndex == index,
                    young = young,
                    onClick = { onAnswer(index) }
                )
            }

            if (answer != null) {
                FeedbackCard(
                    correct = answer.isCorrect,
                    explanation = question.explanationFor(answer.isCorrect)
                )
                if (quiz.canAdvance) {
                    PrimaryButton(stringResource(R.string.quiz_next), onNext)
                } else if (quiz.isComplete) {
                    val r = quiz.result
                    Text(stringResource(R.string.quiz_result_summary, r.correct, r.answered, r.score ?: 0), style = MaterialTheme.typography.titleMedium)
                    PrimaryButton(stringResource(R.string.quiz_finish), onFinish)
                }
            }
        }
    }
}

@Composable
private fun AgeBadge(group: AgeGroup, ageYears: Int?) {
    val label = if (ageYears != null) stringResource(R.string.quiz_age_badge, ageYears, group.code)
    else stringResource(R.string.quiz_age_badge_group, group.code, group.code)
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { contentDescription = label }
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun HintCard(hint: String) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text("💡 $hint", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Retroalimentación con la explicación positiva; el estado se comunica con icono y texto, no solo con color. */
@Composable
private fun FeedbackCard(correct: Boolean, explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (correct) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (correct) R.string.quiz_feedback_correct_title else R.string.quiz_feedback_wrong_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(explanation, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun QuizOption(text: String, answered: Boolean, isCorrectOption: Boolean, isSelected: Boolean, young: Boolean, onClick: () -> Unit) {
    val showCorrect = answered && isCorrectOption
    val showWrong = answered && isSelected && !isCorrectOption
    val container = when {
        showCorrect -> MaterialTheme.colorScheme.primary
        showWrong -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.background
    }
    val content = when {
        showCorrect -> MaterialTheme.colorScheme.onPrimary
        showWrong -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onBackground
    }
    val stateText = when {
        showCorrect -> stringResource(R.string.quiz_option_correct)
        showWrong -> stringResource(R.string.quiz_option_wrong)
        else -> ""
    }
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = !answered,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (young) 80.dp else 56.dp).semantics { contentDescription = "$text. $stateText" },
        shape = RoundedCornerShape(if (young) 32.dp else 16.dp),
        border = BorderStroke(if (young) 3.dp else 2.dp, if (showCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container, disabledContentColor = content
        )
    ) {
        if (showCorrect) Icon(Icons.Filled.Check, contentDescription = null)
        if (showWrong) Icon(Icons.Filled.Close, contentDescription = null)
        if (showCorrect || showWrong) Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = if (young) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
