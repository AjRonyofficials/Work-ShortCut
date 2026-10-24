package com.example.service

import android.content.Context
import android.content.SharedPreferences
import com.example.util.OtpNotificationHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.example.util.AppLifecycleTracker

data class WithdrawalRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userEmail: String,
    val method: String, // "Binance", "bKash", "Nagad", "Rocket"
    val accountNumber: String,
    val amount: Double,
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val requestTimestamp: Long = System.currentTimeMillis(),
    val processedTimestamp: Long? = null,
    val note: String = ""
)

data class WithdrawalLimits(
    val minBinance: Double = 20.0,
    val minBkash: Double = 50.0,
    val minNagad: Double = 50.0,
    val minRocket: Double = 50.0
)

data class UserPayoutAccounts(
    val binanceId: String = "",
    val bkashNumber: String = "",
    val nagadNumber: String = "",
    val rocketNumber: String = ""
)

data class WithdrawalState(
    val totalEarnedTk: Double = 0.0,
    val availableBalanceTk: Double = 0.0,
    val pendingWithdrawTk: Double = 0.0,
    val approvedWithdrawTk: Double = 0.0,
    val allRequests: List<WithdrawalRequest> = emptyList()
)

object WithdrawalManager {

    private const val PREFS_NAME = "work_shortcut_withdrawals_prefs"
    private const val KEY_REQUESTS_JSON = "withdrawals_requests_json"
    private const val KEY_BASE_CREDIT = "base_earned_credit_tk"
    private const val KEY_CREDIT_RESET_V2 = "credit_cleaned_v2"
    private const val KEY_MIN_BINANCE = "min_binance_limit_tk"
    private const val KEY_MIN_BKASH = "min_bkash_limit_tk"
    private const val KEY_MIN_NAGAD = "min_nagad_limit_tk"
    private const val KEY_MIN_ROCKET = "min_rocket_limit_tk"

    private const val SUPABASE_BASE_URL = "https://fbtqjpuclmnsiqehalow.supabase.co/rest/v1"
    private const val SUPABASE_KEY = "sb_publishable_zBqxIMDPy4yHEuAdJJUlxg_wSCRF9u5"

    const val MIN_BINANCE_TK = 20.0
    const val MIN_BKASH_TK = 50.0
    const val MIN_NAGAD_TK = 50.0
    const val MIN_ROCKET_TK = 50.0

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isPolling = false

    private var prefs: SharedPreferences? = null

    private val _limits = MutableStateFlow(WithdrawalLimits())
    val limits: StateFlow<WithdrawalLimits> = _limits.asStateFlow()

    private val _state = MutableStateFlow(WithdrawalState())
    val state: StateFlow<WithdrawalState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadData()
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
                    delay(4000L) // 4s live sync for instant updates
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

