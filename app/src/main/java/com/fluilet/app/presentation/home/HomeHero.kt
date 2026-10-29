package com.fluilet.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.common.AnimatedCounter
import com.fluilet.app.presentation.fluibot.FluiBotCanvas
import com.fluilet.app.presentation.fluibot.FluiBotMood
import com.fluilet.app.presentation.fluibot.FluiBotUiState
import com.fluilet.app.presentation.fluibot.XpBar

/**
 * Cabecera de Inicio: FluiBot del perfil seleccionado saludando (salta al tocarlo) junto al saludo y, debajo, los
 * contadores animados de gemas, racha y XP (en varias líneas si no caben) y la barra de experiencia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeHeroCard(state: HomeUiState, bot: FluiBotUiState, onCelebrate: () -> Unit, modifier: Modifier = Modifier) {
    val name = if (state.isTutor) state.selectedChild?.name else state.userName.substringBefore(' ').takeIf { it.isNotBlank() }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(FluiletBrand.Purple.copy(alpha = 0.22f), FluiletBrand.Mint.copy(alpha = 0.14f), MaterialTheme.colorScheme.surfaceVariant)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FluiBotCanvas(
                    stage = bot.bot.stage,
                    equipped = bot.equipped,
                    celebrationTick = bot.celebrationTick,
                    onTap = onCelebrate,
                    mood = FluiBotMood.HAPPY,
                    modifier = Modifier.size(104.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (name == null) stringResource(R.string.home_hero_hello_plain) else stringResource(R.string.home_hero_hello, name),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.home_hero_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val days = pluralStringResource(R.plurals.home_hero_streak, bot.streakDays, bot.streakDays)
                StatChip("💎", bot.gems, "${bot.gems} gemas")
                StatChip("🔥", bot.streakDays, "Racha de $days")
                StatChip("⭐", bot.bot.currentXp, "${bot.bot.currentXp} XP")
            }
            XpBar(bot)
        }
    }
}

@Composable
private fun StatChip(emoji: String, value: Int, description: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.labelLarge)
        AnimatedCounter(value, MaterialTheme.typography.labelLarge)
    }
}
