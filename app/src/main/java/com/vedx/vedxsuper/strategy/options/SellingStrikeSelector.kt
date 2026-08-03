package com.vedx.vedxsuper.strategy.options

import com.vedx.vedxsuper.strategy.engine.MarketRegime
import com.vedx.vedxsuper.strategy.engine.MarketStructure
import com.vedx.vedxsuper.strategy.engine.StrengthMetrics
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * प्रोडक्शन-ग्रेड Option Selling Strike Selector.
 * Multi-ST बँड्स, regime detection, आणि liquidity filters बरोबर जोडलेला.
 */
class SellingStrikeSelector(
    private val indexSymbol: String,
    private val lotSizeProvider: (String) -> Int = { symbol ->
        when {
            symbol.contains("BANKNIFTY") -> 15 // Current Nifty Bank lot size is 15
            symbol.contains("SENSEX") -> 10
            symbol.contains("FINNIFTY") -> 25
            symbol.contains("NIFTY") -> 25 // Current Nifty 50 lot size is 25
            else -> 25
        }
    }
) {

    data class SellingCandidate(
        val strikePrice: Double,      // स्ट्राइक किंमत
        val type: String,             // "CE" किंवा "PE"
        val distancePercent: Double,  // ATM पासून किती % दूर
        val beyondBand: Int,          // कोणत्या ST बँडपेक्षा पुढे
        val marginRequired: Double,   // किती मार्जिन लागेल
        val premiumTarget: Double,    // 50% प्रीमियम घेऊन बसायचं
        val stopLossPremium: Double,  // किती प्रीमियम वाढलं की SL
        val daysToExpiry: Int,        // एक्सपायरीपर्यंत किती दिवस
        val confidence: Int,          // 0-100 आत्मविश्वास स्कोर
        val reason: String            // का हा स्ट्राइक निवडला
    )

    /**
     * **Call विकायचा (Short CE)** - Bearish/Sideways मार्केटमध्ये.
     * Strike resistance च्या वर असलं पाहिजे.
     */
    fun selectOtmCall(
        indexPrice: Double,
        indexSt: MultiSuperTrendResult,
        vix: Double,
        daysToExpiry: Int,
        structure: MarketStructure,
        strength: StrengthMetrics
    ): SellingCandidate? {
        if (!isSafeToSell(indexSt, MarketRegime.SIDEWAYS, structure, strength)) return null

        // [1] VIX जास्त असेल तर जास्त OTM (सुरक्षित अंतर)
        val baseOtm = if (vix > 20) 0.035 else 0.025
        val structureMultiplier = if (structure == MarketStructure.EXPANSION) 1.3 else 1.0
        val rawStrike = indexPrice * (1 + baseOtm * structureMultiplier)

        // [2] Exchange strike gap नुसार round करा (NIFTY 50, BANKNIFTY 100)
        val strikeGap = if (indexSymbol.contains("BANKNIFTY")) 100.0 else 50.0
        val strikePrice = roundToTick(rawStrike, strikeGap)

        // [3] ST7/ST8 upper band पेक्षा वर असलं पाहिजे
        val upperResistance = max(indexSt.st8.upperBand, indexSt.st7.upperBand)
        val safeStrike = max(strikePrice, roundToTick(upperResistance * 1.005, strikeGap)) // 0.5% बफर

        // [4] ATM पासून किमान 1.5% दूर असलं पाहिजे
        val distancePct = (safeStrike - indexPrice) / indexPrice * 100.0
        if (distancePct < 1.5) return null

        // [5] Risk parameters
        val lotSize = lotSizeProvider(indexSymbol)
        val margin = safeStrike * lotSize * 0.18
        val premium = estimatePremium(safeStrike, daysToExpiry, vix, isCall = true)
        val premiumTarget = premium * 0.5
        val slPremium = (margin * 0.25) / lotSize

        val confidence = calculateConfidence(indexSt, distancePct, daysToExpiry, vix)

        return SellingCandidate(
            strikePrice = safeStrike,
            type = "CE",
            distancePercent = distancePct,
            beyondBand = 8,
            marginRequired = margin,
            premiumTarget = premiumTarget,
            stopLossPremium = slPremium,
            daysToExpiry = daysToExpiry,
            confidence = confidence,
            reason = "Short CE beyond ST8+ | ${String.format(Locale.US, "%.1f", distancePct)}% OTM | DTE=$daysToExpiry"
        )
    }

    /**
     * **Put विकायचा (Short PE)** - Bullish/Sideways मार्केटमध्ये.
     * Strike support च्या खाली असलं पाहिजे.
     */
    fun selectOtmPut(
        indexPrice: Double,
        indexSt: MultiSuperTrendResult,
        vix: Double,
        daysToExpiry: Int,
        structure: MarketStructure,
        strength: StrengthMetrics
    ): SellingCandidate? {
        if (!isSafeToSell(indexSt, MarketRegime.SIDEWAYS, structure, strength)) return null

        val baseOtm = if (vix > 20) 0.035 else 0.025
        val structureMultiplier = if (structure == MarketStructure.EXPANSION) 1.3 else 1.0
        val rawStrike = indexPrice * (1 - baseOtm * structureMultiplier)

        val strikeGap = if (indexSymbol.contains("BANKNIFTY")) 100.0 else 50.0
        val strikePrice = roundToTick(rawStrike, strikeGap)

        // ST7/ST8 lower band पेक्षा खाली
        val lowerSupport = min(indexSt.st8.lowerBand, indexSt.st7.lowerBand)
        val safeStrike = min(strikePrice, roundToTick(lowerSupport * 0.995, strikeGap))

        val distancePct = (indexPrice - safeStrike) / indexPrice * 100.0
        if (distancePct < 1.5) return null

        val lotSize = lotSizeProvider(indexSymbol)
        val margin = safeStrike * lotSize * 0.18
        val premium = estimatePremium(safeStrike, daysToExpiry, vix, isCall = false)
        val premiumTarget = premium * 0.5
        val slPremium = (margin * 0.25) / lotSize

        val confidence = calculateConfidence(indexSt, distancePct, daysToExpiry, vix)

        return SellingCandidate(
            strikePrice = safeStrike,
            type = "PE",
            distancePercent = distancePct,
            beyondBand = 8,
            marginRequired = margin,
            premiumTarget = premiumTarget,
            stopLossPremium = slPremium,
            daysToExpiry = daysToExpiry,
            confidence = confidence,
            reason = "Short PE beyond ST8- | ${String.format(Locale.US, "%.1f", distancePct)}% OTM | DTE=$daysToExpiry"
        )
    }

    /**
     * विक्री करणं सुरक्षित आहे का? - Regime + Structure + Strength + Band Spread तपासणी.
     */
    fun isSafeToSell(
        indexSt: MultiSuperTrendResult,
        regime: MarketRegime,
        structure: MarketStructure,
        strength: StrengthMetrics
    ): Boolean {
        // [A] Regime: फक्त sideways किंवा कमी volatility
        val regimeOk = regime in listOf(MarketRegime.SIDEWAYS, MarketRegime.LOW_VOLATILITY)

        // [B] Structure: Expansion किंवा break असेल तर विकू नका
        val structureOk = structure !in listOf(
            MarketStructure.EXPANSION,
            MarketStructure.STRUCTURE_BREAK
        )

        // [C] Trend strength: जोरात trend असेल तर विकू नका
        val strengthOk = strength.acceleration < 1.2 && strength.trendStrength < 70

        // [D] Band compression: सर्व बँड्स जवळ जवळ असले पाहिजेत (flat market)
        val bands = listOf(indexSt.st2, indexSt.st3, indexSt.st4, indexSt.st5, indexSt.st6, indexSt.st7, indexSt.st8)
        val bandSpread = bands.maxOf { it.upperBand } - bands.minOf { it.lowerBand }
        val spreadOk = bandSpread < (indexSt.master.value * 0.08) // 8% पेक्षा कमी

        return regimeOk && structureOk && strengthOk && spreadOk
    }

    // ─── मदतनीस फंक्शन्स ─────────────────────────────────────────────

    private fun roundToTick(price: Double, tick: Double): Double {
        return round(price / tick) * tick
    }

    private fun estimatePremium(
        strike: Double,
        dte: Int,
        vix: Double,
        isCall: Boolean
    ): Double {
        // साधी Black-Scholes सारखी गणना (screening साठी)
        val dteSafe = if (dte <= 0) 1 else dte
        val timeFactor = sqrt(dteSafe / 365.0)
        val volComponent = (vix / 100.0) * strike * timeFactor
        return volComponent * if (isCall) 0.4 else 0.35
    }

    private fun calculateConfidence(
        indexSt: MultiSuperTrendResult,
        distancePct: Double,
        dte: Int,
        vix: Double
    ): Int {
        var score = 50

        // Distance: जास्त दूर = जास्त सुरक्षित
        score += when {
            distancePct > 5.0 -> 25
            distancePct > 3.5 -> 20
            distancePct > 2.5 -> 10
            else -> 0
        }

        // DTE: 7-14 दिवस हा sweet spot
        score += when (dte) {
            in 7..14 -> 15
            in 4..6 -> 10
            in 15..21 -> 5
            else -> -5 // खूप जवळ किंवा खूप दूर
        }

        // VIX: कमी असेल तर चांगलं
        score += when {
            vix < 15 -> 10
            vix < 20 -> 5
            vix < 25 -> 0
            else -> -10
        }

        // Band alignment
        if (indexSt.master.alignmentScore >= 6) score += 10

        return score.coerceIn(0, 100)
    }
}
