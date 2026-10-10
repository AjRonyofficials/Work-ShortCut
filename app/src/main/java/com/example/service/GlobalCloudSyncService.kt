package com.example.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Realtime Global Cloud Synchronization Service
 *
 * Synchronizes across all distributed user devices:
 * 1. Admin uploaded numbers (Instant cloud broadcast to all users)
 * 2. OTP Rates in BDT (Unix SMS rate & Zenex Kop Engine rate)
 * 3. Country-specific rates
 *
 * Ensures all connected users instantly receive newly uploaded numbers
 * and rate updates even when on completely separate phones/networks.
 */
object GlobalCloudSyncService {

    private const val TAG = "GlobalCloudSync"
    // Persistent Cloud REST Synchronizer Object for global state
    private const val CLOUD_OBJECT_URL = "https://api.restful-api.dev/objects/ff808181a09d98f701a12710e98a3771"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isPolling = false
    private var lastLocalPushTimestamp = 0L

    fun init(context: Context) {
        if (!isPolling) {
            isPolling = true
            // Initial sync immediately
            scope.launch {
                pullFromCloud(context)
            }
            // Continuous polling every 4 seconds for instant real-time sync across all users
            scope.launch {
                while (isActive) {
                    delay(4000L)
                    try {
                        pullFromCloud(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Pull error: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Pulls latest uploaded numbers & OTP rates from cloud
     */
    suspend fun pullFromCloud(context: Context) {
        try {
            val req = Request.Builder()
                .url(CLOUD_OBJECT_URL)
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: return
                val root = JSONObject(bodyStr)
                val data = root.optJSONObject("data") ?: return

                val cloudTimestamp = data.optLong("updated_at", 0L)
                // If local just pushed within 2 seconds, avoid race overwrite
                if (System.currentTimeMillis() - lastLocalPushTimestamp < 2000L) {
                    return
                }

                // 1. Sync Uploaded Numbers
                val numbersJson = data.optString("numbers_json", "")
                if (numbersJson.isNotEmpty()) {
                    val arr = JSONArray(numbersJson)
                    val cloudNumbers = mutableListOf<UploadedNumberItem>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        cloudNumbers.add(
                            UploadedNumberItem(
                                id = o.optString("id", java.util.UUID.randomUUID().toString()),
                                number = o.getString("number"),
                                countryCode = o.optString("countryCode", "US"),
                                countryName = o.optString("countryName", "United States"),
                                flag = o.optString("flag", "🇺🇸"),
                                service = o.optString("service", "Facebook"),
                                addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                                isUsed = o.optBoolean("isUsed", false),
                                otpCode = if (o.has("otpCode") && !o.isNull("otpCode")) o.optString("otpCode") else null,
                                otpMessage = if (o.has("otpMessage") && !o.isNull("otpMessage")) o.optString("otpMessage") else null
                            )
                        )
                    }
                    // Apply to UnixSmsManager
                    UnixSmsManager.applyCloudNumbers(cloudNumbers)
                }

                // 2. Sync OTP Rates
                val unixRate = data.optDouble("unix_rate", -1.0)
                if (unixRate > 0.0) {
                    UnixSmsManager.applyCloudRate(unixRate)
                }

                val zenexRate = data.optDouble("zenex_rate", -1.0)
                if (zenexRate > 0.0) {
                    VirtualNumberManager.applyCloudRate(zenexRate)
                }

                // 3. Sync Country Rates
                val countryRatesStr = data.optString("country_rates_json", "")
                if (countryRatesStr.isNotEmpty()) {
                    val cObj = JSONObject(countryRatesStr)
                    val cMap = mutableMapOf<String, Double>()
                    val keys = cObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        cMap[k] = cObj.optDouble(k, 0.50)
                    }
                    if (cMap.isNotEmpty()) {
                        UnixSmsManager.applyCloudCountryRates(cMap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull from cloud failed: ${e.message}")
        }
    }

    /**
     * Called whenever Admin uploads numbers, deletes numbers, or changes rates.
     * Pushes state to cloud immediately so all users receive it.
     */
    fun pushToCloud(
        uploadedNumbers: List<UploadedNumberItem>,
        unixRate: Double,
        zenexRate: Double,
        countryRates: Map<String, Double>
    ) {
        lastLocalPushTimestamp = System.currentTimeMillis()
        scope.launch {
            try {
                val numArr = JSONArray()
                // Take up to 250 numbers to maintain lightweight payload
                for (item in uploadedNumbers.take(250)) {
                    val o = JSONObject()
                    o.put("id", item.id)
                    o.put("number", item.number)
                    o.put("countryCode", item.countryCode)
                    o.put("countryName", item.countryName)
                    o.put("flag", item.flag)
                    o.put("service", item.service)
                    o.put("addedAt", item.addedAt)
                    o.put("isUsed", item.isUsed)
                    item.otpCode?.let { o.put("otpCode", it) }
                    item.otpMessage?.let { o.put("otpMessage", it) }
                    numArr.put(o)
                }

                val countryObj = JSONObject()
                for ((k, v) in countryRates) {
                    countryObj.put(k, v)
                }

                val putPayload = JSONObject().apply {
                    put("name", "work_shortcut_global_cloud_sync")
                    put("data", JSONObject().apply {
                        put("numbers_json", numArr.toString())
                        put("unix_rate", unixRate)
                        put("zenex_rate", zenexRate)
                        put("country_rates_json", countryObj.toString())
                        put("updated_at", System.currentTimeMillis())
                    })
                }

                val body = putPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url(CLOUD_OBJECT_URL)
                    .put(body)
                    .build()
                httpClient.newCall(req).execute()
                Log.d(TAG, "Pushed ${numArr.length()} numbers & rates to cloud successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Push to cloud failed: ${e.message}")
            }
        }
    }
}
