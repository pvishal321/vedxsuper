package com.vedx.vedxsuper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.model.market.IndexData
import com.vedx.vedxsuper.repository.MarketRepository
import com.vedx.vedxsuper.strategy.engine.*
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import com.vedx.vedxsuper.strategy.indicator.StrategySignal
import com.vedx.vedxsuper.websocket.ConnectionStatus
import com.vedx.vedxsuper.websocket.SmartStreamManager
import com.vedx.vedxsuper.websocket.StreamMetrics
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val selectedIndex: String = "NIFTY",
    val selectedOption: String? = null,
    val marketHealth: Int = 0,
    val tradeProbability: Int = 0,
    val marketRegime: String = "NO_REGIME",
    val trendState: String = "WAITING",
    val pullbackCount: Int = 0,
    val reEntryCount: Int = 0,
    val activeBand: Int = 0,
    val structure: String = "NO_STRUCTURE",
    val confidence: Int = 0,
    val recommendedTrade: InstitutionalSignal? = null,
    val riskState: AccountState? = null,
    val brokerStatus: Boolean = false,
    val websocketStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val aiStatus: Boolean = true,
    val countdown: String = "02:45",
    val learningStats: LearningStats? = null,
    val indicatorSignal: StrategySignal = StrategySignal.NO_TRADE,
    val trendAge: Int = 0,
    val alignmentScore: Int = 0,
    val distanceFromPrice: Double = 0.0,
    val systemHealth: SystemHealth = SystemHealth(),
    val streamMetrics: StreamMetrics = StreamMetrics(),
    val strategyWeights: Map<String, Double> = emptyMap(),
    val intelligenceFeed: List<String> = emptyList(),
    val zoneMatchScore: Int = 0,
    val indexZoneStatus: ZoneStatus? = null,
    val optionZoneStatus: ZoneStatus? = null,
    val indexStResult: MultiSuperTrendResult? = null,
    val optionStResult: MultiSuperTrendResult? = null,
    val ticksSavedCount: Int = 0,
    val syncStatus: String = "",
    val indexAgentReports: List<AgentReport> = emptyList(),
    val optionAgentReports: List<AgentReport> = emptyList(),
    val indexPrice: Double = 0.0,
    val optionPrice: Double = 0.0
)

