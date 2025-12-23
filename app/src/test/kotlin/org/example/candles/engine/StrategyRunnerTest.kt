package org.example.candles.engine

import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.engine.runner.StrategyRunner
import org.example.candles.engine.strategy.Annotation
import org.example.candles.engine.strategy.Strategy
import org.example.candles.engine.strategy.StrategyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StrategyRunnerTest {
    @Test
    fun `strategy runner preserves deterministic ordering`() {
        val strategyA = TestStrategy("A")
        val strategyB = TestStrategy("B")
        val runner = StrategyRunner(listOf(strategyA, strategyB))

        val candle1 = candleAt(0)
        val candle2 = candleAt(60)
        val events = runner.run(sequenceOf(candle1, candle2))

        val ids = events.map { it.strategyId to it.time }.toList()
        assertEquals(
            listOf(
                "A" to candle1.start,
                "B" to candle1.start,
                "A" to candle2.start,
                "B" to candle2.start
            ),
            ids
        )
    }

    private class TestStrategy(override val id: String) : Strategy {
        override fun onCandle(candle: Candle): List<StrategyEvent> {
            return listOf(Annotation(time = candle.start, strategyId = id, message = "tick"))
        }
    }

    private fun candleAt(epochSecond: Long): Candle {
        return Candle(
            start = Instant.ofEpochSecond(epochSecond),
            endExclusive = Instant.ofEpochSecond(epochSecond + 60),
            open = 1.0,
            high = 1.0,
            low = 1.0,
            close = 1.0,
            volume = 1L
        )
    }
}
