package com.vedx.vedxsuper.strategy.signal

import com.vedx.vedxsuper.model.market.TickData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class FullStrategyState(
    val indexState: MultiTrendStrategyState? = null,
    val optionStates: Map<String, MultiTrendStrategyState> = emptyMap()
)

class MultiSymbolStrategyEngine {
    private val indexEngine = StrategyEngine()
    private val optionEngines = ConcurrentHashMap<String, StrategyEngine>()
    
    private val _fullState = MutableStateFlow(FullStrategyState())
    val fullState = _fullState.asStateFlow()

    private var indexSymbol: String = ""

    fun setIndexSymbol(symbol: String) {
        indexSymbol = symbol
    }

    fun onTick(tick: TickData) {
        if (tick.symbol == indexSymbol) {
            indexEngine.onTick(tick)
            updateState()
        } else {
            val engine = optionEngines.getOrPut(tick.symbol) { StrategyEngine() }
            engine.onTick(tick)
            updateState()
        }
    }

    private fun updateState() {
        _fullState.value = FullStrategyState(
            indexState = indexEngine.state.value,
            optionStates = optionEngines.mapValues { it.value.state.value }
        )
    }
    
    fun clearOptions() {
        optionEngines.clear()
        updateState()
    }
}
