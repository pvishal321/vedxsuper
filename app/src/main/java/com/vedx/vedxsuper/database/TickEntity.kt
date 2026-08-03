package com.vedx.vedxsuper.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ticks")
data class TickEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val price: Double,
    val volume: Long,
    val openInterest: Long,
    val timestamp: Long
)
