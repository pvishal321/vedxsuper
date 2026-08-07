package com.vedx.vedxsuper.data

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import kotlin.math.abs

@JvmInline
value class Price(val cents: Int) {
    val rupees: Double get() = cents / 100.0
    companion object {
        fun from(rupees: Double) = Price((rupees * 100).toInt())
        fun from(rupees: BigDecimal) = Price((rupees * BigDecimal(100)).toInt())
    }
}

@JvmInline
value class Symbol(val value: String)

@JvmInline
value class Confidence(val pct: Int) {
    val score: Int get() = pct
    val isStrong: Boolean get() = pct >= 75
    val isValid: Boolean get() = pct >= 60
}

@Immutable
data class Candle(
    val open: Price,
    val high: Price,
    val low: Price,
    val close: Price,
    val volume: Long,
    val timestamp: Long,
    val isComplete: Boolean = true
) {
    val isBullish: Boolean get() = close.cents >= open.cents
    val bodySize: Double get() = abs(close.rupees - open.rupees)
    val range: Double get() = high.rupees - low.rupees
    val upperShadow: Double get() = high.rupees - kotlin.math.max(open.rupees, close.rupees)
    val lowerShadow: Double get() = kotlin.math.min(open.rupees, close.rupees) - low.rupees
    val body: Price get() = Price(abs(close.cents - open.cents))
}

data class STResult(
    val triggerPrice: Double,
    val upperBand: Double,
    val lowerBand: Double,
    val trend: Byte,
    val atr: Double
)

data class MultiST(
    val st2: STResult,
    val st3: STResult,
    val st4: STResult,
    val st5: STResult,
    val st6: STResult,
    val st7: STResult,
    val st8: STResult,
    val master: STResult,
    val bullCount: Int = 0,
    val bearCount: Int = 0,
    val alignmentPct: Double = 0.0,
    val strongTrend: Boolean = false,
    val confidenceScore: Double = 0.0,
    val adx: Double = 0.0,
    val regime: Regimes = Regimes.SIDEWAY
) {
    fun bandList(): DoubleArray = doubleArrayOf(
        st2.triggerPrice, st3.triggerPrice, st4.triggerPrice,
        st5.triggerPrice, st6.triggerPrice, st7.triggerPrice, st8.triggerPrice
    )
    val isCompressed: Boolean get() {
        val bands = bandList()
        val max = bands.maxOrNull() ?: 0.0
        val min = bands.minOrNull() ?: 0.0
        return (max - min) / ((max + min) / 2) < 0.005
    }
}

enum class Actions { BUY, SELL, SCALP, EXIT, TRAIL, PARTIAL, NO_TRADE, WAIT }
enum class OptionType { CE, PE }
enum class Regimes { TRENDING_UP, TRENDING_DOWN, SIDEWAY, VOLATILE, BREAKOUT, REVERSAL }
enum class TradeGrade { AP, A, B, C, REJECT }
enum class OrderType { MARKET, LIMIT, SL, SLM, BRACKET, COVER }

// ===== Position Sizing =====
data class PositionSize(
    val quantity: Int,
    val lotSize: Int,
    val lots: Int,
    val marginRequired: Double,
    val riskAmount: Double,
    val riskPerUnit: Double
)

/**
 * Audit 4.12: TradePlan returned by RiskEngine
 */
data class TradePlan(
    val approved: Boolean,
    val symbol: String,
    val quantity: Int,
    val lots: Int,
    val entryPrice: Double,
    val stopLoss: Double,
    val target: Double,
    val trailingSl: Double,
    val marginRequired: Double,
    val riskAmount: Double,
    val charges: TradeCharges = TradeCharges(),
    val rejectionReason: String = ""
)

/**
 * Audit 4.10: Simulation of Brokerage & Taxes
 */
data class TradeCharges(
    val brokerage: Double = 20.0,
    val stt: Double = 0.0,
    val exchangeCharges: Double = 0.0,
    val gst: Double = 0.0,
    val sebi: Double = 0.0,
    val total: Double = 20.0
)

// ===== Risk Limits =====
data class RiskLimits(
    val dailyLossLimit: Double = 50_000.0,
    val dailyTarget: Double = 100_000.0,
    val maxExposure: Double = 200_000.0,
    val maxTradesPerDay: Int = 20,
    val circuitBreakerLoss: Double = 30_000.0,
    val circuitBreakerTrades: Int = 10
)

