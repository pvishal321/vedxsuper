package com.vedx.vedxsuper.strategy.indicator

import com.vedx.vedxsuper.model.market.Candle
import kotlin.math.abs

data class MultiSuperTrendResult(
    val master: SuperTrendResult,
    val st2: SuperTrendResult,
    val st3: SuperTrendResult,
    val st4: SuperTrendResult,
    val st5: SuperTrendResult,
    val st6: SuperTrendResult,
    val st7: SuperTrendResult,
    val st8: SuperTrendResult,
    val timestamp: Long
)

/**
 * Multi-ST Engine: Tracks ANY band touch (ST2-ST8), not just ST2.
 */
class MultiSuperTrendEngine(val period: Int = 10) {
    private val stBands = mapOf(
        2 to SuperTrend(period, 2.0),
        3 to SuperTrend(period, 3.0),
        4 to SuperTrend(period, 4.0),
        5 to SuperTrend(period, 5.0),
        6 to SuperTrend(period, 6.0),
        7 to SuperTrend(period, 7.0),
        8 to SuperTrend(period, 8.0)
    )

    private var lastMasterTrend = 0
    private var pullbackOccurred = false
    private var lastProcessedTimestamp = -1L
    private var lastResult: MultiSuperTrendResult? = null

    // [NEW] Track which specific band was touched for re-entry logic
    private var lastTouchedBandTrend = 0
    private var lastTouchedBandMultiplier = 2

    /**
     * O(N) single-pass history. Use once on init.
     */
    fun calculateHistory(candles: List<Candle>): List<MultiSuperTrendResult> {
        if (candles.size < period) return emptyList()
        reset()
        return candles.map { update(it) }
    }

    /**
     * O(1) incremental update.
     */
    @Synchronized
    fun update(currentCandle: Candle): MultiSuperTrendResult {
        val results = stBands.mapValues { it.value.update(currentCandle) }
        val currentPrice = currentCandle.close

        val upCount = results.values.count { it.trend == 1 }
        val downCount = results.values.count { it.trend == -1 }
        val alignmentScore = maxOf(upCount, downCount)
        val masterTrend = when {
            upCount > downCount -> 1
            downCount > upCount -> -1
            else -> 0
        }

        // [CORE FIX] Check ANY band for touch (ST2 through ST8)
        val allResults = results.values.toList()
        val touchedBand = allResults.find {
            abs(currentPrice - it.value) < (currentPrice * 0.001)
        }
        val isTouchingAny = touchedBand != null
        val touchedMultiplier = touchedBand?.activeBand ?: 2

        var signal = StrategySignal.NO_TRADE

        if (masterTrend != 0 && masterTrend != lastMasterTrend && lastMasterTrend != 0) {
            signal = StrategySignal.REVERSAL
            pullbackOccurred = false
        } else if (isTouchingAny) {
            // [FIX] Any band touch triggers pullback tracking
            pullbackOccurred = true
            lastTouchedBandTrend = touchedBand?.trend ?: masterTrend
            lastTouchedBandMultiplier = touchedMultiplier
            signal = StrategySignal.PREPARE
        } else if (pullbackOccurred && masterTrend == lastTouchedBandTrend) {
            // [FIX] Price moved away from touched band in trend direction
            signal = StrategySignal.RE_ENTRY
            pullbackOccurred = false
        } else if (alignmentScore >= 6) {
            signal = StrategySignal.CONTINUATION
        }

        val nextTarget = if (masterTrend == 1) {
            results.values.filter { it.trend == -1 }.map { it.value }.minOrNull() ?: (currentPrice * 1.01)
        } else {
            results.values.filter { it.trend == 1 }.map { it.value }.maxOrNull() ?: (currentPrice * 0.99)
        }

        val confidence = when (alignmentScore) {
            7 -> 100
            6 -> 85
            5 -> 70
            else -> (alignmentScore * 14)
        }

        val masterResult = SuperTrendResult(
            trend = masterTrend,
            value = results[3]?.value ?: 0.0,
            upperBand = results[8]?.upperBand ?: 0.0,
            lowerBand = results[8]?.lowerBand ?: 0.0,
            activeBand = if (isTouchingAny) touchedMultiplier else 3,
            trendAge = results[3]?.trendAge ?: 0,
            strength = (alignmentScore.toDouble() / 7.0) * 100.0,
            alignmentScore = alignmentScore,
            distanceFromPrice = abs(currentPrice - (results[2]?.value ?: 0.0)),
            signal = signal,
            nextTarget = nextTarget,
            trailingStop = results[2]?.value ?: 0.0,
            confidence = confidence
        )

        lastMasterTrend = masterTrend
        lastProcessedTimestamp = currentCandle.timestamp

        lastResult = MultiSuperTrendResult(
            master = masterResult,
            st2 = results[2]!!,
            st3 = results[3]!!,
            st4 = results[4]!!,
            st5 = results[5]!!,
            st6 = results[6]!!,
            st7 = results[7]!!,
            st8 = results[8]!!,
            timestamp = currentCandle.timestamp
        )
        return lastResult!!
    }

    /**
     * Smart entry point. Auto-detects history pass vs incremental.
     */
    fun calculate(candles: List<Candle>): MultiSuperTrendResult? {
        if (candles.isEmpty()) return null
        val last = candles.last()

        when {
            lastProcessedTimestamp == -1L -> calculateHistory(candles)
            last.timestamp > lastProcessedTimestamp -> {
                val startIdx = candles.indexOfFirst { it.timestamp > lastProcessedTimestamp }
                if (startIdx != -1) {
                    for (i in startIdx until candles.size) update(candles[i])
                }
            }
            last.timestamp == lastProcessedTimestamp -> update(last)
        }
        return lastResult
    }

    fun reset() {
        stBands.values.forEach { it.reset() }
        lastMasterTrend = 0
        pullbackOccurred = false
        lastProcessedTimestamp = -1L
        lastResult = null
        lastTouchedBandTrend = 0
        lastTouchedBandMultiplier = 2
    }
}
