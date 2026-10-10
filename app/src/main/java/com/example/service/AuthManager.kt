package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val permissions: SubAdminPermissions = SubAdminPermissions()
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

    private const val CLOUD_OBJECT_URL = "https://api.restful-api.dev/objects/ff808181a09d98f701a121d19e7c3002"
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val authScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val DEFAULT_SEEDED_USERS = listOf(
        AuthUser(
            email = "nafis2026@gmail.com",
            passwordHash = "Nafis2026",
            name = "Nafis",
            role = "USER",
            isActive = true
        ),
        AuthUser(
            email = "sumaiya2026@gmail.com",
            passwordHash = "Sumaiya2026",
            name = "Sumaiya",
            role = "USER",
            isActive = true
        ),
        AuthUser(
            email = "user2026@gmail.com",
            passwordHash = "User2026",
            name = "General User",
            role = "USER",
            isActive = true
        )
    )

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isLoggedIn.value = prefs?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
            _currentEmail.value = prefs?.getString(KEY_LOGGED_IN_EMAIL, "") ?: ""
            _currentUserRole.value = prefs?.getString(KEY_LOGGED_IN_ROLE, "USER") ?: "USER"
            loadUsers()
            // Background sync with cloud
            authScope.launch {
                syncFromCloud()
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
                        permissions = perms
                    )
                )
            }
        } catch (_: Exception) {}

        // Guarantee seeded accounts (including nafis2026@gmail.com) are always present
        for (defaultUser in DEFAULT_SEEDED_USERS) {
            if (list.none { it.email.equals(defaultUser.email, ignoreCase = true) }) {
                list.add(defaultUser)
            }
        }

        _managedUsers.value = list
    }

    fun syncFromCloud() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            val t = Thread { syncFromCloudInternal() }
            t.start()
            try {
                t.join(3500)
            } catch (_: Exception) {}
        } else {
            syncFromCloudInternal()
        }
    }

    private fun syncFromCloudInternal() {
        try {
            val req = okhttp3.Request.Builder()
                .url(CLOUD_OBJECT_URL)
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val bodyStr = resp.body?.string() ?: ""
                val rootObj = JSONObject(bodyStr)
                val dataObj = rootObj.optJSONObject("data")
                val usersJsonStr = dataObj?.optString("users_json", "") ?: ""
                if (usersJsonStr.isNotEmpty() && usersJsonStr != "[]") {
                    val arr = JSONArray(usersJsonStr)
                    val cloudUsers = mutableListOf<AuthUser>()
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
                        } else SubAdminPermissions()

                        cloudUsers.add(
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
                                permissions = perms
                            )
                        )
                    }

                    // Merge cloud users with local users
                    val currentList = _managedUsers.value.toMutableList()
                    for (cu in cloudUsers) {
                        val idx = currentList.indexOfFirst { it.email.equals(cu.email, ignoreCase = true) }
                        if (idx >= 0) {
                            // Update existing with cloud if newer or merge
                            currentList[idx] = cu
                        } else {
                            currentList.add(cu)
                        }
                    }
                    for (du in DEFAULT_SEEDED_USERS) {
                        if (currentList.none { it.email.equals(du.email, ignoreCase = true) }) {
                            currentList.add(du)
                        }
                    }
                    _managedUsers.value = currentList
                    saveUsersLocallyOnly(currentList)
                }
            }
        } catch (_: Exception) {}
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
            arr.put(obj)
        }
        return arr
    }

    private fun syncToCloud(list: List<AuthUser>) {
        authScope.launch {
            try {
                val arr = serializeUsersToJson(list)
                val putPayload = JSONObject().apply {
                    put("name", "work_shortcut_authorized_users")
                    put("data", JSONObject().apply {
                        put("users_json", arr.toString())
                    })
                }
                val body = putPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = okhttp3.Request.Builder()
                    .url(CLOUD_OBJECT_URL)
                    .put(body)
                    .build()
                httpClient.newCall(req).execute()
            } catch (_: Exception) {}
        }
    }

    private fun saveUsers(list: List<AuthUser>) {
        _managedUsers.value = list
        saveUsersLocallyOnly(list)
        syncToCloud(list)
    }

    /**
     * Checks credentials against Admin master account OR admin-created managed users
     */
    fun login(emailInput: String, passInput: String): Pair<Boolean, String> {
        val email = emailInput.trim().lowercase()
        val pass = passInput.trim()

        if (email.isEmpty() || pass.isEmpty()) {
            return Pair(false, "ইমেইল ও পাসওয়ার্ড প্রদান করুন")
        }

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

        // 2. Check Managed Users created by Admin
        var user = _managedUsers.value.firstOrNull { it.email.lowercase() == email }
        if (user == null) {
            // Attempt fast cloud sync before rejecting
            try {
                syncFromCloud()
                user = _managedUsers.value.firstOrNull { it.email.lowercase() == email }
            } catch (_: Exception) {}
        }

        if (user != null) {
            if (user.isBanned) {
                return Pair(false, "আপনার অ্যাকাউন্টটি এডমিন কর্তৃক ব্যান (Ban) করা হয়েছে! যোগাযোগ: $TELEGRAM_CONTACT")
            }
            if (!user.isActive) {
                return Pair(false, "আপনার অ্যাকাউন্টটি নিষ্ক্রিয় (Inactive)। এডমিনের সাথে যোগাযোগ করুন: $TELEGRAM_CONTACT")
            }
            if (user.passwordHash == pass) {
                // Track device and login count
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                val newLoginCount = user.loginCount + 1
                val newActiveDevices = maxOf(1, user.activeDevicesCount + 1)
                val updatedUser = user.copy(
                    loginCount = newLoginCount,
                    activeDevicesCount = newActiveDevices,
                    lastLoginAt = System.currentTimeMillis(),
                    lastDeviceName = deviceName
                )

                val updatedList = _managedUsers.value.map {
                    if (it.email.lowercase() == email) updatedUser else it
                }
                saveUsers(updatedList)

                _isLoggedIn.value = true
                _currentEmail.value = user.email
                _currentUserRole.value = user.role

                prefs?.edit()
                    ?.putBoolean(KEY_IS_LOGGED_IN, true)
                    ?.putString(KEY_LOGGED_IN_EMAIL, user.email)
                    ?.putString(KEY_LOGGED_IN_ROLE, user.role)
                    ?.apply()

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
            // Decrement active devices count
            val updated = _managedUsers.value.map {
                if (it.email.lowercase() == current) {
                    it.copy(activeDevicesCount = maxOf(0, it.activeDevicesCount - 1))
                } else it
            }
            saveUsers(updated)
        }

        _isLoggedIn.value = false
        _currentEmail.value = ""
        _currentUserRole.value = "USER"
        prefs?.edit()
            ?.putBoolean(KEY_IS_LOGGED_IN, false)
            ?.putString(KEY_LOGGED_IN_EMAIL, "")
            ?.putString(KEY_LOGGED_IN_ROLE, "USER")
            ?.apply()
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
        val updated = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) {
                it.copy(role = if (isSubAdmin) "SUB_ADMIN" else "USER")
            } else it
        }
        saveUsers(updated)
    }

    fun updateSubAdminPermissions(email: String, permissions: SubAdminPermissions) {
        if (!isPrimeAdmin()) return
        val updated = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) {
                it.copy(permissions = permissions)
            } else it
        }
        saveUsers(updated)
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

        val newList = _managedUsers.value + AuthUser(
            email = email,
            passwordHash = pass,
            name = nameInput.trim().ifEmpty { "User" },
            role = "USER",
            loginCount = 0,
            activeDevicesCount = 0
        )
        saveUsers(newList)
        return Pair(true, "নতুন ইউজার সফলভাবে তৈরি হয়েছে!")
    }

    fun deleteManagedUser(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.filter { it.email.lowercase() != email.lowercase() }
        saveUsers(newList)
    }

    fun toggleUserBan(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) it.copy(isBanned = !it.isBanned) else it
        }
        saveUsers(newList)
    }

    fun toggleUserStatus(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) it.copy(isActive = !it.isActive) else it
        }
        saveUsers(newList)
    }

    fun resetUserLogins(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) it.copy(loginCount = 0, activeDevicesCount = 0) else it
        }
        saveUsers(newList)
    }

    fun changeUserPassword(email: String, newPass: String): Boolean {
        if (!isAdmin()) return false
        val cleanPass = newPass.trim()
        if (cleanPass.isEmpty()) return false
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) it.copy(passwordHash = cleanPass) else it
        }
        saveUsers(newList)
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
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) {
                it.copy(
                    totalOtps = totalOtps,
                    balanceTk = balanceTk,
                    todayOtps = today,
                    last7DaysOtps = last7d,
                    last30DaysOtps = last30d
                )
            } else it
        }
        saveUsers(newList)
    }

    private const val KEY_BROADCAST_MESSAGE = "admin_broadcast_message"
    private val _latestBroadcast = MutableStateFlow<String?>(null)
    val latestBroadcast: StateFlow<String?> = _latestBroadcast.asStateFlow()

    fun broadcastNotification(message: String) {
        if (!isAdmin()) return
        prefs?.edit()?.putString(KEY_BROADCAST_MESSAGE, message)?.apply()
        _latestBroadcast.value = message
    }
}
