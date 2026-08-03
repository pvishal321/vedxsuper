package com.vedx.vedxsuper.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.strategy.engine.InstitutionalSignal
import com.vedx.vedxsuper.model.market.IndexData
import java.util.Locale

@Composable
fun SummaryItem(label: String, value: String, color: Color = Color(0xFF1A1C1E)) {
    Column {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
fun MiniMetric(value: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            value,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StatusRow(label: String, active: Boolean, invert: Boolean = false) {
    val isActive = if (invert) !active else active
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (isActive) "🟢 " else "🔴 ") + label,
            color = if (isActive) Color(0xFF1A1C1E) else Color.Gray,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            if (isActive) "READY" else "WAITING",
            color = if (isActive) Color(0xFF00B97D) else Color(0xFFF23645),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun ZoneCheck(label: String, isOk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            if (isOk) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isOk) Color(0xFF00B97D) else Color.LightGray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = if (isOk) Color(0xFF1A1C1E) else Color.Gray, fontWeight = if (isOk) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun PriceTag(label: String, price: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(price, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun StatusBadge(label: String, active: Boolean, modifier: Modifier = Modifier, invert: Boolean = false) {
    val isActive = if (invert) !active else active
    Surface(
        modifier = modifier,
        color = (if (isActive) Color(0xFF00B97D) else Color.LightGray).copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, (if (isActive) Color(0xFF00B97D) else Color.LightGray).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
            Text(if (isActive) "ONLINE" else "OFFLINE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = if (isActive) Color(0xFF00B97D) else Color.Gray)
        }
    }
}

@Composable
fun MetricSmall(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1C1E))
    }
}

@Composable
fun DiagnosticBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1C1E))
        }
    }
}

@Composable
fun DiagnosticCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        }
    }
}

@Composable
fun PulseItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
fun InsightCard(signal: InstitutionalSignal) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFF0F4FF))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(if (signal.type == "BUY") Color(0xFF00B97D) else Color(0xFFF23645), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(signal.optionSymbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("Confidence: " + signal.confidence.toString() + "%", fontSize = 11.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signal.type,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (signal.type == "BUY") Color(0xFF00B97D) else Color(0xFFF23645)
                )
                Text("Target: ₹" + String.format(Locale.US, "%.2f", signal.target), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C6BE5))
            }
        }
    }
}

@Composable
fun DetailBlock(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
fun OptionMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable
fun StatusItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun IndexHeader(indexData: Map<String, IndexData>) {
    // Redundant stub
}
