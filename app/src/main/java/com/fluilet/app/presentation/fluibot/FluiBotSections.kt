package com.fluilet.app.presentation.fluibot

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.fluilet.app.presentation.common.FluiletCarousel
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.fluilet.app.presentation.common.SelectableChip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import com.fluilet.app.presentation.brand.FluiletBrand
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
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

/** Tamaños de la escena de FluiBot, sin Android (probados con pruebas unitarias). */
object StageLayout {
    /** Escena de la tarjeta principal: grande y centrada (200–216 dp), sin pasar del 80 % del ancho de la tarjeta. */
    fun cardSceneSize(cardWidth: Dp, windowHeight: Dp): Dp =
        minOf(cardWidth * 0.8f, if (windowHeight < ShortScreen) CardSceneSmall else CardSceneMax)

    /** Visor a pantalla completa: 300–360 dp cuando cabe, nunca más ancho que la pantalla ni más de la mitad del alto. */
    fun viewerSceneSize(width: Dp, windowHeight: Dp): Dp =
        minOf(ViewerSceneMax, width, windowHeight * 0.5f).coerceAtLeast(minOf(ViewerSceneMin, width))

    val CardSceneMax: Dp = 216.dp
    val CardSceneSmall: Dp = 200.dp

    /** En pantallas más bajas que esto la escena de la tarjeta usa 200 dp para que las pestañas sigan a la vista. */
    val ShortScreen: Dp = 700.dp
    val ViewerSceneMax: Dp = 360.dp
    val ViewerSceneMin: Dp = 300.dp

    /** Por debajo de este ancho, "Recargar Energía" y "Ver en grande" van uno sobre otro. */
    val ButtonsStackBelow: Dp = 280.dp
}

/**
 * Tarjeta principal de FluiBot (Avatar y Laboratorio): el robot GRANDE y limpio arriba (en su escenario, con sus
 * accesorios; nada escrito encima), y debajo, compactos, su nombre, etapa, gemas, XP, "⚡ Recargar Energía" y el botón
 * independiente "Ver en grande". Tocar a FluiBot sigue dándole energía; solo el botón abre el visor a pantalla completa.
 */
