package org.example.candles.integration

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Properties
import org.example.candles.io.CsvHeaderValidator
import org.example.candles.io.CsvParseException
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat

class CsvDayIndex(
    private val zoneId: ZoneId,
    private val binaryCache: BinaryDayCache,
    private val indexVersion: Int = 1
) {
    private var cachedIndex: CsvFileIndex? = null

    fun ensureIndex(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): CsvFileIndex {
        val existing = cachedIndex
        if (existing != null && existing.matches(path, schema, timestampFormat)) {
            return existing
        }
        val loaded = loadIndex(path, schema, timestampFormat)
        if (loaded != null) {
            cachedIndex = loaded
            return loaded
        }
        val built = buildIndex(path, schema, timestampFormat)
        saveIndex(built)
        cachedIndex = built
        return built
    }

    private fun buildIndex(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): CsvFileIndex {
        val ranges = linkedMapOf<LocalDate, DayRange>()
        RandomAccessFile(path.toFile(), "r").use { file ->
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
        return try {
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
            CsvFileIndex(path, schema, timestampFormat, headers, ranges)
        } catch (_: Exception) {
            null
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
        props.setProperty("zoneId", zoneId.id)
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
                props.getProperty("zoneId") == zoneId.id
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
        return instant.atZone(zoneId).toLocalDate()
    }
}

data class DayRange(val startOffset: Long, val endOffset: Long)

data class CsvFileIndex(
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
