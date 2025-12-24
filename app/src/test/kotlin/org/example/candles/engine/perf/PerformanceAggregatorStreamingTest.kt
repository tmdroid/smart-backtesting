package org.example.candles.engine.perf

import java.time.Instant
import org.example.candles.engine.strategy.Direction
import org.example.candles.engine.trade.Trade
import org.example.candles.engine.trade.TradeOutcome
import org.example.candles.engine.trade.TradeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerformanceAggregatorStreamingTest {
    @Test
    fun `summarize consumes sequence only once`() {
        val tradeResult = TradeResult(
            strategyId = "s1",
            trade = Trade(
                direction = Direction.LONG,
                entryTime = Instant.EPOCH,
                entryPrice = 1.0,
                stopPrice = 0.5,
                takeProfitPrice = 2.0,
                breakEvenTriggerPrice = null
            ),
            outcome = TradeOutcome.TAKE_PROFIT,
            exitTime = Instant.EPOCH.plusSeconds(60),
            exitPrice = 2.0,
            pnlPoints = 1.0
        )
        val singleUse = singleUseSequence(tradeResult)
        val summaries = PerformanceAggregator().summarize(singleUse)
        assertEquals(1, summaries.size)
    }

    private fun singleUseSequence(trade: TradeResult): Sequence<TradeResult> {
        var used = false
        return Sequence {
            if (used) {
                throw IllegalStateException("Sequence iterated more than once")
            }
            used = true
            listOf(trade).iterator()
        }
    }
}
