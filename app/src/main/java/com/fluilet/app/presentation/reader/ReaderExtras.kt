package com.fluilet.app.presentation.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fluilet.app.R
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.LevelPreview
import com.fluilet.app.domain.model.LexicalReplacement
import com.fluilet.app.domain.text.LevelComparison
import com.fluilet.app.domain.text.LexicalDiff
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.Locale
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.presentation.theme.OpenDyslexicFontFamily
import com.fluilet.app.presentation.theme.SafeLineHeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.key
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fluilet.app.presentation.common.CollapsibleCard
import com.fluilet.app.presentation.common.rememberTextWidths
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.fluilet.app.domain.text.VocabularyEntry
import com.fluilet.app.presentation.common.AdaptiveSingleLineText
import com.fluilet.app.presentation.common.AdaptiveText
import com.fluilet.app.presentation.common.ChipCarousel
import com.fluilet.app.domain.model.ReadingSettings
import androidx.compose.runtime.getValue


/** 🧠 "¿Sabías que...?" al final del texto. */
@Composable
internal fun FunFactCard(fact: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.funfact_title), style = MaterialTheme.typography.titleMedium)
            Text(fact, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Pestaña del comparador dentro de la tarjeta de lectura. */
enum class CompareTab { ADAPTED, ORIGINAL }

/**
 * 🔄 "Adaptado vs. Original" dentro de la tarjeta de lectura, sin títulos ni botones grandes: arriba solo una barra de
 * 34 dp ("🧠 Adaptado IA" | "📄 Original" | "Ver juntos") y debajo el texto completo de la pestaña elegida, con la misma
 * letra del lector (A-/A+) y los términos simplificados resaltados. El texto es el protagonista (8 a 10 renglones en un
 * teléfono común). "Ver juntos" abre ambos textos a pantalla completa con la ficha técnica. Todo vive solo en RAM.
 */
@Composable
internal fun ComparisonCard(state: ReaderUiState, onLevel: (Int) -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(CompareTab.ADAPTED) }
    var together by rememberSaveable { mutableStateOf(false) }
    val bodyStyle = readingTextStyle(state.settings, MaterialTheme.colorScheme.onBackground)
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.fillMaxSize()) {
            CompareBar(tab, onTab = { tab = it }, onTogether = { together = true }, Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp))
            Crossfade(tab, Modifier.weight(1f).fillMaxWidth(), label = "compareTab") { shown ->
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
                        when (shown) {
                            CompareTab.ADAPTED -> AdaptedComparisonText(state, bodyStyle)
                            CompareTab.ORIGINAL -> OriginalComparisonText(state, bodyStyle)
                        }
                        Spacer(Modifier.height(if (scroll.maxValue > 0) 44.dp else 8.dp))
                    }
                    ReadingScrollIndicator(scroll, cardColor, Modifier.matchParentSize())
                }
            }
        }
    }
    if (together) CompareTogetherDialog(state, onLevel, onClose = { together = false })
}

