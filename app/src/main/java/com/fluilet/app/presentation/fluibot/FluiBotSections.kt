package com.fluilet.app.presentation.fluibot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.fluilet.app.presentation.common.AdaptiveSingleLineText
import com.fluilet.app.presentation.common.AdaptiveText
import com.fluilet.app.presentation.common.AnimatedCounter
import com.fluilet.app.presentation.common.ButtonLabel
import com.fluilet.app.presentation.brand.FluiletBrand
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.unit.sp
import com.fluilet.app.presentation.common.CelebrationBurst
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.common.SpringProgressBar
import com.fluilet.app.presentation.home.BadgeGallery
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import com.fluilet.app.presentation.common.CategoryArtwork

private val XpCyan = Color(0xFF22D3EE)
private val FluiletMint = FluiletBrand.Mint

/** Barra de XP con física de resorte: `current_xp / next_stage_xp`. */
@Composable
fun XpBar(state: FluiBotUiState, modifier: Modifier = Modifier) {
    // "0 / 150 XP" completo y centrado ARRIBA de la barra, con aire propio: nunca queda cortado por un borde.
    Column(
        modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AdaptiveSingleLineText(state.bot.label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge)
        SpringProgressBar(
            fraction = state.bot.fraction,
            height = 14.dp,
            brush = Brush.horizontalGradient(listOf(XpCyan, MaterialTheme.colorScheme.primary)),
            modifier = Modifier.semantics { contentDescription = "Experiencia de FluiBot: ${state.bot.label}" }
        )
    }
}

/**
 * Cabecera de 🤖 Mi FluiBot y de la Tienda/Armario: vista previa en vivo del robot en su escenario (salta y celebra
 * al tocarlo, al comprar y al equipar), su nombre y etapa, gemas animadas, XP con resorte y "Recargar Energía".
 */
