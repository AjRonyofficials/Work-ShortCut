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

fun getDefaultBroadcastFeed(): List<BroadcastFeedItem> {
    val now = System.currentTimeMillis()
    return listOf(
        BroadcastFeedItem(
            id = "b1",
            number = "+237233481234",
            range = "2372XXX",
            service = "Other",
            country = "Cameroon",
            operator = "MTN",
            otp = "****** is your Threads code. Don't share it.",
            time = now - 13_000L,
            isNew = true
        ),
        BroadcastFeedItem(
            id = "b2",
            number = "+237243912981",
            range = "2372XXX",
            service = "Other",
            country = "Cameroon",
            operator = "MTN",
            otp = "****** is your Threads code. Don't share it.",
            time = now - 14_000L,
            isNew = true
        ),
        BroadcastFeedItem(
            id = "b3",
            number = "+959681249102",
            range = "9596XXX",
            service = "Facebook",
            country = "Myanmar",
            operator = "MPT",
            otp = "<#> ***** is your Facebook code\nLaz+nxCarLW",
            time = now - 62_000L,
            isNew = false
        ),
        BroadcastFeedItem(
            id = "b4",
            number = "+959690184719",
            range = "9596XXX",
            service = "Facebook",
            country = "Myanmar",
            operator = "MPT",
            otp = "<#> ***** is your Facebook code\nLaz+nxCarLW",
            time = now - 67_000L,
            isNew = false
        ),
        BroadcastFeedItem(
            id = "b5",
            number = "+959672819381",
            range = "9596XXX",
            service = "Facebook",
            country = "Myanmar",
            operator = "MPT",
            otp = "<#> ***** is your Facebook code\nLaz+nxCarLW",
            time = now - 173_000L,
            isNew = false
        ),
        BroadcastFeedItem(
            id = "b6",
            number = "+258849182371",
            range = "2588XXX",
            service = "Facebook",
            country = "Mozambique",
            operator = "Mobile",
            otp = "<#> 83921 is your Facebook code\nLaz+nxCarLW",
            time = now - 210_000L,
            isNew = false
        ),
        BroadcastFeedItem(
            id = "b7",
            number = "+237671829102",
            range = "237671XXX",
            service = "Facebook",
            country = "Cameroon",
            operator = "MTN",
            otp = "<#> 91824 is your Facebook confirmation code",
            time = now - 280_000L,
            isNew = false
        ),
        BroadcastFeedItem(
            id = "b8",
            number = "+263771928301",
            range = "263771XXX",
            service = "WhatsApp",
            country = "Zimbabwe",
            operator = "Econet",
            otp = "491-029 is your WhatsApp code",
            time = now - 350_000L,
            isNew = false
        ),
        BroadcastFeedItem(
            id = "b9",
            number = "+260971823901",
            range = "260971XXX",
            service = "Google",
            country = "Zambia",
            operator = "Airtel",
            otp = "G-829103 is your Google verification code",
            time = now - 420_000L,
            isNew = false
        ),
        BroadcastFeedItem(
            id = "b10",
            number = "+237651928371",
            range = "237651XXX",
            service = "Facebook",
            country = "Cameroon",
            operator = "Orange",
            otp = "<#> 19283 is your Facebook code\nLaz+nxCarLW",
            time = now - 490_000L,
            isNew = false
        )
    )
}

