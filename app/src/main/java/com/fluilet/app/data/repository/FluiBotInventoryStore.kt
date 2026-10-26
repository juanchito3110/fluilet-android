package com.fluilet.app.data.repository

import com.fluilet.app.core.security.SecureStore
import com.fluilet.app.domain.gamification.FluiBotInventory
import com.fluilet.app.domain.gamification.FluiBotInventoryRepository
import com.fluilet.app.domain.gamification.Scenario
import javax.inject.Inject
import javax.inject.Singleton

/** Inventario guardado por perfil en el almacén cifrado (preferencias de juego, sin texto de lectura). */
@Singleton
class FluiBotInventoryStore @Inject constructor(
    private val store: SecureStore
) : FluiBotInventoryRepository {

    override fun load(ownerKey: String): FluiBotInventory = FluiBotInventory(
        owned = store.getString(key(ownerKey, OWNED)).toSet(),
        equipped = store.getString(key(ownerKey, EQUIPPED)).toSet(),
        spentGems = store.getLong(key(ownerKey, SPENT)).toInt().coerceAtLeast(0),
        ownedScenarios = store.getString(key(ownerKey, SCENARIOS)).toSet(),
        equippedScenario = store.getString(key(ownerKey, SCENARIO)) ?: Scenario.DEFAULT.id,
        bonusGems = store.getLong(key(ownerKey, BONUS)).toInt().coerceAtLeast(0),
        claimedMissions = store.getString(key(ownerKey, CLAIMED)).toSet()
    ).sanitized()

    override fun save(ownerKey: String, inventory: FluiBotInventory) {
        store.putString(key(ownerKey, OWNED), inventory.owned.sorted().joinToString(","))
        store.putString(key(ownerKey, EQUIPPED), inventory.equipped.sorted().joinToString(","))
        store.putLong(key(ownerKey, SPENT), inventory.spentGems.toLong())
        store.putString(key(ownerKey, SCENARIOS), inventory.ownedScenarios.sorted().joinToString(","))
        store.putString(key(ownerKey, SCENARIO), inventory.equippedScenario)
        store.putLong(key(ownerKey, BONUS), inventory.bonusGems.toLong())
        // Solo se conservan las misiones de los últimos días: las claves viejas ya no hacen falta.
        store.putString(key(ownerKey, CLAIMED), inventory.claimedMissions.sorted().takeLast(MAX_CLAIMED).joinToString(","))
    }

    private fun String?.toSet(): Set<String> = this?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
    private fun key(owner: String, field: String) = "fluibot.$owner.$field"

    private companion object {
        const val OWNED = "owned"
        const val EQUIPPED = "equipped"
        const val SPENT = "spent"
        const val SCENARIOS = "scenarios"
        const val SCENARIO = "scenario"
        const val BONUS = "bonus"
        const val CLAIMED = "claimed"
        const val MAX_CLAIMED = 60
    }
}
