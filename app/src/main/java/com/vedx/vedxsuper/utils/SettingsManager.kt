package com.vedx.vedxsuper.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings manager with virtual wallet add/withdraw support.
 */
class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "vedx_settings"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_SOUND_ALERTS = "sound_alerts"
        private const val KEY_ANALYSIS_TIMEFRAME = "analysis_timeframe"
        private const val KEY_AUTO_TRADE_CONFIRM = "auto_trade_confirm"
        private const val KEY_DEFAULT_QTY = "default_quantity"
        private const val KEY_RISK_PER_TRADE = "risk_per_trade_pct"
        private const val KEY_AUTO_START_BOOT = "auto_start_on_boot"
        private const val DEFAULT_TIMEFRAME = "15_MINUTE"
        private const val DEFAULT_QTY = 50
        private const val DEFAULT_RISK = 2 // 2%
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _soundAlerts = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ALERTS, true))
    val soundAlerts: StateFlow<Boolean> = _soundAlerts.asStateFlow()

    private val _analysisTimeframe = MutableStateFlow(
        try {
            prefs.getString(KEY_ANALYSIS_TIMEFRAME, DEFAULT_TIMEFRAME) ?: DEFAULT_TIMEFRAME
        } catch (e: Exception) {
            prefs.edit { remove(KEY_ANALYSIS_TIMEFRAME) }
            DEFAULT_TIMEFRAME
        }
    )
    val analysisTimeframe: StateFlow<String> = _analysisTimeframe.asStateFlow()

    private val _autoTradeConfirm = MutableStateFlow(prefs.getBoolean(KEY_AUTO_TRADE_CONFIRM, false))
    val autoTradeConfirm: StateFlow<Boolean> = _autoTradeConfirm.asStateFlow()

    private val _defaultQuantity = MutableStateFlow(prefs.getInt(KEY_DEFAULT_QTY, DEFAULT_QTY))
    val defaultQuantity: StateFlow<Int> = _defaultQuantity.asStateFlow()

    private val _riskPerTrade = MutableStateFlow(prefs.getInt(KEY_RISK_PER_TRADE, DEFAULT_RISK))
    val riskPerTrade: StateFlow<Int> = _riskPerTrade.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(prefs.getBoolean(KEY_AUTO_START_BOOT, false))
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    // ===== SETTINGS SETTERS =====

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        prefs.edit { putBoolean(KEY_DARK_MODE, enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
    }

    fun setSoundAlerts(enabled: Boolean) {
        _soundAlerts.value = enabled
        prefs.edit { putBoolean(KEY_SOUND_ALERTS, enabled) }
    }

    fun setAnalysisTimeframe(timeframe: String) {
        _analysisTimeframe.value = timeframe
        prefs.edit { putString(KEY_ANALYSIS_TIMEFRAME, timeframe) }
    }

    fun setAutoTradeConfirm(enabled: Boolean) {
        _autoTradeConfirm.value = enabled
        prefs.edit { putBoolean(KEY_AUTO_TRADE_CONFIRM, enabled) }
    }

    fun setDefaultQuantity(qty: Int) {
        _defaultQuantity.value = qty
        prefs.edit { putInt(KEY_DEFAULT_QTY, qty) }
    }

    fun setRiskPerTrade(pct: Int) {
        _riskPerTrade.value = pct
        prefs.edit { putInt(KEY_RISK_PER_TRADE, pct) }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        _autoStartOnBoot.value = enabled
        prefs.edit { putBoolean(KEY_AUTO_START_BOOT, enabled) }
    }

    // ===== WALLET SETTINGS =====
    // These are stored in VirtualTradeManager, but exposed here for UI convenience

    fun resetAllSettings() {
        prefs.edit { clear() }
        _darkMode.value = false
        _notificationsEnabled.value = true
        _soundAlerts.value = true
        _analysisTimeframe.value = DEFAULT_TIMEFRAME
        _autoTradeConfirm.value = false
        _defaultQuantity.value = DEFAULT_QTY
        _riskPerTrade.value = DEFAULT_RISK
    }
}
