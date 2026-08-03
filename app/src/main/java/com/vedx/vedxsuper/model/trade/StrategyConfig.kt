package com.vedx.vedxsuper.model.trade

enum class TradingMode {
    SCALPING,   // Fast entry-exit, smaller targets
    TRENDING    // Ride the trend, trailing SL
}

enum class RiskLevel {
    SAFE,       // Wait for high confidence setups
    MODERATE,   // Balanced approach
    AGGRESSIVE  // Quick entry on momentum
}

data class StrategyConfig(
    val mode: TradingMode = TradingMode.SCALPING,
    val riskLevel: RiskLevel = RiskLevel.MODERATE,
    val maxRiskPerTrade: Double = 1.0,
    val maxDailyLoss: Double = 3.0,
    val dailyProfitTarget: Double = 5.0,
    val stopLossValue: Double = 10.0,
    val targetValue: Double = 20.0,
    val isAutoTrailing: Boolean = true,
    val strikeType: String = "ATM"
)
