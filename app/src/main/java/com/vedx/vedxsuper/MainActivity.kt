package com.vedx.vedxsuper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vedx.vedxsuper.ui.*
import com.vedx.vedxsuper.ui.login.LoginScreen
import com.vedx.vedxsuper.ui.chart.ChartViewModel
import com.vedx.vedxsuper.auth.*
import com.vedx.vedxsuper.auth.AuthState
import com.vedx.vedxsuper.ui.login.LoginViewModelV2
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    private val app by lazy { application as VedxApp }

    private val loginViewModel: LoginViewModelV2 by viewModels { factory }
    private val marketViewModel: MarketViewModel by viewModels { factory }
    private val backtestViewModel: BacktestViewModel by viewModels { factory }
    private val dashboardViewModel: DashboardViewModel by viewModels { factory }
    private val tradeViewModel: TradeViewModel by viewModels { factory }
    private val historyViewModel: HistoryViewModel by viewModels { factory }
    private val settingsViewModel: SettingsViewModel by viewModels { factory }
    private val optionChainViewModel: OptionChainViewModel by viewModels { factory }
    private val chartViewModel: ChartViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VedxMainApp(
                        loginViewModel = loginViewModel,
                        marketViewModel = marketViewModel,
                        backtestViewModel = backtestViewModel,
                        dashboardViewModel = dashboardViewModel,
                        tradeViewModel = tradeViewModel,
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel,
                        optionChainViewModel = optionChainViewModel,
                        chartViewModel = chartViewModel,
                        biometricAuthManager = app.biometricAuthManager
                    )
                }
            }
        }
        checkNotificationPermission()
    }

    @Suppress("UNCHECKED_CAST")
    private val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val authRepository = AuthRepository(app.autoLoginManagerV2, app.secureTokenManagerV2)
            return when {
                modelClass.isAssignableFrom(LoginViewModelV2::class.java) -> 
                    LoginViewModelV2(authRepository) as T
                modelClass.isAssignableFrom(MarketViewModel::class.java) ->
                    MarketViewModel(app.ultraNeuralCore, app.tradeRepository, app.portfolio, app.virtualTrade, app.secureTokenManagerV2, app.tradeNotificationManager, app.appStateStore, app.settingsManager) as T
                modelClass.isAssignableFrom(BacktestViewModel::class.java) ->
                    BacktestViewModel(app.appDatabase, app.settingsManager) as T
                modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                    DashboardViewModel(app.secureTokenManagerV2, app.portfolio) as T
                modelClass.isAssignableFrom(TradeViewModel::class.java) ->
                    TradeViewModel(app.portfolio, app.settingsManager) as T
                modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                    HistoryViewModel(app.portfolio) as T
                modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                    SettingsViewModel(app.settingsManager, app.portfolio) as T
                modelClass.isAssignableFrom(OptionChainViewModel::class.java) ->
                    OptionChainViewModel(app.optionDataManager, app.marketFeedEngine) as T
                modelClass.isAssignableFrom(ChartViewModel::class.java) ->
                    ChartViewModel(app.ultraNeuralCore, app.appStateStore) as T
                else -> throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

@Composable
fun VedxMainApp(
    loginViewModel: LoginViewModelV2,
    marketViewModel: MarketViewModel,
    backtestViewModel: BacktestViewModel,
    dashboardViewModel: DashboardViewModel,
    tradeViewModel: TradeViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    optionChainViewModel: OptionChainViewModel,
    chartViewModel: ChartViewModel,
    biometricAuthManager: BiometricAuthManager
) {
    val navController = rememberNavController()
    val authState by loginViewModel.authState.collectAsState()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as VedxApp
    
    var isAppReady by remember { mutableStateOf(app.isReady) }
    
    LaunchedEffect(Unit) {
        while(!app.isReady) {
            delay(100)
        }
        isAppReady = true
    }

    if (!isAppReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDest = if (authState is AuthState.Authenticated) "dashboard" else "login"

    NavHost(navController = navController, startDestination = startDest) {
        composable("login") {
            LoginScreen(viewModel = loginViewModel, biometricAuthManager = biometricAuthManager, onLoginSuccess = {
                navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("dashboard") {
            DashboardScreen(
                marketViewModel = marketViewModel,
                backtestViewModel = backtestViewModel,
                dashboardViewModel = dashboardViewModel,
                settingsViewModel = settingsViewModel,
                tradeViewModel = tradeViewModel,
                historyViewModel = historyViewModel,
                optionChainViewModel = optionChainViewModel,
                chartViewModel = chartViewModel,
                loginViewModel = loginViewModel,
                navController = navController,
                settingsManager = (androidx.compose.ui.platform.LocalContext.current.applicationContext as VedxApp).settingsManager
            )
        }
    }
}
