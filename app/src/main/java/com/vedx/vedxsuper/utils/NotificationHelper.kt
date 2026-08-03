package com.vedx.vedxsuper.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vedx.vedxsuper.MainActivity
import com.vedx.vedxsuper.strategy.engine.InstitutionalSignal
import com.vedx.vedxsuper.strategy.engine.MasterAction
import java.util.Locale

class NotificationHelper(private val context: Context) {
    private val channelId = "vedx_signals"
    private val summaryChannelId = "vedx_summary"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsManager = SettingsManager(context)
    private val voiceAlertHelper = VoiceAlertHelper(context).apply {
        setLanguage(settingsManager.getVoiceLanguage())
    }

    // Maps symbols to notification IDs to allow updates
    private val activeNotifications = mutableMapOf<String, Int>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val signalChannel = NotificationChannel(
                channelId,
                "Trading Signal Intelligence",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High probability institutional trading setups"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val summaryChannel = NotificationChannel(
                summaryChannelId,
                "Market Summary",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily performance and market state summary"
            }

            notificationManager.createNotificationChannel(signalChannel)
            notificationManager.createNotificationChannel(summaryChannel)
        }
    }

    fun showTradeStageNotification(symbol: String, stage: String, message: String, confidence: Int = 0) {
        val settings = SettingsManager(context)
        if (!settings.isNotificationEnabled()) return

        val title = when (stage) {
            "WATCH" -> "👀 MONITORING: $symbol"
            "PREPARE" -> "⚠️ PREPARING: $symbol"
            "ENTRY_READY" -> "🚀 ENTRY READY: $symbol"
            "TARGET" -> "🎯 TARGET HIT: $symbol"
            "TRAIL" -> "🛡️ TRAILING: $symbol"
            "EXIT" -> "🏁 EXIT SIGNAL: $symbol"
            else -> "VEDX: $symbol"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(symbol.hashCode(), notification)
        
        if (stage == "ENTRY_READY" || stage == "EXIT") {
            voiceAlertHelper.speak(message)
        }
    }

    fun showInstitutionalSignal(signal: InstitutionalSignal) {
        val notificationId = activeNotifications.getOrPut(signal.optionSymbol) { signal.optionSymbol.hashCode() }
        val settings = SettingsManager(context)
        val isAutoTrade = settings.isAutoTradeEnabled()
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isBuy = signal.type == "BUY" || signal.type == "RE_ENTRY"
        val color = if (isBuy) 0xFF00B97D.toInt() else 0xFFF23645.toInt()

        val stageText = when(signal.action) {
            MasterAction.WATCH -> "STAGE 1: WATCHING (Setup forming)"
            MasterAction.PREPARE -> "STAGE 2: PREPARING (Price near band)"
            MasterAction.BUY, MasterAction.SELL, MasterAction.RE_ENTRY -> "STAGE 3: ENTRY READY (Confirmed)"
            else -> signal.type
        }

        val footerText = if (isAutoTrade) {
            "Status: EXECUTED AUTOMATICALLY"
        } else {
            "Status: WAITING FOR APPROVAL"
        }

        val expandedText = """
            $stageText
            
            Strike: ${signal.optionSymbol}
            Price: ₹${signal.price} | Target: ₹${signal.target}
            Confidence: ${signal.confidence}%
            RR: 1 : ${String.format(Locale.US, "%.1f", signal.riskReward)}
            Structure: ${signal.structure}
            Reason: ${signal.reason}
            
            $footerText
        """.trimIndent()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🚀 VEDX: ${signal.type} ${signal.optionSymbol}")
            .setContentText("$stageText | Confidence ${signal.confidence}%")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(Notification.DEFAULT_ALL)
            .setColor(color)
            .setColorized(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setFullScreenIntent(pendingIntent, true)
            .setOnlyAlertOnce(false)

        // Voice Alert for High Confidence Entries
        if (settings.isVoiceAlertEnabled() && signal.confidence >= 80 && (signal.action == MasterAction.BUY || signal.action == MasterAction.SELL || signal.action == MasterAction.RE_ENTRY)) {
            voiceAlertHelper.setLanguage(settings.getVoiceLanguage())
            voiceAlertHelper.speak("Attention! Stage three high probability ${signal.type} setup detected for ${signal.optionSymbol}. Confidence ${signal.confidence} percent. $footerText")
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelSignalNotification(symbol: String) {
        activeNotifications.remove(symbol)?.let {
            notificationManager.cancel(it)
        }
    }
}
