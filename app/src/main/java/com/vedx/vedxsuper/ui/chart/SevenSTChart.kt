package com.vedx.vedxsuper.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedx.vedxsuper.data.*
import com.vedx.vedxsuper.ui.AppColors
import kotlin.math.*

@Composable
fun SevenSTChart(
    candles: List<Candle>,
    indexST: MultiST?,
    optionST: MultiST?,
    signals: List<Signal>,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var scaleX by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }

    // Professional TradingView Colors for ST2-ST8
    val stColors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Green
        Color(0xFFF59E0B), // Orange
        Color(0xFFEF4444), // Red
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4)  // Cyan
    )

    Box(modifier = modifier.background(Color.White)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scaleX = (scaleX * zoom).coerceIn(1f, 10f)
                        offsetX += pan.x
                    }
                }
        ) {
            if (candles.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height
            val chartW = w - 80f
            val chartH = h - 60f
            val leftPad = 60f
            val topPad = 20f

            val visibleCount = (candles.size / scaleX).toInt().coerceAtLeast(10)
            val startIdx = (candles.size - visibleCount).coerceAtLeast(0)
            val visible = candles.takeLast(visibleCount)

            // Price Scale
            val minP = visible.minOf { it.low.rupees } * 0.9995
            val maxP = visible.maxOf { it.high.rupees } * 1.0005
            val pRange = maxP - minP

            fun Double.toY() = (topPad + chartH - ((this - minP) / pRange * chartH)).toFloat()
            fun Int.toX() = leftPad + (this * (chartW / visibleCount))

            // 1. Draw Grid
            drawGrid(leftPad, topPad, chartW, chartH, minP, maxP, textMeasurer)

            // 2. Draw ST Bands (ST2 - ST8)
            indexST?.let { st ->
                val bands = st.bandList()
                bands.forEachIndexed { i, price ->
                    val color = if (st.master.trend == 1.toByte()) AppColors.Green.copy(0.2f) else AppColors.Red.copy(0.2f)
                    drawLine(
                        color = color,
                        start = Offset(leftPad, price.toY()),
                        end = Offset(leftPad + chartW, price.toY()),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                }
            }

            // 3. Draw Candlesticks
            val candleW = (chartW / visibleCount) * 0.7f
            visible.forEachIndexed { i, c ->
                val cx = i.toX()
                val isBullish = c.close.cents >= c.open.cents
                val color = if (isBullish) Color(0xFF10B981) else Color(0xFFEF4444)
                
                // Wick
                drawLine(color, Offset(cx, c.high.rupees.toY()), Offset(cx, c.low.rupees.toY()), strokeWidth = 1.5f)
                // Body
                val top = max(c.open.rupees, c.close.rupees).toY()
                val bottom = min(c.open.rupees, c.close.rupees).toY()
                drawRect(color, Offset(cx - candleW/2, top), Size(candleW, max(2f, abs(top - bottom))))
            }

            // 4. Draw Signal Markers (BUY/EXIT)
            signals.takeLast(10).forEach { sig ->
                // Simplified positioning for demo: find candle by timestamp
                val candleIdx = visible.indexOfFirst { abs(it.timestamp - sig.timestamp) < 60000 }
                if (candleIdx != -1) {
                    val sx = candleIdx.toX()
                    val sy = if(sig.optionType == OptionType.CE) visible[candleIdx].low.rupees.toY() + 20f else visible[candleIdx].high.rupees.toY() - 20f
                    
                    drawCircle(if(sig.optionType == OptionType.CE) AppColors.Green else AppColors.Red, 8f, Offset(sx, sy))
                    drawCircle(Color.White, 4f, Offset(sx, sy))
                }
            }

            // 5. Current Price Line
            val cy = currentPrice.toY()
            drawLine(AppColors.Blue, Offset(leftPad, cy), Offset(leftPad + chartW, cy), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f)))
            drawRect(AppColors.Blue, Offset(leftPad + chartW, cy - 20f), Size(80f, 40f))
            drawText(textMeasurer, "%.1f".format(currentPrice), Offset(leftPad + chartW + 5f, cy - 15f), TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}

private fun DrawScope.drawGrid(left: Float, top: Float, w: Float, h: Float, minP: Double, maxP: Double, tm: androidx.compose.ui.text.TextMeasurer) {
    val lines = 6
    for (i in 0..lines) {
        val gy = top + (i.toFloat() / lines) * h
        val price = maxP - (i.toFloat() / lines) * (maxP - minP)
        drawLine(AppColors.Border.copy(0.5f), Offset(left, gy), Offset(left + w, gy), 1f)
        drawText(tm, "%.0f".format(price), Offset(5f, gy - 15f), TextStyle(color = AppColors.TextMuted, fontSize = 10.sp))
    }
}
