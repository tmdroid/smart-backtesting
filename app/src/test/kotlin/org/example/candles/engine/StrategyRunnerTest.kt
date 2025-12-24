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

    @Test
    fun `strategy runner consumes sequence only once`() {
        val runner = StrategyRunner(listOf(TestStrategy("A")))
        val candle = candleAt(0)
        val singleUse = singleUseSequence(candle)
        val events = runner.run(singleUse)
        assertEquals(1, events.size)
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

    private fun singleUseSequence(candle: Candle): Sequence<Candle> {
        var used = false
        return Sequence {
            if (used) {
                throw IllegalStateException("Sequence iterated more than once")
            }
            used = true
            listOf(candle).iterator()
        }
    }
}
