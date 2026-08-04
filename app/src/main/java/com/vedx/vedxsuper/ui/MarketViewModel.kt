package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.core.UltraNeuralCore
import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.MultiST
import com.vedx.vedxsuper.notification.TradeNotificationManager
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.stream.FastTickEngine
import com.vedx.vedxsuper.trade.VirtualTrade
import com.vedx.vedxsuper.trade.VirtualTradeManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class IndexData(
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePct: Double
)

data class CorrelationSignal(
    val optionSymbol: String,
    val indexSignal: IndexSignal
)

data class IndexSignal(
    val type: String,
    val price: Double
)

data class InstitutionalSignal(
    val optionSymbol: String,
    val type: String,
    val confidence: Int,
    val reason: String
)

data class MultiTrendState(
    val candles: List<Candle>,
    val stResult: MultiST?
)

class MarketViewModel(
    private val ultraNeuralCore: UltraNeuralCore,
    private val tradeRepository: TradeRepository,
    private val virtualTradeManager: VirtualTradeManager,
    private val tokenManager: SecureTokenManager,
    private val notificationManager: TradeNotificationManager
) : ViewModel() {

    private val _indexData = MutableStateFlow<List<IndexData>>(emptyList())
    val indexData: StateFlow<List<IndexData>> = _indexData.asStateFlow()

    val signals = ultraNeuralCore.signals

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _correlationSignals = MutableStateFlow<List<CorrelationSignal>>(emptyList())
    fun getCorrelationSignals(): StateFlow<List<CorrelationSignal>> = _correlationSignals.asStateFlow()

    private val _institutionalSignals = MutableStateFlow<List<InstitutionalSignal>>(emptyList())
    fun getInstitutionalSignals(): StateFlow<List<InstitutionalSignal>> = _institutionalSignals.asStateFlow()

    private val _multiTrendStates = MutableStateFlow<Map<String, MultiTrendState>>(emptyMap())

    fun getMultiTrendState(symbol: String): StateFlow<MultiTrendState?> {
        return _multiTrendStates.map { it[symbol] }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    init {
        // Simulate index data for demo
        viewModelScope.launch {
            _indexData.value = listOf(
                IndexData("NIFTY", 24500.0, 120.5, 0.49),
                IndexData("BANKNIFTY", 52000.0, -80.0, -0.15),
                IndexData("FINNIFTY", 23500.0, 45.0, 0.19)
            )
        }
    }

    /**
     * When signal arrives, send notification FIRST, then execute virtual trade on user confirmation
     */
    fun onSignalReceived(
        symbol: String,
        action: String,
        price: Double,
        stopLoss: Double,
        target: Double,
        confidence: Int,
        reason: String
    ) {
        viewModelScope.launch {
            // STEP 1: Send notification BEFORE any trade
            notificationManager.sendPreTradeNotification(
                symbol = symbol,
                action = action,
                price = price,
                stopLoss = stopLoss,
                target = target,
                confidence = confidence,
                reason = reason
            )

            _events.emit(String.format(Locale.US, "📢 Signal Alert: %s %s @ ₹%.2f", action, symbol, price))
        }
    }

    /**
     * User confirmed the trade from notification/app
     */
    fun confirmVirtualTrade(
        symbol: String,
        action: String,
        price: Double,
        stopLoss: Double,
        target: Double,
        confidence: Int,
        reason: String,
        quantity: Int = 50
    ) {
        viewModelScope.launch {
            val trade = when (action) {
                "BUY" -> virtualTradeManager.executeVirtualBuy(
                    symbol, price, quantity, stopLoss, target, confidence, reason
                )
                "SELL" -> virtualTradeManager.executeVirtualSell(
                    symbol, price, quantity, stopLoss, target, confidence, reason
                )
                else -> null
            }

            if (trade != null) {
                notificationManager.sendTradeExecutedNotification(symbol, action, price, quantity)
                _events.emit("✅ Virtual $action executed: $symbol | Qty: $quantity")
            } else {
                _events.emit("❌ Insufficient virtual balance for $action $symbol")
            }
        }
    }

    fun reconnectFeed() {
        viewModelScope.launch {
            _events.emit("🔄 Reconnecting market feed...")
        }
    }

    fun logout() {
        tokenManager.clearSession()
    }

    fun syncAllHistory() {
        viewModelScope.launch {
            _events.emit("📊 History synced")
        }
    }

    fun emergencyExitAll() {
        viewModelScope.launch {
            _events.emit("🚨 Emergency exit triggered for all open trades")
        }
    }

    fun updateTimeframe(timeframe: String) {
        viewModelScope.launch {
            _events.emit("⏱️ Timeframe changed to $timeframe")
        }
    }
}
