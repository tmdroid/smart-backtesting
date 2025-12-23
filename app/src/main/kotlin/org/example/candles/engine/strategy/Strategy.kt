package org.example.candles.engine.strategy

import org.example.candles.domain.Candle

interface Strategy {
    val id: String
    fun onCandle(candle: Candle): List<StrategyEvent>
    fun flush(): List<StrategyEvent> = emptyList()
}
