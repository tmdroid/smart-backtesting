package org.example.candles.io

import org.example.candles.domain.Candle

interface CandleSource {
    fun stream(): Sequence<Candle>
}
