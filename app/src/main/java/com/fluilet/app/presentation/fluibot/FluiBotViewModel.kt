package com.fluilet.app.presentation.fluibot

import androidx.lifecycle.ViewModel
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.CollectibleCard
import com.fluilet.app.domain.gamification.DailyMission
import com.fluilet.app.domain.gamification.DailyMissions
import com.fluilet.app.domain.gamification.FluiBotInventory
import com.fluilet.app.domain.gamification.FluiBotInventoryRepository
import com.fluilet.app.domain.gamification.FluiBotProgress
import com.fluilet.app.domain.gamification.PurchaseResult
import com.fluilet.app.domain.gamification.Scenario
import com.fluilet.app.domain.gamification.SessionContribution
import com.fluilet.app.domain.gamification.SessionLoot
import com.fluilet.app.domain.model.EducationalBadge
import com.fluilet.app.domain.model.EducationalProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Un accesorio del catálogo con su estado para este niño. */
data class AccessoryUi(
    val accessory: Accessory,
    val owned: Boolean,
    val equipped: Boolean,
    val affordable: Boolean
)

/** Un escenario del catálogo con su estado para este niño. */
data class ScenarioUi(val scenario: Scenario, val owned: Boolean, val equipped: Boolean, val affordable: Boolean)

/** Misión del día con su estado de reclamo. */
data class MissionUi(val mission: DailyMission, val claimKey: String, val claimed: Boolean) {
    val canClaim: Boolean get() = mission.completed && !claimed
}

data class FluiBotUiState(
    val ownerKey: String = "",
    /** Gemas disponibles: total ganado (con las 30 de bienvenida) menos las gastadas en el taller. */
    val gems: Int = 0,
    val totalGems: Int = 0,
    val spentGems: Int = 0,
    val bot: FluiBotProgress = FluiBotProgress.of(0),
    val equipped: Set<String> = emptySet(),
    val accessories: List<AccessoryUi> = emptyList(),
    val scenario: Scenario = Scenario.DEFAULT,
    val scenarios: List<ScenarioUi> = emptyList(),
    val missions: List<MissionUi> = emptyList(),
    val cards: List<CollectibleCard> = emptyList(),
    val badges: List<EducationalBadge> = emptyList(),
    val streakDays: Int = 0,
    /** Cada incremento dispara una celebración de FluiBot (ojos ★, salto y partículas). */
    val celebrationTick: Int = 0,
    val message: String? = null
)

