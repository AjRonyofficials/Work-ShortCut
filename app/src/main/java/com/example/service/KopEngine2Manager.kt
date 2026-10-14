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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

val defaultKop2ActiveRangesList = listOf(
    ActiveRangeItem(range = "260XXX", service = "Facebook", tag = "Hot Zambia", hits = 72),
    ActiveRangeItem(range = "23276345XXX", service = "Other", tag = "Sierra Leone", hits = 45),
    ActiveRangeItem(range = "447700XXX", service = "WhatsApp", tag = "UK Fast", hits = 38),
    ActiveRangeItem(range = "120255XXX", service = "Telegram", tag = "US Tier1", hits = 29),
    ActiveRangeItem(range = "336123XXX", service = "Facebook", tag = "FR Direct", hits = 22),
    ActiveRangeItem(range = "491512XXX", service = "Google", tag = "DE Direct", hits = 18)
)

val defaultKop2BroadcastList = listOf(
    BroadcastFeedItem(
        number = "260XXX",
        range = "260XXX",
        service = "Facebook",
        country = "Zambia",
        operator = "Zamtel",
        otp = "<#> ******** is your Facebook code\nH29Q+Fsn4Sr",
        time = System.currentTimeMillis() - 52_000
    ),
    BroadcastFeedItem(
        number = "260XXX",
        range = "260XXX",
        service = "Facebook",
        country = "Zambia",
        operator = "Zamtel",
        otp = "<#> ******** est votre code Facebook\nH29Q+Fsn4Sr",
        time = System.currentTimeMillis() - 73_000
    ),
    BroadcastFeedItem(
        number = "260XXX",
        range = "260XXX",
        service = "Facebook",
        country = "Zambia",
        operator = "Zamtel",
        otp = "<#> ***** is your Facebook code\nH29Q+Fsn4Sr",
        time = System.currentTimeMillis() - 118_000
    ),
    BroadcastFeedItem(
        number = "232763XXX",
        range = "232763XXX",
        service = "Telegram",
        country = "Sierra Leone",
        operator = "Africell",
        otp = "Telegram code: 48921. Do not give this code to anyone.",
        time = System.currentTimeMillis() - 175_000
    ),
    BroadcastFeedItem(
        number = "447700XXX",
        range = "447700XXX",
        service = "WhatsApp",
        country = "United Kingdom",
        operator = "EE UK",
        otp = "Your WhatsApp code: 391-042. You can also tap on the link to verify your phone.",
        time = System.currentTimeMillis() - 240_000
    )
)

