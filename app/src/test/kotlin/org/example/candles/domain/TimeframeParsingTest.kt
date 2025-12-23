package org.example.candles.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TimeframeParsingTest {
    @Test
    fun `parses valid timeframes`() {
        assertEquals(60_000L, Timeframe.parse("1m").millis)
        assertEquals(300_000L, Timeframe.parse("5m").millis)
        assertEquals(3_600_000L, Timeframe.parse("1h").millis)
        assertEquals(86_400_000L, Timeframe.parse("1d").millis)
        assertEquals(420_000L, Timeframe.parse("7m").millis)
        assertEquals(5_400_000L, Timeframe.parse("90m").millis)
    }

    @Test
    fun `rejects invalid timeframes`() {
        assertThrows(IllegalArgumentException::class.java) { Timeframe.parse("0m") }
        assertThrows(IllegalArgumentException::class.java) { Timeframe.parse("5x") }
        assertThrows(IllegalArgumentException::class.java) { Timeframe.parse("m") }
    }
}
