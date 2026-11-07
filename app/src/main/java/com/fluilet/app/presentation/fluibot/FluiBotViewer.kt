package com.fluilet.app.presentation.fluibot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fluilet.app.R
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.Scenario
import com.fluilet.app.presentation.common.AdaptiveSingleLineText
import com.fluilet.app.presentation.common.AdaptiveText
import com.fluilet.app.presentation.common.ChipCarousel

/**
 * Visor de FluiBot a pantalla completa (se abre con "Ver en grande"): el robot y su escenario GIGANTES (300–360 dp) en
 * el centro, con una entrada suave. Aquí también se le da energía (tocándolo o con "⚡ Recargar Energía") y se ponen o
 * quitan los accesorios y escenarios que el niño ya tiene; "✕ Cerrar" (o atrás) vuelve al Avatar.
 */
@Composable
internal fun FluiBotViewer(
    state: FluiBotUiState,
    onCelebrate: () -> Unit,
    onToggleAccessory: ((Accessory) -> Unit)?,
    onEquipScenario: ((Scenario) -> Unit)?,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val scene = StageLayout.viewerSceneSize(maxWidth - 32.dp, windowHeight)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            AdaptiveText(stringResource(R.string.fluibot_name), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall)
                            AdaptiveText(
                                stringResource(R.string.fluibot_stage_label, state.bot.stage.number, state.bot.stage.displayName),
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                            AdaptiveSingleLineText(stringResource(R.string.fluibot_viewer_close), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    // Entrada suave: FluiBot crece con un pequeño rebote hasta su tamaño gigante.
                    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        appear,
                        enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), initialScale = 0.7f) + fadeIn()
                    ) {
                        FluiBotScene(state, onCelebrate, Modifier.size(scene))
                    }
                    AdaptiveText(
                        stringResource(R.string.fluibot_viewer_hint),
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GemPill(state.gems)
                        XpBar(state, Modifier.weight(1f))
                    }
                    RechargeButton(onCelebrate, Modifier.fillMaxWidth().heightIn(min = 52.dp))
                    Wardrobe(state, onToggleAccessory, onEquipScenario)
                }
            }
        }
    }
}

/** Accesorios y escenarios que el niño ya tiene, para ponerlos o quitarlos sin salir del visor. */
@Composable
private fun Wardrobe(state: FluiBotUiState, onToggleAccessory: ((Accessory) -> Unit)?, onEquipScenario: ((Scenario) -> Unit)?) {
    val accessories = state.accessories.filter { it.owned }
    val scenarios = state.scenarios.filter { it.owned }
    val showAccessories = onToggleAccessory != null && accessories.isNotEmpty()
    val showScenarios = onEquipScenario != null && scenarios.size > 1
    if (showAccessories && onToggleAccessory != null) {
        ChipCarousel(
            items = accessories,
            label = { it.accessory.displayName },
            isSelected = { it.equipped },
            onClick = { onToggleAccessory(it.accessory) },
            title = stringResource(R.string.fluibot_viewer_accessories),
            followSelection = false
        )
    }
    if (showScenarios && onEquipScenario != null) {
        ChipCarousel(
            items = scenarios,
            label = { it.scenario.displayName },
            isSelected = { it.equipped },
            onClick = { onEquipScenario(it.scenario) },
            title = stringResource(R.string.fluibot_viewer_scenarios)
        )
    }
    if (!showAccessories && !showScenarios) {
        AdaptiveText(
            stringResource(R.string.fluibot_viewer_empty),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
