package com.vedx.vedxsuper.strategy.signal

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.model.market.TickData
import com.vedx.vedxsuper.strategy.candle.CandleBuilder
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendEngine
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReversalSignal(
    val type: String, // BUY, SELL
    val band: Int,
    val price: Double,
    val timestamp: Long,
    val isConfirmed: Boolean = false,
    val strength: String = "WEAK" // STRONG or WEAK
)

data class ReversalState(
    val lastPrice: Double = 0.0,
    val bandInfo: BandInfo? = null,
    val signal: ReversalSignal? = null,
    val m15Candles: List<Candle> = emptyList()
)

class ReversalStrategyEngine(val symbol: String, val isIndex: Boolean = true) {
    private val candle1m = CandleBuilder(1)
    private val candle15m = CandleBuilder(15)
    
    private val stEngine = MultiSuperTrendEngine()
    private val bandDetector = BandDetector()

    private val _state = MutableStateFlow(ReversalState())
    val state = _state.asStateFlow()

    private var lastProcessed15mTimestamp: Long = 0

    fun onTick(tick: TickData, vix: Double = 15.0) {
        candle1m.onTick(tick)
        candle15m.onTick(tick)

        val current15m = candle15m.candles.value
        if (current15m.isEmpty()) return

        val multiSt15m = stEngine.calculate(current15m) ?: return
        val bandInfo = bandDetector.detect(tick.ltp, multiSt15m, isIndex, vix)

        val lastClosed15m = current15m.findLast { it.isComplete }
        var signal: ReversalSignal? = _state.value.signal

        if (lastClosed15m != null && lastClosed15m.timestamp != lastProcessed15mTimestamp) {
            lastProcessed15mTimestamp = lastClosed15m.timestamp
            signal = detectReversal(lastClosed15m, multiSt15m, vix)
        }

        _state.value = ReversalState(
            lastPrice = tick.ltp,
            bandInfo = bandInfo,
            signal = signal,
            m15Candles = current15m
        )
    }

    private fun detectReversal(candle: Candle, st: MultiSuperTrendResult, vix: Double): ReversalSignal? {
        val bands = listOf(st.st2, st.st3, st.st4, st.st5, st.st6, st.st7, st.st8)
        
        // Dynamic Zone Width based on INDIA VIX
        val vixMultiplier = vix / 15.0
        val zoneWidth = if (isIndex) {
            (15.0 * vixMultiplier).coerceIn(10.0, 40.0)
        } else {
            (candle.close * 0.05 * vixMultiplier).coerceAtLeast(2.0)
        }

        val strength = calculateStrength(st)

        // Iterate backwards from ST8 to ST2 to find the strongest band interaction
        for (i in bands.indices.reversed()) {
            val band = bands[i]
            val multiplier = i + 2

            if (band.trend == 1) {
                // SUPPORT REJECTION: Price touches band from above and stays above
                if (candle.low <= band.value + zoneWidth && candle.close > band.value) {
                    return ReversalSignal("BUY", multiplier, candle.close, candle.timestamp, true, strength)
                }
            } else if (band.trend == -1) {
                // RESISTANCE REJECTION: Price touches band from below and stays below
                if (candle.high >= band.value - zoneWidth && candle.close < band.value) {
                    return ReversalSignal("SELL", multiplier, candle.close, candle.timestamp, true, strength)
                }
            }
        }
        
        return null
    }

    private fun calculateStrength(st: MultiSuperTrendResult): String {
        val trends = listOf(st.st2.trend, st.st3.trend, st.st4.trend, st.st5.trend, st.st6.trend, st.st7.trend, st.st8.trend)
        val upCount = trends.count { it == 1 }
        val downCount = trends.count { it == -1 }
        return if (upCount >= 6 || downCount >= 6) "STRONG" else "WEAK"
    }
}
