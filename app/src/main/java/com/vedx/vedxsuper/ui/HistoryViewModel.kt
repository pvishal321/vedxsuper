package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.trade.VirtualTradeManager
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val virtualTradeManager: VirtualTradeManager
) : ViewModel() {

    val allTrades = virtualTradeManager.tradeHistory

    fun clearHistory() {
        viewModelScope.launch {
            virtualTradeManager.clearHistory()
        }
    }
}
