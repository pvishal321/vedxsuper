package com.vedx.vedxsuper.core.market

import android.util.Log
import com.vedx.vedxsuper.core.event.EventBus
import com.vedx.vedxsuper.core.event.SystemEvent
import com.vedx.vedxsuper.data.OptionDataManager
import com.vedx.vedxsuper.data.TickData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * V5 MarketFeedEngine (Event-Driven)
 */
class MarketFeedEngine(
    private var token: String,
    private var clientCode: String,
    private var feedToken: String,
    private var apiKey: String,
    private val optionDataManager: OptionDataManager,
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    private val TAG = "MarketFeedEngine"
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
        
    private var ws: WebSocket? = null
    private val url = "wss://smartapisocket.angelone.in/smart-stream"

    private val subscribedTokens = mutableSetOf<String>()
    private var isConnected = false
    private var reconnectAttempt = 0
    private var connectJob: Job? = null

    private val lastLtpMap = java.util.concurrent.ConcurrentHashMap<String, Double>()

    private val _ticks = MutableSharedFlow<TickData>(
        extraBufferCapacity = 1000,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val ticks = _ticks.asSharedFlow()

    fun updateAuth(newToken: String, newClientCode: String, newFeedToken: String, newApiKey: String) {
        this.token = newToken
        this.clientCode = newClientCode
        this.feedToken = newFeedToken
        this.apiKey = newApiKey
    }

    fun connect() {
        if (isConnected || token.isBlank() || clientCode.isBlank() || feedToken.isBlank()) {
            Log.w(TAG, "Cannot connect: Already connected or missing auth tokens")
            return
        }
        
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                val req = Request.Builder().url(url)
                    .header("Authorization", "Bearer $token")
                    .header("x-api-key", apiKey)
                    .header("x-client-code", clientCode)
                    .header("x-feed-token", feedToken)
                    .build()

                ws = client.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "WebSocket Connected Successfully")
                        isConnected = true
                        reconnectAttempt = 0
                        resubscribe()
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        parse(bytes.toByteArray())
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket Failure: ${t.message}")
                        handleDisconnect()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.w(TAG, "WebSocket Closed: $reason")
                        handleDisconnect()
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Connect error", e)
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        isConnected = false
        ws = null
        val delayMs = calculateBackoffDelay(reconnectAttempt++)
        connectJob?.cancel()
        connectJob = scope.launch {
            delay(delayMs)
            connect()
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val baseDelay = 1000L * 2.0.pow(min(attempt, 6).toDouble()).toLong()
        val jitter = Random.nextLong(0, 1000)
        return min(baseDelay + jitter, 300_000L)
    }

    private fun resubscribe() {
        subscribeIndices()
        val tokens = subscribedTokens.toList()
        subscribedTokens.clear()
        subscribeOptionTokens(tokens)
    }

    private fun subscribeIndices() {
        // Nifty, BankNifty (NSE) - include both short and 999-prefixed tokens
        val nseIndices = listOf("26000", "99926000", "26009", "99926009")
        sendSubscribeRequest(nseIndices, 1)
        
        // Sensex (BSE) - include both short and 999-prefixed tokens
        val bseIndices = listOf("19000", "99919000")
        sendSubscribeRequest(bseIndices, 3) // exchangeType 3 for BSE_CM
    }

    fun subscribeOptionTokens(tokens: List<String>) {
        val newTokens = tokens.filter { it !in subscribedTokens }
        if (newTokens.isEmpty()) return

        newTokens.chunked(100).forEach { batch ->
            val nfoTokens = mutableListOf<String>()
            val bfoTokens = mutableListOf<String>()
            batch.forEach { t ->
                val inst = optionDataManager.getInstrumentByToken(t)
                if (inst?.exchange == "BFO") bfoTokens.add(t) else nfoTokens.add(t)
            }
            if (nfoTokens.isNotEmpty()) sendSubscribeRequest(nfoTokens, 2) // NSE_FO
            if (bfoTokens.isNotEmpty()) sendSubscribeRequest(bfoTokens, 4) // BSE_FO
            subscribedTokens.addAll(batch)
        }
    }

    private fun sendSubscribeRequest(tokens: List<String>, exchangeType: Int) {
        if (ws == null || !isConnected) return
        val j = JSONObject().apply {
            put("action", 1)
            put("params", JSONObject().apply {
                // Mode 2 (Quote) for Indices, Mode 3 (Snap Quote) for Options
                val mode = if (exchangeType == 1 || exchangeType == 3) 2 else 3
                put("mode", mode)
                put("tokenList", JSONArray().apply {
                    put(JSONObject().apply {
                        put("exchangeType", exchangeType)
                        put("tokens", JSONArray(tokens))
                    })
                })
            })
        }
        ws?.send(j.toString())
    }

    private fun parse(data: ByteArray) {
        try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            while (buf.remaining() >= 27) {
                val packetStart = buf.position()
                val mode = buf.get().toInt() and 0xFF
                val exchange = buf.get().toInt() and 0xFF
                val tokenBytes = ByteArray(25)
                buf.get(tokenBytes)
                val tokenStr = String(tokenBytes).trim { it <= ' ' || it.code == 0 }

                if (tokenStr.isEmpty()) {
                    Log.w(TAG, "Malformed packet: empty token, skipping one byte")
                    if (buf.remaining() > 0) buf.position(buf.position() + 1)
                    continue
                }

                val packetSize = when (mode) {
                    1 -> 51
                    2 -> 123
                    3 -> 379
                    else -> -1
                }

                if (packetSize < 0) {
                    Log.w(TAG, "Unknown packet mode $mode for token $tokenStr, skipping one byte")
                    if (buf.remaining() > 0) buf.position(buf.position() + 1)
                    continue
                }

                if (buf.remaining() < packetSize - 27) {
                    Log.w(TAG, "Incomplete packet mode $mode for token $tokenStr, expected $packetSize bytes, remaining ${buf.remaining()}")
                    break
                }

                // Angel One SmartAPI LTP is located at byte offset 43 in the fixed binary packet.
                val ltp = (buf.getInt(packetStart + 43).toLong() and 0xFFFFFFFFL) / 100.0
                buf.position(packetStart + packetSize)
                processTick(tokenStr, ltp, 0L, hasChange = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
        }
    }

    private fun processTick(token: String, ltp: Double, volume: Long, oi: Long = 0L, change: Double = 0.0, changePct: Double = 0.0, hasChange: Boolean = true) {
        if (ltp <= 0) return
        if (lastLtpMap[token] == ltp && volume == 0L && oi == 0L) return
        lastLtpMap[token] = ltp

        val tick = TickData(token, ltp, volume, System.currentTimeMillis(), oi = oi, change = change, changePct = changePct, hasChange = hasChange)
        _ticks.tryEmit(tick)
        eventBus.tryPublish(SystemEvent.TickReceived(tick))
    }

    fun disconnect() {
        ws?.close(1000, "Normal closure")
        ws = null
        isConnected = false
        connectJob?.cancel()
    }
}
