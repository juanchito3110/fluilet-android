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
import com.fluilet.app.presentation.common.AdaptiveSingleLineText
import com.fluilet.app.presentation.common.AdaptiveText
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
import androidx.compose.ui.text.style.TextOverflow
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.brand.FluiletIsotypeLogo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextAlign

/**
 * Encabezado institucional: arriba la marca (isotipo, "Fluilet" y eslogan) con dos botones compactos (introducción y
 * cuenta, cerrar sesión); debajo, en su propia fila y a todo el ancho, el saludo y el rol. Así el saludo nunca se
 * aplasta letra por letra junto a un botón.
 */
@Composable
internal fun Header(state: HomeUiState, onLogout: () -> Unit, onShowIntro: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FluiletIsotypeLogo(Modifier.size(42.dp))
            Column(Modifier.weight(1f)) {
                Text(FluiletBrand.NAME, color = FluiletBrand.Purple, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 24.sp, maxLines = 1)
                AdaptiveSingleLineText(
                    FluiletBrand.SLOGAN,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Normal),
                    color = FluiletBrand.SloganPurple,
                    textAlign = TextAlign.Start
                )
            }
            FilledTonalIconButton(onClick = onShowIntro, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.home_intro_action))
            }
            FilledTonalIconButton(onClick = onLogout, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.home_logout))
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AdaptiveText(
                if (state.userName.isBlank()) stringResource(R.string.home_hello_plain) else stringResource(R.string.home_hello, state.userName),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(if (state.isTutor) R.string.home_greeting_sub_tutor else R.string.home_greeting_sub_adult),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            // El cambio espera "Confirmar" (diálogo en HomeScreen); mientras tanto el deslizador muestra el nivel propuesto.
            NlpLevelSlider(child.nlpLevel, onNlpLevel, pending = state.pendingNlpLevel)
            // Apilados: con la tipografía OpenDyslexic y su espaciado, dos botones con icono no caben en una fila.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(stringResource(R.string.action_edit), { onEdit(child.id) }, icon = Icons.Filled.Edit)
                SecondaryButton(stringResource(R.string.action_delete), { onDelete(child) }, icon = Icons.Filled.Delete)
            }
        }
    }
}

/**
 * Deslizador de nivel 1-5. Mientras se arrastra sigue al dedo; al soltar avisa [onCommit] y se acomoda con un resorte
 * en [pending] (un nivel que aún espera confirmación) o en [level]. Si se cancela una confirmación, regresa solo.
 */
@Composable
fun NlpLevelSlider(level: NlpLevel, onCommit: (NlpLevel) -> Unit, pending: NlpLevel? = null) {
    val target = (pending ?: level).value.toFloat()
    val settled = remember { Animatable(target) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(target) {
        settled.animateTo(target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val position = dragging ?: settled.value
    val shown = NlpLevel.fromValue(Math.round(position))
    Column {
        AdaptiveText(stringResource(R.string.nlp_level_title, shown.label), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = position,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                val chosen = NlpLevel.fromValue(Math.round(dragging ?: settled.value))
                scope.launch { settled.snapTo(chosen.value.toFloat()) }
                dragging = null
                onCommit(chosen)
            },
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
    Row(Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

/**
 * Tarjeta de un lector: FluiBot del niño enmarcado (animado si está seleccionado), nombre completo (hasta 2 renglones,
 * nunca cortado), edad y grupo, e insignias de nivel, etapa y gemas que se ajustan antes de cortarse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildCard(child: ChildProfile, summary: ChildSummary?, selected: Boolean, equipped: Set<String>, onClick: () -> Unit) {
    val level = summary?.level ?: child.nlpLevel.value
    val description = buildString {
        append("${child.name}, ${child.ageYears()} años, nivel $level")
        summary?.let { append(", etapa ${it.stage}, ${it.gems} gemas") }
        append(if (selected) ", seleccionado" else "")
    }
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.width(248.dp).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) scheme.primaryContainer else scheme.surfaceVariant),
        border = BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(FluiletBrand.Lavender, FluiletBrand.Purple.copy(alpha = 0.35f))))
                        .border(2.dp, if (selected) scheme.primary else Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // FluiBot del niño en su etapa y con sus accesorios; el del perfil seleccionado está animado.
                    FluiBotCanvas(
                        stage = FluiBotStage.entries.firstOrNull { it.number == summary?.stage } ?: FluiBotStage.NUCLEO,
                        equipped = equipped,
                        animated = selected,
                        interactive = false,
                        modifier = Modifier.size(62.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AdaptiveText(child.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        child.ageGroup?.let { stringResource(R.string.child_card_age_group, child.ageYears(), it) }
                            ?: stringResource(R.string.child_card_age, child.ageYears()),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (selected) CheckMark()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(stringResource(R.string.child_card_level, level), scheme.primary, scheme.onPrimary)
                Pill(
                    summary?.let { stringResource(R.string.child_card_stage_short, it.stage) } ?: stringResource(R.string.child_card_loading),
                    scheme.background, scheme.onBackground
                )
                Pill("💎 " + (summary?.gems?.toString() ?: "…"), FluiletBrand.Mint.copy(alpha = 0.22f), scheme.onBackground)
            }
        }
    }
}

@Composable
private fun NewChildCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(132.dp).height(IntrinsicSize.Max).semantics { contentDescription = "Crear un niño nuevo" },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            AdaptiveText(stringResource(R.string.home_new_child), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