class DashboardViewModel(
    private val repository: MarketRepository,
    private val tokenManager: SecureTokenManager,
    private val smartStreamManager: SmartStreamManager,
    private val healthEngine: SystemHealthEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _selectedSymbol = MutableStateFlow("NIFTY")
    private val _selectedOption = MutableStateFlow<String?>(null)

    init {
        observeChanges()
    }

    fun selectIndex(symbol: String) {
        _selectedSymbol.value = symbol
        repository.setSelectedDashboardIndex(symbol)
    }

    fun selectOption(symbol: String?) {
        _selectedOption.value = symbol
    }

    private fun observeChanges() {
        viewModelScope.launch {
            combine(_selectedSymbol, _selectedOption) { symbol, option -> symbol to option }
                .flatMapLatest { (sSymbol, sOption) ->
                    combine(
                        repository.indexData,
                        smartStreamManager.connectionState,
                        smartStreamManager.metrics,
                        repository.getInstitutionalSignals(),
                        healthEngine.health,
                        repository.getVirtualBalanceFlow(),
                        repository.getIndexMultiTrend(sSymbol) ?: MutableStateFlow(null),
                        repository.getIndexZoneStatus(sSymbol) ?: MutableStateFlow(null),
                        repository.ticksSavedCount,
                        repository.syncStatus
                    ) { args ->
                        @Suppress("UNCHECKED_CAST")
                        val indexDataMap = args[0] as Map<String, IndexData>
                        val connectionState = args[1] as ConnectionStatus
                        val metrics = args[2] as StreamMetrics
                        @Suppress("UNCHECKED_CAST")
                        val signals = args[3] as? List<InstitutionalSignal> ?: emptyList()
                        val health = args[4] as SystemHealth
                        val balance = args[5] as Double
                        val multiTrend = args[6] as? MultiSuperTrendResult
                        val indexZoneStatus = args[7] as? ZoneStatus
                        val ticksSaved = args[8] as Int
                        val currentSyncStatus = args[9] as? String ?: ""

                        val currentVix = repository.getVix()
                        val currentPrice = indexDataMap[sSymbol]?.lastTradedPrice ?: 0.0
                        val indexAgents = repository.getIndexAgents(sSymbol, currentPrice, currentVix)
                        val latestInstitutionalSignal = signals.lastOrNull { it.optionSymbol.contains(sSymbol) }
                        
                        val activeOptionSymbol = sOption ?: latestInstitutionalSignal?.optionSymbol
                        val optionAgents = activeOptionSymbol?.let { repository.getOptionAgents(it, currentVix) } ?: emptyList()
                        val optStResult = activeOptionSymbol?.let { repository.getOptionMultiTrend(it) }
                        
                        val strength = repository.getIndexStrength(sSymbol)
                    
                    val stats = repository.getLearningStats()
                    val risk = repository.getRiskState()
                    risk.balance = balance

                    val regime = repository.getMarketRegime(sSymbol) ?: MarketRegime.NO_TRADE
                    val structure = repository.getMarketStructure(sSymbol) ?: MarketStructure.NO_STRUCTURE
                    val trend = repository.getTrendLifecycle(sSymbol)
                    val weightsObj = repository.getLearningWeights()
                    
                    val optZoneStatus = latestInstitutionalSignal?.optionZone
                    val zoneMatch = latestInstitutionalSignal?.zoneMatchScore ?: 0

                    val weights = if (weightsObj is StrategyWeights) {
                        mapOf(
                            "Correlation" to weightsObj.correlationWeight,
                            "Index Strength" to weightsObj.indexStrengthWeight,
                            "Option Strength" to weightsObj.optionStrengthWeight,
                            "Trend Memory" to weightsObj.trendMemoryWeight,
                            "Premium Potential" to weightsObj.premiumPotentialWeight
                        )
                    } else {
                        emptyMap<String, Double>()
                    }
                    
                    val feed = mutableListOf<String>()
                    if (regime != MarketRegime.SIDEWAYS) feed.add("Market Regime: ${regime.name}")
                    if (structure != MarketStructure.NO_STRUCTURE) feed.add("Structure: ${structure.name}")
                    if ((trend?.pullbackCount ?: 0) > 0) feed.add("Trend Pullback # ${trend?.pullbackCount} detected")
                    latestInstitutionalSignal?.let { feed.add("Latest Intelligence: ${it.reason}") }

                    DashboardUiState(
                        selectedIndex = sSymbol,
                        selectedOption = activeOptionSymbol,
                        indexPrice = currentPrice,
                        optionPrice = activeOptionSymbol?.let { indexDataMap[it]?.lastTradedPrice } ?: 0.0,
                        marketHealth = strength.trendStrength.toInt(),
                        tradeProbability = ((strength.trendStrength * 0.8 + (latestInstitutionalSignal?.confidence ?: 0) * 0.2)).toInt(),
                        marketRegime = regime.name,
                        trendState = trend?.state?.name ?: "WAITING",
                        pullbackCount = trend?.pullbackCount ?: 0,
                        reEntryCount = trend?.reEntryCount ?: 0,
                        activeBand = trend?.activeBand ?: 0,
                        structure = structure.name.replace("_", " "),
                        confidence = latestInstitutionalSignal?.confidence ?: 0,
                        recommendedTrade = latestInstitutionalSignal,
                        riskState = risk,
                        brokerStatus = tokenManager.hasValidSession(),
                        websocketStatus = connectionState,
                        aiStatus = true,
                        countdown = "02:45",
                        learningStats = stats,
                        indicatorSignal = multiTrend?.master?.signal ?: StrategySignal.NO_TRADE,
                        trendAge = multiTrend?.master?.trendAge ?: 0,
                        alignmentScore = multiTrend?.master?.alignmentScore ?: 0,
                        distanceFromPrice = multiTrend?.master?.distanceFromPrice ?: 0.0,
                        systemHealth = health,
                        streamMetrics = metrics,
                        strategyWeights = weights,
                        intelligenceFeed = feed,
                        zoneMatchScore = zoneMatch,
                        indexZoneStatus = indexZoneStatus,
                        optionZoneStatus = optZoneStatus,
                        indexStResult = multiTrend,
                        optionStResult = optStResult ?: latestInstitutionalSignal?.let { repository.getOptionMultiTrend(it.optionSymbol) },
                        ticksSavedCount = ticksSaved,
                        syncStatus = currentSyncStatus,
                        indexAgentReports = indexAgents,
                        optionAgentReports = optionAgents
                    )
                }
            }.conflate().collect {
                _uiState.value = it
            }
        }
    }
}
