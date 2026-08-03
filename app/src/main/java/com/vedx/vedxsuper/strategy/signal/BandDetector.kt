package com.vedx.vedxsuper.strategy.signal

import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import kotlin.math.abs

data class BandInfo(
    val currentBand: Int,
    val isInZone: Boolean,
    val isTouch: Boolean,
    val isRejection: Boolean,
    val isBreakout: Boolean,
    val role: String // SUPPORT or RESISTANCE
)

class BandDetector {

    fun detect(price: Double, st: MultiSuperTrendResult, isIndex: Boolean, vix: Double): BandInfo {
        val bands = mapOf(
            2 to st.st2, 3 to st.st3, 4 to st.st4, 5 to st.st5, 
            6 to st.st6, 7 to st.st7, 8 to st.st8
        )

        var activeBand = 0
        var minDiff = Double.MAX_VALUE
        var role = "NEUTRAL"

        // [FIXED LOGIC] Identify the closest band and its dynamic role (Flip Logic)
        for ((idx, band) in bands) {
            val diff = abs(price - band.value)
            if (diff < minDiff) {
                minDiff = diff
                activeBand = idx
                // If price is below the band value, the band is acting as Resistance (Red Line)
                // If price is above, it's acting as Support (Green Line)
                role = if (price < band.value) "RESISTANCE" else "SUPPORT"
            }
        }

        // [FIXED] Percentage-based Zone: Scales with Index Price and VIX
        val threshold = if (isIndex) {
            // Formula: Price * (VIX / 20000).
            // Nifty 24k @ VIX 15 = ~18 pts | Sensex 80k @ VIX 15 = ~60 pts
            (price * (vix / 20000.0)).coerceIn(price * 0.0004, price * 0.0015)
        } else {
            (price * 0.025).coerceIn(1.0, 10.0) // 2.5% for Options
        }

        val isTouch = minDiff <= threshold

        // Rejection Logic: Price touches Resistance and stays below, or touches Support and stays above
        val isRejection = isTouch && ((role == "RESISTANCE" && price < bands[activeBand]!!.value) ||
                                     (role == "SUPPORT" && price > bands[activeBand]!!.value))

        return BandInfo(
            currentBand = activeBand,
            isInZone = minDiff <= (threshold * 3),
            isTouch = isTouch,
            isRejection = isRejection,
            isBreakout = !isTouch && !isRejection && minDiff > threshold,
            role = role
        )
    }
}
