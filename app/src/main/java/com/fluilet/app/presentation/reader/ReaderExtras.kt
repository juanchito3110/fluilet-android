package com.fluilet.app.presentation.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.TextButton
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
import com.fluilet.app.domain.model.ModelAudit
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

/** Tope de caracteres del original que se dibujan en la comparación (el texto vive solo en RAM). */
private const val MAX_ORIGINAL_CHARS = 6_000

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

/**
 * 🔄 Super-comparador "Adaptado vs. Original", disponible en TODAS las lecturas (demo, por tema, texto pegado o
 * archivo): sello del modelo reentrenado, selector en vivo de los 5 niveles (`level_previews`), Ficha Técnica
 * Szigriszt-Pazos del nivel elegido, transformaciones léxicas y el original frente al adaptado con los términos
 * simplificados resaltados. Todo el texto vive solo en RAM.
 */
@Composable
internal fun ComparisonCard(state: ReaderUiState, onLevel: (Int) -> Unit, modifier: Modifier = Modifier) {
    val settings = state.settings
    val bodyStyle = TextStyle(
        fontFamily = OpenDyslexicFontFamily,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
        letterSpacing = settings.letterSpacingEm.em,
        color = MaterialTheme.colorScheme.onBackground
    )
    val adaptation = state.reading?.adaptation
    val comparison = state.comparison
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.compare_heading), style = MaterialTheme.typography.titleLarge)
            if (adaptation != null) ModelSeal(adaptation, state.modelAudit)

            if (state.levelPreviews.isNotEmpty()) {
                LevelSelector(state.levelPreviews, state.comparisonLevel, onLevel)
            }
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

            val replaced = comparison?.replacements.orEmpty()
            // Original y adaptado, cada uno en su panel delimitado para leerlos (y compararlos) sin confundirlos.
            TextPane(stringResource(R.string.compare_original_title), MaterialTheme.colorScheme.outline) {
                val original = state.originalText
                when {
                    !original.isNullOrBlank() -> {
                        val shown = if (original.length > MAX_ORIGINAL_CHARS) original.take(MAX_ORIGINAL_CHARS) + "…" else original
                        val mark = SpanStyle(background = MaterialTheme.colorScheme.errorContainer, textDecoration = TextDecoration.Underline)
                        val annotated = remember(shown, replaced, mark) { highlighted(shown, replaced.map { it.original }, mark) }
                        Text(annotated, style = bodyStyle)
                    }
                    state.originalLoading -> Text(stringResource(R.string.compare_original_loading), style = MaterialTheme.typography.bodyMedium)
                    else -> Text(stringResource(R.string.compare_original_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
            }

            val level = comparison?.level ?: adaptation?.effectiveLevel ?: 0
            TextPane(stringResource(R.string.compare_adapted_title, level), MaterialTheme.colorScheme.primary) {
                val adaptedText = comparison?.adaptedText ?: state.currentText
                val shownAdapted = if (adaptedText.length > MAX_ORIGINAL_CHARS) adaptedText.take(MAX_ORIGINAL_CHARS) + "…" else adaptedText
                val good = SpanStyle(background = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), fontWeight = FontWeight.Bold)
                val annotatedAdapted = remember(shownAdapted, replaced, good) { highlighted(shownAdapted, replaced.map { it.adapted }, good) }
                // Al cambiar de nivel, el texto adaptado entra con un fundido y un leve deslizamiento.
                AnimatedContent(
                    targetState = AdaptedPane(level, annotatedAdapted),
                    contentKey = { it.level },
                    transitionSpec = {
                        val down = targetState.level > initialState.level
                        (fadeIn(tween(380)) + slideInVertically(tween(380)) { if (down) it / 10 else -it / 10 })
                            .togetherWith(fadeOut(tween(180)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "adaptedText"
                ) { pane -> Text(pane.text, style = bodyStyle) }
            }
            if (replaced.isNotEmpty()) {
                Text(
                    stringResource(R.string.compare_highlight_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

/** El texto adaptado que muestra el comparador para un nivel. */
private data class AdaptedPane(val level: Int, val text: AnnotatedString)

/** Resalta las apariciones de [terms] (palabras o frases completas) sin cambiar el texto. */
private fun highlighted(text: String, terms: List<String>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    LexicalDiff.spans(text, terms).forEach { addStyle(style, it.first, it.last + 1) }
}

/** 🧠 Sello del motor que adaptó la lectura: modelo reentrenado (con sus cifras), motor local u otro motor. */
@Composable
private fun ModelSeal(adaptation: AdaptationResult, audit: ModelAudit?) {
    val retrained = adaptation.isRetrainedModel
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (retrained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
        )
    ) {
        val onColor = if (retrained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                retrained -> {
                    Text(stringResource(R.string.compare_model_active), style = MaterialTheme.typography.titleMedium, color = onColor)
                    val version = listOfNotNull(adaptation.modelVersion, adaptation.checkpointName).joinToString(" / ")
                    Text(version, style = MaterialTheme.typography.labelMedium, color = onColor)
                    audit?.takeIf { it.active }?.let {
                        Text(it.badgeLine, style = MaterialTheme.typography.labelLarge, color = onColor)
                        if (!it.fromServer) Text(stringResource(R.string.compare_model_offline_values), style = MaterialTheme.typography.bodySmall, color = onColor)
                    }
                }
                adaptation.isLocalEngine -> {
                    Text(stringResource(R.string.compare_model_local), style = MaterialTheme.typography.titleMedium, color = onColor)
                    Text(stringResource(R.string.compare_model_local_body), style = MaterialTheme.typography.bodySmall, color = onColor)
                }
                else -> Text(
                    stringResource(R.string.compare_model_other, adaptation.modelVersion ?: adaptation.engineVersion ?: "—"),
                    style = MaterialTheme.typography.titleSmall, color = onColor
                )
            }
        }
    }
}

/** Selector en vivo: `Nivel 1 (6-8)` | `Nivel 2` | `Nivel 3` | `Nivel 4` | `Nivel 5 (12+)`. */
@Composable
private fun LevelSelector(previews: List<LevelPreview>, selected: Int?, onLevel: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.compare_levels_title), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** 🔍 Glosario Mágico: palabras clave tocables; cada una muestra definición, ejemplo y "Escuchar palabra". */
@Composable
internal fun GlossaryBar(state: ReaderUiState, vm: ReaderViewModel, modifier: Modifier = Modifier) {
    if (state.vocabulary.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.glossary_title), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.vocabulary.forEach { entry ->
                SelectableChip(
                    selected = entry.word == state.selectedWord?.word,
                    onClick = { vm.selectWord(entry.word) },
                    label = { Text(entry.word, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.height(48.dp)
                )
            }
        }
        state.selectedWord?.let { WordCard(it.word, it.definition, it.example, onSpeak = { vm.speakWord(it.word) }, onClose = vm::clearWord) }
    }
}

@Composable
private fun WordCard(word: String, definition: String, example: String, onSpeak: () -> Unit, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(12.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(word, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text(stringResource(R.string.glossary_close)) }
            }
            Text(definition, style = MaterialTheme.typography.bodyMedium)
            if (example.isNotBlank()) Text(stringResource(R.string.glossary_example, example), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSpeak, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.glossary_listen), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