// ===== Greeks =====
data class OptionGreeks(
    val delta: Double,
    val gamma: Double,
    val theta: Double,
    val vega: Double,
    val iv: Double
)

// ===== Market Breadth =====
data class MarketBreadth(
    val advances: Int,
    val declines: Int,
    val unchanged: Int,
    val advanceDeclineRatio: Double,
    val newHighs: Int,
    val newLows: Int
)

// ===== PCR Data =====
data class PCRData(
    val pcr: Double,
    val callOi: Long,
    val putOi: Long,
    val callVolume: Long,
    val putVolume: Long,
    val maxPain: Double
)

// ===== Signal with full metadata =====
@Immutable
data class Signal(
    val action: Actions,
    val optionType: OptionType,
    val symbol: Symbol,
    val entryPrice: Price,
    val target: Price,
    val stopLoss: Price,
    val trailingSl: Price? = null,
    val confidence: Confidence,
    val reason: String,
    val timestamp: Long,
    val quantity: Int,
    val lots: Int,
    val regime: Regimes = Regimes.SIDEWAY,
    val matchedBand: String = "",
    val reversalStrength: Double = 0.0,
    val grade: TradeGrade = TradeGrade.C,
    val weightedConfidence: Double = 0.0,
    val greeks: OptionGreeks? = null,
    val pcrAtSignal: Double = 0.0,
    val vixAtSignal: Double = 0.0
) {
    val isEntry: Boolean get() = action == Actions.BUY || action == Actions.SELL || action == Actions.SCALP
}

// ===== Open Position =====
data class OpenPosition(
    val id: String,
    val symbol: Symbol,
    val optionType: OptionType,
    val entryPrice: Price,
    val currentPrice: Price,
    val quantity: Int,
    val lots: Int,
    val target: Price,
    val stopLoss: Price,
    val trailingSl: Price?,
    val mtm: Double,
    val mtmPct: Double,
    val entryTime: Long,
    val greeks: OptionGreeks?,
    val orderType: OrderType = OrderType.BRACKET
)

// ===== Margin Info =====
data class MarginInfo(
    val totalFund: Double,
    val usedMargin: Double,
    val availableMargin: Double,
    val openPositionsMargin: Double,
    val dayPnL: Double,
    val unrealizedPnL: Double
)

data class TickData(
    val symbol: String,
    val ltp: Double,
    val volume: Long,
    val ts: Long,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val oi: Long = 0L,
    val iv: Double = 0.0
)

// ===== OI Analysis =====
data class OIAnalysis(
    val symbol: String,
    val currentOi: Long,
    val oiChange: Long,
    val oiChangePct: Double,
    val priceChange: Double,
    val interpretation: String // "Long Buildup", "Short Buildup", "Long Unwinding", "Short Covering"
)

// ===== Volume Profile =====
data class VolumeProfile(
    val poc: Double, // Point of Control
    val valueAreaHigh: Double,
    val valueAreaLow: Double,
    val volumeNodes: List<VolumeNode>
)

data class VolumeNode(val price: Double, val volume: Long)

// ===== Trade Memory for AI Learning =====
data class TradeMemory(
    val signal: Signal,
    val outcome: Double, // PnL
    val exitReason: String,
    val marketContext: MarketContext,
    val timestamp: Long
)

data class MarketContext(
    val vix: Double,
    val pcr: Double,
    val regime: Regimes,
    val adx: Double,
    val marketBreadth: MarketBreadth?
)

// ===== VIRTUAL TRADE MODELS =====
data class VirtualTrade(
    val id: String, // Audit 4.9: UUID String
    val symbol: String,
    val action: String, // BUY or SELL
    val entryPrice: Double,
    val quantity: Int,
    var stopLoss: Double, // Var for Audit 4.7 Trailing
    val target: Double,
    val confidence: Int,
    val reason: String,
    val matchedBand: String = "",
    val entryTime: Long,
    val exitPrice: Double = 0.0,
    val exitTime: Long = 0L,
    val status: TradeStatus = TradeStatus.OPEN,
    val pnl: Long = 0L,
    val charges: Double = 0.0, // Audit 4.10
    val brokerage: Long = 0L,
    val exitBrokerage: Long = 0L
) {
    val isOpen: Boolean get() = status == TradeStatus.OPEN
    val totalBrokerage: Long get() = brokerage + exitBrokerage
    val entryValue: Long get() = (entryPrice * quantity).toLong()
}

enum class TradeStatus { OPEN, PROFIT, LOSS }
