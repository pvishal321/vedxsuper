package com.vedx.vedxsuper.utils

import android.util.Log

object VedxLogger {
    private const val TAG = "VedxSuper"

    fun i(msg: String) = Log.i(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)
    fun d(msg: String) = Log.d(TAG, msg)

    fun trade(msg: String) {
        Log.i("VEDX_TRADE", "🔥 $msg")
    }

    fun risk(msg: String) {
        Log.w("VEDX_RISK", "⚠️ $msg")
    }
}
