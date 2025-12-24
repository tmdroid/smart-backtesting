package org.example.candles.engine.backtest

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DateRangeTest {
    @Test
    fun `date range validates start before end`() {
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 1))
        }
    }
}
