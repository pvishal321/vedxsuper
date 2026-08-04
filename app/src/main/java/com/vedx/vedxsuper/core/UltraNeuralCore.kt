package com.vedx.vedxsuper.core

import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

// ===== ULTRA-FAST SUPER TREND CALCULATOR =====
class FastST(private val multiplier: Float) {
    private var prevClose = 0.0
    private var prevUpper = Double.MAX_VALUE
    private var prevLower = 0.0
    private var prevTrend: Byte = 1
    
    fun reset() {
        prevClose = 0.0; prevUpper = Double.MAX_VALUE; prevLower = 0.0; prevTrend = 1
    }
    
    fun calculate(candles: List<Candle>): STResult? {
        if (candles.size < 11) return null
        val atr = calcATR(candles, 10)
        val close = candles.last().close.rupees
        val mid = (candles.last().high.rupees + candles.last().low.rupees) / 2.0
        val upper = mid + multiplier * atr
        val lower = mid - multiplier * atr
        
        val newUpper = if (close > prevUpper) upper else min(upper, prevUpper)
        val newLower = if (close < prevLower) lower else max(lower, prevLower)
        val trend = if (close > prevUpper) 1 else if (close < prevLower) (-1).toByte() else prevTrend
        
        prevClose = close; prevUpper = newUpper; prevLower = newLower; prevTrend = trend
        return STResult(if (trend == 1.toByte()) newLower else newUpper, newUpper, newLower, trend, atr)
    }
    
    private fun calcATR(candles: List<Candle>, period: Int): Double {
        var sum = 0.0
        val start = max(1, candles.size - period)
        for (i in start until candles.size) {
            val c = candles[i]; val p = candles[i-1]
            val tr1 = c.high.rupees - c.low.rupees
            val tr2 = abs(c.high.rupees - p.close.rupees)
            val tr3 = abs(c.low.rupees - p.close.rupees)
            sum += max(tr1, max(tr2, tr3))
        }
        return sum / (candles.size - start).coerceAtLeast(1)
    }
}

// ===== MULTI-BAND ENGINE =====
class MultiBandEngine {
    private val engines = arrayOf(FastST(2f), FastST(3f), FastST(4f), FastST(5f), FastST(6f), FastST(7f), FastST(8f))
    private val master = FastST(3f)
    
    fun calculate(candles: List<Candle>): MultiST? {
        if (candles.size < 15) return null
        val results = engines.map { it.calculate(candles) ?: return null }
        val m = master.calculate(candles) ?: return null
        return MultiST(results[0], results[1], results[2], results[3], results[4], results[5], results[6], m)
    }
    
    fun reset() { engines.forEach { it.reset() }; master.reset() }
}

// ===== CANDLE BUILDER (Ring buffer based) =====
class FastCandleBuilder(private val intervalMin: Int) {
    private val ticks = TickRingBuffer(2048)
    private val _candles = MutableStateFlow<List<Candle>>(emptyList())
    val candles = _candles.asStateFlow()
    private var currentOpen = 0
    private var currentHigh = 0
    private var currentLow = Int.MAX_VALUE
    private var currentVol = 0L
    private var candleStart = 0L
    private var lastClose = 0
    
    fun onTick(priceCents: Int, volume: Int, timestamp: Long) {
        ticks.push(priceCents, volume, timestamp, 0)
        if (candleStart == 0L) { candleStart = timestamp; currentOpen = priceCents }
        val intervalMs = intervalMin * 60_000L
        if (timestamp - candleStart >= intervalMs) {
            closeCandle()
            candleStart = timestamp
            currentOpen = priceCents
            currentHigh = priceCents
            currentLow = priceCents
            currentVol = volume.toLong()
        } else {
            if (priceCents > currentHigh) currentHigh = priceCents
            if (priceCents < currentLow) currentLow = priceCents
            currentVol += volume
        }
        lastClose = priceCents
    }
    