@Composable
fun FluiBotStageCard(state: FluiBotUiState, onCelebrate: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FluiBotScene(state, onCelebrate)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fluibot_name), style = MaterialTheme.typography.headlineSmall)
                        AdaptiveText(
                            stringResource(R.string.fluibot_stage_label, state.bot.stage.number, state.bot.stage.displayName),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    GemPill(state.gems)
                }
                XpBar(state)
                // Un solo rayo (el ícono) y el texto centrado en una línea.
                Button(onClick = onCelebrate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    AdaptiveSingleLineText(stringResource(R.string.fluibot_recharge), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Tamaño máximo de la escena: el mismo encuadre exacto de FluiBot en cualquier teléfono. */
private val SceneMaxSize = 260.dp

/**
 * Escena de FluiBot con relación de aspecto FIJA (1:1) y tope de 260 dp, centrada: el robot, su fondo y sus
 * accesorios se dibujan en proporción al contenedor, así que el encuadre es idéntico en cualquier pantalla.
 */
@Composable
private fun FluiBotScene(state: FluiBotUiState, onCelebrate: () -> Unit) {
    BoxWithConstraints(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            .sizeIn(maxWidth = SceneMaxSize, maxHeight = SceneMaxSize).aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
    ) {
        FluiBotCanvas(
            stage = state.bot.stage,
            equipped = state.equipped,
            celebrationTick = state.celebrationTick,
            onTap = onCelebrate,
            scenario = state.scenario,
            modifier = Modifier.fillMaxSize()
        )
        AdaptiveSingleLineText(
            stringResource(R.string.fluibot_live_preview),
            Modifier.align(Alignment.TopCenter).padding(top = maxWidth * 0.04f)
                .widthIn(max = maxWidth * 0.86f)
                .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

/** Saldo de gemas con contador animado. */
@Composable
fun GemPill(gems: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$gems gemas" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("💎", style = MaterialTheme.typography.titleMedium)
        AnimatedCounter(gems, MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
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

/** Cuadrícula de 2 columnas con los 12 accesorios (cada uno con su vista previa): comprar y equipar/quitar varios a la vez. */
@Composable
fun AccessoriesGrid(state: FluiBotUiState, onBuy: (Accessory) -> Unit, onToggle: (Accessory) -> Unit) {
    SectionCard(stringResource(R.string.fluibot_workshop_title)) {
        Text(stringResource(R.string.fluibot_gems_balance, state.gems), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.fluibot_workshop_hint), style = MaterialTheme.typography.bodySmall)
        WorkshopMessage(state.message)
        state.accessories.chunked(2).forEach { row ->
            // IntrinsicSize.Min: las dos tarjetas de cada fila miden lo mismo y sus botones quedan alineados.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item -> AccessoryCard(item, state.bot.stage, onBuy, onToggle, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccessoryCard(item: AccessoryUi, stage: FluiBotStage, onBuy: (Accessory) -> Unit, onToggle: (Accessory) -> Unit, modifier: Modifier = Modifier) {
    val a = item.accessory
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = if (item.equipped) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxHeight().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Vista previa limpia: FluiBot (en su etapa) con este accesorio puesto, sin nada encima que lo tape.
            FluiBotCanvas(
                stage = stage,
                equipped = setOf(a.id),
                animated = false,
                interactive = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)))
            )
            // El precio va FUERA de la imagen, en su propia fila.
            AdaptiveSingleLineText(
                if (item.owned) stringResource(R.string.fluibot_owned) else stringResource(R.string.fluibot_price, a.cost),
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (item.owned) FluiletMint.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.owned) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onPrimary
            )
            AdaptiveText(
                a.displayName,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
                textAlign = TextAlign.Center
            )
            Text(
                a.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.weight(1f))
            val buttonModifier = Modifier.fillMaxWidth().height(42.dp)
            val buttonPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            val buttonShape = RoundedCornerShape(12.dp)
            when {
                !item.owned -> Button(onClick = { onBuy(a) }, enabled = item.affordable, modifier = buttonModifier, contentPadding = buttonPadding, shape = buttonShape) {
                    ShopButtonText(if (item.affordable) stringResource(R.string.fluibot_buy_short) else stringResource(R.string.fluibot_not_enough_short))
                }
                item.equipped -> OutlinedButton(onClick = { onToggle(a) }, modifier = buttonModifier, contentPadding = buttonPadding, shape = buttonShape) {
                    ShopButtonText(stringResource(R.string.fluibot_remove))
                }
                else -> Button(onClick = { onToggle(a) }, modifier = buttonModifier, contentPadding = buttonPadding, shape = buttonShape) {
                    ShopButtonText(stringResource(R.string.fluibot_equip))
                }
            }
        }
    }
}

/** "Comprar", "Equipar" o "Quitar" en una sola línea (13 sp, centrada), que se ajusta antes de partirse. */
@Composable
private fun ShopButtonText(text: String) {
    AdaptiveSingleLineText(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), minSize = 10.sp)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdaptiveText(sc.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(if (sc.isFree) stringResource(R.string.scenarios_free) else "${sc.cost} 💎", style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                }
                Text(sc.description, style = MaterialTheme.typography.bodySmall)
                when {
                    item.equipped -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        ButtonLabel(stringResource(R.string.scenarios_equipped))
                    }
                    item.owned -> Button(onClick = { onEquip(sc) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        ButtonLabel(stringResource(R.string.scenarios_equip))
                    }
                    else -> Button(onClick = { onBuy(sc) }, enabled = item.affordable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        ButtonLabel(if (item.affordable) stringResource(R.string.fluibot_buy, sc.cost) else stringResource(R.string.fluibot_not_enough))
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
                // FluiBot arriba y los textos a todo el ancho debajo: nada queda apretado junto al dibujo.
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FluiBotCanvas(stage = stage, equipped = emptySet(), animated = isCurrent, interactive = false, modifier = Modifier.size(76.dp))
                        Spacer(Modifier.weight(1f))
                        if (reached) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Filled.Lock, contentDescription = null)
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AdaptiveText(stringResource(R.string.fluibot_stage_label, stage.number, stage.displayName), style = MaterialTheme.typography.titleSmall)
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
                }
            }
        }
    }
}

/** Las 3 misiones del día con su barra de progreso y su recompensa; reclamar una lanza confeti y estrellas. */
@Composable
fun MissionsCard(missions: List<MissionUi>, onClaim: (String) -> Unit) {
    Box {
        SectionCard(stringResource(R.string.missions_title)) {
            Text(stringResource(R.string.missions_subtitle), style = MaterialTheme.typography.bodySmall)
            missions.forEach { MissionRow(it, onClaim) }
        }
        CelebrationBurst(trigger = missions.count { it.claimed }, modifier = Modifier.matchParentSize(), originY = 0.35f)
    }
}

