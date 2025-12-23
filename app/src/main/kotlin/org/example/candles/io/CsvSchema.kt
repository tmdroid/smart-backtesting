package org.example.candles.io

data class CsvSchema(
    val timestamp: String = "timestamp",
    val open: String = "open",
    val high: String = "high",
    val low: String = "low",
    val close: String = "close",
    val volume: String = "volume",
    val optionalColumns: Set<String> = emptySet()
) {
    fun requiredColumns(): Set<String> = setOf(timestamp, open, high, low, close, volume)

    fun allowedColumns(): Set<String> = requiredColumns() + optionalColumns
}
