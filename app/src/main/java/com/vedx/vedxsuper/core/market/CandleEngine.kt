package com.vedx.vedxsuper.core.market

import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.Price
import com.vedx.vedxsuper.data.TickData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * V4 CandleEngine
 * Responsible for aggregating ticks into OHLC candles for specific timeframes.
 */
class CandleEngine(private val intervalMin: Int) {
    private val _candles = MutableStateFlow<List<Candle>>(emptyList())
    val candles = _candles.asStateFlow()

    private var currentOpen = 0
    private var currentHigh = 0
    private var currentLow = Int.MAX_VALUE
    private var currentVol = 0L
    private var candleStart = 0L
    private var lastClose = 0

    fun onTick(tick: TickData) {
        val priceCents = (tick.ltp * 100).toInt()
        val volume = tick.volume.toInt()
        val timestamp = tick.ts

        if (candleStart == 0L) {
            candleStart = timestamp
            currentOpen = priceCents
        }
        val intervalMs = intervalMin * 60_000L
        if (timestamp - candleStart >= intervalMs) {
            closeCandle()
            candleStart = timestamp
            currentOpen = priceCents
            currentHigh = priceCents
            currentLow = priceCents
            currentVol = volume.toLong()
        } else {
            if (priceCents > currentHigh) currentHigh = priceCents
            if (priceCents < currentLow) currentLow = priceCents
            currentVol += volume
        }
        lastClose = priceCents
    }

    private fun closeCandle() {
        if (candleStart == 0L) return
        val c = Candle(
            open = Price(currentOpen),
            high = Price(currentHigh),
            low = Price(currentLow),
            close = Price(lastClose),
            volume = currentVol,
            timestamp = candleStart,
            isComplete = true
        )
        val updated = _candles.value + c
        // Keep last 500 candles
        _candles.value = if (updated.size > 500) updated.takeLast(300) else updated
    }

    fun initialize(history: List<Candle>) {
        _candles.value = history
    }

    fun reset() {
        _candles.value = emptyList()
        candleStart = 0L
        currentOpen = 0
        currentHigh = 0
        currentLow = Int.MAX_VALUE
        currentVol = 0L
        lastClose = 0
    }
}
