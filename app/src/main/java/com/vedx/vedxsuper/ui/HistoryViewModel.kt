package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val portfolio: PortfolioEngine
) : ViewModel() {

    val allTrades = portfolio.tradeHistory

    fun clearHistory() {
        portfolio.resetBalance()
    }
}
