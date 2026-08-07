package com.vedx.vedxsuper.core.strategy

import com.vedx.vedxsuper.data.*
import java.util.concurrent.ConcurrentHashMap

/**
 * V4 AnalyticsEngine
 * Responsible for detailed stats for each ST band and performance metrics.
 */
class AnalyticsEngine {
    private val bandStats = ConcurrentHashMap<String, BandMetric>()
    
    data class BandMetric(
        val trades: Int = 0,
        val wins: Int = 0,
        val totalProfit: Long = 0,
        val totalLoss: Long = 0,
        val totalHoldTime: Long = 0
    ) {
        val winRate: Float get() = if (trades > 0) (wins.toFloat() / trades * 100) else 0f
        val avgProfit: Double get() = if (wins > 0) totalProfit.toDouble() / wins else 0.0
        val avgLoss: Double get() = if (trades - wins > 0) totalLoss.toDouble() / (trades - wins) else 0.0
    }

    fun recordTrade(matchedBand: String, pnl: Long, holdTime: Long) {
        val current = bandStats[matchedBand] ?: BandMetric()
        val updated = current.copy(
            trades = current.trades + 1,
            wins = if (pnl > 0) current.wins + 1 else current.wins,
            totalProfit = if (pnl > 0) current.totalProfit + pnl else current.totalProfit,
            totalLoss = if (pnl < 0) current.totalLoss + kotlin.math.abs(pnl) else current.totalLoss,
            totalHoldTime = current.totalHoldTime + holdTime
        )
        bandStats[matchedBand] = updated
    }

    fun getBandAnalytics(): Map<String, BandMetric> = bandStats
}
