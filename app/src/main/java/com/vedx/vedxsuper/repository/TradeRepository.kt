package com.vedx.vedxsuper.repository

import com.vedx.vedxsuper.database.TradeDao
import com.vedx.vedxsuper.database.TradeEntity
import kotlinx.coroutines.flow.Flow

class TradeRepository(private val tradeDao: TradeDao) {

    val allTrades: Flow<List<TradeEntity>> = tradeDao.getAllTrades()
    val openTrades: Flow<List<TradeEntity>> = tradeDao.getOpenTrades()

    suspend fun insertTrade(trade: TradeEntity) = tradeDao.insertTrade(trade)
    
    suspend fun updateTrade(trade: TradeEntity) = tradeDao.updateTrade(trade)
    
    suspend fun clearHistory() {
        tradeDao.deleteAll()
    }
}
