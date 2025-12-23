package org.example.candles.aggregation

open class AggregationException(message: String) : RuntimeException(message)

class OrderingViolationException(message: String) : AggregationException(message)

class DuplicateTimestampException(message: String) : AggregationException(message)

class CandleValidationException(message: String) : AggregationException(message)
