package com.example.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.example.util.AppLifecycleTracker

data class OtpHistoryRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val phoneNumber: String,
    val otpCode: String,
    val service: String, // e.g. Facebook, Instagram, WhatsApp, Zenex
    val platform: String, // "Unix SMS" or "Zenex"
    val rate: Double = 0.50, // Rate active at time of OTP
    val timestamp: Long = System.currentTimeMillis(),
    val dateKey: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp)),
    val userEmail: String = ""
)

data class DailyOtpSummary(
    val dateKey: String, // "yyyy-MM-dd"
    val displayLabel: String, // "Today (08 Oct)", "Yesterday (07 Oct)", etc.
    val totalCount: Int,
    val unixCount: Int,
    val zenexCount: Int,
    val kop2Count: Int = 0,
    val v2Count: Int = 0,
    val unixRateOnDay: Double,
    val records: List<OtpHistoryRecord>
)

data class OtpHistoryState(
    val totalOtpsAllTime: Int = 0,
    val unixTotalAllTime: Int = 0,
    val zenexTotalAllTime: Int = 0,
    val kop2TotalAllTime: Int = 0,
    val v2TotalAllTime: Int = 0,
    val todayOtps: Int = 0,
    val unixTodayOtps: Int = 0,
    val zenexTodayOtps: Int = 0,
    val kop2TodayOtps: Int = 0,
    val v2TodayOtps: Int = 0,
    val currentUnixRate: Double = 0.50,
    val currentZenexRate: Double = 0.50,
    val currentKop2Rate: Double = 0.50,
    val currentV2Rate: Double = 0.50,
    val last7DaysSummaries: List<DailyOtpSummary> = emptyList(),
    val last30DaysSummaries: List<DailyOtpSummary> = emptyList(),
    val last7DaysOtps: Int = 0,
    val last30DaysOtps: Int = 0,
    val lastMonthOtps: Int = 0,
    val twoMonthsAgoOtps: Int = 0,
    val lastMonthLabel: String = "Previous Month",
    val twoMonthsAgoLabel: String = "2 Months Ago",
    val recentRecords: List<OtpHistoryRecord> = emptyList(),
    val resetCountdownSeconds: Long = 0L,
    val resetTargetEpoch: Long = 0L,
    val monthlyCycleStartEpoch: Long = 0L,
    val monthlyCycleTargetEpoch: Long = 0L
)

object OtpHistoryManager {

    private const val PREFS_NAME = "work_shortcut_otp_history"
    private const val KEY_RECORDS_JSON = "otp_records_json"
    private const val KEY_TOTAL_COUNT = "total_otp_all_time"
    private const val KEY_MONTHLY_CYCLE_START = "monthly_cycle_start_time"
    private const val KEY_CLEANED_DUMMY_V2 = "cleaned_dummy_records_v2"

    private const val SUPABASE_BASE_URL = "https://fbtqjpuclmnsiqehalow.supabase.co/rest/v1"
    private const val SUPABASE_KEY = "sb_publishable_zBqxIMDPy4yHEuAdJJUlxg_wSCRF9u5"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isPolling = false

