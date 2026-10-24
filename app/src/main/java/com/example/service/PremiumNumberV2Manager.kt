package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.data.model.ProvisionedNumber
import com.example.util.AllCountriesProvider
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class PremiumV2ConsoleItem(
    val id: String,
    val maskedNumber: String,
    val rangePrefix: String = "",
    val service: String,
    val country: String,
    val iso: String = "ZW",
    val flag: String = "🇿🇼",
    val otpCode: String = "******",
    val rawSnippet: String,
    val receivedAt: String = "",
    val timeAgo: String = "just now",
    val timestamp: Long = System.currentTimeMillis()
)

data class PremiumNumberV2UiState(
    val apiKey: String = PremiumNumberV2Manager.DEFAULT_API_KEY,
    val balanceUsd: Double = 0.0,
    val balanceUsername: String = "",
    val activeNumbers: List<ProvisionedNumber> = emptyList(),
    val liveConsoleFeed: List<PremiumV2ConsoleItem> = emptyList(),
    val todayOtpCount: Int = 0,
    val selectedCountryCode: String = "ZW",
    val selectedCountryName: String = "Zimbabwe",
    val selectedCountryFlag: String = "🇿🇼",
    val selectedCountryDial: String = "+263",
    val selectedService: String = "ANY (All Services)",
    val rangePrefix: String = "",
    val requestCount: Int = 1,
    val nationalFormat: Boolean = false,
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val isApiConnected: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val lastError: String? = null,
    val lastSyncTime: Long = 0L,
    val countryRates: Map<String, Double> = emptyMap(),
    // Live Stats matching Screenshot 2
    val servicesLive: Int = 3,
    val countriesLive: Int = 4,
    val throughputOtpPerMin: Int = 50,
    val totalLiveEvents: Int = 50
)

object PremiumNumberV2Manager {

    const val DEFAULT_API_KEY = "sl_live_17852e6050e9559278b5e8a69a2cc7429f9346f83f8cb667"
    const val AUTO_RETURN_TIMEOUT_MS = (14 * 60 + 40) * 1000L // 14 min 40 sec (880,000 ms)
    private const val BASE_URL = "https://slvirtualnumber.com/api/v1/developer"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var pollingJob: Job? = null
    private var tickerJob: Job? = null
    private var appContext: Context? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(PremiumNumberV2UiState())
    val state: StateFlow<PremiumNumberV2UiState> = _state.asStateFlow()

    private val _otpRate = MutableStateFlow(0.50)
    val otpRate: StateFlow<Double> = _otpRate.asStateFlow()

    private val otpRegex = Pattern.compile("\\b\\d{4,8}\\b")

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.getSharedPreferences("premium_number_v2_prefs", Context.MODE_PRIVATE)
            val savedKey = prefs?.getString("api_key", DEFAULT_API_KEY) ?: DEFAULT_API_KEY
            val effectiveKey = if (savedKey.isBlank()) DEFAULT_API_KEY else savedKey

            val savedRate = prefs?.getFloat("otp_rate", 0.50f)?.toDouble() ?: 0.50
            _otpRate.value = Math.round(savedRate * 100.0) / 100.0

            val savedCountryRates = loadCountryRates()

            val savedCountry = prefs?.getString("country_code", "IT") ?: "IT"
            val countryInfo = AllCountriesProvider.findByCode(savedCountry)
            val savedService = prefs?.getString("service", "WhatsApp") ?: "WhatsApp"
            val savedCount = prefs?.getInt("request_count", 1) ?: 1

            val todayKey = "today_otp_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val todayOtps = prefs?.getInt(todayKey, 0) ?: 0
            val savedSound = prefs?.getBoolean("sound_enabled", true) ?: true

            val savedNumbers = loadNumbers()

            _state.update {
                it.copy(
                    apiKey = effectiveKey,
                    selectedCountryCode = countryInfo?.code ?: savedCountry,
                    selectedCountryName = countryInfo?.name ?: "Italy",
                    selectedCountryFlag = countryInfo?.flag ?: "🇮🇹",
                    selectedCountryDial = countryInfo?.dialCode ?: "+39",
                    selectedService = savedService,
                    requestCount = savedCount,
                    todayOtpCount = todayOtps,
                    activeNumbers = savedNumbers,
                    isSoundEnabled = savedSound,
                    countryRates = savedCountryRates
                )
            }

