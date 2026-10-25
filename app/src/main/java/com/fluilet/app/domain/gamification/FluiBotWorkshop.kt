package com.fluilet.app.domain.gamification

/** Los 10 accesorios económicos y combinables del Taller de FluiBot. */
enum class Accessory(val id: String, val displayName: String, val cost: Int, val description: String) {
    ANTENA_PRISMA("antena_prisma", "Antena Prisma Neón", 15, "Cristal multicolor brillante en la punta de su antena."),
    VISOR_HOLOGRAFICO("visor_holografico", "Visor Holográfico", 25, "Lente futurista neón translúcido sobre sus ojos LED."),
    CORBATIN_LED("corbatin_led", "Corbatín LED de Gala", 30, "Moño geométrico iluminado en el cuello."),
    AURICULARES_SONICOS("auriculares_sonicos", "Auriculares Sónicos", 40, "Audífonos futuristas con ondas sonoras."),
    EMBLEMA_ESTELAR("emblema_estelar", "Emblema Estelar", 50, "Insignia de estrella dorada resplandeciente en el pecho."),
    JETPACK_PLASMA("jetpack_plasma", "Jetpack de Plasma", 60, "Propulsores con llamas azules y naranjas animadas."),
    MINI_DRON("mini_dron", "Mini-Dron Satélite", 75, "Pequeño dron esférico que orbita a FluiBot."),
    CAPA_FOTONICA("capa_fotonica", "Capa Fotónica", 90, "Capa de energía luminosa que ondea detrás del chasis."),
    ALAS_CYBER("alas_cyber", "Alas Cyber-Halcón", 100, "Alas tecnológicas desplegadas a ambos lados."),
    CORONA_CUANTICA("corona_cuantica", "Corona Cuántica Dorada", 120, "Corona holográfica dorada flotando sobre su cabeza.");

    companion object {
        fun fromId(id: String): Accessory? = entries.firstOrNull { it.id == id }
        val totalCost: Int get() = entries.sumOf { it.cost }
    }
}

sealed interface PurchaseResult {
    data class Bought(val inventory: FluiBotInventory) : PurchaseResult
    data class NotEnoughGems(val missing: Int) : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data object UnknownItem : PurchaseResult
}

/**
 * Inventario del taller por perfil: accesorios comprados, equipados (varios a la vez) y gemas gastadas.
 * Es una preferencia local del juego (no contiene datos personales ni texto de lectura).
 */
data class FluiBotInventory(
    val owned: Set<String> = emptySet(),
    val equipped: Set<String> = emptySet(),
    val spentGems: Int = 0
) {
    init {
        require(spentGems >= 0) { "spentGems no puede ser negativo" }
    }

    /** Saldo disponible: gemas totales del niño menos las gastadas en el taller. */
    fun balance(totalGems: Int): Int = (totalGems - spentGems).coerceAtLeast(0)

    fun purchase(accessory: Accessory, totalGems: Int): PurchaseResult {
        if (accessory.id in owned) return PurchaseResult.AlreadyOwned
        val balance = balance(totalGems)
        if (balance < accessory.cost) return PurchaseResult.NotEnoughGems(accessory.cost - balance)
        // Al comprar se equipa de inmediato; el resto de lo equipado se conserva.
        return PurchaseResult.Bought(
            copy(owned = owned + accessory.id, equipped = equipped + accessory.id, spentGems = spentGems + accessory.cost)
        )
    }

    /** Equipa o quita un accesorio ya comprado. Se pueden llevar varios a la vez. */
    fun toggleEquipped(accessory: Accessory): FluiBotInventory {
        if (accessory.id !in owned) return this
        return copy(equipped = if (accessory.id in equipped) equipped - accessory.id else equipped + accessory.id)
    }

    /** Descarta ids desconocidos y garantiza que lo equipado esté comprado (datos guardados corruptos). */
    fun sanitized(): FluiBotInventory {
        val known = owned.filter { Accessory.fromId(it) != null }.toSet()
        return copy(owned = known, equipped = equipped.filter { it in known }.toSet())
    }

    companion object {
        val EMPTY = FluiBotInventory()
    }
}

/** Persistencia del inventario por perfil (`ownerKey` = id del niño o clave del adulto). */
interface FluiBotInventoryRepository {
    fun load(ownerKey: String): FluiBotInventory
    fun save(ownerKey: String, inventory: FluiBotInventory)
}
