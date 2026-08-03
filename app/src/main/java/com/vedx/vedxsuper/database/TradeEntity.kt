package com.vedx.vedxsuper.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val type: String, // BUY, SELL
    val entryPrice: Double,
    val exitPrice: Double? = null,
    val stopLoss: Double,
    val target: Double,
    val quantity: Int,
    val brokerage: Double = 20.0,
    val pnl: Double = 0.0,
    val status: String, // OPEN, CLOSED
    val confidence: Int = 0,
    val explanation: String = "",
    val entryTime: Long = System.currentTimeMillis(),
    val exitTime: Long? = null
)
