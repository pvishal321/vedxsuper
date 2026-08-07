package com.vedx.vedxsuper.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.vedx.vedxsuper.VedxApp
import com.vedx.vedxsuper.auth.AuthState
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.ui.login.LoginViewModelV2
import com.vedx.vedxsuper.ui.chart.ChartViewModel
import com.vedx.vedxsuper.utils.SettingsManager
import java.util.*
import kotlin.math.abs

// ===== COLORS (WHITE/LIGHT THEME) =====
object AppColors {
    val White = Color(0xFFFFFFFF)
    val Background = Color(0xFFF5F7FA)
    val CardBg = Color(0xFFFFFFFF)
    val Border = Color(0xFFE2E8F0)
    val TextPrimary = Color(0xFF1E293B)
    val TextSecondary = Color(0xFF64748B)
    val TextMuted = Color(0xFF94A3B8)
    val Blue = Color(0xFF3B82F6)
    val BlueLight = Color(0xFFEFF6FF)
    val Green = Color(0xFF22C55E)
    val GreenLight = Color(0xFFF0FDF4)
    val Red = Color(0xFFEF4444)
    val RedLight = Color(0xFFFEF2F2)
    val Orange = Color(0xFFF59E0B)
    val Purple = Color(0xFF8B5CF6)
}

