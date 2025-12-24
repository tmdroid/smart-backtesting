package org.example.candles.cli

import java.nio.file.Path
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat

data class CliConfig(
    val input: Path,
    val targetTimeframe: Timeframe,
    val output: Path?,
    val timestampFormat: TimestampFormat,
    val schema: CsvSchema
) {
    companion object {
        fun parse(args: Array<String>): CliConfig {
            var input: Path? = null
            var targetTimeframe: Timeframe? = null
            var output: Path? = null
            var timestampFormat: TimestampFormat = TimestampFormat.ISO_8601_UTC
            var schemaPreset: String? = null

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
                    "--out" -> {
                        output = Path.of(requireValue(args, index, arg))
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
                    else -> throw IllegalArgumentException("Unknown argument: $arg")
                }
            }

            val resolvedInput = input ?: throw IllegalArgumentException("--input is required")
            val resolvedTarget = targetTimeframe ?: throw IllegalArgumentException("--tf is required")
            val resolvedSchema = parseSchema(schemaPreset)

            return CliConfig(
                input = resolvedInput,
                targetTimeframe = resolvedTarget,
                output = output,
                timestampFormat = timestampFormat,
                schema = resolvedSchema
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
