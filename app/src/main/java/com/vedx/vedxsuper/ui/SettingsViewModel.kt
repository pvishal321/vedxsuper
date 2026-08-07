package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.TradingConstants
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import com.vedx.vedxsuper.data.VirtualTrade
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val virtualBalance: Double = 0.0,
    val totalPnL: Double = 0.0,
    val winRate: Float = 0f,
    val tradeCount: Int = 0,
    val autoTradeConfirm: Boolean = false,
    val defaultQuantity: Int = 50,
    val riskPerTrade: Int = 5000,
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundAlerts: Boolean = true
)

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val portfolio: PortfolioEngine
) : ViewModel() {
    
    val uiState: StateFlow<SettingsUiState> = combine(
        portfolio.balance,
        portfolio.tradeHistory,
        settingsManager.autoTradeConfirm,
        settingsManager.defaultQuantity,
        settingsManager.riskPerTrade,
        settingsManager.darkMode,
        settingsManager.notificationsEnabled,
        settingsManager.soundAlerts
    ) { args: Array<Any> ->
        val balance = args[0] as Double
        val history = args[1] as List<VirtualTrade>
        val autoConfirm = args[2] as Boolean
        val defQty = args[3] as Int
        val risk = args[4] as Int
        val dark = args[5] as Boolean
        val notify = args[6] as Boolean
        val sound = args[7] as Boolean

        val wins = history.count { it.status == com.vedx.vedxsuper.data.TradeStatus.PROFIT }
        val rate = if (history.isNotEmpty()) (wins.toFloat() / history.size * 100f) else 0f
        
        SettingsUiState(
            virtualBalance = balance,
            totalPnL = balance - TradingConstants.INITIAL_VIRTUAL_BALANCE,
            winRate = rate,
            tradeCount = history.size,
            autoTradeConfirm = autoConfirm,
            defaultQuantity = defQty,
            riskPerTrade = risk,
            darkMode = dark,
            notificationsEnabled = notify,
            soundAlerts = sound
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun setAutoTradeConfirm(v: Boolean) {
        settingsManager.setAutoTradeConfirm(v)
    }

    fun setDefaultQuantity(v: Int) {
        settingsManager.setDefaultQuantity(v)
    }

    fun setRiskPerTrade(v: Int) {
        settingsManager.setRiskPerTrade(v)
    }

    fun setDarkMode(v: Boolean) {
        settingsManager.setDarkMode(v)
    }

    fun setNotificationsEnabled(v: Boolean) {
        settingsManager.setNotificationsEnabled(v)
    }

    fun setSoundAlerts(v: Boolean) {
        settingsManager.setSoundAlerts(v)
    }

    fun resetBalance() {
        portfolio.resetBalance()
    }

    fun addFunds(v: Long) {
        portfolio.addFunds(v.toDouble())
    }

    fun withdrawFunds(v: Long): Boolean {
        val success = portfolio.withdrawFunds(v.toDouble())
        if (!success) {
            viewModelScope.launch {
                _events.emit("❌ Insufficient balance for withdrawal")
            }
        }
        return success
    }
}
