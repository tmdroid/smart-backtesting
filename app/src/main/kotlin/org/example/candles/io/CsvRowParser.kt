package org.example.candles.io

import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe

class CsvRowParser(
    private val sourceTimeframe: Timeframe,
    private val schema: CsvSchema,
    private val timestampFormat: TimestampFormat,
    headerColumns: List<String>,
    private val delimiter: Char = ',',
    private val trimWhitespace: Boolean = true
) {
    private val indexByName: Map<String, Int> = headerColumns.withIndex().associate { it.value to it.index }

    fun parseCandle(line: String, lineNumber: Int): Candle {
        val fields = splitLine(line, lineNumber)
        val start = parseTimestamp(fields, lineNumber)
        val endExclusive = start.plusMillis(sourceTimeframe.millis)
        val open = parseDouble(fields, schema.open, lineNumber)
        val high = parseDouble(fields, schema.high, lineNumber)
        val low = parseDouble(fields, schema.low, lineNumber)
        val close = parseDouble(fields, schema.close, lineNumber)
        val volume = parseLong(fields, schema.volume, lineNumber)
        return Candle(
            start = start,
            endExclusive = endExclusive,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume
        )
    }

    private fun splitLine(line: String, lineNumber: Int): List<String> {
        val rawFields = line.split(delimiter)
        val fields = if (trimWhitespace) rawFields.map { it.trim() } else rawFields
        if (fields.any { it.isEmpty() }) {
            throw CsvParseException("Empty field at line $lineNumber")
        }
        return fields
    }

    private fun parseTimestamp(fields: List<String>, lineNumber: Int): Instant {
        val value = fields[indexByName.getValue(schema.timestamp)]
        return try {
            when (timestampFormat) {
                TimestampFormat.ISO_8601_UTC -> Instant.parse(value)
                TimestampFormat.EPOCH_MILLIS -> Instant.ofEpochMilli(value.trim().toLong())
                TimestampFormat.EPOCH_NANOS -> {
                    val nanos = value.trim().toLong()
                    val seconds = Math.floorDiv(nanos, 1_000_000_000L)
                    val nanoAdjustment = Math.floorMod(nanos, 1_000_000_000L)
                    Instant.ofEpochSecond(seconds, nanoAdjustment)
                }
            }
        } catch (ex: Exception) {
            throw CsvParseException("Invalid timestamp at line $lineNumber in field ${schema.timestamp}: $value")
        }
    }

    private fun parseDouble(fields: List<String>, name: String, lineNumber: Int): Double {
        val value = fields[indexByName.getValue(name)]
        return try {
            value.trim().toDouble()
        } catch (ex: Exception) {
            throw CsvParseException("Invalid number at line $lineNumber in field $name: $value")
        }
    }

    private fun parseLong(fields: List<String>, name: String, lineNumber: Int): Long {
        val value = fields[indexByName.getValue(name)]
        return try {
            value.trim().toLong()
        } catch (ex: Exception) {
            throw CsvParseException("Invalid number at line $lineNumber in field $name: $value")
        }
    }
}
