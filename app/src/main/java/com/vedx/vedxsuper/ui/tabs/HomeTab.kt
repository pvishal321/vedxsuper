package com.vedx.vedxsuper.ui.tabs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.ui.DashboardViewModel
import com.vedx.vedxsuper.ui.MarketViewModel
import com.vedx.vedxsuper.ui.SettingsViewModel
import com.vedx.vedxsuper.ui.components.*
import com.vedx.vedxsuper.strategy.engine.AgentStatus
import com.vedx.vedxsuper.strategy.engine.AgentReport
import java.util.Locale

@Composable
fun HomeTab(
    viewModel: MarketViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    onChartClick: (String) -> Unit,
    onTimeframeChange: (Int) -> Unit
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val displaySymbols = listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // 2. Index Selector & Timeframe
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val selectedIdx = displaySymbols.indexOf(uiState.selectedIndex).coerceAtLeast(0)
                ScrollableTabRow(
                    selectedTabIndex = selectedIdx,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF2C6BE5),
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedIdx]),
                            color = Color(0xFF2C6BE5)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    displaySymbols.forEach { symbol ->
                        Tab(
                            selected = uiState.selectedIndex == symbol,
                            onClick = { dashboardViewModel.selectIndex(symbol) },
                            text = { Text(symbol, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                var showTimeframeMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { showTimeframeMenu = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text = settingsState.analysisTimeframe.toString() + "M", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C6BE5))
                    }
                    DropdownMenu(expanded = showTimeframeMenu, onDismissRequest = { showTimeframeMenu = false }) {
                        listOf(1, 3, 5, 10, 15, 30, 60).forEach { min ->
                            DropdownMenuItem(
                                text = { Text(text = min.toString() + "m Timeframe", fontWeight = if (settingsState.analysisTimeframe == min) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    settingsViewModel.setAnalysisTimeframe(min)
                                    onTimeframeChange(min)
                                    showTimeframeMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. INDEX NEURAL MATRIX
        item {
            SectionHeader("📊 INDEX NEURAL MATRIX", uiState.selectedIndex, Color(0xFF2C6BE5))

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, Color(0xFFE5E9F2)),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Top Section: Gauge and Quick Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConvergenceGauge(
                            score = uiState.zoneMatchScore,
                            modifier = Modifier.size(100.dp)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text("CURRENT INDEX PRICE", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Black)
                            Text("₹${String.format(java.util.Locale.US, "%,.2f", uiState.indexPrice)}",
                                fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF1A1C1E))

                            uiState.indexStResult?.master?.let { master ->
                                Surface(
                                    color = (if (master.trend == 1) Color(0xFF00B97D) else Color(0xFFF23645)).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(top = 6.dp),
                                    border = BorderStroke(1.dp, (if (master.trend == 1) Color(0xFF00B97D) else Color(0xFFF23645)).copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = if (master.trend == 1) "BULLISH CONVERGENCE" else "BEARISH CONVERGENCE",
                                        color = if (master.trend == 1) Color(0xFF00B97D) else Color(0xFFF23645),
                                        fontSize = 10.sp, fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFFF0F4FF), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(20.dp))

                    // The Matrix (Table)
                    IntelligenceMindTable(reports = uiState.indexAgentReports)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Heatmap at the bottom of the card
                    uiState.indexStResult?.let { st ->
                        InstitutionalZoneHeatmap(currentPrice = uiState.indexPrice, stResult = st)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4. OPTION SELECTION & NEURAL MATRIX
        item {
            val currentOptionSymbol = uiState.recommendedTrade?.optionSymbol ?: "Select Option"
            SectionHeader("💎 OPTION NEURAL MATRIX", currentOptionSymbol, Color(0xFF673AB7))

            // Manual Option Input/Search can be complex, for now we use the latest signal or a placeholder
            if (uiState.optionAgentReports.isNotEmpty()) {
                IntelligenceMindCard(reports = uiState.optionAgentReports, onChartClick = {
                    uiState.recommendedTrade?.optionSymbol?.let { onChartClick(it) }
                })
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Compact Option Chain
                uiState.recommendedTrade?.let { signal ->
                    CompactOptionChain(
                        symbol = signal.optionSymbol,
                        ltp = uiState.optionPrice,
                        delta = signal.delta,
                        theta = signal.theta,
                        oiChange = signal.oiChange,
                        onClick = { onChartClick(signal.optionSymbol) }
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FB)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0E3E7))
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Waiting for Option Signal or Selection", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 5. TRADE READY CARD
        uiState.recommendedTrade?.let { signal ->
            item {
                Text("🎯 AI ACTION SIGNAL", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00B97D), modifier = Modifier.padding(bottom = 12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, Color(0xFF00B97D)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Surface(color = Color(0xFF1A1C1E), shape = RoundedCornerShape(4.dp)) {
                                    Text(text = signal.optionSymbol, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                                Text(text = "CONVERGENCE SCORE: " + uiState.zoneMatchScore + "%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C6BE5), modifier = Modifier.padding(top = 4.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = signal.type, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (signal.type == "BUY") Color(0xFF00B97D) else Color(0xFFF23645))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F4FF))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PriceTag("ENTRY", "₹" + String.format(Locale.US, "%.2f", signal.price), Color(0xFF2C6BE5), Modifier.weight(1f))
                            PriceTag("SL", "₹" + String.format(Locale.US, "%.2f", signal.stopLoss), Color(0xFFF23645), Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Surface(color = Color(0xFFF8F9FB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = "🧠 LOGIC: " + signal.reason.split("|").first().trim(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, modifier = Modifier.padding(10.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.enterTrade(signal.optionSymbol, if (signal.type == "BUY") "BUY" else "SELL", signal.price, signal.stopLoss, signal.target) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1C1E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "EXECUTE AI DECISION", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }

        // 6. Intelligence Activity Feed
        item {
            Text("🧠 LIVE INTELLIGENCE ACTIVITY", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FB)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E3E7))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (uiState.intelligenceFeed.isEmpty()) {
                        Text("Monitoring market signals...", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        uiState.intelligenceFeed.reversed().take(5).forEach { log ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                IntelligenceIcon(text = log)
                                Text(text = log, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1E))
                            }
                        }
                    }
                }
            }
        }

        // 7. System Health
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFF0F4FF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("SYSTEM HEALTH", color = Color(0xFF1A1C1E), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        MiniMetric(uiState.streamMetrics.latencyMs.toString() + "ms", Color(0xFF2C6BE5))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusRow("Broker Session", uiState.systemHealth.isSessionValid)
                    StatusRow("SmartStream: ${uiState.systemHealth.streamStatusName}", uiState.systemHealth.isSmartStreamLive)
                    StatusRow("Neural AI Engine", uiState.systemHealth.isAiEngineRunning)
                    StatusRow("Market Status (${if (uiState.systemHealth.isMarketOpen) "Open" else "Closed"})", uiState.systemHealth.isMarketOpen)

                    if (uiState.syncStatus.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = Color(0xFFFFF9C4), shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = "⏳ SYNC: ${uiState.syncStatus}", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF57F17), modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, symbol: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF1A1C1E),
            letterSpacing = 0.5.sp
        )
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
        ) {
            Text(
                text = symbol,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun IntelligenceMindTable(reports: List<AgentReport>) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("BAND", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("TREND", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.8f))
            Text("INTENSITY", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("DISTANCE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("ETA", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
        }

        reports.forEach { agent ->
            val isActive = agent.status != AgentStatus.WAITING && agent.status != AgentStatus.FAR_AWAY

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(10.dp).background(getStatusColor(agent.status), CircleShape))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("ST ${agent.multiplier}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (isActive) Color(0xFF1A1C1E) else Color.Gray)
                }

                Box(modifier = Modifier.weight(0.8f)) {
                    Icon(
                        imageVector = if (agent.trend == 1) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (agent.trend == 1) Color(0xFF00B97D) else Color(0xFFF23645),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    LinearProgressIndicator(
                        progress = { agent.intensityScore / 100f },
                        modifier = Modifier.width(50.dp).height(6.dp).clip(CircleShape),
                        color = getStatusColor(agent.status),
                        trackColor = Color(0xFFF0F4FF)
                    )
                }

                Text(
                    text = "${agent.distancePoints.toInt()} pts",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = if (agent.distancePoints < 15) Color(0xFF00B97D) else Color(0xFF1A1C1E)
                )

                val etaText = agent.etaMinutes?.let { if (it < 60) "${it}m" else "---" } ?: "---"
                Text(
                    text = etaText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(0.7f),
                    textAlign = TextAlign.End,
                    color = if ((agent.etaMinutes ?: 99) < 5) Color(0xFF00B97D) else Color(0xFF1A1C1E)
                )
            }
            if (reports.indexOf(agent) < reports.size - 1) {
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F4FF))
            }
        }
    }
}

@Composable
fun IntelligenceMindCard(reports: List<AgentReport>, onChartClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onChartClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF0F4FF)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BAND", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("TREND", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                Text("INTENSITY", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("DISTANCE", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("ETA", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
            }

            reports.forEach { agent ->
                val isActive = agent.status != AgentStatus.WAITING && agent.status != AgentStatus.FAR_AWAY

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                    label = "alpha"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) getStatusColor(agent.status).copy(alpha = 0.1f) else Color.Transparent)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .then(if (isActive) Modifier.alpha(alpha) else Modifier)
                                .background(getStatusColor(agent.status), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ST ${agent.multiplier}", fontSize = 12.sp, fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold)
                    }
                    
                    Box(modifier = Modifier.weight(0.8f)) {
                        Icon(
                            imageVector = if (agent.trend == 1) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (agent.trend == 1) Color(0xFF00B97D) else Color(0xFFF23645),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Intensity Meter
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        LinearProgressIndicator(
                            progress = { agent.intensityScore / 100f },
                            modifier = Modifier.width(40.dp).height(4.dp),
                            color = getStatusColor(agent.status),
                            trackColor = Color(0xFFF0F4FF)
                        )
                    }

                    Text(
                        text = "${agent.distancePoints.toInt()} pts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        color = if (agent.distancePoints < 10) Color(0xFF00B97D) else Color.Gray
                    )

                    val etaText = agent.etaMinutes?.let { if (it < 60) "${it}m" else "---" } ?: "---"
                    Text(
                        text = etaText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(0.7f),
                        textAlign = TextAlign.End,
                        color = if ((agent.etaMinutes ?: 99) < 5) Color(0xFF00B97D) else Color(0xFF1A1C1E)
                    )
                }
                if (reports.indexOf(agent) < reports.size - 1) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F4FF))
                }
            }
        }
    }
}

fun getStatusColor(status: AgentStatus): Color = when(status) {
    AgentStatus.TOUCHING -> Color(0xFF00B97D)
    AgentStatus.APPROACHING -> Color(0xFF2C6BE5)
    AgentStatus.REJECTING -> Color(0xFF673AB7)
    AgentStatus.BREAKING -> Color(0xFFF23645)
    else -> Color.LightGray
}
