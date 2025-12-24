package org.example.candles.chart

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javafx.scene.paint.Color
import org.example.candles.domain.Candle

class CandlestickCanvas : Canvas(800.0, 600.0) {
    private val leftPadding = 20.0
    private val rightPadding = 80.0
    private val topPadding = 20.0
    private val bottomPadding = 30.0
    private var timeZone: ZoneId = ZoneId.of("America/New_York")
    private var crosshairX: Double? = null
    private var crosshairY: Double? = null
    private var lastState: ChartState? = null
    private var lastVisible: List<Candle> = emptyList()
    private var lastYMin: Double = 0.0
    private var lastYMax: Double = 1.0

    init {
        setOnMouseMoved { event ->
            crosshairX = event.x
            crosshairY = event.y
            lastState?.let { render(it) }
        }
        setOnMouseExited {
            crosshairX = null
            crosshairY = null
            lastState?.let { render(it) }
        }
    }

    override fun isResizable(): Boolean = true

    override fun prefWidth(height: Double): Double = width

    override fun prefHeight(width: Double): Double = height

    fun drawableWidth(): Double = (width - leftPadding - rightPadding).coerceAtLeast(0.0)

    fun drawableHeight(): Double = (height - topPadding - bottomPadding).coerceAtLeast(0.0)

    fun plotLeft(): Double = leftPadding

    fun plotRight(): Double = width - rightPadding

    fun plotTop(): Double = topPadding

    fun plotBottom(): Double = height - bottomPadding

    fun isInPlotArea(x: Double, y: Double): Boolean =
        x in plotLeft()..plotRight() && y in plotTop()..plotBottom()

    fun isInXAxisArea(y: Double): Boolean = y >= plotBottom()

    fun isInYAxisArea(x: Double): Boolean = x >= plotRight()

    fun setTimeZone(zoneId: ZoneId) {
        timeZone = zoneId
    }