    suspend fun syncFromCloud(context: Context? = null) {
        try {
            val req = Request.Builder()
                .url("$SUPABASE_BASE_URL/withdraw_history?select=*&order=created_at.desc&limit=50")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val bodyStr = resp.body?.string() ?: return
                val arr = JSONArray(bodyStr)
                val cloudRequests = mutableListOf<WithdrawalRequest>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val idStr = o.optString("id", "")
                    val email = o.optString("user_email", "")
                    val method = o.optString("payment_method", "bKash")
                    val acc = o.optString("account_number", "")
                    val amt = o.optDouble("amount", 0.0)
                    val status = o.optString("status", "PENDING")
                    val ts = parseCreatedAt(o.optString("created_at", ""))
                    cloudRequests.add(
                        WithdrawalRequest(
                            id = idStr,
                            userEmail = email,
                            method = method,
                            accountNumber = acc,
                            amount = amt,
                            status = status,
                            requestTimestamp = ts,
                            note = ""
                        )
                    )
                }
                if (cloudRequests.isNotEmpty()) {
                    val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
                    recomputeBalances(cloudRequests, baseCredit)
                    saveRequests(cloudRequests)
                }
            }
        } catch (_: Exception) {}
    }

    fun triggerLiveCloudSync() {
        scope.launch {
            syncFromCloud()
        }
    }

    private fun loadData() {
        val json = prefs?.getString(KEY_REQUESTS_JSON, "[]") ?: "[]"
        val requests = mutableListOf<WithdrawalRequest>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                requests.add(
                    WithdrawalRequest(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        userEmail = o.optString("userEmail", ""),
                        method = o.optString("method", "bKash"),
                        accountNumber = o.optString("accountNumber", ""),
                        amount = o.optDouble("amount", 0.0),
                        status = o.optString("status", "PENDING"),
                        requestTimestamp = o.optLong("requestTimestamp", System.currentTimeMillis()),
                        processedTimestamp = if (o.has("processedTimestamp") && !o.isNull("processedTimestamp")) o.getLong("processedTimestamp") else null,
                        note = o.optString("note", "")
                    )
                )
            }
        } catch (_: Exception) {}

        // Load dynamic withdrawal limits
        val minBinance = prefs?.getFloat(KEY_MIN_BINANCE, 20.0f)?.toDouble() ?: 20.0
        val minBkash = prefs?.getFloat(KEY_MIN_BKASH, 50.0f)?.toDouble() ?: 50.0
        val minNagad = prefs?.getFloat(KEY_MIN_NAGAD, 50.0f)?.toDouble() ?: 50.0
        val minRocket = prefs?.getFloat(KEY_MIN_ROCKET, 50.0f)?.toDouble() ?: 50.0
        _limits.value = WithdrawalLimits(minBinance, minBkash, minNagad, minRocket)

        // One-time cleanup of legacy test 60 Tk credit
        val hasReset = prefs?.getBoolean(KEY_CREDIT_RESET_V2, false) ?: false
        if (!hasReset) {
            prefs?.edit()?.putFloat(KEY_BASE_CREDIT, 0.0f)?.putBoolean(KEY_CREDIT_RESET_V2, true)?.apply()
        }

        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0

        recomputeBalances(requests, baseCredit)
    }

    fun refreshBalances() {
        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
        recomputeBalances(_state.value.allRequests, baseCredit)
    }

    private fun recomputeBalances(requests: List<WithdrawalRequest>, baseCredit: Double) {
        val records = OtpHistoryManager.state.value.recentRecords
        val currentUnixRate = UnixSmsManager.state.value.otpRatePerSms
        val currentZenexRate = VirtualNumberManager.zenexOtpRate.value
        val currentKop2Rate = KopEngine2Manager.kop2OtpRate.value
        val currentV2Rate = PremiumNumberV2Manager.otpRate.value

        val otpEarnedTk = if (records.isNotEmpty()) {
            records.sumOf { rec ->
                val rate = if (rec.rate > 0.0) rec.rate else {
                    when {
                        rec.platform.contains("V2", ignoreCase = true) || rec.platform.contains("Premium Number V2", ignoreCase = true) || rec.platform.contains("Premium V2", ignoreCase = true) -> currentV2Rate
                        rec.platform.contains("Unix", ignoreCase = true) || rec.platform.contains("Lamix", ignoreCase = true) || (rec.platform.contains("Premium", ignoreCase = true) && !rec.platform.contains("V2", ignoreCase = true)) -> currentUnixRate
                        rec.platform.contains("Kop Engine 2", ignoreCase = true) || rec.platform.contains("Kop2", ignoreCase = true) || rec.platform.contains("Kop 2", ignoreCase = true) -> currentKop2Rate
                        else -> currentZenexRate
                    }
                }
                rate
            }
        } else {
            val unixCount = OtpHistoryManager.state.value.unixTotalAllTime
            val zenexCount = OtpHistoryManager.state.value.zenexTotalAllTime
            val kop2Count = OtpHistoryManager.state.value.kop2TotalAllTime
            val v2Count = OtpHistoryManager.state.value.v2TotalAllTime
            (unixCount * currentUnixRate) + (zenexCount * currentZenexRate) + (kop2Count * currentKop2Rate) + (v2Count * currentV2Rate)
        }

        val totalEarned = baseCredit + otpEarnedTk
        val pending = requests.filter { it.status == "PENDING" }.sumOf { it.amount }
        val approved = requests.filter { it.status == "APPROVED" }.sumOf { it.amount }
        val available = maxOf(0.0, totalEarned - pending - approved)

        _state.update {
            it.copy(
                totalEarnedTk = totalEarned,
                availableBalanceTk = available,
                pendingWithdrawTk = pending,
                approvedWithdrawTk = approved,
                allRequests = requests
            )
        }
    }

    private fun saveRequests(list: List<WithdrawalRequest>) {
        val arr = JSONArray()
        val trimmed = list.take(50)
        for (r in trimmed) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("userEmail", r.userEmail)
            o.put("method", r.method)
            o.put("accountNumber", r.accountNumber)
            o.put("amount", r.amount)
            o.put("status", r.status)
            o.put("requestTimestamp", r.requestTimestamp)
            if (r.processedTimestamp != null) {
                o.put("processedTimestamp", r.processedTimestamp)
            }
            o.put("note", r.note)
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_REQUESTS_JSON, arr.toString())?.apply()
    }

    fun updateLimits(minBinance: Double, minBkash: Double, minNagad: Double, minRocket: Double) {
        val b = (Math.round(minBinance.coerceAtLeast(1.0) * 100.0) / 100.0)
        val bk = (Math.round(minBkash.coerceAtLeast(1.0) * 100.0) / 100.0)
        val n = (Math.round(minNagad.coerceAtLeast(1.0) * 100.0) / 100.0)
        val r = (Math.round(minRocket.coerceAtLeast(1.0) * 100.0) / 100.0)
        _limits.value = WithdrawalLimits(b, bk, n, r)
        prefs?.edit()
            ?.putFloat(KEY_MIN_BINANCE, b.toFloat())
            ?.putFloat(KEY_MIN_BKASH, bk.toFloat())
            ?.putFloat(KEY_MIN_NAGAD, n.toFloat())
            ?.putFloat(KEY_MIN_ROCKET, r.toFloat())
            ?.apply()
    }

    fun getSavedAccounts(email: String): UserPayoutAccounts {
        val safeKey = email.ifBlank { "default" }.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val b = prefs?.getString("payout_binance_$safeKey", "") ?: ""
        val bk = prefs?.getString("payout_bkash_$safeKey", "") ?: ""
        val n = prefs?.getString("payout_nagad_$safeKey", "") ?: ""
        val r = prefs?.getString("payout_rocket_$safeKey", "") ?: ""
        return UserPayoutAccounts(b, bk, n, r)
    }

    fun saveAccounts(email: String, binance: String, bkash: String, nagad: String, rocket: String) {
        val safeKey = email.ifBlank { "default" }.replace("[^a-zA-Z0-9]".toRegex(), "_")
        prefs?.edit()
            ?.putString("payout_binance_$safeKey", binance.trim())
            ?.putString("payout_bkash_$safeKey", bkash.trim())
            ?.putString("payout_nagad_$safeKey", nagad.trim())
            ?.putString("payout_rocket_$safeKey", rocket.trim())
            ?.apply()
    }

    fun getMinimumAmountForMethod(method: String): Double {
        val lim = _limits.value
        return when (method.trim().lowercase()) {
            "binance" -> lim.minBinance
            "bkash" -> lim.minBkash
            "nagad" -> lim.minNagad
            "rocket" -> lim.minRocket
            else -> lim.minBkash
        }
    }

    fun isWithdrawUnlocked(method: String): Boolean {
        val min = getMinimumAmountForMethod(method)
        return _state.value.availableBalanceTk >= min
    }

    fun submitWithdrawal(
        context: Context,
        userEmail: String,
        method: String,
        accountNumber: String,
        amount: Double
    ): Pair<Boolean, String> {
        val acc = accountNumber.trim()
        if (acc.length < 5) {
            return Pair(false, "সঠিক একাউন্ট নাম্বার / Binance Pay ID দিন")
        }

        val min = getMinimumAmountForMethod(method)
        if (amount < min) {
            return Pair(false, "$method এর জন্য সর্বনিম্ন উইথড্র ৳$min প্রয়োজন")
        }

        val currentAvail = _state.value.availableBalanceTk
        if (amount > currentAvail) {
            return Pair(false, "পর্যাপ্ত ব্যালেন্স নেই! আপনার ব্যালেন্স: ৳${String.format(java.util.Locale.US, "%.2f", currentAvail)}")
        }

        val newRequest = WithdrawalRequest(
            userEmail = userEmail.ifBlank { "User" },
            method = method,
            accountNumber = acc,
            amount = amount,
            status = "PENDING",
            requestTimestamp = System.currentTimeMillis()
        )

        val updated = listOf(newRequest) + _state.value.allRequests
        saveRequests(updated)
        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
        recomputeBalances(updated, baseCredit)

        // Real-time Push to Supabase withdraw_history table
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("user_email", newRequest.userEmail)
                    put("amount", newRequest.amount)
                    put("payment_method", newRequest.method)
                    put("account_number", newRequest.accountNumber)
                    put("status", "PENDING")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/withdraw_history")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .post(body)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        val arr = JSONArray(bodyStr)
                        if (arr.length() > 0) {
                            val serverId = arr.getJSONObject(0).optString("id", newRequest.id)
                            _state.update { st ->
                                val remapped = st.allRequests.map {
                                    if (it.id == newRequest.id) it.copy(id = serverId) else it
                                }
                                st.copy(allRequests = remapped)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WithdrawalManager", "Error pushing withdrawal to Supabase: ${e.message}")
            }
        }

        VibrationHelper.vibrateSuccess(context)
        return Pair(true, "✓ উইথড্র রিকোয়েস্ট সফল হয়েছে! এডমিনের অনুমোদনের অপেক্ষায় আছে।")
    }

    fun approveRequest(context: Context, requestId: String, note: String = ""): Boolean {
        val currentList = _state.value.allRequests.toMutableList()
        val index = currentList.indexOfFirst { it.id == requestId }
        if (index == -1) return false

        val req = currentList[index]
        val updatedReq = req.copy(
            status = "APPROVED",
            processedTimestamp = System.currentTimeMillis(),
            note = note.ifBlank { "Approved by Admin" }
        )
        currentList[index] = updatedReq
        saveRequests(currentList)

        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
        recomputeBalances(currentList, baseCredit)

        // Real-time PATCH to Supabase withdraw_history table
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("status", "APPROVED")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val patchReq = Request.Builder()
                    .url("$SUPABASE_BASE_URL/withdraw_history?id=eq.$requestId")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                httpClient.newCall(patchReq).execute()
            } catch (_: Exception) {}
        }

        // Dispatch real Android notification to user
        OtpNotificationHelper.showWithdrawNotification(
            context = context,
            title = "🎉 উইথড্র সফল হয়েছে!",
            message = "আপনার ৳${String.format(java.util.Locale.US, "%.2f", req.amount)} (${req.method}: ${req.accountNumber}) এ সফলভাবে পাঠানো হয়েছে।"
        )
        VibrationHelper.vibrateSuccess(context)
        return true
    }

    fun rejectRequest(context: Context, requestId: String, reason: String = ""): Boolean {
        val currentList = _state.value.allRequests.toMutableList()
        val index = currentList.indexOfFirst { it.id == requestId }
        if (index == -1) return false

        val req = currentList[index]
        val updatedReq = req.copy(
            status = "REJECTED",
            processedTimestamp = System.currentTimeMillis(),
            note = reason.ifBlank { "Rejected by Admin" }
        )
        currentList[index] = updatedReq
        saveRequests(currentList)

        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
        recomputeBalances(currentList, baseCredit)

        // Real-time PATCH to Supabase withdraw_history table
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("status", "REJECTED")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val patchReq = Request.Builder()
                    .url("$SUPABASE_BASE_URL/withdraw_history?id=eq.$requestId")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                httpClient.newCall(patchReq).execute()
            } catch (_: Exception) {}
        }

        // Dispatch notification
        OtpNotificationHelper.showWithdrawNotification(
            context = context,
            title = "❌ উইথড্র বাতিল হয়েছে",
            message = "আপনার ৳${String.format(java.util.Locale.US, "%.2f", req.amount)} (${req.method}) রিকোয়েস্ট বাতিল করা হয়েছে। ব্যালেন্স ফেরত দেওয়া হয়েছে।"
        )
        return true
    }

    fun getUserRequests(userEmail: String): List<WithdrawalRequest> {
        val email = userEmail.trim().lowercase()
        return if (email.isBlank() || email == "user account") {
            _state.value.allRequests
        } else {
            _state.value.allRequests.filter { it.userEmail.equals(email, ignoreCase = true) || it.userEmail.isBlank() }
        }
    }

    /**
     * Download / Export last 5 transactions to file / share
     */
    fun exportWithdrawalHistory(context: android.content.Context, userEmail: String) {
        val requests = getUserRequests(userEmail).take(5)
        val timeNowStr = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.US).format(java.util.Date())
        val sb = StringBuilder()
        sb.append("========================================\n")
        sb.append("     WORK SHORTCUT - WITHDRAWAL STATEMENT\n")
        sb.append("========================================\n")
        sb.append("Generated: ").append(timeNowStr).append("\n")
        sb.append("User: ").append(userEmail.ifBlank { "All Users / Admin" }).append("\n")
        sb.append("Available Balance: ৳").append(String.format(java.util.Locale.US, "%.2f", _state.value.availableBalanceTk)).append("\n")
        sb.append("Total Earned: ৳").append(String.format(java.util.Locale.US, "%.2f", _state.value.totalEarnedTk)).append("\n")
        sb.append("----------------------------------------\n")
        sb.append("LAST ").append(requests.size).append(" WITHDRAWAL TRANSACTIONS:\n\n")
        if (requests.isEmpty()) {
            sb.append("No withdrawal records found.\n")
        } else {
            requests.forEachIndexed { idx, req ->
                val dateStr = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.US).format(java.util.Date(req.requestTimestamp))
                sb.append("${idx + 1}. [").append(req.status).append("] ৳")
                    .append(String.format(java.util.Locale.US, "%.2f", req.amount))
                    .append(" via ").append(req.method)
                    .append(" (").append(req.accountNumber).append(")\n")
                    .append("   Date: ").append(dateStr).append("\n")
                if (req.note.isNotBlank()) {
                    sb.append("   Note: ").append(req.note).append("\n")
                }
                sb.append("\n")
            }
        }
        sb.append("========================================\n")
        val text = sb.toString()
        com.example.util.ClipboardHelper.copyToClipboard(context, text, "Withdrawal Statement")
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Withdrawal Statement - " + timeNowStr)
                type = "text/plain"
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = android.content.Intent.createChooser(intent, "Download / Share Statement").apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
            android.widget.Toast.makeText(context, "✓ উইথড্র হিস্টোরি (শেষ ৫টি ট্রানজেকশন) ডাউনলোড ও শেয়ার প্রস্তুত!", android.widget.Toast.LENGTH_LONG).show()
            com.example.util.VibrationHelper.vibrateSuccess(context)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "✓ উইথড্র হিস্টোরি ক্লিপবোর্ডে কপি করা হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun restoreUserBalanceIfEmpty(cloudBalance: Double) {
        if (_state.value.availableBalanceTk < cloudBalance) {
            val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
            val neededCredit = maxOf(baseCredit, cloudBalance)
            prefs?.edit()?.putFloat(KEY_BASE_CREDIT, neededCredit.toFloat())?.apply()
            refreshBalances()
        }
    }

    /**
     * Purge withdrawal records older than specified days (default 60 days = 2 months).
     * Keeps PENDING requests safe, purges only completed/rejected older records.
     */
    fun purgeOldWithdrawalRequests(olderThanDays: Int = 60): Int {
        val cutoff = System.currentTimeMillis() - (olderThanDays * 24L * 60L * 60L * 1000L)
        val initialSize = _state.value.allRequests.size
        val filtered = _state.value.allRequests.filter { req ->
            req.status == "PENDING" || req.requestTimestamp >= cutoff
        }
        val removed = initialSize - filtered.size
        if (removed > 0) {
            saveRequests(filtered)
            val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
            recomputeBalances(filtered, baseCredit)
        }
        return removed
    }

    /**
     * Purge corrupted or malformed withdrawal records
     */
    fun purgeCorruptedWithdrawals(): Int {
        val initialSize = _state.value.allRequests.size
        val filtered = _state.value.allRequests.filter { req ->
            req.accountNumber.isNotBlank() && req.accountNumber.length >= 4 &&
            req.amount > 0.0 && !req.amount.isNaN() &&
            req.userEmail.isNotBlank() && req.requestTimestamp > 0L
        }
        val removed = initialSize - filtered.size
        if (removed > 0) {
            saveRequests(filtered)
            val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 0.0f)?.toDouble() ?: 0.0
            recomputeBalances(filtered, baseCredit)
        }
        return removed
    }
}
