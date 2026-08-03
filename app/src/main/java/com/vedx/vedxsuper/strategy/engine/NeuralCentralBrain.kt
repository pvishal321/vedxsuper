package com.vedx.vedxsuper.strategy.engine

import kotlin.math.abs

data class CentralDecision(
    val action: MasterAction,
    val masterProbability: Int,
    val nearestAgent: Int,
    val etaToNextLevel: Int?,
    val internalLogic: String
)

/**
 * Brain that checks ANY band touch+reversal on Index AND Option.
 * 
 * [POINT 1] Regime-aware arbitration:
 * - TRENDING market → Directional BUY (CE for Bull, PE for Bear)
 * - SIDEWAYS/LOW_VOL market → Premium SELLING (Short CE/PE)
 */
class NeuralCentralBrain {

    fun coordinate(
        indexReports: List<AgentReport>,
        optionReports: List<AgentReport>,
        optionSymbol: String,
        indexTrend: Int,
        regime: MarketRegime,           // [NEW] Buy vs Sell निवडण्यासाठी
        indexPrice: Double,            // [NEW] OTM % काढण्यासाठी
        strikePrice: Double,           // [NEW] Option च strike
        isMarketCompressed: Boolean = false, // [NEW] Bands flat आहेत का?
        timestamp: Long = System.currentTimeMillis()
    ): CentralDecision {
        if (indexReports.isEmpty() || optionReports.isEmpty()) {
            return defaultDecision()
        }

        val indexTouchReject = indexReports.find { it.status == AgentStatus.REJECTING }
            ?: indexReports.find { it.status == AgentStatus.TOUCHING }

        val optionTouchReject = optionReports.find { it.status == AgentStatus.REJECTING }
            ?: optionReports.find { it.status == AgentStatus.TOUCHING }

        val isCall = optionSymbol.contains("CE")
        val isPut = optionSymbol.contains("PE")
        
        val directionMatch = when (indexTrend) {
            1 -> isCall
            -1 -> isPut
            else -> false
        }

        val activeBands = (indexReports + optionReports).filter {
            it.status in listOf(AgentStatus.TOUCHING, AgentStatus.REJECTING, AgentStatus.APPROACHING)
        }
        val avgProb = if (activeBands.isNotEmpty()) {
            activeBands.map { it.probability }.average().toInt()
        } else 0

        var finalAction = MasterAction.WAIT
        val logicParts = mutableListOf<String>()

        when {
            // ═══════════════════════════════════════════════════════
            //  [POINT 1] SELLING REGIME: Sideways / Low Volatility
            // ═══════════════════════════════════════════════════════
            isSellingRegime(regime) && isMarketCompressed -> {
                val otmPercent = calculateOtmPercent(indexPrice, strikePrice, isCall)
                logicParts.add("SELL_MODE: ${regime.name} | OTM=${String.format(java.util.Locale.US, "%.1f", otmPercent)}%")
                
                when {
                    // Short CE: Bearish/Sideways + Strike above price (OTM)
                    isCall && indexTrend != 1 && otmPercent >= 2.0 -> {
                        finalAction = MasterAction.SELL_CALL
                        logicParts.add("SHORT_CE: Safe OTM ${String.format(java.util.Locale.US, "%.1f", otmPercent)}%")
                    }
                    // Short PE: Bullish/Sideways + Strike below price (OTM)  
                    isPut && indexTrend != -1 && otmPercent >= 2.0 -> {
                        finalAction = MasterAction.SELL_PUT
                        logicParts.add("SHORT_PE: Safe OTM ${String.format(java.util.Locale.US, "%.1f", otmPercent)}%")
                    }
                    otmPercent < 2.0 -> {
                        logicParts.add("SKIP_SELL: Too close to ATM (${String.format(java.util.Locale.US, "%.1f", otmPercent)}%)")
                    }
                    else -> {
                        logicParts.add("SKIP_SELL: Trend conflict (Trend=$indexTrend)")
                    }
                }
            }

            // Market compressed नाही but sideways आहे → Wait
            isSellingRegime(regime) && !isMarketCompressed -> {
                logicParts.add("SKIP_SELL: Bands expanding, directional risk")
            }

            // ═══════════════════════════════════════════════════════
            //  BUYING REGIME: Trending market (existing logic)
            // ═══════════════════════════════════════════════════════
            !directionMatch -> {
                logicParts.add("SKIP_DIR: Idx=${if(indexTrend==1)"BULL" else if(indexTrend==-1)"BEAR" else "SIDE"} Opt=${if(isCall)"CE" else "PE"}")
            }

            indexTouchReject?.status == AgentStatus.REJECTING && 
            optionTouchReject?.status == AgentStatus.REJECTING -> {
                finalAction = if (isCall) MasterAction.BUY else MasterAction.SELL
                logicParts.add(
                    "REVERSAL: IdxST${indexTouchReject.multiplier}+OptST${optionTouchReject.multiplier} | Prob=${avgProb}%"
                )
            }

            indexTouchReject?.status == AgentStatus.TOUCHING && 
            optionTouchReject?.status == AgentStatus.TOUCHING -> {
                finalAction = MasterAction.PREPARE
                logicParts.add(
                    "TOUCH: IdxST${indexTouchReject.multiplier}+OptST${optionTouchReject.multiplier}"
                )
            }

            indexTouchReject != null && optionTouchReject == null -> {
                logicParts.add("IDX_ONLY: ST${indexTouchReject.multiplier} ${indexTouchReject.status}")
            }
            indexTouchReject == null && optionTouchReject != null -> {
                logicParts.add("OPT_ONLY: ST${optionTouchReject.multiplier} ${optionTouchReject.status}")
            }

            else -> {
                logicParts.add("SCAN: No touch/reversal")
            }
        }

        return CentralDecision(
            action = finalAction,
            masterProbability = avgProb.coerceIn(0, 100),
            nearestAgent = indexTouchReject?.multiplier 
                ?: indexReports.minByOrNull { it.distancePoints }?.multiplier ?: 0,
            etaToNextLevel = indexTouchReject?.etaMinutes,
            internalLogic = logicParts.joinToString(" | ")
        )
    }

    // ─── Selling Helpers ─────────────────────────────────────────

    private fun isSellingRegime(regime: MarketRegime): Boolean {
        return regime in listOf(MarketRegime.SIDEWAYS, MarketRegime.LOW_VOLATILITY)
    }

    /**
     * OTM % काढतो. Call साठी Strike > Index, Put साठी Strike < Index.
     */
    private fun calculateOtmPercent(indexPrice: Double, strikePrice: Double, isCall: Boolean): Double {
        if (strikePrice <= 0 || indexPrice <= 0) return 0.0
        return if (isCall) {
            ((strikePrice - indexPrice) / indexPrice * 100.0).coerceAtLeast(0.0)
        } else {
            ((indexPrice - strikePrice) / indexPrice * 100.0).coerceAtLeast(0.0)
        }
    }

    private fun defaultDecision() = CentralDecision(
        MasterAction.WAIT, 0, 0, null, "Initializing..."
    )
}
