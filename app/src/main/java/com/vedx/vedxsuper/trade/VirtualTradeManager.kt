package com.vedx.vedxsuper.trade

import android.content.Context
import androidx.core.content.edit
import com.vedx.vedxsuper.VedxApp
import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Pure Virtual Trading Manager - NO REAL ORDERS
 * Manages paper trading with virtual balance, P&L tracking, and trade history.
 */
class VirtualTradeManager(context: Context) {

    private val notificationManager get() = VedxApp.instance.tradeNotificationManager

    companion object {
        private const val PREFS_NAME = "vedx_virtual_wallet"
        private const val KEY_BALANCE = "virtual_balance"
        private const val KEY_TOTAL_PNL = "total_pnl"
        private const val KEY_TRADE_COUNT = "trade_count"
        private const val DEFAULT_BALANCE = 100000L // ₹1,00,000 default
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _balance = MutableStateFlow(prefs.getLong(KEY_BALANCE, DEFAULT_BALANCE))
    val balance: StateFlow<Long> = _balance.asStateFlow()

    private val _totalPnL = MutableStateFlow(prefs.getLong(KEY_TOTAL_PNL, 0L))
    val totalPnL: StateFlow<Long> = _totalPnL.asStateFlow()

    private val _openTrades = MutableStateFlow<List<VirtualTrade>>(emptyList())
    val openTrades: StateFlow<List<VirtualTrade>> = _openTrades.asStateFlow()

    private val _tradeHistory = MutableStateFlow<List<VirtualTrade>>(emptyList())
    val tradeHistory: StateFlow<List<VirtualTrade>> = _tradeHistory.asStateFlow()

    private val _tradeCount = MutableStateFlow(prefs.getInt(KEY_TRADE_COUNT, 0))
    val tradeCount: StateFlow<Int> = _tradeCount.asStateFlow()

    // ===== BALANCE MANAGEMENT =====

    fun getBalance(): Long = _balance.value

    fun addFunds(amount: Long) {
        if (amount <= 0) return
        val newBalance = _balance.value + amount
        _balance.value = newBalance
        saveBalance(newBalance)
    }

    fun withdrawFunds(amount: Long): Boolean {
        if (amount <= 0 || amount > _balance.value) return false
        val newBalance = _balance.value - amount
        _balance.value = newBalance
        saveBalance(newBalance)
        return true
    }

    fun resetBalance() {
        _balance.value = DEFAULT_BALANCE
        saveBalance(DEFAULT_BALANCE)
    }

    private fun saveBalance(amount: Long) {
        prefs.edit { putLong(KEY_BALANCE, amount) }
    }

    // ===== VIRTUAL TRADE EXECUTION =====

    /**
     * Execute a virtual BUY trade (paper trade only)
     * Returns the created VirtualTrade or null if insufficient funds
     */
    fun executeVirtualBuy(
        symbol: String,
        entryPrice: Double,
        quantity: Int,
        stopLoss: Double,
        target: Double,
        confidence: Int,
        reason: String
    ): VirtualTrade? {
        val entryValue = (entryPrice * quantity).toLong()
        val brokerage = (entryValue * 0.0003).toLong().coerceAtLeast(20) // 0.03% min ₹20
        val totalRequired = entryValue + brokerage

        if (totalRequired > _balance.value) {
            return null // Insufficient funds
        }

        val trade = VirtualTrade(
            id = System.currentTimeMillis(),
            symbol = symbol,
            action = "BUY",
            entryPrice = entryPrice,
            quantity = quantity,
            stopLoss = stopLoss,
            target = target,
            confidence = confidence,
            reason = reason,
            entryTime = System.currentTimeMillis(),
            status = TradeStatus.OPEN,
            brokerage = brokerage
        )

        // Deduct from balance
        val newBalance = _balance.value - totalRequired
        _balance.value = newBalance
        saveBalance(newBalance)

        _openTrades.value = _openTrades.value + trade
        incrementTradeCount()

        notificationManager.sendTradeExecutedNotification(symbol, "BUY", entryPrice, quantity)

        return trade
    }

    /**
     * Execute a virtual SELL trade (paper trade only)
     */
    fun executeVirtualSell(
        symbol: String,
        entryPrice: Double,
        quantity: Int,
        stopLoss: Double,
        target: Double,
        confidence: Int,
        reason: String
    ): VirtualTrade? {
        // For short selling in virtual mode, we just track it
        val brokerage = ((entryPrice * quantity) * 0.0003).toLong().coerceAtLeast(20)

        val trade = VirtualTrade(
            id = System.currentTimeMillis(),
            symbol = symbol,
            action = "SELL",
            entryPrice = entryPrice,
            quantity = quantity,
            stopLoss = stopLoss,
            target = target,
            confidence = confidence,
            reason = reason,
            entryTime = System.currentTimeMillis(),
            status = TradeStatus.OPEN,
            brokerage = brokerage
        )

        _openTrades.value = _openTrades.value + trade
        incrementTradeCount()

        notificationManager.sendTradeExecutedNotification(symbol, "SELL", entryPrice, quantity)

        return trade
    }

    /**
     * Close a virtual trade at given exit price
     */
    fun closeTrade(tradeId: Long, exitPrice: Double) {
        val trade = _openTrades.value.find { it.id == tradeId } ?: return

        val exitValue = (exitPrice * trade.quantity).toLong()
        val entryValue = (trade.entryPrice * trade.quantity).toLong()
        val exitBrokerage = (exitValue * 0.0003).toLong().coerceAtLeast(20)
        val totalBrokerage = trade.brokerage + exitBrokerage

        val pnl = when (trade.action) {
            "BUY" -> exitValue - entryValue - totalBrokerage
            "SELL" -> entryValue - exitValue - totalBrokerage
            else -> 0L
        }

        val closedTrade = trade.copy(
            exitPrice = exitPrice,
            exitTime = System.currentTimeMillis(),
            status = if (pnl >= 0) TradeStatus.PROFIT else TradeStatus.LOSS,
            pnl = pnl,
            exitBrokerage = exitBrokerage
        )

        // For BUY trades, return the capital + P&L
        if (trade.action == "BUY") {
            val newBalance = _balance.value + exitValue - exitBrokerage
            _balance.value = newBalance
            saveBalance(newBalance)
        }

        _openTrades.value = _openTrades.value.filter { it.id != tradeId }
        _tradeHistory.value = (listOf(closedTrade) + _tradeHistory.value).take(500)

        val newTotalPnL = _totalPnL.value + pnl
        _totalPnL.value = newTotalPnL
        prefs.edit { putLong(KEY_TOTAL_PNL, newTotalPnL) }

        notificationManager.sendTradeClosedNotification(trade.symbol, pnl, closedTrade.status.name)
    }

    /**
     * Check SL/Target for open trades and auto-close if hit
     */
    fun checkAndUpdateTrades(currentPrice: Double, symbol: String) {
        val tradesToCheck = _openTrades.value.filter { it.symbol == symbol }

        tradesToCheck.forEach { trade ->
            val shouldClose = when (trade.action) {
                "BUY" -> currentPrice <= trade.stopLoss || currentPrice >= trade.target
                "SELL" -> currentPrice >= trade.stopLoss || currentPrice <= trade.target
                else -> false
            }

            if (shouldClose) {
                val exitPrice = when {
                    trade.action == "BUY" && currentPrice <= trade.stopLoss -> trade.stopLoss
                    trade.action == "BUY" && currentPrice >= trade.target -> trade.target
                    trade.action == "SELL" && currentPrice >= trade.stopLoss -> trade.stopLoss
                    trade.action == "SELL" && currentPrice <= trade.target -> trade.target
                    else -> currentPrice
                }
                closeTrade(trade.id, exitPrice)
            }
        }
    }

    /**
     * Emergency exit all open trades
     */
    fun emergencyExitAll(currentPriceMap: Map<String, Double>) {
        _openTrades.value.forEach { trade ->
            val currentPrice = currentPriceMap[trade.symbol] ?: trade.entryPrice
            closeTrade(trade.id, currentPrice)
        }
    }

    private fun incrementTradeCount() {
        val newCount = _tradeCount.value + 1
        _tradeCount.value = newCount
        prefs.edit { putInt(KEY_TRADE_COUNT, newCount) }
    }

    fun getWinRate(): Float {
        val closed = _tradeHistory.value
        if (closed.isEmpty()) return 0f
        val wins = closed.count { it.status == TradeStatus.PROFIT }
        return (wins.toFloat() / closed.size) * 100f
    }

    fun getTodayPnL(): Long {
        val today = System.currentTimeMillis()
        val startOfDay = today - (today % (24 * 60 * 60 * 1000))
        return _tradeHistory.value
            .filter { it.exitTime >= startOfDay }
            .sumOf { it.pnl }
    }

    fun clearHistory() {
        _tradeHistory.value = emptyList()
        _totalPnL.value = 0L
        _tradeCount.value = 0
        prefs.edit { 
            putLong(KEY_TOTAL_PNL, 0L)
            putInt(KEY_TRADE_COUNT, 0)
        }
    }
}

// ===== DATA CLASSES =====

data class VirtualTrade(
    val id: Long,
    val symbol: String,
    val action: String, // BUY or SELL
    val entryPrice: Double,
    val quantity: Int,
    val stopLoss: Double,
    val target: Double,
    val confidence: Int,
    val reason: String,
    val entryTime: Long,
    val exitPrice: Double = 0.0,
    val exitTime: Long = 0L,
    val status: TradeStatus = TradeStatus.OPEN,
    val pnl: Long = 0L,
    val brokerage: Long = 0L,
    val exitBrokerage: Long = 0L
) {
    val isOpen: Boolean get() = status == TradeStatus.OPEN
    val totalBrokerage: Long get() = brokerage + exitBrokerage
    val entryValue: Long get() = (entryPrice * quantity).toLong()
}

enum class TradeStatus {
    OPEN, PROFIT, LOSS
}