            refreshBalance()
            refreshLiveConsole()
            startBackgroundPolling(context)
        }
    }

    fun setApiKey(newKey: String) {
        val trimmed = newKey.trim()
        val effective = if (trimmed.isBlank()) DEFAULT_API_KEY else trimmed
        prefs?.edit()?.putString("api_key", effective)?.apply()
        _state.update { it.copy(apiKey = effective) }
        refreshBalance()
    }

    fun setOtpRate(rate: Double) {
        val rounded = Math.round(rate * 100.0) / 100.0
        prefs?.edit()?.putFloat("otp_rate", rounded.toFloat())?.apply()
        _otpRate.value = rounded
        // Sync to cloud
        GlobalCloudSyncService.pushRateUpdate("premium_v2_rate", rounded)
    }

    fun applyCloudRate(rate: Double) {
        val rounded = Math.round(rate * 100.0) / 100.0
        prefs?.edit()?.putFloat("otp_rate", rounded.toFloat())?.apply()
        _otpRate.value = rounded
    }

    /**
     * Get configured rate for specific country, falling back to base rate
     */
    fun getRateForCountry(countryCode: String?): Double {
        if (countryCode.isNullOrBlank() || countryCode == "ALL" || countryCode == "ANY") {
            return _otpRate.value
        }
        val key = countryCode.trim().uppercase()
        return _state.value.countryRates[key] ?: _otpRate.value
    }

    /**
     * Admin Panel: Set country-specific rate permanently
     */
    fun setRateForCountry(countryCode: String?, rate: Double) {
        val rounded = Math.round(rate.coerceAtLeast(0.01) * 100.0) / 100.0
        if (countryCode.isNullOrBlank() || countryCode == "ALL" || countryCode == "ANY") {
            setOtpRate(rounded)
            return
        }
        val key = countryCode.trim().uppercase()
        val updatedMap = _state.value.countryRates.toMutableMap()
        updatedMap[key] = rounded
        saveCountryRates(updatedMap)
        _state.update { it.copy(countryRates = updatedMap) }
    }

    fun setSelectedCountry(code: String) {
        if (code.equals("ANY", ignoreCase = true) || code.equals("ALL", ignoreCase = true)) {
            prefs?.edit()?.putString("country_code", "ANY")?.apply()
            // Auto-update prefix from top feed item
            val topPfx = _state.value.liveConsoleFeed.firstOrNull { it.rangePrefix.isNotBlank() }?.rangePrefix ?: ""
            _state.update {
                it.copy(
                    selectedCountryCode = "ANY",
                    selectedCountryName = "ANY (All Countries)",
                    selectedCountryFlag = "🌐",
                    selectedCountryDial = "",
                    rangePrefix = if (topPfx.isNotBlank()) topPfx else it.rangePrefix
                )
            }
            return
        }
        val info = AllCountriesProvider.findByCode(code) ?: AllCountriesProvider.findByNameOrCode(code)
        if (info != null) {
            prefs?.edit()?.putString("country_code", info.code)?.apply()
            // Auto-update live prefix to match this country from live pool
            val matchingFeedPfx = _state.value.liveConsoleFeed
                .firstOrNull { it.iso.equals(info.code, ignoreCase = true) || it.country.equals(info.name, ignoreCase = true) }
                ?.rangePrefix
            val autoPfx = when {
                !matchingFeedPfx.isNullOrBlank() -> matchingFeedPfx
                info.dialCode.isNotBlank() -> info.dialCode.replace("+", "")
                else -> _state.value.rangePrefix
            }

            _state.update {
                it.copy(
                    selectedCountryCode = info.code,
                    selectedCountryName = info.name,
                    selectedCountryFlag = info.flag,
                    selectedCountryDial = info.dialCode,
                    rangePrefix = autoPfx
                )
            }
        }
    }

    fun setSelectedService(service: String) {
        prefs?.edit()?.putString("service", service)?.apply()
        _state.update { it.copy(selectedService = service) }
    }

    fun setRangePrefix(prefix: String) {
        _state.update { it.copy(rangePrefix = prefix.trim()) }
    }

    fun setRequestCount(count: Int) {
        val c = count.coerceIn(1, 10)
        prefs?.edit()?.putInt("request_count", c)?.apply()
        _state.update { it.copy(requestCount = c) }
    }

    fun setNationalFormat(enabled: Boolean) {
        prefs?.edit()?.putBoolean("national_format", enabled)?.apply()
        _state.update { it.copy(nationalFormat = enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("sound_enabled", enabled)?.apply()
        _state.update { it.copy(isSoundEnabled = enabled) }
    }

    fun purgeOlderThan24Hours() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val filteredNumbers = _state.value.activeNumbers.filter { it.timestamp >= cutoff }
        val filteredFeed = _state.value.liveConsoleFeed.filter { it.timestamp >= cutoff }
        if (filteredNumbers.size != _state.value.activeNumbers.size || filteredFeed.size != _state.value.liveConsoleFeed.size) {
            saveNumbers(filteredNumbers)
            _state.update { it.copy(activeNumbers = filteredNumbers, liveConsoleFeed = filteredFeed) }
        }
    }

    fun clearExpiredOrCompletedNumbers() {
        val now = System.currentTimeMillis()
        val remaining = _state.value.activeNumbers.filter {
            (it.status == "pending" || it.status == "wait") && it.otpCode.isNullOrEmpty() && now < it.expiresAt
        }
        saveNumbers(remaining)
        _state.update { it.copy(activeNumbers = remaining) }
    }

    /**
     * Acquire virtual numbers (1 to 10) from https://slvirtualnumber.com/api/v1/developer/rent
     */
    fun acquireNumbers(context: Context) {
        val currentState = _state.value
        val count = currentState.requestCount.coerceIn(1, 10)
        val apiKey = currentState.apiKey
        val countryCode = currentState.selectedCountryCode
        val countryName = currentState.selectedCountryName
        val flag = currentState.selectedCountryFlag
        val service = currentState.selectedService
        val rangePrefix = currentState.rangePrefix
        val activeRate = getRateForCountry(countryCode)

        _state.update { it.copy(isLoading = true, lastError = null) }

        scope.launch {
            var successCount = 0
            var lastErrorMsg: String? = null
            val newNumbers = mutableListOf<ProvisionedNumber>()

            for (i in 0 until count) {
                try {
                    val jsonPayload = JSONObject().apply {
                        if (rangePrefix.isNotBlank()) {
                            put("rangePrefix", rangePrefix)
                        }
                        if (service.isNotBlank() && !service.startsWith("ANY", ignoreCase = true)) {
                            put("service", service)
                        }
                        if (countryCode.isNotBlank() && countryCode != "ANY" && countryCode != "ALL") {
                            put("countryCode", countryCode)
                        }
                    }

                    val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url("$BASE_URL/rent")
                        .addHeader("x-api-key", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val json = JSONObject(respBody)
                            if (json.optString("status") == "success") {
                                val dataObj = json.optJSONObject("data")
                                if (dataObj != null) {
                                    val rentalId = dataObj.optString("rentalId", "")
                                    val phoneNumber = dataObj.optString("phoneNumber", "")
                                    val rentService = dataObj.optString("service", service)
                                    // Auto-detect country and flag directly from phone number digits
                                    val autoCountry = com.example.util.AllCountriesProvider.findByPhoneNumber(phoneNumber)
                                    val rentCountry = autoCountry?.name ?: dataObj.optString("country", "Live Range")
                                    val rentCountryCode = autoCountry?.code ?: dataObj.optString("countryCode", "LIVE")
                                    val rentFlag = autoCountry?.flag ?: "🌐"

                                    // 14-Minute 80-Second Auto-Return Limit (920,000 ms)
                                    val now = System.currentTimeMillis()
                                    val expiresAt = now + AUTO_RETURN_TIMEOUT_MS

                                    if (phoneNumber.isNotBlank()) {
                                        val item = ProvisionedNumber(
                                            id = if (rentalId.isNotBlank()) rentalId else java.util.UUID.randomUUID().toString(),
                                            number = phoneNumber,
                                            country = rentCountry,
                                            operator = "Live Range",
                                            iso = rentCountryCode.lowercase(),
                                            status = "pending",
                                            otpCode = null,
                                            otpMessage = null,
                                            range = rentService,
                                            timestamp = now,
                                            expiresAt = expiresAt,
                                            gateway = "PREMIUM_V2",
                                            rate = activeRate,
                                            rentalId = rentalId,
                                            flag = rentFlag
                                        )
                                        newNumbers.add(item)
                                        successCount++
                                    }
                                }
                            } else {
                                lastErrorMsg = json.optString("message", "Request unsuccessful")
                            }
                        } else {
                            try {
                                val errJson = JSONObject(respBody)
                                lastErrorMsg = errJson.optString("message", "HTTP ${response.code}")
                            } catch (_: Exception) {
                                lastErrorMsg = "HTTP ${response.code}: ${response.message}"
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastErrorMsg = e.message ?: "Network error"
                }
            }

            if (newNumbers.isNotEmpty()) {
                // Ensure newly acquired numbers strictly stay at the top
                val updated = (newNumbers + _state.value.activeNumbers).sortedByDescending { it.timestamp }
                saveNumbers(updated)
                _state.update {
                    it.copy(
                        activeNumbers = updated,
                        isLoading = false,
                        lastError = null,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }

                // Auto copy all acquired numbers to clipboard together (newline separated, stripped of '+')
                val allNumbersText = newNumbers.joinToString("\n") { it.number.replace("+", "").trim() }
                mainHandler.post {
                    com.example.util.ClipboardHelper.copyToClipboard(context, allNumbersText, "Premium V2 Numbers")
                    com.example.util.VibrationHelper.vibrateSuccess(context)
                    val copyMsg = if (newNumbers.size > 1) "✓ ${newNumbers.size} টি নম্বর এক সাথে অটো কপি হয়েছে!" else "✓ নম্বর ${newNumbers.first().number} অটো কপি হয়েছে!"
                    Toast.makeText(context, "$copyMsg (১৪ মিনিট ৪০ সেকেন্ড মেয়াদ)", Toast.LENGTH_SHORT).show()
                }
                startBackgroundPolling(context)
                refreshBalance()
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        lastError = lastErrorMsg ?: "নম্বর পাওয়া যায়নি"
                    )
                }
                mainHandler.post {
                    Toast.makeText(context, "নম্বর সংগ্রহ ব্যর্থ: ${lastErrorMsg ?: "চেক এপিআই কি বা ব্যালেন্স"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Cancel / Return active rental back to the pool
     */
    fun cancelRental(rentalId: String, context: Context?, isAutoReturn: Boolean = false) {
        if (rentalId.isBlank()) return
        val apiKey = _state.value.apiKey

        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/cancel/$rentalId")
                    .addHeader("x-api-key", apiKey)
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    // Success or already retired
                }
            } catch (_: Exception) {}

            // If auto-return, delete/remove the number from the list completely
            val updated = if (isAutoReturn) {
                _state.value.activeNumbers.filter { it.rentalId != rentalId && it.id != rentalId }
            } else {
                _state.value.activeNumbers.map {
                    if (it.rentalId == rentalId || it.id == rentalId) {
                        it.copy(status = "cancelled", failReason = "User cancelled")
                    } else it
                }
            }
            saveNumbers(updated)
            _state.update { it.copy(activeNumbers = updated) }

            if (context != null) {
                mainHandler.post {
                    val msg = if (isAutoReturn) "নম্বর ১৪ মিনিট ৪০ সেকেন্ড পার হওয়ায় অটো-রিটার্ন ও লিস্ট থেকে সরানো হয়েছে" else "✓ নম্বর সফলভাবে ফেরত দেওয়া হয়েছে"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Fetch Live Account Balance in USD ($)
     */
    fun refreshBalance() {
        val apiKey = _state.value.apiKey
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/balance")
                    .addHeader("x-api-key", apiKey)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.optString("status") == "success") {
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                val balStr = data.optString("balanceUsd", "0.0")
                                val balVal = balStr.toDoubleOrNull() ?: 0.0
                                val user = data.optString("username", "")
                                _state.update {
                                    it.copy(
                                        balanceUsd = balVal,
                                        balanceUsername = user
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Direct one-tap rent from Live Console stream item (Screenshot 2 feature)
     */
    fun rentDirectFromConsoleItem(item: PremiumV2ConsoleItem, context: Context) {
        val apiKey = _state.value.apiKey
        val activeRate = getRateForCountry(item.iso)
        _state.update { it.copy(isLoading = true, lastError = null) }

        scope.launch {
            try {
                val jsonPayload = JSONObject().apply {
                    if (item.service.isNotBlank() && !item.service.startsWith("ANY", ignoreCase = true)) {
                        put("service", item.service)
                    }
                    if (item.iso.isNotBlank() && item.iso != "ANY" && item.iso != "ALL") {
                        put("countryCode", item.iso)
                    }
                    if (item.rangePrefix.isNotBlank()) {
                        put("rangePrefix", item.rangePrefix)
                    }
                }

                val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$BASE_URL/rent")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(respBody)
                        if (json.optString("status") == "success") {
                            val dataObj = json.optJSONObject("data")
                            if (dataObj != null) {
                                val rentalId = dataObj.optString("rentalId", "")
                                val phoneNumber = dataObj.optString("phoneNumber", "")
                                val rentService = dataObj.optString("service", item.service)
                                val autoCountry = com.example.util.AllCountriesProvider.findByPhoneNumber(phoneNumber)
                                val rentCountry = autoCountry?.name ?: item.country
                                val rentCountryCode = autoCountry?.code ?: item.iso
                                val rentFlag = autoCountry?.flag ?: item.flag
                                val now = System.currentTimeMillis()
                                val expiresAt = now + AUTO_RETURN_TIMEOUT_MS

                                if (phoneNumber.isNotBlank()) {
                                    val prov = ProvisionedNumber(
                                        id = if (rentalId.isNotBlank()) rentalId else java.util.UUID.randomUUID().toString(),
                                        number = phoneNumber,
                                        country = rentCountry,
                                        operator = "Live Console",
                                        iso = rentCountryCode.lowercase(),
                                        status = "pending",
                                        otpCode = null,
                                        otpMessage = null,
                                        range = rentService,
                                        timestamp = now,
                                        expiresAt = expiresAt,
                                        gateway = "PREMIUM_V2",
                                        rate = activeRate,
                                        rentalId = rentalId,
                                        flag = rentFlag
                                    )
                                    val updated = (listOf(prov) + _state.value.activeNumbers).sortedByDescending { it.timestamp }
                                    saveNumbers(updated)
                                    _state.update { it.copy(activeNumbers = updated, isLoading = false) }

                                    mainHandler.post {
                                        ClipboardHelper.copyToClipboard(context, phoneNumber, "Allocated Number")
                                        Toast.makeText(context, "✓ $phoneNumber নম্বর নেওয়া হয়েছে ও কপি করা হয়েছে!", Toast.LENGTH_LONG).show()
                                        startBackgroundPolling(context)
                                    }
                                    return@launch
                                }
                            }
                        }
                    }
                    mainHandler.post {
                        _state.update { it.copy(isLoading = false) }
                        Toast.makeText(context, "নম্বর নিতে সমস্যা হয়েছে, পুনরায় চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    _state.update { it.copy(isLoading = false, lastError = e.message) }
                    Toast.makeText(context, "ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun formatMaskedNumber(raw: String, itemId: String = "", rangePrefix: String = ""): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "+2637••••0889"

        // If already formatted like +2519••••8156 with 4 trailing digits
        val exactMatch = Pattern.compile("^\\+[0-9]{3,5}••••[0-9]{4}$").matcher(trimmed)
        if (exactMatch.matches()) return trimmed

        val rawDigits = trimmed.replace("[^0-9]".toRegex(), "")

        // 1. Prefix: e.g. 2519, 3932, 3365, 2637, 2491
        val prefixPart = if (rangePrefix.isNotBlank()) {
            val pfxClean = rangePrefix.replace("[^0-9]".toRegex(), "")
            if (pfxClean.length >= 4) pfxClean.take(4) else pfxClean
        } else if (rawDigits.length >= 4) {
            rawDigits.take(4)
        } else "2637"

        // 2. 4-digit suffix
        val suffixPart = if (rawDigits.length >= 8 && !trimmed.contains("X", ignoreCase = true) && !trimmed.contains("*")) {
            rawDigits.takeLast(4)
        } else {
            val seed = if (itemId.isNotBlank()) Math.abs(itemId.hashCode()) else Math.abs(trimmed.hashCode())
            String.format(Locale.US, "%04d", (seed % 9000) + 1000)
        }

        return "+$prefixPart••••$suffixPart"
    }

    /**
     * Fetch Live Console Feed (Carrier global stream - Hubu same as Screenshot 1 on slvirtualnumber.com)
     */
    fun refreshLiveConsole(limit: Int = 50) {
        val apiKey = _state.value.apiKey
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/live-console?limit=$limit")
                    .addHeader("x-api-key", apiKey)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.optString("status") == "success") {
                            val dataArr = json.optJSONArray("data")
                            if (dataArr != null && dataArr.length() > 0) {
                                val items = mutableListOf<PremiumV2ConsoleItem>()
                                for (i in 0 until dataArr.length()) {
                                    val obj = dataArr.optJSONObject(i) ?: continue
                                    val idStr = obj.optString("id", java.util.UUID.randomUUID().toString())
                                    val rawNum = obj.optString("maskedNumber", obj.optString("phoneNumber", ""))

                                    var itemPrefix = obj.optString("rangePrefix", "")
                                    if (itemPrefix.isBlank()) {
                                        val digits = rawNum.replace("[^0-9]".toRegex(), "")
                                        itemPrefix = if (digits.length >= 4) digits.take(4) else digits
                                    } else if (itemPrefix.length > 4) {
                                        itemPrefix = itemPrefix.take(4)
                                    }

                                    val formattedNum = formatMaskedNumber(raw = rawNum, itemId = idStr, rangePrefix = itemPrefix)
                                    val autoC = com.example.util.AllCountriesProvider.findByPhoneNumber(rawNum)
                                        ?: com.example.util.AllCountriesProvider.findByNameOrCode(obj.optString("country", ""))
                                    val cName = autoC?.name ?: obj.optString("country", "Zimbabwe")
                                    val cIso = autoC?.code ?: obj.optString("iso", "ZW")
                                    val cFlag = autoC?.flag ?: obj.optString("flag", "🇿🇼")
                                    val srv = obj.optString("service", "FACEBOOK").uppercase()

                                    val fullMsg = obj.optString("rawSnippet", obj.optString("message", "<#> ****** is your $srv code")).trim()

                                    items.add(
                                        PremiumV2ConsoleItem(
                                            id = idStr,
                                            maskedNumber = formattedNum,
                                            rangePrefix = itemPrefix,
                                            service = srv,
                                            country = cName,
                                            iso = cIso,
                                            flag = cFlag,
                                            otpCode = obj.optString("otpCode", "******"),
                                            rawSnippet = fullMsg,
                                            receivedAt = obj.optString("receivedAt", ""),
                                            timeAgo = obj.optString("timeAgo", if (i == 0) "just now" else "${i * 2} mins ago")
                                        )
                                    )
                                }
                                if (items.isNotEmpty()) {
                                    // Auto-update range prefix if current prefix is blank
                                    val currentPfx = _state.value.rangePrefix
                                    val autoPfx = if (currentPfx.isBlank()) {
                                        val match = items.firstOrNull { it.iso.equals(_state.value.selectedCountryCode, ignoreCase = true) }
                                            ?: items.firstOrNull()
                                        match?.rangePrefix ?: ""
                                    } else currentPfx

                                    _state.update {
                                        it.copy(
                                            liveConsoleFeed = items,
                                            rangePrefix = autoPfx,
                                            isApiConnected = true
                                        )
                                    }
                                    return@launch
                                }
                            }
                        }
                    }
                    _state.update { it.copy(isApiConnected = response.isSuccessful) }
                }
            } catch (_: Exception) {
                _state.update { it.copy(isApiConnected = false) }
            }

            // Fallback default stream items matching Screenshot 1/2 if network feed is unavailable
            if (_state.value.liveConsoleFeed.isEmpty()) {
                val defaults = getDefaultLiveConsoleFeed()
                val defPfx = if (_state.value.rangePrefix.isBlank()) defaults.firstOrNull()?.rangePrefix ?: "" else _state.value.rangePrefix
                _state.update { it.copy(liveConsoleFeed = defaults, rangePrefix = defPfx) }
            }
        }
    }

    fun getDefaultLiveConsoleFeed(): List<PremiumV2ConsoleItem> = listOf(
        PremiumV2ConsoleItem(
            id = "live-1",
            maskedNumber = "+2519••••8156",
            rangePrefix = "2519",
            service = "FACEBOOK",
            country = "Ethiopia",
            iso = "ET",
            flag = "🇪🇹",
            otpCode = "H29Q+Fsn4Sr",
            rawSnippet = "<#> ****** is your Facebook code\nH29Q+Fsn4Sr",
            timeAgo = "just now"
        ),
        PremiumV2ConsoleItem(
            id = "live-2",
            maskedNumber = "+3932••••1114",
            rangePrefix = "3932",
            service = "FACEBOOK",
            country = "Italy",
            iso = "IT",
            flag = "🇮🇹",
            otpCode = "H29Q+Fsn4Sr",
            rawSnippet = "<#> ****** is your Facebook code\nH29Q+Fsn4Sr",
            timeAgo = "just now"
        ),
        PremiumV2ConsoleItem(
            id = "live-3",
            maskedNumber = "+2519••••8156",
            rangePrefix = "2519",
            service = "FACEBOOK",
            country = "Ethiopia",
            iso = "ET",
            flag = "🇪🇹",
            otpCode = "H29Q+Fsn4Sr",
            rawSnippet = "<#> ****** is your Facebook code\nH29Q+Fsn4Sr",
            timeAgo = "1 min ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-4",
            maskedNumber = "+3932••••1114",
            rangePrefix = "3932",
            service = "FACEBOOK",
            country = "Italy",
            iso = "IT",
            flag = "🇮🇹",
            otpCode = "H29Q+Fsn4Sr",
            rawSnippet = "<#> ****** is your Facebook code\nH29Q+Fsn4Sr",
            timeAgo = "2 mins ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-5",
            maskedNumber = "+3365••••3422",
            rangePrefix = "3365",
            service = "FACEBOOK",
            country = "France",
            iso = "FR",
            flag = "🇫🇷",
            otpCode = "LaznxC",
            rawSnippet = "<#> ****** is your Facebook code\nLaz+nxCarLW",
            timeAgo = "4 mins ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-6",
            maskedNumber = "+9617••••1890",
            rangePrefix = "9617",
            service = "FACEBOOK",
            country = "Lebanon",
            iso = "LB",
            flag = "🇱🇧",
            otpCode = "941028",
            rawSnippet = "هو الرمز الخاص بك على Facebook: ******",
            timeAgo = "6 mins ago"
        )
    )

    /**
     * Start Background High-Speed Polling and 15-Minute Expiry Ticker
     */
    fun startBackgroundPolling(context: Context) {
        appContext = context.applicationContext
        if (pollingJob?.isActive == true) return
        _state.update { it.copy(isPolling = true) }

        pollingJob = scope.launch {
            var counter = 0
            while (isActive) {
                counter++
                val currentList = _state.value.activeNumbers
                val pendingList = currentList.filter {
                    (it.status == "pending" || it.status == "wait") && it.otpCode.isNullOrEmpty()
                }

                // 1. Check 15-minute auto-expiry on all pending numbers
                val now = System.currentTimeMillis()
                val expiredNumbers = pendingList.filter { now >= it.expiresAt }
                for (exp in expiredNumbers) {
                    val rId = exp.rentalId ?: exp.id
                    cancelRental(rId, appContext, isAutoReturn = true)
                }

                // 2. If there are pending numbers, poll high-speed bulk OTP monitor
                if (pendingList.isNotEmpty()) {
                    pollBulkOtpMonitor(appContext)
                }

                // 3. Continuously refresh live pool console from API (every 3 seconds)
                if (counter % 3 == 0) {
                    refreshLiveConsole()
                }
                if (counter % 10 == 0) {
                    refreshBalance()
                }
                // 4. Auto-purge records older than 24 hours every 30 seconds
                if (counter % 30 == 0) {
                    purgeOlderThan24Hours()
                }

                delay(1000L) // Instant 1-second OTP poll rate
            }
        }
    }

    private suspend fun pollBulkOtpMonitor(ctx: Context?) {
        val apiKey = _state.value.apiKey
        try {
            val request = Request.Builder()
                .url("$BASE_URL/otp-monitor?limit=100")
                .addHeader("x-api-key", apiKey)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        val dataArr = json.optJSONArray("data")
                        if (dataArr != null && dataArr.length() > 0) {
                            processIncomingOtps(dataArr, ctx)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun processIncomingOtps(dataArr: JSONArray, ctx: Context?) {
        val current = _state.value.activeNumbers.toMutableList()
        var updated = false
        val newlyArrived = mutableListOf<Pair<ProvisionedNumber, String>>()

        for (i in 0 until dataArr.length()) {
            val obj = dataArr.optJSONObject(i) ?: continue
            val rId = obj.optString("rentalId", "")
            val phone = obj.optString("phoneNumber", "").replace("[^0-9+]".toRegex(), "")
            val otp = obj.optString("otpCode", "")
            val fullSms = obj.optString("fullSms", "")
            val srv = obj.optString("service", "SMS")

            if (otp.isBlank()) continue

            for (idx in current.indices) {
                val item = current[idx]
                val cleanItemPhone = item.number.replace("[^0-9+]".toRegex(), "")
                val isMatch = (rId.isNotBlank() && item.rentalId == rId) ||
                        (cleanItemPhone.isNotEmpty() && (cleanItemPhone == phone || cleanItemPhone.endsWith(phone) || phone.endsWith(cleanItemPhone)))

                if (isMatch && item.otpCode.isNullOrEmpty()) {
                    val updatedItem = item.copy(
                        status = "success",
                        otpCode = otp,
                        otpMessage = fullSms
                    )
                    current[idx] = updatedItem
                    updated = true
                    newlyArrived.add(Pair(updatedItem, otp))
                }
            }
        }

        if (updated) {
            saveNumbers(current)

            // Increment today's count
            val todayKey = "today_otp_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val newTodayCount = _state.value.todayOtpCount + newlyArrived.size
            prefs?.edit()?.putInt(todayKey, newTodayCount)?.apply()

            _state.update {
                it.copy(
                    activeNumbers = current,
                    todayOtpCount = newTodayCount,
                    lastSyncTime = System.currentTimeMillis()
                )
            }

            // Record to permanent OTP History & update balance
            for ((item, otp) in newlyArrived) {
                OtpHistoryManager.recordOtp(
                    phoneNumber = item.number,
                    otpCode = otp,
                    service = item.range.ifEmpty { "WhatsApp" },
                    platform = "Premium Number V2",
                    rate = item.rate
                )
            }
            WithdrawalManager.refreshBalances()

            // Trigger notification, sound/vibration, and auto-copy to clipboard
            if (ctx != null) {
                mainHandler.post {
                    if (_state.value.isSoundEnabled) {
                        playOtpAlertSound(ctx)
                    }
                    for ((item, otp) in newlyArrived) {
                        ClipboardHelper.copyToClipboard(ctx, otp, "OTP")
                        OtpNotificationHelper.showOtpNotification(
                            context = ctx,
                            phoneNumber = item.number,
                            otpCode = otp,
                            fullMessage = item.otpMessage ?: "OTP Code: $otp"
                        )
                        VibrationHelper.vibrateSuccess(ctx)
                        Toast.makeText(ctx, "⚡ ওটিপি এসেছে ও কী-বোর্ডে কপি হয়েছে: $otp", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun playOtpAlertSound(context: Context) {
        try {
            val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = android.media.RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (_: Exception) {
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 350)
            } catch (_: Exception) {}
        }
    }

    fun clearAllNumbers() {
        saveNumbers(emptyList())
        _state.update { it.copy(activeNumbers = emptyList()) }
    }

    fun removeNumber(id: String) {
        val updated = _state.value.activeNumbers.filter { it.id != id }
        saveNumbers(updated)
        _state.update { it.copy(activeNumbers = updated) }
    }

    private fun saveNumbers(list: List<ProvisionedNumber>) {
        try {
            val arr = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("number", item.number)
                    put("country", item.country)
                    put("operator", item.operator)
                    put("iso", item.iso)
                    put("status", item.status)
                    put("otpCode", item.otpCode ?: "")
                    put("otpMessage", item.otpMessage ?: "")
                    put("range", item.range)
                    put("timestamp", item.timestamp)
                    put("expiresAt", item.expiresAt)
                    put("gateway", item.gateway)
                    put("rate", item.rate)
                    put("rentalId", item.rentalId ?: "")
                    put("flag", item.flag ?: "")
                }
                arr.put(obj)
            }
            prefs?.edit()?.putString("active_numbers_json", arr.toString())?.apply()
        } catch (_: Exception) {}
    }

    private fun loadNumbers(): List<ProvisionedNumber> {
        val json = prefs?.getString("active_numbers_json", "[]") ?: "[]"
        val list = mutableListOf<ProvisionedNumber>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    ProvisionedNumber(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        number = obj.optString("number", ""),
                        country = obj.optString("country", "Italy"),
                        operator = obj.optString("operator", "Carrier"),
                        iso = obj.optString("iso", "it"),
                        status = obj.optString("status", "pending"),
                        otpCode = obj.optString("otpCode", "").ifEmpty { null },
                        otpMessage = obj.optString("otpMessage", "").ifEmpty { null },
                        range = obj.optString("range", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        expiresAt = obj.optLong("expiresAt", System.currentTimeMillis() + AUTO_RETURN_TIMEOUT_MS),
                        gateway = obj.optString("gateway", "PREMIUM_V2"),
                        rate = obj.optDouble("rate", 0.50),
                        rentalId = obj.optString("rentalId", "").ifEmpty { null },
                        flag = obj.optString("flag", "").ifEmpty { null }
                    )
                )
            }
        } catch (_: Exception) {}
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return list.filter { it.timestamp >= cutoff }.sortedByDescending { it.timestamp }
    }

    private fun saveCountryRates(rates: Map<String, Double>) {
        try {
            val obj = JSONObject()
            rates.forEach { (k, v) -> obj.put(k, v) }
            prefs?.edit()?.putString("v2_country_rates", obj.toString())?.apply()
        } catch (_: Exception) {}
    }

    private fun loadCountryRates(): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        try {
            val json = prefs?.getString("v2_country_rates", "{}") ?: "{}"
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.optDouble(k, 0.50)
            }
        } catch (_: Exception) {}
        return result
    }
}
