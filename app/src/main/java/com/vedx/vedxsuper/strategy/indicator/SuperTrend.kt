package com.vedx.vedxsuper.strategy.indicator

import com.vedx.vedxsuper.model.market.Candle
import kotlin.math.abs

enum class StrategySignal {
    WATCH, PREPARE, REVERSAL, REVERSAL_CONFIRMED, RE_ENTRY,
    CONTINUATION, TARGET1, TARGET2, TARGET3, TRAIL, EXIT, NO_TRADE, BUY, SELL
}

data class SuperTrendResult(
    val trend: Int,
    val value: Double,
    val upperBand: Double,
    val lowerBand: Double,
    val activeBand: Int = 0,
    val trendAge: Int = 0,
    val strength: Double = 0.0,
    val volatilityScore: Double = 0.0,
    val alignmentScore: Int = 0,
    val distanceFromPrice: Double = 0.0,
    val signal: StrategySignal = StrategySignal.NO_TRADE,
    val nextTarget: Double = 0.0,
    val trailingStop: Double = 0.0,
    val confidence: Int = 0,
    val signals: List<String> = emptyList()
)

/**
 * Stateful SuperTrend. O(1) incremental. O(N) history (single pass).
 * Separates committed state (complete candles) from real-time projection.
 */
class SuperTrend(
    val period: Int = 10,
    val multiplier: Double = 3.0
) {
    private val atrCalc = ATRCalculator(period)

    // Committed state
    private var lastConfirmedUpper = 0.0
    private var lastConfirmedLower = 0.0
    private var lastConfirmedTrend = 0
    private var previousConfirmedTrend = 0
    private var lastProcessedTimestamp = -1L
    private var trendAge = 0

    /**
     * Incremental update. O(1).
     * Commits state only on new complete candles.
     * Projects for forming candles without mutating committed bands.
     */
    @Synchronized
    fun update(candle: Candle): SuperTrendResult {
        val atr = atrCalc.update(candle)
        val effectiveAtr = if (atr <= 0.0) {
            (candle.high - candle.low).coerceAtLeast(candle.close * 0.001)
        } else atr

        // Commit confirmed state only once per new complete candle
        if (candle.isComplete && candle.timestamp > lastProcessedTimestamp) {
            commitState(candle, effectiveAtr)
            lastProcessedTimestamp = candle.timestamp
        }

        // Real-time projection (non-mutating)
        val hl2 = (candle.high + candle.low) / 2.0
        val basicUpper = hl2 + (multiplier * effectiveAtr)
        val basicLower = hl2 - (multiplier * effectiveAtr)

        var projUpper = lastConfirmedUpper
        var projLower = lastConfirmedLower
        var projTrend = lastConfirmedTrend

        if (lastConfirmedUpper == 0.0) {
            // Bootstrap on first run
            projUpper = basicUpper
            projLower = basicLower
            projTrend = if (candle.close > projUpper) 1 else -1
        } else {
            projUpper = if (basicUpper < lastConfirmedUpper || candle.close > lastConfirmedUpper)
                basicUpper else lastConfirmedUpper
            projLower = if (basicLower > lastConfirmedLower || candle.close < lastConfirmedLower)
                basicLower else lastConfirmedLower

            projTrend = when {
                candle.close > projUpper -> 1
                candle.close < projLower -> -1
                else -> lastConfirmedTrend
            }
        }

        val stValue = if (projTrend == 1) projLower else projUpper
        val distance = abs(candle.close - stValue)

        return SuperTrendResult(
            trend = projTrend,
            value = stValue,
            upperBand = projUpper,
            lowerBand = projLower,
            activeBand = multiplier.toInt(),
            trendAge = trendAge,
            strength = ((effectiveAtr * multiplier) / (distance + 0.001)).coerceIn(0.0, 100.0),
            distanceFromPrice = distance,
            signal = if (projTrend != previousConfirmedTrend && previousConfirmedTrend != 0) {
                StrategySignal.REVERSAL_CONFIRMED
            } else StrategySignal.CONTINUATION
        )
    }

    private fun commitState(candle: Candle, atr: Double) {
        val hl2 = (candle.high + candle.low) / 2.0
        val basicUpper = hl2 + (multiplier * atr)
        val basicLower = hl2 - (multiplier * atr)

        previousConfirmedTrend = lastConfirmedTrend

        if (lastConfirmedUpper == 0.0) {
            lastConfirmedUpper = basicUpper
            lastConfirmedLower = basicLower
            lastConfirmedTrend = if (candle.close > basicUpper) 1 else -1
            trendAge = 1
            return
        }

        val newUpper = if (basicUpper < lastConfirmedUpper || candle.close > lastConfirmedUpper)
            basicUpper else lastConfirmedUpper
        val newLower = if (basicLower > lastConfirmedLower || candle.close < lastConfirmedLower)
            basicLower else lastConfirmedLower

        val newTrend = when {
            candle.close > newUpper -> 1
            candle.close < newLower -> -1
            else -> lastConfirmedTrend
        }

        trendAge = if (newTrend == lastConfirmedTrend) trendAge + 1 else 1
        lastConfirmedTrend = newTrend
        lastConfirmedUpper = newUpper
        lastConfirmedLower = newLower
    }

    /**
     * Legacy support. Smart incremental catch-up.
     * Resets only if dataset jump detected.
     */
    fun calculate(candles: List<Candle>): SuperTrendResult {
        if (candles.isEmpty()) return SuperTrendResult(0, 0.0, 0.0, 0.0)

        // Detect dataset switch (gap or completely new history)
        if (lastProcessedTimestamp == -1L || candles.first().timestamp > lastProcessedTimestamp) {
            reset()
        }

        // Process all candles up to last (skip already processed)
        for (i in 0 until candles.size - 1) {
            if (candles[i].timestamp > lastProcessedTimestamp) {
                update(candles[i].copy(isComplete = true))
            }
        }
        return update(candles.last())
    }

    fun reset() {
        atrCalc.reset()
        lastConfirmedUpper = 0.0
        lastConfirmedLower = 0.0
        lastConfirmedTrend = 0
        previousConfirmedTrend = 0
        lastProcessedTimestamp = -1L
        trendAge = 0
    }
}
