package org.example.candles.ui

import java.nio.file.Path
import java.time.LocalDate
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.DatePicker
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.stage.FileChooser
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat

class ControlsPane(
    private val onOpenFile: (Path) -> Unit,
    private val onTimeframeChange: (Timeframe) -> Unit,
    private val onSchemaChange: (CsvSchema) -> Unit,
    private val onDayChange: (LocalDate?) -> Unit,
    private val onExport: () -> Unit
) {
    val root: HBox = HBox()

    private val fileLabel = Label("No file")
    private val openButton = Button("Open File")
    private val exportButton = Button("Export PNG")
    private val timeframeBox = ComboBox<String>()
    private val customField = TextField()
    private val applyCustomButton = Button("Apply")
    private val schemaBox = ComboBox<String>()
    private val dayPicker = DatePicker()
    private val clearDayButton = Button("Clear Day")

    private var currentFile: Path? = null
    private var suppressSchemaEvent = false
    private var suppressTimeframeEvent = false

    init {
        root.spacing = 8.0
        root.padding = Insets(8.0)

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

        schemaBox.items.addAll("default", "mnq")
        schemaBox.selectionModel.select("default")
        schemaBox.setOnAction {
            if (!suppressSchemaEvent) {
                onSchemaChange(currentSchema())
            }
        }

        dayPicker.promptText = "Day (UTC)"
        dayPicker.isEditable = false
        dayPicker.valueProperty().addListener { _, _, newValue ->
            onDayChange(newValue)
        }
        clearDayButton.setOnAction {
            dayPicker.value = null
            onDayChange(null)
        }

        customField.promptText = "Custom TF (e.g. 7m)"
        applyCustomButton.setOnAction { applyCustomTimeframe() }

        root.children.addAll(
            openButton,
            fileLabel,
            timeframeBox,
            customField,
            applyCustomButton,
            schemaBox,
            dayPicker,
            clearDayButton,
            exportButton
        )
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
        return when (schemaBox.value) {
            "mnq" -> CsvSchema(
                timestamp = "ts_event",
                optionalColumns = setOf("symbol", "source_symbol")
            )
            else -> CsvSchema()
        }
    }

    fun currentTimestampFormat(): TimestampFormat {
        return when (schemaBox.value) {
            "mnq" -> TimestampFormat.EPOCH_NANOS
            else -> TimestampFormat.ISO_8601_UTC
        }
    }

    fun setSchemaPreset(name: String) {
        suppressSchemaEvent = true
        schemaBox.selectionModel.select(name)
        suppressSchemaEvent = false
    }

    fun setTimeframePreset(value: String) {
        suppressTimeframeEvent = true
        timeframeBox.selectionModel.select(value)
        suppressTimeframeEvent = false
    }

    fun currentDay(): LocalDate? = dayPicker.value

    fun setCurrentDay(day: LocalDate?) {
        dayPicker.value = day
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

    companion object {
        fun defaultMnqPath(): Path? {
            val path = Path.of("data/MNQ/mnq-history/mnq1_continuous.ohlcv-1m.csv")
            return if (path.toFile().exists()) path else null
        }
    }
}
