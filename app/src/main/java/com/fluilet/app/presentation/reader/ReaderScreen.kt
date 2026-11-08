package com.fluilet.app.presentation.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.fluilet.app.presentation.common.rememberTextWidths
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.fluilet.app.domain.text.ReadingScroll
import kotlinx.coroutines.launch
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.fluilet.app.domain.quiz.ComprehensionQuiz
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.AdaptiveSingleLineText
import com.fluilet.app.presentation.common.AdaptiveText
import com.fluilet.app.presentation.common.SpringProgressBar
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.theme.SafeLineHeight
import com.fluilet.app.domain.gamification.SessionLoot
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.formatDuration
import com.fluilet.app.presentation.common.CelebrationBurst
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.presentation.fluibot.FluiBotCanvas
import com.fluilet.app.presentation.fluibot.FluiBotUiState
import com.fluilet.app.presentation.fluibot.FluiBotViewModel
import com.fluilet.app.presentation.parentalgate.GateMode
import com.fluilet.app.presentation.parentalgate.ParentalGateDialog
import com.fluilet.app.presentation.theme.FluiletTheme
import com.fluilet.app.presentation.theme.OpenDyslexicFontFamily
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.CircularProgressIndicator
import com.fluilet.app.presentation.common.FitText
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun ReaderScreen(onExit: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // POST_NOTIFICATIONS (API 33+): permite ver la notificación del servicio. La lectura inicia igual.
    // Primero solo se prepara (voz y bloques): el Modo Enfoque empieza tras la ventana "Preparar dictado por voz".
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.prepare()
    }
    LaunchedEffect(Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.prepare()
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
        Text(stringResource(R.string.reader_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.reader_empty_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.reader_back), onBack)
    }
}

@Composable
private fun ReaderContent(state: ReaderUiState, vm: ReaderViewModel, botViewModel: FluiBotViewModel = hiltViewModel()) {
    val bot by botViewModel.state.collectAsStateWithLifecycle()
    // FluiBot acompaña la lectura con los accesorios del niño. En el laboratorio lo enlaza RewardLab con el botín.
    LaunchedEffect(state.ownerKey, state.baseline, state.showLab) {
        if (!state.showLab) botViewModel.bind(state.ownerKey, state.baseline)
    }
    // Problemas de voz (volumen en 0, motor sin español o sin audio): ventanita emergente, nunca un aviso en la pantalla.
    val voiceHelp = rememberVoiceHelp(state, vm)
    VoiceHelpDialog(voiceHelp, state)
    // Antes de fijar la pantalla: comprobar que la voz en español esté descargada y se escuche.
    VoicePrepGate(voiceHelp, state)
    // Separaciones cortas: todo el alto que se ahorra es para la tarjeta de lectura.
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderTopBar(state, vm, bot, onBotTap = botViewModel::celebrate)
        val progress by animateFloatAsState(
            state.progressFraction,
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
            label = "readingProgress"
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                .semantics { contentDescription = "Progreso de lectura ${(state.progressFraction * 100).toInt()} por ciento" }
        )

        if (state.showQuiz) {
            // Mini-Reto de Comprensión: al terminar la lectura, antes del Laboratorio. Nunca abre el PIN.
            Box(Modifier.weight(1f)) {
                state.quiz?.let {
                    QuizCard(
                        quiz = it,
                        ageGroup = state.ageGroup ?: AgeGroup.TWELVE_PLUS,
                        ageYears = state.reading?.displayAge,
                        loot = state.loot,
                        onAnswer = vm::answerQuiz,
                        onNext = vm::nextQuestion,
                        onFinish = vm::finishQuiz
                    )
                }
                // Mini-reto superado: explosión de confeti y estrellas sobre la tarjeta.
                CelebrationBurst(trigger = if (state.quizSucceeded) 1 else 0, modifier = Modifier.matchParentSize(), originY = 0.3f)
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
            ReaderControls(state, vm, onPlayPause = { voiceHelp.playOrPause(state) })
        }
    }
}

