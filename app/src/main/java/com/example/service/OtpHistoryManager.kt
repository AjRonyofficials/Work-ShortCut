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

    private val _adminGlobalRecords = MutableStateFlow<List<OtpHistoryRecord>>(emptyList())
    val adminGlobalRecords: StateFlow<List<OtpHistoryRecord>> = _adminGlobalRecords.asStateFlow()

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

    private fun getUserCycleStartKey(email: String): String {
        val clean = email.trim().lowercase()
        return if (clean.isNotEmpty()) "${KEY_MONTHLY_CYCLE_START}_$clean" else KEY_MONTHLY_CYCLE_START
    }

    private fun isPhoneDigitsMatch(a: String, b: String): Boolean {
        val da = a.replace("[^0-9]".toRegex(), "")
        val db = b.replace("[^0-9]".toRegex(), "")
        if (da.length < 7 || db.length < 7) return false
        return da == db || da.endsWith(db) || db.endsWith(da)
    }

    /**
     * Strictly verifies that the arrived OTP phone number was actually taken/claimed by [userEmail].
     */
    fun isNumberTakenByUser(phoneNumber: String, userEmail: String): Boolean {
        val cleanEmail = userEmail.trim().lowercase()
        if (cleanEmail.isBlank()) return false

        // 1. Check UnixSmsManager active numbers for this user
        val unixActiveMatch = UnixSmsManager.state.value.activeNumbers.any {
            isPhoneDigitsMatch(it.number, phoneNumber) &&
                (it.userEmail.isBlank() || it.userEmail.equals(cleanEmail, ignoreCase = true))
        }
        if (unixActiveMatch) return true

        // 2. Check VirtualNumberManager (Kop 1 / Zenex) provisioned numbers for this user
        val zenexMatch = VirtualNumberManager.state.value.provisionedNumbers.any {
            isPhoneDigitsMatch(it.number, phoneNumber) &&
                (it.userEmail.isBlank() || it.userEmail.equals(cleanEmail, ignoreCase = true))
        }
        if (zenexMatch) return true

        // 3. Check KopEngine2Manager provisioned numbers for this user
        val kop2Match = KopEngine2Manager.state.value.provisionedNumbers.any {
            isPhoneDigitsMatch(it.number, phoneNumber) &&
                (it.userEmail.isBlank() || it.userEmail.equals(cleanEmail, ignoreCase = true))
        }
        if (kop2Match) return true

        // 4. Check PremiumNumberV2Manager active numbers for this user
        val v2Match = PremiumNumberV2Manager.state.value.activeNumbers.any {
            isPhoneDigitsMatch(it.number, phoneNumber) &&
                (it.userEmail.isBlank() || it.userEmail.equals(cleanEmail, ignoreCase = true))
        }
        if (v2Match) return true

        return false
    }

    suspend fun syncFromCloud(context: Context? = null) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val isAdmin = AuthManager.isAdmin()
        if (currentEmail.isBlank()) return

        // 1. Sync strictly ONLY the logged-in user's own OTP history for their Profile & Wallet
        try {
            val encEmail = java.net.URLEncoder.encode(currentEmail, "UTF-8")
            val url = "$SUPABASE_BASE_URL/otp_history?user_email=eq.$encEmail&order=created_at.desc&limit=1000"

            val req = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: "[]"
                    val arr = JSONArray(bodyStr)
                    val cloudList = mutableListOf<OtpHistoryRecord>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val recUser = o.optString("user_email", "").trim().lowercase()

                        // Strict Per-User Isolation: Discard any record not belonging to currentEmail
                        if (recUser != currentEmail) {
                            continue
                        }

                        val phone = o.optString("phone_number", "").trim()
                        val code = o.optString("otp_code", "").trim()

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
                                userEmail = currentEmail
                            )
                        )
                    }

                    // Filter local records strictly for currentEmail only (discard any leaked records from other users)
                    val existingOwn = _state.value.recentRecords.filter {
                        it.userEmail.equals(currentEmail, ignoreCase = true)
                    }
                    val combined = (cloudList + existingOwn)
                        .distinctBy { "${it.phoneNumber}_${it.otpCode}" }
                        .filterNot { it.phoneNumber.startsWith("+120255501") || it.phoneNumber.startsWith("+2376991122") }
                        .sortedByDescending { it.timestamp }
                        .take(2000)

                    val savedTotal = prefs?.getInt(getUserTotalKey(currentEmail), combined.size) ?: combined.size
                    val total = maxOf(savedTotal, combined.size, _state.value.totalOtpsAllTime)

                    val cycleStart = _state.value.monthlyCycleStartEpoch.let {
                        if (it == 0L) System.currentTimeMillis() else it
                    }
                    recalculateSummaries(combined, total, cycleStart)
                    saveRecords(combined, total)
                    WithdrawalManager.refreshBalances()
                }
            }
        } catch (_: Exception) {}

        // 2. If logged-in user is Admin, separately sync all users' recent OTP stream for Admin Panel ONLY
        if (isAdmin) {
            try {
                val adminUrl = "$SUPABASE_BASE_URL/otp_history?select=*&order=created_at.desc&limit=300"
                val adminReq = Request.Builder()
                    .url(adminUrl)
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .get()
                    .build()
                httpClient.newCall(adminReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: "[]"
                        val arr = JSONArray(bodyStr)
                        val globalList = mutableListOf<OtpHistoryRecord>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val recUser = o.optString("user_email", "").trim().lowercase()
                            val phone = o.optString("phone_number", "").trim()
                            val code = o.optString("otp_code", "").trim()
                            if (code.isBlank() || code == "null" || code.length < 3) continue
                            if (phone.isBlank() || phone.startsWith("+120255501") || phone.startsWith("+2376991122")) continue
                            val idStr = o.optString("id", java.util.UUID.randomUUID().toString())
                            val srv = o.optString("service", "SMS")
                            val plt = o.optString("platform", "Unix SMS")
                            val rateVal = o.optDouble("earned_amount", 0.50)
                            val ts = parseCreatedAt(o.optString("created_at", ""))
                            globalList.add(
                                OtpHistoryRecord(
                                    id = idStr,
                                    phoneNumber = phone,
                                    otpCode = code,
                                    service = srv,
                                    platform = plt,
                                    rate = if (rateVal < 0.05) 0.50 else rateVal,
                                    timestamp = ts,
                                    dateKey = dateFormat.format(Date(ts)),
                                    userEmail = recUser
                                )
                            )
                        }
                        _adminGlobalRecords.value = globalList.sortedByDescending { it.timestamp }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun setAdminAssignedOtps(totalOtps: Int, today: Int, last7d: Int, last30d: Int) {
        val newTotal = maxOf(0, totalOtps)
        val newToday = maxOf(0, today)
        val new7d = maxOf(0, last7d)
        val new30d = maxOf(0, last30d)

        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        if (currentEmail.isNotEmpty()) {
            val totalKey = getUserTotalKey(currentEmail)
            prefs?.edit()
                ?.putBoolean("admin_override_active_$currentEmail", true)
                ?.putInt(totalKey, newTotal)
                ?.putInt("admin_override_today_$currentEmail", newToday)
                ?.putInt("admin_override_7d_$currentEmail", new7d)
                ?.putInt("admin_override_30d_$currentEmail", new30d)
                ?.apply()
        }

        _state.update {
            it.copy(
                totalOtpsAllTime = newTotal,
                todayOtps = newToday,
                last7DaysOtps = new7d,
                last30DaysOtps = new30d
            )
        }
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

    suspend fun deleteUserOtpRecord(recordId: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (recordId.isBlank()) return@withContext false
        try {
            val url = "$SUPABASE_BASE_URL/otp_history?id=eq.$recordId"
            val req = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .delete()
                .build()
            val resp = httpClient.newCall(req).execute()
            val ok = resp.isSuccessful
            resp.close()
            ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearAllUserOtpRecords(userEmail: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val cleanEmail = userEmail.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext false
        try {
            val encEmail = java.net.URLEncoder.encode(cleanEmail, "UTF-8")
            val url = "$SUPABASE_BASE_URL/otp_history?user_email=eq.$encEmail"
            val req = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .delete()
                .build()
            val resp = httpClient.newCall(req).execute()
            val ok = resp.isSuccessful
            resp.close()
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun triggerLiveCloudSync() {
        scope.launch {
            syncFromCloud()
        }
    }

    fun onUserLoggedIn(email: String, context: Context? = null) {
        val clean = email.trim().lowercase()
        _state.value = OtpHistoryState()
        loadHistoryForUser(clean)
        scope.launch {
            syncFromCloud(context)
        }
    }

    fun onUserLoggedOut() {
        _state.value = OtpHistoryState()
        _adminGlobalRecords.value = emptyList()
    }

    fun wipeAllHistory(context: Context? = null) {
        _state.value = OtpHistoryState()
        _adminGlobalRecords.value = emptyList()
        prefs?.edit()
            ?.remove(KEY_RECORDS_JSON)
            ?.remove(KEY_TOTAL_COUNT)
            ?.remove(KEY_MONTHLY_CYCLE_START)
            ?.apply()
        prefs?.all?.keys?.filter {
            it.startsWith("otp_records_json") ||
            it.startsWith("total_otp_all_time") ||
            it.startsWith("admin_override_") ||
            it.startsWith("monthly_cycle_start_time")
        }?.forEach { k ->
            prefs?.edit()?.remove(k)?.apply()
        }
    }

    fun wipeAllCloudOtpRecords(onComplete: ((Boolean) -> Unit)? = null) {
        wipeAllHistory()
        scope.launch {
            try {
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/otp_history?id=gt.0")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                val resp = httpClient.newCall(req).execute()
                val ok = resp.isSuccessful
                resp.close()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(ok)
                }
            } catch (_: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    private fun loadHistory() {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        loadHistoryForUser(currentEmail)
    }

    fun loadHistoryForUser(email: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) {
            _state.value = OtpHistoryState()
            return
        }
        val key = getUserRecordsKey(cleanEmail)
        val totalKey = getUserTotalKey(cleanEmail)
        val cycleKey = getUserCycleStartKey(cleanEmail)
        val json = prefs?.getString(key, "[]") ?: "[]"
        val list = mutableListOf<OtpHistoryRecord>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val recUser = o.optString("userEmail", "").trim().lowercase()
                // Strict per-user check: only load records explicitly belonging to cleanEmail
                if (recUser != cleanEmail) {
                    continue
                }
                val phone = o.optString("phoneNumber", "").trim()
                val code = o.optString("otpCode", "").trim()

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
                            if (r < 0.05) 0.50 else r
                        },
                        timestamp = ts,
                        dateKey = o.optString("dateKey", dateFormat.format(Date(ts))),
                        userEmail = cleanEmail
                    )
                )
            }
        } catch (_: Exception) {}

        val total = prefs?.getInt(totalKey, list.size) ?: list.size

        var cycleStart = prefs?.getLong(cycleKey, 0L) ?: 0L
        val now = System.currentTimeMillis()
        if (cycleStart == 0L || now - cycleStart >= ONE_MONTH_MS) {
            cycleStart = now
            prefs?.edit()?.putLong(cycleKey, cycleStart)?.apply()
        }

        val cutoff = now - ONE_MONTH_MS
        val validRecords = list.filter { it.timestamp >= cutoff }

        recalculateSummaries(validRecords, total, cycleStart)
    }

    private fun saveRecords(list: List<OtpHistoryRecord>, totalAllTime: Int) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        if (currentEmail.isBlank()) return
        val key = getUserRecordsKey(currentEmail)
        val totalKey = getUserTotalKey(currentEmail)

        val now = System.currentTimeMillis()
        val cutoff = now - ONE_MONTH_MS
        val validRecords = list.filter {
            it.timestamp >= cutoff &&
            !it.phoneNumber.startsWith("+120255501") &&
            it.userEmail.equals(currentEmail, ignoreCase = true)
        }
        val trimmed = validRecords.take(2000)

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
            o.put("userEmail", currentEmail)
            arr.put(o)
        }
        prefs?.edit()
            ?.putString(key, arr.toString())
            ?.putInt(totalKey, totalAllTime)
            ?.apply()
    }

    /**
     * Record arrived OTP from Unix SMS, Zenex, Kop Engine 2, or Premium V2
     * Strictly counts ONLY if the number was taken by the currently logged-in user!
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

        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        if (currentEmail.isBlank() || currentEmail == "user" || currentEmail == "user account") return

        // Strictly verify that this phone number was taken/claimed by currentEmail
        if (!isNumberTakenByUser(phoneNumber, currentEmail)) {
            return
        }

        val now = System.currentTimeMillis()

        // Deduplication: prevent counting duplicate OTP code on same phone within 5 minutes
        val isDuplicate = _state.value.recentRecords.any {
            isPhoneDigitsMatch(it.phoneNumber, phoneNumber) && it.otpCode == otpCode && Math.abs(it.timestamp - now) < 300_000L
        }
        if (isDuplicate) return

        val effectiveRate = if (rate < 0.05) 0.50 else rate

        val record = OtpHistoryRecord(
            phoneNumber = phoneNumber,
            otpCode = otpCode,
            service = service,
            platform = platform,
            rate = effectiveRate,
            timestamp = now,
            dateKey = dateFormat.format(Date(now)),
            userEmail = currentEmail
        )

        val ownExisting = _state.value.recentRecords.filter {
            it.userEmail.equals(currentEmail, ignoreCase = true)
        }
        val updatedRecords = listOf(record) + ownExisting
        val newTotal = _state.value.totalOtpsAllTime + 1

        // If Admin had previously overridden OTP counts for this user, increment those overrides by +1
        val hasAdminOverride = prefs?.getBoolean("admin_override_active_$currentEmail", false) ?: false
        if (hasAdminOverride) {
            val prevToday = prefs?.getInt("admin_override_today_$currentEmail", _state.value.todayOtps) ?: _state.value.todayOtps
            val prev7d = prefs?.getInt("admin_override_7d_$currentEmail", _state.value.last7DaysOtps) ?: _state.value.last7DaysOtps
            val prev30d = prefs?.getInt("admin_override_30d_$currentEmail", _state.value.last30DaysOtps) ?: _state.value.last30DaysOtps
            prefs?.edit()
                ?.putInt(getUserTotalKey(currentEmail), newTotal)
                ?.putInt("admin_override_today_$currentEmail", prevToday + 1)
                ?.putInt("admin_override_7d_$currentEmail", prev7d + 1)
                ?.putInt("admin_override_30d_$currentEmail", prev30d + 1)
                ?.apply()
        }

        val cycleStart = _state.value.monthlyCycleStartEpoch.let {
            if (it == 0L) now else it
        }

        recalculateSummaries(updatedRecords, newTotal, cycleStart)
        saveRecords(updatedRecords, newTotal)
        WithdrawalManager.creditOtpReward(effectiveRate)
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
                    fullMessage = "$service ওটিপি: $otpCode (৳${String.format(Locale.US, "%.2f", effectiveRate)})"
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
                    put("earned_amount", effectiveRate)
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
                httpClient.newCall(req).execute().close()
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
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val ownRecords = if (currentEmail.isNotEmpty()) {
            records.filter { it.userEmail.equals(currentEmail, ignoreCase = true) }
        } else {
            emptyList()
        }

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
        fun isUnix(p: String) = (p.contains("Unix", ignoreCase = true) || p.contains("Lamix", ignoreCase = true) || p.contains("Hadi", ignoreCase = true) || p.contains("Premium", ignoreCase = true)) && !p.contains("V2", ignoreCase = true)
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
            val matched = ownRecords.filter { it.dateKey == dKey }
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
        val liveUnixToday = UnixSmsManager.state.value.activeNumbers.count { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
        val liveZenexToday = VirtualNumberManager.state.value.provisionedNumbers.count { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
        val liveKop2Today = KopEngine2Manager.state.value.provisionedNumbers.count { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
        val liveV2Today = PremiumNumberV2Manager.state.value.activeNumbers.count { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }

        val todayUnix = maxOf(todaySummary?.unixCount ?: 0, liveUnixToday, UnixSmsManager.state.value.todayOtpCount)
        val todayZenex = maxOf(todaySummary?.zenexCount ?: 0, liveZenexToday, VirtualNumberManager.state.value.todayOtpCount)
        val todayKop2 = maxOf(todaySummary?.kop2Count ?: 0, liveKop2Today, KopEngine2Manager.state.value.todayOtpCount)
        val todayV2 = maxOf(todaySummary?.v2Count ?: 0, liveV2Today, PremiumNumberV2Manager.state.value.todayOtpCount)
        val computedToday = maxOf(todaySummary?.totalCount ?: 0, todayUnix + todayZenex + todayKop2 + todayV2)

        val unixTotal = maxOf(ownRecords.count { isUnix(it.platform) }, todayUnix)
        val kop2Total = maxOf(ownRecords.count { isKop2(it.platform) }, todayKop2)
        val v2Total = maxOf(ownRecords.count { isV2(it.platform) }, todayV2)
        val zenexTotal = maxOf(ownRecords.count { !isUnix(it.platform) && !isKop2(it.platform) && !isV2(it.platform) }, todayZenex)

        val computed7Days = maxOf(days7List.sumOf { it.totalCount }, computedToday)
        val computed30Days = maxOf(days30List.sumOf { it.totalCount }, computed7Days)

        val hasAdminOverride = currentEmail.isNotEmpty() && (prefs?.getBoolean("admin_override_active_$currentEmail", false) ?: false)
        val finalToday = if (hasAdminOverride) maxOf(prefs?.getInt("admin_override_today_$currentEmail", computedToday) ?: computedToday, computedToday) else computedToday
        val final7Days = if (hasAdminOverride) maxOf(prefs?.getInt("admin_override_7d_$currentEmail", computed7Days) ?: computed7Days, computed7Days, finalToday) else maxOf(computed7Days, finalToday)
        val final30Days = if (hasAdminOverride) maxOf(prefs?.getInt("admin_override_30d_$currentEmail", computed30Days) ?: computed30Days, computed30Days, final7Days) else maxOf(computed30Days, final7Days)
        val baseTotal = if (hasAdminOverride) (prefs?.getInt(getUserTotalKey(currentEmail), totalAllTime) ?: totalAllTime) else totalAllTime
        val finalTotal = maxOf(baseTotal, totalAllTime, ownRecords.size, final30Days, finalToday, unixTotal + zenexTotal + kop2Total + v2Total)

        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)
        val sixtyDaysAgo = now - (60L * 24 * 60 * 60 * 1000L)
        val ninetyDaysAgo = now - (90L * 24 * 60 * 60 * 1000L)

        val lastMonthCount = ownRecords.count { it.timestamp in (sixtyDaysAgo until thirtyDaysAgo) }
        val twoMonthsAgoCount = ownRecords.count { it.timestamp in (ninetyDaysAgo until sixtyDaysAgo) }

        val m1Cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val m2Cal = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }
        val monthNameFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val m1Label = monthNameFormat.format(m1Cal.time)
        val m2Label = monthNameFormat.format(m2Cal.time)

        _state.update {
            it.copy(
                totalOtpsAllTime = finalTotal,
                unixTotalAllTime = unixTotal,
                zenexTotalAllTime = zenexTotal,
                kop2TotalAllTime = kop2Total,
                v2TotalAllTime = v2Total,
                todayOtps = finalToday,
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
                last7DaysOtps = final7Days,
                last30DaysOtps = final30Days,
                lastMonthOtps = lastMonthCount,
                twoMonthsAgoOtps = twoMonthsAgoCount,
                lastMonthLabel = m1Label,
                twoMonthsAgoLabel = m2Label,
                recentRecords = ownRecords,
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
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val records = _state.value.recentRecords.filter {
            currentEmail.isNotEmpty() && it.userEmail.equals(currentEmail, ignoreCase = true)
        }
        if (records.isEmpty()) {
            Toast.makeText(context, "কোনো ওটিপি রেকর্ড পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val timeNowStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sb = StringBuilder()
        records.forEach { r ->
            val cleanPhone = r.phoneNumber.trim()
            val cleanOtp = r.otpCode.trim()
            if (cleanPhone.isNotBlank() && cleanOtp.isNotBlank()) {
                sb.append("$cleanPhone | $cleanOtp\n")
            }
        }

        val exportText = sb.toString().trimEnd()

        // 1. Copy to clipboard
        ClipboardHelper.copyToClipboard(context, exportText, "OTP History")

        // 2. Open Android Share / Save Sheet
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, exportText)
                putExtra(Intent.EXTRA_SUBJECT, "OTP History ($timeNowStr)")
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val shareIntent = Intent.createChooser(sendIntent, "Download / Share OTP History").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
            Toast.makeText(context, "✓ মোট ${records.size}টি নাম্বার ও OTP ডাউনলোড হয়েছে!", Toast.LENGTH_LONG).show()
            VibrationHelper.vibrateSuccess(context)
        } catch (_: Exception) {
            Toast.makeText(context, "✓ OTP ক্লিপবোর্ডে কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreUserHistoryIfEmpty(totalOtps: Int, todayOtps: Int, last7d: Int, last30d: Int) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val mergedTotal = maxOf(_state.value.totalOtpsAllTime, totalOtps, todayOtps, last7d, last30d)
        val mergedToday = maxOf(_state.value.todayOtps, todayOtps)
        val merged7d = maxOf(_state.value.last7DaysOtps, last7d, mergedToday)
        val merged30d = maxOf(_state.value.last30DaysOtps, last30d, merged7d)
        _state.update {
            it.copy(
                totalOtpsAllTime = mergedTotal,
                todayOtps = mergedToday,
                last7DaysOtps = merged7d,
                last30DaysOtps = merged30d
            )
        }
        if (currentEmail.isNotEmpty()) {
            prefs?.edit()?.putInt(getUserTotalKey(currentEmail), mergedTotal)?.apply()
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
            currentEmail.isNotEmpty() && it.userEmail.equals(currentEmail, ignoreCase = true)
        }

        if (records.isEmpty()) {
            Toast.makeText(context, "এক্সপোর্ট করার মতো কোনো ওটিপি রেকর্ড নেই", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val sb = java.lang.StringBuilder()
            sb.append("Phone Number,OTP Code\n")
            for (r in records) {
                val cleanPhone = r.phoneNumber.trim().replace(",", "")
                val cleanOtp = r.otpCode.trim().replace(",", "")
                if (cleanPhone.isNotBlank() && cleanOtp.isNotBlank()) {
                    sb.append("\"$cleanPhone\",\"$cleanOtp\"\n")
                }
            }

            val fileName = "OTP_Numbers_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
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
                putExtra(Intent.EXTRA_SUBJECT, "OTP Numbers Export ($fileName)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Export OTP CSV")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "এক্সপোর্ট করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Copy strictly phone number and OTP code to clipboard
     */
    fun copyRecentOtpsToClipboard(context: Context) {
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()
        val records = _state.value.recentRecords.filter {
            currentEmail.isNotEmpty() && it.userEmail.equals(currentEmail, ignoreCase = true)
        }

        if (records.isEmpty()) {
            Toast.makeText(context, "কপি করার মতো কোনো ওটিপি রেকর্ড নেই", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = java.lang.StringBuilder()
        records.forEach { r ->
            val cleanPhone = r.phoneNumber.trim()
            val cleanOtp = r.otpCode.trim()
            if (cleanPhone.isNotBlank() && cleanOtp.isNotBlank()) {
                sb.append("$cleanPhone | $cleanOtp\n")
            }
        }
        val copyText = sb.toString().trimEnd()
        ClipboardHelper.copyToClipboard(context, copyText, "OTP Numbers")
        Toast.makeText(context, "✓ মোট ${records.size}টি নাম্বার ও OTP কপি হয়েছে", Toast.LENGTH_SHORT).show()
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

    private fun getPanelResetKey(panelKey: String): String {
        val email = AuthManager.currentEmail.value.trim().lowercase()
        val norm = normalizePanelKey(panelKey)
        return if (email.isNotEmpty()) "panel_24h_reset_${norm}_$email" else "panel_24h_reset_$norm"
    }

    fun normalizePanelKey(panelName: String): String {
        val p = panelName.trim().lowercase(Locale.US)
        return when {
            p.contains("v2") -> "PREMIUM_V2"
            p.contains("kop engine 2") || p.contains("kop 2") || p.contains("kop2") -> "KOP_2"
            p.contains("kop engine") || p.contains("kop 1") || p.contains("zenex") -> "KOP_1"
            p.contains("unix") || p.contains("lamix") || p.contains("hadi") || p.contains("premium") -> "UNIX_SMS"
            else -> "ALL"
        }
    }

    fun getPanelLastResetTime(panelName: String): Long {
        val key = getPanelResetKey(panelName)
        return prefs?.getLong(key, 0L) ?: 0L
    }

    fun markPanel24hReset(panelName: String) {
        val now = System.currentTimeMillis()
        val norm = normalizePanelKey(panelName)
        if (norm == "ALL") {
            listOf("UNIX_SMS", "KOP_1", "KOP_2", "PREMIUM_V2", "ALL").forEach { k ->
                prefs?.edit()?.putLong(getPanelResetKey(k), now)?.apply()
            }
        } else {
            prefs?.edit()?.putLong(getPanelResetKey(norm), now)?.apply()
        }
    }

    /**
     * Returns all OTPs received within the last 24 hours (and after any manual reset for that panel).
     */
    fun get24hOtpItemsForPanel(panelName: String): List<com.example.util.ExportOtpItem> {
        val now = System.currentTimeMillis()
        val cutoff24h = now - 24L * 60L * 60L * 1000L
        val norm = normalizePanelKey(panelName)
        val resetCutoff = if (norm == "ALL") {
            getPanelLastResetTime("ALL")
        } else {
            maxOf(getPanelLastResetTime(norm), getPanelLastResetTime("ALL"))
        }
        val effectiveCutoff = maxOf(cutoff24h, resetCutoff)
        val currentEmail = AuthManager.currentEmail.value.trim().lowercase()

        return _state.value.recentRecords
            .filter { r ->
                r.timestamp >= effectiveCutoff &&
                r.otpCode.isNotBlank() &&
                r.phoneNumber.isNotBlank() &&
                (currentEmail.isNotEmpty() && r.userEmail.equals(currentEmail, ignoreCase = true)) &&
                (norm == "ALL" || normalizePanelKey(r.platform) == norm)
            }
            .map { r ->
                val cInfo = com.example.util.AllCountriesProvider.detectCountry(r.phoneNumber)
                com.example.util.ExportOtpItem(
                    phoneNumber = r.phoneNumber,
                    country = cInfo?.name ?: "Global",
                    iso = (cInfo?.code ?: "").uppercase(),
                    flag = cInfo?.flag ?: "🌐",
                    serviceOrRange = "${r.platform} • ${r.service}",
                    otpCode = r.otpCode,
                    otpMessage = null,
                    timestamp = r.timestamp,
                    rate = r.rate
                )
            }
    }
}
