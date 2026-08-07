package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.RealBacktestEngine
import com.vedx.vedxsuper.core.UltraNeuralCore
import com.vedx.vedxsuper.data.AppDB
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BacktestResult(
    val totalTrades: Int = 0,
    val winRate: Float = 0f,
    val totalPnL: Long = 0,
    val maxDrawdown: Float = 0f,
    val sharpeRatio: Float = 0f,
    val isRunning: Boolean = false
)

class BacktestViewModel(
    private val db: AppDB,
    private val settingsManager: SettingsManager,
    private val ultraNeuralCore: UltraNeuralCore
) : ViewModel() {

    private val _result = MutableStateFlow(BacktestResult())
    val result: StateFlow<BacktestResult> = _result.asStateFlow()
    
    private val backtestEngine = RealBacktestEngine(ultraNeuralCore)

    fun runBacktest(symbol: String, days: Int) {
        viewModelScope.launch {
            _result.update { it.copy(isRunning = true) }
            try {
                // Fetch tokens if needed, but here we use NIFTY default token
                val token = if (symbol == "BANKNIFTY") "26009" else "26000"
                val res = backtestEngine.runAngelBacktest(token, days)
                
                _result.value = BacktestResult(
                    totalTrades = res.totalTrades,
                    winRate = res.winRate,
                    totalPnL = res.netPnl.toLong(),
                    maxDrawdown = res.maxDrawdownPct,
                    sharpeRatio = res.sharpeRatio,
                    isRunning = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _result.update { it.copy(isRunning = false) }
            }
        }
    }
}
