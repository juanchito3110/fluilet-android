package com.fluilet.app.domain

import com.fluilet.app.data.repository.FluiBotInventoryStore
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.FluiBotInventory
import com.fluilet.app.domain.gamification.PurchaseResult
import com.fluilet.app.testutil.FakeSecureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FluiBotWorkshopTest {

    @Test fun catalogHasExactlyTheTwelveAccessoriesWithTheirPrices() {
        assertEquals(
            listOf("antena_prisma", "visor_holografico", "corbatin_led", "sombrero_explorador", "auriculares_sonicos", "emblema_estelar",
                "jetpack_plasma", "mini_dron", "aura_particulas", "capa_fotonica", "alas_cyber", "corona_cuantica"),
            Accessory.entries.map { it.id }
        )
        assertEquals(listOf(15, 25, 30, 35, 40, 50, 60, 75, 80, 90, 100, 120), Accessory.entries.map { it.cost })
        assertEquals(
            listOf("Antena Prisma Neón", "Visor Holográfico", "Corbatín LED de Gala", "Sombrero de Explorador", "Auriculares Sónicos",
                "Emblema Estelar", "Jetpack de Plasma", "Mini-Dron Satélite", "Aura de Partículas", "Capa Fotónica", "Alas Cyber-Halcón",
                "Corona Cuántica Dorada"),
            Accessory.entries.map { it.displayName }
        )
        assertEquals(720, Accessory.totalCost)
        assertEquals(Accessory.MINI_DRON, Accessory.fromId("mini_dron"))
        assertNull(Accessory.fromId("inexistente"))
    }

    @Test fun welcomeGemsAreEnoughForTheCheapestAccessories() {
        val result = FluiBotInventory.EMPTY.purchase(Accessory.ANTENA_PRISMA, totalGems = 30)
        assertTrue(result is PurchaseResult.Bought)
        val inv = (result as PurchaseResult.Bought).inventory
        assertEquals(15, inv.spentGems)
        assertEquals(15, inv.balance(30))
        assertTrue("al comprar se equipa de inmediato", "antena_prisma" in inv.equipped)
    }

    @Test fun cannotBuyWithoutEnoughGemsAndReportsHowManyAreMissing() {
        val result = FluiBotInventory.EMPTY.purchase(Accessory.CORONA_CUANTICA, totalGems = 30)
        assertEquals(PurchaseResult.NotEnoughGems(90), result)
    }

    @Test fun cannotBuyTwice() {
        val inv = (FluiBotInventory.EMPTY.purchase(Accessory.VISOR_HOLOGRAFICO, 100) as PurchaseResult.Bought).inventory
        assertEquals(PurchaseResult.AlreadyOwned, inv.purchase(Accessory.VISOR_HOLOGRAFICO, 100))
    }

    @Test fun spendingIsAccountedAgainstTheTotalGems() {
        var inv = FluiBotInventory.EMPTY
        inv = (inv.purchase(Accessory.VISOR_HOLOGRAFICO, 130) as PurchaseResult.Bought).inventory // 25
        inv = (inv.purchase(Accessory.CORBATIN_LED, 130) as PurchaseResult.Bought).inventory // 30
        assertEquals(55, inv.spentGems)
        assertEquals(75, inv.balance(130))
        // Con 75 de saldo todavía alcanza el Mini-Dron (75) pero no la Capa (90).
        assertTrue(inv.purchase(Accessory.CAPA_FOTONICA, 130) is PurchaseResult.NotEnoughGems)
        assertTrue(inv.purchase(Accessory.MINI_DRON, 130) is PurchaseResult.Bought)
    }

    @Test fun severalAccessoriesCanBeWornAtTheSameTime() {
        var inv = FluiBotInventory.EMPTY
        val total = 1000
        listOf(Accessory.VISOR_HOLOGRAFICO, Accessory.CORBATIN_LED, Accessory.ALAS_CYBER, Accessory.MINI_DRON, Accessory.CORONA_CUANTICA)
            .forEach { inv = (inv.purchase(it, total) as PurchaseResult.Bought).inventory }
        assertEquals(
            setOf("visor_holografico", "corbatin_led", "alas_cyber", "mini_dron", "corona_cuantica"),
            inv.equipped
        )
    }

    @Test fun equipAndRemoveWorkPerAccessoryWithoutAffectingTheOthers() {
        var inv = FluiBotInventory.EMPTY
        inv = (inv.purchase(Accessory.VISOR_HOLOGRAFICO, 500) as PurchaseResult.Bought).inventory
        inv = (inv.purchase(Accessory.CORBATIN_LED, 500) as PurchaseResult.Bought).inventory

        inv = inv.toggleEquipped(Accessory.VISOR_HOLOGRAFICO) // quitar
        assertEquals(setOf("corbatin_led"), inv.equipped)
        assertTrue("sigue siendo suyo", "visor_holografico" in inv.owned)

        inv = inv.toggleEquipped(Accessory.VISOR_HOLOGRAFICO) // volver a equipar
        assertEquals(setOf("visor_holografico", "corbatin_led"), inv.equipped)
        assertEquals("quitar no devuelve gemas", 55, inv.spentGems)
    }

    @Test fun cannotEquipSomethingNotOwned() {
        val inv = FluiBotInventory.EMPTY
        assertSame(inv, inv.toggleEquipped(Accessory.ALAS_CYBER))
    }

    @Test fun balanceNeverGoesNegative() {
        assertEquals(0, FluiBotInventory(spentGems = 200).balance(totalGems = 50))
        assertThrows(IllegalArgumentException::class.java) { FluiBotInventory(spentGems = -1) }
    }

    @Test fun sanitizingDropsUnknownIdsAndEquippedItemsThatAreNotOwned() {
        val dirty = FluiBotInventory(owned = setOf("mini_dron", "fantasma"), equipped = setOf("mini_dron", "alas_cyber", "fantasma"))
        val clean = dirty.sanitized()
        assertEquals(setOf("mini_dron"), clean.owned)
        assertEquals(setOf("mini_dron"), clean.equipped)
    }

    // --- Persistencia por perfil ----------------------------------------------------------------------

    @Test fun inventoryIsStoredPerChildAndIsolatedBetweenProfiles() {
        val store = FluiBotInventoryStore(FakeSecureStore())
        val mateo = (FluiBotInventory.EMPTY.purchase(Accessory.CORBATIN_LED, 100) as PurchaseResult.Bought).inventory
        store.save("child-mateo", mateo)

        assertEquals(mateo, store.load("child-mateo"))
        assertEquals(FluiBotInventory.EMPTY, store.load("child-lucia"))
        assertEquals(FluiBotInventory.EMPTY, store.load("adult:x@example.com"))
    }

    @Test fun savedInventoryRoundTripsWithEquippedAndSpent() {
        val store = FluiBotInventoryStore(FakeSecureStore())
        var inv = FluiBotInventory.EMPTY
        inv = (inv.purchase(Accessory.MINI_DRON, 500) as PurchaseResult.Bought).inventory
        inv = (inv.purchase(Accessory.CAPA_FOTONICA, 500) as PurchaseResult.Bought).inventory
        inv = inv.toggleEquipped(Accessory.CAPA_FOTONICA)
        store.save("c1", inv)

        val loaded = store.load("c1")
        assertEquals(setOf("mini_dron", "capa_fotonica"), loaded.owned)
        assertEquals(setOf("mini_dron"), loaded.equipped)
        assertEquals(165, loaded.spentGems)
    }

    @Test fun corruptedStoredDataIsSanitizedOnLoad() {
        val fake = FakeSecureStore()
        fake.putString("fluibot.c1.owned", "mini_dron,basura")
        fake.putString("fluibot.c1.equipped", "mini_dron,alas_cyber,basura")
        fake.putLong("fluibot.c1.spent", -50)
        val loaded = FluiBotInventoryStore(fake).load("c1")
        assertEquals(setOf("mini_dron"), loaded.owned)
        assertEquals(setOf("mini_dron"), loaded.equipped)
        assertEquals(0, loaded.spentGems)
    }
}
