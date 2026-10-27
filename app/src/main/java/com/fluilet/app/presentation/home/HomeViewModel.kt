package com.fluilet.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.auth.SessionManager
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.SessionState
import com.fluilet.app.domain.model.EducationalProgress
import com.fluilet.app.domain.model.UserRole
import com.fluilet.app.domain.repository.AuthRepository
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.DeleteChildUseCase
import com.fluilet.app.domain.usecase.GetChildrenUseCase
import com.fluilet.app.domain.usecase.GetEducationalProgressUseCase
import com.fluilet.app.domain.usecase.IsTutorPinConfiguredUseCase
import com.fluilet.app.domain.usecase.LogoutUseCase
import com.fluilet.app.domain.usecase.SetNlpLevelUseCase
import com.fluilet.app.domain.usecase.ClaimDailyMissionUseCase
import com.fluilet.app.domain.usecase.SeedDemoDataUseCase
import com.fluilet.app.domain.demo.DemoData
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.ReadingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val role: UserRole = UserRole.TUTOR,
    val userName: String = "",
    val email: String = "",
    val isLoading: Boolean = true,
    val children: List<ChildProfile> = emptyList(),
    val selectedChildId: String? = null,
    /** Avance educativo del perfil seleccionado (o del adulto): siempre presente, vacío si no hay datos. */
    val progress: EducationalProgress = EducationalProgress.empty(),
    val progressNote: String? = null,
    val selectedTab: HomeTab = HomeTab.DEFAULT,
    val activeReading: ActiveReading? = null,
    val pinConfigured: Boolean = false,
    val errorMessage: String? = null,
    /** Lecturas de "🎓 Cargar Datos Demo" del perfil seleccionado (solo en RAM). */
    val demoLessons: List<AdaptationResult> = emptyList(),
    val isDemoLoading: Boolean = false,
    val demoNote: String? = null
) {
    val selectedChild: ChildProfile? get() = children.firstOrNull { it.id == selectedChildId }

    /** Clave del perfil para los datos de juego (FluiBot): el niño seleccionado o el adulto. */
    val ownerKey: String get() = selectedChildId ?: "adult:$email"
    val isTutor: Boolean get() = role.canManageChildren

    /** La lectura lista pertenece al perfil seleccionado (tutor) o al propio adulto. */
    val readingForSelection: ActiveReading?
        get() = activeReading?.takeIf { it.childId == selectedChildId }
}

