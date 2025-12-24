package org.example.candles.aggregation

import java.time.Duration
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.policy.AggregationPolicy

fun aggregate(
    source: Sequence<Candle>,
    sourceTimeframe: Timeframe,
    targetTimeframe: Timeframe,
    policy: AggregationPolicy = AggregationPolicy()
): Sequence<Candle> = sequence {
    val aggregator = CandleAggregator(sourceTimeframe, targetTimeframe, policy)
    for (candle in source) {
        val emitted = aggregator.onCandle(candle)
        if (emitted != null) {
            yield(emitted)
        }
    }
    val flushed = aggregator.flush()
    if (flushed != null) {
        yield(flushed)
    }
}

fun aggregate(
    source: Sequence<Candle>,
    targetTimeframe: Timeframe,
    policy: AggregationPolicy = AggregationPolicy()
): Sequence<Candle> = sequence {
    val iterator = source.iterator()
    if (!iterator.hasNext()) {
        return@sequence
    }
    val first = iterator.next()
    val sourceTimeframe = try {
        Timeframe.fromDuration(Duration.between(first.start, first.endExclusive))
    } catch (_: IllegalArgumentException) {
        throw CandleValidationException("Invalid source timeframe inferred from first candle")
    }
    val aggregator = CandleAggregator(sourceTimeframe, targetTimeframe, policy)
    val firstEmitted = aggregator.onCandle(first)
    if (firstEmitted != null) {
        yield(firstEmitted)
    }
    while (iterator.hasNext()) {
        val candle = iterator.next()
        val emitted = aggregator.onCandle(candle)
        if (emitted != null) {
            yield(emitted)
        }
    }
    val flushed = aggregator.flush()
    if (flushed != null) {
        yield(flushed)
    }
}
