package com.vedx.vedxsuper

import android.app.*
import android.content.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vedx.vedxsuper.api.AngelClient
import com.vedx.vedxsuper.auth.*
import com.vedx.vedxsuper.core.*
import com.vedx.vedxsuper.core.event.EventBus
import com.vedx.vedxsuper.core.market.MarketFeedEngine
import com.vedx.vedxsuper.core.portfolio.PortfolioEngine
import com.vedx.vedxsuper.core.risk.RiskEngine
import com.vedx.vedxsuper.core.trade.VirtualTradeEngine
import com.vedx.vedxsuper.core.learning.LearningEngine
import com.vedx.vedxsuper.core.audit.AuditEngine
import com.vedx.vedxsuper.core.strategy.*
import com.vedx.vedxsuper.core.state.AppStateStore
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.notification.TradeNotificationManager
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VedxApp : Application() {

    lateinit var secureTokenManagerV2: SecureTokenManagerV2
    lateinit var angelClient: AngelClient
    lateinit var appDatabase: AppDB
    lateinit var settingsManager: SettingsManager
    lateinit var tradeNotificationManager: TradeNotificationManager
    lateinit var ultraNeuralCore: UltraNeuralCore
    lateinit var autoLoginManagerV2: AutoLoginManagerV2
    lateinit var biometricAuthManager: BiometricAuthManager
    lateinit var eventBus: EventBus
    lateinit var optionDataManager: OptionDataManager
    lateinit var portfolio: PortfolioEngine
    lateinit var virtualTrade: VirtualTradeEngine
    lateinit var appStateStore: AppStateStore
    lateinit var brokerAuth: BrokerAuthManagerV2
    
    // Infrastructure needed for background processing
    lateinit var marketFeedEngine: MarketFeedEngine
    lateinit var tradeRepository: TradeRepository
    lateinit var risk: RiskEngine
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var isReady = false
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize only essential components on main thread
        eventBus = EventBus()
        appStateStore = AppStateStore()
        
        // Move heavy initialization to background thread
        scope.launch(Dispatchers.IO) {
            try {
                appDatabase = AppDB.get(this@VedxApp)
                settingsManager = SettingsManager(this@VedxApp)
                tradeNotificationManager = TradeNotificationManager(this@VedxApp)
                optionDataManager = OptionDataManager(this@VedxApp)
                secureTokenManagerV2 = SecureTokenManagerV2(this@VedxApp)
                
                // Migration with error handling
                try {
                    secureTokenManagerV2.migrateFromLegacyV1(getSharedPreferences("v", 0))
                } catch (e: Exception) {
                    Log.e("VedxApp", "Migration failed", e)
                }

                val stored = secureTokenManagerV2.getStoredTokens()
                marketFeedEngine = MarketFeedEngine(
                    stored?.jwt ?: "",
                    stored?.clientCode ?: "",
                    stored?.feedToken ?: "",
                    stored?.apiKey ?: "",
                    optionDataManager,
                    eventBus,
                    scope
                )

                angelClient = AngelClient()
                angelClient.isPaperTrading = true // FORCE PAPER TRADING ONLY

                // Core Engines
                risk = RiskEngine(riskDao = appDatabase.rd())
                portfolio = PortfolioEngine(initialBalance = TradingConstants.INITIAL_VIRTUAL_BALANCE, vtd = appDatabase.vtd())
                val contextEngine = ContextEngine()
                val signalEngine = SignalEngine(risk)
                val auditEngine = AuditEngine()
                val learningEngine = LearningEngine(ld = appDatabase.ld())
                val analyticsEngine = AnalyticsEngine()
                
                virtualTrade = VirtualTradeEngine(portfolio, risk, scope) { trade, pnl, status ->
                    tradeNotificationManager.sendTradeClosedNotification(trade.symbol, pnl, status.name)
                }

                val services = CoreServices(
                    risk = risk,
                    portfolio = portfolio,
                    context = contextEngine,
                    signalEngine = signalEngine,
                    virtualTrade = virtualTrade,
                    learning = learningEngine,
                    audit = auditEngine,
                    analytics = analyticsEngine,
                    eventBus = eventBus,
                    stateStore = appStateStore
                )
                
                ultraNeuralCore = UltraNeuralCore(Symbol("NIFTY"), services)

                val wsManager = object : BrokerAuthManagerV2.WebSocketManager {
                    override suspend fun validateAuth() = true
                    override suspend fun connectFeed() { 
                        if(this@VedxApp::marketFeedEngine.isInitialized) {
                            val tokens = secureTokenManagerV2.getStoredTokens()
                            if (tokens != null) {
                                marketFeedEngine.updateAuth(tokens.jwt, tokens.clientCode, tokens.feedToken, tokens.apiKey)
                                angelClient.token = tokens.jwt
                                angelClient.apiKey = tokens.apiKey
                                angelClient.feedToken = tokens.feedToken
                            }
                            marketFeedEngine.connect() 
                            startService()
                        }
                    }
                    override suspend fun subscribeToIndices() {}
                    override suspend fun subscribeToOptions() {}
                    override suspend fun unsubscribeAll() {}
                    override suspend fun disconnectFeed() { if(this@VedxApp::marketFeedEngine.isInitialized) marketFeedEngine.disconnect() }
                    override suspend fun disconnect() { if(this@VedxApp::marketFeedEngine.isInitialized) marketFeedEngine.disconnect() }
                }
                brokerAuth = BrokerAuthManagerV2(wsManager)

                autoLoginManagerV2 = AutoLoginManagerV2(this@VedxApp, secureTokenManagerV2, angelClient, brokerAuth)
                biometricAuthManager = BiometricAuthManager(this@VedxApp, secureTokenManagerV2)
                tradeRepository = TradeRepository(appDatabase.td())

                isReady = true

                if (stored != null) {
                    angelClient.token = stored.jwt
                    angelClient.apiKey = stored.apiKey
                    angelClient.feedToken = stored.feedToken
                    withContext(Dispatchers.Main) {
                        startService()
                    }
                }
            } catch (e: Exception) {
                Log.e("VedxApp", "Critical initialization failure", e)
            }
        }
    }
    
    fun startService() = startForegroundService(Intent(this, VedxService::class.java))
}

