package com.vedx.vedxsuper.strategy.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.websocket.ConnectionStatus
import com.vedx.vedxsuper.websocket.SmartStreamManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SystemHealth(
    val isBrokerLogin: Boolean = false,
    val isSessionValid: Boolean = false,
    val isSmartStreamLive: Boolean = false,
    val isOptionChainSynced: Boolean = false,
    val isAiEngineRunning: Boolean = false,
    val isMarketOpen: Boolean = false,
    val isLearningActive: Boolean = false,
    val isRiskEngineActive: Boolean = true,
    val isDecisionEngineReady: Boolean = false,
    val internetQuality: String = "Unknown",
    val streamStatusName: String = "Disconnected",
    val cpuUsage: Int = 0,
    val memoryUsage: Int = 0
)

class SystemHealthEngine(
    private val context: Context,
    private val tokenManager: SecureTokenManager,
    private val smartStreamManager: SmartStreamManager,
    private val repository: com.vedx.vedxsuper.repository.MarketRepository,
    private val scope: CoroutineScope
) {
    private val _health = MutableStateFlow(SystemHealth())
    val health: StateFlow<SystemHealth> = _health.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                updateHealth()
                delay(2000) // Update every 2 seconds
            }
        }
    }

    private fun updateHealth() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val internetStatus = when {
            capabilities == null -> "Offline"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi - High"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular - Good"
            else -> "Other"
        }

        val sessionValid = tokenManager.hasValidSession()
        val connectionState = smartStreamManager.connectionState.value
        val streamStatus = connectionState == ConnectionStatus.LIVE
        val learningStats = repository.getLearningStats()
        val marketOpen = repository.isMarketOpen()
        
        // Actual Check for Option Flow
        val isOptionDataFlowing = repository.indexData.value.any { it.key.contains("CE") || it.key.contains("PE") }

        _health.value = SystemHealth(
            isBrokerLogin = tokenManager.getJwtToken() != null,
            isSessionValid = sessionValid,
            isSmartStreamLive = streamStatus,
            isOptionChainSynced = isOptionDataFlowing, 
            isAiEngineRunning = streamStatus, // Changed: Running if stream is live
            isMarketOpen = marketOpen,
            isLearningActive = learningStats.isLearningMode,
            isRiskEngineActive = true,
            isDecisionEngineReady = sessionValid && streamStatus && isOptionDataFlowing,
            internetQuality = internetStatus,
            streamStatusName = connectionState.name
        )
    }
}
