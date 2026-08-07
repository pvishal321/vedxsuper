package com.vedx.vedxsuper.core

import com.vedx.vedxsuper.core.audit.AuditEngine
import com.vedx.vedxsuper.core.event.EventBus
import com.vedx.vedxsuper.core.event.SystemEvent
import com.vedx.vedxsuper.core.learning.LearningEngine
import com.vedx.vedxsuper.core.market.CandleEngine
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import com.vedx.vedxsuper.core.risk.RiskEngine
import com.vedx.vedxsuper.core.state.AppStateStore
import com.vedx.vedxsuper.core.strategy.*
import com.vedx.vedxsuper.core.trade.VirtualTradeEngine
import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Audit 2.6: Dependency Wrapper to prevent constructor bloat
 */
data class CoreServices(
    val risk: RiskEngine,
    val portfolio: PortfolioEngine,
    val context: ContextEngine,
    val signalEngine: SignalEngine,
    val virtualTrade: VirtualTradeEngine,
    val learning: LearningEngine,
    val audit: AuditEngine,
    val analytics: AnalyticsEngine,
    val eventBus: EventBus,
    val stateStore: AppStateStore,
    val optionDataManager: OptionDataManager
)

/**
 * V5 UltraNeuralCore (System Orchestrator)
 */
class UltraNeuralCore(
    private val services: CoreServices
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Candle engines for 3 Indices
    private val candlesMap = mapOf(
        "NIFTY" to CandleEngine(15),
        "BANKNIFTY" to CandleEngine(15),
        "SENSEX" to CandleEngine(15)
    )

    // ST Engines for 3 Indices
    private val stMap = mapOf(
        "NIFTY" to SuperTrendEngine(),
        "BANKNIFTY" to SuperTrendEngine(),
        "SENSEX" to SuperTrendEngine()
    )

    // Latest ST Results for all indices
    private val _indexSTResults = MutableStateFlow<Map<String, MultiST>>(emptyMap())
    val indexSTResults = _indexSTResults.asStateFlow()

    // Legacy support for single flow (defaults to NIFTY)
    val indexCandlesFlow = candlesMap["NIFTY"]!!.candles
    val indexSTResult = indexSTResults.map { it["NIFTY"] }.stateIn(scope, SharingStarted.Lazily, null)

    fun getIndexCandles(symbol: String): StateFlow<List<Candle>> {
        return candlesMap[symbol]?.candles ?: indexCandlesFlow
    }

    fun getIndexST(symbol: String): Flow<MultiST?> {
        return indexSTResults.map { it[symbol] }
    }

    private class OptState(
        val candles: CandleEngine = CandleEngine(15),
        val st: SuperTrendEngine = SuperTrendEngine(),
        val oiHistory: ArrayDeque<OIPoint> = ArrayDeque(101)
    )
    private data class OIPoint(val oi: Long, val price: Double, val ts: Long)
    private val options = ConcurrentHashMap<String, OptState>()

    private val _signals = MutableStateFlow<List<Signal>>(emptyList())
    val signals = _signals.asStateFlow()

    init {
        observeEvents()
        
        services.portfolio.openTrades
            .onEach { open -> 
                services.risk.updateExposure(open.map { 
                    OpenPosition(it.id.toString(), Symbol(it.symbol), 
                    if(it.symbol.contains("CE")) OptionType.CE else OptionType.PE,
                    Price.from(it.entryPrice), Price.from(it.entryPrice), it.quantity, 
                    it.quantity/65, Price.from(it.target), Price.from(it.stopLoss), null, 0.0, 0.0, it.entryTime, null)
                })
                services.stateStore.updatePortfolio { it.copy(openTrades = open) }
            }
            .launchIn(scope)

        services.portfolio.balance.onEach { b ->
            services.stateStore.updatePortfolio { it.copy(balance = b) }
        }.launchIn(scope)

        // Audit 2.5: Automatic Cleanup of Expired Options (Every hour)
        scope.launch {
            while (isActive) {
                delay(3_600_000)
                cleanupExpiredOptions()
            }
        }
    }

    private val INDEX_KEYS = setOf("NIFTY", "BANKNIFTY", "SENSEX")

    private fun observeEvents() {
        services.eventBus.events.onEach { event ->
            when (event) {
                is SystemEvent.TickReceived -> handleTick(event.tick)
                is SystemEvent.SignalGenerated -> {
                    services.audit.logSignal(event.signal)
                    emit(event.signal)
                    services.virtualTrade.executeSignal(event.signal)
                }
                is SystemEvent.TradeClosed -> {
                    services.analytics.recordTrade(event.trade.matchedBand, event.pnl, System.currentTimeMillis() - event.trade.entryTime)
                }
                else -> {}
            }
        }.launchIn(scope)
    }

    private fun handleTick(tick: TickData) {
        val normalizedSymbol = normalizeToken(tick.symbol)
        val symbolKey = when (normalizedSymbol) {
            "26000", "99926000", "NIFTY" -> "NIFTY"
            "26009", "99926009", "BANKNIFTY" -> "BANKNIFTY"
            "19000", "99919000", "SENSEX" -> "SENSEX"
            else -> tick.symbol
        }

        // Update Global State LTP
        if (symbolKey in INDEX_KEYS) {
            services.stateStore.updateMarket { m -> 
                val newLtp = m.lastLtp.toMutableMap()
                val newChange = m.lastChange.toMutableMap()
                val newChangePct = m.lastChangePct.toMutableMap()
                
                newLtp[symbolKey] = tick.ltp
                if (tick.hasChange) {
                    newChange[symbolKey] = tick.change
                    newChangePct[symbolKey] = tick.changePct
                }
                
                m.copy(lastLtp = newLtp, lastChange = newChange, lastChangePct = newChangePct)
            }
            onIndexTick(symbolKey, tick.ltp, tick.volume, tick.ts)
        } else if (symbolKey !in setOf("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX")) {
            // Option Processing
            val inst = services.optionDataManager.getInstrumentByToken(normalizedSymbol)
            
            // Sync Option Price to StateStore
            inst?.let { i ->
                services.stateStore.updateMarket { m ->
                    val newLtp = m.lastLtp.toMutableMap()
                    val newChange = m.lastChange.toMutableMap()
                    val newChangePct = m.lastChangePct.toMutableMap()
                    
                    newLtp[i.symbol] = tick.ltp
                    if (tick.hasChange) {
                        newChange[i.symbol] = tick.change
                        newChangePct[i.symbol] = tick.changePct
                    }
                    
                    m.copy(lastLtp = newLtp, lastChange = newChange, lastChangePct = newChangePct)
                }
            }

            val underlying = when {
                inst?.name?.contains("BANKNIFTY", true) == true -> "BANKNIFTY"
                inst?.name?.contains("SENSEX", true) == true -> "SENSEX"
                else -> "NIFTY"
            }
            onOptionTick(tick.symbol, tick.ltp, tick.volume, tick.ts, getIndexPrice(underlying), tick.oi, underlying)
        }
    }

    private fun normalizeToken(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.all { it.isDigit() } && trimmed.length > 1) {
            trimmed.trimStart('0').ifEmpty { "0" }
        } else {
            trimmed
        }
    }

    fun onIndexTick(symbol: String, ltp: Double, vol: Long, ts: Long) {
        val engine = candlesMap[symbol] ?: return
        val tick = TickData(symbol, ltp, vol, ts)
        engine.onTick(tick)

        val candles = engine.candles.value
        if (candles.size >= 200) {
            val stRes = stMap[symbol]?.calculate(candles)
            if (stRes != null) {
                val results = _indexSTResults.value.toMutableMap()
                results[symbol] = stRes
                _indexSTResults.value = results
                
                if (symbol == "NIFTY") { // Default context from Nifty
                    services.context.updateContext(stRes.adx, stRes.master.trend, stRes.bullCount, stRes.bearCount, stRes.isCompressed)
                    services.stateStore.updateMarket { m -> m.copy(indexST = stRes) }
                }
            }
        }
    }

    fun onOptionTick(symbol: String, ltp: Double, vol: Long, ts: Long, idxPrice: Double, oi: Long = 0L, underlying: String = "NIFTY") {
        val tick = TickData(symbol, ltp, vol, ts, oi = oi)
        val state = options.getOrPut(symbol) { OptState() }
        
        state.candles.onTick(tick)
        state.oiHistory.addLast(OIPoint(oi, ltp, ts))
        if (state.oiHistory.size > 100) state.oiHistory.removeFirst()

        val optCandles = state.candles.candles.value
        if (optCandles.size < 200) {
            services.virtualTrade.updateTrades(symbol, ltp)
            return
        }
        
        val optST = state.st.calculate(optCandles) ?: return
        val idxST = _indexSTResults.value[underlying] ?: _indexSTResults.value["NIFTY"] ?: return

        scope.launch {
            val oiAnalysis = analyzeOI(state.oiHistory.toList())
            val isPositionOpen = services.portfolio.openTrades.value.any { it.symbol == symbol }
            val isExpiryDay = isExpiry(symbol)

            val signal = services.signalEngine.evaluate(
                symbol = symbol,
                price = ltp,
                optST = optST,
                idxST = idxST,
                optCandles = optCandles,
                context = services.context.context.value,
                tick = tick,
                oiAnalysis = oiAnalysis,
                isPositionOpen = isPositionOpen,
                isExpiryDay = isExpiryDay
            )

            signal?.let { services.eventBus.publish(SystemEvent.SignalGenerated(it)) }
        }
        
        services.virtualTrade.updateTrades(symbol, ltp)
    }

    private fun isExpiry(symbol: String): Boolean {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.THURSDAY
    }

    private fun cleanupExpiredOptions() {
        val now = System.currentTimeMillis()
        val toRemove = options.keys.filter { symbol ->
            val state = options[symbol]
            val lastTickTs = state?.oiHistory?.lastOrNull()?.ts ?: 0L
            (now - lastTickTs) > 86_400_000 // 24 hours
        }
        toRemove.forEach { options.remove(it) }
    }

    private fun analyzeOI(history: List<OIPoint>): OIAnalysis {
        if (history.size < 10) return OIAnalysis("", 0, 0, 0.0, 0.0, "Neutral")
        val cur = history.last(); val prev = history[history.size - 6]
        val oiChg = cur.oi - prev.oi; val prChg = cur.price - prev.price
        val interp = when {
            oiChg > 0 && prChg > 0 -> "Long Buildup"
            oiChg > 0 && prChg < 0 -> "Short Buildup"
            oiChg < 0 && prChg < 0 -> "Long Unwinding"
            oiChg < 0 && prChg > 0 -> "Short Covering"
            else -> "Neutral"
        }
        return OIAnalysis(cur.price.toString(), cur.oi, oiChg, 0.0, prChg, interp)
    }

    private fun emit(signal: Signal) {
        _signals.value = (_signals.value + signal).takeLast(100)
        services.stateStore.updateStrategy { it.copy(lastSignal = signal, activeSignals = _signals.value) }
    }

    fun onVixUpdate(vix: Double) = services.context.updateVix(vix)
    fun onPcrUpdate(pcr: Double) = services.context.updatePcr(pcr)
    fun onMarketBreadthUpdate(b: MarketBreadth) = services.context.updateBreadth(b)

    fun initialize(history: List<Candle>) {
        candlesMap["NIFTY"]?.initialize(history)
    }

    fun getIndexPrice(symbol: String = "NIFTY") = candlesMap[symbol]?.candles?.value?.lastOrNull()?.close?.rupees ?: candlesMap["NIFTY"]?.candles?.value?.lastOrNull()?.close?.rupees ?: 0.0
    fun getOptionCandles(symbol: String) = options[symbol]?.candles?.candles?.value ?: emptyList()
    
    fun cleanup() { scope.cancel() }

    fun onTradeCompleted(signal: Signal, pnl: Long, holdTime: Long, status: TradeStatus) {
        services.analytics.recordTrade(signal.matchedBand, pnl, holdTime)
        services.learning.onTradeCompleted(signal, status)
    }
}
