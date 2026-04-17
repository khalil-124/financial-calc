package com.khalil.calc.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

object LiveRatesEngine {

    // Renamed from FedRate to be more generic
    data class LiveRate(
        val rate: Double,
        val date: String,
        val isSuccess: Boolean,
        val source: String = "",
        val errorMessage: String? = null
    )

    // إصلاح جلب الفائدة الفيدرالية (إضافة User-Agent لمنع الحظر 403)
    suspend fun fetchFedRate(): LiveRate = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://markets.newyorkfed.org/api/rates/secured/sofr/last/1.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // إضافة User-Agent ضروري جداً لتجنب الحظر من سيرفر الفيدرالي
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val ratesArray = json.getJSONObject("refRates").getJSONArray("refRates")
                if (ratesArray.length() > 0) {
                    val latestEntry = ratesArray.getJSONObject(0)
                    val rate = latestEntry.getDouble("percentRate")
                    val date = latestEntry.getString("effectiveDate")
                    return@withContext LiveRate(rate, date, true, "Fed")
                }
            }
            return@withContext LiveRate(4.55, "Offline", false, "Fed", "HTTP ${connection.responseCode}")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext LiveRate(4.55, "Offline", false, "Fed", e.message)
        }
    }

    // جلب فائدة البنك المركزي الأردني (CBJ)
    suspend fun fetchCBJRate(): LiveRate = withContext(Dispatchers.IO) {
        try {
            // نستخدم https لمنع خطأ Cleartext HTTP traffic في الأندرويد
            val url = URL("https://dataportal.cbj.gov.jo/api/1.0/data/get?dataset=interest-rates-on-monetary-policy-instruments-cbj&frequency=M&dim-indicator=CBJMRERP")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val dataArray = json.getJSONArray("data")
                if (dataArray.length() > 0) {
                    var latestRate = 0.0
                    var latestDateStr = ""
                    
                    // المرور على كافة البيانات برمجياً للعثور على أحدث تاريخ دون الاعتماد على ترتيب الـ API
                    for (i in 0 until dataArray.length()) {
                        val entry = dataArray.getJSONObject(i)
                        val currentStr = entry.getString("Date")
                        if (latestDateStr.isEmpty() || currentStr > latestDateStr) {
                            latestDateStr = currentStr
                            latestRate = entry.getDouble("Value")
                        }
                    }

                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val parsedDate = inputFormat.parse(latestDateStr)
                    val formattedDate = parsedDate?.let { outputFormat.format(it) } ?: latestDateStr

                    return@withContext LiveRate(latestRate, formattedDate, true, "CBJ")
                }
            }
            return@withContext LiveRate(5.75, "Offline", false, "CBJ", "HTTP ${connection.responseCode}")
        } catch (e: Exception) {
            e.printStackTrace()
            // الفائدة الحالية للبنك المركزي الأردني كقيمة احتياطية في حال انقطاع النت
            return@withContext LiveRate(5.75, "Offline", false, "CBJ", e.message)
        }
    }
}
