package com.fluilet.app.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.gamification.CardChange
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.gamification.SessionLoot
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.fluibot.AlbumSection
import com.fluilet.app.presentation.fluibot.FluiBotViewModel
import com.fluilet.app.presentation.fluibot.TreasureChest
import com.fluilet.app.presentation.fluibot.AccessoriesGrid
import com.fluilet.app.presentation.fluibot.FluiBotStageCard
import com.fluilet.app.presentation.fluibot.GemPill
import com.fluilet.app.presentation.fluibot.MissionsCard
import com.fluilet.app.presentation.fluibot.ScenariosSection
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow

/**
 * "Laboratorio y Rincón de Recompensas de [Nombre]": cofre, FluiBot, taller y álbum. Se muestra DENTRO
 * del Modo Enfoque al terminar la lectura/mini-reto. La única salida es el botón del tutor, que abre
 * el Muro Parental (PIN); con un adulto independiente el botón simplemente sale.
 */
@Composable
fun RewardLab(
    state: ReaderUiState,
    onClaimChest: () -> Unit,
    onReadAgain: () -> Unit,
    onDeliver: () -> Unit,
    onLabTab: (LabTab) -> Unit,
    botViewModel: FluiBotViewModel = hiltViewModel()
) {
    val bot by botViewModel.state.collectAsStateWithLifecycle()
    val loot = state.loot

    // El botín solo suma al saldo y al XP cuando el niño abre el cofre.
    LaunchedEffect(state.baseline, state.chestOpened, loot, state.ownerKey, state.session) {
        botViewModel.bind(
            state.ownerKey,
            state.baseline,
            if (state.chestOpened) loot else null,
            if (state.chestOpened) state.session else null
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.lab_title, state.readerName.ifBlank { "ti" }),
                style = MaterialTheme.typography.headlineSmall
            )
            TabRow(selectedTabIndex = state.labTab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                LabTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.labTab,
                        onClick = { onLabTab(tab) },
                        text = { Text("${tab.emoji} ${tab.label}", style = MaterialTheme.typography.labelMedium, maxLines = 2) }
                    )
                }
            }
            when (state.labTab) {
                LabTab.PREMIO -> {
                    ChestSection(state, loot, bot.bot.stage, onClaimChest)
                    if (state.chestOpened) {
                        GemPill(bot.gems)
                        MissionsCard(bot.missions, botViewModel::claimMission)
                    }
                }
                LabTab.FLUIBOT -> {
                    FluiBotStageCard(bot, botViewModel::celebrate)
                    AccessoriesGrid(bot, botViewModel::buy, botViewModel::toggleEquip)
                    ScenariosSection(bot, botViewModel::buyScenario, botViewModel::equipScenario)
                }
                LabTab.ALBUM -> AlbumSection(bot.cards, bot.badges)
            }
        }

        // Salida exclusiva del tutor: fija y siempre visible.
        Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.reader_again), onReadAgain)
            PrimaryButton(
                text = stringResource(if (state.lockActive) R.string.lab_deliver_to_tutor else R.string.lab_exit),
                onClick = onDeliver,
                icon = if (state.lockActive) Icons.Filled.Lock else null
            )
        }
    }
}

@Composable
private fun ChestSection(state: ReaderUiState, loot: SessionLoot, stageBefore: FluiBotStage, onClaim: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TreasureChest(opened = state.chestOpened, onClick = onClaim, modifier = Modifier.size(200.dp, 170.dp))
            if (!state.chestOpened) {
                Text(
                    stringResource(R.string.lab_chest_prompt),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                LootList(loot, stageBefore, state)
            }
        }
    }
}

@Composable
private fun LootList(loot: SessionLoot, currentStage: FluiBotStage, state: ReaderUiState) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.lab_loot_title), style = MaterialTheme.typography.titleMedium)
        if (loot.completed) Text(stringResource(R.string.lab_loot_completed, loot.gemsForCompleting), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.lab_loot_answers, loot.correctAnswers, loot.gemsForAnswers),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(stringResource(R.string.lab_loot_xp, loot.xp), style = MaterialTheme.typography.bodyLarge)
        val title = loot.cardAfter?.title.orEmpty()
        when (loot.cardChange) {
            CardChange.UNLOCKED -> Text(stringResource(R.string.lab_card_unlocked, title, loot.cardAfter?.rarity?.displayName.orEmpty()), style = MaterialTheme.typography.titleSmall)
            CardChange.UPGRADED -> Text(stringResource(R.string.lab_card_upgraded, title, loot.cardAfter?.rarity?.displayName.orEmpty()), style = MaterialTheme.typography.titleSmall)
            CardChange.NONE -> Unit
        }
        val before = com.fluilet.app.domain.gamification.FluiBotStage.forXp(state.baseline.currentXp)
        if (currentStage.number > before.number) {
            Text(stringResource(R.string.lab_evolved, currentStage.number, currentStage.displayName), style = MaterialTheme.typography.titleSmall)
        }
    }
}
