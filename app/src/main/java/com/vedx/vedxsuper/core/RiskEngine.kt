package com.vedx.vedxsuper.core

class RiskEngine {
    private val maxLoss = -5000000 // -50k in cents
    private val maxExp = 20000000  // 2L in cents
    private val maxTrades = 10
    private var pnl = 0
    private var exp = 0
    private var count = 0
    
    fun canTrade(entryVal: Int, sl: Int): Boolean {
        if (pnl < maxLoss || exp + entryVal > maxExp || count >= maxTrades || sl <= 0) return false
        return true
    }
    fun onEntry(v: Int) { exp += v; count++ }
    fun onExit(p: Int) { pnl += p; exp = (exp - p).coerceAtLeast(0) }
    fun reset() { pnl = 0; exp = 0; count = 0 }
    fun stats() = "P&L: ₹${pnl/100} | Exp: ₹${exp/100} | #$count"
}
