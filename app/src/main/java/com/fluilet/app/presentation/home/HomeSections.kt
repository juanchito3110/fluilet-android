package com.fluilet.app.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluilet.app.R
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ModelAudit
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.common.CategoryArtwork
import com.fluilet.app.presentation.common.FitText
import com.fluilet.app.presentation.common.ModelAuditCard
import com.fluilet.app.presentation.common.PrimaryButton
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.fluibot.AlbumSection
import com.fluilet.app.presentation.fluibot.FluiBotUiState
import com.fluilet.app.presentation.fluibot.MissionsCard
import com.fluilet.app.presentation.newreading.NewReadingContent
import com.fluilet.app.presentation.newreading.NewReadingViewModel
import java.util.Locale

private const val MAX_HISTORY_ROWS = 10

// --- Piezas comunes ---------------------------------------------------------------------------------

/** Encabezado de un apartado: título grande y una línea que explica para qué sirve. */
@Composable
internal fun TabHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Selector compacto del lector activo (solo tutor) para los apartados que trabajan sobre un niño. */
@Composable
internal fun ReaderSwitcher(state: HomeUiState, onSelect: (String) -> Unit) {
    if (!state.isTutor || state.children.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.reader_switcher_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(state.children, key = { it.id }) { child ->
                FilterChip(
                    selected = child.id == state.selectedChildId,
                    onClick = { onSelect(child.id) },
                    label = { Text(child.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) },
                    modifier = Modifier.heightIn(min = 44.dp)
                )
            }
        }
    }
}

/** Lectura lista para empezar (adaptada o abierta desde la biblioteca). */
@Composable
internal fun ReadingReadyCard(state: HomeUiState, onStartReading: () -> Unit) {
    val reading = state.readingForSelection ?: return
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_reading_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
            Text(reading.adaptation.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
            Text(
                stringResource(R.string.home_reading_ready, reading.adaptation.chunks.size, reading.adaptation.metrics.adaptedSzigrisztScore),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Button(
                onClick = onStartReading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (state.isTutor) R.string.home_start_focus else R.string.home_start),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// --- Inicio --------------------------------------------------------------------------------------------

@Composable
internal fun InicioTab(
    state: HomeUiState,
    bot: FluiBotUiState,
    onSelect: (String) -> Unit,
    onNewChild: () -> Unit,
    onEditChild: (String) -> Unit,
    onDeleteChild: (ChildProfile) -> Unit,
    onNlpLevel: (NlpLevel) -> Unit,
    onPin: () -> Unit,
    onLoadDemo: () -> Unit,
    onCelebrate: () -> Unit,
    onStartReading: () -> Unit,
    currentEquipped: Set<String>,
    equippedFor: (String) -> Set<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (state.isTutor) {
            Text(stringResource(R.string.home_readers_title), style = MaterialTheme.typography.titleMedium)
            ChildCardsRow(state, onSelect = onSelect, onNew = onNewChild, currentEquipped = currentEquipped, equippedFor = equippedFor)
        }
        if (!state.isTutor || state.selectedChild != null) HomeHeroCard(state, bot, onCelebrate = onCelebrate)
        ReadingReadyCard(state, onStartReading)
        if (state.isTutor) {
            TutorPanelCard(state, onSelect, onNewChild, onEditChild, onDeleteChild, onNlpLevel, onPin, onLoadDemo)
        } else {
            DemoDataCard(state, onLoadDemo)
        }
    }
}

/**
 * "Panel del tutor": herramientas que no se usan a diario (nivel de lectura del niño, editar o eliminar su perfil, PIN
 * parental y datos demo) plegadas en una sola tarjeta para que Inicio no se sature.
 */
@Composable
private fun TutorPanelCard(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onNewChild: () -> Unit,
    onEditChild: (String) -> Unit,
    onDeleteChild: (ChildProfile) -> Unit,
    onNlpLevel: (NlpLevel) -> Unit,
    onPin: () -> Unit,
    onLoadDemo: () -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Un cambio de nivel pendiente de confirmar mantiene el panel abierto.
    LaunchedEffect(state.pendingNlpLevel) { if (state.pendingNlpLevel != null) open = true }
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "panelArrow")
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, FluiletBrand.Purple.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.tutor_panel_close else R.string.tutor_panel_open)) { open = !open }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(FluiletBrand.Purple, FluiletBrand.SloganPurple))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = Color.White) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.tutor_panel_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tutor_panel_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(arrow))
            }
            AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChildrenSection(state, onSelect, onNewChild, onEditChild, onDeleteChild, onNlpLevel)
                    SectionCard(stringResource(R.string.home_pin_title)) {
                        Text(
                            stringResource(if (state.pinConfigured) R.string.home_pin_set else R.string.home_pin_missing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        SecondaryButton(
                            text = stringResource(if (state.pinConfigured) R.string.home_pin_change else R.string.home_pin_create),
                            onClick = onPin,
                            icon = Icons.Filled.Lock
                        )
                    }
                    DemoDataCard(state, onLoadDemo)
                }
            }
        }
    }
}