@Composable
private fun MissionRow(item: MissionUi, onClaim: (String) -> Unit) {
    val m = item.mission
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${m.title}: ${m.current} de ${m.target}. Recompensa ${m.rewardGems} gemas." +
                if (item.claimed) " Reclamada." else if (m.completed) " Lista para reclamar." else ""
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdaptiveText(m.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("+${m.rewardGems} 💎", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
            }
            if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall)
            SpringProgressBar(m.fraction)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(missionProgressText(m), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                when {
                    item.claimed -> Text(stringResource(R.string.missions_claimed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    item.canClaim -> Button(onClick = { onClaim(m.id) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                        AdaptiveSingleLineText(stringResource(R.string.missions_claim), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun missionProgressText(m: com.fluilet.app.domain.gamification.DailyMission): String =
    if (m.id == com.fluilet.app.domain.gamification.DailyMissions.TIME_ID) "${m.current / 60} de ${m.target / 60} min" else "${m.current} de ${m.target}"

// --- Álbum -----------------------------------------------------------------------------------------

/**
 * Álbum de cartas coleccionables (6 categorías), una carta grande por fila para que ningún título ni dato se parta,
 * seguido de las 5 insignias.
 */
@Composable
fun AlbumSection(cards: List<CollectibleCard>, badges: List<EducationalBadge>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.album_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.album_cards_count, cards.count { it.unlocked }, cards.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        cards.forEach { CollectibleCardTile(it) }
    }
    BadgeGallery(badges)
}

/** Acabado del marco según la rareza: degradado metálico, borde, insignia y estrellas. */
private data class CardFinish(
    val frame: List<Color>,
    val edge: Color,
    val badge: Color,
    val onBadge: Color,
    val stars: Int
)

private fun finishFor(rarity: CardRarity?): CardFinish = when (rarity) {
    CardRarity.ORO -> CardFinish(
        listOf(Color(0xFFFFF3B0), Color(0xFFF5C542), Color(0xFFC98A0B), Color(0xFFF8DA6E)),
        Color(0xFF8A5A00), Color(0xFFF5C542), Color(0xFF3F2800), 3
    )
    CardRarity.PLATA -> CardFinish(
        listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFF8F9BB0), Color(0xFFE2E8F0)),
        Color(0xFF475569), Color(0xFFDDE3EC), Color(0xFF1E293B), 2
    )
    CardRarity.BRONCE -> CardFinish(
        listOf(Color(0xFFF6C08E), Color(0xFFCD7F32), Color(0xFF8B4A17), Color(0xFFE8A86A)),
        Color(0xFF5E2E0C), Color(0xFFCD7F32), Color.White, 1
    )
    null -> CardFinish(
        listOf(Color(0xFFB8BEC8), Color(0xFF7C8494), Color(0xFFA3AAB6)),
        Color(0xFF4B5563), Color(0xFF6B7280), Color.White, 0
    )
}

private val CardInk = Color(0xFF17132E)
private val ParchmentPaper = Color(0xFFFFF4D6)
private val ParchmentEdge = Color(0xFFE2C27A)
private val ParchmentInk = Color(0xFF4A3410)

/**
 * Carta coleccionable con estética de trading card: marco metálico de su rareza, cabecera con la insignia ("ORO") y
 * sus estrellas, título completo, ventana de arte con el fondo temático de la categoría y una ficha inferior con la
 * categoría, las lecciones completadas y el dato curioso en un recuadro tipo pergamino.
 */
@Composable
fun CollectibleCardTile(card: CollectibleCard, modifier: Modifier = Modifier) {
    val finish = finishFor(card.rarity)
    val rarityName = card.rarity?.displayName ?: stringResource(R.string.album_locked)
    val categoryName = LessonCategory.displayNameFor(card.categoryCode)
    val headingStyle = MaterialTheme.typography.titleLarge.copy(lineBreak = LineBreak.Heading, hyphens = Hyphens.None)
    Box(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(finish.frame))
            .border(2.dp, finish.edge, RoundedCornerShape(28.dp))
            .padding(9.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Carta ${card.title}, rareza $rarityName, categoría $categoryName, ${card.lessonsCompleted} lecciones completadas"
            }
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(CardInk).alpha(if (card.unlocked) 1f else 0.92f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabecera: rareza y estrellas.
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(finish.badge).border(1.dp, finish.edge, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!card.unlocked) Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = finish.onBadge)
                    Text(rarityName.uppercase(), style = MaterialTheme.typography.labelLarge, color = finish.onBadge, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.weight(1f))
                repeat(3) { index ->
                    Icon(
                        if (index < finish.stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (index < finish.stars) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                card.title,
                style = headingStyle,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            // Ventana de arte de la carta.
            Box(
                Modifier.padding(horizontal = 14.dp).fillMaxWidth().aspectRatio(1.55f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, finish.frame[1], RoundedCornerShape(18.dp))
            ) {
                CategoryArtwork(card.categoryCode, Modifier.fillMaxSize(), iconSize = 76.dp, locked = !card.unlocked)
                // Brillo diagonal de carta impresa.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.08f)))
                    )
                )
            }
            // Ficha inferior.
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(categoryName, style = MaterialTheme.typography.titleMedium.copy(lineBreak = LineBreak.Heading, hyphens = Hyphens.None), color = finish.frame[1])
                Text(
                    stringResource(R.string.album_lessons_completed, card.lessonsCompleted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ParchmentPaper)
                        .border(1.5.dp, ParchmentEdge, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (card.unlocked) {
                        Text("📜 " + stringResource(R.string.album_fun_fact_title), style = MaterialTheme.typography.labelLarge, color = ParchmentInk)
                        Text(card.funFact, style = MaterialTheme.typography.bodyMedium, color = ParchmentInk)
                    } else {
                        Text(stringResource(R.string.album_locked_hint), style = MaterialTheme.typography.bodyMedium, color = ParchmentInk)
                    }
                }
            }
        }
    }
}
