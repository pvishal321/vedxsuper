package com.vedx.vedxsuper.trade

object TradeCalculator {
    
    fun calculatePnl(type: String, entryPrice: Double, exitPrice: Double, quantity: Int): Double {
        return if (type == "BUY") {
            (exitPrice - entryPrice) * quantity
        } else {
            (entryPrice - exitPrice) * quantity
        }
    }

    fun calculateNetPnl(grossPnl: Double, brokerage: Double): Double {
        return grossPnl - (brokerage * 2) // Entry + Exit
    }
}
