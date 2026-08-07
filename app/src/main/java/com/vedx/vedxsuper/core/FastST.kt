package com.vedx.vedxsuper.core

import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.STResult
import kotlin.math.abs
import kotlin.math.max

/**
 * SuperTrend (FastST) Implementation
 * Optimized for accuracy and following TradingView standard.
 * Non-repainting: logic uses confirmed previous closes for band locking.
 * Smooth ATR: uses Wilder's RMA.
 */
class FastST(private val multiplier: Float, private val period: Int = 10) {
    private var lastCandleTimestamp = -1L
    private var lastResult: STResult? = null

    fun reset() {
        lastCandleTimestamp = -1L
        lastResult = null
    }

    fun calculate(candles: List<Candle>): STResult? {
        val minBars = max(200, 3 * period)
        if (candles.size < minBars) return null

        val lastInList = candles.last()
        if (lastInList.timestamp == lastCandleTimestamp) {
            return lastResult
        }

        // Full recalculation to maintain recursive state consistency across list updates.
        // O(N) for N=500 is extremely fast and avoids complex incremental state management.
        
        var atr: Double
        var finalUpper = Double.MAX_VALUE
        var finalLower = 0.0
        var trend: Byte = 1
        
        // 1. Initial ATR (SMA of first 'period' true ranges)
        var sumTR = 0.0
        for (i in 1..period) {
            sumTR += calcTR(candles[i], candles[i - 1])
        }
        atr = sumTR / period
        
        // Initial bands at index 'period'
        val mid0 = (candles[period].high.rupees + candles[period].low.rupees) / 2.0
        finalUpper = mid0 + multiplier * atr
        finalLower = mid0 - multiplier * atr
        // Initial trend based on close vs bands
        trend = if (candles[period].close.rupees > finalUpper) 1.toByte() else (-1).toByte()

        // 2. Loop from period + 1 to end
        for (i in (period + 1) until candles.size) {
            val c = candles[i]
            val p = candles[i - 1]
            val tr = calcTR(c, p)
            
            // Wilder's ATR (Recursive RMA)
            atr = (atr * (period - 1) + tr) / period
            
            val mid = (c.high.rupees + c.low.rupees) / 2.0
            val basicUpper = mid + multiplier * atr
            val basicLower = mid - multiplier * atr

            val prevClose = p.close.rupees

            // Final Band Update Logic (Locking)
            // Upper band can only move down, lower band only move up, unless trend flipped.
            finalUpper = if (basicUpper < finalUpper || prevClose > finalUpper) basicUpper else finalUpper
            finalLower = if (basicLower > finalLower || prevClose < finalLower) basicLower else finalLower

            // Trend Flip Conditions (Confirmed by Closing Price)
            if (trend == 1.toByte() && c.close.rupees < finalLower) {
                trend = (-1).toByte()
            } else if (trend == (-1).toByte() && c.close.rupees > finalUpper) {
                trend = 1.toByte()
            }
        }

        lastCandleTimestamp = lastInList.timestamp
        lastResult = STResult(
            triggerPrice = if (trend == 1.toByte()) finalLower else finalUpper,
            upperBand = finalUpper,
            lowerBand = finalLower,
            trend = trend,
            atr = atr
        )
        return lastResult
    }

    private fun calcTR(c: Candle, p: Candle): Double {
        val tr1 = c.high.rupees - c.low.rupees
        val tr2 = abs(c.high.rupees - p.close.rupees)
        val tr3 = abs(c.low.rupees - p.close.rupees)
        return max(tr1, max(tr2, tr3))
    }
}
