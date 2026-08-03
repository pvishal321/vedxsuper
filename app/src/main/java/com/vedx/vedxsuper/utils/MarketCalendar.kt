package com.vedx.vedxsuper.utils

import java.util.*

class MarketCalendar {

    // NSE/BSE Holidays 2026 (Official List based on 2026 Calendar)
    private val holidays2026 = setOf(
        "2026-01-26", // Republic Day
        "2026-03-03", // Holi
        "2026-03-26", // Shri Ram Navami
        "2026-03-31", // Shri Mahavir Jayanti
        "2026-04-03", // Good Friday
        "2026-04-14", // Dr. Baba Saheb Ambedkar Jayanti
        "2026-05-01", // Maharashtra Day
        "2026-05-28", // Bakri Id
        "2026-06-26", // Muharram
        "2026-09-14", // Ganesh Chaturthi
        "2026-10-02", // Mahatma Gandhi Jayanti
        "2026-10-20", // Dussehra
        "2026-11-10", // Diwali-Balipratipada
        "2026-11-24", // Guru Nanak Jayanti
        "2026-12-25"  // Christmas
    )

    fun isHoliday(calendar: Calendar): Boolean {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return true

        val dateStr = String.format(Locale.US, "%04d-%02d-%02d", 
            calendar.get(Calendar.YEAR), 
            calendar.get(Calendar.MONTH) + 1, 
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        return holidays2026.contains(dateStr)
    }

    /**
     * Auto-detects the year and checks holidays. 
     * In a future update, this could fetch from an API.
     */
    fun isTodayHoliday(): Boolean {
        return isHoliday(Calendar.getInstance())
    }

    fun getPrecedingTradingDay(calendar: Calendar): Calendar {
        val result = calendar.clone() as Calendar
        result.add(Calendar.DAY_OF_YEAR, -1)
        while (isHoliday(result)) {
            result.add(Calendar.DAY_OF_YEAR, -1)
        }
        return result
    }

    fun getThetaMeltingPressure(): Double {
        val now = Calendar.getInstance()
        var meltingScore = 1.0 
        
        val checkDays = 3
        for (i in 1..checkDays) {
            val future = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            if (isHoliday(future)) {
                meltingScore += (0.6 / i)
            }
        }
        
        if (now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && now.get(Calendar.HOUR_OF_DAY) >= 14) {
            meltingScore += 0.4
        }
        
        return meltingScore
    }
}
