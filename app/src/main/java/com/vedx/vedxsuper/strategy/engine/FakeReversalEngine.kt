package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import kotlin.math.abs

data class FakeReversalResult(
    val isFake: Boolean,
    val confidence: Int,
    val reason: String
)

class FakeReversalEngine {
    
    fun analyze(
        candles: List<Candle>,
        stResult: MultiSuperTrendResult?,
        strength: StrengthMetrics,
        structure: MarketStructure
    ): FakeReversalResult {
        if (candles.size < 5) return FakeReversalResult(false, 100, "Insufficient Data")
        
        val last = candles.last()
        val prev = candles[candles.size - 2]
        
        // 1. Low Volume Reversal check
        val avgVol = candles.takeLast(10).map { it.volume }.average()
        val isLowVolume = last.volume < avgVol * 0.7
        
        // 2. Momentum check
        val isWeakMomentum = abs(strength.velocity) < 0.2
        
        // 3. Structure Mismatch
        val isStructureMismatch = structure == MarketStructure.CONSOLIDATION || structure == MarketStructure.ACCUMULATION
        
        return when {
            isLowVolume && isWeakMomentum -> FakeReversalResult(true, 80, "Low Volume & Weak Momentum")
            isStructureMismatch && strength.acceleration < 0 -> FakeReversalResult(true, 70, "Structure Mismatch")
            else -> FakeReversalResult(false, 90, "Confirmed Move")
        }
    }
}
