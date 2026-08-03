package com.vedx.vedxsuper.strategy.options

import com.vedx.vedxsuper.utils.MarketCalendar
import java.util.*

enum class InstrumentType { OPTION, FUTURE }

/**
 * AI-Ready Expiry & Session Intelligence.
 * 2026+ रेकॉर्ड्स आणि डायनॅमिक मार्केट फेजेस मॅनेज करते.
 */
class ExpiryManager {
    private val marketCalendar = MarketCalendar()
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    enum class SessionPhase {
        PRE_MARKET,       // 09:00 - 09:15
        OPENING_DRIVE,    // 09:15 - 10:30 (High Volatility)
        MID_DAY_CHOP,     // 10:30 - 13:00 (Range bound)
        EUROPEAN_OPEN,    // 13:00 - 14:30 (Trend Reversal potential)
        EXPIRY_RAMP,      // 14:30 - 15:15 (Hero-Zero Window)
        CLOSING_AUCTION,  // 15:15 - 15:30
        POST_MARKET       // After 15:30
    }

    /**
     * सध्याचा मार्केट फेज ओळखते.
     */
    fun getCurrentPhase(): SessionPhase {
        val now = Calendar.getInstance(ist)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val totalMins = hour * 60 + minute

        return when {
            totalMins < 555 -> SessionPhase.PRE_MARKET
            totalMins in 555..630 -> SessionPhase.OPENING_DRIVE
            totalMins in 631..780 -> SessionPhase.MID_DAY_CHOP
            totalMins in 781..870 -> SessionPhase.EUROPEAN_OPEN
            totalMins in 871..915 -> SessionPhase.EXPIRY_RAMP
            totalMins in 916..930 -> SessionPhase.CLOSING_AUCTION
            else -> SessionPhase.POST_MARKET
        }
    }

    /**
     * अचूक एक्सपायरी तारीख काढते.
     * SEBI 2026 सुधारित वेळापत्रकाप्रमाणे (Dynamic Pattern Matching).
     */
    fun getExpiry(symbol: String, type: InstrumentType): Date {
        val calendar = Calendar.getInstance(ist)

        var expiry = if (type == InstrumentType.FUTURE) {
            getMonthlyExpiry(calendar)
        } else {
            // 2026 Pattern: indices move towards specific weekly buckets
            val isWeeklyEligible = symbol.contains("NIFTY") || symbol.contains("SENSEX") || symbol.contains("BANKNIFTY")
            if (isWeeklyEligible) getWeeklyExpiry(symbol, calendar) else getMonthlyExpiry(calendar)
        }

        // Holiday Check & Adjustment
        val expiryCal = Calendar.getInstance(ist).apply { time = expiry }
        if (marketCalendar.isHoliday(expiryCal)) {
            val adjusted = marketCalendar.getPrecedingTradingDay(expiryCal)
            expiry = adjusted.time
        }

        return expiry
    }

    private fun getWeeklyExpiry(symbol: String, calendar: Calendar): Date {
        val expiryDay = when {
            symbol.contains("BANKNIFTY") -> Calendar.WEDNESDAY
            symbol.contains("NIFTY") -> Calendar.THURSDAY
            symbol.contains("SENSEX") -> Calendar.TUESDAY
            symbol.contains("FINNIFTY") -> Calendar.TUESDAY
            else -> Calendar.THURSDAY
        }

        val today = calendar.get(Calendar.DAY_OF_WEEK)
        var daysToExpiry = (expiryDay - today + 7) % 7

        if (daysToExpiry == 0 && isAfterMarketHours()) {
            daysToExpiry = 7
        }

        val result = calendar.clone() as Calendar
        result.add(Calendar.DAY_OF_YEAR, daysToExpiry)
        return result.time
    }

    private fun getMonthlyExpiry(calendar: Calendar): Date {
        val result = calendar.clone() as Calendar
        val targetDay = Calendar.THURSDAY

        result.set(Calendar.DAY_OF_MONTH, result.getActualMaximum(Calendar.DAY_OF_MONTH))
        while (result.get(Calendar.DAY_OF_WEEK) != targetDay) {
            result.add(Calendar.DAY_OF_MONTH, -1)
        }

        if (result.before(calendar) || (isSameDay(result, calendar) && isAfterMarketHours())) {
            result.add(Calendar.MONTH, 1)
            result.set(Calendar.DAY_OF_MONTH, result.getActualMaximum(Calendar.DAY_OF_MONTH))
            while (result.get(Calendar.DAY_OF_WEEK) != targetDay) {
                result.add(Calendar.DAY_OF_MONTH, -1)
            }
        }

        return result.time
    }

    fun isAfterMarketHours(): Boolean {
        val now = Calendar.getInstance(ist)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        return hour > 15 || (hour == 15 && minute > 30)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun isExpiryDay(symbol: String): Boolean {
        val calendar = Calendar.getInstance(ist)
        val expiry = getExpiry(symbol, InstrumentType.OPTION)
        return isSameDay(calendar, Calendar.getInstance(ist).apply { time = expiry })
    }

    fun getDaysToExpiry(symbol: String): Int {
        val expiry = getExpiry(symbol, InstrumentType.OPTION)
        val today = Calendar.getInstance(ist)
        val expiryCal = Calendar.getInstance(ist).apply { time = expiry }

        val diff = expiryCal.timeInMillis - today.timeInMillis
        return (diff / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }
}
