package com.vedx.vedxsuper.ui.chart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.ui.AppColors
import com.vedx.vedxsuper.ui.MarketAnalysis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    viewModel: ChartViewModel = viewModel()
) {
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val currentPrice by viewModel.currentPrice.collectAsState()
    val indexCandles by viewModel.indexCandles.collectAsState()
    val signals by viewModel.signals.collectAsState()
    val indexST by viewModel.indexST.collectAsState()
    val optionST by viewModel.optionST.collectAsState()
    val marketAnalysis by viewModel.marketAnalysis.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.White),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedIndex.displayName, fontSize = 18.sp, fontWeight = FontWeight.Black, color = AppColors.Blue)
                        Spacer(Modifier.width(12.dp))
                        Text("₹${"%.2f".format(currentPrice)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Menu, null, tint = AppColors.TextPrimary)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            ChartViewModel.IndianIndex.entries.forEach { idx ->
                                DropdownMenuItem(
                                    text = { Text(idx.displayName) },
                                    onClick = {
                                        viewModel.selectIndex(idx)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. CONTEXT STRIP (PCR, VIX, Regime)
            ContextStrip(marketAnalysis)

            // 2. MAIN CHART AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.White)
                    .border(BorderStroke(1.dp, AppColors.Border), RoundedCornerShape(16.dp))
            ) {
                if (isLoading || indexCandles.size < 15) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    SevenSTChart(
                        candles = indexCandles,
                        indexST = indexST,
                        optionST = optionST,
                        signals = signals,
                        currentPrice = currentPrice,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Overlay: Last Signal
                signals.lastOrNull()?.let { last ->
                    ActiveSignalOverlay(last, Modifier.align(Alignment.TopStart))
                }
            }

            // 3. INDICATOR SUMMARY
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IndicatorPill("OI Pattern", "Long Build-up", AppColors.Green)
                IndicatorPill("Vol. Spike", "1.5x", AppColors.Orange)
                IndicatorPill("ST Match", "${indexST?.bullCount ?: 0}/7", AppColors.Blue)
            }
        }
    }
}

@Composable
fun ContextStrip(analysis: MarketAnalysis) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContextItem("PCR", "%.2f".format(analysis.pcr), if(analysis.pcr > 1) AppColors.Green else AppColors.Red)
        ContextItem("VIX", "%.2f".format(analysis.vix), if(analysis.vix < 20) AppColors.Green else AppColors.Red)
        
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AppColors.BlueLight,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                analysis.regime.name.replace("_", " "),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.Blue
            )
        }
    }
}

@Composable
fun ContextItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Black)
    }
}

@Composable
fun IndicatorPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = AppColors.TextMuted, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Text(
                value,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ActiveSignalOverlay(signal: Signal, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(12.dp).width(180.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if(signal.optionType == OptionType.CE) AppColors.Green else AppColors.Red))
                Spacer(Modifier.width(8.dp))
                Text(signal.symbol.value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(signal.reason, fontSize = 9.sp, color = AppColors.TextSecondary, maxLines = 2)
            HorizontalDivider(Modifier.padding(vertical = 6.dp), thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Conf: ${signal.confidence.pct}%", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.Blue)
                Text("Grade ${signal.grade}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.Orange)
            }
        }
    }
}
