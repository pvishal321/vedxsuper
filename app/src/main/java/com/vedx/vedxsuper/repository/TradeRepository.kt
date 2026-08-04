package com.vedx.vedxsuper.repository

import com.vedx.vedxsuper.data.TradeDao

class TradeRepository(private val tradeDao: TradeDao) {
    suspend fun getAllTrades() = tradeDao.all()
    suspend fun clearHistory() = tradeDao.clear()
}
