package com.vedx.vedxsuper.core.risk

import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RiskEngineTest {

    @Test
    fun `test Dynamic Risk and Max Trades`() = runTest {
        val limits = RiskLimits(maxTradesPerDay = 20, dailyLossLimit = 50000.0)
        val engine = RiskEngine(limits = limits)
        val margin = 100_000.0
        val lotSize = 75
        val marginPerLot = 10000.0
        
        val signal = Signal(
            action = Actions.BUY,
            optionType = OptionType.CE,
            symbol = Symbol("NIFTY"),
            entryPrice = Price.from(100.0),
            target = Price.from(120.0),
            stopLoss = Price.from(90.0),
            confidence = Confidence(80),
            reason = "Test",
            timestamp = System.currentTimeMillis(),
            quantity = 75,
            lots = 1
        )

        // 1. Valid trade
        val plan1 = engine.validateAndCreatePlan(signal, margin, lotSize, marginPerLot, false)
        assertTrue("Should approve valid trade", plan1.approved)
        assertTrue("Quantity should be > 0", plan1.quantity > 0)

        // 2. Already open
        val plan2 = engine.validateAndCreatePlan(signal, margin, lotSize, marginPerLot, true)
        assertFalse("Should reject if already open", plan2.approved)
        assertEquals("POSITION_ALREADY_OPEN", plan2.rejectionReason)

        // 3. Max Trades (simulate 20 trades)
        for (i in 0 until 20) {
            engine.onTradeOutcome(100.0)
        }
        val plan3 = engine.validateAndCreatePlan(signal, margin, lotSize, marginPerLot, false)
        assertFalse("Should reject after max trades", plan3.approved)
        assertEquals("MAX_TRADES_REACHED", plan3.rejectionReason)
    }
}
