package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.database.TradeEntity
import com.vedx.vedxsuper.repository.TradeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val tradeRepository: TradeRepository
) : ViewModel() {

    val allTrades: Flow<List<TradeEntity>> = tradeRepository.allTrades

    fun clearHistory() {
        viewModelScope.launch {
            tradeRepository.clearHistory()
        }
    }
}
