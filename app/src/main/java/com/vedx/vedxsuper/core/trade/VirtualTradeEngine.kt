package com.vedx.vedxsuper.core.trade

import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import com.vedx.vedxsuper.core.risk.RiskEngine
import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.flow.collect
import kotlin.math.abs

import kotlinx.coroutines.*
import java.util.UUID

/**
 * V4 VirtualTradeEngine
 * executes TradePlans from RiskEngine (Audit 4.4)
 */
class VirtualTradeEngine(
    private val portfolio: PortfolioEngine,
    private val riskEngine: RiskEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onTradeClosed: (VirtualTrade, Long, TradeStatus) -> Unit = { _, _, _ -> }
) {
    suspend fun executeSignal(signal: Signal, riskPct: Double = 0.02) {
        val lotSize = getLotSize(signal.symbol.value)
        val marginPerLot = signal.entryPrice.rupees * lotSize * 0.15 // 15% margin
        val isAlreadyOpen = portfolio.openTrades.value.any { it.symbol == signal.symbol.value }

        // 1. Get TradePlan from RiskEngine (Audit 4.12)
        val plan = riskEngine.validateAndCreatePlan(
            signal, portfolio.balance.value, lotSize, marginPerLot, isAlreadyOpen, riskPct
        )
        
        if (!plan.approved) {
            com.vedx.vedxsuper.utils.VedxLogger.i("RISK_REJECT: ${plan.symbol} -> ${plan.rejectionReason}")
            return
        }

        // 2. Execute Plan
        val trade = VirtualTrade(
            id = UUID.randomUUID().toString(), // Audit 4.9
            symbol = plan.symbol,
            action = signal.action.name,
            entryPrice = plan.entryPrice,
            quantity = plan.quantity,
            stopLoss = plan.stopLoss,
            target = plan.target,
            confidence = signal.confidence.pct,
            reason = signal.reason,
            matchedBand = signal.matchedBand,
            entryTime = System.currentTimeMillis(),
            charges = plan.charges.total
        )
        
        portfolio.addTrade(trade, plan.marginRequired)
        riskEngine.onEntry((plan.marginRequired * 100).toInt())
    }

    fun updateTrades(symbol: String, price: Double) {
        val open = portfolio.openTrades.value.filter { it.symbol == symbol }
        open.forEach { trade ->
            // Audit 4.7: Continuous Trailing SL Update
            updateTrailingSL(trade, price)

            val hitSL = if (trade.action == "BUY") price <= trade.stopLoss else price >= trade.stopLoss
            val hitTarget = if (trade.action == "BUY") price >= trade.target else price <= trade.target
            
            if (hitSL || hitTarget) {
                val pnl = if (trade.action == "BUY") (price - trade.entryPrice) * trade.quantity else (trade.entryPrice - price) * trade.quantity
                val charges = estimateTotalCharges(trade, price)
                val status = if (pnl >= charges) TradeStatus.PROFIT else TradeStatus.LOSS
                
                portfolio.closeTrade(trade.id, pnl, trade.entryPrice * trade.quantity * 0.15, charges, status)
                onTradeClosed(trade, pnl.toLong(), status)
                
                scope.launch {
                    riskEngine.onTradeOutcome(pnl - charges)
                }
            }
        }
    }

    private fun updateTrailingSL(trade: VirtualTrade, currentPrice: Double) {
        if (trade.action == "BUY") {
            // If price moves up, move SL up (keep 1% distance or as per strategy)
            val newSL = currentPrice * 0.99
            if (newSL > trade.stopLoss) {
                portfolio.updateTrailingSL(trade.id, newSL)
            }
        } else {
            val newSL = currentPrice * 1.01
            if (newSL < trade.stopLoss) {
                portfolio.updateTrailingSL(trade.id, newSL)
            }
        }
    }

    private fun estimateTotalCharges(trade: VirtualTrade, exitPrice: Double): Double {
        val entryVal = trade.entryPrice * trade.quantity
        val exitVal = exitPrice * trade.quantity
        val totalTurnover = entryVal + exitVal
        
        val brokerage = 40.0 // 20 buy + 20 sell
        val stt = exitVal * 0.00125 // STT on sell for options
        val exch = totalTurnover * 0.0005
        val gst = (brokerage + exch) * 0.18
        return brokerage + stt + exch + gst
    }

    private fun getLotSize(s: String) = when {
        s.contains("BANKNIFTY") -> 30
        s.contains("NIFTY") -> 75
        s.contains("FINNIFTY") -> 40
        s.contains("MIDCPNIFTY") -> 75
        else -> 25
    }

    fun closeAllTrades() {
        portfolio.openTrades.value.forEach { trade ->
            // Use entryPrice as exit price for emergency exit (0 PnL minus charges)
            val pnl = 0.0
            val charges = estimateTotalCharges(trade, trade.entryPrice)
            portfolio.closeTrade(trade.id, pnl, trade.entryPrice * trade.quantity * 0.15, charges, TradeStatus.LOSS)
            onTradeClosed(trade, pnl.toLong(), TradeStatus.LOSS)
        }
    }
}
