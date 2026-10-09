package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.data.model.ActiveRangeItem
import com.example.data.model.BroadcastFeedItem
import com.example.data.model.ProvisionedNumber
import com.example.util.ClipboardHelper
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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

val defaultActiveRangesList = listOf(
    ActiveRangeItem(range = "237627XXX", service = "Facebook", tag = "PC Clone", hits = 17),
    ActiveRangeItem(range = "324685XXX", service = "Facebook", tag = "New Fb", hits = 6),
    ActiveRangeItem(range = "324685XXX", service = "Facebook", tag = "PC Clone", hits = 5),
    ActiveRangeItem(range = "237622XXX", service = "Facebook", tag = "Mobile", hits = 1),
    ActiveRangeItem(range = "228984XXX", service = "Instagram", tag = "Hot", hits = 8),
    ActiveRangeItem(range = "234802XXX", service = "WhatsApp", tag = "Direct", hits = 12)
)

val defaultBroadcastList = listOf(
    BroadcastFeedItem(
        number = "237627834XXX",
        range = "237627XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Your code is ******",
        time = System.currentTimeMillis() - 2_000
    ),
    BroadcastFeedItem(
        number = "237627874XXX",
        range = "237627XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Your code is ******",
        time = System.currentTimeMillis() - 5_000
    ),
    BroadcastFeedItem(
        number = "32468571XXXX",
        range = "324685XXX",
        service = "FACEBOOK",
        country = "BELGIUM",
        operator = "Base",
        otp = "<#> ****** is your Facebook code H29Q+Fsn4Sr",
        time = System.currentTimeMillis() - 7_000
    ),
    BroadcastFeedItem(
        number = "32468523XXXX",
        range = "324685XXX",
        service = "FACEBOOK",
        country = "BELGIUM",
        operator = "Base",
        otp = "<#> ****** est le code de r initialisation de votre mot de passe Facebook",
        time = System.currentTimeMillis() - 7_000
    ),
    BroadcastFeedItem(
        number = "32468597XXXX",
        range = "324685XXX",
        service = "FACEBOOK",
        country = "BELGIUM",
        operator = "Base",
        otp = "<#> ***** is your Facebook code H29Q+Fsn4Sr",
        time = System.currentTimeMillis() - 8_000
    ),
    BroadcastFeedItem(
        number = "237627650XXX",
        range = "237627XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Your code is ******",
        time = System.currentTimeMillis() - 10_000
    ),
    BroadcastFeedItem(
        number = "32468577XXXX",
        range = "324685XXX",
        service = "FACEBOOK",
        country = "BELGIUM",
        operator = "Base",
        otp = "<#> ***** is your Facebook code H29Q+Fsn4Sr",
        time = System.currentTimeMillis() - 10_000
    ),
    BroadcastFeedItem(
        number = "261363603XXX",
        range = "261363XXX",
        service = "FACEBOOK",
        country = "MADAGASCAR",
        operator = "Mobile",
        otp = "<#> ***** est votre code Facebook H29Q+Fsn4Sr",
        time = System.currentTimeMillis() - 13_000
    ),
    BroadcastFeedItem(
        number = "237627812XXX",
        range = "237627XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Your code is ******",
        time = System.currentTimeMillis() - 16_000
    ),
    BroadcastFeedItem(
        number = "237627981XXX",
        range = "237627XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Your code is ******",
        time = System.currentTimeMillis() - 16_000
    ),
    BroadcastFeedItem(
        number = "237625693XXX",
        range = "237625XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Your code is ******",
        time = System.currentTimeMillis() - 20_000
    ),
    BroadcastFeedItem(
        number = "237627989XXX",
        range = "237627XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Your code is ******",
        time = System.currentTimeMillis() - 29_000
    ),
    BroadcastFeedItem(
        number = "32468599XXXX",
        range = "324685XXX",
        service = "INSTAGRAM",
        country = "BELGIUM",
        operator = "Base",
        otp = "<#> *** *** is your Instagram code. Don't share it. SIYRxKrru1t",
        time = System.currentTimeMillis() - 31_000
    ),
    BroadcastFeedItem(
        number = "237627605XXX",
        range = "237627XXX",
        service = "FACEBOOK",
        country = "CAMEROON",
        operator = "Mobile",
        otp = "<#> Facebook: Kode Anda adalah ******",
        time = System.currentTimeMillis() - 33_000
    ),
    BroadcastFeedItem(
        number = "22898475XXXX",
        range = "228984XXX",
        service = "INSTAGRAM",
        country = "TOGO",
        operator = "ATL(Moov)",
        otp = "<#> *** *** is your Instagram code. Don't share it. SIYRxKrru1t",
        time = System.currentTimeMillis() - 40_000
    ),
    BroadcastFeedItem(
        number = "234802937XXX",
        range = "234802XXX",
        service = "WHATSAPP",
        country = "NIGERIA",
        operator = "Celtel (Airtel)",
        otp = "<#> Your WhatsApp code: ***-*** Don't share this code with others",
        time = System.currentTimeMillis() - 45_000
    )
)

