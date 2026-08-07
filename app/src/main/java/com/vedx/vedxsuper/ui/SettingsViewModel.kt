package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.TradingConstants
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import com.vedx.vedxsuper.core.state.AppStateStore
import com.vedx.vedxsuper.data.VirtualTrade
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

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
    val soundAlerts: Boolean = true,
    val niftyPrice: Double = 0.0,
    val bankNiftyPrice: Double = 0.0,
    val sensexPrice: Double = 0.0,
    val niftyStatus: String = "Missing",
    val bankNiftyStatus: String = "Missing",
    val sensexStatus: String = "Missing",
    val marketConnected: Boolean = false
)

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val portfolio: PortfolioEngine,
    private val stateStore: AppStateStore
) : ViewModel() {
    
    val uiState: StateFlow<SettingsUiState> = combine(
        portfolio.balance,
        portfolio.tradeHistory,
        settingsManager.autoTradeConfirm,
        settingsManager.defaultQuantity,
        settingsManager.riskPerTrade,
        settingsManager.darkMode,
        settingsManager.notificationsEnabled,
        settingsManager.soundAlerts,
        stateStore.state
    ) { args: Array<Any> ->
        val balance = args[0] as Double
        val history = args[1] as List<VirtualTrade>
        val autoConfirm = args[2] as Boolean
        val defQty = args[3] as Int
        val risk = args[4] as Int
        val dark = args[5] as Boolean
        val notify = args[6] as Boolean
        val sound = args[7] as Boolean
        val state = args[8] as AppStateStore.GlobalState
        
        val wins = history.count { it.status == com.vedx.vedxsuper.data.TradeStatus.PROFIT }
        val rate = if (history.isNotEmpty()) (wins.toFloat() / history.size * 100f) else 0f
        
        val niftyPrice = state.market.lastLtp["NIFTY"] ?: 0.0
        val bankNiftyPrice = state.market.lastLtp["BANKNIFTY"] ?: 0.0
        val sensexPrice = state.market.lastLtp["SENSEX"] ?: 0.0
        
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
            soundAlerts = sound,
            niftyPrice = niftyPrice,
            bankNiftyPrice = bankNiftyPrice,
            sensexPrice = sensexPrice,
            niftyStatus = if (niftyPrice > 0.0) "Live" else "Missing",
            bankNiftyStatus = if (bankNiftyPrice > 0.0) "Live" else "Missing",
            sensexStatus = if (sensexPrice > 0.0) "Live" else "Missing",
            marketConnected = state.system.isConnected
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

    fun validateIndexData() {
        val state = stateStore.state.value
        val niftyPrice = state.market.lastLtp["NIFTY"] ?: 0.0
        val bankPrice = state.market.lastLtp["BANKNIFTY"] ?: 0.0
        val sensexPrice = state.market.lastLtp["SENSEX"] ?: 0.0
        val message = "Index check => NIFTY: ${if (niftyPrice > 0) "${String.format(Locale.US, "%.2f", niftyPrice)}" else "missing"}, " +
            "BANKNIFTY: ${if (bankPrice > 0) "${String.format(Locale.US, "%.2f", bankPrice)}" else "missing"}, " +
            "SENSEX: ${if (sensexPrice > 0) "${String.format(Locale.US, "%.2f", sensexPrice)}" else "missing"}"
        viewModelScope.launch {
            _events.emit(message)
        }
    }
}
