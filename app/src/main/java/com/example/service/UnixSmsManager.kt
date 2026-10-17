package com.example.service

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ProvisionedNumber
import com.example.util.ClipboardHelper
import com.example.util.OtpNotificationHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class UploadedNumberItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val countryCode: String, // e.g. "US", "BD", "CM", "GB"
    val countryName: String,
    val flag: String,
    val service: String, // "Facebook", "Instagram", "WhatsApp"
    val gateway: String = "LAMIX", // "LAMIX" or "UNIX"
    val customRate: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val isUsed: Boolean = false,
    val otpCode: String? = null,
    val otpMessage: String? = null
)

data class UnixSmsCdrRecord(
    val dt: String,
    val num: String,
    val cli: String,
    val message: String,
    val payout: String
)

data class UnixSmsState(
    val apiToken: String = "simple_v2_fQxnBh-RLx8BIZS2Zjx8TGVLYLv6aqPkATsrIqEKFbxIaowb",
    val lamixApiToken: String = "pMRolCWFnQI0PjGibIKWOpeefLn8HbS6AqGS3BqPZlg",
    val selectedUploadGateway: String = "LAMIX", // "LAMIX" or "UNIX"
    val uploadedNumbers: List<UploadedNumberItem> = emptyList(),
    val activeNumbers: List<ProvisionedNumber> = emptyList(),
    val liveCdrRecords: List<UnixSmsCdrRecord> = emptyList(),
    val isPolling: Boolean = false,
    val selectedCountry: String = "ALL",
    val selectedService: String = "Facebook",
    val todayOtpCount: Int = 0,
    val otpRatePerSms: Double = 0.50, // Default 0.50 ৳
    val countryRates: Map<String, Double> = mapOf(
        "BD" to 0.50,
        "US" to 0.50,
        "GB" to 0.50,
        "CA" to 0.49,
        "IN" to 0.49,
        "NG" to 0.48,
        "CM" to 0.48,
        "KE" to 0.48,
        "ID" to 0.49,
        "PH" to 0.49,
        "FR" to 0.50,
        "DE" to 0.50,
        "PK" to 0.48,
        "BR" to 0.49,
        "VN" to 0.49,
        "RU" to 0.50
    ),
    val requestCount: Int = 1,
    val lastSyncTime: Long = 0L,
    val lastError: String? = null
)

object UnixSmsManager {

    private const val PREFS_NAME = "unix_sms_manager_prefs"
    private const val KEY_API_TOKEN = "unix_api_token"
    private const val KEY_LAMIX_API_TOKEN = "lamix_api_token"
    private const val KEY_SELECTED_UPLOAD_GATEWAY = "unix_selected_upload_gateway"
    private const val KEY_UPLOADED_NUMBERS = "uploaded_numbers_json"
    private const val KEY_ACTIVE_NUMBERS = "active_numbers_json"
    private const val KEY_TODAY_OTP = "today_otp_unix"
    private const val KEY_OTP_RATE = "unix_otp_rate_setting"
    private const val KEY_SAVED_COUNTRY = "unix_saved_country"
    private const val KEY_COUNTRY_RATES = "unix_country_rates_json"
    const val DEFAULT_TOKEN = "simple_v2_fQxnBh-RLx8BIZS2Zjx8TGVLYLv6aqPkATsrIqEKFbxIaowb"
    const val DEFAULT_LAMIX_TOKEN = "pMRolCWFnQI0PjGibIKWOpeefLn8HbS6AqGS3BqPZlg"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var prefs: SharedPreferences? = null
    private var pollingJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val otpRegex = Pattern.compile("\\b\\d{4,8}\\b")

