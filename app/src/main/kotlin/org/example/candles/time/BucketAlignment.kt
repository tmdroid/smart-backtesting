package org.example.candles.time

import java.time.Instant
import java.time.Duration

object BucketAlignment {
    fun floorToBoundary(instant: Instant, bucketDuration: Duration): Instant {
        val bucketMillis = bucketDuration.toMillis()
        val epochMillis = instant.toEpochMilli()
        val floored = Math.floorDiv(epochMillis, bucketMillis) * bucketMillis
        return Instant.ofEpochMilli(floored)
    }
}
