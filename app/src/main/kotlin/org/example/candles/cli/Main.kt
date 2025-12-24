package org.example.candles.cli

import org.example.candles.aggregation.aggregate
import org.example.candles.domain.Timeframe
import org.example.candles.domain.TimestampSemantics
import org.example.candles.engine.backtest.BacktestExecutor
import org.example.candles.engine.backtest.BacktestRun
import org.example.candles.engine.backtest.StrategyFactory
import org.example.candles.engine.backtest.reporting.BacktestReporter
import org.example.candles.engine.range.RangeDefinition
import org.example.candles.engine.range.TradingSessionTime
import org.example.candles.engine.strategy.RangeBreakoutStrategy
import org.example.candles.engine.strategy.TradeParameters
import org.example.candles.integration.CachedBacktestSource
import org.example.candles.io.CsvCandleSource
import org.example.candles.util.Log
import java.io.BufferedWriter
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

fun main(args: Array<String>) {
    if (args.contains("--backtest")) {
        runBacktest(args.filterNot { it == "--backtest" }.toTypedArray())
        return
    }
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

private fun runBacktest(args: Array<String>) {
    val config = BacktestCliConfig.parse(args)
    val sourceTimeframe = Timeframe.parse("1m")
    val nyZone = ZoneId.of("America/New_York")
    Log.info(
        "Backtest config: input=${config.input} tf=${config.targetTimeframe} periods=${config.periods.size} " +
                "sessions=${config.sessions.joinToString { sessionLabel(it) }}"
    )

    val strategyFactories = config.sessions.mapIndexed { index, session ->
        StrategyFactory {
            RangeBreakoutStrategy(
                id = "range-breakout-${index + 1}",
                rangeDefinition = RangeDefinition(
                    timeframe = config.targetTimeframe,
                    sessionTime = TradingSessionTime(
                        timezone = nyZone,
                        start = session.start,
                        end = session.end
                    )
                ),
                tradeParameters = TradeParameters(
                    stopLossPoints = session.risk.stopLossPoints,
                    takeProfitPoints = session.risk.takeProfitPoints,
                    breakEvenTriggerPoints = session.risk.breakEvenTriggerPoints
                )
            )
        }
    }

    val cachedSource = CachedBacktestSource(
        path = config.input,
        schema = config.schema,
        timestampFormat = config.timestampFormat,
        sourceTimeframe = sourceTimeframe,
        zoneId = nyZone
    )
    cachedSource.prebuildAllDays()

    val executor = BacktestExecutor { range ->
        val raw = cachedSource.stream(range)
        if (config.targetTimeframe.millis == sourceTimeframe.millis) {
            withCountLogging(raw, "Backtest candles (1m) for ${range.start} -> ${range.endInclusive}")
        } else {
            val aggregated = aggregate(raw, sourceTimeframe, config.targetTimeframe)
            withCountLogging(
                aggregated,
                "Backtest candles (${config.targetTimeframe}) for ${range.start} -> ${range.endInclusive}"
            )
        }
    }

    val run = BacktestRun(
        periods = config.periods,
        strategyFactories = strategyFactories,
        timezone = nyZone
    )
    val result = executor.run(run)

    BacktestReporter.printReport(result)
    println()
    BacktestReporter.printJson(result)
}

private fun sessionLabel(session: SessionSpec): String {
    val be = session.risk.breakEvenTriggerPoints?.toString() ?: "none"
    return "${session.start}-${session.end}(sl=${session.risk.stopLossPoints},tp=${session.risk.takeProfitPoints},be=$be)"
}

private fun <T> withCountLogging(sequence: Sequence<T>, label: String): Sequence<T> = sequence {
    var count = 0
    for (item in sequence) {
        count++
        yield(item)
    }
    Log.info("$label: $count")
}
