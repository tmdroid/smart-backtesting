package org.example.candles.engine.backtest

import java.time.LocalDate

/**
 * Inclusive LocalDate range for backtests.
 */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate
) {
    init {
        require(!endInclusive.isBefore(start)) { "DateRange endInclusive must be >= start" }
    }
}
