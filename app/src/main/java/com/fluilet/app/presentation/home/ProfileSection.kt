package com.fluilet.app.presentation.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import com.fluilet.app.presentation.common.SelectableChip
import com.fluilet.app.presentation.common.CheckMark
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.FullScreenLoading
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.parentalgate.GateMode
import com.fluilet.app.presentation.parentalgate.ParentalGateDialog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun Header(state: HomeUiState, onLogout: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.userName.isBlank()) stringResource(R.string.home_hello_plain)
                else stringResource(R.string.home_hello, state.userName),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                stringResource(if (state.isTutor) R.string.home_role_tutor else R.string.home_role_adult),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.home_logout))
        }
    }
}

@Composable
internal fun ChildrenSection(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (ChildProfile) -> Unit,
    onNlpLevel: (NlpLevel) -> Unit
) {
    SectionCard(stringResource(R.string.home_children_title)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.children.forEach { child ->
                SelectableChip(
                    selected = child.id == state.selectedChildId,
                    onClick = { onSelect(child.id) },
                    label = { Text(child.name, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.height(48.dp)
                )
            }
            SelectableChip(
                selected = false,
                onClick = onNew,
                label = { Text(stringResource(R.string.home_new_child), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.height(48.dp)
            )
        }

        val child = state.selectedChild
        if (child == null) {
            Text(stringResource(R.string.home_children_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                stringResource(R.string.home_child_summary, child.difficulty.label, child.palette.displayName, child.fontSizeSp.toInt()),
                style = MaterialTheme.typography.bodyMedium
            )
            NlpLevelSlider(child.nlpLevel, onNlpLevel)
            // Apilados: con la tipografía OpenDyslexic y su espaciado, dos botones con icono no caben en una fila.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(stringResource(R.string.action_edit), { onEdit(child.id) }, icon = Icons.Filled.Edit)
                SecondaryButton(stringResource(R.string.action_delete), { onDelete(child) }, icon = Icons.Filled.Delete)
            }
        }
    }
}

@Composable
fun NlpLevelSlider(level: NlpLevel, onCommit: (NlpLevel) -> Unit) {
    var position by remember(level) { mutableStateOf(level.value.toFloat()) }
    val shown = NlpLevel.fromValue(position.toInt())
    Column {
        Text(stringResource(R.string.nlp_level_title, shown.label), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onCommit(NlpLevel.fromValue(Math.round(position))) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
        Text(shown.description, style = MaterialTheme.typography.bodySmall)
    }
}



/** Selector compacto de perfil para los apartados que dependen del niño (FluiBot, Colección, Estadísticas). */
@Composable
internal fun ProfileSelector(state: HomeUiState, onSelect: (String) -> Unit) {
    if (!state.isTutor || state.children.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.children.forEach { child ->
            SelectableChip(
                selected = child.id == state.selectedChildId,
                onClick = { onSelect(child.id) },
                label = { Text(child.name, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.height(48.dp)
            )
        }
    }
}
