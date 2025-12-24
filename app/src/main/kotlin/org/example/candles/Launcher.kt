package org.example.candles

import javafx.application.Application
import org.example.candles.cli.main as cliMain
import org.example.candles.ui.MainApp
import org.example.candles.util.Log

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        Log.info("Launching CLI mode with args: ${args.joinToString(" ")}")
        cliMain(args)
        return
    }
    Log.info("Launching UI mode")
    Application.launch(MainApp::class.java, *args)
}
