package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.database.CandleDao
import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.engine.BacktestEngine
import com.vedx.vedxsuper.strategy.engine.BacktestResult
import com.vedx.vedxsuper.strategy.engine.InstitutionalStrategyEngine
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class BacktestViewModel(
    private val candleDao: CandleDao,
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val _result = MutableStateFlow<BacktestResult?>(null)
    val result = _result.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    fun runBacktest() {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val backtestResult = withContext(Dispatchers.Default) {
                    val timeframe = settingsManager.getAnalysisTimeframe()
                    val strategy = InstitutionalStrategyEngine("NIFTY", timeframe = timeframe)
                    val engine = BacktestEngine(strategy)
                    
                    // [UPDATED] Take only the last 2 days of data for a quick accurate test (approx 800 candles)
                    val savedIndex = candleDao.getLastCandles("NIFTY", 800).reversed()
                    
                    val indexCandles: List<Candle>
                    val optionCandles: Map<String, List<Candle>>

                    if (savedIndex.size > 100) {
                        // Use REAL data (Last 2 days)
                        indexCandles = savedIndex.map { it.toModel() }
                        
                        // Try to find REAL saved options in the DB (Last 2 days)
                        val realOptionMap = mutableMapOf<String, List<Candle>>()
                        val allSymbols = candleDao.getSymbols()
                        
                        var count = 0
                        for (sym in allSymbols) {
                            if (sym.contains("CE") || sym.contains("PE")) {
                                val savedOpt = candleDao.getLastCandles(sym, 800).reversed()
                                if (savedOpt.size > 50) {
                                    realOptionMap[sym] = savedOpt.map { it.toModel() }
                                    count++
                                    if (count >= 10) break
                                }
                            }
                        }

                        if (realOptionMap.isNotEmpty()) {
                            optionCandles = realOptionMap
                        } else {
                            // Blend real index with synthetic options if no real option history
                            // IMPORTANT: Match timestamps exactly with index candles
                            optionCandles = mapOf(
                                "NIFTY_CE" to generateSyntheticOptions(indexCandles, 200.0, 0.95),
                                "NIFTY_PE" to generateSyntheticOptions(indexCandles, 200.0, -0.95)
                            )
                        }
                    } else {
                        // 2. Generate HIGH-QUALITY Institutional Mock Data if no real data
                        val dataPoints = 1000 
                        indexCandles = generateMockCandles(25000.0, dataPoints)
                        optionCandles = mapOf(
                            "NIFTY_CE" to generateSyntheticOptions(indexCandles, 200.0, 0.98),
                            "NIFTY_PE" to generateSyntheticOptions(indexCandles, 200.0, -0.98)
                        )
                    }
                    
                    engine.run(indexCandles, optionCandles)
                }
                _result.value = backtestResult
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun com.vedx.vedxsuper.database.CandleEntity.toModel(): Candle {
        return Candle(
            timestamp = timestamp,
            open = open,
            high = high,
            low = low,
            close = close,
            isComplete = true
        )
    }

    private fun generateSyntheticOptions(indexCandles: List<Candle>, startPrice: Double, correlation: Double): List<Candle> {
        val options = mutableListOf<Candle>()
        var currentPrice = startPrice
        val random = Random()
        
        indexCandles.forEachIndexed { i, index ->
            val prevIndexClose = if (i > 0) indexCandles[i-1].close else index.open
            val indexChangePerc = (index.close - prevIndexClose) / prevIndexClose
            
            // Premium moves by index change * correlation * leverage (simplified)
            val leverage = 5.0 
            val change = currentPrice * indexChangePerc * correlation * leverage
            val noise = random.nextGaussian() * (currentPrice * 0.005)
            
            val open = currentPrice
            val close = (open + change + noise).coerceAtLeast(1.0)
            val high = maxOf(open, close) * 1.002
            val low = minOf(open, close) * 0.998
            
            options.add(Candle(index.timestamp, open, high, low, close, isComplete = true))
            currentPrice = close
        }
        return options
    }

    private fun generateMockCandles(startPrice: Double, count: Int, correlation: Double = 1.0): List<Candle> {
        val candles = mutableListOf<Candle>()
        var currentPrice = startPrice
        val random = Random()
        val startTime = System.currentTimeMillis() - (count * 60 * 1000L) // 1-minute interval for backtest stability
        
        var trendBias = 0.0
        var biasDuration = 0

        for (i in 0 until count) {
            if (biasDuration <= 0) {
                trendBias = (random.nextDouble() - 0.5) * 0.001 
                biasDuration = 50 + random.nextInt(100)
            }
            biasDuration--

            val noise = random.nextGaussian() * (startPrice * 0.0005)
            val change = (noise + (startPrice * trendBias)) * correlation
            
            val open = currentPrice
            val close = open + change
            val high = maxOf(open, close) + (random.nextDouble() * startPrice * 0.0002)
            val low = minOf(open, close) - (random.nextDouble() * startPrice * 0.0002)
            
            candles.add(Candle(
                timestamp = startTime + (i * 60 * 1000L),
                open = open,
                high = high,
                low = low,
                close = close,
                isComplete = true
            ))
            currentPrice = close
        }
        return candles
    }
}
