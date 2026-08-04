package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.VedxApp
import com.vedx.vedxsuper.core.RealBacktestEngine
import com.vedx.vedxsuper.core.UltraNeuralCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BacktestVM : ViewModel() {
    private val core = UltraNeuralCore(com.vedx.vedxsuper.data.Symbol("NIFTY"))
    private val engine = RealBacktestEngine(core)
    
    val result = MutableStateFlow<RealBacktestEngine.BacktestResult?>(null)
    val loading = MutableStateFlow(false)
    val logs = MutableStateFlow(listOf<String>())
    
    fun runAngelBacktest(token: String, days: Int = 5) = viewModelScope.launch {
        loading.value = true
        logs.value += "Fetching Angel One data (last $days days)..."
        try {
            val res = engine.runAngelBacktest(token, days)
            result.value = res
            logs.value += "✅ Done! Trades: ${res.totalTrades} | Win%: ${"%.1f".format(res.winRate)} | P&L: ₹${"%.0f".format(res.netPnl)}"
        } catch (e: Exception) {
            logs.value += "❌ Error: ${e.message}"
        }
        loading.value = false
    }
    
    fun runNseBacktest() = viewModelScope.launch {
        loading.value = true
        logs.value += "Fetching NSE Bhavcopy..."
        try {
            val res = engine.runNseBacktest()
            result.value = res
            logs.value += "✅ Done! Trades: ${res.totalTrades} | Win%: ${"%.1f".format(res.winRate)}"
        } catch (e: Exception) {
            logs.value += "❌ Error: ${e.message}"
        }
        loading.value = false
    }
    
    fun runCustom(csvText: String) = viewModelScope.launch {
        loading.value = true
        logs.value += "Parsing custom data..."
        loading.value = false
    }
}
