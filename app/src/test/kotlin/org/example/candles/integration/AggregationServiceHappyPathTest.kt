package org.example.candles.integration

import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregationServiceHappyPathTest {
    @Test
    fun `aggregates csv with service`() {
        val executor = Executors.newSingleThreadExecutor()
        val service = AggregationService(executor)
        val latch = CountDownLatch(1)
        val path = Paths.get(requireNotNull(javaClass.getResource("/fixtures/sample-1m.csv")).toURI())
        val results = ArrayList<Int>()
        val errors = ArrayList<Throwable>()

        service.aggregate(
            path = path,
            timeframe = Timeframe.parse("5m"),
            schema = CsvSchema(),
            timestampFormat = org.example.candles.io.TimestampFormat.ISO_8601_UTC,
            onSuccess = { candles, _ ->
                results.add(candles.size)
                latch.countDown()
            },
            onError = { error ->
                errors.add(error)
                latch.countDown()
            }
        )

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(0, errors.size)
        assertEquals(1, results.single())
        service.shutdown()
    }
}
