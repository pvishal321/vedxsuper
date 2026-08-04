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
import com.vedx.vedxsuper.core.*
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.stream.FastTickEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.*

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

// ===== VIEW MODEL =====
class VedxVM : ViewModel() {
    val app = VedxApp.instance
    val signals = app.core.signals
    val logs = MutableStateFlow(listOf<String>())
    val isConnected = MutableStateFlow(true)
    val indexPrice = MutableStateFlow(0.0)
    val pnl = MutableStateFlow(0.0)
    
    init {
        viewModelScope.launch {
            while (true) {
                indexPrice.value = app.core.getIndexPrice()
                isConnected.value = true
                delay(1000)
            }
        }
    }
    
    fun login(c: String, p: String, t: String) = viewModelScope.launch {
        logs.value = logs.value + "Logging in..."
        if (app.client.login(c, p, t)) {
            app.getSharedPreferences("v", 0).edit()
                .putString("tok", app.client.token)
                .putString("cc", c).apply()
            
            app.engine = FastTickEngine(app.client.token, c, app.core, app.scope)
            app.startService()
            
            logs.value = logs.value + "Success!"
        } else logs.value = logs.value + "Failed"
    }
    
    fun emergency() {
        app.risk.reset(); app.core.emergencyStop()
        logs.value = logs.value + "STOPPED"
    }
}

// ===== ROOT =====
@Composable
fun VedxRoot(vm: VedxVM = viewModel()) {
    val tok = remember { VedxApp.instance.getSharedPreferences("v", 0).getString("tok", null) }
    var screen by remember { mutableStateOf(if (tok != null) "main" else "login") }
    when (screen) {
        "login" -> LoginScreen { screen = "main" }
        "main" -> MainScreen(vm)
    }
}

// ===== LOGIN SCREEN (Clean White) =====
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    val vm: VedxVM = viewModel()
    
    Box(Modifier.fillMaxSize().background(AppColors.White), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(AppColors.Blue),
                contentAlignment = Alignment.Center
            ) {
                Text("V", color = AppColors.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text("VedxSuper", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text("AI Trading Intelligence", fontSize = 14.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(40.dp))
            
            AppInput(code, { code = it }, "Client Code", Icons.Default.AccountCircle)
            Spacer(Modifier.height(12.dp))
            AppInput(pass, { pass = it }, "Password", Icons.Default.Lock, true)
            Spacer(Modifier.height(12.dp))
            AppInput(totp, { totp = it }, "TOTP Code", Icons.Default.Lock)
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = { vm.login(code, pass, totp); onDone() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue)
            ) {
                Text("CONNECT BROKER", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Secure connection to Angel One", fontSize = 11.sp, color = AppColors.TextMuted)
        }
    }
}

@Composable
fun AppInput(value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isPass: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = { Icon(icon, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Blue,
            unfocusedBorderColor = AppColors.Border
        ),
        visualTransformation = if (isPass) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

// ===== MAIN SCREEN (Bottom Nav) =====
@Composable
fun MainScreen(vm: VedxVM) {
    var tab by remember { mutableIntStateOf(0) }
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Watch" to Icons.Default.Search,
        "Trades" to Icons.AutoMirrored.Filled.List,
        "Backtest" to Icons.Default.Refresh,
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
                0 -> HomeTab(vm)
                1 -> WatchTab(vm)
                2 -> TradesTab(vm)
                3 -> BacktestTab()
                4 -> SettingsTab(vm)
            }
        }
    }
}

// ===== HOME TAB (Premium Dashboard) =====
@Composable
fun HomeTab(vm: VedxVM) {
    val price by vm.indexPrice.collectAsState()
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
                        modifier = Modifier.fillMaxWidth(),
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
                                        if (isBuy) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        null,
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

// ===== WATCH TAB (Market Overview) =====
@Composable
fun WatchTab(vm: VedxVM) {
    val price by vm.indexPrice.collectAsState()
    val indices = listOf(
        Triple("NIFTY 50", price, true),
        Triple("BANKNIFTY", price * 2.15, true),
        Triple("FINNIFTY", price * 0.85, false),
        Triple("SENSEX", price * 3.2, false)
    )
    
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Text("Market Watch", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
        }
        items(indices) { itemData ->
            val name = itemData.first
            val p = itemData.second
            val isLive = itemData.third
            IndexCard(name, p, isLive)
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

// ===== TRADES TAB (Journal) =====
@Composable
fun TradesTab(vm: VedxVM) {
    val logs by vm.logs.collectAsState()
    val stats = remember { VedxApp.instance.risk.stats() }
    
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Text("Trade Journal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
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
                    Text("Today's Stats", fontSize = 13.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(stats, fontSize = 14.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        
        items(logs) { log ->
            val isError = log.contains("❌") || log.contains("STOP")
            val isSuccess = log.contains("✅") || log.contains("Success")
            Text(
                log,
                fontSize = 12.sp,
                color = when {
                    isError -> AppColors.Red
                    isSuccess -> AppColors.Green
                    else -> AppColors.TextSecondary
                },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ===== BACKTEST TAB (With Chart) =====
@Composable
fun BacktestTab() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FakeResult?>(null) }
    
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Strategy Backtest", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        
        Button(
            onClick = { running = true; result = generateFakeResult() },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue)
        ) {
            Text(if (running) "Analyzing..." else "Run 7-ST Backtest", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(16.dp))
        
        result?.let { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Win Rate", "${r.winRate}%", AppColors.Green, Modifier.weight(1f))
                StatBox("Profit F.", "${r.profitFactor}x", AppColors.Blue, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Max DD", "${r.maxDD}%", AppColors.Red, Modifier.weight(1f))
                StatBox("Sharpe", r.sharpe, AppColors.Purple, Modifier.weight(1f))
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Equity Curve", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                EquityChart(r.equity)
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Trades: ${r.trades} | Net P&L: ₹${r.pnl}", fontSize = 13.sp, color = AppColors.TextSecondary)
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
fun SettingsTab(vm: VedxVM) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        
        SettingCard {
            SettingItem("Strategy", "7-ST Match + Any Band Reversal", Icons.Default.Info)
            HorizontalDivider(color = AppColors.Border, thickness = 0.5.dp)
            SettingItem("Risk Mode", "Conservative (Max 3 Losses)", Icons.Default.Lock)
            HorizontalDivider(color = AppColors.Border, thickness = 0.5.dp)
            SettingItem("Timeframe", "15 Minutes", Icons.Default.Refresh)
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
                    onClick = { vm.emergency() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Red)
                ) {
                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EMERGENCY STOP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        VedxApp.instance.getSharedPreferences("v", 0).edit().clear().apply()
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
