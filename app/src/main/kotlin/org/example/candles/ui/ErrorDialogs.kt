package org.example.candles.ui

import javafx.scene.control.Alert

object ErrorDialogs {
    fun show(title: String, message: String) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.headerText = title
        alert.contentText = message
        alert.showAndWait()
    }
}
