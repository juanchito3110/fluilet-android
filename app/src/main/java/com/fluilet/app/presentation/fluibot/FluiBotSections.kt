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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.domain.gamification.CardRarity
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

/** Taller de 10 accesorios: comprar con un toque y equipar/quitar varios a la vez. */
@Composable
fun WorkshopSection(state: FluiBotUiState, onBuy: (com.fluilet.app.domain.gamification.Accessory) -> Unit, onToggle: (com.fluilet.app.domain.gamification.Accessory) -> Unit) {
    SectionCard(stringResource(R.string.fluibot_workshop_title)) {
        Text(stringResource(R.string.fluibot_gems_balance, state.gems), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.fluibot_workshop_hint), style = MaterialTheme.typography.bodySmall)
        state.message?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        state.accessories.forEach { item -> AccessoryRow(item, onBuy, onToggle) }
    }
}

@Composable
private fun AccessoryRow(item: AccessoryUi, onBuy: (com.fluilet.app.domain.gamification.Accessory) -> Unit, onToggle: (com.fluilet.app.domain.gamification.Accessory) -> Unit) {
    val a = item.accessory
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = if (item.equipped) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${a.cost} 💎", style = MaterialTheme.typography.titleSmall)
            }
            Text(a.description, style = MaterialTheme.typography.bodySmall)
            when {
                !item.owned -> Button(
                    onClick = { onBuy(a) },
                    enabled = item.affordable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (item.affordable) stringResource(R.string.fluibot_buy, a.cost)
                        else stringResource(R.string.fluibot_not_enough),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                item.equipped -> OutlinedButton(
                    onClick = { onToggle(a) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.fluibot_equipped_remove), style = MaterialTheme.typography.labelLarge)
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
        cards.forEach { CollectibleCardTile(it) }
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
