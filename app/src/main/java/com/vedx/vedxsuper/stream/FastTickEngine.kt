package com.vedx.vedxsuper.stream

import com.vedx.vedxsuper.core.UltraNeuralCore
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class FastTickEngine(
    private val token: String,
    private val clientCode: String,
    private val core: UltraNeuralCore,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private val url = "wss://smartapisocket.angelone.in/smart-stream"
    
    fun connect() {
        val req = Request.Builder().url(url)
            .header("Authorization", token)
            .header("x-client-code", clientCode)
            .build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, resp: Response) { subscribe() }
            override fun onMessage(ws: WebSocket, b: ByteString) { parse(b.toByteArray()) }
            override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) {
                scope.launch { delay(5000); connect() }
            }
        })
    }
    
    private fun subscribe() {
        val j = JSONObject().apply {
            put("action", 1)
            put("params", JSONObject().apply {
                put("mode", 1)
                put("tokenList", listOf(
                    JSONObject().apply {
                        put("exchangeType", 1)
                        put("tokens", listOf("26000", "26009", "26037"))
                    }
                ))
            })
        }
        ws?.send(j.toString())
    }
    
    private fun parse(data: ByteArray) {
        if (data.size < 20) return
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val mode = buf.get().toInt()
        // val exchange = buf.get().toInt() // Unused but present in byte stream
        buf.get() // Skip exchange
        val tokenLen = buf.get().toInt()
        if (data.size < 3 + tokenLen + 4) return
        val tokenBytes = ByteArray(tokenLen).also { buf.get(it) }
        val tokenStr = String(tokenBytes)
        
        val ltp = when(mode) {
            1 -> buf.getInt() / 100.0
            2 -> { buf.getInt(); buf.getInt(); buf.getInt(); buf.getInt() / 100.0 }
            else -> return
        }
        
        val sym = when(tokenStr) {
            "26000" -> "NIFTY"
            "26009" -> "BANKNIFTY"
            "26037" -> "FINNIFTY"
            else -> tokenStr
        }
        core.onIndexTick(sym, ltp, 0, System.currentTimeMillis())
    }
    
    fun disconnect() {
        ws?.close(1000, null)
        client.dispatcher.executorService.shutdown()
    }
}
