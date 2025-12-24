package org.example.candles.integration

import java.nio.file.Paths
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregationServiceDayFilterTest {
    @Test
    fun `filters by day before aggregation`() {
        val executor = Executors.newSingleThreadExecutor()
        val service = AggregationService(executor)
        val latch = CountDownLatch(1)
        val path = Paths.get(requireNotNull(javaClass.getResource("/fixtures/two-days-1m.csv")).toURI())
        val results = ArrayList<Int>()

        service.aggregate(
            path = path,
            timeframe = Timeframe.parse("1m"),
            schema = CsvSchema(),
            timestampFormat = TimestampFormat.ISO_8601_UTC,
            day = LocalDate.parse("2024-01-01"),
            onSuccess = { candles, _ ->
                results.add(candles.size)
                latch.countDown()
            },
            onError = { error ->
                throw AssertionError("Unexpected error: ${error.message}")
            }
        )

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(2, results.single())
        service.shutdown()
    }
}
