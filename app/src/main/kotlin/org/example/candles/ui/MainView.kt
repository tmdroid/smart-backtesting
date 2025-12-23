package org.example.candles.ui

import java.nio.file.Path
import java.util.concurrent.Executors
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.ScrollBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.example.candles.chart.CandlestickCanvas
import org.example.candles.chart.ChartState
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.integration.AggregationService
import org.example.candles.util.ZoomPanHandler

class MainView {
    val root: BorderPane = BorderPane()

    private val chartCanvas = CandlestickCanvas()
    private val statusBar = StatusBar()
    private val scrollBar = ScrollBar()
    private val aggregationService = AggregationService(Executors.newSingleThreadExecutor())
    private val chartState = ChartState()
    private val zoomPanHandler = ZoomPanHandler(chartCanvas, chartState, ::onChartChanged)

    private lateinit var controlsPane: ControlsPane

    init {
        controlsPane = ControlsPane(
            onOpenFile = { path -> loadFile(path) },
            onTimeframeChange = { timeframe -> aggregate(timeframe) },
            onSchemaChange = { _ -> onSchemaChanged() },
            onExport = { exportPng() }
        )
        root.top = controlsPane.root
        root.center = VBox(chartCanvas, scrollBar).apply {
            VBox.setVgrow(chartCanvas, Priority.ALWAYS)
            spacing = 4.0
            padding = Insets(4.0)
        }
        root.bottom = statusBar.root

        scrollBar.min = 0.0
        scrollBar.visibleAmount = 1.0
        scrollBar.valueProperty().addListener { _, _, newValue ->
            if (chartState.totalCandles == 0) return@addListener
            chartState.visibleStartIndex = newValue.toInt().coerceIn(0, chartState.maxStartIndex())
            render()
        }

        chartCanvas.widthProperty().addListener { _, _, _ ->
            chartState.updateVisibleCount(chartCanvas.width)
            clampAndRender()
        }
        chartCanvas.heightProperty().addListener { _, _, _ ->
            render()
        }
    }

    fun onShown() {
        val defaultPath = ControlsPane.defaultMnqPath()
        if (defaultPath != null) {
            controlsPane.setSchemaPreset("mnq")
            controlsPane.setCurrentFile(defaultPath)
            loadFile(defaultPath)
        }
    }

    private fun loadFile(path: Path) {
        controlsPane.setCurrentFile(path)
        autoSelectSchema(path)
        statusBar.setStatus("Loading ${path.fileName}...")
        val timeframe = controlsPane.currentTimeframe()
        if (timeframe == null) {
            statusBar.setStatus("Select timeframe")
            return
        }
        aggregate(timeframe)
    }

    private fun aggregate(timeframe: Timeframe) {
        val path = controlsPane.currentFilePath() ?: return
        statusBar.setStatus("Aggregating ${timeframe}...")
        statusBar.setLoading(true)

        aggregationService.aggregate(
            path = path,
            timeframe = timeframe,
            schema = controlsPane.currentSchema(),
            timestampFormat = controlsPane.currentTimestampFormat(),
            onSuccess = { candles, tf ->
                Platform.runLater {
                    updateChart(candles, tf)
                }
            },
            onError = { error ->
                Platform.runLater {
                    statusBar.setLoading(false)
                    showError(error)
                }
            }
        )
    }

    private fun onSchemaChanged() {
        val timeframe = controlsPane.currentTimeframe() ?: return
        aggregate(timeframe)
    }

    private fun autoSelectSchema(path: Path) {
        try {
            java.nio.file.Files.newBufferedReader(path).use { reader ->
                val header = reader.readLine() ?: return
                val columns = header.split(',').map { it.trim() }
                if (columns.contains("ts_event")) {
                    controlsPane.setSchemaPreset("mnq")
                } else {
                    controlsPane.setSchemaPreset("default")
                }
            }
        } catch (_: Exception) {
            // Ignore header inspection failures; parsing will surface errors.
        }
    }

    private fun updateChart(candles: List<Candle>, timeframe: Timeframe) {
        chartState.setCandles(candles)
        chartState.updateVisibleCount(chartCanvas.width)
        chartState.visibleStartIndex = chartState.defaultStartIndex()
        scrollBar.max = chartState.maxStartIndex().toDouble()
        scrollBar.visibleAmount = chartState.visibleCount.toDouble().coerceAtLeast(1.0)
        scrollBar.value = chartState.visibleStartIndex.toDouble()
        statusBar.setStatus("${candles.size} candles (${timeframe})")
        statusBar.setLoading(false)
        render()
    }

    private fun clampAndRender() {
        if (chartState.totalCandles == 0) {
            render()
            return
        }
        chartState.visibleStartIndex = chartState.visibleStartIndex.coerceIn(0, chartState.maxStartIndex())
        scrollBar.max = chartState.maxStartIndex().toDouble()
        scrollBar.visibleAmount = chartState.visibleCount.toDouble().coerceAtLeast(1.0)
        scrollBar.value = chartState.visibleStartIndex.toDouble()
        render()
    }

    private fun onChartChanged() {
        if (chartState.totalCandles == 0) {
            render()
            return
        }
        scrollBar.max = chartState.maxStartIndex().toDouble()
        scrollBar.visibleAmount = chartState.visibleCount.toDouble().coerceAtLeast(1.0)
        scrollBar.value = chartState.visibleStartIndex.toDouble()
        render()
    }

    private fun render() {
        chartCanvas.render(chartState)
    }

    private fun showError(error: Throwable) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = "Error"
        alert.headerText = "Failed to load candles"
        alert.contentText = error.message ?: error.toString()
        alert.showAndWait()
        statusBar.setStatus("Error")
    }

    private fun exportPng() {
        Exporter.exportChart(chartCanvas)
    }

    fun shutdown() {
        aggregationService.shutdown()
    }
}