/**
 * Barra superior del lector en su propia tarjeta: FluiBot en un halo (lee, piensa mientras carga, celebra), el bloque
 * actual y el tiempo en una píldora, y los dos botones a la derecha con aire suficiente para no encimarse.
 */
@Composable
private fun ReaderTopBar(state: ReaderUiState, vm: ReaderViewModel, bot: FluiBotUiState, onBotTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                FluiBotCanvas(
                    stage = FluiBotStage.forXp(state.baseline.currentXp),
                    equipped = bot.equipped,
                    celebrationTick = bot.celebrationTick,
                    onTap = onBotTap,
                    mood = state.botMood,
                    modifier = Modifier.size(48.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BlockLabel(state.chunkIndex + 1, state.chunkCount)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.lockActive) {
                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.reader_focus_on), modifier = Modifier.size(14.dp))
                    }
                    Text(formatDuration(state.elapsedSeconds), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            IconButton(onClick = vm::openSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.reader_settings))
            }
            IconButton(onClick = vm::requestExit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.reader_exit))
            }
        }
    }
}

/**
 * "Bloque 1 de 8" SIEMPRE completo en una sola línea; si la columna es muy angosta pasa a "Bloque 1/8", y en ambos casos
 * el tamaño se ajusta antes de cortarse.
 */
@Composable
private fun BlockLabel(current: Int, total: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val text = if (maxWidth < NARROW_BLOCK_LABEL) stringResource(R.string.reader_block_short, current, total)
        else stringResource(R.string.reader_block_of, current, total)
        AdaptiveSingleLineText(
            text,
            Modifier.fillMaxWidth().semantics { contentDescription = "Bloque $current de $total" },
            style = MaterialTheme.typography.titleMedium,
            minSize = 12.sp,
            textAlign = TextAlign.Start
        )
    }
}

/** Por debajo de este ancho, "Bloque 1 de 8" se abrevia a "Bloque 1/8". */
private val NARROW_BLOCK_LABEL = 128.dp

/** Reglas de la barra de herramientas y del comparador del lector (sin Android, probadas con pruebas unitarias). */
object ReaderToolbar {
    /** Alto fijo de la barra: UNA sola fila compacta que nunca baja a un segundo renglón. */
    val Height: Dp = 38.dp

    /** Alto de la barra "🧠 Adaptado IA | 📄 Original | Ver juntos" dentro de la tarjeta de lectura. */
    val CompareBarHeight: Dp = 34.dp

    /** Las etiquetas pueden encogerse de 13 a 12 sp antes de renunciar al emoji o a una flecha. */
    private const val SHRINK = 12f / 13f

    /** Cómo se acomoda la barra de herramientas según el ancho y la medida real de sus etiquetas (a 13 sp). */
    data class ToolbarFit(val emoji: Boolean, val arrows: Boolean)

    private fun toolbarWidth(labels: Dp, arrows: Boolean): Dp =
        labels * SHRINK + 24.dp + 80.dp + (if (arrows) 68.dp + 20.dp else 12.dp)

    /**
     * Prefiere conservar las flechas de la guía sobre el emoji; si ni así caben, sin flechas (tocar el texto avanza la
     * guía). Las palabras "Guía" y "Comparar" siempre se ven enteras.
     */
    fun toolbarFit(width: Dp, wantsArrows: Boolean, emojiLabels: Dp, plainLabels: Dp): ToolbarFit = when {
        wantsArrows && toolbarWidth(emojiLabels, arrows = true) <= width -> ToolbarFit(emoji = true, arrows = true)
        wantsArrows && toolbarWidth(plainLabels, arrows = true) <= width -> ToolbarFit(emoji = false, arrows = true)
        toolbarWidth(emojiLabels, arrows = false) <= width -> ToolbarFit(emoji = true, arrows = false)
        else -> ToolbarFit(emoji = false, arrows = false)
    }

    /** Cómo se acomoda la barra del comparador: pestañas con o sin emoji y "Ver juntos" con palabras o solo su ícono. */
    data class CompareFit(val emoji: Boolean, val buttonText: Boolean)

    private fun compareWidth(tabs: Dp, button: Dp?): Dp =
        tabs * SHRINK + 16.dp + 6.dp + (if (button != null) button + 36.dp else 32.dp)

