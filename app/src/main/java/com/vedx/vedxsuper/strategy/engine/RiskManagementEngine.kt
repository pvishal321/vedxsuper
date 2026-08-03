package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.utils.StrategyUtils.safe
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.floor

data class RiskConfig(
    val maxRiskPerTradePercent: Double = 1.0,
    val maxDailyLossPercent: Double = 3.0,
    val dailyProfitTargetPercent: Double = 5.0,
    val maxConsecutiveLosses: Int = 5,
    val maxPortfolioExposurePercent: Double = 80.0,
    val minRiskRewardRatio: Double = 1.5,
    val emergencyStopEnabled: Boolean = true
)

data class AccountState(
    var balance: Double = 10000000.0,
    var initialBalance: Double = 10000000.0,
    var dailyPnL: Double = 0.0,
    var consecutiveLosses: Int = 0,
    var activeExposure: Double = 0.0,
    var isTradingLocked: Boolean = false,
    var lockReason: String = ""
)

data class PositionSizeResult(
    val quantity: Int,
    val riskAmount: Double,
    val isBlocked: Boolean,
    val reason: String = ""
)

data class PartialExitResult(
    val quantityToExit: Int,
    val reason: String
)

class RiskManagementEngine(initialConfig: RiskConfig = RiskConfig()) {
    private var config = initialConfig
    private val state = AtomicReference(AccountState())
    private val lock = Any()
    
    fun updateConfig(newConfig: RiskConfig) {
        synchronized(lock) {
            this.config = newConfig
        }
    }
    
    private var balanceProvider: (() -> Double)? = null

    private companion object {
        const val CONFIDENCE_HIGH = 90
        const val CONFIDENCE_MID = 70
        const val CONFIDENCE_LOW = 60
        
        const val RISK_MULTIPLIER_HIGH = 1.5
        const val RISK_MULTIPLIER_LOW = 0.5
        
        const val EPSILON = 0.000001
        const val PARTIAL_EXIT_QTY_FACTOR = 0.25
    }

    fun setBalanceProvider(provider: () -> Double) {
        this.balanceProvider = provider
    }

    private fun getCurrentBalance(): Double {
        return (balanceProvider?.invoke() ?: state.get().balance).safe()
    }

    private val activeTradeExits = mutableMapOf<String, Int>()

    fun calculatePartialExit(
        symbol: String,
        ltp: Double,
        entryPrice: Double,
        totalQuantity: Int,
        targets: List<Double>
    ): PartialExitResult? {
        val phase = activeTradeExits.getOrDefault(symbol, 0)
        if (phase >= 3) return null 

        val currentTarget = targets.getOrNull(phase) ?: return null
        
        val isTargetMet = if (ltp > entryPrice) ltp >= currentTarget - EPSILON else ltp <= currentTarget + EPSILON
        
        if (isTargetMet) {
            val exitQty = (totalQuantity * PARTIAL_EXIT_QTY_FACTOR).toInt()
            activeTradeExits[symbol] = phase + 1
            return PartialExitResult(exitQty, "Target ${phase + 1} Reached")
        }
        
        return null
    }

    fun calculateTrailingStop(
        symbol: String,
        currentLtp: Double,
        currentSL: Double,
        stBands: Map<Int, Double>,
        trend: Int
    ): Double {
        var newSL = currentSL
        stBands.values.forEach { bandPrice ->
            if (trend == 1) {
                if (bandPrice > newSL + EPSILON && bandPrice < currentLtp - EPSILON) {
                    newSL = bandPrice
                }
            } else {
                if (bandPrice < newSL - EPSILON && bandPrice > currentLtp + EPSILON) {
                    newSL = bandPrice
                }
            }
        }
        return newSL.safe()
    }

