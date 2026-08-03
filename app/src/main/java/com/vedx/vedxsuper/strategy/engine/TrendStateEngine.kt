package com.vedx.vedxsuper.strategy.engine

import com.vedx.vedxsuper.model.market.TickData
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import com.vedx.vedxsuper.strategy.signal.BandInfo
import java.util.concurrent.atomic.AtomicReference

enum class TrendState {
    WAITING,
    BUILDING_TREND,
    REVERSAL_SETUP,
    REVERSAL_CONFIRMED,
    TREND_RUNNING,
    PULLBACK,
    RE_ENTRY_READY,
    TARGET_RUNNING,
    TREND_EXHAUSTION,
    REVERSAL_FAILED,
    TREND_FINISHED,
    NO_TRADE,
    SCALP_READY // New state for small quick trades
}

data class TrendContext(
    val state: TrendState = TrendState.WAITING,
    val startTime: Long = 0,
    val startPrice: Double = 0.0,
    val highestTarget: Int = 0,
    val pullbackCount: Int = 0,
    val reEntryCount: Int = 0,
    val activeBand: Int = 0,
    val lastUpdate: Long = System.currentTimeMillis()
)

class TrendStateEngine {
    private val _context = AtomicReference(TrendContext())
    val context: TrendContext get() = _context.get()

    fun updateState(
        tick: TickData,
        indexSt: MultiSuperTrendResult,
        optSt: MultiSuperTrendResult,
        indexBand: BandInfo,
        optBand: BandInfo,
        strength: StrengthMetrics,
        regime: MarketRegime,
        is15mCandleClosed: Boolean
    ): TrendState {
        val current = _context.get()
        var nextState = current.state

        val isPausedRegime = regime == MarketRegime.NO_TRADE

        if (isPausedRegime || strength.trendStrength < 20.0) {
            nextState = TrendState.NO_TRADE
        } else {
            when (current.state) {
                TrendState.WAITING, TrendState.NO_TRADE, TrendState.TREND_FINISHED -> {
                    // Start building trend if either index or option touches any band
                    if (indexBand.isTouch || optBand.isTouch) {
                        nextState = TrendState.BUILDING_TREND
                    }
                }

                TrendState.BUILDING_TREND -> {
                    // REQUIRED: Both Index AND Option must touch their ST zones
                    // [UPDATED] Entry ONLY on Candle Close as per user request
                    val indexMatch = indexBand.isTouch || indexBand.isRejection
                    val optMatch = optBand.isTouch || optBand.isRejection

                    if (indexMatch && optMatch && is15mCandleClosed) {
                        nextState = TrendState.REVERSAL_CONFIRMED
                    }
                }

                TrendState.REVERSAL_SETUP -> {
                    // REVERSAL confirmed when both show rejection from their respective bands
                    val isIndexRejected = indexBand.isRejection
                    val isOptRejected = optBand.isRejection

                    if (isIndexRejected && isOptRejected) {
                        nextState = TrendState.REVERSAL_CONFIRMED
                    } else if (!indexBand.isTouch && !optBand.isTouch) {
                        nextState = TrendState.REVERSAL_FAILED
                    }
                }

                TrendState.SCALP_READY -> {
                    // Fast entry for scalping on momentum
                    if (indexBand.isRejection || strength.acceleration > 0.5) {
                        nextState = TrendState.TREND_RUNNING // Jump directly to running
                    } else if (!indexBand.isInZone) {
                        nextState = TrendState.WAITING
                    }
                }

                TrendState.REVERSAL_CONFIRMED -> {
                    nextState = TrendState.TREND_RUNNING
                }

                TrendState.TREND_RUNNING, TrendState.TARGET_RUNNING -> {
                    if (strength.isExhausted) {
                        nextState = TrendState.TREND_EXHAUSTION
                    } else if (indexBand.isTouch || optBand.isTouch) {
                        nextState = TrendState.PULLBACK
                    } else if (optBand.currentBand > current.highestTarget) {
                        nextState = TrendState.TARGET_RUNNING
                    }

                    if (indexSt.st2.trend != optSt.st2.trend || indexSt.st2.trend == 0) {
                        nextState = TrendState.TREND_FINISHED
                    }
                }

                TrendState.PULLBACK -> {
                    if (indexSt.st2.trend != optSt.st2.trend) {
                        nextState = TrendState.TREND_FINISHED
                    } else if (!indexBand.isTouch && strength.acceleration > 0) {
                        nextState = TrendState.RE_ENTRY_READY
                    }
                }

                TrendState.RE_ENTRY_READY -> {
                    nextState = TrendState.TREND_RUNNING
                }

                TrendState.TREND_EXHAUSTION -> {
                    if (indexSt.st2.trend != optSt.st2.trend) {
                        nextState = TrendState.TREND_FINISHED
                    }
                }

                TrendState.REVERSAL_FAILED -> {
                    nextState = TrendState.WAITING
                }
            }
        }

        updateContext(nextState, tick, optBand)
        return nextState
    }

    private fun updateContext(nextState: TrendState, tick: TickData, optBand: BandInfo) {
        val current = _context.get()
        val newContext = when {
            nextState != current.state && nextState == TrendState.TREND_RUNNING -> {
                current.copy(
                    state = nextState,
                    startTime = tick.timestamp,
                    startPrice = tick.ltp,
                    activeBand = optBand.currentBand,
                    highestTarget = optBand.currentBand,
                    lastUpdate = tick.timestamp
                )
            }
            nextState == TrendState.PULLBACK && current.state != TrendState.PULLBACK -> {
                current.copy(state = nextState, pullbackCount = current.pullbackCount + 1, lastUpdate = tick.timestamp)
            }
            else -> {
                current.copy(
                    state = nextState,
                    activeBand = optBand.currentBand,
                    highestTarget = maxOf(current.highestTarget, optBand.currentBand),
                    lastUpdate = tick.timestamp
                )
            }
        }
        _context.set(newContext)
    }

    fun reset() {
        _context.set(TrendContext())
    }
}
