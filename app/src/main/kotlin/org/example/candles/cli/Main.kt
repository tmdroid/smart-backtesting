package org.example.candles.cli

import java.io.BufferedWriter
import java.nio.file.Files
import java.time.Instant
import org.example.candles.aggregation.aggregate
import org.example.candles.domain.Timeframe
import org.example.candles.domain.TimestampSemantics
import org.example.candles.io.CsvCandleSource

fun main(args: Array<String>) {
    val config = CliConfig.parse(args)
    val sourceTimeframe = Timeframe.parse("1m")

    val source = CsvCandleSource(
        path = config.input,
        sourceTimeframe = sourceTimeframe,
        timestampFormat = config.timestampFormat,
        timestampSemantics = TimestampSemantics.START_TIME,
        schema = config.schema
    )

    val outputWriter = config.output?.let { Files.newBufferedWriter(it) }
    if (outputWriter != null) {
        outputWriter.use { writer ->
            writeOutput(writer, source, sourceTimeframe, config.targetTimeframe)
        }
    } else {
        val writer = System.out.bufferedWriter()
        writeOutput(writer, source, sourceTimeframe, config.targetTimeframe)
        writer.flush()
    }
}

private fun writeOutput(
    writer: BufferedWriter,
    source: CsvCandleSource,
    sourceTimeframe: Timeframe,
    targetTimeframe: Timeframe
) {
    writer.write("timestamp,open,high,low,close,volume")
    writer.newLine()
    val aggregated = aggregate(source.stream(), sourceTimeframe, targetTimeframe)
    for (candle in aggregated) {
        writer.write(formatCandle(candle.start, candle.open, candle.high, candle.low, candle.close, candle.volume))
        writer.newLine()
    }
}

private fun formatCandle(
    timestamp: Instant,
    open: Double,
    high: Double,
    low: Double,
    close: Double,
    volume: Long
): String {
    return listOf(timestamp.toString(), open, high, low, close, volume).joinToString(",")
}
