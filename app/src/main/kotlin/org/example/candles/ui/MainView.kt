package org.example.candles.ui

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.time.ZoneId
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.ScrollBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.Duration
import org.example.candles.chart.CandlestickCanvas
import org.example.candles.chart.ChartState
import org.example.candles.chart.RangeBox
import org.example.candles.chart.RiskRewardBox
import org.example.candles.chart.StrategyOverlay
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.engine.range.RangeDefinition
import org.example.candles.engine.range.TradingSessionTime
import org.example.candles.engine.runner.StrategyRunner
import org.example.candles.engine.strategy.BreakoutSignal
import org.example.candles.engine.strategy.Direction
import org.example.candles.engine.strategy.RangeBreakoutStrategy
import org.example.candles.engine.strategy.RangeBuilt
import org.example.candles.engine.strategy.TradeParameters
import org.example.candles.integration.AggregationService
import org.example.candles.util.ZoomPanHandler
import org.example.candles.util.Log

class MainView {
    val root: StackPane = StackPane()

    private val content: BorderPane = BorderPane()
    private val chartCanvas = CandlestickCanvas()
    private val chartPane = StackPane(chartCanvas)
    private val statusBar = StatusBar()
    private val scrollBar = ScrollBar()
    private val aggregationService = AggregationService(Executors.newSingleThreadExecutor())
    private val overlayExecutor = Executors.newSingleThreadExecutor()
    private val overlayRequestId = AtomicLong(0)
    private val overlayDebounce = PauseTransition(Duration.millis(150.0))
    private val chartState = ChartState()
    private var currentTimeframe: Timeframe? = null
    private var currentDay: LocalDate? = null

    private val controlsPane: ControlsPane
    private val loadingOverlay = StackPane()