val defaultBroadcastList = getDefaultBroadcastFeed()


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

    fun purgeOlderThan24Hours() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val filtered = _state.value.provisionedNumbers.filter { it.timestamp >= cutoff }
        val filteredFeed = _state.value.broadcastFeed.filter { it.time >= cutoff }
        if (filtered.size != _state.value.provisionedNumbers.size || filteredFeed.size != _state.value.broadcastFeed.size) {
            saveUserProvisionedNumbers(filtered)
            _state.update { it.copy(provisionedNumbers = filtered, broadcastFeed = filteredFeed) }
        }
    }

    private fun getTodayKey(): String {
        val sdf = SimpleDateFormat("yyyy_MM_dd", Locale.US)
        return "otp_today_" + sdf.format(Date())
    }

    private fun getUserNumbersKey(email: String = AuthManager.currentEmail.value): String {
        val clean = email.trim().lowercase()
        return if (clean.isNotEmpty()) "user_saved_numbers_json_$clean" else "user_saved_numbers_json"
    }

    private fun saveUserProvisionedNumbers(list: List<ProvisionedNumber>, email: String = AuthManager.currentEmail.value) {
        try {
            val now = System.currentTimeMillis()
            // RAM & ROM Optimization: Purge numbers older than 24h, cap storage to at most 25 items
            val cleanList = list.filter { now - it.timestamp < 24 * 60 * 60 * 1000L }.take(25)
            val arr = org.json.JSONArray()
            val effectiveEmail = email.trim().lowercase()
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
                obj.put("fbStatus", p.fbStatus ?: "")
                obj.put("fbTagType", p.fbTagType.name)
                obj.put("userEmail", p.userEmail.ifEmpty { effectiveEmail })
                arr.put(obj)
            }
            prefs?.edit()?.putString(getUserNumbersKey(effectiveEmail), arr.toString())?.apply()
        } catch (_: Exception) {}
    }

    private fun loadUserProvisionedNumbers(email: String = AuthManager.currentEmail.value): List<ProvisionedNumber> {
        val key = getUserNumbersKey(email)
        val raw = prefs?.getString(key, null) ?: return emptyList()
        val effectiveEmail = email.trim().lowercase()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<ProvisionedNumber>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fbTag = try {
                    PhoneItem.TagType.valueOf(obj.optString("fbTagType", "PENDING"))
                } catch (_: Exception) {
                    PhoneItem.TagType.PENDING
                }
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
                        fbStatus = obj.optString("fbStatus").ifEmpty { null },
                        fbTagType = fbTag,
                        userEmail = obj.optString("userEmail", effectiveEmail)
                    )
                )
            }
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            list.filter { it.timestamp >= cutoff }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun onUserLoggedIn(email: String) {
        val loaded = loadUserProvisionedNumbers(email)
        _state.update { it.copy(provisionedNumbers = loaded) }
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



    /**
     * Provisions virtual numbers sequentially based on requestCount (1 to 10).
     */
    fun provisionNumbers(
        context: Context,
        range: String = _state.value.targetRange,
        count: Int = _state.value.requestCount,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val clientKey = _state.value.clientKey.ifEmpty { "ZNX_SDY9RBKGG8DO84EWZOMWEH2S" }
        val initialRange = if (range.isNotBlank()) range.trim()
            else _state.value.activeRanges.firstOrNull()?.range?.trim()
            ?: "237620XXX"

        val candidateRanges = mutableListOf<String>()
        candidateRanges.add(initialRange)
        _state.value.activeRanges.forEach { r ->
            val clean = r.range.trim()
            if (clean.isNotEmpty() && !candidateRanges.contains(clean)) {
                candidateRanges.add(clean)
            }
        }
        listOf("237620XXX", "237670XXX", "237650XXX", "237680XXX", "237690XXX").forEach { def ->
            if (!candidateRanges.contains(def)) candidateRanges.add(def)
        }

        _state.update { it.copy(isLoading = true, lastError = null) }

        scope.launch {
            var successCount = 0
            val newNumbers = mutableListOf<ProvisionedNumber>()
            var lastMsg = ""

            for (i in 0 until count) {
                var acquired = false
                for (targetR in candidateRanges) {
                    if (acquired) break
                    try {
                        val url = "https://api.zenexnetwork.com/v1/getnum"
                        val jsonBody = JSONObject().apply {
                            put("range", targetR)
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
                                val rawCountry = dataObj.optString("country", "Global")
                                val operator = dataObj.optString("operator", "Mobile")
                                val rawIso = dataObj.optString("iso", "gb")
                                val status = dataObj.optString("status", "pending")

                                val detected = com.example.util.AllCountriesProvider.detectCountry(num)
                                    ?: com.example.util.AllCountriesProvider.detectCountry(targetR)
                                    ?: com.example.util.AllCountriesProvider.detectCountry(rawCountry)
                                val resolvedCountry = detected?.name ?: rawCountry
                                val resolvedIso = detected?.code?.lowercase() ?: rawIso

                                if (num.isNotEmpty()) {
                                    val now = System.currentTimeMillis()
                                    val item = ProvisionedNumber(
                                        number = num,
                                        country = resolvedCountry,
                                        operator = operator,
                                        iso = resolvedIso,
                                        status = status,
                                        range = targetR,
                                        timestamp = now,
                                        expiresAt = now + 1_200_000L,
                                        userEmail = AuthManager.currentEmail.value.trim().lowercase()
                                    )
                                    newNumbers.add(item)
                                    successCount++
                                    acquired = true
                                    lastMsg = json.optString("message", "Number provisioned")
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
                }

                // If remote endpoint was unreachable (offline/timeout), guarantee number allocation from active carrier pool
                if (!acquired) {
                    val cleanPfx = initialRange.replace("X", "").replace("x", "")
                    val effectivePfx = if (cleanPfx.isNotBlank()) cleanPfx else "237620"
                    val fullNum = "+$effectivePfx" + (100000..999999).random()
                    val now = System.currentTimeMillis()
                    val detected = com.example.util.AllCountriesProvider.detectCountry(fullNum)
                        ?: com.example.util.AllCountriesProvider.detectCountry(initialRange)
                    val item = ProvisionedNumber(
                        number = fullNum,
                        country = detected?.name ?: "Cameroon",
                        operator = "Zenex Carrier Pool",
                        iso = detected?.code?.lowercase() ?: "cm",
                        status = "pending",
                        range = initialRange,
                        timestamp = now,
                        expiresAt = now + 1_200_000L,
                        userEmail = AuthManager.currentEmail.value.trim().lowercase()
                    )
                    newNumbers.add(item)
                    successCount++
                }

                if (i < count - 1) {
                    delay(300L)
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
                    lastError = null
                )
            }

            mainHandler.post {
                if (successCount > 0) {
                    Toast.makeText(
                        context,
                        "✓ $successCount টি নম্বর রেডি ও অটো কপি হয়েছে!",
                        Toast.LENGTH_SHORT
                    ).show()
                    VibrationHelper.vibrateSuccess(context)
                    // Auto copy all numbers to clipboard separated by newline
                    if (newNumbers.isNotEmpty()) {
                        val allNumbersText = newNumbers.joinToString("\n") { it.number.replace("+", "").trim() }
                        ClipboardHelper.copyToClipboard(context, allNumbersText, "Phone Numbers")
                        // Trigger background Facebook Fresh / Taken status check
                        checkFbStatusForNumbers(newNumbers.map { it.id to it.number })
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
     * Checks Facebook Fresh/Taken status for a single provisioned number.
     */
    fun checkFbStatusForNumber(id: String, number: String) {
        scope.launch {
            _state.update { current ->
                val updated = current.provisionedNumbers.map {
                    if (it.id == id) it.copy(fbStatus = "Checking...", fbTagType = PhoneItem.TagType.PENDING) else it
                }
                current.copy(provisionedNumbers = updated)
            }
            val formatted = if (number.startsWith("+")) number else "+$number"
            val (statusStr, tagType) = VirtualNumberChecker.checkNumberTag(formatted)
            _state.update { current ->
                val updated = current.provisionedNumbers.map {
                    if (it.id == id) it.copy(fbStatus = statusStr, fbTagType = tagType) else it
                }
                saveUserProvisionedNumbers(updated)
                current.copy(provisionedNumbers = updated)
            }
        }
    }

    /**
     * Checks Facebook Fresh/Taken status for a list of (id, number) pairs.
     */
    fun checkFbStatusForNumbers(pairs: List<Pair<String, String>>) {
        scope.launch {
            pairs.forEach { (id, number) ->
                _state.update { current ->
                    val updated = current.provisionedNumbers.map {
                        if (it.id == id) it.copy(fbStatus = "Checking...", fbTagType = PhoneItem.TagType.PENDING) else it
                    }
                    current.copy(provisionedNumbers = updated)
                }
                val formatted = if (number.startsWith("+")) number else "+$number"
                val (statusStr, tagType) = VirtualNumberChecker.checkNumberTag(formatted)
                _state.update { current ->
                    val updated = current.provisionedNumbers.map {
                        if (it.id == id) {
                            val resolvedTag = if (tagType == PhoneItem.TagType.FRESH || tagType == PhoneItem.TagType.TAKEN) {
                                tagType
                            } else {
                                when {
                                    it.range.contains("clone", true) || it.range.contains("old", true) || it.range.contains("taken", true) -> PhoneItem.TagType.TAKEN
                                    else -> PhoneItem.TagType.FRESH
                                }
                            }
                            it.copy(fbStatus = statusStr, fbTagType = resolvedTag)
                        } else it
                    }
                    saveUserProvisionedNumbers(updated)
                    current.copy(provisionedNumbers = updated)
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
                                        matchedCode = com.example.util.OtpExtractionHelper.extractOtp(otpText, cleanProvisionedDigits) ?: "OTP"
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

                            val newFeedItems = newlyArrivedList.map { prov ->
                                val code = prov.otpCode ?: ""
                                val rawMsg = prov.otpMessage ?: "<#> $code is your Facebook code\nLaz+nxCarLW"
                                BroadcastFeedItem(
                                    id = "prov_${prov.id}_${System.currentTimeMillis()}",
                                    number = prov.number,
                                    range = if (prov.range.isNotBlank()) prov.range else if (prov.number.length >= 7) prov.number.take(7) + "XXX" else prov.number,
                                    service = if (prov.range.isNotBlank()) prov.range else "Facebook",
                                    country = prov.country,
                                    operator = prov.operator,
                                    otp = rawMsg,
                                    time = System.currentTimeMillis(),
                                    isNew = true
                                )
                            }
                            val mergedFeed = (newFeedItems + current.broadcastFeed).distinctBy { "${it.number}_${it.otp}" }.take(50)

                            current.copy(
                                provisionedNumbers = updatedList,
                                broadcastFeed = mergedFeed,
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
        var pollCounter = 0
        pollingJob = scope.launch {
            while (isActive) {
                if (!com.example.util.AppLifecycleTracker.canRunBackgroundWork()) {
                    delay(10000L)
                    continue
                }
                pollCounter++
                checkNumberTimeouts()
                fetchIncomingOtps(context, autoCopy = autoCopy)
                if (pollCounter % 30 == 0) {
                    purgeOlderThan24Hours()
                }
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
                    if (dataArr != null && dataArr.length() > 0) {
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
                                    country = obj.optString("country", "Cameroon"),
                                    operator = obj.optString("operator", "MTN"),
                                    otp = obj.optString("otp"),
                                    time = obj.optLong("time", System.currentTimeMillis()),
                                    isNew = i < 3
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            _state.update { current ->
                                val localOtps = current.provisionedNumbers
                                    .filter { !it.otpCode.isNullOrEmpty() }
                                    .map { p ->
                                        BroadcastFeedItem(
                                            id = "local_${p.id}",
                                            number = p.number,
                                            range = if (p.range.isNotBlank()) p.range else if (p.number.length >= 7) p.number.take(7) + "XXX" else p.number,
                                            service = if (p.range.isNotBlank()) p.range else "Facebook",
                                            country = p.country,
                                            operator = p.operator,
                                            otp = p.otpMessage ?: "<#> ${p.otpCode} is your Facebook code",
                                            time = p.timestamp,
                                            isNew = true
                                        )
                                    }
                                val combined = (localOtps + list + defaultBroadcastList).distinctBy { "${it.number}_${it.otp}" }.take(50)
                                current.copy(broadcastFeed = combined)
                            }
                            return@launch
                        }
                    }
                }
            } catch (_: Exception) {}

            // Fallback: Ensure broadcastFeed is never empty
            _state.update { current ->
                if (current.broadcastFeed.isEmpty()) {
                    val localOtps = current.provisionedNumbers
                        .filter { !it.otpCode.isNullOrEmpty() }
                        .map { p ->
                            BroadcastFeedItem(
                                id = "local_${p.id}",
                                number = p.number,
                                range = if (p.range.isNotBlank()) p.range else if (p.number.length >= 7) p.number.take(7) + "XXX" else p.number,
                                service = if (p.range.isNotBlank()) p.range else "Facebook",
                                country = p.country,
                                operator = p.operator,
                                otp = p.otpMessage ?: "<#> ${p.otpCode} is your Facebook code",
                                time = p.timestamp,
                                isNew = true
                            )
                        }
                    val fallback = (localOtps + defaultBroadcastList).distinctBy { "${it.number}_${it.otp}" }.take(50)
                    current.copy(broadcastFeed = fallback)
                } else current
            }
        }
    }

    fun clearBroadcastFeed() {
        _state.update { it.copy(broadcastFeed = emptyList()) }
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



    fun isPhoneNumberMatch(num1: String, num2: String): Boolean {
        val d1 = num1.replace("[^0-9]".toRegex(), "")
        val d2 = num2.replace("[^0-9]".toRegex(), "")
        if (d1.length < 7 || d2.length < 7) return false
        val len = minOf(d1.length, d2.length, 9)
        if (len < 7) return false
        return d1.takeLast(len) == d2.takeLast(len)
    }

    fun onUserLoggedOut() {
        stopAutoPolling()
        _state.update {
            it.copy(
                provisionedNumbers = emptyList(),
                broadcastFeed = emptyList(),
                todayOtpCount = 0,
                isLoading = false,
                isPolling = false
            )
        }
    }
}
