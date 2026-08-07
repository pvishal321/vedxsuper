package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.auth.SecureTokenManagerV2
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val tokenManager: SecureTokenManagerV2,
    private val portfolio: PortfolioEngine
) : ViewModel() {
    
    val accountInfo = tokenManager.getStoredTokens()
    
    val balance = portfolio.balance
    
    val activeTradesCount = portfolio.openTrades.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
        
    val dailyPnL = portfolio.tradeHistory.map { history ->
        // Simplified daily PnL (in a real app, filter by today's date)
        history.sumOf { it.pnl.toDouble() - it.charges }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
}
