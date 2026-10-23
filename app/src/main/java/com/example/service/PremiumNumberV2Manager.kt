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
                    isSoundEnabled = savedSound
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

    fun setSelectedCountry(code: String) {
        if (code.equals("ANY", ignoreCase = true) || code.equals("ALL", ignoreCase = true)) {
            prefs?.edit()?.putString("country_code", "ANY")?.apply()
            _state.update {
                it.copy(
                    selectedCountryCode = "ANY",
                    selectedCountryName = "ANY (All Countries)",
                    selectedCountryFlag = "🌐",
                    selectedCountryDial = ""
                )
            }
            return
        }
        val info = AllCountriesProvider.findByCode(code)
        if (info != null) {
            prefs?.edit()?.putString("country_code", info.code)?.apply()
            _state.update {
                it.copy(
                    selectedCountryCode = info.code,
                    selectedCountryName = info.name,
                    selectedCountryFlag = info.flag,
                    selectedCountryDial = info.dialCode
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
        val activeRate = _otpRate.value

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
                mainHandler.post {
                    Toast.makeText(context, "✓ $successCount টি নম্বর সফলভাবে সংগ্রহ হয়েছে! (১৪ মিনিট ৪০ সেকেন্ড মেয়াদ)", Toast.LENGTH_SHORT).show()
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
        val activeRate = _otpRate.value
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

    fun formatMaskedNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "+2637••••0889"
        if (trimmed.contains("••••") || trimmed.contains("****")) return trimmed
        val digits = trimmed.replace("[^0-9+]".toRegex(), "")
        if (digits.length <= 7) return digits
        val prefixLen = if (digits.startsWith("+")) 5 else 4
        val suffixLen = 4
        return if (digits.length > prefixLen + suffixLen) {
            val start = digits.take(prefixLen)
            val end = digits.takeLast(suffixLen)
            "$start••••$end"
        } else {
            val start = digits.take(digits.length / 2)
            val end = digits.takeLast(2)
            "$start••••$end"
        }
    }

    /**
     * Fetch Live Console Feed (Carrier global stream - Hubu same as slvirtualnumber.com)
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
                                    val rawNum = obj.optString("maskedNumber", obj.optString("phoneNumber", ""))
                                    val formattedNum = formatMaskedNumber(if (rawNum.isNotBlank()) rawNum else "+26371145${1000 + i}")
                                    val autoC = com.example.util.AllCountriesProvider.findByPhoneNumber(rawNum)
                                        ?: com.example.util.AllCountriesProvider.findByNameOrCode(obj.optString("country", ""))
                                    val cName = autoC?.name ?: obj.optString("country", "Zimbabwe")
                                    val cIso = autoC?.code ?: obj.optString("iso", "ZW")
                                    val cFlag = autoC?.flag ?: obj.optString("flag", "🇿🇼")
                                    val srv = obj.optString("service", "FACEBOOK").uppercase()

                                    items.add(
                                        PremiumV2ConsoleItem(
                                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                            maskedNumber = formattedNum,
                                            rangePrefix = obj.optString("rangePrefix", ""),
                                            service = srv,
                                            country = cName,
                                            iso = cIso,
                                            flag = cFlag,
                                            otpCode = obj.optString("otpCode", "******"),
                                            rawSnippet = obj.optString("rawSnippet", obj.optString("message", "<#> ****** is your $srv code")),
                                            receivedAt = obj.optString("receivedAt", ""),
                                            timeAgo = obj.optString("timeAgo", if (i == 0) "just now" else "${i * 2} mins ago")
                                        )
                                    )
                                }
                                if (items.isNotEmpty()) {
                                    _state.update { it.copy(liveConsoleFeed = items, isApiConnected = true) }
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

            // Fallback default stream items matching Screenshot 2 if network feed is unavailable
            if (_state.value.liveConsoleFeed.isEmpty()) {
                _state.update { it.copy(liveConsoleFeed = getDefaultLiveConsoleFeed()) }
            }
        }
    }

    fun getDefaultLiveConsoleFeed(): List<PremiumV2ConsoleItem> = listOf(
        PremiumV2ConsoleItem(
            id = "live-1",
            maskedNumber = "+2637••••0889",
            rangePrefix = "263",
            service = "FACEBOOK",
            country = "Zimbabwe",
            iso = "ZW",
            flag = "🇿🇼",
            otpCode = "H29Q+Fsn4Sr",
            rawSnippet = "<#> ****** is your Facebook code H29Q+Fsn4Sr",
            timeAgo = "just now"
        ),
        PremiumV2ConsoleItem(
            id = "live-2",
            maskedNumber = "+2491••••4505",
            rangePrefix = "249",
            service = "APPLE",
            country = "Sudan",
            iso = "SD",
            flag = "🇸🇩",
            otpCode = "026AD8",
            rawSnippet = "REG-RESP?v=5;r=******;n=+249114505702;s=026AD8A5ECFFFFFFFF27519349BDD645354BC0D99536C1EC7E2E091C50",
            timeAgo = "2 mins ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-3",
            maskedNumber = "+3365••••3422",
            rangePrefix = "33",
            service = "FACEBOOK",
            country = "France",
            iso = "FR",
            flag = "🇫🇷",
            otpCode = "GdDGcw",
            rawSnippet = "# ****** is your Instagram code Dont share it GdDGcwrWHVm",
            timeAgo = "5 mins ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-4",
            maskedNumber = "+3365••••3422",
            rangePrefix = "33",
            service = "FACEBOOK",
            country = "France",
            iso = "FR",
            flag = "🇫🇷",
            otpCode = "LaznxC",
            rawSnippet = "# ****** is your Facebook code LaznxCarLW",
            timeAgo = "8 mins ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-5",
            maskedNumber = "+3365••••3422",
            rangePrefix = "33",
            service = "FACEBOOK",
            country = "France",
            iso = "FR",
            flag = "🇫🇷",
            otpCode = "GdDGcw",
            rawSnippet = "# ****** is your Instagram code Dont share it GdDGcwrWHVm",
            timeAgo = "10 mins ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-6",
            maskedNumber = "+2637••••0883",
            rangePrefix = "263",
            service = "FACEBOOK",
            country = "Zimbabwe",
            iso = "ZW",
            flag = "🇿🇼",
            otpCode = "7yKqw1",
            rawSnippet = "<#> ****** is your Facebook code 7yKqw193Pq",
            timeAgo = "10 mins ago"
        ),
        PremiumV2ConsoleItem(
            id = "live-7",
            maskedNumber = "+2609••••1120",
            rangePrefix = "260",
            service = "WHATSAPP",
            country = "Zambia",
            iso = "ZM",
            flag = "🇿🇲",
            otpCode = "******",
            rawSnippet = "Your WhatsApp code: ******",
            timeAgo = "12 mins ago"
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
        return list.sortedByDescending { it.timestamp }
    }
}
