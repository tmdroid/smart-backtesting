package org.example.candles.io

import java.nio.file.Path
import java.time.Instant
import org.example.candles.domain.Timeframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CsvCandleSourceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `parses csv with header`() {
        val file = tempDir.resolve("candles.csv")
        file.toFile().writeText(
            "timestamp,open,high,low,close,volume\n" +
                "2024-01-01T00:00:00Z,1,2,0.5,1.5,10\n"
        )
        val source = CsvCandleSource(
            path = file,
            sourceTimeframe = Timeframe.parse("1m"),
            timestampFormat = TimestampFormat.ISO_8601_UTC
        )
        val candles = source.stream().toList()
        assertEquals(1, candles.size)
        val candle = candles[0]
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), candle.start)
        assertEquals(Instant.parse("2024-01-01T00:01:00Z"), candle.endExclusive)
        assertEquals(1.0, candle.open)
        assertEquals(2.0, candle.high)
        assertEquals(0.5, candle.low)
        assertEquals(1.5, candle.close)
        assertEquals(10L, candle.volume)
    }

    @Test
    fun `rejects missing columns`() {
        val file = tempDir.resolve("missing.csv")
        file.toFile().writeText("timestamp,open,high,low,close\n2024-01-01T00:00:00Z,1,2,0,1,10\n")
        val source = CsvCandleSource(
            path = file,
            sourceTimeframe = Timeframe.parse("1m"),
            timestampFormat = TimestampFormat.ISO_8601_UTC
        )
        assertThrows(CsvParseException::class.java) {
            source.stream().first()
        }
    }

    @Test
    fun `rejects unknown columns`() {
        val file = tempDir.resolve("unknown.csv")
        file.toFile().writeText("timestamp,open,high,low,close,volume,extra\n2024-01-01T00:00:00Z,1,2,0,1,10,0\n")
        val source = CsvCandleSource(
            path = file,
            sourceTimeframe = Timeframe.parse("1m"),
            timestampFormat = TimestampFormat.ISO_8601_UTC
        )
        assertThrows(CsvParseException::class.java) {
            source.stream().first()
        }
    }
}
