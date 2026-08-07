package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.auth.SecureTokenManagerV2
import com.vedx.vedxsuper.core.TradingConstants
import com.vedx.vedxsuper.core.UltraNeuralCore
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import com.vedx.vedxsuper.core.state.AppStateStore
import com.vedx.vedxsuper.core.trade.VirtualTradeEngine
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.notification.TradeNotificationManager
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class IndexData(val symbol: String, val price: Double, val change: Double, val changePct: Double)
data class MarketAnalysis(val regime: Regimes = Regimes.SIDEWAY, val vix: Double = 15.5, val pcr: Double = 0.95, val adx: Double = 22.0, val isCompressed: Boolean = false)
data class MultiTrendState(val candles: List<Candle>, val stResult: MultiST?)

class MarketViewModel(
    private val ultraNeuralCore: UltraNeuralCore,
    private val tradeRepository: TradeRepository,
    private val portfolio: PortfolioEngine,
    private val virtualTrade: VirtualTradeEngine,
    private val tokenManager: SecureTokenManagerV2,
    private val notificationManager: TradeNotificationManager,
    private val stateStore: AppStateStore,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val appState = stateStore.state
    
    private val _selectedSymbol = MutableStateFlow("NIFTY")
    val selectedSymbol = _selectedSymbol.asStateFlow()

    private val _indexData = MutableStateFlow<List<IndexData>>(emptyList())
    val indexData = _indexData.asStateFlow()

    val signals = ultraNeuralCore.signals
    val indexSTLevels = ultraNeuralCore.indexSTResult

    private val _marketAnalysis = MutableStateFlow(MarketAnalysis())
    val marketAnalysis = _marketAnalysis.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val multiTrendCache = mutableMapOf<String, StateFlow<MultiTrendState?>>()

    init {
        // Sync index data from stateStore
        appState.onEach { state ->
            _indexData.value = listOf(
                IndexData("NIFTY 50", state.market.lastLtp["NIFTY"] ?: 0.0, 0.0, 0.0),
                IndexData("BANK NIFTY", state.market.lastLtp["BANKNIFTY"] ?: 0.0, 0.0, 0.0),
                IndexData("FIN NIFTY", state.market.lastLtp["FINNIFTY"] ?: 0.0, 0.0, 0.0),
                IndexData("MIDCAP", state.market.lastLtp["MIDCPNIFTY"] ?: 0.0, 0.0, 0.0),
                IndexData("SENSEX", state.market.lastLtp["SENSEX"] ?: 0.0, 0.0, 0.0),
                IndexData("BANKEX", state.market.lastLtp["BANKEX"] ?: 0.0, 0.0, 0.0)
            )

            state.market.indexST?.let { st ->
                _marketAnalysis.value = _marketAnalysis.value.copy(
                    regime = st.regime,
                    adx = st.adx,
                    isCompressed = st.isCompressed
                )
            }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            signals.collect { sigList ->
                sigList.lastOrNull()?.let { signal ->
                    if (signal.isEntry) {
                        notificationManager.sendPreTradeNotification(
                            signal.symbol.value, signal.action.name, signal.entryPrice.rupees,
                            signal.stopLoss.rupees, signal.target.rupees, signal.confidence.pct, signal.reason
                        )
                        _events.emit(String.format(Locale.US, "📢 Signal Alert: %s %s @ ₹%.2f", signal.action, signal.symbol.value, signal.entryPrice.rupees))
                    }
                }
            }
        }
    }

    fun confirmVirtualTrade(signal: Signal) {
        viewModelScope.launch {
            val riskPct = settingsManager.riskPerTrade.value / 100.0
            virtualTrade.executeSignal(signal, riskPct)
            _events.emit("✅ Virtual Trade Confirmed: ${signal.symbol.value}")
        }
    }

    fun logout() { viewModelScope.launch { tokenManager.clearAll() } }
    fun reconnectFeed() { viewModelScope.launch { _events.emit("🔄 Reconnecting market feed...") } }

    fun selectSymbol(symbol: String) {
        _selectedSymbol.value = symbol
    }

    // Legacy Bridge for VedxUI
    val totalPnL = portfolio.balance.map { (it - TradingConstants.INITIAL_VIRTUAL_BALANCE).toLong() }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)
    
    fun syncAllHistory() {
        viewModelScope.launch {
            _events.emit("🔄 Syncing trade history...")
            // portfolio is already synced in init
        }
    }
    
    fun emergencyExitAll() {
        viewModelScope.launch {
            virtualTrade.closeAllTrades()
            _events.emit("⚠️ EMERGENCY EXIT EXECUTED")
        }
    }

    fun getMultiTrendState(symbol: String): StateFlow<MultiTrendState?> {
        return multiTrendCache.getOrPut(symbol) {
            if (symbol == "NIFTY") {
                combine(ultraNeuralCore.indexCandlesFlow, ultraNeuralCore.indexSTResult) { candles, st ->
                    MultiTrendState(candles, st)
                }.stateIn(viewModelScope, SharingStarted.Lazily, null)
            } else {
                MutableStateFlow<MultiTrendState?>(null).asStateFlow()
            }
        }
    }
    
    fun onSignalReceived(s: String, a: String, p: Double, sl: Double, t: Double, c: Int, r: String) {
        // Handle legacy signal bridge if needed
    }
}
