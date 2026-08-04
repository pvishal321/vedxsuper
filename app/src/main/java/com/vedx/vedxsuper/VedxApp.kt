package com.vedx.vedxsuper

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.vedx.vedxsuper.api.AngelClient
import com.vedx.vedxsuper.core.*
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.stream.FastTickEngine
import kotlinx.coroutines.*

class VedxApp : Application() {
    lateinit var core: UltraNeuralCore 
    lateinit var client: AngelClient
    lateinit var risk: RiskEngine
    lateinit var engine: FastTickEngine
    lateinit var db: AppDB
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDB.get(this)
        core = UltraNeuralCore(Symbol("NIFTY"))
        client = AngelClient()
        risk = RiskEngine()
        
        val prefs = getSharedPreferences("v", 0)
        val tok = prefs.getString("tok", null)
        val cc = prefs.getString("cc", "") ?: ""
        if (tok != null) {
            client.token = tok
            engine = FastTickEngine(tok, cc, core, scope)
            startService()
        }
    }
    
    fun startService() = startForegroundService(Intent(this, VedxService::class.java))
    
    companion object { lateinit var instance: VedxApp }
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
        app.engine.connect()
        
        scope.launch {
            app.core.signals.collect { sigs ->
                sigs.lastOrNull()?.let { s ->
                    if (s.isEntry && app.risk.canTrade(s.entryPrice.cents * s.quantity, s.stopLoss.cents)) {
                        app.risk.onEntry(s.entryPrice.cents * s.quantity)
                        val tok = if (s.symbol.value.contains("BANKNIFTY")) "26009" else "26000"
                        if (s.action == Actions.BUY) app.client.buy(s.symbol.value, tok, s.quantity, s.entryPrice.rupees, s.stopLoss.rupees)
                        else app.client.sell(s.symbol.value, tok, s.quantity, s.entryPrice.rupees, s.stopLoss.rupees)
                        notify("🎯 ${s.symbol.value}", "${s.reason} | Conf:${s.confidence.pct}%")
                    }
                }
            }
        }
    }
    
    private fun notify(t: String, m: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(this, "v")
            .setContentTitle(t).setContentText(m).setSmallIcon(android.R.drawable.ic_dialog_info).build())
    }
    
    override fun onBind(i: Intent?) = null
    override fun onDestroy() {
        (application as VedxApp).engine.disconnect()
        scope.cancel()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("v", 0).getString("tok", null) != null)
            c.startForegroundService(Intent(c, VedxService::class.java))
    }
}
