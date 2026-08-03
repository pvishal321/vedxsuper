package com.vedx.vedxsuper.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.model.market.IndexData
import com.vedx.vedxsuper.strategy.signal.StrategyState
import com.vedx.vedxsuper.ui.MarketViewModel
import com.vedx.vedxsuper.ui.components.*
import java.util.Locale
import kotlin.math.abs

@Composable
fun MarketTab(
    indexData: Map<String, IndexData>, 
    viewModel: MarketViewModel,
    onIndexClick: (String) -> Unit,
    onChartClick: (String) -> Unit
) {
    val activeIndices by viewModel.activeIndices.collectAsState()
    val displaySymbols = if (activeIndices.isEmpty()) listOf("NIFTY", "BANKNIFTY") else activeIndices.toList()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedIndexForOptions by remember { mutableStateOf(displaySymbols.first()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedTabIndex == 1) {
            // Index Selector for Options Matrix
            ScrollableTabRow(
                selectedTabIndex = displaySymbols.indexOf(selectedIndexForOptions).coerceAtLeast(0),
                containerColor = Color.White,
                contentColor = Color(0xFF2C6BE5),
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[displaySymbols.indexOf(selectedIndexForOptions).coerceAtLeast(0)]),
                        color = Color(0xFF2C6BE5)
                    )
                }
            ) {
                displaySymbols.forEach { symbol ->
                    Tab(
                        selected = selectedIndexForOptions == symbol,
                        onClick = { selectedIndexForOptions = symbol },
                        text = { Text(symbol, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.White,
            contentColor = Color(0xFF2C6BE5),
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFF2C6BE5)
                    )
                }
            },
            divider = { HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E3E7)) }
        ) {
            Tab(
                selected = selectedTabIndex == 0, 
                onClick = { selectedTabIndex = 0 }, 
                text = { Text("Strategy Watchlist", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTabIndex == 1, 
                onClick = { selectedTabIndex = 1 }, 
                text = { Text("Options Matrix", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (selectedTabIndex == 0) {
                items(displaySymbols) { symbol ->
                    val data = indexData[symbol] ?: return@items
                    val token = viewModel.resolveToken(symbol)
                    val strategyState by viewModel.getStrategyState(token).collectAsState(StrategyState())
                    val multiTrendState by viewModel.getMultiTrendState(symbol).collectAsState(null)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable {
                                selectedIndexForOptions = symbol
                                onIndexClick(symbol)
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header: Symbol and Price
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(symbol, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF1A1C1E))
                                    Surface(
                                        color = Color(0xFFF0F4FF),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("INSTITUTIONAL GRADE", color = Color(0xFF2C6BE5), fontSize = 8.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "₹" + String.format(Locale.US, "%,.2f", data.lastTradedPrice),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = if (data.change >= 0) Color(0xFF00B97D) else Color(0xFFF23645)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "H: ₹" + String.format(Locale.US, "%,.2f", data.high) + " ",
                                            fontSize = 9.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "L: ₹" + String.format(Locale.US, "%,.2f", data.low),
                                            fontSize = 9.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        (if (data.change >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", data.changePercent),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (data.change >= 0) Color(0xFF00B97D) else Color(0xFFF23645)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color(0xFFE0E3E7))

                                // Strategy Details Section
                            val strength = viewModel.getIndexStrength(symbol)
                            val trendState = strategyState.ribbons[2] ?: 0
                            val trendText = when (trendState) {
                                1 -> "BULLISH"
                                -1 -> "BEARISH"
                                else -> "NEUTRAL"
                            }
                            val trendColor = when (trendState) {
                                1 -> Color(0xFF00B97D)
                                -1 -> Color(0xFFF23645)
                                else -> Color.Gray
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DetailBlock("CURRENT TREND", trendText, trendColor)
                                DetailBlock("MOMENTUM", String.format(Locale.US, "%.1f", strength.momentum), if (strength.momentum > 50) Color(0xFF00B97D) else Color(0xFFF23645))
                                DetailBlock("VELOCITY", String.format(Locale.US, "%.2f", strength.velocity), if (strength.velocity >= 0) Color(0xFF00B97D) else Color(0xFFF23645))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DetailBlock("ACTIVE BAND", "ST " + (multiTrendState?.bandInfo?.currentBand?.toString() ?: "-"), Color.DarkGray)
                                DetailBlock("ZONE STATUS", if (multiTrendState?.bandInfo?.isInZone == true) "IN ZONE" else "STABLE", if (multiTrendState?.bandInfo?.isInZone == true) Color(0xFF2C6BE5) else Color.Gray)
                                DetailBlock("STRENGTH", String.format(Locale.US, "%.1f%%", strength.trendStrength), Color(0xFF2C6BE5))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Detailed Multi SuperTrend Visualization
                            Text("SUPER-TREND LEVELS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))

                            // 2-Column Grid for ST Levels
                            Column {
                                listOf(2, 3, 4, 5, 6, 7, 8).chunked(2).forEach { rowMultipliers ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowMultipliers.forEach { m ->
                                            val trend = strategyState.ribbons[m] ?: 0
                                            val price = strategyState.ribbonPrices[m] ?: 0.0
                                            Box(modifier = Modifier.weight(1f)) {
                                                // Minimalistic ST level view for grid
                                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onChartClick(symbol) }) {
                                                    Text("ST $m", fontSize = 7.sp, color = Color.Gray)
                                                    Text(
                                                        "₹${String.format(Locale.US, "%.1f", price)}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (trend == 1) Color(0xFF00B97D) else Color(0xFFF23645)
                                                    )
                                                }
                                            }
                                        }
                                        if (rowMultipliers.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Visual Target & SL
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val currentTrend = if (multiTrendState?.bandInfo?.role == "SUPPORT") "UP" else if (multiTrendState?.bandInfo?.role == "RESISTANCE") "DOWN" else "NEUTRAL"
                                val ltp = data.lastTradedPrice

                                val stopLoss = if (currentTrend == "UP") strategyState.ribbonPrices[2] ?: (ltp * 0.99) else strategyState.ribbonPrices[2] ?: (ltp * 1.01)
                                val target = if (currentTrend == "UP") strategyState.ribbonPrices[8] ?: (ltp * 1.02) else strategyState.ribbonPrices[8] ?: (ltp * 0.98)

                                Column(modifier = Modifier.weight(1f).background(Color(0xFFFFF1F1), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                    Text("STOP LOSS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF23645))
                                    Text("₹" + String.format(Locale.US, "%.1f", stopLoss), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF23645))
                                }
                                Column(modifier = Modifier.weight(1f).background(Color(0xFFF1FFF4), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                    Text("TARGET", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B97D))
                                    Text("₹" + String.format(Locale.US, "%.1f", target), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00B97D))
                                }
                            }

                            // Execution Signal
                            val signal = strategyState.lastSignal
                            if (signal != null && signal.side != "EXIT") {
                                Spacer(modifier = Modifier.height(12.dp))

                                val currentTrend = if (multiTrendState?.bandInfo?.role == "SUPPORT") "UP" else if (multiTrendState?.bandInfo?.role == "RESISTANCE") "DOWN" else "NEUTRAL"
                                val ltp = data.lastTradedPrice
                                val stopLoss = if (currentTrend == "UP") strategyState.ribbonPrices[2] ?: (ltp * 0.99) else strategyState.ribbonPrices[2] ?: (ltp * 1.01)
                                val target = if (currentTrend == "UP") strategyState.ribbonPrices[8] ?: (ltp * 1.02) else strategyState.ribbonPrices[8] ?: (ltp * 0.98)

                                Surface(
                                    color = (if (signal.side == "BUY_CALL") Color(0xFF00B97D) else Color(0xFFF23645)).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (signal.side == "BUY_CALL") Color(0xFF00B97D) else Color(0xFFF23645)),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        viewModel.enterTrade(
                                            symbol = symbol,
                                            type = if (signal.side == "BUY_CALL") "BUY" else "SELL",
                                            price = data.lastTradedPrice,
                                            sl = stopLoss,
                                            target = target
                                        )
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("EXECUTE SIGNAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (signal.side == "BUY_CALL") Color(0xFF00B97D) else Color(0xFFF23645))
                                            Text(signal.side.replace("BUY_", "") + " @ ₹" + String.format(Locale.US, "%.2f", signal.strike), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("CLICK TO TRADE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (signal.side == "BUY_CALL") Color(0xFF00B97D) else Color(0xFFF23645))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Options Matrix View
                val options = indexData.filter {
                    it.key.startsWith(selectedIndexForOptions) && (it.key.contains("CE") || it.key.contains("PE"))
                }
                if (options.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("No Options Data Available. Please select an index.", color = Color.Gray)
                        }
                    }
                } else {
                    items(options.toList()) { (symbol, data) ->
                        val token = viewModel.resolveToken(symbol)
                        val strategyState by viewModel.getStrategyState(token).collectAsState(StrategyState())
                                val optStrength = viewModel.getOptionStrength(symbol)
                                val metrics = viewModel.getOptionMetrics(symbol)

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                                        .clickable { onChartClick(symbol) },
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF0F4FF))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(symbol, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1A1C1E))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(color = Color(0xFF00B97D).copy(alpha = 0.1f), shape = RoundedCornerShape(2.dp)) {
                                                        Text("NEURAL ACTIVE", color = Color(0xFF00B97D), fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                    }
                                                }
                                                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    listOf(2, 3, 4, 5, 6, 7, 8).forEach { multiplier ->
                                                        val trend = strategyState.ribbons[multiplier] ?: 0
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .background(
                                                                    if (trend == 1) Color(0xFF00B97D) else if (trend == -1) Color(0xFFF23645) else Color(0xFFE0E3E7),
                                                                    CircleShape
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "₹" + String.format(Locale.US, "%.2f", data.lastTradedPrice),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 18.sp,
                                                    color = if (data.change >= 0) Color(0xFF00B97D) else Color(0xFFF23645)
                                                )
                                                Text(
                                                    (if (data.change >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", data.changePercent),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (data.change >= 0) Color(0xFF00B97D) else Color(0xFFF23645)
                                                )
                                            }
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color(0xFFF0F4FF))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        OptionMetric("STRENGTH", String.format(Locale.US, "%.0f%%", optStrength.trendStrength), Color(0xFF2C6BE5))
                                        OptionMetric("OI CHG", String.format(Locale.US, "%,d", metrics?.oiChange ?: 0), if ((metrics?.oiChange ?: 0) >= 0) Color(0xFF00B97D) else Color(0xFFF23645))
                                        OptionMetric("EXPANSION", String.format(Locale.US, "%.1f", metrics?.premiumExpansion ?: 0.0), Color(0xFF673AB7))
                                        OptionMetric("LIQUIDITY", if (metrics != null) String.format(Locale.US, "%.2f", metrics.lastAsk - metrics.lastBid) else "0.0", Color(0xFF2C6BE5))
                                    }

                                        Spacer(modifier = Modifier.height(12.dp))

                                Text("SUPER-TREND LEVELS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))

                                // Show all 7 SuperTrend levels for Options
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(2, 3, 4, 5, 6, 7, 8).chunked(4).forEach { row ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            row.forEach { m ->
                                                val stPrice = strategyState.ribbonPrices[m] ?: 0.0
                                                val stTrend = strategyState.ribbons[m] ?: 0
                                                val isTouching = abs(data.lastTradedPrice - stPrice) < (stPrice * 0.001) // 0.1% proximity

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            if (isTouching) Color(0xFF2C6BE5).copy(alpha = 0.1f)
                                                            else Color(0xFFF8F9FB),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .border(
                                                            if (isTouching) 1.dp else 0.dp,
                                                            if (isTouching) Color(0xFF2C6BE5) else Color.Transparent,
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .clickable { onChartClick(symbol) }
                                                        .padding(4.dp)
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                                        Text("ST" + m, fontSize = 7.sp, color = Color.Gray)
                                                        Text(
                                                            "₹" + String.format(Locale.US, "%.1f", stPrice),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (stTrend == 1) Color(0xFF00B97D) else Color(0xFFF23645)
                                                        )
                                                        if (isTouching) {
                                                            Text("TOUCH", fontSize = 6.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C6BE5))
                                                        }
                                                    }
                                                }
                                            }
                                            if (row.size < 4) Spacer(modifier = Modifier.weight(4f - row.size))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            viewModel.enterTrade(
                                                symbol = symbol,
                                                type = "BUY",
                                                price = data.lastTradedPrice,
                                                sl = strategyState.ribbonPrices[2] ?: (data.lastTradedPrice * 0.9),
                                                target = strategyState.ribbonPrices[8] ?: (data.lastTradedPrice * 1.1)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1C1E)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("BUY ₹" + String.format(Locale.US, "%,.2f", data.lastTradedPrice), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