/** "🧠 Adaptado IA" | "📄 Original" lado a lado y el botón compacto "Ver juntos", en una sola línea de 34 dp (13 sp). */
@Composable
private fun CompareBar(tab: CompareTab, onTab: (CompareTab) -> Unit, onTogether: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(ReaderToolbar.CompareBarHeight)) {
        val shape = RoundedCornerShape(10.dp)
        val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
        val adaptedEmoji = stringResource(R.string.compare_tab_adapted)
        val originalEmoji = stringResource(R.string.compare_tab_original)
        val adaptedPlain = stringResource(R.string.compare_tab_adapted_plain)
        val originalPlain = stringResource(R.string.compare_tab_original_plain)
        val together = stringResource(R.string.compare_view_together)
        val widths = rememberTextWidths(listOf(adaptedEmoji, originalEmoji, adaptedPlain, originalPlain, together), labelStyle)
        val fit = ReaderToolbar.compareFit(maxWidth, widths[0] + widths[1], widths[2] + widths[3], widths[4])
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).selectableGroup()
            ) {
                // Cada pestaña recibe un ancho proporcional a su etiqueta: "Adaptado IA" necesita más que "Original".
                listOf(
                    Triple(CompareTab.ADAPTED, if (fit.emoji) adaptedEmoji else adaptedPlain, if (fit.emoji) widths[0] else widths[2]),
                    Triple(CompareTab.ORIGINAL, if (fit.emoji) originalEmoji else originalPlain, if (fit.emoji) widths[1] else widths[3])
                ).forEach { (option, label, labelWidth) ->
                    val selected = option == tab
                    Box(
                        Modifier.weight(labelWidth.value + 8f).fillMaxHeight()
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onTab(option) }),
                        contentAlignment = Alignment.Center
                    ) {
                        AdaptiveSingleLineText(
                            label,
                            Modifier.padding(horizontal = 4.dp),
                            style = labelStyle,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxHeight().clip(shape).background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(role = Role.Button, onClick = onTogether)
                    .semantics { contentDescription = together }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                if (fit.buttonText) {
                    Text(together, style = labelStyle, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Texto adaptado (del nivel comparado o el de la lectura) con los términos simplificados resaltados. */
@Composable
private fun AdaptedComparisonText(state: ReaderUiState, style: TextStyle) {
    val replaced = state.comparison?.replacements.orEmpty()
    val text = state.comparison?.adaptedText ?: state.currentText
    val good = SpanStyle(background = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), fontWeight = FontWeight.Bold)
    val annotated = remember(text, replaced, good) { highlighted(text, replaced.map { it.adapted }, good) }
    Text(annotated, style = style)
}

/** Texto original completo con los términos que el motor simplificó marcados (o por qué no está disponible). */
@Composable
private fun OriginalComparisonText(state: ReaderUiState, style: TextStyle) {
    val replaced = state.comparison?.replacements.orEmpty()
    val original = state.originalText
    when {
        !original.isNullOrBlank() -> {
            val mark = SpanStyle(background = MaterialTheme.colorScheme.errorContainer, textDecoration = TextDecoration.Underline)
            val annotated = remember(original, replaced, mark) { highlighted(original, replaced.map { it.original }, mark) }
            Text(annotated, style = style)
        }
        state.originalLoading -> Text(stringResource(R.string.compare_original_loading), style = MaterialTheme.typography.bodyMedium)
        else -> Text(stringResource(R.string.compare_original_unavailable), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * "Ver juntos": ventana a pantalla completa con el texto ADAPTADO arriba y el ORIGINAL completo abajo, cada uno en su
 * panel, para compararlos cómodamente. Debajo, plegados, los detalles de la adaptación (motor, nivel en vivo, banda,
 * Ficha Técnica y transformaciones léxicas). "✕ Cerrar" o atrás vuelve a la lectura.
 */
@Composable
private fun CompareTogetherDialog(state: ReaderUiState, onLevel: (Int) -> Unit, onClose: () -> Unit) {
    val adaptation = state.reading?.adaptation
    val comparison = state.comparison
    val bodyStyle = readingTextStyle(state.settings, MaterialTheme.colorScheme.onBackground)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AdaptiveText(stringResource(R.string.compare_heading), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                        AdaptiveSingleLineText(stringResource(R.string.compare_close), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.levelPreviews.isNotEmpty()) LevelSelector(state.levelPreviews, state.comparisonLevel, onLevel)
                    val level = comparison?.level ?: adaptation?.effectiveLevel ?: 0
                    TextPane(stringResource(R.string.compare_adapted_title, level), MaterialTheme.colorScheme.primary) {
                        // Al cambiar de nivel, el texto adaptado del nuevo nivel entra con un fundido.
                        key(level) {
                            val appear = remember { MutableTransitionState(false).apply { targetState = true } }
                            AnimatedVisibility(appear, enter = fadeIn(tween(380))) { AdaptedComparisonText(state, bodyStyle) }
                        }
                    }
                    TextPane(stringResource(R.string.compare_original_title), MaterialTheme.colorScheme.outline) {
                        OriginalComparisonText(state, bodyStyle)
                    }
                    if (comparison?.replacements.orEmpty().isNotEmpty()) {
                        Text(
                            stringResource(R.string.compare_highlight_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CollapsibleCard(
                        title = stringResource(R.string.compare_details_title),
                        subtitle = stringResource(R.string.compare_details_sub)
                    ) {
                        if (adaptation != null) ModelSeal(adaptation)
                        comparison?.let {
                            BandLine(it)
                            FichaTecnicaCard(it, shortLesson = adaptation?.shortLesson != null)
                            LexicalTransformations(it.replacements)
                        }
                        adaptation?.source?.let { src ->
                            Text(
                                stringResource(R.string.compare_source, if (src.isWikipedia) "Wikipedia" else "Banco Fluilet", src.title),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        adaptation?.shortLesson?.let { info ->
                            Text(
                                stringResource(R.string.compare_short_lesson, info.shownWords, info.fullWords),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Panel con borde y franja de color a la izquierda para un texto del comparador (original o adaptado). */
@Composable
private fun TextPane(title: String, accent: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
                content()
            }
        }
    }
}

/** Resalta las apariciones de [terms] (palabras o frases completas) sin cambiar el texto. */
private fun highlighted(text: String, terms: List<String>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    LexicalDiff.spans(text, terms).forEach { addStyle(style, it.first, it.last + 1) }
}

/** 🧠 Sello del motor que adaptó la lectura: modelo reentrenado (con sus cifras), motor local u otro motor. */
@Composable
private fun ModelSeal(adaptation: AdaptationResult) {
    val retrained = adaptation.isRetrainedModel
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (retrained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
        )
    ) {
        val onColor = if (retrained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
        // Sello sencillo para el lector y el tutor: qué adaptó el texto, sin versiones, métricas internas ni rutas.
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                retrained -> Text(stringResource(R.string.compare_model_active), style = MaterialTheme.typography.titleMedium, color = onColor)
                adaptation.isLocalEngine -> {
                    Text(stringResource(R.string.compare_model_local), style = MaterialTheme.typography.titleMedium, color = onColor)
                    Text(stringResource(R.string.compare_model_local_body), style = MaterialTheme.typography.bodySmall, color = onColor)
                }
                else -> Text(stringResource(R.string.compare_model_other), style = MaterialTheme.typography.titleSmall, color = onColor)
            }
        }
    }
}

/** Selector en vivo: `Nivel 1 (6-8)` | `Nivel 2` | `Nivel 3` | `Nivel 4` | `Nivel 5 (12+)`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelSelector(previews: List<LevelPreview>, selected: Int?, onLevel: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.compare_levels_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            previews.forEach { preview ->
                val label = when (preview.level) {
                    1 -> stringResource(R.string.compare_level_1)
                    5 -> stringResource(R.string.compare_level_5)
                    else -> stringResource(R.string.compare_level_n, preview.level)
                }
                SelectableChip(
                    selected = preview.level == selected,
                    onClick = { onLevel(preview.level) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.semantics {
                        contentDescription = "$label, Szigriszt-Pazos ${oneDecimal(preview.readabilityScore)}"
                    }
                )
            }
        }
        if (previews.size == 5) {
            val strict = previews.sortedBy { it.level }.zipWithNext().all { (a, b) -> a.readabilityScore > b.readabilityScore }
            Text(
                stringResource(if (strict) R.string.compare_progression_ok else R.string.compare_progression_partial),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BandLine(c: LevelComparison) {
    val band = c.band ?: return
    Text(
        stringResource(if (c.inBand) R.string.compare_band_in else R.string.compare_band_out, c.readabilityLevel, c.level, band.range),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** 🔬 Ficha Técnica del Motor NLP Szigriszt-Pazos del nivel elegido; sus cifras se animan al cambiar de nivel. */
@Composable
private fun FichaTecnicaCard(c: LevelComparison, shortLesson: Boolean = false) {
    val score by animateFloatAsState(c.adaptedScore.toFloat(), tween(650), label = "score")
    val gainValue by animateFloatAsState(c.gain.toFloat(), tween(650), label = "gain")
    val wps by animateFloatAsState(c.adaptedWordsPerSentence.toFloat(), tween(650), label = "wps")
    val spw by animateFloatAsState(c.adaptedSyllablesPerWord.toFloat(), tween(650), label = "spw")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.compare_ficha_title), style = MaterialTheme.typography.titleMedium)

            FichaRow(
                stringResource(R.string.compare_index_label),
                stringResource(R.string.compare_index_values, oneDecimal(c.originalScore), c.level, oneDecimal(score.toDouble()))
            )
            val gain = if (gainValue >= 0) stringResource(R.string.compare_gain_positive, oneDecimal(gainValue.toDouble()))
            else stringResource(R.string.compare_gain_negative, oneDecimal(gainValue.toDouble()))
            Text(
                gain,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            ReadabilityBandBar(c, score)
            FichaRow(
                stringResource(R.string.compare_wps_label),
                stringResource(R.string.compare_wps_values, oneDecimal(c.originalWordsPerSentence), oneDecimal(wps.toDouble()))
            )
            FichaRow(
                stringResource(R.string.compare_spw_label),
                stringResource(R.string.compare_spw_values, twoDecimals(c.originalSyllablesPerWord), twoDecimals(spw.toDouble()))
            )
            if (!c.fromServer) {
                Text(
                    stringResource(if (shortLesson) R.string.compare_measured_short else R.string.compare_measured_locally),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Barra visual de legibilidad (0–100): la banda del nivel resaltada, el original como referencia y el puntaje de la
 * variante, que se desliza con [score] animado.
 */
@Composable
private fun ReadabilityBandBar(c: LevelComparison, score: Float) {
    val band = c.band ?: return
    val track = MaterialTheme.colorScheme.outlineVariant
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
    val marker = MaterialTheme.colorScheme.primary
    val original = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.compare_band_bar, oneDecimal(c.adaptedScore), band.range, oneDecimal(c.originalScore))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(34.dp).semantics { contentDescription = description }) {
            val y = size.height / 2
            val h = size.height * 0.32f
            fun x(value: Double) = (value.coerceIn(0.0, 100.0) / 100.0 * size.width).toFloat()
            drawRoundRect(track, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(bandColor, Offset(x(band.min), y - h), Size(x(band.max) - x(band.min), h * 2), CornerRadius(h / 2))
            drawCircle(original, h * 0.55f, Offset(x(c.originalScore), y))
            val at = Offset(x(score.toDouble()), y)
            drawCircle(Color.White, h * 1.15f, at)
            drawCircle(marker, h * 0.85f, at)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.compare_band_bar_legend, band.range), style = MaterialTheme.typography.labelSmall)
            Text("100", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun FichaRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** ✨ Transformaciones léxicas `Antes ➔ Ahora` del nivel elegido (las frases muy largas solo se resaltan en el texto). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LexicalTransformations(replacements: List<LexicalReplacement>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.compare_lexical_title), style = MaterialTheme.typography.titleSmall)
        val shown = replacements.filter { it.original.split(' ').size <= MAX_CHIP_WORDS && it.adapted.split(' ').size <= MAX_CHIP_WORDS }
            .take(MAX_CHIPS)
        if (shown.isEmpty()) {
            Text(stringResource(R.string.compare_lexical_empty), style = MaterialTheme.typography.bodySmall)
            return
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { r ->
                Text(
                    "«${r.original}» ➔ «${r.adapted}»",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics { contentDescription = "Antes ${r.original}, ahora ${r.adapted}" }
                )
            }
        }
        val more = replacements.size - shown.size
        if (more > 0) Text(pluralStringResource(R.plurals.compare_lexical_more, more, more), style = MaterialTheme.typography.bodySmall)
    }
}

private fun oneDecimal(value: Double) = String.format(Locale.US, "%.1f", value)
private fun twoDecimals(value: Double) = String.format(Locale.US, "%.2f", value)

private const val MAX_CHIPS = 16
private const val MAX_CHIP_WORDS = 6

/**
 * "Palabras clave" en UNA sola fila que se desliza por páginas (ninguna palabra se corta en el borde y no
 * le roba altura al texto). Al tocar una palabra, su tarjeta de definición se despliega suavemente justo debajo, con
 * "Escuchar palabra"; al tocarla de nuevo (o "Cerrar") se pliega.
 */
@Composable
internal fun GlossaryBar(state: ReaderUiState, vm: ReaderViewModel, modifier: Modifier = Modifier) {
    if (state.vocabulary.isEmpty()) return
    val selected = state.selectedWord
    // La última palabra abierta se conserva mientras la tarjeta se pliega (sin parpadeo a vacío).
    val lastShown = remember { arrayOfNulls<VocabularyEntry>(1) }
    if (selected != null) lastShown[0] = selected
    // El texto de la definición nunca pasa de ~1/6 de la pantalla: el bloque de lectura conserva el protagonismo.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxDefinition = (windowHeight * 0.16f).coerceIn(DefinitionMinHeight, DefinitionMaxHeight)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Encabezado "🔍 Palabras clave" y ◀ ● ▶ en una sola línea compacta; chips de 38 dp.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 30.dp) {
            ChipCarousel(
                items = state.vocabulary,
                label = { it.word },
                isSelected = { it.word == selected?.word },
                onClick = { vm.selectWord(it.word) },
                title = stringResource(R.string.glossary_title),
                minHeight = 38.dp,
                compactHeader = true
            )
        }
        AnimatedVisibility(selected != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            lastShown[0]?.let { entry ->
                WordDefinitionCard(entry.word, entry.definition, entry.example, maxDefinition, onSpeak = { vm.speakWord(entry.word) }, onClose = vm::clearWord)
            }
        }
    }
}

/** Tarjeta pedagógica de una palabra clave: palabra, definición, ejemplo y "Escuchar palabra" (se desplaza si es larga). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordDefinitionCard(word: String, definition: String, example: String, maxHeight: Dp, onSpeak: () -> Unit, onClose: () -> Unit) {
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)) {
            // Escuchar y Cerrar siempre a la vista, junto a la palabra: nunca quedan escondidos bajo el texto.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AdaptiveText(word, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = content)
                FilledIconButton(onClick = onSpeak, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.glossary_listen))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.glossary_close), tint = content)
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(definition, style = MaterialTheme.typography.bodyMedium, color = content)
                if (example.isNotBlank()) {
                    Text(stringResource(R.string.glossary_example, example), style = MaterialTheme.typography.bodySmall, color = content)
                }
            }
        }
    }
}

/** Límites del texto de la definición: el bloque de lectura conserva el protagonismo. */
private val DefinitionMinHeight = 80.dp
private val DefinitionMaxHeight = 150.dp

/**
 * Estilo del texto de lectura: OpenDyslexic del tamaño elegido (14–28 pt) con el interlineado del perfil, que se
 * escala con la letra. Renglones con aire completo arriba y abajo y sin partir palabras.
 */
internal fun readingTextStyle(settings: ReadingSettings, color: Color): TextStyle {
    val size = settings.fontSizeSp.coerceIn(ReadingSettings.MIN_FONT_SIZE, ReadingSettings.MAX_FONT_SIZE)
    return TextStyle(
        fontFamily = OpenDyslexicFontFamily,
        fontSize = size.sp,
        lineHeight = (size * settings.lineSpacing).sp,
        letterSpacing = settings.letterSpacingEm.em,
        lineHeightStyle = SafeLineHeight,
        hyphens = Hyphens.None,
        lineBreak = LineBreak.Paragraph,
        color = color
    )
}