    fun render(state: ChartState) {
        lastState = state
        val gc = graphicsContext2D
        gc.clearRect(0.0, 0.0, width, height)

        if (state.totalCandles == 0 || state.visibleCount == 0) {
            return
        }

        val drawWidth = drawableWidth()
        val drawHeight = drawableHeight()
        if (drawWidth <= 0 || drawHeight <= 0) return

        val startIndex = state.visibleStartIndex
        val endIndex = (startIndex + state.visibleCount).coerceAtMost(state.totalCandles)
        val visible = state.candles.subList(startIndex, endIndex)
        lastVisible = visible

        val (minPrice, maxPrice) = minMaxPrice(visible)
        val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
        val padding = range * 0.05
        val baseMin = minPrice - padding
        val baseMax = maxPrice + padding
        val center = (baseMin + baseMax) / 2.0
        val halfRange = (baseMax - baseMin) / 2.0
        val scaledHalf = (halfRange / state.yZoomFactor).coerceAtLeast(1e-6)
        val yMin = center - scaledHalf
        val yMax = center + scaledHalf
        lastYMin = yMin
        lastYMax = yMax

        val candleWidth = state.candleWidthPx
        val bodyWidth = maxOf(1.0, candleWidth * 0.7)

        drawGrid(gc, yMin, yMax)
        drawTimeAxis(gc, visible, candleWidth)
        drawOverlays(gc, state, yMin, yMax)

        for ((idx, candle) in visible.withIndex()) {
            val xCenter = leftPadding + idx * candleWidth + candleWidth / 2
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

        drawCrosshair(gc)
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
        return topPadding + (1 - normalized) * drawableHeight()
    }

    private fun drawGrid(gc: GraphicsContext, min: Double, max: Double) {
        val gridColor = Color.web("#E0E0E0")
        gc.stroke = gridColor
        gc.fill = Color.web("#616161")

        val horizontalLines = 6
        val verticalLines = 6
        val drawW = drawableWidth()
        val drawH = drawableHeight()

        for (i in 0..horizontalLines) {
            val y = topPadding + (i.toDouble() / horizontalLines) * drawH
            gc.strokeLine(leftPadding, y, leftPadding + drawW, y)
            val value = max - (i.toDouble() / horizontalLines) * (max - min)
            gc.fillText(String.format("%.2f", value), leftPadding + drawW + 6.0, y + 4.0)
        }

        for (i in 0..verticalLines) {
            val x = leftPadding + (i.toDouble() / verticalLines) * drawW
            gc.strokeLine(x, topPadding, x, topPadding + drawH)
        }

        gc.stroke = Color.web("#BDBDBD")
        gc.strokeRect(leftPadding, topPadding, drawW, drawH)
    }

    private fun drawTimeAxis(gc: GraphicsContext, candles: List<Candle>, candleWidth: Double) {
        if (candles.isEmpty()) return
        val drawW = drawableWidth()
        val labelCount = 6
        val step = (candles.size / labelCount).coerceAtLeast(1)
        val y = topPadding + drawableHeight() + 16.0
        gc.fill = Color.web("#616161")
        for (i in candles.indices step step) {
            val candle = candles[i]
            val x = leftPadding + i * candleWidth
            if (x > leftPadding + drawW) break
            gc.strokeLine(x, topPadding + drawableHeight(), x, topPadding + drawableHeight() + 4.0)
            gc.fillText(timeFormatter().format(candle.start), x - 12.0, y)
        }
    }

    private fun drawCrosshair(gc: GraphicsContext) {
        val x = crosshairX ?: return
        val y = crosshairY ?: return
        val drawW = drawableWidth()
        val drawH = drawableHeight()
        if (x < leftPadding || x > leftPadding + drawW) return
        if (y < topPadding || y > topPadding + drawH) return
        gc.stroke = Color.web("#9E9E9E")
        gc.lineWidth = 1.0
        gc.strokeLine(x, topPadding, x, topPadding + drawH)
        gc.strokeLine(leftPadding, y, leftPadding + drawW, y)
        gc.lineWidth = 1.0
        drawCrosshairLabels(gc, x, y)
    }

    private fun drawCrosshairLabels(gc: GraphicsContext, x: Double, y: Double) {
        if (lastVisible.isEmpty()) return
        val index = ((x - leftPadding) / (lastState?.candleWidthPx ?: 1.0)).toInt()
            .coerceIn(0, lastVisible.size - 1)
        val candle = lastVisible[index]
        val timeText = timeFormatter().format(candle.start)
        val price = priceForY(y)
        val priceText = String.format("%.2f", price)

        val timeY = plotBottom() + 18.0
        val timeWidth = 52.0
        val timeHeight = 16.0
        val timeX = (x - timeWidth / 2).coerceIn(leftPadding, plotRight() - timeWidth)
        gc.fill = Color.web("#FFFFFF")
        gc.stroke = Color.web("#9E9E9E")
        gc.fillRect(timeX, timeY - timeHeight + 2.0, timeWidth, timeHeight)
        gc.strokeRect(timeX, timeY - timeHeight + 2.0, timeWidth, timeHeight)
        gc.fill = Color.web("#424242")
        gc.fillText(timeText, timeX + 4.0, timeY)

        val priceWidth = 64.0
        val priceHeight = 16.0
        val priceX = plotRight() + 6.0
        val priceY = (y + 4.0).coerceIn(plotTop() + priceHeight, plotBottom())
        gc.fill = Color.web("#FFFFFF")
        gc.stroke = Color.web("#9E9E9E")
        gc.fillRect(priceX, priceY - priceHeight + 2.0, priceWidth, priceHeight)
        gc.strokeRect(priceX, priceY - priceHeight + 2.0, priceWidth, priceHeight)
        gc.fill = Color.web("#424242")
        gc.fillText(priceText, priceX + 4.0, priceY)
    }

    private fun priceForY(y: Double): Double {
        val normalized = ((plotBottom() - y) / drawableHeight()).coerceIn(0.0, 1.0)
        return lastYMin + normalized * (lastYMax - lastYMin)
    }

    private fun timeFormatter(): DateTimeFormatter {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(timeZone)
    }

    private fun drawOverlays(
        gc: GraphicsContext,
        state: ChartState,
        yMin: Double,
        yMax: Double
    ) {
        val overlay = state.overlay ?: return
        val visibleStart = state.visibleStartIndex
        val visibleEnd = (visibleStart + state.visibleCount).coerceAtMost(state.totalCandles) - 1
        val candleWidth = state.candleWidthPx

        overlay.rangeBox?.let { box ->
            val start = box.startIndexInclusive.coerceIn(visibleStart, visibleEnd)
            val end = box.endIndexInclusive.coerceIn(visibleStart, visibleEnd)
            if (end >= start) {
                val xStart = leftPadding + (start - visibleStart) * candleWidth
                val xEnd = leftPadding + (end - visibleStart + 1) * candleWidth
                val topY = yForPrice(box.high, yMin, yMax)
                val bottomY = yForPrice(box.low, yMin, yMax)
                gc.fill = Color.web("#F9A825", 0.15)
                gc.stroke = Color.web("#FB8C00", 0.8)
                gc.fillRect(xStart, topY, xEnd - xStart, bottomY - topY)
                gc.strokeRect(xStart, topY, xEnd - xStart, bottomY - topY)
            }
        }

        overlay.riskRewardBox?.let { box ->
            val start = box.startIndexInclusive.coerceIn(visibleStart, visibleEnd)
            val end = box.endIndexInclusive.coerceIn(visibleStart, visibleEnd)
            if (end >= start) {
                val xStart = leftPadding + (start - visibleStart) * candleWidth
                val xEnd = leftPadding + (end - visibleStart + 1) * candleWidth
                val entryY = yForPrice(box.entry, yMin, yMax)
                val stopY = yForPrice(box.stop, yMin, yMax)
                val targetY = yForPrice(box.target, yMin, yMax)

                val riskTop = minOf(entryY, stopY)
                val riskBottom = maxOf(entryY, stopY)
                gc.fill = Color.web("#C62828", 0.18)
                gc.fillRect(xStart, riskTop, xEnd - xStart, riskBottom - riskTop)

                val rewardTop = minOf(entryY, targetY)
                val rewardBottom = maxOf(entryY, targetY)
                gc.fill = Color.web("#2E7D32", 0.18)
                gc.fillRect(xStart, rewardTop, xEnd - xStart, rewardBottom - rewardTop)

                gc.stroke = Color.web("#424242", 0.6)
                gc.strokeLine(xStart, entryY, xEnd, entryY)
            }
        }
    }
}
