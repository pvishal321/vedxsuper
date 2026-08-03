package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.database.TradeEntity
import com.vedx.vedxsuper.model.trade.StrategyConfig
import com.vedx.vedxsuper.repository.MarketRepository
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.strategy.signal.*
import com.vedx.vedxsuper.trade.VirtualTradeManager
import com.vedx.vedxsuper.model.market.TickData
import com.vedx.vedxsuper.strategy.engine.StrengthMetrics
import com.vedx.vedxsuper.strategy.options.OptionSelector
import com.vedx.vedxsuper.strategy.options.OptionType
import com.vedx.vedxsuper.websocket.SmartStreamManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.*

class MarketViewModel(
    private val repository: MarketRepository,
    private val tradeRepository: TradeRepository,
    private val tradeManager: VirtualTradeManager,
    private val smartStreamManager: SmartStreamManager,
    private val tokenManager: SecureTokenManager
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()
    val connectionState = smartStreamManager.connectionState
    fun hasValidSession() = tokenManager.hasValidSession()
    fun isMarketOpenCheck() = isMarketOpen()

    val indexData = repository.indexData
    val allTrades = tradeRepository.allTrades
    val openTrades = tradeRepository.openTrades

    private val _activeIndices = MutableStateFlow(repository.getActiveIndices())
    val activeIndices = _activeIndices.asStateFlow()

    fun toggleIndex(symbol: String) {
        repository.toggleIndex(symbol)
        _activeIndices.value = repository.getActiveIndices()
    }

    fun getIndexStrength(symbol: String) = repository.getIndexStrength(symbol)
    fun getOptionStrength(symbol: String) = repository.getOptionStrength(symbol)
    fun getTrendLifecycle() = repository.getTrendLifecycle()
    fun getTrendState(symbol: String) = repository.getTrendState(symbol)
    fun getMarketRegime(symbol: String) = repository.getMarketRegime(symbol)
    fun getMarketStructure(symbol: String) = repository.getMarketStructure(symbol)
    fun getLearningStats() = repository.getLearningStats()
    fun getRiskState() = repository.getRiskState()

    fun getOptionMetrics(symbol: String) = repository.getOptionMetrics(symbol)

    fun getIntelligence(symbol: String) = repository.getIntelligence(symbol)

    init {
        // App starts with empty state and waits for real data from Angel One API.
        // No more mock data or simulations.
    }

    private fun isMarketOpen(): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute

        val isWeekday = day in Calendar.MONDAY..Calendar.FRIDAY
        val isTimeInRange = timeInMinutes in 555..930 // 9:15 AM to 3:30 PM

        return isWeekday && isTimeInRange
    }

    fun getStrategyState(token: String): Flow<StrategyState> {
        return repository.getStrategyEngine(token)?.state ?: emptyFlow()
    }

    fun getMultiTrendState(symbol: String): Flow<MultiTrendStrategyState?> {
        return repository.getMultiTrendState(symbol)
    }

    fun getCorrelationSignals(): Flow<List<CorrelationSignal>> {
        return repository.getCorrelationSignals()
    }

    fun getInstitutionalSignals() = repository.getInstitutionalSignals()

    fun enterTrade(symbol: String, type: String, price: Double, sl: Double, target: Double) {
        viewModelScope.launch {
            var tradeSymbol = symbol
            var tradePrice = price
            var tradeSide = if (type == "BUY") "BUY_CALL" else "BUY_PUT"

            // [NEW] If user clicks Index signal, find the ATM Option to trade
            if (symbol == "NIFTY" || symbol == "BANKNIFTY" || symbol == "FINNIFTY" || symbol == "SENSEX") {
                val selector = OptionSelector()
                val strike = selector.getATMStrike(price, symbol)
                val optType = if (type == "BUY") "CE" else "PE"
                tradeSymbol = "${symbol}_${strike.toInt()}_$optType"

                // Get LTP for this option if available
                val optionData = indexData.value[tradeSymbol]
                tradePrice = optionData?.lastTradedPrice ?: 100.0 // Fallback
                tradeSide = if (optType == "CE") "BUY_CALL" else "BUY_PUT"
                _events.emit("Picking Option: $tradeSymbol @ ₹$tradePrice")
            } else {
                // Direct Option trade from matrix
                tradeSide = if (symbol.contains("PE")) "BUY_PUT" else "BUY_CALL"
            }

            _events.emit("Opening Trade for $tradeSymbol...")
            tradeManager.processSignal(
                side = tradeSide,
                symbol = tradeSymbol,
                price = tradePrice,
                config = StrategyConfig(),
                providedSL = if (tradeSymbol != symbol) 0.0 else sl, // Reset SL for options to let manager calc it
                providedTarget = if (tradeSymbol != symbol) 0.0 else target
            )
            _events.emit("Trade Executed Successfully")
        }
    }

    fun exitTrade(trade: TradeEntity, price: Double) {
        viewModelScope.launch {
            tradeManager.closePosition(trade, price)
        }
    }

    fun clearTradeHistory() {
        viewModelScope.launch {
            tradeRepository.clearHistory()
        }
    }

    fun syncAllHistory() {
        repository.syncAllHistory()
    }

    fun updateTimeframe(minutes: Int) {
        repository.updateTimeframe(minutes)
    }

    fun reconnectFeed() {
        viewModelScope.launch {
            smartStreamManager.disconnect()
            delay(1000)
            smartStreamManager.connect()
            // Also refresh latest history to bridge any gap
            repository.syncAllHistory()
        }
    }

    fun getVirtualBalance() = repository.getVirtualBalance()
    fun addFunds(amount: Double) = repository.addFunds(amount)
    fun withdrawFunds(amount: Double) = repository.withdrawFunds(amount)

    /**
     * [FIXED] Point 9: Ensures all components are cleared when user logs out.
     */
    fun resolveToken(symbol: String): String {
        return OptionSelector().resolveToken(symbol) ?: ""
    }

    fun emergencyExitAll() {
        viewModelScope.launch {
            _events.emit("EMERGENCY EXIT INITIATED!")
            repository.logoutCleanup() // This cleans up engines and stops monitoring
            _events.emit("All automated engines stopped.")
        }
    }

    fun logout() {
        smartStreamManager.disconnect()
        repository.logoutCleanup()
        tokenManager.clearAll()
    }
}
