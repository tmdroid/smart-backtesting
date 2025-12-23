package org.example.candles.ui

import java.time.LocalDate
import javafx.scene.control.ButtonType
import javafx.scene.control.DatePicker
import javafx.scene.control.Dialog
import javafx.stage.Window

object DayPickerDialog {
    fun pick(owner: Window?): LocalDate? {
        val dialog = Dialog<LocalDate>()
        dialog.title = "Select Day"
        dialog.headerText = "Pick a day to load (UTC)"
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        val picker = DatePicker()
        picker.isEditable = false
        dialog.dialogPane.content = picker
        dialog.initOwner(owner)

        dialog.setResultConverter { button ->
            if (button == ButtonType.OK) picker.value else null
        }

        return dialog.showAndWait().orElse(null)
    }
}
