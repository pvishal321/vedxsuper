package com.vedx.vedxsuper.trade

data class Position(
    val symbol: String,
    val type: String, // BUY or SELL
    val entryPrice: Double,
    val quantity: Int,
    var currentPrice: Double = 0.0
) {
    val pnl: Double
        get() = if (type == "BUY") (currentPrice - entryPrice) * quantity else (entryPrice - currentPrice) * quantity
}
