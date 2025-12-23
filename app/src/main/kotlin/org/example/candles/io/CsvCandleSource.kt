package org.example.candles.io

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.domain.TimestampSemantics

class CsvCandleSource(
    private val path: Path,
    private val sourceTimeframe: Timeframe,
    private val timestampFormat: TimestampFormat = TimestampFormat.ISO_8601_UTC,
    private val timestampSemantics: TimestampSemantics = TimestampSemantics.START_TIME,
    private val schema: CsvSchema = CsvSchema(),
    private val delimiter: Char = ',',
    private val trimWhitespace: Boolean = true,
    private val headerRequired: Boolean = true
) : CandleSource {
    override fun stream(): Sequence<Candle> = sequence {
        Files.newBufferedReader(path).use { reader ->
            val lineIterator = reader.lineSequence().iterator()
            if (!lineIterator.hasNext()) {
                throw CsvParseException("Missing header at line 1")
            }
            val headerLine = lineIterator.next()
            if (!headerRequired) {
                throw CsvParseException("CSV header required in Step 1")
            }
            val headerColumns = splitLine(headerLine, 1)
            validateHeader(headerColumns)
            val indexByName = headerColumns.withIndex().associate { it.value to it.index }

            var lineNumber = 1
            while (lineIterator.hasNext()) {
                lineNumber++
                val line = lineIterator.next()
                if (line.isBlank()) {
                    throw CsvParseException("Empty line at $lineNumber")
                }
                val fields = splitLine(line, lineNumber)
                if (fields.size != headerColumns.size) {
                    throw CsvParseException(
                        "Wrong column count at line $lineNumber: expected ${headerColumns.size}, got ${fields.size}"
                    )
                }
                val start = parseTimestamp(fields, indexByName, lineNumber)
                val endExclusive = start.plusMillis(sourceTimeframe.millis)
                val open = parseDouble(fields, indexByName, schema.open, lineNumber)
                val high = parseDouble(fields, indexByName, schema.high, lineNumber)
                val low = parseDouble(fields, indexByName, schema.low, lineNumber)
                val close = parseDouble(fields, indexByName, schema.close, lineNumber)
                val volume = parseLong(fields, indexByName, schema.volume, lineNumber)

                if (timestampSemantics != TimestampSemantics.START_TIME) {
                    throw CsvParseException("Unsupported timestamp semantics: $timestampSemantics")
                }

                yield(
                    Candle(
                        start = start,
                        endExclusive = endExclusive,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
                )
            }
        }
    }

    private fun splitLine(line: String, lineNumber: Int): List<String> {
        val rawFields = line.split(delimiter)
        val fields = if (trimWhitespace) rawFields.map { it.trim() } else rawFields
        if (fields.any { it.isEmpty() }) {
            throw CsvParseException("Empty field at line $lineNumber")
        }
        return fields
    }

    private fun validateHeader(columns: List<String>) {
        if (columns.isEmpty()) {
            throw CsvParseException("Missing header at line 1")
        }
        if (columns.toSet().size != columns.size) {
            throw CsvParseException("Duplicate column names in header at line 1")
        }
        val required = schema.requiredColumns()
        val allowed = schema.allowedColumns()
        val unknown = columns.filterNot { it in allowed }
        if (unknown.isNotEmpty()) {
            throw CsvParseException("Unknown column(s) in header at line 1: ${unknown.joinToString(",")}")
        }
        val missing = required.filterNot { it in columns }
        if (missing.isNotEmpty()) {
            throw CsvParseException("Missing column(s) in header at line 1: ${missing.joinToString(",")}")
        }
    }

    private fun parseTimestamp(
        fields: List<String>,
        indexByName: Map<String, Int>,
        lineNumber: Int
    ): Instant {
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

    private fun parseDouble(
        fields: List<String>,
        indexByName: Map<String, Int>,
        name: String,
        lineNumber: Int
    ): Double {
        val value = fields[indexByName.getValue(name)]
        return try {
            value.trim().toDouble()
        } catch (ex: Exception) {
            throw CsvParseException("Invalid number at line $lineNumber in field $name: $value")
        }
    }

    private fun parseLong(
        fields: List<String>,
        indexByName: Map<String, Int>,
        name: String,
        lineNumber: Int
    ): Long {
        val value = fields[indexByName.getValue(name)]
        return try {
            value.trim().toLong()
        } catch (ex: Exception) {
            throw CsvParseException("Invalid number at line $lineNumber in field $name: $value")
        }
    }
}