    // 1 Month = 30 Days in Milliseconds
    private const val ONE_MONTH_MS = 30L * 24L * 60L * 60L * 1000L

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _state = MutableStateFlow(OtpHistoryState())
    val state: StateFlow<OtpHistoryState> = _state.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("dd MMM", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadHistory()
        }
        if (!isPolling) {
            isPolling = true
            scope.launch {
                syncFromCloud(context)
                while (isActive) {
                    if (!AppLifecycleTracker.canRunBackgroundWork()) {
                        delay(20000L)
                        continue
                    }
                    delay(4000L) // 4s live sync
                    try {
                        syncFromCloud(context)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun parseCreatedAt(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        return try {
            val clean = if (dateStr.contains(".")) {
                dateStr.substringBefore(".") + "Z"
            } else if (dateStr.endsWith("+00:00")) {
                dateStr.substringBefore("+") + "Z"
            } else dateStr
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(clean)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun getUserRecordsKey(email: String): String {
        val clean = email.trim().lowercase()
        return if (clean.isNotEmpty()) "${KEY_RECORDS_JSON}_$clean" else KEY_RECORDS_JSON
    }

    private fun getUserTotalKey(email: String): String {
        val clean = email.trim().lowercase()
        return if (clean.isNotEmpty()) "${KEY_TOTAL_COUNT}_$clean" else KEY_TOTAL_COUNT
    }

    suspend fun syncFromCloud(context: Context? = null) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val isAdmin = AuthManager.isAdmin()
        if (currentEmail.isBlank()) return

        try {
            val url = if (isAdmin) {
                "$SUPABASE_BASE_URL/otp_history?select=*&order=created_at.desc&limit=150"
            } else {
                val encEmail = java.net.URLEncoder.encode(currentEmail, "UTF-8")
                "$SUPABASE_BASE_URL/otp_history?user_email=eq.$encEmail&order=created_at.desc&limit=100"
            }

            val req = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val bodyStr = resp.body?.string() ?: return
                val arr = JSONArray(bodyStr)
                if (arr.length() == 0) return
                val cloudList = mutableListOf<OtpHistoryRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val recUser = o.optString("user_email", "").trim().lowercase()

                    // Strict Isolation: If not admin, strictly discard any record not belonging to the logged-in user
                    if (!isAdmin && recUser.isNotEmpty() && recUser != currentEmail) {
                        continue
                    }

                    val phone = o.optString("phone_number", "").trim()
                    val code = o.optString("otp_code", "").trim()

                    // Strict Fake / Corrupt data prevention: Never display mock/fake/empty codes
                    if (code.isBlank() || code == "null" || code.length < 3) continue
                    if (phone.isBlank() || phone.startsWith("+120255501") || phone.startsWith("+2376991122")) continue

                    val idStr = o.optString("id", java.util.UUID.randomUUID().toString())
                    val srv = o.optString("service", "SMS")
                    val plt = o.optString("platform", "Unix SMS")
                    val rateVal = o.optDouble("earned_amount", 0.50)
                    val ts = parseCreatedAt(o.optString("created_at", ""))
                    cloudList.add(
                        OtpHistoryRecord(
                            id = idStr,
                            phoneNumber = phone,
                            otpCode = code,
                            service = srv,
                            platform = plt,
                            rate = if (rateVal < 0.05) 0.50 else rateVal,
                            timestamp = ts,
                            dateKey = dateFormat.format(Date(ts)),
                            userEmail = recUser.ifEmpty { currentEmail }
                        )
                    )
                }

                if (cloudList.isNotEmpty()) {
                    // Deduplicate with local records for this user only
                    val existing = _state.value.recentRecords.filter {
                        isAdmin || it.userEmail.isBlank() || it.userEmail.equals(currentEmail, ignoreCase = true)
                    }
                    val combined = (cloudList + existing).distinctBy { "${it.phoneNumber}_${it.otpCode}" }
                        .filterNot { it.phoneNumber.startsWith("+120255501") || it.phoneNumber.startsWith("+2376991122") }
                        .sortedByDescending { it.timestamp }
                        .take(100)
                    val total = maxOf(combined.size, cloudList.size)
                    val cycleStart = _state.value.monthlyCycleStartEpoch.let {
                        if (it == 0L) System.currentTimeMillis() else it
                    }
                    recalculateSummaries(combined, total, cycleStart)
                    saveRecords(combined, total)
                    WithdrawalManager.refreshBalances()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun fetchUserOtpRecords(userEmail: String): List<OtpHistoryRecord> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val cleanEmail = userEmail.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext emptyList()
        try {
            val encEmail = java.net.URLEncoder.encode(cleanEmail, "UTF-8")
            val url = "$SUPABASE_BASE_URL/otp_history?user_email=eq.$encEmail&order=created_at.desc&limit=100"
            val req = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val bodyStr = resp.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(bodyStr)
                val list = mutableListOf<OtpHistoryRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val phone = o.optString("phone_number", "").trim()
                    val code = o.optString("otp_code", "").trim()
                    if (code.isBlank() || code == "null" || code.length < 3) continue
                    if (phone.startsWith("+120255501") || phone.startsWith("+2376991122")) continue
                    val idStr = o.optString("id", java.util.UUID.randomUUID().toString())
                    val srv = o.optString("service", "SMS")
                    val plt = o.optString("platform", "Unix SMS")
                    val rateVal = o.optDouble("earned_amount", 0.50)
                    val ts = parseCreatedAt(o.optString("created_at", ""))
                    list.add(
                        OtpHistoryRecord(
                            id = idStr,
                            phoneNumber = phone,
                            otpCode = code,
                            service = srv,
                            platform = plt,
                            rate = if (rateVal < 0.05) 0.50 else rateVal,
                            timestamp = ts,
                            dateKey = dateFormat.format(Date(ts)),
                            userEmail = cleanEmail
                        )
                    )
                }
                list.sortedByDescending { it.timestamp }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun triggerLiveCloudSync() {
        scope.launch {
            syncFromCloud()
        }
    }

    fun onUserLoggedIn(email: String, context: Context? = null) {
        val clean = email.trim().lowercase()
        loadHistoryForUser(clean)
        scope.launch {
            syncFromCloud(context)
        }
    }

    fun onUserLoggedOut() {
        _state.value = OtpHistoryState()
    }

    private fun loadHistory() {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        loadHistoryForUser(currentEmail)
    }

    fun loadHistoryForUser(email: String) {
        val key = getUserRecordsKey(email)
        val totalKey = getUserTotalKey(email)
        val json = prefs?.getString(key, "[]") ?: "[]"
        val list = mutableListOf<OtpHistoryRecord>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val recUser = o.optString("userEmail", "").trim().lowercase()
                if (email.isNotEmpty() && recUser.isNotEmpty() && recUser != email) {
                    continue
                }
                val phone = o.optString("phoneNumber", "").trim()
                val code = o.optString("otpCode", "").trim()

                // Filter out any fake/dummy numbers or invalid records
                if (phone.startsWith("+120255501") || phone.startsWith("+2376991122") || code.isBlank() || code == "null") {
                    continue
                }

                val ts = o.optLong("timestamp", System.currentTimeMillis())
                list.add(
                    OtpHistoryRecord(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        phoneNumber = phone,
                        otpCode = code,
                        service = o.optString("service", "SMS"),
                        platform = o.optString("platform", "Unix SMS"),
                        rate = let {
                            val r = o.optDouble("rate", 0.50)
                            if (r < 0.1) 0.50 else r
                        },
                        timestamp = ts,
                        dateKey = o.optString("dateKey", dateFormat.format(Date(ts))),
                        userEmail = recUser.ifEmpty { email }
                    )
                )
            }
        } catch (_: Exception) {}

        val total = prefs?.getInt(totalKey, list.size) ?: list.size

        var cycleStart = prefs?.getLong(KEY_MONTHLY_CYCLE_START, 0L) ?: 0L
        val now = System.currentTimeMillis()
        if (cycleStart == 0L || now - cycleStart >= ONE_MONTH_MS) {
            cycleStart = now
            prefs?.edit()?.putLong(KEY_MONTHLY_CYCLE_START, cycleStart)?.apply()
        }

        val cutoff = now - ONE_MONTH_MS
        val validRecords = list.filter { it.timestamp >= cutoff }

        recalculateSummaries(validRecords, total, cycleStart)
    }

    private fun saveRecords(list: List<OtpHistoryRecord>, totalAllTime: Int) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val key = getUserRecordsKey(currentEmail)
        val totalKey = getUserTotalKey(currentEmail)

        val now = System.currentTimeMillis()
        val cutoff = now - ONE_MONTH_MS
        val validRecords = list.filter { it.timestamp >= cutoff && !it.phoneNumber.startsWith("+120255501") }
        val trimmed = validRecords.take(150)

        val arr = JSONArray()
        for (item in trimmed) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("phoneNumber", item.phoneNumber)
            o.put("otpCode", item.otpCode)
            o.put("service", item.service)
            o.put("platform", item.platform)
            o.put("rate", item.rate)
            o.put("timestamp", item.timestamp)
            o.put("dateKey", item.dateKey)
            o.put("userEmail", item.userEmail.ifEmpty { currentEmail })
            arr.put(o)
        }
        prefs?.edit()
            ?.putString(key, arr.toString())
            ?.putInt(totalKey, totalAllTime)
            ?.apply()
    }

    /**
     * Record arrived OTP from Unix SMS, Zenex, Kop Engine 2, or Premium V2
     */
    fun recordOtp(
        phoneNumber: String,
        otpCode: String,
        service: String,
        platform: String,
        rate: Double = 0.50
    ) {
        // Discard any empty/corrupt codes or fake test numbers immediately
        if (otpCode.isBlank() || otpCode == "null" || otpCode.length < 3) return
        if (phoneNumber.isBlank() || phoneNumber.startsWith("+120255501") || phoneNumber.startsWith("+2376991122")) return

        val now = System.currentTimeMillis()
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase().ifBlank { "user" }

        // Deduplication: prevent counting duplicate OTP code on same phone within 5 minutes
        val isDuplicate = _state.value.recentRecords.any {
            it.phoneNumber == phoneNumber && it.otpCode == otpCode && Math.abs(it.timestamp - now) < 300_000L
        }
        if (isDuplicate) return

        val record = OtpHistoryRecord(
            phoneNumber = phoneNumber,
            otpCode = otpCode,
            service = service,
            platform = platform,
            rate = rate,
            timestamp = now,
            dateKey = dateFormat.format(Date(now)),
            userEmail = currentEmail
        )

        val updatedRecords = listOf(record) + _state.value.recentRecords
        val newTotal = _state.value.totalOtpsAllTime + 1

        val cycleStart = _state.value.monthlyCycleStartEpoch.let {
            if (it == 0L) now else it
        }

        recalculateSummaries(updatedRecords, newTotal, cycleStart)
        saveRecords(updatedRecords, newTotal)
        WithdrawalManager.refreshBalances()
        val st = _state.value
        val bal = WithdrawalManager.state.value.availableBalanceTk
        AuthManager.syncCurrentUserDataToCloud(
            totalOtps = st.totalOtpsAllTime,
            balanceTk = bal,
            todayOtps = st.todayOtps,
            last7d = st.last7DaysOtps,
            last30d = st.last30DaysOtps
        )

        // Instant Audible Chime, Vibration, Auto Clipboard Copy, and Heads-up Notification Alert
        appContext?.let { ctx ->
            try {
                com.example.util.OtpNotificationHelper.showOtpNotification(
                    context = ctx,
                    phoneNumber = phoneNumber,
                    otpCode = otpCode,
                    fullMessage = "$service ওটিপি: $otpCode (৳${String.format(Locale.US, "%.2f", rate)})"
                )
            } catch (_: Exception) {}
        }

        // Real-time Push to Supabase otp_history table with current user_email
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("user_email", currentEmail)
                    put("phone_number", phoneNumber)
                    put("otp_code", otpCode)
                    put("service", service)
                    put("platform", platform)
                    put("earned_amount", rate)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/otp_history")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .post(body)
                    .build()
                httpClient.newCall(req).execute()
            } catch (e: Exception) {
                android.util.Log.e("OtpHistoryManager", "Error pushing OTP to Supabase: ${e.message}")
            }
        }
    }

    fun recalculateSummaries(
        records: List<OtpHistoryRecord>,
        totalAllTime: Int,
        cycleStartEpoch: Long = _state.value.monthlyCycleStartEpoch
    ) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        // Compute midnight of next day (reset time for Today OTP)
        cal.set(Calendar.HOUR_OF_DAY, 24)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val midnightEpoch = cal.timeInMillis
        val secondsUntilReset = maxOf(0L, (midnightEpoch - now) / 1000L)

        val targetMonthlyEpoch = cycleStartEpoch + ONE_MONTH_MS

        val currentUnixRate = UnixSmsManager.state.value.otpRatePerSms
        val currentZenexRate = VirtualNumberManager.zenexOtpRate.value
        val currentKop2Rate = KopEngine2Manager.kop2OtpRate.value
        val currentV2Rate = PremiumNumberV2Manager.otpRate.value

        // Helper to check platform types
        fun isUnix(p: String) = (p.contains("Unix", ignoreCase = true) || p.contains("Lamix", ignoreCase = true) || p.contains("Premium", ignoreCase = true)) && !p.contains("V2", ignoreCase = true)
        fun isKop2(p: String) = p.contains("Kop Engine 2", ignoreCase = true) || p.contains("Kop2", ignoreCase = true) || p.contains("Kop 2", ignoreCase = true)
        fun isV2(p: String) = p.contains("V2", ignoreCase = true) || p.contains("Premium Number V2", ignoreCase = true) || p.contains("Premium V2", ignoreCase = true)

        // Compute 30 days breakdown (Today = Day 0, ..., Day -29)
        val days30List = mutableListOf<DailyOtpSummary>()
        val checkCal = Calendar.getInstance()

        for (i in 0 until 30) {
            val dKey = dateFormat.format(checkCal.time)
            val label = when (i) {
                0 -> "Today (${displayFormat.format(checkCal.time)})"
                1 -> "Yesterday (${displayFormat.format(checkCal.time)})"
                else -> displayFormat.format(checkCal.time)
            }
            val matched = records.filter { it.dateKey == dKey }
            val unixCount = matched.count { isUnix(it.platform) }
            val kop2Count = matched.count { isKop2(it.platform) }
            val v2Count = matched.count { isV2(it.platform) }
            val zenexCount = matched.count { !isUnix(it.platform) && !isKop2(it.platform) && !isV2(it.platform) }

            val dayRate = matched.firstOrNull { isUnix(it.platform) }?.rate ?: currentUnixRate

            days30List.add(
                DailyOtpSummary(
                    dateKey = dKey,
                    displayLabel = label,
                    totalCount = matched.size,
                    unixCount = unixCount,
                    zenexCount = zenexCount,
                    kop2Count = kop2Count,
                    v2Count = v2Count,
                    unixRateOnDay = dayRate,
                    records = matched
                )
            )
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val days7List = days30List.take(7)

        val todaySummary = days30List.firstOrNull()
        val todayTotal = todaySummary?.totalCount ?: 0
        val todayUnix = todaySummary?.unixCount ?: 0
        val todayZenex = todaySummary?.zenexCount ?: 0
        val todayKop2 = todaySummary?.kop2Count ?: 0
        val todayV2 = todaySummary?.v2Count ?: 0

        val unixTotal = records.count { isUnix(it.platform) }
        val kop2Total = records.count { isKop2(it.platform) }
        val v2Total = records.count { isV2(it.platform) }
        val zenexTotal = records.count { !isUnix(it.platform) && !isKop2(it.platform) && !isV2(it.platform) }

        val sum7Days = days7List.sumOf { it.totalCount }
        val sum30Days = days30List.sumOf { it.totalCount }

        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)
        val sixtyDaysAgo = now - (60L * 24 * 60 * 60 * 1000L)
        val ninetyDaysAgo = now - (90L * 24 * 60 * 60 * 1000L)

        val lastMonthCount = records.count { it.timestamp in (sixtyDaysAgo until thirtyDaysAgo) }
        val twoMonthsAgoCount = records.count { it.timestamp in (ninetyDaysAgo until sixtyDaysAgo) }

        val m1Cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val m2Cal = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }
        val monthNameFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val m1Label = monthNameFormat.format(m1Cal.time)
        val m2Label = monthNameFormat.format(m2Cal.time)

