package com.vedx.vedxsuper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.vedx.vedxsuper.utils.SettingsManager
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onClearHistory: () -> Unit,
    onSyncAll: () -> Unit,
    onEmergencyExit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showAddDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("Account & Funds", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Trading Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Available Trading Balance", fontSize = 12.sp, color = Color.Gray)
                Text(
                    "₹${String.format(Locale.US, "%,.2f", uiState.virtualBalance)}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2C6BE5)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B97D)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ADD FUNDS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedButton(
                        onClick = { viewModel.withdrawFunds(uiState.virtualBalance) }, // Set to 0 then add
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("RESET", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Safety & Panic Controls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onEmergencyExit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF23645)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("EMERGENCY EXIT ALL POSITIONS", fontWeight = FontWeight.ExtraBold)
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Trading Balance") },
                text = { Text("Add ₹50,000 to your virtual trading account?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addFunds(50000.0)
                        showAddDialog = false
                    }) {
                        Text("ADD ₹50,000", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("App Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        SettingSwitch(
            title = "Enable Notifications",
            subtitle = "Get alerts for BUY/SELL signals",
            checked = uiState.notificationEnabled,
            onCheckedChange = { viewModel.setNotificationEnabled(it) }
        )

        SettingSwitch(
            title = "Auto Trading (Robot Mode)",
            subtitle = "Automatically execute BUY/SELL signals from AI Engine.",
            checked = uiState.autoTradeEnabled,
            onCheckedChange = { viewModel.setAutoTradeEnabled(it) },
            activeColor = Color(0xFF2C6BE5)
        )

        SettingSwitch(
            title = "Voice Alerts",
            subtitle = "Announce high probability setups.",
            checked = uiState.voiceAlertEnabled,
            onCheckedChange = { viewModel.setVoiceAlertEnabled(it) }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text("Trading Strategy", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Trading Mode", style = MaterialTheme.typography.bodyLarge)
                Text("Current: ${uiState.tradingMode}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.vedx.vedxsuper.model.trade.TradingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.tradingMode == mode,
                        onClick = { viewModel.setTradingMode(mode) },
                        label = { Text(mode.name, fontSize = 10.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Risk Level", style = MaterialTheme.typography.bodyLarge)
                Text("Current: ${uiState.riskLevel}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.vedx.vedxsuper.model.trade.RiskLevel.entries.forEach { level ->
                    FilterChip(
                        selected = uiState.riskLevel == level,
                        onClick = { viewModel.setRiskLevel(level) },
                        label = { Text(level.name, fontSize = 10.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Risk Sliders
        RiskSlider(
            label = "Max Risk Per Trade",
            value = uiState.maxRiskPerTrade,
            suffix = "%",
            onValueChange = { viewModel.setMaxRiskPerTrade(it) },
            range = 0.1f..5.0f
        )

        RiskSlider(
            label = "Max Daily Loss",
            value = uiState.maxDailyLoss,
            suffix = "%",
            onValueChange = { viewModel.setMaxDailyLoss(it) },
            range = 1.0f..10.0f
        )

        RiskSlider(
            label = "Daily Profit Target",
            value = uiState.dailyProfitTarget,
            suffix = "%",
            onValueChange = { viewModel.setDailyProfitTarget(it) },
            range = 1.0f..20.0f
        )

        Spacer(modifier = Modifier.height(32.dp))
        Text("Advanced Trading Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        SettingSwitch(
            title = "Trading Notifications",
            subtitle = "Only High Probability Signals",
            checked = uiState.onlyHighProbability,
            onCheckedChange = { viewModel.setOnlyHighProbability(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Confidence Limit", style = MaterialTheme.typography.bodyLarge)
                Text("${uiState.confidenceLimit}%", fontWeight = FontWeight.Bold, color = Color(0xFF2C6BE5))
            }
            Slider(
                value = uiState.confidenceLimit.toFloat(),
                onValueChange = { viewModel.setConfidenceLimit(it.toInt()) },
                valueRange = 50f..100f,
                steps = 10
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingSwitch(
            title = "AI Explain Signals",
            subtitle = "Get detailed reasoning for each trade",
            checked = uiState.aiExplainSignals,
            onCheckedChange = { viewModel.setAiExplainSignals(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingSwitch(
            title = "Background Mode",
            subtitle = "Keep engine running when app is closed",
            checked = uiState.backgroundMode,
            onCheckedChange = { viewModel.setBackgroundMode(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingSwitch(
            title = "Lock Screen Alerts",
            subtitle = "Show signals on lock screen",
            checked = uiState.lockScreenAlerts,
            onCheckedChange = { viewModel.setLockScreenAlerts(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingSwitch(
            title = "Floating Notifications",
            subtitle = "Show overlay for active trades",
            checked = uiState.floatingNotify,
            onCheckedChange = { viewModel.setFloatingNotify(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingSwitch(
            title = "Auto-Save Market Data",
            subtitle = "Persist historical data locally for charts",
            checked = uiState.autoSaveData,
            onCheckedChange = { viewModel.setAutoSaveData(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Notification Before Entry", style = MaterialTheme.typography.bodyLarge)
                Text("Early alert time", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 5).forEach { min ->
                    FilterChip(
                        selected = uiState.notifyBeforeEntry == min,
                        onClick = { viewModel.setNotifyBeforeEntry(min) },
                        label = { Text("${min}m", fontSize = 10.sp) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var showLangDialog by remember { mutableStateOf(false) }
            Column {
                Text("Voice Language", style = MaterialTheme.typography.bodyLarge)
                Text(uiState.voiceLang, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2C6BE5), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { showLangDialog = true }) {
                Text("CHANGE", fontWeight = FontWeight.Bold)
            }

            if (showLangDialog) {
                AlertDialog(
                    onDismissRequest = { showLangDialog = false },
                    title = { Text("Select Language") },
                    text = {
                        Column {
                            listOf("English", "Hindi", "Marathi").forEach { lang ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        viewModel.setVoiceLang(lang)
                                        showLangDialog = false
                                    }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = uiState.voiceLang == lang, onClick = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(lang)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showLangDialog = false }) { Text("CANCEL") } }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Risk Management", style = MaterialTheme.typography.bodyLarge)
                Text("Max Daily Loss: ₹5,000", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            TextButton(onClick = {}) {
                Text("EDIT", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Data Management", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onSyncAll()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C6BE5)),
            enabled = uiState.syncStatus == "Idle" || uiState.syncStatus == "Ready" || uiState.syncStatus.contains("Failed")
        ) {
            if (uiState.syncStatus != "Idle" && uiState.syncStatus != "Ready" && !uiState.syncStatus.contains("Failed")) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(uiState.syncStatus)
            } else {
                Text("Sync Full History (60 Days)")
            }
        }
        
        Text("Required for accurate ST Zones & Charts.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onClearHistory,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear Trade History")
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            "VedxSuper v1.1.0",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (checked && activeColor != Color.Unspecified) activeColor else Color.Black, fontWeight = if (checked && activeColor != Color.Unspecified) FontWeight.Bold else FontWeight.Normal)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun RiskSlider(
    label: String,
    value: Double,
    suffix: String,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text("${String.format(Locale.US, "%.1f", value)}$suffix", fontWeight = FontWeight.Bold, color = Color(0xFF2C6BE5))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF2C6BE5),
                activeTrackColor = Color(0xFF2C6BE5).copy(alpha = 0.5f)
            )
        )
    }
}
