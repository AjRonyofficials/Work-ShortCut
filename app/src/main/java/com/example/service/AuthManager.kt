package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SubAdminPermissions(
    val canUploadNumbers: Boolean = true,
    val canDeleteNumbers: Boolean = true,
    val canUpdateOtpRate: Boolean = true,
    val canManageWithdrawals: Boolean = false,
    val canViewLiveCdr: Boolean = false,
    val canBroadcastNotify: Boolean = false
)

data class UserDeviceSession(
    val deviceId: String,
    val deviceModel: String,
    val loginTime: Long = System.currentTimeMillis(),
    val lastActiveTime: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val isRevoked: Boolean = false
)

data class AuthUser(
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: String = "USER", // "PRIME_ADMIN", "SUB_ADMIN", "AGENT", or "USER"
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isBanned: Boolean = false,
    val loginCount: Int = 0,
    val activeDevicesCount: Int = 0,
    val lastLoginAt: Long = 0L,
    val lastDeviceName: String = "",
    val totalOtps: Int = 0,
    val balanceTk: Double = 0.0,
    val todayOtps: Int = 0,
    val last7DaysOtps: Int = 0,
    val last30DaysOtps: Int = 0,
    val lastActive: Long = 0L,
    val isOnline: Boolean = false,
    val permissions: SubAdminPermissions = SubAdminPermissions(),
    val deviceSessions: List<UserDeviceSession> = emptyList(),
    val disabledSections: List<String> = emptyList(),
    val boundDeviceId: String? = null,
    val boundDeviceModel: String? = null,
    val boundAt: Long = 0L,
    val isPinResetRequested: Boolean = false,
    val isAgent: Boolean = false,
    val createdByAgent: String = "",
    val agentCommissionOtpTarget: Int = 100,
    val agentCommissionTkPerTarget: Double = 10.0,
    val customUnixRate: Double? = null,
    val customPremiumV2Rate: Double? = null,
    val customKop1Rate: Double? = null,
    val customKop2Rate: Double? = null
)

object AuthManager {

    private const val PREFS_NAME = "work_shortcut_auth"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_LOGGED_IN_EMAIL = "logged_in_email"
    private const val KEY_LOGGED_IN_ROLE = "logged_in_role"
    private const val KEY_USERS_LIST = "users_list_json"

    const val ADMIN_EMAIL = "mdronyinfohelp@gmail.com"
    val ADMIN_PASS: String get() = com.example.util.SecureVault.adminPass
    const val TELEGRAM_CONTACT = "@ismailislamrony1"

    private var prefs: SharedPreferences? = null

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentEmail = MutableStateFlow("")
    val currentEmail: StateFlow<String> = _currentEmail.asStateFlow()

    private val _currentUserRole = MutableStateFlow("USER") // "PRIME_ADMIN", "SUB_ADMIN", or "USER"
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _managedUsers = MutableStateFlow<List<AuthUser>>(emptyList())
    val managedUsers: StateFlow<List<AuthUser>> = _managedUsers.asStateFlow()

    private val SUPABASE_BASE_URL: String get() = com.example.util.SecureVault.supabaseBaseUrl
    private val SUPABASE_KEY: String get() = com.example.util.SecureVault.supabaseKey
    private val httpClient = com.example.util.SecureHttpClient.create(
        connectTimeoutSeconds = 5L,
        readTimeoutSeconds = 5L,
        writeTimeoutSeconds = 5L
    )

    private val authScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isLoggedIn.value = prefs?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
            _currentEmail.value = prefs?.getString(KEY_LOGGED_IN_EMAIL, "") ?: ""
            _currentUserRole.value = prefs?.getString(KEY_LOGGED_IN_ROLE, "USER") ?: "USER"
            loadUsers()
            val savedEmail = _currentEmail.value.trim().lowercase()
            if (_isLoggedIn.value && savedEmail.isNotEmpty()) {
                VirtualNumberManager.onUserLoggedIn(savedEmail)
                UnixSmsManager.onUserLoggedIn(savedEmail)
                KopEngine2Manager.onUserLoggedIn(savedEmail)
                PremiumNumberV2Manager.onUserLoggedIn(savedEmail)
                OtpHistoryManager.onUserLoggedIn(savedEmail, context)
            }
            // Background sync with cloud & live heartbeat
            authScope.launch {
                syncFromCloud()
                while (isActive) {
                    kotlinx.coroutines.delay(8000L) // every 8s live sync
                    try {
                        if (!com.example.util.AppLifecycleTracker.canRunBackgroundWork()) {
                            continue
                        }
                        if (_isLoggedIn.value && !isPrimeAdmin()) {
                            sendHeartbeat()
                        }
                        if (_isLoggedIn.value) {
                            syncFromCloud()
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun loadUsers() {
        val json = prefs?.getString(KEY_USERS_LIST, "[]") ?: "[]"
        val list = mutableListOf<AuthUser>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val permObj = obj.optJSONObject("permissions")
                val perms = if (permObj != null) {
                    SubAdminPermissions(
                        canUploadNumbers = permObj.optBoolean("canUploadNumbers", true),
                        canDeleteNumbers = permObj.optBoolean("canDeleteNumbers", true),
                        canUpdateOtpRate = permObj.optBoolean("canUpdateOtpRate", true),
                        canManageWithdrawals = permObj.optBoolean("canManageWithdrawals", false),
                        canViewLiveCdr = permObj.optBoolean("canViewLiveCdr", false),
                        canBroadcastNotify = permObj.optBoolean("canBroadcastNotify", false)
                    )
                } else {
                    SubAdminPermissions()
                }

                val devArr = obj.optJSONArray("deviceSessions") ?: JSONArray()
                val devList = mutableListOf<UserDeviceSession>()
                for (di in 0 until devArr.length()) {
                    val dObj = devArr.optJSONObject(di) ?: continue
                    devList.add(
                        UserDeviceSession(
                            deviceId = dObj.optString("deviceId", ""),
                            deviceModel = dObj.optString("deviceModel", "Android"),
                            loginTime = dObj.optLong("loginTime", System.currentTimeMillis()),
                            lastActiveTime = dObj.optLong("lastActiveTime", System.currentTimeMillis()),
                            isOnline = dObj.optBoolean("isOnline", true),
                            isRevoked = dObj.optBoolean("isRevoked", false)
                        )
                    )
                }

                val secArr = obj.optJSONArray("disabledSections") ?: JSONArray()
                val secList = mutableListOf<String>()
                for (si in 0 until secArr.length()) {
                    secList.add(secArr.optString(si))
                }

                val roleVal = obj.optString("role", "USER")
                val isAgentVal = obj.optBoolean("isAgent", roleVal == "AGENT" || (permObj?.optBoolean("isAgent", false) == true))
                val createdByAgentVal = obj.optString("createdByAgent", permObj?.optString("createdByAgent", "") ?: "")
                val commTargetVal = obj.optInt("agentCommissionOtpTarget", permObj?.optInt("agentCommissionOtpTarget", 100) ?: 100).coerceAtLeast(1)
                val commTkVal = obj.optDouble("agentCommissionTkPerTarget", permObj?.optDouble("agentCommissionTkPerTarget", 10.0) ?: 10.0).coerceAtLeast(0.0)
                val cUnix = obj.optDouble("customUnixRate", permObj?.optDouble("customUnixRate", -1.0) ?: -1.0).takeIf { it > 0.0 }
                val cV2 = obj.optDouble("customPremiumV2Rate", permObj?.optDouble("customPremiumV2Rate", -1.0) ?: -1.0).takeIf { it > 0.0 }
                val cKop1 = obj.optDouble("customKop1Rate", permObj?.optDouble("customKop1Rate", -1.0) ?: -1.0).takeIf { it > 0.0 }
                val cKop2 = obj.optDouble("customKop2Rate", permObj?.optDouble("customKop2Rate", -1.0) ?: -1.0).takeIf { it > 0.0 }

                list.add(
                    AuthUser(
                        email = obj.getString("email"),
                        passwordHash = obj.getString("password"),
                        name = obj.optString("name", "User"),
                        role = if (isAgentVal && roleVal == "USER") "AGENT" else roleVal,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isActive = obj.optBoolean("isActive", true),
                        isBanned = obj.optBoolean("isBanned", false),
                        loginCount = obj.optInt("loginCount", 0),
                        activeDevicesCount = obj.optInt("activeDevicesCount", 0),
                        lastLoginAt = obj.optLong("lastLoginAt", 0L),
                        lastDeviceName = obj.optString("lastDeviceName", ""),
                        totalOtps = obj.optInt("totalOtps", 0),
                        balanceTk = obj.optDouble("balanceTk", 0.0),
                        todayOtps = obj.optInt("todayOtps", 0),
                        last7DaysOtps = obj.optInt("last7DaysOtps", 0),
                        last30DaysOtps = obj.optInt("last30DaysOtps", 0),
                        permissions = perms,
                        deviceSessions = devList,
                        disabledSections = secList,
                        boundDeviceId = if (obj.has("boundDeviceId")) obj.optString("boundDeviceId").ifEmpty { null } else null,
                        boundDeviceModel = if (obj.has("boundDeviceModel")) obj.optString("boundDeviceModel").ifEmpty { null } else null,
                        boundAt = obj.optLong("boundAt", 0L),
                        isPinResetRequested = obj.optBoolean("isPinResetRequested", false),
                        isAgent = isAgentVal,
                        createdByAgent = createdByAgentVal,
                        agentCommissionOtpTarget = commTargetVal,
                        agentCommissionTkPerTarget = commTkVal,
                        customUnixRate = cUnix,
                        customPremiumV2Rate = cV2,
                        customKop1Rate = cKop1,
                        customKop2Rate = cKop2
                    )
                )
            }
        } catch (_: Exception) {}

        _managedUsers.value = list
    }

    suspend fun syncFromCloudAsync(): Int = withContext(Dispatchers.IO) {
        syncFromCloudInternal()
    }

    fun syncFromCloud() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            Thread { syncFromCloudInternal() }.start()
        } else {
            syncFromCloudInternal()
        }
    }

