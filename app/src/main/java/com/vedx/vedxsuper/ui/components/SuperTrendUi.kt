package com.vedx.vedxsuper.ui.components

import android.util.Log
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vedx.vedxsuper.BuildConfig
import com.vedx.vedxsuper.model.market.Candle
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendEngine
import com.vedx.vedxsuper.strategy.indicator.MultiSuperTrendResult
import com.vedx.vedxsuper.strategy.engine.InstitutionalSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Advanced Institutional Charting Component.
 * Fully optimized for production performance and accuracy.
 */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun SuperTrendChart(
    symbol: String,
    candles: List<Candle>,
    stResult: MultiSuperTrendResult?,
    activeSignal: InstitutionalSignal?,
    isIndex: Boolean,
    timeframe: Int = 15,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131722),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).background(Color(0xFF131722))) {
            ChartHeader(symbol, stResult, timeframe, onDismiss)
            
            InstitutionalWebViewChart(
                symbol = symbol,
                candles = candles,
                stResult = stResult,
                activeSignal = activeSignal,
                timeframe = timeframe,
                modifier = Modifier.weight(1f)
            )
            
            if (activeSignal != null) {
                TradeActionBar(activeSignal)
            }
        }
    }
}

@Composable
private fun ChartHeader(
    symbol: String, 
    stResult: MultiSuperTrendResult?, 
    tf: Int, 
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Surface(color = Color(0xFF2C6BE5).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("${tf}M", color = Color(0xFF2C6BE5), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(
                text = if (stResult?.st2?.trend == 1) "TREND: BULLISH" else "TREND: BEARISH",
                color = if (stResult?.st2?.trend == 1) Color(0xFF00B97D) else Color(0xFFF23645),
                fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
        }
    }
}

@Composable
private fun InstitutionalWebViewChart(
    symbol: String,
    candles: List<Candle>,
    stResult: MultiSuperTrendResult?,
    activeSignal: InstitutionalSignal?,
    timeframe: Int,
    modifier: Modifier = Modifier
) {
    val chartHtml = remember(symbol) { generateChartHtml() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }

    // [Fix 1, 3, 4] Optimized History Load with Timeframe Awareness
    LaunchedEffect(isPageLoaded, symbol, timeframe) {
        if (!isPageLoaded) return@LaunchedEffect
        
        snapshotFlow { candles.size }.collectLatest { size ->
            if (size > 0) {
                val data = withContext(Dispatchers.Default) {
                    try {
                        val engine = MultiSuperTrendEngine()
                        // Sort and de-duplicate by millisecond timestamp
                        val sorted = candles.toList().sortedBy { it.timestamp }.distinctBy { it.timestamp }
                        
                        // [Fix 4] Mark all as complete except last for correct bootstrap
                        val historyPass = sorted.mapIndexed { idx, c ->
                            c.copy(isComplete = idx < sorted.size - 1) 
                        }
                        
                        val stHistory = engine.calculateHistory(historyPass)
                        
                        JSONObject().apply {
                            val candleArr = JSONArray()
                            val stMatrix = List(7) { JSONArray() }
                            
                            sorted.forEach { c ->
                                candleArr.put(JSONObject().apply {
                                    put("time", c.timestamp / 1000)
                                    put("open", c.open)
                                    put("high", c.high)
                                    put("low", c.low)
                                    put("close", c.close)
                                })
                            }

                            stHistory.forEach { res ->
                                val time = res.timestamp / 1000
                                val levels = listOf(res.st2.value, res.st3.value, res.st4.value, res.st5.value, res.st6.value, res.st7.value, res.st8.value)
                                levels.forEachIndexed { idx, valAtTime ->
                                    if (valAtTime > 0) {
                                        stMatrix[idx].put(JSONObject().apply {
                                            put("time", time)
                                            put("value", valAtTime)
                                        })
                                    }
                                }
                            }
                            
                            put("candles", candleArr)
                            put("stHistory", JSONArray(stMatrix))
                            generateSignalJsonObj(activeSignal)?.let { put("signal", it) }
                        }.toString()
                    } catch (e: Exception) {
                        Log.e("ChartData", "Error generating history", e)
                        null
                    }
                }

                data?.let {
                    webViewRef?.evaluateJavascript("""
                        if(window.loadHistory) {
                            window.loadHistory($it);
                        } else {
                            setTimeout(() => { if(window.loadHistory) window.loadHistory($it); }, 150);
                        }
                    """.trimIndent(), null)
                }
                throw kotlinx.coroutines.CancellationException("History Loaded")
            }
        }
    }

    // [Fix 13] Debounced Real-time Sync
    LaunchedEffect(isPageLoaded, candles.lastOrNull(), stResult, activeSignal) {
        if (!isPageLoaded) return@LaunchedEffect
        
        snapshotFlow { candles.lastOrNull() }
            .debounce(100)
            .collect { lastCandle ->
                if (lastCandle == null) return@collect
                
                val update = JSONObject().apply {
                    put("time", lastCandle.timestamp / 1000)
                    put("open", lastCandle.open)
                    put("high", lastCandle.high)
                    put("low", lastCandle.low)
                    put("close", lastCandle.close)
                    put("stLevels", JSONArray(generateStArray(stResult)))
                    generateSignalJsonObj(activeSignal)?.let { put("signal", it) }
                }.toString()

                webViewRef?.evaluateJavascript("if(window.updateCandle) window.updateCandle($update)", null)
            }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                        if (BuildConfig.DEBUG) Log.d("ChartJS", "${msg?.message()} [Line ${msg?.lineNumber()}]")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) { isPageLoaded = true }
                    override fun onReceivedError(v: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                        Log.e("ChartData", "WebView Error: ${err?.description}")
                        isPageLoaded = false
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    setSupportZoom(true)
                    displayZoomControls = false
                    builtInZoomControls = true
                }
                setBackgroundColor(0xFF131722.toInt())
                // [Fix 11] Local Assets fallback could be added here
                loadDataWithBaseURL(null, chartHtml, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        onRelease = { 
            it.destroy()
            webViewRef = null
        }
    )
}

private fun generateStArray(st: MultiSuperTrendResult?): List<Double> {
    if (st == null) return emptyList()
    return listOf(st.st2.value, st.st3.value, st.st4.value, st.st5.value, st.st6.value, st.st7.value, st.st8.value)
}

private fun generateSignalJsonObj(sig: InstitutionalSignal?): JSONObject? {
    if (sig == null || sig.price <= 0) return null
    return JSONObject().apply {
        put("type", sig.type)
        put("target", sig.target)
        put("stopLoss", sig.stopLoss)
    }
}

@Composable
private fun TradeActionBar(signal: InstitutionalSignal) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1A1C1E)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("AI ENTRY SIGNAL", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("₹${String.format(Locale.US, "%.2f", signal.price)}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Button(
                onClick = { /* Execution */ },
                colors = ButtonDefaults.buttonColors(containerColor = if (signal.type == "BUY") Color(0xFF00B97D) else Color(0xFFF23645)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("EXECUTE ${signal.type}", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun generateChartHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <script src="https://unpkg.com/lightweight-charts/dist/lightweight-charts.standalone.production.js" 
                    onerror="document.body.innerHTML='<div style=\'color:red;padding:20px;\'>Chart library failed to load</div>'"></script>
            <style>
                body { margin: 0; padding: 0; background-color: #131722; overflow: hidden; }
                #chart { width: 100vw; height: 100vh; }
            </style>
        </head>
        <body>
            <div id="chart"></div>
            <script>
                const chart = LightweightCharts.createChart(document.getElementById('chart'), {
                    layout: { backgroundColor: '#131722', textColor: '#d1d4dc' },
                    grid: { vertLines: { color: '#1f222d' }, horzLines: { color: '#1f222d' } },
                    priceScale: { borderColor: '#485c7b' },
                    timeScale: { borderColor: '#485c7b', timeVisible: true },
                    handleScroll: { vertTouchDrag: false }
                });

                const candleSeries = chart.addCandlestickSeries({
                    upColor: '#00b97d', downColor: '#f23645', borderVisible: false,
                    wickUpColor: '#00b97d', wickDownColor: '#f23645'
                });

                const stSeries = Array.from({length: 7}, () => chart.addLineSeries({
                    color: 'rgba(44, 107, 229, 0.4)', lineWidth: 1, lineStyle: 2, 
                    lastValueVisible: false, priceLineVisible: false
                }));

                let targetLine = null;
                let slLine = null;
                let currentSignal = null;

                window.loadHistory = (data) => {
                    if (data.candles) candleSeries.setData(data.candles);
                    if (data.stHistory) data.stHistory.forEach((s, i) => stSeries[i].setData(s));
                    if (data.signal) updatePriceLines(data.signal);
                    chart.timeScale().fitContent();
                };

                window.updateCandle = (data) => {
                    candleSeries.update(data);
                    if (data.stLevels) data.stLevels.forEach((v, i) => stSeries[i].update({ time: data.time, value: v }));
                    if (data.signal) {
                        const changed = !currentSignal || currentSignal.target !== data.signal.target || currentSignal.stopLoss !== data.signal.stopLoss;
                        if (changed) updatePriceLines(data.signal);
                    }
                };

                function updatePriceLines(sig) {
                    currentSignal = sig;
                    if (targetLine) candleSeries.removePriceLine(targetLine);
                    if (slLine) candleSeries.removePriceLine(slLine);
                    
                    targetLine = candleSeries.createPriceLine({
                        price: sig.target, color: '#00b97d', lineWidth: 2, lineStyle: 0, axisLabelVisible: true, title: 'TARGET'
                    });
                    slLine = candleSeries.createPriceLine({
                        price: sig.stopLoss, color: '#f23645', lineWidth: 2, lineStyle: 0, axisLabelVisible: true, title: 'SL'
                    });
                }

                new ResizeObserver(entries => {
                    if (entries.length > 0) chart.applyOptions({ width: entries[0].contentRect.width, height: entries[0].contentRect.height });
                }).observe(document.getElementById('chart'));
            </script>
        </body>
        </html>
    """.trimIndent()
}
