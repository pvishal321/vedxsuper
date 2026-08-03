package com.vedx.vedxsuper.strategy.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

import com.vedx.vedxsuper.model.market.*

/**
 * In-memory state tracking for active or potential trades.
 */
data class TradeLifecycleMemory(
    val state: MasterAction = MasterAction.WAIT,
    val entryPrice: Double = 0.0,
    val entryTime: Long = 0,
    val highestPrice: Double = 0.0,
    val lowestPrice: Double = Double.MAX_VALUE,
    val maxProfit: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val pullbackCount: Int = 0,
    val reEntryCount: Int = 0,
    val highestTarget: Int = 0,
    val exitReason: String = ""
)

/**
 * Result of the master decision processing.
 */
data class MasterDecision(
    val action: MasterAction,
    val reason: String,
    val confidence: Int,
    val recommendedStrike: OptionIntelligenceScore?,
    val timestamp: Long = System.currentTimeMillis()
)

class MasterDecisionEngine {
    private val _decision = MutableStateFlow<MasterDecision?>(null)
    val decision = _decision.asStateFlow()

    private val memory = AtomicReference(TradeLifecycleMemory())

    private companion object {
        const val MOMENTUM_SCALP_THRESHOLD = 60
        const val MIN_LIQUIDITY_SCORE = 40
    }

    /**
     * Processes various market metrics and decides on the final trade action.
     */
    fun process(
        regime: MarketRegime,
        state: TrendState,
        structure: MarketStructure,
        strength: StrengthMetrics,
        bestStrike: OptionIntelligenceScore?,
        confidenceScore: Int,
        correlationMatched: Boolean,
        riskLevel: com.vedx.vedxsuper.model.trade.RiskLevel = com.vedx.vedxsuper.model.trade.RiskLevel.MODERATE
    ): MasterDecision {
        var finalAction = MasterAction.WAIT
        val reasons = mutableListOf<String>()

        // 1. REJECT LOGIC (Hard Stops)
        val rejection = checkRejection(regime, structure, bestStrike, correlationMatched, confidenceScore, riskLevel)

        if (rejection != null) {
            finalAction = MasterAction.NO_TRADE
            reasons.add("REJECT: $rejection")
        } else {
            // 2. CORE DECISION LOGIC
            finalAction = determineAction(state, structure, strength)

            // 3. EXPLAINABLE AI OUTPUT
            reasons.add("Market = $regime")
            reasons.add("Structure = $structure")
            reasons.add("TrendState = $state")
            reasons.add("Final Confidence = $confidenceScore%")

            bestStrike?.let {
                reasons.add("Premium Potential = ${it.potentialScore.toInt()}")
                val rr = String.format(Locale.US, "1:%.1f", it.riskReward)
                reasons.add("Risk Reward = $rr")
                reasons.add("OI Action = ${it.oiAction}")
            }
        }

        val result = MasterDecision(
            action = finalAction,
            reason = reasons.joinToString(" | "),
            confidence = confidenceScore,
            recommendedStrike = bestStrike
        )

        _decision.value = result
        updateMemory(result)
        return result
    }

    private fun checkRejection(
        regime: MarketRegime,
        structure: MarketStructure,
        bestStrike: OptionIntelligenceScore?,
        correlationMatched: Boolean,
        confidence: Int,
        riskLevel: com.vedx.vedxsuper.model.trade.RiskLevel
    ): String? {
        if (regime == MarketRegime.NO_TRADE) return "No Trade Regime"
        if (structure == MarketStructure.FAKE_BREAKOUT) return "Fake Breakout Detected"
        if (structure == MarketStructure.LIQUIDITY_GRAB) return "Liquidity Grab - Waiting"

        // Dynamic Correlation Check based on Risk
        if (!correlationMatched && riskLevel != com.vedx.vedxsuper.model.trade.RiskLevel.AGGRESSIVE) {
            return "Weak Index-Option Correlation"
        }

        // Dynamic Confidence Check based on Risk
        val minConfidence = when(riskLevel) {
            com.vedx.vedxsuper.model.trade.RiskLevel.SAFE -> 85
            com.vedx.vedxsuper.model.trade.RiskLevel.MODERATE -> 75
            com.vedx.vedxsuper.model.trade.RiskLevel.AGGRESSIVE -> 65
        }
        if (confidence < minConfidence) return "Confidence too low for $riskLevel risk ($confidence% < $minConfidence%)"

        if (bestStrike == null) return "No Suitable Strike Found"
        if (bestStrike.liquidityScore < MIN_LIQUIDITY_SCORE) return "Poor Premium Liquidity"

        return null
    }

    private fun determineAction(
        state: TrendState,
        structure: MarketStructure,
        strength: StrengthMetrics
    ): MasterAction {
        return when (state) {
            TrendState.WAITING -> MasterAction.WAIT
            TrendState.BUILDING_TREND -> MasterAction.WATCH
            TrendState.REVERSAL_SETUP -> MasterAction.PREPARE
            TrendState.REVERSAL_CONFIRMED -> MasterAction.BUY

            TrendState.SCALP_READY -> {
                if (strength.momentum > MOMENTUM_SCALP_THRESHOLD || structure == MarketStructure.CONTINUATION) {
                    MasterAction.SCALP
                } else {
                    MasterAction.WATCH
                }
            }

            TrendState.RE_ENTRY_READY -> MasterAction.RE_ENTRY

            TrendState.TREND_RUNNING, TrendState.TARGET_RUNNING -> {
                if (strength.isExhausted) MasterAction.TRAIL else MasterAction.HOLD
            }
            TrendState.PULLBACK -> MasterAction.WATCH
            TrendState.TREND_FINISHED, TrendState.TREND_EXHAUSTION -> MasterAction.EXIT
            else -> MasterAction.WAIT
        }
    }

    private fun updateMemory(decision: MasterDecision) {
        while (true) {
            val oldMem = memory.get()
            val newMem = when (decision.action) {
                MasterAction.BUY, MasterAction.SELL, MasterAction.RE_ENTRY, MasterAction.SCALP -> {
                    if (oldMem.state == MasterAction.WAIT || oldMem.state == MasterAction.WATCH ||
                        oldMem.state == MasterAction.PREPARE || oldMem.state == MasterAction.NO_TRADE) {
                        oldMem.copy(
                            entryPrice = decision.recommendedStrike?.recommendedPrice ?: 0.0,
                            entryTime = decision.timestamp,
                            state = decision.action
                        )
                    } else oldMem
                }
                MasterAction.EXIT -> {
                    oldMem.copy(
                        exitReason = decision.reason,
                        state = MasterAction.EXIT
                    )
                }
                else -> {
                    oldMem.copy(state = decision.action)
                }
            }
            if (memory.compareAndSet(oldMem, newMem)) break
        }
    }

    fun getTradeMemory() = memory.get().copy()

    fun reset() {
        _decision.value = null
        memory.set(TradeLifecycleMemory())
    }
}