@Composable
fun FluiBotStageCard(
    state: FluiBotUiState,
    onCelebrate: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleAccessory: ((Accessory) -> Unit)? = null,
    onEquipScenario: ((Scenario) -> Unit)? = null
) {
    var viewerOpen by rememberSaveable { mutableStateOf(false) }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val scene = StageLayout.cardSceneSize(maxWidth, windowHeight)
            val stackButtons = maxWidth < StageLayout.ButtonsStackBelow
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FluiBotScene(state, onCelebrate, Modifier.size(scene))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        AdaptiveText(stringResource(R.string.fluibot_name), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
                        AdaptiveText(
                            stringResource(R.string.fluibot_stage_label, state.bot.stage.number, state.bot.stage.displayName),
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GemPill(state.gems)
                }
                XpBar(state)
                if (stackButtons) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RechargeButton(onCelebrate, Modifier.fillMaxWidth())
                        ViewBigButton({ viewerOpen = true }, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RechargeButton(onCelebrate, Modifier.weight(1.15f))
                        ViewBigButton({ viewerOpen = true }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (viewerOpen) {
        FluiBotViewer(state, onCelebrate, onToggleAccessory, onEquipScenario, onClose = { viewerOpen = false })
    }
}

/** Relleno de los botones de la tarjeta: deja ancho a "Recargar Energía" y "Ver en grande" en una línea. */
private val StageButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

/** "⚡ Recargar Energía": un solo rayo (el ícono) y el texto centrado en una línea. */
@Composable
internal fun RechargeButton(onCelebrate: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onCelebrate,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = StageButtonPadding
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        AdaptiveSingleLineText(stringResource(R.string.fluibot_recharge), style = MaterialTheme.typography.labelLarge)
    }
}

/** "🔍 Ver en grande": botón aparte (no el toque sobre FluiBot) que abre el visor a pantalla completa. */
@Composable
private fun ViewBigButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = StageButtonPadding
    ) {
        Icon(Icons.Filled.ZoomIn, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        AdaptiveSingleLineText(stringResource(R.string.fluibot_view_big), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Escena de FluiBot con relación de aspecto FIJA (1:1): el robot, su fondo y sus accesorios se dibujan en proporción al
 * contenedor, así que el encuadre es idéntico en cualquier tamaño. Nada se escribe encima del dibujo. Tocarlo le da
 * energía (salta y celebra). [modifier] fija el tamaño.
 */
@Composable
internal fun FluiBotScene(state: FluiBotUiState, onCelebrate: () -> Unit, modifier: Modifier = Modifier) {
    FluiBotCanvas(
        stage = state.bot.stage,
        equipped = state.equipped,
        celebrationTick = state.celebrationTick,
        onTap = onCelebrate,
        scenario = state.scenario,
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(24.dp))
    )
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

/** Título y ayuda de una sección del taller, con el mensaje de la última compra debajo. */
@Composable
private fun ShopHeader(title: String, hint: String, message: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AdaptiveText(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorkshopMessage(message)
    }
}

/**
 * Los 12 accesorios en un CARRUSEL horizontal: una tarjeta compacta a la vez (las vecinas se asoman), con el nombre y la
 * descripción completos, el precio debajo del dibujo y el botón en una sola línea. FluiBot sigue a la vista arriba y
 * los accesorios se combinan a la vez.
 */
@Composable
fun AccessoriesShop(state: FluiBotUiState, onBuy: (Accessory) -> Unit, onToggle: (Accessory) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShopHeader(stringResource(R.string.fluibot_workshop_title), stringResource(R.string.fluibot_workshop_hint), state.message)
        FluiletCarousel(state.accessories, itemKey = { it.accessory.id }) { item, _ ->
            AccessoryCard(item, state.bot.stage, onBuy, onToggle)
        }
    }
}

@Composable
private fun AccessoryCard(item: AccessoryUi, stage: FluiBotStage, onBuy: (Accessory) -> Unit, onToggle: (Accessory) -> Unit) {
    val a = item.accessory
    ShopItemCard(
        title = a.displayName,
        description = a.description,
        price = ShopCatalog.priceTag(item.owned, item.equipped, a.cost),
        highlighted = item.equipped,
        preview = { modifier ->
            // Vista previa limpia: FluiBot (en su etapa) con este accesorio puesto, sin nada encima que lo tape.
            FluiBotCanvas(
                stage = stage,
                equipped = setOf(a.id),
                animated = false,
                interactive = false,
                modifier = modifier.background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background))
                )
            )
        }
    ) {
        when {
            !item.owned -> ShopButton(
                if (item.affordable) stringResource(R.string.fluibot_buy_short) else stringResource(R.string.fluibot_not_enough_short),
                enabled = item.affordable,
                onClick = { onBuy(a) }
            )
            item.equipped -> ShopButton(stringResource(R.string.fluibot_remove), outlined = true, onClick = { onToggle(a) })
            else -> ShopButton(stringResource(R.string.fluibot_equip), onClick = { onToggle(a) })
        }
    }
}

/** Etiqueta de precio de un artículo: "15 💎", "Gratis", "✓ Tuyo" o "✓ Equipado". */
sealed interface PriceTag {
    data class Gems(val cost: Int) : PriceTag
    data object Free : PriceTag
    data object Owned : PriceTag
    data object Equipped : PriceTag
}

/** Reglas de la tienda sin Android (probadas con pruebas unitarias). */
object ShopCatalog {
    fun priceTag(owned: Boolean, equipped: Boolean, cost: Int): PriceTag = when {
        equipped -> PriceTag.Equipped
        owned -> PriceTag.Owned
        cost <= 0 -> PriceTag.Free
        else -> PriceTag.Gems(cost)
    }