    private val _state = MutableStateFlow(UnixSmsState())
    val state: StateFlow<UnixSmsState> = _state.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val token = prefs?.getString(KEY_API_TOKEN, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
            val lamixToken = prefs?.getString(KEY_LAMIX_API_TOKEN, DEFAULT_LAMIX_TOKEN) ?: DEFAULT_LAMIX_TOKEN
            val gateway = prefs?.getString(KEY_SELECTED_UPLOAD_GATEWAY, "LAMIX") ?: "LAMIX"
            val todayOtp = prefs?.getInt(KEY_TODAY_OTP, 0) ?: 0
            val rawSavedRate = prefs?.getFloat(KEY_OTP_RATE, 0.50f)?.toDouble() ?: 0.50
            val savedRate = if (rawSavedRate < 0.1) 0.50 else rawSavedRate
            val uploaded = loadUploadedNumbers()
            val active = loadActiveNumbers()
            val savedCountry = prefs?.getString(KEY_SAVED_COUNTRY, "ALL") ?: "ALL"
            val loadedCountryRates = loadCountryRates()

            _state.update { current ->
                val finalUploaded = if (current.uploadedNumbers.isNotEmpty()) {
                    current.uploadedNumbers
                } else {
                    uploaded
                }
                current.copy(
                    apiToken = token,
                    lamixApiToken = lamixToken,
                    selectedUploadGateway = gateway,
                    uploadedNumbers = finalUploaded,
                    activeNumbers = if (current.activeNumbers.isNotEmpty()) current.activeNumbers else active,
                    selectedCountry = savedCountry,
                    todayOtpCount = todayOtp,
                    otpRatePerSms = savedRate,
                    countryRates = loadedCountryRates
                )
            }

            if (_state.value.uploadedNumbers.isNotEmpty()) {
                saveUploadedNumbers(_state.value.uploadedNumbers)
            }

            startFastOtpPolling(context)
        }
    }

    fun getRateForCountry(countryCode: String): Double {
        val upper = countryCode.uppercase().trim()
        return _state.value.countryRates[upper] ?: _state.value.otpRatePerSms
    }

    fun setCountryRate(countryCode: String, rate: Double) {
        val upper = countryCode.uppercase().trim()
        val cleanRate = (Math.round(rate.coerceAtLeast(0.01) * 100.0) / 100.0)
        val updated = _state.value.countryRates.toMutableMap()
        updated[upper] = cleanRate
        _state.update { it.copy(countryRates = updated) }
        saveCountryRates(updated)
        // Push globally to all devices
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = _state.value.uploadedNumbers,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = updated
        )
    }

    fun applyCloudNumbers(cloudList: List<UploadedNumberItem>, context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
        if (cloudList.isEmpty()) {
            _state.update { it.copy(uploadedNumbers = emptyList()) }
            saveUploadedNumbers(emptyList())
            return
        }
        val current = _state.value.uploadedNumbers
        val currentMap = current.associateBy { it.number }
        // Cloud list is authoritative so all users have fresh access to uploaded pool
        val merged = cloudList.map { cloudItem ->
            val local = currentMap[cloudItem.number]
            cloudItem.copy(
                otpCode = cloudItem.otpCode ?: local?.otpCode,
                otpMessage = cloudItem.otpMessage ?: local?.otpMessage
            )
        }
        val cloudDigits = cloudList.map { it.number.replace("[^0-9]".toRegex(), "") }.toSet()
        val cleanedActive = _state.value.activeNumbers.filter { prov ->
            !prov.otpCode.isNullOrEmpty() || cloudDigits.any { isPhoneNumberMatch(it, prov.number) }
        }
        _state.update { it.copy(uploadedNumbers = merged, activeNumbers = cleanedActive) }
        saveUploadedNumbers(merged)
        saveActiveNumbers(cleanedActive)
    }

    fun applyCloudRate(newRate: Double) {
        if (newRate <= 0.0) return
        val cleanRate = (Math.round(newRate * 100.0) / 100.0)
        if (Math.abs(_state.value.otpRatePerSms - cleanRate) > 0.001) {
            _state.update { it.copy(otpRatePerSms = cleanRate) }
            prefs?.edit()?.putFloat(KEY_OTP_RATE, cleanRate.toFloat())?.apply()
        }
    }

    fun applyCloudCountryRates(newRates: Map<String, Double>) {
        if (newRates.isEmpty()) return
        val updated = _state.value.countryRates.toMutableMap()
        var changed = false
        for ((k, v) in newRates) {
            val cleanRate = (Math.round(v * 100.0) / 100.0)
            if (updated[k] != cleanRate) {
                updated[k] = cleanRate
                changed = true
            }
        }
        if (changed) {
            _state.update { it.copy(countryRates = updated) }
            saveCountryRates(updated)
        }
    }

    private fun loadCountryRates(): Map<String, Double> {
        val json = prefs?.getString(KEY_COUNTRY_RATES, "") ?: ""
        if (json.isEmpty()) return _state.value.countryRates
        val map = mutableMapOf<String, Double>()
        try {
            val obj = org.json.JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val r = obj.optDouble(k, 0.50)
                map[k] = if (r < 0.1) 0.50 else r
            }
        } catch (_: Exception) {}
        return if (map.isEmpty()) _state.value.countryRates else map
    }

    private fun saveCountryRates(rates: Map<String, Double>) {
        val obj = org.json.JSONObject()
        for ((k, v) in rates) {
            obj.put(k, v)
        }
        prefs?.edit()?.putString(KEY_COUNTRY_RATES, obj.toString())?.apply()
    }

    private fun loadUploadedNumbers(): List<UploadedNumberItem> {
        val json = prefs?.getString(KEY_UPLOADED_NUMBERS, "[]") ?: "[]"
        val list = mutableListOf<UploadedNumberItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
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
                        otpCode = if (o.has("otpCode")) o.optString("otpCode") else null,
                        otpMessage = if (o.has("otpMessage")) o.optString("otpMessage") else null
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveUploadedNumbers(list: List<UploadedNumberItem>) {
        val arr = JSONArray()
        for (item in list) {
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
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_UPLOADED_NUMBERS, arr.toString())?.apply()
    }

    private fun loadActiveNumbers(): List<ProvisionedNumber> {
        val json = prefs?.getString(KEY_ACTIVE_NUMBERS, "[]") ?: "[]"
        val list = mutableListOf<ProvisionedNumber>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ProvisionedNumber(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        number = o.getString("number"),
                        country = o.optString("country", "United States"),
                        operator = o.optString("operator", "Lamix SMS ⚡"),
                        iso = o.optString("iso", "us"),
                        status = o.optString("status", "pending"),
                        otpCode = if (o.has("otpCode")) o.optString("otpCode") else null,
                        otpMessage = if (o.has("otpMessage")) o.optString("otpMessage") else null,
                        range = o.optString("range", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        expiresAt = o.optLong("expiresAt", System.currentTimeMillis() + 1_200_000L),
                        gateway = o.optString("gateway", "LAMIX"),
                        rate = o.optDouble("rate", 0.50)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveActiveNumbers(list: List<ProvisionedNumber>) {
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("number", item.number)
            o.put("country", item.country)
            o.put("operator", item.operator)
            o.put("iso", item.iso)
            o.put("status", item.status)
            item.otpCode?.let { o.put("otpCode", it) }
            item.otpMessage?.let { o.put("otpMessage", it) }
            o.put("range", item.range)
            o.put("timestamp", item.timestamp)
            o.put("expiresAt", item.expiresAt)
            o.put("gateway", item.gateway)
            o.put("rate", item.rate)
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_ACTIVE_NUMBERS, arr.toString())?.apply()
    }

    fun setApiToken(token: String) {
        val clean = token.trim()
        _state.update { it.copy(apiToken = clean) }
        prefs?.edit()?.putString(KEY_API_TOKEN, clean)?.apply()
    }

    fun setLamixApiToken(token: String) {
        val clean = token.trim()
        _state.update { it.copy(lamixApiToken = clean) }
        prefs?.edit()?.putString(KEY_LAMIX_API_TOKEN, clean)?.apply()
    }

    fun setSelectedUploadGateway(gateway: String) {
        val clean = gateway.trim().uppercase()
        _state.update { it.copy(selectedUploadGateway = clean) }
        prefs?.edit()?.putString(KEY_SELECTED_UPLOAD_GATEWAY, clean)?.apply()
    }

    /**
     * Admin Panel: Adjust User OTP Rate (e.g. $0.014, increase or decrease)
     */
    fun setOtpRate(rate: Double) {
        val cleanRate = (Math.round(rate.coerceAtLeast(0.01) * 100.0) / 100.0)
        _state.update { it.copy(otpRatePerSms = cleanRate) }
        prefs?.edit()?.putFloat(KEY_OTP_RATE, cleanRate.toFloat())?.apply()
        // Push globally to all devices
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = _state.value.uploadedNumbers,
            unixRate = cleanRate,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates
        )
    }

    fun adjustOtpRate(delta: Double) {
        val current = _state.value.otpRatePerSms
        setOtpRate((Math.round((current + delta) * 100.0) / 100.0))
    }

    /**
     * Admin Panel: Add numbers via plain text or file content
     * Formats supported:
     * 1 per line (e.g. +12025550199 or 12025550199)
     * comma / space separated
     */
    fun addNumbersBatch(
        rawContent: String,
        countryCode: String,
        countryName: String,
        flag: String,
        service: String,
        gateway: String = _state.value.selectedUploadGateway,
        customRate: Double? = null
    ): Int {
        val lines = rawContent.lines()
            .flatMap { it.split(",", ";", " ", "\t") }
            .map { it.trim().removePrefix("+").replace("[^0-9]".toRegex(), "") }
            .filter { it.length >= 7 }
            .distinct()

        if (lines.isEmpty()) return 0

        val existingNumbers = _state.value.uploadedNumbers.map { it.number.replace("[^0-9]".toRegex(), "") }.toSet()
        val toAdd = lines.filterNot { existingNumbers.contains(it) }.map { num ->
            UploadedNumberItem(
                number = num,
                countryCode = countryCode,
                countryName = countryName,
                flag = flag,
                service = service,
                gateway = gateway.uppercase(),
                customRate = customRate
            )
        }

        if (toAdd.isNotEmpty()) {
            val updated = _state.value.uploadedNumbers + toAdd
            _state.update { it.copy(uploadedNumbers = updated) }
            saveUploadedNumbers(updated)
            // Push globally so all users receive the new numbers immediately
            GlobalCloudSyncService.pushToCloud(
                uploadedNumbers = updated,
                unixRate = _state.value.otpRatePerSms,
                zenexRate = VirtualNumberManager.zenexOtpRate.value,
                countryRates = _state.value.countryRates
            )
        }

        return toAdd.size
    }

    /**
     * Admin: Delete numbers by specific Country or All
     * Automatically and instantly syncs deletion to all user devices.
     */
    fun deleteNumbersByCountry(countryCode: String) {
        val updated = if (countryCode.equals("ALL", ignoreCase = true)) {
            emptyList()
        } else {
            _state.value.uploadedNumbers.filterNot { it.countryCode.equals(countryCode, ignoreCase = true) }
        }
        val updatedActive = if (countryCode.equals("ALL", ignoreCase = true)) {
            _state.value.activeNumbers.filter { it.status == "success" }
        } else {
            _state.value.activeNumbers.filterNot { it.iso.equals(countryCode, ignoreCase = true) && it.status != "success" }
        }
        _state.update { it.copy(uploadedNumbers = updated, activeNumbers = updatedActive) }
        saveUploadedNumbers(updated)
        saveActiveNumbers(updatedActive)
        // Push globally so numbers are deleted on user devices too
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates
        )
    }

    /**
     * Admin: Delete a single number.
     * Automatically and instantly syncs deletion to all user devices.
     */
    fun deleteSingleNumber(id: String) {
        val toDelete = _state.value.uploadedNumbers.firstOrNull { it.id == id }
        val updated = _state.value.uploadedNumbers.filterNot { it.id == id }
        val updatedActive = if (toDelete != null) {
            _state.value.activeNumbers.filterNot { isPhoneNumberMatch(it.number, toDelete.number) && it.status != "success" }
        } else _state.value.activeNumbers

        _state.update { it.copy(uploadedNumbers = updated, activeNumbers = updatedActive) }
        saveUploadedNumbers(updated)
        saveActiveNumbers(updatedActive)
        // Push globally so deletion reflects everywhere instantly
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates
        )
    }

    fun setSelectedCountry(countryCode: String) {
        prefs?.edit()?.putString(KEY_SAVED_COUNTRY, countryCode)?.apply()
        _state.update { it.copy(selectedCountry = countryCode) }
    }

    fun setRequestCount(count: Int) {
        val safe = count.coerceIn(1, 10)
        _state.update { it.copy(requestCount = safe) }
    }

    /**
     * User: Get Numbers from Premium pool (Multi-Gateway: Lamix SMS or Unix SMS)
     */
    fun getNumbersForUser(
        context: Context,
        countryCode: String,
        service: String,
        count: Int = _state.value.requestCount,
        onResult: (Boolean, String) -> Unit
    ) {
        val safeCount = count.coerceIn(1, 10)
        val available = _state.value.uploadedNumbers.filter {
            !it.isUsed &&
            (countryCode == "ALL" || it.countryCode.equals(countryCode, ignoreCase = true)) &&
            (it.service.isBlank() || it.service.equals("ALL", ignoreCase = true) || it.service.equals(service, ignoreCase = true))
        }

        if (available.isEmpty()) {
            val msg = "No available numbers for $service ($countryCode)! Admin needs to upload numbers."
            onResult(false, msg)
            return
        }

        val chosenList = available.take(safeCount)
        val chosenIds = chosenList.map { it.id }.toSet()

        // Mark as used
        val updatedUploaded = _state.value.uploadedNumbers.map {
            if (it.id in chosenIds) it.copy(isUsed = true) else it
        }
        saveUploadedNumbers(updatedUploaded)

        val now = System.currentTimeMillis()
        val newProvs = chosenList.map { chosen ->
            val gw = chosen.gateway.uppercase().ifEmpty { "LAMIX" }
            val opName = if (gw == "LAMIX") "Lamix SMS ⚡" else "Unix SMS 🌐"
            val r = chosen.customRate ?: getRateForCountry(chosen.countryCode)
            ProvisionedNumber(
                number = chosen.number,
                country = chosen.countryName,
                operator = opName,
                iso = chosen.countryCode.lowercase(),
                status = "pending",
                range = chosen.service,
                timestamp = now,
                expiresAt = now + 1_200_000L,
                gateway = gw,
                rate = r
            )
        }

        val updatedActive = newProvs + _state.value.activeNumbers
        saveActiveNumbers(updatedActive)

        _state.update {
            it.copy(
                uploadedNumbers = updatedUploaded,
                activeNumbers = updatedActive
            )
        }

        if (chosenList.isNotEmpty()) {
            val allNumbersText = chosenList.joinToString("\n") { it.number.replace("+", "").trim() }
            ClipboardHelper.copyToClipboard(context, allNumbersText, "Unix SMS Numbers")
        }
        VibrationHelper.vibrateSuccess(context)

        // Make sure polling is active
        startFastOtpPolling(context)

        if (chosenList.size > 1) {
            onResult(true, "✓ ${chosenList.size} টি নম্বর রেডি! সবগুলো নম্বর ক্লিপবোর্ডে কপি হয়েছে।")
        } else {
            onResult(true, "নম্বর ${chosenList.first().number} রেডি! ক্লিপবোর্ডে কপি হয়েছে।")
        }
    }

    fun getNumberForUser(
        context: Context,
        countryCode: String,
        service: String,
        onResult: (Boolean, String) -> Unit
    ) {
        getNumbersForUser(context, countryCode, service, _state.value.requestCount, onResult)
    }

    /**
     * Fast 1-Second OTP Polling using Unix SMS CDR API
     * https://agent-api.unixsms.com/v2/cdr?token=...&records=20
     */
    fun startFastOtpPolling(context: Context) {
        if (pollingJob?.isActive == true) return
        _state.update { it.copy(isPolling = true) }

        pollingJob = scope.launch {
            while (isActive) {
                if (!com.example.util.AppLifecycleTracker.canRunBackgroundWork()) {
                    delay(10000L)
                    continue
                }
                try {
                    val activeNums = _state.value.activeNumbers
                    val uploadedNums = _state.value.uploadedNumbers

                    val needsLamix = activeNums.any { it.gateway.equals("LAMIX", ignoreCase = true) && it.otpCode.isNullOrEmpty() } ||
                            uploadedNums.any { it.gateway.equals("LAMIX", ignoreCase = true) && it.otpCode.isNullOrEmpty() }
                    val needsUnix = activeNums.any { it.gateway.equals("UNIX", ignoreCase = true) && it.otpCode.isNullOrEmpty() } ||
                            uploadedNums.any { it.gateway.equals("UNIX", ignoreCase = true) && it.otpCode.isNullOrEmpty() }

                    if (needsLamix) {
                        pollLamixSms(context)
                    }
                    if (needsUnix) {
                        pollUnixSmsCdr(context)
                    }
                } catch (_: Exception) {}
                delay(1200L) // 1.2s fast poll interval compliant with Lamix rate limit
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _state.update { it.copy(isPolling = false) }
    }

    /**
     * Lamix SMS Gateway Live Polling (/api/v1/messages)
     * Matches ONLY numbers uploaded/assigned to LAMIX gateway.
     */
    private suspend fun pollLamixSms(context: Context) {
        val token = _state.value.lamixApiToken.ifEmpty { DEFAULT_LAMIX_TOKEN }
        val url = "https://panel.lamix.org/api/v1/messages?token=${URLEncoder.encode(token, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: Exception) { null }

        val body = response?.body?.string() ?: ""
        if (response != null && response.isSuccessful && body.isNotEmpty()) {
            val root = JSONObject(body)
            val recordsArr = root.optJSONArray("records") ?: JSONArray()
            val nowTime = System.currentTimeMillis()

            val newlyArrivedOtps = mutableListOf<Pair<ProvisionedNumber, String>>()
            val currentActive = _state.value.activeNumbers.filter { prov ->
                prov.otpCode != null || (nowTime - prov.timestamp < 20 * 60 * 1000L)
            }

            val updatedActive = currentActive.map { prov ->
                // ONLY handle LAMIX gateway numbers
                if (!prov.gateway.equals("LAMIX", ignoreCase = true)) {
                    return@map prov
                }
                if (!prov.otpCode.isNullOrEmpty() && prov.status == "success") {
                    return@map prov
                }

                val cleanDigits = prov.number.replace("[^0-9]".toRegex(), "")
                if (cleanDigits.length < 7) {
                    return@map prov
                }

                var matchedRecord: JSONObject? = null
                for (i in 0 until recordsArr.length()) {
                    val r = recordsArr.optJSONObject(i) ?: continue
                    val rNum = r.optString("number", "").replace("[^0-9]".toRegex(), "")
                    if (isPhoneNumberMatch(cleanDigits, rNum)) {
                        matchedRecord = r
                        break
                    }
                }

                if (matchedRecord != null) {
                    val content = matchedRecord.optString("content", "")
                    val extracted = extractOtp(content, cleanDigits)
                    if (extracted.isNotEmpty() && prov.otpCode != extracted) {
                        val updatedProv = prov.copy(
                            status = "success",
                            otpCode = extracted,
                            otpMessage = content
                        )
                        newlyArrivedOtps.add(Pair(updatedProv, extracted))
                        updatedProv
                    } else {
                        prov
                    }
                } else {
                    prov
                }
            }

            var newTodayOtpCount = _state.value.todayOtpCount
            val currentUploaded = _state.value.uploadedNumbers
            var uploadedModified = false

            val updatedUploaded = currentUploaded.map { up ->
                if (!up.gateway.equals("LAMIX", ignoreCase = true)) {
                    return@map up
                }
                val upClean = up.number.replace("[^0-9]".toRegex(), "")
                if (upClean.length < 7) return@map up

                val matchedFromNew = newlyArrivedOtps.firstOrNull { (p, _) ->
                    isPhoneNumberMatch(p.number, up.number)
                }
                if (matchedFromNew != null) {
                    uploadedModified = true
                    up.copy(
                        isUsed = true,
                        otpCode = matchedFromNew.second,
                        otpMessage = matchedFromNew.first.otpMessage
                    )
                } else if (up.otpCode.isNullOrEmpty()) {
                    var directMatch: JSONObject? = null
                    for (i in 0 until recordsArr.length()) {
                        val r = recordsArr.optJSONObject(i) ?: continue
                        val rNum = r.optString("number", "").replace("[^0-9]".toRegex(), "")
                        if (isPhoneNumberMatch(upClean, rNum)) {
                            directMatch = r
                            break
                        }
                    }
                    if (directMatch != null) {
                        val content = directMatch.optString("content", "")
                        val extracted = extractOtp(content, upClean)
                        if (extracted.isNotEmpty()) {
                            uploadedModified = true
                            up.copy(
                                isUsed = true,
                                otpCode = extracted,
                                otpMessage = content
                            )
                        } else up
                    } else up
                } else up
            }

            if (uploadedModified) {
                saveUploadedNumbers(updatedUploaded)
            }

            if (newlyArrivedOtps.isNotEmpty()) {
                newTodayOtpCount += newlyArrivedOtps.size
                prefs?.edit()?.putInt(KEY_TODAY_OTP, newTodayOtpCount)?.apply()
                saveActiveNumbers(updatedActive)

                newlyArrivedOtps.forEach { (prov, otp) ->
                    OtpHistoryManager.recordOtp(
                        phoneNumber = prov.number,
                        otpCode = otp,
                        service = prov.range.ifEmpty { "Facebook" },
                        platform = "Lamix SMS",
                        rate = prov.rate
                    )
                }

                launchMain {
                    newlyArrivedOtps.forEach { (prov, otp) ->
                        OtpNotificationHelper.showOtpNotification(
                            context = context,
                            phoneNumber = prov.number,
                            otpCode = otp,
                            fullMessage = prov.otpMessage ?: "OTP: $otp"
                        )
                    }
                    val firstOtp = newlyArrivedOtps.first().second
                    ClipboardHelper.copyToClipboard(context, firstOtp, "OTP Code")
                    VibrationHelper.vibrateSuccess(context)
                }
            }

            _state.update {
                it.copy(
                    activeNumbers = updatedActive,
                    uploadedNumbers = if (uploadedModified) updatedUploaded else it.uploadedNumbers,
                    todayOtpCount = newTodayOtpCount,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Unix SMS Gateway Live Polling (/v2/cdr)
     * Matches ONLY numbers uploaded/assigned to UNIX gateway.
     */
    private suspend fun pollUnixSmsCdr(context: Context) {
        val token = _state.value.apiToken.ifEmpty { DEFAULT_TOKEN }
        val url = "https://agent-api.unixsms.com/v2/cdr?token=${URLEncoder.encode(token, "UTF-8")}&records=20"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: Exception) { null }

        val body = response?.body?.string() ?: ""

        if (response != null && response.isSuccessful && body.isNotEmpty()) {
            val root = JSONObject(body)
            if (root.optString("status") == "success") {
                val dataArr = root.optJSONArray("data") ?: JSONArray()
                val cdrList = mutableListOf<UnixSmsCdrRecord>()

                val maxCdrToParse = minOf(dataArr.length(), 40)
                for (i in 0 until maxCdrToParse) {
                    val o = dataArr.optJSONObject(i) ?: continue
                    cdrList.add(
                        UnixSmsCdrRecord(
                            dt = o.optString("dt", ""),
                            num = o.optString("num", ""),
                            cli = o.optString("cli", ""),
                            message = o.optString("message", ""),
                            payout = o.optString("payout", "0")
                        )
                    )
                }

                // 24-hour cycle auto reset for Live CDR
                val nowTime = System.currentTimeMillis()
                val cdrCycleStart = prefs?.getLong("unix_cdr_cycle_start_v2", 0L) ?: 0L
                if (cdrCycleStart <= 0L || nowTime - cdrCycleStart >= 24 * 60 * 60 * 1000L) {
                    prefs?.edit()?.putLong("unix_cdr_cycle_start_v2", nowTime)?.apply()
                    _state.update { it.copy(liveCdrRecords = emptyList()) }
                }

                val newlyArrivedOtps = mutableListOf<Pair<ProvisionedNumber, String>>()
                // Auto-cleanup: remove unfulfilled numbers older than 20 mins to save RAM/ROM
                val currentActive = _state.value.activeNumbers.filter { prov ->
                    prov.otpCode != null || (nowTime - prov.timestamp < 20 * 60 * 1000L)
                }

                val updatedActive = currentActive.map { prov ->
                    // ONLY handle UNIX gateway numbers
                    if (!prov.gateway.equals("UNIX", ignoreCase = true)) {
                        return@map prov
                    }
                    if (!prov.otpCode.isNullOrEmpty() && prov.status == "success") {
                        return@map prov
                    }

                    val cleanDigits = prov.number.replace("[^0-9]".toRegex(), "")
                    if (cleanDigits.length < 7) {
                        return@map prov
                    }

                    var matchedCdr: UnixSmsCdrRecord? = null
                    for (cdr in cdrList) {
                        if (isPhoneNumberMatch(cleanDigits, cdr.num)) {
                            matchedCdr = cdr
                            break
                        }
                    }

                    if (matchedCdr != null) {
                        val extracted = extractOtp(matchedCdr.message, cleanDigits)
                        if (extracted.isNotEmpty() && prov.otpCode != extracted) {
                            val updatedProv = prov.copy(
                                status = "success",
                                otpCode = extracted,
                                otpMessage = matchedCdr.message
                            )
                            newlyArrivedOtps.add(Pair(updatedProv, extracted))
                            updatedProv
                        } else {
                            prov
                        }
                    } else {
                        prov
                    }
                }

                var newTodayOtpCount = _state.value.todayOtpCount
                val currentUploaded = _state.value.uploadedNumbers
                var uploadedModified = false

                val updatedUploaded = currentUploaded.map { up ->
                    if (!up.gateway.equals("UNIX", ignoreCase = true)) {
                        return@map up
                    }
                    val upClean = up.number.replace("[^0-9]".toRegex(), "")
                    if (upClean.length < 7) return@map up

                    // Check if an OTP arrived for this uploaded number
                    val matchedFromNew = newlyArrivedOtps.firstOrNull { (p, _) ->
                        isPhoneNumberMatch(p.number, up.number)
                    }
                    if (matchedFromNew != null) {
                        uploadedModified = true
                        up.copy(
                            isUsed = true,
                            otpCode = matchedFromNew.second,
                            otpMessage = matchedFromNew.first.otpMessage
                        )
                    } else if (up.otpCode.isNullOrEmpty()) {
                        // Direct CDR check for uploaded number
                        var directCdr: UnixSmsCdrRecord? = null
                        for (cdr in cdrList) {
                            if (isPhoneNumberMatch(upClean, cdr.num)) {
                                directCdr = cdr
                                break
                            }
                        }
                        if (directCdr != null) {
                            val extracted = extractOtp(directCdr.message, upClean)
                            if (extracted.isNotEmpty()) {
                                uploadedModified = true
                                up.copy(
                                    isUsed = true,
                                    otpCode = extracted,
                                    otpMessage = directCdr.message
                                )
                            } else {
                                up
                            }
                        } else {
                            up
                        }
                    } else {
                        up
                    }
                }

                if (uploadedModified) {
                    saveUploadedNumbers(updatedUploaded)
                }

                if (newlyArrivedOtps.isNotEmpty()) {
                    newTodayOtpCount += newlyArrivedOtps.size
                    prefs?.edit()?.putInt(KEY_TODAY_OTP, newTodayOtpCount)?.apply()
                    saveActiveNumbers(updatedActive)

                    // Record to permanent Total OTP History
                    newlyArrivedOtps.forEach { (prov, otp) ->
                        OtpHistoryManager.recordOtp(
                            phoneNumber = prov.number,
                            otpCode = otp,
                            service = prov.range.ifEmpty { "Unix SMS" },
                            platform = "Unix SMS",
                            rate = prov.rate
                        )
                    }

                    // Dispatch Notifications & Auto-copy
                    launchMain {
                        newlyArrivedOtps.forEach { (prov, otp) ->
                            OtpNotificationHelper.showOtpNotification(
                                context = context,
                                phoneNumber = prov.number,
                                otpCode = otp,
                                fullMessage = prov.otpMessage ?: "OTP: $otp"
                            )
                        }

                        // Auto-copy first extracted OTP
                        val firstOtp = newlyArrivedOtps.first().second
                        ClipboardHelper.copyToClipboard(context, firstOtp, "OTP Code")
                        VibrationHelper.vibrateSuccess(context)
                    }
                }

                _state.update {
                    it.copy(
                        liveCdrRecords = cdrList.take(35),
                        activeNumbers = updatedActive,
                        uploadedNumbers = if (uploadedModified) updatedUploaded else it.uploadedNumbers,
                        todayOtpCount = newTodayOtpCount,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun extractOtp(message: String, phoneDigits: String): String {
        val candidates = mutableListOf<String>()
        val matcher = otpRegex.matcher(message)
        while (matcher.find()) {
            candidates.add(matcher.group())
        }
        // Filter out phone number parts and prioritize 5 to 8 digits (or 4 if none)
        return candidates.firstOrNull { cand -> !phoneDigits.contains(cand) && cand.length in 5..8 }
            ?: candidates.firstOrNull { cand -> !phoneDigits.contains(cand) && cand.length in 4..8 }
            ?: candidates.firstOrNull { it.length in 5..8 }
            ?: candidates.firstOrNull() ?: ""
    }

    fun isPhoneNumberMatch(num1: String, num2: String): Boolean {
        val d1 = num1.replace("[^0-9]".toRegex(), "")
        val d2 = num2.replace("[^0-9]".toRegex(), "")
        if (d1.length < 7 || d2.length < 7) return false
        val len = minOf(d1.length, d2.length, 9)
        if (len < 7) return false
        return d1.takeLast(len) == d2.takeLast(len)
    }

    private fun launchMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    fun clearActiveNumbers() {
        _state.update { it.copy(activeNumbers = emptyList()) }
        saveActiveNumbers(emptyList())
    }
}
