package com.vedx.vedxsuper.core.strategy

import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.core.risk.RiskEngine
import kotlin.math.abs

/**
 * V4 SignalEngine
 * Evaluates strategy rules: ST Match, Reversal, Context Filters.
 * Enhanced with Trade Quality Grading and Weighted Confidence.
 */
class SignalEngine(
    private val riskEngine: RiskEngine
) {
    // Audit 3.5: Priority table for ST Bands
    private val bandPriority = mapOf(
        2 to 1.0, 3 to 1.2, 4 to 1.5, 5 to 1.4, 6 to 1.2, 7 to 1.0, 8 to 0.8
    )

    fun evaluate(
        symbol: String,
        price: Double,
        optST: MultiST,
        idxST: MultiST,
        optCandles: List<Candle>,
        context: MarketContext,
        tick: TickData,
        oiAnalysis: OIAnalysis,
        isPositionOpen: Boolean = false,
        isExpiryDay: Boolean = false
    ): Signal? {
        val optionType = if (symbol.contains("CE")) OptionType.CE else OptionType.PE

        // Audit 3.6: State-based Duplicate Filter & Cooldown
        if (isPositionOpen) return null

        // Audit 3.7: Sideways Market Filter
        if (context.regime == Regimes.SIDEWAY || optST.isCompressed) {
            return null 
        }

        // Audit 3.8: Gap Opening Filter
        if (detectExtremeGap(optCandles)) return null

        // 1. Adaptive ST Match (Priority Weighted)
        val idxBands = idxST.bandList()
        val optBands = optST.bandList()
        val tolerance = price * (0.001 + (optST.master.atr / price) * 0.01)
        val matchFound = findSTMatch(idxBands, optBands, tolerance)
        if (matchFound == 0) return null

        // 2. Band Reversal
        val rev = detectReversal(optCandles, optBands, price, optionType)
        if (!rev.reversed) return null

        // 3. Rule Pipeline (Audit 3.11)
        
        // Audit 3.4: Weighted Confidence
        val weights = mapOf(
            "ST_MATCH" to 0.35,
            "REVERSAL" to 0.20,
            "VOLUME" to 0.15,
            "OI" to 0.10,
            "PCR" to 0.10,
            "VIX" to 0.10
        )

        val stBaseScore = (matchFound.toDouble() / 7.0).coerceAtMost(1.0) * 100.0
        val priorityMultiplier = bandPriority[rev.bandIdx + 2] ?: 1.0
        val stScore = (stBaseScore * priorityMultiplier).coerceAtMost(100.0)

        val pcrScore = if (optionType == OptionType.CE) {
            if (context.pcr < 1.0) 100.0 else if (context.pcr < 1.3) 60.0 else 0.0
        } else {
            if (context.pcr > 1.0) 100.0 else if (context.pcr > 0.7) 60.0 else 0.0
        }

        val vixScore = if (context.vix < 20) 100.0 else if (context.vix < 25) 70.0 else 40.0
        val volumeScore = rev.volumeConfirm * 100.0
        val oiScore = calculateOIScore(oiAnalysis, optionType).toDouble() * 5.0

        var weightedConf = (stScore * weights["ST_MATCH"]!!) + 
                           (100.0 * weights["REVERSAL"]!!) + 
                           (volumeScore * weights["VOLUME"]!!) + 
                           (oiScore * weights["OI"]!!) + 
                           (pcrScore * weights["PCR"]!!) + 
                           (vixScore * weights["VIX"]!!)

        // Audit 3.9: Expiry Filter
        if (isExpiryDay) {
            weightedConf *= 0.85 // Reduce confidence due to high theta decay risk
        }

        if (weightedConf < 70) return null

        // 5. Trade Grading
        val grade = when {
            weightedConf >= 90 -> TradeGrade.AP
            weightedConf >= 80 -> TradeGrade.A
            weightedConf >= 72 -> TradeGrade.B
            else -> TradeGrade.C
        }

        // Audit 3.11: Structured Explainable Reason
        val reason = """
            BUY CONFIRMED:
            - ST Match: $matchFound (Band ST${rev.bandIdx + 2})
            - Reversal: ${"%.1f".format(rev.strength * 100)}% Strength
            - OI: ${oiAnalysis.interpretation}
            - PCR: ${"%.2f".format(context.pcr)}
            - Volume: ${"%.1f".format(volumeScore)}% Spike
            - Regime: ${context.regime}
        """.trimIndent()

        return Signal(
            action = Actions.BUY,
            optionType = optionType,
            symbol = Symbol(symbol),
            entryPrice = Price.from(price),
            target = Price.from(calculateTarget(optBands, rev.bandIdx, optionType)),
            stopLoss = Price.from(rev.sl),
            trailingSl = if (optionType == OptionType.CE) {
                Price.from(price + abs(price - rev.sl) * 0.5)
            } else {
                Price.from(price - abs(price - rev.sl) * 0.5)
            },
            confidence = Confidence(weightedConf.toInt()),
            reason = reason,
            timestamp = tick.ts,
            quantity = 0,
            lots = 0,
            regime = context.regime,
            matchedBand = "ST${rev.bandIdx + 2}",
            reversalStrength = rev.strength,
            grade = grade,
            weightedConfidence = weightedConf,
            pcrAtSignal = context.pcr,
            vixAtSignal = context.vix
        )
    }

    private fun detectExtremeGap(candles: List<Candle>): Boolean {
        if (candles.size < 2) return false
        val last = candles.last()
        val prev = candles[candles.size - 2]
        val gap = abs(last.open.rupees - prev.close.rupees)
        val gapPct = (gap / prev.close.rupees) * 100.0
        return gapPct > 2.0 // Ignore if gap is more than 2%
    }

    private fun findSTMatch(idx: DoubleArray, opt: DoubleArray, tol: Double): Int {
        var count = 0
        idx.forEach { i -> opt.forEach { o -> if (abs(i - o) < tol) count++ } }
        return count
    }

    private fun detectReversal(candles: List<Candle>, bands: DoubleArray, price: Double, type: OptionType): RevRes {
        val last = candles.last(); val prev = candles[candles.size - 2]
        val avgVol = candles.takeLast(11).dropLast(1).map { it.volume }.average().coerceAtLeast(1.0)
        val volConfirm = (last.volume / avgVol).coerceIn(0.0, 2.0) / 2.0
        val tol = price * 0.001
        for (i in bands.indices) {
            val band = bands[i]
            val touched = abs(price - band) < tol || abs(prev.close.rupees - band) < tol
            if (touched) {
                val ok = if (type == OptionType.CE) price > prev.close.rupees && price > band else price < prev.close.rupees && price < band
                if (ok) {
                    val sl = if (type == OptionType.CE) (if (i < bands.size - 1) bands[i+1] * 0.998 else band * 0.99) else (if (i > 0) bands[i-1] * 1.002 else band * 1.01)
                    return RevRes(true, i, 0.8, volConfirm, sl)
                }
            }
        }
        return RevRes(false, -1, 0.0, 0.0, 0.0)
    }

    private fun calculateOIScore(oi: OIAnalysis, type: OptionType) = when (oi.interpretation) {
        "Long Buildup" -> if (type == OptionType.CE) 20 else 5
        "Short Buildup" -> if (type == OptionType.PE) 20 else 5
        "Long Unwinding" -> if (type == OptionType.PE) 15 else 0
        "Short Covering" -> if (type == OptionType.CE) 15 else 0
        else -> 10
    }

    private fun calculateTarget(bands: DoubleArray, idx: Int, type: OptionType): Double {
        return if (type == OptionType.CE) {
            // For Call, we want a HIGHER target. Lower index in bands array is a higher level (closer to price)
            if (idx > 0) bands[idx - 1] else bands[0] * 1.05
        } else {
            // For Put, we want a LOWER target. Lower index (ST2) is lower level than ST3/ST4 in downtrend
            if (idx > 0) bands[idx - 1] else bands[0] * 0.95
        }
    }

    private data class RevRes(val reversed: Boolean, val bandIdx: Int, val strength: Double, val volumeConfirm: Double, val sl: Double)
}
