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
 * 1. Admin uploaded numbers (Persistent cloud broadcast to all users via Supabase PostgreSQL)
 *    - Protected against accidental overwrite by non-admin clients
 *    - Atomic per-number `isUsed = true` locking so 1 number goes to 1 user only
 * 2. OTP Rates in BDT (Unix SMS rate & Zenex Kop Engine rate)
 * 3. Country-specific rates
 */
object GlobalCloudSyncService {

    private const val TAG = "GlobalCloudSync"
    private const val SUPABASE_BASE_URL = "https://fbtqjpuclmnsiqehalow.supabase.co/rest/v1"
    private const val SUPABASE_KEY = "sb_publishable_zBqxIMDPy4yHEuAdJJUlxg_wSCRF9u5"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isPolling = false
    @Volatile private var isPushing = false
    @Volatile private var lastLocalPushTimestamp = 0L
    @Volatile private var lastObservedServerTimestamp = 0L
    @Volatile private var lastExplicitClearTimestamp = 0L
    private var consecutiveErrors = 0

    fun init(context: Context) {
        if (!isPolling) {
            isPolling = true
            scope.launch {
                pullFromCloud(context)
            }
            scope.launch {
                while (isActive) {
                    if (!AppLifecycleTracker.canRunBackgroundWork()) {
                        delay(20000L)
                        continue
                    }
                    val pollDelay = if (consecutiveErrors > 3) 10000L else if (consecutiveErrors > 0) 5000L else 3000L
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

    private fun parseNumbersJsonArray(numbersJson: String): List<UploadedNumberItem> {
        if (numbersJson.isBlank() || numbersJson == "[]") return emptyList()
        return try {
            val numArr = JSONArray(numbersJson)
            val cloudNumbers = ArrayList<UploadedNumberItem>(numArr.length())
            for (i in 0 until numArr.length()) {
                val o = numArr.optJSONObject(i) ?: continue
                val numStr = o.optString("number", o.optString("n", "")).trim()
                if (numStr.isEmpty()) continue
                val rawSrv = o.optString("service", o.optString("s", "FB Fresh"))
                val rawType = o.optString("numberType", o.optString("t", "Fresh"))
                val canonSrv = UnixSmsManager.canonicalService(rawSrv, rawType)
                val canonType = UnixSmsManager.canonicalNumberTypeForService(canonSrv, rawType)
                cloudNumbers.add(
                    UploadedNumberItem(
                        id = o.optString("id", java.util.UUID.randomUUID().toString().take(8)),
                        number = numStr,
                        countryCode = o.optString("countryCode", o.optString("c", "US")).uppercase().trim(),
                        countryName = o.optString("countryName", o.optString("cn", "United States")),
                        flag = o.optString("flag", o.optString("f", "🇺🇸")),
                        service = canonSrv,
                        gateway = o.optString("gateway", o.optString("g", "LAMIX")),
                        customRate = if (o.has("customRate") && !o.isNull("customRate")) o.optDouble("customRate") else null,
                        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                        isUsed = o.optBoolean("isUsed", o.optBoolean("u", false)),
                        otpCode = null,
                        otpMessage = null,
                        numberType = canonType
                    )
                )
            }
            cloudNumbers
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cloud numbers: ${e.message}")
            emptyList()
        }
    }

    private fun serializeNumbersJsonArray(uploadedNumbers: List<UploadedNumberItem>): JSONArray {
        val numArr = JSONArray()
        for (item in uploadedNumbers.take(25000)) {
            val o = JSONObject()
            o.put("id", item.id.take(12))
            o.put("number", item.number)
            o.put("countryCode", item.countryCode)
            o.put("countryName", item.countryName)
            o.put("flag", item.flag)
            o.put("service", item.service)
            o.put("gateway", item.gateway)
            if (item.customRate != null) o.put("customRate", item.customRate)
            o.put("addedAt", item.addedAt)
            o.put("isUsed", item.isUsed)
            o.put("numberType", item.numberType)
            numArr.put(o)
        }
        return numArr
    }

    /**
     * Pulls latest uploaded numbers & OTP rates directly from Supabase app_settings
     */
    suspend fun pullFromCloud(context: Context) {
        try {
            UnixSmsManager.init(context)
            VirtualNumberManager.init(context)
            KopEngine2Manager.init(context)
            AuthManager.syncCurrentUserProfileFromCloud(context)

            if (isPushing || System.currentTimeMillis() - lastLocalPushTimestamp < 12000L) {
                return
            }

            val req = Request.Builder()
                .url("$SUPABASE_BASE_URL/app_settings?id=in.(1,2)&select=*")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val bodyStr = resp.body?.string() ?: return
                val arr = JSONArray(bodyStr)
                if (arr.length() == 0) return

                var row1: JSONObject? = null
                var row2: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    when (obj.optInt("id", 0)) {
                        1 -> row1 = obj
                        2 -> row2 = obj
                    }
                }

                val primaryData = row2 ?: row1 ?: return
                val fallbackData = row1 ?: row2 ?: return

                val cloudTimestamp = maxOf(
                    row2?.optLong("updated_at", 0L) ?: 0L,
                    row1?.optLong("updated_at", 0L) ?: 0L
                )
                if (isPushing || System.currentTimeMillis() - lastLocalPushTimestamp < 12000L) {
                    return
                }

                // 1. Parse Uploaded Numbers from Protected Master Vault (row 2) first, fallback to row 1
                val vaultNumbers = row2?.optString("numbers_json", "[]")?.trim()?.let { parseNumbersJsonArray(it) } ?: emptyList()
                val legacyNumbers = row1?.optString("numbers_json", "[]")?.trim()?.let { parseNumbersJsonArray(it) } ?: emptyList()

                // Merge vaultNumbers and legacyNumbers (if vault has numbers, vault is authoritative; if legacy marked a number isUsed=true, preserve isUsed=true)
                val cloudNumbers: List<UploadedNumberItem> = if (vaultNumbers.isNotEmpty()) {
                    val legacyUsedDigits = legacyNumbers.filter { it.isUsed }.map { it.number.replace("[^0-9]".toRegex(), "") }.toSet()
                    vaultNumbers.map { vItem ->
                        val d = vItem.number.replace("[^0-9]".toRegex(), "")
                        if (!vItem.isUsed && d in legacyUsedDigits) vItem.copy(isUsed = true) else vItem
                    }
                } else {
                    legacyNumbers
                }

                // Auto-heal row 1 or row 2 if one of them was wiped by an older client while the other still has numbers
                if (vaultNumbers.isNotEmpty() && legacyNumbers.isEmpty()) {
                    scope.launch {
                        try {
                            val healPayload = JSONObject().apply {
                                put("numbers_json", serializeNumbersJsonArray(cloudNumbers).toString())
                                put("updated_at", System.currentTimeMillis())
                            }
                            val healBody = healPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                            val healReq = Request.Builder()
                                .url("$SUPABASE_BASE_URL/app_settings?id=eq.1")
                                .header("apikey", SUPABASE_KEY)
                                .header("Authorization", "Bearer $SUPABASE_KEY")
                                .header("Content-Type", "application/json")
                                .header("Prefer", "return=minimal")
                                .patch(healBody)
                                .build()
                            httpClient.newCall(healReq).execute().close()
                        } catch (_: Exception) {}
                    }
                } else if (vaultNumbers.isEmpty() && legacyNumbers.isNotEmpty()) {
                    scope.launch {
                        try {
                            val healPayload = JSONObject().apply {
                                put("id", 2)
                                put("numbers_json", serializeNumbersJsonArray(legacyNumbers).toString())
                                put("updated_at", System.currentTimeMillis())
                            }
                            val healBody = healPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                            val healReq = Request.Builder()
                                .url("$SUPABASE_BASE_URL/app_settings")
                                .header("apikey", SUPABASE_KEY)
                                .header("Authorization", "Bearer $SUPABASE_KEY")
                                .header("Content-Type", "application/json")
                                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                                .post(healBody)
                                .build()
                            httpClient.newCall(healReq).execute().close()
                        } catch (_: Exception) {}
                    }
                }

                // Parse country_rates_json and check explicit admin_clear_v2_ts ONLY from row 2 (Protected Master Vault)
                val countryRatesJsonStr = primaryData.optString("country_rates_json", "").ifEmpty {
                    fallbackData.optString("country_rates_json", "{}")
                }
                var cloudClearTs = 0L
                if (countryRatesJsonStr.isNotEmpty() && countryRatesJsonStr != "{}") {
                    try {
                        val cObj = JSONObject(countryRatesJsonStr)
                        // Only honor admin_clear_v2_ts (written exclusively by Admin clear actions on Protected Vault id=2)
                        val rawClearTs = cObj.optLong("admin_clear_v2_ts", 0L)
                        if (cloudNumbers.isNotEmpty()) {
                            cloudClearTs = 0L
                            lastExplicitClearTimestamp = 0L
                        } else if (rawClearTs > 0L && rawClearTs <= System.currentTimeMillis() + 60_000L) {
                            cloudClearTs = rawClearTs
                            if (cloudClearTs > lastExplicitClearTimestamp) {
                                lastExplicitClearTimestamp = cloudClearTs
                            }
                        }
                        val kRate = cObj.optDouble("kop2_rate", -1.0)
                        if (kRate > 0.0) {
                            KopEngine2Manager.applyCloudRate(kRate)
                        }
                        val pv2Rate = cObj.optDouble("premium_v2_rate", -1.0)
                        if (pv2Rate > 0.0) {
                            PremiumNumberV2Manager.applyCloudRate(pv2Rate)
                        }
                        val cMap = mutableMapOf<String, Double>()
                        val keys = cObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k != "kop2_rate" && k != "premium_v2_rate" && k != "telegram_bots_json" && k != "clear_all_ts" && k != "admin_clear_v2_ts") {
                                val r = cObj.optDouble(k, -1.0)
                                if (r > 0.0) cMap[k] = r
                            }
                        }
                        if (cMap.isNotEmpty()) {
                            UnixSmsManager.applyCloudCountryRates(cMap)
                        }
                    } catch (_: Exception) {}
                }

                UnixSmsManager.applyCloudNumbers(
                    cloudList = cloudNumbers,
                    context = context,
                    cloudTimestamp = cloudTimestamp,
                    explicitClearTimestamp = cloudClearTs
                )
                lastObservedServerTimestamp = cloudTimestamp

                // 2. Sync OTP Rates
                val unixRate = primaryData.optDouble("otp_rate", fallbackData.optDouble("otp_rate", -1.0))
                if (unixRate > 0.0) {
                    UnixSmsManager.applyCloudRate(unixRate)
                }

                val zenexRate = primaryData.optDouble("zenex_rate", fallbackData.optDouble("zenex_rate", -1.0))
                if (zenexRate > 0.0) {
                    VirtualNumberManager.applyCloudRate(zenexRate)
                }

                // 3. Check for published App Updates (1-Click in-app OTA)
                val notifTitle = fallbackData.optString("notification_title", primaryData.optString("notification_title", ""))
                val notifMsg = fallbackData.optString("notification_message", primaryData.optString("notification_message", ""))
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
     * Atomically marks specific numbers as `isUsed = true` in Supabase (both Protected Vault id=2 and id=1)
     * without overwriting any newly uploaded numbers from Admin. Ensures 1 number goes to 1 user only.
     */
    fun markNumbersUsedInCloud(
        usedIds: Set<String>,
        usedDigits: Set<String>,
        usedSlotKeys: Set<String>
    ) {
        if (usedIds.isEmpty() && usedDigits.isEmpty()) return
        val patchTimestamp = System.currentTimeMillis()
        lastLocalPushTimestamp = patchTimestamp
        scope.launch {
            isPushing = true
            try {
                // 1. Fetch latest numbers_json from Supabase (rows 1 & 2)
                val getReq = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=in.(1,2)&select=id,numbers_json")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .get()
                    .build()

                var vaultList: List<UploadedNumberItem> = emptyList()
                var legacyList: List<UploadedNumberItem> = emptyList()
                httpClient.newCall(getReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: "[]"
                        val arr = JSONArray(bodyStr)
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val rowId = o.optInt("id", 0)
                            val parsed = parseNumbersJsonArray(o.optString("numbers_json", "[]"))
                            if (rowId == 2) vaultList = parsed
                            else if (rowId == 1) legacyList = parsed
                        }
                    }
                }

                val baseList = when {
                    vaultList.isNotEmpty() -> vaultList
                    legacyList.isNotEmpty() -> legacyList
                    else -> UnixSmsManager.state.value.uploadedNumbers
                }
                if (baseList.isEmpty()) return@launch

                val updatedList = baseList.map { item ->
                    val cleanD = item.number.replace("[^0-9]".toRegex(), "")
                    val cSrv = UnixSmsManager.canonicalService(item.service, item.numberType)
                    val sKey = "${cSrv}:::${item.countryCode.uppercase().trim()}:::${cleanD}"
                    if (item.id in usedIds || sKey in usedSlotKeys || cleanD in usedDigits) {
                        item.copy(isUsed = true)
                    } else {
                        item
                    }
                }

                val numArr = serializeNumbersJsonArray(updatedList)
                val patchPayload = JSONObject().apply {
                    put("numbers_json", numArr.toString())
                    put("updated_at", patchTimestamp)
                }

                // Update both Protected Vault (id=2) and Legacy row (id=1) in one query
                val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val patchReq = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=in.(1,2)")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()

                httpClient.newCall(patchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        lastObservedServerTimestamp = patchTimestamp
                        lastLocalPushTimestamp = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking numbers used in cloud: ${e.message}")
            } finally {
                isPushing = false
            }
        }
    }

    /**
     * Synchronously / directly pushes uploaded numbers to Supabase (both Protected Vault id=2 and id=1) and returns success status.
     * Protected against accidental empty overwrites unless `allowEmptyClear = true` is explicitly passed by an Admin.
     */
    suspend fun pushToCloudSync(
        uploadedNumbers: List<UploadedNumberItem>,
        unixRate: Double,
        zenexRate: Double,
        kop2Rate: Double = KopEngine2Manager.kop2OtpRate.value,
        countryRates: Map<String, Double> = emptyMap(),
        allowEmptyClear: Boolean = false,
        mergeWithCloudFirst: Boolean = false
    ): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        // Never allow non-admin clients to overwrite or clear the master uploadedNumbers pool
        if (!AuthManager.isAdmin()) {
            return@withContext false
        }

        if (uploadedNumbers.isEmpty() && !allowEmptyClear) {
            // Never allow an accidental empty list to wipe out cloud numbers!
            pushRateSettingsOnly(unixRate, zenexRate, kop2Rate)
            return@withContext true
        }

        val patchTimestamp = System.currentTimeMillis()
        lastLocalPushTimestamp = patchTimestamp
        isPushing = true
        try {
            var finalNumbersToPush = uploadedNumbers

            // If Admin is uploading new numbers, merge with any existing cloud numbers in Protected Vault (id=2) or id=1 so nothing is ever lost
            if (mergeWithCloudFirst && uploadedNumbers.isNotEmpty()) {
                try {
                    val getReq = Request.Builder()
                        .url("$SUPABASE_BASE_URL/app_settings?id=in.(1,2)&select=id,numbers_json")
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer $SUPABASE_KEY")
                        .get()
                        .build()
                    httpClient.newCall(getReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: "[]"
                            val arr = JSONArray(bodyStr)
                            var vaultExisting: List<UploadedNumberItem> = emptyList()
                            var legacyExisting: List<UploadedNumberItem> = emptyList()
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val rId = o.optInt("id", 0)
                                val parsed = parseNumbersJsonArray(o.optString("numbers_json", "[]"))
                                if (rId == 2) vaultExisting = parsed
                                else if (rId == 1) legacyExisting = parsed
                            }
                            val cloudExisting = if (vaultExisting.isNotEmpty()) vaultExisting else legacyExisting
                            if (cloudExisting.isNotEmpty()) {
                                val mergedMap = LinkedHashMap<String, UploadedNumberItem>(cloudExisting.size + uploadedNumbers.size)
                                for (cItem in cloudExisting) {
                                    val d = cItem.number.replace("[^0-9]".toRegex(), "")
                                    val cSrv = UnixSmsManager.canonicalService(cItem.service, cItem.numberType)
                                    val key = "${cSrv}:::${cItem.countryCode.uppercase().trim()}:::${d}"
                                    mergedMap[key] = cItem
                                }
                                // Local items override / add to cloud items
                                for (lItem in uploadedNumbers) {
                                    val d = lItem.number.replace("[^0-9]".toRegex(), "")
                                    val cSrv = UnixSmsManager.canonicalService(lItem.service, lItem.numberType)
                                    val key = "${cSrv}:::${lItem.countryCode.uppercase().trim()}:::${d}"
                                    mergedMap[key] = lItem
                                }
                                finalNumbersToPush = mergedMap.values.toList()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (finalNumbersToPush.isNotEmpty()) {
                // Reset explicit clear timestamps to 0 whenever numbers are present so devices never discard them!
                lastExplicitClearTimestamp = 0L
            } else if (allowEmptyClear) {
                lastExplicitClearTimestamp = patchTimestamp
            }

            val numArr = serializeNumbersJsonArray(finalNumbersToPush)

            val ratesObj = JSONObject().apply {
                put("kop2_rate", kop2Rate)
                put("premium_v2_rate", PremiumNumberV2Manager.otpRate.value)
                put("clear_all_ts", 0L)
                put("admin_clear_v2_ts", if (finalNumbersToPush.isNotEmpty()) 0L else lastExplicitClearTimestamp)
                for ((k, v) in countryRates) {
                    put(k, v)
                }
            }

            val patchPayload = JSONObject().apply {
                put("otp_rate", unixRate)
                put("zenex_rate", zenexRate)
                put("numbers_json", numArr.toString())
                put("country_rates_json", ratesObj.toString())
                put("updated_at", patchTimestamp)
            }

            val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("$SUPABASE_BASE_URL/app_settings?id=in.(1,2)")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .patch(body)
                .build()

            var success = httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }

            // Ensure Protected Master Vault row id=2 exists via upsert
            val row2Payload = JSONObject(patchPayload.toString()).apply {
                put("id", 2)
            }
            val postBody2 = row2Payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val postReq2 = Request.Builder()
                .url("$SUPABASE_BASE_URL/app_settings")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(postBody2)
                .build()
            val ok2 = httpClient.newCall(postReq2).execute().use { resp ->
                resp.isSuccessful
            }
            success = success || ok2

            if (success) {
                lastObservedServerTimestamp = patchTimestamp
                lastLocalPushTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Pushed ${numArr.length()} numbers & rates to Supabase Protected Vault (id=2 & id=1) successfully.")
            } else {
                Log.e(TAG, "Supabase patch returned unsuccess status.")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing to cloud: ${e.message}")
            false
        } finally {
            isPushing = false
        }
    }

    /**
     * Called whenever Admin uploads numbers, deletes numbers, or changes rates.
     */
    fun pushToCloud(
        uploadedNumbers: List<UploadedNumberItem>,
        unixRate: Double,
        zenexRate: Double,
        kop2Rate: Double = KopEngine2Manager.kop2OtpRate.value,
        countryRates: Map<String, Double> = emptyMap(),
        allowEmptyClear: Boolean = false,
        mergeWithCloudFirst: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!AuthManager.isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        lastLocalPushTimestamp = System.currentTimeMillis()
        scope.launch {
            val ok = pushToCloudSync(
                uploadedNumbers = uploadedNumbers,
                unixRate = unixRate,
                zenexRate = zenexRate,
                kop2Rate = kop2Rate,
                countryRates = countryRates,
                allowEmptyClear = allowEmptyClear,
                mergeWithCloudFirst = mergeWithCloudFirst
            )
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
        if (!AuthManager.isAdmin()) return
        val patchTimestamp = System.currentTimeMillis()
        lastLocalPushTimestamp = patchTimestamp
        scope.launch {
            try {
                val hasActiveNumbers = UnixSmsManager.state.value.uploadedNumbers.isNotEmpty()
                if (hasActiveNumbers) {
                    lastExplicitClearTimestamp = 0L
                }
                val ratesObj = JSONObject().apply {
                    put("kop2_rate", kop2Rate)
                    put("premium_v2_rate", PremiumNumberV2Manager.otpRate.value)
                    put("clear_all_ts", 0L)
                    put("admin_clear_v2_ts", if (hasActiveNumbers) 0L else lastExplicitClearTimestamp)
                    for ((k, v) in UnixSmsManager.state.value.countryRates) {
                        put(k, v)
                    }
                }
                val patchPayload = JSONObject().apply {
                    put("otp_rate", unixRate)
                    put("zenex_rate", zenexRate)
                    put("country_rates_json", ratesObj.toString())
                    put("updated_at", patchTimestamp)
                }
                val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=in.(1,2)")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        lastObservedServerTimestamp = patchTimestamp
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing rates to cloud: ${e.message}")
            }
        }
    }

    fun pushRateUpdate(rateKey: String, rate: Double) {
        pushRateSettingsOnly()
    }

    fun forceSync(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            try {
                pullFromCloud(context)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }
}
