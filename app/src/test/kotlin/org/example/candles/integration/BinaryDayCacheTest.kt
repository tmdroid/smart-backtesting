package org.example.candles.integration

import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BinaryDayCacheTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `saves and loads cached candles`() {
        val csvPath = tempDir.resolve("sample.csv")
        csvPath.toFile().writeText("timestamp,open,high,low,close,volume\n")

        val cache = BinaryDayCache(Timeframe.parse("1m"))
        val day = LocalDate.parse("2024-01-02")
        val candles = listOf(
            candleAt(Instant.parse("2024-01-02T00:00:00Z"), 1.0),
            candleAt(Instant.parse("2024-01-02T00:01:00Z"), 2.0)
        )

        cache.save(csvPath, CsvSchema(), TimestampFormat.ISO_8601_UTC, day, candles)
        val loaded = cache.load(csvPath, CsvSchema(), TimestampFormat.ISO_8601_UTC, day)

        requireNotNull(loaded)
        assertEquals(candles, loaded)
    }

    @Test
    fun `lists cached days`() {
        val csvPath = tempDir.resolve("sample.csv")
        csvPath.toFile().writeText("timestamp,open,high,low,close,volume\n")
        val cache = BinaryDayCache(Timeframe.parse("1m"))
        val day = LocalDate.parse("2024-01-03")
        val candles = listOf(candleAt(Instant.parse("2024-01-03T00:00:00Z"), 1.0))
        cache.save(csvPath, CsvSchema(), TimestampFormat.ISO_8601_UTC, day, candles)
        val days = cache.cachedDays(csvPath, CsvSchema(), TimestampFormat.ISO_8601_UTC)
        assertTrue(days.contains(day))
    }

    private fun candleAt(start: Instant, value: Double): Candle {
        val endExclusive = start.plusMillis(60_000L)
        return Candle(start, endExclusive, value, value, value, value, 1L)
    }
}
