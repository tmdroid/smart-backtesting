package org.example.candles.integration

import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.engine.backtest.DateRange
import org.example.candles.io.CsvCandleRangeSource
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.example.candles.util.Log

class CachedBacktestSource(
    private val path: Path,
    private val schema: CsvSchema,
    private val timestampFormat: TimestampFormat,
    private val sourceTimeframe: Timeframe,
    zoneId: ZoneId
) {
    private val binaryCache = BinaryDayCache(sourceTimeframe, zoneId)
    private val dayIndex = CsvDayIndex(zoneId, binaryCache)
    private var cachedIndex: CsvFileIndex? = null

    fun stream(range: DateRange): Sequence<Candle> = sequence {
        val dayCount = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
        Log.info("Backtest range ${range.start} -> ${range.endInclusive} ($dayCount day(s))")
        val days = generateSequence(range.start) { day ->
            if (day.isBefore(range.endInclusive)) day.plusDays(1) else null
        }
        for (day in days) {
            val candles = loadDay(day)
            if (candles.isEmpty()) {
                Log.info("Backtest day $day: no candles")
                continue
            }
            Log.info("Backtest day $day: ${candles.size} candles")
            for (candle in candles) {
                yield(candle)
            }
        }
    }

    fun prebuildAllDays() {
        val index = ensureIndex()
        val days = index.dayRanges.keys.sorted()
        Log.info("Backtest prebuild cache: ${days.size} day(s) detected")
        val threads = 10
        val executor = Executors.newFixedThreadPool(threads)
        val completion = ExecutorCompletionService<Unit>(executor)
        var submitted = 0
        var completed = 0
        for (day in days) {
            completion.submit {
                if (!binaryCache.isCached(path, schema, timestampFormat, day)) {
                    loadDay(day)
                }
            }
            submitted++
        }
        repeat(submitted) {
            completion.take()
            completed++
            if (completed % 25 == 0 || completed == days.size) {
                Log.info("Backtest prebuild cache progress: $completed/${days.size}")
            }
        }
        executor.shutdown()
    }

    private fun loadDay(day: LocalDate): List<Candle> {
        val cached = binaryCache.load(path, schema, timestampFormat, day)
        if (cached != null) {
//            Log.info("Backtest cache hit (disk) for $day")
            return cached
        }
        Log.info("Backtest cache miss for $day; reading CSV range")
        val index = ensureIndex()
        val range = index.dayRanges[day] ?: return emptyList()
        val rangeSource = CsvCandleRangeSource(
            path = path,
            sourceTimeframe = sourceTimeframe,
            timestampFormat = timestampFormat,
            schema = schema,
            headerColumns = index.headerColumns,
            startOffset = range.startOffset,
            endOffset = range.endOffset
        )
        val raw = rangeSource.stream().toList()
        Log.info("Backtest cache write (disk) for $day: ${raw.size} candles")
        binaryCache.save(path, schema, timestampFormat, day, raw)
        return raw
    }

    private fun ensureIndex(): CsvFileIndex {
        val existing = cachedIndex
        if (existing != null) return existing
        val index = dayIndex.ensureIndex(path, schema, timestampFormat)
        Log.info("Backtest index ready: ${index.dayRanges.size} day(s)")
        cachedIndex = index
        return index
    }
}
