package com.vedx.vedxsuper.websocket

import android.util.Log
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.market.MarketTickParser
import com.vedx.vedxsuper.repository.MarketRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.pow

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    SUBSCRIBING,
    LIVE,
    RECONNECTING,
    RECOVERING,
    SYNCING,
    DEGRADED,
    ERROR
}

data class StreamMetrics(
    val latencyMs: Long = 0,
    val tickRate: Int = 0,
    val lastTickTime: Long = 0,
    val connectionQuality: String = "Unknown"
)

class SmartStreamManager(
    private val tokenManager: SecureTokenManager,
    private val marketRepository: MarketRepository,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private val wsUrl = "wss://smartapisocket.angelone.in/smart-stream"
    
    private var isManualDisconnect = false
    private var reconnectAttempt = 0

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _metrics = MutableStateFlow(StreamMetrics())
    val metrics: StateFlow<StreamMetrics> = _metrics.asStateFlow()

    private var tickCount = 0
    private var lastMetricsUpdate = System.currentTimeMillis()
    private var lastTickTimestamp = 0L

    fun connect() {
        Log.i("SmartStream", "connect() called. Current state: ${_connectionState.value}")
        if (_connectionState.value == ConnectionStatus.LIVE || _connectionState.value == ConnectionStatus.CONNECTING) {
            Log.i("SmartStream", "Already connecting or live. Skipping.")
            return
        }

        isManualDisconnect = false
        _connectionState.value = ConnectionStatus.CONNECTING
        
        val jwtToken = tokenManager.getJwtToken()?.removePrefix("Bearer ") ?: run {
            Log.e("SmartStream", "JWT Token is null")
            _connectionState.value = ConnectionStatus.ERROR
            return
        }
        val clientCode = tokenManager.getCredentials()["client_id"] ?: run {
            Log.e("SmartStream", "Client Code is null")
            _connectionState.value = ConnectionStatus.ERROR
            return
        }
        val feedToken = tokenManager.getFeedToken() ?: run {
            Log.e("SmartStream", "Feed Token is null")
            _connectionState.value = ConnectionStatus.ERROR
            return
        }
        val apiKey = tokenManager.getCredentials()["api_key"] ?: run {
            Log.e("SmartStream", "API Key is null")
            _connectionState.value = ConnectionStatus.ERROR
            return
        }

        Log.i("SmartStream", "Requesting WebSocket connection for $clientCode")
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $jwtToken")
            .addHeader("x-client-code", clientCode)
            .addHeader("x-feed-token", feedToken)
            .addHeader("x-api-key", apiKey)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("SmartStream", "Connected to Angel One SmartStream")
                reconnectAttempt = 0
                _connectionState.value = ConnectionStatus.AUTHENTICATING
                scope.launch {
                    delay(500) // Authenticating...
                    _connectionState.value = ConnectionStatus.SUBSCRIBING
                    subscribe()
                    
                    // [FIXED] Point 2: Dynamically subscribe to Neural Matrix tokens (Numeric)
                    launch {
                        marketRepository.getNeuralMatrixTokens().collect { tokens ->
                            if (tokens.isNotEmpty()) {
                                updateSubscriptions(tokens)
                                Log.i("SmartStream", "Auto-Subscribed to ${tokens.size} Neural Matrix Tokens")
                            }
                        }
                    }

                    _connectionState.value = ConnectionStatus.LIVE
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val now = System.currentTimeMillis()
                tickCount++
                
                if (lastTickTimestamp > 0) {
                    val latency = now - lastTickTimestamp
                    updateMetrics(latency)
                }
                lastTickTimestamp = now

                MarketTickParser.parseBinary(bytes)?.let { tick ->
                    marketRepository.handleTick(tick)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SmartStream", "Connection Failure: ${t.message}")
                _connectionState.value = ConnectionStatus.ERROR
                handleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("SmartStream", "Connection Closed: $reason")
                if (!isManualDisconnect) {
                    _connectionState.value = ConnectionStatus.RECONNECTING
                    handleReconnect()
                } else {
                    _connectionState.value = ConnectionStatus.DISCONNECTED
                }
            }
        })
    }

    private fun updateMetrics(latency: Long) {
        val now = System.currentTimeMillis()
        if (now - lastMetricsUpdate >= 1000) {
            val rate = tickCount
            tickCount = 0
            lastMetricsUpdate = now
            
            val quality = when {
                latency < 200 && rate > 5 -> "Excellent"
                latency < 500 && rate > 2 -> "Good"
                latency < 2000 -> "Fair"
                else -> {
                    _connectionState.value = ConnectionStatus.DEGRADED
                    "Poor"
                }
            }
            
            if (quality != "Poor" && _connectionState.value == ConnectionStatus.DEGRADED) {
                _connectionState.value = ConnectionStatus.LIVE
            }
            
            _metrics.value = StreamMetrics(
                latencyMs = latency,
                tickRate = rate,
                lastTickTime = now,
                connectionQuality = quality
            )
        }
    }

    fun updateSubscriptions(tokens: List<String>, exchange: Int = 1) {
        val json = """
            {
                "action": 1,
                "params": {
                    "mode": 3,
                    "tokenList": [
                        {
                            "exchangeType": $exchange,
                            "tokens": ${tokens.map { "\"$it\"" }}
                        }
                    ]
                }
            }
        """.trimIndent()
        webSocket?.send(json)
    }

    private fun subscribe() {
        val json = """
            {
                "action": 1,
                "params": {
                    "mode": 3,
                    "tokenList": [
                        {
                            "exchangeType": 1,
                            "tokens": ["26000", "26009", "26037", "26017"]
                        },
                        {
                            "exchangeType": 3,
                            "tokens": ["1"]
                        }
                    ]
                }
            }
        """.trimIndent()
        webSocket?.send(json)
    }

    private fun handleReconnect() {
        if (isManualDisconnect) return
        
        reconnectAttempt++
        val delayTime = (2.0.pow(reconnectAttempt.toDouble()) * 1000).toLong().coerceAtMost(30000)
        
        Log.i("SmartStream", "Attempting reconnect in ${delayTime/1000}s (Attempt $reconnectAttempt)")
        
        scope.launch {
            delay(delayTime)
            if (!isManualDisconnect && tokenManager.hasValidSession()) {
                connect()
            }
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        _connectionState.value = ConnectionStatus.DISCONNECTED
        webSocket?.close(1000, "User logout")
        webSocket = null
    }
}
