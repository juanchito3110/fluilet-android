package com.fluilet.app.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.TabRow
import com.fluilet.app.presentation.common.IconLabelTab
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.fluilet.app.presentation.common.ChipCarousel
import com.fluilet.app.presentation.common.InlineChipStrip
import com.fluilet.app.presentation.common.CollapsibleCard
import com.fluilet.app.presentation.common.FluiletCarousel
import com.fluilet.app.presentation.common.SelectableChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluilet.app.R
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LibraryItem
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.presentation.brand.FluiletBrand
import com.fluilet.app.presentation.common.CategoryArtwork
import com.fluilet.app.presentation.common.AdaptiveSingleLineText
import com.fluilet.app.presentation.common.AdaptiveText
import com.fluilet.app.presentation.common.ButtonLabel
import androidx.compose.ui.text.style.TextAlign
import com.fluilet.app.presentation.common.SecondaryButton
import com.fluilet.app.presentation.common.SectionCard
import com.fluilet.app.presentation.fluibot.AlbumCarousel
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AdaptiveText(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Selector compacto del lector activo (solo tutor) en UNA sola fila: "Lector activo" a la izquierda y los nombres
 * completos a la derecha; si no caben todos, la fila se desliza por páginas con una flecha. Así no empuja hacia abajo
 * el contenido del apartado.
 */
@Composable
internal fun ReaderSwitcher(state: HomeUiState, onSelect: (String) -> Unit) {
    if (!state.isTutor || state.children.isEmpty()) return
    InlineChipStrip(
        items = state.children,
        label = { it.name },
        isSelected = { it.id == state.selectedChildId },
        onClick = { onSelect(it.id) },
        leadingLabel = stringResource(R.string.reader_switcher_label)
    )
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
            AdaptiveText(reading.adaptation.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
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
                ButtonLabel(stringResource(if (state.isTutor) R.string.home_start_focus else R.string.home_start))
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
    onCelebrate: () -> Unit,
    onStartReading: () -> Unit,
    currentEquipped: Set<String>,
    equippedFor: (String) -> Set<String>,
    onDeleteAccount: () -> Unit,
    onDismissAccountError: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (state.isTutor) {
            Text(stringResource(R.string.home_readers_title), style = MaterialTheme.typography.titleMedium)
            ChildCardsRow(state, onSelect = onSelect, onNew = onNewChild, currentEquipped = currentEquipped, equippedFor = equippedFor)
        }
        if (!state.isTutor || state.selectedChild != null) HomeHeroCard(state, bot, onCelebrate = onCelebrate)
        ReadingReadyCard(state, onStartReading)
        // Sin "Cargar Datos Demo": esa función solo existe en el Panel de Administración Maestro.
        if (state.isTutor) TutorPanelCard(state, onSelect, onNewChild, onEditChild, onDeleteChild, onNlpLevel, onPin)
        // Aviso de privacidad y "Eliminar mi cuenta y datos" siempre a la vista del adulto (tutor o independiente).
        AccountPrivacyCard(state, onDeleteAccount, onDismissAccountError)
    }
}

/**
 * "Panel del tutor": herramientas que no se usan a diario (nivel de lectura del niño, editar o eliminar su perfil y PIN
 * parental) plegadas en una sola tarjeta para que Inicio no se sature.
 */
@Composable
private fun TutorPanelCard(
    state: HomeUiState,
    onSelect: (String) -> Unit,
    onNewChild: () -> Unit,
    onEditChild: (String) -> Unit,
    onDeleteChild: (ChildProfile) -> Unit,
    onNlpLevel: (NlpLevel) -> Unit,
    onPin: () -> Unit
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
                    AdaptiveText(stringResource(R.string.tutor_panel_title), style = MaterialTheme.typography.titleMedium)
                    AdaptiveText(stringResource(R.string.tutor_panel_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onGoAdapt: () -> Unit,
    onOpenDemoLesson: (AdaptationResult) -> Unit,
    onOpenLibraryLesson: (LibraryItem) -> Unit
) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var quick by rememberSaveable { mutableStateOf(LibraryQuickFilter.ALL) }
    val filter = LibraryFilter(category = category, quick = quick)
    val byCategory = LibraryShelf.entries(state.library, state.demoLessons, LibraryFilter(category = category))
    val entries = byCategory.filter(quick::matches)
    val counts = LibraryShelf.quickCounts(byCategory)
    val categories = LibraryShelf.categories(state.library, state.demoLessons)
    val readerName = state.selectedChild?.name
    val readerLevel = state.selectedChild?.nlpLevel?.value ?: state.progress.recommendedLevel.takeIf { it in 1..5 }
    val suggested = if (quick == LibraryQuickFilter.ALL && category == null) LibraryShelf.suggested(byCategory, readerLevel) else emptyList()
    val opening: (ShelfEntry) -> Unit = { entry -> entry.item?.let(onOpenLibraryLesson) ?: entry.lesson?.let(onOpenDemoLesson) }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TabHeader(
            HomeTab.LECTURAS.title,
            if (readerName != null) stringResource(R.string.library_header_sub, readerName) else stringResource(R.string.library_header_sub_plain)
        )
        ReaderSwitcher(state, onSelect)
        // Lo recién creado va primero y destacado.
        ReadingReadyCard(state, onStartReading)

        if (categories.isNotEmpty()) {
            // Filtros rápidos de dos renglones (nombre arriba; banda y cantidad abajo): caben varios por fila y ninguno se corta.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryQuickFilter.entries.forEach { option ->
                    SelectableChip(
                        selected = quick == option,
                        onClick = { quick = option },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(option.title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), maxLines = 1, softWrap = false)
                                Text(option.detailLine(counts[option] ?: 0), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal), maxLines = 1, softWrap = false)
                            }
                        }
                    )
                }
            }
            // Categorías en una sola fila deslizable por páginas.
            val allLabel = stringResource(R.string.library_filter_all)
            ChipCarousel(
                items = listOf<String?>(null) + categories,
                label = { code -> code?.let(LessonCategory::displayNameFor) ?: allLabel },
                isSelected = { it == category },
                onClick = { category = it },
                title = stringResource(R.string.library_filter_category),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                minHeight = 40.dp,
                compactHeader = true
            )
        }

        if (suggested.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdaptiveText(
                    stringResource(R.string.library_suggested_title, readerLevel ?: 1),
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium
                )
                FluiletCarousel(suggested, itemKey = { it.key }) { entry, _ ->
                    ShelfCard(
                        entry = entry,
                        opening = entry.item != null && state.openingLessonId == entry.item.documentId,
                        enabled = state.openingLessonId == null,
                        highlighted = true,
                        onOpen = { opening(entry) }
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            Text(stringResource(R.string.library_count, entries.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }

        // El listado cambia con un fundido suave al tocar un filtro.
        AnimatedContent(filter, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "libraryFilter") { shownFilter ->
            val shown = LibraryShelf.entries(state.library, state.demoLessons, shownFilter)
            var visible by rememberSaveable(shownFilter.category, shownFilter.quick) { mutableIntStateOf(LIBRARY_PAGE) }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    shown.isNotEmpty() -> {
                        shown.take(visible).forEach { entry ->
                            ShelfCard(
                                entry = entry,
                                opening = entry.item != null && state.openingLessonId == entry.item.documentId,
                                enabled = state.openingLessonId == null,
                                onOpen = { opening(entry) }
                            )
                        }
                        if (shown.size > visible) {
                            SecondaryButton(
                                stringResource(R.string.library_show_more, shown.size - visible),
                                { visible += LIBRARY_PAGE },
                                icon = Icons.Filled.ExpandMore
                            )
                        }
                    }
                    categories.isNotEmpty() -> EmptyShelf(stringResource(R.string.library_empty_filtered), null)
                    else -> EmptyShelf(
                        if (readerName != null) stringResource(R.string.library_empty, readerName) else stringResource(R.string.library_empty_plain),
                        onGoAdapt
                    )
                }
            }
        }

        // Historial plegado: no alarga la biblioteca hasta que se pide.
        val lessons = state.progress.lessons
        CollapsibleCard(
            title = stringResource(R.string.readings_history_title),
            subtitle = stringResource(R.string.library_history_count, lessons.size)
        ) {
            if (lessons.isEmpty()) {
                Text(stringResource(R.string.readings_history_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                lessons.asReversed().take(MAX_HISTORY_ROWS).forEach { LessonRow(it) }
            }
        }
    }
}

/** Lecturas que se muestran de entrada (y cuántas más trae "Mostrar más"). */
private const val LIBRARY_PAGE = 6

/**
 * Tarjeta compacta de una lectura: el arte de su categoría a la izquierda y, a la derecha, el título completo, su nivel
 * y cuánto se facilitó; el botón "Abrir" a todo el ancho debajo. [highlighted] la resalta en "Sugeridas para ti".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfCard(entry: ShelfEntry, opening: Boolean, enabled: Boolean, onOpen: () -> Unit, highlighted: Boolean = false) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CategoryArtwork(entry.category, Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)), iconSize = 34.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val categoryName = LessonCategory.displayNameFor(entry.category)
                    // El título completo (los renglones que necesite); la categoría encima solo si no repite el título.
                    if (!categoryName.equals(entry.title, ignoreCase = true)) {
                        AdaptiveText(categoryName, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    AdaptiveText(entry.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.level?.let { Pill(stringResource(R.string.child_card_level, it), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) }
                        Pill(
                            stringResource(if (entry.item != null) R.string.library_server_badge else R.string.library_demo_badge),
                            MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            // A todo el ancho de la tarjeta, debajo del arte: "+15.5 pts de facilidad" se lee completo en cualquier teléfono.
            entry.gain?.takeIf { it > 0 }?.let {
                Pill(stringResource(R.string.library_gain, String.format(Locale.US, "%.1f", it)), FluiletBrand.Mint.copy(alpha = 0.2f), MaterialTheme.colorScheme.onBackground)
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
                ButtonLabel(stringResource(if (opening) R.string.library_opening else R.string.demo_lesson_open))
            }
        }
    }
}

@Composable
internal fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 10.dp, vertical = 4.dp)) {
        AdaptiveSingleLineText(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun EmptyShelf(message: String, onGoAdapt: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
    // Encabezado y márgenes compactos: FluiBot, las pestañas del taller y su título caben en la primera vista.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabHeader(HomeTab.AVATAR.title, stringResource(R.string.avatar_header_sub))
        ReaderSwitcher(state, onSelect)
        FluiBotTab(bot, onCelebrate, onBuyAccessory, onToggleAccessory, onBuyScenario, onEquipScenario)
        MissionsCard(bot.missions, onClaimMission)
    }
}

// --- Cartas --------------------------------------------------------------------------------------------

@Composable
internal fun CartasTab(state: HomeUiState, bot: FluiBotUiState, onSelect: (String) -> Unit) {
    var view by rememberSaveable { mutableStateOf(CardsView.ALBUM) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TabHeader(HomeTab.CARTAS.title, stringResource(R.string.cards_header_sub))
        ReaderSwitcher(state, onSelect)
        // 3 sub-pestañas con emoji arriba y palabra completa debajo: "Álbum", "Logros" y "Progreso" nunca se cortan.
        TabRow(selectedTabIndex = view.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            CardsView.entries.forEach { option ->
                IconLabelTab(selected = view == option, onClick = { view = option }, emoji = option.emoji, label = option.label)
            }
        }
        AnimatedContent(view, transitionSpec = { slideBetweenTabs(initialState.ordinal, targetState.ordinal) }, label = "cardsView") { current ->
            when (current) {
                CardsView.ALBUM -> AlbumCarousel(bot.cards.ifEmpty { state.progress.cards })
                CardsView.LOGROS -> BadgeGallery(bot.badges.ifEmpty { state.progress.badges })
                CardsView.PROGRESO -> ProgressDashboard(state.progress, state.progressNote)
            }
        }
    }
}

/** Transición entre sub-pestañas: la nueva entra deslizándose desde el lado al que se avanza, con fundido. */
internal fun slideBetweenTabs(from: Int, to: Int): ContentTransform {
    val direction = if (to >= from) 1 else -1
    return (slideInHorizontally { width -> direction * width / 5 } + fadeIn()) togetherWith
        (slideOutHorizontally { width -> -direction * width / 5 } + fadeOut())
}
