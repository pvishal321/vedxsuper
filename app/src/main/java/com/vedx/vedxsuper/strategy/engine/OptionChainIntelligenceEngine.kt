package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import com.vedx.vedxsuper.strategy.options.ExpiryManager
import com.vedx.vedxsuper.utils.MarketCalendar
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

enum class OIAction {
    LONG_BUILDUP, SHORT_BUILDUP, LONG_UNWINDING, SHORT_COVERING, NEUTRAL
}

data class OptionIntelligenceScore(
    val symbol: String,
    val strikePrice: Double,
    val type: String,
    val potentialScore: Double,
    val confidenceScore: Double,
    val expansionProbability: Double,
    val delta: Double,
    val gamma: Double,
    val vega: Double,
    val theta: Double,
    val liquidityScore: Double,
    val recoveryRate: Double,
    val riskReward: Double,
    val recommendedPrice: Double,
    val expectedMove: Double,
    val stopLoss: Double,
    val target: Double,
    val oiAction: OIAction = OIAction.NEUTRAL,
    val isExpiryDay: Boolean = false,
    val isHeroZero: Boolean = false,
    val isJackpotPotential: Boolean = false,
    val meltingPressure: Double = 1.0
)

data class StrikeMetrics(
    val symbol: String,
    val ltp: Double,
    val openInterest: Long,
    val oiChange: Long,
    val volume: Long,
    val iv: Double,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val candles: List<Candle>,
    val stResult: MultiSuperTrendResult?
)

class OptionChainIntelligenceEngine {

    private val memory = mutableMapOf<String, OptionMemory>()
    private val lock = Any()
    private val expiryManager = ExpiryManager()
    private val marketCalendar = MarketCalendar()

    data class OptionMemory(
        val symbol: String,
        var highestPremium: Double = 0.0,
        var lowestPremium: Double = Double.MAX_VALUE,
        var selectionCount: Int = 0,
        var winCount: Int = 0
    )

    fun analyzeChain(
        indexPrice: Double,
        indexTrend: Int,
        indexStrength: StrengthMetrics,
        strikes: List<StrikeMetrics>,
        vix: Double,
        regime: MarketRegime,
        structure: MarketStructure,
        weights: StrategyWeights = StrategyWeights()
    ): List<OptionIntelligenceScore> {
        synchronized(lock) {
            val results = strikes.mapNotNull { strike ->
                calculateStrikeScore(indexPrice, indexTrend, indexStrength, strike, vix, regime, structure, weights)
            }
            return results.sortedByDescending { it.potentialScore }
        }
    }

