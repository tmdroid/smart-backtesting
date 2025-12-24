package org.example.candles.ui

import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.layout.HBox

class StatusBar {
    val root: HBox = HBox()

    private val statusLabel = Label("Idle")
    private val loadingIndicator = ProgressIndicator()
    private val loadingProperty = SimpleBooleanProperty(false)

    init {
        root.spacing = 8.0
        root.padding = Insets(6.0)
        loadingIndicator.isVisible = false
        loadingIndicator.maxWidth = 16.0
        loadingIndicator.maxHeight = 16.0
        loadingIndicator.visibleProperty().bind(loadingProperty)
        root.children.addAll(statusLabel, loadingIndicator)
    }

    fun setStatus(text: String) {
        statusLabel.text = text
    }

    fun setLoading(loading: Boolean) {
        loadingProperty.set(loading)
    }

    fun loadingProperty(): BooleanProperty = loadingProperty
}
