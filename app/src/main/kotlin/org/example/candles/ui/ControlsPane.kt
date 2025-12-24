package org.example.candles.ui

import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.DatePicker
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat

class ControlsPane(
    private val onOpenFile: (Path) -> Unit,
    private val onTimeframeChange: (Timeframe) -> Unit,
    private val onDayChange: (LocalDate?) -> Unit,
    private val onStrategyChange: (StrategyUiConfig) -> Unit,
    private val onExport: () -> Unit
) {
    val root: VBox = VBox()
    private val rowTop: HBox = HBox()
    private val rowBottom: HBox = HBox()

    private val fileLabel = Label("No file")
    private val openButton = Button("Open File")
    private val exportButton = Button("Export PNG")
    private val timeframeBox = ComboBox<String>()
    private val customField = TextField()
    private val applyCustomButton = Button("Apply")
    private val dayPicker = DatePicker()
    private val prevDayButton = Button("◀")
    private val nextDayButton = Button("▶")
    private val clearDayButton = Button("Clear Day")
    private val sessionStartField = TextField()
    private val sessionEndField = TextField()
    private val stopLossField = TextField()
    private val takeProfitField = TextField()
    private val breakEvenField = TextField()

    private var currentFile: Path? = null
    private var suppressTimeframeEvent = false
    private var schemaPreset: String = "default"

    init {
        root.spacing = 6.0
        root.padding = Insets(8.0)
        rowTop.spacing = 8.0
        rowBottom.spacing = 8.0

        openButton.setOnAction { openFileDialog() }
        exportButton.setOnAction { onExport() }

        timeframeBox.items.addAll("1m", "5m", "15m", "1h", "Custom")
        timeframeBox.selectionModel.selectFirst()
        timeframeBox.setOnAction {
            val value = timeframeBox.value
            if (!suppressTimeframeEvent && value != null && value != "Custom") {
                onTimeframeChange(Timeframe.parse(value))
            }
        }

        dayPicker.promptText = "Day"
        dayPicker.isEditable = false
        dayPicker.valueProperty().addListener { _, _, newValue ->
            onDayChange(newValue)
        }
        prevDayButton.setOnAction {
            dayPicker.value = (dayPicker.value ?: return@setOnAction).minusDays(1)
        }
        nextDayButton.setOnAction {
            dayPicker.value = (dayPicker.value ?: return@setOnAction).plusDays(1)
        }
        clearDayButton.setOnAction {
            dayPicker.value = null
            onDayChange(null)
        }

        customField.promptText = "Custom TF (e.g. 7m)"
        applyCustomButton.setOnAction { applyCustomTimeframe() }

        sessionStartField.text = "03:00"
        sessionEndField.text = "03:15"
        stopLossField.text = "40"
        takeProfitField.text = "40"
        breakEvenField.promptText = "BE (optional)"

        val strategyFields = listOf(
            sessionStartField,
            sessionEndField,
            stopLossField,
            takeProfitField,
            breakEvenField
        )
        for (field in strategyFields) {
            field.setOnAction { emitStrategyConfig() }
            field.focusedProperty().addListener { _, _, newValue ->
                if (!newValue) emitStrategyConfig()
            }
        }

        rowTop.children.addAll(
            openButton,
            fileLabel,
            timeframeBox,
            customField,
            applyCustomButton,
            prevDayButton,
            dayPicker,
            nextDayButton,
            clearDayButton,
            exportButton
        )
        rowBottom.children.addAll(
            Label("Session"),
            sessionStartField,
            Label("-"),
            sessionEndField,
            Label("SL"),
            stopLossField,
            Label("TP"),
            takeProfitField,
            Label("BE"),
            breakEvenField
        )
        root.children.addAll(rowTop, rowBottom)
    }

    fun setCurrentFile(path: Path) {
        currentFile = path
        fileLabel.text = path.fileName.toString()
    }

    fun currentFilePath(): Path? = currentFile

    fun currentTimeframe(): Timeframe? {
        val value = timeframeBox.value ?: return null
        return if (value == "Custom") {
            parseCustomTimeframeOrNull()
        } else {
            Timeframe.parse(value)
        }
    }

    fun currentSchema(): CsvSchema {
        return when (schemaPreset) {
            "mnq" -> CsvSchema(
                timestamp = "ts_event",
                optionalColumns = setOf("symbol", "source_symbol")
            )
            else -> CsvSchema()
        }
    }

    fun currentTimestampFormat(): TimestampFormat {
        return when (schemaPreset) {
            "mnq" -> TimestampFormat.EPOCH_NANOS
            else -> TimestampFormat.ISO_8601_UTC
        }
    }

    fun setSchemaPreset(name: String) {
        schemaPreset = name
    }

    fun setTimeframePreset(value: String) {
        suppressTimeframeEvent = true
        timeframeBox.selectionModel.select(value)
        suppressTimeframeEvent = false
    }

    fun setCurrentDay(day: LocalDate?) {
        dayPicker.value = day
    }

    fun currentStrategyConfig(): StrategyUiConfig? {
        val start = parseLocalTime(sessionStartField.text.trim(), "Session start")
        val end = parseLocalTime(sessionEndField.text.trim(), "Session end")
        if (start == null || end == null) return null
        val stopLoss = parseDouble(stopLossField.text.trim(), "Stop loss") ?: return null
        val takeProfit = parseDouble(takeProfitField.text.trim(), "Take profit") ?: return null
        val breakEvenText = breakEvenField.text.trim()
        val breakEven = if (breakEvenText.isEmpty()) {
            null
        } else {
            parseDouble(breakEvenText, "Break even") ?: return null
        }
        return StrategyUiConfig(
            sessionStart = start,
            sessionEnd = end,
            stopLossPoints = stopLoss,
            takeProfitPoints = takeProfit,
            breakEvenTriggerPoints = breakEven
        )
    }

    private fun openFileDialog() {
        val chooser = FileChooser()
        chooser.title = "Open CSV"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("CSV Files", "*.csv"))
        val defaultPath = defaultMnqPath()
        if (defaultPath != null) {
            chooser.initialDirectory = defaultPath.parent.toFile()
        }
        val selected = chooser.showOpenDialog(root.scene?.window)
        if (selected != null) {
            onOpenFile(selected.toPath())
        }
    }

    private fun applyCustomTimeframe() {
        val tf = parseCustomTimeframeOrNull() ?: return
        timeframeBox.selectionModel.select("Custom")
        onTimeframeChange(tf)
    }

    private fun parseCustomTimeframeOrNull(): Timeframe? {
        val raw = customField.text.trim()
        if (raw.isEmpty()) return null
        return try {
            Timeframe.parse(raw)
        } catch (ex: Exception) {
            ErrorDialogs.show("Invalid timeframe", ex.message ?: "Invalid timeframe")
            null
        }
    }

    private fun emitStrategyConfig() {
        val config = currentStrategyConfig() ?: return
        onStrategyChange(config)
    }

    private fun parseLocalTime(value: String, label: String): LocalTime? {
        if (value.isEmpty()) {
            ErrorDialogs.show("Invalid $label", "Value is required")
            return null
        }
        return try {
            LocalTime.parse(value)
        } catch (_: Exception) {
            ErrorDialogs.show("Invalid $label", "Use HH:mm format")
            null
        }
    }

    private fun parseDouble(value: String, label: String): Double? {
        if (value.isEmpty()) {
            ErrorDialogs.show("Invalid $label", "Value is required")
            return null
        }
        return value.toDoubleOrNull()?.also {
            if (it <= 0.0) {
                ErrorDialogs.show("Invalid $label", "Value must be > 0")
                return null
            }
        }
    }

    companion object {
        fun defaultMnqPath(): Path? {
            val path = Path.of("data/MNQ/mnq-history/mnq1_continuous.ohlcv-1m.csv")
            return if (path.toFile().exists()) path else null
        }
    }
}
