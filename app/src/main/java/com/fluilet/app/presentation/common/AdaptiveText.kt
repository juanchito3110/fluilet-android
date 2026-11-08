package com.fluilet.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** Tamaño mínimo legible al que se reduce una etiqueta antes de rendirse. */
val MinReadableSize: TextUnit = 11.sp

/**
 * Reglas de corte de línea "sin partir palabras": un salto solo es válido si cae entre palabras (espacio, signo o
 * emoji). Un salto entre dos letras o dígitos, como "Leccione / s" o "Com / prar", es una palabra partida.
 */
object WordBreaks {
    /** `true` si ningún salto de línea de [lineEnds] cae dentro de una palabra de [text]. */
    fun keepsWordsWhole(text: CharSequence, lineEnds: List<Int>): Boolean = lineEnds.all { end ->
        end <= 0 || end >= text.length || !(text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit())
    }

    /** La palabra más larga (en caracteres) de [text]: la que más ancho exige a una columna. */
    fun longestWord(text: String): String = text.split(' ', '\n').maxByOrNull { it.length }.orEmpty()
}

/**
 * Ajuste automático que busca el mayor tamaño (entre [min] y [max], de 0,5 en 0,5 sp) con el que el texto cabe en
 * su espacio, en sus renglones permitidos y SIN partir ninguna palabra. Si ni el mínimo lo logra, usa el mínimo.
 */
data class WordSafeAutoSize(val min: TextUnit, val max: TextUnit) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val minSp = min.value
        val maxSp = maxOf(max.value, minSp)
        if (fits(performLayout(constraints, text, maxSp.sp))) return maxSp.sp
        val steps = ((maxSp - minSp) / STEP).toInt()
        // Búsqueda binaria: el índice más alto (tamaño más grande) que todavía cabe.
        var low = 0
        var high = steps - 1
        var best = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (fits(performLayout(constraints, text, (minSp + mid * STEP).sp))) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return (minSp + best * STEP).sp
    }

    private fun fits(layout: TextLayoutResult): Boolean {
        if (layout.hasVisualOverflow) return false
        val ends = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
        return WordBreaks.keepsWordsWhole(layout.layoutInput.text.text, ends)
    }

    private companion object {
        const val STEP = 0.5f
    }
}

/**
 * Ancho real de cada texto con la letra con que se dibuja. OpenDyslexic es muy ancha (unos 0,8 em por letra): las
 * decisiones de acomodo (¿caben lado a lado?, ¿hay espacio para el emoji?) se toman con esta medida, nunca estimando.
 */
@Composable
fun rememberTextWidths(labels: List<String>, style: TextStyle): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, density) {
        labels.map { text -> with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() } }
    }
}

/** Sin tope de renglones: el texto baja de renglón las veces que haga falta y se lee completo, sin "...". */
const val UnlimitedLines: Int = Int.MAX_VALUE

/**
 * Texto que nunca parte una palabra a media sílaba ni se esconde tras "...": por defecto usa todos los renglones que
 * necesite ([UnlimitedLines]) y solo reduce su tamaño (hasta [minSize], 11 sp) cuando una palabra sola no cabe a lo
 * ancho. Con un tope de renglones explícito (etiquetas de una línea) también se encoge antes de recortarse.
 */
@Composable
fun AdaptiveText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = UnlimitedLines,
    minSize: TextUnit = MinReadableSize,
    textAlign: TextAlign? = null
) {
    val max = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    // El interlineado se expresa en proporción al tamaño para que acompañe la reducción.
    val ratio = if (style.lineHeight.isSpecified && style.fontSize.isSpecified) style.lineHeight.value / style.fontSize.value else null
    Text(
        text = text,
        modifier = modifier,
        color = color,
        autoSize = WordSafeAutoSize(minOf(minSize.value, max.value).sp, max),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        // Sin tope nunca hay "...". Con tope, los puntos son el último recurso tras encogerse hasta [minSize].
        overflow = if (maxLines == UnlimitedLines) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = textAlign,
        style = if (ratio != null) style.copy(lineHeight = ratio.em) else style
    )
}

/**
 * Una sola línea que se encoge antes de cortarse (botones, pestañas, chips, insignias e indicadores numéricos):
 * `maxLines = 1`, `softWrap = false` y tamaño adaptable hasta [minSize].
 */
@Composable
fun AdaptiveSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minSize: TextUnit = MinReadableSize,
    textAlign: TextAlign? = TextAlign.Center
) = AdaptiveText(text, modifier, style.copy(lineHeight = TextUnit.Unspecified), color, maxLines = 1, minSize = minSize, textAlign = textAlign)

/** Etiqueta de botón: ocupa el espacio que deja el ícono, centrada, completa y sin partir palabras. */
@Composable
fun RowScope.ButtonLabel(
    text: String,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    maxLines: Int = UnlimitedLines,
    minSize: TextUnit = MinReadableSize
) = AdaptiveText(
    text,
    Modifier.weight(1f, fill = false),
    style = style,
    maxLines = maxLines,
    minSize = minSize,
    textAlign = TextAlign.Center
)

/**
 * Pestaña con el emoji ARRIBA y la palabra DEBAJO, en una sola línea que se ajusta (12–13 sp): "Accesorios",
 * "Escenarios" y "Evolución" se leen completas aunque la pantalla sea angosta.
 */
@Composable
fun IconLabelTab(selected: Boolean, onClick: () -> Unit, emoji: String, label: String, modifier: Modifier = Modifier) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp).semantics { contentDescription = label }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            AdaptiveSingleLineText(
                label,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                minSize = 10.sp
            )
        }
    }
}
