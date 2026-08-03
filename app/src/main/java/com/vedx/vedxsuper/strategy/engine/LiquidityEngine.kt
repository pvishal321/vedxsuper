package com.vedx.vedxsuper.strategy.engine

data class LiquidityResult(
    val score: Int,
    val spreadPercent: Double,
    val isExcellent: Boolean,
    val isTradeable: Boolean
)

class LiquidityEngine {
    
    fun analyze(bid: Double, ask: Double, ltp: Double): LiquidityResult {
        if (ltp <= 0 || ask <= 0) return LiquidityResult(0, 0.0, false, false)
        
        val spread = (ask - bid) / ltp * 100.0
        val score = when {
            spread < 0.2 -> 100
            spread < 0.5 -> 85
            spread < 1.0 -> 60
            spread < 2.0 -> 30
            else -> 0
        }
        
        return LiquidityResult(
            score = score,
            spreadPercent = spread,
            isExcellent = score >= 85,
            isTradeable = score >= 30
        )
    }
}
