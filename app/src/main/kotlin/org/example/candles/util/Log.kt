package org.example.candles.util

import java.time.Instant

object Log {
    var enabled: Boolean = true

    fun info(message: String) {
        if (!enabled) return
        println("[${Instant.now()}] $message")
    }

    fun warn(message: String) {
        if (!enabled) return
        println("[${Instant.now()}] WARN: $message")
    }
}
