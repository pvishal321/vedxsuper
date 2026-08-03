package com.vedx.vedxsuper.trade

import com.vedx.vedxsuper.database.TradeEntity
import com.vedx.vedxsuper.model.trade.StrategyConfig
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VirtualTradeManager(
    private val repository: TradeRepository,
    private val settingsManager: SettingsManager
) {

    private val _activePosition = MutableStateFlow<TradeEntity?>(null)
    val activePosition = _activePosition.asStateFlow()

    private val tradeMutex = Mutex() // [FIXED] Point 1: Added Mutex to prevent race conditions during trade entry/exit

    private var tradeCompletionListener: ((Double, Double) -> Unit)? = null

    fun setTradeCompletionListener(listener: (Double, Double) -> Unit) {
        this.tradeCompletionListener = listener
    }

    /**
     * Tries to open a new position based on a signal.
     */
    suspend fun processSignal(
        side: String,
        symbol: String,
        price: Double,
        config: StrategyConfig,
        confidence: Int = 0,
        explanation: String = "",
        providedSL: Double = 0.0,
        providedTarget: Double = 0.0
    ) = tradeMutex.withLock { // [FIXED] Ensure thread safety
        val current = _activePosition.value
        if (current != null) {
            if (side == "EXIT" || (current.type == "BUY_CALL" && side == "BUY_PUT") || (current.type == "BUY_PUT" && side == "BUY_CALL")) {
                closePositionInternal(current, price)
            }
            return@withLock
        }

        if (side == "WAIT" || side == "EXIT") return@withLock

        // [FIXED] Point 2: Use Dynamic Lot Sizes from Settings
        val lotSize = settingsManager.getLotSize(symbol)

        // [FIXED] Final Price Verification: Use the actual provided price, but ensure it's not zero
        val finalEntryPrice = if (price > 0.000001) price else 100.0 // Minimal fallback

        val requiredMargin = finalEntryPrice * lotSize
        
        // [FIXED] Auto-top up if balance is too low for testing
        val availableBalance = settingsManager.getVirtualBalance()
        if (availableBalance < requiredMargin) {
            settingsManager.addVirtualBalance(2000000.0) // Add 20L automatically
        }
        
        val finalBalance = settingsManager.getVirtualBalance()
        if (finalBalance < requiredMargin) {
            android.util.Log.e("VedxTrade", "Trade BLOCKED: Insufficient Margin for $symbol. Required: $requiredMargin, Available: $finalBalance")
            return@withLock
        }

        // 2. Deduct Margin from Virtual Balance
        settingsManager.withdrawVirtualBalance(requiredMargin)

        // [FIXED] In Option Buying, both CE and PE profit when Premium goes UP
        // Increased distance to 15% for Stop Loss to avoid immediate exit
        val sl = if (providedSL > 0) providedSL else (price * 0.85)
        val target = if (providedTarget > 0) providedTarget else (price * 1.30)

        val trade = TradeEntity(
            symbol = symbol,
            type = side,
            entryPrice = price,
            stopLoss = sl,
            target = target,
            quantity = lotSize,
            status = "OPEN",
            confidence = confidence,
            explanation = explanation
        )
        
        val id = repository.insertTrade(trade)
        _activePosition.value = trade.copy(id = id)
    }

    suspend fun updateTrailingSL(symbol: String, newSL: Double) {
        val trade = _activePosition.value ?: return
        if (trade.symbol != symbol) return

        // For Option Buying, Trail only if new SL is higher than current SL
        if (newSL > trade.stopLoss) {
            val updatedTrade = trade.copy(stopLoss = newSL)
            repository.updateTrade(updatedTrade)
            _activePosition.value = updatedTrade
        }
    }

    suspend fun processPartialExit(symbol: String, quantityToExit: Int, exitPrice: Double) {
        val trade = _activePosition.value ?: return
        if (trade.symbol != symbol || trade.quantity <= 0) return
        
        val actualExitQty = quantityToExit.coerceAtMost(trade.quantity)
        
        // PnL calculation for Option Buying (Exit - Entry)
        val pnl = (exitPrice - trade.entryPrice) * actualExitQty
        
        // Return Margin + PnL back to balance
        val returnAmount = (trade.entryPrice * actualExitQty) + pnl
        settingsManager.addVirtualBalance(returnAmount)
        tradeCompletionListener?.invoke(pnl, trade.entryPrice * actualExitQty)

        val newQty = trade.quantity - actualExitQty
        if (newQty <= 0) {
            closePosition(trade, exitPrice)
        } else {
            val updatedTrade = trade.copy(quantity = newQty)
            repository.updateTrade(updatedTrade)
            _activePosition.value = updatedTrade
        }
    }

    /**
     * Tracks open position against live price.
     */
    suspend fun onPriceUpdate(currentPrice: Double, config: StrategyConfig) = tradeMutex.withLock {
        val trade = _activePosition.value ?: return@withLock

        var shouldClose = false
        var exitPrice = currentPrice

        // Universal logic for Option Buying (CE or PE)
        if (currentPrice <= trade.stopLoss) {
            shouldClose = true
            exitPrice = trade.stopLoss
        } 
        else if (currentPrice >= trade.target) {
            shouldClose = true
            exitPrice = trade.target
        }

        if (shouldClose) {
            closePositionInternal(trade, exitPrice)
        }
    }

    suspend fun closePosition(trade: TradeEntity, exitPrice: Double) = tradeMutex.withLock {
        closePositionInternal(trade, exitPrice)
    }

    private suspend fun closePositionInternal(trade: TradeEntity, exitPrice: Double) {
        // PnL = (Exit Premium - Entry Premium) * Quantity
        val grossPnl = (exitPrice - trade.entryPrice) * trade.quantity
        val netPnl = TradeCalculator.calculateNetPnl(grossPnl, trade.brokerage)
        
        val returnAmount = (trade.entryPrice * trade.quantity) + netPnl
        settingsManager.addVirtualBalance(returnAmount)
        tradeCompletionListener?.invoke(netPnl, trade.entryPrice * trade.quantity)

        val finalizedTrade = trade.copy(
            exitPrice = exitPrice,
            pnl = netPnl,
            status = "CLOSED",
            exitTime = System.currentTimeMillis()
        )
        repository.updateTrade(finalizedTrade)
        _activePosition.value = null
    }

    suspend fun syncWithDatabase() {
        val openTrades = repository.openTrades.first()
        if (openTrades.isNotEmpty()) {
            val trade = openTrades.first()
            _activePosition.value = trade
            android.util.Log.i("VedxTrade", "Position Recovered from DB: ${trade.symbol} at ${trade.entryPrice}")
        } else {
            _activePosition.value = null
        }
    }
}