    /** Primero se renuncia al emoji; "Ver juntos" solo pierde sus palabras si de verdad no caben. */
    fun compareFit(width: Dp, emojiTabs: Dp, plainTabs: Dp, button: Dp): CompareFit = when {
        compareWidth(emojiTabs, button) <= width -> CompareFit(emoji = true, buttonText = true)
        compareWidth(plainTabs, button) <= width -> CompareFit(emoji = false, buttonText = true)
        compareWidth(emojiTabs, null) <= width -> CompareFit(emoji = true, buttonText = false)
        else -> CompareFit(emoji = false, buttonText = false)
    }
}

/**
 * Herramientas de lectura en UNA sola fila de 38 dp: "A-" y "A+", "📏 Guía" (guía de renglón), "🔄 Comparar" (adaptado
 * vs. original) y, con la guía activa, sus flechas. Etiquetas cortas que se ajustan antes de cortarse: la tarjeta de
 * lectura conserva casi toda la pantalla.
 */
@Composable
private fun GuideBar(state: ReaderUiState, vm: ReaderViewModel) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(ReaderToolbar.Height)) {
        val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
        val guideEmoji = stringResource(R.string.reader_guide_short)
        val guidePlain = stringResource(R.string.reader_guide_plain)
        val compareEmoji = stringResource(R.string.reader_compare_short)
        val comparePlain = stringResource(R.string.reader_compare_plain)
        val widths = rememberTextWidths(listOf(guideEmoji, compareEmoji, guidePlain, comparePlain), labelStyle)
        val fit = ReaderToolbar.toolbarFit(
            width = maxWidth,
            wantsArrows = state.guideEnabled && !state.showOriginal,
            emojiLabels = widths[0] + widths[1],
            plainLabels = widths[2] + widths[3]
        )
        // Cada interruptor recibe un ancho proporcional a su etiqueta.
        val guideWeight = (if (fit.emoji) widths[0] else widths[2]).value + 12f
        val compareWeight = (if (fit.emoji) widths[1] else widths[3]).value + 12f
        val guideName = stringResource(R.string.reader_guide)
        val compareName = stringResource(R.string.reader_compare)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // A- / A+: cambian la letra en vivo (14–28 pt) y el interlineado se escala con ella.
            ToolbarButton(
                label = "A-",
                description = stringResource(R.string.reader_font_smaller),
                enabled = state.settings.fontSizeSp > ReadingSettings.MIN_FONT_SIZE,
                fontSize = 14.sp,
                onClick = vm::fontSmaller
            )
            ToolbarButton(
                label = "A+",
                description = stringResource(R.string.reader_font_larger),
                enabled = state.settings.fontSizeSp < ReadingSettings.MAX_FONT_SIZE,
                fontSize = 17.sp,
                onClick = vm::fontLarger
            )
            ToolbarToggle(
                label = if (fit.emoji) guideEmoji else guidePlain,
                selected = state.guideEnabled,
                description = "$guideName, ${if (state.guideEnabled) "activada" else "desactivada"}",
                onClick = vm::toggleGuide,
                modifier = Modifier.weight(guideWeight)
            )
            ToolbarToggle(
                label = if (fit.emoji) compareEmoji else comparePlain,
                selected = state.showOriginal,
                description = "$compareName, ${if (state.showOriginal) "activado" else "desactivado"}",
                onClick = vm::toggleOriginal,
                modifier = Modifier.weight(compareWeight)
            )
            if (fit.arrows) {
                ToolbarIcon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.reader_guide_previous), vm::guidePrevious)
                ToolbarIcon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.reader_guide_next), vm::guideNext)
            }
        }
    }
}

private val ToolbarShape = RoundedCornerShape(10.dp)

/** Botón "A-" / "A+" de la barra: 40 dp de ancho, todo el alto de la fila y muy claro. */
@Composable
private fun ToolbarButton(label: String, description: String, enabled: Boolean, fontSize: TextUnit, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.width(40.dp).fillMaxHeight().clip(ToolbarShape).border(1.5.dp, color, ToolbarShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize), color = color, maxLines = 1, softWrap = false)
    }
}

