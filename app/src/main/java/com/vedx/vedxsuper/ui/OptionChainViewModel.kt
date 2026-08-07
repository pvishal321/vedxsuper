package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.core.market.MarketFeedEngine
import com.vedx.vedxsuper.data.OptionDataManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
    private val marketFeedEngine: com.vedx.vedxsuper.core.market.MarketFeedEngine,
    private val appStateStore: com.vedx.vedxsuper.core.state.AppStateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OptionChainUiState())
    val uiState: StateFlow<OptionChainUiState> = _uiState.asStateFlow()

    private val tokenToStrikeMap = AtomicReference<Map<String, Pair<Double, Boolean>>>(emptyMap()) // token -> (strike, isCall)
    
    // Throttling UI updates to prevent OOM and lag
    private val currentCallPrices = ConcurrentHashMap<Double, Double>()
    private val currentPutPrices = ConcurrentHashMap<Double, Double>()
    private var lastUsedUnderlying = ""
    private var lastUsedIndexPrice = 0.0

    init {
        selectUnderlying("NIFTY")
        observeTicks()
        observeIndexPrice()
    }

    private fun observeTicks() {
        // Collect ticks and update local cache
        viewModelScope.launch {
            marketFeedEngine.ticks.collect { tick ->
                // Normalize token by trimming and removing leading zeros
                val normalizedToken = tick.symbol.trim().trimStart('0')
                val mapping = tokenToStrikeMap.get()[normalizedToken]
                if (mapping != null) {
                    val (strike, isCall) = mapping
                    if (isCall) currentCallPrices[strike] = tick.ltp
                    else currentPutPrices[strike] = tick.ltp
                }
            }
        }

        // Throttle UI updates to once per 500ms
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (currentCallPrices.isNotEmpty() || currentPutPrices.isNotEmpty()) {
                    val calls = currentCallPrices.toMap()
                    val puts = currentPutPrices.toMap()
                    _uiState.update { 
                        it.copy(
                            callPrices = it.callPrices + calls,
                            putPrices = it.putPrices + puts
                        )
                    }
                }
            }
        }
    }

    private fun observeIndexPrice() {
        viewModelScope.launch {
            appStateStore.state.collect { state ->
                val underlying = _uiState.value.underlying
                val currentPrice = state.market.lastLtp[underlying] ?: 0.0

                if (currentPrice > 0 && (underlying != lastUsedUnderlying || lastUsedIndexPrice <= 0 || kotlin.math.abs(currentPrice - lastUsedIndexPrice) > 10.0) && _uiState.value.selectedExpiry.isNotEmpty()) {
                    lastUsedUnderlying = underlying
                    lastUsedIndexPrice = currentPrice
                    refreshChainForSelectedExpiry(currentPrice)
                }
            }
        }
    }

    private fun refreshChainForSelectedExpiry(currentIdxPrice: Double) {
        viewModelScope.launch {
            val expiry = _uiState.value.selectedExpiry
            if (expiry.isEmpty()) return@launch
            val fullChain = optionDataManager.getOptionChain(_uiState.value.underlying, expiry)
            if (fullChain != null) {
                updateChainState(fullChain, currentIdxPrice)
            }
        }
    }

    private fun updateChainState(chain: OptionDataManager.OptionChain, currentIdxPrice: Double) {
        val effectivePrice = if (currentIdxPrice > 0) {
            currentIdxPrice
        } else {
            chain.strikes.getOrNull(chain.strikes.size / 2)
                ?: chain.strikes.firstOrNull()
                ?: 0.0
        }

        val limitedChain = optionDataManager.getATMStrikes(chain, effectivePrice, range = 5)
        val newMap = mutableMapOf<String, Pair<Double, Boolean>>()
        limitedChain.calls.forEach { (strike, inst) ->
            newMap[inst.token.trim().trimStart('0')] = strike to true
        }
        limitedChain.puts.forEach { (strike, inst) ->
            newMap[inst.token.trim().trimStart('0')] = strike to false
        }
        tokenToStrikeMap.set(newMap)

        _uiState.update {
            it.copy(
                isLoading = false,
                optionChain = limitedChain,
                strikes = limitedChain.strikes,
                atmStrike = limitedChain.atmStrike
            )
        }
        marketFeedEngine.subscribeOptionTokens(optionDataManager.getTokensForStrikes(limitedChain, limitedChain.strikes))
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
                val currentIdxPrice = appStateStore.state.value.market.lastLtp[_uiState.value.underlying] ?: 0.0
                lastUsedUnderlying = _uiState.value.underlying
                lastUsedIndexPrice = currentIdxPrice
                updateChainState(chain, currentIdxPrice)
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load chain") }
            }
        }
    }
}
