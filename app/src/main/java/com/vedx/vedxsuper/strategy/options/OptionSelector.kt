package com.vedx.vedxsuper.strategy.options

import com.vedx.vedxsuper.model.market.OptionChain
import com.vedx.vedxsuper.model.market.OptionStrike
import kotlin.math.abs
import kotlin.math.round

enum class OptionType { CE, PE }
enum class StrikePreference { ATM, ITM1, ITM2, OTM1, OTM2 }

class OptionSelector {

    /**
     * Finds the At-The-Money (ATM) strike price based on current spot price.
     * Nifty: Intervals of 50
     * BankNifty: Intervals of 100
     */
    fun getATMStrike(spotPrice: Double, symbol: String): Double {
        val interval = getStrikeInterval(symbol)
        return (round(spotPrice / interval) * interval)
    }

    /**
     * Selects a specific strike based on preference (ATM, ITM, OTM).
     * Rule:
     * CE ITM is below ATM.
     * PE ITM is above ATM.
     */
    fun selectStrike(
        spotPrice: Double,
        symbol: String,
        type: OptionType,
        preference: StrikePreference = StrikePreference.ATM
    ): Double {
        val atm = getATMStrike(spotPrice, symbol)
        val interval = getStrikeInterval(symbol)

        return when (preference) {
            StrikePreference.ATM -> atm
            StrikePreference.ITM1 -> if (type == OptionType.CE) atm - interval else atm + interval
            StrikePreference.ITM2 -> if (type == OptionType.CE) atm - (2 * interval) else atm + (2 * interval)
            StrikePreference.OTM1 -> if (type == OptionType.CE) atm + interval else atm - interval
            StrikePreference.OTM2 -> if (type == OptionType.CE) atm + (2 * interval) else atm - (2 * interval)
        }
    }

    /**
     * Identifies the best option contract to trade based on SuperTrend direction.
     */
    fun getTargetOption(
        trend: Int, // 1 for Up, -1 for Down
        spotPrice: Double,
        symbol: String,
        preference: StrikePreference = StrikePreference.ATM
    ): Pair<OptionType, Double> {
        val type = if (trend == 1) OptionType.CE else OptionType.PE
        val strike = selectStrike(spotPrice, symbol, type, preference)
        return Pair(type, strike)
    }

    private fun getStrikeInterval(symbol: String): Double {
        return when {
            symbol.contains("BANKNIFTY", ignoreCase = true) -> 100.0
            symbol.contains("SENSEX", ignoreCase = true) -> 100.0
            symbol.contains("NIFTY", ignoreCase = true) -> 50.0
            symbol.contains("FINNIFTY", ignoreCase = true) -> 50.0
            else -> 100.0
        }
    }

    /**
     * Analyzes PCR (Put-Call Ratio) from the option chain.
     * PCR > 1.2 is Bullish
     * PCR < 0.7 is Bearish
     */
    fun analyzePCR(chain: OptionChain): String {
        return when {
            chain.pcr > 1.2 -> "BULLISH"
            chain.pcr < 0.7 -> "BEARISH"
            else -> "SIDEWAYS"
        }
    }

    /**
     * [FIXED] Point 2: Resolves symbol strings to Angel One Tokens.
     * Note: In a real app, this should fetch from a JSON/Database mapper.
     * For now, this handles index mapping as a fallback.
     */
    fun resolveToken(symbol: String): String? {
        return when (symbol) {
            "NIFTY" -> "26000"
            "BANKNIFTY" -> "26009"
            "FINNIFTY" -> "26037"
            "SENSEX" -> "1"
            else -> symbol // Return symbol itself for options/others
        }
    }
}
