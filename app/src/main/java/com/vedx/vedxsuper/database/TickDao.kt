package com.vedx.vedxsuper.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TickDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTick(tick: TickEntity)

    @Query("SELECT * FROM ticks WHERE symbol = :symbol AND timestamp >= :fromTime ORDER BY timestamp ASC")
    suspend fun getTicks(symbol: String, fromTime: Long): List<TickEntity>

    @Query("DELETE FROM ticks WHERE timestamp < :expiry")
    suspend fun deleteOldTicks(expiry: Long)
}
