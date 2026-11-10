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
import kotlinx.coroutines.coroutineScope
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
    val service: String, // Canonical: "FB Fresh", "FB Clone", "Instagram", "WhatsApp", "OTP Work"
    val gateway: String = "LAMIX", // "LAMIX", "UNIX", or "HADI"
    val customRate: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val isUsed: Boolean = false,
    val otpCode: String? = null,
    val otpMessage: String? = null,
    val numberType: String = "Fresh" // "Fresh" (FB Fresh), "Old" (FB Clone), or "Normal" (IG/WA/OTP Work)
)

data class UnixSmsCdrRecord(
    val dt: String,
    val num: String,
    val cli: String,
    val message: String,
    val payout: String,
    val countryName: String = "",
    val countryFlag: String = "",
    val countryCode: String = "",
    val gateway: String = "UNIX", // "UNIX", "HADI", or "LAMIX"
    val serviceName: String = "",
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
    val selectedCountry: String = "US",
    val selectedService: String = "✨ FB Fresh",
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
    private const val KEY_SAVED_SERVICE = "unix_saved_service"
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

    private val _state = MutableStateFlow(UnixSmsState())
    val state: StateFlow<UnixSmsState> = _state.asStateFlow()

    private var appContext: Context? = null

    /**
     * Maps any service string & numberType into one of the 5 strict canonical service names:
     * "FB Fresh", "FB Clone", "Instagram", "WhatsApp", "OTP Work"
     */
    fun canonicalService(service: String, numberType: String = ""): String {
        val s = service.trim()
        val t = numberType.trim()
        return when {
            s.contains("Clone", ignoreCase = true) || s.contains("FB Old", ignoreCase = true) -> "FB Clone"
            s.contains("Fresh", ignoreCase = true) || s.contains("FB New", ignoreCase = true) -> "FB Fresh"
            s.contains("Instagram", ignoreCase = true) || s.contains("IG", ignoreCase = true) || s.contains("Insta", ignoreCase = true) -> "Instagram"
            s.contains("WhatsApp", ignoreCase = true) || s.contains("WA", ignoreCase = true) || s.contains("WP", ignoreCase = true) -> "WhatsApp"
            s.contains("OTP", ignoreCase = true) -> "OTP Work"
            s.contains("FB", ignoreCase = true) || s.contains("Face", ignoreCase = true) -> {
                if (t.contains("Old", ignoreCase = true) || t.contains("Clone", ignoreCase = true) || t.contains("Taken", ignoreCase = true)) {
                    "FB Clone"
                } else {
                    "FB Fresh"
                }
            }
            else -> "FB Fresh"
        }
    }

    fun canonicalNumberTypeForService(canonicalSrv: String, requestedType: String = ""): String {
        return when (canonicalSrv) {
            "FB Fresh" -> "Fresh"
            "FB Clone" -> "Old"
            else -> "Normal" // Instagram, WhatsApp, OTP Work have NO Fresh/Old distinction
        }
    }

    private fun slotKey(service: String, numberType: String, countryCode: String, digits: String): String {
        val canonSrv = canonicalService(service, numberType)
        return "${canonSrv}:::${countryCode.uppercase().trim()}:::${digits}"
    }

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

            // Load persisted permanentlyUsedDigits first so taken numbers never revert
            val savedUsedSet = prefs?.getStringSet(KEY_PERMANENTLY_USED_DIGITS, emptySet()) ?: emptySet()
            permanentlyUsedDigits.addAll(savedUsedSet)

            val uploaded = loadUploadedNumbers()
            val active = loadActiveNumbers()
            val savedCountry = prefs?.getString(KEY_SAVED_COUNTRY, "US") ?: "US"
            val savedService = prefs?.getString(KEY_SAVED_SERVICE, "✨ FB Fresh") ?: "✨ FB Fresh"
            val loadedCountryRates = loadCountryRates()

            active.forEach {
                val d = it.number.replace("[^0-9]".toRegex(), "")
                if (d.length >= 7) permanentlyUsedDigits.add(d)
            }
            uploaded.filter { it.isUsed }.forEach {
                val d = it.number.replace("[^0-9]".toRegex(), "")
                if (d.length >= 7) permanentlyUsedDigits.add(d)
            }
            savePermanentlyUsedDigits()

            _state.update { current ->
                val finalUploaded = if (current.uploadedNumbers.isNotEmpty()) {
                    current.uploadedNumbers
                } else {
                    uploaded
                }
                current.copy(
                    apiToken = token,
                    lamixApiToken = lamixToken,
                    hadiApiToken = hadiToken,
                    selectedUploadGateway = gateway,
                    uploadedNumbers = finalUploaded,
                    activeNumbers = if (current.activeNumbers.isNotEmpty()) current.activeNumbers else active,
                    selectedCountry = if (savedCountry.equals("ALL", true)) "US" else savedCountry,
                    selectedService = savedService,
                    todayOtpCount = todayOtp,
                    otpRatePerSms = savedRate,
                    countryRates = loadedCountryRates
                )
            }

            if (_state.value.uploadedNumbers.isNotEmpty()) {
                saveUploadedNumbers(_state.value.uploadedNumbers)
            }

            clearLiveDlrLogs()
            startFastOtpPolling(context)
        }
    }

    /**
     * Delete and purge all existing live DLR logs and remove any saved CDR cache from disk.
     * DLR logs are strictly in-memory only and never saved to the database.
     */
    fun clearLiveDlrLogs() {
        _state.update { it.copy(liveCdrRecords = emptyList()) }
        prefs?.edit()
            ?.remove("unix_live_cdr_records_v2")
            ?.remove("unix_cdr_records")
            ?.remove("unix_cdr_cycle_start_v2")
            ?.apply()
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

    fun applyCloudNumbers(
        cloudList: List<UploadedNumberItem>,
        context: Context? = null,
        cloudTimestamp: Long = 0L,
        explicitClearTimestamp: Long = 0L
    ) {
        if (context != null) {
            appContext = context.applicationContext
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
        val current = _state.value.uploadedNumbers
        val activeNumbers = _state.value.activeNumbers
        val now = System.currentTimeMillis()
        // Protect against future-skewed timestamps (e.g. from devices with wrong system clock)
        val safeClearTs = if (explicitClearTimestamp > now + 60_000L) 0L else explicitClearTimestamp

        if (cloudList.isEmpty()) {
            // Only clear local uploaded numbers if Admin explicitly cleared all numbers AFTER our local items were added
            if (safeClearTs > 0L) {
                val survivingLocal = current.filter { it.addedAt > safeClearTs }
                _state.update { it.copy(uploadedNumbers = survivingLocal) }
                saveUploadedNumbers(survivingLocal)
            } else if (current.isNotEmpty() && AuthManager.isAdmin()) {
                // Admin has local uploaded numbers that haven't reached cloud yet — push them to cloud so all users get them!
                GlobalCloudSyncService.pushToCloud(
                    uploadedNumbers = current,
                    unixRate = _state.value.otpRatePerSms,
                    zenexRate = VirtualNumberManager.zenexOtpRate.value,
                    countryRates = _state.value.countryRates,
                    mergeWithCloudFirst = true
                )
            }
            return
        }

        val localBySlot = current.associateBy {
            val d = it.number.replace("[^0-9]".toRegex(), "")
            slotKey(it.service, it.numberType, it.countryCode, d)
        }
        val localByDigits = current.associateBy { it.number.replace("[^0-9]".toRegex(), "") }

        val mergedMap = LinkedHashMap<String, UploadedNumberItem>(cloudList.size + current.size)

        for (cloudItem in cloudList) {
            val cleanDigits = cloudItem.number.replace("[^0-9]".toRegex(), "")
            if (cleanDigits.length < 7) continue
            val canonSrv = canonicalService(cloudItem.service, cloudItem.numberType)
            val canonType = canonicalNumberTypeForService(canonSrv, cloudItem.numberType)
            val sKey = slotKey(canonSrv, canonType, cloudItem.countryCode, cleanDigits)
            val local = localBySlot[sKey] ?: localByDigits[cleanDigits]
            val isActiveOnDevice = activeNumbers.any { isPhoneNumberMatch(it.number, cleanDigits) }
            val isPermanentlyUsed = permanentlyUsedDigits.contains(cleanDigits) || permanentlyUsedDigits.contains(sKey)
            val isUsed = isActiveOnDevice || isPermanentlyUsed || (local?.isUsed == true) || cloudItem.isUsed
            if (isUsed) {
                permanentlyUsedDigits.add(cleanDigits)
                permanentlyUsedDigits.add(sKey)
            }
            mergedMap[sKey] = cloudItem.copy(
                id = local?.id?.ifEmpty { cloudItem.id } ?: cloudItem.id,
                service = canonSrv,
                numberType = canonType,
                isUsed = isUsed,
                otpCode = null,
                otpMessage = null
            )
        }

        // Preserve any locally uploaded numbers by Admin that were added recently (within last 2 minutes) and are still syncing
        if (AuthManager.isAdmin()) {
            var hasUnpushedAdminItems = false
            for (localItem in current) {
                if (Math.abs(now - localItem.addedAt) < 120_000L && (safeClearTs == 0L || localItem.addedAt > safeClearTs)) {
                    val cleanDigits = localItem.number.replace("[^0-9]".toRegex(), "")
                    val canonSrv = canonicalService(localItem.service, localItem.numberType)
                    val canonType = canonicalNumberTypeForService(canonSrv, localItem.numberType)
                    val sKey = slotKey(canonSrv, canonType, localItem.countryCode, cleanDigits)
                    if (!mergedMap.containsKey(sKey)) {
                        mergedMap[sKey] = localItem.copy(service = canonSrv, numberType = canonType)
                        hasUnpushedAdminItems = true
                    }
                }
            }
            if (hasUnpushedAdminItems) {
                GlobalCloudSyncService.pushToCloud(
                    uploadedNumbers = mergedMap.values.toList(),
                    unixRate = _state.value.otpRatePerSms,
                    zenexRate = VirtualNumberManager.zenexOtpRate.value,
                    countryRates = _state.value.countryRates,
                    mergeWithCloudFirst = true
                )
            }
        }

        val merged = mergedMap.values.toList()
        savePermanentlyUsedDigits()
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
            val obj = JSONObject(json)
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
        val obj = JSONObject()
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
                val rawService = o.optString("service", "FB Fresh")
                val rawType = o.optString("numberType", "Fresh")
                val canonSrv = canonicalService(rawService, rawType)
                val canonType = canonicalNumberTypeForService(canonSrv, rawType)
                list.add(
                    UploadedNumberItem(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        number = o.getString("number"),
                        countryCode = o.optString("countryCode", "US").uppercase().trim(),
                        countryName = o.optString("countryName", "United States"),
                        flag = o.optString("flag", "🇺🇸"),
                        service = canonSrv,
                        gateway = o.optString("gateway", "LAMIX"),
                        customRate = if (o.has("customRate") && !o.isNull("customRate")) o.optDouble("customRate") else null,
                        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                        isUsed = o.optBoolean("isUsed", false),
                        otpCode = null,
                        otpMessage = null,
                        numberType = canonType
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveUploadedNumbers(list: List<UploadedNumberItem>) {
        scope.launch(Dispatchers.IO) {
            try {
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
                    o.put("numberType", item.numberType)
                    arr.put(o)
                }
                prefs?.edit()?.putString(KEY_UPLOADED_NUMBERS, arr.toString())?.apply()
            } catch (_: Exception) {}
        }
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
                        expiresAt = o.optLong("expiresAt", System.currentTimeMillis() + 20 * 60 * 1000L),
                        gateway = o.optString("gateway", "LAMIX"),
                        rate = o.optDouble("rate", 0.50),
                        fbStatus = o.optString("fbStatus").ifEmpty { null },
                        fbTagType = fbTag,
                        userEmail = o.optString("userEmail", effectiveEmail),
                        isMarked = o.optBoolean("isMarked", false)
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
            o.put("isMarked", item.isMarked)
            arr.put(o)
        }
        prefs?.edit()?.putString(getUserActiveNumbersKey(effectiveEmail), arr.toString())?.apply()
    }

    fun toggleMarkActiveNumber(id: String) {
        _state.update { current ->
            val updated = current.activeNumbers.map { item ->
                if (item.id == id && item.range.contains("Clone", ignoreCase = true)) {
                    item.copy(isMarked = !item.isMarked)
                } else {
                    item
                }
            }
            saveActiveNumbers(updated)
            current.copy(activeNumbers = updated)
        }
    }

    fun resetTodayCycle(context: Context? = null) {
        OtpHistoryManager.markPanel24hReset("Premium Numbers")
        prefs?.edit()?.putInt(KEY_TODAY_OTP, 0)?.apply()
        saveActiveNumbers(emptyList())
        _state.update {
            it.copy(
                activeNumbers = emptyList(),
                todayOtpCount = 0
            )
        }
        if (context != null) {
            mainHandler.post {
                Toast.makeText(context, "✓ Premium Numbers ওটিপি ও অ্যাক্টিভ নাম্বার রিসেট করা হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeActiveNumber(id: String) {
        _state.update { current ->
            val updated = current.activeNumbers.filterNot { it.id == id }
            saveActiveNumbers(updated)
            current.copy(activeNumbers = updated)
        }
    }

    fun clearExpiredAndFailedNumbers(context: Context? = null): Int {
        val now = System.currentTimeMillis()
        var removedCount = 0
        _state.update { current ->
            val surviving = current.activeNumbers.filter { prov ->
                val hasOtp = !prov.otpCode.isNullOrEmpty() || prov.status.equals("success", ignoreCase = true)
                val isFailedOrExpired = !hasOtp && (
                    prov.status.equals("expired", ignoreCase = true) ||
                    prov.status.equals("failed", ignoreCase = true) ||
                    now >= prov.expiresAt
                )
                if (isFailedOrExpired) {
                    removedCount++
                    false
                } else {
                    true
                }
            }
            saveActiveNumbers(surviving)
            current.copy(activeNumbers = surviving)
        }
        if (context != null) {
            mainHandler.post {
                if (removedCount > 0) {
                    Toast.makeText(context, "🗑️ $removedCount টি Expired/Failed নাম্বার মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "কোনো Expired বা Failed নাম্বার নেই", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return removedCount
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

    fun setOtpRate(rate: Double) {
        val cleanRate = (Math.round(rate.coerceAtLeast(0.01) * 100.0) / 100.0)
        _state.update { it.copy(otpRatePerSms = cleanRate) }
        prefs?.edit()?.putFloat(KEY_OTP_RATE, cleanRate.toFloat())?.apply()
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
     * Admin Panel: Add numbers via plain text or file content (supports 5,000 / 10,000+ numbers in one batch)
     * Keeps numbers strictly separated by canonical service ("FB Fresh", "FB Clone", "Instagram", "WhatsApp", "OTP Work")
     * and by country code so slots never overwrite or merge with each other.
     */
    @Synchronized
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
        val phonePattern = Pattern.compile("\\+?[0-9]{7,16}")
        val matcher = phonePattern.matcher(rawContent)
        val extractedSet = LinkedHashSet<String>(16384)
        while (matcher.find()) {
            val digits = matcher.group().removePrefix("+").replace("[^0-9]".toRegex(), "")
            if (digits.length >= 7) {
                extractedSet.add(digits)
            }
        }
        val lines = extractedSet.toList()
        if (lines.isEmpty()) return 0

        val canonSrv = canonicalService(service, numberType)
        val canonType = canonicalNumberTypeForService(canonSrv, numberType)
        val cleanCountryCode = countryCode.uppercase().trim()

        // Unconditionally remove uploaded digits/slotKeys from permanentlyUsedDigits so they are 100% available
        lines.forEach { d ->
            permanentlyUsedDigits.remove(d)
            permanentlyUsedDigits.remove(slotKey(canonSrv, canonType, cleanCountryCode, d))
        }
        savePermanentlyUsedDigits()

        val existingList = _state.value.uploadedNumbers
        val existingBySlot = LinkedHashMap<String, UploadedNumberItem>(existingList.size + lines.size + 32)
        for (item in existingList) {
            val d = item.number.replace("[^0-9]".toRegex(), "")
            val itemCanonSrv = canonicalService(item.service, item.numberType)
            val itemCanonType = canonicalNumberTypeForService(itemCanonSrv, item.numberType)
            val key = slotKey(itemCanonSrv, itemCanonType, item.countryCode, d)
            existingBySlot[key] = item.copy(service = itemCanonSrv, numberType = itemCanonType)
        }

        val now = System.currentTimeMillis()
        var processedCount = 0

        for (num in lines) {
            val key = slotKey(canonSrv, canonType, cleanCountryCode, num)
            val existing = existingBySlot[key]
            if (existing != null) {
                existingBySlot[key] = existing.copy(
                    countryCode = cleanCountryCode,
                    countryName = countryName,
                    flag = flag,
                    service = canonSrv,
                    gateway = gateway.uppercase(),
                    customRate = customRate,
                    numberType = canonType,
                    isUsed = false,
                    otpCode = null,
                    otpMessage = null,
                    addedAt = now
                )
            } else {
                existingBySlot[key] = UploadedNumberItem(
                    number = num,
                    countryCode = cleanCountryCode,
                    countryName = countryName,
                    flag = flag,
                    service = canonSrv,
                    gateway = gateway.uppercase(),
                    customRate = customRate,
                    numberType = canonType,
                    isUsed = false,
                    otpCode = null,
                    otpMessage = null,
                    addedAt = now
                )
            }
            processedCount++
        }

        val updated = existingBySlot.values.toList()
        _state.update { it.copy(uploadedNumbers = updated) }
        saveUploadedNumbers(updated)

        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            mergeWithCloudFirst = true
        )

        return processedCount
    }

    /**
     * Admin Panel Dual Slot Upload for Facebook:
     * Uploads FB Fresh and/or FB Clone numbers simultaneously for the same country in one atomic cloud push.
     */
    @Synchronized
    fun addFacebookDualSlotsBatch(
        freshRawContent: String,
        cloneRawContent: String,
        countryCode: String,
        countryName: String,
        flag: String,
        gateway: String = _state.value.selectedUploadGateway,
        customRate: Double? = null
    ): Pair<Int, Int> {
        val phonePattern = Pattern.compile("\\+?[0-9]{7,16}")

        val freshSet = LinkedHashSet<String>(8192)
        if (freshRawContent.isNotBlank()) {
            val m1 = phonePattern.matcher(freshRawContent)
            while (m1.find()) {
                val d = m1.group().removePrefix("+").replace("[^0-9]".toRegex(), "")
                if (d.length >= 7) freshSet.add(d)
            }
        }

        val cloneSet = LinkedHashSet<String>(8192)
        if (cloneRawContent.isNotBlank()) {
            val m2 = phonePattern.matcher(cloneRawContent)
            while (m2.find()) {
                val d = m2.group().removePrefix("+").replace("[^0-9]".toRegex(), "")
                if (d.length >= 7) cloneSet.add(d)
            }
        }

        if (freshSet.isEmpty() && cloneSet.isEmpty()) return Pair(0, 0)

        val cleanCountryCode = countryCode.uppercase().trim()

        freshSet.forEach { d ->
            permanentlyUsedDigits.remove(d)
            permanentlyUsedDigits.remove(slotKey("FB Fresh", "Fresh", cleanCountryCode, d))
        }
        cloneSet.forEach { d ->
            permanentlyUsedDigits.remove(d)
            permanentlyUsedDigits.remove(slotKey("FB Clone", "Old", cleanCountryCode, d))
        }
        savePermanentlyUsedDigits()

        val existingList = _state.value.uploadedNumbers
        val existingBySlot = LinkedHashMap<String, UploadedNumberItem>(existingList.size + freshSet.size + cloneSet.size + 32)
        for (item in existingList) {
            val d = item.number.replace("[^0-9]".toRegex(), "")
            val itemCanonSrv = canonicalService(item.service, item.numberType)
            val itemCanonType = canonicalNumberTypeForService(itemCanonSrv, item.numberType)
            val key = slotKey(itemCanonSrv, itemCanonType, item.countryCode, d)
            existingBySlot[key] = item.copy(service = itemCanonSrv, numberType = itemCanonType)
        }

        val now = System.currentTimeMillis()
        for (num in freshSet) {
            val key = slotKey("FB Fresh", "Fresh", cleanCountryCode, num)
            val existing = existingBySlot[key]
            existingBySlot[key] = (existing ?: UploadedNumberItem(
                number = num,
                countryCode = cleanCountryCode,
                countryName = countryName,
                flag = flag,
                service = "FB Fresh"
            )).copy(
                countryCode = cleanCountryCode,
                countryName = countryName,
                flag = flag,
                service = "FB Fresh",
                gateway = gateway.uppercase(),
                customRate = customRate,
                numberType = "Fresh",
                isUsed = false,
                otpCode = null,
                otpMessage = null,
                addedAt = now
            )
        }

        for (num in cloneSet) {
            val key = slotKey("FB Clone", "Old", cleanCountryCode, num)
            val existing = existingBySlot[key]
            existingBySlot[key] = (existing ?: UploadedNumberItem(
                number = num,
                countryCode = cleanCountryCode,
                countryName = countryName,
                flag = flag,
                service = "FB Clone"
            )).copy(
                countryCode = cleanCountryCode,
                countryName = countryName,
                flag = flag,
                service = "FB Clone",
                gateway = gateway.uppercase(),
                customRate = customRate,
                numberType = "Old",
                isUsed = false,
                otpCode = null,
                otpMessage = null,
                addedAt = now
            )
        }

        val updated = existingBySlot.values.toList()
        _state.update { it.copy(uploadedNumbers = updated) }
        saveUploadedNumbers(updated)

        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            mergeWithCloudFirst = true
        )

        return Pair(freshSet.size, cloneSet.size)
    }

    /**
     * Admin: Reset all used uploaded numbers back to available status
     */
    fun resetUsedNumbersToAvailable(onComplete: ((Int) -> Unit)? = null): Int {
        permanentlyUsedDigits.clear()
        savePermanentlyUsedDigits()
        val activeNumbers = _state.value.activeNumbers
        val current = _state.value.uploadedNumbers
        var count = 0
        val updated = current.map { item ->
            val cleanDigits = item.number.replace("[^0-9]".toRegex(), "")
            val isActive = activeNumbers.any { it.status == "pending" && isPhoneNumberMatch(it.number, cleanDigits) }
            if (item.isUsed && !isActive) {
                count++
                item.copy(isUsed = false, otpCode = null, otpMessage = null)
            } else {
                item
            }
        }
        _state.update { it.copy(uploadedNumbers = updated) }
        saveUploadedNumbers(updated)
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            onComplete = { onComplete?.invoke(count) }
        )
        return count
    }

    /**
     * Admin: Delete numbers by specific Country or All
     */
    fun deleteNumbersByCountry(countryCode: String, onComplete: ((Boolean) -> Unit)? = null) {
        if (!AuthManager.isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val updated = if (countryCode.equals("ALL", ignoreCase = true)) {
            permanentlyUsedDigits.clear()
            savePermanentlyUsedDigits()
            emptyList()
        } else {
            val toDelete = _state.value.uploadedNumbers.filter { it.countryCode.equals(countryCode, ignoreCase = true) }
            toDelete.forEach {
                val d = it.number.replace("[^0-9]".toRegex(), "")
                permanentlyUsedDigits.remove(d)
                permanentlyUsedDigits.remove(slotKey(it.service, it.numberType, it.countryCode, d))
            }
            savePermanentlyUsedDigits()
            _state.value.uploadedNumbers.filterNot { it.countryCode.equals(countryCode, ignoreCase = true) }
        }
        val isDeleteAll = countryCode.equals("ALL", ignoreCase = true)
        val updatedActive = _state.value.activeNumbers
        _state.update { it.copy(uploadedNumbers = updated, activeNumbers = updatedActive) }
        saveUploadedNumbers(updated)
        saveActiveNumbers(updatedActive)
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            allowEmptyClear = isDeleteAll || updated.isEmpty(),
            onComplete = onComplete
        )
    }

    fun deleteSingleNumber(id: String, onComplete: ((Boolean) -> Unit)? = null) {
        if (!AuthManager.isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val target = _state.value.uploadedNumbers.find { it.id == id }
        if (target != null) {
            val d = target.number.replace("[^0-9]".toRegex(), "")
            permanentlyUsedDigits.remove(d)
            permanentlyUsedDigits.remove(slotKey(target.service, target.numberType, target.countryCode, d))
            savePermanentlyUsedDigits()
        }
        val updated = _state.value.uploadedNumbers.filterNot { it.id == id }
        val updatedActive = _state.value.activeNumbers

        _state.update { it.copy(uploadedNumbers = updated, activeNumbers = updatedActive) }
        saveUploadedNumbers(updated)
        saveActiveNumbers(updatedActive)
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            allowEmptyClear = updated.isEmpty(),
            onComplete = onComplete
        )
    }

    fun updateUploadedNumber(
        updatedItem: UploadedNumberItem,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!AuthManager.isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val canonSrv = canonicalService(updatedItem.service, updatedItem.numberType)
        val canonType = canonicalNumberTypeForService(canonSrv, updatedItem.numberType)
        val normalizedItem = updatedItem.copy(service = canonSrv, numberType = canonType)

        val current = _state.value.uploadedNumbers
        val index = current.indexOfFirst { it.id == normalizedItem.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { set(index, normalizedItem) }
        } else {
            current + normalizedItem
        }

        val cleanDigits = normalizedItem.number.replace("[^0-9]".toRegex(), "")
        val sKey = slotKey(canonSrv, canonType, normalizedItem.countryCode, cleanDigits)
        if (!normalizedItem.isUsed) {
            permanentlyUsedDigits.remove(cleanDigits)
            permanentlyUsedDigits.remove(sKey)
        } else {
            permanentlyUsedDigits.add(cleanDigits)
            permanentlyUsedDigits.add(sKey)
        }
        savePermanentlyUsedDigits()

        _state.update { it.copy(uploadedNumbers = updated) }
        saveUploadedNumbers(updated)

        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = updated,
            unixRate = _state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            countryRates = _state.value.countryRates,
            onComplete = onComplete
        )
    }

    fun clearAllUploadedNumbers(onComplete: ((Boolean) -> Unit)? = null) {
        if (!AuthManager.isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        deleteNumbersByCountry("ALL", onComplete)
    }

    fun purgeInvalidUploadedNumbers(onComplete: ((Boolean) -> Unit)? = null): Int {
        if (!AuthManager.isAdmin()) {
            onComplete?.invoke(false)
            return 0
        }
        val current = _state.value.uploadedNumbers
        val initialSize = current.size
        val cleaned = current.filter { item ->
            val digits = item.number.replace("[^0-9]".toRegex(), "")
            digits.length in 7..16 && item.countryCode.isNotBlank() && item.countryName.isNotBlank()
        }.distinctBy {
            val digits = it.number.replace("[^0-9]".toRegex(), "")
            slotKey(it.service, it.numberType, it.countryCode, digits)
        }

        val removed = initialSize - cleaned.size
        if (removed > 0) {
            _state.update { it.copy(uploadedNumbers = cleaned) }
            saveUploadedNumbers(cleaned)
            GlobalCloudSyncService.pushToCloud(
                uploadedNumbers = cleaned,
                unixRate = _state.value.otpRatePerSms,
                zenexRate = VirtualNumberManager.zenexOtpRate.value,
                countryRates = _state.value.countryRates,
                allowEmptyClear = cleaned.isEmpty(),
                onComplete = onComplete
            )
        } else {
            onComplete?.invoke(true)
        }
        return removed
    }

    fun setSelectedCountry(countryCode: String) {
        val clean = countryCode.trim().uppercase().ifEmpty { "US" }
        prefs?.edit()?.putString(KEY_SAVED_COUNTRY, clean)?.apply()
        _state.update { it.copy(selectedCountry = clean) }
    }

    fun setSelectedService(service: String) {
        val clean = service.trim().ifEmpty { "✨ FB Fresh" }
        prefs?.edit()?.putString(KEY_SAVED_SERVICE, clean)?.apply()
        _state.update { it.copy(selectedService = clean) }
    }

    fun setRequestCount(count: Int) {
        val safe = count.coerceIn(1, 10)
        _state.update { it.copy(requestCount = safe) }
    }

    /**
     * Strictly checks if an uploaded number belongs to the requested service & type.
     * Never mixes FB Fresh with FB Clone, or Instagram/WhatsApp/OTP Work with each other.
     */
    fun isNumberMatchingService(item: UploadedNumberItem, serviceLabel: String, targetNumberType: String = "ALL"): Boolean {
        if (item.isUsed) return false
        val cleanDigits = item.number.replace("[^0-9]".toRegex(), "")
        val itemCanonSrv = canonicalService(item.service, item.numberType)
        val itemCanonType = canonicalNumberTypeForService(itemCanonSrv, item.numberType)
        val sKey = slotKey(itemCanonSrv, itemCanonType, item.countryCode, cleanDigits)
        if (permanentlyUsedDigits.contains(cleanDigits) || permanentlyUsedDigits.contains(sKey)) return false

        if (serviceLabel.equals("ALL", ignoreCase = true) || serviceLabel.isBlank()) return true

        val requestedCanonSrv = canonicalService(
            serviceLabel,
            if (targetNumberType.equals("ALL", ignoreCase = true)) "" else targetNumberType
        )
        return itemCanonSrv.equals(requestedCanonSrv, ignoreCase = true)
    }

    /**
     * User: Get Numbers from Premium pool (Multi-Gateway: Lamix SMS, Unix SMS, Hadi SMS)
     * Strictly filters by exact selected service and exact selected country code (zero cross-country pulling),
     * and atomically locks numbers so they never fall back or revert.
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
        val requestedCanonSrv = canonicalService(service, if (numberType == "ALL") "" else numberType)

        // Filter strictly by the requested service and unused status
        val matchingService = _state.value.uploadedNumbers.filter { item ->
            val cleanDigits = item.number.replace("[^0-9]".toRegex(), "")
            !item.isUsed &&
            !activeNumbers.any { it.status == "pending" && isPhoneNumberMatch(it.number, cleanDigits) } &&
            isNumberMatchingService(item, service, numberType)
        }

        // Strict country matching: NEVER pull from another country when a country is selected
        val cleanCountry = countryCode.trim().uppercase()
        val pool = if (cleanCountry.isEmpty() || cleanCountry == "ALL") {
            // If no specific country was set yet, pick the first available country for this service and only take from that country
            val firstCountry = matchingService.firstOrNull()?.countryCode?.uppercase() ?: ""
            if (firstCountry.isNotEmpty()) {
                matchingService.filter { it.countryCode.equals(firstCountry, ignoreCase = true) }
            } else {
                emptyList()
            }
        } else {
            matchingService.filter { item ->
                item.countryCode.equals(cleanCountry, ignoreCase = true)
            }
        }

        if (pool.isEmpty()) {
            val msg = if (cleanCountry.isNotEmpty() && cleanCountry != "ALL") {
                "নির্বাচিত দেশ ($cleanCountry) ও সার্ভিস ($requestedCanonSrv) এর কোনো খালি নম্বর নেই!"
            } else {
                "$requestedCanonSrv এর জন্য কোনো নম্বর খালি নেই! অ্যাডমিনকে নম্বর আপলোড করতে বলুন।"
            }
            onResult(false, msg)
            return
        }

        val chosen = pool.take(safeCount)
        val chosenIds = chosen.map { it.id }.toSet()

        val chosenDigits = HashSet<String>()
        val chosenSlotKeys = HashSet<String>()

        // Atomically mark chosen numbers as permanently used locally so cloud sync never reverts them
        chosen.forEach { item ->
            val d = item.number.replace("[^0-9]".toRegex(), "")
            val cSrv = canonicalService(item.service, item.numberType)
            val cType = canonicalNumberTypeForService(cSrv, item.numberType)
            if (d.length >= 7) {
                val sKey = slotKey(cSrv, cType, item.countryCode, d)
                permanentlyUsedDigits.add(d)
                permanentlyUsedDigits.add(sKey)
                chosenDigits.add(d)
                chosenSlotKeys.add(sKey)
            }
        }
        savePermanentlyUsedDigits()

        val updatedUploaded = _state.value.uploadedNumbers.map {
            if (it.id in chosenIds) it.copy(isUsed = true) else it
        }
        saveUploadedNumbers(updatedUploaded)

        val now = System.currentTimeMillis()
        val effectiveEmail = AuthManager.currentEmail.value.trim().lowercase()
        val newProvs = chosen.mapIndexed { idx, item ->
            val gw = item.gateway.uppercase().ifEmpty { "LAMIX" }
            val opName = when (gw) {
                "HADI" -> "Hadi SMS 🔥"
                "LAMIX" -> "Lamix SMS ⚡"
                else -> "Unix SMS 🌐"
            }
            val r = item.customRate ?: getRateForCountry(item.countryCode)
            val cSrv = canonicalService(item.service, item.numberType)
            val initialTag = when (cSrv) {
                "FB Clone" -> PhoneItem.TagType.TAKEN
                "FB Fresh" -> PhoneItem.TagType.FRESH
                else -> PhoneItem.TagType.FRESH
            }
            ProvisionedNumber(
                number = item.number,
                country = item.countryName,
                operator = opName,
                iso = item.countryCode.lowercase(),
                status = "pending",
                range = cSrv,
                timestamp = now + (chosen.size - idx), // Keep newest strictly at top in exact order
                expiresAt = now + 20 * 60 * 1000L, // 20-minute expiration
                gateway = gw,
                rate = r,
                userEmail = effectiveEmail,
                fbTagType = initialTag
            )
        }

        val maxCap = OverlayStateManager.uiState.value.maxOverlayNumbersCount.coerceIn(1, 10)
        val updatedActive = (newProvs + _state.value.activeNumbers).sortedByDescending { it.timestamp }.take(maxCap)
        saveActiveNumbers(updatedActive, effectiveEmail)

        _state.update {
            it.copy(
                uploadedNumbers = updatedUploaded,
                activeNumbers = updatedActive,
                selectedCountry = chosen.first().countryCode.uppercase()
            )
        }

        // Atomically mark ONLY the taken numbers as isUsed = true in Supabase so 1 number goes to 1 user
        // without ever overwriting newly uploaded numbers from Admin!
        GlobalCloudSyncService.markNumbersUsedInCloud(
            usedIds = chosenIds,
            usedDigits = chosenDigits,
            usedSlotKeys = chosenSlotKeys
        )

        mainHandler.post {
            if (chosen.isNotEmpty()) {
                val allNumbersText = chosen.joinToString("\n") { it.number.replace("+", "").trim() }
                ClipboardHelper.copyToClipboard(context, allNumbersText, "Premium Numbers")
            }
            VibrationHelper.vibrateSuccess(context)

            startFastOtpPolling(context)

            val rateStr = String.format(java.util.Locale.US, "%.2f", chosen.first().customRate ?: getRateForCountry(chosen.first().countryCode))
            if (chosen.size > 1) {
                onResult(true, "✓ ${chosen.size} টি নম্বর ($requestedCanonSrv • ৳$rateStr) কপি হয়েছে!")
            } else {
                onResult(true, "✓ নম্বর ${chosen.first().number} ($requestedCanonSrv • ৳$rateStr) কপি হয়েছে!")
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
     * Fast OTP & Live DLR Polling across all 3 panels:
     * - Hadi SMS (1s)
     * - Lamix SMS (1s)
     * - Unix SMS CDR (2.5s)
     * Captures OTP for user's taken numbers (credits balance & OTP count) and streams Live DLR Logs
     * strictly for added countries & services without saving any DLR logs to the database.
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

                    if (now - lastPurgeTime >= 30_000L) {
                        lastPurgeTime = now
                        purgeOlderThan24Hours()
                    }

                    val activeNums = _state.value.activeNumbers
                    val uploadedNums = _state.value.uploadedNumbers

                    if (activeNums.isNotEmpty() || uploadedNums.isNotEmpty()) {
                        coroutineScope {
                            // 1. Fast 1-Second Hadi SMS CDR Polling
                            launch { pollHadiSmsCdr(context) }

                            // 2. Fast 1-Second Lamix SMS Polling
                            launch { pollLamixSms(context) }

                            // 3. Unix SMS API (throttled to 2.0s)
                            if (now - lastUnixPollTime >= 2000L) {
                                lastUnixPollTime = now
                                launch { pollUnixSmsCdr(context) }
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(1000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _state.update { it.copy(isPolling = false) }
    }

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

    private fun detectServiceFromCliAndMessage(cli: String, message: String): String {
        val combined = "$cli $message"
        return when {
            combined.contains("Facebook", ignoreCase = true) || combined.contains("FB-", ignoreCase = true) || cli.equals("FB", ignoreCase = true) -> "Facebook"
            combined.contains("Instagram", ignoreCase = true) || combined.contains("IG", ignoreCase = true) -> "Instagram"
            combined.contains("WhatsApp", ignoreCase = true) || combined.contains("WA", ignoreCase = true) -> "WhatsApp"
            else -> "OTP Work"
        }
    }

    /**
     * Filters and enriches incoming DLR records so ONLY messages matching the countries and services
     * added in `uploadedNumbers` or `activeNumbers` are shown in Live DLR Logs.
     */
    private fun filterDlrForAddedCountriesAndServices(
        rawList: List<Pair<UnixSmsCdrRecord, String>>
    ): List<UnixSmsCdrRecord> {
        val uploaded = _state.value.uploadedNumbers
        val active = _state.value.activeNumbers
        if (uploaded.isEmpty() && active.isEmpty()) return emptyList()

        val addedCountryCodes = HashSet<String>()
        val addedServiceCategories = HashSet<String>()

        uploaded.forEach { item ->
            addedCountryCodes.add(item.countryCode.uppercase().trim())
            val canon = canonicalService(item.service, item.numberType)
            if (canon.startsWith("FB")) addedServiceCategories.add("Facebook") else addedServiceCategories.add(canon)
        }
        active.forEach { item ->
            addedCountryCodes.add(item.iso.uppercase().trim())
            val canon = canonicalService(item.range, "")
            if (canon.startsWith("FB")) addedServiceCategories.add("Facebook") else addedServiceCategories.add(canon)
        }

        val filtered = ArrayList<UnixSmsCdrRecord>(rawList.size)
        for ((rec, rawPhone) in rawList) {
            val cleanRaw = rawPhone.replace("[^0-9]".toRegex(), "")
            if (cleanRaw.length < 6 || rec.message.isBlank()) continue

            // 1. Direct match with an uploaded or active number
            val matchedUploaded = uploaded.firstOrNull { isPhoneNumberMatch(it.number, cleanRaw) }
            val matchedActive = active.firstOrNull { isPhoneNumberMatch(it.number, cleanRaw) }

            if (matchedUploaded != null) {
                filtered.add(
                    rec.copy(
                        countryCode = matchedUploaded.countryCode.uppercase(),
                        countryName = matchedUploaded.countryName,
                        countryFlag = matchedUploaded.flag,
                        serviceName = canonicalService(matchedUploaded.service, matchedUploaded.numberType)
                    )
                )
                continue
            }

            if (matchedActive != null) {
                val cInfo = com.example.util.AllCountriesProvider.findByCode(matchedActive.iso)
                filtered.add(
                    rec.copy(
                        countryCode = matchedActive.iso.uppercase(),
                        countryName = matchedActive.country,
                        countryFlag = cInfo?.flag ?: rec.countryFlag.ifEmpty { "🌐" },
                        serviceName = matchedActive.range.ifEmpty { detectServiceFromCliAndMessage(rec.cli, rec.message) }
                    )
                )
                continue
            }

            // 2. Match against added country codes AND added services
            val detectedCountry = com.example.util.AllCountriesProvider.findByPhoneNumber(cleanRaw)
            val cCode = (detectedCountry?.code ?: rec.countryCode).uppercase().trim()
            if (cCode.isEmpty() || !addedCountryCodes.contains(cCode)) continue

            val detectedSrv = detectServiceFromCliAndMessage(rec.cli, rec.message)
            val serviceAllowed = addedServiceCategories.contains("OTP Work") ||
                addedServiceCategories.any { it.equals(detectedSrv, ignoreCase = true) }
            if (!serviceAllowed) continue

            // Map "Facebook" to whichever FB service is active for that country
            val displaySrv = if (detectedSrv == "Facebook") {
                uploaded.firstOrNull {
                    it.countryCode.equals(cCode, ignoreCase = true) && canonicalService(it.service, it.numberType).startsWith("FB")
                }?.let { canonicalService(it.service, it.numberType) } ?: "Facebook"
            } else {
                detectedSrv
            }

            filtered.add(
                rec.copy(
                    countryCode = cCode,
                    countryName = detectedCountry?.name ?: rec.countryName,
                    countryFlag = detectedCountry?.flag ?: rec.countryFlag,
                    serviceName = displaySrv
                )
            )
        }
        return filtered
    }

    /**
     * Common helper to process newly arrived OTPs for user's active taken numbers,
     * credit user balance via OtpHistoryManager & WithdrawalManager, and update in-memory Live DLR Logs.
     */
    private fun processMatchedOtpsAndUpdateDlr(
        context: Context,
        rawCdrList: List<Pair<UnixSmsCdrRecord, String>>,
        gatewayLabel: String
    ) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val currentActive = _state.value.activeNumbers
        val allUploaded = _state.value.uploadedNumbers
        val newlyArrivedOtps = mutableListOf<Pair<ProvisionedNumber, String>>()

        val updatedActive = if (currentEmail.isBlank()) {
            currentActive
        } else {
            currentActive.map { prov ->
                if (!prov.otpCode.isNullOrEmpty() && prov.status == "success") {
                    return@map prov
                }
                val cleanDigits = prov.number.replace("[^0-9]".toRegex(), "")
                if (cleanDigits.length < 7) return@map prov

                // Ensure this active number belongs to the currently logged-in user
                if (prov.userEmail.isNotBlank() && !prov.userEmail.equals(currentEmail, ignoreCase = true)) {
                    return@map prov
                }

                var matchedCdr: UnixSmsCdrRecord? = null
                for ((cdr, rawPhone) in rawCdrList) {
                    if (isPhoneNumberMatch(cleanDigits, rawPhone)) {
                        matchedCdr = cdr
                        break
                    }
                }

                if (matchedCdr != null && matchedCdr.message.isNotBlank()) {
                    val extracted = extractOtp(matchedCdr.message, cleanDigits).ifEmpty {
                        extractOtp(matchedCdr.message, "")
                    }
                    // Strictly require a valid 5–8 digit OTP code before crediting balance and marking success
                    if (extracted.length in 5..8) {
                        if (prov.otpCode != extracted || prov.status != "success") {
                            val effectiveRate = if (prov.rate >= 0.05) prov.rate else getRateForCountry(prov.iso)
                            val updatedProv = prov.copy(
                                status = "success",
                                otpCode = extracted,
                                otpMessage = matchedCdr.message,
                                rate = effectiveRate
                            )
                            newlyArrivedOtps.add(Pair(updatedProv, extracted))
                            updatedProv
                        } else prov
                    } else prov
                } else prov
            }
        }

        var newTodayOtpCount = _state.value.todayOtpCount
        if (newlyArrivedOtps.isNotEmpty() && currentEmail.isNotBlank()) {
            newTodayOtpCount += newlyArrivedOtps.size
            prefs?.edit()?.putInt("${KEY_TODAY_OTP}_$currentEmail", newTodayOtpCount)?.apply()
            saveActiveNumbers(updatedActive)

            newlyArrivedOtps.forEach { (prov, otp) ->
                val platformName = when (prov.gateway.uppercase()) {
                    "HADI" -> "Hadi SMS"
                    "LAMIX" -> "Lamix SMS"
                    else -> "Unix SMS"
                }
                val creditRate = if (prov.rate >= 0.05) prov.rate else getRateForCountry(prov.iso)
                OtpHistoryManager.recordOtp(
                    phoneNumber = prov.number,
                    otpCode = otp,
                    service = prov.range.ifEmpty { gatewayLabel },
                    platform = platformName,
                    rate = creditRate
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

        // Filter DLR records strictly for added countries and services (in-memory only, never saved to DB)
        val filteredDlr = filterDlrForAddedCountriesAndServices(rawCdrList)

        _state.update { current ->
            val mergedActive = current.activeNumbers.map { prov ->
                val matched = newlyArrivedOtps.firstOrNull { isPhoneNumberMatch(it.first.number, prov.number) }
                if (matched != null) {
                    prov.copy(status = "success", otpCode = matched.second, otpMessage = matched.first.otpMessage)
                } else prov
            }
            if (newlyArrivedOtps.isNotEmpty()) {
                saveActiveNumbers(mergedActive)
            }
            val mergedCdr = (filteredDlr + current.liveCdrRecords)
                .distinctBy { "${it.gateway}_${it.num}_${it.message}" }
                .sortedByDescending { it.timestamp }
                .take(80)
            current.copy(
                liveCdrRecords = mergedCdr,
                activeNumbers = mergedActive,
                todayOtpCount = newTodayOtpCount,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    /**
     * 1. Fast 1-Second CR Hadi API SMS Gateway Polling (/crapi/had/viewstats)
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
                val dataArr = root.optJSONArray("data") ?: root.optJSONArray("records") ?: JSONArray()

                for (i in 0 until dataArr.length()) {
                    val o = dataArr.optJSONObject(i) ?: continue
                    val rawNum = o.optString("num", o.optString("number", ""))
                    val rawMsg = o.optString("message", o.optString("content", o.optString("text", "")))
                    val rawCli = o.optString("cli", o.optString("sender", "Hadi"))
                    val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(rawNum)
                    val rec = UnixSmsCdrRecord(
                        dt = o.optString("dt", ""),
                        num = maskPhoneNumberMiddle4(rawNum),
                        cli = rawCli,
                        message = rawMsg,
                        payout = o.optString("payout", "0"),
                        countryName = countryInfo?.name ?: "Global",
                        countryFlag = countryInfo?.flag ?: "🌐",
                        countryCode = countryInfo?.code ?: "",
                        gateway = "HADI",
                        serviceName = detectServiceFromCliAndMessage(rawCli, rawMsg),
                        timestamp = System.currentTimeMillis()
                    )
                    rawCdrList.add(Pair(rec, rawNum))
                }
            } catch (_: Exception) {}
        }

        // Direct query for pending active numbers via filternum
        val activeNumbers = _state.value.activeNumbers
        for (prov in activeNumbers) {
            if (prov.otpCode.isNullOrEmpty()) {
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
                                val fCli = fo.optString("cli", fo.optString("sender", "Hadi"))
                                val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(fNum)
                                val rec = UnixSmsCdrRecord(
                                    dt = fo.optString("dt", ""),
                                    num = maskPhoneNumberMiddle4(fNum),
                                    cli = fCli,
                                    message = fMsg,
                                    payout = fo.optString("payout", "0"),
                                    countryName = countryInfo?.name ?: prov.country,
                                    countryFlag = countryInfo?.flag ?: "🌐",
                                    countryCode = countryInfo?.code ?: prov.iso.uppercase(),
                                    gateway = "HADI",
                                    serviceName = prov.range.ifEmpty { detectServiceFromCliAndMessage(fCli, fMsg) },
                                    timestamp = System.currentTimeMillis()
                                )
                                rawCdrList.add(Pair(rec, fNum))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        if (rawCdrList.isNotEmpty()) {
            processMatchedOtpsAndUpdateDlr(context, rawCdrList, "Hadi SMS")
        }
    }

    /**
     * 2. Fast 1-Second Lamix SMS Gateway Live Polling (/api/v1/messages)
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
        val rawCdrList = mutableListOf<Pair<UnixSmsCdrRecord, String>>()

        if (response != null && response.isSuccessful && body.isNotEmpty()) {
            try {
                val root = JSONObject(body)
                val recordsArr = root.optJSONArray("records") ?: root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until recordsArr.length()) {
                    val r = recordsArr.optJSONObject(i) ?: continue
                    val rawNum = r.optString("number", r.optString("num", ""))
                    val rawMsg = r.optString("content", r.optString("message", r.optString("text", "")))
                    val rawCli = r.optString("cli", r.optString("sender", r.optString("service", "Lamix")))
                    val dtStr = r.optString("created_at", r.optString("dt", r.optString("time", "")))
                    val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(rawNum)
                    val rec = UnixSmsCdrRecord(
                        dt = dtStr,
                        num = maskPhoneNumberMiddle4(rawNum),
                        cli = rawCli,
                        message = rawMsg,
                        payout = "0",
                        countryName = countryInfo?.name ?: "Global",
                        countryFlag = countryInfo?.flag ?: "🌐",
                        countryCode = countryInfo?.code ?: "",
                        gateway = "LAMIX",
                        serviceName = detectServiceFromCliAndMessage(rawCli, rawMsg),
                        timestamp = System.currentTimeMillis()
                    )
                    rawCdrList.add(Pair(rec, rawNum))
                }
            } catch (_: Exception) {}
        }

        // Also query pending active numbers directly on Lamix if not found in general stream
        val activeNumbers = _state.value.activeNumbers
        for (prov in activeNumbers) {
            if (prov.otpCode.isNullOrEmpty()) {
                val cleanDigits = prov.number.replace("[^0-9]".toRegex(), "")
                if (cleanDigits.length >= 7 && !rawCdrList.any { isPhoneNumberMatch(it.second, cleanDigits) }) {
                    try {
                        val numUrl = "https://panel.lamix.org/api/v1/messages?token=${URLEncoder.encode(token, "UTF-8")}&number=$cleanDigits"
                        val numReq = Request.Builder().url(numUrl).header("User-Agent", "Mozilla/5.0").get().build()
                        val numResp = httpClient.newCall(numReq).execute()
                        val numBody = numResp.body?.string() ?: ""
                        if (numResp.isSuccessful && numBody.isNotEmpty()) {
                            val numRoot = JSONObject(numBody)
                            val numRecords = numRoot.optJSONArray("records") ?: numRoot.optJSONArray("data") ?: JSONArray()
                            for (j in 0 until numRecords.length()) {
                                val r = numRecords.optJSONObject(j) ?: continue
                                val fNum = r.optString("number", cleanDigits)
                                val fMsg = r.optString("content", r.optString("message", ""))
                                val fCli = r.optString("cli", r.optString("sender", "Lamix"))
                                val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(fNum)
                                val rec = UnixSmsCdrRecord(
                                    dt = r.optString("created_at", r.optString("dt", "")),
                                    num = maskPhoneNumberMiddle4(fNum),
                                    cli = fCli,
                                    message = fMsg,
                                    payout = "0",
                                    countryName = countryInfo?.name ?: prov.country,
                                    countryFlag = countryInfo?.flag ?: "🌐",
                                    countryCode = countryInfo?.code ?: prov.iso.uppercase(),
                                    gateway = "LAMIX",
                                    serviceName = prov.range.ifEmpty { detectServiceFromCliAndMessage(fCli, fMsg) },
                                    timestamp = System.currentTimeMillis()
                                )
                                rawCdrList.add(Pair(rec, fNum))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        if (rawCdrList.isNotEmpty()) {
            processMatchedOtpsAndUpdateDlr(context, rawCdrList, "Lamix SMS")
        }
    }

    /**
     * 3. Unix SMS Gateway Live Polling (/v2/cdr)
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
            try {
                val root = JSONObject(body)
                if (root.optString("status") == "success") {
                    val dataArr = root.optJSONArray("data") ?: JSONArray()
                    val rawCdrList = mutableListOf<Pair<UnixSmsCdrRecord, String>>()

                    val maxCdrToParse = minOf(dataArr.length(), 100)
                    for (i in 0 until maxCdrToParse) {
                        val o = dataArr.optJSONObject(i) ?: continue
                        val rawNum = o.optString("num", "")
                        val rawCli = o.optString("cli", "Unix")
                        val rawMsg = o.optString("message", "")
                        val countryInfo = com.example.util.AllCountriesProvider.findByPhoneNumber(rawNum)
                        val rec = UnixSmsCdrRecord(
                            dt = o.optString("dt", ""),
                            num = maskPhoneNumberMiddle4(rawNum),
                            cli = rawCli,
                            message = rawMsg,
                            payout = o.optString("payout", "0"),
                            countryName = countryInfo?.name ?: "Global",
                            countryFlag = countryInfo?.flag ?: "🌐",
                            countryCode = countryInfo?.code ?: "",
                            gateway = "UNIX",
                            serviceName = detectServiceFromCliAndMessage(rawCli, rawMsg),
                            timestamp = System.currentTimeMillis()
                        )
                        rawCdrList.add(Pair(rec, rawNum))
                    }

                    if (rawCdrList.isNotEmpty()) {
                        processMatchedOtpsAndUpdateDlr(context, rawCdrList, "Unix SMS")
                    }
                }
            } catch (_: Exception) {}
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
        Handler(Looper.getMainLooper()).post(block)
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
