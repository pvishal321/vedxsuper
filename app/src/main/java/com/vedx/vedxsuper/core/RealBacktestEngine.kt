package com.vedx.vedxsuper.core

import com.vedx.vedxsuper.api.AngelDataFetcher
import com.vedx.vedxsuper.api.NseBhavcopy
import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * RealBacktestEngine
 * Production-grade backtesting with Slippage, Gap handling, and realistic charges.
 */
class RealBacktestEngine(private val core: UltraNeuralCore) {
    
    data class BacktestResult(
        val totalTrades: Int,
        val wins: Int,
        val losses: Int,
        val winRate: Float,
        val profitFactor: Float,
        val maxDrawdownPct: Float,
        val sharpeRatio: Float,
        val netPnl: Double,
        val avgWin: Double,
        val avgLoss: Double,
        val totalCharges: Double,
        val equityCurve: List<Pair<Long, Double>>,
        val tradeLog: List<TradeRecord>
    )
    
    data class TradeRecord(
        val symbol: String,
        val entryTime: Long,
        val exitTime: Long,
        val entryPrice: Double,
        val exitPrice: Double,
        val target: Double,
        val stopLoss: Double,
        val pnl: Double,
        val charges: Double,
        val status: String,
        val reason: String
    )
    
    suspend fun runWithCandles(candles: List<Candle>, symbol: String = "NIFTY"): BacktestResult = withContext(Dispatchers.Default) {
        val trades = mutableListOf<TradeRecord>()
        var capital = 100000.0
        var peak = capital
        var maxDD = 0.0
        val equity = mutableListOf<Pair<Long, Double>>()
        var totalCharges = 0.0
        
        val slippagePct = 0.001 // 0.1% slippage
        
        var position: Signal? = null
        var posEntryTime = 0L
        var posEntryPrice = 0.0
        
        if (candles.size > 50) {
            core.initialize(candles.take(50))
        }
        
        candles.drop(50).forEach { candle ->
            val high = candle.high.rupees
            val low = candle.low.rupees
            val open = candle.open.rupees
            val close = candle.close.rupees
            val ts = candle.timestamp
            
            core.onIndexTick(symbol, close, candle.volume, ts)
            val latest = core.signals.value.lastOrNull()
            
            // 1. Entry Logic (Realistic Slippage)
            if (position == null && latest != null && latest.isEntry) {
                position = latest
                posEntryTime = ts
                // Apply slippage on entry
                posEntryPrice = if (latest.action == Actions.BUY) {
                    latest.entryPrice.rupees * (1 + slippagePct)
                } else {
                    latest.entryPrice.rupees * (1 - slippagePct)
                }
            }
            
            // 2. Exit Logic (High/Low Check + Gap Handling)
            position?.let { pos ->
                val isCall = pos.action == Actions.BUY
                val sl = pos.stopLoss.rupees
                val target = pos.target.rupees
                
                var hitSL = false
                var hitTarget = false
                var exitPrice = close
                var status = "TIME_EXIT"
                
                if (isCall) {
                    if (low <= sl) {
                        hitSL = true
                        exitPrice = min(sl, open) // Gap down handle
                        status = "LOSS"
                    } else if (high >= target) {
                        hitTarget = true
                        exitPrice = max(target, open) // Gap up handle
                        status = "WIN"
                    }
                } else {
                    if (high >= sl) {
                        hitSL = true
                        exitPrice = max(sl, open) // Gap up handle
                        status = "LOSS"
                    } else if (low <= target) {
                        hitTarget = true
                        exitPrice = min(target, open) // Gap down handle
                        status = "WIN"
                    }
                }
                
                val timeExit = (ts - posEntryTime) > 15 * 60 * 1000
                if (hitSL || hitTarget || timeExit) {
                    // Apply slippage on exit
                    val finalExitPrice = if (isCall) exitPrice * (1 - slippagePct) else exitPrice * (1 + slippagePct)
                    
                    val grossPnl = if (isCall) (finalExitPrice - posEntryPrice) * pos.quantity else (posEntryPrice - finalExitPrice) * pos.quantity
                    val charges = calculateCharges(posEntryPrice, finalExitPrice, pos.quantity)
                    val netPnl = grossPnl - charges
                    
                    capital += netPnl
                    totalCharges += charges
                    if (capital > peak) peak = capital
                    val dd = (peak - capital) / peak * 100
                    if (dd > maxDD) maxDD = dd
                    
                    trades.add(TradeRecord(pos.symbol.value, posEntryTime, ts, posEntryPrice, finalExitPrice, target, sl, netPnl, charges, status, pos.reason))
                    // In backtest, we just track the PnL locally in capital
                    equity.add(ts to capital)
                    position = null
                }
            }
        }
        
        val wins = trades.count { it.status == "WIN" }
        val losses = trades.size - wins
        val winRate = if (trades.isNotEmpty()) wins.toFloat() / trades.size * 100 else 0f
        val grossProfit = trades.filter { it.pnl > 0 }.sumOf { it.pnl }
        val grossLoss = trades.filter { it.pnl < 0 }.sumOf { abs(it.pnl) }
        val pf = if (grossLoss > 0) (grossProfit / grossLoss).toFloat() else 0f
        
        val returns = trades.map { it.pnl }
        val sharpe = calculateSharpe(returns)
        
        BacktestResult(
            totalTrades = trades.size, wins = wins, losses = losses, winRate = winRate,
            profitFactor = pf, maxDrawdownPct = maxDD.toFloat(), sharpeRatio = sharpe,
            netPnl = capital - 100000.0, avgWin = if (wins > 0) grossProfit / wins else 0.0,
            avgLoss = if (losses > 0) grossLoss / losses else 0.0,
            totalCharges = totalCharges,
            equityCurve = equity, tradeLog = trades
        )
    }

    private fun calculateCharges(entry: Double, exit: Double, qty: Int): Double {
        val turnover = (entry + exit) * qty
        val brokerage = min(40.0, turnover * 0.0003) // ₹20 per side max
        val stt = exit * qty * 0.00125 // 0.125% on sell side for options
        val txCharges = turnover * 0.0005 // Approx transaction charges
        val sebi = turnover * 0.000001
        val gst = (brokerage + txCharges) * 0.18
        return brokerage + stt + txCharges + sebi + gst
    }

    private fun calculateSharpe(returns: List<Double>): Float {
        if (returns.size < 5) return 0f
        val avg = returns.average()
        val stdDev = sqrt(returns.map { (it - avg).pow(2) }.average())
        return if (stdDev > 0) (avg / stdDev * sqrt(252.0)).toFloat() else 0f
    }
    
    suspend fun runAngelBacktest(token: String, days: Int = 5): BacktestResult {
        val result = AngelDataFetcher { token }.fetchLastDays(days)
        val candles = result.getOrThrow()
        if (candles.isEmpty()) throw IllegalStateException("No data from Angel One")
        return runWithCandles(candles)
    }
    
    suspend fun runNseBacktest(date: java.util.Date = java.util.Date()): BacktestResult {
        val result = NseBhavcopy.fetch(date)
        val candles = result.getOrThrow()
        if (candles.isEmpty()) throw IllegalStateException("No NSE bhavcopy data")
        return runWithCandles(candles)
    }
}
