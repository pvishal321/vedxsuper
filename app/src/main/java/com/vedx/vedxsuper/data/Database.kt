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

@Entity
data class DbRiskState(
    @PrimaryKey val id: Int = 1,
    val dailyRealizedPnL: Double,
    val totalTradesToday: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val peakPnL: Double,
    val consecutiveLosses: Int,
    val isCircuitBroken: Boolean,
    val lastUpdate: Long
)

@Entity
data class DbVirtualTrade(
    @PrimaryKey val id: String,
    val symbol: String,
    val action: String,
    val entryPrice: Double,
    val quantity: Int,
    val stopLoss: Double,
    val target: Double,
    val matchedBand: String = "",
    val entryTime: Long,
    val status: String, // OPEN, PROFIT, LOSS
    val pnl: Long = 0,
    val charges: Double = 0.0
)

@Entity
data class DbLearningState(
    @PrimaryKey val factor: String, // e.g. "ST2"
    val successCount: Int
)

@Entity
data class DbTick(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val price: Double,
    val volume: Long,
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

@Dao
interface RiskDao {
    @Query("SELECT * FROM DbRiskState WHERE id = 1")
    suspend fun get(): DbRiskState?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(s: DbRiskState)
    @Query("DELETE FROM DbRiskState")
    suspend fun clear()
}

@Dao
interface VirtualTradeDao {
    @Query("SELECT * FROM DbVirtualTrade WHERE status = 'OPEN'")
    suspend fun getOpen(): List<DbVirtualTrade>
    @Query("SELECT * FROM DbVirtualTrade ORDER BY entryTime DESC LIMIT 500")
    suspend fun getAll(): List<DbVirtualTrade>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(t: DbVirtualTrade)
    @Query("DELETE FROM DbVirtualTrade")
    suspend fun clear()
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM DbLearningState")
    suspend fun getAll(): List<DbLearningState>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(s: DbLearningState)
}

@Dao
interface TickDao {
    @Insert
    suspend fun insert(t: DbTick)
    @Query("SELECT * FROM DbTick WHERE symbol = :s AND ts >= :from AND ts <= :to ORDER BY ts ASC")
    suspend fun getRange(s: String, from: Long, to: Long): List<DbTick>
}

@Database(entities = [DbCandle::class, DbTrade::class, DbRiskState::class, DbVirtualTrade::class, DbLearningState::class, DbTick::class], version = 6)
abstract class AppDB : RoomDatabase() {
    abstract fun cd(): CandleDao
    abstract fun td(): TradeDao
    abstract fun rd(): RiskDao
    abstract fun vtd(): VirtualTradeDao
    abstract fun ld(): LearningDao
    abstract fun tkd(): TickDao
    companion object {
        @Volatile private var i: AppDB? = null
        fun get(c: android.content.Context) = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c, AppDB::class.java, "vdb")
                .fallbackToDestructiveMigration()
                .build().also { i = it }
        }
    }
}
