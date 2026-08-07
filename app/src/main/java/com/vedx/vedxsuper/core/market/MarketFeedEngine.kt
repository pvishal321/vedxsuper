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
        // Nifty, BankNifty, FinNifty, MidcapNifty (NSE)
        val nseIndices = listOf("26000", "26009", "26037", "26074")
        sendSubscribeRequest(nseIndices, 1)
        
        // Sensex, Bankex (BSE)
        val bseIndices = listOf("19000", "19003")
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
                put("mode", 1)
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
            while (buf.remaining() >= 27) { // Min header size: mode(1) + exch(1) + token(25)
                val mode = buf.get().toInt() and 0xFF
                val exchange = buf.get().toInt() and 0xFF
                val tokenBytes = ByteArray(25)
                buf.get(tokenBytes)
                val tokenStr = String(tokenBytes).trim { it <= ' ' || it.code == 0 }

                if (tokenStr.isEmpty()) break

                when (mode) {
                    1 -> { // LTP (31 bytes total)
                        if (buf.remaining() >= 4) {
                            val ltp = (buf.getInt().toLong() and 0xFFFFFFFFL) / 100.0
                            processTick(tokenStr, ltp, 0)
                        } else break
                    }
                    2 -> { // Quote (110 bytes total)
                        if (buf.remaining() >= 83) {
                            val ltp = (buf.getInt().toLong() and 0xFFFFFFFFL) / 100.0
                            buf.getInt() // ltq
                            buf.getInt() // atp
                            val volume = buf.getInt().toLong() and 0xFFFFFFFFL
                            buf.position(buf.position() + 67)
                            processTick(tokenStr, ltp, volume)
                        } else break
                    }
                    3 -> { // Snapquote (227 bytes total)
                        if (buf.remaining() >= 200) {
                            val ltp = (buf.getInt().toLong() and 0xFFFFFFFFL) / 100.0
                            buf.getInt() // ltq
                            buf.getInt() // atp
                            val volume = buf.getInt().toLong() and 0xFFFFFFFFL
                            buf.position(buf.position() + 184)
                            processTick(tokenStr, ltp, volume)
                        } else break
                    }
                    else -> break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    private fun processTick(token: String, ltp: Double, volume: Long) {
        if (ltp <= 0) return
        if (lastLtpMap[token] == ltp && volume == 0L) return
        lastLtpMap[token] = ltp

        val tick = TickData(token, ltp, volume, System.currentTimeMillis())
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
