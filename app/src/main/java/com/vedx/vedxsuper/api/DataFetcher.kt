package com.vedx.vedxsuper.api

import android.util.Log
import com.vedx.vedxsuper.data.Candle
import com.vedx.vedxsuper.data.Price
import kotlinx.coroutines.*
import okhttp3.*
import retrofit2.http.*
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ===== NSE FREE BHAVCOPY =====
object NseBhavcopy {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
    
    // ThreadLocal formatters are more efficient than creating new ones per call
    private val dateFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("ddMMyyyy", Locale.US)
    }
    
    private val csvDateFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
    }
    
    suspend fun fetch(date: Date = Date()): Result<List<Candle>> = withContext(Dispatchers.IO) {
        val d = dateFmt.get()?.format(date) ?: return@withContext Result.failure(IOException("Date format failed"))
        val url = "https://archives.nseindia.com/products/content/sec_bhavdata_full_$d.csv"
        val req = Request.Builder().url(url).build()
        
        try {
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext Result.failure(IOException("Empty response body"))
            if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}"))
            
            Result.success(parseBhavcopy(body, "NIFTY"))
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                    val ts = csvDateFmt.get()?.parse(p[10].trim())?.time ?: 0
                    candles.add(Candle(Price.from(open), Price.from(high), Price.from(low), Price.from(close), vol, ts, true))
                } catch (e: Exception) {
                    Log.e("NseBhavcopy", "Parse error for line: $line", e)
                }
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

class AngelDataFetcher(private val tokenProvider: () -> String) {
    private val api: AngelHistoryApi = retrofit2.Retrofit.Builder()
        .baseUrl("https://apiconnect.angelone.in/")
        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
        .client(OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", "Bearer ${tokenProvider()}")
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }.build())
        .build()
        .create(AngelHistoryApi::class.java)
    
    private val angelTsFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    }

    private val formatFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    suspend fun fetchNifty1Min(from: String, to: String): Result<List<Candle>> {
        return try {
            val resp = api.candles(CandleReq(fromdate = from, todate = to))
            if (!resp.isSuccessful) return Result.failure(IOException("Angel API Error: ${resp.code()} ${resp.errorBody()?.string()}"))
            val data = resp.body()?.data ?: return Result.success(emptyList())
            
            val candles = data.mapNotNull { row ->
                if (row.size < 6) return@mapNotNull null
                try {
                    val ts = parseAngelTs(row[0].toString())
                    val open = row[1].toString().toDouble()
                    val high = row[2].toString().toDouble()
                    val low = row[3].toString().toDouble()
                    val close = row[4].toString().toDouble()
                    val vol = row[5].toString().toDouble().toLong()
                    Candle(Price.from(open), Price.from(high), Price.from(low), Price.from(close), vol, ts, true)
                } catch (e: Exception) {
                    Log.e("AngelDataFetcher", "Parsing error in row: $row", e)
                    null
                }
            }.sortedBy { it.timestamp }
            Result.success(candles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseAngelTs(ts: String): Long {
        return try {
            angelTsFmt.get()?.parse(ts)?.time ?: 0
        } catch (e: Exception) {
            Log.e("AngelDataFetcher", "Invalid timestamp format: $ts", e)
            0
        }
    }
    
    suspend fun fetchLastDays(days: Int): Result<List<Candle>> {
        val cal = Calendar.getInstance()
        val to = formatDate(cal)
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val from = formatDate(cal)
        return fetchNifty1Min(from, to)
    }
    
    private fun formatDate(cal: Calendar): String {
        return formatFmt.get()?.format(cal.time) ?: ""
    }
}
