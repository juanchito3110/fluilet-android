package com.fluilet.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == state.selectedTab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Text(tab.emoji, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                FullScreenLoading()
                return@Box
            }
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header(state, onLogout = viewModel::logout)
                ErrorMessage(state.errorMessage)
                if (state.selectedTab != HomeTab.LECTURAS) ProfileSelector(state, viewModel::select)

                when (state.selectedTab) {
                    HomeTab.LECTURAS -> ReadingsTab(
                        state = state,
                        onSelect = viewModel::select,
                        onNewChild = onNewChild,
                        onEditChild = onEditChild,
                        onDeleteChild = { confirmDelete = it },
                        onNlpLevel = viewModel::onNlpLevel,
                        onStartReading = {
                            // El Modo Enfoque con niño exige tener un PIN de tutor antes de empezar.
                            if (state.isTutor && !state.pinConfigured) pinDialog = PinDialog.SETUP_FOR_START else onStartReading()
                        },
                        onReadingSaved = viewModel::refresh,
                        onPin = { pinDialog = if (state.pinConfigured) PinDialog.CHANGE else PinDialog.SETUP },
                        // "Empezar ahora" ya pasó por la configuración de PIN dentro del creador de lecturas.
                        onNewReadingStart = onStartReading
                    )
                    HomeTab.FLUIBOT -> FluiBotTab(
                        bot = bot,
                        onCelebrate = botViewModel::celebrate,
                        onBuyAccessory = botViewModel::buy,
                        onToggleAccessory = botViewModel::toggleEquip,
                        onBuyScenario = botViewModel::buyScenario,
                        onEquipScenario = botViewModel::equipScenario
                    )
                    HomeTab.COLECCION -> CollectionTab(
                        bot = bot,
                        progress = state.progress,
                        onClaim = botViewModel::claimMission
                    )
                    HomeTab.ESTADISTICAS -> ProgressDashboard(state.progress, state.progressNote)
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
