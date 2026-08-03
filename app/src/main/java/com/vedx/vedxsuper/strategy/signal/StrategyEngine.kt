package com.vedx.vedxsuper.strategy.signal

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.model.market.TickData
import com.vedx.vedxsuper.strategy.candle.CandleBuilder
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MultiTrendStrategyState(
    val lastPrice: Double = 0.0,
    val bandInfo: BandInfo? = null,
    val candles: List<Candle> = emptyList(),
    val stResult: com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult? = null
)

class StrategyEngine(val timeframe: Int = 1) {
    private val candleBuilder = CandleBuilder(timeframe)
    private val stEngine = MultiSuperTrendEngine()
    private val bandDetector = BandDetector()

    private val _state = MutableStateFlow(MultiTrendStrategyState())
    val state = _state.asStateFlow()

    fun initialize(candles: List<Candle>, vix: Double = 15.0) {
        candleBuilder.initialize(candles)
        val currentCandles = candleBuilder.candles.value
        if (currentCandles.isNotEmpty()) {
            val lastPrice = currentCandles.last().close
            val multiSt = stEngine.calculate(currentCandles)
            if (multiSt != null) {
                val bandInfo = bandDetector.detect(lastPrice, multiSt, isIndex = true, vix = vix)
                _state.value = MultiTrendStrategyState(
                    lastPrice = lastPrice,
                    bandInfo = bandInfo,
                    candles = currentCandles,
                    stResult = multiSt
                )
            }
        }
    }

    fun onTick(tick: TickData, vix: Double = 15.0) {
        candleBuilder.onTick(tick)
        val currentCandles = candleBuilder.candles.value
        
        val multiSt = stEngine.calculate(currentCandles)
        
        if (multiSt != null) {
            val bandInfo = bandDetector.detect(tick.ltp, multiSt, isIndex = true, vix = vix)
            _state.value = MultiTrendStrategyState(
                lastPrice = tick.ltp,
                bandInfo = bandInfo,
                candles = currentCandles,
                stResult = multiSt
            )
        }
    }
}
