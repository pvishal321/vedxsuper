package com.vedx.vedxsuper.core.strategy

import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.Price
import org.junit.Assert.*
import org.junit.Test

class SuperTrendTest {

    @Test
    fun `test Wilder ATR and Band Locking`() {
        val minBars = 200
        val engine = SuperTrendEngine(factors = listOf(3f), minBarsRequired = minBars)
        val candles = mutableListOf<Candle>()
        
        // Generate enough candles for warmup
        for (i in 0 until 210) {
            candles.add(createCandle(100.0, 101.0, 99.0, 100.0, i.toLong()))
        }

        // Initially it should be null during warmup (less than minBars)
        assertNull("Warmup period not respected", engine.calculate(candles.take(minBars - 10)))

        val res = engine.calculate(candles)
        assertNotNull("Result should not be null after $minBars candles", res)
        
        // With constant price 100, High 101, Low 99, TR = 2
        // ATR should eventually stabilize around 2.0
        assertEquals(2.0, res!!.master.atr, 0.1)

        // Lower Band = Mid - 3 * ATR = 100 - 6 = 94
        // Upper Band = Mid + 3 * ATR = 100 + 6 = 106
        assertTrue("Lower band mismatch: ${res.master.lowerBand}", res.master.lowerBand <= 94.1 && res.master.lowerBand >= 93.9)
    }

    private fun createCandle(o: Double, h: Double, l: Double, c: Double, ts: Long) = Candle(
        open = Price.from(o),
        high = Price.from(h),
        low = Price.from(l),
        close = Price.from(c),
        volume = 1000,
        timestamp = ts * 60_000,
        isComplete = true
    )
}