/** Interruptor de la barra ("📏 Guía", "🔄 Comparar"): lleno cuando está activo, con su estado para TalkBack. */
@Composable
private fun ToolbarToggle(label: String, selected: Boolean, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.fillMaxHeight().clip(ToolbarShape).background(container)
            .border(if (selected) 0.dp else 1.dp, MaterialTheme.colorScheme.outline, ToolbarShape)
            .toggleable(value = selected, role = Role.Switch, onValueChange = { onClick() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        AdaptiveSingleLineText(
            label,
            Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = content
        )
    }
}

/** Flecha compacta de la guía de renglón (↑ / ↓). */
@Composable
private fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.width(34.dp).fillMaxHeight().clip(ToolbarShape).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
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
    val annotated = remember(text, highlight, highlightColor, guide, textColor) {
        buildAnnotatedString {
            append(text)
            // Guía de renglón: se atenúa suavemente el resto del bloque y la oración activa conserva todo su contraste.
            if (guide != null && guide.last < text.length) {
                addStyle(SpanStyle(color = textColor.copy(alpha = 0.38f)), 0, text.length)
                addStyle(SpanStyle(color = textColor), guide.first, guide.last + 1)
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

    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
      // Sin alto fijo: el bloque completo vive en un área desplazable con barra visible y un aviso cuando hay más texto.
      Box(Modifier.fillMaxSize()) {
        Box(
            // Aire arriba y abajo dentro del área desplazable: el primer y el último renglón nunca tocan los bordes.
            Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)
                .clickable(enabled = state.guideEnabled, onClickLabel = "Siguiente renglón", onClick = onGuideTap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                androidx.compose.material3.Text(
                    text = annotated,
                    onTextLayout = { layout = it },
                    // Franja redondeada detrás de cada renglón de la oración activa (como una regla de lectura).
                    modifier = Modifier.drawBehind {
                        val l = layout
                        if (guide != null && l != null && guide.last < l.layoutInput.text.length) {
                            guideBands(l, guide).forEach { band ->
                                drawRoundRect(guideBand, topLeft = band.topLeft, size = band.size, cornerRadius = CornerRadius(12.dp.toPx()))
                            }
                        }
                    },
                    style = readingTextStyle(settings, MaterialTheme.colorScheme.onBackground)
                )
                // "¿Sabías que...?" cierra la lectura: solo al final del último bloque.
                val fact = state.funFact
                if (state.isLastChunk && fact != null) FunFactCard(fact)
                // Aire al final: el último renglón nunca queda pegado al borde de la tarjeta (ni tapado por el aviso).
                Spacer(Modifier.height(if (scroll.maxValue > 0) 44.dp else 12.dp))
            }
        }
        ReadingScrollIndicator(scroll, cardColor, Modifier.matchParentSize())
      }
    }
}

/**
 * Indicadores del bloque largo: una barra delgada a la derecha que muestra cuánto falta y, mientras quede texto abajo,
 * un degradado con el aviso "Desliza para seguir leyendo" (al tocarlo avanza con un desplazamiento suave).
 */
@Composable
internal fun ReadingScrollIndicator(scroll: ScrollState, cardColor: Color, modifier: Modifier = Modifier) {
    if (scroll.maxValue <= 0 || scroll.maxValue == Int.MAX_VALUE) return
    val scope = rememberCoroutineScope()
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Box(modifier) {
        Canvas(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 18.dp, horizontal = 6.dp).width(4.dp)
                .clearAndSetSemantics { }
        ) {
            val track = size.height
            val thumb = ReadingScroll.thumbLength(track, scroll.viewportSize.toFloat(), scroll.maxValue.toFloat())
            val top = ReadingScroll.thumbOffset(track, thumb, scroll.value.toFloat(), scroll.maxValue.toFloat())
            val radius = CornerRadius(size.width / 2)
            drawRoundRect(trackColor, cornerRadius = radius)
            drawRoundRect(thumbColor, topLeft = Offset(0f, top), size = Size(size.width, thumb), cornerRadius = radius)
        }
        AnimatedVisibility(
            visible = scroll.canScrollForward,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(cardColor.copy(alpha = 0f), cardColor)))
                    .padding(top = 18.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AdaptiveSingleLineText(
                    stringResource(R.string.reader_scroll_more),
                    Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                        .clickable { scope.launch { scroll.animateScrollBy(scroll.viewportSize * 0.75f) } }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun rememberScrollStateKeyed(key: Any) = androidx.compose.runtime.key(key) { rememberScrollState() }

@Composable
private fun ReaderControls(state: ReaderUiState, vm: ReaderViewModel, onPlayPause: () -> Unit) {
    val playing = state.tts.status == TtsStatus.Playing
    // "Reproducir" queda en cola mientras el motor inicia; si falta voz o volumen, abre la ventanita de ayuda.
    val preparing = state.tts.pendingPlay

    Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = vm::previous, enabled = state.chunkIndex > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_previous))
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors()
            ) {
                if (preparing) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        when {
                            playing -> R.string.reader_pause
                            state.tts.status == TtsStatus.Paused -> R.string.reader_resume
                            else -> R.string.reader_play
                        }
                    ),
                    modifier = Modifier.size(32.dp)
                )
            }
            FilledTonalIconButton(
                onClick = vm::stopSpeech,
                enabled = state.tts.status == TtsStatus.Playing || state.tts.status == TtsStatus.Paused,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.reader_stop))
            }
            FilledTonalIconButton(onClick = vm::next, enabled = !state.isLastChunk, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.reader_next))
            }
        }

        // Tres velocidades del mismo ancho, texto corto de 12 sp que se ajusta antes de cortarse.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedButton(R.string.reader_speed_slow, 0.8f, state, vm, Modifier.weight(1f))
            SpeedButton(R.string.reader_speed_normal, 1.0f, state, vm, Modifier.weight(1f))
            SpeedButton(R.string.reader_speed_fast, 1.2f, state, vm, Modifier.weight(1f))
        }

        if (state.isLastChunk) {
            PrimaryButton(stringResource(R.string.reader_finish), vm::finishReading, icon = Icons.Filled.Check)
        }
    }
}

