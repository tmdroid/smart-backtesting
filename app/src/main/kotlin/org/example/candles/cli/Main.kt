package org.example.candles.cli

import java.io.BufferedWriter
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import org.example.candles.engine.backtest.BacktestExecutor
import org.example.candles.engine.backtest.BacktestResult
import org.example.candles.engine.backtest.BacktestRun
import org.example.candles.engine.backtest.StrategyFactory
import org.example.candles.engine.range.RangeDefinition
import org.example.candles.engine.range.TradingSessionTime
import org.example.candles.engine.strategy.RangeBreakoutStrategy
import org.example.candles.engine.strategy.TradeParameters
import org.example.candles.aggregation.aggregate
import org.example.candles.domain.Timeframe
import org.example.candles.domain.TimestampSemantics
import org.example.candles.integration.CachedBacktestSource
import org.example.candles.io.CsvCandleSource
import org.example.candles.util.Log

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
            "session=${config.sessionStart}-${config.sessionEnd} sl=${config.stopLossPoints} " +
            "tp=${config.takeProfitPoints} be=${config.breakEvenTriggerPoints ?: "none"}"
    )

    val strategyFactory = StrategyFactory {
        RangeBreakoutStrategy(
            id = "range-breakout",
            rangeDefinition = RangeDefinition(
                timeframe = config.targetTimeframe,
                sessionTime = TradingSessionTime(
                    timezone = nyZone,
                    start = config.sessionStart,
                    end = config.sessionEnd
                )
            ),
            tradeParameters = TradeParameters(
                stopLossPoints = config.stopLossPoints,
                takeProfitPoints = config.takeProfitPoints,
                breakEvenTriggerPoints = config.breakEvenTriggerPoints
            )
        )
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
            withCountLogging(aggregated, "Backtest candles (${config.targetTimeframe}) for ${range.start} -> ${range.endInclusive}")
        }
    }

    val run = BacktestRun(
        periods = config.periods,
        strategyFactories = listOf(strategyFactory),
        timezone = nyZone
    )
    val result = executor.run(run)

    printBacktestTable(result)
    println()
    printBacktestJson(result)
}

private fun <T> withCountLogging(sequence: Sequence<T>, label: String): Sequence<T> = sequence {
    var count = 0
    for (item in sequence) {
        count++
        yield(item)
    }
    Log.info("$label: $count")
}

private fun printBacktestTable(result: BacktestResult) {
    println("range_start,range_end,trades,wins,losses,breakevens,netPoints")
    for (rangeResult in result.rangeResults) {
        val perf = rangeResult.performance
        println("${rangeResult.dateRange.start},${rangeResult.dateRange.endInclusive},${perf.trades},${perf.wins},${perf.losses},${perf.breakevens},${perf.netPoints}")
    }
    val overall = result.overallPerformance
    println("OVERALL,OVERALL,${overall.trades},${overall.wins},${overall.losses},${overall.breakevens},${overall.netPoints}")
}

private fun printBacktestJson(result: BacktestResult) {
    val sb = StringBuilder()
    sb.append("{\"ranges\":[")
    result.rangeResults.forEachIndexed { index, rangeResult ->
        if (index > 0) sb.append(",")
        val perf = rangeResult.performance
        sb.append("{")
        sb.append("\"start\":\"").append(rangeResult.dateRange.start).append("\",")
        sb.append("\"end\":\"").append(rangeResult.dateRange.endInclusive).append("\",")
        sb.append("\"trades\":").append(perf.trades).append(",")
        sb.append("\"wins\":").append(perf.wins).append(",")
        sb.append("\"losses\":").append(perf.losses).append(",")
        sb.append("\"breakevens\":").append(perf.breakevens).append(",")
        sb.append("\"netPoints\":").append(perf.netPoints)
        sb.append("}")
    }
    sb.append("],\"overall\":{")
    val overall = result.overallPerformance
    sb.append("\"trades\":").append(overall.trades).append(",")
    sb.append("\"wins\":").append(overall.wins).append(",")
    sb.append("\"losses\":").append(overall.losses).append(",")
    sb.append("\"breakevens\":").append(overall.breakevens).append(",")
    sb.append("\"netPoints\":").append(overall.netPoints)
    sb.append("}}")
    println(sb.toString())
}
