package com.vedx.vedxsuper.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.database.TradeEntity
import com.vedx.vedxsuper.model.market.IndexData
import com.vedx.vedxsuper.ui.HistoryViewModel
import com.vedx.vedxsuper.ui.TradeViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TradesTab(
    openTrades: List<TradeEntity>,
    allTrades: List<TradeEntity>,
    tradeViewModel: TradeViewModel,
    historyViewModel: HistoryViewModel,
    indexData: Map<String, IndexData>
) {
    val closedPnl = allTrades.filter { it.status == "CLOSED" }.sumOf { it.pnl }
    val livePnl = openTrades.sumOf { trade ->
        val currentPrice = indexData[trade.symbol]?.lastTradedPrice ?: trade.entryPrice
        val isCall = trade.type.contains("CALL")
        if (trade.type == "BUY" || isCall) {
            (currentPrice - trade.entryPrice) * trade.quantity
        } else {
            (trade.entryPrice - currentPrice) * trade.quantity
        }
    }
    val totalMtm = livePnl + closedPnl

    val closedTrades = allTrades.filter { it.status == "CLOSED" }
    val wins = closedTrades.count { it.pnl > 0 }
    val losses = closedTrades.count { it.pnl < 0 }
    val winRatio = if (closedTrades.isNotEmpty()) (wins.toDouble() / closedTrades.size * 100).toInt() else 0

    val bestTrade = closedTrades.maxByOrNull { it.pnl }
    val worstTrade = closedTrades.minByOrNull { it.pnl }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FB))) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total MTM", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                "₹" + String.format(Locale.US, "%.2f", totalMtm),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalMtm >= 0) Color(0xFF00B97D) else Color(0xFFF23645)
                            )
                        }

                        // Win/Loss Analytics
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Win Ratio", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${winRatio}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF673AB7))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Realized P&L", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("₹" + String.format(Locale.US, "%.2f", closedPnl), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Unrealized P&L", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("₹" + String.format(Locale.US, "%.2f", livePnl), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (livePnl >= 0) Color(0xFF00B97D) else Color(0xFFF23645))
                        }
                    }

                    if (closedTrades.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F4FF))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            bestTrade?.let {
                                Text("Best: +₹${it.pnl.toInt()}", fontSize = 10.sp, color = Color(0xFF00B97D), fontWeight = FontWeight.Bold)
                            }
                            worstTrade?.let {
                                Text("Worst: ₹${it.pnl.toInt()}", fontSize = 10.sp, color = Color(0xFFF23645), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("POSITIONS (" + openTrades.size.toString() + ")", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        if (openTrades.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No Open Positions", color = Color.LightGray, fontSize = 14.sp)
                }
            }
        }

        items(openTrades) { trade ->
            val currentPrice = indexData[trade.symbol]?.lastTradedPrice ?: trade.entryPrice
            val isCall = trade.type.contains("CALL")
            val tradePnl = if (isCall) (currentPrice - trade.entryPrice) * trade.quantity else (trade.entryPrice - currentPrice) * trade.quantity

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = (if (isCall) Color(0xFF00B97D) else Color(0xFFF23645)).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(trade.type, color = if (isCall) Color(0xFF00B97D) else Color(0xFFF23645), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(trade.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Text("Qty: " + trade.quantity.toString() + " | Avg: ₹" + String.format(Locale.US, "%.2f", trade.entryPrice), color = Color.Gray, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "₹" + String.format(Locale.US, "%.2f", tradePnl),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = if (tradePnl >= 0) Color(0xFF00B97D) else Color(0xFFF23645)
                            )
                            Text("LTP: ₹" + String.format(Locale.US, "%.2f", currentPrice), color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("STOP LOSS", fontSize = 10.sp, color = Color.Gray)
                            Text("₹" + String.format(Locale.US, "%.2f", trade.stopLoss), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF23645))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("CONFIDENCE", fontSize = 10.sp, color = Color.Gray)
                            Text(trade.confidence.toString() + "%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF673AB7))
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("TARGET", fontSize = 10.sp, color = Color.Gray)
                            Text("₹" + String.format(Locale.US, "%.2f", trade.target), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B97D))
                        }
                    }

                    if (trade.explanation.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFFF8F9FB),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                trade.explanation,
                                modifier = Modifier.padding(8.dp),
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { tradeViewModel.exitTrade(trade, currentPrice) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Text("SQUARE OFF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("CLOSED POSITIONS", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        items(allTrades.filter { it.status == "CLOSED" }.sortedByDescending { it.exitTime }) { trade ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(trade.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = (if (trade.type.contains("CALL")) Color(0xFF00B97D) else Color(0xFFF23645)).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text(
                                    if (trade.type.contains("CALL")) "CE" else "PE",
                                    fontSize = 9.sp,
                                    color = if (trade.type.contains("CALL")) Color(0xFF00B97D) else Color(0xFFF23645),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                        Text(
                            "Exit: " + dateFormat.format(Date(trade.exitTime ?: 0)),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "₹" + String.format(Locale.US, "%.2f", trade.pnl),
                            color = if (trade.pnl >= 0) Color(0xFF00B97D) else Color(0xFFF23645),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            "In: ₹" + String.format(Locale.US, "%.1f", trade.entryPrice) + " | Out: ₹" + String.format(Locale.US, "%.1f", trade.exitPrice ?: 0.0),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.2f), modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
