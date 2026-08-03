package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.database.TradeEntity
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.trade.VirtualTradeManager
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TradeViewModel(
    private val tradeRepository: TradeRepository,
    private val tradeManager: VirtualTradeManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val openTrades: Flow<List<TradeEntity>> = tradeRepository.openTrades

    fun enterTrade(symbol: String, type: String, price: Double) {
        viewModelScope.launch {
            val config = settingsManager.getStrategyConfig()
            tradeManager.processSignal(
                side = type,
                symbol = symbol,
                price = price,
                config = config,
                confidence = 100,
                explanation = "Manual Entry"
            )
        }
    }

    fun exitTrade(trade: TradeEntity, exitPrice: Double) {
        viewModelScope.launch {
            tradeManager.closePosition(trade, exitPrice)
        }
    }
}