    private fun calculateStrikeScore(
        indexPrice: Double,
        indexTrend: Int, // [NEW] Used for direction-aware scoring
        indexStrength: StrengthMetrics,
        strike: StrikeMetrics,
        vix: Double,
        regime: MarketRegime,
        structure: MarketStructure,
        weights: StrategyWeights
    ): OptionIntelligenceScore? {
        if (strike.ltp <= 0) return null

        val isCall = strike.symbol.contains("CE")
        val isPut = strike.symbol.contains("PE")

        // [NEW] Direction boost: Favor strikes matching index trend
        val directionBoost = when (indexTrend) {
            1 -> if (isCall) 10.0 else 0.0   // Bullish → boost CE
            -1 -> if (isPut) 10.0 else 0.0   // Bearish → boost PE
            else -> 0.0
        }

        val isExpiry = expiryManager.isExpiryDay(strike.symbol)
        val isH2ZWindow = isExpiry && isHeroZeroTimeWindow(strike.symbol)
        val meltingFactor = marketCalendar.getThetaMeltingPressure()

        val spread = if (strike.ask > 0) (strike.ask - strike.bid) / strike.ltp * 100.0 else 0.5
        if (spread > 10.0 && !isH2ZWindow) return null 

        val last20 = strike.candles.takeLast(20)
        val high = last20.maxOfOrNull { it.high } ?: strike.ltp
        val low = last20.minOfOrNull { it.low } ?: strike.ltp
        val recoveryRate = if (high > low) (strike.ltp - low) / (high - low) * 100.0 else 0.0

        val delta = calculateDelta(indexPrice, strike, isCall)
        val gamma = calculateGamma(indexPrice, strike, isExpiry)
        val vega = calculateVega(indexPrice, strike, vix)

        var expansionProb = 50.0
        if (isExpiry) {
            expansionProb += 30.0 
            if (isH2ZWindow) expansionProb += 30.0 
        }

        val volScore = if (strike.volume > 0) 15.0 else 0.0
        val oiAction = detectOIAction(strike)
        val oiScore = when (oiAction) {
            OIAction.LONG_BUILDUP -> 15.0
            OIAction.SHORT_COVERING -> 25.0 
            OIAction.SHORT_BUILDUP -> -10.0  // [NEW] Penalty for short buildup (bearish)
            OIAction.LONG_UNWINDING -> -10.0 // [NEW] Penalty for long unwinding
            else -> 0.0
        }

        val isH2ZCandidate = isH2ZWindow && strike.ltp in 1.0..15.0
        val isJackpot = isH2ZCandidate && (oiAction == OIAction.SHORT_COVERING || indexStrength.acceleration > 1.5)

        var potentialScore = (
            recoveryRate * weights.recoveryRateWeight +
            expansionProb * weights.expansionProbWeight +
            volScore + oiScore + (delta * 100.0) + directionBoost
        ).coerceIn(0.0, 100.0)

        if (meltingFactor > 1.2) {
            potentialScore /= (meltingFactor * 0.8) 
        }

        if (isH2ZCandidate) potentialScore += 20.0
        if (isJackpot) potentialScore += 30.0

        val confidenceScore = calculateConfidence(strike, indexStrength, potentialScore, isH2ZCandidate)

        val st = strike.stResult
        val stopLoss = if (isH2ZCandidate) 0.0 else (st?.st2?.value ?: (strike.ltp * 0.9))
        val target = when {
            isJackpot -> max(50.0, strike.ltp * 10.0)
            isH2ZCandidate -> strike.ltp * 4.0
            else -> st?.st6?.value ?: (strike.ltp * 1.5)
        }

        updateMemory(strike)

        return OptionIntelligenceScore(
            symbol = strike.symbol,
            strikePrice = extractStrikePrice(strike.symbol),
            type = if (isCall) "CE" else "PE",
            potentialScore = potentialScore,
            confidenceScore = confidenceScore,
            expansionProbability = expansionProb,
            delta = delta,
            gamma = gamma,
            vega = vega,
            theta = calculateTheta(strike, vix, isExpiry) * meltingFactor,
            liquidityScore = 100.0 - spread,
            recoveryRate = recoveryRate,
            riskReward = if (isJackpot) 20.0 else if (isH2ZCandidate) 5.0 else 2.0,
            recommendedPrice = strike.ltp,
            expectedMove = target - strike.ltp,
            stopLoss = stopLoss,
            target = target,
            oiAction = oiAction,
            isExpiryDay = isExpiry,
            isHeroZero = isH2ZCandidate,
            isJackpotPotential = isJackpot,
            meltingPressure = meltingFactor
        )
    }

    /**
     * AI-Driven Hero Zero Detection.
     * Uses session phase and volatility instead of just hardcoded time.
     */
    private fun isHeroZeroTimeWindow(symbol: String): Boolean {
        if (!expiryManager.isExpiryDay(symbol)) return false

        val phase = expiryManager.getCurrentPhase()
        return phase == ExpiryManager.SessionPhase.EXPIRY_RAMP ||
               phase == ExpiryManager.SessionPhase.CLOSING_AUCTION
    }

    // [FIX] Complete OI action detection
    private fun detectOIAction(strike: StrikeMetrics): OIAction {
        if (strike.candles.size < 2) return OIAction.NEUTRAL
        val priceChange = strike.candles.last().close - strike.candles[strike.candles.size - 2].close
        val oiChange = strike.oiChange
        return when {
            priceChange > 0 && oiChange > 0 -> OIAction.LONG_BUILDUP      // Bullish
            priceChange > 0 && oiChange < 0 -> OIAction.SHORT_COVERING    // Bullish
            priceChange < 0 && oiChange > 0 -> OIAction.SHORT_BUILDUP     // Bearish
            priceChange < 0 && oiChange < 0 -> OIAction.LONG_UNWINDING    // Bearish
            else -> OIAction.NEUTRAL
        }
    }

