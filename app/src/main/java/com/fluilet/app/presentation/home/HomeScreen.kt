package com.fluilet.app.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.FullScreenLoading
import com.fluilet.app.presentation.fluibot.FluiBotViewModel
import com.fluilet.app.presentation.parentalgate.GateMode
import com.fluilet.app.presentation.parentalgate.ParentalGateDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.fluilet.app.presentation.common.FitText

internal enum class PinDialog { NONE, SETUP_FOR_START, SETUP, CHANGE }

/**
 * Pantalla principal: 4 apartados independientes en una barra de navegación inferior (Material 3).
 * Lecturas · Mi FluiBot · Colección y Misiones · Estadísticas.
 */
@Composable
fun HomeScreen(
    onNewChild: () -> Unit,
    onEditChild: (String) -> Unit,
    onStartReading: () -> Unit,
    onShowIntro: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    botViewModel: FluiBotViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bot by botViewModel.state.collectAsStateWithLifecycle()
    var pinDialog by remember { mutableStateOf(PinDialog.NONE) }
    var confirmDelete by remember { mutableStateOf<ChildProfile?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    // FluiBot, Colección y Estadísticas comparten el mismo inventario del perfil seleccionado.
    LaunchedEffect(state.ownerKey, state.progress) { botViewModel.bind(state.ownerKey, state.progress) }

    // Un "Iniciar lectura" con niño exige antes un PIN de tutor (Modo Enfoque).
    val startReading: () -> Unit = {
        val now = viewModel.uiState.value
        if (now.isTutor && !now.pinConfigured) pinDialog = PinDialog.SETUP_FOR_START else onStartReading()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { HomeNavigationBar(state.selectedTab, viewModel::selectTab) }
    ) { padding ->
        // consumeWindowInsets: el Scaffold ya reservó barra de estado y navegación; safeDrawingPadding solo añade lo que
        // falte (teclado, recortes de cámara) en lugar de duplicar esos márgenes.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.isLoading) {
                FullScreenLoading()
                return@Box
            }
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (state.selectedTab == HomeTab.INICIO) Header(state, onLogout = viewModel::logout, onShowIntro = onShowIntro)
                ErrorMessage(state.errorMessage)
                if (state.offlineDemo) Text(stringResource(R.string.child_offline_demo), style = MaterialTheme.typography.bodySmall)

                // Cambio de apartado con un fundido y un leve deslizamiento.
                AnimatedContent(
                    targetState = state.selectedTab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { if (forward) it / 10 else -it / 10 })
                            .togetherWith(fadeOut(tween(160)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "homeTab"
                ) { tab ->
                    when (tab) {
                        HomeTab.INICIO -> InicioTab(
                            state = state,
                            bot = bot,
                            onSelect = viewModel::select,
                            onNewChild = onNewChild,
                            onEditChild = onEditChild,
                            onDeleteChild = { confirmDelete = it },
                            onNlpLevel = viewModel::requestNlpLevel,
                            onPin = { pinDialog = if (state.pinConfigured) PinDialog.CHANGE else PinDialog.SETUP },
                            onLoadDemo = viewModel::loadDemoData,
                            onCelebrate = botViewModel::celebrate,
                            onStartReading = startReading,
                            currentEquipped = bot.equipped,
                            equippedFor = botViewModel::equippedFor
                        )
                        HomeTab.LECTURAS -> LecturasTab(
                            state = state,
                            onSelect = viewModel::select,
                            onStartReading = startReading,
                            onLoadDemo = viewModel::loadDemoData,
                            onGoAdapt = { viewModel.selectTab(HomeTab.ADAPTAR) },
                            onOpenDemoLesson = { lesson ->
                                viewModel.openDemoLesson(lesson)
                                startReading()
                            },
                            onOpenLibraryLesson = { item -> viewModel.openLibraryLesson(item) { startReading() } }
                        )
                        HomeTab.ADAPTAR -> AdaptarTab(
                            state = state,
                            onSelect = viewModel::select,
                            onReadingSaved = viewModel::refresh,
                            // "Empezar ahora" ya pasó por la configuración de PIN dentro del estudio.
                            onNewReadingStart = onStartReading
                        )
                        HomeTab.AVATAR -> AvatarTab(
                            state = state,
                            bot = bot,
                            onSelect = viewModel::select,
                            onCelebrate = botViewModel::celebrate,
                            onBuyAccessory = botViewModel::buy,
                            onToggleAccessory = botViewModel::toggleEquip,
                            onBuyScenario = botViewModel::buyScenario,
                            onEquipScenario = botViewModel::equipScenario,
                            // Las misiones del servidor se confirman con su endpoint (ya abonadas); las locales suman al instante.
                            onClaimMission = { id ->
                                if (state.progress.serverMissions?.any { it.id == id } == true) {
                                    viewModel.claimServerMission(id, onBackendUnavailable = { botViewModel.claimMissionOffline(id) })
                                } else botViewModel.claimMission(id)
                            }
                        )
                        HomeTab.CARTAS -> CartasTab(state, bot, onSelect = viewModel::select)
                    }
                }
            }
        }
    }

    if (pinDialog != PinDialog.NONE) {
        ParentalGateDialog(
            mode = if (pinDialog == PinDialog.CHANGE) GateMode.CHANGE else GateMode.SETUP,
            onSuccess = {
                val startAfter = pinDialog == PinDialog.SETUP_FOR_START
                pinDialog = PinDialog.NONE
                viewModel.refreshPin()
                if (startAfter) onStartReading()
            },
            onDismiss = { pinDialog = PinDialog.NONE }
        )
    }

    // Cambio de nivel del niño: nada se guarda hasta "Confirmar"; "Cancelar" (o tocar fuera) lo deshace.
    val pendingLevel = state.pendingNlpLevel
    val levelChild = state.selectedChild
    if (pendingLevel != null && levelChild != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelNlpLevel,
            title = { Text(stringResource(R.string.home_level_confirm_title, levelChild.name, pendingLevel.value)) },
            text = { Text(stringResource(R.string.home_level_confirm_body, pendingLevel.description, levelChild.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmNlpLevel) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelNlpLevel) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    confirmDelete?.let { child ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.home_delete_title, child.name)) },
            text = { Text(stringResource(R.string.home_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(child.id); confirmDelete = null }) {
                    Text(stringResource(R.string.home_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/** Barra inferior de 5 apartados: ícono y UNA palabra que se ajusta antes de cortarse en pantallas angostas. */
@Composable
private fun HomeNavigationBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { FitText(tab.label, style = MaterialTheme.typography.labelMedium, minSize = 8.sp) },
                alwaysShowLabel = true
            )
        }
    }
}

private fun HomeTab.icon(): ImageVector = when (this) {
    HomeTab.INICIO -> Icons.Filled.Home
    HomeTab.LECTURAS -> Icons.AutoMirrored.Filled.MenuBook
    HomeTab.ADAPTAR -> Icons.Filled.AutoFixHigh
    HomeTab.AVATAR -> Icons.Filled.SmartToy
    HomeTab.CARTAS -> Icons.Filled.Style
}
