package com.vedx.vedxsuper.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedx.vedxsuper.VedxApp
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.stream.FastTickEngine
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.auth.BiometricAuthManager
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs

// ===== COLORS (Ultra Light White Theme) =====
object AppColors {
    val White = Color(0xFFFFFFFF)
    val Background = Color(0xFFF8F9FA)
    val CardBg = Color(0xFFFFFFFF)
    val Border = Color(0xFFE5E7EB)
    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF6B7280)
    val TextMuted = Color(0xFF9CA3AF)
    val Blue = Color(0xFF2563EB)
    val BlueLight = Color(0xFFDBEAFE)
    val Green = Color(0xFF10B981)
    val GreenLight = Color(0xFFD1FAE5)
    val Red = Color(0xFFEF4444)
    val RedLight = Color(0xFFFEE2E2)
    val Orange = Color(0xFFF59E0B)
    val Purple = Color(0xFF8B5CF6)
}

// ===== ROOT COMPOSABLE (Entry Point) =====
@Composable
fun VedxRoot(vm: VedxVM = viewModel()) {
    // This is the old entry point, kept for compatibility if needed
    // But MainActivity now calls VedxApp (the Composable)
}

@Composable
fun DashboardScreen(
    marketViewModel: MarketViewModel,
    backtestViewModel: BacktestViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    tradeViewModel: TradeViewModel,
    historyViewModel: HistoryViewModel,
    navController: NavHostController,
    settingsManager: SettingsManager
) {
    var tab by remember { mutableIntStateOf(0) }
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Watch" to Icons.Default.Search,
        "Trades" to Icons.AutoMirrored.Filled.List,
        "Backtest" to Icons.Default.Build,
        "Settings" to Icons.Default.Settings
    )
    
    Scaffold(
        containerColor = AppColors.Background,
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
                0 -> HomeTab(marketViewModel)
                1 -> WatchTab(marketViewModel)
                2 -> TradesTab(tradeViewModel)
                3 -> BacktestTab(backtestViewModel)
                4 -> SettingsTab(settingsViewModel)
            }
        }
    }
}

// ===== HOME TAB =====
@Composable
fun HomeTab(vm: MarketViewModel) {
    val indexData by vm.indexData.collectAsState()
    val nifty = indexData.find { it.symbol == "NIFTY" }
    val price = nifty?.price ?: 0.0
    val signals by vm.signals.collectAsState(initial = emptyList())
    val lastSignal = signals.lastOrNull()
    
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("NIFTY 50", fontSize = 13.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                            Text("₹${"%.2f".format(price)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        }
                        ConnectionBadge()
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        MetricPill("AI Active", AppColors.Green, AppColors.GreenLight)
                        Spacer(Modifier.width(8.dp))
                        MetricPill("7-ST Strategy", AppColors.Blue, AppColors.BlueLight)
                    }
                }
            }
        }
        
        item {
            AnimatedVisibility(
                visible = lastSignal != null,
                enter = fadeIn() + slideInVertically { it / 2 }
            ) {
                lastSignal?.let { sig ->
                    val isBuy = sig.action == Actions.BUY
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { 
                            vm.onSignalReceived(sig.symbol.value, if(isBuy) "BUY" else "SELL", sig.entryPrice.rupees, sig.stopLoss.rupees, sig.target.rupees, sig.confidence.pct, sig.reason)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBuy) AppColors.GreenLight else AppColors.RedLight
                        ),
                        border = BorderStroke(1.dp, if (isBuy) AppColors.Green.copy(alpha = 0.3f) else AppColors.Red.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(if (isBuy) AppColors.Green else AppColors.Red),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBuy) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = AppColors.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        sig.symbol.value,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        if (isBuy) "BUY CALL" else "BUY PUT",
                                        fontSize = 12.sp,
                                        color = if (isBuy) AppColors.Green else AppColors.Red,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("${sig.confidence.pct}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                SignalMetric("Entry", "₹${"%.1f".format(sig.entryPrice.rupees)}")
                                SignalMetric("Target", "₹${"%.1f".format(sig.target.rupees)}", AppColors.Green)
                                SignalMetric("SL", "₹${"%.1f".format(sig.stopLoss.rupees)}", AppColors.Red)
                                SignalMetric("Qty", sig.quantity.toString())
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(sig.reason, fontSize = 11.sp, color = AppColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        
        item {
            Text("Recent Signals", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
        }
        items(signals.reversed()) { sig ->
            SignalRow(sig)
        }
        
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun ConnectionBadge() {
    val isConnected by remember { mutableStateOf(true) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isConnected) AppColors.GreenLight else AppColors.RedLight)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (isConnected) AppColors.Green else AppColors.Red))
        Spacer(Modifier.width(4.dp))
        Text(if (isConnected) "LIVE" else "OFF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isConnected) AppColors.Green else AppColors.Red)
    }
}

@Composable
fun MetricPill(text: String, textColor: Color, bgColor: Color) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun SignalMetric(label: String, value: String, valueColor: Color = AppColors.TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = AppColors.TextMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun SignalRow(sig: Signal) {
    val isBuy = sig.action == Actions.BUY
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.CardBg)
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (isBuy) AppColors.Green else AppColors.Red)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sig.symbol.value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Text(sig.reason, fontSize = 10.sp, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("₹${"%.1f".format(sig.entryPrice.rupees)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text("${sig.confidence.pct}% conf", fontSize = 10.sp, color = AppColors.TextMuted)
        }
    }
}

// ===== WATCH TAB =====
@Composable
fun WatchTab(vm: MarketViewModel) {
    val indices by vm.indexData.collectAsState()
    
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Text("Market Watch", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
        }
        items(indices) { itemData ->
            IndexCard(itemData.symbol, itemData.price, true)
            Spacer(Modifier.height(8.dp))
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun IndexCard(name: String, price: Double, isLive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Text(if (isLive) "Streaming" else "Delayed", fontSize = 11.sp, color = if (isLive) AppColors.Green else AppColors.TextMuted)
            }
            Text("₹${"%.2f".format(price)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (isLive) AppColors.Green else AppColors.TextMuted)
            )
        }
    }
}