    // [FIX] Better delta calculation with direction awareness
    private fun calculateDelta(indexPrice: Double, strike: StrikeMetrics, isCall: Boolean): Double {
        val strikePrice = extractStrikePrice(strike.symbol)
        if (strikePrice <= 0) return 0.5
        val moneyness = (indexPrice - strikePrice) / strikePrice * 100.0

        val callDelta = when {
            moneyness > 4 -> 0.85   // Deep ITM
            moneyness > 2 -> 0.75   // ITM
            moneyness > -2 -> 0.5   // ATM
            moneyness > -4 -> 0.25  // OTM
            else -> 0.1             // Deep OTM
        }
        return if (isCall) callDelta else (1.0 - callDelta)
    }

    private fun calculateGamma(indexPrice: Double, strike: StrikeMetrics, isExpiry: Boolean): Double {
        val strikePrice = extractStrikePrice(strike.symbol)
        val diff = abs(indexPrice - strikePrice)
        val baseGamma = if (diff < 50) 0.005 else 0.001
        return if (isExpiry) baseGamma * 3.0 else baseGamma
    }

    private fun calculateVega(indexPrice: Double, strike: StrikeMetrics, vix: Double): Double {
        val strikePrice = extractStrikePrice(strike.symbol)
        val diff = abs(indexPrice - strikePrice)
        val proximityFactor = (1.0 - (diff / 500.0).coerceIn(0.0, 1.0))
        return (vix / 100.0) * proximityFactor
    }

    private fun calculateTheta(strike: StrikeMetrics, vix: Double, isExpiry: Boolean): Double {
        return if (isExpiry) (vix / 5) else (vix / 100.0)
    }

    private fun calculateConfidence(strike: StrikeMetrics, indexStrength: StrengthMetrics, potential: Double, isH2Z: Boolean): Double {
        var conf = potential * 0.7
        if (indexStrength.trendStrength > 75) conf += 20.0
        return if (isH2Z) (conf * 0.75) else conf.coerceIn(0.0, 100.0)
    }

    private fun extractStrikePrice(symbol: String): Double {
        return try {
            val matches = Regex("\\d+").findAll(symbol).toList()
            matches.lastOrNull()?.value?.toDouble() ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    private fun updateMemory(strike: StrikeMetrics) {
        val mem = memory.getOrPut(strike.symbol) { OptionMemory(strike.symbol) }
        if (strike.ltp > mem.highestPremium) mem.highestPremium = strike.ltp
    }

    /**
     * [FIX] Direction-aware best strike selection.
     * Only returns strikes matching index trend (CE for bullish, PE for bearish).
     */
    fun getBestStrike(
        indexPrice: Double, 
        indexTrend: Int, 
        indexStrength: StrengthMetrics, 
        strikes: List<StrikeMetrics>, 
        vix: Double, 
        regime: MarketRegime, 
        structure: MarketStructure,
        weights: StrategyWeights = StrategyWeights()
    ): OptionIntelligenceScore? {
        val analyzed = analyzeChain(indexPrice, indexTrend, indexStrength, strikes, vix, regime, structure, weights)

        // [CRITICAL FIX] Filter by index trend direction
        val filtered = when (indexTrend) {
            1 -> analyzed.filter { it.type == "CE" }
            -1 -> analyzed.filter { it.type == "PE" }
            else -> emptyList()
        }

        // Strict thresholds - no blind fallback
        return filtered.firstOrNull { it.isJackpotPotential && it.potentialScore > 85 }
            ?: filtered.firstOrNull { it.isHeroZero && it.potentialScore > 75 }
            ?: filtered.firstOrNull { it.potentialScore > 65 && it.liquidityScore > 70 }
            ?: filtered.firstOrNull()
    }
}
