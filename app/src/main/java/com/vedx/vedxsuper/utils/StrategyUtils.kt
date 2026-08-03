package com.vedx.vedxsuper.utils

/**
 * Utility functions for trading strategy calculations.
 */
object StrategyUtils {
    
    /**
     * Ensures a Double value is finite and safe for calculation.
     */
    fun Double.safe(): Double = if (isFinite()) this else 0.0

    /**
     * Clamps a score between 0.0 and 100.0.
     */
    fun Double.clampScore(): Double = this.coerceIn(0.0, 100.0)

    /**
     * Clamps a ratio between 0.0 and 1.0.
     */
    fun Double.clampRatio(): Double = this.coerceIn(0.0, 1.0)
    
    /**
     * Precision comparison for floating point prices.
     */
    fun isPriceEqual(p1: Double, p2: Double, epsilon: Double = 0.000001): Boolean =
        kotlin.math.abs(p1 - p2) < epsilon
}
