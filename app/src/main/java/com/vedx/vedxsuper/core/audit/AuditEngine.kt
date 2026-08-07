package com.vedx.vedxsuper.core.audit

import android.util.Log
import com.vedx.vedxsuper.data.Signal
import com.vedx.vedxsuper.data.TradeStatus

/**
 * V4 AuditEngine
 * Detailed logging and auditing of system decisions.
 * Audit Fix 42: Stripping sensitive price/ID details from Logcat in production
 */
class AuditEngine {
    fun logSignal(signal: Signal) {
        // Log stripped details to Logcat, full details should go to encrypted file in future
        Log.i("VEDX_AUDIT", "📡 SIGNAL: ${signal.symbol.value} ${signal.optionType} Reason: ${signal.reason.take(30)}...")
    }

    fun logTrade(tradeId: String, action: String, symbol: String, price: Double) {
        Log.i("VEDX_AUDIT", "💼 TRADE $action: $symbol ID: ${tradeId.takeLast(4)}")
    }

    fun logExit(tradeId: String, pnl: Long, status: TradeStatus) {
        Log.i("VEDX_AUDIT", "🏁 EXIT ID: ${tradeId.takeLast(4)} Status: $status")
    }
}
