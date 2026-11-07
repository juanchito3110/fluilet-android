package com.fluilet.app.presentation.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import kotlinx.coroutines.launch

/** Reparte chips en páginas que caben COMPLETAS a lo ancho: ninguno queda cortado en el borde derecho. */
object ChipPaging {
    /** Rangos de índices por página, en orden; un chip más ancho que la fila va solo en su página. */
    fun pages(widths: List<Float>, available: Float, gap: Float): List<IntRange> {
        if (widths.isEmpty()) return emptyList()
        val pages = mutableListOf<IntRange>()
        var start = 0
        var used = -gap
        widths.forEachIndexed { index, width ->
            val next = used + gap + width
            if (index > start && next > available) {
                pages += start until index
                start = index
                used = width
            } else {
                used = next
            }
        }
        pages += start until widths.size
        return pages
    }

    /** Página donde está el chip [index] (0 si no aparece). */
    fun pageOf(index: Int, pages: List<IntRange>): Int = pages.indexOfFirst { index in it }.coerceAtLeast(0)
}

private val ChipPadding = 14.dp
private val ChipIconSize = 18.dp
private val ChipIconGap = 6.dp
private val ChipSlack = 2.dp

/**
 * Tira de chips en UNA sola fila que se desliza por páginas (`HorizontalPager`): cada página lleva solo los chips que
 * caben enteros, así que ninguna palabra se corta en el borde. Si hay más de una página, ◀ · puntos · ▶ junto al título
 * indican que hay más. Ocupa la altura de un solo renglón aunque haya muchas opciones.
 */
@Composable
fun <T> ChipCarousel(
    items: List<T>,
    label: (T) -> String,
    isSelected: (T) -> Boolean,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ((T) -> ImageVector)? = null,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    minHeight: Dp = 44.dp,
    gap: Dp = 8.dp
) {
    if (items.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth
        val labels = items.map(label)
        val hasIcon = icon != null
        val widths = remember(labels, style, density, hasIcon) {
            labels.map { text ->
                val textWidth = with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }
                textWidth + ChipPadding * 2 + (if (hasIcon) ChipIconSize + ChipIconGap else 0.dp) + ChipSlack
            }
        }
        val pages = remember(widths, available, gap) { ChipPaging.pages(widths.map { it.value }, available.value, gap.value) }
        val state = rememberPagerState { pages.size }
        val selectedIndex = items.indexOfFirst(isSelected)
        // Si se elige una opción desde otro lugar, la tira se desliza hasta su página.
        LaunchedEffect(selectedIndex, pages.size) {
            if (selectedIndex >= 0) state.animateScrollToPage(ChipPaging.pageOf(selectedIndex, pages))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null || pages.size > 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (title != null) {
                        AdaptiveText(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (pages.size > 1) MiniPagerNav(state)
                }
            }
            val row: @Composable (IntRange) -> Unit = { range ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    range.forEach { index ->
                        val item = items[index]
                        CarouselChip(
                            text = labels[index],
                            selected = isSelected(item),
                            icon = icon?.invoke(item),
                            fits = widths[index] <= available,
                            style = style,
                            minHeight = minHeight,
                            maxWidth = available,
                            onClick = { onClick(item) }
                        )
                    }
                }
            }
            if (pages.size == 1) {
                row(pages.first())
            } else {
                HorizontalPager(state = state, modifier = Modifier.fillMaxWidth(), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { page ->
                    row(pages[page])
                }
            }
        }
    }
}

/** ◀ · puntos · ▶ compacto para las tiras de chips. */
@Composable
private fun MiniPagerNav(state: PagerState) {
    val scope = rememberCoroutineScope()
    val current = state.currentPage
    val count = state.pageCount
    val pageText = stringResource(R.string.carousel_page, current + 1, count)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CarouselArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.carousel_previous), enabled = current > 0, size = 36.dp) {
            scope.launch { state.animateScrollToPage(current - 1) }
        }
        Box(Modifier.semantics { contentDescription = pageText }) { PageDots(current, count) }
        CarouselArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.carousel_next), enabled = current < count - 1, size = 36.dp) {
            scope.launch { state.animateScrollToPage(current + 1) }
        }
    }
}

@Composable
private fun CarouselChip(
    text: String,
    selected: Boolean,
    icon: ImageVector?,
    fits: Boolean,
    style: TextStyle,
    minHeight: Dp,
    maxWidth: Dp,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(if (selected) primary else MaterialTheme.colorScheme.surface, label = "chipColor")
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.heightIn(min = minHeight).widthIn(max = maxWidth)
    ) {
        Row(
            Modifier.padding(horizontal = ChipPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChipIconGap)
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(ChipIconSize)) }
            // Cabe siempre a su tamaño; solo una palabra más ancha que toda la fila (letra gigante) se reduce.
            if (fits) Text(text, style = style, maxLines = 1, softWrap = false)
            else AdaptiveSingleLineText(text, Modifier.weight(1f, fill = false), style = style, textAlign = TextAlign.Start)
        }
    }
}
