package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.TradingConstants
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.*

class TradeViewModel(
    private val portfolio: PortfolioEngine,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val openTrades = portfolio.openTrades
    val tradeHistory = portfolio.tradeHistory
    val balance = portfolio.balance
    val totalPnL = portfolio.balance.map { (it - TradingConstants.INITIAL_VIRTUAL_BALANCE).toLong() }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)
}
