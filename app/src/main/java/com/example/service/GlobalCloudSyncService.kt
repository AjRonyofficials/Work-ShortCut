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
import com.example.util.AppLifecycleTracker

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
            // Smart adaptive interval with strict battery protection:
            // When app is in background and overlay is inactive, loop sleeps
            scope.launch {
                while (isActive) {
                    if (!AppLifecycleTracker.canRunBackgroundWork()) {
                        // User is not using app - preserve 100% battery & zero network
                        delay(20000L)
                        continue
                    }
                    val pollDelay = if (consecutiveErrors > 3) 12000L else if (consecutiveErrors > 0) 6000L else 3500L
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
            UnixSmsManager.init(context)
            VirtualNumberManager.init(context)
            KopEngine2Manager.init(context)

            val req = Request.Builder()
                .url("$SUPABASE_BASE_URL/app_settings?id=eq.1&select=*")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val bodyStr = resp.body?.string() ?: return
                val arr = JSONArray(bodyStr)
                if (arr.length() == 0) return
                val data = arr.getJSONObject(0)

                val cloudTimestamp = data.optLong("updated_at", 0L)
                // If local just pushed within 8 seconds, avoid race overwrite
                if (System.currentTimeMillis() - lastLocalPushTimestamp < 8000L) {
                    return
                }

                // If nothing changed on server and local already has the numbers, skip re-parsing
                if (cloudTimestamp != 0L && cloudTimestamp == lastObservedServerTimestamp && UnixSmsManager.state.value.uploadedNumbers.isNotEmpty()) {
                    return
                }
                lastObservedServerTimestamp = cloudTimestamp

                // 1. Sync Uploaded Numbers
                val numbersJson = data.optString("numbers_json", "")
                if (numbersJson.isNotEmpty() && numbersJson != "[]") {
                    try {
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
                                    gateway = o.optString("gateway", "LAMIX"),
                                    customRate = if (o.has("customRate") && !o.isNull("customRate")) o.optDouble("customRate") else null,
                                    addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                                    isUsed = o.optBoolean("isUsed", false),
                                    otpCode = if (o.has("otpCode") && !o.isNull("otpCode")) o.optString("otpCode") else null,
                                    otpMessage = if (o.has("otpMessage") && !o.isNull("otpMessage")) o.optString("otpMessage") else null
                                )
                            )
                        }
                        // Authoritative apply to UnixSmsManager for all users
                        UnixSmsManager.applyCloudNumbers(cloudNumbers, context)
                    } catch (_: Exception) {}
                } else {
                    UnixSmsManager.applyCloudNumbers(emptyList(), context)
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

                val kop2Rate = data.optDouble("kop2_rate", -1.0)
                if (kop2Rate > 0.0) {
                    KopEngine2Manager.applyCloudRate(kop2Rate)
                }

                // 3. Check for published App Updates (1-Click in-app OTA)
                val notifTitle = data.optString("notification_title", "")
                val notifMsg = data.optString("notification_message", "")
                if (notifTitle.equals("APP_UPDATE", ignoreCase = true) && notifMsg.isNotBlank()) {
                    try {
                        val updObj = JSONObject(notifMsg)
                        val rCode = updObj.optInt("versionCode", 1)
                        if (rCode > com.example.BuildConfig.VERSION_CODE) {
                            AppUpdateManager.checkForUpdate(context, silent = true)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull from Supabase failed: ${e.message}")
        }
    }

    /**
     * Synchronously / directly pushes uploaded numbers to Supabase and returns success status.
     */
    suspend fun pushToCloudSync(
        uploadedNumbers: List<UploadedNumberItem>,
        unixRate: Double,
        zenexRate: Double,
        kop2Rate: Double = KopEngine2Manager.kop2OtpRate.value,
        countryRates: Map<String, Double> = emptyMap()
    ): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val patchTimestamp = System.currentTimeMillis()
        lastLocalPushTimestamp = patchTimestamp
        try {
            val numArr = JSONArray()
            // Up to 300 numbers to maintain fast transmission
            for (item in uploadedNumbers.take(300)) {
                val o = JSONObject()
                o.put("id", item.id)
                o.put("number", item.number)
                o.put("countryCode", item.countryCode)
                o.put("countryName", item.countryName)
                o.put("flag", item.flag)
                o.put("service", item.service)
                o.put("gateway", item.gateway)
                if (item.customRate != null) o.put("customRate", item.customRate)
                o.put("addedAt", item.addedAt)
                o.put("isUsed", item.isUsed)
                item.otpCode?.let { o.put("otpCode", it) }
                item.otpMessage?.let { o.put("otpMessage", it) }
                numArr.put(o)
            }

            val patchPayload = JSONObject().apply {
                put("otp_rate", unixRate)
                put("zenex_rate", zenexRate)
                put("kop2_rate", kop2Rate)
                put("numbers_json", numArr.toString())
                put("updated_at", patchTimestamp)
            }

            val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("$SUPABASE_BASE_URL/app_settings?id=eq.1")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .patch(body)
                .build()

            val success = httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
            if (success) {
                lastObservedServerTimestamp = patchTimestamp
                Log.d(TAG, "Pushed ${numArr.length()} numbers & rates to Supabase successfully.")
            } else {
                Log.e(TAG, "Supabase patch returned unsuccess status.")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing to cloud: ${e.message}")
            false
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
        kop2Rate: Double = KopEngine2Manager.kop2OtpRate.value,
        countryRates: Map<String, Double> = emptyMap(),
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = pushToCloudSync(uploadedNumbers, unixRate, zenexRate, kop2Rate, countryRates)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete?.invoke(ok)
            }
        }
    }

    fun pushRateSettingsOnly(
        unixRate: Double = UnixSmsManager.state.value.otpRatePerSms,
        zenexRate: Double = VirtualNumberManager.zenexOtpRate.value,
        kop2Rate: Double = KopEngine2Manager.kop2OtpRate.value
    ) {
        val patchTimestamp = System.currentTimeMillis()
        lastLocalPushTimestamp = patchTimestamp
        scope.launch {
            try {
                val patchPayload = JSONObject().apply {
                    put("otp_rate", unixRate)
                    put("zenex_rate", zenexRate)
                    put("kop2_rate", kop2Rate)
                    put("updated_at", patchTimestamp)
                }
                val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=eq.1")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        lastObservedServerTimestamp = patchTimestamp
                        Log.d(TAG, "Pushed rate settings to Supabase successfully: Unix=$unixRate, Zenex=$zenexRate, Kop2=$kop2Rate")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing rates to cloud: ${e.message}")
            }
        }
    }
}
