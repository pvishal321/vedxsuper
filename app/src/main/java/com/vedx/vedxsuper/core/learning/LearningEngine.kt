package com.vedx.vedxsuper.core.learning

import com.vedx.vedxsuper.data.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * V4 LearningEngine V2 with Persistence
 * Analyzes trade success patterns across ST factors, PCR/VIX ranges.
 */
class LearningEngine(
    private val ld: LearningDao? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val stStats = ConcurrentHashMap<String, Int>()
    private val pcrSuccessRanges = ConcurrentHashMap<String, Int>() // "0.7-0.9" -> SuccessCount
    private val vixSuccessRanges = ConcurrentHashMap<String, Int>()

    init {
        scope.launch {
            val saved = ld?.getAll() ?: emptyList()
            saved.forEach { stStats[it.factor] = it.successCount }
        }
    }
    
    fun onTradeCompleted(signal: Signal, status: TradeStatus) {
        if (status == TradeStatus.PROFIT) {
            val key = signal.matchedBand
            val count = (stStats[key] ?: 0) + 1
            stStats[key] = count
            scope.launch { ld?.save(DbLearningState(key, count)) }

            // Log PCR/VIX Range success
            val pcrRange = getRangeKey(signal.pcrAtSignal, 0.1)
            pcrSuccessRanges[pcrRange] = (pcrSuccessRanges[pcrRange] ?: 0) + 1

            val vixRange = getRangeKey(signal.vixAtSignal, 1.0)
            vixSuccessRanges[vixRange] = (vixSuccessRanges[vixRange] ?: 0) + 1
        }
    }

    private fun getRangeKey(value: Double, step: Double): String {
        val start = (value / step).toInt() * step
        return "%.1f-%.1f".format(java.util.Locale.US, start, start + step)
    }

    fun getBestST() = stStats.maxByOrNull { it.value }?.key ?: "ST3"
    fun getBestPcrRange() = pcrSuccessRanges.maxByOrNull { it.value }?.key ?: "0.8-0.9"
}
