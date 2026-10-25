package com.fluilet.app.presentation

import com.fluilet.app.data.repository.FluiBotInventoryStore
import com.fluilet.app.domain.gamification.Accessory
import com.fluilet.app.domain.gamification.FluiBotStage
import com.fluilet.app.domain.gamification.Gamification
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.presentation.fluibot.FluiBotViewModel
import com.fluilet.app.testutil.FakeSecureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Taller de FluiBot: saldo de gemas, compra con un toque, equipamiento múltiple y botín reclamado. */
class FluiBotViewModelTest {

    private val secure = FakeSecureStore()
    private val store = FluiBotInventoryStore(secure)
    private lateinit var vm: FluiBotViewModel

    /** 130 gemas (30 de bienvenida + 100 ganadas) y 200 XP: etapa 2, Bot Explorador. */
    private val progress = EducationalProgress.empty().copy(totalGems = 130, currentXp = 200)

    @Before fun setUp() {
        vm = FluiBotViewModel(store)
        vm.bind("child-1", progress)
    }

    @Test fun balanceIsTotalGemsMinusSpentAndStageComesFromXp() {
        val s = vm.state.value
        assertEquals(130, s.gems)
        assertEquals(0, s.spentGems)
        assertEquals(FluiBotStage.EXPLORADOR, s.bot.stage)
        assertEquals("200 / 350 XP", s.bot.label)
        assertEquals(10, s.accessories.size)
        assertTrue(s.equipped.isEmpty())
    }

    @Test fun affordabilityFollowsTheBalance() {
        val byId = vm.state.value.accessories.associateBy { it.accessory }
        assertTrue(byId.getValue(Accessory.MINI_DRON).affordable) // 75 <= 130
        assertTrue(byId.getValue(Accessory.ALAS_CYBER).affordable) // 100 <= 130
        assertTrue(byId.getValue(Accessory.CORONA_CUANTICA).affordable) // 120 <= 130
        vm.buy(Accessory.ALAS_CYBER) // saldo 30
        val after = vm.state.value.accessories.associateBy { it.accessory }
        assertFalse(after.getValue(Accessory.CORONA_CUANTICA).affordable)
        assertTrue(after.getValue(Accessory.CORBATIN_LED).affordable) // 30 <= 30
    }

    @Test fun buyingSpendsGemsEquipsAtOnceAndCelebrates() {
        val before = vm.state.value.celebrationTick
        vm.buy(Accessory.MINI_DRON)

        val s = vm.state.value
        assertEquals(55, s.gems)
        assertEquals(75, s.spentGems)
        assertTrue("mini_dron" in s.equipped)
        assertTrue(s.accessories.first { it.accessory == Accessory.MINI_DRON }.owned)
        assertTrue(s.message!!.contains("Mini-Dron"))
        assertEquals(before + 1, s.celebrationTick)
    }

    @Test fun cannotBuyWithoutGemsAndTheBalanceIsUntouched() {
        vm.buy(Accessory.ALAS_CYBER) // 100 -> saldo 30
        vm.buy(Accessory.CORONA_CUANTICA) // 120: faltan 90
        val s = vm.state.value
        assertEquals(30, s.gems)
        assertFalse(s.accessories.first { it.accessory == Accessory.CORONA_CUANTICA }.owned)
        assertTrue(s.message!!.contains("Te faltan 90"))
    }

    @Test fun severalAccessoriesAreEquippedTogetherAndCanBeRemovedOneByOne() {
        vm.bind("child-1", progress.copy(totalGems = 1000))
        listOf(Accessory.VISOR_HOLOGRAFICO, Accessory.CORBATIN_LED, Accessory.ALAS_CYBER, Accessory.MINI_DRON, Accessory.CORONA_CUANTICA)
            .forEach(vm::buy)
        assertEquals(5, vm.state.value.equipped.size)

        vm.toggleEquip(Accessory.ALAS_CYBER)

        assertEquals(setOf("visor_holografico", "corbatin_led", "mini_dron", "corona_cuantica"), vm.state.value.equipped)
        assertNull(vm.state.value.message)
        vm.toggleEquip(Accessory.ALAS_CYBER)
        assertTrue("alas_cyber" in vm.state.value.equipped)
    }

    @Test fun equippingSomethingNotOwnedDoesNothing() {
        vm.toggleEquip(Accessory.JETPACK_PLASMA)
        assertTrue(vm.state.value.equipped.isEmpty())
    }

    @Test fun inventoryPersistsAcrossViewModelsPerChild() {
        vm.buy(Accessory.VISOR_HOLOGRAFICO)
        vm.buy(Accessory.CORBATIN_LED)

        val reopened = FluiBotViewModel(store).apply { bind("child-1", progress) }
        assertEquals(setOf("visor_holografico", "corbatin_led"), reopened.state.value.equipped)
        assertEquals(75, reopened.state.value.gems) // 130 - 25 - 30

        val sibling = FluiBotViewModel(store).apply { bind("child-2", progress) }
        assertTrue(sibling.state.value.equipped.isEmpty())
        assertEquals(130, sibling.state.value.gems)
    }

    @Test fun claimedLootAddsGemsAndXpButUnclaimedLootDoesNot() {
        val loot = Gamification.lootFor(completed = true, correctAnswers = 2, categoryCode = "deportes", before = progress)

        vm.bind("child-1", progress, claimedLoot = null)
        assertEquals(130, vm.state.value.gems)

        vm.bind("child-1", progress, claimedLoot = loot)
        assertEquals(230, vm.state.value.gems) // +50 +2 x 25
        assertEquals(400, vm.state.value.bot.currentXp) // 200 + 200
        assertEquals("el botín puede hacer evolucionar a FluiBot", FluiBotStage.GUARDIAN, vm.state.value.bot.stage)
    }

    @Test fun rebindingTheSameOwnerKeepsTheInventory() {
        vm.buy(Accessory.EMBLEMA_ESTELAR)
        vm.bind("child-1", progress.copy(totalGems = 400))
        assertTrue("emblema_estelar" in vm.state.value.equipped)
        assertEquals(350, vm.state.value.gems)
    }

    @Test fun celebrateRaisesTheTickSoFluiBotReacts() {
        val before = vm.state.value.celebrationTick
        vm.celebrate()
        vm.celebrate()
        assertEquals(before + 2, vm.state.value.celebrationTick)
    }

    @Test fun cardsAndBadgesComeFromTheProgress() {
        assertEquals(6, vm.state.value.cards.size)
        assertEquals(5, vm.state.value.badges.size)
        assertNotNull(vm.state.value.cards.firstOrNull { it.categoryCode == "ciencia_espacio" })
    }

    @Test fun messagesCanBeDismissed() {
        vm.buy(Accessory.ANTENA_PRISMA)
        assertNotNull(vm.state.value.message)
        vm.consumeMessage()
        assertNull(vm.state.value.message)
    }
}
