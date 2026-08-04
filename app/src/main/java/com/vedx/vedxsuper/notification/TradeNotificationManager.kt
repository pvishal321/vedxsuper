package com.vedx.vedxsuper.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vedx.vedxsuper.MainActivity
import com.vedx.vedxsuper.R
import java.util.Locale

/**
 * Handles all trade-related notifications.
 * Sends alert BEFORE virtual trade execution for user confirmation.
 */
class TradeNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "vedx_trade_alerts"
        const val CHANNEL_NAME = "Trade Alerts"
        const val CHANNEL_DESC = "Notifications for virtual trade signals and executions"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Send a PRE-TRADE notification asking user to confirm virtual trade
     */
    fun sendPreTradeNotification(
        symbol: String,
        action: String,
        price: Double,
        stopLoss: Double,
        target: Double,
        confidence: Int,
        reason: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("trade_symbol", symbol)
            putExtra("trade_action", action)
            putExtra("trade_price", price)
            putExtra("trade_sl", stopLoss)
            putExtra("trade_target", target)
            putExtra("trade_confidence", confidence)
            putExtra("trade_reason", reason)
            putExtra("show_trade_dialog", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            symbol.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎯 Virtual Trade Signal: $action $symbol")
            .setContentText("Confidence: $confidence% | Tap to review and confirm")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(String.format(Locale.US, """
                    Signal: %s %s
                    Price: ₹%.2f
                    Stop Loss: ₹%.2f
                    Target: ₹%.2f
                    Confidence: %d%%
                    Reason: %s

                    ⚠️ This is a PAPER TRADE notification.
                    Tap to review and confirm in app.
                """.trimIndent(), action, symbol, price, stopLoss, target, confidence, reason)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                "Open App",
                pendingIntent
            )
            .build()

        notificationManager.notify(symbol.hashCode(), notification)
    }

    /**
     * Notify that a virtual trade was executed
     */
    fun sendTradeExecutedNotification(symbol: String, action: String, price: Double, quantity: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_input_get)
            .setContentTitle("✅ Virtual Trade Executed")
            .setContentText(String.format(Locale.US, "%s %d qty of %s @ ₹%.2f", action, quantity, symbol, price))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(String.format(Locale.US, """
                    Virtual Trade Executed (Paper Only)

                    Symbol: %s
                    Action: %s
                    Price: ₹%.2f
                    Quantity: %d

                    💰 This is NOT a real order.
                    Your virtual balance has been updated.
                """.trimIndent(), symbol, action, price, quantity)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((symbol + "_exec").hashCode(), notification)
    }

    /**
     * Notify trade closed (SL hit or Target hit)
     */
    fun sendTradeClosedNotification(symbol: String, pnl: Long, status: String) {
        val isProfit = pnl >= 0
        val emoji = if (isProfit) "🟢" else "🔴"
        val title = "$emoji Virtual Trade Closed: $status"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isProfit) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)
            .setContentTitle(title)
            .setContentText(String.format(Locale.US, "%s | P&L: ₹%d.%02d", symbol, pnl / 100, kotlin.math.abs(pnl % 100)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((symbol + "_closed").hashCode(), notification)
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}
