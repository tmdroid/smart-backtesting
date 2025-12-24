package org.example.candles.integration

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Properties
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.example.candles.aggregation.aggregate
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvCandleRangeSource
import org.example.candles.io.CsvCandleSource
import org.example.candles.io.CsvHeaderValidator
import org.example.candles.io.CsvParseException
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.example.candles.util.Log

class AggregationService(
    private val executor: ExecutorService,
    private val runner: ((Path, Timeframe, CsvSchema, TimestampFormat, LocalDate?) -> List<Candle>)? = null,
    private val prebuildExecutor: ExecutorService = Executors.newFixedThreadPool(defaultPrebuildThreads())
) {
    private val requestId = AtomicLong(0)
    private val dayRequestId = AtomicLong(0)
    private val prebuildId = AtomicLong(0)
    private var activeFuture: Future<*>? = null
    private var index: CsvFileIndex? = null
    private var cachedDay: LocalDate? = null
    private var cachedRaw: List<Candle>? = null
    private var cachedPath: Path? = null
    private var cachedSchema: CsvSchema? = null
    private var cachedFormat: TimestampFormat? = null
    private val binaryCache = BinaryDayCache(sourceTimeframe)

    fun aggregate(
        path: Path,
        timeframe: Timeframe,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        day: LocalDate?,
        onSuccess: (List<Candle>, Timeframe) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val id = requestId.incrementAndGet()
        activeFuture?.cancel(true)
        Log.info("Aggregation request $id: timeframe=$timeframe path=$path format=$timestampFormat schema=${schema.timestamp} day=${day ?: "all"}")
        activeFuture = executor.submit {
            try {
                val result = runner?.invoke(path, timeframe, schema, timestampFormat, day)
                    ?: aggregateInternal(path, timeframe, schema, timestampFormat, day)
                if (requestId.get() == id) {
                    Log.info("Aggregation request $id completed: ${result.size} candles")
                    onSuccess(result, timeframe)
                }
            } catch (_: CancellationException) {
                Log.info("Aggregation request $id canceled")
                // Ignore canceled tasks
            } catch (ex: Exception) {
                if (requestId.get() == id) {
                    Log.warn("Aggregation request $id failed: ${ex.message}")
                    onError(ex)
                }
            }
        }
    }

    fun shutdown() {
        activeFuture?.cancel(true)
        executor.shutdownNow()
        prebuildExecutor.shutdownNow()
    }

    fun detectLastDay(
        path: Path,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        onStatus: (String) -> Unit = {},
        onSuccess: (LocalDate?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val id = dayRequestId.incrementAndGet()
        executor.submit {
            try {
                onStatus("Checking cache...")
                val cachedDays = binaryCache.cachedDays(path, schema, timestampFormat)
                val day = if (cachedDays.isNotEmpty()) {
                    Log.info("Detected day from cache: ${cachedDays.size} cached day(s)")
                    cachedDays.maxOrNull()
                } else {
                    Log.info("No cached days found; building CSV index")
                    onStatus("Indexing file...")
                    val fileIndex = ensureIndex(path, schema, timestampFormat)
                    fileIndex.dayRanges.keys.maxOrNull()
                }
                if (dayRequestId.get() == id) {
                    onSuccess(day)
                }
            } catch (ex: Exception) {
                if (dayRequestId.get() == id) {
                    onError(ex)
                }
            }
        }
    }

    fun prebuildDayCaches(
        path: Path,
        schema: CsvSchema,
        timestampFormat: TimestampFormat
    ) {
        val id = prebuildId.incrementAndGet()
        executor.submit {
            try {
                val fileIndex = ensureIndex(path, schema, timestampFormat)
                val days = fileIndex.dayRanges.keys.sorted()
                Log.info("Prebuild cache: ${days.size} day(s) detected")
                val completion = ExecutorCompletionService<Unit>(prebuildExecutor)
                var submitted = 0
                var completed = 0
                for (day in days) {
                    if (prebuildId.get() != id) {
                        Log.info("Prebuild cache canceled")
                        return@submit
                    }
                    if (binaryCache.isCached(path, schema, timestampFormat, day)) {
                        completed++
                        logPrebuildProgress(completed, days.size)
                        continue
                    }
                    completion.submit {
                        if (prebuildId.get() != id) return@submit
                        val range = fileIndex.dayRanges[day] ?: return@submit
                        val rangeSource = CsvCandleRangeSource(
                            path = path,
                            sourceTimeframe = sourceTimeframe,
                            timestampFormat = timestampFormat,
                            schema = schema,
                            headerColumns = fileIndex.headerColumns,
                            startOffset = range.startOffset,
                            endOffset = range.endOffset
                        )
                        val raw = rangeSource.stream().toList()
                        binaryCache.save(path, schema, timestampFormat, day, raw)
                    }
                    submitted++
                }
                repeat(submitted) {
                    if (prebuildId.get() != id) {
                        Log.info("Prebuild cache canceled")
                        return@submit
                    }
                    completion.take()
                    completed++
                    logPrebuildProgress(completed, days.size)
                }
            } catch (ex: Exception) {
                Log.warn("Prebuild cache failed: ${ex.message}")
            }
        }
    }

    companion object {
        private val sourceTimeframe: Timeframe = Timeframe.parse("1m")
        private val backtestZone: ZoneId = ZoneId.of("America/New_York")
        private const val indexVersion = 1
        private fun defaultPrebuildThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return cores.coerceIn(2, 8)
        }
    }

    private fun logPrebuildProgress(done: Int, total: Int) {
        Log.info("Prebuild cache progress: $done/$total")
    }

    private fun ensureIndex(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): CsvFileIndex {
        val existing = index
        if (existing != null && existing.matches(path, schema, timestampFormat)) {
            return existing
        }
        val loaded = loadIndex(path, schema, timestampFormat)
        if (loaded != null) {
            index = loaded
            cachedDay = null
            cachedRaw = null
            cachedPath = path
            cachedSchema = schema
            cachedFormat = timestampFormat
            return loaded
        }
        val built = buildIndex(path, schema, timestampFormat)
        saveIndex(built)
        index = built
        cachedDay = null
        cachedRaw = null
        cachedPath = path
        cachedSchema = schema
        cachedFormat = timestampFormat
        return built
    }

    private fun loadRawDayCandles(
        path: Path,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        day: LocalDate
    ): List<Candle> {
        if (path == cachedPath && schema == cachedSchema && timestampFormat == cachedFormat && day == cachedDay) {
            Log.info("Cache hit (memory) for $day")
            return cachedRaw ?: emptyList()
        }
        val cached = binaryCache.load(path, schema, timestampFormat, day)
        if (cached != null) {
            Log.info("Cache hit (disk) for $day")
            cachedDay = day
            cachedRaw = cached
            cachedPath = path
            cachedSchema = schema
            cachedFormat = timestampFormat
            return cached
        }
        Log.info("Cache miss for $day; parsing CSV range")
        val fileIndex = ensureIndex(path, schema, timestampFormat)
        val range = fileIndex.dayRanges[day] ?: return emptyList()
        val rangeSource = CsvCandleRangeSource(
            path = path,
            sourceTimeframe = sourceTimeframe,
            timestampFormat = timestampFormat,
            schema = schema,
            headerColumns = fileIndex.headerColumns,
            startOffset = range.startOffset,
            endOffset = range.endOffset
        )
        val raw = rangeSource.stream().toList()
        Log.info("Cache write (disk) for $day: ${raw.size} candles")
        binaryCache.save(path, schema, timestampFormat, day, raw)
        cachedDay = day
        cachedRaw = raw
        cachedPath = path
        cachedSchema = schema
        cachedFormat = timestampFormat
        return raw
    }

    private fun aggregateInternal(
        path: Path,
        timeframe: Timeframe,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        day: LocalDate?
    ): List<Candle> {
        val results = ArrayList<Candle>()
        if (timeframe.millis == sourceTimeframe.millis) {
            val raw = if (day == null) {
                val source = CsvCandleSource(path, sourceTimeframe, timestampFormat = timestampFormat, schema = schema)
                source.stream().toList()
            } else {
                loadRawDayCandles(path, schema, timestampFormat, day)
            }
            results.addAll(raw)
            return results
        }
        if (day == null) {
            val source = CsvCandleSource(path, sourceTimeframe, timestampFormat = timestampFormat, schema = schema)
            for (candle in aggregate(source.stream(), sourceTimeframe, timeframe)) {
                if (Thread.currentThread().isInterrupted) {
                    throw CancellationException("Aggregation canceled")
                }
                results.add(candle)
            }
            return results
        }
        val raw = loadRawDayCandles(path, schema, timestampFormat, day)
        for (candle in aggregate(raw.asSequence(), sourceTimeframe, timeframe)) {
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Aggregation canceled")
            }
            results.add(candle)
        }
        return results
    }

    private fun buildIndex(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): CsvFileIndex {
        val ranges = linkedMapOf<LocalDate, DayRange>()
        val raf = RandomAccessFile(path.toFile(), "r")
        raf.use { file ->
            val headerLine = file.readLine() ?: throw CsvParseException("Missing header at line 1")
            val headerColumns = headerLine.split(',').map { it.trim() }
            CsvHeaderValidator.validate(headerColumns, schema)
            val tsIndex = headerColumns.indexOf(schema.timestamp)
            if (tsIndex < 0) throw CsvParseException("Missing timestamp column in header")

            var lastDay: LocalDate? = null
            var dayStartOffset: Long = file.filePointer

            while (true) {
                val lineStart = file.filePointer
                val line = file.readLine() ?: break
                if (line.isBlank()) continue
                val fields = line.split(',').map { it.trim() }
                if (tsIndex >= fields.size) continue
                val day = parseDay(fields[tsIndex], timestampFormat)
                if (lastDay == null) {
                    lastDay = day
                    dayStartOffset = lineStart
                } else if (day != lastDay) {
                    ranges[lastDay] = DayRange(dayStartOffset, lineStart)
                    lastDay = day
                    dayStartOffset = lineStart
                }
            }
            if (lastDay != null) {
                ranges[lastDay] = DayRange(dayStartOffset, file.length())
            }
            return CsvFileIndex(path, schema, timestampFormat, headerColumns, ranges)
        }
    }

    private fun loadIndex(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): CsvFileIndex? {
        val dir = binaryCache.cacheDirFor(path, schema, timestampFormat)
        val metaPath = dir.resolve("index.meta")
        val dataPath = dir.resolve("index.csv")
        if (!Files.exists(metaPath) || !Files.exists(dataPath)) {
            return null
        }
        val props = Properties()
        try {
            Files.newInputStream(metaPath).use { props.load(it) }
            if (!indexMetaMatches(path, schema, timestampFormat, props)) {
                return null
            }
            val headerCount = props.getProperty("header.count")?.toIntOrNull() ?: return null
            val headers = (0 until headerCount).map { idx ->
                props.getProperty("header.$idx") ?: return null
            }
            val ranges = linkedMapOf<LocalDate, DayRange>()
            Files.newBufferedReader(dataPath).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split(',')
                    if (parts.size < 3) return@forEach
                    val day = runCatching { LocalDate.parse(parts[0].trim()) }.getOrNull() ?: return@forEach
                    val start = parts[1].trim().toLongOrNull() ?: return@forEach
                    val end = parts[2].trim().toLongOrNull() ?: return@forEach
                    ranges[day] = DayRange(start, end)
                }
            }
            if (ranges.isEmpty()) return null
            return CsvFileIndex(path, schema, timestampFormat, headers, ranges)
        } catch (_: Exception) {
            return null
        }
    }

    private fun saveIndex(index: CsvFileIndex) {
        val dir = binaryCache.cacheDirFor(index.path, index.schema, index.format)
        Files.createDirectories(dir)
        val metaPath = dir.resolve("index.meta.tmp")
        val dataPath = dir.resolve("index.csv.tmp")
        val props = Properties()
        val stat = Files.getLastModifiedTime(index.path)
        props.setProperty("csvPath", index.path.toAbsolutePath().toString())
        props.setProperty("csvSize", Files.size(index.path).toString())
        props.setProperty("csvMtime", stat.toMillis().toString())
        props.setProperty("schemaTimestamp", index.schema.timestamp)
        props.setProperty("format", index.format.name)
        props.setProperty("version", indexVersion.toString())
        props.setProperty("zoneId", backtestZone.id)
        props.setProperty("header.count", index.headerColumns.size.toString())
        index.headerColumns.forEachIndexed { i, name ->
            props.setProperty("header.$i", name)
        }
        Files.newOutputStream(metaPath).use { props.store(it, null) }
        Files.newBufferedWriter(dataPath).use { writer ->
            for ((day, range) in index.dayRanges) {
                writer.write("${day},${range.startOffset},${range.endOffset}")
                writer.newLine()
            }
        }
        Files.move(metaPath, dir.resolve("index.meta"), StandardCopyOption.REPLACE_EXISTING)
        Files.move(dataPath, dir.resolve("index.csv"), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun indexMetaMatches(
        path: Path,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        props: Properties
    ): Boolean {
        return try {
            val size = Files.size(path).toString()
            val mtime = Files.getLastModifiedTime(path).toMillis().toString()
            props.getProperty("csvSize") == size &&
                props.getProperty("csvMtime") == mtime &&
                props.getProperty("schemaTimestamp") == schema.timestamp &&
                props.getProperty("format") == timestampFormat.name &&
                props.getProperty("version") == indexVersion.toString() &&
                props.getProperty("zoneId") == backtestZone.id
        } catch (_: Exception) {
            false
        }
    }

    private fun parseDay(value: String, format: TimestampFormat): LocalDate {
        val instant = when (format) {
            TimestampFormat.ISO_8601_UTC -> Instant.parse(value)
            TimestampFormat.EPOCH_MILLIS -> Instant.ofEpochMilli(value.toLong())
            TimestampFormat.EPOCH_NANOS -> {
                val nanos = value.toLong()
                val seconds = Math.floorDiv(nanos, 1_000_000_000L)
                val nanoAdj = Math.floorMod(nanos, 1_000_000_000L)
                Instant.ofEpochSecond(seconds, nanoAdj)
            }
        }
        return instant.atZone(backtestZone).toLocalDate()
    }

    private data class DayRange(val startOffset: Long, val endOffset: Long)

    private data class CsvFileIndex(
        val path: Path,
        val schema: CsvSchema,
        val format: TimestampFormat,
        val headerColumns: List<String>,
        val dayRanges: Map<LocalDate, DayRange>
    ) {
        fun matches(path: Path, schema: CsvSchema, format: TimestampFormat): Boolean {
            return this.path == path && this.schema == schema && this.format == format
        }
    }

}
