package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AuthUser(
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: String = "USER", // "ADMIN" or "USER"
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isBanned: Boolean = false,
    val loginCount: Int = 0,
    val activeDevicesCount: Int = 0,
    val lastLoginAt: Long = 0L,
    val lastDeviceName: String = ""
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

    private val _currentUserRole = MutableStateFlow("USER") // "ADMIN" or "USER"
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _managedUsers = MutableStateFlow<List<AuthUser>>(emptyList())
    val managedUsers: StateFlow<List<AuthUser>> = _managedUsers.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isLoggedIn.value = prefs?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
            _currentEmail.value = prefs?.getString(KEY_LOGGED_IN_EMAIL, "") ?: ""
            _currentUserRole.value = prefs?.getString(KEY_LOGGED_IN_ROLE, "USER") ?: "USER"
            loadUsers()
        }
    }

    private fun loadUsers() {
        val json = prefs?.getString(KEY_USERS_LIST, "[]") ?: "[]"
        val list = mutableListOf<AuthUser>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
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
                        lastDeviceName = obj.optString("lastDeviceName", "")
                    )
                )
            }
        } catch (_: Exception) {}
        _managedUsers.value = list
    }

    private fun saveUsers(list: List<AuthUser>) {
        _managedUsers.value = list
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
            arr.put(obj)
        }
        prefs?.edit()?.putString(KEY_USERS_LIST, arr.toString())?.apply()
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
            _currentUserRole.value = "ADMIN"

            prefs?.edit()
                ?.putBoolean(KEY_IS_LOGGED_IN, true)
                ?.putString(KEY_LOGGED_IN_EMAIL, ADMIN_EMAIL)
                ?.putString(KEY_LOGGED_IN_ROLE, "ADMIN")
                ?.apply()

            return Pair(true, "এডমিন হিসেবে সফলভাবে লগইন হয়েছে")
        }

        // 2. Check Managed Users created by Admin
        val user = _managedUsers.value.firstOrNull { it.email.lowercase() == email }
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

    fun isAdmin(): Boolean {
        return _isLoggedIn.value && (_currentUserRole.value == "ADMIN" || _currentEmail.value.equals(ADMIN_EMAIL, ignoreCase = true))
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
}
