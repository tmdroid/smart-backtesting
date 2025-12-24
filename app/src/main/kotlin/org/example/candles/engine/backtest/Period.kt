package org.example.candles.engine.backtest

import java.time.LocalDate
import java.time.YearMonth

sealed interface Period {
    fun toDateRanges(): List<DateRange>
}

data class CustomDateRange(val start: LocalDate, val end: LocalDate) : Period {
    override fun toDateRanges(): List<DateRange> = listOf(DateRange(start, end))
}

data class SingleMonth(val year: Int, val month: Int) : Period {
    override fun toDateRanges(): List<DateRange> {
        val ym = YearMonth.of(year, month)
        return listOf(DateRange(ym.atDay(1), ym.atEndOfMonth()))
    }
}

data class SingleYear(val year: Int) : Period {
    override fun toDateRanges(): List<DateRange> {
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        return listOf(DateRange(start, end))
    }
}

data class MonthRange(val start: YearMonth, val endInclusive: YearMonth) : Period {
    override fun toDateRanges(): List<DateRange> {
        require(!endInclusive.isBefore(start)) { "MonthRange endInclusive must be >= start" }
        val ranges = mutableListOf<DateRange>()
        var current = start
        while (!current.isAfter(endInclusive)) {
            ranges.add(DateRange(current.atDay(1), current.atEndOfMonth()))
            current = current.plusMonths(1)
        }
        return ranges
    }
}
