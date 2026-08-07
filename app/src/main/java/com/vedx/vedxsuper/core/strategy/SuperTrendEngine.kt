package com.vedx.vedxsuper.core.strategy

import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.MultiST
import com.vedx.vedxsuper.data.Regimes
import com.vedx.vedxsuper.data.STResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * V4 SuperTrendEngine
 * Brain of the system. Implements ST2 to ST8.
 */
class SuperTrendEngine(
    private val factors: List<Float> = listOf(2f, 3f, 4f, 5f, 6f, 7f, 8f),
    private val period: Int = 10,
    private val masterFactor: Float = 3f,
    private val minBarsRequired: Int = 200
) {
    private val subEngines = factors.map { FastSTUnit(it, period) }
    private val masterIndex = factors.indexOf(masterFactor).coerceAtLeast(0)
    
    // Audit 2.2: ADX Caching
    private var lastAdxTs = -1L
    private var cachedAdx = 0.0

    fun calculate(candles: List<Candle>): MultiST? {
        val minBars = max(minBarsRequired, 3 * period)
        if (candles.size < minBars) return null

        val results = subEngines.map { it.calculate(candles) ?: return null }
        val m = results[masterIndex]
        
        var bulls = 0
        var bears = 0
        results.forEach { 
            if (it.trend == 1.toByte()) bulls++ else bears++
        }
        
        val alignmentPct = (max(bulls, bears).toDouble() / results.size) * 100.0
        
        // Audit 2.2: Only calc ADX on new candle
        val lastTs = candles.last().timestamp
        val adx = if (lastTs == lastAdxTs) {
            cachedAdx
        } else {
            val newVal = calcADX(candles, 14)
            lastAdxTs = lastTs
            cachedAdx = newVal
            newVal
        }
        
        return MultiST(
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
    }

    private fun detectRegime(adx: Double, masterTrend: Byte, results: List<STResult>): Regimes {
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
        if (candles.size < period * 2) return 0.0
        val tr = mutableListOf<Double>(); val plusDM = mutableListOf<Double>(); val minusDM = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val c = candles[i]; val p = candles[i - 1]
            val up = c.high.rupees - p.high.rupees; val down = p.low.rupees - c.low.rupees
            tr.add(max(c.high.rupees - c.low.rupees, max(abs(c.high.rupees - p.close.rupees), abs(c.low.rupees - p.close.rupees))))
            plusDM.add(if (up > down && up > 0) up else 0.0)
            minusDM.add(if (down > up && down > 0) down else 0.0)
        }
        var trN = tr.take(period).sum(); var pdmN = plusDM.take(period).sum(); var mdmN = minusDM.take(period).sum()
        val dxList = mutableListOf<Double>()
        for (i in period until tr.size) {
            trN = trN - (trN / period) + tr[i]; pdmN = pdmN - (pdmN / period) + plusDM[i]; mdmN = mdmN - (mdmN / period) + minusDM[i]
            val pDI = (pdmN / trN) * 100; val mDI = (mdmN / trN) * 100
            dxList.add(abs(pDI - mDI) / (pDI + mDI).coerceAtLeast(1.0) * 100)
        }
        var adx = dxList.take(period).average()
        for (i in period until dxList.size) { adx = (adx * (period - 1) + dxList[i]) / period }
        return adx
    }

    fun reset() = subEngines.forEach { it.reset() }

    private class FastSTUnit(private val multiplier: Float, private val period: Int) {
        private var lastTs = -1L
        private var lastRes: STResult? = null
        fun reset() { lastTs = -1L; lastRes = null }
        fun calculate(candles: List<Candle>): STResult? {
            if (candles.size < period + 1) return null
            if (candles.last().timestamp == lastTs) return lastRes
            var atr = 0.0; var fUp = Double.MAX_VALUE; var fLow = 0.0; var trend: Byte = 1
            var sumTR = 0.0
            for (i in 1..period) {
                sumTR += calcTR(candles[i], candles[i - 1])
            }
            atr = sumTR / period
            val mid0 = (candles[period].high.rupees + candles[period].low.rupees) / 2.0
            fUp = mid0 + multiplier * atr; fLow = mid0 - multiplier * atr
            trend = if (candles[period].close.rupees > fUp) 1.toByte() else (-1).toByte()
            for (i in (period + 1) until candles.size) {
                val c = candles[i]; val p = candles[i - 1]
                atr = (atr * (period - 1) + calcTR(c, p)) / period
                val mid = (c.high.rupees + c.low.rupees) / 2.0
                val bUp = mid + multiplier * atr; val bLow = mid - multiplier * atr
                fUp = if (bUp < fUp || p.close.rupees > fUp) bUp else fUp
                fLow = if (bLow > fLow || p.close.rupees < fLow) bLow else fLow
                if (trend == 1.toByte() && c.close.rupees < fLow) trend = (-1).toByte()
                else if (trend == (-1).toByte() && c.close.rupees > fUp) trend = 1.toByte()
            }
            lastTs = candles.last().timestamp
            lastRes = STResult(if (trend == 1.toByte()) fLow else fUp, fUp, fLow, trend, atr)
            return lastRes
        }
        private fun calcTR(c: Candle, p: Candle) = max(c.high.rupees - c.low.rupees, max(abs(c.high.rupees - p.close.rupees), abs(c.low.rupees - p.close.rupees)))
    }
}
