package org.example.candles.chart

import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import org.example.candles.domain.Candle

class CandlestickCanvas : Canvas(800.0, 600.0) {
    override fun isResizable(): Boolean = true

    override fun prefWidth(height: Double): Double = width

    override fun prefHeight(width: Double): Double = height

    override fun resize(width: Double, height: Double) {
        this.width = width
        this.height = height
    }

    fun render(state: ChartState) {
        val gc = graphicsContext2D
        gc.clearRect(0.0, 0.0, width, height)

        if (state.totalCandles == 0 || state.visibleCount == 0) {
            return
        }

        val startIndex = state.visibleStartIndex
        val endIndex = (startIndex + state.visibleCount).coerceAtMost(state.totalCandles)
        val visible = state.candles.subList(startIndex, endIndex)

        val (minPrice, maxPrice) = minMaxPrice(visible)
        val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
        val padding = range * 0.05
        val yMin = minPrice - padding
        val yMax = maxPrice + padding

        val candleWidth = state.candleWidthPx
        val bodyWidth = maxOf(1.0, candleWidth * 0.7)

        for ((idx, candle) in visible.withIndex()) {
            val xCenter = idx * candleWidth + candleWidth / 2
            val openY = yForPrice(candle.open, yMin, yMax)
            val closeY = yForPrice(candle.close, yMin, yMax)
            val highY = yForPrice(candle.high, yMin, yMax)
            val lowY = yForPrice(candle.low, yMin, yMax)

            val isUp = candle.close >= candle.open
            val color = if (isUp) Color.web("#2E7D32") else Color.web("#C62828")
            gc.stroke = color
            gc.fill = color

            gc.strokeLine(xCenter, highY, xCenter, lowY)

            val topY = minOf(openY, closeY)
            val bottomY = maxOf(openY, closeY)
            val bodyHeight = maxOf(1.0, bottomY - topY)
            gc.fillRect(xCenter - bodyWidth / 2, topY, bodyWidth, bodyHeight)
        }
    }

    private fun minMaxPrice(candles: List<Candle>): Pair<Double, Double> {
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        for (candle in candles) {
            min = minOf(min, candle.low)
            max = maxOf(max, candle.high)
        }
        if (min == Double.POSITIVE_INFINITY) {
            min = 0.0
            max = 1.0
        }
        return min to max
    }

    private fun yForPrice(price: Double, min: Double, max: Double): Double {
        val normalized = (price - min) / (max - min)
        return height - normalized * height
    }
}
