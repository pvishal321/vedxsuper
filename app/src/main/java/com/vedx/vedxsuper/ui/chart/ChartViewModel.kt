package com.vedx.vedxsuper.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.UltraNeuralCore
import com.vedx.vedxsuper.core.state.AppStateStore
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.ui.MarketAnalysis
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChartViewModel(
    private val core: UltraNeuralCore,
    private val stateStore: AppStateStore
) : ViewModel() {

    private val _selectedIndex = MutableStateFlow(IndianIndex.NIFTY_50)
    val selectedIndex = _selectedIndex.asStateFlow()

    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption = _selectedOption.asStateFlow()

    private val _currentPrice = MutableStateFlow(0.0)
    val currentPrice = _currentPrice.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Context Data from AppState
    val appState = stateStore.state
    
    val marketAnalysis = appState.map { state ->
        val st = state.market.indexST
        MarketAnalysis(
            regime = st?.regime ?: Regimes.SIDEWAY,
            vix = 15.5, // Default VIX
            pcr = 0.95, // Replace with actual PCR logic if needed
            adx = st?.adx ?: 20.0,
            isCompressed = st?.isCompressed ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MarketAnalysis())

    // Using the flow directly from core
    val indexCandles: StateFlow<List<Candle>> = _selectedIndex.flatMapLatest { 
        core.getIndexCandles(it.symbol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val indexST: StateFlow<MultiST?> = _selectedIndex.flatMapLatest { 
        core.getIndexST(it.symbol)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val signals: StateFlow<List<Signal>> = core.signals

    private val _optionST = MutableStateFlow<MultiST?>(null)
    val optionST = _optionST.asStateFlow()

    init {
        // Sync current price from global app state (Reacts to selection changes)
        combine(appState, _selectedIndex, _selectedOption) { state, index, option ->
            val symbol = option ?: index.symbol
            state.market.lastLtp[symbol] ?: 0.0
        }.onEach { price ->
            _currentPrice.value = price
        }.launchIn(viewModelScope)
    }

    fun selectIndex(index: IndianIndex) {
        _selectedIndex.value = index
    }

    fun selectOption(symbol: String) {
        _selectedOption.value = symbol
    }

    // Ticks are now handled globally by UltraNeuralCore via EventBus
    fun onIndexTick(ltp: Double, volume: Long, timestamp: Long) {
        _currentPrice.value = ltp
    }

    fun onOptionTick(symbol: String, ltp: Double, volume: Long, timestamp: Long) {
        viewModelScope.launch {
            // Determine underlying for option ST calculation
            val underlying = if (symbol.contains("BANKNIFTY")) "BANKNIFTY" else if (symbol.contains("SENSEX")) "SENSEX" else "NIFTY"
            core.onOptionTick(symbol, ltp, volume, timestamp, _currentPrice.value, underlying = underlying)
            val optCandles = core.getOptionCandles(symbol)
            if (optCandles.size >= 15) {
                val engine = com.vedx.vedxsuper.core.strategy.SuperTrendEngine()
                _optionST.value = engine.calculate(optCandles)
            }
        }
    }

    fun initializeWithHistory(history: List<Candle>) {
        core.initialize(history)
    }

    override fun onCleared() {
        super.onCleared()
    }

    enum class IndianIndex(val displayName: String, val symbol: String) {
        NIFTY_50("Nifty 50", "NIFTY"),
        BANKNIFTY("Bank Nifty", "BANKNIFTY"),
        SENSEX("Sensex", "SENSEX")
    }
}
