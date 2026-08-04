package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import com.vedx.vedxsuper.trade.VirtualTradeManager
import com.vedx.vedxsuper.utils.SettingsManager

class TradeViewModel(
    private val virtualTradeManager: VirtualTradeManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val openTrades = virtualTradeManager.openTrades
    val tradeHistory = virtualTradeManager.tradeHistory
    val balance = virtualTradeManager.balance
    val totalPnL = virtualTradeManager.totalPnL
}
