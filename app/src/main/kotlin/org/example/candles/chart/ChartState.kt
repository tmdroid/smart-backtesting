package org.example.candles.chart

import org.example.candles.domain.Candle

class ChartState {
    var candles: List<Candle> = emptyList()
        private set

    var visibleStartIndex: Int = 0
    var visibleCount: Int = 0
    var candleWidthPx: Double = 6.0

    val minCandleWidthPx: Double = 2.0
    val maxVisibleCandles: Int = 5000

    val totalCandles: Int
        get() = candles.size

    fun setCandles(newCandles: List<Candle>) {
        candles = newCandles
        visibleStartIndex = 0
    }

    fun updateVisibleCount(canvasWidth: Double) {
        if (canvasWidth <= 0) {
            visibleCount = 0
            return
        }
        val minWidthForMaxVisible = canvasWidth / maxVisibleCandles
        candleWidthPx = maxOf(minCandleWidthPx, maxOf(candleWidthPx, minWidthForMaxVisible))
        val countByWidth = (canvasWidth / candleWidthPx).toInt().coerceAtLeast(1)
        visibleCount = minOf(countByWidth, maxVisibleCandles, totalCandles.coerceAtLeast(1))
        if (totalCandles == 0) {
            visibleCount = 0
        }
    }

    fun defaultStartIndex(): Int {
        if (totalCandles <= visibleCount) return 0
        return totalCandles - visibleCount
    }

    fun maxStartIndex(): Int {
        if (totalCandles == 0) return 0
        return (totalCandles - visibleCount).coerceAtLeast(0)
    }
}
