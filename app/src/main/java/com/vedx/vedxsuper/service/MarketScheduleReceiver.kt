package com.vedx.vedxsuper.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar
import java.util.TimeZone

class MarketScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == "START_MARKET_MONITORING") {
            val serviceIntent = Intent(context, TradingBackgroundService::class.java)
            context.startForegroundService(serviceIntent)
        } else if (action == "STOP_MARKET_MONITORING") {
            val serviceIntent = Intent(context, TradingBackgroundService::class.java).apply {
                this.action = "STOP_SERVICE"
            }
            context.stopService(serviceIntent)
        }

        // Reschedule for next day
        scheduleMarketAlarms(context)
    }

    companion object {
        fun scheduleMarketAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val zone = TimeZone.getTimeZone("Asia/Kolkata")

            // 1. Startup Alarm (08:45 AM)
            val startCalendar = Calendar.getInstance(zone).apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 45)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance(zone))) {
                    add(Calendar.DATE, 1)
                }
            }

            val startIntent = Intent(context, MarketScheduleReceiver::class.java).apply {
                action = "START_MARKET_MONITORING"
            }
            val startPendingIntent = PendingIntent.getBroadcast(
                context, 101, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                startCalendar.timeInMillis,
                startPendingIntent
            )

            // 2. Shutdown Alarm (03:35 PM)
            val stopCalendar = Calendar.getInstance(zone).apply {
                set(Calendar.HOUR_OF_DAY, 15)
                set(Calendar.MINUTE, 35)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance(zone))) {
                    add(Calendar.DATE, 1)
                }
            }

            val stopIntent = Intent(context, MarketScheduleReceiver::class.java).apply {
                action = "STOP_MARKET_MONITORING"
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 102, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                stopCalendar.timeInMillis,
                stopPendingIntent
            )
        }
    }
}
