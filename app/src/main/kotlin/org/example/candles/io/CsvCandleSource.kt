package org.example.candles.io

import java.nio.file.Files
import java.nio.file.Path
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
            CsvHeaderValidator.validate(headerColumns, schema)
            val parser = CsvRowParser(
                sourceTimeframe,
                schema,
                timestampFormat,
                headerColumns,
                delimiter,
                trimWhitespace
            )

            var lineNumber = 1
            while (lineIterator.hasNext()) {
                lineNumber++
                val line = lineIterator.next()
                if (line.isBlank()) {
                    throw CsvParseException("Empty line at $lineNumber")
                }
                if (timestampSemantics != TimestampSemantics.START_TIME) {
                    throw CsvParseException("Unsupported timestamp semantics: $timestampSemantics")
                }
                val fields = splitLine(line, lineNumber)
                if (fields.size != headerColumns.size) {
                    throw CsvParseException(
                        "Wrong column count at line $lineNumber: expected ${headerColumns.size}, got ${fields.size}"
                    )
                }
                yield(parser.parseCandle(line, lineNumber))
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
}
