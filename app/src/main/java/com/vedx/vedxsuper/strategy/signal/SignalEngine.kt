package com.vedx.vedxsuper.strategy.signal

import com.vedx.vedxsuper.model.market.*
import com.vedx.vedxsuper.model.trade.RiskLevel
import com.vedx.vedxsuper.model.trade.StrategyConfig
import com.vedx.vedxsuper.strategy.indicator.SuperTrend
import com.vedx.vedxsuper.strategy.options.OptionSelector
import com.vedx.vedxsuper.strategy.options.StrikePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TradingSignal(
    val side: String, // BUY_CALL, BUY_PUT, EXIT
    val symbol: String,
    val strike: Double,
    val type: String, // CE or PE
    val confidence: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class StrategyState(
    val config: StrategyConfig = StrategyConfig(),
    val mainTrend: Int = 0,
    val ribbons: Map<Int, Int> = emptyMap(),
    val ribbonPrices: Map<Int, Double> = emptyMap(),
    val isAligned: Boolean = false,
    val alignmentCount: Int = 0,
    val currentRecommendation: String = "WAIT",
    val lastSignal: TradingSignal? = null
)

class SignalEngine(atrPeriod: Int = 10) {
    private val _state = MutableStateFlow(StrategyState())
    val state = _state.asStateFlow()

    private var currentConfig = StrategyConfig()
    private val multipliers = listOf(2, 3, 4, 5, 6, 7, 8)
    private val ribbons = multipliers.associateWith { SuperTrend(atrPeriod, it.toDouble()) }
    private val optionSelector = OptionSelector()

    private var lastSignalSide = "NONE"

    fun updateConfig(config: StrategyConfig) {
        currentConfig = config
        _state.value = _state.value.copy(config = config)
    }

    /**
     * Resets the engine state and all internal supertrend ribbons.
     */
    fun reset() {
        ribbons.values.forEach { it.reset() }
        lastSignalSide = "NONE"
        _state.value = StrategyState()
    }

    fun onCandlesUpdated(candles: List<Candle>, spotSymbol: String) {
        if (candles.isEmpty()) return

        val newRibbonTrends = mutableMapOf<Int, Int>()
        val newRibbonPrices = mutableMapOf<Int, Double>()

        ribbons.forEach { (m, strategy) ->
            val res = strategy.calculate(candles)
            newRibbonTrends[m] = res.trend
            newRibbonPrices[m] = res.value
        }

        val upCount = newRibbonTrends.values.count { it == 1 }
        val downCount = newRibbonTrends.values.count { it == -1 }
        val alignmentCount = maxOf(upCount, downCount)
        val dominantTrend = if (upCount > downCount) 1 else if (downCount > upCount) -1 else 0

        // Vedex AI Risk-based Entry Logic
        val canEnter = when (currentConfig.riskLevel) {
            RiskLevel.SAFE -> alignmentCount >= 6
            RiskLevel.MODERATE -> alignmentCount >= 4
            RiskLevel.AGGRESSIVE -> alignmentCount >= 2
        }

        val spotPrice = candles.last().close
        var signal: TradingSignal? = null
        var recommendation = "WAIT"

        if (canEnter && dominantTrend != 0) {
            val side = if (dominantTrend == 1) "BUY_CALL" else "BUY_PUT"

            // Duplicate Signal Prevention
            if (side != lastSignalSide) {
                recommendation = side
                lastSignalSide = side

                val pref = try {
                    StrikePreference.valueOf(currentConfig.strikeType)
                } catch(e: Exception) {
                    StrikePreference.ATM
                }

                val (optType, strike) = optionSelector.getTargetOption(dominantTrend, spotPrice, spotSymbol, pref)
                
                signal = TradingSignal(
                    side = side,
                    symbol = spotSymbol,
                    strike = strike,
                    type = optType.name,
                    confidence = alignmentCount / 7.0
                )
            }
        } else if (lastSignalSide != "NONE") {
            // Exit logic if trend changes or alignment disappears
            val trendChanged = (lastSignalSide == "BUY_CALL" && dominantTrend == -1) ||
                               (lastSignalSide == "BUY_PUT" && dominantTrend == 1)

            if (trendChanged || alignmentCount == 0) {
                recommendation = "EXIT"
                lastSignalSide = "NONE"
                signal = TradingSignal("EXIT", spotSymbol, 0.0, "", 1.0)
            }
        }

        _state.value = _state.value.copy(
            mainTrend = dominantTrend,
            ribbons = newRibbonTrends,
            ribbonPrices = newRibbonPrices,
            isAligned = alignmentCount == 5,
            alignmentCount = alignmentCount,
            currentRecommendation = recommendation,
            lastSignal = signal ?: _state.value.lastSignal
        )
    }
}
