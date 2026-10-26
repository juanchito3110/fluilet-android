package com.fluilet.app.presentation.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fluilet.app.R
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

/** 🔄 Vista "Adaptado vs. Original": cambio de legibilidad Szigriszt-Pazos y, si existe, el texto original. */
@Composable
internal fun ComparisonCard(state: ReaderUiState, modifier: Modifier = Modifier) {
    val metrics = state.reading?.adaptation?.metrics
    val settings = state.settings
    val bodyStyle = TextStyle(
        fontFamily = OpenDyslexicFontFamily,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
        letterSpacing = settings.letterSpacingEm.em,
        color = MaterialTheme.colorScheme.onBackground
    )
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.titleLarge)
            if (metrics != null) {
                Text(
                    stringResource(R.string.compare_score, metrics.originalSzigrisztScore, metrics.adaptedSzigrisztScore),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "Legibilidad Szigriszt-Pazos: de ${metrics.originalSzigrisztScore.toInt()} a ${metrics.adaptedSzigrisztScore.toInt()}"
                    }
                )
                Text(
                    stringResource(R.string.compare_delta, metrics.scoreDelta),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.compare_words, metrics.originalWordCount, metrics.adaptedWordCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(stringResource(R.string.compare_original_label), style = MaterialTheme.typography.titleMedium)
            val original = state.originalText
            if (original.isNullOrBlank()) {
                Text(stringResource(R.string.compare_original_unavailable), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    if (original.length > MAX_ORIGINAL_CHARS) original.take(MAX_ORIGINAL_CHARS) + "…" else original,
                    style = bodyStyle
                )
            }
            Text(stringResource(R.string.compare_adapted_label), style = MaterialTheme.typography.titleMedium)
            Text(state.currentText, style = bodyStyle)
        }
    }
}

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
