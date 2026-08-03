package com.vedx.vedxsuper.repository

import com.vedx.vedxsuper.broker.AngelApiClient
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.database.CandleEntity
import com.vedx.vedxsuper.market.MarketDataManager
import com.vedx.vedxsuper.model.market.*
import com.vedx.vedxsuper.strategy.Phase3Engine
import com.vedx.vedxsuper.strategy.candle.CandleBuilder
import com.vedx.vedxsuper.strategy.engine.*
import com.vedx.vedxsuper.strategy.signal.CorrelationEngine
import com.vedx.vedxsuper.strategy.signal.CorrelationSignal
import com.vedx.vedxsuper.strategy.signal.SignalEngine
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import com.vedx.vedxsuper.strategy.options.OptionSelector
import com.vedx.vedxsuper.strategy.signal.*
import com.vedx.vedxsuper.trade.VirtualTradeManager
import com.vedx.vedxsuper.utils.NotificationHelper
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MarketRepository(
    private val scope: CoroutineScope,
    private val notificationHelper: NotificationHelper,
    private val settingsManager: SettingsManager,
    private val tokenManager: SecureTokenManager,
    private val marketDataManager: MarketDataManager,
    private val virtualTradeManager: VirtualTradeManager,
    private val candleDao: com.vedx.vedxsuper.database.CandleDao,
    private val tickDao: com.vedx.vedxsuper.database.TickDao,
    private val app: com.vedx.vedxsuper.VedxApplication
) {
    private val _ticksSavedCount = MutableStateFlow(0)
    val ticksSavedCount = _ticksSavedCount.asStateFlow()
    
    val indexData = marketDataManager.indexData
    private val lastTickSavedTime = ConcurrentHashMap<String, Long>()
    private val dbMutex = Mutex()
    
    // Efficient Tick Processing via Channel
    private val tickChannel = Channel<TickData>(Channel.BUFFERED)

    // Candle Builders for specific tokens
    private val candleBuilders = ConcurrentHashMap<String, CandleBuilder>()
    
    // Strategy Engines for specific tokens
    private val strategyEngines = ConcurrentHashMap<String, SignalEngine>()
    
    private val phase3Engine = Phase3Engine(OptionSelector(), settingsManager.getAnalysisTimeframe())
    private val correlationEngine = CorrelationEngine(OptionSelector())

    private val riskManagementEngine = com.vedx.vedxsuper.strategy.engine.RiskManagementEngine()
    private var institutionalEngines = createInstitutionalEngines()

    fun syncRiskConfig() {
        val config = settingsManager.getStrategyConfig()
        
        val riskConfig = com.vedx.vedxsuper.strategy.engine.RiskConfig(
            maxRiskPerTradePercent = config.maxRiskPerTrade,
            maxDailyLossPercent = config.maxDailyLoss,
            dailyProfitTargetPercent = config.dailyProfitTarget,
            emergencyStopEnabled = true,
            minRiskRewardRatio = 1.5
        )
        
        riskManagementEngine.updateConfig(riskConfig)
        institutionalEngines.values.forEach { it.updateConfig(config) }
        
        // [NEW] Also sync the basic SignalEngines used for Neural Matrix
        strategyEngines.values.forEach { it.updateConfig(config) }

        android.util.Log.i("RiskSync", "Risk Updated: Risk/Trade=${config.maxRiskPerTrade}%, DailyLoss=${config.maxDailyLoss}%")
    }

    private fun createInstitutionalEngines(): Map<String, InstitutionalStrategyEngine> {
        val timeframe = settingsManager.getAnalysisTimeframe()
        return mapOf(
            "NIFTY" to InstitutionalStrategyEngine("NIFTY", riskManagementEngine, timeframe, settingsManager::getLotSize),
            "BANKNIFTY" to InstitutionalStrategyEngine("BANKNIFTY", riskManagementEngine, timeframe, settingsManager::getLotSize),
            "SENSEX" to InstitutionalStrategyEngine("SENSEX", riskManagementEngine, timeframe, settingsManager::getLotSize),
            "FINNIFTY" to InstitutionalStrategyEngine("FINNIFTY", riskManagementEngine, timeframe, settingsManager::getLotSize)
        )
    }

    fun updateTimeframe(minutes: Int) {
        settingsManager.setAnalysisTimeframe(minutes)
        institutionalEngines = createInstitutionalEngines()
        syncRiskConfig()
        
        // [FIX] Re-initialize signal collection for new engines
        institutionalEngines.values.forEach { engine ->
            scope.launch {
                engine.signals.collect { signals ->
                    signals.forEach { signal ->
                        val signalKey = "INST_${signal.optionSymbol}_${signal.timestamp}_${signal.type}"
                        if (processedSignals[signalKey] != true) {
                            processedSignals[signalKey] = true
                            handleInstitutionalSignal(signal)
                        }
                    }
                }
            }
        }

        phase3Engine.updateTimeframe(minutes)
        candleBuilders.clear()
        strategyEngines.clear()
        scope.launch(Dispatchers.IO) {
            preloadDataFromDb()
        }
    }

    private val processedSignals = ConcurrentHashMap<String, Boolean>()

    init {
        syncRiskConfig()
        riskManagementEngine.setBalanceProvider { settingsManager.getVirtualBalance() }
        
        virtualTradeManager.setTradeCompletionListener { pnl, exposure ->
            riskManagementEngine.updateOnTradeExit(pnl, exposure)
        }

        // 1. Tick Worker: Processes all ticks sequentially to ensure thread safety
        scope.launch(Dispatchers.Default) {
            for (tick in tickChannel) {
                processTickInternal(tick)
            }
        }

        // 2. Persistent Position Sync
        scope.launch {
            virtualTradeManager.syncWithDatabase()
        }
        
        // 3. Robust Signal Processing: Process entire list to avoid skipping signals
        institutionalEngines.values.forEach { engine ->
            scope.launch {
                engine.signals.collect { signals ->
                    signals.forEach { signal ->
                        val signalKey = "INST_${signal.optionSymbol}_${signal.timestamp}_${signal.type}"
                        if (processedSignals[signalKey] != true) {
                            processedSignals[signalKey] = true
                            handleInstitutionalSignal(signal)
                        }
                    }
                }
            }
        }

        // 4. Initial Sync & Scheduled Cleanup
        scope.launch(Dispatchers.IO) {
            // [NEW] Immediately preload existing data from DB so charts aren't empty on launch
            preloadDataFromDb()

            tokenManager.tokenState.collect { state ->
                if (state == com.vedx.vedxsuper.model.auth.TokenState.VALID) {
                    fetchHistoricalData()
                }
            }
        }
        
        scope.launch(Dispatchers.IO) {
            // [UPDATED] Keep 60 days of data as requested for 2-month backtesting
            val expiry = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
            candleDao.deleteOldCandles(expiry)
            
            // [NEW] Keep only 2 days of high-frequency tick data to save space
            val tickExpiry = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000)
            tickDao.deleteOldTicks(tickExpiry)
        }
    }

    private suspend fun handleInstitutionalSignal(latest: InstitutionalSignal) {
        // [FIXED] Point 1: Absolute Auto Trade Switch check
        val isAutoTrade = settingsManager.isAutoTradeEnabled()
        if (!isAutoTrade && latest.type != "EXIT") {
            android.util.Log.d("TradeGate", "Execution Blocked: Auto Trade is OFF")
            return
        }

        // [FIXED] Point 2: Only trade high probability in learning mode
        val learningMode = getLearningStats().isLearningMode
        if (learningMode && latest.confidence < 90 && latest.type != "EXIT") {
            android.util.Log.d("TradeGate", "Execution Blocked: Learning Mode active, confidence ${latest.confidence}% < 90%")
            return
        }

        val activeIndices = settingsManager.getActiveIndices()
        val currentIdx = when {
            latest.optionSymbol.contains("NIFTY") -> "NIFTY"
            latest.optionSymbol.contains("BANKNIFTY") -> "BANKNIFTY"
            latest.optionSymbol.contains("SENSEX") -> "SENSEX"
            latest.optionSymbol.contains("FINNIFTY") -> "FINNIFTY"
            else -> ""
        }

        when (latest.type) {
            "BUY_CALL", "BUY_PUT", "EXIT" -> {
                if (activeIndices.contains(currentIdx) || latest.type == "EXIT") {
                    if (latest.confidence >= 80 && settingsManager.isNotificationEnabled()) {
                        notificationHelper.showInstitutionalSignal(latest)
                    }
                    
                    val side = when (latest.type) {
                        "BUY_CALL" -> "BUY_CALL"
                        "BUY_PUT" -> "BUY_PUT"
                        else -> "EXIT"
                    }
                    
                    // [FIXED] Bug 3: Use the absolute latest LTP from MarketDataManager at the moment of execution
                    val executionPrice = marketDataManager.getLtp(latest.optionSymbol).takeIf { it > 0.000001 } ?: latest.price
                    
                    virtualTradeManager.processSignal(
                        side = side,
                        symbol = latest.optionSymbol,
                        price = executionPrice,
                        config = settingsManager.getStrategyConfig(),
                        confidence = latest.confidence,
                        explanation = latest.reason,
                        providedSL = latest.stopLoss,
                        providedTarget = latest.target
                    )
                }
            }
            "TRAIL" -> {
                virtualTradeManager.updateTrailingSL(latest.optionSymbol, latest.stopLoss)
            }
            "PARTIAL_EXIT" -> {
                virtualTradeManager.processPartialExit(latest.optionSymbol, latest.quantity, latest.price)
            }
        }
    }

    fun handleTick(tick: TickData) {
        scope.launch {
            tickChannel.send(tick)
        }
    }

    private fun generateVirtualOptionTicks(indexTick: TickData) {
        val spot = indexTick.ltp
        val symbol = indexTick.symbol
        val interval = when {
            symbol.contains("BANKNIFTY") || symbol.contains("SENSEX") -> 100.0
            else -> 50.0
        }
        
        val atm = (Math.round(spot / interval) * interval).toDouble()
        
        // Generate 3 strikes up and 3 down
        for (i in -3..3) {
            val strike = atm + (i * interval)
            
            // Virtual CE
            val ceSymbol = "${symbol}_${strike.toInt()}_CE"
            // Realistic price: Intrinsic Value + Time Value (VIX dependent)
            val intrinsicCE = (spot - strike).coerceAtLeast(0.0)
            val timeValue = (interval * 0.6) * (marketDataManager.indiaVix.value / 15.0)
            val cePrice = (intrinsicCE + timeValue).coerceAtLeast(2.0)
            
            val ceTick = TickData(
                symbol = ceSymbol,
                token = ceSymbol,
                ltp = cePrice,
                openInterest = 1000000L + (i * 10000),
                timestamp = indexTick.timestamp
            )
            marketDataManager.updateTick(ceTick)
            processTickInternal(ceTick)
            
            // Virtual PE
            val peSymbol = "${symbol}_${strike.toInt()}_PE"
            val intrinsicPE = (strike - spot).coerceAtLeast(0.0)
            val pePrice = (intrinsicPE + timeValue).coerceAtLeast(2.0)
            
            val peTick = TickData(
                symbol = peSymbol,
                token = peSymbol,
                ltp = pePrice,
                openInterest = 1000000L - (i * 10000),
                timestamp = indexTick.timestamp
            )
            marketDataManager.updateTick(peTick)
            processTickInternal(peTick) // Feed into ST engine pipeline
        }
    }

    private var lastDayReset = 0

    private fun processTickInternal(tick: TickData) {
        // [FIXED] Critical check: Absolute Session Gate
        if (!tokenManager.hasValidSession()) {
            return
        }

        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (lastDayReset != 0 && currentDay != lastDayReset) {
            institutionalEngines.values.forEach { it.reset() }
        }
        lastDayReset = currentDay

        marketDataManager.updateTick(tick)
        
        // [NEW] Per-second Tick Persistence Logic
        val lastSaved = lastTickSavedTime[tick.symbol] ?: 0L
        if (tick.timestamp - lastSaved >= 1000) {
            lastTickSavedTime[tick.symbol] = tick.timestamp
            scope.launch(Dispatchers.IO) {
                tickDao.insertTick(
                    com.vedx.vedxsuper.database.TickEntity(
                        symbol = tick.symbol,
                        price = tick.ltp,
                        volume = tick.volume,
                        openInterest = tick.openInterest,
                        timestamp = tick.timestamp
                    )
                )
                _ticksSavedCount.value += 1
            }
        }

        val currentVix = marketDataManager.indiaVix.value
        
        // Feed UI Engine
        phase3Engine.onTick(tick, currentVix)

        val indices = listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX")
        if (indices.contains(tick.symbol)) {
            phase3Engine.setIndex(tick.symbol)
            correlationEngine.setIndex(tick.symbol)
            institutionalEngines[tick.symbol]?.onIndexTick(tick, currentVix)
            
            // [NEW] Generate Virtual Option Ticks for the Matrix if real ones are missing
            if (tick.token == "26000" || tick.token == "26009" || tick.token == "26037" || tick.token == "1") {
                 generateVirtualOptionTicks(tick)
            }
        } else if (tick.symbol.contains("CE") || tick.symbol.contains("PE")) {
            val indexSymbol = when {
                tick.symbol.contains("NIFTY") -> "NIFTY"
                tick.symbol.contains("BANKNIFTY") -> "BANKNIFTY"
                tick.symbol.contains("SENSEX") -> "SENSEX"
                tick.symbol.contains("FINNIFTY") -> "FINNIFTY"
                else -> ""
            }
            
            // Check cache before fetching history to save bandwidth
            if (!candleBuilders.containsKey(tick.token)) {
                scope.launch(Dispatchers.IO) {
                    val existing = candleDao.getLastCandles(tick.symbol, 1)
                    if (existing.isEmpty() && tokenManager.hasValidSession()) {
                        fetchOptionHistoricalData(tick.symbol, tick.token)
                    }
                }
            }

            institutionalEngines[indexSymbol]?.onOptionTick(tick, marketDataManager.getLtp(indexSymbol), currentVix)
        }

        // Position Tracking
        val activePos = virtualTradeManager.activePosition.value
        if (activePos != null && activePos.symbol == tick.symbol) {
            scope.launch {
                virtualTradeManager.onPriceUpdate(tick.ltp, settingsManager.getStrategyConfig())
            }
        }

        // [FIXED] Point 5/6: Process tick through CandleBuilder and SignalEngine
        val builder = candleBuilders.getOrPut(tick.token) { 
            val timeframe = settingsManager.getAnalysisTimeframe()
            val b = CandleBuilder(timeframe)
            scope.launch(Dispatchers.IO) {
                val historical = candleDao.getLastCandles(tick.symbol, 200).map { entity ->
                    Candle(entity.timestamp, entity.open, entity.high, entity.low, entity.close, isComplete = true)
                }.reversed()
                b.initialize(historical)
            }

            scope.launch {
                b.candles.collect { candles ->
                    strategyEngines[tick.token]?.onCandlesUpdated(candles, tick.symbol)
                }
            }
            scope.launch {
                b.finalizedCandleFlow.collect { candle ->
                    candle?.let { saveCandleToDbInternal(tick.symbol, it) }
                }
            }
            b
        }
        
        builder.onTick(tick) // Built candles from ticks (Removed duplicate call)
        
        if (!strategyEngines.containsKey(tick.token)) {
            val engine = SignalEngine(settingsManager.getAtrPeriod())
            engine.updateConfig(settingsManager.getStrategyConfig())
            strategyEngines[tick.token] = engine
        }
    }

    private suspend fun preloadDataFromDb() {
        val indices = listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX")
        val vix = marketDataManager.indiaVix.value
        indices.forEach { symbol ->
            // [UPDATED] Load 1500 candles (~1.5 days) to ensure immediate chart availability
            val historical = candleDao.getLastCandles(symbol, 1500)
                .reversed()
                .map { Candle(it.timestamp, it.open, it.high, it.low, it.close, isComplete = true) }
            
            if (historical.isNotEmpty()) {
                institutionalEngines[symbol]?.initialize(historical)
                // Also initialize Phase 3 engine for UI charts
                phase3Engine.initializeIndex(historical, vix)
            }
        }
    }

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus = _syncStatus.asStateFlow()
    private var isSyncInProgress = false

    fun syncAllHistory() {
        if (isSyncInProgress) return
        isSyncInProgress = true
        scope.launch(Dispatchers.IO) {
            try {
                _syncStatus.value = "Starting Sync..."
                for ((symbol, token) in indicesToDownload) {
                    // Download in 10-day chunks to stay within Angel One's 8000 record limit for ONE_MINUTE
                    for (i in 5 downTo 0) {
                        val start = -(i + 1) * 10
                        val end = -i * 10
                        _syncStatus.value = "Downloading $symbol (${5-i + 1}/6)..."
                        downloadChunk(symbol, token, start, end)
                        if (_syncStatus.value.contains("Error")) break
                        kotlinx.coroutines.delay(1000) // Rate limit protection
                    }
                    if (_syncStatus.value.contains("Error")) break
                }
                
                if (!_syncStatus.value.contains("Error")) {
                    _syncStatus.value = "Sync Complete. Initializing..."
                    preloadDataFromDb()
                    _syncStatus.value = "Ready"
                }
            } catch (e: Exception) {
                _syncStatus.value = "Sync Failed: ${e.message}"
            } finally {
                isSyncInProgress = false
            }
        }
    }

    private val indicesToDownload = mapOf(
        "NIFTY" to "99926000", "BANKNIFTY" to "99926009", "FINNIFTY" to "99926037", "SENSEX" to "9991"
    )

    private suspend fun downloadChunk(symbol: String, token: String, startDays: Int, endDays: Int) {
        val sdfRequest = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        
        // Angel One Returns: "2024-05-20T09:15:00+05:30"
        val sdfParse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        
        val creds = tokenManager.getCredentials()
        val apiKey = creds["api_key"] ?: run {
            _syncStatus.value = "Error: API Key Missing"
            return
        }
        val jwtToken = "Bearer " + tokenManager.getJwtToken()
        
        val exchange = if (symbol == "SENSEX") "BSE" else "NSE"

        val calStart = Calendar.getInstance()
        calStart.add(Calendar.DAY_OF_YEAR, startDays)
        val fromDate = sdfRequest.format(calStart.time)

        val calEnd = Calendar.getInstance()
        calEnd.add(Calendar.DAY_OF_YEAR, endDays)
        val toDate = sdfRequest.format(calEnd.time)

        // For historical data, Angel One needs just the token, but some Indices need prefix handling
        val requestToken = if (token.startsWith("999")) token.removePrefix("999") else token
        val request = HistoricalDataRequest(exchange, requestToken, "ONE_MINUTE", fromDate, toDate)
        
        val ip = "106.51.72.100" // Use a more realistic public IP to avoid WAF rejection

        try {
            android.util.Log.d("VedxSync", "Requesting $symbol from $fromDate to $toDate")
            val response = AngelApiClient.api.getHistoricalData(apiKey, jwtToken, clientLocalIp=ip, clientPublicIp=ip, macAddress="02:00:00:00:00:00", request=request)
            
            if (response.isSuccessful && response.body()?.status == true) {
                val data = response.body()?.data
                if (data.isNullOrEmpty()) {
                    android.util.Log.w("VedxSync", "No data returned for $symbol ($startDays to $endDays)")
                    _syncStatus.value = "No Data for $symbol"
                    return
                }
                
                val candlesToSave = data.mapNotNull { row ->
                    if (row.size >= 5) {
                        try {
                            // Extract just the YYYY-MM-DDTHH:MM:SS part for parsing (ignore timezone offset)
                            val rawTime = row[0].toString().split("+")[0]
                            val time = sdfParse.parse(rawTime)?.time ?: 0L
                            Candle(
                                timestamp = time, 
                                open = row[1].toString().toDouble(), 
                                high = row[2].toString().toDouble(), 
                                low = row[3].toString().toDouble(), 
                                close = row[4].toString().toDouble(), 
                                isComplete = true
                            )
                        } catch (e: Exception) { null }
                    } else null
                }
                
                _syncStatus.value = "Saving ${candlesToSave.size} candles..."
                
                dbMutex.withLock {
                    candlesToSave.forEach { candle ->
                        candleDao.insertCandle(CandleEntity(
                            symbol = symbol,
                            open = candle.open,
                            high = candle.high,
                            low = candle.low,
                            close = candle.close,
                            volume = 0L,
                            timestamp = candle.timestamp
                        ))
                    }
                }
                
                // If it's the recent chunk, initialize engines
                if (endDays >= 0) {
                    val initCandles = candleDao.getLastCandles(symbol, 1500)
                        .reversed()
                        .map { Candle(it.timestamp, it.open, it.high, it.low, it.close, isComplete = true) }
                    
                    if (initCandles.isNotEmpty()) {
                        institutionalEngines[symbol]?.initialize(initCandles)
                        phase3Engine.initializeIndex(initCandles, marketDataManager.indiaVix.value)
                    }
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                if (errorBody.contains("<html") || response.code() == 200) {
                    // This handles the "Request Rejected" HTML case which sometimes returns code 200
                    android.util.Log.e("VedxSync", "WAF Rejection for $symbol. Error: $errorBody")
                    _syncStatus.value = "Error: API Firewall Rejected Request"
                    return
                }

                if (response.code() == 401) {
                    _syncStatus.value = "Error: Session Expired"
                    return
                } else if (response.code() == 429) {
                    _syncStatus.value = "Error: Rate Limit"
                    return
                } else {
                    val errorMsg = response.body()?.message ?: "API Error ${response.code()}"
                    android.util.Log.e("VedxSync", "API Error ($symbol): $errorMsg")
                    _syncStatus.value = errorMsg
                    return
                }
            }
            kotlinx.coroutines.delay(1500) // Increase delay to avoid rate limiting
        } catch (e: Exception) { 
            android.util.Log.e("VedxSync", "Exception downloading $symbol", e)
            _syncStatus.value = "Exception: ${e.message}"
        }
    }

    private suspend fun fetchHistoricalData() {
        _syncStatus.value = "Checking Data..."
        indicesToDownload.forEach { (symbol, token) ->
            val lastCandle = candleDao.getLastCandles(symbol, 1).firstOrNull()
            val needsHistory = lastCandle == null || candleDao.getLastCandles(symbol, 1000).size < 800
            val needsUpdate = lastCandle != null && (System.currentTimeMillis() - lastCandle.timestamp > 3600000)

            if (needsHistory || needsUpdate) {
                _syncStatus.value = "Updating $symbol..."
                // If no data, fetch full 60 days. Otherwise, just fetch the last 2 days.
                if (lastCandle == null) {
                    downloadChunk(symbol, token, -60, -31)
                    downloadChunk(symbol, token, -30, 0)
                } else {
                    downloadChunk(symbol, token, -2, 0)
                }
            } else {
                // Even if no download needed, ensure engines are initialized from DB
                val initCandles = candleDao.getLastCandles(symbol, 1500)
                    .reversed()
                    .map { Candle(it.timestamp, it.open, it.high, it.low, it.close, isComplete = true) }
                
                if (initCandles.isNotEmpty()) {
                    institutionalEngines[symbol]?.initialize(initCandles)
                    phase3Engine.initializeIndex(initCandles, marketDataManager.indiaVix.value)
                }
            }
        }
        _syncStatus.value = "Ready"
    }

    private suspend fun fetchOptionHistoricalData(symbol: String, token: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val toDate = sdf.format(Date())
        val calendar = Calendar.getInstance()
        // [UPDATED] Only fetch 2 days of option history for trading to keep it light
        val daysToFetch = 2
        calendar.add(Calendar.DAY_OF_YEAR, -daysToFetch)
        val fromDate = sdf.format(calendar.time)
        
        val creds = tokenManager.getCredentials()
        val apiKey = creds["api_key"] ?: return
        val jwtToken = "Bearer " + tokenManager.getJwtToken()

        val request = HistoricalDataRequest("NFO", token, "ONE_MINUTE", fromDate, toDate)
        val ip = "106.51.72.100"

        try {
            val response = AngelApiClient.api.getHistoricalData(
                apiKey = apiKey,
                jwtToken = jwtToken,
                clientLocalIp = ip,
                clientPublicIp = ip,
                macAddress = "02:00:00:00:00:00",
                request = request
            )
            if (response.isSuccessful && response.body()?.status == true) {
                val data = response.body()?.data
                if (!data.isNullOrEmpty()) {
                    val candles = data.mapNotNull { row ->
                        if (row.size >= 5) {
                            try {
                                val time = sdf.parse(row[0].toString())?.time ?: 0L
                                Candle(time, row[1].toString().toDouble(), row[2].toString().toDouble(), row[3].toString().toDouble(), row[4].toString().toDouble(), isComplete = true)
                            } catch (e: Exception) { null }
                        } else null
                    }
                    
                    // Batch save to DB
                    dbMutex.withLock {
                        candles.forEach { candle ->
                            candleDao.insertCandle(CandleEntity(
                                symbol = symbol, open = candle.open, high = candle.high,
                                low = candle.low, close = candle.close, volume = 0L,
                                timestamp = candle.timestamp
                            ))
                        }
                    }
                    
                    // Initialize UI engines for this option
                    val vix = marketDataManager.indiaVix.value
                    phase3Engine.initializeOption(symbol, candles, vix)
                    
                    android.util.Log.d("VedxSync", "Downloaded ${candles.size} candles for Option $symbol")
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveCandleToDbInternal(symbol: String, candle: Candle) {
        if (!settingsManager.isAutoSaveEnabled()) return

        scope.launch(Dispatchers.IO) {
            dbMutex.withLock {
                candleDao.insertCandle(CandleEntity(
                    symbol = symbol,
                    open = candle.open,
                    high = candle.high,
                    low = candle.low,
                    close = candle.close,
                    volume = 0L,
                    timestamp = candle.timestamp
                ))
            }
        }
    }

    fun getNeuralMatrixSymbols(): Flow<List<String>> = phase3Engine.state.map { it.targetOptionSymbols }.distinctUntilChanged()

    /**
     * [FIXED] Resolves option symbols to tokens for WebSocket subscription.
     */
    fun getNeuralMatrixTokens(): Flow<List<String>> = getNeuralMatrixSymbols().map { symbols ->
        symbols.mapNotNull { symbol ->
            // [MOCK/FIX] In production, this should use a proper token mapper
            // For now, we resolve it via OptionSelector if possible or use custom logic
            OptionSelector().resolveToken(symbol) 
        }
    }

    fun getCandleBuilder(token: String): CandleBuilder? = candleBuilders[token]
    fun getCandles(token: String): List<Candle> = candleBuilders[token]?.candles?.value ?: emptyList()
    fun getStrategyEngine(token: String): SignalEngine? = strategyEngines[token]
    fun getCorrelationSignals(): Flow<List<CorrelationSignal>> = correlationEngine.signals
    fun getInstitutionalSignals(): Flow<List<InstitutionalSignal>> = institutionalEngines.values.map { it.signals }.merge()

    fun getIndexStrength(symbol: String = "NIFTY") = institutionalEngines[symbol]?.getIndexStrength() ?: StrengthMetrics()
    fun getOptionStrength(symbol: String): StrengthMetrics {
        institutionalEngines.values.forEach { engine ->
            val s = engine.getOptionStrength(symbol)
            if (s.trendStrength > 0) return s
        }
        return StrengthMetrics()
    }

    fun getTrendLifecycle(symbol: String = "NIFTY") = institutionalEngines[symbol]?.getTrendLifecycle()
    fun getTrendState(symbol: String) = institutionalEngines[symbol]?.getTrendState()
    fun getMarketRegime(symbol: String) = institutionalEngines[symbol]?.getMarketRegime()
    fun getMarketStructure(symbol: String) = institutionalEngines[symbol]?.getMarketStructure()
    fun getLearningStats() = institutionalEngines["NIFTY"]?.getLearningStats() ?: com.vedx.vedxsuper.strategy.engine.LearningStats()
    fun getRiskState() = institutionalEngines["NIFTY"]?.getRiskState() ?: com.vedx.vedxsuper.strategy.engine.AccountState()
    fun getVix() = marketDataManager.indiaVix.value
    fun getIndexMultiTrend(symbol: String) = institutionalEngines[symbol]?.indexMultiTrend
    fun getIndexZoneStatus(symbol: String) = institutionalEngines[symbol]?.indexZoneStatus
    fun getIndexCandles(symbol: String) = institutionalEngines[symbol]?.getIndexCandles() ?: emptyList<Candle>()
    fun getIndexAgents(symbol: String, price: Double, vix: Double = 15.0) = institutionalEngines[symbol]?.getIndexAgents(price, vix) ?: emptyList<AgentReport>()
    fun getOptionAgents(symbol: String, vix: Double = 15.0) = institutionalEngines.values.firstNotNullOfOrNull { it.getOptionAgents(symbol, vix).ifEmpty { null } } ?: emptyList<AgentReport>()
    
    fun getOptionMultiTrend(symbol: String): MultiSuperTrendResult? {
        institutionalEngines.values.forEach { it.getOptionMultiTrend(symbol)?.let { res -> return res } }
        return null
    }
    
    fun getIntelligence(symbol: String): InstitutionalStrategyEngine.OptionIntelligence? {
        institutionalEngines.values.forEach { it.getIntelligence(symbol)?.let { res -> return res } }
        return null
    }

    fun getLearningWeights() = institutionalEngines["NIFTY"]?.getLearningWeights() ?: emptyMap<String, Double>()
    fun getOptionMetrics(symbol: String): InstitutionalStrategyEngine.OptionState? {
        institutionalEngines.values.forEach { it.getOptionMetrics(symbol)?.let { res -> return res } }
        return null
    }

    fun getVirtualBalance() = settingsManager.getVirtualBalance()
    fun getVirtualBalanceFlow() = settingsManager.virtualBalance
    fun addFunds(amount: Double) = settingsManager.addVirtualBalance(amount)
    fun withdrawFunds(amount: Double) = settingsManager.withdrawVirtualBalance(amount)

    fun isMarketOpen(): Boolean {
        val c = Calendar.getInstance()
        val d = c.get(Calendar.DAY_OF_WEEK)
        if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) return false
        val t = c.get(Calendar.HOUR_OF_DAY) * 100 + c.get(Calendar.MINUTE)
        return t in 915..1530
    }

    fun getActiveIndices() = settingsManager.getActiveIndices()
    fun toggleIndex(symbol: String) = settingsManager.toggleIndex(symbol)

    fun setSelectedDashboardIndex(symbol: String) {
        settingsManager.prefs.edit().putString("selected_index", symbol).apply()
    }

    /**
     * [FIXED] Point 9: Comprehensive Logout Cleanup
     * Stops all engines, clears cache, and resets state for a clean session restart.
     */
    fun logoutCleanup() {
        scope.launch {
            // 1. Reset all Strategy Engines
            institutionalEngines.values.forEach { it.reset() }
            strategyEngines.values.forEach { it.reset() }
            
            // 2. Clear In-memory Caches
            candleBuilders.clear()
            strategyEngines.clear()
            processedSignals.clear()
            lastTickSavedTime.clear()
            
            // 3. Reset UI Indicators
            _syncStatus.value = "Idle"
            
            android.util.Log.i("MarketRepo", "Logout Cleanup Complete: All engines and caches cleared.")
        }
    }

    fun getMultiTrendState(symbol: String): Flow<MultiTrendStrategyState?> {
        return phase3Engine.state.map { if (it.indexSymbol == symbol) it.indexState else it.optionStates[symbol] }
    }
}
