package com.vedx.vedxsuper.market

import com.vedx.vedxsuper.model.market.IndexData
import com.vedx.vedxsuper.model.market.TickData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class MarketDataManager {
    
    private val _indexData = MutableStateFlow<Map<String, IndexData>>(emptyMap())
    val indexData = _indexData.asStateFlow()

    private val _indiaVix = MutableStateFlow(15.0) // Default baseline VIX
    val indiaVix = _indiaVix.asStateFlow()

    private val internalIndexData = ConcurrentHashMap<String, IndexData>()

    fun updateTick(tick: TickData) {
        if (tick.symbol == "INDIA VIX" || tick.token == "26017") {
            _indiaVix.value = tick.ltp
        }

        val existing = internalIndexData[tick.symbol]
        
        // Final Logic for Points Movement: Priority to API Data, else Manual Calculation
        var change = tick.change
        var changePercent = tick.changePercent
        
        if (change == 0.0 && tick.prevClose > 0) {
            change = tick.ltp - tick.prevClose
            changePercent = (change / tick.prevClose) * 100.0
        } else if (change == 0.0 && existing != null) {
            change = tick.ltp - existing.lastTradedPrice
            changePercent = if (existing.lastTradedPrice != 0.0) (change / existing.lastTradedPrice) * 100 else 0.0
        }
        val high = if (tick.high != 0.0) tick.high else (if (existing != null && existing.high != 0.0) maxOf(existing.high, tick.ltp) else tick.ltp)
        val low = if (tick.low != 0.0) tick.low else (if (existing != null && existing.low != 0.0) minOf(existing.low, tick.ltp) else tick.ltp)

        val newData = IndexData(
            symbol = tick.symbol,
            lastTradedPrice = tick.ltp,
            change = change,
            changePercent = changePercent,
            high = high,
            low = low
        )
        internalIndexData[tick.symbol] = newData
        
        // Ensure atomic update and new instance for StateFlow
        _indexData.value = HashMap(internalIndexData)
    }

    fun getLtp(symbol: String): Double = internalIndexData[symbol]?.lastTradedPrice ?: 0.0
}
