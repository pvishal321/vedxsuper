package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import com.vedx.vedxsuper.utils.StrategyUtils.safe
import com.vedx.vedxsuper.utils.StrategyUtils.clampScore
import com.vedx.vedxsuper.utils.StrategyUtils.clampRatio
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Defines the role of a SuperTrend band relative to the current price.
 */
enum class BandRole {
    SUPPORT, RESISTANCE, UNKNOWN
}

/**
 * Detailed status of a price interaction with SuperTrend zones.
 */
data class ZoneStatus(
    val activeBand: Int,
    val isTouch: Boolean,
    val isRejection: Boolean,
    val role: BandRole,
    val price: Double,
    val nextBandValue: Double,
    val allTargets: List<Double>,
    val gapPoints: Double,
    val matchScore: Double,
    val rejectionQuality: Double = 0.0,
    val bandDistancePercent: Double = 0.0,
    val atrDistance: Double = 0.0,
    val wickRatio: Double = 0.0,
    val bodyStrength: Double = 0.0
)

class SuperTrendZoneEngine {

    private companion object {
        const val MAX_PROXIMITY_SCORE = 30.0
        const val MAX_REJECTION_SCORE = 40.0
        const val ATR_ALIGNMENT_SCORE = 20.0
        const val BODY_SCORE = 10.0
        const val MIN_ATR_SAFE = 0.0001
        const val ATR_PROXY_FACTOR = 0.10
        const val PROXIMITY_THRESHOLD = 0.2
        const val EPSILON = 0.000001
        
        const val FALLBACK_UP_MULTIPLIER = 1.05
        const val FALLBACK_DOWN_MULTIPLIER = 0.95
        
        const val REJECTION_BODY_BOOST = 20.0
        const val SCORE_MAX = 100.0
    }
    
    /**
     * Identifies the current trading zone and calculates interaction quality metrics.
     * [ltp] current price, [stResult] multi-supertrend data.
     */
    fun identifyZone(ltp: Double, stResult: MultiSuperTrendResult, candles: List<Candle> = emptyList(), isIndex: Boolean = true, vix: Double = 15.0): ZoneStatus {
        // [FIXED] Point 3: Early Validation
        if (ltp <= EPSILON) return emptyZoneStatus(ltp)

        // [FIXED] Point 4: Validate Band Values for NaN/Infinity
        val bands = doubleArrayOf(
            stResult.st2.value.safe(), stResult.st3.value.safe(), stResult.st4.value.safe(),
            stResult.st5.value.safe(), stResult.st6.value.safe(), stResult.st7.value.safe(), 
            stResult.st8.value.safe()
        )
        
        // [FIXED] Point 6 & 14: Linear Search & Cached Calculations
        var nearestBandIdx = 2
        var nearestValue = bands[0]
        var minDistance = abs(bands[0] - ltp)

        for (i in 1 until bands.size) {
            val dist = abs(bands[i] - ltp)
            if (dist < minDistance) {
                minDistance = dist
                nearestValue = bands[i]
                nearestBandIdx = i + 2
            }
        }
        
        val st2Gap = abs(stResult.st2.upperBand - stResult.st2.lowerBand)
        val atrProxy = (st2Gap / 4.0).safe()

        val baseThreshold = if (isIndex) {
            (nearestValue * (vix / 20000.0)).coerceIn(nearestValue * 0.0004, nearestValue * 0.0015)
        } else {
            (nearestValue * 0.025).coerceIn(1.0, 10.0)
        }
        
        val threshold = max(baseThreshold, atrProxy * ATR_PROXY_FACTOR).safe()
        val isTouch = minDistance <= (threshold + EPSILON)
        val role = if (ltp < nearestValue - EPSILON) BandRole.RESISTANCE else BandRole.SUPPORT
        
        // [FIXED] Point 5: Safe Divisions
        val bandDistancePercent = if (nearestValue > EPSILON) (minDistance / nearestValue * 100.0).safe() else 100.0
        val atrDistance = if (atrProxy > MIN_ATR_SAFE) (minDistance / atrProxy).safe() else Double.MAX_VALUE

        var wickRatio = 0.0
        var bodyStrength = 0.0
        var rejectionQuality = 0.0
        
        if (candles.isNotEmpty()) {
            val last = candles.last()
            val totalSize = abs(last.high - last.low).coerceAtLeast(0.1)
            val bodySize = abs(last.close - last.open)
            
            val upperWick = last.high - max(last.open, last.close)
            val lowerWick = min(last.open, last.close) - last.low
            
            wickRatio = (if (role == BandRole.RESISTANCE) upperWick / totalSize else lowerWick / totalSize).clampRatio().safe()
            bodyStrength = (bodySize / totalSize).clampRatio().safe()
            rejectionQuality = (wickRatio * SCORE_MAX).clampScore().safe()
            
            val isBodyMovingAway = if (role == BandRole.RESISTANCE) last.close < last.open else last.close > last.open
            if (isBodyMovingAway) {
                rejectionQuality = (rejectionQuality + (bodyStrength * REJECTION_BODY_BOOST)).clampScore().safe()
            }
        }
        
        val isRejection = isTouch && ((role == BandRole.RESISTANCE && ltp <= nearestValue + EPSILON) || 
                                     (role == BandRole.SUPPORT && ltp >= nearestValue - EPSILON))
        
        val targetCandidates = mutableListOf<Double>()
        bands.forEach { bandVal -> 
            val isPotentialTarget = if (role == BandRole.SUPPORT) bandVal > ltp + EPSILON else bandVal < ltp - EPSILON
            if (isPotentialTarget) targetCandidates.add(bandVal) 
        }
        
        // [FIXED] Point 2: Defensive Copy via toList()
        val finalTargets = targetCandidates.distinct().sortedBy { if (role == BandRole.SUPPORT) it else -it }.toList()
        
        val nextBandValue = finalTargets.firstOrNull() ?: fallbackTarget(ltp, role)
        val gapPoints = abs(nextBandValue - ltp).safe()
        
        val proximityScore = (1.0 - (bandDistancePercent / PROXIMITY_THRESHOLD).clampRatio()).safe() * MAX_PROXIMITY_SCORE
        val rejectionScore = (rejectionQuality / SCORE_MAX).safe() * MAX_REJECTION_SCORE
        val atrScore = if (atrDistance < 0.5) ATR_ALIGNMENT_SCORE else 0.0
        val bScore = (bodyStrength * BODY_SCORE).safe()

        val totalMatchScore = (proximityScore + rejectionScore + atrScore + bScore).clampScore().safe()

        return ZoneStatus(
            activeBand = nearestBandIdx,
            isTouch = isTouch,
            isRejection = isRejection,
            role = role,
            price = ltp,
            nextBandValue = nextBandValue,
            allTargets = finalTargets,
            gapPoints = gapPoints,
            matchScore = totalMatchScore,
            rejectionQuality = rejectionQuality,
            bandDistancePercent = bandDistancePercent,
            atrDistance = atrDistance,
            wickRatio = wickRatio,
            bodyStrength = bodyStrength
        )
    }

