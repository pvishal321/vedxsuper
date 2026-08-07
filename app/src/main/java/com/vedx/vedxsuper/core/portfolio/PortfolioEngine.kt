package com.vedx.vedxsuper.core.portfolio

import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * V4 PortfolioEngine with Persistence
 * Audit 4.5 & 4.8: Balance Reservation & PnL Tracking
 */
class PortfolioEngine(
    private val initialBalance: Double = 100_000.0,
    private val vtd: VirtualTradeDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _balance = MutableStateFlow(initialBalance)
    val balance = _balance.asStateFlow()

    private val _reservedMargin = MutableStateFlow(0.0)
    val reservedMargin = _reservedMargin.asStateFlow()

    private val _openTrades = MutableStateFlow<List<VirtualTrade>>(emptyList())
    val openTrades = _openTrades.asStateFlow()

    private val _tradeHistory = MutableStateFlow<List<VirtualTrade>>(emptyList())
    val tradeHistory = _tradeHistory.asStateFlow()

    init {
        scope.launch {
            val history = vtd?.getAll() ?: emptyList()
            val open = history.filter { it.status == "OPEN" }.map { it.toModel() }
            val closed = history.filter { it.status != "OPEN" }.map { it.toModel() }
            
            _openTrades.value = open
            _tradeHistory.value = closed
            
            val realizedPnL = closed.sumOf { it.pnl.toDouble() - it.charges }
            val usedMargin = open.sumOf { it.entryPrice * it.quantity * 0.15 } // Approx
            
            _balance.value = initialBalance + realizedPnL
            _reservedMargin.value = usedMargin
        }
    }

    fun addTrade(trade: VirtualTrade, margin: Double) {
        _reservedMargin.value += margin
        _openTrades.value = _openTrades.value + trade
        scope.launch { vtd?.save(trade.toDb()) }
    }

    fun closeTrade(tradeId: String, pnl: Double, marginReleased: Double, charges: Double, status: TradeStatus) {
        val trade = _openTrades.value.find { it.id == tradeId } ?: return
        val updated = trade.copy(status = status, pnl = pnl.toLong(), charges = charges, exitTime = System.currentTimeMillis())
        
        _balance.value += (pnl - charges)
        _reservedMargin.value -= marginReleased
        _openTrades.value = _openTrades.value.filter { it.id != tradeId }
        _tradeHistory.value = (listOf(updated) + _tradeHistory.value).take(500)
        
        scope.launch { vtd?.save(updated.toDb()) }
    }

    fun updateTrailingSL(tradeId: String, newSL: Double) {
        _openTrades.value = _openTrades.value.map {
            if (it.id == tradeId) it.copy(stopLoss = newSL) else it
        }
    }

    fun getOpenTrades() = _openTrades.value

    fun resetBalance() {
        scope.launch {
            vtd?.clear()
            _balance.value = initialBalance
            _reservedMargin.value = 0.0
            _openTrades.value = emptyList()
            _tradeHistory.value = emptyList()
        }
    }

    fun addFunds(amount: Double) {
        _balance.value += amount
    }

    fun withdrawFunds(amount: Double): Boolean {
        return if (_balance.value >= amount) {
            _balance.value -= amount
            true
        } else {
            false
        }
    }

    private fun DbVirtualTrade.toModel() = VirtualTrade(
        id = id, symbol = symbol, action = action, entryPrice = entryPrice, 
        quantity = quantity, stopLoss = stopLoss, target = target, confidence = 0, 
        reason = "", matchedBand = matchedBand, entryTime = entryTime, 
        status = TradeStatus.valueOf(status), pnl = pnl, charges = charges
    )
    
    private fun VirtualTrade.toDb() = DbVirtualTrade(
        id = id, symbol = symbol, action = action, entryPrice = entryPrice, 
        quantity = quantity, stopLoss = stopLoss, target = target, 
        matchedBand = matchedBand, entryTime = entryTime, status = status.name, 
        pnl = pnl, charges = charges
    )
}
