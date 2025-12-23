package org.example.candles.engine.runner

import org.example.candles.domain.Candle
import org.example.candles.engine.strategy.Strategy
import org.example.candles.engine.strategy.StrategyEvent

class StrategyRunner(
    private val strategies: List<Strategy>
) {
    fun run(candles: Sequence<Candle>): List<StrategyEvent> {
        val events = mutableListOf<StrategyEvent>()
        for (candle in candles) {
            for (strategy in strategies) {
                events.addAll(strategy.onCandle(candle))
            }
        }
        for (strategy in strategies) {
            events.addAll(strategy.flush())
        }
        return events
    }
}
