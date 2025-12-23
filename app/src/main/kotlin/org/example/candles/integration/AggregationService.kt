package org.example.candles.integration

import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import org.example.candles.aggregation.aggregate
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvCandleSource
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat

class AggregationService(
    private val executor: ExecutorService,
    private val runner: (Path, Timeframe, CsvSchema, TimestampFormat) -> List<Candle> = ::defaultRunner
) {
    private val requestId = AtomicLong(0)
    private var activeFuture: Future<*>? = null

    fun aggregate(
        path: Path,
        timeframe: Timeframe,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        onSuccess: (List<Candle>, Timeframe) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val id = requestId.incrementAndGet()
        activeFuture?.cancel(true)
        activeFuture = executor.submit {
            try {
                val result = runner(path, timeframe, schema, timestampFormat)
                if (requestId.get() == id) {
                    onSuccess(result, timeframe)
                }
            } catch (ex: CancellationException) {
                // Ignore canceled tasks
            } catch (ex: Exception) {
                if (requestId.get() == id) {
                    onError(ex)
                }
            }
        }
    }

    fun shutdown() {
        activeFuture?.cancel(true)
        executor.shutdownNow()
    }

    companion object {
        private fun defaultRunner(
            path: Path,
            timeframe: Timeframe,
            schema: CsvSchema,
            timestampFormat: TimestampFormat
        ): List<Candle> {
            val sourceTimeframe = Timeframe.parse("1m")
            val source = CsvCandleSource(
                path,
                sourceTimeframe,
                timestampFormat = timestampFormat,
                schema = schema
            )
            val results = ArrayList<Candle>()
            for (candle in aggregate(source.stream(), sourceTimeframe, timeframe)) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Aggregation canceled")
                }
                results.add(candle)
            }
            return results
        }
    }
}
