package com.vedx.vedxsuper.core

import com.vedx.vedxsuper.api.AngelDataFetcher
import com.vedx.vedxsuper.api.NseBhavcopy
import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import kotlin.math.*

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
        val status: String,
        val reason: String
    )
    
    suspend fun runWithCandles(candles: List<Candle>, symbol: String = "NIFTY"): BacktestResult = withContext(Dispatchers.Default) {
        val trades = mutableListOf<TradeRecord>()
        var capital = 100000.0
        var peak = capital
        var maxDD = 0.0
        val equity = mutableListOf<Pair<Long, Double>>()
        
        var position: Signal? = null
        var posEntryTime = 0L
        var posEntryPrice = 0.0
        
        if (candles.size > 50) {
            core.initialize(candles.take(50))
        }
        
        candles.drop(50).forEach { candle ->
            val price = candle.close.rupees
            val ts = candle.timestamp
            
            core.onIndexTick(symbol, price, candle.volume, ts)
            val latest = core.signals.value.lastOrNull()
            
            if (position == null && latest != null && latest.isEntry) {
                position = latest
                posEntryTime = ts
                posEntryPrice = latest.entryPrice.rupees
            }
            
            position?.let { pos ->
                val isCall = pos.action == Actions.BUY
                val hitSL = if (isCall) price <= pos.stopLoss.rupees else price >= pos.stopLoss.rupees
                val hitTarget = if (isCall) price >= pos.target.rupees else price <= pos.target.rupees
                val timeExit = (ts - posEntryTime) > 15 * 60 * 1000
                
                if (hitSL || hitTarget || timeExit) {
                    val pnl = if (isCall) (price - posEntryPrice) * pos.quantity else (posEntryPrice - price) * pos.quantity
                    capital += pnl
                    if (capital > peak) peak = capital
                    val dd = (peak - capital) / peak * 100
                    if (dd > maxDD) maxDD = dd
                    
                    val status = when {
                        hitTarget -> "WIN"
                        hitSL -> "LOSS"
                        else -> "TIME_EXIT"
                    }
                    
                    trades.add(TradeRecord(pos.symbol.value, posEntryTime, ts, posEntryPrice, price, pos.target.rupees, pos.stopLoss.rupees, pnl, status, pos.reason))
                    core.onTradeCompleted(pnl)
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
        
        BacktestResult(
            totalTrades = trades.size, wins = wins, losses = losses, winRate = winRate,
            profitFactor = pf, maxDrawdownPct = maxDD.toFloat(), sharpeRatio = 0f,
            netPnl = capital - 100000.0, avgWin = if (wins > 0) grossProfit / wins else 0.0,
            avgLoss = if (losses > 0) grossLoss / losses else 0.0,
            equityCurve = equity, tradeLog = trades
        )
    }
    
    suspend fun runAngelBacktest(token: String, days: Int = 5): BacktestResult {
        val candles = AngelDataFetcher(token).fetchLastDays(days)
        if (candles.isEmpty()) throw IllegalStateException("No data from Angel One")
        return runWithCandles(candles)
    }
    
    suspend fun runNseBacktest(date: java.util.Date = java.util.Date()): BacktestResult {
        val candles = NseBhavcopy.fetch(date)
        if (candles.isEmpty()) throw IllegalStateException("No NSE bhavcopy data")
        return runWithCandles(candles)
    }
}