    /** Por debajo de este ancho de tarjeta, el dibujo va arriba y los textos debajo (a todo el ancho). */
    val StackedBelow: Dp = 220.dp

    /** Ancho del dibujo: 36 % de la tarjeta, sin pasar de [ShopPreviewMax]. */
    fun previewWidth(cardWidth: Dp): Dp = minOf(ShopPreviewMax, cardWidth * 0.36f)

    val ShopPreviewMax: Dp = 104.dp
}

@Composable
private fun PriceTag.label(): String = when (this) {
    is PriceTag.Gems -> stringResource(R.string.fluibot_price, cost)
    PriceTag.Free -> stringResource(R.string.scenarios_free)
    PriceTag.Owned -> stringResource(R.string.fluibot_owned)
    PriceTag.Equipped -> stringResource(R.string.fluibot_equipped)
}

/**
 * Tarjeta horizontal y compacta del carrusel de la tienda (accesorios y escenarios), con altura 100 % dinámica: a la
 * izquierda el dibujo limpio con el precio DEBAJO (jamás encima); a la derecha el nombre y la descripción completos y el
 * botón en una sola línea. Si la tarjeta es muy angosta (letra enorme), todo se apila centrado.
 */
@Composable
private fun ShopItemCard(
    title: String,
    description: String,
    price: PriceTag,
    highlighted: Boolean,
    preview: @Composable (Modifier) -> Unit,
    action: @Composable () -> Unit
) {
    val priceText = price.label()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        // Las tarjetas del carrusel van sobre el fondo de la pantalla: un tono de superficie las separa de él.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val previewModifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            val previewWidth = ShopCatalog.previewWidth(maxWidth)
            if (maxWidth >= ShopCatalog.StackedBelow) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        Modifier.width(previewWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        preview(previewModifier)
                        PricePill(priceText, price)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // TalkBack lee nombre y descripción de corrido; el botón conserva su propio foco.
                        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ShopTitle(title, TextAlign.Start)
                            ShopDescription(description, TextAlign.Start)
                        }
                        Spacer(Modifier.size(2.dp))
                        action()
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.widthIn(max = ShopCatalog.ShopPreviewMax)) { preview(previewModifier) }
                    PricePill(priceText, price)
                    ShopTitle(title, TextAlign.Center)
                    ShopDescription(description, TextAlign.Center)
                    action()
                }
            }
        }
    }
}

@Composable
private fun ShopTitle(text: String, align: TextAlign) {
    // Nombre COMPLETO ("Sombrero de Explorador", "Corbatín LED de Gala"): baja de renglón en lugar de cortarse.
    AdaptiveText(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp), textAlign = align)
}

@Composable
private fun ShopDescription(text: String, align: TextAlign) {
    AdaptiveText(
        text,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align
    )
}

