package com.vedx.vedxsuper.utils

import android.content.Context
import android.content.SharedPreferences
import com.vedx.vedxsuper.model.trade.RiskLevel
import com.vedx.vedxsuper.model.trade.StrategyConfig
import com.vedx.vedxsuper.model.trade.TradingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("vedx_settings", Context.MODE_PRIVATE)

    private val _virtualBalance = MutableStateFlow(prefs.getFloat("virtual_balance", 10000000.0f).toDouble())
    val virtualBalance = _virtualBalance.asStateFlow()

    fun getAtrPeriod(): Int = prefs.getInt("atr_period", 10)
    fun setAtrPeriod(period: Int) = prefs.edit().putInt("atr_period", period).apply()

    fun getAtrMultiplier(): Float = prefs.getFloat("atr_multiplier", 3.0f)
    fun setAtrMultiplier(multiplier: Float) = prefs.edit().putFloat("atr_multiplier", multiplier).apply()
    
    fun isNotificationEnabled(): Boolean = prefs.getBoolean("notifications_enabled", true)
    fun setNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("notifications_enabled", enabled).apply()

    fun getStrategyConfig(): StrategyConfig {
        return StrategyConfig(
            mode = TradingMode.valueOf(prefs.getString("trading_mode", TradingMode.SCALPING.name)!!),
            riskLevel = RiskLevel.valueOf(prefs.getString("risk_level", RiskLevel.MODERATE.name)!!),
            maxRiskPerTrade = prefs.getFloat("max_risk_trade", 1.0f).toDouble(),
            maxDailyLoss = prefs.getFloat("max_daily_loss", 3.0f).toDouble(),
            dailyProfitTarget = prefs.getFloat("daily_profit_target", 5.0f).toDouble(),
            targetValue = prefs.getFloat("target_value", 20.0f).toDouble(),
            stopLossValue = prefs.getFloat("sl_value", 10.0f).toDouble(),
            isAutoTrailing = prefs.getBoolean("auto_trailing", true),
            strikeType = prefs.getString("strike_type", "ATM")!!
        )
    }

    fun setMaxRiskPerTrade(value: Double) = prefs.edit().putFloat("max_risk_trade", value.toFloat()).apply()
    fun setMaxDailyLoss(value: Double) = prefs.edit().putFloat("max_daily_loss", value.toFloat()).apply()
    fun setDailyProfitTarget(value: Double) = prefs.edit().putFloat("daily_profit_target", value.toFloat()).apply()

    fun getActiveIndices(): Set<String> {
        return prefs.getStringSet("active_indices", setOf("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY")) ?: emptySet()
    }

    fun toggleIndex(symbol: String) {
        val current = getActiveIndices().toMutableSet()
        if (current.contains(symbol)) {
            if (current.size > 1) current.remove(symbol) // Keep at least one active
        } else {
            current.add(symbol)
        }
        prefs.edit().putStringSet("active_indices", current).apply()
    }

    fun getVirtualBalance(): Double = _virtualBalance.value
    
    fun setVirtualBalance(balance: Double) {
        prefs.edit().putFloat("virtual_balance", balance.toFloat()).apply()
        _virtualBalance.value = balance
    }

    fun addVirtualBalance(amount: Double) {
        setVirtualBalance(getVirtualBalance() + amount)
    }

    fun withdrawVirtualBalance(amount: Double) {
        val current = getVirtualBalance()
        if (current >= amount) {
            setVirtualBalance(current - amount)
        }
    }

    fun isAutoTradeEnabled(): Boolean = prefs.getBoolean("auto_trade_enabled", false)
    fun setAutoTradeEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_trade_enabled", enabled).apply()

    fun isVoiceAlertEnabled(): Boolean = prefs.getBoolean("voice_alert_enabled", true)
    fun setVoiceAlertEnabled(enabled: Boolean) = prefs.edit().putBoolean("voice_alert_enabled", enabled).apply()

    // New Settings for Institutional Dashboard
    fun isOnlyHighProbabilityEnabled(): Boolean = prefs.getBoolean("only_high_prob", true)
    fun setOnlyHighProbabilityEnabled(enabled: Boolean) = prefs.edit().putBoolean("only_high_prob", enabled).apply()

    fun getConfidenceLimit(): Int = prefs.getInt("confidence_limit", 85)
    fun setConfidenceLimit(limit: Int) = prefs.edit().putInt("confidence_limit", limit).apply()

    fun getNotificationBeforeEntry(): Int = prefs.getInt("notify_before_entry", 1) // In minutes
    fun setNotificationBeforeEntry(minutes: Int) = prefs.edit().putInt("notify_before_entry", minutes).apply()

    fun isNotificationSoundEnabled(): Boolean = prefs.getBoolean("notify_sound", true)
    fun setNotificationSoundEnabled(enabled: Boolean) = prefs.edit().putBoolean("notifications_enabled", enabled).apply()

    fun getVoiceLanguage(): String = prefs.getString("voice_lang", "English") ?: "English"
    fun setVoiceLanguage(lang: String) = prefs.edit().putString("voice_lang", lang).apply()

    fun isLockScreenAlertsEnabled(): Boolean = prefs.getBoolean("lock_screen_alerts", false)
    fun setLockScreenAlertsEnabled(enabled: Boolean) = prefs.edit().putBoolean("lock_screen_alerts", enabled).apply()

    fun isFloatingNotificationEnabled(): Boolean = prefs.getBoolean("floating_notify", true)
    fun setFloatingNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("floating_notify", enabled).apply()

    fun getVibrationPattern(): String = prefs.getString("vibration_pattern", "Default") ?: "Default"
    fun setVibrationPattern(pattern: String) = prefs.edit().putString("vibration_pattern", pattern).apply()

    fun isBackgroundModeEnabled(): Boolean = prefs.getBoolean("background_mode", true)
    fun setBackgroundModeEnabled(enabled: Boolean) = prefs.edit().putBoolean("background_mode", enabled).apply()

    fun isAiExplainSignalsEnabled(): Boolean = prefs.getBoolean("ai_explain_signals", true)
    fun setAiExplainSignalsEnabled(enabled: Boolean) = prefs.edit().putBoolean("ai_explain_signals", enabled).apply()

    fun isAutoSaveEnabled(): Boolean = prefs.getBoolean("auto_save_data", true)
    fun setAutoSaveEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_save_data", enabled).apply()

    fun getAnalysisTimeframe(): Int = prefs.getInt("analysis_timeframe", 15)
    fun setAnalysisTimeframe(minutes: Int) = prefs.edit().putInt("analysis_timeframe", minutes).apply()

    // --- [NEW] Dynamic Lot Sizes Configuration ---
    fun getLotSize(symbol: String): Int {
        val baseSymbol = when {
            symbol.contains("BANKNIFTY") -> "BANKNIFTY"
            symbol.contains("SENSEX") -> "SENSEX"
            symbol.contains("FINNIFTY") -> "FINNIFTY"
            symbol.contains("NIFTY") -> "NIFTY"
            else -> "NIFTY"
        }
        val defaultSize = when (baseSymbol) {
            "BANKNIFTY" -> 30
            "SENSEX" -> 20
            "FINNIFTY" -> 60
            "NIFTY" -> 65
            else -> 65
        }
        return prefs.getInt("lot_size_$baseSymbol", defaultSize)
    }

    fun setLotSize(baseSymbol: String, size: Int) {
        prefs.edit().putInt("lot_size_$baseSymbol", size).apply()
    }
}
