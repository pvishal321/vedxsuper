package com.vedx.vedxsuper.data

import androidx.room.*
import androidx.room.RoomDatabase

@Entity
data class DbCandle(
    @PrimaryKey val id: Long = System.currentTimeMillis(),
    val symbol: String,
    val open: Int,
    val high: Int,
    val low: Int,
    val close: Int,
    val vol: Long,
    val ts: Long
)

@Entity
data class DbTrade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val action: String,
    val entry: Int,
    val exit: Int,
    val qty: Int,
    val pnl: Int,
    val ts: Long
)

@Dao
interface CandleDao {
    @Query("SELECT * FROM DbCandle WHERE symbol=:s ORDER BY ts DESC LIMIT 500")
    suspend fun get(s: String): List<DbCandle>
    @Insert
    suspend fun insert(c: DbCandle)
    @Query("DELETE FROM DbCandle WHERE symbol=:s")
    suspend fun clear(s: String)
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM DbTrade ORDER BY ts DESC")
    suspend fun all(): List<DbTrade>
    @Insert
    suspend fun insert(t: DbTrade)
    @Query("DELETE FROM DbTrade")
    suspend fun clear()
}

@Database(entities = [DbCandle::class, DbTrade::class], version = 1)
abstract class AppDB : RoomDatabase() {
    abstract fun cd(): CandleDao
    abstract fun td(): TradeDao
    companion object {
        @Volatile private var i: AppDB? = null
        fun get(c: android.content.Context) = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c, AppDB::class.java, "vdb").build().also { i = it }
        }
    }
}