@Composable
private fun PricePill(text: String, price: PriceTag) {
    val paid = price is PriceTag.Gems
    AdaptiveSingleLineText(
        text,
        Modifier.clip(RoundedCornerShape(50))
            .background(if (paid) MaterialTheme.colorScheme.primary else FluiletMint.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (paid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    )
}

/** "Comprar", "Equipar", "Quitar" o "Equipado" a todo el ancho, en una sola línea centrada (sin alto fijo). */
@Composable
private fun ShopButton(text: String, enabled: Boolean = true, outlined: Boolean = false, check: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
    val padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    val shape = RoundedCornerShape(12.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (check) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        AdaptiveSingleLineText(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
    }
    if (outlined) OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = padding, shape = shape, content = content)
    else Button(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = padding, shape = shape, content = content)
}

/** Los 5 escenarios en el mismo carrusel de la tienda: se desbloquean con gemas y solo uno está puesto a la vez. */
@Composable
fun ScenariosSection(state: FluiBotUiState, onBuy: (Scenario) -> Unit, onEquip: (Scenario) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShopHeader(stringResource(R.string.scenarios_title), stringResource(R.string.scenarios_hint), state.message)
        // Arranca en el escenario puesto para que el niño vea primero el que tiene FluiBot.
        val equipped = state.scenarios.indexOfFirst { it.equipped }.coerceAtLeast(0)
        FluiletCarousel(state.scenarios, initialPage = equipped, itemKey = { it.scenario.id }) { item, _ ->
            ScenarioCard(item, onBuy, onEquip)
        }
    }
}

@Composable
private fun ScenarioCard(item: ScenarioUi, onBuy: (Scenario) -> Unit, onEquip: (Scenario) -> Unit) {
    val sc = item.scenario
    ShopItemCard(
        title = sc.displayName,
        description = sc.description,
        price = ShopCatalog.priceTag(item.owned && !sc.isFree, item.equipped, sc.cost),
        highlighted = item.equipped,
        preview = { modifier -> ScenarioPreview(sc, modifier) }
    ) {
        when {
            item.equipped -> ShopButton(stringResource(R.string.fluibot_equipped_button), enabled = false, outlined = true, check = true, onClick = {})
            item.owned -> ShopButton(stringResource(R.string.fluibot_equip), onClick = { onEquip(sc) })
            else -> ShopButton(
                if (item.affordable) stringResource(R.string.fluibot_buy_short) else stringResource(R.string.fluibot_not_enough_short),
                enabled = item.affordable,
                onClick = { onBuy(sc) }
            )
        }
    }
}

/** Avance de FluiBot por etapas, sin Android (probado con pruebas unitarias). */
object EvolutionTrack {
    /** Cuánto lleva de una etapa: 1 si ya la superó, el avance dentro de la actual o el camino hacia una bloqueada. */
    fun stageFraction(stage: FluiBotStage, currentXp: Int): Float {
        val xp = currentXp.coerceAtLeast(0)
        return when {
            xp >= stage.upperXp -> 1f
            xp >= stage.minXp -> ((xp - stage.minXp).toFloat() / (stage.upperXp - stage.minXp)).coerceIn(0f, 1f)
            stage.minXp <= 0 -> 0f
            else -> (xp.toFloat() / stage.minXp).coerceIn(0f, 1f)
        }
    }

    /** Avance total del camino de evolución (0 XP = 0; tope de la última etapa = 1). */
    fun overallFraction(currentXp: Int): Float =
        (currentXp.coerceAtLeast(0).toFloat() / FluiBotStage.entries.last().upperXp).coerceIn(0f, 1f)
}

/**
 * Evolución de FluiBot: barra de progreso del camino completo y un CARRUSEL por etapas ("Etapa 2 de 4") que arranca en
 * la etapa actual. Cada tarjeta muestra a FluiBot en esa etapa, su estado y su propia barra de avance.
 */
@Composable
fun EvolutionSection(state: FluiBotUiState) {
    val current = state.bot.stage
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveText(stringResource(R.string.evolution_title), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
            AdaptiveText(
                stringResource(R.string.evolution_path, current.number, FluiBotStage.entries.size),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SpringProgressBar(
                fraction = EvolutionTrack.overallFraction(state.bot.currentXp),
                height = 12.dp,
                brush = Brush.horizontalGradient(listOf(XpCyan, MaterialTheme.colorScheme.primary)),
                modifier = Modifier.semantics { contentDescription = "Camino de evolución: etapa ${current.number} de ${FluiBotStage.entries.size}" }
            )
        }
        FluiletCarousel(
            FluiBotStage.entries,
            initialPage = current.ordinal,
            itemKey = { it.number },
            counter = { page, count -> stringResource(R.string.evolution_counter, page + 1, count) }
        ) { stage, _ -> StageCard(stage, state) }
    }
}

@Composable
private fun StageCard(stage: FluiBotStage, state: FluiBotUiState) {
    val reached = state.bot.currentXp >= stage.minXp
    val isCurrent = stage == state.bot.stage
    val status = when {
        isCurrent -> stringResource(R.string.evolution_current)
        reached -> stringResource(R.string.evolution_reached)
        else -> stringResource(R.string.evolution_requires, stage.minXp)
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Etapa ${stage.number}: ${stage.displayName}. $status."
        },
        shape = RoundedCornerShape(20.dp),
        // Las tarjetas del carrusel van sobre el fondo de la pantalla: un tono de superficie las separa de él.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(if (isCurrent) 3.dp else 1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FluiBotCanvas(
                    stage = stage, equipped = emptySet(), animated = isCurrent, interactive = false,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)).alpha(if (reached) 1f else 0.55f)
                )
                if (reached) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Filled.Lock, contentDescription = null)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdaptiveText(stringResource(R.string.fluibot_stage_label, stage.number, stage.displayName), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
                AdaptiveText(
                    status,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                SpringProgressBar(EvolutionTrack.stageFraction(stage, state.bot.currentXp), height = 8.dp)
                val next = stage.next
                Text(
                    when {
                        isCurrent && next != null -> stringResource(R.string.evolution_missing, (next.minXp - state.bot.currentXp).coerceAtLeast(0), next.displayName)
                        isCurrent -> stringResource(R.string.evolution_max)
                        reached -> stringResource(R.string.evolution_range, stage.minXp, stage.upperXp)
                        else -> stringResource(R.string.evolution_missing, (stage.minXp - state.bot.currentXp).coerceAtLeast(0), stage.displayName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

/** Filtro rápido del álbum. */
enum class AlbumFilter {
    ALL, UNLOCKED, LOCKED;

    fun apply(cards: List<CollectibleCard>): List<CollectibleCard> = when (this) {
        ALL -> cards
        UNLOCKED -> cards.filter { it.unlocked }
        LOCKED -> cards.filterNot { it.unlocked }
    }
}

/** Álbum (cartas en carrusel) y, debajo, los logros en su propio carrusel: la vista del Laboratorio de Recompensas. */
@Composable
fun AlbumSection(cards: List<CollectibleCard>, badges: List<EducationalBadge>) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AlbumCarousel(cards)
        BadgeGallery(badges)
    }
}

/**
 * Álbum de cartas coleccionables: contador y barra de progreso arriba, filtro rápido ("Todas", "Desbloqueadas", "Por
 * descubrir") y las cartas en un CARRUSEL horizontal con su ilustración, título y dato curioso completos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumCarousel(cards: List<CollectibleCard>) {
    var filter by rememberSaveable { mutableStateOf(AlbumFilter.ALL) }
    val unlocked = cards.count { it.unlocked }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveText(stringResource(R.string.album_title), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
            AdaptiveText(
                stringResource(R.string.album_cards_count, unlocked, cards.size),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SpringProgressBar(if (cards.isEmpty()) 0f else unlocked.toFloat() / cards.size, height = 10.dp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AlbumFilter.entries.forEach { option ->
                val count = option.apply(cards).size
                SelectableChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = {
                        AdaptiveText(
                            when (option) {
                                AlbumFilter.ALL -> stringResource(R.string.album_filter_all, count)
                                AlbumFilter.UNLOCKED -> stringResource(R.string.album_filter_unlocked, count)
                                AlbumFilter.LOCKED -> stringResource(R.string.album_filter_locked, count)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }
        AnimatedContent(filter, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "albumFilter") { current ->
            val list = current.apply(cards)
            if (list.isEmpty()) {
                Text(
                    stringResource(if (current == AlbumFilter.UNLOCKED) R.string.album_empty_unlocked else R.string.album_empty_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            } else {
                FluiletCarousel(list, itemKey = { it.categoryCode }) { card, _ -> CollectibleCardTile(card) }
            }
        }
    }
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