        _state.update {
            it.copy(
                totalOtpsAllTime = totalAllTime,
                unixTotalAllTime = unixTotal,
                zenexTotalAllTime = zenexTotal,
                kop2TotalAllTime = kop2Total,
                v2TotalAllTime = v2Total,
                todayOtps = todayTotal,
                unixTodayOtps = todayUnix,
                zenexTodayOtps = todayZenex,
                kop2TodayOtps = todayKop2,
                v2TodayOtps = todayV2,
                currentUnixRate = currentUnixRate,
                currentZenexRate = currentZenexRate,
                currentKop2Rate = currentKop2Rate,
                currentV2Rate = currentV2Rate,
                last7DaysSummaries = days7List,
                last30DaysSummaries = days30List,
                last7DaysOtps = sum7Days,
                last30DaysOtps = sum30Days,
                lastMonthOtps = lastMonthCount,
                twoMonthsAgoOtps = twoMonthsAgoCount,
                lastMonthLabel = m1Label,
                twoMonthsAgoLabel = m2Label,
                recentRecords = records,
                resetCountdownSeconds = secondsUntilReset,
                resetTargetEpoch = midnightEpoch,
                monthlyCycleStartEpoch = cycleStartEpoch,
                monthlyCycleTargetEpoch = targetMonthlyEpoch
            )
        }
    }

    fun getFormattedDailyResetCountdown(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 24)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val diff = maxOf(0L, cal.timeInMillis - System.currentTimeMillis())
        val hours = diff / (1000 * 60 * 60)
        val mins = (diff / (1000 * 60)) % 60
        val secs = (diff / 1000) % 60
        return String.format(Locale.US, "%02dh %02dm %02ds", hours, mins, secs)
    }

    fun getFormatted1MonthRemainingCountdown(): String {
        val now = System.currentTimeMillis()
        var target = _state.value.monthlyCycleTargetEpoch
        if (target <= now) {
            val cycleStart = now
            prefs?.edit()?.putLong(KEY_MONTHLY_CYCLE_START, cycleStart)?.apply()
            target = cycleStart + ONE_MONTH_MS
            _state.update {
                it.copy(monthlyCycleStartEpoch = cycleStart, monthlyCycleTargetEpoch = target)
            }
        }

        val diff = maxOf(0L, target - now)
        val days = diff / (1000 * 60 * 60 * 24)
        val hours = (diff / (1000 * 60 * 60)) % 24
        val mins = (diff / (1000 * 60)) % 60
        val secs = (diff / 1000) % 60

        return if (days > 0) {
            String.format(Locale.US, "%dd %02dh %02dm", days, hours, mins)
        } else {
            String.format(Locale.US, "%02dh %02dm %02ds", hours, mins, secs)
        }
    }

    /**
     * Download / Export 30 Days OTP History
     */
    fun exportOtpHistory(context: Context) {
        val records = _state.value.recentRecords
        val total = _state.value.totalOtpsAllTime
        val today = _state.value.todayOtps
        val timeNowStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())

        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("  WORK SHORTCUT - 30 DAYS OTP HISTORY\n")
        sb.append("  Export Date: $timeNowStr\n")
        sb.append("  Total OTPs: $total (Today: $today)\n")
        sb.append("=========================================\n\n")

        if (records.isEmpty()) {
            sb.append("No OTP records available yet.\n")
        } else {
            sb.append("DATE & TIME       | PLATFORM   | SERVICE    | NUMBER          | OTP CODE\n")
            sb.append("-------------------------------------------------------------------------\n")
            records.forEach { r ->
                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(r.timestamp))
                val platformPadded = r.platform.padEnd(10)
                val servicePadded = r.service.padEnd(10)
                val numPadded = r.phoneNumber.padEnd(15)
                sb.append("$timeStr | $platformPadded | $servicePadded | $numPadded | ${r.otpCode}\n")
            }
            sb.append("\nTotal Records Exported: ${records.size}\n")
            sb.append("=========================================\n")
        }

        val exportText = sb.toString()

        // 1. Copy to clipboard
        ClipboardHelper.copyToClipboard(context, exportText, "OTP 30-Day History")

        // 2. Open Android Share / Save Sheet
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, exportText)
                putExtra(Intent.EXTRA_SUBJECT, "Work ShortCut - OTP History ($timeNowStr)")
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val shareIntent = Intent.createChooser(sendIntent, "Download / Share OTP History").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
            Toast.makeText(context, "✓ OTP হিস্টোরি কপি ও ডাউনলোডের জন্য প্রস্তুত!", Toast.LENGTH_LONG).show()
            VibrationHelper.vibrateSuccess(context)
        } catch (_: Exception) {
            Toast.makeText(context, "✓ OTP হিস্টোরি ক্লিপবোর্ডে কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreUserHistoryIfEmpty(totalOtps: Int, todayOtps: Int, last7d: Int, last30d: Int) {
        if (_state.value.totalOtpsAllTime < totalOtps) {
            _state.update {
                it.copy(
                    totalOtpsAllTime = totalOtps,
                    todayOtps = maxOf(it.todayOtps, todayOtps),
                    last7DaysOtps = maxOf(it.last7DaysOtps, last7d),
                    last30DaysOtps = maxOf(it.last30DaysOtps, last30d)
                )
            }
            prefs?.edit()?.putInt(KEY_TOTAL_COUNT, totalOtps)?.apply()
        }
    }

    /**
     * Purge OTP records older than specified days (default 60 days = 2 months)
     */
    fun purgeOldOtpRecords(olderThanDays: Int = 60): Int {
        val cutoff = System.currentTimeMillis() - (olderThanDays * 24L * 60L * 60L * 1000L)
        val currentRecords = _state.value.recentRecords
        val initialSize = currentRecords.size
        val filtered = currentRecords.filter { it.timestamp >= cutoff }
        val removed = initialSize - filtered.size
        if (removed > 0) {
            val newTotal = maxOf(0, _state.value.totalOtpsAllTime - removed)
            saveRecords(filtered, newTotal)
            recalculateSummaries(filtered, newTotal)
        }
        return removed
    }

    /**
     * Purge corrupted or malformed OTP records (empty otp, invalid phone, corrupt timestamp)
     */
    fun purgeCorruptedOtpRecords(): Int {
        val now = System.currentTimeMillis()
        val currentRecords = _state.value.recentRecords
        val initialSize = currentRecords.size
        val filtered = currentRecords.filter { r ->
            r.otpCode.isNotBlank() && r.otpCode != "null" && r.otpCode.length >= 3 &&
            r.phoneNumber.isNotBlank() && r.phoneNumber.replace("[^0-9]".toRegex(), "").length >= 7 &&
            r.timestamp in 1L..(now + 86400000L)
        }
        val removed = initialSize - filtered.size
        if (removed > 0) {
            val newTotal = maxOf(0, _state.value.totalOtpsAllTime - removed)
            saveRecords(filtered, newTotal)
            recalculateSummaries(filtered, newTotal)
        }
        return removed
    }

    /**
     * Export OTP History to RFC 4180 CSV file and launch Android share sheet
     */
    fun exportToCsv(context: Context) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val records = _state.value.recentRecords.filter {
            currentEmail.isEmpty() || it.userEmail.isBlank() || it.userEmail.equals(currentEmail, ignoreCase = true)
        }

        if (records.isEmpty()) {
            Toast.makeText(context, "এক্সপোর্ট করার মতো কোনো ওটিপি রেকর্ড নেই", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val sb = java.lang.StringBuilder()
            sb.append("ID,Date Time,Phone Number,Service,Platform,OTP Code,Rate (Tk),User Email\n")
            for (r in records) {
                val dateStr = sdf.format(Date(r.timestamp))
                val cleanPhone = r.phoneNumber.replace(",", " ")
                val cleanSrv = r.service.replace(",", " ")
                val cleanPlt = r.platform.replace(",", " ")
                sb.append("\"${r.id}\",\"$dateStr\",\"$cleanPhone\",\"$cleanSrv\",\"$cleanPlt\",\"${r.otpCode}\",${r.rate},\"${r.userEmail}\"\n")
            }

            val fileName = "OTP_History_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
            val cacheDir = context.cacheDir
            val file = java.io.File(cacheDir, fileName)
            file.writeText(sb.toString(), Charsets.UTF_8)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "OTP History Export ($fileName)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Export OTP History CSV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "এক্সপোর্ট করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Copy formatted plain text summary of recent OTPs to clipboard
     */
    fun copyRecentOtpsToClipboard(context: Context) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val records = _state.value.recentRecords.filter {
            currentEmail.isEmpty() || it.userEmail.isBlank() || it.userEmail.equals(currentEmail, ignoreCase = true)
        }.take(25)

        if (records.isEmpty()) {
            Toast.makeText(context, "কপি করার মতো কোনো ওটিপি রেকর্ড নেই", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("HH:mm, dd MMM", Locale.US)
        val sb = java.lang.StringBuilder()
        sb.append("📊 ওটিপি হিস্টোরি সামারি (মোট: ${records.size}টি)\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        records.forEachIndexed { idx, r ->
            sb.append("${idx + 1}. [${r.service}] কোড: ${r.otpCode}\n")
            sb.append("   নম্বর: ${r.phoneNumber} | ৳${String.format(Locale.US, "%.2f", r.rate)} | ${sdf.format(Date(r.timestamp))}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        ClipboardHelper.copyToClipboard(context, sb.toString(), "OTP Summary")
        Toast.makeText(context, "✓ ওটিপি হিস্টোরি টেক্সট কপি হয়েছে", Toast.LENGTH_SHORT).show()
    }

    /**
     * Clear all OTP history on demand
     */
    fun clearAllOtpHistory(): Int {
        val count = _state.value.recentRecords.size
        saveRecords(emptyList(), 0)
        recalculateSummaries(emptyList(), 0)
        return count
    }
}
