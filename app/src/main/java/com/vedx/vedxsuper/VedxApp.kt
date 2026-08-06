package com.vedx.vedxsuper

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.vedx.vedxsuper.api.AngelClient
import com.vedx.vedxsuper.auth.AutoLoginManager
import com.vedx.vedxsuper.auth.BiometricAuthManager
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.core.*
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.notification.TradeNotificationManager
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.stream.FastTickEngine
import com.vedx.vedxsuper.trade.VirtualTradeManager
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.*

class VedxApp : Application() {

    lateinit var secureTokenManager: SecureTokenManager
    lateinit var angelClient: AngelClient
    lateinit var appDatabase: AppDB
    lateinit var virtualTradeManager: VirtualTradeManager
    lateinit var settingsManager: SettingsManager
    lateinit var tradeNotificationManager: TradeNotificationManager
    lateinit var ultraNeuralCore: UltraNeuralCore
    lateinit var autoLoginManager: AutoLoginManager
    lateinit var biometricAuthManager: BiometricAuthManager
    
    // Infrastructure needed for background processing
    var engine: FastTickEngine? = null
    lateinit var tradeRepository: TradeRepository
    lateinit var risk: RiskEngine
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize all components
        secureTokenManager = SecureTokenManager(this)
        secureTokenManager.migrateFromLegacyPrefs(this)

        angelClient = AngelClient()
        angelClient.isPaperTrading = true // FORCE PAPER TRADING ONLY

        appDatabase = AppDB.get(this)
        virtualTradeManager = VirtualTradeManager(this)
        settingsManager = SettingsManager(this)
        tradeNotificationManager = TradeNotificationManager(this)
        ultraNeuralCore = UltraNeuralCore(Symbol("NIFTY"))
        autoLoginManager = AutoLoginManager(this, secureTokenManager, angelClient)
        biometricAuthManager = BiometricAuthManager(this, secureTokenManager)
        
        tradeRepository = TradeRepository(appDatabase.td())
        risk = RiskEngine()

        val tok = secureTokenManager.getJwtToken()
        val cc = secureTokenManager.getClientCode() ?: ""
        if (tok != null) {
            angelClient.token = tok
            engine = FastTickEngine(tok, cc, ultraNeuralCore, scope)
            startService()
        }
    }
    
    fun startService() = startForegroundService(Intent(this, VedxService::class.java))

    companion object {
        lateinit var instance: VedxApp
    }
}

class VedxService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onCreate() {
        super.onCreate()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("v", "Vedx", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        startForeground(1, NotificationCompat.Builder(this, "v")
            .setContentTitle("VedxSuper AI Pro")
            .setContentText("7-ST Match Strategy Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build())
        
        val app = application as VedxApp
        app.engine?.connect()
        
        scope.launch {
            app.ultraNeuralCore.signals.collect { sigs ->
                sigs.lastOrNull()?.let { s ->
                    if (s.isEntry) {
                        // Send notification for confirmation (Paper Trade)
                        app.tradeNotificationManager.sendPreTradeNotification(
                            s.symbol.value, 
                            if (s.action == Actions.BUY) "BUY" else "SELL",
                            s.entryPrice.rupees,
                            s.stopLoss.rupees,
                            s.target.rupees,
                            s.confidence.pct,
                            s.reason
                        )

                        if (app.risk.canTrade(s.entryPrice.cents * s.quantity, s.stopLoss.cents)) {
                            app.risk.onEntry(s.entryPrice.cents * s.quantity)
                            
                            if (s.action == Actions.BUY) {
                                app.virtualTradeManager.executeVirtualBuy(
                                    s.symbol.value, s.entryPrice.rupees, s.quantity,
                                    s.stopLoss.rupees, s.target.rupees, s.confidence.pct, s.reason
                                )
                            } else if (s.action == Actions.SELL) {
                                app.virtualTradeManager.executeVirtualSell(
                                    s.symbol.value, s.entryPrice.rupees, s.quantity,
                                    s.stopLoss.rupees, s.target.rupees, s.confidence.pct, s.reason
                                )
                            }
                            
                            val tok = if (s.symbol.value.contains("BANKNIFTY")) "26009" else "26000"
                            if (!app.angelClient.isPaperTrading) {
                                if (s.action == Actions.BUY) app.angelClient.buy(s.symbol.value, tok, s.quantity, s.entryPrice.rupees, s.stopLoss.rupees)
                                else app.angelClient.sell(s.symbol.value, tok, s.quantity, s.entryPrice.rupees, s.stopLoss.rupees)
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onBind(i: Intent?) = null
    override fun onDestroy() {
        val app = application as VedxApp
        app.engine?.disconnect()
        scope.cancel()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val secureTokenManager = SecureTokenManager(c)
        if (secureTokenManager.hasValidSession()) {
            val app = c.applicationContext as VedxApp
            val tok = secureTokenManager.getJwtToken()
            val cc = secureTokenManager.getClientCode() ?: ""
            if (tok != null) {
                app.angelClient.token = tok
                if (app.engine == null) {
                    app.engine = FastTickEngine(tok, cc, app.ultraNeuralCore, app.scope)
                }
                c.startForegroundService(Intent(c, VedxService::class.java))
            }
        }
    }
}
