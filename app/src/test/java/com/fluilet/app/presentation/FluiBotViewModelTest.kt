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
        assertEquals(12, s.accessories.size)
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

    @Test fun equippingAnAccessoryMakesTheLivePreviewCelebrateButRemovingItDoesNot() {
        vm.buy(Accessory.SOMBRERO_EXPLORADOR) // 35: compra y se lo pone
        val afterBuy = vm.state.value.celebrationTick
        vm.toggleEquip(Accessory.SOMBRERO_EXPLORADOR) // quitar
        assertEquals(afterBuy, vm.state.value.celebrationTick)
        assertFalse("sombrero_explorador" in vm.state.value.equipped)
        vm.toggleEquip(Accessory.SOMBRERO_EXPLORADOR) // volver a ponerlo
        assertEquals(afterBuy + 1, vm.state.value.celebrationTick)
        assertTrue("sombrero_explorador" in vm.state.value.equipped)
    }

    @Test fun profileCardsReadEachChildsEquippedAccessories() {
        vm.buy(Accessory.CORBATIN_LED)
        val other = FluiBotViewModel(store).apply { bind("child-2", progress.copy(totalGems = 500)) }
        other.buy(Accessory.AURA_PARTICULAS)
        assertEquals(setOf("aura_particulas"), vm.equippedFor("child-2"))
        assertEquals(setOf("corbatin_led"), vm.equippedFor("child-1"))
        assertTrue(vm.equippedFor("child-sin-taller").isEmpty())
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

    // --- Escenarios y misiones diarias -----------------------------------------------------

    private val day = java.time.LocalDate.of(2026, 10, 25)

    private fun lessonToday(correct: Int = 2, seconds: Long = 320) = com.fluilet.app.domain.model.LessonProgress(
        1, "r1", java.time.Instant.parse("2026-10-25T12:00:00Z"), "deportes", 2, true, 100, 100, seconds, 30.0, 70.0, correct
    )

    private fun withClock(date: java.time.LocalDate = day) {
        vm.today = { date }
        vm.zone = java.time.ZoneOffset.UTC
    }

    @Test fun theLaboratoryIsTheFreeDefaultAndAllFiveScenariosAreListed() {
        val s = vm.state.value
        assertEquals(com.fluilet.app.domain.gamification.Scenario.LABORATORIO_CIAN, s.scenario)
        assertEquals(5, s.scenarios.size)
        assertTrue(s.scenarios.first().owned && s.scenarios.first().equipped)
        assertTrue(s.scenarios.all { it.affordable }) // 130 de saldo alcanza para los cinco (máximo 60)
    }

    @Test fun buyingAScenarioSpendsGemsEquipsItAndCelebrates() {
        val before = vm.state.value.celebrationTick
        vm.buyScenario(com.fluilet.app.domain.gamification.Scenario.ESTACION_ESPACIAL)

        val s = vm.state.value
        assertEquals(110, s.gems)
        assertEquals(com.fluilet.app.domain.gamification.Scenario.ESTACION_ESPACIAL, s.scenario)
        assertTrue(s.scenarios.first { it.scenario == com.fluilet.app.domain.gamification.Scenario.ESTACION_ESPACIAL }.equipped)
        assertTrue(s.message!!.contains("Estación Espacial Estelar"))
        assertEquals(before + 1, s.celebrationTick)
    }

    @Test fun aScenarioCannotBeBoughtWithoutGemsAndYouCanSwitchBackForFree() {
        vm.bind("child-1", progress.copy(totalGems = 30))
        vm.buyScenario(com.fluilet.app.domain.gamification.Scenario.ESTADIO_CAMPEON) // 45: faltan 15
        assertEquals(30, vm.state.value.gems)
        assertTrue(vm.state.value.message!!.contains("Te faltan 15"))
        assertEquals(com.fluilet.app.domain.gamification.Scenario.LABORATORIO_CIAN, vm.state.value.scenario)

        vm.buyScenario(com.fluilet.app.domain.gamification.Scenario.OCEANO_SUBMARINO) // 30: alcanza
        assertEquals(0, vm.state.value.gems)
        vm.equipScenario(com.fluilet.app.domain.gamification.Scenario.LABORATORIO_CIAN)
        assertEquals(com.fluilet.app.domain.gamification.Scenario.LABORATORIO_CIAN, vm.state.value.scenario)
    }

    @Test fun theChosenScenarioIsRememberedPerProfile() {
        vm.buyScenario(com.fluilet.app.domain.gamification.Scenario.BOSQUE_CYBER_NEON)
        val again = FluiBotViewModel(store).also { it.bind("child-1", progress) }
        assertEquals(com.fluilet.app.domain.gamification.Scenario.BOSQUE_CYBER_NEON, again.state.value.scenario)
        val other = FluiBotViewModel(store).also { it.bind("child-2", progress) }
        assertEquals(com.fluilet.app.domain.gamification.Scenario.LABORATORIO_CIAN, other.state.value.scenario)
    }

    @Test fun threeDailyMissionsStartPendingAndCannotBeClaimed() {
        withClock()
        vm.bind("child-1", progress)
        val missions = vm.state.value.missions
        assertEquals(3, missions.size)
        assertTrue(missions.none { it.canClaim || it.claimed })
        vm.claimMission("leer_leccion")
        assertEquals(130, vm.state.value.gems)
    }

    @Test fun completedMissionsAreClaimedOnceAndPayTheirGems() {
        withClock()
        vm.bind("child-1", progress.copy(lessons = listOf(lessonToday())))
        assertTrue(vm.state.value.missions.all { it.canClaim })

        vm.claimMission("leer_leccion")
        vm.claimMission("leer_leccion") // duplicado: no vuelve a pagar
        vm.claimMission("respuestas_correctas")
        vm.claimMission("practicar_tiempo")

        val s = vm.state.value
        assertEquals(130 + 25 + 30 + 40, s.gems)
        assertTrue(s.missions.all { it.claimed && !it.canClaim })
    }

    @Test fun missionsRenewTheNextDayButKeepTheEarnedGems() {
        withClock()
        vm.bind("child-1", progress.copy(lessons = listOf(lessonToday())))
        vm.claimMission("leer_leccion")

        withClock(day.plusDays(1))
        vm.bind("child-1", progress.copy(lessons = listOf(lessonToday())))

        val s = vm.state.value
        assertEquals(155, s.gems)
        assertTrue(s.missions.none { it.claimed || it.mission.current > 0 })
    }

    @Test fun serverMissionsTakePrecedenceOverTheLocalOnes() {
        withClock()
        val server = com.fluilet.app.domain.gamification.DailyMission("m_servidor", "Misión del servidor", "d", 2, 2, 15)
        vm.bind("child-1", progress.copy(serverMissions = listOf(server)))
        val missions = vm.state.value.missions
        assertEquals(listOf("m_servidor"), missions.map { it.mission.id })
        assertTrue(missions.single().canClaim)
    }

    @Test fun theSessionBeingReadCountsTowardsTheMissions() {
        withClock()
        vm.bind("child-1", progress, session = com.fluilet.app.domain.gamification.SessionContribution(true, 2, 310))
        assertTrue(vm.state.value.missions.all { it.canClaim })
    }

    // --- Misiones del servidor (backend f6f09d9): ya abonadas, sin pago local duplicado ---------

    private fun serverMission(claimed: Boolean, done: Boolean = true) = com.fluilet.app.domain.gamification.DailyMission(
        "mission_read_lesson", "Explorador del Día", "d", if (done) 1 else 0, 1, 25, fromServer = true, claimed = claimed
    )

    @Test fun aServerMissionAlreadyClaimedByTheBackendShowsAsClaimedAndPaysNothingLocally() {
        withClock()
        vm.bind("child-1", progress.copy(serverMissions = listOf(serverMission(claimed = true))))
        val ui = vm.state.value.missions.single()
        assertTrue(ui.claimed)
        assertFalse(ui.canClaim)
        vm.claimMission("mission_read_lesson")
        assertEquals(130, vm.state.value.gems) // las gemas de la misión ya están en `total_gems`
    }

    @Test fun anUnclaimedServerMissionIsNeverPaidByTheDeviceItIsConfirmedWithTheEndpoint() {
        withClock()
        vm.bind("child-1", progress.copy(serverMissions = listOf(serverMission(claimed = false))))
        assertTrue(vm.state.value.missions.single().canClaim)
        vm.claimMission("mission_read_lesson")
        assertEquals(130, vm.state.value.gems)
        assertFalse(vm.state.value.missions.single().claimed)
    }

    @Test fun theGemCounterFollowsTheServersTotalAfterTheMissionIsConfirmed() {
        withClock()
        vm.bind("child-1", progress.copy(serverMissions = listOf(serverMission(claimed = false))))
        vm.buy(Accessory.CORBATIN_LED) // 30 gastadas
        // El resumen que devuelve `.../claim` trae el abono (+25) y la misión reclamada.
        vm.bind("child-1", progress.copy(totalGems = 155, serverMissions = listOf(serverMission(claimed = true))))
        assertEquals(125, vm.state.value.gems) // 155 - 30 gastadas
        assertTrue(vm.state.value.missions.single().claimed)
    }

    // --- Claim sin conexión: abono provisional que se concilia con el backend --------------------

    @Test fun whenTheBackendIsDownAnUnclaimedServerMissionIsPaidProvisionallyOnce() {
        withClock()
        vm.bind("child-1", progress.copy(serverMissions = listOf(serverMission(claimed = false))))

        vm.claimMissionOffline("mission_read_lesson")
        vm.claimMissionOffline("mission_read_lesson") // no se duplica

        val s = vm.state.value
        assertEquals(155, s.gems)
        assertTrue(s.missions.single().claimed)
        assertTrue(s.message!!.contains("provisionales"))
    }

    @Test fun theProvisionalRewardIsRemovedWhenTheServerReportsTheMissionAsClaimed() {
        withClock()
        vm.bind("child-1", progress.copy(serverMissions = listOf(serverMission(claimed = false))))
        vm.claimMissionOffline("mission_read_lesson")
        assertEquals(155, vm.state.value.gems)

        // Vuelve la conexión: el servidor ya abonó la misión dentro de `total_gems` (130 + 25).
        vm.bind("child-1", progress.copy(totalGems = 155, serverMissions = listOf(serverMission(claimed = true))))

        assertEquals("sin duplicar: 155, no 180", 155, vm.state.value.gems)
        assertTrue(vm.state.value.missions.single().claimed)
        // Y queda persistido: un ViewModel nuevo tampoco suma el premio provisional.
        val again = FluiBotViewModel(store).also {
            it.today = { day }
            it.zone = java.time.ZoneOffset.UTC
            it.bind("child-1", progress.copy(totalGems = 155, serverMissions = listOf(serverMission(claimed = true))))
        }
        assertEquals(155, again.state.value.gems)
    }

    @Test fun anIncompleteServerMissionCannotBeClaimedOffline() {
        withClock()
        vm.bind("child-1", progress.copy(serverMissions = listOf(serverMission(claimed = false, done = false))))
        vm.claimMissionOffline("mission_read_lesson")
        assertEquals(130, vm.state.value.gems)
    }
}
