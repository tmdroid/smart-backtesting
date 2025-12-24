package org.example.candles.integration

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.example.candles.test.DirectExecutorService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationServiceTimeframeChangeTest {
    @Test
    fun `timeframe change triggers re-aggregation`() {
        val executor = DirectExecutorService()
        val results = ArrayList<String>()
        val latch = CountDownLatch(2)
        val service = AggregationService(
            executor = executor,
            runner = { _, tf, _, _, _ ->
            when (tf) {
                Timeframe.parse("1m") -> listOf(dummyCandle())
                Timeframe.parse("5m") -> listOf(dummyCandle(), dummyCandle())
                else -> emptyList()
            }
        }
        )
        val dummyPath = Paths.get("/tmp/ignore.csv")
        val schema = CsvSchema()
        val format = TimestampFormat.ISO_8601_UTC

        service.aggregate(dummyPath, Timeframe.parse("1m"), schema, format, null, { _, tf ->
            results.add(tf.toString())
            latch.countDown()
        }, { _ ->
            latch.countDown()
        })

        service.aggregate(dummyPath, Timeframe.parse("5m"), schema, format, null, { _, tf ->
            results.add(tf.toString())
            latch.countDown()
        }, { _ ->
            latch.countDown()
        })

        latch.await(1, TimeUnit.SECONDS)
        assertEquals(listOf("1m", "5m"), results)
        service.shutdown()
    }

    private fun dummyCandle(): Candle {
        val tf = Timeframe.parse("1m")
        val start = Instant.EPOCH
        return Candle(start, start.plusMillis(tf.millis), 1.0, 1.0, 1.0, 1.0, 1L)
    }
}
