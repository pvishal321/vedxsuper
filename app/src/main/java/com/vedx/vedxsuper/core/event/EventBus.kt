package com.vedx.vedxsuper.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class EventBus {
    private val _events = MutableSharedFlow<SystemEvent>(extraBufferCapacity = 1000)
    val events = _events.asSharedFlow()

    suspend fun publish(event: SystemEvent) {
        _events.emit(event)
    }

    fun tryPublish(event: SystemEvent) {
        _events.tryEmit(event)
    }
}
