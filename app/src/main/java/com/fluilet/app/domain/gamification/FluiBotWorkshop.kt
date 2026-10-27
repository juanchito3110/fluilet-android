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

/** Los 5 escenarios de FluiBot (fondos dibujados en `Canvas` detrás del robot). El primero es gratuito. */
enum class Scenario(val id: String, val displayName: String, val cost: Int, val description: String) {
    LABORATORIO_CIAN("laboratorio_cian", "Laboratorio Cian", 0, "El taller tecnológico donde nació FluiBot."),
    ESTACION_ESPACIAL("estacion_espacial", "Estación Espacial Estelar", 20, "Estrellas titilantes y un planeta con anillos."),
    OCEANO_SUBMARINO("oceano_submarino", "Océano Submarino", 30, "Burbujas que suben y ondas marinas."),
    ESTADIO_CAMPEON("estadio_campeon", "Estadio Campeón", 45, "Reflectores y líneas de cancha luminosas."),
    BOSQUE_CYBER_NEON("bosque_cyber_neon", "Bosque Cyber-Neón", 60, "Luciérnagas y una aurora de geometría neón.");

    val isFree: Boolean get() = cost == 0

    companion object {
        val DEFAULT = LABORATORIO_CIAN
        fun fromId(id: String?): Scenario? = entries.firstOrNull { it.id == id }
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
    val spentGems: Int = 0,
    /** Escenarios comprados (el Laboratorio Cian siempre es suyo) y el que tiene puesto. */
    val ownedScenarios: Set<String> = emptySet(),
    val equippedScenario: String = Scenario.DEFAULT.id,
    /** Gemas ganadas con misiones diarias (se guardan localmente; el servidor no las conoce). */
    val bonusGems: Int = 0,
    /** Misiones ya reclamadas, con clave `fecha:id` para que se renueven cada día. */
    val claimedMissions: Set<String> = emptySet()
) {
    init {
        require(spentGems >= 0) { "spentGems no puede ser negativo" }
        require(bonusGems >= 0) { "bonusGems no puede ser negativo" }
    }

    /** Saldo disponible: gemas totales del niño + premios de misiones - gastadas en el taller. */
    fun balance(totalGems: Int): Int = (totalGems + bonusGems - spentGems).coerceAtLeast(0)

    val scenario: Scenario get() = Scenario.fromId(equippedScenario)?.takeIf { ownsScenario(it) } ?: Scenario.DEFAULT

    fun ownsScenario(scenario: Scenario): Boolean = scenario.isFree || scenario.id in ownedScenarios

    /** Compra un escenario y lo pone de inmediato. */
    fun purchaseScenario(scenario: Scenario, totalGems: Int): PurchaseResult {
        if (ownsScenario(scenario)) return PurchaseResult.AlreadyOwned
        val balance = balance(totalGems)
        if (balance < scenario.cost) return PurchaseResult.NotEnoughGems(scenario.cost - balance)
        return PurchaseResult.Bought(
            copy(ownedScenarios = ownedScenarios + scenario.id, equippedScenario = scenario.id, spentGems = spentGems + scenario.cost)
        )
    }

    /** Pone un escenario ya comprado (o el gratuito). Solo uno a la vez. */
    fun equipScenario(scenario: Scenario): FluiBotInventory =
        if (ownsScenario(scenario)) copy(equippedScenario = scenario.id) else this

    /** Reclama el premio de una misión completada, una sola vez por día. */
    fun claimMission(mission: DailyMission, claimKey: String): FluiBotInventory {
        if (!mission.completed || claimKey in claimedMissions) return this
        return copy(bonusGems = bonusGems + mission.rewardGems, claimedMissions = claimedMissions + claimKey)
    }

    /**
     * Retira un premio local de misión cuando el servidor ya lo abonó (`claimed` en `daily_missions`): evita sumar dos
     * veces las gemas que se dieron de forma provisional mientras el backend no estaba disponible.
     */
    fun revokeClaim(mission: DailyMission, claimKey: String): FluiBotInventory {
        if (claimKey !in claimedMissions) return this
        return copy(bonusGems = (bonusGems - mission.rewardGems).coerceAtLeast(0), claimedMissions = claimedMissions - claimKey)
    }

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
        val scenarios = ownedScenarios.filter { Scenario.fromId(it) != null }.toSet()
        val current = Scenario.fromId(equippedScenario)?.takeIf { it.isFree || it.id in scenarios } ?: Scenario.DEFAULT
        return copy(owned = known, equipped = equipped.filter { it in known }.toSet(), ownedScenarios = scenarios, equippedScenario = current.id)
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
