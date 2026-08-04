package com.vedx.vedxsuper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.ui.*
import com.vedx.vedxsuper.ui.login.LoginScreen
import com.vedx.vedxsuper.ui.login.LoginViewModel
import com.vedx.vedxsuper.utils.SettingsManager
import com.vedx.vedxsuper.auth.BiometricAuthManager

class MainActivity : FragmentActivity() { // FragmentActivity for Biometric

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* Permission handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as VedxApp

        val tokenManager = app.secureTokenManager
        tokenManager.migrateFromLegacyPrefs(this)

        val db = app.appDatabase
        val virtualTradeManager = app.virtualTradeManager
        val settingsManager = app.settingsManager
        val notificationManager = app.tradeNotificationManager

        // Create ViewModels
        val loginViewModel = LoginViewModel(app.angelClient, tokenManager, app.autoLoginManager)
        val marketViewModel = MarketViewModel(
            ultraNeuralCore = app.ultraNeuralCore,
            tradeRepository = TradeRepository(db.td()),
            virtualTradeManager = virtualTradeManager,
            tokenManager = tokenManager,
            notificationManager = notificationManager
        )
        val backtestViewModel = BacktestViewModel()
        val dashboardViewModel = DashboardViewModel(tokenManager, virtualTradeManager)
        val settingsViewModel = SettingsViewModel(settingsManager, virtualTradeManager)
        val tradeViewModel = TradeViewModel(virtualTradeManager, settingsManager)
        val historyViewModel = HistoryViewModel(virtualTradeManager)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VedxMainApp(
                        loginViewModel = loginViewModel,
                        marketViewModel = marketViewModel,
                        backtestViewModel = backtestViewModel,
                        dashboardViewModel = dashboardViewModel,
                        settingsViewModel = settingsViewModel,
                        tradeViewModel = tradeViewModel,
                        historyViewModel = historyViewModel,
                        tokenManager = tokenManager,
                        settingsManager = settingsManager,
                        biometricAuthManager = app.biometricAuthManager
                    )
                }
            }
        }

        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun VedxMainApp(
    loginViewModel: LoginViewModel,
    marketViewModel: MarketViewModel,
    backtestViewModel: BacktestViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    tradeViewModel: TradeViewModel,
    historyViewModel: HistoryViewModel,
    tokenManager: SecureTokenManager,
    settingsManager: SettingsManager,
    biometricAuthManager: BiometricAuthManager
) {
    val navController = rememberNavController()
    val startDest = if (tokenManager.hasValidSession()) "dashboard" else "login"

    NavHost(navController = navController, startDestination = startDest) {
        composable("login") {
            LoginScreen(
                viewModel = loginViewModel,
                biometricAuthManager = biometricAuthManager,
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                marketViewModel = marketViewModel,
                backtestViewModel = backtestViewModel,
                dashboardViewModel = dashboardViewModel,
                settingsViewModel = settingsViewModel,
                tradeViewModel = tradeViewModel,
                historyViewModel = historyViewModel,
                navController = navController,
                settingsManager = settingsManager
            )
        }
    }
}
