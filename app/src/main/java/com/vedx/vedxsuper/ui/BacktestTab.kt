package com.vedx.vedxsuper.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedx.vedxsuper.VedxApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestTab(vm: BacktestVM = viewModel()) {
    val res by vm.result.collectAsState()
    val load by vm.loading.collectAsState()
    val logs by vm.logs.collectAsState()
    var token by remember { mutableStateOf(VedxApp.instance.client.token) }
    
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("🧪 AI Backtest Engine", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF2C6BE5))
        Spacer(Modifier.height(12.dp))
        
        var source by remember { mutableStateOf(0) }
        Row {
            listOf("Angel One", "NSE Bhavcopy", "Custom CSV").forEachIndexed { i, label ->
                FilterChip(
                    selected = source == i,
                    onClick = { source = i },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        if (source == 0) {
            OutlinedTextField(token, { token = it }, label = { Text("Angel Token") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        
        Button(
            onClick = {
                when (source) {
                    0 -> vm.runAngelBacktest(token)
                    1 -> vm.runNseBacktest()
                    2 -> vm.runCustom("")
                }
            },
            enabled = !load,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6BE5))
        ) {
            Text(if (load) "Running AI Backtest..." else "🚀 RUN BACKTEST", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(16.dp))
        
        res?.let { r ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                Column(Modifier.padding(16.dp)) {
                    Text("📊 RESULTS", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    ResultRow("Total Trades", r.totalTrades.toString(), Color.White)
                    ResultRow("Win Rate", "%.1f%%".format(r.winRate), if (r.winRate > 55) Color(0xFF00FF88) else Color(0xFFFF4444))
                    ResultRow("Profit Factor", "%.2f".format(r.profitFactor), if (r.profitFactor > 1.5) Color(0xFF00FF88) else Color(0xFFFFAA00))
                    ResultRow("Max Drawdown", "%.1f%%".format(r.maxDrawdownPct), Color(0xFFFF4444))
                    ResultRow("Net P&L", "₹%,.0f".format(r.netPnl), if (r.netPnl > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    ResultRow("Avg Win", "₹%,.0f".format(r.avgWin), Color(0xFF00FF88))
                    ResultRow("Avg Loss", "₹%,.0f".format(r.avgLoss), Color(0xFFFF4444))
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("📈 Equity Curve", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            EquityChartPlaceholder(r.equityCurve)
            
            Spacer(Modifier.height(16.dp))
            Text("📝 Trade Log", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            r.tradeLog.takeLast(10).forEach { trade ->
                val col = when (trade.status) {
                    "WIN" -> Color(0xFF00FF88)
                    "LOSS" -> Color(0xFFFF4444)
                    else -> Color.Gray
                }
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(trade.status, color = col, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(50.dp))
                        Text(trade.symbol, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                        Text("₹%,.0f".format(trade.pnl), color = if (trade.pnl > 0) Color(0xFF00FF88) else Color(0xFFFF4444), fontSize = 11.sp, modifier = Modifier.width(70.dp))
                        Text(trade.reason, color = Color.Gray, fontSize = 9.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Logs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
        logs.forEach { Text(it, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 1.dp)) }
    }
}

@Composable
fun ResultRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 12.sp)
    }
}

@Composable
fun EquityChartPlaceholder(curve: List<Pair<Long, Double>>) {
    Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF1A1A2E), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text("Equity Chart Placeholder (${curve.size} points)", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.align(Alignment.Center))
    }
}
