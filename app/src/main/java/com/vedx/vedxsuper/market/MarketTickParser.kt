package com.vedx.vedxsuper.market

import com.vedx.vedxsuper.model.market.TickData
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MarketTickParser {
    
    fun parseBinary(bytes: ByteString): TickData? {
        return try {
            val data = bytes.toByteArray()
            if (data.size < 11) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val subscriptionMode = buffer.get(0).toInt()
            
            val tokenBuffer = ByteArray(25)
            buffer.position(2)
            buffer.get(tokenBuffer, 0, 25)
            val token = String(tokenBuffer).trim().replace("\u0000", "")
            
            val symbol = when (token) {
                "26000" -> "NIFTY"
                "26009" -> "BANKNIFTY"
                "26037" -> "FINNIFTY"
                "26017" -> "INDIA VIX"
                "1" -> "SENSEX"
                "99926000" -> "NIFTY"
                "99926009" -> "BANKNIFTY"
                else -> token
            }

            when (subscriptionMode) {
                3 -> { // Snap Quote Mode
                    // Angel One Index vs Option binary positions
                    val isIndex = token == "26000" || token == "26009" || token == "26037" || token == "1"
                    
                    val ltpPos = if (isIndex) 43 else 43 // LTP is usually at 43 for both in Mode 3
                    buffer.position(ltpPos)
                    val ltp = buffer.int / 100.0
                    
                    // For indices, change data positions might differ
                    buffer.position(51)
                    val change = buffer.int / 100.0
                    val changePercent = buffer.int / 100.0
                    
                    buffer.position(79)
                    val volume = buffer.long
                    
                    buffer.position(91)
                    val oi = buffer.long
                    
                    buffer.position(103)
                    val high = buffer.int / 100.0
                    val low = buffer.int / 100.0
                    val prevClose = buffer.int / 100.0
                    
                    TickData(
                        symbol = symbol,
                        token = token,
                        ltp = ltp,
                        change = change,
                        changePercent = changePercent,
                        high = high,
                        low = low,
                        prevClose = prevClose,
                        openInterest = oi,
                        volume = volume,
                        timestamp = System.currentTimeMillis()
                    )
                }
                1 -> { // LTP Mode
                    buffer.position(43)
                    val ltp = buffer.int / 100.0
                    TickData(symbol, token, ltp)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
