package com.vedx.vedxsuper.core.event

import com.vedx.vedxsuper.data.*

sealed class SystemEvent {
    // Market Events
    data class TickReceived(val tick: TickData) : SystemEvent()
    data class CandleClosed(val candle: Candle, val timeframe: Int) : SystemEvent()
    
    // Strategy Events
    data class STLevelsUpdated(val symbol: String, val result: MultiST) : SystemEvent()
    data class SignalGenerated(val signal: Signal) : SystemEvent()
    
    // Risk & Trade Events
    data class RiskCheckPassed(val signal: Signal) : SystemEvent()
    data class RiskCheckFailed(val signal: Signal, val reason: String) : SystemEvent()
    data class TradeExecuted(val trade: VirtualTrade) : SystemEvent()
    data class TradeClosed(val trade: VirtualTrade, val pnl: Long) : SystemEvent()
    
    // System Events
    data class ErrorOccurred(val module: String, val message: String) : SystemEvent()
    data class StateRestored(val module: String) : SystemEvent()
}
