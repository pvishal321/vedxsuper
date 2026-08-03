package com.vedx.vedxsuper.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandle(candle: CandleEntity)

    @Query("SELECT * FROM candles WHERE symbol = :symbol ORDER BY timestamp ASC")
    suspend fun getCandles(symbol: String): List<CandleEntity>

    @Query("SELECT * FROM candles WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastCandles(symbol: String, limit: Int): List<CandleEntity>
    
    @Query("DELETE FROM candles WHERE timestamp < :expiry")
    suspend fun deleteOldCandles(expiry: Long)

    @Query("SELECT DISTINCT symbol FROM candles")
    suspend fun getSymbols(): List<String>
}
