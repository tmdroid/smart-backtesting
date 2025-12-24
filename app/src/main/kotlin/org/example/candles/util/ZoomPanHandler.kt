package org.example.candles.util

import javafx.scene.input.MouseButton
import javafx.scene.input.ScrollEvent
import kotlin.math.roundToInt
import org.example.candles.chart.CandlestickCanvas
import org.example.candles.chart.ChartState

class ZoomPanHandler(
    private val canvas: CandlestickCanvas,
    private val state: ChartState,
    private val onChange: () -> Unit
) {
    private var dragStartX = 0.0
    private var startIndexAtDrag = 0
    private var axisZoomMode: AxisZoomMode? = null
    private var axisZoomStartY = 0.0
    private var startCandleWidth = 0.0
    private var startYZoom = 1.0

    init {
        canvas.addEventHandler(ScrollEvent.SCROLL) { event ->
            handleZoom(event)
        }
        canvas.setOnMousePressed { event ->
            if (event.button == MouseButton.PRIMARY) {
                dragStartX = event.x
                startIndexAtDrag = state.visibleStartIndex
                axisZoomMode = when {
                    canvas.isInXAxisArea(event.y) -> AxisZoomMode.X
                    canvas.isInYAxisArea(event.x) -> AxisZoomMode.Y
                    else -> null
                }
                axisZoomStartY = event.y
                startCandleWidth = state.candleWidthPx
                startYZoom = state.yZoomFactor
            }
        }
        canvas.setOnMouseDragged { event ->
            if (event.button == MouseButton.PRIMARY) {
                when (axisZoomMode) {
                    AxisZoomMode.X -> handleAxisZoomX(event.y)
                    AxisZoomMode.Y -> handleAxisZoomY(event.y)
                    null -> {
                        val dx = event.x - dragStartX
                        val deltaCandles = (dx / state.candleWidthPx).roundToInt()
                        val newStart = (startIndexAtDrag - deltaCandles).coerceIn(0, state.maxStartIndex())
                        if (newStart != state.visibleStartIndex) {
                            state.visibleStartIndex = newStart
                            onChange()
                        }
                    }
                }
            }
        }
    }

    private fun handleZoom(event: ScrollEvent) {
        if (state.totalCandles == 0) return
        val factor = if (event.deltaY > 0) 1.015 else 0.985
        val drawWidth = canvas.drawableWidth()
        val focusX = event.x
        val focusIndex = focusIndexFromX(focusX)
        val minWidthForMaxVisible = drawWidth / state.maxVisibleCandles
        val newWidth = (state.candleWidthPx * factor).coerceAtLeast(state.minCandleWidthPx)
        val minWidthForAll = if (state.totalCandles <= state.maxVisibleCandles && state.totalCandles > 0) {
            drawWidth / state.totalCandles
        } else {
            0.0
        }
        val minWidth = maxOf(minWidthForMaxVisible, minWidthForAll)
        state.candleWidthPx = maxOf(newWidth, minWidth)
        state.updateVisibleCount(drawWidth)
        val newStart = startIndexForFocus(focusX, focusIndex)
        state.visibleStartIndex = newStart.coerceIn(0, state.maxStartIndex())
        onChange()
        event.consume()
    }

    private fun handleAxisZoomX(currentY: Double) {
        val drawWidth = canvas.drawableWidth()
        val delta = axisZoomStartY - currentY
        val factor = 1.0 + (delta / 600.0)
        val focusX = dragStartX
        val focusIndex = focusIndexFromX(focusX)
        val targetWidth = (startCandleWidth * factor).coerceAtLeast(state.minCandleWidthPx)
        val minWidthForMaxVisible = drawWidth / state.maxVisibleCandles
        val minWidthForAll = if (state.totalCandles <= state.maxVisibleCandles && state.totalCandles > 0) {
            drawWidth / state.totalCandles
        } else {
            0.0
        }
        val minWidth = maxOf(minWidthForMaxVisible, minWidthForAll)
        state.candleWidthPx = maxOf(targetWidth, minWidth)
        state.updateVisibleCount(drawWidth)
        val newStart = startIndexForFocus(focusX, focusIndex)
        state.visibleStartIndex = newStart.coerceIn(0, state.maxStartIndex())
        onChange()
    }

    private fun handleAxisZoomY(currentY: Double) {
        val delta = axisZoomStartY - currentY
        val factor = 1.0 + (delta / 600.0)
        state.yZoomFactor = (startYZoom * factor).coerceIn(1.0, 12.0)
        onChange()
    }

    private fun focusIndexFromX(x: Double): Int {
        val relative = (x - canvas.plotLeft()) / state.candleWidthPx
        val offset = if (relative.isFinite()) relative else (state.visibleCount / 2.0)
        return (state.visibleStartIndex + offset).roundToInt().coerceIn(0, state.totalCandles - 1)
    }

    private fun startIndexForFocus(x: Double, focusIndex: Int): Int {
        val offset = (x - canvas.plotLeft()) / state.candleWidthPx
        val normalized = if (offset.isFinite()) offset else (state.visibleCount / 2.0)
        return (focusIndex - normalized).roundToInt()
    }

    private enum class AxisZoomMode { X, Y }
}
