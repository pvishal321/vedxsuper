package com.vedx.vedxsuper.strategy.indicator

import com.vedx.vedxsuper.model.market.Candle
import kotlin.math.abs

/**
 * Truly Incremental ATR with committed vs projected state separation.
 * O(1) per update. No double-counting on forming candles.
 */
class ATRCalculator(private val period: Int = 10) {
    // Committed state (after complete candle)
    private var committedAtr = 0.0
    private var lastProcessedTimestamp = -1L
    private var lastSmoothedTimestamp = -1L
    private var prevClose = -1.0
    private var count = 0
    private var trSum = 0.0

    /**
     * Update with a new candle.
     * - New timestamp + complete: advances Wilder's smoothing
     * - Same timestamp (forming): re-projects without mutating committed state
     * - Old timestamp: returns cached committed ATR
     */
    @Synchronized
    fun update(candle: Candle): Double {
        if (candle.timestamp < lastProcessedTimestamp) return committedAtr

        val tr = calculateTr(candle)

        // [CRITICAL FIX] Advance smoothing ONLY on new timestamps
        if (candle.timestamp > lastSmoothedTimestamp) {
            if (count < period) {
                trSum += tr
                count++
                committedAtr = trSum / count
            } else {
                // Wilder's smoothing: O(1)
                committedAtr = (committedAtr * (period - 1) + tr) / period
            }
            lastSmoothedTimestamp = candle.timestamp
        }

        // Projection for current candle (doesn't mutate committed state)
        val projectedAtr = if (count < period) {
            (trSum + tr) / (count + 1)
        } else {
            (committedAtr * (period - 1) + tr) / period
        }

        if (candle.isComplete) {
            lastProcessedTimestamp = candle.timestamp
            prevClose = candle.close
        }

        // Return projection for forming candles, committed for complete
        return if (candle.isComplete) committedAtr else projectedAtr
    }

    private fun calculateTr(candle: Candle): Double {
        return if (prevClose < 0) {
            candle.high - candle.low
        } else {
            maxOf(
                candle.high - candle.low,
                abs(candle.high - prevClose),
                abs(candle.low - prevClose)
            )
        }
    }

    fun reset() {
        committedAtr = 0.0
        lastProcessedTimestamp = -1L
        lastSmoothedTimestamp = -1L
        prevClose = -1.0
        count = 0
        trSum = 0.0
    }

    /**
     * Legacy: full list calculation. Always resets to ensure correctness.
     */
    fun calculate(candles: List<Candle>): Double {
        if (candles.isEmpty()) return 0.0
        reset()
        candles.forEach { update(it) }
        return committedAtr
    }
}
