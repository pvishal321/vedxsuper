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

// ===== AI NEURAL CORE (Replaces ALL separate engines) =====
class NeuralCore(private val indexSymbol: Symbol) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val indexChannel = Channel<TickData>(Channel.CONFLATED)
    private val optionChannel = Channel<Pair<TickData, Double>>(Channel.CONFLATED)
    
    // Index state
    private val indexCandles = FastCandleBuilder(15)
    private val indexST = MultiBandEngine()
    private var indexPrice = 0.0
    private var indexState = MarketState.pack(Trends.WAIT, Regimes.SIDEWAYS, 0, 0)
    
    // Option states
    private class OptState(val candles: FastCandleBuilder = FastCandleBuilder(15), val st: MultiBandEngine = MultiBandEngine(), val ticks: TickRingBuffer = TickRingBuffer(1024))
    private val options = ConcurrentHashMap<String, OptState>()
    
    // AI
    private val weights = AIWeights()
    private val _signals = MutableStateFlow<List<Signal>>(emptyList())
    val signals = _signals.asStateFlow()
    
    private var lastSignalTime = ConcurrentHashMap<String, Long>()
    private var exposure = 0.0
    private var dailyPnl = 0.0
    private var consecutiveLosses = 0
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
        if (indexState.regime == Regimes.NO_TRADE) return
        optionChannel.trySend(TickData(symbol, ltp, vol, ts) to indexPrice)
    }
    
    private suspend fun processIndex(tick: TickData) {
        indexPrice = tick.ltp
        indexCandles.onTick(Price.from(tick.ltp).cents, tick.volume.toInt(), tick.ts)
        val candles = indexCandles.candles.value
        if (candles.size < 15) return
        
        val st = indexST.calculate(candles) ?: return
        val regime = detectRegime(candles, st)
        val structure = detectStructure(candles, st, regime)
        val trend = detectTrend(st)
        val zone = detectZone(indexPrice, st)
        indexState = MarketState.pack(trend, regime, structure, zone)
    }
    
    private suspend fun processOption(tick: TickData, idxPrice: Double) {
        val state = options.getOrPut(tick.symbol) { OptState() }
        state.ticks.push(Price.from(tick.ltp).cents, tick.volume.toInt(), tick.ts, 0)
        state.candles.onTick(Price.from(tick.ltp).cents, tick.volume.toInt(), tick.ts)
        
        val optCandles = state.candles.candles.value
        if (optCandles.size < 15) return
        val optST = state.st.calculate(optCandles) ?: return
        val idxST = indexST.calculate(indexCandles.candles.value) ?: return
        
        val signal = evaluate(tick.symbol, tick.ltp, idxPrice, optST, idxST, optCandles, indexCandles.candles.value)
        signal?.let { emit(it) }
    }
    
    // ===== AI DECISION (All logic in ONE function - ultra fast) =====
    private fun evaluate(symbol: String, price: Double, idxPrice: Double, optST: MultiST, idxST: MultiST, optCandles: List<Candle>, idxCandles: List<Candle>): Signal? {
        if (consecutiveLosses >= 3) return null // Learning: pause after 3 losses
        
        val isCall = symbol.contains("CE")
        val idxTrend = idxST.master.trend
        val optTrend = optST.master.trend
        
        // Correlation check
        val correlated = (isCall && idxTrend == 1.toByte() && optTrend == 1.toByte()) || 
                       (!isCall && idxTrend == (-1).toByte() && optTrend == (-1).toByte())
        if (!correlated) return null
        
        // Fake breakout detection
        if (detectFakeBreakout(optCandles)) return null
        
        // Liquidity check
        val spread = options[symbol]?.ticks?.let { buf ->
            var lastP = 0; var spreadSum = 0
            buf.forEachRecent(5) { p, _, _ -> if (lastP != 0) spreadSum += abs(p - lastP); lastP = p }
            spreadSum / 5.0 / 100.0
        } ?: 0.1
        if (spread > price * 0.02) return null // Too illiquid
        
        // Momentum
        val momentum = calcMomentum(optCandles)
        val strength = calcStrength(optCandles, idxCandles)
        
        // AI Score (0-100)
        val trendScore = if (optTrend == idxTrend) 30f else 10f
        val momentumScore = (momentum * 100).coerceIn(0f, 25f)
        val strengthScore = (strength * 100).coerceIn(0f, 20f)
        val structureScore = if (indexState.structure == 1.toByte()) 15f else 5f // Continuation
        val zoneScore = if (indexState.zone == optST.master.trend) 10f else 0f
        val totalScore = (trendScore * weights.trendW + momentumScore * weights.strengthW + 
                         strengthScore * weights.structureW + structureScore * weights.regimeW + 
                         zoneScore * weights.correlationW).toInt() * 3
        
        if (totalScore < 65) return null
        
        // Position sizing
        val atr = optST.st2.atr
        val sl = price - atr * 1.5
        val target = price + atr * 2.5
        val qty = if (exposure < 100000) (50000 / price).toInt() else 0
        if (qty <= 0) return null
        
        val action = when {
            momentum > 0.7f && strength > 0.6f -> Actions.SCALP
            isCall -> Actions.BUY
            else -> Actions.SELL
        }
        
        return Signal(action, Symbol(symbol), Price.from(price), Price.from(target), Price.from(sl), 
                     Confidence(totalScore.coerceIn(0, 100)), "AI: Score=$totalScore M=${momentum.format(2)} S=${strength.format(2)}", 
                     System.currentTimeMillis(), qty, indexState.regime)
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
    
    private fun detectStructure(candles: List<Candle>, st: MultiST, regime: Byte): Byte {
        if (candles.size < 5) return 0
        val last3 = candles.takeLast(3)
        val avgRange = candles.takeLast(20).map { it.range.rupees }.average()
        val lastRange = last3.last().range.rupees
        return when {
            lastRange > avgRange * 2 && regime == Regimes.VOLATILE -> 2 // Expansion
            detectFakeBreakout(candles) -> 3 // Fake breakout
            last3.all { it.isBullish } || last3.all { !it.isBullish } -> 1 // Continuation
            else -> 0
        }
    }
    
    private fun detectTrend(st: MultiST): Byte {
        val trends = listOf(st.st2, st.st3, st.st4, st.st5, st.st6, st.st7, st.st8).map { it.trend }
        val up = trends.count { it == 1.toByte() }
        return when {
            up >= 6 -> Trends.TREND_RUN
            up >= 4 -> Trends.BUILDING
            up <= 2 -> Trends.REVERSAL_SETUP
            else -> Trends.WAIT
        }
    }
    
    private fun detectZone(price: Double, st: MultiST): Byte {
        val bands = st.bandList()
        return when {
            price > bands[5] -> 1 // Deep buy zone
            price > bands[3] -> 2 // Buy zone
            price < bands[2] -> (-1).toByte() // Sell zone
            price < bands[0] -> (-2).toByte() // Deep sell zone
            else -> 0
        }
    }
    
    private fun detectFakeBreakout(candles: List<Candle>): Boolean {
        if (candles.size < 5) return false
        val last = candles.last()
        val prev = candles[candles.size - 2]
        val wickRatio = if (last.isBullish) 
            (last.high.rupees - last.close.rupees) / last.range.rupees 
        else 
            (last.close.rupees - last.low.rupees) / last.range.rupees
        return wickRatio > 0.7 && abs(last.close.rupees - prev.close.rupees) < last.range.rupees * 0.1
    }
    
    private fun calcMomentum(candles: List<Candle>): Float {
        if (candles.size < 10) return 0f
        val gains = candles.takeLast(10).count { it.close.cents > it.open.cents }
        return gains / 10f
    }
    
    private fun calcStrength(opt: List<Candle>, idx: List<Candle>): Float {
        if (opt.size < 5 || idx.size < 5) return 0f
        val optDir = if (opt.last().close.cents > opt[opt.size-5].close.cents) 1 else -1
        val idxDir = if (idx.last().close.cents > idx[idx.size-5].close.cents) 1 else -1
        return if (optDir == idxDir) 1f else 0f
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
        val dx = if (trSum > 0) abs(plusDM - minusDM) / (plusDM + minusDM + 0.001) * 100 else 0.0
        return dx
    }
    
    private fun emit(signal: Signal) {
        val key = "${signal.symbol.value}_${signal.action}"
        val last = lastSignalTime[key] ?: 0L
        if (System.currentTimeMillis() - last < 500) return // Anti-spam
        
        lastSignalTime[key] = System.currentTimeMillis()
        val current = _signals.value
        _signals.value = (current + signal).takeLast(50)
    }
    
    fun onTradeCompleted(pnl: Double) {
        dailyPnl += pnl
        if (pnl < 0) consecutiveLosses++ else consecutiveLosses = 0
        // Adaptive learning
        if (consecutiveLosses >= 2) {
            weights.trendW *= 0.9f
            weights.strengthW *= 1.1f
        }
    }
    
    fun emergencyStop() { exposure = Double.MAX_VALUE }
    fun resetDaily() { dailyPnl = 0.0; consecutiveLosses = 0; exposure = 0.0 }
    fun getState() = indexState
    fun getIndexPrice() = indexPrice
    
    private fun Float.format(d: Int) = "%.${d}f".format(this)
    private fun Double.format(d: Int) = "%.${d}f".format(this)
    
    data class TickData(val symbol: String, val ltp: Double, val volume: Long, val ts: Long)
}
