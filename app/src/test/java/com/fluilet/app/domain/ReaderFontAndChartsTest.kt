package com.fluilet.app.domain

import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.progress.CompletionStats
import org.junit.Assert.assertEquals
import org.junit.Test

/** Rango de letra del lector (14–28 pt, 18 por defecto) y porcentajes de la gráfica de lecciones. */
class ReaderFontAndChartsTest {

    @Test fun readingFontGoesFrom14To28WithA18Default() {
        assertEquals(14f, ReadingSettings.MIN_FONT_SIZE)
        assertEquals(28f, ReadingSettings.MAX_FONT_SIZE)
        assertEquals(18f, ReadingSettings.DEFAULT_FONT_SIZE)
        assertEquals(18f, ReadingSettings().fontSizeSp)
        // Se puede guardar la letra más pequeña: ya no hay un mínimo oculto de 22.
        assertEquals(14f, ReadingSettings(fontSizeSp = 14f).fontSizeSp)
    }

    @Test fun aMinusAndAPlusMoveOneStepAndStayInRange() {
        assertEquals(16f, ReadingSettings.smallerFont(18f))
        assertEquals(20f, ReadingSettings.largerFont(18f))
        assertEquals(14f, ReadingSettings.smallerFont(14f))
        assertEquals(14f, ReadingSettings.smallerFont(15f))
        assertEquals(28f, ReadingSettings.largerFont(28f))
        assertEquals(28f, ReadingSettings.largerFont(27f))
    }

    @Test fun withNoLessonsTheChartShowsZeroPercentNeverOneHundred() {
        val empty = CompletionStats(completed = 0, inProgress = 0)
        assertEquals(0, empty.total)
        assertEquals(0, empty.completedPercent)
        assertEquals(0, empty.inProgressPercent)
        assertEquals(0, CompletionStats.percent(0, 0))
        assertEquals(0, CompletionStats.percent(5, 0))
    }

    @Test fun percentagesComeFromTheCounts() {
        val stats = CompletionStats(completed = 3, inProgress = 1)
        assertEquals(4, stats.total)
        assertEquals(75, stats.completedPercent)
        assertEquals(25, stats.inProgressPercent)
        assertEquals(66, CompletionStats.percent(2, 3))
        assertEquals(100, CompletionStats.percent(4, 4))
        assertEquals("nunca pasa de 100", 100, CompletionStats.percent(9, 4))
        assertEquals("los negativos cuentan como 0", 0, CompletionStats(-2, 0).completedPercent)
    }
}
