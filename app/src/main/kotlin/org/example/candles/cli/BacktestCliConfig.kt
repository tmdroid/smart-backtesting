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
    val sessions: List<SessionSpec>
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
            val sessions = mutableListOf<SessionWindow>()
            val sessionRisks = mutableListOf<RiskParams>()
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
                    "--session" -> {
                        val raw = requireValue(args, index, arg)
                        sessions.add(parseSession(raw))
                        index += 2
                    }
                    "--session-risk" -> {
                        val raw = requireValue(args, index, arg)
                        sessionRisks.add(parseSessionRisk(raw))
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
            val resolvedSessionWindows = if (sessions.isEmpty()) {
                listOf(SessionWindow(sessionStart, sessionEnd))
            } else {
                sessions.toList()
            }
            val defaultRisk = RiskParams(stopLoss, takeProfit, breakEven)
            val resolvedSessionRisks = when {
                sessionRisks.isEmpty() -> List(resolvedSessionWindows.size) { defaultRisk }
                sessionRisks.size == 1 && resolvedSessionWindows.size > 1 ->
                    List(resolvedSessionWindows.size) { sessionRisks.first() }
                sessionRisks.size == resolvedSessionWindows.size -> sessionRisks.toList()
                else -> throw IllegalArgumentException(
                    "Number of --session-risk entries must be 1 or equal to number of --session entries"
                )
            }
            val resolvedSessions = resolvedSessionWindows.zip(resolvedSessionRisks).map { (window, risk) ->
                SessionSpec(window.start, window.end, risk)
            }

            return BacktestCliConfig(
                input = resolvedInput,
                targetTimeframe = resolvedTarget,
                periods = periods,
                schema = resolvedSchema,
                timestampFormat = timestampFormat,
                sessions = resolvedSessions
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

        private fun parseSession(raw: String): SessionWindow {
            val parts = raw.split('-')
            if (parts.size != 2) {
                throw IllegalArgumentException("--session expects HH:mm-HH:mm")
            }
            return SessionWindow(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]))
        }

        private fun parseSessionRisk(raw: String): RiskParams {
            val parts = raw.split(',')
            if (parts.size !in 2..3) {
                throw IllegalArgumentException("--session-risk expects sl,tp or sl,tp,be")
            }
            val sl = parts[0].toDouble()
            val tp = parts[1].toDouble()
            val be = if (parts.size == 3) parts[2].toDouble() else null
            if (sl <= 0.0 || tp <= 0.0) {
                throw IllegalArgumentException("--session-risk sl and tp must be > 0")
            }
            return RiskParams(sl, tp, be)
        }
    }
}

data class SessionWindow(
    val start: LocalTime,
    val end: LocalTime
)

data class RiskParams(
    val stopLossPoints: Double,
    val takeProfitPoints: Double,
    val breakEvenTriggerPoints: Double?
)

data class SessionSpec(
    val start: LocalTime,
    val end: LocalTime,
    val risk: RiskParams
)
