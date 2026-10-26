package com.fluilet.app.presentation.fluibot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.CardRarity
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.gamification.Scenario
import com.fluilet.app.domain.gamification.CollectibleCard
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.home.BadgeGallery

private val BronzeColor = Color(0xFFB45309)
private val SilverColor = Color(0xFF94A3B8)
private val GoldColor = Color(0xFFF59E0B)

/** Barra de XP animada: `current_xp / next_stage_xp`. */
@Composable
fun XpBar(state: FluiBotUiState, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(state.bot.fraction, tween(900), label = "xp")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
                .semantics { contentDescription = "Experiencia de FluiBot: ${state.bot.label}" }
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary))
        }
        Text(state.bot.label, style = MaterialTheme.typography.labelMedium)
    }
}

/** FluiBot en vivo con su rango, barra de XP y el botón "⚡ Recargar Energía". */
@Composable
fun FluiBotPanel(state: FluiBotUiState, onCelebrate: () -> Unit, modifier: Modifier = Modifier, botSize: Dp = 220.dp) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluiBotCanvas(
            stage = state.bot.stage,
            equipped = state.equipped,
            celebrationTick = state.celebrationTick,
            onTap = onCelebrate,
            modifier = Modifier.size(botSize).aspectRatio(1f)
        )
        Text("FluiBot · ${state.bot.stage.rankTitle}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.fluibot_stage_label, state.bot.stage.number, state.bot.stage.displayName),
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
        )
        XpBar(state)
        Button(onClick = onCelebrate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Filled.Bolt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.fluibot_recharge), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Escenario amplio de FluiBot: robot, fondo animado, nombre, etapa, XP, gemas y "⚡ Recargar Energía". */