    private fun closeCandle() {
        val c = Candle(Price(currentOpen), Price(currentHigh), Price(currentLow), Price(lastClose), currentVol, candleStart, true)
        _candles.value = _candles.value + c
        if (_candles.value.size > 500) _candles.value = _candles.value.takeLast(300)
    }
    
    fun initialize(history: List<Candle>) { _candles.value = history }
}

// ===== ENHANCED AI CORE - 7 ST MATCH STRATEGY =====
class UltraNeuralCore(private val indexSymbol: Symbol) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val indexChannel = Channel<TickData>(Channel.CONFLATED)
    private val optionChannel = Channel<Pair<TickData, Double>>(Channel.CONFLATED)
    
    // 7 ST bands for Index
    private val indexCandles = FastCandleBuilder(15)
    private val indexST = MultiBandEngine()
    private var indexPrice = 0.0
    private var indexSTResult: MultiST? = null
    
    // 7 ST bands for each Option
    private class OptState(
        val candles: FastCandleBuilder = FastCandleBuilder(15),
        val st: MultiBandEngine = MultiBandEngine(),
        var lastLtp: Double = 0.0
    )
    private val options = ConcurrentHashMap<String, OptState>()
    
    // Signals
    private val _signals = MutableStateFlow<List<Signal>>(emptyList())
    val signals = _signals.asStateFlow()
    private var lastSignalTime = ConcurrentHashMap<String, Long>()
    
    // Risk & Learning
    private var consecutiveLosses = 0
    private var dailyPnl = 0.0
    private var exposure = 0.0
    private val weights = AIWeights()
    
    val lossCount: Int get() = consecutiveLosses

    init {
        scope.launch { for (t in indexChannel) processIndex(t) }
        scope.launch { for ((t, ip) in optionChannel) processOption(t, ip) }
    }
    
    fun onIndexTick(symbol: String, ltp: Double, vol: Long, ts: Long) {
        if (symbol != indexSymbol.value) return
        indexChannel.trySend(TickData(symbol, ltp, vol, ts))
    }
    
    fun onOptionTick(symbol: String, ltp: Double, vol: Long, ts: Long, indexPrice: Double) {
        optionChannel.trySend(TickData(symbol, ltp, vol, ts) to indexPrice)
    }
    
    private suspend fun processIndex(tick: TickData) {
        indexPrice = tick.ltp
        indexCandles.onTick(Price.from(tick.ltp).cents, tick.volume.toInt(), tick.ts)
        val candles = indexCandles.candles.value
        if (candles.size < 15) return
        indexSTResult = indexST.calculate(candles)
    }
    
    private suspend fun processOption(tick: TickData, idxPrice: Double) {
        val state = options.getOrPut(tick.symbol) { OptState() }
        state.lastLtp = tick.ltp
        state.candles.onTick(Price.from(tick.ltp).cents, tick.volume.toInt(), tick.ts)
        
        val optCandles = state.candles.candles.value
        if (optCandles.size < 15) return
        val optST = state.st.calculate(optCandles) ?: return
        val idxST = indexSTResult ?: return
        
        // ===== STRATEGY: ANY ST MATCH + ANY BAND REVERSAL =====
        val signal = evaluateUltra(tick.symbol, tick.ltp, idxPrice, optST, idxST, optCandles)
        signal?.let { emit(it) }
    }
    
    // ===== ULTRA STRATEGY LOGIC =====
    private fun evaluateUltra(
        symbol: String,
        price: Double,
        idxPrice: Double,
        optST: MultiST,
        idxST: MultiST,
        optCandles: List<Candle>
    ): Signal? {
        
        if (consecutiveLosses >= 3) return null // Learning pause
        
        val isCall = symbol.contains("CE")
        val isPut = symbol.contains("PE")
        
        // Get all 7 bands for both
        val idxBands = idxST.bandList() // [st2, st3, st4, st5, st6, st7, st8]
        val optBands = optST.bandList()
        
        // ===== RULE 1: ANY ST MATCH BETWEEN INDEX & OPTION =====
        val matchFound = findSTMatch(idxBands, optBands, price)
        if (!matchFound.valid) return null
        
        // ===== RULE 2: REVERSAL FROM ANY ST BAND =====
        val reversal = detectAnyBandReversal(optCandles, optBands, price, isCall)
        if (!reversal.reversed) return null
        
        // ===== RULE 3: DIRECTION CONFIRMATION =====
        val directionOK = if (isCall) {
            reversal.direction == 1 // Up reversal
        } else if (isPut) {
            reversal.direction == -1 // Down reversal
        } else false
        
        if (!directionOK) return null
        
        // ===== RULE 4: ST 2 TO 8 ARE TARGETS =====
        val targets = calculateTargets(optBands, reversal.bandIndex, isCall)
        val stopLoss = calculateSL(optBands, reversal.bandIndex, isCall)
        
        // ===== RULE 5: AI CONFIDENCE SCORE =====
        val matchScore = matchFound.matchCount * 10
        val reversalScore = if (reversal.strength > 0.7) 30 else if (reversal.strength > 0.5) 20 else 10
        val trendScore = checkAllBandAlignment(optBands, isCall)
        val totalScore = (matchScore + reversalScore + trendScore).coerceIn(0, 100)
        
        if (totalScore < 60) return null
        
        // Position sizing
        val qty = calculateQty(price, stopLoss)
        if (qty <= 0) return null
        
        val action = if (isCall) Actions.BUY else Actions.SELL
        val target = targets.firstOrNull() ?: (price * 1.02)
        
        return Signal(
            action = action,
            symbol = Symbol(symbol),
            entryPrice = Price.from(price),
            target = Price.from(target),
            stopLoss = Price.from(stopLoss),
            confidence = Confidence(totalScore),
            reason = "ST-Match[${matchFound.matchCount}] Rev@ST${reversal.bandIndex + 2} Str=${"%.2f".format(reversal.strength)}",
            timestamp = System.currentTimeMillis(),
            quantity = qty,
            regime = detectRegime(optCandles, optST)
        )
    }
    
    data class MatchResult(val valid: Boolean, val matchCount: Int)
    
    private fun findSTMatch(idxBands: DoubleArray, optBands: DoubleArray, optPrice: Double): MatchResult {
        var count = 0
        val tolerance = optPrice * 0.002
        for (i in idxBands.indices) {
            for (j in optBands.indices) {
                if (abs(idxBands[i] - optBands[j]) < tolerance) count++
            }
        }
        val specialMatch = abs(idxBands[2] - optBands[1]) < tolerance
        return MatchResult(count > 0 || specialMatch, count)
    }
    
    data class ReversalResult(val reversed: Boolean, val direction: Int, val bandIndex: Int, val strength: Double)
    
    private fun detectAnyBandReversal(candles: List<Candle>, bands: DoubleArray, price: Double, isCall: Boolean): ReversalResult {
        if (candles.size < 3) return ReversalResult(false, 0, -1, 0.0)
        val prev = candles[candles.size - 2].close.rupees
        val prev2 = candles[candles.size - 3].close.rupees
        val tolerance = price * 0.001
        
        for (i in bands.indices) {
            val band = bands[i]
            val touchedBand = abs(price - band) < tolerance || abs(prev - band) < tolerance
            if (touchedBand) {
                val goingUp = price > prev && prev > prev2
                val goingDown = price < prev && prev < prev2
                val isReversal = if (isCall) band < price && goingUp else band > price && goingDown
                if (isReversal) {
                    return ReversalResult(true, if (isCall) 1 else -1, i, calculateReversalStrength(candles, band, isCall))
                }
            }
        }
        return ReversalResult(false, 0, -1, 0.0)
    }
    
    private fun calculateReversalStrength(candles: List<Candle>, band: Double, isCall: Boolean): Double {
        val last3 = candles.takeLast(3)
        if (last3.size < 3) return 0.0
        val bodyStrength = (if (isCall) last3.count { it.isBullish } else last3.count { !it.isBullish }) / 3.0
        val bandBounce = abs(last3.last().close.rupees - band) / band
        return (bodyStrength * 0.5 + bandBounce * 10.0 * 0.5).coerceIn(0.0, 1.0)
    }
    
    private fun calculateTargets(bands: DoubleArray, fromBand: Int, isCall: Boolean): List<Double> {
        val targets = mutableListOf<Double>()
        if (isCall) {
            for (i in fromBand + 1 until bands.size) targets.add(bands[i] * 1.002)
        } else {
            for (i in fromBand - 1 downTo 0) targets.add(bands[i] * 0.998)
        }
        return targets
    }
    
    private fun calculateSL(bands: DoubleArray, bandIndex: Int, isCall: Boolean): Double {
        return if (isCall) {
            val b = if (bandIndex > 0) bands[bandIndex - 1] else bands[0] * 0.99
            b * 0.995
        } else {
            val b = if (bandIndex < bands.size - 1) bands[bandIndex + 1] else bands.last() * 1.01
            b * 1.005
        }
    }
    
    private fun checkAllBandAlignment(bands: DoubleArray, isCall: Boolean): Int {
        var aligned = 0
        for (i in 0 until bands.size - 1) {
            if (isCall && bands[i] < bands[i + 1]) aligned++
            else if (!isCall && bands[i] > bands[i + 1]) aligned++
        }
        return aligned * 5
    }
    
    private fun detectRegime(candles: List<Candle>, st: MultiST): Byte {
        val adx = calcADX(candles, 14)
        return when {
            adx > 25 && st.master.trend == 1.toByte() -> Regimes.TRENDING_UP
            adx > 25 && st.master.trend == (-1).toByte() -> Regimes.TRENDING_DOWN
            st.isCompressed -> Regimes.VOLATILE
            else -> Regimes.SIDEWAYS
        }
    }
    
    private fun calcADX(candles: List<Candle>, period: Int): Double {
        if (candles.size < period + 1) return 0.0
        var plusDM = 0.0; var minusDM = 0.0; var trSum = 0.0
        for (i in candles.size - period until candles.size) {
            val c = candles[i]; val p = candles[i-1]
            val upMove = c.high.rupees - p.high.rupees
            val downMove = p.low.rupees - c.low.rupees
            plusDM += if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDM += if (downMove > upMove && downMove > 0) downMove else 0.0
            val tr1 = c.high.rupees - c.low.rupees
            val tr2 = abs(c.high.rupees - p.close.rupees)
            val tr3 = abs(c.low.rupees - p.close.rupees)
            trSum += max(tr1, max(tr2, tr3))
        }
        return if (trSum > 0) abs(plusDM - minusDM) / (plusDM + minusDM + 0.001) * 100 else 0.0
    }
    
    private fun calculateQty(price: Double, sl: Double): Int {
        val riskPerUnit = abs(price - sl)
        return if (riskPerUnit > 0) (5000.0 / riskPerUnit).toInt().coerceIn(1, 1000) else 0
    }
    
    private fun emit(signal: Signal) {
        val key = "${signal.symbol.value}_${signal.action}"
        if (System.currentTimeMillis() - (lastSignalTime[key] ?: 0L) < 500) return
        lastSignalTime[key] = System.currentTimeMillis()
        _signals.value = (_signals.value + signal).takeLast(50)
    }
    
    fun initialize(history: List<Candle>) {
        indexCandles.initialize(history)
        indexST.calculate(history)?.let { indexSTResult = it }
    }
    
    fun onTradeCompleted(pnl: Double) {
        dailyPnl += pnl
        if (pnl < 0) consecutiveLosses++ else consecutiveLosses = 0
        if (consecutiveLosses >= 2) { weights.trendW *= 0.9f; weights.strengthW *= 1.1f }
    }
    
    fun emergencyStop() { exposure = Double.MAX_VALUE }
    fun resetDaily() { dailyPnl = 0.0; consecutiveLosses = 0; exposure = 0.0 }
    fun getState() = indexSTResult
    fun getIndexPrice() = indexPrice
    
    data class TickData(val symbol: String, val ltp: Double, val volume: Long, val ts: Long)
}
