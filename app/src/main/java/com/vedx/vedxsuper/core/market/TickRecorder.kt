package com.vedx.vedxsuper.core.market

import com.vedx.vedxsuper.core.event.EventBus
import com.vedx.vedxsuper.core.event.SystemEvent
import com.vedx.vedxsuper.data.DbTick
import com.vedx.vedxsuper.data.TickDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * V5 TickRecorder
 * Listens to EventBus and saves every tick to Database for future replay.
 */
class TickRecorder(
    private val tickDao: TickDao,
    private val eventBus: EventBus,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    init {
        eventBus.events.onEach { event ->
            if (event is SystemEvent.TickReceived) {
                record(event.tick)
            }
        }.launchIn(scope)
    }

    private fun record(tick: com.vedx.vedxsuper.data.TickData) {
        scope.launch {
            tickDao.insert(DbTick(
                symbol = tick.symbol,
                price = tick.ltp,
                volume = tick.volume,
                ts = tick.ts
            ))
        }
    }
}
