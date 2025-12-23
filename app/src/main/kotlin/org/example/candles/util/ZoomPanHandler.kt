package org.example.candles.util

import javafx.scene.canvas.Canvas
import javafx.scene.input.MouseButton
import javafx.scene.input.ScrollEvent
import kotlin.math.roundToInt
import org.example.candles.chart.ChartState

class ZoomPanHandler(
    private val canvas: Canvas,
    private val state: ChartState,
    private val onChange: () -> Unit
) {
    private var dragStartX = 0.0
    private var startIndexAtDrag = 0

    init {
        canvas.addEventHandler(ScrollEvent.SCROLL) { event ->
            handleZoom(event)
        }
        canvas.setOnMousePressed { event ->
            if (event.button == MouseButton.PRIMARY) {
                dragStartX = event.x
                startIndexAtDrag = state.visibleStartIndex
            }
        }
        canvas.setOnMouseDragged { event ->
            if (event.button == MouseButton.PRIMARY) {
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

    private fun handleZoom(event: ScrollEvent) {
        if (state.totalCandles == 0) return
        val factor = if (event.deltaY > 0) 1.1 else 0.9
        val minWidthForMaxVisible = canvas.width / state.maxVisibleCandles
        val newWidth = (state.candleWidthPx * factor).coerceAtLeast(state.minCandleWidthPx)
        state.candleWidthPx = maxOf(newWidth, minWidthForMaxVisible)
        state.updateVisibleCount(canvas.width)
        state.visibleStartIndex = state.visibleStartIndex.coerceIn(0, state.maxStartIndex())
        onChange()
        event.consume()
    }
}