private data class HomeLocal(
    val userName: String = "",
    val isLoading: Boolean = true,
    val children: List<ChildProfile> = emptyList(),
    val selectedChildId: String? = null,
    val progress: EducationalProgress = EducationalProgress.empty(),
    val progressNote: String? = null,
    val selectedTab: HomeTab = HomeTab.DEFAULT,
    val pinConfigured: Boolean = false,
    val errorMessage: String? = null,
    /** Perfil dueño de los datos demo cargados y su contenido (RAM): sobrevive a `refresh()` si el servidor no los tiene. */
    val demoOwner: String? = null,
    val demoProgress: EducationalProgress? = null,
    val demoLessons: List<AdaptationResult> = emptyList(),
    val isDemoLoading: Boolean = false,
    val demoNote: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionManager: SessionManager,
    holder: ActiveReadingHolder,
    private val auth: AuthRepository,
    private val getChildren: GetChildrenUseCase,
    private val setNlpLevel: SetNlpLevelUseCase,
    private val deleteChild: DeleteChildUseCase,
    private val getProgress: GetEducationalProgressUseCase,
    private val isPinConfigured: IsTutorPinConfiguredUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val readingHolder: ActiveReadingHolder,
    private val seedDemo: SeedDemoDataUseCase,
    private val claimDailyMission: ClaimDailyMissionUseCase
) : ViewModel() {

    private val local = MutableStateFlow(HomeLocal())

    val uiState: StateFlow<HomeUiState> = combine(sessionManager.state, holder.current, local) { session, reading, l ->
        HomeUiState(
            role = (session as? SessionState.LoggedIn)?.role ?: UserRole.TUTOR,
            userName = l.userName,
            email = (session as? SessionState.LoggedIn)?.email.orEmpty(),
            isLoading = l.isLoading,
            children = l.children,
            selectedChildId = l.selectedChildId,
            progress = l.progress,
            progressNote = l.progressNote,
            selectedTab = l.selectedTab,
            activeReading = reading,
            pinConfigured = l.pinConfigured,
            errorMessage = l.errorMessage,
            demoLessons = if (l.demoOwner != null && l.demoOwner == ownerOf(l, session)) l.demoLessons else emptyList(),
            isDemoLoading = l.isDemoLoading,
            demoNote = l.demoNote
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Recarga datos; se llama al abrir y al volver a primer plano. */
    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(pinConfigured = isPinConfigured(), errorMessage = null) }

            auth.currentUser()
                .onSuccess { user -> local.update { it.copy(userName = user.fullName.substringBefore(' ')) } }

            if (uiState.value.isTutor) {
                getChildren()
                    .onSuccess { list ->
                        local.update { l ->
                            val keep = l.selectedChildId?.takeIf { id -> list.any { it.id == id } }
                            l.copy(children = list, selectedChildId = keep ?: list.firstOrNull()?.id, isLoading = false)
                        }
                    }
                    .onFailure { e -> local.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
            } else {
                local.update { it.copy(isLoading = false) }
            }
            loadProgress()
        }
    }

    /** Cambia de apartado (Lecturas, Mi FluiBot, Colección, Estadísticas). */
    fun selectTab(tab: HomeTab) = local.update { it.copy(selectedTab = tab) }

    fun refreshPin() = local.update { it.copy(pinConfigured = isPinConfigured()) }

    fun select(childId: String) {
        local.update { it.copy(selectedChildId = childId, progress = EducationalProgress.empty(it.children.firstOrNull { c -> c.id == childId }?.nlpLevel?.value ?: 2), progressNote = null) }
        viewModelScope.launch { loadProgress() }
    }

    /**
     * Carga el avance del perfil seleccionado (tutor) o del propio adulto. Nunca deja la pantalla sin
     * estado: si el servidor y el cálculo de respaldo fallan se muestra el resumen vacío con un aviso.
     */
    private suspend fun loadProgress() {
        val isTutor = uiState.value.isTutor
        // Se lee de `local` (no del estado combinado) para no depender de que el combine ya haya emitido.
        val child = if (isTutor) local.value.children.firstOrNull { it.id == local.value.selectedChildId } else null
        if (isTutor && child == null) {
            local.update { it.copy(progress = EducationalProgress.empty(), progressNote = null) }
            return
        }
        val baseline = child?.nlpLevel?.value ?: 2
        getProgress(child?.id, baseline)
            .onSuccess { progress ->
                local.update { l ->
                    // Si el servidor aún no tiene los datos demo (respaldo local), se conservan mientras sean más completos.
                    val demo = l.demoProgress?.takeIf { l.demoOwner == (child?.id ?: ADULT_DEMO_OWNER) && progress.lessons.size < it.lessons.size }
                    l.copy(progress = demo ?: progress, progressNote = null)
                }
            }
            .onFailure {
                local.update {
                    it.copy(
                        progress = EducationalProgress.empty(baseline),
                        progressNote = "No pudimos cargar el avance ahora. Se actualizará cuando haya conexión."
                    )
                }
            }
    }

    fun onNlpLevel(level: NlpLevel) {
        val child = uiState.value.selectedChild ?: return
        if (child.nlpLevel == level) return
        viewModelScope.launch {
            // Optimista: la UI refleja el cambio; si falla se revierte.
            replaceChild(child.copy(nlpLevel = level))
            setNlpLevel(child.id, level)
                .onSuccess(::replaceChild)
                .onFailure { e ->
                    replaceChild(child)
                    local.update { it.copy(errorMessage = e.toAppError().userMessage) }
                }
        }
    }

    private fun replaceChild(updated: ChildProfile) = local.update { l ->
        l.copy(children = l.children.map { if (it.id == updated.id) updated else it })
    }

    fun delete(childId: String) {
        viewModelScope.launch {
            deleteChild(childId)
                .onSuccess {
                    if (readingHolder.current.value?.childId == childId) readingHolder.clear()
                    local.update { l ->
                        val remaining = l.children.filterNot { it.id == childId }
                        l.copy(children = remaining, selectedChildId = remaining.firstOrNull()?.id, progress = EducationalProgress.empty(), progressNote = null)
                    }
                    loadProgress()
                }
                .onFailure { e -> local.update { it.copy(errorMessage = e.toAppError().userMessage) } }
        }
    }

    // --- Datos demo y misiones del servidor ---------------------------------------------------

    /**
     * "🎓 Cargar Datos Demo": aplica de inmediato el respaldo local (7 días, 620 💎 + 95 de misiones, 900 XP y tres
     * lecturas) y, si es un tutor con perfil, pide al backend `POST /profiles/{id}/seed-demo` y lo reemplaza
     * por el resultado real (seis lecturas retenidas). Sin conexión se queda con el respaldo local.
     */
    fun loadDemoData(today: java.time.LocalDate = java.time.LocalDate.now()) {
        val current = uiState.value
        if (current.isDemoLoading) return
        val child = current.selectedChild
        if (current.isTutor && child == null) {
            local.update { it.copy(errorMessage = "Crea un perfil de niño para cargar los datos demo.") }
            return
        }
        val owner = child?.id ?: ADULT_DEMO_OWNER
        val fallback = DemoData.progress(today)
        local.update {
            it.copy(
                progress = fallback, progressNote = null, errorMessage = null,
                demoOwner = owner, demoProgress = fallback, demoLessons = fallback.demoLessons,
                demoNote = "Datos demo locales cargados.", isDemoLoading = child != null
            )
        }
        if (child == null) return
        viewModelScope.launch {
            seedDemo(child.id)
                .onSuccess { server ->
                    local.update { l ->
                        if (l.demoOwner != owner) l.copy(isDemoLoading = false)
                        else l.copy(
                            progress = server, demoProgress = server,
                            demoLessons = server.demoLessons.ifEmpty { l.demoLessons },
                            demoNote = "Datos demo cargados desde el servidor.", isDemoLoading = false
                        )
                    }
                }
                .onFailure {
                    local.update { it.copy(isDemoLoading = false, demoNote = "Datos demo locales (sin conexión con el servidor).") }
                }
        }
    }

    /** Deja una lectura demo en RAM, con las preferencias del perfil, lista para el lector. */
    fun openDemoLesson(lesson: AdaptationResult) {
        val s = uiState.value
        val child = s.selectedChild
        readingHolder.set(
            ActiveReading(
                childId = child?.id,
                readerName = child?.name ?: s.userName.ifBlank { "Lector" },
                settings = child?.toReadingSettings() ?: ReadingSettings(),
                adaptation = lesson,
                ageYears = child?.let { java.time.Period.between(it.birthDate, java.time.LocalDate.now()).years },
                ownerKey = s.ownerKey
            )
        )
    }

    /**
     * Confirma una misión diaria del servidor. El backend ya abonó las gemas al completarla, así que la respuesta
     * (resumen con `total_gems` actualizado) alimenta el contador 💎 de FluiBot sin pagar dos veces.
     */
    fun claimServerMission(missionId: String) {
        val child = uiState.value.selectedChild ?: return
        viewModelScope.launch {
            claimDailyMission(child.id, missionId)
                .onSuccess { p -> local.update { it.copy(progress = p, progressNote = null) } }
                .onFailure { e -> local.update { it.copy(errorMessage = e.toAppError().userMessage) } }
        }
    }

    fun logout() {
        viewModelScope.launch {
            readingHolder.clear()
            logoutUseCase()
        }
    }

    fun consumeError() = local.update { it.copy(errorMessage = null) }

    private fun ownerOf(l: HomeLocal, session: SessionState): String? =
        if ((session as? SessionState.LoggedIn)?.role?.canManageChildren == false) ADULT_DEMO_OWNER else l.selectedChildId

    private companion object {
        const val ADULT_DEMO_OWNER = "adult"
    }
}
