package com.fluilet.app.presentation.admin

import com.fluilet.app.presentation.common.DialogTitle
import com.fluilet.app.presentation.common.DialogTextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.fluilet.app.presentation.common.FitText
import com.fluilet.app.presentation.common.AdaptiveSingleLineText
import com.fluilet.app.presentation.common.AdaptiveText
import com.fluilet.app.presentation.common.ButtonLabel
import com.fluilet.app.domain.model.AccountType
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign

/**
 * Panel de Administración de la cuenta maestra (`admin@fluilet.com`): encabezado institucional, indicadores globales y
 * dos vistas — perfiles de niños (recompensas, etapa, nivel y edad) y cuentas de tutores — conectadas a
 * `/api/v1/admin/...`. Sin datos técnicos del servidor en pantalla.
 */
@Composable
fun MasterAdminScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteProfile by remember { mutableStateOf<AdminProfile?>(null) }
    var deleteTutor by remember { mutableStateOf<AdminUser?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AdminHeader(state.isBusy, onRestore = viewModel::restoreDemoAccounts, onLogout = viewModel::logout)

            state.message?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            ErrorMessage(state.errorMessage)

            KpiPanel(state)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AdminTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = tab == state.tab,
                        onClick = { viewModel.selectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, AdminTab.entries.size),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = tab.label },
                        label = { FitText(tab.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
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
                            owner = state.overview.ownerOf(p),
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
                    // Toda cuenta registrada (tutor, adulto independiente o niño, con o sin perfiles) con su tipo y su correo.
                    state.overview.tutors.forEach { u ->
                        AccountAdminCard(
                            user = u,
                            profiles = state.overview.profilesOf(u.userId),
                            enabled = !state.isBusy,
                            onResetPin = { viewModel.resetPin(u.userId) },
                            onManageProfiles = { viewModel.selectTab(AdminTab.CHILDREN) },
                            onDelete = { deleteTutor = u }
                        )
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
        title = { DialogTitle(title) },
        text = { Text(body) },
        confirmButton = { DialogTextButton(confirm, onConfirm, color = MaterialTheme.colorScheme.error) },
        dismissButton = { DialogTextButton(stringResource(R.string.action_cancel), onDismiss) }
    )
}

/** Marca y salida arriba; el título del panel a todo el ancho (nunca cortado) y la restauración de la demo. */
@Composable
private fun AdminHeader(busy: Boolean, onRestore: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FluiletIsotypeLogo(Modifier.size(42.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(FluiletBrand.NAME, color = FluiletBrand.Purple, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, fontSize = 22.sp, maxLines = 1)
                Text(
                    stringResource(R.string.admin_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(FluiletBrand.Purple).padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            FilledTonalIconButton(onClick = onLogout, modifier = Modifier.size(46.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.admin_logout))
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.admin_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.admin_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(
            onClick = onRestore,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            ButtonLabel(stringResource(R.string.admin_restore))
        }
    }
}

@Composable
private fun KpiPanel(state: AdminUiState) {
    val o = state.overview
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile("👤", stringResource(R.string.admin_kpi_tutors), "${o.totalTutors}", Modifier.weight(1f))
            KpiTile("👦", stringResource(R.string.admin_kpi_children), "${o.totalProfiles}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile("📖", stringResource(R.string.admin_kpi_adaptations), "${o.totalAdaptations}", Modifier.weight(1f))
            KpiTile("🧠", stringResource(R.string.admin_kpi_comprehension), "%.0f %%".format(java.util.Locale.US, o.averageComprehension), Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiTile(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        // Número grande arriba y su etiqueta completa debajo, centrados (nunca uno al lado del otro).
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            AdaptiveSingleLineText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium)
            AdaptiveText(
                label,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Ícono institucional en un círculo con el degradado de la marca. */
@Composable
private fun AdminAvatar(icon: ImageVector) {
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(FluiletBrand.Purple, FluiletBrand.SloganPurple))),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun InfoPill(text: String, background: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 5.dp)) {
        AdaptiveSingleLineText(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Grupo de controles con su título (sección de una tarjeta de administración). */
@Composable
private fun AdminSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** Opción seleccionable de una fila deslizable (etapa, nivel o edad): la elegida lleva color y palomita. */
@Composable
private fun ChoiceChip(selected: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier.heightIn(min = 40.dp)
    )
}

/** Opciones que bajan de renglón (FlowRow): todas completas, ninguna escondida en el borde derecho. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(options: List<T>, content: @Composable (T) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { content(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileAdminCard(profile: AdminProfile, owner: AdminUser?, enabled: Boolean, onGrant: (MasterGrant, String) -> Unit, onDelete: () -> Unit) {
    var exactGems by remember(profile.profileId) { mutableStateOf("") }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AdminAvatar(Icons.Filled.ChildCare)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AdaptiveText(profile.name, style = MaterialTheme.typography.titleLarge)
                    // El correo completo de la cuenta dueña, con su tipo (tutor o la propia cuenta del adulto).
                    AdaptiveText(
                        owner?.let { stringResource(R.string.admin_owner_line, it.accountType.label, profile.tutorEmail) }
                            ?: stringResource(R.string.admin_tutor_line, profile.tutorEmail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(stringResource(R.string.admin_level_chip, profile.currentLevel), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                InfoPill(stringResource(R.string.admin_age_chip, profile.ageGroup))
                InfoPill(stringResource(R.string.admin_stage_pill, profile.evolutionStage, profile.evolutionTitle))
                InfoPill("${profile.totalXp} XP")
                InfoPill("💎 ${profile.totalGems}", FluiletBrand.Mint.copy(alpha = 0.22f))
                InfoPill("🔥 ${profile.streakDays}")
                InfoPill(stringResource(R.string.admin_lessons_pill, profile.completedLessons))
                if (!profile.isActive) InfoPill(stringResource(R.string.admin_inactive), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }

            AdminSection(stringResource(R.string.admin_gems_section)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MasterGrant.QUICK_GEMS.forEach { amount ->
                        OutlinedButton(
                            onClick = { onGrant(MasterGrant.gems(amount), "+$amount 💎 otorgadas a ${profile.name}.") },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { FitText("+$amount 💎", Modifier.fillMaxWidth()) }
                    }
                }
                OutlinedTextField(
                    value = exactGems,
                    onValueChange = { exactGems = it.filter(Char::isDigit).take(9) },
                    label = { Text(stringResource(R.string.admin_exact_gems)) },
                    leadingIcon = { Icon(Icons.Filled.Diamond, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { exactGems.toIntOrNull()?.let { onGrant(MasterGrant.exactGems(it), "Saldo de ${profile.name} fijado en $it 💎."); exactGems = "" } },
                    enabled = enabled && exactGems.toIntOrNull() != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { ButtonLabel(stringResource(R.string.admin_set_gems)) }
            }

            AdminSection(stringResource(R.string.admin_stage_section)) {
                ChoiceRow((1..4).toList()) { stage ->
                    ChoiceChip(profile.evolutionStage == stage, stringResource(R.string.admin_stage_chip, stage), enabled) {
                        onGrant(MasterGrant.stage(stage), "${profile.name} ahora está en la etapa $stage.")
                    }
                }
                OutlinedButton(
                    onClick = { onGrant(MasterGrant.xp(), "+${MasterGrant.QUICK_XP} XP para ${profile.name}.") },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { ButtonLabel(stringResource(R.string.admin_add_xp)) }
            }

            AdminSection(stringResource(R.string.admin_level_section)) {
                ChoiceRow((1..5).toList()) { level ->
                    ChoiceChip(profile.currentLevel == level, stringResource(R.string.admin_level_chip, level), enabled) {
                        onGrant(MasterGrant.level(level), "Nivel $level para ${profile.name}.")
                    }
                }
            }

            AdminSection(stringResource(R.string.admin_age_section)) {
                ChoiceRow(MasterGrant.AGE_GROUPS) { group ->
                    ChoiceChip(profile.ageGroup == group, stringResource(R.string.admin_age_chip, group), enabled) {
                        onGrant(MasterGrant.ageGroup(group), "Edad didáctica $group para ${profile.name}.")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onGrant(MasterGrant.unlockAll(), "Recompensas desbloqueadas para ${profile.name}.") },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Redeem, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.admin_unlock_all))
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.admin_delete_child))
                }
            }
        }
    }
}

/**
 * Una cuenta registrada: etiqueta clara de su tipo (tutor, adulto independiente o niño), su correo completo, sus
 * perfiles lectores (también cuando tiene 0) y todas las acciones del maestro: restablecer PIN, gestionar el perfil y
 * sus gemas, y dar de baja. Ningún texto se corta: el correo se ajusta y los botones bajan de línea sin partir palabras.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountAdminCard(
    user: AdminUser,
    profiles: List<AdminProfile>,
    enabled: Boolean,
    onResetPin: () -> Unit,
    onManageProfiles: () -> Unit,
    onDelete: () -> Unit
) {
    val type = user.accountType
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AdminAvatar(
                    when (type) {
                        AccountType.TUTOR -> Icons.Filled.SupervisorAccount
                        AccountType.CHILD -> Icons.Filled.ChildCare
                        else -> Icons.Filled.Person
                    }
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoPill(type.label, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    AdaptiveText(user.email, style = MaterialTheme.typography.titleMedium)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(pluralStringResource(R.plurals.admin_profiles_count, profiles.size, profiles.size))
                if (user.isActive) InfoPill(stringResource(R.string.admin_active), FluiletBrand.Mint.copy(alpha = 0.22f))
                else InfoPill(stringResource(R.string.admin_inactive), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
            AdminSection(stringResource(R.string.admin_reading_profiles_title)) {
                if (profiles.isEmpty()) {
                    Text(stringResource(R.string.admin_no_reading_profile), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    profiles.forEach { p ->
                        AdaptiveText(
                            "📖 " + stringResource(R.string.admin_profile_line, p.name, p.currentLevel, p.totalGems),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onManageProfiles,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Diamond, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        ButtonLabel(stringResource(R.string.admin_manage_profiles))
                    }
                }
            }
            FilledTonalButton(
                onClick = onResetPin,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                ButtonLabel(stringResource(R.string.admin_reset_pin))
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = enabled && user.canBeDeleted,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.PersonRemove, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                ButtonLabel(stringResource(R.string.admin_delete_tutor))
            }
        }
    }
}
