package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import kotlin.math.abs

enum class MarketStructure {
    STRONG_UPTREND,
    WEAK_UPTREND,
    STRONG_DOWNTREND,
    WEAK_DOWNTREND,
    PULLBACK,
    CONTINUATION,
    ACCUMULATION,
    DISTRIBUTION,
    EXPANSION,
    CONSOLIDATION,
    REVERSAL_BUILDING,
    REVERSAL_CONFIRMED,
    STRUCTURE_BREAK,
    FAKE_BREAKOUT,
    LIQUIDITY_GRAB,
    NO_STRUCTURE
}

data class StructureMemory(
    val current: MarketStructure = MarketStructure.NO_STRUCTURE,
    val previous: MarketStructure = MarketStructure.NO_STRUCTURE,
    val age: Int = 0,
    val highestSwing: Double = 0.0,
    val lowestSwing: Double = Double.MAX_VALUE,
    val pullbackCount: Int = 0,
    val continuationCount: Int = 0,
    val lastBreakPrice: Double = 0.0,
    val strength: Double = 0.0,
    val confidence: Double = 0.0,
    val lastUpdateTime: Long = 0
)

class MarketStructureEngine {
    private var memory = StructureMemory()
    private val lock = Any()

    private val swingWindow = 5
    private val lastHighs = mutableListOf<Double>()
    private val lastLows = mutableListOf<Double>()
    private val volumeSma = mutableListOf<Long>()

    fun updateStructure(
        candles: List<Candle>,
        stResult: MultiSuperTrendResult?,
        strength: StrengthMetrics,
        regime: MarketRegime,
        premiumExpansion: Double = 0.0
    ): MarketStructure {
        synchronized(lock) {
            if (candles.size < 20) return MarketStructure.NO_STRUCTURE

            updateSwings(candles)
            updateVolume(candles)
            
            val newStructure = classifyStructure(candles, stResult, strength, regime, premiumExpansion)

            if (newStructure != memory.current) {
                var pCount = memory.pullbackCount
                var cCount = memory.continuationCount
                
                if (newStructure == MarketStructure.PULLBACK) pCount++
                if (newStructure == MarketStructure.CONTINUATION) cCount++

                memory = memory.copy(
                    previous = memory.current,
                    current = newStructure,
                    age = 0,
                    pullbackCount = pCount,
                    continuationCount = cCount,
                    lastUpdateTime = System.currentTimeMillis()
                )
            } else {
                memory = memory.copy(
                    age = memory.age + 1,
                    lastUpdateTime = System.currentTimeMillis()
                )
            }

            memory = memory.copy(
                highestSwing = lastHighs.maxOrNull() ?: memory.highestSwing,
                lowestSwing = lastLows.minOrNull() ?: memory.lowestSwing,
                strength = strength.trendStrength,
                confidence = calculateConfidence(newStructure, strength, stResult)
            )

            return newStructure
        }
    }

    private fun updateSwings(candles: List<Candle>) {
        if (candles.size < swingWindow * 2 + 1) return

        val centerIdx = candles.size - 1 - swingWindow
        val centerCandle = candles[centerIdx]

        // Swing High
        var isHigh = true
        for (i in (centerIdx - swingWindow) until (centerIdx + swingWindow)) {
            if (i == centerIdx) continue
            if (candles[i].high > centerCandle.high) {
                isHigh = false
                break
            }
        }
        if (isHigh) {
            if (lastHighs.isEmpty() || abs(lastHighs.last() - centerCandle.high) > 0.01) {
                lastHighs.add(centerCandle.high)
                if (lastHighs.size > 10) lastHighs.removeAt(0)
            }
        }

        // Swing Low
        var isLow = true
        for (i in (centerIdx - swingWindow) until (centerIdx + swingWindow)) {
            if (i == centerIdx) continue
            if (candles[i].low < centerCandle.low) {
                isLow = false
                break
            }
        }
        if (isLow) {
            if (lastLows.isEmpty() || abs(lastLows.last() - centerCandle.low) > 0.01) {
                lastLows.add(centerCandle.low)
                if (lastLows.size > 10) lastLows.removeAt(0)
            }
        }
    }

    private fun updateVolume(candles: List<Candle>) {
        val last = candles.last()
        volumeSma.add(last.volume)
        if (volumeSma.size > 20) volumeSma.removeAt(0)
    }