// --- Lecturas ------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LecturasTab(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onStartReading: () -> Unit,
    onLoadDemo: () -> Unit,
    onGoAdapt: () -> Unit,
    onOpenDemoLesson: (AdaptationResult) -> Unit,
    onOpenLibraryLesson: (LibraryItem) -> Unit
) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    val filter = LibraryFilter(category, level)
    val entries = LibraryShelf.entries(state.library, state.demoLessons, filter)
    val categories = LibraryShelf.categories(state.library, state.demoLessons)
    val readerName = state.selectedChild?.name

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TabHeader(
            HomeTab.LECTURAS.title,
            if (readerName != null) stringResource(R.string.library_header_sub, readerName) else stringResource(R.string.library_header_sub_plain)
        )
        ReaderSwitcher(state, onSelect)
        ReadingReadyCard(state, onStartReading)

        if (categories.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.library_filter_category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.library_filter_all), style = MaterialTheme.typography.labelLarge) })
                    categories.forEach { code ->
                        FilterChip(
                            selected = category == code,
                            onClick = { category = if (category == code) null else code },
                            label = { Text(LessonCategory.displayNameFor(code), style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
                Text(stringResource(R.string.library_filter_level), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = level == null, onClick = { level = null }, label = { Text(stringResource(R.string.library_filter_all_levels), style = MaterialTheme.typography.labelLarge) })
                    (1..5).forEach { n ->
                        FilterChip(
                            selected = level == n,
                            onClick = { level = if (level == n) null else n },
                            label = { Text(stringResource(R.string.child_card_level, n), style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }
            Text(stringResource(R.string.library_count, entries.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }

        when {
            entries.isNotEmpty() -> entries.forEach { entry ->
                ShelfCard(
                    entry = entry,
                    opening = entry.item != null && state.openingLessonId == entry.item.documentId,
                    enabled = state.openingLessonId == null,
                    onOpen = { entry.item?.let(onOpenLibraryLesson) ?: entry.lesson?.let(onOpenDemoLesson) }
                )
            }
            categories.isNotEmpty() -> EmptyShelf(stringResource(R.string.library_empty_filtered), null, null)
            else -> EmptyShelf(
                if (readerName != null) stringResource(R.string.library_empty, readerName) else stringResource(R.string.library_empty_plain),
                onLoadDemo.takeIf { !state.isDemoLoading },
                onGoAdapt
            )
        }

        SectionCard(stringResource(R.string.readings_history_title)) {
            val lessons = state.progress.lessons
            if (lessons.isEmpty()) {
                Text(stringResource(R.string.readings_history_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                lessons.asReversed().take(MAX_HISTORY_ROWS).forEach { LessonRow(it) }
            }
        }
    }
}

/** Tarjeta editorial de una lectura: franja con el arte de su categoría, título, nivel y cuánto se facilitó. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfCard(entry: ShelfEntry, opening: Boolean, enabled: Boolean, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            CategoryArtwork(entry.category, Modifier.fillMaxWidth().height(96.dp), iconSize = 40.dp)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LessonCategory.displayNameFor(entry.category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.level?.let { Pill(stringResource(R.string.child_card_level, it), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) }
                    Pill(
                        stringResource(if (entry.item != null) R.string.library_server_badge else R.string.library_demo_badge),
                        MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground
                    )
                    entry.gain?.takeIf { it > 0 }?.let {
                        Pill(stringResource(R.string.library_gain, String.format(Locale.US, "%.1f", it)), FluiletBrand.Mint.copy(alpha = 0.2f), MaterialTheme.colorScheme.onBackground)
                    }
                }
                if (entry.originalScore != null && entry.adaptedScore != null && entry.originalScore > 0) {
                    Text(
                        stringResource(
                            R.string.library_scores,
                            String.format(Locale.US, "%.1f", entry.originalScore),
                            String.format(Locale.US, "%.1f", entry.adaptedScore)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (opening) R.string.library_opening else R.string.demo_lesson_open), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun EmptyShelf(message: String, onLoadDemo: (() -> Unit)?, onGoAdapt: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            onLoadDemo?.let { PrimaryButton(stringResource(R.string.demo_load), it) }
            onGoAdapt?.let { SecondaryButton(stringResource(R.string.library_go_adapt), it, icon = Icons.Filled.AutoFixHigh) }
        }
    }
}

// --- Adaptar -------------------------------------------------------------------------------------------

@Composable
internal fun AdaptarTab(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onReadingSaved: () -> Unit,
    onNewReadingStart: () -> Unit,
    readingViewModel: NewReadingViewModel = hiltViewModel()
) {
    // El estudio siempre trabaja para el lector activo.
    LaunchedEffect(state.selectedChildId, state.isTutor) {
        readingViewModel.bindChild(if (state.isTutor) state.selectedChildId else null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TabHeader(HomeTab.ADAPTAR.title, stringResource(R.string.adapt_header_sub))
        ReaderSwitcher(state, onSelect)
        if (!state.isTutor || state.selectedChild != null) {
            NewReadingContent(onSaved = onReadingSaved, onStartNow = onNewReadingStart, viewModel = readingViewModel)
        } else {
            SectionCard(stringResource(R.string.newreading_title)) {
                Text(stringResource(R.string.home_children_empty), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// --- Avatar --------------------------------------------------------------------------------------------

@Composable
internal fun AvatarTab(
    state: HomeUiState,
    bot: FluiBotUiState,
    onSelect: (String) -> Unit,
    onCelebrate: () -> Unit,
    onBuyAccessory: (com.fluilet.app.domain.gamification.Accessory) -> Unit,
    onToggleAccessory: (com.fluilet.app.domain.gamification.Accessory) -> Unit,
    onBuyScenario: (com.fluilet.app.domain.gamification.Scenario) -> Unit,
    onEquipScenario: (com.fluilet.app.domain.gamification.Scenario) -> Unit,
    onClaimMission: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TabHeader(HomeTab.AVATAR.title, stringResource(R.string.avatar_header_sub))
        ReaderSwitcher(state, onSelect)
        FluiBotTab(bot, onCelebrate, onBuyAccessory, onToggleAccessory, onBuyScenario, onEquipScenario)
        MissionsCard(bot.missions, onClaimMission)
    }
}

// --- Cartas --------------------------------------------------------------------------------------------

@Composable
internal fun CartasTab(state: HomeUiState, bot: FluiBotUiState, onSelect: (String) -> Unit, audit: ModelAudit?) {
    var view by rememberSaveable { mutableStateOf(CardsView.ALBUM) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TabHeader(HomeTab.CARTAS.title, stringResource(R.string.cards_header_sub))
        ReaderSwitcher(state, onSelect)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CardsView.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = view == option,
                    onClick = { view = option },
                    shape = SegmentedButtonDefaults.itemShape(index, CardsView.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = option.label },
                    label = { FitText(option.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                )
            }
        }
        when (view) {
            CardsView.ALBUM -> AlbumSection(bot.cards.ifEmpty { state.progress.cards }, bot.badges.ifEmpty { state.progress.badges })
            CardsView.ESTADISTICAS -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressDashboard(state.progress, state.progressNote)
                audit?.let { ModelAuditCard(it) }
            }
        }
    }
}
