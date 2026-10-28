package com.fluilet.app.presentation

import com.fluilet.app.core.network.AppError
import com.fluilet.app.domain.model.AdminOverview
import com.fluilet.app.domain.model.AdminProfile
import com.fluilet.app.domain.model.AdminUser
import com.fluilet.app.domain.model.MasterGrant
import com.fluilet.app.domain.model.PinResetResult
import com.fluilet.app.domain.repository.AdminRepository
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.usecase.GetModelAuditUseCase
import com.fluilet.app.domain.usecase.LogoutUseCase
import com.fluilet.app.testutil.FakeModelInfoRepository
import com.fluilet.app.presentation.admin.AdminTab
import com.fluilet.app.presentation.admin.AdminViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Sistema de Control Maestro: KPIs, otorgamiento, bajas, reset-pin y restauración de cuentas demo. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val admin = mockk<AdminRepository>()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val modelInfo = FakeModelInfoRepository()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun profile(id: String, name: String, level: Int, stage: Int, gems: Int, xp: Int, group: String = "9-11") =
        AdminProfile(id, name, "u-tutor", "tutor@fluilet.com", true, group, level, gems, xp, stage, "Etapa $stage", 3, 3, 2)

    private val master = AdminUser("u-admin", "admin@fluilet.com", "master_admin", true, true, 0)
    private val tutor = AdminUser("u-tutor", "tutor@fluilet.com", "TUTOR", false, true, 4)
    private val manual = AdminUser("u-manual", "mama@example.com", "TUTOR", false, true, 1)

    private val fourKids = listOf(
        profile("p1", "Sofía", 1, 1, 45, 40, "6-8"),
        profile("p2", "Mateo", 2, 2, 160, 190),
        profile("p3", "Diego", 4, 3, 340, 460),
        profile("p4", "Valentina", 5, 4, 900, 980, "12+")
    )

    private fun overview(profiles: List<AdminProfile> = fourKids, users: List<AdminUser> = listOf(master, tutor, manual)) =
        AdminOverview(2, profiles.size, 24, 15, 82.5, users, profiles)

    private fun viewModel(initial: AdminOverview = overview()): AdminViewModel {
        coEvery { admin.overview() } returns Result.success(initial)
        return AdminViewModel(admin, LogoutUseCase(auth), GetModelAuditUseCase(modelInfo))
    }

    // --- overview ------------------------------------------------------------------------------------------

    @Test fun openingLoadsTheKpisChildrenAndTutors() = runTest(dispatcher) {
        val vm = viewModel()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(2, s.overview.totalTutors)
        assertEquals(4, s.overview.totalProfiles)
        assertEquals(24, s.overview.totalAdaptations)
        assertEquals(82.5, s.overview.averageComprehension, 0.0)
        assertEquals(AdminTab.CHILDREN, s.tab)
        assertEquals(listOf("Sofía", "Mateo", "Diego", "Valentina"), s.overview.profiles.map { it.name })
        assertEquals(listOf("tutor@fluilet.com", "mama@example.com"), s.overview.tutors.map { it.email })
    }

    @Test fun thereAreTwoSectionsAndTheyCanBeSwitched() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(listOf("👦 Gestión y Otorgamiento a Niños", "👤 Gestión de Usuarios / Tutores"), AdminTab.entries.map { "${it.emoji} ${it.label}" })
        vm.selectTab(AdminTab.TUTORS)
        assertEquals(AdminTab.TUTORS, vm.state.value.tab)
    }

    @Test fun anOverviewFailureIsShownAndCanBeRetried() = runTest(dispatcher) {
        coEvery { admin.overview() } returns Result.failure(AppError.Forbidden())
        val vm = AdminViewModel(admin, LogoutUseCase(auth), GetModelAuditUseCase(modelInfo))
        assertTrue(vm.state.value.errorMessage!!.contains("permisos"))
        assertFalse(vm.state.value.isLoading)

        coEvery { admin.overview() } returns Result.success(overview())
        vm.refresh()
        assertEquals(4, vm.state.value.overview.profiles.size)
        assertNull(vm.state.value.errorMessage)
    }

    // --- grant ---------------------------------------------------------------------------------------------

    private fun captureGrant(): io.mockk.CapturingSlot<MasterGrant> {
        val slot = slot<MasterGrant>()
        coEvery { admin.grant("p2", capture(slot)) } returns Result.success(com.fluilet.app.domain.model.EducationalProgress.empty())
        return slot
    }

    @Test fun quickGemButtonsGrantFiftyTwoHundredAndFiveHundredAndRefreshTheList() = runTest(dispatcher) {
        val vm = viewModel()
        val slot = captureGrant()
        MasterGrant.QUICK_GEMS.forEach { amount ->
            vm.grant("p2", MasterGrant.gems(amount), "+$amount 💎")
            assertEquals(amount, slot.captured.addGems)
            assertEquals("+$amount 💎", vm.state.value.message)
            assertFalse(vm.state.value.isBusy)
        }
        coVerify(exactly = 4) { admin.overview() } // carga inicial + una lectura por cada grant
    }

    @Test fun anExactAmountSetsTheBalanceWithoutAddingGems() = runTest(dispatcher) {
        val vm = viewModel()
        val slot = captureGrant()
        vm.grant("p2", MasterGrant.exactGems(1000))
        assertEquals(1000, slot.captured.setGems)
        assertNull(slot.captured.addGems)
    }

    @Test fun eachEvolutionStageXpLevelAgeAndUnlockAllIsSentToTheBackend() = runTest(dispatcher) {
        val vm = viewModel()
        val slot = captureGrant()
        (1..4).forEach { stage ->
            vm.grant("p2", MasterGrant.stage(stage))
            assertEquals(stage, slot.captured.setEvolutionStage)
        }
        vm.grant("p2", MasterGrant.xp())
        assertEquals(100, slot.captured.addXp)
        (1..5).forEach { level ->
            vm.grant("p2", MasterGrant.level(level))
            assertEquals(level, slot.captured.setLevel)
        }
        MasterGrant.AGE_GROUPS.forEach { group ->
            vm.grant("p2", MasterGrant.ageGroup(group))
            assertEquals(group, slot.captured.setAgeGroup)
        }
        vm.grant("p2", MasterGrant.unlockAll())
        assertTrue(slot.captured.unlockAllRewards)
    }

    @Test fun aRejectedGrantShowsTheServersMessage() = runTest(dispatcher) {
        val vm = viewModel()
        coEvery { admin.grant("p2", any()) } returns Result.failure(AppError.Validation("El saldo resultante debe estar entre 0 y 1000000000."))
        vm.grant("p2", MasterGrant.gems(-999))
        assertTrue(vm.state.value.errorMessage!!.contains("saldo"))
        assertFalse(vm.state.value.isBusy)
    }

    // --- bajas ----------------------------------------------------------------------------------------------

    @Test fun removingAChildCallsDeleteAndShowsTheListWithoutThem() = runTest(dispatcher) {
        val vm = viewModel()
        coEvery { admin.deleteProfile("p3") } returns Result.success(Unit)
        coEvery { admin.overview() } returns Result.success(overview(fourKids.filterNot { it.profileId == "p3" }))

        vm.deleteProfile("p3")

        coVerify { admin.deleteProfile("p3") }
        assertEquals(listOf("Sofía", "Mateo", "Valentina"), vm.state.value.overview.profiles.map { it.name })
        assertEquals("Niño dado de baja.", vm.state.value.message)
    }

    @Test fun removingATutorCallsDeleteAndRefreshes() = runTest(dispatcher) {
        val vm = viewModel()
        coEvery { admin.deleteTutor("u-manual") } returns Result.success(Unit)
        coEvery { admin.overview() } returns Result.success(overview(users = listOf(master, tutor)))

        vm.deleteTutor("u-manual")

        coVerify { admin.deleteTutor("u-manual") }
        assertEquals(listOf("tutor@fluilet.com"), vm.state.value.overview.tutors.map { it.email })
    }

    @Test fun theMasterAccountCanNeverBeRemovedAndTheBackendIsNotEvenCalled() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(master.canBeDeleted)
        vm.deleteTutor("u-admin")
        coVerify(exactly = 0) { admin.deleteTutor(any()) }
        assertTrue(vm.state.value.errorMessage!!.contains("maestra"))
    }

    // --- reset-pin y restauración ----------------------------------------------------------------------------

    @Test fun resetPinAlwaysSendsThePublicDefault1234() = runTest(dispatcher) {
        val vm = viewModel()
        coEvery { admin.resetPin("u-tutor", "1234") } returns Result.success(PinResetResult("u-tutor", true))
        vm.resetPin("u-tutor")
        coVerify { admin.resetPin("u-tutor", "1234") }
        assertEquals("PIN restablecido a 1234.", vm.state.value.message)
    }

    @Test fun restoringTheDemoAccountsBringsBackTheTutorAndHisFourChildren() = runTest(dispatcher) {
        val vm = viewModel(overview(profiles = emptyList(), users = listOf(master)))
        assertTrue(vm.state.value.overview.profiles.isEmpty())
        coEvery { admin.ensureDemoAccounts() } returns Result.success(overview())

        vm.restoreDemoAccounts()

        coVerify(exactly = 1) { admin.ensureDemoAccounts() }
        assertEquals(4, vm.state.value.overview.profiles.size)
        assertTrue(vm.state.value.overview.tutors.any { it.email == "tutor@fluilet.com" })
        assertTrue(vm.state.value.message!!.contains("4 niños"))
    }

    @Test fun anOperationInProgressBlocksAnotherOne() = runTest(dispatcher) {
        val vm = viewModel()
        coEvery { admin.deleteProfile(any()) } coAnswers { kotlinx.coroutines.delay(1_000); Result.success(Unit) }
        vm.deleteProfile("p1")
        // UnconfinedTestDispatcher: la primera operación queda suspendida en el delay.
        assertTrue(vm.state.value.isBusy)
        vm.deleteProfile("p2")
        coVerify(exactly = 1) { admin.deleteProfile(any()) }
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.isBusy)
    }

    @Test fun logoutRevokesTheSessionSoTheLoginScreenCanBeShownAgain() = runTest(dispatcher) {
        val vm = viewModel()
        vm.logout()
        coVerify(exactly = 1) { auth.logout() }
    }

    // --- 🧠 Auditoría del modelo reentrenado ---------------------------------------------------------------

    @Test fun theMasterPanelShowsTheAuditThatComesWithTheOverview() = runTest(dispatcher) {
        val audit = com.fluilet.app.domain.model.ModelAudit.VERIFIED_42482E0.copy(fromServer = true)
        val vm = viewModel(overview().copy(modelAudit = audit))
        assertEquals(audit, vm.state.value.modelAudit)
        assertTrue(vm.state.value.modelAudit!!.acceptanceCriteriaMet)
        assertEquals("sin segunda consulta a model-info", 0, modelInfo.calls)
    }

    @Test fun withoutModelInfoInTheOverviewItAsksModelInfoOrUsesTheVerifiedValues() = runTest(dispatcher) {
        val vm = viewModel(overview())
        assertEquals(1, modelInfo.calls)
        assertEquals("4,738", vm.state.value.modelAudit!!.datasetPairs)

        coEvery { admin.overview() } returns Result.failure(AppError.NoConnection())
        modelInfo.audit = com.fluilet.app.domain.model.ModelAudit.VERIFIED_42482E0
        val offline = AdminViewModel(admin, LogoutUseCase(auth), GetModelAuditUseCase(modelInfo))
        assertFalse(offline.state.value.modelAudit!!.fromServer)
        assertEquals("73.25%", offline.state.value.modelAudit!!.lexicalAccuracyText)
    }
}