object KopEngine2Manager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var pollingJob: Job? = null

    private const val MK_BASE_URL = "https://mknetworkbd.com/api/v2/public"
    private const val DEFAULT_MK_API_KEY = "e1f877801b810ff8cedff1ba"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(
        VirtualNumbersUiState(
            apiKey = DEFAULT_MK_API_KEY,
            targetRange = "260XXX",
            activeRanges = defaultKop2ActiveRangesList,
            broadcastFeed = defaultKop2BroadcastList
        )
    )
    val state: StateFlow<VirtualNumbersUiState> = _state.asStateFlow()

    private val _kop2OtpRate = MutableStateFlow(0.50)
    val kop2OtpRate: StateFlow<Double> = _kop2OtpRate.asStateFlow()

    private val otpRegex = Pattern.compile("\\b\\d{4,8}\\b")

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("kop_engine_2_prefs", Context.MODE_PRIVATE)

            val rawRate = prefs?.getFloat("kop2_otp_rate_setting", 0.50f)?.toDouble() ?: 0.50
            val savedRate = if (rawRate < 0.1) 0.50 else rawRate
            _kop2OtpRate.value = (Math.round(savedRate * 100.0) / 100.0)

            val savedKey = prefs?.getString("api_key", DEFAULT_MK_API_KEY) ?: DEFAULT_MK_API_KEY
            val effectiveKey = if (savedKey.startsWith("KOP2_") || savedKey.isBlank()) DEFAULT_MK_API_KEY else savedKey
            val savedRange = prefs?.getString("target_range", "260XXX") ?: "260XXX"
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
                    apiKey = effectiveKey,
                    targetRange = savedRange,
                    requestCount = savedCount,
                    todayOtpCount = effectiveOtpCount,
                    resetCycleStartTime = effectiveCycleStart,
                    provisionedNumbers = effectiveNumbers,
                    activeRanges = defaultKop2ActiveRangesList,
                    broadcastFeed = defaultKop2BroadcastList
                )
            }

            scope.launch {
                fetchActiveRanges()
                fetchGlobalBroadcast()
            }

            if (effectiveNumbers.any { it.otpCode.isNullOrEmpty() && (it.status.equals("pending", true) || it.status.equals("wait", true)) }) {
                startAutoPolling(context, autoCopy = true)
            }
        }
    }

    fun adjustKop2OtpRate(delta: Double) {
        val current = _kop2OtpRate.value
        val updated = (Math.round(maxOf(0.01, current + delta) * 100.0) / 100.0)
        _kop2OtpRate.value = updated
        prefs?.edit()?.putFloat("kop2_otp_rate_setting", updated.toFloat())?.apply()
        GlobalCloudSyncService.pushRateSettingsOnly(
            unixRate = UnixSmsManager.state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            kop2Rate = updated
        )
        WithdrawalManager.refreshBalances()
    }

    fun setKop2OtpRate(rate: Double) {
        val safe = (Math.round(maxOf(0.01, rate) * 100.0) / 100.0)
        _kop2OtpRate.value = safe
        prefs?.edit()?.putFloat("kop2_otp_rate_setting", safe.toFloat())?.apply()
        GlobalCloudSyncService.pushRateSettingsOnly(
            unixRate = UnixSmsManager.state.value.otpRatePerSms,
            zenexRate = VirtualNumberManager.zenexOtpRate.value,
            kop2Rate = safe
        )
        WithdrawalManager.refreshBalances()
    }

    fun applyCloudRate(newRate: Double) {
        if (newRate <= 0.0) return
        val safe = (Math.round(newRate * 100.0) / 100.0)
        if (Math.abs(_kop2OtpRate.value - safe) > 0.001) {
            _kop2OtpRate.value = safe
            prefs?.edit()?.putFloat("kop2_otp_rate_setting", safe.toFloat())?.apply()
            WithdrawalManager.refreshBalances()
        }
    }

    fun checkNumberTimeouts() {
        val now = System.currentTimeMillis()
        val currentCycleStart = _state.value.resetCycleStartTime

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

        val list = _state.value.provisionedNumbers
        val updated = list.map { item ->
            if (item.otpCode.isNullOrEmpty() && (item.status.equals("pending", true) || item.status.equals("wait", true)) && now >= item.expiresAt) {
                item.copy(status = "expired")
            } else item
        }
        if (updated != list) {
            saveUserProvisionedNumbers(updated)
            _state.update { it.copy(provisionedNumbers = updated) }
        }
    }

    fun setTargetRange(range: String) {
        val cleaned = range.trim().uppercase()
        prefs?.edit()?.putString("target_range", cleaned)?.apply()
        _state.update { it.copy(targetRange = cleaned) }
    }

    fun setRequestCount(count: Int) {
        val safe = count.coerceIn(1, 10)
        prefs?.edit()?.putInt("request_count", safe)?.apply()
        _state.update { it.copy(requestCount = safe) }
    }

    fun setOptions(isNational: Boolean, removePlus: Boolean) {
        _state.update { it.copy(isNational = isNational, removePlus = removePlus) }
    }

    fun setNational(national: Boolean) {
        _state.update { it.copy(isNational = national) }
    }

    fun setRemovePlus(removePlus: Boolean) {
        _state.update { it.copy(removePlus = removePlus) }
    }

    fun getFormattedCycleCountdown(): String {
        val cycleStart = _state.value.resetCycleStartTime
        if (cycleStart <= 0L) return "24h Ready"
        val remainingMs = ((cycleStart + 24 * 60 * 60 * 1000L) - System.currentTimeMillis()).coerceAtLeast(0L)
        val h = (remainingMs / (1000 * 60 * 60)).toInt()
        val m = ((remainingMs / (1000 * 60)) % 60).toInt()
        val s = ((remainingMs / 1000) % 60).toInt()
        return String.format(Locale.US, "%02dh %02dm %02ds", h, m, s)
    }

    fun copyAndRemovePlus(context: Context, number: String) {
        val clean = number.replace("+", "").trim()
        ClipboardHelper.copyToClipboard(context, clean, "Phone Number")
        Toast.makeText(context, "কপি হয়েছে: $clean", Toast.LENGTH_SHORT).show()
    }

    fun copyOtpCode(context: Context, code: String) {
        ClipboardHelper.copyToClipboard(context, code, "OTP Code")
        Toast.makeText(context, "OTP কপি হয়েছে: $code", Toast.LENGTH_SHORT).show()
    }

    fun deleteNumber(id: String) {
        val updated = _state.value.provisionedNumbers.filter { it.id != id }
        saveUserProvisionedNumbers(updated)
        _state.update { it.copy(provisionedNumbers = updated) }
    }

    fun provisionNumbers(
        context: Context,
        range: String = _state.value.targetRange,
        count: Int = _state.value.requestCount,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val apiKey = _state.value.apiKey.ifEmpty { DEFAULT_MK_API_KEY }
        if (range.isBlank()) {
            Toast.makeText(context, "Range প্রদান করুন (e.g. 260XXX)", Toast.LENGTH_SHORT).show()
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
                    val url = "$MK_BASE_URL/getnum/number"
                    val jsonBody = JSONObject().apply {
                        put("range", range)
                    }

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("mknetwork-key", apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = try {
                        httpClient.newCall(request).execute()
                    } catch (_: Exception) {
                        null
                    }

                    val responseStr = response?.body?.string() ?: ""

                    if (response != null && response.isSuccessful && responseStr.isNotEmpty()) {
                        val json = JSONObject(responseStr)
                        val dataObj = json.optJSONObject("data")
                        if (dataObj != null) {
                            val rawNum = dataObj.optString("number").ifEmpty {
                                dataObj.optString("full_number")
                            }
                            val reqId = dataObj.optLong("request_id", 0L)
                            val country = dataObj.optString("country", "Zambia")
                            val operator = dataObj.optString("operator", "Zamtel")
                            val status = dataObj.optString("status", "pending")
                            val secondsRemaining = dataObj.optLong("seconds_remaining", 900L)

                            if (rawNum.isNotEmpty()) {
                                val now = System.currentTimeMillis()
                                val formattedNum = if (_state.value.removePlus) {
                                    rawNum.replace("+", "").trim()
                                } else {
                                    if (!rawNum.startsWith("+")) "+$rawNum" else rawNum
                                }

                                val item = ProvisionedNumber(
                                    number = formattedNum,
                                    country = country,
                                    operator = operator,
                                    iso = getIsoForCountry(country),
                                    status = status,
                                    range = range,
                                    timestamp = now,
                                    expiresAt = now + (secondsRemaining * 1000L).coerceAtLeast(15 * 60 * 1000L),
                                    requestId = if (reqId > 0L) reqId else null
                                )
                                newNumbers.add(item)
                                successCount++
                            }
                        }
                        lastMsg = json.optString("message", "Number provisioned successfully")
                    } else {
                        // Extract API error message if provided by MK Network
                        if (responseStr.isNotEmpty()) {
                            try {
                                val errJson = JSONObject(responseStr)
                                lastMsg = errJson.optString("message", "MK Network: Range $range available নেই")
                            } catch (_: Exception) {
                                lastMsg = if (response?.code == 404) "MK Network: Range $range available নেই" else "API Error ${response?.code}"
                            }
                        } else {
                            lastMsg = "MK Network এর সাথে সংযোগ স্থাপন করা যায়নি"
                        }
                    }
                } catch (e: Exception) {
                    lastMsg = "Error: ${e.message ?: "Network error"}"
                }

                if (i < count - 1) {
                    delay(350L)
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
                        "✓ Kop Engine 2: $successCount টি নম্বর রেডি!",
                        Toast.LENGTH_SHORT
                    ).show()
                    VibrationHelper.vibrateSuccess(context)
                    if (newNumbers.isNotEmpty()) {
                        val allNumbersText = newNumbers.joinToString("\n") { it.number.replace("+", "").trim() }
                        ClipboardHelper.copyToClipboard(context, allNumbersText, "Phone Numbers")
                    }
                    startAutoPolling(context, autoCopy = true)
                    onComplete(true, "Successfully provisioned $successCount numbers")
                } else {
                    Toast.makeText(context, "নম্বর পাওয়া যায়নি: $lastMsg", Toast.LENGTH_LONG).show()
                    onComplete(false, lastMsg)
                }
            }
        }
    }

    private fun getIsoForCountry(country: String): String {
        return when {
            country.contains("Zambia", ignoreCase = true) -> "zm"
            country.contains("Sierra Leone", ignoreCase = true) -> "sl"
            country.contains("United Kingdom", ignoreCase = true) || country.contains("UK", ignoreCase = true) -> "gb"
            country.contains("United States", ignoreCase = true) || country.contains("US", ignoreCase = true) -> "us"
            country.contains("France", ignoreCase = true) -> "fr"
            country.contains("Germany", ignoreCase = true) -> "de"
            country.contains("Cameroon", ignoreCase = true) -> "cm"
            country.contains("Nigeria", ignoreCase = true) -> "ng"
            else -> "un"
        }
    }

    fun fetchIncomingOtps(context: Context, autoCopy: Boolean = true) {
        val apiKey = _state.value.apiKey.ifEmpty { DEFAULT_MK_API_KEY }
        val pendingItems = _state.value.provisionedNumbers.filter {
            it.otpCode.isNullOrEmpty() && (it.status.equals("pending", true) || it.status.equals("wait", true))
        }

        if (pendingItems.isEmpty()) return

        val requestIds = pendingItems.mapNotNull { it.requestId }
        if (requestIds.isEmpty()) return

        scope.launch {
            try {
                val url = "$MK_BASE_URL/check/status"
                val bodyJson = JSONObject().apply {
                    val arr = JSONArray()
                    requestIds.take(100).forEach { arr.put(it) }
                    put("request_ids", arr)
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("mknetwork-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = try {
                    httpClient.newCall(request).execute()
                } catch (_: Exception) { null }

                val responseStr = response?.body?.string() ?: ""

                if (response != null && response.isSuccessful && responseStr.isNotEmpty()) {
                    val root = JSONObject(responseStr)
                    val dataObj = root.optJSONObject("data")
                    val resultsArr = dataObj?.optJSONArray("results")

                    if (resultsArr != null && resultsArr.length() > 0) {
                        processMkCheckStatusResults(context, resultsArr, autoCopy)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun processMkCheckStatusResults(context: Context, arr: JSONArray, autoCopy: Boolean) {
        var hasChanges = false
        val currentList = _state.value.provisionedNumbers.toMutableList()
        var newlyReceivedCount = 0

        for (i in 0 until arr.length()) {
            val resObj = arr.getJSONObject(i)
            val reqId = resObj.optLong("request_id", -1L)
            val status = resObj.optString("status", "")
            val num = resObj.optString("number", "")
            val code = resObj.optString("otp_code", "")
            val fullSms = resObj.optString("full_sms", "")

            val index = currentList.indexOfFirst {
                (reqId > 0 && it.requestId == reqId) || (num.isNotEmpty() && isPhoneNumberMatch(it.number, num))
            }

            if (index != -1) {
                val target = currentList[index]
                if (status.equals("success", ignoreCase = true) && code.isNotEmpty() && target.otpCode.isNullOrEmpty()) {
                    val updated = target.copy(
                        status = "success",
                        otpCode = code,
                        otpMessage = fullSms.ifEmpty { "OTP Code: $code" }
                    )
                    currentList[index] = updated
                    hasChanges = true
                    newlyReceivedCount++

                    OtpHistoryManager.recordOtp(
                        phoneNumber = target.number,
                        otpCode = code,
                        service = target.range.ifEmpty { "Kop Engine 2" },
                        platform = "Kop Engine 2",
                        rate = _kop2OtpRate.value
                    )

                    // Inject to live broadcast feed
                    val feedItem = BroadcastFeedItem(
                        number = target.number,
                        range = target.range.ifEmpty { "260XXX" },
                        service = "Facebook",
                        country = target.country,
                        operator = target.operator,
                        otp = fullSms.ifEmpty { "<#> $code is your verification code" },
                        time = System.currentTimeMillis()
                    )
                    _state.update { it.copy(broadcastFeed = listOf(feedItem) + it.broadcastFeed.take(25)) }

                    mainHandler.post {
                        VibrationHelper.vibrateSuccess(context)
                        OtpNotificationHelper.showOtpNotification(
                            context = context,
                            phoneNumber = target.number,
                            otpCode = code,
                            fullMessage = fullSms
                        )
                        if (autoCopy) {
                            ClipboardHelper.copyToClipboard(context, code, "OTP Code")
                        }
                    }
                } else if (status.equals("expired", ignoreCase = true) && target.otpCode.isNullOrEmpty()) {
                    if (!target.status.equals("expired", ignoreCase = true)) {
                        currentList[index] = target.copy(status = "expired")
                        hasChanges = true
                    }
                }
            }
        }

        if (hasChanges) {
            val updatedCount = _state.value.todayOtpCount + newlyReceivedCount
            saveTodayOtpCount(updatedCount)
            saveUserProvisionedNumbers(currentList)

            _state.update {
                it.copy(
                    provisionedNumbers = currentList,
                    todayOtpCount = updatedCount
                )
            }
        }
    }

    fun startAutoPolling(context: Context, autoCopy: Boolean = true) {
        if (pollingJob?.isActive == true) return
        _state.update { it.copy(isPolling = true) }

        pollingJob = scope.launch {
            while (isActive) {
                if (!com.example.util.AppLifecycleTracker.canRunBackgroundWork()) {
                    delay(10000L)
                    continue
                }
                checkNumberTimeouts()
                val pendingExist = _state.value.provisionedNumbers.any { 
                    it.otpCode.isNullOrEmpty() && (it.status.equals("pending", true) || it.status.equals("wait", true))
                }
                if (pendingExist) {
                    fetchIncomingOtps(context, autoCopy = autoCopy)
                }
                delay(1200L)
            }
        }
    }

    fun stopAutoPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _state.update { it.copy(isPolling = false) }
    }

    fun fetchActiveRanges() {
        _state.update { it.copy(activeRanges = defaultKop2ActiveRangesList) }
    }

    fun fetchGlobalBroadcast() {
        // Keeps timestamps refreshed relative to current clock
        val currentFeed = _state.value.broadcastFeed
        val refreshed = currentFeed.mapIndexed { i, item ->
            val offset = (i + 1) * 35_000L
            item.copy(time = System.currentTimeMillis() - offset)
        }
        _state.update { it.copy(broadcastFeed = refreshed) }
    }

    fun clearActiveNumbers(context: Context) {
        saveUserProvisionedNumbers(emptyList())
        _state.update { it.copy(provisionedNumbers = emptyList()) }
        Toast.makeText(context, "Kop Engine 2 নম্বর লিস্ট পরিষ্কার করা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    fun clearFailedNumbers(context: Context) {
        val now = System.currentTimeMillis()
        val current = _state.value.provisionedNumbers
        val (failed, remaining) = current.partition {
            it.otpCode.isNullOrEmpty() && (it.status.equals("failed", true) || it.status.equals("expired", true) || now >= it.expiresAt)
        }
        if (failed.isEmpty()) {
            Toast.makeText(context, "Kop Engine 2: কোনো ব্যর্থ নম্বর নেই", Toast.LENGTH_SHORT).show()
            return
        }
        saveUserProvisionedNumbers(remaining)
        _state.update { it.copy(provisionedNumbers = remaining) }
        Toast.makeText(context, "✓ Kop 2: ${failed.size} টি ব্যর্থ নম্বর রিমুভ করা হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    fun resetTodayCycle(context: Context) {
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
        Toast.makeText(context, "Kop Engine 2 ২৪ ঘণ্টার ডাটা রিসেট হয়েছে", Toast.LENGTH_SHORT).show()
    }

    private fun getTodayKey(): String {
        return "today_otp_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    private fun saveTodayOtpCount(count: Int) {
        prefs?.edit()?.putInt(getTodayKey(), count)?.apply()
    }

    private fun loadUserProvisionedNumbers(): List<ProvisionedNumber> {
        val json = prefs?.getString("kop2_user_provisioned_numbers_v2", null) ?: return emptyList()
        val list = mutableListOf<ProvisionedNumber>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ProvisionedNumber(
                        number = o.getString("number"),
                        country = o.optString("country", "Zambia"),
                        operator = o.optString("operator", "Zamtel"),
                        iso = o.optString("iso", "zm"),
                        status = o.optString("status", "pending"),
                        range = o.optString("range", "260XXX"),
                        otpCode = if (o.has("otpCode") && !o.isNull("otpCode")) o.optString("otpCode") else null,
                        otpMessage = if (o.has("otpMessage") && !o.isNull("otpMessage")) o.optString("otpMessage") else null,
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        expiresAt = o.optLong("expiresAt", System.currentTimeMillis() + 15 * 60 * 1000L),
                        requestId = if (o.has("requestId") && !o.isNull("requestId")) o.optLong("requestId") else null
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveUserProvisionedNumbers(list: List<ProvisionedNumber>) {
        try {
            val arr = JSONArray()
            for (item in list) {
                val o = JSONObject()
                o.put("number", item.number)
                o.put("country", item.country)
                o.put("operator", item.operator)
                o.put("iso", item.iso)
                o.put("status", item.status)
                o.put("range", item.range)
                item.otpCode?.let { o.put("otpCode", it) }
                item.otpMessage?.let { o.put("otpMessage", it) }
                o.put("timestamp", item.timestamp)
                o.put("expiresAt", item.expiresAt)
                item.requestId?.let { o.put("requestId", it) }
                arr.put(o)
            }
            prefs?.edit()?.putString("kop2_user_provisioned_numbers_v2", arr.toString())?.apply()
        } catch (_: Exception) {}
    }

    fun isPhoneNumberMatch(num1: String, num2: String): Boolean {
        val d1 = num1.replace("[^0-9]".toRegex(), "")
        val d2 = num2.replace("[^0-9]".toRegex(), "")
        if (d1.length < 7 || d2.length < 7) return false
        val len = minOf(d1.length, d2.length, 9)
        if (len < 7) return false
        return d1.takeLast(len) == d2.takeLast(len)
    }
}
