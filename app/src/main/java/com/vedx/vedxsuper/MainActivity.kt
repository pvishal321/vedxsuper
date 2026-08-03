package com.vedx.vedxsuper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.database.AppDatabase
import com.vedx.vedxsuper.repository.TradeRepository
import com.vedx.vedxsuper.strategy.signal.StrategyState
import com.vedx.vedxsuper.ui.*
import com.vedx.vedxsuper.ui.components.*
import com.vedx.vedxsuper.ui.login.LoginScreen
import com.vedx.vedxsuper.ui.login.LoginViewModel
import com.vedx.vedxsuper.ui.tabs.*
import com.vedx.vedxsuper.utils.SettingsManager

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as VedxApplication
        
        val db = AppDatabase.getDatabase(this)
        val tradeRepository = TradeRepository(db.tradeDao())

        // Use Application scoped instances
        val loginViewModel = LoginViewModel(app.authRepository, app.tokenManager)
        val marketViewModel = MarketViewModel(app.marketRepository, tradeRepository, app.virtualTradeManager, app.smartStreamManager, app.tokenManager)
        val backtestViewModel = BacktestViewModel(db.candleDao(), app.settingsManager)
        val dashboardViewModel = DashboardViewModel(app.marketRepository, app.tokenManager, app.smartStreamManager, app.systemHealthEngine)
        val settingsViewModel = SettingsViewModel(app.settingsManager, app.marketRepository)
        val tradeViewModel = TradeViewModel(tradeRepository, app.virtualTradeManager, app.settingsManager)
        val historyViewModel = HistoryViewModel(tradeRepository)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VedxApp(
                        loginViewModel,
                        marketViewModel,
                        backtestViewModel,
                        dashboardViewModel,
                        settingsViewModel,
                        tradeViewModel,
                        historyViewModel,
                        app.tokenManager,
                        app.settingsManager
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
fun VedxApp(
    loginViewModel: LoginViewModel,
    marketViewModel: MarketViewModel,
    backtestViewModel: BacktestViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    tradeViewModel: TradeViewModel,
    historyViewModel: HistoryViewModel,
    tokenManager: SecureTokenManager,
    settingsManager: SettingsManager
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDest = if (tokenManager.hasValidSession()) "dashboard" else "login"
    
    NavHost(navController = navController, startDestination = startDest) {
        composable("login") {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    val serviceIntent = Intent(context, com.vedx.vedxsuper.service.TradingBackgroundService::class.java)
                    context.startForegroundService(serviceIntent)
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(marketViewModel, backtestViewModel, dashboardViewModel, settingsViewModel, tradeViewModel, historyViewModel, navController, settingsManager)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MarketViewModel,
    backtestViewModel: BacktestViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    tradeViewModel: TradeViewModel,
    historyViewModel: HistoryViewModel,
    navController: NavHostController,
    settingsManager: SettingsManager
) {
    val indexData by viewModel.indexData.collectAsState()
    val openTrades by tradeViewModel.openTrades.collectAsState(emptyList())
    val allTrades by historyViewModel.allTrades.collectAsState(emptyList())
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val correlationSignals by viewModel.getCorrelationSignals().collectAsState(emptyList())
    val institutionalSignals by viewModel.getInstitutionalSignals().collectAsState(emptyList())
    
    // VedxSuper Live Intelligence
    val learningStats = dashboardUiState.learningStats

    var selectedTab by remember { mutableIntStateOf(3) } // Default to HOME
    var selectedIndexDetail by remember { mutableStateOf<String?>(null) }
    var selectedChartSymbol by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (selectedIndexDetail != null) {
        val symbol = selectedIndexDetail!!
        val multiTrendState by viewModel.getMultiTrendState(symbol).collectAsState(null)
        val institutionalSignals by viewModel.getInstitutionalSignals().collectAsState(emptyList())
        val latestSignal = institutionalSignals.lastOrNull { it.optionSymbol == symbol }

        SuperTrendChart(
            symbol = symbol,
            candles = multiTrendState?.candles ?: emptyList(),
            stResult = multiTrendState?.stResult,
            activeSignal = latestSignal,
            isIndex = !symbol.contains("CE") && !symbol.contains("PE"),
            timeframe = settingsState.analysisTimeframe,
            onDismiss = { selectedIndexDetail = null }
        )
    }

    if (selectedChartSymbol != null) {
        val symbol = selectedChartSymbol!!
        val multiTrendState by viewModel.getMultiTrendState(symbol).collectAsState(null)
        val institutionalSignals by viewModel.getInstitutionalSignals().collectAsState(emptyList())
        val latestSignal = institutionalSignals.lastOrNull { it.optionSymbol == symbol }

        SuperTrendChart(
            symbol = symbol,
            candles = multiTrendState?.candles ?: emptyList(),
            stResult = multiTrendState?.stResult,
            activeSignal = latestSignal,
            isIndex = !symbol.contains("CE") && !symbol.contains("PE"),
            timeframe = settingsState.analysisTimeframe,
            onDismiss = { selectedChartSymbol = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(Color.White)) {
                CenterAlignedTopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("VedxSuper Intelligence", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C6BE5), fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                when (dashboardUiState.websocketStatus) {
                                                    com.vedx.vedxsuper.websocket.ConnectionStatus.LIVE -> Color(0xFF00B97D)
                                                    com.vedx.vedxsuper.websocket.ConnectionStatus.DISCONNECTED -> Color.Gray
                                                    else -> Color(0xFFFF9800)
                                                }, 
                                                CircleShape
                                            )
                                    )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.reconnectFeed() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF2C6BE5))
                        }
                        IconButton(onClick = {
                            viewModel.logout()
                            navController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.LightGray)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
                )

                // High Priority Learning Mode Alert
                if (learningStats?.isLearningMode == true) {
                    Surface(
                        color = Color(0xFFFF9800),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LEARNING MODE ACTIVE: Analyzing consecutive losses. Trade approval paused.", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // Show Institutional Grade Alerts
                institutionalSignals.lastOrNull()?.let { latest ->
                    Surface(
                        color = Color(0xFF2C6BE5).copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF2C6BE5).copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(8.dp).background(Color(0xFF2C6BE5), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val masterText = "MASTER ACTION " + latest.type + ": " + latest.optionSymbol
                                Text(
                                    text = masterText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2C6BE5)
                                )
                                val confText = "Confidence: " + latest.confidence + "% | Reason: " + latest.reason
                                Text(
                                    text = confText,
                                    fontSize = 9.sp,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                
                // Show Correlation Alerts in Header
                correlationSignals.lastOrNull()?.let { latest ->
                    Surface(
                        color = (if (latest.indexSignal.type == "BUY") Color(0xFF00B97D) else Color(0xFFF23645)).copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Refresh, 
                                contentDescription = null, 
                                tint = if (latest.indexSignal.type == "BUY") Color(0xFF00B97D) else Color(0xFFF23645),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val revText = "REVERSAL: " + latest.indexSignal.type + " " + latest.optionSymbol + " @ ₹" + latest.optionSignal.price.toString()
                            Text(
                                text = revText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (latest.indexSignal.type == "BUY") Color(0xFF00B97D) else Color(0xFFF23645)
                            )
                        }
                    }
                }
                
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E3E7))
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("HOME", fontSize = 8.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2C6BE5),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF2C6BE5),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Watchlist") },
                    label = { Text("WATCHLIST", fontSize = 8.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2C6BE5),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF2C6BE5),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Orders") },
                    label = { Text("ORDERS", fontSize = 8.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2C6BE5),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF2C6BE5),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Backtest") },
                    label = { Text("BACKTEST", fontSize = 8.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2C6BE5),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF2C6BE5),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
                    label = { Text("ACCOUNT", fontSize = 8.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2C6BE5),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF2C6BE5),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF4F6F9))) {
            when (selectedTab) {
                0 -> MarketTab(indexData, viewModel, onIndexClick = { selectedIndexDetail = it }, onChartClick = { selectedChartSymbol = it })
                1 -> TradesTab(openTrades, allTrades, tradeViewModel, historyViewModel, indexData)
                2 -> SettingsScreen(
                    viewModel = settingsViewModel, 
                    onClearHistory = { historyViewModel.clearHistory() },
                    onSyncAll = { viewModel.syncAllHistory() },
                    onEmergencyExit = { viewModel.emergencyExitAll() }
                )
                3 -> HomeTab(
                    viewModel = viewModel,
                    dashboardViewModel = dashboardViewModel, 
                    settingsViewModel = settingsViewModel,
                    onChartClick = { selectedChartSymbol = it },
                    onTimeframeChange = { viewModel.updateTimeframe(it) }
                )
                4 -> BacktestTab(backtestViewModel)
            }
        }
    }
}
