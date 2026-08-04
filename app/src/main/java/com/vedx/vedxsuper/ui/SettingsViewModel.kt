package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import com.vedx.vedxsuper.trade.VirtualTradeManager
import com.vedx.vedxsuper.utils.SettingsManager

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val virtualTradeManager: VirtualTradeManager
) : ViewModel() {
    
    fun resetAll() {
        settingsManager.resetAllSettings()
        virtualTradeManager.resetBalance()
        virtualTradeManager.clearHistory()
    }
}
