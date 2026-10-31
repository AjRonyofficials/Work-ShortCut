package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
    val service: String, // "Facebook", "Instagram", "WhatsApp", "OTP Work"
    val gateway: String = "LAMIX", // "LAMIX" or "UNIX"
    val customRate: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val isUsed: Boolean = false,
    val otpCode: String? = null,
    val otpMessage: String? = null,
    val numberType: String = "Fresh" // "Fresh" (New Fresh Number) or "PC Clone" (Pc clone number)
)

data class UnixSmsCdrRecord(
    val dt: String,
    val num: String,
    val cli: String,
    val message: String,
    val payout: String,
    val countryName: String = "",
    val countryFlag: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class UnixSmsState(
    val apiToken: String = "simple_v2_fQxnBh-RLx8BIZS2Zjx8TGVLYLv6aqPkATsrIqEKFbxIaowb",
    val lamixApiToken: String = "pMRolCWFnQI0PjGibIKWOpeefLn8HbS6AqGS3BqPZlg",
    val hadiApiToken: String = "QlBYQklBUzRdV4hea5VzRXSJYnlDjGtKXWFmg4NoaGViYm2AX3eHZg==",
    val selectedUploadGateway: String = "LAMIX", // "LAMIX", "UNIX", or "HADI"
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private const val PREFS_NAME = "unix_sms_manager_prefs"
    private const val KEY_API_TOKEN = "unix_api_token"
    private const val KEY_LAMIX_API_TOKEN = "lamix_api_token"
    private const val KEY_HADI_API_TOKEN = "hadi_api_token"
    private const val KEY_SELECTED_UPLOAD_GATEWAY = "unix_selected_upload_gateway"
    private const val KEY_UPLOADED_NUMBERS = "uploaded_numbers_json"
    private const val KEY_ACTIVE_NUMBERS = "active_numbers_json"
    private const val KEY_TODAY_OTP = "today_otp_unix"
    private const val KEY_OTP_RATE = "unix_otp_rate_setting"
    private const val KEY_SAVED_COUNTRY = "unix_saved_country"
    private const val KEY_COUNTRY_RATES = "unix_country_rates_json"
    private const val KEY_PERMANENTLY_USED_DIGITS = "permanently_used_number_digits_set"
    const val DEFAULT_TOKEN = "simple_v2_fQxnBh-RLx8BIZS2Zjx8TGVLYLv6aqPkATsrIqEKFbxIaowb"
    const val DEFAULT_LAMIX_TOKEN = "pMRolCWFnQI0PjGibIKWOpeefLn8HbS6AqGS3BqPZlg"
    const val DEFAULT_HADI_TOKEN = "QlBYQklBUzRdV4hea5VzRXSJYnlDjGtKXWFmg4NoaGViYm2AX3eHZg=="
    const val HADI_BASE_URL = "http://147.135.212.197/crapi/had/viewstats"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var prefs: SharedPreferences? = null
    private var pollingJob: Job? = null
    private val permanentlyUsedDigits = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
            val rawToken = prefs?.getString(KEY_API_TOKEN, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
            val token = if (rawToken.isBlank() || rawToken.startsWith("AQ.") || rawToken.contains("PLACEHOLDER") || rawToken.contains("AIza")) {
                prefs?.edit()?.putString(KEY_API_TOKEN, DEFAULT_TOKEN)?.apply()
                DEFAULT_TOKEN
            } else rawToken
            val rawLamix = prefs?.getString(KEY_LAMIX_API_TOKEN, DEFAULT_LAMIX_TOKEN) ?: DEFAULT_LAMIX_TOKEN
            val lamixToken = if (rawLamix.isBlank() || rawLamix.startsWith("AQ.") || rawLamix.contains("PLACEHOLDER") || rawLamix.contains("AIza")) {
                prefs?.edit()?.putString(KEY_LAMIX_API_TOKEN, DEFAULT_LAMIX_TOKEN)?.apply()
                DEFAULT_LAMIX_TOKEN
            } else rawLamix
            val rawHadi = prefs?.getString(KEY_HADI_API_TOKEN, DEFAULT_HADI_TOKEN) ?: DEFAULT_HADI_TOKEN
            val hadiToken = if (rawHadi.isBlank()) DEFAULT_HADI_TOKEN else rawHadi
            val gateway = prefs?.getString(KEY_SELECTED_UPLOAD_GATEWAY, "LAMIX") ?: "LAMIX"
            val todayOtp = prefs?.getInt(KEY_TODAY_OTP, 0) ?: 0
            val rawSavedRate = prefs?.getFloat(KEY_OTP_RATE, 0.50f)?.toDouble() ?: 0.50
            val savedRate = if (rawSavedRate < 0.1) 0.50 else rawSavedRate
            val uploaded = loadUploadedNumbers()
            val active = loadActiveNumbers()
            val savedCountry = prefs?.getString(KEY_SAVED_COUNTRY, "ALL") ?: "ALL"
            val loadedCountryRates = loadCountryRates()

            // Load persistent used numbers digits
            val savedUsedDigits = prefs?.getStringSet(KEY_PERMANENTLY_USED_DIGITS, emptySet()) ?: emptySet()
            permanentlyUsedDigits.addAll(savedUsedDigits)
            uploaded.filter { it.isUsed || !it.otpCode.isNullOrEmpty() }.forEach {
                val d = it.number.replace("[^0-9]".toRegex(), "")
                if (d.length >= 7) permanentlyUsedDigits.add(d)
            }
            active.forEach {
                val d = it.number.replace("[^0-9]".toRegex(), "")
                if (d.length >= 7) permanentlyUsedDigits.add(d)
            }
            savePermanentlyUsedDigits()

            _state.update { current ->
                val finalUploaded = if (current.uploadedNumbers.isNotEmpty()) {
                    current.uploadedNumbers
                } else {
                    uploaded.map { item ->
                        val d = item.number.replace("[^0-9]".toRegex(), "")
                        if (permanentlyUsedDigits.contains(d)) item.copy(isUsed = true) else item
                    }
                }
                current.copy(
                    apiToken = token,
                    lamixApiToken = lamixToken,
                    hadiApiToken = hadiToken,
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

    private fun savePermanentlyUsedDigits() {
        prefs?.edit()?.putStringSet(KEY_PERMANENTLY_USED_DIGITS, permanentlyUsedDigits.toSet())?.apply()
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
            // Never wipe local uploaded numbers if cloud array is empty!
            return
        }
        val current = _state.value.uploadedNumbers
        val activeNumbers = _state.value.activeNumbers

        // Compound key: digits + numberType + service to safely preserve all platform tags
        val allNumbersMap = linkedMapOf<String, UploadedNumberItem>()

        // 1. Keep all existing local numbers (authoritative)
        for (localItem in current) {
            val cleanDigits = localItem.number.replace("[^0-9]".toRegex(), "")
            val key = "${cleanDigits}_${localItem.numberType}_${localItem.service.lowercase()}"
            allNumbersMap[key] = localItem
        }

        // 2. Safely merge cloud list
        for (cloudItem in cloudList) {
            val cleanDigits = cloudItem.number.replace("[^0-9]".toRegex(), "")
            val key = "${cleanDigits}_${cloudItem.numberType}_${cloudItem.service.lowercase()}"
            val local = allNumbersMap[key]

            val isClaimedLocally = permanentlyUsedDigits.contains(cleanDigits) ||
                    local?.isUsed == true ||
                    activeNumbers.any { isPhoneNumberMatch(it.number, cleanDigits) }

            val isPermanentlyUsed = cloudItem.isUsed || isClaimedLocally
            if (isPermanentlyUsed && cleanDigits.length >= 7) {
                permanentlyUsedDigits.add(cleanDigits)
            }

            if (local == null) {
                allNumbersMap[key] = cloudItem.copy(isUsed = isPermanentlyUsed)
            } else {
                allNumbersMap[key] = local.copy(
                    isUsed = isPermanentlyUsed,
                    otpCode = cloudItem.otpCode ?: local.otpCode,
                    otpMessage = cloudItem.otpMessage ?: local.otpMessage
                )
            }
        }
        savePermanentlyUsedDigits()

        val merged = allNumbersMap.values.toList()
        // Never wipe or touch activeNumbers during cloud pool sync
        _state.update { it.copy(uploadedNumbers = merged) }
        saveUploadedNumbers(merged)
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
                        otpMessage = if (o.has("otpMessage")) o.optString("otpMessage") else null,
                        numberType = o.optString("numberType", "Fresh")
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
            o.put("numberType", item.numberType)
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_UPLOADED_NUMBERS, arr.toString())?.apply()
    }

    private fun getUserActiveNumbersKey(email: String = AuthManager.currentEmail.value): String {
        val clean = email.trim().lowercase()
        return if (clean.isNotEmpty()) "${KEY_ACTIVE_NUMBERS}_$clean" else KEY_ACTIVE_NUMBERS
    }

    private fun loadActiveNumbers(email: String = AuthManager.currentEmail.value): List<ProvisionedNumber> {
        val key = getUserActiveNumbersKey(email)
        val json = prefs?.getString(key, "[]") ?: "[]"
        val list = mutableListOf<ProvisionedNumber>()
        val effectiveEmail = email.trim().lowercase()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fbTag = try {
                    PhoneItem.TagType.valueOf(o.optString("fbTagType", "PENDING"))
                } catch (_: Exception) {
                    PhoneItem.TagType.PENDING
                }
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
                        rate = o.optDouble("rate", 0.50),
                        fbStatus = o.optString("fbStatus").ifEmpty { null },
                        fbTagType = fbTag,
                        userEmail = o.optString("userEmail", effectiveEmail)
                    )
                )
            }
        } catch (_: Exception) {}
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return list.filter { it.timestamp >= cutoff }
    }

    private fun saveActiveNumbers(list: List<ProvisionedNumber>, email: String = AuthManager.currentEmail.value) {
        val arr = JSONArray()
        val effectiveEmail = email.trim().lowercase()
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
            o.put("fbStatus", item.fbStatus ?: "")
            o.put("fbTagType", item.fbTagType.name)
            o.put("userEmail", item.userEmail.ifEmpty { effectiveEmail })
            arr.put(o)
        }
        prefs?.edit()?.putString(getUserActiveNumbersKey(effectiveEmail), arr.toString())?.apply()
    }

    fun onUserLoggedIn(email: String) {
        val loaded = loadActiveNumbers(email)
        _state.update { it.copy(activeNumbers = loaded) }
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

    fun setHadiApiToken(token: String) {
        val clean = token.trim()
        _state.update { it.copy(hadiApiToken = clean) }
        prefs?.edit()?.putString(KEY_HADI_API_TOKEN, clean)?.apply()
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
        customRate: Double? = null,
        numberType: String = "Fresh"
    ): Int {
        val lines = rawContent.lines()
            .flatMap { it.split(",", ";", " ", "\t") }
            .map { it.trim().removePrefix("+").replace("[^0-9]".toRegex(), "") }
            .filter { it.length >= 7 }
            .distinct()

        if (lines.isEmpty()) return 0

        val targetType = when {
            numberType.contains("pc", ignoreCase = true) -> "PC Clone"
            numberType.contains("clone", ignoreCase = true) -> "FB Clone"
            else -> "Fresh"
        }
        val existingKeys = _state.value.uploadedNumbers.map {
            "${it.number.replace("[^0-9]".toRegex(), "")}_${it.numberType}_${it.service.lowercase()}"
        }.toSet()
        val toAdd = lines.filterNot { num ->
            existingKeys.contains("${num}_${targetType}_${service.lowercase()}")
        }.map { num ->
            UploadedNumberItem(
                number = num,
                countryCode = countryCode,
                countryName = countryName,
                flag = flag,
                service = service,
                gateway = gateway.uppercase(),
                customRate = customRate,
                numberType = targetType
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
     * Automatically and instantly syncs deletion to Supabase and all user devices.
     */
    fun deleteNumbersByCountry(countryCode: String, onComplete: ((Boolean) -> Unit)? = null) {
        val updated = if (countryCode.equals("ALL", ignoreCase = true)) {
            emptyList()
        } else {
            _state.value.uploadedNumbers.filterNot { it.countryCode.equals(countryCode, ignoreCase = true) }
        }
        // User's active taken numbers, OTP success records, and balances are 100% preserved
        val updatedActive = _state.value.activeNumbers
        _state.update { it.copy(uploadedNumbers = updated, activeNumbers = updatedActive) }
        saveUploadedNumbers(updated)
        saveActiveNumbers(updatedActive)
        // Push globally to Supabase immediately so numbers are deleted everywhere
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            onComplete = onComplete
        )
    }

    /**
     * Admin: Delete a single number.
     * Automatically and instantly syncs deletion to Supabase and all user devices.
     */
    fun deleteSingleNumber(id: String, onComplete: ((Boolean) -> Unit)? = null) {
        val updated = _state.value.uploadedNumbers.filterNot { it.id == id }
        val updatedActive = _state.value.activeNumbers

        _state.update { it.copy(uploadedNumbers = updated, activeNumbers = updatedActive) }
        saveUploadedNumbers(updated)
        saveActiveNumbers(updatedActive)
        // Push globally so deletion reflects everywhere instantly
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            onComplete = onComplete
        )
    }

    /**
     * Admin: Clear ALL uploaded numbers completely from database and local storage.
     */
    fun clearAllUploadedNumbers(onComplete: ((Boolean) -> Unit)? = null) {
        deleteNumbersByCountry("ALL", onComplete)
    }

    /**
     * Auto-purges invalid / malformed / corrupted numbers (fewer than 7 digits, blank, or malformed)
     * and syncs the clean list directly with the cloud database.
     */
    fun purgeInvalidUploadedNumbers(onComplete: ((Boolean) -> Unit)? = null): Int {
        val current = _state.value.uploadedNumbers
        val initialSize = current.size
        val cleaned = current.filter { item ->
            val digits = item.number.replace("[^0-9]".toRegex(), "")
            digits.length in 7..16 && item.countryCode.isNotBlank() && item.countryName.isNotBlank()
        }.distinctBy { it.number.replace("[^0-9]".toRegex(), "") }

        val removed = initialSize - cleaned.size
        if (removed > 0) {
            _state.update { it.copy(uploadedNumbers = cleaned) }
            saveUploadedNumbers(cleaned)
            GlobalCloudSyncService.pushToCloud(
                uploadedNumbers = cleaned,
                unixRate = _state.value.otpRatePerSms,
                zenexRate = VirtualNumberManager.zenexOtpRate.value,
                countryRates = _state.value.countryRates,
                onComplete = onComplete
            )
        } else {
            onComplete?.invoke(true)
        }
        return removed
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
     * Determines if an uploaded number matches the user requested service and type
     */
    fun isNumberMatchingService(item: UploadedNumberItem, serviceLabel: String, targetNumberType: String = "ALL"): Boolean {
        if (item.isUsed) return false
        val s = item.service.trim()
        val t = item.numberType.trim()

        if (targetNumberType != "ALL") {
            if (targetNumberType.contains("pc", true) && !t.contains("pc", true)) return false
            if ((targetNumberType.contains("fresh", true) || targetNumberType.contains("new", true)) && !t.contains("fresh", true) && !t.contains("new", true)) return false
            if (targetNumberType.contains("clone", true) && !t.contains("clone", true) && !t.contains("pc", true)) return false
        }

        return when {
            serviceLabel.equals("ALL", ignoreCase = true) -> true
            serviceLabel.contains("Fresh", ignoreCase = true) || serviceLabel.contains("FB New", ignoreCase = true) ->
                (s.contains("FB", true) || s.contains("Face", true) || s.isBlank() || s.equals("ALL", true)) &&
                (t.contains("Fresh", true) || t.contains("New", true))
            serviceLabel.contains("FB Clone", ignoreCase = true) ->
                (s.contains("FB", true) || s.contains("Face", true) || s.isBlank() || s.equals("ALL", true)) &&
                (t.contains("Clone", true) || t.contains("PC", true) || t.equals("Taken", true) || t.isBlank())
            serviceLabel.contains("PC", ignoreCase = true) ->
                t.contains("PC", true) || s.contains("PC", true)
            serviceLabel.contains("WhatsApp", ignoreCase = true) || serviceLabel.contains("WA", ignoreCase = true) ->
                s.contains("WhatsApp", true) || s.contains("WA", true) || s.contains("WP", true)
            serviceLabel.contains("Instagram", ignoreCase = true) || serviceLabel.contains("IG", ignoreCase = true) ->
                s.contains("Instagram", true) || s.contains("Insta", true) || s.contains("IG", true)
            serviceLabel.contains("OTP Work", ignoreCase = true) || serviceLabel.contains("OTP", ignoreCase = true) ->
                s.contains("OTP", true) || s.isBlank() || s.equals("ALL", true)
            else ->
                s.isBlank() || s.equals("ALL", true) || s.contains(serviceLabel, true) || serviceLabel.contains(s, true)
        }
    }

    /**
     * User: Get Numbers from Premium pool (Multi-Gateway: Lamix SMS or Unix SMS)
     * Atomically assigns exclusive numbers that never revert to available pool.
     */
    @Synchronized
    fun getNumbersForUser(
        context: Context,
        countryCode: String,
        service: String,
        count: Int = _state.value.requestCount,
        numberType: String = "ALL",
        onResult: (Boolean, String) -> Unit
    ) {
        val safeCount = count.coerceIn(1, 10)
        val activeNumbers = _state.value.activeNumbers

        // Exclude used numbers, permanently claimed digits, and currently active numbers
        val available = _state.value.uploadedNumbers.filter { item ->
            val cleanDigits = item.number.replace("[^0-9]".toRegex(), "")
            !item.isUsed &&
            !permanentlyUsedDigits.contains(cleanDigits) &&
            !activeNumbers.any { isPhoneNumberMatch(it.number, cleanDigits) } &&
            (countryCode == "ALL" || item.countryCode.equals(countryCode, ignoreCase = true)) &&
            isNumberMatchingService(item, service, numberType)
        }

        if (available.isEmpty()) {
            val msg = "No available numbers for $service ($countryCode)! Admin needs to upload numbers."
            onResult(false, msg)
            return
        }

        val chosen = available.take(safeCount)
        val chosenIds = chosen.map { it.id }.toSet()

        // Atomically mark chosen numbers as permanently used locally
        chosen.forEach {
            val d = it.number.replace("[^0-9]".toRegex(), "")
            if (d.length >= 7) permanentlyUsedDigits.add(d)
        }
        savePermanentlyUsedDigits()

        val updatedUploaded = _state.value.uploadedNumbers.map {
            if (it.id in chosenIds) it.copy(isUsed = true) else it
        }
        saveUploadedNumbers(updatedUploaded)

        scope.launch {
            GlobalCloudSyncService.pushToCloud(
                uploadedNumbers = updatedUploaded,
                unixRate = _state.value.otpRatePerSms,
                zenexRate = VirtualNumberManager.zenexOtpRate.value,
                countryRates = _state.value.countryRates
            )

            val now = System.currentTimeMillis()
            val effectiveEmail = AuthManager.currentEmail.value.trim().lowercase()
            val newProvs = chosen.map { item ->
                val gw = item.gateway.uppercase().ifEmpty { "LAMIX" }
                val opName = when (gw) {
                    "HADI" -> "Hadi SMS 🔥"
                    "LAMIX" -> "Lamix SMS ⚡"
                    else -> "Unix SMS 🌐"
                }
                val r = item.customRate ?: getRateForCountry(item.countryCode)
                ProvisionedNumber(
                    number = item.number,
                    country = item.countryName,
                    operator = opName,
                    iso = item.countryCode.lowercase(),
                    status = "pending",
                    range = item.service,
                    timestamp = now,
                    expiresAt = now + 24 * 60 * 60 * 1000L, // 24-hour persistence, never back prematurely
                    gateway = gw,
                    rate = r,
                    userEmail = effectiveEmail
                )
            }

            val updatedActive = newProvs + _state.value.activeNumbers
            saveActiveNumbers(updatedActive, effectiveEmail)

            _state.update {
                it.copy(
                    uploadedNumbers = updatedUploaded,
                    activeNumbers = updatedActive
                )
            }

            mainHandler.post {
                if (chosen.isNotEmpty()) {
                    val allNumbersText = chosen.joinToString("\n") { it.number.replace("+", "").trim() }
                    ClipboardHelper.copyToClipboard(context, allNumbersText, "Unix SMS Numbers")
                    // Trigger background Facebook Fresh / Taken status check
                    checkFbStatusForNumbers(newProvs.map { it.id to it.number })
                }
                VibrationHelper.vibrateSuccess(context)

                // Make sure polling is active
                startFastOtpPolling(context)

                if (chosen.size > 1) {
                    onResult(true, "✓ ${chosen.size} টি নম্বর রেডি ও কপি হয়েছে!")
                } else if (chosen.isNotEmpty()) {
                    onResult(true, "✓ নম্বর ${chosen.first().number} রেডি ও কপি হয়েছে!")
                } else {
                    onResult(false, "কোনো উপযুক্ত নম্বর পাওয়া যায়নি।")
                }
            }
        }
    }

    fun checkFbStatusForNumber(id: String, number: String) {
        scope.launch {
            _state.update { current ->
                val updated = current.activeNumbers.map {
                    if (it.id == id) it.copy(fbStatus = "Checking...", fbTagType = PhoneItem.TagType.PENDING) else it
                }
                current.copy(activeNumbers = updated)
            }
            val formatted = if (number.startsWith("+")) number else "+$number"
            val (statusStr, tagType) = VirtualNumberChecker.checkNumberTag(formatted)
            _state.update { current ->
                val updated = current.activeNumbers.map {
                    if (it.id == id) it.copy(fbStatus = statusStr, fbTagType = tagType) else it
                }
                saveActiveNumbers(updated)
                current.copy(activeNumbers = updated)
            }
        }
    }

    fun checkFbStatusForNumbers(pairs: List<Pair<String, String>>) {
        scope.launch {
            pairs.forEach { (id, number) ->
                _state.update { current ->
                    val updated = current.activeNumbers.map {
                        if (it.id == id) it.copy(fbStatus = "Checking...", fbTagType = PhoneItem.TagType.PENDING) else it
                    }
                    current.copy(activeNumbers = updated)
                }
                val formatted = if (number.startsWith("+")) number else "+$number"
                val (statusStr, tagType) = VirtualNumberChecker.checkNumberTag(formatted)
                _state.update { current ->
                    val updated = current.activeNumbers.map {
                        if (it.id == id) it.copy(fbStatus = statusStr, fbTagType = tagType) else it
                    }
                    saveActiveNumbers(updated)
                    current.copy(activeNumbers = updated)
                }
            }
        }
    }


    fun getNumberForUser(
        context: Context,
        countryCode: String,
        service: String,
        onResult: (Boolean, String) -> Unit
    ) {
        getNumbersForUser(
            context = context,
            countryCode = countryCode,
            service = service,
            count = _state.value.requestCount,
            numberType = "ALL",
            onResult = onResult
        )
    }

    private var lastUnixPollTime = 0L
    private var lastPurgeTime = 0L

    /**
     * Fast OTP Polling using Hadi SMS (1s), Lamix SMS (1s), and Unix SMS CDR API (2.5s)
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
                    val now = System.currentTimeMillis()

                    // 24-hour auto purge runs every 30 seconds
                    if (now - lastPurgeTime >= 30_000L) {
                        lastPurgeTime = now
                        purgeOlderThan24Hours()
                    }

                    val activeNums = _state.value.activeNumbers
                    val uploadedNums = _state.value.uploadedNumbers

                    val hasPendingActive = activeNums.any { it.otpCode.isNullOrEmpty() }
                    val hasPendingUploaded = uploadedNums.any { it.otpCode.isNullOrEmpty() }

                    if (hasPendingActive || hasPendingUploaded) {
                        // 1. Fast 1-Second Hadi SMS CDR Polling (CR Hadi API)
                        pollHadiSmsCdr(context)

                        // 2. Fast 1-Second Lamix SMS Polling
                        pollLamixSms(context)

                        // 3. Unix SMS API (throttled to at least 2500ms to stay within 2s rate limit)
                        if (now - lastUnixPollTime >= 2500L) {
                            lastUnixPollTime = now
                            pollUnixSmsCdr(context)
                        }
                    }
                } catch (_: Exception) {}
                delay(1000L) // Fast 1-second interval as requested!
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _state.update { it.copy(isPolling = false) }
    }

    /**
     * Auto-purges CDR records, expired active numbers, and older OTPs across Unix SMS and Hadi SMS after 24 hours.
     */
    fun purgeOlderThan24Hours() {
        val nowTime = System.currentTimeMillis()
        val cutoff = nowTime - 24 * 60 * 60 * 1000L

        val currentCdr = _state.value.liveCdrRecords
        val filteredCdr = currentCdr.filter { it.timestamp > cutoff }

        val currentActive = _state.value.activeNumbers
        val filteredActive = currentActive.filter { it.timestamp > cutoff || !it.otpCode.isNullOrEmpty() }

        if (filteredCdr.size != currentCdr.size || filteredActive.size != currentActive.size) {
            _state.update {
                it.copy(
                    liveCdrRecords = filteredCdr,
                    activeNumbers = filteredActive
                )
            }
            saveActiveNumbers(filteredActive)
        }
    }

    /**
     * Fast 1-Second CR Hadi API SMS Gateway Polling (/crapi/had/viewstats)
     * Actively fetches messages and individual phone query for every active taken number!
     */
    private suspend fun pollHadiSmsCdr(context: Context) {
        val token = _state.value.hadiApiToken.ifEmpty { DEFAULT_HADI_TOKEN }
        val url = "$HADI_BASE_URL?token=${URLEncoder.encode(token, "UTF-8")}&records=100"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (_: Exception) { null }

        val body = response?.body?.string() ?: ""
        val rawCdrList = mutableListOf<Pair<UnixSmsCdrRecord, String>>()

        if (response != null && response.isSuccessful && body.isNotEmpty()) {
            try {
                val root = JSONObject(body)
                val status = root.optString("status")
                val dataArr = root.optJSONArray("data") ?: root.optJSONArray("records") ?: JSONArray()

                for (i in 0 until dataArr.length()) {
                    val o = dataArr.optJSONObject(i) ?: continue
                    val rawNum = o.optString("num", o.optString("number", ""))
                    val rawMsg = o.optString("message", o.optString("content", o.optString("text", "")))
                    val rawCli = o.optString("cli", o.optString("sender", ""))
                    val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(rawNum)
                    val cName = countryInfo?.name ?: "Global"
                    val cFlag = countryInfo?.flag ?: "🌐"
                    val masked = maskPhoneNumberMiddle4(rawNum)
                    val rec = UnixSmsCdrRecord(
                        dt = o.optString("dt", ""),
                        num = masked,
                        cli = rawCli,
                        message = rawMsg,
                        payout = o.optString("payout", "0"),
                        countryName = cName,
                        countryFlag = cFlag,
                        timestamp = System.currentTimeMillis()
                    )
                    rawCdrList.add(Pair(rec, rawNum))
                }
            } catch (_: Exception) {}
        }

        // Also query pending active numbers directly via filternum if not yet found
        val activeNumbers = _state.value.activeNumbers
        for (prov in activeNumbers) {
            if (prov.otpCode.isNullOrEmpty() && prov.status == "pending") {
                val cleanDigits = prov.number.replace("[^0-9]".toRegex(), "")
                if (cleanDigits.length >= 7 && !rawCdrList.any { isPhoneNumberMatch(it.second, cleanDigits) }) {
                    try {
                        val filterUrl = "$HADI_BASE_URL?token=${URLEncoder.encode(token, "UTF-8")}&filternum=$cleanDigits"
                        val filterReq = Request.Builder().url(filterUrl).header("User-Agent", "Mozilla/5.0").get().build()
                        val filterResp = httpClient.newCall(filterReq).execute()
                        val filterBody = filterResp.body?.string() ?: ""
                        if (filterResp.isSuccessful && filterBody.isNotEmpty()) {
                            val fRoot = JSONObject(filterBody)
                            val fArr = fRoot.optJSONArray("data") ?: fRoot.optJSONArray("records") ?: JSONArray()
                            for (j in 0 until fArr.length()) {
                                val fo = fArr.optJSONObject(j) ?: continue
                                val fNum = fo.optString("num", fo.optString("number", cleanDigits))
                                val fMsg = fo.optString("message", fo.optString("content", fo.optString("text", "")))
                                val fCli = fo.optString("cli", fo.optString("sender", ""))
                                val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(fNum)
                                val rec = UnixSmsCdrRecord(
                                    dt = fo.optString("dt", ""),
                                    num = maskPhoneNumberMiddle4(fNum),
                                    cli = fCli,
                                    message = fMsg,
                                    payout = fo.optString("payout", "0"),
                                    countryName = countryInfo?.name ?: "Global",
                                    countryFlag = countryInfo?.flag ?: "🌐",
                                    timestamp = System.currentTimeMillis()
                                )
                                rawCdrList.add(Pair(rec, fNum))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        if (rawCdrList.isEmpty()) return

        val nowTime = System.currentTimeMillis()
        val newlyArrivedOtps = mutableListOf<Pair<ProvisionedNumber, String>>()

        val currentActive = _state.value.activeNumbers

        val updatedActive = currentActive.map { prov ->
            if (!prov.otpCode.isNullOrEmpty() && prov.status == "success") {
                return@map prov
            }
            val cleanDigits = prov.number.replace("[^0-9]".toRegex(), "")
            if (cleanDigits.length < 7) return@map prov

            var matchedCdr: UnixSmsCdrRecord? = null
            for ((cdr, rawPhone) in rawCdrList) {
                if (isPhoneNumberMatch(cleanDigits, rawPhone)) {
                    matchedCdr = cdr
                    break
                }
            }

            if (matchedCdr != null) {
                val extracted = extractOtp(matchedCdr.message, cleanDigits).ifEmpty {
                    extractOtp(matchedCdr.message, "")
                }
                val finalOtp = if (extracted.isNotEmpty()) extracted else "OTP"
                if (prov.otpCode != finalOtp || prov.status != "success") {
                    val updatedProv = prov.copy(
                        status = "success",
                        otpCode = finalOtp,
                        otpMessage = matchedCdr.message
                    )
                    newlyArrivedOtps.add(Pair(updatedProv, finalOtp))
                    updatedProv
                } else prov
            } else prov
        }

        var newTodayOtpCount = _state.value.todayOtpCount
        val currentUploaded = _state.value.uploadedNumbers
        var uploadedModified = false

        val updatedUploaded = currentUploaded.map { up ->
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
                var directCdr: UnixSmsCdrRecord? = null
                for ((cdr, rawPhone) in rawCdrList) {
                    if (isPhoneNumberMatch(upClean, rawPhone)) {
                        directCdr = cdr
                        break
                    }
                }
                if (directCdr != null) {
                    val extracted = extractOtp(directCdr.message, upClean).ifEmpty {
                        extractOtp(directCdr.message, "")
                    }
                    val finalOtp = if (extracted.isNotEmpty()) extracted else "OTP"
                    uploadedModified = true
                    up.copy(
                        isUsed = true,
                        otpCode = finalOtp,
                        otpMessage = directCdr.message
                    )
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
                    service = prov.range.ifEmpty { "Hadi SMS" },
                    platform = "Hadi SMS",
                    rate = prov.rate
                )
            }
            WithdrawalManager.refreshBalances()

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

        val newCdrEntries = rawCdrList.map { it.first }
        val mergedCdr = (newCdrEntries + _state.value.liveCdrRecords)
            .distinctBy { "${it.num}_${it.message}" }
            .take(35)

        _state.update {
            it.copy(
                liveCdrRecords = mergedCdr,
                activeNumbers = updatedActive,
                uploadedNumbers = if (uploadedModified) updatedUploaded else it.uploadedNumbers,
                todayOtpCount = newTodayOtpCount,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    /**
     * Lamix SMS Gateway Live Polling (/api/v1/messages)
     * Actively fetches messages and individual phone query for every active taken number!
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
        val recordsArr = if (response != null && response.isSuccessful && body.isNotEmpty()) {
            try {
                val root = JSONObject(body)
                root.optJSONArray("records") ?: JSONArray()
            } catch (_: Exception) { JSONArray() }
        } else JSONArray()

        val nowTime = System.currentTimeMillis()
        val newlyArrivedOtps = mutableListOf<Pair<ProvisionedNumber, String>>()
        val currentActive = _state.value.activeNumbers

            val updatedActive = currentActive.map { prov ->
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

                // If not found in general records, query Lamix directly for this exact number!
                if (matchedRecord == null && cleanDigits.length >= 7) {
                    try {
                        val numUrl = "https://panel.lamix.org/api/v1/messages?token=${URLEncoder.encode(token, "UTF-8")}&number=$cleanDigits"
                        val numReq = Request.Builder().url(numUrl).header("User-Agent", "Mozilla/5.0").get().build()
                        val numResp = httpClient.newCall(numReq).execute()
                        val numBody = numResp.body?.string() ?: ""
                        if (numResp.isSuccessful && numBody.isNotEmpty()) {
                            val numRoot = JSONObject(numBody)
                            val numRecords = numRoot.optJSONArray("records") ?: JSONArray()
                            if (numRecords.length() > 0) {
                                matchedRecord = numRecords.optJSONObject(0)
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (matchedRecord != null) {
                    val content = matchedRecord.optString("content", "")
                    val extracted = extractOtp(content, cleanDigits).ifEmpty {
                        extractOtp(content, "")
                    }
                    val finalOtp = if (extracted.isNotEmpty()) extracted else "OTP"
                    if (prov.otpCode != finalOtp || prov.status != "success") {
                        val updatedProv = prov.copy(
                            status = "success",
                            otpCode = finalOtp,
                            otpMessage = content
                        )
                        newlyArrivedOtps.add(Pair(updatedProv, finalOtp))
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
                        val extracted = extractOtp(content, upClean).ifEmpty {
                            extractOtp(content, "")
                        }
                        val finalOtp = if (extracted.isNotEmpty()) extracted else "OTP"
                        uploadedModified = true
                        up.copy(
                            isUsed = true,
                            otpCode = finalOtp,
                            otpMessage = content
                        )
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
                WithdrawalManager.refreshBalances()

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

    /**
     * Unix SMS Gateway Live Polling (/v2/cdr)
     * Matches ONLY numbers uploaded/assigned to UNIX gateway.
     */
    private suspend fun pollUnixSmsCdr(context: Context) {
        val token = _state.value.apiToken.ifEmpty { DEFAULT_TOKEN }
        val url = "https://agent-api.unixsms.com/v2/cdr?token=${URLEncoder.encode(token, "UTF-8")}&records=100"

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
                val rawCdrList = mutableListOf<Pair<UnixSmsCdrRecord, String>>()

                val maxCdrToParse = minOf(dataArr.length(), 100)
                for (i in 0 until maxCdrToParse) {
                    val o = dataArr.optJSONObject(i) ?: continue
                    val rawNum = o.optString("num", "")
                    val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(rawNum)
                    val cName = countryInfo?.name ?: "Global"
                    val cFlag = countryInfo?.flag ?: "🌐"
                    val masked = maskPhoneNumberMiddle4(rawNum)
                    val rec = UnixSmsCdrRecord(
                        dt = o.optString("dt", ""),
                        num = masked, // Masked phone number (strictly middle 4 digits masked with ****)
                        cli = o.optString("cli", ""),
                        message = o.optString("message", ""),
                        payout = o.optString("payout", "0"),
                        countryName = cName,
                        countryFlag = cFlag
                    )
                    rawCdrList.add(Pair(rec, rawNum))
                }

                // 24-hour cycle auto reset for Live CDR
                val nowTime = System.currentTimeMillis()
                val cdrCycleStart = prefs?.getLong("unix_cdr_cycle_start_v2", 0L) ?: 0L
                if (cdrCycleStart <= 0L || nowTime - cdrCycleStart >= 24 * 60 * 60 * 1000L) {
                    prefs?.edit()?.putLong("unix_cdr_cycle_start_v2", nowTime)?.apply()
                    _state.update { it.copy(liveCdrRecords = emptyList()) }
                }

                val newlyArrivedOtps = mutableListOf<Pair<ProvisionedNumber, String>>()
                val currentActive = _state.value.activeNumbers

                val updatedActive = currentActive.map { prov ->
                    if (!prov.otpCode.isNullOrEmpty() && prov.status == "success") {
                        return@map prov
                    }

                    val cleanDigits = prov.number.replace("[^0-9]".toRegex(), "")
                    if (cleanDigits.length < 7) {
                        return@map prov
                    }

                    var matchedCdr: UnixSmsCdrRecord? = null
                    for ((cdr, rawPhone) in rawCdrList) {
                        if (isPhoneNumberMatch(cleanDigits, rawPhone)) {
                            matchedCdr = cdr
                            break
                        }
                    }

                    if (matchedCdr != null) {
                        val extracted = extractOtp(matchedCdr.message, cleanDigits).ifEmpty {
                            extractOtp(matchedCdr.message, "")
                        }
                        val finalOtp = if (extracted.isNotEmpty()) extracted else "OTP"
                        if (prov.otpCode != finalOtp || prov.status != "success") {
                            val updatedProv = prov.copy(
                                status = "success",
                                otpCode = finalOtp,
                                otpMessage = matchedCdr.message
                            )
                            newlyArrivedOtps.add(Pair(updatedProv, finalOtp))
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
                        var directCdr: UnixSmsCdrRecord? = null
                        for ((cdr, rawPhone) in rawCdrList) {
                            if (isPhoneNumberMatch(upClean, rawPhone)) {
                                directCdr = cdr
                                break
                            }
                        }
                        if (directCdr != null) {
                            val extracted = extractOtp(directCdr.message, upClean).ifEmpty {
                                extractOtp(directCdr.message, "")
                            }
                            val finalOtp = if (extracted.isNotEmpty()) extracted else "OTP"
                            uploadedModified = true
                            up.copy(
                                isUsed = true,
                                otpCode = finalOtp,
                                otpMessage = directCdr.message
                            )
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
                    WithdrawalManager.refreshBalances()

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

                val cdrList = rawCdrList.map { it.first }
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

    fun extractOtp(message: String, phoneDigits: String = ""): String {
        return com.example.util.OtpExtractionHelper.extractOtp(message, phoneDigits) ?: ""
    }

    fun isPhoneNumberMatch(num1: String, num2: String): Boolean {
        val d1 = num1.replace("[^0-9]".toRegex(), "")
        val d2 = num2.replace("[^0-9]".toRegex(), "")
        if (d1.isEmpty() || d2.isEmpty()) return false
        if (d1 == d2) return true
        if (d1.length >= 7 && d2.length >= 7) {
            if (d1.endsWith(d2) || d2.endsWith(d1)) return true
            if (d1.contains(d2) || d2.contains(d1)) return true
            val len = minOf(d1.length, d2.length, 9)
            if (len >= 7 && d1.takeLast(len) == d2.takeLast(len)) return true
            if (d1.takeLast(8) == d2.takeLast(8)) return true
            if (d1.takeLast(7) == d2.takeLast(7)) return true
        }
        return false
    }

    /**
     * Masks strictly the middle 4 digits of the phone number with asterisks (****),
     * keeping the start (prefix/country code) and end (suffix) digits clearly visible.
     * e.g. 593969423261 -> 5939****3261
     *      +8801712345678 -> +8801****5678
     */
    fun maskPhoneNumberMiddle4(number: String): String {
        if (number.contains("*")) return number
        val hasPlus = number.trim().startsWith("+")
        val clean = number.replace("[^0-9]".toRegex(), "")
        if (clean.length <= 4) return (if (hasPlus) "+" else "") + "****"
        if (clean.length in 5..6) {
            val midStart = 1
            val prefix = clean.substring(0, midStart)
            val suffix = clean.substring(clean.length - 1)
            return (if (hasPlus) "+" else "") + prefix + "****" + suffix
        }
        val maskCount = 4
        val remaining = clean.length - maskCount
        val prefixLen = remaining / 2
        val suffixLen = remaining - prefixLen
        val prefix = clean.substring(0, prefixLen)
        val suffix = clean.substring(clean.length - suffixLen)
        return (if (hasPlus) "+" else "") + prefix + "****" + suffix
    }

    fun maskPhoneNumberMiddle3(number: String): String = maskPhoneNumberMiddle4(number)

    private fun launchMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }



    fun clearActiveNumbers() {
        _state.update { it.copy(activeNumbers = emptyList()) }
        saveActiveNumbers(emptyList())
    }

    fun onUserLoggedOut() {
        stopPolling()
        _state.update {
            it.copy(
                activeNumbers = emptyList(),
                liveCdrRecords = emptyList(),
                isPolling = false,
                todayOtpCount = 0
            )
        }
    }
}
