package org.example.candles.aggregation

import org.example.candles.domain.Timeframe
import org.example.candles.policy.AggregationPolicy
import org.example.candles.policy.GapPolicy
import org.example.candles.policy.PolicyConfigurationException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregationNonDivisiblePolicyTest {
    @Test
    fun `drop bucket policy requires divisible timeframes`() {
        assertThrows(PolicyConfigurationException::class.java) {
            CandleAggregator(
                sourceTimeframe = Timeframe.parse("2m"),
                targetTimeframe = Timeframe.parse("7m"),
                policy = AggregationPolicy(gapPolicy = GapPolicy.DROP_BUCKET_IF_INCOMPLETE)
            )
        }
    }
}