@Composable
fun FluiBotStageCard(state: FluiBotUiState, onCelebrate: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))) {
                FluiBotCanvas(
                    stage = state.bot.stage,
                    equipped = state.equipped,
                    celebrationTick = state.celebrationTick,
                    onTap = onCelebrate,
                    scenario = state.scenario,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fluibot_name), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.fluibot_stage_label, state.bot.stage.number, state.bot.stage.displayName),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    GemPill(state.gems)
                }
                XpBar(state)
                Button(onClick = onCelebrate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.fluibot_recharge), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun GemPill(gems: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = "$gems gemas" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("💎", style = MaterialTheme.typography.titleMedium)
        Text("$gems", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Mensaje breve del taller (compra, falta de gemas...). */
@Composable
private fun WorkshopMessage(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Cuadrícula de 2 columnas con los 10 accesorios: comprar con un toque y equipar/quitar varios a la vez. */
@Composable
fun AccessoriesGrid(state: FluiBotUiState, onBuy: (Accessory) -> Unit, onToggle: (Accessory) -> Unit) {
    SectionCard(stringResource(R.string.fluibot_workshop_title)) {
        Text(stringResource(R.string.fluibot_gems_balance, state.gems), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.fluibot_workshop_hint), style = MaterialTheme.typography.bodySmall)
        WorkshopMessage(state.message)
        state.accessories.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> AccessoryCard(item, onBuy, onToggle, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccessoryCard(item: AccessoryUi, onBuy: (Accessory) -> Unit, onToggle: (Accessory) -> Unit, modifier: Modifier = Modifier) {
    val a = item.accessory
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = if (item.equipped) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(a.displayName, style = MaterialTheme.typography.titleSmall)
            Text("${a.cost} 💎", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(a.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
            when {
                !item.owned -> Button(
                    onClick = { onBuy(a) },
                    enabled = item.affordable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (item.affordable) stringResource(R.string.fluibot_buy_short) else stringResource(R.string.fluibot_not_enough_short),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                item.equipped -> OutlinedButton(
                    onClick = { onToggle(a) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.fluibot_remove), style = MaterialTheme.typography.labelLarge)
                }
                else -> Button(
                    onClick = { onToggle(a) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.fluibot_equip), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** Los 5 escenarios: se desbloquean con gemas y solo uno está puesto a la vez. */
@Composable
fun ScenariosSection(state: FluiBotUiState, onBuy: (Scenario) -> Unit, onEquip: (Scenario) -> Unit) {
    SectionCard(stringResource(R.string.scenarios_title)) {
        Text(stringResource(R.string.fluibot_gems_balance, state.gems), style = MaterialTheme.typography.headlineSmall)
        WorkshopMessage(state.message)
        state.scenarios.forEach { item -> ScenarioCard(item, onBuy, onEquip) }
    }
}

@Composable
private fun ScenarioCard(item: ScenarioUi, onBuy: (Scenario) -> Unit, onEquip: (Scenario) -> Unit) {
    val sc = item.scenario
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = if (item.equipped) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScenarioPreview(sc, Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
            Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sc.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(if (sc.isFree) stringResource(R.string.scenarios_free) else "${sc.cost} 💎", style = MaterialTheme.typography.titleSmall)
                }
                Text(sc.description, style = MaterialTheme.typography.bodySmall)
                when {
                    item.equipped -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scenarios_equipped), style = MaterialTheme.typography.labelLarge)
                    }
                    item.owned -> Button(onClick = { onEquip(sc) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.scenarios_equip), style = MaterialTheme.typography.labelLarge)
                    }
                    else -> Button(onClick = { onBuy(sc) }, enabled = item.affordable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(
                            if (item.affordable) stringResource(R.string.fluibot_buy, sc.cost) else stringResource(R.string.fluibot_not_enough),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/** Las 4 etapas evolutivas: cuáles alcanzó FluiBot y cuánto XP requiere la siguiente. */
@Composable
fun EvolutionSection(state: FluiBotUiState) {
    SectionCard(stringResource(R.string.evolution_title)) {
        val current = state.bot.stage
        FluiBotStage.entries.forEach { stage ->
            val reached = state.bot.currentXp >= stage.minXp
            val isCurrent = stage == current
            Card(
                modifier = Modifier.fillMaxWidth().alpha(if (reached) 1f else 0.6f).semantics(mergeDescendants = true) {
                    contentDescription = "Etapa ${stage.number}: ${stage.displayName}. " +
                        if (reached) "Alcanzada." else "Requiere ${stage.minXp} XP."
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                border = BorderStroke(if (isCurrent) 3.dp else 1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FluiBotCanvas(stage = stage, equipped = emptySet(), modifier = Modifier.size(84.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.fluibot_stage_label, stage.number, stage.displayName), style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                isCurrent -> stringResource(R.string.evolution_current)
                                reached -> stringResource(R.string.evolution_reached)
                                else -> stringResource(R.string.evolution_requires, stage.minXp)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                        if (isCurrent) {
                            val next = stage.next
                            Text(
                                if (next != null) stringResource(R.string.evolution_missing, (next.minXp - state.bot.currentXp).coerceAtLeast(0), next.displayName)
                                else stringResource(R.string.evolution_max),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (reached) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Filled.Lock, contentDescription = null)
                }
            }
        }
    }
}

/** Las 3 misiones del día con su barra de progreso y su recompensa en gemas. */
@Composable
fun MissionsCard(missions: List<MissionUi>, onClaim: (String) -> Unit) {
    SectionCard(stringResource(R.string.missions_title)) {
        Text(stringResource(R.string.missions_subtitle), style = MaterialTheme.typography.bodySmall)
        missions.forEach { MissionRow(it, onClaim) }
    }
}

@Composable
private fun MissionRow(item: MissionUi, onClaim: (String) -> Unit) {
    val m = item.mission
    val fraction by animateFloatAsState(m.fraction, tween(700), label = "mission")
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${m.title}: ${m.current} de ${m.target}. Recompensa ${m.rewardGems} gemas." +
                if (item.claimed) " Reclamada." else if (m.completed) " Lista para reclamar." else ""
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("+${m.rewardGems} 💎", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(missionProgressText(m), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                when {
                    item.claimed -> Text(stringResource(R.string.missions_claimed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    item.canClaim -> Button(onClick = { onClaim(m.id) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(stringResource(R.string.missions_claim), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun missionProgressText(m: com.fluilet.app.domain.gamification.DailyMission): String =
    if (m.id == com.fluilet.app.domain.gamification.DailyMissions.TIME_ID) "${m.current / 60} de ${m.target / 60} min" else "${m.current} de ${m.target}"

// --- Álbum -----------------------------------------------------------------------------------------

private fun rarityColor(rarity: CardRarity?): Color = when (rarity) {
    CardRarity.BRONCE -> BronzeColor
    CardRarity.PLATA -> SilverColor
    CardRarity.ORO -> GoldColor
    null -> Color(0xFF9CA3AF)
}

/** Álbum de cartas coleccionables (6 categorías) seguido de las 5 insignias. */
@Composable
fun AlbumSection(cards: List<CollectibleCard>, badges: List<EducationalBadge>) {
    SectionCard(stringResource(R.string.album_title)) {
        Text(
            stringResource(R.string.album_cards_count, cards.count { it.unlocked }, cards.size),
            style = MaterialTheme.typography.bodyMedium
        )
        cards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { CollectibleCardTile(it, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    BadgeGallery(badges)
}

@Composable
fun CollectibleCardTile(card: CollectibleCard, modifier: Modifier = Modifier) {
    val color = rarityColor(card.rarity)
    val rarityName = card.rarity?.displayName ?: stringResource(R.string.album_locked)
    Card(
        modifier = modifier.fillMaxWidth().alpha(if (card.unlocked) 1f else 0.6f).semantics(mergeDescendants = true) {
            contentDescription = "Carta ${card.title}, rareza $rarityName, ${card.lessonsCompleted} lecciones completadas"
        },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(if (card.rarity == CardRarity.ORO) 4.dp else 3.dp, color)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!card.unlocked) Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Text(rarityName, style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
            Text(LessonCategory.displayNameFor(card.categoryCode), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.album_lessons_completed, card.lessonsCompleted), style = MaterialTheme.typography.bodyMedium)
            if (card.unlocked) {
                Text(stringResource(R.string.album_fun_fact_title), style = MaterialTheme.typography.labelLarge, color = color)
                Text(card.funFact, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.album_locked_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
