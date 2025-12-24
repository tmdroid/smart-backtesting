package org.example.candles.aggregation

import java.time.Duration
import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.policy.AggregationPolicy
import org.example.candles.policy.GapPolicy
import org.example.candles.policy.PolicyConfigurationException
import org.example.candles.policy.FlushPolicy
import org.example.candles.policy.ValidationPolicy
import org.example.candles.time.BucketAlignment

class CandleAggregator(
    sourceTimeframe: Timeframe,
    targetTimeframe: Timeframe,
    private val policy: AggregationPolicy = AggregationPolicy()
) {
    private val targetDuration: Duration = targetTimeframe.toDuration()
    private val sourceMillis: Long = sourceTimeframe.millis
    private val targetMillis: Long = targetTimeframe.millis
    private val divisible: Boolean = targetMillis % sourceMillis == 0L
    private val expectedCount: Long = if (divisible) targetMillis / sourceMillis else -1L

    private var lastStart: Instant? = null

    private var bucketStart: Instant? = null
    private var bucketEndExclusive: Instant? = null
    private var bucketOpen: Double = 0.0
    private var bucketHigh: Double = 0.0
    private var bucketLow: Double = 0.0
    private var bucketClose: Double = 0.0
    private var bucketVolume: Long = 0L
    private var bucketCount: Long = 0L
    private var internalGap: Boolean = false
    private var expectedNextStart: Instant? = null

    init {
        if (policy.gapPolicy == GapPolicy.DROP_BUCKET_IF_INCOMPLETE && !divisible) {
            throw PolicyConfigurationException(
                "GapPolicy.DROP_BUCKET_IF_INCOMPLETE requires divisible timeframes: source=$sourceTimeframe target=$targetTimeframe"
            )
        }
    }

    fun onCandle(candle: Candle): Candle? {
        validateCandle(candle)
        validateOrdering(candle)

        val candleBucketStart = BucketAlignment.floorToBoundary(candle.start, targetDuration)
        val currentBucketStart = bucketStart

        if (currentBucketStart == null) {
            startBucket(candle, candleBucketStart)
            return null
        }

        if (candleBucketStart == currentBucketStart) {
            updateBucket(candle)
            return null
        }

        val completed = finalizeBucketForGapPolicy()
        startBucket(candle, candleBucketStart)
        return completed
    }

    fun flush(): Candle? {
        if (bucketStart == null) return null
        val result = finalizeBucketForFlushPolicy()
        clearBucket()
        return result
    }

    private fun startBucket(candle: Candle, newBucketStart: Instant) {
        val newBucketEndExclusive = newBucketStart.plusMillis(targetMillis)
        bucketStart = newBucketStart
        bucketEndExclusive = newBucketEndExclusive
        bucketOpen = candle.open
        bucketHigh = candle.high
        bucketLow = candle.low
        bucketClose = candle.close
        bucketVolume = candle.volume
        bucketCount = 1L
        internalGap = false
        expectedNextStart = candle.start.plusMillis(sourceMillis)
    }

    private fun updateBucket(candle: Candle) {
        val expected = expectedNextStart
        if (expected != null && candle.start != expected) {
            internalGap = true
        }
        bucketHigh = maxOf(bucketHigh, candle.high)
        bucketLow = minOf(bucketLow, candle.low)
        bucketClose = candle.close
        bucketVolume = safeAddVolume(bucketVolume, candle.volume)
        bucketCount += 1L
        expectedNextStart = candle.start.plusMillis(sourceMillis)
    }

    private fun finalizeBucketForGapPolicy(): Candle? {
        val incomplete = isIncomplete()
        val candle = buildBucketCandle()
        return when (policy.gapPolicy) {
            GapPolicy.KEEP_PARTIAL -> candle
            GapPolicy.DROP_BUCKET_IF_INCOMPLETE -> if (incomplete) null else candle
        }
    }

    private fun finalizeBucketForFlushPolicy(): Candle? {
        val incomplete = isIncomplete()
        val candle = buildBucketCandle()
        return when (policy.flushPolicy) {
            FlushPolicy.EMIT_PARTIAL -> candle
            FlushPolicy.DROP_PARTIAL -> if (incomplete) null else candle
        }
    }

    private fun buildBucketCandle(): Candle {
        val start = bucketStart ?: throw IllegalStateException("No active bucket to build")
        val endExclusive = bucketEndExclusive ?: throw IllegalStateException("No active bucket end")
        return Candle(
            start = start,
            endExclusive = endExclusive,
            open = bucketOpen,
            high = bucketHigh,
            low = bucketLow,
            close = bucketClose,
            volume = bucketVolume
        )
    }

    private fun clearBucket() {
        bucketStart = null
        bucketEndExclusive = null
        bucketOpen = 0.0
        bucketHigh = 0.0
        bucketLow = 0.0
        bucketClose = 0.0
        bucketVolume = 0L
        bucketCount = 0L
        internalGap = false
        expectedNextStart = null
    }

    private fun isIncomplete(): Boolean {
        val countMismatch = if (divisible) bucketCount != expectedCount else false
        return internalGap || countMismatch
    }

    private fun validateCandle(candle: Candle) {
        val expectedEnd = candle.start.plusMillis(sourceMillis)
        if (candle.endExclusive != expectedEnd) {
            throw CandleValidationException(
                "Candle duration mismatch: start=${candle.start} end=${candle.endExclusive} expectedEnd=$expectedEnd"
            )
        }
        if (policy.validationPolicy == ValidationPolicy.STRICT) {
            val maxOpenClose = maxOf(candle.open, candle.close)
            val minOpenClose = minOf(candle.open, candle.close)
            if (candle.high < maxOpenClose) {
                throw CandleValidationException("High is below open/close at ${candle.start}")
            }
            if (candle.low > minOpenClose) {
                throw CandleValidationException("Low is above open/close at ${candle.start}")
            }
            if (candle.high < candle.low) {
                throw CandleValidationException("High is below low at ${candle.start}")
            }
        }
    }

    private fun validateOrdering(candle: Candle) {
        val previous = lastStart
        if (previous != null) {
            if (candle.start == previous) {
                throw DuplicateTimestampException("Duplicate timestamp: ${candle.start}")
            }
            if (candle.start.isBefore(previous)) {
                throw OrderingViolationException("Out-of-order timestamp: ${candle.start} before $previous")
            }
        }
        lastStart = candle.start
    }

    private fun safeAddVolume(current: Long, delta: Long): Long {
        return try {
            Math.addExact(current, delta)
        } catch (_: ArithmeticException) {
            throw CandleValidationException("Volume overflow while aggregating")
        }
    }
}
