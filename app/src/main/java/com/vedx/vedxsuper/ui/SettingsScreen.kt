package com.vedx.vedxsuper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onClearHistory: () -> Unit,
    onSyncAll: () -> Unit,
    onEmergencyExit: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddFundsDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ===== VIRTUAL WALLET CARD =====
        WalletCard(
            balance = uiState.virtualBalance,
            totalPnL = uiState.totalPnL,
            winRate = uiState.winRate,
            tradeCount = uiState.tradeCount,
            onAddFunds = { showAddFundsDialog = true },
            onWithdraw = { showWithdrawDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ===== TRADING SETTINGS =====
        Text("Trading Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = "Auto Confirm Trades",
            subtitle = "Skip notification and auto-execute virtual trades",
            icon = Icons.Default.CheckCircle,
            checked = uiState.autoTradeConfirm,
            onCheckedChange = { viewModel.setAutoTradeConfirm(it) }
        )

        SettingNumberItem(
            title = "Default Quantity",
            value = uiState.defaultQuantity,
            onValueChange = { viewModel.setDefaultQuantity(it) }
        )

        SettingNumberItem(
            title = "Risk Per Trade (%)",
            value = uiState.riskPerTrade,
            onValueChange = { viewModel.setRiskPerTrade(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ===== APP SETTINGS =====
        Text("App Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = "Dark Mode",
            subtitle = "Enable dark theme",
            icon = Icons.Default.Settings,
            checked = uiState.darkMode,
            onCheckedChange = { viewModel.setDarkMode(it) }
        )

        SettingItem(
            title = "Notifications",
            subtitle = "Trade signal alerts",
            icon = Icons.Default.Notifications,
            checked = uiState.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
        )

        SettingItem(
            title = "Sound Alerts",
            subtitle = "Play sound on trade signals",
            icon = Icons.Default.Info,
            checked = uiState.soundAlerts,
            onCheckedChange = { viewModel.setSoundAlerts(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ===== ACTIONS =====
        Text("Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))

        ActionButton(
            text = "🔄 Sync History",
            onClick = onSyncAll,
            color = Color(0xFF2C6BE5)
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionButton(
            text = "🗑️ Clear History",
            onClick = onClearHistory,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionButton(
            text = "🚨 Emergency Exit All",
            onClick = onEmergencyExit,
            color = Color(0xFFF23645)
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionButton(
            text = "♻️ Reset Balance to ₹1,00,000",
            onClick = { viewModel.resetBalance() },
            color = Color(0xFFFF9800)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ===== LOGOUT =====
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout Session", color = Color.Gray, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // Add Funds Dialog
    if (showAddFundsDialog) {
        AmountDialog(
            title = "Add Funds",
            onConfirm = {
                val amount = amountText.toLongOrNull() ?: 0
                if (amount > 0) {
                    viewModel.addFunds(amount)
                }
                amountText = ""
                showAddFundsDialog = false
            },
            onDismiss = {
                amountText = ""
                showAddFundsDialog = false
            },
            amountText = amountText,
            onAmountChange = { amountText = it }
        )
    }

    // Withdraw Dialog
    if (showWithdrawDialog) {
        AmountDialog(
            title = "Withdraw Funds",
            onConfirm = {
                val amount = amountText.toLongOrNull() ?: 0
                if (amount > 0) {
                    val success = viewModel.withdrawFunds(amount)
                    if (!success) {
                        // Show error - insufficient balance
                    }
                }
                amountText = ""
                showWithdrawDialog = false
            },
            onDismiss = {
                amountText = ""
                showWithdrawDialog = false
            },
            amountText = amountText,
            onAmountChange = { amountText = it }
        )
    }
}

@Composable
fun WalletCard(
    balance: Double,
    totalPnL: Double,
    winRate: Float,
    tradeCount: Int,
    onAddFunds: () -> Unit,
    onWithdraw: () -> Unit
) {
    val pnlColor = if (totalPnL >= 0) Color(0xFF00B97D) else Color(0xFFF23645)
    val pnlPrefix = if (totalPnL >= 0) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C6BE5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Virtual Wallet", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(
                "₹${String.format(Locale.US, "%,.2f", balance)}",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total P&L", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    Text(
                        "$pnlPrefix₹${String.format(Locale.US, "%,.2f", kotlin.math.abs(totalPnL))}",
                        color = pnlColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Win Rate", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    Text("${String.format(Locale.US, "%.1f", winRate)}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Trades", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                    Text("$tradeCount", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddFunds,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B97D)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("➕ Add Funds", fontSize = 12.sp)
                }
                Button(
                    onClick = onWithdraw,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("➖ Withdraw", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF2C6BE5), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingNumberItem(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > 1) onValueChange(value - 1) }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease")
            }
            Text("$value", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), fontSize = 14.sp)
            IconButton(onClick = { onValueChange(value + 1) }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase")
            }
        }
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit, color: Color) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AmountDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { onAmountChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
