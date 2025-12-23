package org.example.candles.ui

import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage

class MainApp : Application() {
    private var view: MainView? = null

    override fun start(primaryStage: Stage) {
        val view = MainView()
        this.view = view
        val scene = Scene(view.root, 1200.0, 800.0)
        primaryStage.title = "Candle Chart"
        primaryStage.scene = scene
        primaryStage.show()
        view.onShown()
    }

    override fun stop() {
        view?.shutdown()
        super.stop()
    }
}
