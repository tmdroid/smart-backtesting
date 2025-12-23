package org.example.candles.io

import java.io.RandomAccessFile
import java.nio.file.Path
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe

class CsvCandleRangeSource(
    private val path: Path,
    private val sourceTimeframe: Timeframe,
    private val timestampFormat: TimestampFormat,
    private val schema: CsvSchema,
    private val headerColumns: List<String>,
    private val startOffset: Long,
    private val endOffset: Long
) : CandleSource {
    override fun stream(): Sequence<Candle> = sequence {
        val parser = CsvRowParser(sourceTimeframe, schema, timestampFormat, headerColumns)
        RandomAccessFile(path.toFile(), "r").use { raf ->
            raf.seek(startOffset)
            var lineNumber = 1
            while (raf.filePointer < endOffset) {
                val line = raf.readLine() ?: break
                if (line.isBlank()) {
                    throw CsvParseException("Empty line at range line $lineNumber")
                }
                yield(parser.parseCandle(line, lineNumber))
                lineNumber++
            }
        }
    }
}