@Composable
fun DashboardScreen(
    marketViewModel: MarketViewModel,
    backtestViewModel: BacktestViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    tradeViewModel: TradeViewModel,
    historyViewModel: HistoryViewModel,
    optionChainViewModel: OptionChainViewModel,
    chartViewModel: ChartViewModel,
    loginViewModel: LoginViewModelV2,
    navController: NavHostController,
    settingsManager: SettingsManager
) {
    var tab by remember { mutableIntStateOf(0) }
    val authState by loginViewModel.authState.collectAsState()

    val items = listOf(
        "Home" to Icons.Default.Home,
        "Chart" to Icons.Default.Info,
        "Options" to Icons.Default.DateRange,
        "Trades" to Icons.Default.List,
        "Backtest" to Icons.Default.Refresh,
        "Settings" to Icons.Default.Settings
    )

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(marketViewModel.events) {
        marketViewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(settingsViewModel.events) {
        settingsViewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }
    
    Scaffold(
        containerColor = AppColors.Background,
        topBar = { V5Header(authState) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.White,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(AppColors.Border, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                }
            ) {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Blue,
                            selectedTextColor = AppColors.Blue,
                            unselectedIconColor = AppColors.TextMuted,
                            unselectedTextColor = AppColors.TextMuted,
                            indicatorColor = AppColors.BlueLight.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(marketViewModel, authState) { 
                    marketViewModel.selectSymbol(it)
                    tab = 1 // Switch to Chart/Analysis Hub
                }
                1 -> com.vedx.vedxsuper.ui.chart.ChartScreen(chartViewModel)
                2 -> OptionChainScreen(optionChainViewModel)
                3 -> TradesTab(tradeViewModel)
                4 -> BacktestTab(backtestViewModel)
                5 -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onClearHistory = { historyViewModel.clearHistory() },
                    onSyncAll = { marketViewModel.syncAllHistory() },
                    onEmergencyExit = { marketViewModel.emergencyExitAll() },
                    onLogout = { 
                        loginViewModel.logout()
                        navController.navigate("login") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun V5Header(authState: AuthState) {
    Surface(
        color = AppColors.White,
        modifier = Modifier.fillMaxWidth().drawBehind {
            drawLine(AppColors.Border, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        }
    ) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("VEDX SUPER", fontSize = 18.sp, fontWeight = FontWeight.Black, color = AppColors.Blue)
                Text("V5 INSTITUTIONAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextMuted, letterSpacing = 1.sp)
            }
            
            // Pulsating Logging Indicator
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "alpha"
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.Background).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(AppColors.Green.copy(alpha = alpha)))
                Spacer(Modifier.width(6.dp))
                Text("RECORDING", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            ConnectionBadge(authState)
        }
    }
}

// ===== HOME TAB =====
@Composable
fun HomeTab(vm: MarketViewModel, authState: AuthState, onIndexClick: (String) -> Unit) {
    val indexData by vm.indexData.collectAsState()
    val signals by vm.signals.collectAsState(initial = emptyList())
    val stLevels by vm.indexSTLevels.collectAsState()
    val analysis by vm.marketAnalysis.collectAsState()
    
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        
        // INDEX SLIDER (Clickable)
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(indexData) { idx -> 
                    AngelIndexCard(idx) { onIndexClick(idx.symbol.replace(" ", "")) }
                }
            }
        }

        // MINI CHART PREVIEW (ST2-ST8)
        item {
            if (stLevels != null) {
                VisualSTChart(stLevels!!, vm.appState.collectAsState().value.market.lastLtp["NIFTY"] ?: 0.0)
            }
        }

        // AI ANALYSIS & REGIME
        item { AIAnalysisCard(analysis, authState) }

        // ST LEVELS
        item { stLevels?.let { SuperTrendLevelsCard(it) } }

        // RECENT SIGNALS
        item {
            Text("AI Strategy Signals", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
        }
        items(signals.reversed()) { sig -> SignalRow(sig) }
        
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun VisualSTChart(st: MultiST, currentPrice: Double) {
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.White),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Box(Modifier.padding(16.dp).fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bands = listOf(st.st2, st.st3, st.st4, st.st5, st.st6, st.st7, st.st8)
                val minPrice = (bands.map { it.triggerPrice } + currentPrice).min() * 0.998
                val maxPrice = (bands.map { it.triggerPrice } + currentPrice).max() * 1.002
                val range = maxPrice - minPrice
                
                fun Double.toY() = size.height - ((this - minPrice) / range * size.height).toFloat()
                
                // Draw Bands
                bands.forEachIndexed { i, res ->
                    val color = if (res.trend == 1.toByte()) AppColors.Green.copy(0.4f) else AppColors.Red.copy(0.4f)
                    drawLine(color, Offset(0f, res.triggerPrice.toY()), Offset(size.width, res.triggerPrice.toY()), strokeWidth = 2f)
                }
                
                // Current Price Line
                drawLine(AppColors.Blue, Offset(0f, currentPrice.toY()), Offset(size.width, currentPrice.toY()), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            Text("ST Live Tracker", fontSize = 10.sp, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
            Text("LTP: ₹$currentPrice", fontSize = 12.sp, color = AppColors.Blue, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
fun AIAnalysisCard(analysis: MarketAnalysis, authState: AuthState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AppColors.Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI Intelligence", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.Blue)
                }
            }
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AnalysisItem("Market Regime", analysis.regime.name.replace("_", " "), 
                    when(analysis.regime) {
                        Regimes.BREAKOUT -> AppColors.Purple
                        Regimes.REVERSAL -> AppColors.Orange
                        Regimes.VOLATILE -> AppColors.Red
                        Regimes.SIDEWAY -> AppColors.TextSecondary
                        else -> AppColors.Green
                    })
                AnalysisItem("Trend ADX", "${analysis.adx.toInt()}", if(analysis.adx > 25) AppColors.Green else AppColors.TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AnalysisItem("VIX (Risk)", "%.2f".format(analysis.vix), if(analysis.vix > 20) AppColors.Red else AppColors.Green)
                AnalysisItem("PCR (Sent.)", "%.2f".format(analysis.pcr), if(analysis.pcr > 1.2) AppColors.Green else if(analysis.pcr < 0.7) AppColors.Red else AppColors.TextSecondary)
            }
        }
    }
}

@Composable
fun AnalysisItem(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = AppColors.TextMuted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun SuperTrendLevelsCard(st: MultiST) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("ST2 – ST8 Support/Resistance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Blue)
            Spacer(Modifier.height(12.dp))
            
            val bands = listOf(
                "ST 2" to st.st2, "ST 3" to st.st3, "ST 4" to st.st4,
                "ST 5" to st.st5, "ST 6" to st.st6, "ST 7" to st.st7, "ST 8" to st.st8
            )
            
            bands.chunked(4).forEach { rowBands ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    rowBands.forEach { (name, res) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, fontSize = 10.sp, color = AppColors.TextMuted)
                            Text("₹${"%.0f".format(res.triggerPrice)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (res.trend == 1.toByte()) AppColors.Green else AppColors.Red)
                        }
                    }
                }
                if (rowBands != bands.takeLast(3)) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun SignalRow(sig: Signal) {
    val isCE = sig.optionType == OptionType.CE
    val gradeColor = when(sig.grade) {
        TradeGrade.AP -> AppColors.Green
        TradeGrade.A -> AppColors.Blue
        TradeGrade.B -> AppColors.Orange
        else -> AppColors.TextSecondary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.White),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (isCE) AppColors.Green else AppColors.Red))
                Spacer(Modifier.width(8.dp))
                Text(sig.symbol.value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                MetricPill("GRADE ${sig.grade}", AppColors.White, gradeColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(sig.reason, fontSize = 11.sp, color = AppColors.TextSecondary)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = AppColors.Border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SignalMetric("Entry", "₹${"%.1f".format(sig.entryPrice.rupees)}")
                SignalMetric("Target", "₹${"%.1f".format(sig.target.rupees)}", AppColors.Green)
                SignalMetric("SL", "₹${"%.1f".format(sig.stopLoss.rupees)}", AppColors.Red)
                SignalMetric("Conf.", "${sig.confidence.pct}%")
            }
        }
    }
}

@Composable
fun MetricPill(text: String, textColor: Color, bgColor: Color) {
    Text(
        text,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        color = textColor,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bgColor).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun SignalMetric(label: String, value: String, color: Color = AppColors.TextPrimary) {
    Column {
        Text(label, fontSize = 9.sp, color = AppColors.TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun AngelIndexCard(index: IndexData, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(150.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.White),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(index.symbol, fontSize = 11.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text("₹${"%.2f".format(index.price)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            
            val isPositive = index.change >= 0
            val color = if (isPositive) AppColors.Green else AppColors.Red
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isPositive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "${"%.2f".format(index.change)} (${"%.2f".format(index.changePct)}%)",
                    fontSize = 10.sp,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConnectionBadge(status: AuthState) {
    val (text, color, bgColor) = when (status) {
        is AuthState.Authenticated -> Triple("LIVE", AppColors.Green, AppColors.GreenLight)
        is AuthState.Checking, is AuthState.Refreshing -> Triple("SYNC", AppColors.Orange, AppColors.BlueLight)
        else -> Triple("OFF", AppColors.Red, AppColors.RedLight)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bgColor).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TradesTab(vm: TradeViewModel) {
    val balance by vm.balance.collectAsState()
    val totalPnL by vm.totalPnL.collectAsState()
    val openTrades by vm.openTrades.collectAsState()
    val history by vm.tradeHistory.collectAsState()
    
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item { WalletCardV5(balance, totalPnL, openTrades.size, history.size) }
        item { Text("Active Virtual Positions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, modifier = Modifier.padding(vertical = 12.dp)) }
        if (openTrades.isEmpty()) item { EmptyState("No open trades") }
        items(openTrades) { VirtualTradeRow(it) }
        item { Text("Recent Performance", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, modifier = Modifier.padding(vertical = 12.dp)) }
        items(history.take(10)) { VirtualTradeRow(it) }
    }
}

@Composable
fun WalletCardV5(balance: Double, pnl: Long, open: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Blue)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Available Virtual Balance", color = AppColors.White.copy(0.7f), fontSize = 12.sp)
            Text("₹${"%,.2f".format(balance)}", color = AppColors.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Day P&L", color = AppColors.White.copy(0.7f), fontSize = 10.sp)
                    val pnlDisplay = pnl.toDouble()
                    Text("${if(pnlDisplay>=0) "+" else ""}₹${"%,.2f".format(pnlDisplay)}", color = if(pnlDisplay>=0) AppColors.GreenLight else Color.Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Active / Total", color = AppColors.White.copy(0.7f), fontSize = 10.sp)
                    Text("$open / $total", color = AppColors.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VirtualTradeRow(trade: VirtualTrade) {
    val isProfit = trade.pnl >= 0
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.White),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if(trade.action=="BUY") AppColors.GreenLight else AppColors.RedLight), contentAlignment = Alignment.Center) {
                Text(trade.action.take(1), color = if(trade.action=="BUY") AppColors.Green else AppColors.Red, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(trade.symbol, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Qty: ${trade.quantity} | ${trade.matchedBand}", fontSize = 10.sp, color = AppColors.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (trade.status == TradeStatus.OPEN) {
                    Text("OPEN", color = AppColors.Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    Text("${if(isProfit) "+" else ""}₹${trade.pnl}", color = if(isProfit) AppColors.Green else AppColors.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyState(msg: String) {
    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = AppColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
fun BacktestTab(vm: BacktestViewModel) {
    val result by vm.result.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Backtesting", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.runBacktest("NIFTY", 5) }, modifier = Modifier.fillMaxWidth()) {
            Text("Run 5-Day Historical Validation")
        }
        Spacer(Modifier.height(20.dp))
        if (result.totalTrades > 0) {
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AppColors.Border), colors = CardDefaults.cardColors(containerColor = AppColors.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Validation Summary", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AnalysisItem("Win Rate", "${result.winRate}%", AppColors.Green)
                        AnalysisItem("Net P&L", "₹${result.totalPnL}", if(result.totalPnL >= 0) AppColors.Green else AppColors.Red)
                    }
                }
            }
        }
    }
}
