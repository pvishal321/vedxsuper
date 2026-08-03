package com.vedx.vedxsuper.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.ui.BacktestViewModel
import com.vedx.vedxsuper.ui.components.SummaryItem
import java.util.Locale

@Composable
fun BacktestTab(viewModel: BacktestViewModel) {
    val result by viewModel.result.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Backtest Intelligence", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.runBacktest() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6BE5)),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("RUN SIMULATION (LAST 60 DAYS)", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        result?.let { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F4FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Performance Summary", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryItem("WIN RATE", String.format(Locale.US, "%.1f%%", res.winRate), Color(0xFF00B97D))
                        SummaryItem("LOSS RATE", String.format(Locale.US, "%.1f%%", res.lossRate), Color(0xFFF23645))
                        SummaryItem("MAX DD", String.format(Locale.US, "%.1f%%", res.maxDrawdown), Color(0xFFF23645))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = Color(0xFFF0F4FF))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryItem("AVG TIME", (res.avgHoldingTimeMs / 60000).toString() + "m", Color.DarkGray)
                        SummaryItem("BEST ST", "ST " + res.bestSuperTrend, Color(0xFF2C6BE5))
                        SummaryItem("NET P&L", (if (res.totalPnl >= 0) "+" else "") + "₹" + String.format(Locale.US, "%,.2f", res.totalPnl), if (res.totalPnl >= 0) Color(0xFF00B97D) else Color(0xFFF23645))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Institutional Trade Journal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                items(res.trades.reversed()) { trade ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF0F4FF))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(trade.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(trade.exitReason, fontSize = 10.sp, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        (if (trade.pnl >= 0) "+" else "") + "₹" + String.format(Locale.US, "%,.2f", trade.pnl),
                                        color = if (trade.pnl >= 0) Color(0xFF00B97D) else Color(0xFFF23645),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(String.format(Locale.US, "%.1f%%", trade.pnlPercent), fontSize = 10.sp, color = if (trade.pnl >= 0) Color(0xFF00B97D) else Color(0xFFF23645))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