data class VirtualNumbersUiState(
    val apiKey: String = "ZNX_SDY9RBKGG8DO84EWZOMWEH2S",
    val targetRange: String = "237627XXX",
    val requestCount: Int = 1,
    val isNational: Boolean = false,
    val removePlus: Boolean = false,
    val provisionedNumbers: List<ProvisionedNumber> = emptyList(),
    val activeRanges: List<ActiveRangeItem> = defaultActiveRangesList,
    val broadcastFeed: List<BroadcastFeedItem> = defaultBroadcastList,
    val todayOtpCount: Int = 0,
    val resetCycleStartTime: Long = 0L,
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val lastError: String? = null,
    val lastSyncTime: Long = 0L
)

fun createDefaultProvisionedList(): List<ProvisionedNumber> {
    return emptyList()
}

object VirtualNumberManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var pollingJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(VirtualNumbersUiState(provisionedNumbers = createDefaultProvisionedList()))
    val state: StateFlow<VirtualNumbersUiState> = _state.asStateFlow()

    private val _selectedPanel = MutableStateFlow(0) // 0: Unix SMS, 1: Zenex SMS
    val selectedPanel: StateFlow<Int> = _selectedPanel.asStateFlow()

    private val _zenexOtpRate = MutableStateFlow(0.014)
    val zenexOtpRate: StateFlow<Double> = _zenexOtpRate.asStateFlow()

    fun setSelectedPanel(panelIndex: Int) {
        _selectedPanel.value = panelIndex
        prefs?.edit()?.putInt("selected_panel_index", panelIndex)?.apply()
    }

    fun adjustZenexOtpRate(delta: Double) {
        val current = _zenexOtpRate.value
        val updated = maxOf(0.001, current + delta)
        _zenexOtpRate.value = updated
        prefs?.edit()?.putFloat("zenex_otp_rate_setting", updated.toFloat())?.apply()
    }

    fun setZenexOtpRate(rate: Double) {
        val safe = maxOf(0.001, rate)
        _zenexOtpRate.value = safe
        prefs?.edit()?.putFloat("zenex_otp_rate_setting", safe.toFloat())?.apply()
    }

    private val otpRegex = Pattern.compile("\\b\\d{4,8}\\b")

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("virtual_numbers_prefs", Context.MODE_PRIVATE)
            val savedPanel = prefs?.getInt("selected_panel_index", 0) ?: 0
            _selectedPanel.value = savedPanel
            val savedZenexRate = prefs?.getFloat("zenex_otp_rate_setting", 0.014f)?.toDouble() ?: 0.014
            _zenexOtpRate.value = savedZenexRate

            val savedKey = prefs?.getString("api_key", "ZNX_SDY9RBKGG8DO84EWZOMWEH2S") ?: "ZNX_SDY9RBKGG8DO84EWZOMWEH2S"
            val savedRange = prefs?.getString("target_range", "237620XXX") ?: "237620XXX"
            val savedCount = prefs?.getInt("request_count", 1) ?: 1
            val todayKey = getTodayKey()
            val savedTodayCount = prefs?.getInt(todayKey, 0) ?: 0
            val savedUserNumbers = loadUserProvisionedNumbers()

            val cycleStartTime = prefs?.getLong("reset_cycle_start_time", 0L) ?: 0L
            val now = System.currentTimeMillis()
            val hasExpired24h = cycleStartTime > 0L && (now - cycleStartTime >= 24 * 60 * 60 * 1000L)

            val effectiveCycleStart: Long
            val effectiveOtpCount: Int
            val effectiveNumbers: List<ProvisionedNumber>

            if (hasExpired24h) {
                // 24 hours have already passed! Auto-reset everything
                effectiveCycleStart = 0L
                effectiveOtpCount = 0
                effectiveNumbers = emptyList()
                prefs?.edit()
                    ?.putLong("reset_cycle_start_time", 0L)
                    ?.putInt(todayKey, 0)
                    ?.apply()
                saveUserProvisionedNumbers(emptyList())
            } else {
                effectiveCycleStart = cycleStartTime
                effectiveOtpCount = savedTodayCount
                effectiveNumbers = savedUserNumbers
            }

            _state.update {
                it.copy(
                    apiKey = savedKey,
                    targetRange = savedRange,
                    requestCount = savedCount,
                    todayOtpCount = effectiveOtpCount,
                    resetCycleStartTime = effectiveCycleStart,
                    provisionedNumbers = effectiveNumbers
                )
            }

            scope.launch {
                fetchActiveRanges()
                fetchGlobalBroadcast()
            }
        }
    }

    fun checkNumberTimeouts() {
        val now = System.currentTimeMillis()
        val currentCycleStart = _state.value.resetCycleStartTime

        // Check if 24 hours have passed since the 1st number/OTP was taken!
        if (currentCycleStart > 0L && now - currentCycleStart >= 24 * 60 * 60 * 1000L) {
            val todayKey = getTodayKey()
            prefs?.edit()
                ?.putLong("reset_cycle_start_time", 0L)
                ?.putInt(todayKey, 0)
                ?.apply()
            saveUserProvisionedNumbers(emptyList())
            _state.update {
                it.copy(
                    provisionedNumbers = emptyList(),
                    todayOtpCount = 0,
                    resetCycleStartTime = 0L
                )
            }
            return
        }

        _state.update { current ->
            var hasChanges = false
            val updated = current.provisionedNumbers.map { item ->
                if (item.status == "pending" && now >= item.expiresAt) {
                    hasChanges = true
                    item.copy(status = "failed", failReason = "Timeout")
                } else {
                    item
                }
            }
            if (hasChanges) {
                saveUserProvisionedNumbers(updated)
                current.copy(provisionedNumbers = updated)
            } else current
        }
    }

    private fun getTodayKey(): String {
        val sdf = SimpleDateFormat("yyyy_MM_dd", Locale.US)
        return "otp_today_" + sdf.format(Date())
    }

    private fun saveUserProvisionedNumbers(list: List<ProvisionedNumber>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { p ->
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("number", p.number)
                obj.put("country", p.country)
                obj.put("operator", p.operator)
                obj.put("iso", p.iso)
                obj.put("status", p.status)
                obj.put("otpCode", p.otpCode ?: "")
                obj.put("otpMessage", p.otpMessage ?: "")
                obj.put("failReason", p.failReason ?: "")
                obj.put("timestamp", p.timestamp)
                obj.put("expiresAt", p.expiresAt)
                obj.put("range", p.range)
                arr.put(obj)
            }
            prefs?.edit()?.putString("user_saved_numbers_json", arr.toString())?.apply()
        } catch (_: Exception) {}
    }

    private fun loadUserProvisionedNumbers(): List<ProvisionedNumber> {
        val raw = prefs?.getString("user_saved_numbers_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<ProvisionedNumber>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ProvisionedNumber(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        number = obj.optString("number"),
                        country = obj.optString("country", "CAMEROON"),
                        operator = obj.optString("operator", "Mobile"),
                        iso = obj.optString("iso", "cm"),
                        status = obj.optString("status", "pending"),
                        otpCode = obj.optString("otpCode").ifEmpty { null },
                        otpMessage = obj.optString("otpMessage").ifEmpty { null },
                        failReason = obj.optString("failReason").ifEmpty { null },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        expiresAt = obj.optLong("expiresAt", System.currentTimeMillis() + 1_200_000L),
                        range = obj.optString("range")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setApiKey(key: String) {
        val clean = key.trim()
        _state.update { it.copy(apiKey = clean) }
        prefs?.edit()?.putString("api_key", clean)?.apply()
    }

    fun setTargetRange(range: String) {
        _state.update { it.copy(targetRange = range.trim()) }
        prefs?.edit()?.putString("target_range", range.trim())?.apply()
    }

    fun setRequestCount(count: Int) {
        val safeCount = count.coerceIn(1, 10)
        _state.update { it.copy(requestCount = safeCount) }
        prefs?.edit()?.putInt("request_count", safeCount)?.apply()
    }

    fun setOptions(isNational: Boolean, removePlus: Boolean) {
        _state.update { it.copy(isNational = isNational, removePlus = removePlus) }
    }

    /**
     * Provisions virtual numbers sequentially based on requestCount (1 to 10).
     */
    fun provisionNumbers(
        context: Context,
        range: String = _state.value.targetRange,
        count: Int = _state.value.requestCount,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val apiKey = _state.value.apiKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        if (range.isBlank()) {
            Toast.makeText(context, "Range প্রদান করুন (e.g. 237620XXX)", Toast.LENGTH_SHORT).show()
            onComplete(false, "Empty range")
            return
        }

        _state.update { it.copy(isLoading = true, lastError = null) }

        scope.launch {
            var successCount = 0
            val newNumbers = mutableListOf<ProvisionedNumber>()
            var lastMsg = ""

            for (i in 0 until count) {
                try {
                    val url = "https://api.zenexnetwork.com/v1/getnum"
                    val jsonBody = JSONObject().apply {
                        put("range", range)
                        put("is_national", _state.value.isNational)
                        put("remove_plus", _state.value.removePlus)
                    }

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("mapikey", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""

                    if (response.isSuccessful && responseStr.isNotEmpty()) {
                        val json = JSONObject(responseStr)
                        val dataObj = json.optJSONObject("data")
                        if (dataObj != null) {
                            val num = dataObj.optString("number").ifEmpty {
                                dataObj.optString("copy").ifEmpty { dataObj.optString("full_number") }
                            }
                            val country = dataObj.optString("country", "Global")
                            val operator = dataObj.optString("operator", "Mobile")
                            val iso = dataObj.optString("iso", "gb")
                            val status = dataObj.optString("status", "pending")

                            if (num.isNotEmpty()) {
                                val now = System.currentTimeMillis()
                                val item = ProvisionedNumber(
                                    number = num,
                                    country = country,
                                    operator = operator,
                                    iso = iso,
                                    status = status,
                                    range = range,
                                    timestamp = now,
                                    expiresAt = now + 1_200_000L // 20 minutes timeout (Zenex panel standard)
                                )
                                newNumbers.add(item)
                                successCount++
                            }
                        }
                        lastMsg = json.optString("message", "Number provisioned")
                    } else {
                        lastMsg = "Error ${response.code}: $responseStr"
                    }
                } catch (e: Exception) {
                    lastMsg = e.message ?: "Network error"
                }

                if (i < count - 1) {
                    delay(300L) // polite pacing between multi-requests
                }
            }

            _state.update { current ->
                val combined = newNumbers + current.provisionedNumbers
                saveUserProvisionedNumbers(combined)
                val currentCycleStart = current.resetCycleStartTime
                val updatedCycleStart = if (currentCycleStart <= 0L && combined.isNotEmpty()) {
                    val t = System.currentTimeMillis()
                    prefs?.edit()?.putLong("reset_cycle_start_time", t)?.apply()
                    t
                } else currentCycleStart

                current.copy(
                    provisionedNumbers = combined,
                    resetCycleStartTime = updatedCycleStart,
                    isLoading = false,
                    lastError = if (successCount == 0) lastMsg else null
                )
            }

            mainHandler.post {
                if (successCount > 0) {
                    Toast.makeText(
                        context,
                        "✓ $successCount টি ভার্চুয়াল নম্বর রেডি!",
                        Toast.LENGTH_SHORT
                    ).show()
                    VibrationHelper.vibrateSuccess(context)
                    // Auto copy first number to clipboard
                    newNumbers.firstOrNull()?.let {
                        ClipboardHelper.copyToClipboard(context, it.number, "Phone Number")
                    }
                    // Start live OTP polling immediately!
                    startAutoPolling(context, autoCopy = true)
                    onComplete(true, "Successfully provisioned $successCount numbers")
                } else {
                    Toast.makeText(context, "নম্বর পাওয়া যায়নি: $lastMsg", Toast.LENGTH_LONG).show()
                    onComplete(false, lastMsg)
                }
            }
        }
    }

    /**
     * Polls Zenex OTP engine (numsuccess/info) for live SMS payloads.
     */
    fun fetchIncomingOtps(context: Context, autoCopy: Boolean = true) {
        val apiKey = _state.value.apiKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        scope.launch {
            try {
                val url = "https://api.zenexnetwork.com/v1/numsuccess/info"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("mapikey", apiKey)
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (response.isSuccessful && responseStr.isNotEmpty()) {
                    val root = JSONObject(responseStr)
                    val dataObj = root.optJSONObject("data")
                    val otpsArr = dataObj?.optJSONArray("otps") ?: root.optJSONArray("otps")

                    if (otpsArr != null && otpsArr.length() > 0) {
                        val newlyArrivedList = mutableListOf<ProvisionedNumber>()
                        var copiedOtpCode: String? = null

                        _state.update { current ->
                            val updatedList = current.provisionedNumbers.map { provisioned ->
                                val cleanProvisionedDigits = provisioned.number.replace("[^0-9]".toRegex(), "")

                                var matchedOtp: String? = null
                                var matchedCode: String? = null

                                for (i in 0 until otpsArr.length()) {
                                    val otpObj = otpsArr.optJSONObject(i) ?: continue
                                    val incomingNum = otpObj.optString("number").replace("[^0-9]".toRegex(), "")
                                    val otpText = otpObj.optString("otp")

                                    if (cleanProvisionedDigits.isNotEmpty() &&
                                        (cleanProvisionedDigits.contains(incomingNum) || incomingNum.contains(cleanProvisionedDigits))) {
                                        matchedOtp = otpText
                                        // Extract 5-8 digit (or 4-8 digit) OTP code
                                        val candidates = mutableListOf<String>()
                                        val matcher = otpRegex.matcher(otpText)
                                        while (matcher.find()) {
                                            candidates.add(matcher.group())
                                        }

                                        // Prefer candidate not part of the phone number and between 4-8 digits (especially 5-8)
                                        matchedCode = candidates.firstOrNull { cand ->
                                            !cleanProvisionedDigits.contains(cand) && cand.length in 5..8
                                        } ?: candidates.firstOrNull { cand ->
                                            !cleanProvisionedDigits.contains(cand) && cand.length == 4
                                        } ?: candidates.firstOrNull { cand -> cand.length in 5..8 }
                                          ?: candidates.firstOrNull()

                                        break
                                    }
                                }

                                if (matchedOtp != null && provisioned.otpCode != matchedCode) {
                                    copiedOtpCode = matchedCode
                                    val updated = provisioned.copy(
                                        status = "success",
                                        otpCode = matchedCode,
                                        otpMessage = matchedOtp
                                    )
                                    newlyArrivedList.add(updated)
                                    updated
                                } else {
                                    provisioned
                                }
                            }

                            val newCount = if (newlyArrivedList.isNotEmpty()) current.todayOtpCount + newlyArrivedList.size else current.todayOtpCount
                            if (newlyArrivedList.isNotEmpty()) {
                                val todayKey = getTodayKey()
                                prefs?.edit()?.putInt(todayKey, newCount)?.apply()

                                // Record to permanent Total OTP History
                                newlyArrivedList.forEach { prov ->
                                    val code = prov.otpCode ?: ""
                                    OtpHistoryManager.recordOtp(
                                        phoneNumber = prov.number,
                                        otpCode = code,
                                        service = prov.range.ifEmpty { "Zenex" },
                                        platform = "Zenex",
                                        rate = _zenexOtpRate.value
                                    )
                                }
                            }
                            saveUserProvisionedNumbers(updatedList)

                            current.copy(
                                provisionedNumbers = updatedList,
                                todayOtpCount = newCount,
                                lastSyncTime = System.currentTimeMillis()
                            )
                        }

                        if (newlyArrivedList.isNotEmpty()) {
                            mainHandler.post {
                                newlyArrivedList.forEach { prov ->
                                    val code = prov.otpCode ?: ""
                                    com.example.util.OtpNotificationHelper.showOtpNotification(
                                        context = context,
                                        phoneNumber = prov.number,
                                        otpCode = code,
                                        fullMessage = prov.otpMessage ?: "Your OTP is $code"
                                    )
                                }
                                if (copiedOtpCode != null) {
                                    ClipboardHelper.copyToClipboard(context, copiedOtpCode!!, "OTP Code")
                                    Toast.makeText(context, "⚡ OTP কপি হয়েছে: $copiedOtpCode", Toast.LENGTH_SHORT).show()
                                    VibrationHelper.vibrateSuccess(context)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun startAutoPolling(context: Context, autoCopy: Boolean = true) {
        if (pollingJob?.isActive == true) return
        _state.update { it.copy(isPolling = true) }
        pollingJob = scope.launch {
            while (isActive) {
                checkNumberTimeouts()
                fetchIncomingOtps(context, autoCopy = autoCopy)
                delay(2000L) // Fast 2-second check & timeout evaluator
            }
        }
    }

    fun stopAutoPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _state.update { it.copy(isPolling = false) }
    }

    /**
     * Fetches dynamic active ranges from Zenex engine.
     */
    fun fetchActiveRanges() {
        val apiKey = _state.value.apiKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        scope.launch {
            try {
                val url = "https://api.zenexnetwork.com/v1/active-ranges"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("mapikey", apiKey)
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (response.isSuccessful && responseStr.isNotEmpty()) {
                    val root = JSONObject(responseStr)
                    val dataObj = root.optJSONObject("data")
                    val rangesArr = dataObj?.optJSONArray("active_ranges") ?: root.optJSONArray("active_ranges")

                    if (rangesArr != null) {
                        val list = mutableListOf<ActiveRangeItem>()
                        for (i in 0 until rangesArr.length()) {
                            val obj = rangesArr.optJSONObject(i) ?: continue
                            list.add(
                                ActiveRangeItem(
                                    range = obj.optString("range"),
                                    service = obj.optString("service", "General"),
                                    tag = obj.optString("tag", "Premium"),
                                    hits = obj.optInt("hits", 0)
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            _state.update { it.copy(activeRanges = list) }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Fetches public global live broadcast feed for the Console view.
     */
    fun fetchGlobalBroadcast() {
        val apiKey = _state.value.apiKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        scope.launch {
            try {
                val url = "https://www.zenexnetwork.com/api/v1/global-broadcast"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("mapikey", apiKey)
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (response.isSuccessful && responseStr.isNotEmpty()) {
                    val root = JSONObject(responseStr)
                    val dataArr = root.optJSONArray("data")
                    if (dataArr != null) {
                        val list = mutableListOf<BroadcastFeedItem>()
                        for (i in 0 until dataArr.length()) {
                            val obj = dataArr.optJSONObject(i) ?: continue
                            val number = obj.optString("number")
                            val range = if (number.length >= 8) number.take(7) + "XXX" else number
                            list.add(
                                BroadcastFeedItem(
                                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                    number = number,
                                    range = range,
                                    service = obj.optString("service", "WHATSAPP").uppercase(),
                                    country = obj.optString("country", "Global"),
                                    operator = obj.optString("operator", "Mobile"),
                                    otp = obj.optString("otp"),
                                    time = obj.optLong("time", System.currentTimeMillis())
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            _state.update { it.copy(broadcastFeed = list) }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun clearProvisionedNumbers() {
        prefs?.edit()?.putLong("reset_cycle_start_time", 0L)?.apply()
        saveUserProvisionedNumbers(emptyList())
        _state.update {
            it.copy(
                provisionedNumbers = emptyList(),
                todayOtpCount = 0,
                resetCycleStartTime = 0L
            )
        }
    }
}
