package com.vedx.vedxsuper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionChainScreen(viewModel: OptionChainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val indices = listOf(
        "NIFTY" to "NIFTY 50",
        "BANKNIFTY" to "BANK NIFTY",
        "FINNIFTY" to "FIN NIFTY",
        "SENSEX" to "SENSEX",
        "MIDCPNIFTY" to "MIDCAP",
        "BANKEX" to "BANKEX"
    )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("Option Chain", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))

        // Index Selection Row (Angel One Style)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(indices) { (id, label) ->
                val selected = uiState.underlying == id
                Surface(
                    selected = selected,
                    onClick = { viewModel.selectUnderlying(id) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) AppColors.Blue else AppColors.CardBg,
                    border = BorderStroke(1.dp, if (selected) AppColors.Blue else AppColors.Border)
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) AppColors.White else AppColors.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Expiry Dropdown
        if (uiState.availableExpiries.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = uiState.selectedExpiry,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Expiry") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    uiState.availableExpiries.forEach { expiry ->
                        DropdownMenuItem(
                            text = { Text(expiry) },
                            onClick = {
                                viewModel.selectExpiry(expiry)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.atmStrike > 0) {
            Text("ATM: ${uiState.atmStrike.toInt()}", fontSize = 12.sp, color = AppColors.TextMuted)
        }

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        }

        if (uiState.strikes.isNotEmpty()) {
            OptionChainTable(uiState)
        }
    }
}

@Composable
fun OptionChainTable(uiState: OptionChainUiState) {
    Column {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF4F6F9)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("CALL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("STRIKE", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
            Text("PUT", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
        }

        LazyColumn {
            items(uiState.strikes) { strike ->
                val isATM = kotlin.math.abs(strike - uiState.atmStrike) < 0.01
                val bgColor = if (isATM) Color(0xFFFFF3E0) else Color.Transparent

                Row(
                    modifier = Modifier.fillMaxWidth().background(bgColor).padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val callPrice = uiState.callPrices[strike] ?: 0.0
                    val putPrice = uiState.putPrices[strike] ?: 0.0

                    Text(
                        if (callPrice > 0) String.format(Locale.US, "%.2f", callPrice) else "-",
                        modifier = Modifier.weight(1f), fontSize = 13.sp,
                        color = Color(0xFF00B97D),
                        fontWeight = if (isATM) FontWeight.Bold else FontWeight.Normal
                    )

                    Text(
                        strike.toInt().toString(),
                        modifier = Modifier.weight(1f), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (isATM) Color(0xFF2C6BE5) else Color.Black
                    )

                    Text(
                        if (putPrice > 0) String.format(Locale.US, "%.2f", putPrice) else "-",
                        modifier = Modifier.weight(1f), fontSize = 13.sp,
                        color = Color(0xFFF23645),
                        fontWeight = if (isATM) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E3E7))
            }
        }
    }
}
