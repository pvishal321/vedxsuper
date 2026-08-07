package com.vedx.vedxsuper.core.state

import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * V5 Centralized AppState Store
 */
class AppStateStore {
    data class GlobalState(
        val market: MarketState = MarketState(),
        val strategy: StrategyState = StrategyState(),
        val portfolio: PortfolioState = PortfolioState(),
        val system: SystemHealth = SystemHealth()
    )

    data class MarketState(
        val lastLtp: Map<String, Double> = emptyMap(),
        val indexST: MultiST? = null,
        val context: MarketContext? = null
    )

    data class StrategyState(
        val lastSignal: Signal? = null,
        val activeSignals: List<Signal> = emptyList()
    )

    data class PortfolioState(
        val balance: Double = 100_000.0,
        val openTrades: List<VirtualTrade> = emptyList(),
        val dailyPnL: Double = 0.0
    )

    data class SystemHealth(
        val isConnected: Boolean = false,
        val latencyMs: Long = 0,
        val cpuUsage: Float = 0f
    )

    private val _state = MutableStateFlow(GlobalState())
    val state = _state.asStateFlow()

    fun updateMarket(transform: (MarketState) -> MarketState) {
        _state.update { it.copy(market = transform(it.market)) }
    }

    fun updateStrategy(transform: (StrategyState) -> StrategyState) {
        _state.update { it.copy(strategy = transform(it.strategy)) }
    }

    fun updatePortfolio(transform: (PortfolioState) -> PortfolioState) {
        _state.update { it.copy(portfolio = transform(it.portfolio)) }
    }
}
