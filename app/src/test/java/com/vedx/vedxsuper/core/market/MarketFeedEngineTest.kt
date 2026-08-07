package com.vedx.vedxsuper.core.market

import com.vedx.vedxsuper.core.event.EventBus
import com.vedx.vedxsuper.data.OptionDataManager
import com.vedx.vedxsuper.data.TickData
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MarketFeedEngineTest {

    @Test
    fun parseUsesOfficialSmartApiLtpOffset() = runTest {
        val engine = MarketFeedEngine(
            token = "",
            clientCode = "",
            feedToken = "",
            apiKey = "",
            optionDataManager = mockk<OptionDataManager>(relaxed = true),
            eventBus = EventBus(),
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val packet = ByteArray(51)
        packet[0] = 1
        packet[1] = 2
        "TESTTOKEN".toByteArray().copyInto(packet, 2)
        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(27, 1L)   // sequence number
            putLong(35, 0L)   // exchange timestamp
            putInt(43, 123456)
        }

        val parseMethod = MarketFeedEngine::class.java.getDeclaredMethod("parse", ByteArray::class.java)
        parseMethod.isAccessible = true

        val tickDeferred = CompletableDeferred<TickData>()
        val collectorJob = async {
            tickDeferred.complete(engine.ticks.first())
        }
        yield()

        parseMethod.invoke(engine, packet)

        val tick = withTimeout(1000) { tickDeferred.await() }
        collectorJob.cancel()

        assertEquals(1234.56, tick.ltp, 0.001)
    }
}
