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

data class WithdrawalRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userEmail: String,
    val method: String, // "Binance", "bKash", "Nagad"
    val accountNumber: String,
    val amount: Double,
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val requestTimestamp: Long = System.currentTimeMillis(),
    val processedTimestamp: Long? = null,
    val note: String = ""
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

    const val MIN_BINANCE_TK = 20.0
    const val MIN_BKASH_TK = 50.0
    const val MIN_NAGAD_TK = 50.0

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(WithdrawalState())
    val state: StateFlow<WithdrawalState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadData()
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
        // Additional earned from received OTPs panel by panel according to each OTP's recorded rate:
        val records = OtpHistoryManager.state.value.recentRecords
        val currentUnixRate = UnixSmsManager.state.value.otpRatePerSms
        val currentZenexRate = VirtualNumberManager.zenexOtpRate.value

        val otpEarnedTk = if (records.isNotEmpty()) {
            records.sumOf { rec ->
                val rate = if (rec.rate > 0.0) rec.rate else {
                    if (rec.platform.contains("Unix", ignoreCase = true)) currentUnixRate else currentZenexRate
                }
                rate * 120.0
            }
        } else {
            val unixCount = OtpHistoryManager.state.value.unixTotalAllTime
            val zenexCount = OtpHistoryManager.state.value.zenexTotalAllTime
            (unixCount * currentUnixRate * 120.0) + (zenexCount * currentZenexRate * 120.0)
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
        for (r in list) {
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

    fun getMinimumAmountForMethod(method: String): Double {
        return when (method.trim().lowercase()) {
            "binance" -> MIN_BINANCE_TK
            else -> MIN_BKASH_TK
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
        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 60.0f)?.toDouble() ?: 60.0
        recomputeBalances(updated, baseCredit)

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

        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 60.0f)?.toDouble() ?: 60.0
        recomputeBalances(currentList, baseCredit)

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

        val baseCredit = prefs?.getFloat(KEY_BASE_CREDIT, 60.0f)?.toDouble() ?: 60.0
        recomputeBalances(currentList, baseCredit)

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
}
