package com.vedx.vedxsuper.core.portfolio

import com.vedx.vedxsuper.core.TradingConstants
import com.vedx.vedxsuper.data.TradeStatus
import com.vedx.vedxsuper.data.VirtualTrade
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest

class PortfolioEngineTest {

    @Test
    fun `test Balance Updates and History`() = runTest {
        val initialBalance = TradingConstants.INITIAL_VIRTUAL_BALANCE
        val engine = PortfolioEngine(initialBalance = initialBalance)
        val trade = createDummyTrade("T1")
        val margin = 10000.0
        
        // Add trade
        engine.addTrade(trade, margin)
        // Note: In current implementation, adding trade only reserves margin in _reservedMargin, it doesn't deduct from balance
        // Wait, I should check PortfolioEngine.addTrade again
        assertEquals(initialBalance, engine.balance.value, 0.01)
        assertEquals(margin, engine.reservedMargin.value, 0.01)
        assertEquals(1, engine.openTrades.value.size)

        // Close with profit of 2000
        val pnl = 2000.0
        val charges = 40.0
        engine.closeTrade(trade.id, pnl, margin, charges, TradeStatus.PROFIT)
        
        assertEquals(initialBalance + pnl - charges, engine.balance.value, 0.01)
        assertEquals(0.0, engine.reservedMargin.value, 0.01)
        assertEquals(0, engine.openTrades.value.size)
        assertEquals(1, engine.tradeHistory.value.size)
    }

    private fun createDummyTrade(id: String) = VirtualTrade(
        id = id,
        symbol = "NIFTY",
        action = "BUY",
        entryPrice = 100.0,
        quantity = 100,
        stopLoss = 90.0,
        target = 120.0,
        confidence = 80,
        reason = "Test",
        entryTime = System.currentTimeMillis()
    )
}
