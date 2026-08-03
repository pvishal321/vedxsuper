package com.vedx.vedxsuper.strategy.candle

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.model.market.TickData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CandleBuilder(val intervalMinutes: Int = 1) {
    private val _candles = MutableStateFlow<List<Candle>>(emptyList())
    val candles = _candles.asStateFlow()

    private val _finalizedCandleFlow = MutableStateFlow<Candle?>(null)
    val finalizedCandleFlow = _finalizedCandleFlow.asStateFlow()

    private var finalizedCandles = mutableListOf<Candle>()
    private var currentCandle: Candle? = null
    private val MAX_CANDLES = 1000 // Increased for better chart history
    private val intervalMillis = intervalMinutes * 60 * 1000L

    fun initialize(candles: List<Candle>) {
        if (candles.isEmpty()) return
        
        val sorted = candles.sortedBy { it.timestamp }
        val aggregated = mutableListOf<Candle>()
        
        var tempOpen = 0.0
        var tempHigh = Double.MIN_VALUE
        var tempLow = Double.MAX_VALUE
        var tempVolume = 0L
        var tempClose = 0.0
        var startTime = 0L
        
        sorted.forEach { candle ->
            val intervalStart = (candle.timestamp / intervalMillis) * intervalMillis
            
            if (startTime == 0L) {
                startTime = intervalStart
                tempOpen = candle.open
                tempHigh = candle.high
                tempLow = candle.low
                tempClose = candle.close
                tempVolume = candle.volume
            } else if (intervalStart != startTime) {
                // Finalize previous interval
                aggregated.add(Candle(startTime, tempOpen, tempHigh, tempLow, tempClose, tempVolume, true))
                
                // Start new interval
                startTime = intervalStart
                tempOpen = candle.open
                tempHigh = candle.high
                tempLow = candle.low
                tempClose = candle.close
                tempVolume = candle.volume
            } else {
                tempHigh = maxOf(tempHigh, candle.high)
                tempLow = minOf(tempLow, candle.low)
                tempClose = candle.close
                tempVolume += candle.volume
            }
        }
        
        // Don't forget to add the last one
        if (startTime != 0L) {
            aggregated.add(Candle(startTime, tempOpen, tempHigh, tempLow, tempClose, tempVolume, true))
        }
        
        finalizedCandles = if (aggregated.size > MAX_CANDLES) aggregated.takeLast(MAX_CANDLES).toMutableList() else aggregated
        _candles.value = finalizedCandles.toList()
    }

    /**
     * Processes a new tick and updates the current candle or creates a new one.
     */
    fun onTick(tick: TickData) {
        // Use tick timestamp for backtesting accuracy, fallback to system time
        val tickTime = if (tick.timestamp > 0) tick.timestamp else System.currentTimeMillis()
        val intervalStart = (tickTime / intervalMillis) * intervalMillis

        if (currentCandle == null) {
            currentCandle = createNewCandle(intervalStart, tick.ltp)
        } else if (currentCandle!!.timestamp != intervalStart) {
            // Interval changed, finalize old candle
            val finalizedCandle = currentCandle!!.copy(isComplete = true)
            finalizedCandles.add(finalizedCandle)
            _finalizedCandleFlow.value = finalizedCandle
            
            // Optimization: Keep memory usage low
            if (finalizedCandles.size > MAX_CANDLES) {
                finalizedCandles.removeAt(0)
            }
            
            // Start new candle
            currentCandle = createNewCandle(intervalStart, tick.ltp)
        } else {
            // Update current candle
            currentCandle = currentCandle!!.copy(
                high = maxOf(currentCandle!!.high, tick.ltp),
                low = minOf(currentCandle!!.low, tick.ltp),
                close = tick.ltp
            )
        }
        
        // Always emit the full list: finalized + current live
        _candles.value = finalizedCandles + listOfNotNull(currentCandle)
    }

    private fun createNewCandle(timestamp: Long, price: Double): Candle {
        return Candle(
            timestamp = timestamp,
            open = price,
            high = price,
            low = price,
            close = price,
            isComplete = false
        )
    }

    fun clear() {
        finalizedCandles.clear()
        currentCandle = null
        _candles.value = emptyList()
    }
}