class VedxService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        val channelId = "vedx_super_foreground_v1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Vedx Market Monitor", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        startForeground(1, NotificationCompat.Builder(this, channelId)
            .setContentTitle("VedxSuper Active")
            .setContentText("Monitoring market signals...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build())
        
        val app = application as VedxApp
        scope.launch {
            // Wait for engines to be initialized
            while(!app.isReady) {
                delay(500)
            }
            
            app.marketFeedEngine.connect()
            
            app.ultraNeuralCore.signals.collect { sigs ->
                try {
                    sigs.lastOrNull()?.let { s ->
                        if (s.isEntry) {
                            app.tradeNotificationManager.sendPreTradeNotification(
                                s.symbol.value, 
                                if (s.action == Actions.BUY) "BUY" else "SELL",
                                s.entryPrice.rupees,
                                s.stopLoss.rupees,
                                s.target.rupees,
                                s.confidence.pct,
                                s.reason
                            )
                            app.virtualTrade.executeSignal(s)

                            // Audit Fix 37: Live trade execution with result handling
                            if (!app.angelClient.isPaperTrading) {
                                val tok = if (s.symbol.value.contains("BANKNIFTY")) "26009" else "26000"
                                scope.launch {
                                    val result = if (s.action == Actions.BUY) {
                                        app.angelClient.buy(s.symbol.value, tok, s.quantity, s.entryPrice.rupees, s.stopLoss.rupees)
                                    } else {
                                        app.angelClient.sell(s.symbol.value, tok, s.quantity, s.entryPrice.rupees, s.stopLoss.rupees)
                                    }
                                    
                                    result.onFailure { error ->
                                        Log.e("VedxService", "LIVE_ORDER_FAILED: ${error.message}")
                                        app.tradeNotificationManager.sendTradeClosedNotification(s.symbol.value, 0, "ORDER_FAILED: ${error.message}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VedxService", "Error executing signal", e)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            val app = application as VedxApp
            scope.launch {
                while(!app.isReady) delay(100)
                if (app.secureTokenManagerV2.getStoredTokens() == null) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(i: Intent?) = null
    override fun onDestroy() {
        val app = application as VedxApp
        if (app.isReady) {
            app.marketFeedEngine.disconnect()
            app.autoLoginManagerV2.cleanup()
        }
        scope.cancel()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        
        val stm = SecureTokenManagerV2(c)
        val sm = SettingsManager(c)
        // Note: sm.autoStartOnBoot is a StateFlow, we can't easily collect it here
        // so we read directly from prefs or just use the value if it's already loaded
        // Since sm is newly created, its value will be read from prefs in its init
        if (sm.autoStartOnBoot.value && stm.getStoredTokens() != null) {
            c.startForegroundService(Intent(c, VedxService::class.java))
        }
    }
}
