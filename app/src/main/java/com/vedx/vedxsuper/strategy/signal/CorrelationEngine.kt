package com.vedx.vedxsuper.strategy.signal

import com.vedx.vedxsuper.model.market.TickData
import com.vedx.vedxsuper.strategy.options.OptionSelector
import com.vedx.vedxsuper.strategy.options.OptionType
import com.vedx.vedxsuper.strategy.options.StrikePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class CorrelationSignal(
    val indexSignal: ReversalSignal,
    val optionSignal: ReversalSignal,
    val optionSymbol: String,
    val timestamp: Long = System.currentTimeMillis()
)

class CorrelationEngine(private val optionSelector: OptionSelector) {
    private var indexSymbol: String = "NIFTY"
    private val indexEngine = ReversalStrategyEngine(indexSymbol, isIndex = true)
    private val optionEngines = ConcurrentHashMap<String, ReversalStrategyEngine>()
    
    private val _signals = MutableStateFlow<List<CorrelationSignal>>(emptyList())
    val signals = _signals.asStateFlow()

    fun setIndex(symbol: String) {
        indexSymbol = symbol
    }

    fun onTick(tick: TickData, vix: Double = 15.0) {
        if (tick.symbol == indexSymbol) {
            indexEngine.onTick(tick, vix)
            checkCorrelation()
        } else {
            val engine = optionEngines.getOrPut(tick.symbol) { ReversalStrategyEngine(tick.symbol, isIndex = false) }
            engine.onTick(tick, vix)
            checkCorrelation()
        }
    }

    private fun checkCorrelation() {
        val indexSignal = indexEngine.state.value.signal ?: return
        if (!indexSignal.isConfirmed) return

        // For each option engine, check if it also has a confirmed signal
        optionEngines.forEach { (symbol, engine) ->
            val optSignal = engine.state.value.signal ?: return@forEach
            if (!optSignal.isConfirmed) return@forEach

            // INSTITUTIONAL LOGIC:
            // 1. CALL ENTRY: Index hits Support (BUY) AND CE Option hits Support (BUY)
            val isCallEntry = indexSignal.type == "BUY" && symbol.contains("CE") && optSignal.type == "BUY"
            
            // 2. PUT ENTRY: Index hits Resistance (SELL) AND PE Option hits Support (BUY)
            val isPutEntry = indexSignal.type == "SELL" && symbol.contains("PE") && optSignal.type == "BUY"

            if (isCallEntry || isPutEntry) {
                // Dual Zone Confirmation!
                val newSignal = CorrelationSignal(indexSignal, optSignal, symbol)
                if (!_signals.value.any { it.indexSignal.timestamp == indexSignal.timestamp && it.optionSymbol == symbol }) {
                    _signals.value = _signals.value + newSignal
                }
            }
        }
    }

    fun getIndexState() = indexEngine.state
    fun getOptionState(symbol: String) = optionEngines[symbol]?.state
}