    init {
        controlsPane = ControlsPane(
            onOpenFile = { path -> loadFile(path) },
            onTimeframeChange = { timeframe -> aggregate(timeframe) },
            onDayChange = { day -> onDayChanged(day) },
            onStrategyChange = { _ -> requestOverlayUpdate() },
            onExport = { exportPng() }
        )
        ZoomPanHandler(chartCanvas, chartState, ::onChartChanged)
        content.top = controlsPane.root
        chartPane.minWidth = 0.0
        chartPane.minHeight = 0.0
        chartCanvas.widthProperty().bind(chartPane.widthProperty())
        chartCanvas.heightProperty().bind(chartPane.heightProperty())

        content.center = VBox(chartPane, scrollBar).apply {
            VBox.setVgrow(chartPane, Priority.ALWAYS)
            spacing = 4.0
            padding = Insets(4.0)
        }
        content.bottom = statusBar.root

        setupLoadingOverlay()
        root.children.addAll(content, loadingOverlay)

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
        overlayDebounce.setOnFinished { computeOverlayAsync() }
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

    private fun autoSelectSchema(path: Path) {
        try {
            Files.newBufferedReader(path).use { reader ->
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
        requestOverlayUpdate()
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

    private fun requestOverlayUpdate() {
        overlayDebounce.playFromStart()
    }

    private fun computeOverlayAsync() {
        val candles = chartState.candles
        val timeframe = currentTimeframe ?: return
        if (candles.isEmpty()) {
            chartState.overlay = null
            render()
            return
        }
        val config = controlsPane.currentStrategyConfig()
        if (config == null) {
            chartState.overlay = null
            render()
            return
        }
        val nyZone = ZoneId.of("America/New_York")
        chartCanvas.setTimeZone(nyZone)
        val id = overlayRequestId.incrementAndGet()
        val candlesSnapshot = candles
        val configSnapshot = config
        overlayExecutor.submit {
            val overlay = buildOverlay(candlesSnapshot, timeframe, configSnapshot, nyZone)
            Platform.runLater {
                if (overlayRequestId.get() == id) {
                    chartState.overlay = overlay
                    render()
                }
            }
        }
    }

    private fun buildOverlay(
        candles: List<Candle>,
        timeframe: Timeframe,
        config: StrategyUiConfig,
        nyZone: ZoneId
    ): StrategyOverlay? {
        val rangeDefinition = RangeDefinition(
            timeframe = timeframe,
            sessionTime = TradingSessionTime(
                timezone = nyZone,
                start = config.sessionStart,
                end = config.sessionEnd
            )
        )
        val tradeParameters = TradeParameters(
            stopLossPoints = config.stopLossPoints,
            takeProfitPoints = config.takeProfitPoints,
            breakEvenTriggerPoints = config.breakEvenTriggerPoints
        )
        val strategy = RangeBreakoutStrategy(
            id = "ui-range-breakout",
            rangeDefinition = rangeDefinition,
            tradeParameters = tradeParameters
        )
        val events = StrategyRunner(listOf(strategy)).run(candles.asSequence())
        val rangeBuilt = events.filterIsInstance<RangeBuilt>().firstOrNull() ?: return null
        val breakout = events.filterIsInstance<BreakoutSignal>().firstOrNull()

        val rangeStartIndex = candles.indexOfFirst { !it.start.isBefore(rangeBuilt.range.startTime) }
            .coerceAtLeast(0)
        val rangeWindowEndIndex = candles.indexOfLast { it.start.isBefore(rangeBuilt.range.endTime) }
            .let { if (it < 0) rangeStartIndex else it }

        val breakoutIndex = breakout?.let { signal ->
            candles.indexOfFirst { it.start == signal.signalCandle.start }.takeIf { it >= 0 }
        }
        val rangeEndIndex = if (breakoutIndex != null) {
            (breakoutIndex + 5).coerceAtMost(candles.size - 1)
        } else {
            rangeWindowEndIndex
        }

        val rangeBox = RangeBox(
            startIndexInclusive = rangeStartIndex,
            endIndexInclusive = rangeEndIndex,
            high = rangeBuilt.range.high,
            low = rangeBuilt.range.low
        )

        val riskRewardBox = breakout?.let { signal ->
            val index = candles.indexOfFirst { it.start == signal.signalCandle.start }.takeIf { it >= 0 }
                ?: return@let null
            val direction = signal.direction
            val entry = signal.breakoutPrice
            val stop = if (direction == Direction.LONG) {
                entry - config.stopLossPoints
            } else {
                entry + config.stopLossPoints
            }
            val target = if (direction == Direction.LONG) {
                entry + config.takeProfitPoints
            } else {
                entry - config.takeProfitPoints
            }
            val beTrigger = config.breakEvenTriggerPoints?.let { points ->
                if (direction == Direction.LONG) entry + points else entry - points
            }
            val endIndex = findOutcomeIndex(
                candles = candles,
                startIndex = index,
                direction = direction,
                entry = entry,
                stop = stop,
                target = target,
                beTrigger = beTrigger
            )
            RiskRewardBox(
                startIndexInclusive = index,
                endIndexInclusive = endIndex,
                entry = entry,
                stop = stop,
                target = target,
                direction = direction
            )
        }

        return StrategyOverlay(rangeBox, riskRewardBox)
    }

    private fun findOutcomeIndex(
        candles: List<Candle>,
        startIndex: Int,
        direction: Direction,
        entry: Double,
        stop: Double,
        target: Double,
        beTrigger: Double?
    ): Int {
        var beArmed = false
        var index = (startIndex + 1).coerceAtMost(candles.size - 1)
        while (index < candles.size) {
            val candle = candles[index]
            if (!beArmed) {
                if (isStopHit(direction, candle, stop)) return index
                if (isTargetHit(direction, candle, target)) return index
                if (beTrigger != null && isBreakEvenTriggerHit(direction, candle, beTrigger)) {
                    beArmed = true
                }
            } else {
                if (isStopHit(direction, candle, entry)) return index
                if (isTargetHit(direction, candle, target)) return index
            }
            index++
        }
        return candles.size - 1
    }

    private fun isStopHit(direction: Direction, candle: Candle, stop: Double): Boolean {
        return if (direction == Direction.LONG) candle.low <= stop else candle.high >= stop
    }

    private fun isTargetHit(direction: Direction, candle: Candle, target: Double): Boolean {
        return if (direction == Direction.LONG) candle.high >= target else candle.low <= target
    }

    private fun isBreakEvenTriggerHit(direction: Direction, candle: Candle, trigger: Double): Boolean {
        return if (direction == Direction.LONG) candle.high >= trigger else candle.low <= trigger
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
        overlayExecutor.shutdownNow()
    }

    private fun onDayChanged(day: LocalDate?) {
        currentDay = day
        val tf = currentTimeframe ?: return
        aggregate(tf)
    }

    private fun setupLoadingOverlay() {
        loadingOverlay.style = "-fx-background-color: rgba(255,255,255,0.6);"
        val indicator = ProgressIndicator()
        indicator.maxWidth = 64.0
        indicator.maxHeight = 64.0
        loadingOverlay.children.add(indicator)
        loadingOverlay.visibleProperty().bind(statusBar.loadingProperty())
        loadingOverlay.managedProperty().bind(statusBar.loadingProperty())
        content.disableProperty().bind(statusBar.loadingProperty())
    }

}
