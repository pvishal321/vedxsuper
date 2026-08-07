package com.vedx.vedxsuper.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.ui.IndexData

@Composable
fun InstitutionalDashboard(
    indexData: List<IndexData>,
    openPositions: List<OpenPosition>,
    marginInfo: MarginInfo,
    signals: List<Signal>,
    marketBreadth: MarketBreadth?,
    pcrData: PCRData?,
    vix: Double,
    strategyState: String,
    onIndexClick: (String) -> Unit,
    onEmergencyStop: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ===== STRATEGY STATUS BAR =====
        item {
            StrategyStatusBar(strategyState, onEmergencyStop)
        }

        // ===== MARGIN & P&L SUMMARY =====
        item {
            MarginSummaryCard(marginInfo)
        }

        // ===== MARKET BREADTH + PCR + VIX =====
        item {
            MarketContextRow(marketBreadth, pcrData, vix)
        }

        // ===== OPEN POSITIONS (MTM) =====
        if (openPositions.isNotEmpty()) {
            item {
                OpenPositionsHeader(openPositions)
            }
            items(openPositions) { pos ->
                OpenPositionCard(pos)
            }
        }

        // ===== INDICES GRID =====
        item {
            Text("Market Indices", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
        }
        items(indexData.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { idx ->
                    IndexGridCard(idx, modifier = Modifier.weight(1f), onClick = { onIndexClick(idx.symbol) })
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // ===== ACTIVE SIGNALS =====
        item {
            Text("Active Signals", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(8.dp))
        }
        items(signals.takeLast(5)) { sig ->
            SignalCardV3(sig)
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StrategyStatusBar(state: String, onEmergency: () -> Unit) {
    val isActive = !state.contains("STOP") && !state.contains("BREAKER")
    val color = when {
        state.contains("CIRCUIT") -> Color(0xFFDC2626)
        state.contains("PAUSE") -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(state, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
            }
            if (isActive) {
                TextButton(onClick = onEmergency) {
                    Text("STOP", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MarginSummaryCard(margin: MarginInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarginItem("Fund", "₹${"%,.0f".format(margin.totalFund)}", Color.White)
                MarginItem("Used", "₹${"%,.0f".format(margin.usedMargin)}", Color(0xFFF59E0B))
                MarginItem("Available", "₹${"%,.0f".format(margin.availableMargin)}", Color(0xFF10B981))
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarginItem("Day P&L", "₹${"%,.0f".format(margin.dayPnL)}", 
                    if (margin.dayPnL >= 0) Color(0xFF10B981) else Color(0xFFEF4444))
                MarginItem("Unrealized", "₹${"%,.0f".format(margin.unrealizedPnL)}",
                    if (margin.unrealizedPnL >= 0) Color(0xFF10B981) else Color(0xFFEF4444))
                MarginItem("Exposure", "₹${"%,.0f".format(margin.openPositionsMargin)}", Color(0xFF60A5FA))
            }
        }
    }
}

@Composable
private fun MarginItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MarketContextRow(breadth: MarketBreadth?, pcr: PCRData?, vix: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Market Breadth
        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("Breadth", fontSize = 11.sp, color = Color.Gray)
                breadth?.let {
                    Text("${it.advances}/${it.declines}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("A/D: ${"%.2f".format(it.advanceDeclineRatio)}", fontSize = 10.sp, color = Color.Gray)
                } ?: Text("--", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        // PCR
        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("PCR", fontSize = 11.sp, color = Color.Gray)
                pcr?.let {
                    Text("${"%.2f".format(it.pcr)}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = when {
                            it.pcr > 1.2 -> Color(0xFF10B981)
                            it.pcr < 0.8 -> Color(0xFFEF4444)
                            else -> Color(0xFFF59E0B)
                        })
                    Text("OI C:${it.callOi/1000}K P:${it.putOi/1000}K", fontSize = 9.sp, color = Color.Gray)
                } ?: Text("--", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        // VIX
        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("VIX", fontSize = 11.sp, color = Color.Gray)
                Text("${"%.2f".format(vix)}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = when {
                        vix > 25 -> Color(0xFFEF4444)
                        vix > 18 -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    })
                Text(if (vix > 20) "High Fear" else "Normal", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun OpenPositionsHeader(positions: List<OpenPosition>) {
    val totalMtm = positions.sumOf { it.mtm }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Open Positions (${positions.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "MTM: ₹${"%,.0f".format(totalMtm)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (totalMtm >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
        )
    }
}

@Composable
private fun OpenPositionCard(pos: OpenPosition) {
    val isProfit = pos.mtm >= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isProfit) Color(0xFF10B981).copy(alpha = 0.05f) else Color(0xFFEF4444).copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, if (isProfit) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(if (pos.optionType == OptionType.CE) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(pos.optionType.name, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = if (pos.optionType == OptionType.CE) Color(0xFF10B981) else Color(0xFFEF4444))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(pos.symbol.value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${pos.lots} lots | Qty: ${pos.quantity}", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹${"%,.0f".format(pos.mtm)}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (isProfit) Color(0xFF10B981) else Color(0xFFEF4444))
                    Text("${"%.1f".format(pos.mtmPct)}%", fontSize = 11.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PositionMetric("Entry", "₹${"%.1f".format(pos.entryPrice.rupees)}")
                PositionMetric("LTP", "₹${"%.1f".format(pos.currentPrice.rupees)}")
                PositionMetric("Target", "₹${"%.1f".format(pos.target.rupees)}", Color(0xFF10B981))
                PositionMetric("SL", "₹${"%.1f".format(pos.stopLoss.rupees)}", Color(0xFFEF4444))
                pos.trailingSl?.let { PositionMetric("Trail", "₹${"%.1f".format(it.rupees)}", Color(0xFF3B82F6)) }
            }
            // Greeks
            pos.greeks?.let { g ->
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GreekPill("Δ", "${"%.2f".format(g.delta)}")
                    GreekPill("Γ", "${"%.3f".format(g.gamma)}")
                    GreekPill("Θ", "${"%.1f".format(g.theta)}")
                    GreekPill("V", "${"%.1f".format(g.vega)}")
                    GreekPill("IV", "${"%.1f".format(g.iv)}%")
                }
            }
        }
    }
}

@Composable
private fun PositionMetric(label: String, value: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = Color.Gray)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun GreekPill(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(value, fontSize = 9.sp, color = Color.DarkGray)
    }
}

@Composable
private fun IndexGridCard(idx: IndexData, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isUp = idx.change >= 0
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(idx.symbol, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
            }
            Spacer(Modifier.height(4.dp))
            Text("₹${"%,.2f".format(idx.price)}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "${if (isUp) "+" else ""}${"%.2f".format(idx.change)} (${if (isUp) "+" else ""}${"%.2f".format(idx.changePct)}%)",
                fontSize = 11.sp,
                color = if (isUp) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
    }
}

@Composable
private fun SignalCardV3(sig: Signal) {
    val isCE = sig.optionType == OptionType.CE
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCE) Color(0xFF10B981).copy(alpha = 0.06f) else Color(0xFFEF4444).copy(alpha = 0.06f)),
        border = BorderStroke(1.dp, if (isCE) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (isCE) Color(0xFF10B981) else Color(0xFFEF4444)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isCE) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null,
                    tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(sig.symbol.value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${sig.optionType.name} • ${sig.lots} lots • Conf: ${sig.confidence.pct}%", fontSize = 11.sp, color = Color.Gray)
                Text(sig.reason, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${"%.1f".format(sig.entryPrice.rupees)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("T: ₹${"%.0f".format(sig.target.rupees)} SL: ₹${"%.0f".format(sig.stopLoss.rupees)}", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}