    private fun classifyStructure(
        candles: List<Candle>,
        stResult: MultiSuperTrendResult?,
        strength: StrengthMetrics,
        regime: MarketRegime,
        premiumExpansion: Double
    ): MarketStructure {
        val last = candles.last()
        val prev = candles[candles.size - 2]
        val avgVol = if (volumeSma.isNotEmpty()) volumeSma.average() else 0.0
        val isHighVolume = last.volume > avgVol * 1.5

        // 1. Structure Break Dynamics
        if (lastHighs.isNotEmpty() && lastLows.isNotEmpty()) {
            val res = checkBreakoutDynamics(last, prev, lastHighs.last(), lastLows.last())
            if (res != null) return res
        }

        // 2. Expansion
        if (premiumExpansion > 15.0 || (regime == MarketRegime.HIGH_VOLATILITY && strength.acceleration > 1.0)) {
            return MarketStructure.EXPANSION
        }

        // 3. Reversal Dynamics
        if (regime == MarketRegime.REVERSAL) {
            val indicatorMismatch = stResult != null && stResult.st2.trend != stResult.st8.trend
            return if (indicatorMismatch) MarketStructure.REVERSAL_BUILDING else MarketStructure.REVERSAL_CONFIRMED
        }

        // 4. Pullback / Continuation
        if (strength.isPullback) return MarketStructure.PULLBACK
        if (memory.current == MarketStructure.PULLBACK && !strength.isPullback && abs(strength.velocity) > 0.5) {
            return MarketStructure.CONTINUATION
        }

        // 5. Accumulation / Distribution
        if (regime == MarketRegime.RANGE_BOUND || regime == MarketRegime.SIDEWAYS) {
            val rangeHigh = lastHighs.lastOrNull() ?: Double.MAX_VALUE
            val rangeLow = lastLows.lastOrNull() ?: 0.0
            
            return when {
                isHighVolume && last.close < (rangeLow + (rangeHigh - rangeLow) * 0.3) -> MarketStructure.ACCUMULATION
                isHighVolume && last.close > (rangeHigh - (rangeHigh - rangeLow) * 0.3) -> MarketStructure.DISTRIBUTION
                else -> MarketStructure.CONSOLIDATION
            }
        }

        // 6. Strong/Weak Trends
        if (stResult != null) {
            val trend = stResult.st2.trend
            val alignment = countTrendAlignment(stResult)
            
            return when {
                trend == 1 && alignment >= 7 && strength.trendStrength > 70 -> MarketStructure.STRONG_UPTREND
                trend == 1 -> MarketStructure.WEAK_UPTREND
                trend == -1 && alignment >= 7 && strength.trendStrength < 30 -> MarketStructure.STRONG_DOWNTREND
                trend == -1 -> MarketStructure.WEAK_DOWNTREND
                else -> MarketStructure.NO_STRUCTURE
            }
        }

        return MarketStructure.NO_STRUCTURE
    }

    private fun checkBreakoutDynamics(last: Candle, prev: Candle, high: Double, low: Double): MarketStructure? {
        // Liquidity Grab (Breaks high/low then reverses immediately)
        if (prev.high > high && last.close < high && last.close > low) return MarketStructure.LIQUIDITY_GRAB
        if (prev.low < low && last.close > low && last.close < high) return MarketStructure.LIQUIDITY_GRAB

        // Fake Breakout
        if (prev.close > high && last.close < high) return MarketStructure.FAKE_BREAKOUT
        if (prev.close < low && last.close > low) return MarketStructure.FAKE_BREAKOUT

        // Structure Break
        if (last.close > high || last.close < low) return MarketStructure.STRUCTURE_BREAK

        return null
    }

    private fun countTrendAlignment(st: MultiSuperTrendResult): Int {
        val trends = listOf(st.st2.trend, st.st3.trend, st.st4.trend, st.st5.trend, st.st6.trend, st.st7.trend, st.st8.trend)
        val major = if (trends.count { it == 1 } > trends.count { it == -1 }) 1 else -1
        return trends.count { it == major }
    }

    private fun calculateConfidence(structure: MarketStructure, strength: StrengthMetrics, st: MultiSuperTrendResult?): Double {
        var conf = 50.0
        when (structure) {
            MarketStructure.STRONG_UPTREND -> conf = strength.trendStrength
            MarketStructure.STRONG_DOWNTREND -> conf = 100.0 - strength.trendStrength
            MarketStructure.REVERSAL_CONFIRMED -> conf = 85.0
            MarketStructure.EXPANSION -> conf = 90.0
            MarketStructure.CONTINUATION -> conf = 75.0
            else -> {}
        }
        return conf.coerceIn(0.0, 100.0)
    }

    fun getMemory() = synchronized(lock) { memory.copy() }
}
