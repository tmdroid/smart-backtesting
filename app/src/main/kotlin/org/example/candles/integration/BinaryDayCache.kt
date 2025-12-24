package org.example.candles.integration

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Properties
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.io.CsvSchema
import org.example.candles.io.TimestampFormat
import org.example.candles.util.Log

class BinaryDayCache(
    private val sourceTimeframe: Timeframe = Timeframe.parse("1m"),
    private val zoneId: ZoneId = ZoneId.of("America/New_York")
) {
    private val magic = "CBIN"
    private val version = 1

    fun load(
        path: Path,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        day: LocalDate
    ): List<Candle>? {
        val dir = cacheDirInternal(path, schema, timestampFormat)
        val binPath = dir.resolve("${day}.bin")
        val metaPath = dir.resolve("${day}.meta")
        if (!Files.exists(binPath) || !Files.exists(metaPath)) {
            Log.info("Cache miss (disk): missing files for $day in ${dir.fileName}")
            return null
        }
        if (!metaMatches(metaPath, path, schema, timestampFormat, day)) {
            Log.info("Cache miss (disk): meta mismatch for $day in ${dir.fileName}")
            return null
        }

        try {
            DataInputStream(BufferedInputStream(Files.newInputStream(binPath))).use { input ->
                val fileMagic = CharArray(4) { input.readByte().toInt().toChar() }.concatToString()
                if (fileMagic != magic) return null
                val fileVersion = input.readInt()
                if (fileVersion != version) return null
                val dayInt = input.readInt()
                val count = input.readInt()
                val expectedDay = dayToInt(day)
                if (dayInt != expectedDay || count < 0) return null

                val candles = ArrayList<Candle>(count)
                repeat(count) {
                    val startMillis = input.readLong()
                    val open = input.readDouble()
                    val high = input.readDouble()
                    val low = input.readDouble()
                    val close = input.readDouble()
                    val volume = input.readLong()
                    val start = Instant.ofEpochMilli(startMillis)
                    val endExclusive = start.plusMillis(sourceTimeframe.millis)
                    candles.add(Candle(start, endExclusive, open, high, low, close, volume))
                }
                return candles
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun save(
        path: Path,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        day: LocalDate,
        candles: List<Candle>
    ) {
        val dir = cacheDirInternal(path, schema, timestampFormat)
        Files.createDirectories(dir)
        val binPath = dir.resolve("${day}.bin.tmp")
        val metaPath = dir.resolve("${day}.meta.tmp")

        DataOutputStream(BufferedOutputStream(Files.newOutputStream(binPath))).use { output ->
            output.writeBytes(magic)
            output.writeInt(version)
            output.writeInt(dayToInt(day))
            output.writeInt(candles.size)
            for (candle in candles) {
                output.writeLong(candle.start.toEpochMilli())
                output.writeDouble(candle.open)
                output.writeDouble(candle.high)
                output.writeDouble(candle.low)
                output.writeDouble(candle.close)
                output.writeLong(candle.volume)
            }
        }

        val props = Properties()
        val stat = Files.getLastModifiedTime(path)
        props.setProperty("csvPath", path.toAbsolutePath().toString())
        props.setProperty("csvSize", Files.size(path).toString())
        props.setProperty("csvMtime", stat.toMillis().toString())
        props.setProperty("schemaTimestamp", schema.timestamp)
        props.setProperty("format", timestampFormat.name)
        props.setProperty("zoneId", zoneId.id)
        props.setProperty("day", day.toString())
        props.setProperty("count", candles.size.toString())
        props.setProperty("version", version.toString())
        Files.newOutputStream(metaPath).use { output ->
            props.store(output, null)
        }

        Files.move(binPath, dir.resolve("${day}.bin"), StandardCopyOption.REPLACE_EXISTING)
        Files.move(metaPath, dir.resolve("${day}.meta"), StandardCopyOption.REPLACE_EXISTING)
    }

    fun cachedDays(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): List<LocalDate> {
        val dir = cacheDirInternal(path, schema, timestampFormat)
        if (!Files.exists(dir)) return emptyList()
        return try {
            val result = ArrayList<LocalDate>()
            Files.list(dir).use { stream ->
                val iterator = stream.iterator()
                while (iterator.hasNext()) {
                    val file = iterator.next()
                    val name = file.fileName.toString()
                    if (name.endsWith(".bin")) {
                        val day = runCatching { LocalDate.parse(name.removeSuffix(".bin")) }.getOrNull()
                        if (day != null) {
                            result.add(day)
                        }
                    }
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isCached(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat, day: LocalDate): Boolean {
        val dir = cacheDirInternal(path, schema, timestampFormat)
        val binPath = dir.resolve("${day}.bin")
        val metaPath = dir.resolve("${day}.meta")
        if (!Files.exists(binPath) || !Files.exists(metaPath)) return false
        return metaMatches(metaPath, path, schema, timestampFormat, day)
    }

    private fun metaMatches(
        metaPath: Path,
        csvPath: Path,
        schema: CsvSchema,
        timestampFormat: TimestampFormat,
        day: LocalDate
    ): Boolean {
        return try {
            val props = Properties()
            Files.newInputStream(metaPath).use { props.load(it) }
            val size = Files.size(csvPath).toString()
            val mtime = Files.getLastModifiedTime(csvPath).toMillis().toString()
            props.getProperty("csvSize") == size &&
                props.getProperty("csvMtime") == mtime &&
            props.getProperty("schemaTimestamp") == schema.timestamp &&
                props.getProperty("format") == timestampFormat.name &&
                props.getProperty("zoneId") == zoneId.id &&
                props.getProperty("day") == day.toString() &&
                props.getProperty("version") == version.toString()
        } catch (_: Exception) {
            false
        }
    }

    fun cacheDirFor(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): Path {
        return cacheDirInternal(path, schema, timestampFormat)
    }

    private fun cacheDirInternal(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): Path {
        val dir = path.parent.resolve(".candle-cache")
        val base = path.fileName.toString()
        val fingerprint = fingerprint(path, schema, timestampFormat)
        return dir.resolve("${base}_${fingerprint}")
    }

    private fun fingerprint(path: Path, schema: CsvSchema, timestampFormat: TimestampFormat): String {
        val stat = Files.getLastModifiedTime(path).toMillis()
        val content = listOf(
            path.toAbsolutePath().toString(),
            Files.size(path).toString(),
            stat.toString(),
            schema.timestamp,
            timestampFormat.name
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { String.format("%02x", it) }
    }

    private fun dayToInt(day: LocalDate): Int {
        return day.year * 10000 + day.monthValue * 100 + day.dayOfMonth
    }
}
