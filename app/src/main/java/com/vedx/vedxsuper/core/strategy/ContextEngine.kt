package com.vedx.vedxsuper.core.strategy

import com.vedx.vedxsuper.data.MarketContext
import com.vedx.vedxsuper.data.Regimes
import com.vedx.vedxsuper.data.MarketBreadth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * V4 ContextEngine
 * Aggregates PCR, VIX, OI Patterns and Volume to provide market context.
 * Enhanced with more granular market regime classification.
 */
class ContextEngine {
    private val _context = MutableStateFlow(MarketContext(15.0, 1.0, Regimes.SIDEWAY, 20.0, null))
    val context = _context.asStateFlow()

    fun updateVix(vix: Double) {
        _context.value = _context.value.copy(vix = vix)
    }

    fun updatePcr(pcr: Double) {
        _context.value = _context.value.copy(pcr = pcr)
    }

    fun updateContext(adx: Double, masterTrend: Byte, bullCount: Int, bearCount: Int, isCompressed: Boolean) {
        val regime = classifyRegime(adx, masterTrend, bullCount, bearCount, isCompressed)
        _context.value = _context.value.copy(regime = regime, adx = adx)
    }

    private fun classifyRegime(adx: Double, trend: Byte, bull: Int, bear: Int, compressed: Boolean): Regimes {
        return when {
            compressed -> Regimes.VOLATILE
            adx > 25 && trend == 1.toByte() && bull >= 6 -> Regimes.TRENDING_UP
            adx > 25 && trend == (-1).toByte() && bear >= 6 -> Regimes.TRENDING_DOWN
            adx > 20 && (bull == 4 || bear == 4) -> Regimes.REVERSAL
            adx > 30 && (bull == 7 || bear == 7) -> Regimes.BREAKOUT
            else -> Regimes.SIDEWAY
        }
    }

    fun updateBreadth(breadth: MarketBreadth) {
        _context.value = _context.value.copy(marketBreadth = breadth)
    }
}
