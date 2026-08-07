package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.market.MarketFeedEngine
import com.vedx.vedxsuper.data.OptionDataManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

data class OptionChainUiState(
    val isLoading: Boolean = false,
    val underlying: String = "NIFTY",
    val selectedExpiry: String = "",
    val availableExpiries: List<String> = emptyList(),
    val atmStrike: Double = 0.0,
    val strikes: List<Double> = emptyList(),
    val callPrices: Map<Double, Double> = emptyMap(),
    val putPrices: Map<Double, Double> = emptyMap(),
    val optionChain: OptionDataManager.OptionChain? = null,
    val error: String? = null
)

class OptionChainViewModel(
    private val optionDataManager: OptionDataManager,
    private val marketFeedEngine: MarketFeedEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(OptionChainUiState())
    val uiState: StateFlow<OptionChainUiState> = _uiState.asStateFlow()

    private val tokenToStrikeMap = AtomicReference<Map<String, Pair<Double, Boolean>>>(emptyMap()) // token -> (strike, isCall)

    init {
        selectUnderlying("NIFTY")
        observeTicks()
    }

    private fun observeTicks() {
        viewModelScope.launch {
            marketFeedEngine.ticks.collect { tick ->
                val mapping = tokenToStrikeMap.get()[tick.symbol.trim()]
                if (mapping != null) {
                    val (strike, isCall) = mapping
                    if (isCall) {
                        _uiState.update { it.copy(callPrices = it.callPrices + (strike to tick.ltp)) }
                    } else {
                        _uiState.update { it.copy(putPrices = it.putPrices + (strike to tick.ltp)) }
                    }
                }
            }
        }
    }

    fun selectUnderlying(symbol: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, underlying = symbol, error = null) }
            if (optionDataManager.needsRefresh()) {
                optionDataManager.fetchScripMaster()
            }
            val expiries = optionDataManager.getExpiries(symbol)
            if (expiries.isNotEmpty()) {
                _uiState.update { it.copy(availableExpiries = expiries) }
                selectExpiry(expiries.first())
            } else {
                _uiState.update { it.copy(isLoading = false, error = "No data found for $symbol") }
            }
        }
    }

    fun selectExpiry(expiry: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedExpiry = expiry, callPrices = emptyMap(), putPrices = emptyMap()) }
            val chain = optionDataManager.getOptionChain(_uiState.value.underlying, expiry)
            if (chain != null) {
                // Build token mapping for fast lookup - with trimming
                val newMap = mutableMapOf<String, Pair<Double, Boolean>>()
                chain.calls.forEach { (strike, inst) -> newMap[inst.token.trim()] = strike to true }
                chain.puts.forEach { (strike, inst) -> newMap[inst.token.trim()] = strike to false }
                tokenToStrikeMap.set(newMap)

                _uiState.update { it.copy(isLoading = false, optionChain = chain, strikes = chain.strikes, atmStrike = chain.atmStrike) }
                // Subscribe to tokens
                marketFeedEngine.subscribeOptionTokens(optionDataManager.getAllOptionTokens(chain))
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load chain") }
            }
        }
    }
}
