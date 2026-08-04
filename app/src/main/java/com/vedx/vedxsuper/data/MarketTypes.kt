package com.vedx.vedxsuper.data

import androidx.compose.runtime.Immutable
import kotlin.math.abs

// ===== VALUE CLASSES (Zero overhead) =====
@JvmInline value class Symbol(val value: String)
@JvmInline value class Price(val cents: Int) { 
    val rupees: Double get() = cents / 100.0 
    companion object { fun from(r: Double) = Price((r * 100).toInt()) }
}
@JvmInline value class BandId(val id: Int)
@JvmInline value class Confidence(val pct: Int)

// ===== BIT-PACKED STATE (1 Long = full state) =====
@JvmInline value class MarketState(val bits: Long) {
    companion object {
        const val TREND_MASK = 0xFL
        const val REGIME_MASK = 0xF0L
        const val STRUCTURE_MASK = 0xF00L
        const val ZONE_MASK = 0xF000L
        
        fun pack(trend: Byte, regime: Byte, structure: Byte, zone: Byte) = MarketState(
            (trend.toLong() and 0xF) or
            ((regime.toLong() and 0xF) shl 4) or
            ((structure.toLong() and 0xF) shl 8) or
            ((zone.toLong() and 0xF) shl 12)
        )
    }
    val trend: Byte get() = (bits and TREND_MASK).toByte()
    val regime: Byte get() = ((bits and REGIME_MASK) shr 4).toByte()
    val structure: Byte get() = ((bits and STRUCTURE_MASK) shr 8).toByte()
    val zone: Byte get() = ((bits and ZONE_MASK) shr 12).toByte()
}

// ===== TREND STATES =====
object Trends {
    const val WAIT: Byte = 0
    const val BUILDING: Byte = 1
    const val REVERSAL_SETUP: Byte = 2
    const val REVERSAL_CONFIRMED: Byte = 3
    const val SCALP_READY: Byte = 4
    const val TREND_RUN: Byte = 5
    const val PULLBACK: Byte = 6
    const val EXHAUSTED: Byte = 7
    const val FINISHED: Byte = 8
}

// ===== REGIMES =====
object Regimes {
    const val SIDEWAYS: Byte = 0
    const val TRENDING_UP: Byte = 1
    const val TRENDING_DOWN: Byte = 2
    const val VOLATILE: Byte = 3
    const val NO_TRADE: Byte = 4
}

// ===== ACTIONS =====
object Actions {
    const val WAIT: Byte = 0
    const val BUY: Byte = 1
    const val SELL: Byte = 2
    const val SCALP: Byte = 3
    const val EXIT: Byte = 4
    const val TRAIL: Byte = 5
    const val PARTIAL: Byte = 6
    const val NO_TRADE: Byte = 7
}

// ===== RING BUFFER TICK (No allocation) =====
class TickRingBuffer(capacity: Int = 1024) {
    @PublishedApi internal val buf = LongArray(capacity)
    @PublishedApi internal var head = 0
    @PublishedApi internal var tail = 0
    @PublishedApi internal val mask = capacity - 1
    
    fun push(priceCents: Int, volume: Int, timestamp: Long, oi: Int) {
        buf[head] = (priceCents.toLong() and 0xFFFFFFFF) or 
                    ((volume.toLong() and 0xFFFF) shl 32) or
                    ((oi.toLong() and 0xFFFF) shl 48)
        head = (head + 1) and mask
        if (head == tail) tail = (tail + 1) and mask
    }
    
    inline fun forEachRecent(count: Int, action: (price: Int, vol: Int, oi: Int) -> Unit) {
        var i = (head - 1) and mask
        var n = count.coerceAtMost(size())
        while (n-- > 0) {
            val v = buf[i]
            action((v and 0xFFFFFFFF).toInt(), ((v shr 32) and 0xFFFF).toInt(), ((v shr 48) and 0xFFFF).toInt())
            i = (i - 1) and mask
        }
    }
    
    fun size() = (head - tail) and mask
    fun clear() { head = 0; tail = 0 }
}

// ===== CANDLE (Compact, Immutable) =====
@Immutable
data class Candle(
    val open: Price,
    val high: Price,
    val low: Price,
    val close: Price,
    val volume: Long,
    val timestamp: Long,
    val isComplete: Boolean = false
) {
    val range: Price get() = Price(abs(close.cents - open.cents))
    val body: Price get() = Price(abs(close.cents - open.cents))
    val isBullish: Boolean get() = close.cents > open.cents
}

// ===== SUPER TREND RESULT (Inline calc, no objects) =====
data class STResult(
    val value: Double,
    val upper: Double,
    val lower: Double,
    val trend: Byte, // 1 = up, -1 = down
    val atr: Double
)

// ===== MULTI-ST RESULT =====
data class MultiST(
    val st2: STResult, val st3: STResult, val st4: STResult,
    val st5: STResult, val st6: STResult, val st7: STResult, val st8: STResult,
    val master: STResult
) {
    val isCompressed: Boolean
        get() {
            val all = listOf(st2, st3, st4, st5, st6, st7, st8)
            val spread = all.maxOf { it.upper } - all.minOf { it.lower }
            return spread < master.value * 0.08
        }
    
    fun bandList() = doubleArrayOf(st2.value, st3.value, st4.value, st5.value, st6.value, st7.value, st8.value)
}

// ===== SIGNAL (AI Output) =====
@Immutable
data class Signal(
    val action: Byte,
    val symbol: Symbol,
    val entryPrice: Price,
    val target: Price,
    val stopLoss: Price,
    val confidence: Confidence,
    val reason: String,
    val timestamp: Long,
    val quantity: Int = 0,
    val regime: Byte = Regimes.SIDEWAYS,
    val zoneMatch: Int = 0
) {
    val isEntry: Boolean get() = action == Actions.BUY || action == Actions.SELL || action == Actions.SCALP
    val riskReward: Float get() = if (stopLoss.cents > 0) 
        abs(target.cents - entryPrice.cents).toFloat() / abs(entryPrice.cents - stopLoss.cents).toFloat() 
    else 0f
}

// ===== AI WEIGHTS (Mutable only during learning) =====
data class AIWeights(
    var trendW: Float = 0.25f,
    var strengthW: Float = 0.20f,
    var structureW: Float = 0.20f,
    var correlationW: Float = 0.15f,
    var liquidityW: Float = 0.10f,
    var regimeW: Float = 0.10f
)

// ===== TRADE JOURNAL (For learning) =====
data class TradeJournal(
    val symbol: Symbol,
    val entryPrice: Price,
    val exitPrice: Price,
    val pnl: Price,
    val timestamp: Long,
    val wasWin: Boolean,
    val stateAtEntry: MarketState
)
