package com.vedx.vedxsuper.strategy.engine

import java.util.concurrent.atomic.AtomicReference

data class TradeJournalEntry(
    val tradeId: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Int,
    val profit: Double,
    val profitPercent: Double,
    val durationMs: Long,
    val maxProfit: Double,
    val maxDrawdown: Double,
    val regime: MarketRegime,
    val structure: MarketStructure,
    val state: TrendState,
    val stBand: Int,
    val premiumSymbol: String,
    val confidence: Int,
    val rrRatio: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class StrategyWeights(
    var correlationWeight: Double = 0.4,
    var indexStrengthWeight: Double = 0.3,
    var optionStrengthWeight: Double = 0.2,
    var trendMemoryWeight: Double = 0.1,
    var premiumPotentialWeight: Double = 0.3,
    var recoveryRateWeight: Double = 0.2,
    var expansionProbWeight: Double = 0.3,
    var deltaWeight: Double = 0.2
)

data class LearningStats(
    var winRate: Double = 0.0,
    var avgProfit: Double = 0.0,
    var totalTrades: Int = 0,
    var consecutiveLosses: Int = 0,
    var isLearningMode: Boolean = false,
    var bestRegime: MarketRegime = MarketRegime.NO_TRADE,
    var bestBand: Int = 0,
    var averageHoldingTimeMs: Long = 0
)

class AdaptiveLearningEngine {
    private val journal = mutableListOf<TradeJournalEntry>()
    private val weights = AtomicReference(StrategyWeights())
    private val stats = AtomicReference(LearningStats())
    private val lock = Any()

    fun recordTrade(entry: TradeJournalEntry) {
        synchronized(lock) {
            journal.add(entry)
            updateStats(entry)
            
            if (journal.size % 10 == 0) {
                optimizeWeights()
            }
        }
    }

    private fun updateStats(entry: TradeJournalEntry) {
        val current = stats.get()
        val isWin = entry.profit > 0
        
        val newConsecutiveLosses = if (isWin) 0 else current.consecutiveLosses + 1
        val newLearningMode = newConsecutiveLosses >= 5 || current.isLearningMode
        
        val newTotal = current.totalTrades + 1
        val totalWins = (current.winRate * current.totalTrades / 100.0).toInt() + (if (isWin) 1 else 0)
        
        // Find Best Regime and Band
        val bestRegime = journal.groupBy { it.regime }
            .mapValues { it.value.count { t -> t.profit > 0 } }
            .maxByOrNull { it.value }?.key ?: current.bestRegime
            
        val bestBand = journal.groupBy { it.stBand }
            .mapValues { it.value.count { t -> t.profit > 0 } }
            .maxByOrNull { it.value }?.key ?: current.bestBand

        stats.set(current.copy(
            winRate = (totalWins.toDouble() / newTotal) * 100.0,
            avgProfit = (current.avgProfit * current.totalTrades + entry.profit) / newTotal,
            totalTrades = newTotal,
            consecutiveLosses = newConsecutiveLosses,
            isLearningMode = newLearningMode,
            bestRegime = bestRegime,
            bestBand = bestBand,
            averageHoldingTimeMs = (current.averageHoldingTimeMs * current.totalTrades + entry.durationMs) / newTotal
        ))
    }

    private fun optimizeWeights() {
        if (journal.size < 20) return
        
        val currentWeights = weights.get()
        val recentTrades = journal.takeLast(50)
        val winRate = recentTrades.count { it.profit > 0 }.toDouble() / recentTrades.size
        
        if (winRate < 0.5) {
            // Underperforming: Re-balance weights based on attribute success
            val newWeights = currentWeights.copy()
            
            // If high confidence trades are failing, reduce confidence weight
            val highConfFailure = recentTrades.count { it.confidence > 80 && it.profit < 0 }
            if (highConfFailure > 5) {
                newWeights.premiumPotentialWeight = (currentWeights.premiumPotentialWeight - 0.05).coerceAtLeast(0.1)
            }
            
            // If correlation matches are failing, reduce correlation weight
            val corrFailure = recentTrades.count { it.profit < 0 } // Simplified
            if (corrFailure > 10) {
                newWeights.correlationWeight = (currentWeights.correlationWeight - 0.05).coerceAtLeast(0.2)
            }
            
            weights.set(newWeights)
        } else {
            // Performing well: Reinforce current winners
            val bestBand = recentTrades.groupBy { it.stBand }
                .maxByOrNull { it.value.count { t -> t.profit > 0 } }?.key ?: 0
                
            if (bestBand in 2..4) {
                // Aggressive entry bands are working
                val newWeights = currentWeights.copy()
                newWeights.recoveryRateWeight = (currentWeights.recoveryRateWeight + 0.05).coerceAtMost(0.4)
                weights.set(newWeights)
            }
        }
    }

    fun getWeights(): StrategyWeights = weights.get()
    
    fun getStats(): LearningStats = stats.get()
    
    fun getJournal(): List<TradeJournalEntry> = synchronized(lock) { journal.toList() }

    fun resetLearningMode() {
        stats.set(stats.get().copy(isLearningMode = false, consecutiveLosses = 0))
    }
}
