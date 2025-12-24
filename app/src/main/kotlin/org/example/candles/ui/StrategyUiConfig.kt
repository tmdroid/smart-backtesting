package org.example.candles.ui

import java.time.LocalTime


data class StrategyUiConfig(
    val sessionStart: LocalTime,
    val sessionEnd: LocalTime,
    val stopLossPoints: Double,
    val takeProfitPoints: Double,
    val breakEvenTriggerPoints: Double?
)
