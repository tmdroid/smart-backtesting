package org.example.candles.cli

import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.example.candles.engine.backtest.CustomDateRange
import org.example.candles.engine.backtest.MonthRange
import org.example.candles.engine.backtest.Period
import org.example.candles.engine.backtest.SingleMonth
import org.example.candles.engine.backtest.SingleYear


data class BacktestCliConfig(
    val input: Path,
    val targetTimeframe: Timeframe,
    val periods: List<Period>,
    val schema: CsvSchema,
    val timestampFormat: TimestampFormat,
    val sessionStart: LocalTime,
    val sessionEnd: LocalTime,
    val stopLossPoints: Double,
    val takeProfitPoints: Double,
    val breakEvenTriggerPoints: Double?
) {
    companion object {
        fun parse(args: Array<String>): BacktestCliConfig {
            var input: Path? = null
            var targetTimeframe: Timeframe? = null
            var timestampFormat: TimestampFormat = TimestampFormat.ISO_8601_UTC
            var schemaPreset: String? = null
            var sessionStart: LocalTime = LocalTime.parse("09:30")
            var sessionEnd: LocalTime = LocalTime.parse("10:30")
            var stopLoss = 10.0
            var takeProfit = 20.0
            var breakEven: Double? = null
            val periods = mutableListOf<Period>()

            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--input" -> {
                        input = Path.of(requireValue(args, index, arg))
                        index += 2
                    }
                    "--tf" -> {
                        targetTimeframe = Timeframe.parse(requireValue(args, index, arg))
                        index += 2
                    }
                    "--timestamp-format" -> {
                        timestampFormat = parseTimestampFormat(requireValue(args, index, arg))
                        index += 2
                    }
                    "--schema" -> {
                        schemaPreset = requireValue(args, index, arg)
                        index += 2
                    }
                    "--session-start" -> {
                        sessionStart = LocalTime.parse(requireValue(args, index, arg))
                        index += 2
                    }
                    "--session-end" -> {
                        sessionEnd = LocalTime.parse(requireValue(args, index, arg))
                        index += 2
                    }
                    "--sl" -> {
                        stopLoss = requireValue(args, index, arg).toDouble()
                        index += 2
                    }
                    "--tp" -> {
                        takeProfit = requireValue(args, index, arg).toDouble()
                        index += 2
                    }
                    "--be" -> {
                        breakEven = requireValue(args, index, arg).toDouble()
                        index += 2
                    }
                    "--start-date" -> {
                        val start = LocalDate.parse(requireValue(args, index, arg))
                        if (index + 2 >= args.size || args[index + 2] != "--end-date") {
                            throw IllegalArgumentException("--start-date must be followed by --end-date")
                        }
                        val end = LocalDate.parse(requireValue(args, index + 2, "--end-date"))
                        periods.add(CustomDateRange(start, end))
                        index += 4
                    }
                    "--month" -> {
                        val ym = YearMonth.parse(requireValue(args, index, arg))
                        periods.add(SingleMonth(ym.year, ym.monthValue))
                        index += 2
                    }
                    "--year" -> {
                        val year = requireValue(args, index, arg).toInt()
                        periods.add(SingleYear(year))
                        index += 2
                    }
                    "--month-range" -> {
                        val range = requireValue(args, index, arg)
                        val parts = range.split(':')
                        if (parts.size != 2) {
                            throw IllegalArgumentException("--month-range expects YYYY-MM:YYYY-MM")
                        }
                        val start = YearMonth.parse(parts[0])
                        val end = YearMonth.parse(parts[1])
                        periods.add(MonthRange(start, end))
                        index += 2
                    }
                    else -> throw IllegalArgumentException("Unknown argument: $arg")
                }
            }

            val resolvedInput = input ?: throw IllegalArgumentException("--input is required")
            val resolvedTarget = targetTimeframe ?: Timeframe.parse("1m")
            val resolvedSchema = parseSchema(schemaPreset)
            if (periods.isEmpty()) {
                throw IllegalArgumentException("At least one period is required")
            }
            if (stopLoss <= 0.0 || takeProfit <= 0.0) {
                throw IllegalArgumentException("--sl and --tp must be > 0")
            }

            return BacktestCliConfig(
                input = resolvedInput,
                targetTimeframe = resolvedTarget,
                periods = periods,
                schema = resolvedSchema,
                timestampFormat = timestampFormat,
                sessionStart = sessionStart,
                sessionEnd = sessionEnd,
                stopLossPoints = stopLoss,
                takeProfitPoints = takeProfit,
                breakEvenTriggerPoints = breakEven
            )
        }

        private fun requireValue(args: Array<String>, index: Int, flag: String): String {
            if (index + 1 >= args.size) {
                throw IllegalArgumentException("Missing value for $flag")
            }
            return args[index + 1]
        }

        private fun parseTimestampFormat(value: String): TimestampFormat {
            return when (value.lowercase()) {
                "iso" -> TimestampFormat.ISO_8601_UTC
                "epochmillis" -> TimestampFormat.EPOCH_MILLIS
                "epochnanos" -> TimestampFormat.EPOCH_NANOS
                else -> throw IllegalArgumentException("Unsupported timestamp format: $value")
            }
        }

        private fun parseSchema(value: String?): CsvSchema {
            return when (value) {
                null, "default" -> CsvSchema()
                "mnq" -> CsvSchema(timestamp = "ts_event", optionalColumns = setOf("symbol", "source_symbol"))
                else -> throw IllegalArgumentException("Unsupported schema preset: $value")
            }
        }
    }
}
