package org.example.candles.io

import java.nio.file.Paths
import org.example.candles.domain.Timeframe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CsvCandleSourceEdgeCasesTest {
    @Test
    fun `rejects timestamps without zone`() {
        val path = Paths.get(requireNotNull(javaClass.getResource("/fixtures/no-zone.csv")).toURI())
        val source = CsvCandleSource(
            path = path,
            sourceTimeframe = Timeframe.parse("1m"),
            timestampFormat = TimestampFormat.ISO_8601_UTC
        )
        assertThrows(CsvParseException::class.java) {
            source.stream().first()
        }
    }

    @Test
    fun `rejects duplicate header names`() {
        val path = Paths.get(requireNotNull(javaClass.getResource("/fixtures/duplicate-header.csv")).toURI())
        val source = CsvCandleSource(
            path = path,
            sourceTimeframe = Timeframe.parse("1m"),
            timestampFormat = TimestampFormat.ISO_8601_UTC
        )
        assertThrows(CsvParseException::class.java) {
            source.stream().first()
        }
    }

    @Test
    fun `rejects rows with wrong column count`() {
        val path = Paths.get(requireNotNull(javaClass.getResource("/fixtures/wrong-column-count.csv")).toURI())
        val source = CsvCandleSource(
            path = path,
            sourceTimeframe = Timeframe.parse("1m"),
            timestampFormat = TimestampFormat.ISO_8601_UTC
        )
        assertThrows(CsvParseException::class.java) {
            source.stream().first()
        }
    }
}
