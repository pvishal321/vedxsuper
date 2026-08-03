package com.vedx.vedxsuper.strategy

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.model.market.TickData
import com.vedx.vedxsuper.strategy.options.OptionSelector
import com.vedx.vedxsuper.strategy.options.OptionType
import com.vedx.vedxsuper.strategy.options.StrikePreference
import com.vedx.vedxsuper.strategy.signal.MultiTrendStrategyState
import com.vedx.vedxsuper.strategy.signal.StrategyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class Phase3State(
    val indexSymbol: String = "",
    val indexState: MultiTrendStrategyState? = null,
    val optionStates: Map<String, MultiTrendStrategyState> = emptyMap(),
    val targetOptionSymbols: List<String> = emptyList()
)

class Phase3Engine(private val optionSelector: OptionSelector, var timeframe: Int = 15) {
    private var indexEngine = StrategyEngine(timeframe)
    private val optionEngines = mutableMapOf<String, StrategyEngine>()
    
    private val _state = MutableStateFlow(Phase3State())
    val state = _state.asStateFlow()

    private var indexSymbol: String = ""
    private var lastAtmUpdatePrice: Double = 0.0

    fun updateTimeframe(newTimeframe: Int) {
        this.timeframe = newTimeframe
        indexEngine = StrategyEngine(newTimeframe)
        optionEngines.clear()
        updateState()
    }

    fun setIndex(symbol: String) {
        indexSymbol = symbol
        _state.value = _state.value.copy(indexSymbol = symbol)
    }

    fun initializeIndex(candles: List<Candle>, vix: Double = 15.0) {
        indexEngine.initialize(candles, vix)
        updateState()
    }

    fun initializeOption(symbol: String, candles: List<Candle>, vix: Double = 15.0) {
        val engine = optionEngines.getOrPut(symbol) { StrategyEngine(timeframe) }
        engine.initialize(candles, vix)
        updateState()
    }

    /**
     * [FIXED] Dynamic Target Update: Updates ATM strikes if price moves > 20 points
     */
    fun updateOptionTargets(spotPrice: Double, symbol: String) {
        if (abs(spotPrice - lastAtmUpdatePrice) < 20.0 && _state.value.targetOptionSymbols.isNotEmpty()) return
        
        lastAtmUpdatePrice = spotPrice
        val preferences = listOf(
            StrikePreference.ITM1,
            StrikePreference.ATM,
            StrikePreference.OTM1
        )

        val newSymbols = mutableListOf<String>()
        preferences.forEach { pref ->
            val ceStrike = optionSelector.selectStrike(spotPrice, symbol, OptionType.CE, pref)
            newSymbols.add("${symbol}_${ceStrike.toInt()}_CE")
            
            val peStrike = optionSelector.selectStrike(spotPrice, symbol, OptionType.PE, pref)
            newSymbols.add("${symbol}_${peStrike.toInt()}_PE")
        }

        _state.value = _state.value.copy(targetOptionSymbols = newSymbols)
    }

    fun onTick(tick: TickData, vix: Double = 15.0) {
        if (tick.symbol == indexSymbol) {
            indexEngine.onTick(tick, vix)
            updateOptionTargets(tick.ltp, tick.symbol)
            updateState()
        } else if (_state.value.targetOptionSymbols.contains(tick.symbol)) {
            val engine = optionEngines.getOrPut(tick.symbol) { StrategyEngine(timeframe) }
            engine.onTick(tick, vix)
            updateState()
        }
    }

    private fun updateState() {
        _state.value = _state.value.copy(
            indexState = indexEngine.state.value,
            optionStates = optionEngines.mapValues { it.value.state.value }
        )
    }
}
