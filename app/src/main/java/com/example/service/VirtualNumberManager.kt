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

val defaultActiveRangesList = emptyList<ActiveRangeItem>()

val defaultBroadcastList = emptyList<BroadcastFeedItem>()

data class VirtualNumbersUiState(
    val clientKey: String = "ZNX_SDY9RBKGG8DO84EWZOMWEH2S",
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

    private val _zenexOtpRate = MutableStateFlow(0.50)
    val zenexOtpRate: StateFlow<Double> = _zenexOtpRate.asStateFlow()

    fun setSelectedPanel(panelIndex: Int) {
        _selectedPanel.value = panelIndex
        prefs?.edit()?.putInt("selected_panel_index", panelIndex)?.apply()
    }

    fun adjustZenexOtpRate(delta: Double) {
        val current = _zenexOtpRate.value
        val updated = (Math.round(maxOf(0.01, current + delta) * 100.0) / 100.0)
        _zenexOtpRate.value = updated
        prefs?.edit()?.putFloat("zenex_otp_rate_setting", updated.toFloat())?.apply()
        // Push globally to all devices
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = UnixSmsManager.state.value.uploadedNumbers,
            unixRate = UnixSmsManager.state.value.otpRatePerSms,
            zenexRate = updated,
            countryRates = UnixSmsManager.state.value.countryRates
        )
    }

    fun setZenexOtpRate(rate: Double) {
        val safe = (Math.round(maxOf(0.01, rate) * 100.0) / 100.0)
        _zenexOtpRate.value = safe
        prefs?.edit()?.putFloat("zenex_otp_rate_setting", safe.toFloat())?.apply()
        // Push globally to all devices
        GlobalCloudSyncService.pushToCloud(
            uploadedNumbers = UnixSmsManager.state.value.uploadedNumbers,
            unixRate = UnixSmsManager.state.value.otpRatePerSms,
            zenexRate = safe,
            countryRates = UnixSmsManager.state.value.countryRates
        )
    }

    fun applyCloudRate(newRate: Double) {
        if (newRate <= 0.0) return
        val safe = (Math.round(newRate * 100.0) / 100.0)
        if (Math.abs(_zenexOtpRate.value - safe) > 0.001) {
            _zenexOtpRate.value = safe
            prefs?.edit()?.putFloat("zenex_otp_rate_setting", safe.toFloat())?.apply()
        }
    }

    private val otpRegex = Pattern.compile("\\b\\d{4,8}\\b")

    fun init(context: Context) {
        AccountCheckerManager.init(context)
        if (prefs == null) {
            prefs = context.getSharedPreferences("virtual_numbers_prefs", Context.MODE_PRIVATE)
            val savedPanel = prefs?.getInt("selected_panel_index", 0) ?: 0
            _selectedPanel.value = savedPanel
            val rawZenexRate = prefs?.getFloat("zenex_otp_rate_setting", 0.50f)?.toDouble() ?: 0.50
            val savedZenexRate = if (rawZenexRate < 0.1) 0.50 else rawZenexRate
            _zenexOtpRate.value = (Math.round(savedZenexRate * 100.0) / 100.0)

            val rawKey = prefs?.getString("api_key", "ZNX_SDY9RBKGG8DO84EWZOMWEH2S") ?: "ZNX_SDY9RBKGG8DO84EWZOMWEH2S"
            val savedKey = if (rawKey.isBlank() || rawKey.startsWith("AQ.") || rawKey.contains("PLACEHOLDER") || rawKey.contains("AIza")) {
                prefs?.edit()?.putString("api_key", "ZNX_SDY9RBKGG8DO84EWZOMWEH2S")?.apply()
                "ZNX_SDY9RBKGG8DO84EWZOMWEH2S"
            } else rawKey
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
                    clientKey = savedKey,
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
                if (item.otpCode.isNullOrEmpty() && (item.status == "pending" || item.status == "wait") && now >= item.expiresAt) {
                    hasChanges = true
                    item.copy(status = "failed", failReason = "Timeout (20m)")
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
            val now = System.currentTimeMillis()
            // RAM & ROM Optimization: Purge numbers older than 24h, cap storage to at most 25 items
            val cleanList = list.filter { now - it.timestamp < 24 * 60 * 60 * 1000L }.take(25)
            val arr = org.json.JSONArray()
            cleanList.forEach { p ->
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
                obj.put("accountStatus", p.accountStatus)
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
                        range = obj.optString("range"),
                        accountStatus = obj.optString("accountStatus", "UNCHECKED")
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
        _state.update { it.copy(clientKey = clean) }
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

    fun recheckNumberAccount(context: Context, id: String) {
        val target = _state.value.provisionedNumbers.firstOrNull { it.id == id } ?: return
        _state.update { curr ->
            curr.copy(
                provisionedNumbers = curr.provisionedNumbers.map {
                    if (it.id == id) it.copy(accountStatus = "CHECKING") else it
                }
            )
        }
        AccountCheckerManager.checkNumberAsync(target.number, forceRefresh = true) { result ->
            _state.update { curr ->
                val updated = curr.provisionedNumbers.map {
                    if (it.id == id) it.copy(accountStatus = result.status) else it
                }
                saveUserProvisionedNumbers(updated)
                curr.copy(provisionedNumbers = updated)
            }
            mainHandler.post {
                Toast.makeText(
                    context,
                    "${target.number}: ${result.badgeLabel}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Provisions virtual numbers sequentially based on requestCount (1 to 10),
     * running `account_checker.py` verification and background auto-retry if Option 1 (Fresh Only) or Option 2 (Old Only) is enabled.
     */
    fun provisionNumbers(
        context: Context,
        range: String = _state.value.targetRange,
        count: Int = _state.value.requestCount,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        AccountCheckerManager.init(context)
        val clientKey = _state.value.clientKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
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
            val checkerState = AccountCheckerManager.state.value
            val checkerEnabled = checkerState.isEnabled
            val mode = checkerState.mode
            val maxAttempts = if (checkerEnabled && mode != AccountCheckerMode.BOTH) checkerState.maxRetries else 1

            for (i in 0 until count) {
                var matchedItem: ProvisionedNumber? = null
                var fallbackCandidate: ProvisionedNumber? = null

                for (attempt in 1..maxAttempts) {
                    if (checkerEnabled && maxAttempts > 1 && attempt > 1) {
                        val targetLabel = if (mode == AccountCheckerMode.FRESH_ONLY) "FRESH (Green)" else "OLD (Red)"
                        AccountCheckerManager.setRetryStatus(
                            true,
                            "🔄 ব্যাকগ্রাউন্ডে $targetLabel নম্বর খোঁজা হচ্ছে... (রি-ট্রাই #$attempt/$maxAttempts)"
                        )
                    } else if (checkerEnabled) {
                        AccountCheckerManager.setRetryStatus(
                            true,
                            "🔍 account_checker.py দিয়ে নম্বর চেক করা হচ্ছে..."
                        )
                    }

                    try {
                        val url = "https://api.zenexnetwork.com/v1/getnum"
                        val jsonBody = JSONObject().apply {
                            put("range", range)
                            put("is_national", _state.value.isNational)
                            put("remove_plus", _state.value.removePlus)
                        }

                        val request = Request.Builder()
                            .url(url)
                            .addHeader("mapikey", clientKey)
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
                                    if (checkerEnabled) {
                                        val checkRes = AccountCheckerManager.checkPhoneNumber(num)
                                        val candidate = ProvisionedNumber(
                                            number = num,
                                            country = country,
                                            operator = operator,
                                            iso = iso,
                                            status = status,
                                            range = range,
                                            timestamp = now,
                                            expiresAt = now + 1_200_000L,
                                            accountStatus = checkRes.status
                                        )
                                        fallbackCandidate = candidate
                                        if (AccountCheckerManager.matchesSelectedMode(checkRes)) {
                                            matchedItem = candidate
                                            lastMsg = "${checkRes.badgeLabel} • $num"
                                            break
                                        } else {
                                            // Did not match mode (e.g. got OLD when wanting FRESH, or got FRESH when wanting OLD)
                                            lastMsg = "Retrying (${checkRes.status})..."
                                        }
                                    } else {
                                        matchedItem = ProvisionedNumber(
                                            number = num,
                                            country = country,
                                            operator = operator,
                                            iso = iso,
                                            status = status,
                                            range = range,
                                            timestamp = now,
                                            expiresAt = now + 1_200_000L,
                                            accountStatus = "UNCHECKED"
                                        )
                                        lastMsg = json.optString("message", "Number provisioned")
                                        break
                                    }
                                }
                            } else {
                                lastMsg = json.optString("message", "No number data")
                            }
                        } else {
                            lastMsg = "Error ${response.code}: $responseStr"
                        }
                    } catch (e: Exception) {
                        lastMsg = e.message ?: "Network error"
                    }

                    if (attempt < maxAttempts) {
                        delay(250L)
                    }
                }

                val finalItem = matchedItem ?: fallbackCandidate
                if (finalItem != null) {
                    newNumbers.add(finalItem)
                    successCount++
                }

                if (i < count - 1) {
                    delay(300L)
                }
            }

            AccountCheckerManager.setRetryStatus(false, null)

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
                    val firstBadge = newNumbers.firstOrNull()?.let {
                        when (it.accountStatus) {
                            "OLD" -> " [🔴 OLD ACCOUNT]"
                            "FRESH" -> " [🟢 NEW / FRESH]"
                            else -> ""
                        }
                    } ?: ""
                    Toast.makeText(
                        context,
                        "✓ $successCount টি নম্বর রেডি ও অটো কপি হয়েছে!$firstBadge",
                        Toast.LENGTH_SHORT
                    ).show()
                    VibrationHelper.vibrateSuccess(context)
                    // Auto copy all numbers to clipboard separated by newline
                    if (newNumbers.isNotEmpty()) {
                        val allNumbersText = newNumbers.joinToString("\n") { it.number.replace("+", "").trim() }
                        ClipboardHelper.copyToClipboard(context, allNumbersText, "Phone Numbers")
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
        val clientKey = _state.value.clientKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        scope.launch {
            try {
                val url = "https://api.zenexnetwork.com/v1/numsuccess/info"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("mapikey", clientKey)
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
                                // If already successfully received OTP, do not re-match or re-notify!
                                if (!provisioned.otpCode.isNullOrEmpty() && provisioned.status == "success") {
                                    return@map provisioned
                                }

                                val cleanProvisionedDigits = provisioned.number.replace("[^0-9]".toRegex(), "")
                                if (cleanProvisionedDigits.length < 7) {
                                    return@map provisioned
                                }

                                var matchedOtp: String? = null
                                var matchedCode: String? = null

                                for (i in 0 until otpsArr.length()) {
                                    val otpObj = otpsArr.optJSONObject(i) ?: continue
                                    val incomingNum = otpObj.optString("number").replace("[^0-9]".toRegex(), "")
                                    val otpText = otpObj.optString("otp")

                                    if (isPhoneNumberMatch(cleanProvisionedDigits, incomingNum)) {
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

                                if (matchedOtp != null && !matchedCode.isNullOrEmpty() && provisioned.otpCode != matchedCode) {
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
                if (!com.example.util.AppLifecycleTracker.canRunBackgroundWork()) {
                    delay(10000L)
                    continue
                }
                checkNumberTimeouts()
                fetchIncomingOtps(context, autoCopy = autoCopy)
                delay(1000L) // Ultra-fast 1-second background auto fetch
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
        val clientKey = _state.value.clientKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        scope.launch {
            try {
                val url = "https://api.zenexnetwork.com/v1/active-ranges"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("mapikey", clientKey)
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
        val clientKey = _state.value.clientKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        scope.launch {
            try {
                val url = "https://www.zenexnetwork.com/api/v1/global-broadcast"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("mapikey", clientKey)
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
                            _state.update { it.copy(broadcastFeed = list.take(30)) }
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

    fun clearFailedNumbers(context: Context) {
        val now = System.currentTimeMillis()
        val current = _state.value.provisionedNumbers
        val (failed, remaining) = current.partition {
            it.otpCode.isNullOrEmpty() && (it.status == "failed" || it.status == "expired" || now >= it.expiresAt)
        }
        if (failed.isEmpty()) {
            Toast.makeText(context, "কোনো ব্যর্থ বা মেয়াদোত্তীর্ণ নম্বর নেই", Toast.LENGTH_SHORT).show()
            return
        }
        saveUserProvisionedNumbers(remaining)
        _state.update { it.copy(provisionedNumbers = remaining) }
        Toast.makeText(context, "✓ ${failed.size} টি ব্যর্থ নম্বর রিমুভ করা হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    suspend fun requestSingleNumberForAccount(context: Context, range: String): ProvisionedNumber? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val effectiveRange = range.trim().ifEmpty { _state.value.targetRange.ifEmpty { "237627XXX" } }
        val clientKey = _state.value.clientKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        try {
            val url = "https://api.zenexnetwork.com/v1/getnum"
            val jsonBody = JSONObject().apply {
                put("range", effectiveRange)
                put("is_national", _state.value.isNational)
                put("remove_plus", _state.value.removePlus)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("mapikey", clientKey)
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
                        val formattedNum = if (!num.startsWith("+")) "+$num" else num
                        val checkRes = AccountCheckerManager.checkPhoneNumber(formattedNum, forceRefresh = false)
                        val prov = ProvisionedNumber(
                            number = formattedNum,
                            country = country,
                            operator = operator,
                            iso = iso,
                            status = status,
                            range = effectiveRange,
                            timestamp = now,
                            expiresAt = now + 1_200_000L,
                            accountStatus = checkRes.status
                        )
                        _state.update { curr ->
                            val updated = (listOf(prov) + curr.provisionedNumbers).distinctBy { it.number }.take(50)
                            saveUserProvisionedNumbers(updated)
                            curr.copy(provisionedNumbers = updated)
                        }
                        mainHandler.post { startAutoPolling(context, autoCopy = true) }
                        return@withContext prov
                    }
                }
            }
        } catch (_: Exception) {}
        null
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
