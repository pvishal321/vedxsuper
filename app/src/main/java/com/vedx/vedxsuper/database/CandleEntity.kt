package com.vedx.vedxsuper.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "candles")
data class CandleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val timestamp: Long
)
