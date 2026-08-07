package com.vedx.vedxsuper.core.risk

import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * V4 RiskEngine
 * Single Source of Truth for risk and position sizing.
 */
class RiskEngine(
    private val limits: RiskLimits = RiskLimits(),
    private val riskDao: RiskDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val mutex = Mutex()
    private var realizedPnL = 0.0
    private var unrealizedPnL = 0.0
    private var tradesToday = 0
    private var winCount = 0
    private var lossCount = 0
    private var peakPnL = 0.0
    private var consecutiveLosses = 0
    private var isCircuitBroken = false
    private var isEmergencyStopped = false

    private val _status = MutableStateFlow("✅ ACTIVE")
    val status = _status.asStateFlow()

    private val _exposure = MutableStateFlow(0.0)
    val exposure = _exposure.asStateFlow()

    private val _livePnL = MutableStateFlow(0.0)
    val livePnL = _livePnL.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                val saved = riskDao?.get()
                if (saved != null) {
                    val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
                    val last = saved.lastUpdate / (24 * 60 * 60 * 1000)
                    if (today == last) {
                        realizedPnL = saved.dailyRealizedPnL
                        tradesToday = saved.totalTradesToday
                        winCount = saved.winningTrades
                        lossCount = saved.losingTrades
                        peakPnL = saved.peakPnL
                        consecutiveLosses = saved.consecutiveLosses
                        isCircuitBroken = saved.isCircuitBroken
                        _livePnL.value = realizedPnL
                        updateStatusText()
                    }
                }
            }
        }
    }

    private suspend fun persist() {
        riskDao?.save(DbRiskState(
            dailyRealizedPnL = realizedPnL,
            totalTradesToday = tradesToday,
            winningTrades = winCount,
            losingTrades = lossCount,
            peakPnL = peakPnL,
            consecutiveLosses = consecutiveLosses,
            isCircuitBroken = isCircuitBroken,
            lastUpdate = System.currentTimeMillis()
        ))
    }

    suspend fun validateAndCreatePlan(
        signal: Signal, 
        availableMargin: Double, 
        lotSize: Int, 
        marginPerLot: Double,
        isAlreadyOpen: Boolean,
        riskPct: Double = 0.02
    ): TradePlan = mutex.withLock {
        // Audit 4.6: Reject if position already open
        if (isAlreadyOpen) return TradePlan(false, signal.symbol.value, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, rejectionReason = "POSITION_ALREADY_OPEN")
        
        if (isCircuitBroken || isEmergencyStopped) return TradePlan(false, signal.symbol.value, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, rejectionReason = "CIRCUIT_BROKEN")
        if (tradesToday >= limits.maxTradesPerDay) return TradePlan(false, signal.symbol.value, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, rejectionReason = "MAX_TRADES_REACHED")
        
        val entry = signal.entryPrice.rupees
        val sl = signal.stopLoss.rupees
        val riskPerUnit = abs(entry - sl)
        
        if (riskPerUnit <= 0) return TradePlan(false, signal.symbol.value, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, rejectionReason = "INVALID_SL")

        // Position Sizing Logic (Audit 4.3)
        val maxRisk = availableMargin * riskPct
        val lotsByRisk = (maxRisk / (riskPerUnit * lotSize)).toInt()
        val lotsByMargin = (availableMargin * 0.8 / marginPerLot).toInt() // Max 80% margin for virtual
        
        val finalLots = minOf(lotsByRisk, lotsByMargin, 20).coerceAtLeast(0)
        
        if (finalLots <= 0) return TradePlan(false, signal.symbol.value, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, rejectionReason = "INSUFFICIENT_MARGIN_OR_RISK_TOO_HIGH")

        val quantity = finalLots * lotSize
        val marginRequired = finalLots * marginPerLot
        val totalRisk = riskPerUnit * quantity
        
        // Audit 4.10: Estimate Charges
        val charges = estimateCharges(entry, quantity)

        return TradePlan(
            approved = true,
            symbol = signal.symbol.value,
            quantity = quantity,
            lots = finalLots,
            entryPrice = entry,
            stopLoss = sl,
            target = signal.target.rupees,
            trailingSl = signal.trailingSl?.rupees ?: (entry - riskPerUnit * 0.5),
            marginRequired = marginRequired,
            riskAmount = totalRisk,
            charges = charges
        )
    }

    private fun estimateCharges(price: Double, qty: Int): TradeCharges {
        val turnover = price * qty
        val brokerage = 20.0 // Flat
        val stt = turnover * 0.00125 // Approx for Options
        val exch = turnover * 0.0005
        val gst = (brokerage + exch) * 0.18
        return TradeCharges(brokerage, stt, exch, gst, 0.0, brokerage + stt + exch + gst)
    }

    suspend fun onEntry(valCents: Int) = mutex.withLock { _exposure.value += valCents / 100.0 }

    suspend fun updateExposure(openPositions: List<OpenPosition>) = mutex.withLock {
        _exposure.value = openPositions.sumOf { it.entryPrice.rupees * it.quantity }
    }

    suspend fun onTradeOutcome(pnl: Double) = mutex.withLock {
        realizedPnL += pnl
        tradesToday++
        if (pnl > 0) { winCount++; consecutiveLosses = 0 } else { lossCount++; consecutiveLosses++ }
        if (realizedPnL > peakPnL) peakPnL = realizedPnL
        _livePnL.value = realizedPnL + unrealizedPnL
        if (realizedPnL <= -limits.dailyLossLimit) triggerCB()
        updateStatusText()
        persist()
    }

    suspend fun updateUnrealized(pnl: Double) = mutex.withLock {
        unrealizedPnL = pnl
        _livePnL.value = realizedPnL + unrealizedPnL
        if (_livePnL.value <= -limits.dailyLossLimit) triggerCB()
    }

    private fun triggerCB() { isCircuitBroken = true; _status.value = "🛑 CIRCUIT BREAKER"; scope.launch { persist() } }
    private fun updateStatusText() { _status.value = if (consecutiveLosses >= 3) "⚠️ LEARNING PAUSE" else "✅ ACTIVE" }
    
    fun getStats() = RiskStats(realizedPnL, tradesToday, if (tradesToday > 0) (winCount.toDouble()/tradesToday*100) else 0.0, !isCircuitBroken && !isEmergencyStopped)

    data class RiskStats(val pnl: Double, val trades: Int, val winRate: Double, val isActive: Boolean)
}