@Composable
private fun SpeedButton(label: Int, rate: Float, state: ReaderUiState, vm: ReaderViewModel, modifier: Modifier = Modifier) {
    val selected = kotlin.math.abs(state.settings.speechRate - rate) < 0.03f
    val text: @Composable () -> Unit = {
        FitText(stringResource(label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), minSize = 9.sp)
    }
    val padding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    val shape = RoundedCornerShape(12.dp)
    if (selected) {
        Button(onClick = { vm.onSpeechRate(rate) }, modifier = modifier.heightIn(min = 40.dp), contentPadding = padding, shape = shape) { text() }
    } else {
        OutlinedButton(onClick = { vm.onSpeechRate(rate) }, modifier = modifier.heightIn(min = 40.dp), contentPadding = padding, shape = shape) { text() }
    }
}

/** Rectángulos de cada renglón que ocupa [range] (para dibujar la franja de la guía de renglón). */
private fun guideBands(layout: TextLayoutResult, range: IntRange): List<Rect> {
    val first = layout.getLineForOffset(range.first)
    val last = layout.getLineForOffset(range.last)
    return (first..last).map { line ->
        val start = maxOf(range.first, layout.getLineStart(line))
        val end = minOf(range.last + 1, layout.getLineEnd(line, visibleEnd = true))
        val left = layout.getHorizontalPosition(start, usePrimaryDirection = true)
        val right = if (end >= layout.getLineEnd(line, visibleEnd = true)) layout.getLineRight(line)
        else layout.getHorizontalPosition(end, usePrimaryDirection = true)
        Rect(minOf(left, right) - 8f, layout.getLineTop(line) + 2f, maxOf(left, right) + 8f, layout.getLineBottom(line) - 2f)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(settings: ReadingSettings, vm: ReaderViewModel) {
    ModalBottomSheet(onDismissRequest = vm::closeSettings, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge)
            // Toda la escala de 14 a 28 pt: en teléfonos compactos la letra se puede achicar.
            SettingSlider(
                R.string.reader_font_size,
                settings.fontSizeSp,
                ReadingSettings.MIN_FONT_SIZE..ReadingSettings.MAX_FONT_SIZE,
                "${settings.fontSizeSp.toInt()} pt",
                vm::onFontSize
            )
            SettingSlider(R.string.reader_line_spacing, settings.lineSpacing, ReadingSettings.MIN_LINE_SPACING..ReadingSettings.MAX_LINE_SPACING, "%.1fx".format(settings.lineSpacing), vm::onLineSpacing)
            SettingSlider(R.string.reader_letter_spacing, settings.letterSpacingEm, ReadingSettings.MIN_LETTER_SPACING..ReadingSettings.MAX_LETTER_SPACING, "%.2f".format(settings.letterSpacingEm), vm::onLetterSpacing)
            SettingSlider(R.string.reader_speech_rate, settings.speechRate, ReadingSettings.MIN_SPEECH_RATE..ReadingSettings.MAX_SPEECH_RATE, "%.2fx".format(settings.speechRate), vm::onSpeechRate)

            Text(stringResource(R.string.reader_palette), style = MaterialTheme.typography.titleMedium)
            // Las 6 paletas bajan de renglón: ninguna queda escondida en el borde derecho.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AdaptiveText(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(valueText, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(84.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp)
    ) {
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
        AdaptiveText(palette.displayName, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

// --- Mini-Reto de Comprensión ------------------------------------------------------------------

/** Verde de acierto y rojo suave de error con texto oscuro: legibles sobre cualquier paleta del lector. */
private val CorrectGreen = Color(0xFF1E7B3A)
private val CorrectContainer = Color(0xFFDDF4E4)
private val CorrectInk = Color(0xFF0E3B1C)
private val WrongRed = Color(0xFFB3261E)
private val WrongContainer = Color(0xFFFDE4E2)
private val WrongInk = Color(0xFF5C1410)
private val ExplanationContainer = Color(0xFFF1EDFC)
private val ExplanationInk = Color(0xFF2A2250)

/**
 * Cuestionario interactivo al terminar la lección: encabezado centrado con "Pregunta 1 de 3" y barra animada, la
 * pregunta destacada en su tarjeta, opciones a todo el ancho con insignia A/B/C/D, "Comprobar respuesta", la
 * explicación de la lectura (del servidor o local) y, al final, un resumen que celebra el puntaje, las gemas y el XP.
 * Estado elevado: la tarjeta dibuja el [ComprehensionQuiz] y avisa de cada acción (probable con pruebas).
 */
@Composable
internal fun QuizCard(
    quiz: ComprehensionQuiz,
    ageGroup: AgeGroup,
    ageYears: Int?,
    loot: SessionLoot,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    var showResults by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(if (ageGroup.isYoungReader) 32.dp else 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AnimatedContent(
            targetState = showResults && quiz.isComplete,
            transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.94f)).togetherWith(fadeOut(tween(160))) },
            label = "quizStage"
        ) { results ->
            if (results) QuizResults(quiz, loot, onFinish)
            else QuizQuestion(quiz, ageGroup, ageYears, onAnswer, onNext, onSeeResults = { showResults = true })
        }
    }
}

@Composable
private fun QuizQuestion(
    quiz: ComprehensionQuiz,
    ageGroup: AgeGroup,
    ageYears: Int?,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onSeeResults: () -> Unit
) {
    val question = quiz.current ?: return
    val answer = quiz.currentAnswer
    val young = ageGroup.isYoungReader
    var selected by remember(question.id) { mutableStateOf<Int?>(null) }
    var hintVisible by remember(question.id) { mutableStateOf(false) }
    // Cada pregunta empieza arriba; al comprobar, se desliza hasta la explicación.
    val scroll = androidx.compose.runtime.key(question.id) { rememberScrollState() }
    LaunchedEffect(question.id, answer) {
        if (answer != null) scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (young) 16.dp else 14.dp)
    ) {
        AdaptiveText(
            stringResource(R.string.quiz_title),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        AgeBadge(ageGroup, ageYears)
        QuizProgress(quiz)
        QuestionCard(question.question, young)

        // Pista pedagógica: no penaliza al niño ni cambia el puntaje.
        if (answer == null) {
            SecondaryButton(
                text = stringResource(if (hintVisible) R.string.quiz_hide_hint else R.string.quiz_show_hint),
                onClick = { hintVisible = !hintVisible }
            )
            if (hintVisible) HintCard(question.effectiveHint)
        }

        question.options.forEachIndexed { index, option ->
            AnswerOption(
                letter = QuizReview.letter(index),
                text = option,
                look = QuizReview.look(index, selected, answer, question.correctOptionIndex),
                young = young,
                enabled = answer == null,
                onClick = { selected = index }
            )
        }

        when (QuizReview.action(quiz)) {
            QuizAction.CHECK -> {
                if (selected == null) {
                    Text(
                        stringResource(R.string.quiz_pick_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PrimaryButton(stringResource(R.string.quiz_check), onClick = { selected?.let(onAnswer) }, enabled = selected != null, icon = Icons.Filled.Check)
            }
            QuizAction.NEXT -> {
                ExplanationCard(answer?.isCorrect == true, question.explanationFor(answer?.isCorrect == true))
                PrimaryButton(stringResource(R.string.quiz_next), onNext, icon = Icons.AutoMirrored.Filled.ArrowForward)
            }
            QuizAction.RESULTS -> {
                ExplanationCard(answer?.isCorrect == true, question.explanationFor(answer?.isCorrect == true))
                PrimaryButton(stringResource(R.string.quiz_see_results), onSeeResults, icon = Icons.Filled.EmojiEvents)
            }
            QuizAction.NONE -> Unit
        }
    }
}

/** Chip centrado "Pregunta 1 de 3" y la barra de avance que se llena con un resorte. */
@Composable
private fun QuizProgress(quiz: ComprehensionQuiz) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveSingleLineText(
            stringResource(R.string.quiz_question_of, quiz.viewIndex + 1, quiz.questions.size),
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
        SpringProgressBar(
            QuizReview.progress(quiz),
            height = 10.dp,
            modifier = Modifier.semantics { contentDescription = "${quiz.answered} de ${quiz.questions.size} preguntas respondidas" }
        )
    }
}

/** La pregunta destacada: texto centrado, limpio y cómodo (17–18 sp). */
@Composable
private fun QuestionCard(text: String, young: Boolean) {
    val size = if (young) 18f else 17f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = OpenDyslexicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = size.sp,
                lineHeight = (size * 1.5f).sp,
                lineHeightStyle = SafeLineHeight,
                hyphens = Hyphens.None,
                lineBreak = LineBreak.Heading,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@Composable
private fun AgeBadge(group: AgeGroup, ageYears: Int?) {
    val label = if (ageYears != null) stringResource(R.string.quiz_age_badge, ageYears, group.code)
    else stringResource(R.string.quiz_age_badge_group, group.code, group.code)
    AdaptiveText(
        label,
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        textAlign = TextAlign.Center
    )
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

/**
 * Opción de respuesta a todo el ancho: insignia circular (A, B, C, D) y el texto completo, sin recortes. Tras
 * comprobar, la correcta se pinta de verde con palomita y la elegida por error de rojo suave con una X.
 */
@Composable
private fun AnswerOption(letter: String, text: String, look: OptionLook, young: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = when (look) {
        OptionLook.IDLE, OptionLook.DIMMED -> scheme.background
        OptionLook.SELECTED -> scheme.primaryContainer
        OptionLook.CORRECT -> CorrectContainer
        OptionLook.WRONG -> WrongContainer
    }
    val content = when (look) {
        OptionLook.IDLE -> scheme.onBackground
        OptionLook.DIMMED -> scheme.onBackground.copy(alpha = 0.55f)
        OptionLook.SELECTED -> scheme.onPrimaryContainer
        OptionLook.CORRECT -> CorrectInk
        OptionLook.WRONG -> WrongInk
    }
    val edge = when (look) {
        OptionLook.IDLE -> scheme.outline
        OptionLook.DIMMED -> scheme.outlineVariant
        OptionLook.SELECTED -> scheme.primary
        OptionLook.CORRECT -> CorrectGreen
        OptionLook.WRONG -> WrongRed
    }
    val badge = when (look) {
        OptionLook.CORRECT -> CorrectGreen
        OptionLook.WRONG -> WrongRed
        OptionLook.SELECTED -> scheme.primary
        else -> scheme.primary.copy(alpha = 0.14f)
    }
    val animatedContainer by animateColorAsState(container, tween(250), label = "optionContainer")
    val stateText = when (look) {
        OptionLook.CORRECT -> stringResource(R.string.quiz_option_correct)
        OptionLook.WRONG -> stringResource(R.string.quiz_option_wrong)
        else -> ""
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (young) 72.dp else 60.dp)
            .semantics {
                contentDescription = "Opción $letter: $text. $stateText"
                selected = look == OptionLook.SELECTED
            },
        shape = RoundedCornerShape(if (young) 24.dp else 18.dp),
        color = animatedContainer,
        contentColor = content,
        border = BorderStroke(if (look == OptionLook.IDLE || look == OptionLook.DIMMED) 1.5.dp else 3.dp, edge)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(if (young) 40.dp else 36.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
                when (look) {
                    OptionLook.CORRECT -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    OptionLook.WRONG -> Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                    OptionLook.SELECTED -> Text(letter, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary)
                    else -> Text(letter, style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                }
            }
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = if (young) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = content
            )
        }
    }
}

/** "📖 Explicación de la lectura": por qué la respuesta es la correcta, según el texto. */
@Composable
private fun ExplanationCard(correct: Boolean, explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ExplanationContainer, contentColor = ExplanationInk),
        border = BorderStroke(2.dp, if (correct) CorrectGreen else FluiletBrand.Purple)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (correct) Icons.Filled.CheckCircle else Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = if (correct) CorrectGreen else FluiletBrand.Purple
                )
                AdaptiveText(
                    stringResource(if (correct) R.string.quiz_feedback_correct_title else R.string.quiz_feedback_wrong_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = ExplanationInk
                )
            }
            Text(stringResource(R.string.quiz_explanation_title), style = MaterialTheme.typography.labelLarge, color = FluiletBrand.Purple)
            Text(explanation, style = MaterialTheme.typography.bodyLarge, color = ExplanationInk)
        }
    }
}

