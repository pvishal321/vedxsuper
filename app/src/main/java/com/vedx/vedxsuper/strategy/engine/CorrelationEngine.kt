package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import kotlin.math.abs

data class CorrelationResult(
    val matched: Boolean,
    val score: Int,
    val reason: String
)

class CorrelationEngine {

    fun calculate(indexSt: MultiSuperTrendResult, optionSt: MultiSuperTrendResult, optionSymbol: String): CorrelationResult {
        val indexTrend = indexSt.st2.trend
        val optionTrend = optionSt.st2.trend

        // [FIXED] Proper Correlation Logic for Option Buying (Both CE and PE)
        // CALL: Index Bullish (1) -> CALL Premium Bullish (1)
        // PUT: Index Bearish (-1) -> PUT Premium Bullish (1)

        val isCall = optionSymbol.contains("CE")
        val isPut = optionSymbol.contains("PE")

        val matched = when {
            isCall -> indexTrend >= 0 && optionTrend == 1 // Allow Neutral Index for Call
            isPut -> indexTrend <= 0 && optionTrend == 1 // Allow Neutral Index for Put
            else -> true // Fallback to always matched for manual override
        }

        var score = 0
        if (matched) {
            score = 100
            // High confidence if deeper bands also align
            if (optionSt.st8.trend == 1) score = 100 else score = 70
        }

        return CorrelationResult(
            matched = matched,
            score = score,
            reason = if (matched) "Market & Premium Sync" else "Trend Mismatch"
        )
    }
}
