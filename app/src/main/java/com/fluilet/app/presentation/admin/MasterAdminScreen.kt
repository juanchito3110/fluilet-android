package com.fluilet.app.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fluilet.app.R
import com.fluilet.app.domain.model.AdminProfile
import com.fluilet.app.domain.model.AdminUser
import com.fluilet.app.domain.model.MasterGrant
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.brand.FluiletIsotypeLogo
import com.fluilet.app.presentation.common.ErrorMessage
import com.fluilet.app.presentation.common.ModelAuditCard
import com.fluilet.app.presentation.common.SelectableChip

/**
 * Sistema de Control Maestro (`admin@fluilet.com`): barra superior con "👑 SISTEMA MAESTRO", KPIs globales y
 * dos secciones — otorgamiento a niños y gestión de tutores — conectadas a `/api/v1/admin/...`.
 */
@Composable
fun MasterAdminScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteProfile by remember { mutableStateOf<AdminProfile?>(null) }
    var deleteTutor by remember { mutableStateOf<AdminUser?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MasterTopBar(state.isBusy, onRestore = viewModel::restoreDemoAccounts, onLogout = viewModel::logout)

            state.message?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            ErrorMessage(state.errorMessage)

            KpiPanel(state)
            state.modelAudit?.let { ModelAuditCard(it) }

            TabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                AdminTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text("${tab.emoji} ${tab.label}", style = MaterialTheme.typography.labelMedium, maxLines = 2) }
                    )
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
            when (state.tab) {
                AdminTab.CHILDREN -> {
                    if (state.overview.profiles.isEmpty() && !state.isLoading) {
                        Text(stringResource(R.string.admin_no_children), style = MaterialTheme.typography.bodyMedium)
                    }
                    state.overview.profiles.forEach { p ->
                        ProfileAdminCard(
                            profile = p,
                            enabled = !state.isBusy,
                            onGrant = { grant, msg -> viewModel.grant(p.profileId, grant, msg) },
                            onDelete = { deleteProfile = p }
                        )
                    }
                }
                AdminTab.TUTORS -> {
                    if (state.overview.tutors.isEmpty() && !state.isLoading) {
                        Text(stringResource(R.string.admin_no_tutors), style = MaterialTheme.typography.bodyMedium)
                    }
                    state.overview.tutors.forEach { u ->
                        TutorAdminCard(u, enabled = !state.isBusy, onResetPin = { viewModel.resetPin(u.userId) }, onDelete = { deleteTutor = u })
                    }
                }
            }
        }
    }

    deleteProfile?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_child_title, p.name),
            body = stringResource(R.string.admin_delete_child_body),
            confirm = stringResource(R.string.admin_delete_confirm),
            onConfirm = { viewModel.deleteProfile(p.profileId); deleteProfile = null },
            onDismiss = { deleteProfile = null }
        )
    }
    deleteTutor?.let { u ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_tutor_title, u.email),
            body = stringResource(R.string.admin_delete_tutor_body, u.profilesCount),
            confirm = stringResource(R.string.admin_delete_confirm),
            onConfirm = { viewModel.deleteTutor(u.userId); deleteTutor = null },
            onDismiss = { deleteTutor = null }
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun MasterTopBar(busy: Boolean, onRestore: () -> Unit, onLogout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FluiletIsotypeLogo(Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(FluiletBrand.NAME, color = FluiletBrand.Purple, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 24.sp)
                Text(
                    stringResource(R.string.admin_badge),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(FluiletBrand.Purple).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRestore, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.admin_restore), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.admin_logout), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun KpiPanel(state: AdminUiState) {
    val o = state.overview
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("👤", stringResource(R.string.admin_kpi_tutors), "${o.totalTutors}", Modifier.weight(1f))
            KpiTile("👦", stringResource(R.string.admin_kpi_children), "${o.totalProfiles}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile("📖", stringResource(R.string.admin_kpi_adaptations), "${o.totalAdaptations}", Modifier.weight(1f))
            KpiTile("🧠", stringResource(R.string.admin_kpi_comprehension), "%.0f %%".format(java.util.Locale.US, o.averageComprehension), Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiTile(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProfileAdminCard(profile: AdminProfile, enabled: Boolean, onGrant: (MasterGrant, String) -> Unit, onDelete: () -> Unit) {
    var exactGems by remember(profile.profileId) { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                profile.name + if (profile.isActive) "" else " " + stringResource(R.string.admin_inactive),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.admin_tutor_line, profile.tutorEmail), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.admin_profile_line, profile.ageGroup, profile.currentLevel, profile.evolutionStage, profile.evolutionTitle),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.admin_profile_stats, profile.totalXp, profile.totalGems, profile.streakDays, profile.completedLessons),
                style = MaterialTheme.typography.titleSmall
            )

            SectionLabel(stringResource(R.string.admin_gems_section))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MasterGrant.QUICK_GEMS.forEach { amount ->
                    OutlinedButton(
                        onClick = { onGrant(MasterGrant.gems(amount), "+$amount 💎 otorgadas a ${profile.name}.") },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("+$amount 💎") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = exactGems,
                    onValueChange = { exactGems = it.filter(Char::isDigit).take(9) },
                    label = { Text(stringResource(R.string.admin_exact_gems)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { exactGems.toIntOrNull()?.let { onGrant(MasterGrant.exactGems(it), "Saldo de ${profile.name} fijado en $it 💎."); exactGems = "" } },
                    enabled = enabled && exactGems.toIntOrNull() != null,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) { Text(stringResource(R.string.admin_set_gems)) }
            }

            SectionLabel(stringResource(R.string.admin_stage_section))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { stage ->
                    SelectableChip(
                        selected = profile.evolutionStage == stage,
                        onClick = { onGrant(MasterGrant.stage(stage), "${profile.name} ahora está en la etapa $stage.") },
                        label = { Text(stringResource(R.string.admin_stage_chip, stage)) }
                    )
                }
                OutlinedButton(
                    onClick = { onGrant(MasterGrant.xp(), "+${MasterGrant.QUICK_XP} XP para ${profile.name}.") },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.admin_add_xp)) }
            }

            SectionLabel(stringResource(R.string.admin_level_section))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { level ->
                    SelectableChip(
                        selected = profile.currentLevel == level,
                        onClick = { onGrant(MasterGrant.level(level), "Nivel ${level} para ${profile.name}.") },
                        label = { Text(stringResource(R.string.admin_level_chip, level)) }
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MasterGrant.AGE_GROUPS.forEach { group ->
                    SelectableChip(
                        selected = profile.ageGroup == group,
                        onClick = { onGrant(MasterGrant.ageGroup(group), "Edad didáctica $group para ${profile.name}.") },
                        label = { Text(stringResource(R.string.admin_age_chip, group)) }
                    )
                }
            }

            Button(
                onClick = { onGrant(MasterGrant.unlockAll(), "Recompensas desbloqueadas para ${profile.name}.") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.admin_unlock_all), style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.admin_delete_child), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun TutorAdminCard(user: AdminUser, enabled: Boolean, onResetPin: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(user.email, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.admin_children_count, user.profilesCount) + if (user.isActive) "" else " · " + stringResource(R.string.admin_inactive),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = onResetPin,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.admin_reset_pin), style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = onDelete,
                enabled = enabled && user.canBeDeleted,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.admin_delete_tutor), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) }
        }
    }
}
