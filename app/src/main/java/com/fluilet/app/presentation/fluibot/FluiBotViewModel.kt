package com.fluilet.app.presentation.fluibot

import androidx.lifecycle.ViewModel
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.CollectibleCard
import com.fluilet.app.domain.gamification.FluiBotInventory
import com.fluilet.app.domain.gamification.FluiBotInventoryRepository
import com.fluilet.app.domain.gamification.FluiBotProgress
import com.fluilet.app.domain.gamification.PurchaseResult
import com.fluilet.app.domain.gamification.SessionLoot
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Un accesorio del catálogo con su estado para este niño. */
data class AccessoryUi(
    val accessory: Accessory,
    val owned: Boolean,
    val equipped: Boolean,
    val affordable: Boolean
)

data class FluiBotUiState(
    val ownerKey: String = "",
    /** Gemas disponibles: total ganado (con las 30 de bienvenida) menos las gastadas en el taller. */
    val gems: Int = 0,
    val totalGems: Int = 0,
    val spentGems: Int = 0,
    val bot: FluiBotProgress = FluiBotProgress.of(0),
    val equipped: Set<String> = emptySet(),
    val accessories: List<AccessoryUi> = emptyList(),
    val cards: List<CollectibleCard> = emptyList(),
    val badges: List<EducationalBadge> = emptyList(),
    val streakDays: Int = 0,
    /** Cada incremento dispara una celebración de FluiBot (ojos ★, salto y partículas). */
    val celebrationTick: Int = 0,
    val message: String? = null
)

private data class Input(val ownerKey: String, val progress: EducationalProgress, val loot: SessionLoot?)

/**
 * Taller de FluiBot: combina el avance educativo (gemas/XP del niño), el botín reclamado de la sesión
 * y el inventario local por perfil. Comprar equipa de inmediato; se pueden llevar varios accesorios.
 */
@HiltViewModel
class FluiBotViewModel @Inject constructor(
    private val inventories: FluiBotInventoryRepository
) : ViewModel() {

    private var input: Input? = null
    private var inventory = FluiBotInventory.EMPTY
    private var celebration = 0
    private var message: String? = null

    private val _state = MutableStateFlow(FluiBotUiState())
    val state: StateFlow<FluiBotUiState> = _state.asStateFlow()

    /** [claimedLoot] solo se pasa cuando el niño ya abrió el cofre: suma sus gemas y XP al saldo. */
    fun bind(ownerKey: String, progress: EducationalProgress, claimedLoot: SessionLoot? = null) {
        if (input?.ownerKey != ownerKey) {
            inventory = inventories.load(ownerKey)
            message = null
        }
        input = Input(ownerKey, progress, claimedLoot)
        publish()
    }

    fun buy(accessory: Accessory) {
        val current = input ?: return
        val total = totalGems(current)
        when (val result = inventory.purchase(accessory, total)) {
            is PurchaseResult.Bought -> {
                inventory = result.inventory
                inventories.save(current.ownerKey, inventory)
                message = "¡Conseguiste ${accessory.displayName}! Ya lo lleva puesto."
                celebration++
            }
            is PurchaseResult.NotEnoughGems -> message = "Te faltan ${result.missing} 💎 para ${accessory.displayName}."
            PurchaseResult.AlreadyOwned -> message = "${accessory.displayName} ya es tuyo."
            PurchaseResult.UnknownItem -> Unit
        }
        publish()
    }

    fun toggleEquip(accessory: Accessory) {
        val current = input ?: return
        inventory = inventory.toggleEquipped(accessory)
        inventories.save(current.ownerKey, inventory)
        message = null
        publish()
    }

    /** "⚡ Recargar Energía" o un toque sobre FluiBot: reacción festiva. */
    fun celebrate() {
        celebration++
        publish()
    }

    fun consumeMessage() {
        message = null
        publish()
    }

    private fun totalGems(input: Input) = input.progress.totalGems + (input.loot?.gems ?: 0)

    private fun publish() {
        val current = input ?: return
        val total = totalGems(current)
        val balance = inventory.balance(total)
        _state.update {
            FluiBotUiState(
                ownerKey = current.ownerKey,
                gems = balance,
                totalGems = total,
                spentGems = inventory.spentGems,
                bot = FluiBotProgress.of(current.progress.currentXp + (current.loot?.xp ?: 0)),
                equipped = inventory.equipped,
                accessories = Accessory.entries.map { a ->
                    AccessoryUi(a, a.id in inventory.owned, a.id in inventory.equipped, balance >= a.cost)
                },
                cards = current.progress.cards,
                badges = current.progress.badges,
                streakDays = current.progress.streakDays,
                celebrationTick = celebration,
                message = message
            )
        }
    }
}
