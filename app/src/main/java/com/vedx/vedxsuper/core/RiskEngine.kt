package com.vedx.vedxsuper.core

import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * ============================================================
 * RISK ENGINE V3 — INSTITUTIONAL GRADE
 * ============================================================
 * Updates:
 * 1. Thread safety via Mutex.
 * 2. Dynamic risk calculation based on margin %.
 * 3. Exposure check including current trade value.
 * 4. Unrealized PnL tracking for live risk monitoring.
 * 5. Trade rejection for zero-quantity sizing.
 * 6. PERSISTENCE: Recovery from crashes.
 */
class RiskEngine(
    private val limits: RiskLimits = RiskLimits(),
    private val riskDao: RiskDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val mutex = Mutex()

    private var dailyRealizedPnL = 0.0
    private var dailyUnrealizedPnL = 0.0
    private var totalTradesToday = 0
    private var winningTrades = 0
    private var losingTrades = 0
    private var maxDrawdownToday = 0.0
    private var peakPnL = 0.0
    private var consecutiveLosses = 0
    private var isCircuitBroken = false
    private var isEmergencyStopped = false

    private val _status = MutableStateFlow("✅ ACTIVE")
    val status = _status.asStateFlow()

    private val _exposure = MutableStateFlow(0.0)
    val exposure = _exposure.asStateFlow()

    private val _livePnL = MutableStateFlow(0.0) // Realized + Unrealized
    val livePnL = _livePnL.asStateFlow()

    init {
        // Load persisted state if available
        scope.launch {
            mutex.withLock {
                val saved = riskDao?.get()
                if (saved != null) {
                    // Check if it's from today
                    val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
                    val last = saved.lastUpdate / (24 * 60 * 60 * 1000)
                    
                    if (today == last) {
                        dailyRealizedPnL = saved.dailyRealizedPnL
                        totalTradesToday = saved.totalTradesToday
                        winningTrades = saved.winningTrades
                        losingTrades = saved.losingTrades
                        peakPnL = saved.peakPnL
                        consecutiveLosses = saved.consecutiveLosses
                        isCircuitBroken = saved.isCircuitBroken
                        _livePnL.value = dailyRealizedPnL
                        updateStatus()
                    }
                }
            }
        }
    }

    private suspend fun persist() {
        riskDao?.save(DbRiskState(
            dailyRealizedPnL = dailyRealizedPnL,
            totalTradesToday = totalTradesToday,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            peakPnL = peakPnL,
            consecutiveLosses = consecutiveLosses,
            isCircuitBroken = isCircuitBroken,
            lastUpdate = System.currentTimeMillis()
        ))
    }

    // ===== TRADE VALIDATION =====
    suspend fun canTrade(riskAmountCents: Int, entryValueCents: Int, availableMargin: Double = 500000.0): Boolean = mutex.withLock {
        if (isCircuitBroken || isEmergencyStopped) {
            _status.value = "🛑 STOPPED"
            return false
        }
        if (totalTradesToday >= limits.maxTradesPerDay) {
            _status.value = "⛔ MAX TRADES"
            return false
        }
        
        val dynamicMaxRisk = (availableMargin * 0.01).coerceAtMost(5000.0)
        
        val riskAmount = riskAmountCents / 100.0
        val entryValue = entryValueCents / 100.0
        val currentTotalExposure = _exposure.value

        if ((currentTotalExposure + entryValue) > availableMargin * 0.8) {
            _status.value = "⛔ EXPOSURE LIMIT"
            return false
        }

        if (riskAmount > dynamicMaxRisk) {
            return false
        }
        
        if (dailyRealizedPnL + dailyUnrealizedPnL <= -limits.dailyLossLimit) {
            _status.value = "⛔ MAX LOSS"
            triggerCircuitBreaker()
            return false
        }

        if (consecutiveLosses >= 3) {
            _status.value = "⚠️ LEARNING PAUSE"
            return false
        }
        return true
    }
    
    suspend fun onEntry(entryValueCents: Int) = mutex.withLock {
        _exposure.value += entryValueCents / 100.0
    }

    // ===== POSITION SIZING (Lot Aware) =====
    fun calculatePositionSize(
        entry: Double,
        stopLoss: Double,
        lotSize: Int,
        availableMargin: Double,
        marginPerLot: Double
    ): PositionSize {
        val riskPerUnit = abs(entry - stopLoss)
        if (riskPerUnit <= 0) return PositionSize(0, lotSize, 0, 0.0, 0.0, 0.0)

        val maxRisk = (availableMargin * 0.01).coerceAtMost(5000.0)
        val rawQty = (maxRisk / riskPerUnit).toInt()
        val lots = (rawQty / lotSize).coerceAtLeast(0)

        val maxLotsByMargin = (availableMargin * 0.4 / marginPerLot).toInt()
        val finalLots = lots.coerceAtMost(maxLotsByMargin).coerceAtMost(20)
        
        if (finalLots <= 0) {
            return PositionSize(0, lotSize, 0, 0.0, 0.0, riskPerUnit)
        }

        val finalQty = finalLots * lotSize
        val finalMargin = marginPerLot * finalLots
        val finalRisk = riskPerUnit * finalQty

        return PositionSize(
            quantity = finalQty, lotSize = lotSize, lots = finalLots,
            marginRequired = finalMargin, riskAmount = finalRisk, riskPerUnit = riskPerUnit
        )
    }

    // ===== LIVE P&L TRACKING =====
    suspend fun updateUnrealizedPnL(unrealized: Double) = mutex.withLock {
        dailyUnrealizedPnL = unrealized
        _livePnL.value = dailyRealizedPnL + dailyUnrealizedPnL
        
        if (_livePnL.value <= -limits.dailyLossLimit) {
            triggerCircuitBreaker()
        }
    }

    // ===== TRADE OUTCOME TRACKING =====
    suspend fun onTradeOutcome(pnl: Double) = mutex.withLock {
        dailyRealizedPnL += pnl
        totalTradesToday++

        if (pnl > 0) {
            winningTrades++
            consecutiveLosses = 0
        } else {
            losingTrades++
            consecutiveLosses++
        }

        if (dailyRealizedPnL > peakPnL) peakPnL = dailyRealizedPnL
        val dd = peakPnL - dailyRealizedPnL
        if (dd > maxDrawdownToday) maxDrawdownToday = dd

        _livePnL.value = dailyRealizedPnL + dailyUnrealizedPnL
        
        checkCircuitBreakers()
        updateStatus()
        persist() // Auto-save
    }

    private fun updateStatus() {
        if (!isCircuitBroken && !isEmergencyStopped) {
            _status.value = when {
                consecutiveLosses >= 3 -> "⚠️ LEARNING PAUSE"
                else -> "✅ ACTIVE"
            }
        }
    }

    private fun checkCircuitBreakers() {
        if (dailyRealizedPnL <= -limits.dailyLossLimit) {
            triggerCircuitBreaker()
            return
        }
        if (totalTradesToday <= limits.circuitBreakerTrades && 
            dailyRealizedPnL <= -limits.circuitBreakerLoss) {
            triggerCircuitBreaker()
            return
        }
    }

    private fun triggerCircuitBreaker() {
        isCircuitBroken = true
        _status.value = "🛑 CIRCUIT BREAKER"
        scope.launch { persist() }
    }

    fun emergencyStop() {
        isEmergencyStopped = true
        isCircuitBroken = true
        _status.value = "🚨 EMERGENCY STOP"
        scope.launch { persist() }
    }

    suspend fun updateExposure(openPositions: List<OpenPosition>) = mutex.withLock {
        val total = openPositions.sumOf { it.entryPrice.rupees * it.quantity }
        _exposure.value = total
    }

    suspend fun resetDaily() = mutex.withLock {
        dailyRealizedPnL = 0.0
        dailyUnrealizedPnL = 0.0
        totalTradesToday = 0
        winningTrades = 0
        losingTrades = 0
        maxDrawdownToday = 0.0
        peakPnL = 0.0
        consecutiveLosses = 0
        isCircuitBroken = false
        isEmergencyStopped = false
        _status.value = "✅ ACTIVE"
        _livePnL.value = 0.0
        persist()
    }

    fun getStats(): RiskStats {
        val winRate = if (totalTradesToday > 0) (winningTrades.toDouble() / totalTradesToday * 100) else 0.0
        return RiskStats(
            dailyPnL = dailyRealizedPnL,
            totalTrades = totalTradesToday,
            winRate = winRate,
            maxDrawdown = maxDrawdownToday,
            consecutiveLosses = consecutiveLosses,
            isActive = !isCircuitBroken && !isEmergencyStopped
        )
    }

    data class RiskStats(
        val dailyPnL: Double, val totalTrades: Int, val winRate: Double,
        val maxDrawdown: Double, val consecutiveLosses: Int, val isActive: Boolean
    )
}
