package com.vedx.vedxsuper.core

import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.MultiST
import com.vedx.vedxsuper.data.Regimes
import kotlin.math.abs
import kotlin.math.max

/**
 * MultiBandEngine
 * Aggregates multiple SuperTrend (FastST) indicators for consensus-based signals.
 * Optimized with configurable factors and unified market regime analysis.
 */
class MultiBandEngine(
    private val factors: List<Float> = listOf(2f, 3f, 4f, 5f, 6f, 7f, 8f)
) {
    private val engines = factors.map { FastST(it) }
    // Master is the 3x multiplier engine (index 1 in default list)
    private val masterIndex = factors.indexOf(3f).coerceAtLeast(0)
    
    fun calculate(candles: List<Candle>): MultiST? {
        val minBars = max(200, 3 * (factors.maxOrNull()?.toInt() ?: 10))
        if (candles.size < minBars) return null

        val results = engines.map { it.calculate(candles) ?: return null }
        val m = results[masterIndex]
        
        var bulls = 0
        var bears = 0
        
        results.forEach { 
            if (it.trend == 1.toByte()) bulls++ else bears++
        }
        
        val alignmentPct = (max(bulls, bears).toDouble() / results.size) * 100.0
        val adx = calcADX(candles, 14)
        
        val st = MultiST(
            st2 = results[0], st3 = results[1], st4 = results[2],
            st5 = results[3], st6 = results[4], st7 = results[5], st8 = results[6],
            master = m,
            bullCount = bulls,
            bearCount = bears,
            alignmentPct = alignmentPct,
            strongTrend = alignmentPct >= 100.0,
            confidenceScore = alignmentPct,
            adx = adx,
            regime = detectRegime(adx, m.trend, results)
        )
        
        return st
    }

    private fun detectRegime(adx: Double, masterTrend: Byte, results: List<com.vedx.vedxsuper.data.STResult>): Regimes {
        val bands = results.map { it.triggerPrice }
        val maxB = bands.maxOrNull() ?: 0.0
        val minB = bands.minOrNull() ?: 0.0
        val isCompressed = (maxB - minB) / ((maxB + minB) / 2.0) < 0.005

        return when {
            adx > 25 && masterTrend == 1.toByte() -> Regimes.TRENDING_UP
            adx > 25 && masterTrend == (-1).toByte() -> Regimes.TRENDING_DOWN
            isCompressed -> Regimes.VOLATILE
            else -> Regimes.SIDEWAY
        }
    }

    private fun calcADX(candles: List<Candle>, period: Int): Double {
        if (candles.size < period + 1) return 0.0
        var plusDM = 0.0; var minusDM = 0.0; var trSum = 0.0
        for (i in candles.size - period until candles.size) {
            val c = candles[i]; val p = candles[i - 1]
            val upMove = c.high.rupees - p.high.rupees
            val downMove = p.low.rupees - c.low.rupees
            plusDM += if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDM += if (downMove > upMove && downMove > 0) downMove else 0.0
            val tr1 = c.high.rupees - c.low.rupees
            val tr2 = abs(c.high.rupees - p.close.rupees)
            val tr3 = abs(c.low.rupees - p.close.rupees)
            trSum += max(tr1, max(tr2, tr3))
        }
        return if (trSum > 0) abs(plusDM - minusDM) / (plusDM + minusDM + 0.001) * 100 else 0.0
    }

    fun reset() {
        engines.forEach { it.reset() }
    }
}