// ===== TRADES TAB =====
@Composable
fun TradesTab(vm: TradeViewModel) {
    val balance by vm.balance.collectAsState()
    val totalPnL by vm.totalPnL.collectAsState()
    val openTrades by vm.openTrades.collectAsState()
    val history by vm.tradeHistory.collectAsState()
    
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Text("Virtual Wallet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Current Balance", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Text("₹${"%,d".format(balance)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total P&L", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Text(
                                "₹${"%,d".format(totalPnL)}", 
                                fontSize = 20.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (totalPnL >= 0) AppColors.Green else AppColors.Red
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (openTrades.isNotEmpty()) {
            item {
                Text("Open Positions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
            }
            items(openTrades) { trade ->
                VirtualTradeRow(trade)
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        
        item {
            Text("Trade History", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
        }
        
        items(history) { trade ->
            VirtualTradeRow(trade)
            Spacer(Modifier.height(8.dp))
        }
        
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun VirtualTradeRow(trade: com.vedx.vedxsuper.trade.VirtualTrade) {
    val isBuy = trade.action == "BUY"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (isBuy) AppColors.GreenLight else AppColors.RedLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isBuy) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = if (isBuy) AppColors.Green else AppColors.Red,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(trade.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text("${trade.action} • ${trade.quantity} Qty", fontSize = 11.sp, color = AppColors.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (trade.status == com.vedx.vedxsuper.trade.TradeStatus.OPEN) {
                    Text("OPEN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Blue)
                    Text("₹${"%.1f".format(trade.entryPrice)}", fontSize = 13.sp, color = AppColors.TextPrimary)
                } else {
                    Text(
                        if (trade.pnl >= 0) "+₹${"%,d".format(trade.pnl)}" else "-₹${"%,d".format(abs(trade.pnl))}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (trade.pnl >= 0) AppColors.Green else AppColors.Red
                    )
                    Text("Exit: ₹${"%.1f".format(trade.exitPrice)}", fontSize = 11.sp, color = AppColors.TextMuted)
                }
            }
        }
    }
}

// ===== BACKTEST TAB =====
@Composable
fun BacktestTab(vm: BacktestViewModel) {
    val result by vm.result.collectAsState()
    var running by remember { mutableStateOf(false) }
    
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("AI Strategy Backtest", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        
        Button(
            onClick = { 
                running = true
                vm.runBacktest("NIFTY", 5)
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue)
        ) {
            Text(if (running) "Analyzing Historical Data..." else "Run 7-ST Backtest", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (result.totalTrades > 0 || running) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Win Rate", "${result.winRate}%", AppColors.Green, Modifier.weight(1f))
                StatBox("Trades", "${result.totalTrades}", AppColors.Blue, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Max DD", "₹${result.maxDrawdown}", AppColors.Red, Modifier.weight(1f))
                StatBox("Sharpe", "%.2f".format(result.sharpeRatio), AppColors.Purple, Modifier.weight(1f))
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Backtest Results", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Net P&L: ₹${"%,d".format(result.totalPnL)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (result.totalPnL >= 0) AppColors.Green else AppColors.Red
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("Run backtest to see AI performance stats", color = AppColors.TextMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 11.sp, color = AppColors.TextMuted)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun EquityChart(data: List<Float>) {
    if (data.size < 2) return
    val minVal = data.minOrNull() ?: 0f
    val maxVal = data.maxOrNull() ?: 1f
    val range = (maxVal - minVal).coerceAtLeast(0.001f)
    
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        val stepX = w / (data.size - 1).coerceAtLeast(1)
        
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(AppColors.Border.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), 0.5f)
        }
        
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - ((v - minVal) / range * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        val fillPath = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(AppColors.Blue.copy(alpha = 0.15f), AppColors.Blue.copy(alpha = 0.01f)),
                startY = 0f,
                endY = h
            )
        )
        
        drawPath(path, color = AppColors.Blue, style = Stroke(width = 2.5f))
        
        val lastX = (data.size - 1) * stepX
        val lastY = h - ((data.last() - minVal) / range * h)
        drawCircle(AppColors.Blue, 5f, Offset(lastX, lastY))
        drawCircle(Color.White, 2f, Offset(lastX, lastY))
    }
}

// ===== SETTINGS TAB =====
@Composable
fun SettingsTab(vm: SettingsViewModel) {
    val context = LocalContext.current
    val app = VedxApp.instance
    val settings = app.settingsManager
    val darkMode by settings.darkMode.collectAsState()
    val notifEnabled by settings.notificationsEnabled.collectAsState()
    val timeframe by settings.analysisTimeframe.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        
        SettingCard {
            SettingItem("Strategy", "7-ST Match + Any Band Reversal", Icons.Default.Info)
            HorizontalDivider(color = AppColors.Border, thickness = 0.5.dp)
            
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dark Mode", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    Text(if (darkMode) "Enabled" else "Disabled", fontSize = 12.sp, color = AppColors.TextMuted)
                }
                Switch(darkMode, { settings.setDarkMode(it) })
            }
            
            HorizontalDivider(color = AppColors.Border, thickness = 0.5.dp)
            
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Trade Alerts", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    Text(if (notifEnabled) "Instant" else "Silent", fontSize = 12.sp, color = AppColors.TextMuted)
                }
                Switch(notifEnabled, { settings.setNotificationsEnabled(it) })
            }

            HorizontalDivider(color = AppColors.Border, thickness = 0.5.dp)
            SettingItem("Timeframe", timeframe, Icons.Default.Refresh)
        }
        
        Spacer(Modifier.height(12.dp))
        
        Text("Virtual Trading", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, modifier = Modifier.padding(vertical = 8.dp))
        SettingCard {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reset Everything", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    Text("Restore balance & clear history", fontSize = 12.sp, color = AppColors.TextMuted)
                }
                TextButton(onClick = { vm.resetAll() }) {
                    Text("RESET", color = AppColors.Blue, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.RedLight),
            border = BorderStroke(1.dp, AppColors.Red.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Danger Zone", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Red)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { VedxApp.instance.ultraNeuralCore.emergencyStop() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Red)
                ) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EMERGENCY STOP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        VedxApp.instance.secureTokenManager.clearSession()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AppColors.Border)
                ) {
                    Text("Logout", color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
        
        Spacer(Modifier.height(20.dp))
        Text("VedxSuper AI Pro v3.0", fontSize = 11.sp, color = AppColors.TextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            content()
        }
    }
}

@Composable
fun SettingItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = AppColors.TextMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
    }
}

// ===== VIEW MODEL (LEGACY - WILL BE REMOVED) =====
class VedxVM : ViewModel() {
    val app = VedxApp.instance
    // Kept to avoid breaking references temporarily
    fun hasValidSession(): Boolean = app.secureTokenManager.hasValidSession()
}

// ===== FAKE RESULT FOR UI DEMO =====
data class FakeResult(
    val winRate: String,
    val profitFactor: String,
    val maxDD: String,
    val sharpe: String,
    val trades: Int,
    val pnl: String,
    val equity: List<Float>
)

private fun generateFakeResult(): FakeResult {
    val eq = List(50) { i -> 100000f + i * 200f + kotlin.random.Random.nextFloat() * 1000f }
    return FakeResult("64.2", "1.85", "3.8", "2.14", 156, "+28,450", eq)
}
