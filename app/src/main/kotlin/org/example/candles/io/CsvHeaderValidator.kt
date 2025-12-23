package org.example.candles.io

object CsvHeaderValidator {
    fun validate(columns: List<String>, schema: CsvSchema) {
        if (columns.isEmpty()) {
            throw CsvParseException("Missing header at line 1")
        }
        if (columns.toSet().size != columns.size) {
            throw CsvParseException("Duplicate column names in header at line 1")
        }
        val required = schema.requiredColumns()
        val allowed = schema.allowedColumns()
        val unknown = columns.filterNot { it in allowed }
        if (unknown.isNotEmpty()) {
            throw CsvParseException("Unknown column(s) in header at line 1: ${unknown.joinToString(",")}")
        }
        val missing = required.filterNot { it in columns }
        if (missing.isNotEmpty()) {
            throw CsvParseException("Missing column(s) in header at line 1: ${missing.joinToString(",")}")
        }
    }
}
