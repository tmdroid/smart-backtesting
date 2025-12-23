package org.example.candles.integration

import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.Executors
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AggregationServicePrebuildCacheTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `prebuild creates day caches`() {
        val csvPath = tempDir.resolve("sample.csv")
        csvPath.toFile().writeText(
            "timestamp,open,high,low,close,volume\n" +
                "2024-01-01T00:00:00Z,1,1,1,1,1\n" +
                "2024-01-01T00:01:00Z,2,2,2,2,1\n" +
                "2024-01-02T00:00:00Z,3,3,3,3,1\n"
        )
        val schema = CsvSchema()
        val format = TimestampFormat.ISO_8601_UTC
        val service = AggregationService(Executors.newSingleThreadExecutor())
        service.prebuildDayCaches(csvPath, schema, format)

        val cache = BinaryDayCache(Timeframe.parse("1m"))
        val expected = setOf(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-02"))
        val ok = waitFor { cache.cachedDays(csvPath, schema, format).toSet() == expected }
        assertTrue(ok, "Expected cached days to be created")
        service.shutdown()
    }

    private fun waitFor(timeoutMs: Long = 1500, pollMs: Long = 50, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return true
            Thread.sleep(pollMs)
        }
        return false
    }
}