    private fun parseIsoTimestamp(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        return try {
            val clean = if (dateStr.contains(".")) {
                dateStr.substringBefore(".") + "Z"
            } else if (dateStr.endsWith("+00:00")) {
                dateStr.substringBefore("+") + "Z"
            } else dateStr
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(clean)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private data class LiveUserOtpStats(
        var totalOtps: Int = 0,
        var todayOtps: Int = 0,
        var last7dOtps: Int = 0,
        var last30dOtps: Int = 0,
        var totalEarnedTk: Double = 0.0
    )

    private fun syncFromCloudInternal(): Int {
        try {
            // 1. Fetch live OTP history across all users to compute real-time Today / 7D / 30D / Total OTP & Earned Balance
            val liveOtpMap = HashMap<String, LiveUserOtpStats>()
            val seenOtpKeys = HashSet<String>()
            val now = System.currentTimeMillis()
            val todayDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
            val cutoff24h = now - 24L * 60L * 60L * 1000L
            val cutoff7d = now - 7L * 24L * 60L * 60L * 1000L
            val cutoff30d = now - 30L * 24L * 60L * 60L * 1000L

            try {
                val otpReq = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/otp_history?select=*&order=created_at.desc&limit=2000")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .get()
                    .build()
                httpClient.newCall(otpReq).execute().use { otpResp ->
                    if (otpResp.isSuccessful) {
                        val otpBody = otpResp.body?.string() ?: "[]"
                        val otpArr = JSONArray(otpBody)
                        for (i in 0 until otpArr.length()) {
                            val o = otpArr.optJSONObject(i) ?: continue
                            val uEmail = o.optString("user_email", "").trim().lowercase()
                            val phone = o.optString("phone_number", "").trim()
                            val code = o.optString("otp_code", "").trim()
                            if (uEmail.isBlank() || code.isBlank() || code == "null" || code.length < 3) continue
                            if (phone.isBlank() || phone.startsWith("+120255501") || phone.startsWith("+2376991122")) continue

                            val dedupKey = "${uEmail}_${phone}_${code}"
                            if (!seenOtpKeys.add(dedupKey)) continue

                            val ts = parseIsoTimestamp(o.optString("created_at", ""))
                            val recDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ts))
                            val rateVal = o.optDouble("earned_amount", 0.50).let { if (it < 0.05) 0.50 else it }

                            val stats = liveOtpMap.getOrPut(uEmail) { LiveUserOtpStats() }
                            stats.totalOtps++
                            stats.totalEarnedTk += rateVal
                            if (recDateStr == todayDateStr || ts >= cutoff24h) {
                                stats.todayOtps++
                            }
                            if (ts >= cutoff7d) {
                                stats.last7dOtps++
                            }
                            if (ts >= cutoff30d) {
                                stats.last30dOtps++
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Fetch live withdrawal requests per user to deduct pending/approved withdrawals from earned balance
            val withdrawnByUser = HashMap<String, Double>()
            try {
                val wdReq = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/withdraw_history?select=user_email,amount,status&limit=500")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .get()
                    .build()
                httpClient.newCall(wdReq).execute().use { wdResp ->
                    if (wdResp.isSuccessful) {
                        val wdBody = wdResp.body?.string() ?: "[]"
                        val wdArr = JSONArray(wdBody)
                        for (i in 0 until wdArr.length()) {
                            val o = wdArr.optJSONObject(i) ?: continue
                            val uEmail = o.optString("user_email", "").trim().lowercase()
                            val status = o.optString("status", "PENDING").uppercase()
                            val amt = o.optDouble("amount", 0.0)
                            if (uEmail.isNotBlank() && amt > 0.0 && (status == "PENDING" || status == "APPROVED")) {
                                withdrawnByUser[uEmail] = (withdrawnByUser[uEmail] ?: 0.0) + amt
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Fetch app_users and reconcile with live OTP & withdrawal stats
            val req = okhttp3.Request.Builder()
                .url("$SUPABASE_BASE_URL/app_users?select=*")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val arr = JSONArray(bodyStr)
                val cloudUsers = mutableListOf<AuthUser>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val emailStr = obj.optString("email", "").trim()
                    if (emailStr.isEmpty()) continue
                    val emailLower = emailStr.lowercase()

                    val permObj = try {
                        val pStr = obj.optString("permissions_json", "{}")
                        JSONObject(pStr)
                    } catch (_: Exception) { JSONObject() }

                    val perms = SubAdminPermissions(
                        canUploadNumbers = permObj.optBoolean("canUploadNumbers", true),
                        canDeleteNumbers = permObj.optBoolean("canDeleteNumbers", true),
                        canUpdateOtpRate = permObj.optBoolean("canUpdateOtpRate", true),
                        canManageWithdrawals = permObj.optBoolean("canManageWithdrawals", false),
                        canViewLiveCdr = permObj.optBoolean("canViewLiveCdr", false),
                        canBroadcastNotify = permObj.optBoolean("canBroadcastNotify", false)
                    )

                    val statusStr = obj.optString("status", "ACTIVE")
                    val lastActive = obj.optLong("last_active", 0L)
                    val devModel = obj.optString("device_model", "").ifEmpty { obj.optString("lastDeviceName", "") }
                    val isOnline = (now - lastActive) < 180_000L && lastActive > 0L && statusStr != "OFFLINE"

                    // Find any existing local device sessions to preserve
                    val existingUser = _managedUsers.value.firstOrNull { it.email.equals(emailStr, ignoreCase = true) }
                    val devSessions = existingUser?.deviceSessions ?: emptyList()
                    val disabledSecs = try {
                        val sArr = permObj.optJSONArray("disabledSections")
                        if (sArr != null) {
                            val sList = mutableListOf<String>()
                            for (si in 0 until sArr.length()) {
                                val s = sArr.optString(si)
                                if (s.isNotBlank()) sList.add(s)
                            }
                            sList
                        } else {
                            existingUser?.disabledSections ?: emptyList()
                        }
                    } catch (_: Exception) {
                        existingUser?.disabledSections ?: emptyList()
                    }

                    val boundDevId = obj.optString("bound_device_id", "").ifEmpty {
                        obj.optString("device_id", "").ifEmpty { null }
                    }
                    val boundDevModel = obj.optString("bound_device_model", "").ifEmpty {
                        obj.optString("device_model", "").ifEmpty { null }
                    }
                    val boundAt = obj.optLong("bound_at", 0L)

                    val dbTotalOtps = obj.optInt("total_otps", 0)
                    val dbBalance = obj.optDouble("balance", 0.0)
                    val dbToday = obj.optInt("otp_today", 0)
                    val db7d = obj.optInt("otp_7days", 0)
                    val db30d = obj.optInt("otp_30days", 0)
                    val adminEditedAt = permObj.optLong("adminEditedAt", 0L)

                    val recentEditTs = recentAdminEditTimestamps[emailLower] ?: 0L
                    val recentEditUser = recentAdminEdits[emailLower]
                    val isRecentlyEditedByAdmin = recentEditUser != null && (now - recentEditTs) < 20_000L

                    val liveStats = liveOtpMap[emailLower]
                    val withdrawnTk = withdrawnByUser[emailLower] ?: 0.0
                    val liveNetBalance = if (liveStats != null) {
                        maxOf(0.0, Math.round((liveStats.totalEarnedTk - withdrawnTk) * 100.0) / 100.0)
                    } else 0.0

                    // If Admin edited this user's stats/balance, or if app_users already has synced data,
                    // app_users is authoritative so Admin balance/OTP edits (including lowering or setting to 0) stick permanently.
                    val useLiveBackfill = !isRecentlyEditedByAdmin && adminEditedAt == 0L && dbTotalOtps == 0 && dbBalance == 0.0 && liveStats != null

                    val finalTotalOtps = when {
                        isRecentlyEditedByAdmin -> recentEditUser!!.totalOtps
                        useLiveBackfill -> liveStats?.totalOtps ?: 0
                        else -> dbTotalOtps
                    }
                    val finalTodayOtps = when {
                        isRecentlyEditedByAdmin -> recentEditUser!!.todayOtps
                        useLiveBackfill -> liveStats?.todayOtps ?: 0
                        else -> dbToday
                    }
                    val final7dOtps = when {
                        isRecentlyEditedByAdmin -> recentEditUser!!.last7DaysOtps
                        useLiveBackfill -> liveStats?.last7dOtps ?: 0
                        else -> db7d
                    }
                    val final30dOtps = when {
                        isRecentlyEditedByAdmin -> recentEditUser!!.last30DaysOtps
                        useLiveBackfill -> liveStats?.last30dOtps ?: 0
                        else -> db30d
                    }
                    val finalBalanceTk = when {
                        isRecentlyEditedByAdmin -> recentEditUser!!.balanceTk
                        useLiveBackfill -> liveNetBalance
                        else -> dbBalance
                    }

                    if (useLiveBackfill && (finalTotalOtps > 0 || finalBalanceTk > 0.0)) {
                        patchUserField(emailLower, JSONObject().apply {
                            put("total_otps", finalTotalOtps)
                            put("balance", finalBalanceTk)
                            put("total_balance", finalBalanceTk)
                            put("otp_today", finalTodayOtps)
                            put("otp_7days", final7dOtps)
                            put("otp_30days", final30dOtps)
                        })
                    }

                    val cloudRole = obj.optString("role", "USER")
                    val isAgentFlag = permObj.optBoolean("isAgent", cloudRole == "AGENT" || (existingUser?.isAgent == true))
                    val createdByAgentStr = permObj.optString("createdByAgent", existingUser?.createdByAgent ?: "")
                    val commOtpTarget = permObj.optInt("agentCommissionOtpTarget", existingUser?.agentCommissionOtpTarget ?: 100).coerceAtLeast(1)
                    val commTkTarget = permObj.optDouble("agentCommissionTkPerTarget", existingUser?.agentCommissionTkPerTarget ?: 10.0).coerceAtLeast(0.0)
                    val cUnixRate = permObj.optDouble("customUnixRate", -1.0).takeIf { it > 0.0 } ?: existingUser?.customUnixRate
                    val cV2Rate = permObj.optDouble("customPremiumV2Rate", -1.0).takeIf { it > 0.0 } ?: existingUser?.customPremiumV2Rate
                    val cKop1Rate = permObj.optDouble("customKop1Rate", -1.0).takeIf { it > 0.0 } ?: existingUser?.customKop1Rate
                    val cKop2Rate = permObj.optDouble("customKop2Rate", -1.0).takeIf { it > 0.0 } ?: existingUser?.customKop2Rate

                    cloudUsers.add(
                        AuthUser(
                            email = emailStr,
                            passwordHash = obj.optString("password", ""),
                            name = obj.optString("username", "User"),
                            role = if (isAgentFlag && cloudRole == "USER") "AGENT" else cloudRole,
                            createdAt = System.currentTimeMillis(),
                            isActive = statusStr != "INACTIVE" && statusStr != "BANNED",
                            isBanned = statusStr == "BANNED",
                            loginCount = obj.optInt("loginCount", 1),
                            activeDevicesCount = if (devSessions.isNotEmpty()) devSessions.count { it.isOnline && !it.isRevoked } else (if (isOnline) 1 else 0),
                            lastLoginAt = if (lastActive > 0L) lastActive else obj.optLong("lastLoginAt", 0L),
                            lastDeviceName = devModel,
                            totalOtps = finalTotalOtps,
                            balanceTk = finalBalanceTk,
                            todayOtps = finalTodayOtps,
                            last7DaysOtps = final7dOtps,
                            last30DaysOtps = final30dOtps,
                            lastActive = lastActive,
                            isOnline = isOnline,
                            permissions = perms,
                            deviceSessions = devSessions,
                            disabledSections = disabledSecs,
                            boundDeviceId = boundDevId,
                            boundDeviceModel = boundDevModel,
                            boundAt = boundAt,
                            isPinResetRequested = permObj.optBoolean("pinResetRequested", obj.optBoolean("pin_reset_requested", false)),
                            isAgent = isAgentFlag,
                            createdByAgent = createdByAgentStr,
                            agentCommissionOtpTarget = commOtpTarget,
                            agentCommissionTkPerTarget = commTkTarget,
                            customUnixRate = cUnixRate,
                            customPremiumV2Rate = cV2Rate,
                            customKop1Rate = cKop1Rate,
                            customKop2Rate = cKop2Rate
                        )
                    )
                }

                // Cloud users from Supabase are the source of truth,
                // BUT merge any locally created users that are pending sync so they are never lost
                val mergedUsers = cloudUsers.toMutableList()
                val currentLocal = _managedUsers.value
                for (local in currentLocal) {
                    if (mergedUsers.none { it.email.equals(local.email, ignoreCase = true) }) {
                        mergedUsers.add(local)
                    }
                }
                _managedUsers.value = mergedUsers
                saveUsersLocallyOnly(mergedUsers)

                // Keep current logged in user's role updated if Admin promoted them to AGENT or SUB_ADMIN
                val curEmail = _currentEmail.value.trim().lowercase()
                if (curEmail.isNotEmpty() && curEmail != ADMIN_EMAIL.lowercase()) {
                    val me = mergedUsers.firstOrNull { it.email.equals(curEmail, ignoreCase = true) }
                    if (me != null) {
                        val effectiveRole = if (me.isAgent && me.role == "USER") "AGENT" else me.role
                        if (_currentUserRole.value != effectiveRole) {
                            _currentUserRole.value = effectiveRole
                            prefs?.edit()?.putString(KEY_LOGGED_IN_ROLE, effectiveRole)?.apply()
                        }
                    }
                }
                return mergedUsers.size
            }
        } catch (_: Exception) {}
        return _managedUsers.value.size
    }

    fun buildPermissionsJsonForUser(u: AuthUser, adminEditedAt: Long = 0L): JSONObject {
        return JSONObject().apply {
            put("canUploadNumbers", u.permissions.canUploadNumbers)
            put("canDeleteNumbers", u.permissions.canDeleteNumbers)
            put("canUpdateOtpRate", u.permissions.canUpdateOtpRate)
            put("canManageWithdrawals", u.permissions.canManageWithdrawals)
            put("canViewLiveCdr", u.permissions.canViewLiveCdr)
            put("canBroadcastNotify", u.permissions.canBroadcastNotify)
            val sArr = JSONArray()
            u.disabledSections.forEach { sArr.put(it) }
            put("disabledSections", sArr)
            put("pinResetRequested", u.isPinResetRequested)
            put("isAgent", u.isAgent || u.role == "AGENT")
            put("createdByAgent", u.createdByAgent)
            put("agentCommissionOtpTarget", u.agentCommissionOtpTarget)
            put("agentCommissionTkPerTarget", u.agentCommissionTkPerTarget)
            put("customUnixRate", u.customUnixRate ?: -1.0)
            put("customPremiumV2Rate", u.customPremiumV2Rate ?: -1.0)
            put("customKop1Rate", u.customKop1Rate ?: -1.0)
            put("customKop2Rate", u.customKop2Rate ?: -1.0)
            if (adminEditedAt > 0L) {
                put("adminEditedAt", adminEditedAt)
            }
        }
    }

    private fun saveUsersLocallyOnly(list: List<AuthUser>) {
        val arr = serializeUsersToJson(list)
        prefs?.edit()?.putString(KEY_USERS_LIST, arr.toString())?.apply()
    }

    private fun serializeUsersToJson(list: List<AuthUser>): JSONArray {
        val arr = JSONArray()
        for (u in list) {
            val obj = JSONObject()
            obj.put("email", u.email)
            obj.put("password", u.passwordHash)
            obj.put("name", u.name)
            obj.put("role", u.role)
            obj.put("createdAt", u.createdAt)
            obj.put("isActive", u.isActive)
            obj.put("isBanned", u.isBanned)
            obj.put("loginCount", u.loginCount)
            obj.put("activeDevicesCount", u.activeDevicesCount)
            obj.put("lastLoginAt", u.lastLoginAt)
            obj.put("lastDeviceName", u.lastDeviceName)
            obj.put("totalOtps", u.totalOtps)
            obj.put("balanceTk", u.balanceTk)
            obj.put("todayOtps", u.todayOtps)
            obj.put("last7DaysOtps", u.last7DaysOtps)
            obj.put("last30DaysOtps", u.last30DaysOtps)
            obj.put("isAgent", u.isAgent || u.role == "AGENT")
            obj.put("createdByAgent", u.createdByAgent)
            obj.put("agentCommissionOtpTarget", u.agentCommissionOtpTarget)
            obj.put("agentCommissionTkPerTarget", u.agentCommissionTkPerTarget)
            obj.put("customUnixRate", u.customUnixRate ?: -1.0)
            obj.put("customPremiumV2Rate", u.customPremiumV2Rate ?: -1.0)
            obj.put("customKop1Rate", u.customKop1Rate ?: -1.0)
            obj.put("customKop2Rate", u.customKop2Rate ?: -1.0)

            val permObj = buildPermissionsJsonForUser(u)
            obj.put("permissions", permObj)

            val devArr = JSONArray()
            for (d in u.deviceSessions) {
                val dObj = JSONObject().apply {
                    put("deviceId", d.deviceId)
                    put("deviceModel", d.deviceModel)
                    put("loginTime", d.loginTime)
                    put("lastActiveTime", d.lastActiveTime)
                    put("isOnline", d.isOnline)
                    put("isRevoked", d.isRevoked)
                }
                devArr.put(dObj)
            }
            obj.put("deviceSessions", devArr)

            val secArr = JSONArray()
            for (s in u.disabledSections) {
                secArr.put(s)
            }
            obj.put("disabledSections", secArr)

            u.boundDeviceId?.let { obj.put("boundDeviceId", it) }
            u.boundDeviceModel?.let { obj.put("boundDeviceModel", it) }
            obj.put("boundAt", u.boundAt)
            obj.put("isPinResetRequested", u.isPinResetRequested)

            arr.put(obj)
        }
        return arr
    }

    fun markUserPinResetRequested(userEmail: String) {
        val clean = userEmail.trim().lowercase()
        if (clean.isBlank()) return
        _managedUsers.update { list ->
            list.map { if (it.email.equals(clean, ignoreCase = true)) it.copy(isPinResetRequested = true) else it }
        }
        saveUsersLocallyOnly(_managedUsers.value)
        val user = _managedUsers.value.firstOrNull { it.email.lowercase() == clean } ?: return
        val permObj = buildPermissionsJsonForUser(user.copy(isPinResetRequested = true))
        patchUserField(clean, JSONObject().put("permissions_json", permObj.toString()))
    }

    fun resetUserPin(userEmail: String, onComplete: ((Boolean) -> Unit)? = null) {
        val clean = userEmail.trim().lowercase()
        if (clean.isBlank()) return
        WithdrawalManager.clearSecurityPin(clean)
        _managedUsers.update { list ->
            list.map { if (it.email.equals(clean, ignoreCase = true)) it.copy(isPinResetRequested = false) else it }
        }
        saveUsersLocallyOnly(_managedUsers.value)
        val user = _managedUsers.value.firstOrNull { it.email.lowercase() == clean } ?: return
        val permObj = buildPermissionsJsonForUser(user.copy(isPinResetRequested = false))
        patchUserField(clean, JSONObject().put("permissions_json", permObj.toString()), onComplete)
    }

    fun patchUserField(email: String, fields: JSONObject, onComplete: ((Boolean) -> Unit)? = null) {
        val target = email.trim().lowercase()
        if (target.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        authScope.launch {
            try {
                val encTarget = java.net.URLEncoder.encode(target, "UTF-8")
                val body = fields.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=ilike.$encTarget")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                val resp = httpClient.newCall(req).execute()
                val ok = resp.isSuccessful
                resp.close()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(ok)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun patchUserDisabledSections(email: String, sections: List<String>) {
        val target = email.trim().lowercase()
        val user = _managedUsers.value.firstOrNull { it.email.lowercase() == target } ?: return
        val permObj = buildPermissionsJsonForUser(user.copy(disabledSections = sections))
        patchUserField(target, JSONObject().put("permissions_json", permObj.toString()))
    }

    private fun saveUsers(list: List<AuthUser>) {
        _managedUsers.value = list
        saveUsersLocallyOnly(list)
    }

    private var currentDeviceId: String = ""

    fun getOrCreateDeviceId(context: Context?): String {
        if (currentDeviceId.isNotEmpty()) return currentDeviceId
        val saved = prefs?.getString("current_device_id", "") ?: ""
        if (saved.isNotEmpty()) {
            currentDeviceId = saved
            return saved
        }
        val androidId = if (context != null) {
            try {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            } catch (_: Exception) { null }
        } else null
        val finalId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") androidId else java.util.UUID.randomUUID().toString()
        currentDeviceId = finalId
        prefs?.edit()?.putString("current_device_id", finalId)?.apply()
        return finalId
    }

    /**
     * Checks credentials against Admin master account OR admin-created managed users
     */
    fun login(emailInput: String, passInput: String, context: Context? = null): Pair<Boolean, String> {
        val email = emailInput.trim().lowercase()
        val pass = passInput.trim()

        if (email.isEmpty() || pass.isEmpty()) {
            return Pair(false, "ইমেইল ও পাসওয়ার্ড প্রদান করুন")
        }

        val myDeviceId = getOrCreateDeviceId(context)
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        // 1. Check Master Admin
        if (email == ADMIN_EMAIL.lowercase() && pass == ADMIN_PASS) {
            _isLoggedIn.value = true
            _currentEmail.value = ADMIN_EMAIL
            _currentUserRole.value = "PRIME_ADMIN"

            prefs?.edit()
                ?.putBoolean(KEY_IS_LOGGED_IN, true)
                ?.putString(KEY_LOGGED_IN_EMAIL, ADMIN_EMAIL)
                ?.putString(KEY_LOGGED_IN_ROLE, "PRIME_ADMIN")
                ?.apply()

            OtpHistoryManager.onUserLoggedIn(ADMIN_EMAIL, context)
            WithdrawalManager.onUserLoggedIn(ADMIN_EMAIL)
            VirtualNumberManager.onUserLoggedIn(ADMIN_EMAIL)
            UnixSmsManager.onUserLoggedIn(ADMIN_EMAIL)
            KopEngine2Manager.onUserLoggedIn(ADMIN_EMAIL)
            PremiumNumberV2Manager.onUserLoggedIn(ADMIN_EMAIL)

            return Pair(true, "👑 প্রাইম এডমিন হিসেবে সফলভাবে লগইন হয়েছে")
        }

        // 2. Check Managed Users created by Admin (match email, username/name, or email prefix)
        fun matchesUser(u: AuthUser, identifier: String): Boolean {
            val id = identifier.lowercase()
            return u.email.lowercase() == id ||
                   u.name.trim().lowercase() == id ||
                   u.email.substringBefore("@").lowercase() == id
        }

        var user = _managedUsers.value.firstOrNull { matchesUser(it, email) }
        if (user == null) {
            // Attempt fast cloud sync before rejecting
            try {
                syncFromCloud()
                user = _managedUsers.value.firstOrNull { matchesUser(it, email) }
            } catch (_: Exception) {}
        }

        if (user != null) {
            if (user.isBanned) {
                return Pair(false, "আপনার অ্যাকাউন্টটি এডমিন কর্তৃক ব্যান (Ban) করা হয়েছে! যোগাযোগ: $TELEGRAM_CONTACT")
            }
            if (!user.isActive) {
                return Pair(false, "আপনার অ্যাকাউন্টটি নিষ্ক্রিয় (Inactive)। এডমিনের সাথে যোগাযোগ করুন: $TELEGRAM_CONTACT")
            }

            // Check if this specific device was revoked by admin
            val existingDevSession = user.deviceSessions.firstOrNull { it.deviceId == myDeviceId }
            if (existingDevSession != null && existingDevSession.isRevoked) {
                return Pair(false, "আপনার এই ডিভাইসের সেশনটি এডমিন কর্তৃক বাতিল (Revoked) করা হয়েছে! যোগাযোগ: $TELEGRAM_CONTACT")
            }

            if (user.passwordHash == pass) {
                // 1. Device Binding Check (1 Account = 1 Physical Phone)
                val boundId = user.boundDeviceId
                if (!boundId.isNullOrBlank() && boundId != myDeviceId) {
                    val boundModel = user.boundDeviceModel?.ifBlank { null } ?: "পূর্ববর্তী ডিভাইস"
                    return Pair(
                        false,
                        "🚫 ডিভাইস লক ত্রুটি: আপনার অ্যাকাউন্টটি অন্য একটি ডিভাইসে ($boundModel) লক করা আছে! নতুন ডিভাইসে লগইন করতে মেইন এডমিনের সাথে যোগাযোগ করে ডিভাইস রিসেট করান।"
                    )
                }

                // 2. Hardware Limitation Check (1 Physical Phone = 1 Account Only)
                val localBoundEmail = prefs?.getString("bound_device_email", "") ?: ""
                val otherBoundUser = _managedUsers.value.firstOrNull {
                    it.email.lowercase() != user.email.lowercase() &&
                    (!it.boundDeviceId.isNullOrBlank() && it.boundDeviceId == myDeviceId)
                }
                if (otherBoundUser != null || (localBoundEmail.isNotEmpty() && localBoundEmail.lowercase() != user.email.lowercase())) {
                    val conflictEmail = otherBoundUser?.email ?: localBoundEmail
                    return Pair(
                        false,
                        "🚫 ডিভাইস সীমাবদ্ধতা: এই ডিভাইসে ইতিমধ্যে অন্য একটি অ্যাকাউন্ট ($conflictEmail) লিঙ্ক করা রয়েছে! একটি ডিভাইসে কেবল একটি অ্যাকাউন্ট ব্যবহার করা যাবে। এডমিনের সাথে যোগাযোগ করুন।"
                    )
                }

                val nowTime = System.currentTimeMillis()
                val isFirstBinding = user.boundDeviceId.isNullOrBlank()
                val effectiveBoundId = if (isFirstBinding) myDeviceId else user.boundDeviceId
                val effectiveBoundModel = if (user.boundDeviceModel.isNullOrBlank()) deviceName else user.boundDeviceModel
                val effectiveBoundAt = if (user.boundAt <= 0L) nowTime else user.boundAt

                // Save locally on physical phone
                prefs?.edit()?.putString("bound_device_email", user.email.lowercase())?.apply()

                // Track device session
                val currentSessions = user.deviceSessions.filterNot { it.deviceId == myDeviceId }
                val newSession = UserDeviceSession(
                    deviceId = myDeviceId,
                    deviceModel = deviceName,
                    loginTime = nowTime,
                    lastActiveTime = nowTime,
                    isOnline = true,
                    isRevoked = false
                )
                val updatedSessions = (currentSessions + newSession).sortedByDescending { it.lastActiveTime }

                val newLoginCount = user.loginCount + 1
                val activeDevCount = updatedSessions.count { it.isOnline && !it.isRevoked }
                val updatedUser = user.copy(
                    boundDeviceId = effectiveBoundId,
                    boundDeviceModel = effectiveBoundModel,
                    boundAt = effectiveBoundAt,
                    loginCount = newLoginCount,
                    activeDevicesCount = maxOf(1, activeDevCount),
                    lastLoginAt = nowTime,
                    lastDeviceName = deviceName,
                    isOnline = true,
                    lastActive = nowTime,
                    deviceSessions = updatedSessions
                )

                val updatedList = _managedUsers.value.map {
                    if (it.email.lowercase() == email) updatedUser else it
                }
                saveUsers(updatedList)

                // If this device was just bound, patch bound_device_id to Supabase
                if (isFirstBinding) {
                    authScope.launch {
                        val bindPayload = JSONObject().apply {
                            put("device_id", myDeviceId)
                            put("device_model", deviceName)
                            put("last_active", nowTime)
                        }
                        patchUserField(user.email, bindPayload)
                    }
                }

                _isLoggedIn.value = true
                _currentEmail.value = user.email
                _currentUserRole.value = user.role

                prefs?.edit()
                    ?.putBoolean(KEY_IS_LOGGED_IN, true)
                    ?.putString(KEY_LOGGED_IN_EMAIL, user.email)
                    ?.putString(KEY_LOGGED_IN_ROLE, user.role)
                    ?.apply()
                OtpHistoryManager.onUserLoggedIn(user.email, context)
                WithdrawalManager.onUserLoggedIn(user.email)
                VirtualNumberManager.onUserLoggedIn(user.email)
                UnixSmsManager.onUserLoggedIn(user.email)
                KopEngine2Manager.onUserLoggedIn(user.email)
                PremiumNumberV2Manager.onUserLoggedIn(user.email)
                OtpHistoryManager.restoreUserHistoryIfEmpty(
                    totalOtps = user.totalOtps,
                    todayOtps = user.todayOtps,
                    last7d = user.last7DaysOtps,
                    last30d = user.last30DaysOtps
                )
                WithdrawalManager.setAdminAssignedBalance(user.balanceTk)
                WithdrawalManager.refreshBalances()

                authScope.launch {
                    try {
                        sendHeartbeat()
                    } catch (_: Exception) {}
                }

                return Pair(true, "সফলভাবে লগইন হয়েছে (${user.name})")
            } else {
                return Pair(false, "ভুল পাসওয়ার্ড! ভুলে গেলে যোগাযোগ: $TELEGRAM_CONTACT")
            }
        }

        return Pair(false, "অনুমোদিত অ্যাকাউন্ট নয়! আইডি ও পাসওয়ার্ড পেতে যোগাযোগ করুন: $TELEGRAM_CONTACT")
    }

    fun logout() {
        val current = _currentEmail.value.lowercase()
        if (current.isNotEmpty() && current != ADMIN_EMAIL.lowercase()) {
            authScope.launch {
                try {
                    val payload = JSONObject().apply {
                        put("status", "OFFLINE")
                        put("last_active", System.currentTimeMillis())
                    }
                    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val req = okhttp3.Request.Builder()
                        .url("$SUPABASE_BASE_URL/app_users?email=eq.$current")
                        .header("apikey", SUPABASE_KEY)
                        .header("Content-Type", "application/json")
                        .patch(body)
                        .build()
                    httpClient.newCall(req).execute()
                } catch (_: Exception) {}
            }
        }

        _isLoggedIn.value = false
        _currentEmail.value = ""
        _currentUserRole.value = "USER"
        prefs?.edit()
            ?.putBoolean(KEY_IS_LOGGED_IN, false)
            ?.putString(KEY_LOGGED_IN_EMAIL, "")
            ?.putString(KEY_LOGGED_IN_ROLE, "USER")
            ?.apply()

        if (current.isNotEmpty()) {
            _managedUsers.value = _managedUsers.value.map { u ->
                if (u.email.lowercase() == current) {
                    u.copy(isOnline = false, lastActive = System.currentTimeMillis())
                } else u
            }
        }

        OtpHistoryManager.onUserLoggedOut()
        WithdrawalManager.onUserLoggedOut()
        VirtualNumberManager.onUserLoggedOut()
        UnixSmsManager.onUserLoggedOut()
        KopEngine2Manager.onUserLoggedOut()
        PremiumNumberV2Manager.onUserLoggedOut()
    }

    fun isPrimeAdmin(): Boolean {
        return _isLoggedIn.value && (
            _currentUserRole.value == "PRIME_ADMIN" ||
            _currentEmail.value.equals(ADMIN_EMAIL, ignoreCase = true)
        )
    }

    fun isSubAdmin(): Boolean {
        return _isLoggedIn.value && _currentUserRole.value == "SUB_ADMIN"
    }

    fun isAgent(): Boolean {
        if (!_isLoggedIn.value) return false
        if (_currentUserRole.value == "AGENT") return true
        val cur = _currentEmail.value.trim().lowercase()
        if (cur.isEmpty()) return false
        val u = _managedUsers.value.firstOrNull { it.email.equals(cur, ignoreCase = true) }
        return u?.isAgent == true || u?.role == "AGENT"
    }

    fun isAdmin(): Boolean {
        return isPrimeAdmin() || isSubAdmin()
    }

    fun canAccessAdminOrAgentPanel(): Boolean {
        return isAdmin() || isAgent()
    }

    fun getCurrentAuthUser(): AuthUser? {
        val cur = _currentEmail.value.trim().lowercase()
        if (cur.isEmpty()) return null
        return _managedUsers.value.firstOrNull { it.email.equals(cur, ignoreCase = true) }
    }

    fun getEffectiveUnixRate(baseRate: Double): Double {
        val u = getCurrentAuthUser()
        val custom = u?.customUnixRate
        return if (custom != null && custom > 0.0) custom else baseRate
    }

    fun getEffectivePremiumV2Rate(baseRate: Double): Double {
        val u = getCurrentAuthUser()
        val custom = u?.customPremiumV2Rate
        return if (custom != null && custom > 0.0) custom else baseRate
    }

    fun getEffectiveKop1Rate(baseRate: Double): Double {
        val u = getCurrentAuthUser()
        val custom = u?.customKop1Rate
        return if (custom != null && custom > 0.0) custom else baseRate
    }

    fun getEffectiveKop2Rate(baseRate: Double): Double {
        val u = getCurrentAuthUser()
        val custom = u?.customKop2Rate
        return if (custom != null && custom > 0.0) custom else baseRate
    }

    fun getCurrentSubAdminPermissions(): SubAdminPermissions {
        if (isPrimeAdmin()) {
            return SubAdminPermissions(
                canUploadNumbers = true,
                canDeleteNumbers = true,
                canUpdateOtpRate = true,
                canManageWithdrawals = true,
                canViewLiveCdr = true,
                canBroadcastNotify = true
            )
        }
        val email = _currentEmail.value.lowercase()
        val user = _managedUsers.value.firstOrNull { it.email.lowercase() == email }
        return user?.permissions ?: SubAdminPermissions()
    }

    fun setSubAdminRole(email: String, isSubAdmin: Boolean) {
        if (!isPrimeAdmin()) return
        val target = email.trim().lowercase()
        val existing = _managedUsers.value.firstOrNull { it.email.lowercase() == target }
        val roleStr = if (isSubAdmin) "SUB_ADMIN" else if (existing?.isAgent == true) "AGENT" else "USER"
        val updated = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                it.copy(role = roleStr)
            } else it
        }
        _managedUsers.value = updated
        saveUsersLocallyOnly(updated)
        patchUserField(target, JSONObject().put("role", roleStr))
    }

    fun updateSubAdminPermissions(email: String, permissions: SubAdminPermissions) {
        if (!isPrimeAdmin()) return
        val target = email.trim().lowercase()
        var targetUser: AuthUser? = null
        val updated = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                val u = it.copy(permissions = permissions)
                targetUser = u
                u
            } else it
        }
        _managedUsers.value = updated
        saveUsersLocallyOnly(updated)
        val u = targetUser ?: return
        val permObj = buildPermissionsJsonForUser(u)
        patchUserField(target, JSONObject().put("permissions_json", permObj.toString()))
    }

    /**
     * Generates a clean, unique random Gmail and 8-character password from just a User Name
     */
    fun generateRandomCredentialsFromName(nameInput: String): Pair<String, String> {
        val rawName = nameInput.trim()
        val cleanAlphaNum = rawName.lowercase()
            .replace("[^a-z0-9]".toRegex(), "")
            .take(12)
            .ifEmpty { "user" }

        var emailCandidate: String
        var attempt = 0
        do {
            val randDigits = (1000..9999).random()
            emailCandidate = "${cleanAlphaNum}${randDigits}@gmail.com"
            attempt++
        } while (attempt < 20 && (_managedUsers.value.any { it.email.equals(emailCandidate, ignoreCase = true) } || emailCandidate.equals(ADMIN_EMAIL, ignoreCase = true)))

        val prefixChars = cleanAlphaNum.take(2).padEnd(2, 'x')
        val passDigits = (100000..999999).random()
        val generatedPass = "${prefixChars}${passDigits}"
        return Pair(emailCandidate, generatedPass)
    }

    /**
     * Creates a managed user. Supports auto-generating Gmail + Password when only a Name is provided,
     * and supports creation by both Admin and Agent (tagging `createdByAgent` when created by an Agent).
     */
    fun createManagedUser(
        emailInput: String,
        passInput: String,
        nameInput: String = "User",
        createdByAgentEmail: String = ""
    ): Pair<Boolean, String> {
        if (!isAdmin() && !isAgent()) return Pair(false, "অনুমতি নেই")
        val cleanName = nameInput.trim().ifEmpty {
            emailInput.substringBefore("@").trim().ifEmpty { "User" }
        }
        val autoCreds = generateRandomCredentialsFromName(cleanName)
        val email = emailInput.trim().lowercase().ifEmpty { autoCreds.first }
        val pass = passInput.trim().ifEmpty { autoCreds.second }

        if (email.isEmpty() || pass.isEmpty()) {
            return Pair(false, "নাম বা ইমেইল প্রদান করুন")
        }

        val existing = _managedUsers.value.any { it.email.lowercase() == email }
        if (existing || email == ADMIN_EMAIL.lowercase()) {
            return Pair(false, "এই ইমেইলটি ইতিমধ্যে ব্যবহৃত হচ্ছে")
        }

        val effectiveAgentCreator = when {
            createdByAgentEmail.isNotBlank() -> createdByAgentEmail.trim().lowercase()
            isAgent() && !isPrimeAdmin() -> _currentEmail.value.trim().lowercase()
            else -> ""
        }

        val newUser = AuthUser(
            email = email,
            passwordHash = pass,
            name = cleanName,
            role = "USER",
            loginCount = 0,
            activeDevicesCount = 0,
            createdByAgent = effectiveAgentCreator
        )
        val newList = _managedUsers.value + newUser
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)

        val permObj = buildPermissionsJsonForUser(newUser)

        authScope.launch {
            try {
                val obj = JSONObject().apply {
                    put("email", email)
                    put("username", newUser.name)
                    put("password", pass)
                    put("role", "USER")
                    put("status", "ACTIVE")
                    put("balance", 0.0)
                    put("total_otps", 0)
                    put("otp_today", 0)
                    put("otp_7days", 0)
                    put("otp_30days", 0)
                    put("permissions_json", permObj.toString())
                    put("is_active", true)
                    put("is_banned", false)
                }
                val body = JSONArray().put(obj).toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?on_conflict=email")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(body)
                    .build()
                val resp = httpClient.newCall(req).execute()
                val ok = resp.isSuccessful
                resp.close()
                if (ok) {
                    syncFromCloud()
                }
            } catch (_: Exception) {}
        }
        return Pair(true, "$email|||$pass")
    }

    /**
     * Promotes or demotes a user as an Agent by email and configures their commission rule (e.g., 100 Today OTP = 10 Tk).
     */
    fun setAgentRoleByEmail(
        emailInput: String,
        isAgent: Boolean,
        commissionOtpTarget: Int = 100,
        commissionTkPerTarget: Double = 10.0,
        onComplete: ((Boolean, String) -> Unit)? = null
    ): Pair<Boolean, String> {
        if (!isPrimeAdmin()) {
            val err = "শুধুমাত্র প্রাইম এডমিন এজেন্ট নির্ধারণ করতে পারবেন"
            onComplete?.invoke(false, err)
            return Pair(false, err)
        }
        val target = emailInput.trim().lowercase()
        if (target.isEmpty()) {
            val err = "ইউজারের ইমেইল লিখুন"
            onComplete?.invoke(false, err)
            return Pair(false, err)
        }
        val existingUser = _managedUsers.value.firstOrNull {
            it.email.equals(target, ignoreCase = true) ||
            it.name.equals(target, ignoreCase = true) ||
            it.email.substringBefore("@").equals(target, ignoreCase = true)
        }
        if (existingUser == null) {
            val err = "এই ইমেইলে কোনো তৈরি করা ইউজার পাওয়া যায়নি!"
            onComplete?.invoke(false, err)
            return Pair(false, err)
        }

        val realEmail = existingUser.email.lowercase()
        val safeTargetOtps = commissionOtpTarget.coerceAtLeast(1)
        val safeCommTk = Math.round(commissionTkPerTarget.coerceAtLeast(0.0) * 100.0) / 100.0
        val newRole = if (isAgent) {
            if (existingUser.role == "SUB_ADMIN") "SUB_ADMIN" else "AGENT"
        } else {
            if (existingUser.role == "AGENT") "USER" else existingUser.role
        }

        var updatedUserObj: AuthUser? = null
        val updatedList = _managedUsers.value.map {
            if (it.email.lowercase() == realEmail) {
                val u = it.copy(
                    role = newRole,
                    isAgent = isAgent,
                    agentCommissionOtpTarget = safeTargetOtps,
                    agentCommissionTkPerTarget = safeCommTk
                )
                updatedUserObj = u
                u
            } else it
        }
        _managedUsers.value = updatedList
        saveUsersLocallyOnly(updatedList)

        val u = updatedUserObj ?: return Pair(false, "ইউজার আপডেট ব্যর্থ হয়েছে")
        val msg = if (isAgent) {
            "✓ ${u.name} (${u.email}) এখন এজেন্ট! কমিশন: $safeTargetOtps OTP = ৳${String.format(java.util.Locale.US, "%.2f", safeCommTk)}"
        } else {
            "✓ ${u.name}-এর এজেন্ট স্ট্যাটাস বাতিল করা হয়েছে"
        }
        val permObj = buildPermissionsJsonForUser(u)
        patchUserField(
            realEmail,
            JSONObject().apply {
                put("role", newRole)
                put("permissions_json", permObj.toString())
            }
        ) { _ ->
            onComplete?.invoke(true, msg)
        }
        return Pair(true, msg)
    }

    fun updateAgentCommissionRule(
        agentEmail: String,
        otpTarget: Int,
        commissionTk: Double,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!isPrimeAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val target = agentEmail.trim().lowercase()
        val safeOtpTarget = otpTarget.coerceAtLeast(1)
        val safeCommTk = Math.round(commissionTk.coerceAtLeast(0.0) * 100.0) / 100.0
        var updatedUserObj: AuthUser? = null
        val updatedList = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                val u = it.copy(
                    isAgent = true,
                    role = if (it.role == "USER") "AGENT" else it.role,
                    agentCommissionOtpTarget = safeOtpTarget,
                    agentCommissionTkPerTarget = safeCommTk
                )
                updatedUserObj = u
                u
            } else it
        }
        _managedUsers.value = updatedList
        saveUsersLocallyOnly(updatedList)
        val u = updatedUserObj ?: run {
            onComplete?.invoke(false)
            return
        }
        val permObj = buildPermissionsJsonForUser(u)
        patchUserField(
            target,
            JSONObject().apply {
                put("role", u.role)
                put("permissions_json", permObj.toString())
            },
            onComplete
        )
    }

    fun creditAgentCommissionToBalance(
        agentEmail: String,
        commissionAmountTk: Double,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!isPrimeAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val target = agentEmail.trim().lowercase()
        val agent = _managedUsers.value.firstOrNull { it.email.lowercase() == target } ?: run {
            onComplete?.invoke(false)
            return
        }
        val addAmt = Math.round(commissionAmountTk.coerceAtLeast(0.0) * 100.0) / 100.0
        if (addAmt <= 0.0) {
            onComplete?.invoke(false)
            return
        }
        val newBal = Math.round((agent.balanceTk + addAmt) * 100.0) / 100.0
        updateUserOtpAndBalance(
            email = agent.email,
            totalOtps = agent.totalOtps,
            balanceTk = newBal,
            today = agent.todayOtps,
            last7d = agent.last7DaysOtps,
            last30d = agent.last30DaysOtps,
            onComplete = onComplete
        )
    }

    /**
     * Updates per-user custom OTP rates across all 4 panels (Premium Numbers, Premium V2, Kop Engine 1, Kop Engine 2)
     */
    fun updateUserPanelRates(
        email: String,
        unixRate: Double?,
        premiumV2Rate: Double?,
        kop1Rate: Double?,
        kop2Rate: Double?,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val target = email.trim().lowercase()
        val cleanUnix = unixRate?.takeIf { it > 0.0 }?.let { Math.round(it * 100.0) / 100.0 }
        val cleanV2 = premiumV2Rate?.takeIf { it > 0.0 }?.let { Math.round(it * 100.0) / 100.0 }
        val cleanKop1 = kop1Rate?.takeIf { it > 0.0 }?.let { Math.round(it * 100.0) / 100.0 }
        val cleanKop2 = kop2Rate?.takeIf { it > 0.0 }?.let { Math.round(it * 100.0) / 100.0 }

        var updatedTargetUser: AuthUser? = null
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                val u = it.copy(
                    customUnixRate = cleanUnix,
                    customPremiumV2Rate = cleanV2,
                    customKop1Rate = cleanKop1,
                    customKop2Rate = cleanKop2
                )
                updatedTargetUser = u
                u
            } else it
        }
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)

        val u = updatedTargetUser ?: run {
            onComplete?.invoke(false)
            return
        }
        val permObj = buildPermissionsJsonForUser(u, adminEditedAt = System.currentTimeMillis())
        patchUserField(
            target,
            JSONObject().put("permissions_json", permObj.toString()),
            onComplete
        )
    }

    fun deleteManagedUser(email: String) {
        val targetEmail = email.trim().lowercase()
        val targetUser = _managedUsers.value.firstOrNull { it.email.lowercase() == targetEmail }
        val currentLoggedIn = _currentEmail.value.trim().lowercase()
        val canAgentDelete = isAgent() && targetUser != null && targetUser.createdByAgent.equals(currentLoggedIn, ignoreCase = true)
        if (!isAdmin() && !canAgentDelete) return

        val newList = _managedUsers.value.filter { it.email.lowercase() != targetEmail }
        saveUsersLocallyOnly(newList)
        _managedUsers.value = newList
        authScope.launch {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$targetEmail")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                val resp = httpClient.newCall(req).execute()
                resp.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Wipes and permanently deletes all users EXCEPT the Prime Admin (mdronyinfohelp@gmail.com).
     * Clears all non-admin credentials, balances, OTP records, withdrawal history, and uploaded numbers.
     */
    fun deleteAllNonAdminUsers(context: Context? = null, onComplete: ((Boolean) -> Unit)? = null) {
        if (!isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        _managedUsers.value = emptyList()
        saveUsersLocallyOnly(emptyList())
        wipeAllUserBalancesAndOtpHistory(context)
        wipeAllLiveFeedsAndOtpDatabase(context)
        UnixSmsManager.clearAllUploadedNumbers()
        PremiumNumberV2Manager.clearAllNumbers()

        authScope.launch {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=neq.$ADMIN_EMAIL")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun toggleUserBan(email: String) {
        val target = email.trim().lowercase()
        val targetUser = _managedUsers.value.firstOrNull { it.email.lowercase() == target }
        val currentLoggedIn = _currentEmail.value.trim().lowercase()
        val canAgentBan = isAgent() && targetUser != null && targetUser.createdByAgent.equals(currentLoggedIn, ignoreCase = true)
        if (!isAdmin() && !canAgentBan) return

        var newStatus = "ACTIVE"
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                val updatedBanned = !it.isBanned
                newStatus = if (updatedBanned) "BANNED" else if (it.isActive) "ACTIVE" else "INACTIVE"
                it.copy(isBanned = updatedBanned)
            } else it
        }
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)
        patchUserField(target, JSONObject().put("status", newStatus))
    }

    fun toggleUserStatus(email: String) {
        val target = email.trim().lowercase()
        val targetUser = _managedUsers.value.firstOrNull { it.email.lowercase() == target }
        val currentLoggedIn = _currentEmail.value.trim().lowercase()
        val canAgentToggle = isAgent() && targetUser != null && targetUser.createdByAgent.equals(currentLoggedIn, ignoreCase = true)
        if (!isAdmin() && !canAgentToggle) return

        var newStatus = "ACTIVE"
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                val updatedActive = !it.isActive
                newStatus = if (updatedActive) "ACTIVE" else "INACTIVE"
                it.copy(isActive = updatedActive)
            } else it
        }
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)
        patchUserField(target, JSONObject().put("status", newStatus))
    }

