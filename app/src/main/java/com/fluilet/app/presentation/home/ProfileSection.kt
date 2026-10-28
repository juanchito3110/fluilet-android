package com.fluilet.app.presentation.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.presentation.common.CheckMark
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.presentation.fluibot.FluiBotCanvas
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.FullScreenLoading
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.parentalgate.GateMode
import com.fluilet.app.presentation.parentalgate.ParentalGateDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.brand.FluiletIsotypeLogo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Encabezado oficial: isotipo + "Fluilet" + eslogan, el saludo y el acceso "✨ Ver Intro / Cuenta" para repetir
 * la animación del logo y la pantalla de Iniciar Sesión / Crear Cuenta en cualquier momento.
 */
@Composable
internal fun Header(state: HomeUiState, onLogout: () -> Unit, onShowIntro: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FluiletIsotypeLogo(Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(FluiletBrand.NAME, color = FluiletBrand.Purple, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 24.sp)
                Text(FluiletBrand.SLOGAN, color = FluiletBrand.SloganPurple, fontFamily = FontFamily.SansSerif, fontSize = 13.sp)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.home_logout))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.userName.isBlank()) stringResource(R.string.home_hello_plain)
                    else stringResource(R.string.home_hello, state.userName),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(if (state.isTutor) R.string.home_role_tutor else R.string.home_role_adult),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onShowIntro, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_show_intro), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun ChildrenSection(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (ChildProfile) -> Unit,
    onNlpLevel: (NlpLevel) -> Unit
) {
    SectionCard(stringResource(R.string.home_children_title)) {
        val child = state.selectedChild
        if (child == null) {
            Text(stringResource(R.string.home_children_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                stringResource(R.string.home_child_summary, child.difficulty.label, child.palette.displayName, child.fontSizeSp.toInt()),
                style = MaterialTheme.typography.bodyMedium
            )
            NlpLevelSlider(child.nlpLevel, onNlpLevel)
            // Apilados: con la tipografía OpenDyslexic y su espaciado, dos botones con icono no caben en una fila.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(stringResource(R.string.action_edit), { onEdit(child.id) }, icon = Icons.Filled.Edit)
                SecondaryButton(stringResource(R.string.action_delete), { onDelete(child) }, icon = Icons.Filled.Delete)
            }
        }
    }
}

@Composable
fun NlpLevelSlider(level: NlpLevel, onCommit: (NlpLevel) -> Unit) {
    var position by remember(level) { mutableStateOf(level.value.toFloat()) }
    val shown = NlpLevel.fromValue(position.toInt())
    Column {
        Text(stringResource(R.string.nlp_level_title, shown.label), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onCommit(NlpLevel.fromValue(Math.round(position))) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
        Text(shown.description, style = MaterialTheme.typography.bodySmall)
    }
}



/**
 * Selector de perfiles en la parte superior del panel del tutor: una tarjeta por niño con su nombre, edad, nivel
 * (1-5), etapa de FluiBot y gemas 💎, más la tarjeta para crear un niño nuevo. Al tocar una, los 4 apartados
 * (Lecturas, Mi FluiBot, Colección y Estadísticas) cambian a los datos de ese niño.
 */
@Composable
internal fun ChildCardsRow(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    currentEquipped: Set<String> = emptySet(),
    equippedFor: (String) -> Set<String> = { emptySet() }
) {
    if (!state.isTutor) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        state.children.forEach { child ->
            // Los accesorios del perfil seleccionado se actualizan en vivo al equiparlos en la tienda.
            val equipped = remember(child.id, currentEquipped) { equippedFor(child.id) }
            ChildCard(
                child = child,
                summary = state.childSummaries[child.id],
                selected = child.id == state.selectedChildId,
                equipped = equipped,
                onClick = { onSelect(child.id) }
            )
        }
        NewChildCard(onNew)
    }
}

@Composable
private fun ChildCard(child: ChildProfile, summary: ChildSummary?, selected: Boolean, equipped: Set<String>, onClick: () -> Unit) {
    val level = summary?.level ?: child.nlpLevel.value
    val description = buildString {
        append("${child.name}, ${child.ageYears()} años, nivel $level")
        summary?.let { append(", etapa ${it.stage}, ${it.gems} gemas") }
        append(if (selected) ", seleccionado" else "")
    }
    Card(
        onClick = onClick,
        modifier = Modifier.width(176.dp).semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // FluiBot del niño en su etapa y con sus accesorios; el del perfil seleccionado está animado.
            FluiBotCanvas(
                stage = FluiBotStage.entries.firstOrNull { it.number == summary?.stage } ?: FluiBotStage.NUCLEO,
                equipped = equipped,
                animated = selected,
                interactive = false,
                modifier = Modifier.fillMaxWidth().height(76.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(child.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                if (selected) CheckMark()
            }
            Text(
                stringResource(R.string.child_card_age, child.ageYears()) + (child.ageGroup?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
            Text(stringResource(R.string.child_card_level, level), style = MaterialTheme.typography.labelLarge)
            Text(
                summary?.let { stringResource(R.string.child_card_stage, it.stage) } ?: stringResource(R.string.child_card_loading),
                style = MaterialTheme.typography.bodySmall
            )
            Text(summary?.let { "💎 ${it.gems}" } ?: "💎 …", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NewChildCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(120.dp).height(IntrinsicSize.Max).semantics { contentDescription = "Crear un niño nuevo" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.home_new_child), style = MaterialTheme.typography.labelLarge)
        }
    }
}
