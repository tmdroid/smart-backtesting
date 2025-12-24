package org.example.candles.engine.backtest

import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PeriodTest {
    @Test
    fun `single month expands to full month`() {
        val ranges = SingleMonth(2025, 11).toDateRanges()
        assertEquals(1, ranges.size)
        assertEquals(LocalDate.of(2025, 11, 1), ranges[0].start)
        assertEquals(LocalDate.of(2025, 11, 30), ranges[0].endInclusive)
    }

    @Test
    fun `single year expands to full year`() {
        val ranges = SingleYear(2024).toDateRanges()
        assertEquals(1, ranges.size)
        assertEquals(LocalDate.of(2024, 1, 1), ranges[0].start)
        assertEquals(LocalDate.of(2024, 12, 31), ranges[0].endInclusive)
    }

    @Test
    fun `month range expands across year boundary`() {
        val ranges = MonthRange(YearMonth.of(2024, 12), YearMonth.of(2025, 2)).toDateRanges()
        assertEquals(3, ranges.size)
        assertEquals(LocalDate.of(2024, 12, 1), ranges[0].start)
        assertEquals(LocalDate.of(2024, 12, 31), ranges[0].endInclusive)
        assertEquals(LocalDate.of(2025, 1, 1), ranges[1].start)
        assertEquals(LocalDate.of(2025, 1, 31), ranges[1].endInclusive)
        assertEquals(LocalDate.of(2025, 2, 1), ranges[2].start)
        assertEquals(LocalDate.of(2025, 2, 28), ranges[2].endInclusive)
    }
}
