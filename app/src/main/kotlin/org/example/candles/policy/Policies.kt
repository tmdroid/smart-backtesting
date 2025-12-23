package org.example.candles.policy

import org.example.candles.aggregation.AggregationException

enum class GapPolicy {
    KEEP_PARTIAL,
    DROP_BUCKET_IF_INCOMPLETE
}

enum class OrderingPolicy {
    REJECT
}

enum class DuplicatePolicy {
    REJECT
}

enum class FlushPolicy {
    EMIT_PARTIAL,
    DROP_PARTIAL
}

enum class ValidationPolicy {
    STRICT
}

data class AggregationPolicy(
    val gapPolicy: GapPolicy = GapPolicy.KEEP_PARTIAL,
    val orderingPolicy: OrderingPolicy = OrderingPolicy.REJECT,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.REJECT,
    val flushPolicy: FlushPolicy = FlushPolicy.EMIT_PARTIAL,
    val validationPolicy: ValidationPolicy = ValidationPolicy.STRICT
)

class PolicyConfigurationException(message: String) : AggregationException(message)
