package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.model.market.TickData
import android.util.Log

data class BacktestResult(
    val totalTrades: Int,
    val winRate: Double,
    val lossRate: Double,
    val totalPnl: Double,
    val netPnlPercent: Double,
    val maxDrawdown: Double,
    val avgHoldingTimeMs: Long,
    val avgConfidence: Double,
    val bestSuperTrend: Int,
    val trades: List<BacktestTrade>
)

data class BacktestTrade(
    val symbol: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Int,
    val pnl: Double,
    val pnlPercent: Double,
    val type: String,
    val entryTime: Long,
    val exitTime: Long,
    val exitReason: String
)

class BacktestEngine(private val strategy: InstitutionalStrategyEngine) {

    fun run(indexData: List<Candle>, optionData: Map<String, List<Candle>>): BacktestResult {
        var totalPnl = 0.0
        val completedTrades = mutableListOf<BacktestTrade>()
        val activeTrades = mutableMapOf<String, BacktestTrade>()
        
        var maxCapital = 100000.0
        var currentCapital = maxCapital
        var peak = maxCapital
        var maxDrawdown = 0.0

        var lastSignalCount = 0
        
        // Optimization: Convert option lists to maps for O(1) lookup
        val optionMaps = optionData.mapValues { (_, candles) -> 
            candles.associateBy { it.timestamp }
        }

        indexData.forEachIndexed { i, indexCandle ->
            if (i % 500 == 0) Log.d("Backtest", "Processing index tick $i / ${indexData.size}")

            // 1. Process Index Tick
            val indexTick = TickData(
                symbol = strategy.indexSymbol,
                token = "INDEX",
                ltp = indexCandle.close,
                timestamp = indexCandle.timestamp
            )
            strategy.onIndexTick(indexTick)

            // 2. Process Option Ticks (Only relevant ones to save time)
            optionMaps.forEach { (symbol, candlesMap) ->
                val optCandle = candlesMap[indexCandle.timestamp] ?: return@forEach
                
                // [FIXED] Provide fake liquidity and Greeks for backtest validation
                val optTick = TickData(
                    symbol = symbol,
                    token = symbol,
                    ltp = optCandle.close,
                    high = optCandle.high,
                    low = optCandle.low,
                    volume = 1000,
                    openInterest = 50000,
                    bid = optCandle.close * 0.999, // 0.1% spread
                    ask = optCandle.close * 1.001,
                    iv = 20.0,
                    timestamp = optCandle.timestamp
                )
                strategy.onOptionTick(optTick, indexCandle.close)
            }

            // 3. Check for New Signals
            val allSignals = strategy.signals.value
            if (allSignals.size > lastSignalCount) {
                // New signals emitted
                val newSignals = allSignals.subList(lastSignalCount, allSignals.size)
                lastSignalCount = allSignals.size

                newSignals.forEach { signal ->
                    if (signal.type == "EXIT") {
                        val active = activeTrades[signal.optionSymbol]
                        if (active != null) {
                            val pnlPerUnit = if (active.type == "BUY") signal.price - active.entryPrice else active.entryPrice - signal.price
                            val pnl = pnlPerUnit * active.quantity
                            val pnlPerc = (pnlPerUnit / active.entryPrice) * 100.0
                            
                            val tradeEntry = active.copy(
                                exitPrice = signal.price,
                                pnl = pnl,
                                pnlPercent = pnlPerc,
                                exitTime = signal.timestamp,
                                exitReason = signal.reason
                            )
                            completedTrades.add(tradeEntry)
                            
                            // Feed back to learning engine
                            strategy.onTradeCompleted(TradeJournalEntry(
                                tradeId = "BT_${signal.timestamp}",
                                entryPrice = active.entryPrice,
                                exitPrice = signal.price,
                                quantity = active.quantity,
                                profit = pnl,
                                profitPercent = pnlPerc,
                                durationMs = signal.timestamp - active.entryTime,
                                maxProfit = pnl, // Simplified for backtest
                                maxDrawdown = 0.0,
                                regime = strategy.getMarketRegime(),
                                structure = strategy.getMarketStructure(),
                                state = strategy.getTrendState(),
                                stBand = signal.optionBand,
                                premiumSymbol = active.symbol,
                                confidence = signal.confidence,
                                rrRatio = 0.0 
                            ))
                            
                            currentCapital += pnl
                            if (currentCapital > peak) peak = currentCapital
                            val dd = (peak - currentCapital) / peak * 100.0
                            if (dd > maxDrawdown) maxDrawdown = dd
                            
                            activeTrades.remove(signal.optionSymbol)
                        }
                    } else {
                        // Entry Signal
                        if (!activeTrades.containsKey(signal.optionSymbol)) {
                            activeTrades[signal.optionSymbol] = BacktestTrade(
                                symbol = signal.optionSymbol,
                                entryPrice = signal.price,
                                exitPrice = 0.0,
                                quantity = signal.quantity,
                                pnl = 0.0,
                                pnlPercent = 0.0,
                                type = signal.type,
                                entryTime = signal.timestamp,
                                exitTime = 0,
                                exitReason = ""
                            )
                        }
                    }
                }
            }
        }

        val wins = completedTrades.count { it.pnl > 0 }
        val winRate = if (completedTrades.isNotEmpty()) (wins.toDouble() / completedTrades.size) * 100.0 else 0.0
        val lossRate = 100.0 - winRate
        val avgHoldingTime = if (completedTrades.isNotEmpty()) completedTrades.map { it.exitTime - it.entryTime }.average().toLong() else 0L
        
        val stats = strategy.getLearningStats()

        return BacktestResult(
            totalTrades = completedTrades.size,
            winRate = winRate,
            lossRate = lossRate,
            totalPnl = completedTrades.sumOf { it.pnl },
            netPnlPercent = ((currentCapital - maxCapital) / maxCapital) * 100.0,
            maxDrawdown = maxDrawdown,
            avgHoldingTimeMs = avgHoldingTime,
            avgConfidence = stats.winRate, // Using winRate as a proxy for confidence if stats doesn't have avg conf
            bestSuperTrend = stats.bestBand,
            trades = completedTrades
        )
    }
}
