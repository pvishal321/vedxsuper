package com.vedx.vedxsuper

import android.app.Application
import android.content.Intent
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.market.MarketDataManager
import com.vedx.vedxsuper.repository.AuthRepository
import com.vedx.vedxsuper.repository.MarketRepository
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.database.AppDatabase
import com.vedx.vedxsuper.service.MarketScheduleReceiver
import com.vedx.vedxsuper.trade.VirtualTradeManager
import com.vedx.vedxsuper.utils.NotificationHelper
import com.vedx.vedxsuper.utils.SettingsManager
import com.vedx.vedxsuper.websocket.SmartStreamManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VedxApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    lateinit var settingsManager: SettingsManager
    lateinit var tokenManager: SecureTokenManager
    lateinit var notificationHelper: NotificationHelper
    lateinit var marketDataManager: MarketDataManager
    lateinit var virtualTradeManager: VirtualTradeManager
    lateinit var marketRepository: MarketRepository
    lateinit var authRepository: AuthRepository
    lateinit var smartStreamManager: SmartStreamManager
    lateinit var systemHealthEngine: com.vedx.vedxsuper.strategy.engine.SystemHealthEngine

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        settingsManager = SettingsManager(this)
        tokenManager = SecureTokenManager(this)
        notificationHelper = NotificationHelper(this)
        marketDataManager = MarketDataManager()
        
        val db = AppDatabase.getDatabase(this)
        val tradeRepository = TradeRepository(db.tradeDao())
        
        virtualTradeManager = VirtualTradeManager(tradeRepository, settingsManager)
        
        marketRepository = MarketRepository(
            applicationScope,
            notificationHelper,
            settingsManager,
            tokenManager,
            marketDataManager,
            virtualTradeManager,
            db.candleDao(),
            db.tickDao(),
            this
        )
        
        authRepository = AuthRepository(tokenManager)
        
        smartStreamManager = SmartStreamManager(
            tokenManager,
            marketRepository,
            applicationScope
        )

        systemHealthEngine = com.vedx.vedxsuper.strategy.engine.SystemHealthEngine(
            this,
            tokenManager,
            smartStreamManager,
            marketRepository,
            applicationScope
        )

        // Initialize Market Automation Schedule
        MarketScheduleReceiver.scheduleMarketAlarms(this)

        // Start background service immediately if session is valid
        if (tokenManager.hasValidSession()) {
            val serviceIntent = Intent(this, com.vedx.vedxsuper.service.TradingBackgroundService::class.java)
            startForegroundService(serviceIntent)
        }
    }

    companion object {
        lateinit var instance: VedxApplication
            private set
    }
}
