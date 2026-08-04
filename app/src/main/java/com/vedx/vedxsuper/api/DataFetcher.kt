package com.vedx.vedxsuper.api

import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.Price
import kotlinx.coroutines.*
import okhttp3.*
import retrofit2.http.*
import java.io.BufferedReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ===== NSE FREE BHAVCOPY =====
object NseBhavcopy {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
    private val fmt = SimpleDateFormat("ddMMyyyy", Locale.US)
    
    suspend fun fetch(date: Date = Date()): List<Candle> = withContext(Dispatchers.IO) {
        val d = fmt.format(date)
        val url = "https://archives.nseindia.com/products/content/sec_bhavdata_full_$d.csv"
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return@withContext emptyList()
        
        parseBhavcopy(body, "NIFTY")
    }
    
    private fun parseBhavcopy(csv: String, symbol: String): List<Candle> {
        val reader = BufferedReader(StringReader(csv))
        val lines = try { reader.readLines().drop(1) } catch (e: Exception) { emptyList() }
        val candles = mutableListOf<Candle>()
        
        lines.forEach { line ->
            val p = line.split(",")
            if (p.size > 13 && p[0].trim() == symbol) {
                try {
                    val open = p[2].trim().toDouble()
                    val high = p[3].trim().toDouble()
                    val low = p[4].trim().toDouble()
                    val close = p[5].trim().toDouble()
                    val vol = p[8].trim().toLongOrNull() ?: 0
                    val ts = SimpleDateFormat("dd-MMM-yyyy", Locale.US).parse(p[10].trim())?.time ?: 0
                    candles.add(Candle(Price.from(open), Price.from(high), Price.from(low), Price.from(close), vol, ts, true))
                } catch (_: Exception) {}
            }
        }
        return candles.sortedBy { it.timestamp }
    }
}

// ===== ANGEL ONE HISTORICAL DATA =====
interface AngelHistoryApi {
    @POST("rest/secure/angelbroking/historical/v1/getCandleData")
    suspend fun candles(@Body r: CandleReq): retrofit2.Response<CandleResp>
}

class AngelDataFetcher(private val token: String) {
    private val api: AngelHistoryApi = retrofit2.Retrofit.Builder()
        .baseUrl("https://apiconnect.angelone.in/")
        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }.build())
        .build()
        .create(AngelHistoryApi::class.java)
    
    suspend fun fetchNifty1Min(from: String, to: String): List<Candle> {
        // Use default values for exchange, symboltoken, interval
        val resp = api.candles(CandleReq(fromdate = from, todate = to))
        if (!resp.isSuccessful) return emptyList()
        val data = resp.body()?.data ?: return emptyList()
        
        return data.mapNotNull { row ->
            if (row.size < 6) return@mapNotNull null
            try {
                val ts = parseAngelTs(row[0].toString())
                val open = row[1].toString().toDouble()
                val high = row[2].toString().toDouble()
                val low = row[3].toString().toDouble()
                val close = row[4].toString().toDouble()
                val vol = row[5].toString().toDouble().toLong()
                Candle(Price.from(open), Price.from(high), Price.from(low), Price.from(close), vol, ts, true)
            } catch (_: Exception) { null }
        }.sortedBy { it.timestamp }
    }
    
    private fun parseAngelTs(ts: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(ts)?.time ?: 0
        } catch (_: Exception) { 0 }
    }
    
    suspend fun fetchLastDays(days: Int): List<Candle> {
        val cal = Calendar.getInstance()
        val to = formatDate(cal)
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val from = formatDate(cal)
        return fetchNifty1Min(from, to)
    }
    
    private fun formatDate(cal: Calendar): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return fmt.format(cal.time)
    }
}
