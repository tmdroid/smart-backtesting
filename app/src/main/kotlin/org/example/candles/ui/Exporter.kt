package org.example.candles.ui

import java.io.File
import javafx.embed.swing.SwingFXUtils
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.image.WritableImage
import javafx.stage.FileChooser
import javax.imageio.ImageIO

object Exporter {
    fun exportChart(canvas: Canvas) {
        val chooser = FileChooser()
        chooser.title = "Export PNG"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("PNG", "*.png"))
        chooser.initialFileName = "chart.png"
        val file = chooser.showSaveDialog(canvas.scene?.window) ?: return
        val snapshot = WritableImage(canvas.width.toInt(), canvas.height.toInt())
        canvas.snapshot(SnapshotParameters(), snapshot)
        ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", File(file.absolutePath))
    }
}
