package com.vedx.vedxsuper.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 1. Institutional Zone Heatmap
 * Visualizes ST2-ST8 bands as a color-coded vertical scale.
 */
@Composable
fun InstitutionalZoneHeatmap(
    currentPrice: Double,
    stResult: MultiSuperTrendResult?,
    modifier: Modifier = Modifier
) {
    if (stResult == null) return

    val bands = listOf(
        stResult.st2.value, stResult.st3.value, stResult.st4.value,
        stResult.st5.value, stResult.st6.value, stResult.st7.value, stResult.st8.value
    ).sorted()

    val minBand = bands.first()
    val maxBand = bands.last()
    val range = maxBand - minBand

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(60.dp)) {
                Text("RES", color = Color(0xFFF23645), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("SUP", color = Color(0xFF2C6BE5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Background Gradient
                Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                    val brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF23645), Color(0xFF673AB7), Color(0xFF2C6BE5))
                    )
                    drawRoundRect(
                        brush = brush,
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                        alpha = 0.2f
                    )

                    // Draw Band Lines
                    bands.forEach { band ->
                        val y = size.height * (1.0 - (band - minBand) / range).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Current Price Marker
                    val priceY = size.height * (1.0 - (currentPrice - minBand) / range).coerceIn(0.0, 1.0).toFloat()
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(0f, priceY - 1.dp.toPx()),
                        size = Size(size.width, 2.dp.toPx())
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                Text("LIVE PRICE", color = Color.Gray, fontSize = 9.sp)
                Text("₹${String.format(java.util.Locale.US, "%.2f", currentPrice)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * 2. Convergence Gauge
 * Speedometer-style gauge for Index-Option alignment.
 */
@Composable
fun ConvergenceGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(1000), label = "gauge")

    val color = when {
        animatedScore > 80 -> Color(0xFF00B97D)
        animatedScore > 50 -> Color(0xFF2C6BE5)
        else -> Color(0xFFF23645)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(modifier = Modifier.size(80.dp)) {
                // Background Track
                drawArc(
                    color = Color(0xFFF0F4FF),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )

                // Progress Track
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = (270f * (animatedScore / 100f)),
                    useCenter = false,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )

                // Needle
                val angle = (135f + (270f * (animatedScore / 100f))) * (PI / 180f)
                val needleLen = size.width / 2.2f
                val endX = (size.width / 2) + cos(angle).toFloat() * needleLen
                val endY = (size.height / 2) + sin(angle).toFloat() * needleLen

                // Needle shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.1f),
                    start = Offset(size.width / 2 + 2f, size.height / 2 + 2f),
                    end = Offset(endX + 2f, endY + 2f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = Color(0xFF1A1C1E),
                    start = Offset(size.width / 2, size.height / 2),
                    end = Offset(endX, endY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center point
                drawCircle(
                    color = Color(0xFF1A1C1E),
                    radius = 4.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2)
                )
            }
            Text("${animatedScore}%", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Text("NEURAL SCORE", color = Color(0xFF1A1C1E), fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * 3. Panic Control Buttons
 */
@Composable
fun PanicControls(
    onExitAll: () -> Unit,
    onPauseBots: () -> Unit,
    botsPaused: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onExitAll,
            modifier = Modifier.weight(1.5f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF23645)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("EMERGENCY EXIT ALL", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }

        OutlinedButton(
            onClick = onPauseBots,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (botsPaused) Color(0xFF00B97D) else Color.Gray),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (botsPaused) Color(0xFF00B97D).copy(alpha = 0.1f) else Color.Transparent
            )
        ) {
            Text(
                text = if (botsPaused) "RESUME BOTS" else "PAUSE BOTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (botsPaused) Color(0xFF00B97D) else Color(0xFF1A1C1E)
            )
        }
    }
}

/**
 * 4. Compact Option Chain
 */
@Composable
fun CompactOptionChain(
    symbol: String,
    ltp: Double,
    delta: Double,
    theta: Double,
    oiChange: Long,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F4FF))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("₹${String.format(java.util.Locale.US, "%.2f", ltp)}", color = Color(0xFF2C6BE5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            MetricItem("Δ", String.format(java.util.Locale.US, "%.2f", delta), Color(0xFF00B97D), Modifier.weight(0.8f))
            MetricItem("θ", String.format(java.util.Locale.US, "%.1f", theta), Color(0xFFF23645), Modifier.weight(0.8f))
            MetricItem("OI Δ", if (oiChange >= 0) "+${oiChange}" else oiChange.toString(), if (oiChange >= 0) Color(0xFF00B97D) else Color(0xFFF23645), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 11.sp, color = color, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * 5. Intelligence Log Icons
 */
@Composable
fun IntelligenceIcon(text: String) {
    val icon = when {
        text.contains("Volume", true) -> "📊"
        text.contains("Strength", true) -> "⚡"
        text.contains("Regime", true) -> "🌐"
        text.contains("Structure", true) -> "🏗️"
        text.contains("Pullback", true) -> "🔄"
        text.contains("Intelligence", true) -> "🧠"
        else -> "📍"
    }

    Surface(
        color = Color(0xFF2C6BE5).copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = icon,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
