package org.example.candles.engine.backtest

import org.example.candles.engine.strategy.Strategy

fun interface StrategyFactory {
    fun create(): Strategy
}
