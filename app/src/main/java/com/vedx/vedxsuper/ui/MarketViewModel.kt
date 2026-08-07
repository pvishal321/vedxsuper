package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.auth.SecureTokenManagerV2
import com.vedx.vedxsuper.core.TradingConstants
import com.vedx.vedxsuper.core.UltraNeuralCore
import com.vedx.vedxsuper.core.market.MarketFeedEngine
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
    private val settingsManager: SettingsManager,
    private val marketFeedEngine: MarketFeedEngine
) : ViewModel() {

    val appState = stateStore.state
    
    private val _selectedSymbol = MutableStateFlow("NIFTY")
    val selectedSymbol = _selectedSymbol.asStateFlow()

    private val niftyPrice = ultraNeuralCore.getIndexCandles("NIFTY")
        .map { it.lastOrNull()?.close?.rupees ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    private val bankNiftyPrice = ultraNeuralCore.getIndexCandles("BANKNIFTY")
        .map { it.lastOrNull()?.close?.rupees ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    private val sensexPrice = ultraNeuralCore.getIndexCandles("SENSEX")
        .map { it.lastOrNull()?.close?.rupees ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val indexData = combine(appState, niftyPrice, bankNiftyPrice, sensexPrice) { state, nifty, bank, sensex ->
        val niftyLtp = state.market.lastLtp["NIFTY"]?.takeIf { it > 0 } ?: nifty
        val bankNiftyLtp = state.market.lastLtp["BANKNIFTY"]?.takeIf { it > 0 } ?: bank
        val sensexLtp = state.market.lastLtp["SENSEX"]?.takeIf { it > 0 } ?: sensex

        val niftyChg = state.market.lastChange["NIFTY"] ?: 0.0
        val niftyPct = state.market.lastChangePct["NIFTY"] ?: 0.0
        val bankNiftyChg = state.market.lastChange["BANKNIFTY"] ?: 0.0
        val bankNiftyPct = state.market.lastChangePct["BANKNIFTY"] ?: 0.0
        val sensexChg = state.market.lastChange["SENSEX"] ?: 0.0
        val sensexPct = state.market.lastChangePct["SENSEX"] ?: 0.0

        listOf(
            IndexData("NIFTY", niftyLtp, niftyChg, niftyPct),
            IndexData("BANKNIFTY", bankNiftyLtp, bankNiftyChg, bankNiftyPct),
            IndexData("SENSEX", sensexLtp, sensexChg, sensexPct)
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val signals = ultraNeuralCore.signals
    val indexSTLevels = ultraNeuralCore.indexSTResult

    private val _marketAnalysis = MutableStateFlow(MarketAnalysis())
    val marketAnalysis = _marketAnalysis.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val multiTrendCache = mutableMapOf<String, StateFlow<MultiTrendState?>>()

    init {
        // Sync market analysis from stateStore. Index data is derived from both app state and candle prices.
        appState.onEach { state ->
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
    fun reconnectFeed() {
        viewModelScope.launch {
            marketFeedEngine.connect()
            _events.emit("🔄 Reconnecting market feed...")
        }
    }

    fun selectSymbol(symbol: String) {
        _selectedSymbol.value = symbol
    }

    // Legacy Bridge for VedxUI
    val totalPnL = portfolio.balance.map { (it - TradingConstants.INITIAL_VIRTUAL_BALANCE).toLong() }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)
    
    fun syncAllHistory() {
        viewModelScope.launch {
            _events.emit("🔄 Syncing trade history...")
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
            combine(ultraNeuralCore.indexCandlesFlow, ultraNeuralCore.indexSTResult) { candles, st ->
                MultiTrendState(candles, st)
            }.stateIn(viewModelScope, SharingStarted.Lazily, null)
        }
    }
    
    fun onSignalReceived(s: String, a: String, p: Double, sl: Double, t: Double, c: Int, r: String) {
    }
}
