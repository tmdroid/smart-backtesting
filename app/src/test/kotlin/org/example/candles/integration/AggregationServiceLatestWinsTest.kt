package org.example.candles.integration

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregationServiceLatestWinsTest {
    @Test
    fun `latest request wins`() {
        val executor = Executors.newSingleThreadExecutor()
        val blockLatch = CountDownLatch(1)
        val successLatch = CountDownLatch(1)
        val results = ArrayList<String>()
        val errors = ArrayList<Throwable>()

        val service = AggregationService(
            executor = executor,
            runner = { _, tf, _, _, _ ->
            if (tf == Timeframe.parse("5m")) {
                blockLatch.await(1, TimeUnit.SECONDS)
            }
            listOf(dummyCandle())
        }
        )

        val dummyPath = Paths.get("/tmp/ignore.csv")
        val schema = CsvSchema()
        val format = TimestampFormat.ISO_8601_UTC

        service.aggregate(dummyPath, Timeframe.parse("5m"), schema, format, null, { _, tf ->
            results.add(tf.toString())
        }, { error ->
            errors.add(error)
        })

        service.aggregate(dummyPath, Timeframe.parse("15m"), schema, format, null, { _, tf ->
            results.add(tf.toString())
            successLatch.countDown()
        }, { error ->
            errors.add(error)
            successLatch.countDown()
        })

        blockLatch.countDown()
        assertTrue(successLatch.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("15m"), results)
        assertEquals(0, errors.size)
        service.shutdown()
    }

    private fun dummyCandle(): Candle {
        val tf = Timeframe.parse("1m")
        val start = Instant.EPOCH
        return Candle(start, start.plusMillis(tf.millis), 1.0, 1.0, 1.0, 1.0, 1L)
    }
}
