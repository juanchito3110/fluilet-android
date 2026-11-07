package com.fluilet.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import com.fluilet.app.presentation.common.IconLabelTab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.Scenario
import com.fluilet.app.presentation.fluibot.AccessoriesGrid
import com.fluilet.app.presentation.fluibot.EvolutionSection
import com.fluilet.app.presentation.fluibot.FluiBotStageCard
import com.fluilet.app.presentation.fluibot.FluiBotUiState
import com.fluilet.app.presentation.fluibot.ScenariosSection

/** Sub-pestañas de 🤖 Mi FluiBot. */
internal enum class BotSubTab { ACCESORIOS, ESCENARIOS, EVOLUCION }

/** 🤖 Mi FluiBot: escenario amplio con el robot y, debajo, Accesorios · Escenarios · Evolución. */
@Composable
internal fun FluiBotTab(
    bot: FluiBotUiState,
    onCelebrate: () -> Unit,
    onBuyAccessory: (Accessory) -> Unit,
    onToggleAccessory: (Accessory) -> Unit,
    onBuyScenario: (Scenario) -> Unit,
    onEquipScenario: (Scenario) -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FluiBotStageCard(bot, onCelebrate)
        // Emoji arriba y palabra completa debajo: "Accesorios", "Escenarios" y "Evolución" nunca se cortan.
        TabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.background) {
            listOf(
                "🛍️" to stringResource(R.string.bot_tab_accessories),
                "🌌" to stringResource(R.string.bot_tab_scenarios),
                "🚀" to stringResource(R.string.bot_tab_evolution)
            ).forEachIndexed { index, (emoji, label) ->
                IconLabelTab(selected = selected == index, onClick = { selected = index }, emoji = emoji, label = label)
            }
        }
        when (BotSubTab.entries[selected]) {
            BotSubTab.ACCESORIOS -> AccessoriesGrid(bot, onBuyAccessory, onToggleAccessory)
            BotSubTab.ESCENARIOS -> ScenariosSection(bot, onBuyScenario, onEquipScenario)
            BotSubTab.EVOLUCION -> EvolutionSection(bot)
        }
    }
}