    private fun emptyZoneStatus(price: Double) = ZoneStatus(0, false, false, BandRole.UNKNOWN, price, 0.0, emptyList(), 0.0, 0.0)

    private fun fallbackTarget(ltp: Double, role: BandRole): Double = 
        if (role == BandRole.SUPPORT) (ltp * FALLBACK_UP_MULTIPLIER).safe() else (ltp * FALLBACK_DOWN_MULTIPLIER).safe()
}

/**
 * Coordinates matching between Index zones and Option zones for dual confirmation.
 */
class ZoneMatchEngine {
    
    /**
     * Calculates synchronization quality between Index and Option.
     */
    fun calculateMatch(indexStatus: ZoneStatus, optionStatus: ZoneStatus): Double {
        // [FIXED] Point 11: Readability with canProceed style
        val isDualRejection = indexStatus.isRejection && optionStatus.isRejection
        val isSyncMatch = (indexStatus.role == BandRole.RESISTANCE && optionStatus.role == BandRole.SUPPORT) || 
                         (indexStatus.role == BandRole.SUPPORT && optionStatus.role == BandRole.SUPPORT)
        
        var matchScore = minOf(indexStatus.matchScore, optionStatus.matchScore)
        if (isDualRejection && isSyncMatch) matchScore += 30.0
        
        val avgWickRatio = (indexStatus.wickRatio + optionStatus.wickRatio) / 2.0
        val avgBodyStrength = (indexStatus.bodyStrength + optionStatus.bodyStrength) / 2.0
        
        var qualityMultiplier = 0.8
        if (avgWickRatio > 0.6) qualityMultiplier += 0.1
        if (avgBodyStrength > 0.5) qualityMultiplier += 0.1
        
        if (indexStatus.activeBand == optionStatus.activeBand) matchScore += 10.0
        
        return (matchScore * qualityMultiplier.coerceIn(0.8, 1.0)).clampScore().safe()
    }
}
