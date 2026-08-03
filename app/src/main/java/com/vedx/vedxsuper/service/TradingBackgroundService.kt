package com.vedx.vedxsuper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vedx.vedxsuper.MainActivity
import com.vedx.vedxsuper.VedxApplication
import com.vedx.vedxsuper.strategy.engine.InstitutionalSignal
import com.vedx.vedxsuper.strategy.engine.MasterAction
import com.vedx.vedxsuper.R
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.repository.AuthRepository
import com.vedx.vedxsuper.repository.MarketRepository
import com.vedx.vedxsuper.utils.NotificationHelper
import com.vedx.vedxsuper.websocket.SmartStreamManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class TradingBackgroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val app by lazy { application as VedxApplication }

    private val ONGOING_NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "trading_service_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(ONGOING_NOTIFICATION_ID, createPersistentNotification("Initializing VedxSuper..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            initializeTrading()
            if (!isMarketOpen()) {
                updateNotification("Market Closed. System in Standby Mode.")
            }
        }

        return START_STICKY
    }

    private fun isMarketOpen(): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute

        val isWeekday = day in Calendar.MONDAY..Calendar.FRIDAY
        // 9:00 AM (start preparing) to 3:40 PM (finished reporting)
        return isWeekday && timeInMinutes in 540..940
    }

    private suspend fun initializeTrading() {
        // 1. Session Management
        if (app.tokenManager.isExpired()) {
            updateNotification("Restoring Broker Session...")
            val result = app.authRepository.silentLogin()
            if (result.isFailure) {
                updateNotification("Session Expired. Please Login Manually.")
                return
            }
        }

        // 2. Connect Feed
        updateNotification("VedxSuper: Live Monitoring Active")
        app.smartStreamManager.connect()

        // 3. Monitor Connection Stability
        serviceScope.launch {
            app.smartStreamManager.connectionState.collectLatest { state ->
                when (state) {
                    com.vedx.vedxsuper.websocket.ConnectionStatus.RECONNECTING -> {
                        updateNotification("Connection Lost. Reconnecting...")
                    }
                    com.vedx.vedxsuper.websocket.ConnectionStatus.LIVE -> {
                        updateNotification("VedxSuper: Live Monitoring Active")
                    }
                    com.vedx.vedxsuper.websocket.ConnectionStatus.ERROR -> {
                        updateNotification("Connection Error. Check Internet.")
                    }
                    else -> {}
                }
            }
        }

        // 4. Observe Signals & Notify
        serviceScope.launch {
            app.marketRepository.getInstitutionalSignals().collectLatest { signals ->
                val latest = signals.lastOrNull() ?: return@collectLatest
                
                // [CRITICAL] Notification Logic for Background
                if (latest.type != "WATCHING" && latest.type != "PREPARING") {
                    app.notificationHelper.showInstitutionalSignal(latest)
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ONGOING_NOTIFICATION_ID, createPersistentNotification(text))
    }

    private fun createPersistentNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, TradingBackgroundService::class.java).apply { action = "STOP_SERVICE" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VedxSuper Trading Assistant")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Trading Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        app.smartStreamManager.disconnect()
        super.onDestroy()
    }
}
