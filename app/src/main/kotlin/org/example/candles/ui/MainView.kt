package org.example.candles.ui

import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.Executors
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.ScrollBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.example.candles.chart.CandlestickCanvas
import org.example.candles.chart.ChartState
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.integration.AggregationService
import org.example.candles.util.ZoomPanHandler
import org.example.candles.util.Log

class MainView {
    val root: BorderPane = BorderPane()

    private val chartCanvas = CandlestickCanvas()
    private val chartPane = StackPane(chartCanvas)
    private val statusBar = StatusBar()
    private val scrollBar = ScrollBar()
    private val aggregationService = AggregationService(Executors.newSingleThreadExecutor())
    private val chartState = ChartState()
    private val zoomPanHandler = ZoomPanHandler(chartCanvas, chartState, ::onChartChanged)
    private var currentTimeframe: Timeframe? = null
    private var currentDay: LocalDate? = null
    private var prebuildKey: String? = null

    private lateinit var controlsPane: ControlsPane

    init {
        controlsPane = ControlsPane(
            onOpenFile = { path -> loadFile(path) },
            onTimeframeChange = { timeframe -> aggregate(timeframe) },
            onSchemaChange = { _ -> onSchemaChanged() },
            onDayChange = { day -> onDayChanged(day) },
            onExport = { exportPng() }
        )
        root.top = controlsPane.root
        chartPane.minWidth = 0.0
        chartPane.minHeight = 0.0
        chartCanvas.widthProperty().bind(chartPane.widthProperty())
        chartCanvas.heightProperty().bind(chartPane.heightProperty())

        root.center = VBox(chartPane, scrollBar).apply {
            VBox.setVgrow(chartPane, Priority.ALWAYS)
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
            chartState.updateVisibleCount(chartCanvas.drawableWidth())
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
            controlsPane.setTimeframePreset("5m")
            controlsPane.setCurrentFile(defaultPath)
            loadFile(defaultPath)
        }
    }

    private fun loadFile(path: Path) {
        controlsPane.setCurrentFile(path)
        autoSelectSchema(path)
        Log.info("Selected file: $path")
        statusBar.setStatus("Loading ${path.fileName}...")
        val timeframe = controlsPane.currentTimeframe()
        if (timeframe == null) {
            statusBar.setStatus("Select timeframe")
            return
        }
        statusBar.setLoading(true)
        aggregationService.detectLastDay(
            path = path,
            schema = controlsPane.currentSchema(),
            timestampFormat = controlsPane.currentTimestampFormat(),
            onStatus = { status ->
                Platform.runLater {
                    statusBar.setStatus(status)
                }
            },
            onSuccess = { day ->
                Platform.runLater {
                    currentDay = day
                    controlsPane.setCurrentDay(day)
                    if (day != null) {
                        Log.info("Auto-selected day: $day")
                    } else {
                        Log.warn("No day detected; aggregating full file")
                    }
                    aggregate(timeframe)
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

    private fun aggregate(timeframe: Timeframe) {
        val path = controlsPane.currentFilePath() ?: return
        currentTimeframe = timeframe
        val status = if (timeframe.millis == Timeframe.parse("1m").millis) {
            "Loading ${timeframe}..."
        } else {
            "Aggregating ${timeframe}..."
        }
        statusBar.setStatus(status)
        statusBar.setLoading(true)

        aggregationService.aggregate(
            path = path,
            timeframe = timeframe,
            schema = controlsPane.currentSchema(),
            timestampFormat = controlsPane.currentTimestampFormat(),
            day = currentDay,
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
        chartState.updateVisibleCount(chartCanvas.drawableWidth())
        chartState.visibleStartIndex = chartState.defaultStartIndex()
        scrollBar.max = chartState.maxStartIndex().toDouble()
        scrollBar.visibleAmount = chartState.visibleCount.toDouble().coerceAtLeast(1.0)
        scrollBar.value = chartState.visibleStartIndex.toDouble()
        val filterLabel = currentDay?.toString() ?: "all"
        statusBar.setStatus("${candles.size} candles (${timeframe}, ${filterLabel})")
        statusBar.setLoading(false)
        render()
        startPrebuildIfNeeded()
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

    private fun onDayChanged(day: LocalDate?) {
        currentDay = day
        val tf = currentTimeframe ?: return
        aggregate(tf)
    }

    private fun startPrebuildIfNeeded() {
        val path = controlsPane.currentFilePath() ?: return
        val schema = controlsPane.currentSchema()
        val format = controlsPane.currentTimestampFormat()
        val key = "${path.toAbsolutePath()}|${schema.timestamp}|${format.name}"
        if (prebuildKey == key) return
        prebuildKey = key
        aggregationService.prebuildDayCaches(path, schema, format)
    }
}