/** Resumen final: celebra el puntaje (p. ej. "3/3 respuestas correctas"), las gemas y el XP ganados. */
@Composable
private fun QuizResults(quiz: ComprehensionQuiz, loot: SessionLoot, onFinish: () -> Unit) {
    val result = quiz.result
    val total = quiz.questions.size
    val verdict = QuizReview.verdict(result.correct, total)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            when (verdict) {
                QuizVerdict.PERFECT -> "🏆"
                QuizVerdict.GOOD -> "🌟"
                QuizVerdict.TRY_AGAIN -> "💪"
            },
            style = MaterialTheme.typography.displayMedium
        )
        AdaptiveText(
            stringResource(R.string.quiz_results_title),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        AdaptiveSingleLineText(
            stringResource(R.string.quiz_results_score, result.correct, total),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                Icon(
                    if (index < result.correct) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (index < result.correct) Color(0xFFF5B301) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            stringResource(R.string.quiz_results_percent, result.score ?: 0),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(
                when (verdict) {
                    QuizVerdict.PERFECT -> R.string.quiz_results_perfect
                    QuizVerdict.GOOD -> R.string.quiz_results_good
                    QuizVerdict.TRY_AGAIN -> R.string.quiz_results_try
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RewardTile("💎", stringResource(R.string.quiz_results_gems, loot.gems), Modifier.weight(1f))
            RewardTile("⭐", stringResource(R.string.quiz_results_xp, loot.xp), Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.quiz_results_rewards_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        PrimaryButton(stringResource(R.string.quiz_finish), onFinish, icon = Icons.AutoMirrored.Filled.ArrowForward)
    }
}

@Composable
private fun RewardTile(emoji: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            AdaptiveSingleLineText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
