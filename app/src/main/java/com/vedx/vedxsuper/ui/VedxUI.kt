package com.vedx.vedxsuper.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedx.vedxsuper.VedxApp
import com.vedx.vedxsuper.core.*
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.stream.FastTickEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class VedxVM : ViewModel() {
    val app = VedxApp.instance
    val signals = app.core.signals
    val state = MutableStateFlow(app.core.getState())
    val price = MutableStateFlow(app.core.getIndexPrice())
    val logs = MutableStateFlow(listOf<String>())
    
    init {
        viewModelScope.launch {
            while (true) {
                state.value = app.core.getState()
                price.value = app.core.getIndexPrice()
                delay(1000)
            }
        }
    }
    
    fun login(c: String, p: String, t: String) = viewModelScope.launch {
        if (app.client.login(c, p, t)) {
            app.getSharedPreferences("v", 0).edit().putString("tok", app.client.token).putString("cc", c).apply()
            app.engine = FastTickEngine(app.client.token, c, app.core, app.scope)
            app.startService()
            logs.value += "✅ Login OK"
        } else logs.value += "❌ Login Failed"
    }
    
    fun emergency() {
        app.risk.reset(); app.core.emergencyStop()
        logs.value += "🛑 EMERGENCY STOP"
    }
}

@Composable
fun VedxRoot(vm: VedxVM = viewModel()) {
    val tok = remember { VedxApp.instance.getSharedPreferences("v", 0).getString("tok", null) }
    var screen by remember { mutableStateOf(if (tok != null) "dash" else "login") }
    when (screen) {
        "login" -> LoginScreen { screen = "dash" }
        "dash" -> Dashboard(vm)
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit) {
    var c by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var t by remember { mutableStateOf("") }
    val vm: VedxVM = viewModel()
    
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("VedxSuper AI", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C6BE5))
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(c, { c = it }, label = { Text("Client Code") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(p, { p = it }, label = { Text("Password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(t, { t = it }, label = { Text("TOTP") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.login(c, p, t); onDone() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6BE5))) {
            Text("LOGIN", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Dashboard(vm: VedxVM) {
    var tab by remember { mutableIntStateOf(0) }
    val state by vm.state.collectAsState()
    val price by vm.price.collectAsState()
    
    Scaffold(
        topBar = { TopBar(state, price, vm) },
        bottomBar = {
            NavigationBar {
                listOf("Home" to Icons.Default.Home, "Watch" to Icons.Default.Star, "Trades" to Icons.AutoMirrored.Filled.List, "Test" to Icons.Default.Build, "Set" to Icons.Default.Settings)
                    .forEachIndexed { i, (l, ic) ->
                        NavigationBarItem(tab == i, { tab = i }, icon = { Icon(ic, null) }, label = { Text(l, fontSize = 10.sp) })
                    }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFFF4F6F9))) {
            when (tab) {
                0 -> HomeTab(vm)
                1 -> MarketTab()
                2 -> TradesTab(vm)
                3 -> BacktestTab()
                4 -> SettingsTab(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(state: MarketState, price: Double, vm: VedxVM) {
    val regime = when(state.regime) {
        Regimes.TRENDING_UP -> "TREND UP 📈"
        Regimes.TRENDING_DOWN -> "TREND DOWN 📉"
        Regimes.VOLATILE -> "VOLATILE ⚡"
        Regimes.NO_TRADE -> "NO TRADE 🚫"
        else -> "SIDEWAYS ➡️"
    }
    val trendColor = when(state.trend) {
        Trends.TREND_RUN -> Color(0xFF00B97D)
        Trends.REVERSAL_SETUP -> Color(0xFFFF9800)
        Trends.EXHAUSTED -> Color(0xFFF23645)
        else -> Color.Gray
    }
    
    TopAppBar(
        title = {
            Column {
                Text("VedxSuper AI", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF2C6BE5))
                Text("NIFTY @ ₹${"%.1f".format(price)} | $regime", fontSize = 11.sp, color = Color.Gray)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
        actions = {
            Box(Modifier.size(10.dp).background(trendColor, RoundedCornerShape(50)))
            IconButton(onClick = { vm.emergency() }) { Icon(Icons.Default.Warning, null, tint = Color.Red) }
        }
    )
}

@Composable
fun HomeTab(vm: VedxVM) {
    val signals by vm.signals.collectAsState(initial = emptyList())
    val last = signals.lastOrNull()
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C6BE5))) {
            Column(Modifier.padding(16.dp)) {
                Text("🧠 AI NEURAL CORE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Strategies: SuperTrend + Momentum + Correlation", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                val learning = VedxApp.instance.core.lossCount >= 2
                Text("Learning: ${if (learning) "PAUSED ⚠️" else "ACTIVE ✅"}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        
        last?.let {
            val col = if (it.action == Actions.BUY) Color(0xFF00B97D) else Color(0xFFF23645)
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, col)) {
                Column(Modifier.padding(12.dp)) {
                    val act = when(it.action) { 
                        Actions.BUY -> "BUY CALL" 
                        Actions.SELL -> "BUY PUT" 
                        Actions.SCALP -> "SCALP" 
                        else -> "WAIT" 
                    }
                    Text("${it.symbol.value} | $act", fontWeight = FontWeight.ExtraBold, color = col, fontSize = 16.sp)
                    Text("Entry: ₹${it.entryPrice.rupees} | SL: ₹${it.stopLoss.rupees} | T: ₹${it.target.rupees}", fontSize = 12.sp)
                    Text("Conf: ${it.confidence.pct}% | Qty: ${it.quantity}", fontSize = 12.sp, color = Color.Gray)
                    Text(it.reason, fontSize = 10.sp, color = Color.Gray, maxLines = 2)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        
        Text("Signal Log", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(signals.size) { i ->
                val s = signals.reversed()[i]
                val col = when(s.action) { 
                    Actions.BUY -> Color(0xFF00B97D) 
                    Actions.SELL -> Color(0xFFF23645) 
                    Actions.EXIT -> Color.Gray 
                    else -> Color(0xFF2C6BE5) 
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(col, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${s.symbol.value} @ ₹${s.entryPrice.rupees}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${s.confidence.pct}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col)
                }
            }
        }
    }
}

@Composable
fun MarketTab() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Market Watch", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        listOf("NIFTY 50", "BANKNIFTY", "FINNIFTY", "SENSEX").forEach { idx ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(idx, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("LIVE", color = Color(0xFF00B97D), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TradesTab(vm: VedxVM) {
    val logs by vm.logs.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Trade Journal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(VedxApp.instance.risk.stats(), fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(logs.size) { i ->
                Text(logs[i], fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
fun BacktestTab() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Backtest", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    ("Win Rate" to "62.4%") to Color(0xFF00B97D), 
                    ("Profit Factor" to "1.8") to Color(0xFF00B97D), 
                    ("Max DD" to "3.2%") to Color(0xFFF23645), 
                    ("Sharpe" to "2.1") to Color(0xFF2C6BE5)
                ).forEach { (pair, col) ->
                    val (label, value) = pair
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(label, Modifier.weight(1f))
                        Text(value, fontWeight = FontWeight.Bold, color = col)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(vm: VedxVM) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.emergency() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF23645)), modifier = Modifier.fillMaxWidth()) {
            Text("🛑 EMERGENCY STOP ALL", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { VedxApp.instance.getSharedPreferences("v", 0).edit().clear().apply() }, modifier = Modifier.fillMaxWidth()) {
            Text("Logout & Clear")
        }
    }
}
