package com.vedx.vedxsuper.strategy.engine

data class TrendMemory(
    var totalTrends: Int = 0,
    var successfulTrends: Int = 0,
    var lastTrendResult: Boolean = false,
    var averageDurationMs: Long = 0
)

class TrendMemoryEngine {
    private var memory = TrendMemory()
    
    fun update(success: Boolean, duration: Long) {
        memory.totalTrends++
        if (success) memory.successfulTrends++
        memory.lastTrendResult = success
        // Simplified average calculation
        memory.averageDurationMs = (memory.averageDurationMs + duration) / 2
    }
    
    fun getScore(): Int {
        if (memory.totalTrends == 0) return 50
        return (memory.successfulTrends.toDouble() / memory.totalTrends * 100).toInt()
    }
}
