package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.indicator.SuperTrend
import kotlin.math.abs

data class AgentReport(
    val multiplier: Int,
    val stValue: Double,
    val trend: Int,
    val distancePoints: Double,
    val velocity: Double,
    val etaMinutes: Int?, // null = unknown/infinite
    val status: AgentStatus,
    val probability: Int,
    val trendAge: Int,
    val intensityScore: Int
)

enum class AgentStatus {
    WAITING, APPROACHING, TOUCHING, REJECTING, BREAKING, FAR_AWAY
}

/**
 * Thread-safe, timestamp-aware band agent.
 * Uses SuperTrend.calculate() which is internally O(1) incremental.
 */
class NeuralBandAgent(
    val multiplier: Int,
    val isIndex: Boolean,
    val period: Int = 10
) {
    private val stIndicator = SuperTrend(period, multiplier.toDouble())
    private var lastDistance = 0.0
    private var lastUpdateTime = 0L

    @Synchronized
    fun process(
        candles: List<Candle>,
        currentPrice: Double,
        timestamp: Long, // [FIX] Candle/tick time, NOT System.currentTimeMillis()
        vix: Double = 15.0
    ): AgentReport {
        if (candles.isEmpty() || candles.size < period) {
            return AgentReport(multiplier, 0.0, 0, 0.0, 0.0, null, AgentStatus.WAITING, 0, 0, 0)
        }

        val stResult = stIndicator.calculate(candles)
        val stValue = stResult.value

        val distance = abs(currentPrice - stValue)
        val timeDiffMins = if (lastUpdateTime == 0L) 1.0 else (timestamp - lastUpdateTime) / 60000.0
        val distChange = lastDistance - distance
        val velocity = if (timeDiffMins > 0) distChange / timeDiffMins else 0.0

        val eta = if (velocity > 0.001) (distance / velocity).toInt().coerceIn(0, 60) else null

        val threshold = if (isIndex) {
            (stValue * (vix / 20000.0)).coerceIn(stValue * 0.0004, stValue * 0.0015)
        } else {
            (stValue * 0.025).coerceIn(1.0, 10.0)
        }

        val isWrongSide = if (stResult.trend == 1) currentPrice < stValue else currentPrice > stValue

        val velocityThreshold = if (isIndex) {
            stValue * 0.0002
        } else {
            (currentPrice * 0.002).coerceAtLeast(0.5)
        }

        val status = when {
            isWrongSide && distance > threshold -> AgentStatus.BREAKING
            distance <= threshold -> AgentStatus.TOUCHING
            velocity > velocityThreshold && distance < (threshold * 5) -> AgentStatus.APPROACHING
            velocity < -velocityThreshold && distance < (threshold * 3) -> AgentStatus.REJECTING
            distance > (threshold * 10) -> AgentStatus.FAR_AWAY
            else -> AgentStatus.WAITING
        }

        val intensity = ((velocity / (stValue * 0.001)) * 100).toInt().coerceIn(0, 100)
        val prob = when (status) {
            AgentStatus.TOUCHING -> 85
            AgentStatus.APPROACHING -> 75
            AgentStatus.REJECTING -> 40
            AgentStatus.BREAKING -> 5
            else -> (80 - (distance / threshold * 10).toInt())
        }.coerceIn(0, 100) + if (stResult.trendAge > 5) 10 else 0

        lastDistance = distance
        lastUpdateTime = timestamp

        return AgentReport(
            multiplier = multiplier,
            stValue = stValue,
            trend = stResult.trend,
            distancePoints = distance,
            velocity = velocity,
            etaMinutes = eta,
            status = status,
            probability = prob.coerceIn(0, 100),
            trendAge = stResult.trendAge,
            intensityScore = intensity
        )
    }

    fun reset() {
        stIndicator.reset()
        lastDistance = 0.0
        lastUpdateTime = 0L
    }
}
