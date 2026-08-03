package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.model.market.TickData
import kotlin.math.abs

data class StrengthMetrics(
    val velocity: Double = 0.0,
    val acceleration: Double = 0.0,
    val trendStrength: Double = 0.0, // 0 to 100
    val momentum: Double = 0.0,
    val isExhausted: Boolean = false,
    val isPullback: Boolean = false,
    val slope: Double = 0.0
)

class MarketStrengthEngine {
    private var lastPrice: Double = 0.0
    private var lastVelocity: Double = 0.0
    private var lastTimestamp: Long = 0

    private val velocityWindow = mutableListOf<Double>()
    private val windowSize = 20
    private val lock = Any()

    fun calculateStrength(tick: TickData, candles: List<Candle>): StrengthMetrics = synchronized(lock) {
        val currentTime = tick.timestamp
        val dt = (currentTime - lastTimestamp) / 1000.0 // seconds

        var currentVelocity = 0.0
        var currentAcceleration = 0.0

        if (dt > 0 && lastPrice > 0) {
            currentVelocity = (tick.ltp - lastPrice) / dt
            currentAcceleration = (currentVelocity - lastVelocity) / dt
        }

        lastPrice = tick.ltp
        lastVelocity = currentVelocity
        lastTimestamp = currentTime

        velocityWindow.add(currentVelocity)
        if (velocityWindow.size > windowSize) velocityWindow.removeAt(0)

        val slope = calculateSlope(candles)
        val trendStrength = (50.0 + (slope * 10.0)).coerceIn(0.0, 100.0)
        val momentum = calculateMomentum(candles)

        val isExhausted = checkExhaustion(momentum, trendStrength)
        val isPullback = checkPullback(candles, currentVelocity)

        return@synchronized StrengthMetrics(
            velocity = currentVelocity,
            acceleration = currentAcceleration,
            trendStrength = trendStrength,
            momentum = momentum,
            isExhausted = isExhausted,
            isPullback = isPullback,
            slope = slope
        )
    }

    private fun calculateSlope(candles: List<Candle>): Double {
        if (candles.size < 10) return 0.0

        // Linear Regression Slope for more accurate trend detection
        val n = candles.size
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0

        for (i in 0 until n) {
            val x = i.toDouble()
            val y = candles[i].close
            sumX += x
            sumY += y
            sumXY += x * y
            sumX2 += x * x
        }

        val numerator = (n * sumXY) - (sumX * sumY)
        val denominator = (n * sumX2) - (sumX * sumX)

        if (denominator == 0.0) return 0.0

        val rawSlope = numerator / denominator
        return (rawSlope / candles.first().close) * 1000.0 // Normalize as basis points per candle
    }

    private fun calculateMomentum(candles: List<Candle>): Double {
        if (candles.size < 14) return 50.0
        val periods = 14
        val startPrice = candles[candles.size - periods].close
        val endPrice = candles.last().close
        val change = ((endPrice - startPrice) / startPrice) * 1000.0
        return (50.0 + change).coerceIn(0.0, 100.0)
    }

    private fun checkExhaustion(momentum: Double, strength: Double): Boolean {
        return (momentum > 85 && strength > 85) || (momentum < 15 && strength < 15)
    }

    private fun checkPullback(candles: List<Candle>, velocity: Double): Boolean {
        if (candles.size < 10) return false
        val shortTermTrend = if (candles.last().close > candles[candles.size - 10].close) 1 else -1
        return if (shortTermTrend == 1) velocity < -0.05 else velocity > 0.05
    }
}