private data class Input(val ownerKey: String, val progress: EducationalProgress, val loot: SessionLoot?, val session: SessionContribution?)

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

    /** Reloj inyectable para probar el cambio de día de las misiones. */
    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(FluiBotUiState())
    val state: StateFlow<FluiBotUiState> = _state.asStateFlow()

    /** [claimedLoot] solo se pasa cuando el niño ya abrió el cofre: suma sus gemas y XP al saldo. */
    fun bind(
        ownerKey: String,
        progress: EducationalProgress,
        claimedLoot: SessionLoot? = null,
        session: SessionContribution? = null
    ) {
        if (input?.ownerKey != ownerKey) {
            inventory = inventories.load(ownerKey)
            message = null
        }
        input = Input(ownerKey, progress, claimedLoot, session)
        reconcileServerClaims()
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
        val before = inventory
        inventory = inventory.toggleEquipped(accessory)
        inventories.save(current.ownerKey, inventory)
        message = null
        // Al ponerse un accesorio, FluiBot lo celebra en la vista previa (al quitarlo, no).
        if (accessory.id in inventory.equipped && accessory.id !in before.equipped) celebration++
        publish()
    }

    /** Accesorios puestos de cualquier perfil (miniaturas del selector de perfiles). */
    fun equippedFor(ownerKey: String): Set<String> =
        if (ownerKey == input?.ownerKey) inventory.equipped else inventories.load(ownerKey).equipped

    fun buyScenario(scenario: Scenario) {
        val current = input ?: return
        when (val result = inventory.purchaseScenario(scenario, totalGems(current))) {
            is PurchaseResult.Bought -> {
                inventory = result.inventory
                inventories.save(current.ownerKey, inventory)
                message = "¡Nuevo escenario: ${scenario.displayName}! Ya está puesto."
                celebration++
            }
            is PurchaseResult.NotEnoughGems -> message = "Te faltan ${result.missing} 💎 para ${scenario.displayName}."
            PurchaseResult.AlreadyOwned -> message = "${scenario.displayName} ya es tuyo."
            PurchaseResult.UnknownItem -> Unit
        }
        publish()
    }

    fun equipScenario(scenario: Scenario) {
        val current = input ?: return
        inventory = inventory.equipScenario(scenario)
        inventories.save(current.ownerKey, inventory)
        message = null
        publish()
    }

    /** Reclama el premio de una misión completada (una vez por día): suma sus gemas al saldo. */
    fun claimMission(missionId: String) {
        val current = input ?: return
        val ui = currentMissions(current).firstOrNull { it.mission.id == missionId } ?: return
        // Una misión del servidor se confirma con su endpoint (HomeViewModel): aquí nunca se paga dos veces.
        if (!ui.canClaim || ui.mission.fromServer) return
        inventory = inventory.claimMission(ui.mission, ui.claimKey)
        inventories.save(current.ownerKey, inventory)
        message = "¡Misión cumplida! +${ui.mission.rewardGems} 💎"
        celebration++
        publish()
    }

    /**
     * Respaldo SIN conexión para una misión del servidor: el backend no respondió al claim, así que el premio se
     * abona de forma provisional. En cuanto el resumen del servidor la traiga como `claimed` se retira ([reconcileServerClaims]).
     */
    fun claimMissionOffline(missionId: String) {
        val current = input ?: return
        val ui = currentMissions(current).firstOrNull { it.mission.id == missionId } ?: return
        if (!ui.canClaim) return
        inventory = inventory.claimMission(ui.mission, ui.claimKey)
        inventories.save(current.ownerKey, inventory)
        message = "Sin conexión: +${ui.mission.rewardGems} 💎 provisionales. Se ajustarán al sincronizar."
        celebration++
        publish()
    }

    /** Si el servidor ya abonó una misión que se había reclamado sin conexión, se descarta el premio provisional. */
    private fun reconcileServerClaims() {
        val current = input ?: return
        val day = today()
        var updated = inventory
        current.progress.serverMissions.orEmpty().filter { it.claimed }.forEach { m ->
            updated = updated.revokeClaim(m, DailyMissions.claimKey(day, m.id))
        }
        if (updated != inventory) {
            inventory = updated
            inventories.save(current.ownerKey, inventory)
        }
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

    private fun currentMissions(input: Input): List<MissionUi> {
        val day = today()
        val missions = input.progress.serverMissions ?: DailyMissions.compute(input.progress.lessons, day, zone, input.session)
        return missions.map { m ->
            val key = DailyMissions.claimKey(day, m.id)
            // Las del servidor ya vienen abonadas en `total_gems`: manda su `claimed`, nunca el inventario local.
            // Una misión del servidor puede estar abonada por el backend (`claimed`) o, sin conexión, de forma provisional aquí.
            MissionUi(m, key, if (m.fromServer) m.claimed || key in inventory.claimedMissions else key in inventory.claimedMissions)
        }
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
                scenario = inventory.scenario,
                scenarios = Scenario.entries.map { sc ->
                    ScenarioUi(sc, inventory.ownsScenario(sc), inventory.scenario == sc, balance >= sc.cost)
                },
                missions = currentMissions(current),
                cards = current.progress.cards,
                badges = current.progress.badges,
                streakDays = current.progress.streakDays,
                celebrationTick = celebration,
                message = message
            )
        }
    }
}
