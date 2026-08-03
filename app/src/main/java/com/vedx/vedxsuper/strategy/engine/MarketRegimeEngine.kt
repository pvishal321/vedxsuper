package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import kotlin.math.abs

enum class MarketRegime {
    TRENDING_BULL,
    TRENDING_BEAR,
    SIDEWAYS,
    RANGE_BOUND,
    BREAKOUT,
    REVERSAL,
    HIGH_VOLATILITY,
    LOW_VOLATILITY,
    TREND_EXHAUSTION,
    NEWS_VOLATILITY,
    NO_TRADE
}

data class RegimeMemory(
    val current: MarketRegime = MarketRegime.NO_TRADE,
    val previous: MarketRegime = MarketRegime.NO_TRADE,
    val startTime: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val confidence: Double = 0.0,
    val strength: Double = 0.0,
    val lastTransition: String = "INIT"
)

class MarketRegimeEngine {
    private var memory = RegimeMemory()
    private val lock = Any()

    fun identifyRegime(
        candles: List<Candle>,
        stResult: MultiSuperTrendResult?,
        strength: StrengthMetrics,
        atr: Double
    ): MarketRegime {
        synchronized(lock) {
            val newRegime = calculateRegime(candles, stResult, strength, atr)
            
            if (newRegime != memory.current) {
                memory = memory.copy(
                    previous = memory.current,
                    current = newRegime,
                    startTime = System.currentTimeMillis(),
                    lastTransition = "${memory.current} -> $newRegime"
                )
            }
            
            memory = memory.copy(
                durationMs = System.currentTimeMillis() - memory.startTime,
                strength = strength.trendStrength,
                confidence = calculateConfidence(newRegime, strength)
            )
            
            return newRegime
        }
    }

    private fun calculateRegime(
        candles: List<Candle>,
        stResult: MultiSuperTrendResult?,
        strength: StrengthMetrics,
        atr: Double
    ): MarketRegime {
        if (candles.size < 10) return MarketRegime.NO_TRADE

        // 1. News Volatility (Extreme Spikes)
        if (isNewsVolatility(candles, atr)) return MarketRegime.NEWS_VOLATILITY

        // 2. High Volatility
        val avgAtr = getAverageAtr(candles)
        if (atr > avgAtr * 2.5) return MarketRegime.HIGH_VOLATILITY

        // 3. Trending Bull/Bear
        if (stResult != null) {
            if (isTrendingBull(stResult, strength)) return MarketRegime.TRENDING_BULL
            if (isTrendingBear(stResult, strength)) return MarketRegime.TRENDING_BEAR
        }

        // 4. Trend Exhaustion
        if (strength.isExhausted) return MarketRegime.TREND_EXHAUSTION

        // 5. Reversal
        if (stResult != null && isReversal(stResult)) return MarketRegime.REVERSAL

        // 6. Breakout
        if (isBreakout(candles, strength, atr)) return MarketRegime.BREAKOUT

        // 7. Low Volatility
        if (atr < avgAtr * 0.6) return MarketRegime.LOW_VOLATILITY

        // 8. Sideways / Range Bound
        if (isSideways(candles, stResult, atr)) return MarketRegime.SIDEWAYS
        if (isRangeBound(candles, atr)) return MarketRegime.RANGE_BOUND

        return MarketRegime.NO_TRADE
    }

    private fun isNewsVolatility(candles: List<Candle>, atr: Double): Boolean {
        if (candles.size < 2) return false
        val last = candles.last()
        val body = abs(last.close - last.open)
        return body > (atr * 4.0)
    }

    private fun isTrendingBull(st: MultiSuperTrendResult, strength: StrengthMetrics): Boolean {
        val allUp = st.st2.trend == 1 && st.st3.trend == 1 && st.st4.trend == 1 &&
                     st.st5.trend == 1 && st.st6.trend == 1 && st.st7.trend == 1 && st.st8.trend == 1
        return allUp && strength.trendStrength > 60
    }

    private fun isTrendingBear(st: MultiSuperTrendResult, strength: StrengthMetrics): Boolean {
        val allDown = st.st2.trend == -1 && st.st3.trend == -1 && st.st4.trend == -1 &&
                       st.st5.trend == -1 && st.st6.trend == -1 && st.st7.trend == -1 && st.st8.trend == -1
        return allDown && strength.trendStrength < 40
    }

    private fun isReversal(st: MultiSuperTrendResult): Boolean {
        val lowerTrends = listOf(st.st2.trend, st.st3.trend, st.st4.trend)
        val higherTrends = listOf(st.st6.trend, st.st7.trend, st.st8.trend)
        return lowerTrends.distinct().size == 1 && lowerTrends[0] != higherTrends[0]
    }

    private fun isBreakout(candles: List<Candle>, strength: StrengthMetrics, atr: Double): Boolean {
        if (candles.size < 20) return false
        val prev20 = candles.takeLast(20).dropLast(1)
        val high = prev20.maxOf { it.high }
        val low = prev20.minOf { it.low }
        val last = candles.last()

        return (last.close > high || last.close < low) && abs(strength.velocity) > (atr * 0.2)
    }

    private fun isSideways(candles: List<Candle>, st: MultiSuperTrendResult?, atr: Double): Boolean {
        if (st == null) return true
        val trends = listOf(st.st2.trend, st.st3.trend, st.st4.trend, st.st5.trend, st.st6.trend, st.st7.trend, st.st8.trend)
        val mixed = trends.distinct().size > 1

        val last20 = candles.takeLast(20)
        val range = last20.maxOf { it.high } - last20.minOf { it.low }
        return mixed && range < (3.0 * atr)
    }

    private fun isRangeBound(candles: List<Candle>, atr: Double): Boolean {
        if (candles.size < 50) return false
        val last50 = candles.takeLast(50)
        val range = last50.maxOf { it.high } - last50.minOf { it.low }
        return range < (4.0 * atr)
    }

    private fun getAverageAtr(candles: List<Candle>): Double {
        if (candles.size < 20) return 0.0
        val last20 = candles.takeLast(20)
        return last20.map { it.high - it.low }.average()
    }

    private fun calculateConfidence(regime: MarketRegime, strength: StrengthMetrics): Double {
        return when (regime) {
            MarketRegime.TRENDING_BULL -> strength.trendStrength
            MarketRegime.TRENDING_BEAR -> 100.0 - strength.trendStrength
            MarketRegime.SIDEWAYS, MarketRegime.RANGE_BOUND -> 50.0
            MarketRegime.NO_TRADE -> 0.0
            else -> 70.0
        }
    }

    fun getMemory() = synchronized(lock) { memory.copy() }

    fun isTradingPaused(regime: MarketRegime): Boolean {
        return when (regime) {
            MarketRegime.LOW_VOLATILITY,
            MarketRegime.NO_TRADE -> true
            else -> false // Allow trading in SIDEWAYS and RANGE_BOUND for ST Band strategies
        }
    }
}
