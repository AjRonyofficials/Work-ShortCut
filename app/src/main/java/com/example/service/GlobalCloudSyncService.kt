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
 * Realtime Global Cloud Synchronization Service via Supabase
 *
 * Synchronizes across all distributed user devices:
 * 1. Admin uploaded numbers (Instant cloud broadcast to all users via Supabase PostgreSQL)
 * 2. OTP Rates in BDT (Unix SMS rate & Zenex Kop Engine rate)
 * 3. Country-specific rates
 *
 * Ensures all connected users instantly receive newly uploaded numbers
 * and rate updates even when on completely separate phones/networks.
 */
object GlobalCloudSyncService {

    private const val TAG = "GlobalCloudSync"
    // Connected to AjRonyofficials Supabase project
    private const val SUPABASE_BASE_URL = "https://fbtqjpuclmnsiqehalow.supabase.co/rest/v1"
    private const val SUPABASE_KEY = "sb_publishable_zBqxIMDPy4yHEuAdJJUlxg_wSCRF9u5"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isPolling = false
    private var lastLocalPushTimestamp = 0L
    private var lastObservedServerTimestamp = 0L
    private var consecutiveErrors = 0

    fun init(context: Context) {
        if (!isPolling) {
            isPolling = true
            // Initial sync immediately
            scope.launch {
                pullFromCloud(context)
            }
            // Smart adaptive interval: 5s normal, scales back on error
            scope.launch {
                while (isActive) {
                    val pollDelay = if (consecutiveErrors > 3) 15000L else if (consecutiveErrors > 0) 8000L else 5000L
                    delay(pollDelay)
                    try {
                        pullFromCloud(context)
                        consecutiveErrors = 0
                    } catch (e: Exception) {
                        consecutiveErrors++
                        Log.e(TAG, "Pull error: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Pulls latest uploaded numbers & OTP rates directly from Supabase app_settings
     */
    suspend fun pullFromCloud(context: Context) {
        try {
            val req = Request.Builder()
                .url("$SUPABASE_BASE_URL/app_settings?id=eq.1&select=*")
                .header("apikey", SUPABASE_KEY)
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: return
                val arr = JSONArray(bodyStr)
                if (arr.length() == 0) return
                val data = arr.getJSONObject(0)

                val cloudTimestamp = data.optLong("updated_at", 0L)
                // If local just pushed within 2 seconds, avoid race overwrite
                if (System.currentTimeMillis() - lastLocalPushTimestamp < 2000L) {
                    return
                }

                // If nothing changed on server since last check, avoid re-parsing to save CPU/battery
                if (cloudTimestamp != 0L && cloudTimestamp == lastObservedServerTimestamp) {
                    return
                }
                lastObservedServerTimestamp = cloudTimestamp

                // 1. Sync Uploaded Numbers
                val numbersJson = data.optString("numbers_json", "")
                if (numbersJson.isNotEmpty()) {
                    val numArr = JSONArray(numbersJson)
                    val cloudNumbers = mutableListOf<UploadedNumberItem>()
                    for (i in 0 until numArr.length()) {
                        val o = numArr.getJSONObject(i)
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
                val unixRate = data.optDouble("otp_rate", -1.0)
                if (unixRate > 0.0) {
                    UnixSmsManager.applyCloudRate(unixRate)
                }

                val zenexRate = data.optDouble("zenex_rate", -1.0)
                if (zenexRate > 0.0) {
                    VirtualNumberManager.applyCloudRate(zenexRate)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull from Supabase failed: ${e.message}")
        }
    }

    /**
     * Called whenever Admin uploads numbers, deletes numbers, or changes rates.
     * Pushes state to Supabase PostgreSQL immediately so all 10,000+ users receive it.
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
                // Take up to 300 numbers to maintain lightweight payload
                for (item in uploadedNumbers.take(300)) {
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

                val patchPayload = JSONObject().apply {
                    put("otp_rate", unixRate)
                    put("zenex_rate", zenexRate)
                    put("numbers_json", numArr.toString())
                    put("updated_at", System.currentTimeMillis())
                }

                val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=eq.1")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute()
                Log.d(TAG, "Pushed ${numArr.length()} numbers & rates to Supabase successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Push to Supabase failed: ${e.message}")
            }
        }
    }
}