    fun calculatePositionSize(
        ltp: Double,
        stopLoss: Double,
        confidence: Int,
        lotSize: Int = 50
    ): PositionSizeResult {
        val currentState = state.get()
        val currentBalance = getCurrentBalance()
        
        if (currentState.isTradingLocked) {
            return PositionSizeResult(0, 0.0, true, "Trading Locked: ${currentState.lockReason}")
        }

        val slDistance = abs(ltp - stopLoss)
        if (slDistance <= EPSILON) {
            return PositionSizeResult(0, 0.0, true, "Invalid Stop Loss distance")
        }

        val baseRiskPercent = config.maxRiskPerTradePercent
        val dynamicRiskPercent = when {
            confidence >= CONFIDENCE_HIGH -> baseRiskPercent * RISK_MULTIPLIER_HIGH
            confidence >= CONFIDENCE_MID -> baseRiskPercent
            confidence >= CONFIDENCE_LOW -> baseRiskPercent * RISK_MULTIPLIER_LOW
            else -> 0.0
        }

        if (dynamicRiskPercent <= EPSILON) {
            return PositionSizeResult(0, 0.0, true, "Confidence too low for entry")
        }

        val riskAmount = currentBalance * (dynamicRiskPercent / 100.0)
        
        val rawQuantity = (riskAmount / slDistance).toInt()
        val lots = floor(rawQuantity.toDouble() / lotSize).toInt()
        val finalQuantity = lots * lotSize

        if (finalQuantity <= 0) {
            return PositionSizeResult(0, 0.0, true, "Insufficient capital for minimum lot size")
        }

        val tradeExposure = finalQuantity * ltp
        val maxAllowedExposure = currentBalance * config.maxPortfolioExposurePercent / 100.0
        if ((currentState.activeExposure + tradeExposure) > maxAllowedExposure) {
            return PositionSizeResult(0, 0.0, true, "Portfolio Exposure limit exceeded")
        }

        return PositionSizeResult(finalQuantity, (finalQuantity * slDistance).safe(), false)
    }

    fun preTradeCheck(regime: MarketRegime, structure: MarketStructure): String? {
        val s = state.get()
        
        if (s.isTradingLocked) return "Trading Locked: ${s.lockReason}"
        
        if (s.consecutiveLosses >= config.maxConsecutiveLosses) {
            lockTrading("Max Consecutive Losses Reached")
            return "Max Consecutive Losses Reached"
        }

        val dailyLossPercent = if (s.initialBalance > EPSILON) (s.dailyPnL / s.initialBalance * 100.0) else 0.0
        if (dailyLossPercent <= -config.maxDailyLossPercent) {
            lockTrading("Max Daily Loss Reached")
            return "Max Daily Loss Reached"
        }

        if (dailyLossPercent >= config.dailyProfitTargetPercent) {
            lockTrading("Daily Profit Target Reached - Locking Profit")
            return "Daily Profit Target Reached"
        }

        return null
    }

    fun updateOnTradeEntry(exposure: Double) {
        synchronized(lock) {
            val current = state.get()
            state.set(current.copy(activeExposure = (current.activeExposure + exposure).safe()))
        }
    }

    fun updateOnTradeExit(pnl: Double, exposure: Double) {
        synchronized(lock) {
            val current = state.get()
            val newBalance = (current.balance + pnl).safe()
            val newDailyPnL = (current.dailyPnL + pnl).safe()
            val newConsecutiveLosses = if (pnl < 0) current.consecutiveLosses + 1 else 0
            
            state.set(current.copy(
                balance = newBalance,
                dailyPnL = newDailyPnL,
                consecutiveLosses = newConsecutiveLosses,
                activeExposure = (current.activeExposure - exposure).coerceAtLeast(0.0).safe()
            ))
        }
    }

    fun lockTrading(reason: String) {
        synchronized(lock) {
            val current = state.get()
            state.set(current.copy(isTradingLocked = true, lockReason = reason))
        }
    }

    fun emergencyStop() {
        lockTrading("Emergency Stop Triggered")
    }

    fun resetDaily() {
        synchronized(lock) {
            val current = state.get()
            state.set(current.copy(
                initialBalance = current.balance,
                dailyPnL = 0.0,
                consecutiveLosses = 0,
                isTradingLocked = false,
                lockReason = ""
            ))
        }
    }

    fun getState(): AccountState {
        val s = state.get().copy()
        s.balance = getCurrentBalance()
        return s
    }

    /**
     * Future Architecture: Option Selling Risk Management
     */
    fun calculateSellingMargin(
        strikePrice: Double,
        indexPrice: Double,
        lotSize: Int
    ): Double {
        // Simplified: ~15-20% of strike value
        return strikePrice * lotSize * 0.18
    }

    fun calculateSellingSL(
        entryPremium: Double,
        marginUsed: Double
    ): Double {
        // Selling SL: 20-30% of margin (not premium!)
        return marginUsed * 0.25
    }

    fun calculateSellingTarget(
        entryPremium: Double,
        daysToExpiry: Int
    ): Double {
        // Target: 50% of premium collected (theta decay)
        return entryPremium * 0.5
    }
}
