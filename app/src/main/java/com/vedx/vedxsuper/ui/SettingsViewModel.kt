package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val notificationEnabled: Boolean = true,
    val autoTradeEnabled: Boolean = false,
    val voiceAlertEnabled: Boolean = true,
    val virtualBalance: Double = 0.0,
    val onlyHighProbability: Boolean = true,
    val confidenceLimit: Int = 85,
    val notifyBeforeEntry: Int = 1,
    val notifySound: Boolean = true,
    val voiceLang: String = "English",
    val lockScreenAlerts: Boolean = false,
    val floatingNotify: Boolean = true,
    val vibrationPattern: String = "Default",
    val backgroundMode: Boolean = true,
    val aiExplainSignals: Boolean = true,
    val autoSaveData: Boolean = true,
    val syncStatus: String = "Idle",
    val analysisTimeframe: Int = 15,
    val tradingMode: com.vedx.vedxsuper.model.trade.TradingMode = com.vedx.vedxsuper.model.trade.TradingMode.SCALPING,
    val riskLevel: com.vedx.vedxsuper.model.trade.RiskLevel = com.vedx.vedxsuper.model.trade.RiskLevel.MODERATE,
    val maxRiskPerTrade: Double = 1.0,
    val maxDailyLoss: Double = 3.0,
    val dailyProfitTarget: Double = 5.0
)

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val marketRepository: com.vedx.vedxsuper.repository.MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeBalance()
        observeSyncStatus()
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            marketRepository.syncStatus.collect { status ->
                _uiState.value = _uiState.value.copy(syncStatus = status)
            }
        }
    }

    private fun loadSettings() {
        val config = settingsManager.getStrategyConfig()
        _uiState.value = SettingsUiState(
            notificationEnabled = settingsManager.isNotificationEnabled(),
            autoTradeEnabled = settingsManager.isAutoTradeEnabled(),
            voiceAlertEnabled = settingsManager.isVoiceAlertEnabled(),
            virtualBalance = settingsManager.getVirtualBalance(),
            onlyHighProbability = settingsManager.isOnlyHighProbabilityEnabled(),
            confidenceLimit = settingsManager.getConfidenceLimit(),
            notifyBeforeEntry = settingsManager.getNotificationBeforeEntry(),
            notifySound = settingsManager.isNotificationSoundEnabled(),
            voiceLang = settingsManager.getVoiceLanguage(),
            lockScreenAlerts = settingsManager.isLockScreenAlertsEnabled(),
            floatingNotify = settingsManager.isFloatingNotificationEnabled(),
            vibrationPattern = settingsManager.getVibrationPattern(),
            backgroundMode = settingsManager.isBackgroundModeEnabled(),
            aiExplainSignals = settingsManager.isAiExplainSignalsEnabled(),
            autoSaveData = settingsManager.isAutoSaveEnabled(),
            analysisTimeframe = settingsManager.getAnalysisTimeframe(),
            tradingMode = config.mode,
            riskLevel = config.riskLevel,
            maxRiskPerTrade = config.maxRiskPerTrade,
            maxDailyLoss = config.maxDailyLoss,
            dailyProfitTarget = config.dailyProfitTarget
        )
    }

    private fun observeBalance() {
        viewModelScope.launch {
            settingsManager.virtualBalance.collect { balance ->
                _uiState.value = _uiState.value.copy(virtualBalance = balance)
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        settingsManager.setNotificationEnabled(enabled)
        _uiState.value = _uiState.value.copy(notificationEnabled = enabled)
        marketRepository.syncRiskConfig()
    }

    fun setAutoTradeEnabled(enabled: Boolean) {
        settingsManager.setAutoTradeEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoTradeEnabled = enabled)
        marketRepository.syncRiskConfig()
    }

    fun setVoiceAlertEnabled(enabled: Boolean) {
        settingsManager.setVoiceAlertEnabled(enabled)
        _uiState.value = _uiState.value.copy(voiceAlertEnabled = enabled)
        marketRepository.syncRiskConfig()
    }

    fun addFunds(amount: Double) {
        settingsManager.addVirtualBalance(amount)
    }

    fun withdrawFunds(amount: Double) {
        settingsManager.withdrawVirtualBalance(amount)
    }
    
    fun setOnlyHighProbability(enabled: Boolean) {
        settingsManager.setOnlyHighProbabilityEnabled(enabled)
        _uiState.value = _uiState.value.copy(onlyHighProbability = enabled)
        marketRepository.syncRiskConfig()
    }
    
    fun setConfidenceLimit(limit: Int) {
        settingsManager.setConfidenceLimit(limit)
        _uiState.value = _uiState.value.copy(confidenceLimit = limit)
        marketRepository.syncRiskConfig()
    }
    
    fun setNotifyBeforeEntry(minutes: Int) {
        settingsManager.setNotificationBeforeEntry(minutes)
        _uiState.value = _uiState.value.copy(notifyBeforeEntry = minutes)
    }
    
    fun setNotifySound(enabled: Boolean) {
        settingsManager.setNotificationSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(notifySound = enabled)
    }
    
    fun setVoiceLang(lang: String) {
        settingsManager.setVoiceLanguage(lang)
        _uiState.value = _uiState.value.copy(voiceLang = lang)
    }
    
    fun setLockScreenAlerts(enabled: Boolean) {
        settingsManager.setLockScreenAlertsEnabled(enabled)
        _uiState.value = _uiState.value.copy(lockScreenAlerts = enabled)
    }
    
    fun setFloatingNotify(enabled: Boolean) {
        settingsManager.setFloatingNotificationEnabled(enabled)
        _uiState.value = _uiState.value.copy(floatingNotify = enabled)
    }
    
    fun setVibrationPattern(pattern: String) {
        settingsManager.setVibrationPattern(pattern)
        _uiState.value = _uiState.value.copy(vibrationPattern = pattern)
    }
    
    fun setBackgroundMode(enabled: Boolean) {
        settingsManager.setBackgroundModeEnabled(enabled)
        _uiState.value = _uiState.value.copy(backgroundMode = enabled)
    }
    
    fun setAiExplainSignals(enabled: Boolean) {
        settingsManager.setAiExplainSignalsEnabled(enabled)
        _uiState.value = _uiState.value.copy(aiExplainSignals = enabled)
        marketRepository.syncRiskConfig()
    }

    fun setAutoSaveData(enabled: Boolean) {
        settingsManager.setAutoSaveEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoSaveData = enabled)
    }

    fun setAnalysisTimeframe(minutes: Int) {
        settingsManager.setAnalysisTimeframe(minutes)
        _uiState.value = _uiState.value.copy(analysisTimeframe = minutes)
    }

    fun setTradingMode(mode: com.vedx.vedxsuper.model.trade.TradingMode) {
        settingsManager.prefs.edit().putString("trading_mode", mode.name).apply()
        _uiState.value = _uiState.value.copy(tradingMode = mode)
        marketRepository.syncRiskConfig()
    }

    fun setRiskLevel(level: com.vedx.vedxsuper.model.trade.RiskLevel) {
        settingsManager.prefs.edit().putString("risk_level", level.name).apply()
        _uiState.value = _uiState.value.copy(riskLevel = level)
        marketRepository.syncRiskConfig()
    }

    fun setMaxRiskPerTrade(value: Double) {
        settingsManager.setMaxRiskPerTrade(value)
        _uiState.value = _uiState.value.copy(maxRiskPerTrade = value)
        marketRepository.syncRiskConfig()
    }

    fun setMaxDailyLoss(value: Double) {
        settingsManager.setMaxDailyLoss(value)
        _uiState.value = _uiState.value.copy(maxDailyLoss = value)
        marketRepository.syncRiskConfig()
    }

    fun setDailyProfitTarget(value: Double) {
        settingsManager.setDailyProfitTarget(value)
        _uiState.value = _uiState.value.copy(dailyProfitTarget = value)
        marketRepository.syncRiskConfig()
    }
}