    fun resetUserLogins(email: String) {
        if (!isAdmin()) return
        val target = email.trim().lowercase()
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == target) it.copy(loginCount = 0, activeDevicesCount = 0) else it
        }
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)
        patchUserField(target, JSONObject().put("device_id", "").put("device_model", ""))
    }

    fun changeUserPassword(email: String, newPass: String): Boolean {
        if (!isAdmin()) return false
        val cleanPass = newPass.trim()
        if (cleanPass.isEmpty()) return false
        val target = email.trim().lowercase()
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == target) it.copy(passwordHash = cleanPass) else it
        }
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)
        patchUserField(target, JSONObject().put("password", cleanPass))
        return true
    }

    private val recentAdminEdits = java.util.concurrent.ConcurrentHashMap<String, AuthUser>()
    private val recentAdminEditTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun updateUserOtpAndBalance(
        email: String,
        totalOtps: Int,
        balanceTk: Double,
        today: Int,
        last7d: Int,
        last30d: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val target = email.trim().lowercase()
        val cleanTotal = maxOf(0, totalOtps)
        val cleanBal = maxOf(0.0, Math.round(balanceTk * 100.0) / 100.0)
        val cleanToday = maxOf(0, today)
        val clean7d = maxOf(0, last7d)
        val clean30d = maxOf(0, last30d)
        val nowTs = System.currentTimeMillis()

        var updatedTargetUser: AuthUser? = null
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                val u = it.copy(
                    totalOtps = cleanTotal,
                    balanceTk = cleanBal,
                    todayOtps = cleanToday,
                    last7DaysOtps = clean7d,
                    last30DaysOtps = clean30d
                )
                updatedTargetUser = u
                u
            } else it
        }
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)

        if (updatedTargetUser != null) {
            recentAdminEdits[target] = updatedTargetUser!!
            recentAdminEditTimestamps[target] = nowTs
        }

        // If edited user is logged in on this device, immediately apply to WithdrawalManager & OtpHistoryManager
        if (target == _currentEmail.value.trim().lowercase()) {
            WithdrawalManager.setAdminAssignedBalance(cleanBal)
            OtpHistoryManager.setAdminAssignedOtps(cleanTotal, cleanToday, clean7d, clean30d)
        }

        val userForPerms = updatedTargetUser ?: return
        val permObj = buildPermissionsJsonForUser(userForPerms, adminEditedAt = nowTs)

        patchUserField(target, JSONObject().apply {
            put("total_otps", cleanTotal)
            put("balance", cleanBal)
            put("total_balance", cleanBal)
            put("otp_today", cleanToday)
            put("otp_7days", clean7d)
            put("otp_30days", clean30d)
            put("permissions_json", permObj.toString())
        }, onComplete)
    }

    /**
     * Pulls the logged-in user's latest cloud balance and OTP count set by Admin from app_users
     */
    fun syncCurrentUserProfileFromCloud(context: Context? = null) {
        val email = _currentEmail.value.trim().lowercase()
        if (email.isEmpty()) return
        authScope.launch {
            try {
                val encEmail = java.net.URLEncoder.encode(email, "UTF-8")
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=ilike.$encEmail&select=*")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val bodyStr = resp.body?.string() ?: return@use
                    val arr = JSONArray(bodyStr)
                    if (arr.length() == 0) return@use
                    val obj = arr.getJSONObject(0)
                    val cloudBalance = obj.optDouble("balance", 0.0)
                    val cloudTotalOtps = obj.optInt("total_otps", 0)
                    val cloudToday = obj.optInt("otp_today", 0)
                    val cloud7d = obj.optInt("otp_7days", 0)
                    val cloud30d = obj.optInt("otp_30days", 0)
                    val statusStr = obj.optString("status", "ACTIVE")
                    val cloudRole = obj.optString("role", "USER")
                    val permObj = try {
                        JSONObject(obj.optString("permissions_json", "{}"))
                    } catch (_: Exception) { JSONObject() }
                    val isAgentFlag = permObj.optBoolean("isAgent", cloudRole == "AGENT")
                    val commOtpTarget = permObj.optInt("agentCommissionOtpTarget", 100).coerceAtLeast(1)
                    val commTkTarget = permObj.optDouble("agentCommissionTkPerTarget", 10.0).coerceAtLeast(0.0)
                    val cUnix = permObj.optDouble("customUnixRate", -1.0).takeIf { it > 0.0 }
                    val cV2 = permObj.optDouble("customPremiumV2Rate", -1.0).takeIf { it > 0.0 }
                    val cKop1 = permObj.optDouble("customKop1Rate", -1.0).takeIf { it > 0.0 }
                    val cKop2 = permObj.optDouble("customKop2Rate", -1.0).takeIf { it > 0.0 }

                    withContext(Dispatchers.Main) {
                        if ((statusStr == "BANNED" || statusStr == "INACTIVE") && !isPrimeAdmin()) {
                            logout()
                            return@withContext
                        }

                        val effectiveRole = if (isAgentFlag && cloudRole == "USER") "AGENT" else cloudRole
                        if (!isPrimeAdmin() && _currentUserRole.value != effectiveRole) {
                            _currentUserRole.value = effectiveRole
                            prefs?.edit()?.putString(KEY_LOGGED_IN_ROLE, effectiveRole)?.apply()
                        }

                        _managedUsers.value = _managedUsers.value.map { u ->
                            if (u.email.equals(email, ignoreCase = true)) {
                                u.copy(
                                    role = effectiveRole,
                                    isAgent = isAgentFlag,
                                    agentCommissionOtpTarget = commOtpTarget,
                                    agentCommissionTkPerTarget = commTkTarget,
                                    customUnixRate = cUnix,
                                    customPremiumV2Rate = cV2,
                                    customKop1Rate = cKop1,
                                    customKop2Rate = cKop2,
                                    balanceTk = cloudBalance,
                                    totalOtps = cloudTotalOtps,
                                    todayOtps = cloudToday,
                                    last7DaysOtps = cloud7d,
                                    last30DaysOtps = cloud30d
                                )
                            } else u
                        }

                        val currentAvailable = WithdrawalManager.state.value.availableBalanceTk
                        val st = OtpHistoryManager.state.value

                        // When admin edits user balance or OTP count in cloud (including setting to 0), apply it immediately
                        if (Math.abs(currentAvailable - cloudBalance) > 0.005) {
                            WithdrawalManager.setAdminAssignedBalance(cloudBalance)
                        }
                        if (cloudTotalOtps != st.totalOtpsAllTime ||
                            cloudToday != st.todayOtps ||
                            cloud7d != st.last7DaysOtps ||
                            cloud30d != st.last30DaysOtps
                        ) {
                            OtpHistoryManager.setAdminAssignedOtps(cloudTotalOtps, cloudToday, cloud7d, cloud30d)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Wipes all existing user balances, OTP counts, and history records across all accounts
     * and in Supabase, establishing clean, strictly isolated per-user accounting.
     */
    fun wipeAllUserBalancesAndOtpHistory(context: Context? = null, onComplete: ((Boolean) -> Unit)? = null) {
        if (!isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        val resetList = _managedUsers.value.map {
            it.copy(
                balanceTk = 0.0,
                totalOtps = 0,
                todayOtps = 0,
                last7DaysOtps = 0,
                last30DaysOtps = 0
            )
        }
        _managedUsers.value = resetList
        saveUsersLocallyOnly(resetList)

        // Clear local OTP history, live feeds, and withdrawal state
        UnixSmsManager.clearLiveDlrLogs()
        VirtualNumberManager.clearBroadcastFeed()
        KopEngine2Manager.clearBroadcastFeed()
        PremiumNumberV2Manager.clearLiveConsoleFeed()
        OtpHistoryManager.wipeAllHistory(context)
        WithdrawalManager.wipeAllBalances(context)

        // Cloud wipe: Patch all users in Supabase to 0 balance & OTP counts
        authScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("balance", 0.0)
                    put("total_otps", 0)
                    put("otp_today", 0)
                    put("otp_7days", 0)
                    put("otp_30days", 0)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?id=gt.0")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute().close()

                // Delete past records from otp_history
                val delOtp = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/otp_history?id=gt.0")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                httpClient.newCall(delOtp).execute().close()

                // Delete past records from withdraw_history
                val delWd = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/withdraw_history?id=gt.0")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                httpClient.newCall(delWd).execute().close()

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    /**
     * Clears all live items from Live DR (DLR), Live Console, Live OTP, and Live Radar,
     * and permanently deletes all OTP history records from the Supabase database.
     */
    fun wipeAllLiveFeedsAndOtpDatabase(context: Context? = null, onComplete: ((Boolean) -> Unit)? = null) {
        if (!isAdmin()) {
            onComplete?.invoke(false)
            return
        }
        UnixSmsManager.clearLiveDlrLogs()
        VirtualNumberManager.clearBroadcastFeed()
        KopEngine2Manager.clearBroadcastFeed()
        PremiumNumberV2Manager.clearLiveConsoleFeed()
        OtpHistoryManager.wipeAllHistory(context)

        authScope.launch {
            try {
                // Delete all records from otp_history in Supabase
                val delOtp = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/otp_history?id=gt.0")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                httpClient.newCall(delOtp).execute().close()

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    private const val KEY_BROADCAST_MESSAGE = "admin_broadcast_message"
    private val _latestBroadcast = MutableStateFlow<String?>(null)
    val latestBroadcast: StateFlow<String?> = _latestBroadcast.asStateFlow()

    fun broadcastNotification(message: String) {
        if (!isAdmin()) return
        prefs?.edit()?.putString(KEY_BROADCAST_MESSAGE, message)?.apply()
        _latestBroadcast.value = message
    }

    fun syncCurrentUserDataToCloud(
        totalOtps: Int,
        balanceTk: Double,
        todayOtps: Int,
        last7d: Int,
        last30d: Int
    ) {
        val email = _currentEmail.value.trim().lowercase()
        if (email.isEmpty() || email == ADMIN_EMAIL.lowercase()) return
        val nowTime = System.currentTimeMillis()
        val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        // Immediately update local managedUsers list so UI is instant
        _managedUsers.update { list ->
            list.map { u ->
                if (u.email.equals(email, ignoreCase = true)) {
                    u.copy(
                        totalOtps = maxOf(u.totalOtps, totalOtps),
                        balanceTk = balanceTk,
                        todayOtps = maxOf(u.todayOtps, todayOtps),
                        last7DaysOtps = maxOf(u.last7DaysOtps, last7d),
                        last30DaysOtps = maxOf(u.last30DaysOtps, last30d),
                        lastActive = nowTime,
                        lastDeviceName = deviceModel,
                        isOnline = true
                    )
                } else u
            }
        }
        saveUsersLocallyOnly(_managedUsers.value)

        authScope.launch {
            try {
                val encEmail = java.net.URLEncoder.encode(email, "UTF-8")
                val payload = JSONObject().apply {
                    put("total_otps", totalOtps)
                    put("balance", balanceTk)
                    put("otp_today", todayOtps)
                    put("otp_7days", last7d)
                    put("otp_30days", last30d)
                    put("last_active", nowTime)
                    put("device_model", deviceModel)
                    put("status", "ONLINE")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=ilike.$encEmail")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
                val resp = httpClient.newCall(req).execute()
                resp.close()
            } catch (_: Exception) {}
        }
    }

    fun revokeDeviceSession(email: String, deviceId: String) {
        val target = email.lowercase()
        val list = _managedUsers.value.map { user ->
            if (user.email.lowercase() == target) {
                val updatedSessions = user.deviceSessions.map { dev ->
                    if (dev.deviceId == deviceId) dev.copy(isRevoked = true, isOnline = false) else dev
                }
                user.copy(
                    deviceSessions = updatedSessions,
                    activeDevicesCount = updatedSessions.count { it.isOnline && !it.isRevoked }
                )
            } else user
        }
        saveUsers(list)
    }

    fun restoreDeviceSession(email: String, deviceId: String) {
        val target = email.lowercase()
        val list = _managedUsers.value.map { user ->
            if (user.email.lowercase() == target) {
                val updatedSessions = user.deviceSessions.map { dev ->
                    if (dev.deviceId == deviceId) dev.copy(isRevoked = false) else dev
                }
                user.copy(
                    deviceSessions = updatedSessions,
                    activeDevicesCount = updatedSessions.count { it.isOnline && !it.isRevoked }
                )
            } else user
        }
        saveUsers(list)
    }

    fun toggleUserSectionPermission(email: String, sectionKey: String) {
        val target = email.trim().lowercase()
        var updatedSections: List<String> = emptyList()
        val list = _managedUsers.value.map { user ->
            if (user.email.lowercase() == target) {
                val current = user.disabledSections
                val updated = if (current.contains(sectionKey)) {
                    current.filterNot { it == sectionKey }
                } else {
                    current + sectionKey
                }
                updatedSections = updated
                user.copy(disabledSections = updated)
            } else user
        }
        _managedUsers.value = list
        saveUsersLocallyOnly(list)
        patchUserDisabledSections(target, updatedSections)
    }

    fun isSectionAllowedForCurrentUser(sectionKey: String): Boolean {
        if (isPrimeAdmin() || isSubAdmin()) return true
        val current = _currentEmail.value.lowercase()
        val user = _managedUsers.value.firstOrNull { it.email.lowercase() == current } ?: return true
        return !user.disabledSections.contains(sectionKey)
    }

    fun sendHeartbeat() {
        val email = _currentEmail.value.trim().lowercase()
        if (email.isEmpty() || email == ADMIN_EMAIL.lowercase()) return

        // Auto-detect if current device was revoked by admin
        val user = _managedUsers.value.firstOrNull { it.email.lowercase() == email }
        if (user != null && currentDeviceId.isNotEmpty()) {
            val devSession = user.deviceSessions.firstOrNull { it.deviceId == currentDeviceId }
            if (devSession != null && devSession.isRevoked) {
                logout()
                return
            }
        }

        authScope.launch {
            try {
                val encEmail = java.net.URLEncoder.encode(email, "UTF-8")
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val nowTime = System.currentTimeMillis()
                val payload = JSONObject().apply {
                    put("last_active", nowTime)
                    put("device_model", deviceModel)
                    put("status", "ONLINE")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=ilike.$encEmail")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute().close()

                // Update online status in local memory
                _managedUsers.value = _managedUsers.value.map { u ->
                    if (u.email.lowercase() == email) {
                        u.copy(
                            isOnline = true,
                            lastActive = nowTime
                        )
                    } else u
                }
            } catch (_: Exception) {}
        }
    }

    fun resetUserDeviceBinding(targetEmail: String, onComplete: ((Boolean) -> Unit)? = null) {
        val target = targetEmail.trim().lowercase()
        if (target.isBlank() || target == ADMIN_EMAIL.lowercase()) {
            onComplete?.invoke(false)
            return
        }
        authScope.launch {
            var success = false
            try {
                val payload = JSONObject().apply {
                    put("device_id", "")
                    put("device_model", "")
                    put("last_active", 0L)
                    put("status", "ACTIVE")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$target")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
                val resp = httpClient.newCall(req).execute()
                success = resp.isSuccessful
                resp.close()
            } catch (_: Exception) {}

            // Update in-memory user list
            val updatedList = _managedUsers.value.map { u ->
                if (u.email.lowercase() == target) {
                    u.copy(
                        boundDeviceId = null,
                        boundDeviceModel = null,
                        boundAt = 0L,
                        deviceSessions = emptyList()
                    )
                } else u
            }
            _managedUsers.value = updatedList
            saveUsersLocallyOnly(updatedList)

            // If locally this device was bound to that email, clear it
            val localBound = prefs?.getString("bound_device_email", "") ?: ""
            if (localBound.equals(target, ignoreCase = true)) {
                prefs?.edit()?.remove("bound_device_email")?.apply()
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete?.invoke(success)
            }
        }
    }
}
