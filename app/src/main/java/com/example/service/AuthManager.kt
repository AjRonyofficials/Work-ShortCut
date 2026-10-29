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
    val role: String = "USER", // "PRIME_ADMIN", "SUB_ADMIN", or "USER"
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
    val isPinResetRequested: Boolean = false
)

object AuthManager {

    private const val PREFS_NAME = "work_shortcut_auth"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_LOGGED_IN_EMAIL = "logged_in_email"
    private const val KEY_LOGGED_IN_ROLE = "logged_in_role"
    private const val KEY_USERS_LIST = "users_list_json"

    const val ADMIN_EMAIL = "mdronyinfohelp@gmail.com"
    const val ADMIN_PASS = "Ronyvai2026"
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

    private const val SUPABASE_BASE_URL = "https://fbtqjpuclmnsiqehalow.supabase.co/rest/v1"
    private const val SUPABASE_KEY = "sb_publishable_zBqxIMDPy4yHEuAdJJUlxg_wSCRF9u5"
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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
                    kotlinx.coroutines.delay(15000L) // every 15s
                    try {
                        if (!com.example.util.AppLifecycleTracker.canRunBackgroundWork()) {
                            continue
                        }
                        if (_isLoggedIn.value && !isPrimeAdmin()) {
                            sendHeartbeat()
                        }
                        if (isAdmin()) {
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

                list.add(
                    AuthUser(
                        email = obj.getString("email"),
                        passwordHash = obj.getString("password"),
                        name = obj.optString("name", "User"),
                        role = obj.optString("role", "USER"),
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
                        isPinResetRequested = obj.optBoolean("isPinResetRequested", false)
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

    private fun syncFromCloudInternal(): Int {
        try {
            val req = okhttp3.Request.Builder()
                .url("$SUPABASE_BASE_URL/app_users?select=*")
                .header("apikey", SUPABASE_KEY)
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val arr = JSONArray(bodyStr)
                val cloudUsers = mutableListOf<AuthUser>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
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
                    val now = System.currentTimeMillis()
                    val isOnline = (now - lastActive) < 180_000L && lastActive > 0L && statusStr != "OFFLINE"

                    // Find any existing local device sessions to preserve
                    val existingUser = _managedUsers.value.firstOrNull { it.email.equals(obj.getString("email"), ignoreCase = true) }
                    val devSessions = existingUser?.deviceSessions ?: emptyList()
                    val disabledSecs = try {
                        val secStr = obj.optString("disabled_sections_json", "")
                        if (secStr.isNotBlank() && secStr != "null" && secStr != "[]") {
                            val sArr = JSONArray(secStr)
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

                    cloudUsers.add(
                        AuthUser(
                            email = obj.getString("email"),
                            passwordHash = obj.getString("password"),
                            name = obj.optString("username", "User"),
                            role = obj.optString("role", "USER"),
                            createdAt = System.currentTimeMillis(),
                            isActive = statusStr != "INACTIVE" && statusStr != "BANNED",
                            isBanned = statusStr == "BANNED",
                            loginCount = obj.optInt("loginCount", 1),
                            activeDevicesCount = if (devSessions.isNotEmpty()) devSessions.count { it.isOnline && !it.isRevoked } else (if (isOnline) 1 else 0),
                            lastLoginAt = if (lastActive > 0L) lastActive else obj.optLong("lastLoginAt", 0L),
                            lastDeviceName = devModel,
                            totalOtps = obj.optInt("total_otps", 0),
                            balanceTk = obj.optDouble("balance", 0.0),
                            todayOtps = obj.optInt("otp_today", 0),
                            last7DaysOtps = obj.optInt("otp_7days", 0),
                            last30DaysOtps = obj.optInt("otp_30days", 0),
                            lastActive = lastActive,
                            isOnline = isOnline,
                            permissions = perms,
                            deviceSessions = devSessions,
                            disabledSections = disabledSecs,
                            boundDeviceId = boundDevId,
                            boundDeviceModel = boundDevModel,
                            boundAt = boundAt,
                            isPinResetRequested = obj.optBoolean("pin_reset_requested", false)
                        )
                    )
                }

                // Cloud users from Supabase are the source of truth
                _managedUsers.value = cloudUsers
                saveUsersLocallyOnly(cloudUsers)
                return cloudUsers.size
            }
        } catch (_: Exception) {}
        return _managedUsers.value.size
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

            val permObj = JSONObject().apply {
                put("canUploadNumbers", u.permissions.canUploadNumbers)
                put("canDeleteNumbers", u.permissions.canDeleteNumbers)
                put("canUpdateOtpRate", u.permissions.canUpdateOtpRate)
                put("canManageWithdrawals", u.permissions.canManageWithdrawals)
                put("canViewLiveCdr", u.permissions.canViewLiveCdr)
                put("canBroadcastNotify", u.permissions.canBroadcastNotify)
            }
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
        authScope.launch {
            try {
                val encEmail = java.net.URLEncoder.encode(clean, "UTF-8")
                val payload = JSONObject().apply {
                    put("pin_reset_requested", true)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$encEmail")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute()
            } catch (_: Exception) {}
        }
    }

    fun resetUserPin(userEmail: String, onComplete: ((Boolean) -> Unit)? = null) {
        val clean = userEmail.trim().lowercase()
        if (clean.isBlank()) return
        WithdrawalManager.clearSecurityPin(clean)
        _managedUsers.update { list ->
            list.map { if (it.email.equals(clean, ignoreCase = true)) it.copy(isPinResetRequested = false) else it }
        }
        saveUsersLocallyOnly(_managedUsers.value)
        authScope.launch {
            try {
                val encEmail = java.net.URLEncoder.encode(clean, "UTF-8")
                val payload = JSONObject().apply {
                    put("pin_reset_requested", false)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$encEmail")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
                val resp = httpClient.newCall(req).execute()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(resp.isSuccessful)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun patchUserField(email: String, fields: JSONObject) {
        val target = email.trim().lowercase()
        if (target.isBlank() || target == ADMIN_EMAIL.lowercase()) return
        authScope.launch {
            try {
                val body = fields.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$target")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute()
            } catch (_: Exception) {}
        }
    }

    fun patchUserDisabledSections(email: String, sections: List<String>) {
        val sArr = JSONArray()
        sections.forEach { sArr.put(it) }
        patchUserField(email, JSONObject().apply {
            put("disabled_sections_json", sArr.toString())
        })
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
                            put("bound_device_id", myDeviceId)
                            put("bound_device_model", deviceName)
                            put("bound_at", nowTime)
                            put("device_id", myDeviceId)
                            put("device_model", deviceName)
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
                VirtualNumberManager.onUserLoggedIn(user.email)
                UnixSmsManager.onUserLoggedIn(user.email)
                KopEngine2Manager.onUserLoggedIn(user.email)
                PremiumNumberV2Manager.onUserLoggedIn(user.email)
                if (user.totalOtps > 0 || user.balanceTk > 0.0) {
                    OtpHistoryManager.restoreUserHistoryIfEmpty(
                        totalOtps = user.totalOtps,
                        todayOtps = user.todayOtps,
                        last7d = user.last7DaysOtps,
                        last30d = user.last30DaysOtps
                    )
                    WithdrawalManager.restoreUserBalanceIfEmpty(user.balanceTk)
                }
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

    fun isAdmin(): Boolean {
        return isPrimeAdmin() || isSubAdmin()
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
        val roleStr = if (isSubAdmin) "SUB_ADMIN" else "USER"
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
        val updated = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                it.copy(permissions = permissions)
            } else it
        }
        _managedUsers.value = updated
        saveUsersLocallyOnly(updated)
        val permObj = JSONObject().apply {
            put("canUploadNumbers", permissions.canUploadNumbers)
            put("canDeleteNumbers", permissions.canDeleteNumbers)
            put("canUpdateOtpRate", permissions.canUpdateOtpRate)
            put("canManageWithdrawals", permissions.canManageWithdrawals)
            put("canViewLiveCdr", permissions.canViewLiveCdr)
            put("canBroadcastNotify", permissions.canBroadcastNotify)
        }
        patchUserField(target, JSONObject().put("permissions_json", permObj.toString()))
    }

    // Admin operations
    fun createManagedUser(emailInput: String, passInput: String, nameInput: String = "User"): Pair<Boolean, String> {
        if (!isAdmin()) return Pair(false, "অনুমতি নেই")
        val email = emailInput.trim().lowercase()
        val pass = passInput.trim()

        if (email.isEmpty() || pass.isEmpty()) {
            return Pair(false, "ইমেইল ও পাসওয়ার্ড আবশ্যক")
        }

        val existing = _managedUsers.value.any { it.email.lowercase() == email }
        if (existing || email == ADMIN_EMAIL.lowercase()) {
            return Pair(false, "এই ইমেইলটি ইতিমধ্যে ব্যবহৃত হচ্ছে")
        }

        val newUser = AuthUser(
            email = email,
            passwordHash = pass,
            name = nameInput.trim().ifEmpty { "User" },
            role = "USER",
            loginCount = 0,
            activeDevicesCount = 0
        )
        val newList = _managedUsers.value + newUser
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)

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
                    put("disabled_sections_json", "[]")
                    put("permissions_json", "{}")
                }
                val body = JSONArray().put(obj).toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?on_conflict=email")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(body)
                    .build()
                httpClient.newCall(req).execute()
            } catch (_: Exception) {}
        }
        return Pair(true, "নতুন ইউজার সফলভাবে তৈরি হয়েছে!")
    }

    fun deleteManagedUser(email: String) {
        if (!isAdmin()) return
        val targetEmail = email.trim().lowercase()
        val newList = _managedUsers.value.filter { it.email.lowercase() != targetEmail }
        saveUsersLocallyOnly(newList)
        _managedUsers.value = newList
        authScope.launch {
            try {
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$targetEmail")
                    .header("apikey", SUPABASE_KEY)
                    .delete()
                    .build()
                httpClient.newCall(req).execute()
            } catch (_: Exception) {}
        }
    }

    fun toggleUserBan(email: String) {
        if (!isAdmin()) return
        val target = email.trim().lowercase()
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
        if (!isAdmin()) return
        val target = email.trim().lowercase()
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
        patchUserField(target, JSONObject().put("loginCount", 0))
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

    fun updateUserOtpAndBalance(
        email: String,
        totalOtps: Int,
        balanceTk: Double,
        today: Int,
        last7d: Int,
        last30d: Int
    ) {
        if (!isAdmin()) return
        val target = email.trim().lowercase()
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == target) {
                it.copy(
                    totalOtps = totalOtps,
                    balanceTk = balanceTk,
                    todayOtps = today,
                    last7DaysOtps = last7d,
                    last30DaysOtps = last30d
                )
            } else it
        }
        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)
        patchUserField(target, JSONObject().apply {
            put("total_otps", totalOtps)
            put("balance", balanceTk)
            put("otp_today", today)
            put("otp_7days", last7d)
            put("otp_30days", last30d)
        })
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
        authScope.launch {
            try {
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val payload = JSONObject().apply {
                    put("total_otps", totalOtps)
                    put("balance", balanceTk)
                    put("otp_today", todayOtps)
                    put("otp_7days", last7d)
                    put("otp_30days", last30d)
                    put("last_active", System.currentTimeMillis())
                    put("device_model", deviceModel)
                    put("status", "ONLINE")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$email")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute()
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
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                val st = OtpHistoryManager.state.value
                val bal = WithdrawalManager.state.value.availableBalanceTk
                val payload = JSONObject().apply {
                    put("last_active", System.currentTimeMillis())
                    put("device_model", deviceModel)
                    put("status", "ONLINE")
                    put("total_otps", st.totalOtpsAllTime)
                    put("balance", bal)
                    put("otp_today", st.todayOtps)
                    put("otp_7days", st.last7DaysOtps)
                    put("otp_30days", st.last30DaysOtps)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$email")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                httpClient.newCall(req).execute()

                // Update local representation in memory as well
                _managedUsers.value = _managedUsers.value.map { u ->
                    if (u.email.lowercase() == email) {
                        u.copy(
                            totalOtps = st.totalOtpsAllTime,
                            balanceTk = bal,
                            todayOtps = st.todayOtps,
                            last7DaysOtps = st.last7DaysOtps,
                            last30DaysOtps = st.last30DaysOtps,
                            isOnline = true,
                            lastActive = System.currentTimeMillis()
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
                    put("bound_device_id", "")
                    put("bound_device_model", "")
                    put("bound_at", 0L)
                    put("device_id", "")
                    put("status", "ACTIVE")
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_users?email=eq.$target")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .patch(body)
                    .build()
                val resp = httpClient.newCall(req).execute()
                success = resp.isSuccessful
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
