package com.fluilet.app.presentation.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fluilet.app.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Reglas del carrusel sin Android (probadas con pruebas unitarias). */
object CarouselMotion {
    /** Escala y opacidad de las tarjetas vecinas; la central va a 1. */
    const val MIN_SCALE = 0.93f
    const val MIN_ALPHA = 0.75f

    /** Margen a cada lado: la tarjeta central ocupa ~86 % del ancho y se asoman sus vecinas. */
    val SidePeek: Dp = 24.dp
    val PageGap: Dp = 12.dp

    /** Más de estos puntos no caben con aire: se muestra una ventana que sigue a la página actual. */
    const val MAX_DOTS = 7

    /** Tarjetas que se componen fuera de la vista para medir la más alta (todas en listas cortas). */
    const val MAX_PRECOMPOSED = 12

    private fun distance(offsetPages: Float) = abs(offsetPages).coerceIn(0f, 1f)

    /** 0 = página centrada; 1 o más = a una página (o más) de distancia. */
    fun scale(offsetPages: Float): Float = 1f - (1f - MIN_SCALE) * distance(offsetPages)
    fun alpha(offsetPages: Float): Float = 1f - (1f - MIN_ALPHA) * distance(offsetPages)

    /** Índices de los puntos a dibujar: todos si caben; si no, [max] alrededor de la página actual. */
    fun dotWindow(current: Int, count: Int, max: Int = MAX_DOTS): IntRange {
        if (count <= max) return 0 until count
        val start = (current - max / 2).coerceIn(0, count - max)
        return start until start + max
    }
}

/**
 * Carrusel horizontal animado de tarjetas (`HorizontalPager`): la central ocupa ~86 % del ancho, las vecinas se asoman
 * más pequeñas y tenues, y debajo va la barra para niños ◀ · puntos · "1 de 6" · ▶. Todas las tarjetas miden lo mismo
 * que la más alta, así que el contenido de abajo no salta al deslizar y ningún texto se corta.
 */
@Composable
fun <T> FluiletCarousel(
    items: List<T>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    itemKey: ((T) -> Any)? = null,
    counter: @Composable (page: Int, count: Int) -> String = { page, count -> stringResource(R.string.carousel_counter, page + 1, count) },
    content: @Composable (item: T, index: Int) -> Unit
) {
    if (items.isEmpty()) return
    val state = rememberPagerState(initialPage = initialPage.coerceIn(0, items.lastIndex)) { items.size }
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    // Alto de la tarjeta más alta medida hasta ahora: las demás se estiran a esa altura. Se vuelve a medir si cambia la
    // cantidad de tarjetas, el ancho de la pantalla (giro) o el tamaño de letra del sistema.
    var tallest by remember(items.size, window, density.fontScale) { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CarouselMotion.SidePeek),
            pageSpacing = CarouselMotion.PageGap,
            beyondViewportPageCount = (items.size - 1).coerceIn(0, CarouselMotion.MAX_PRECOMPOSED),
            key = itemKey?.let { keyOf -> { index: Int -> keyOf(items[index]) } },
            verticalAlignment = Alignment.Top
        ) { index ->
            Box(
                Modifier.fillMaxWidth()
                    .graphicsLayer {
                        val offset = state.getOffsetDistanceInPages(index)
                        val scale = CarouselMotion.scale(offset)
                        scaleX = scale
                        scaleY = scale
                        alpha = CarouselMotion.alpha(offset)
                    }
                    .onSizeChanged { if (it.height > tallest) tallest = it.height }
                    .heightIn(min = with(density) { tallest.toDp() }),
                propagateMinConstraints = true
            ) { content(items[index], index) }
        }
        if (items.size > 1) CarouselNavBar(state, counter = counter)
    }
}

/** ◀ Anterior · puntos animados + "1 de 6" · Siguiente ▶ (botones de 48 dp, cómodos para niños). */
@Composable
fun CarouselNavBar(
    state: PagerState,
    modifier: Modifier = Modifier,
    counter: @Composable (page: Int, count: Int) -> String = { page, count -> stringResource(R.string.carousel_counter, page + 1, count) }
) {
    val scope = rememberCoroutineScope()
    val current = state.currentPage
    val count = state.pageCount
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CarouselArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.carousel_previous), enabled = current > 0, size = 48.dp) {
            scope.launch { state.animateScrollToPage(current - 1) }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PageDots(current, count)
            AdaptiveSingleLineText(
                counter(current, count),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CarouselArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.carousel_next), enabled = current < count - 1, size = 48.dp) {
            scope.launch { state.animateScrollToPage(current + 1) }
        }
    }
}

/** Puntos de página: el actual se estira y toma el color principal (animado). */
@Composable
fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CarouselMotion.dotWindow(current, count).forEach { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor"
            )
            Box(Modifier.size(width = width, height = 8.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
internal fun CarouselArrow(icon: ImageVector, description: String, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description)
    }
}
