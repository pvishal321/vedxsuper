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
    val stateStore: AppStateStore
)

/**
 * V5 UltraNeuralCore (System Orchestrator)
 */
class UltraNeuralCore(
    private val indexSymbol: Symbol,
    private val services: CoreServices
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val indexCandles1m = CandleEngine(1)
    private val indexCandles15m = CandleEngine(15)
    val indexCandlesFlow = indexCandles15m.candles

    private val indexST = SuperTrendEngine()
    private val _indexSTResult = MutableStateFlow<MultiST?>(null)
    val indexSTResult = _indexSTResult.asStateFlow()

    private class OptState(
        val candles: CandleEngine = CandleEngine(15),
        val st: SuperTrendEngine = SuperTrendEngine(),
        // Audit 2.4: Using ArrayDeque for efficient sliding window
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

    private fun observeEvents() {
        services.eventBus.events.onEach { event ->
            when (event) {
                is SystemEvent.TickReceived -> handleTick(event.tick)
                is SystemEvent.SignalGenerated -> {
                    services.audit.logSignal(event.signal)
                    emit(event.signal)
                    services.virtualTrade.executeSignal(event.signal, 500_000.0)
                }
                is SystemEvent.TradeClosed -> {
                    services.analytics.recordTrade(event.trade.matchedBand, event.pnl, System.currentTimeMillis() - event.trade.entryTime)
                }
                else -> {}
            }
        }.launchIn(scope)
    }

    private fun handleTick(tick: TickData) {
        val symbolKey = when (tick.symbol) {
            "26000", "NIFTY" -> "NIFTY"
            "26009", "BANKNIFTY" -> "BANKNIFTY"
            "26037", "FINNIFTY" -> "FINNIFTY"
            "26074", "MIDCPNIFTY" -> "MIDCPNIFTY"
            "19000", "SENSEX" -> "SENSEX"
            "19003", "BANKEX" -> "BANKEX"
            else -> tick.symbol
        }

        // Update Global State LTP for Dashboard
        services.stateStore.updateMarket { m -> 
            val newMap = m.lastLtp.toMutableMap()
            newMap[symbolKey] = tick.ltp
            m.copy(lastLtp = newMap)
        }

        // Core Processing for Primary Index
        if (symbolKey == indexSymbol.value) {
            onIndexTick(symbolKey, tick.ltp, tick.volume, tick.ts)
        } else if (tick.symbol !in listOf("26000", "26009", "26037", "26074", "19000", "19003")) {
            // Option Processing for tokens that are not main indices
            onOptionTick(tick.symbol, tick.ltp, tick.volume, tick.ts, getIndexPrice(), tick.oi)
        }
    }

    fun onIndexTick(symbol: String, ltp: Double, vol: Long, ts: Long) {
        if (symbol != indexSymbol.value) return
        val tick = TickData(symbol, ltp, vol, ts)
        indexCandles1m.onTick(tick)
        indexCandles15m.onTick(tick)

        val c15 = indexCandles15m.candles.value
        if (c15.size >= 200) {
            val stRes = indexST.calculate(c15)
            _indexSTResult.value = stRes
            stRes?.let { 
                services.context.updateContext(it.adx, it.master.trend, it.bullCount, it.bearCount, it.isCompressed)
                services.stateStore.updateMarket { m -> m.copy(indexST = it) }
            }
        }
    }

    fun onOptionTick(symbol: String, ltp: Double, vol: Long, ts: Long, idxPrice: Double, oi: Long = 0L) {
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
        val idxST = _indexSTResult.value ?: return

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
        // Simple logic for now: check if today is Thursday for NIFTY/BANKNIFTY
        // In production, this would use optionDataManager expiry dates
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.THURSDAY
    }

    private fun cleanupExpiredOptions() {
        val now = System.currentTimeMillis()
        val toRemove = options.keys.filter { symbol ->
            // Logic: If no tick received for 24 hours, remove from map
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
        indexCandles15m.initialize(history)
    }

    fun getIndexPrice() = indexCandles1m.candles.value.lastOrNull()?.close?.rupees ?: 0.0
    fun getOptionCandles(symbol: String) = options[symbol]?.candles?.candles?.value ?: emptyList()
    
    fun cleanup() { scope.cancel() }

    fun onTradeCompleted(signal: Signal, pnl: Long, holdTime: Long, status: TradeStatus) {
        services.analytics.recordTrade(signal.matchedBand, pnl, holdTime)
        services.learning.onTradeCompleted(signal, status)
    }
}
