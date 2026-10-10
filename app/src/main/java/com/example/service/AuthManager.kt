package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val isVerified: Boolean = true,
    val isApproved: Boolean = true,
    val approvalCode: String = "",
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
    private const val KEY_DELETED_EMAILS = "deleted_emails_csv"

    const val ADMIN_EMAIL = "mdronyinfohelp@gmail.com"
    const val ADMIN_PASS = "Ronyvai2026"
    const val TELEGRAM_CONTACT = "@ismailislamrony1"
    private const val ADMIN_MASTER_SALT = "WS_RONY_MASTER_VERIFY_2026_KEY"

    private var prefs: SharedPreferences? = null

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentEmail = MutableStateFlow("")
    val currentEmail: StateFlow<String> = _currentEmail.asStateFlow()

    private val _currentUserRole = MutableStateFlow("USER")
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _managedUsers = MutableStateFlow<List<AuthUser>>(emptyList())
    val managedUsers: StateFlow<List<AuthUser>> = _managedUsers.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow("সার্ভার সংযুক্ত")
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    // Dual redundant cloud objects so sync never fails even if one object is busy
    private const val PRIMARY_CLOUD_URL = "https://api.restful-api.dev/objects/ff808181a09d98f701a121d19e7c3002"
    private const val BACKUP_CLOUD_URL = "https://api.restful-api.dev/objects/ff808181a09d98f701a126580ad13635"

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(7, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val authScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deletedEmails = mutableSetOf<String>()

    private val DEFAULT_SEEDED_USERS = listOf(
        AuthUser(
            email = "nafis2026@gmail.com",
            passwordHash = "Nafis2026",
            name = "Nafis",
            role = "USER",
            isActive = true,
            isVerified = true,
            isApproved = true,
            approvalCode = generateApprovalCode("nafis2026@gmail.com", "Nafis2026")
        ),
        AuthUser(
            email = "sumaiya2026@gmail.com",
            passwordHash = "Sumaiya2026",
            name = "Sumaiya",
            role = "USER",
            isActive = true,
            isVerified = true,
            isApproved = true,
            approvalCode = generateApprovalCode("sumaiya2026@gmail.com", "Sumaiya2026")
        ),
        AuthUser(
            email = "user2026@gmail.com",
            passwordHash = "User2026",
            name = "General User",
            role = "USER",
            isActive = true,
            isVerified = true,
            isApproved = true,
            approvalCode = generateApprovalCode("user2026@gmail.com", "User2026")
        ),
        AuthUser(
            email = "musa2026@gmail.com",
            passwordHash = "Musa2026",
            name = "Musa",
            role = "USER",
            isActive = true,
            isVerified = true,
            isApproved = true,
            approvalCode = generateApprovalCode("musa2026@gmail.com", "Musa2026")
        )
    )

    /**
     * Generates a 6-char cryptographic Admin Approval Code for an email + password pair.
     */
    fun generateApprovalCode(email: String, password: String): String {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = password.trim()
        val raw = "$cleanEmail|$cleanPass|$ADMIN_MASTER_SALT"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02X".format(it) }
            "VRF-" + hex.substring(0, 6)
        } catch (_: Exception) {
            "VRF-2026OK"
        }
    }

    /**
     * Generates a 2-char cryptographic signature tag for an email + base password.
     * Used when Admin clicks "Auto Generate Verified Password".
     */
    fun computeEmailVerifyTag(email: String, basePass: String): String {
        val raw = "${email.trim().lowercase()}|${basePass.trim()}|$ADMIN_MASTER_SALT"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val c1 = chars[(digest[0].toInt() and 0xFF) % chars.length]
            val c2 = chars[(digest[1].toInt() and 0xFF) % chars.length]
            "$c1$c2"
        } catch (_: Exception) {
            "9A"
        }
    }

    /**
     * Generates an Admin-Verified Password for a given email so that even if a user's
     * ISP has slow cloud sync, the app can cryptographically verify the Admin generated it.
     */
    fun generateVerifiedPasswordForEmail(emailInput: String, nameInput: String = ""): String {
        val cleanEmail = emailInput.trim().lowercase()
        val prefixRaw = if (nameInput.isNotBlank()) {
            nameInput.trim().replace(" ", "").take(6)
        } else {
            cleanEmail.substringBefore("@").filter { it.isLetter() }.take(6).ifEmpty { "User" }
        }
        val prefix = prefixRaw.replaceFirstChar { it.uppercase() }
        val basePass = "${prefix}2026"
        val tag = computeEmailVerifyTag(cleanEmail, basePass)
        return "$basePass#$tag"
    }

    /**
     * Checks if a password carries a valid Admin cryptographic signature for this email.
     */
    private fun isCryptographicallyVerifiedCredential(email: String, pass: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()
        if (!cleanPass.contains("#")) return false
        val basePass = cleanPass.substringBeforeLast("#")
        val providedTag = cleanPass.substringAfterLast("#").uppercase()
        if (basePass.length < 4 || providedTag.length != 2) return false
        val expectedTag = computeEmailVerifyTag(cleanEmail, basePass)
        return providedTag == expectedTag
    }

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isLoggedIn.value = prefs?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
            _currentEmail.value = prefs?.getString(KEY_LOGGED_IN_EMAIL, "") ?: ""
            _currentUserRole.value = prefs?.getString(KEY_LOGGED_IN_ROLE, "USER") ?: "USER"

            val savedDeleted = prefs?.getString(KEY_DELETED_EMAILS, "") ?: ""
            deletedEmails.clear()
            savedDeleted.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach {
                deletedEmails.add(it)
            }

            loadUsers()
            authScope.launch {
                syncFromCloudInternal()
            }
        }
    }

    private fun saveDeletedEmailsLocally() {
        val csv = deletedEmails.take(25).joinToString(",")
        prefs?.edit()?.putString(KEY_DELETED_EMAILS, csv)?.apply()
    }

    private fun loadUsers() {
        val json = prefs?.getString(KEY_USERS_LIST, "[]") ?: "[]"
        val list = mutableListOf<AuthUser>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                parseUserFromJsonObject(obj)?.let { list.add(it) }
            }
        } catch (_: Exception) {}

        for (defaultUser in DEFAULT_SEEDED_USERS) {
            if (!deletedEmails.contains(defaultUser.email.lowercase()) &&
                list.none { it.email.equals(defaultUser.email, ignoreCase = true) }
            ) {
                list.add(defaultUser)
            }
        }

        _managedUsers.value = list
    }

    private fun parseUserFromJsonObject(obj: JSONObject): AuthUser? {
        val email = obj.optString("email", "").trim().lowercase()
        val password = obj.optString("password", "").trim()
        if (email.isEmpty() || password.isEmpty()) return null

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

        val isVerified = obj.optBoolean("isVerified", true)
        val isApproved = obj.optBoolean("isApproved", true)
        val approvalCode = obj.optString("approvalCode", "").ifEmpty {
            generateApprovalCode(email, password)
        }

        return AuthUser(
            email = email,
            passwordHash = password,
            name = obj.optString("name", email.substringBefore("@").replaceFirstChar { it.uppercase() }),
            role = obj.optString("role", "USER"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            isActive = obj.optBoolean("isActive", true),
            isBanned = obj.optBoolean("isBanned", false),
            isVerified = isVerified,
            isApproved = isApproved,
            approvalCode = approvalCode,
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
    }

    /**
     * Compact pipe-encoded representation (< 150 chars per user) so api.restful-api.dev
     * never hits its per-column VARCHAR(500) limit even with 100+ users.
     */
    private fun encodeUserCompact(u: AuthUser): String {
        val safeName = u.name.replace("|", " ").trim().take(24).ifEmpty { "User" }
        val safeDevice = u.lastDeviceName.replace("|", " ").trim().take(24)
        var permBits = 0
        if (u.permissions.canUploadNumbers) permBits = permBits or 1
        if (u.permissions.canDeleteNumbers) permBits = permBits or 2
        if (u.permissions.canUpdateOtpRate) permBits = permBits or 4
        if (u.permissions.canManageWithdrawals) permBits = permBits or 8
        if (u.permissions.canViewLiveCdr) permBits = permBits or 16
        if (u.permissions.canBroadcastNotify) permBits = permBits or 32

        return listOf(
            u.email.trim().lowercase(),
            u.passwordHash.trim(),
            safeName,
            u.role,
            if (u.isActive) "1" else "0",
            if (u.isBanned) "1" else "0",
            if (u.isVerified) "1" else "0",
            if (u.isApproved) "1" else "0",
            u.loginCount.toString(),
            u.activeDevicesCount.toString(),
            u.totalOtps.toString(),
            String.format(java.util.Locale.US, "%.2f", u.balanceTk),
            u.todayOtps.toString(),
            u.last7DaysOtps.toString(),
            u.last30DaysOtps.toString(),
            permBits.toString(),
            u.lastLoginAt.toString(),
            safeDevice
        ).joinToString("|")
    }

    private fun decodeUserCompact(row: String): AuthUser? {
        val parts = row.split("|")
        if (parts.size < 6) return null
        val email = parts[0].trim().lowercase()
        val pass = parts[1].trim()
        if (email.isEmpty() || pass.isEmpty()) return null
        val name = parts.getOrNull(2)?.ifEmpty { "User" } ?: "User"
        val role = parts.getOrNull(3)?.ifEmpty { "USER" } ?: "USER"
        val isActive = parts.getOrNull(4) != "0"
        val isBanned = parts.getOrNull(5) == "1"
        val isVerified = parts.getOrNull(6)?.let { it != "0" } ?: true
        val isApproved = parts.getOrNull(7)?.let { it != "0" } ?: true
        val loginCount = parts.getOrNull(8)?.toIntOrNull() ?: 0
        val activeDevices = parts.getOrNull(9)?.toIntOrNull() ?: 0
        val totalOtps = parts.getOrNull(10)?.toIntOrNull() ?: 0
        val balanceTk = parts.getOrNull(11)?.toDoubleOrNull() ?: 0.0
        val todayOtps = parts.getOrNull(12)?.toIntOrNull() ?: 0
        val last7Days = parts.getOrNull(13)?.toIntOrNull() ?: 0
        val last30Days = parts.getOrNull(14)?.toIntOrNull() ?: 0
        val permBits = parts.getOrNull(15)?.toIntOrNull() ?: 7
        val lastLoginAt = parts.getOrNull(16)?.toLongOrNull() ?: 0L
        val lastDevice = parts.getOrNull(17) ?: ""

        val perms = SubAdminPermissions(
            canUploadNumbers = (permBits and 1) != 0,
            canDeleteNumbers = (permBits and 2) != 0,
            canUpdateOtpRate = (permBits and 4) != 0,
            canManageWithdrawals = (permBits and 8) != 0,
            canViewLiveCdr = (permBits and 16) != 0,
            canBroadcastNotify = (permBits and 32) != 0
        )

        return AuthUser(
            email = email,
            passwordHash = pass,
            name = name,
            role = role,
            isActive = isActive,
            isBanned = isBanned,
            isVerified = isVerified,
            isApproved = isApproved,
            approvalCode = generateApprovalCode(email, pass),
            loginCount = loginCount,
            activeDevicesCount = activeDevices,
            lastLoginAt = lastLoginAt,
            lastDeviceName = lastDevice,
            totalOtps = totalOtps,
            balanceTk = balanceTk,
            todayOtps = todayOtps,
            last7DaysOtps = last7Days,
            last30DaysOtps = last30Days,
            permissions = perms
        )
    }

    fun syncFromCloud() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            val t = Thread { syncFromCloudInternal() }
            t.start()
            try {
                t.join(4500)
            } catch (_: Exception) {}
        } else {
            syncFromCloudInternal()
        }
    }

    private fun fetchCloudUsersFromUrl(url: String): Pair<List<AuthUser>, Set<String>>? {
        return try {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val bodyStr = resp.body?.string() ?: return null
            val rootObj = JSONObject(bodyStr)
            val dataObj = rootObj.optJSONObject("data") ?: return null

            val cloudDeleted = mutableSetOf<String>()
            val delCsv = dataObj.optString("del", "")
            if (delCsv.isNotEmpty()) {
                delCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach {
                    cloudDeleted.add(it)
                }
            }

            val byEmail = linkedMapOf<String, AuthUser>()

            // 1. Parse legacy/compact users_json if present
            val usersJsonStr = dataObj.optString("users_json", "")
            if (usersJsonStr.isNotEmpty() && usersJsonStr != "[]") {
                try {
                    val arr = JSONArray(usersJsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        parseUserFromJsonObject(obj)?.let { u ->
                            if (!cloudDeleted.contains(u.email.lowercase())) {
                                byEmail[u.email.lowercase()] = u
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Parse multi-key u0, u1, u2, ... entries (authoritative & full metadata)
            val keys = dataObj.keys()
            val uKeys = mutableListOf<String>()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.startsWith("u") && k != "users_json" && k.drop(1).all { it.isDigit() }) {
                    uKeys.add(k)
                }
            }
            uKeys.sortBy { it.drop(1).toIntOrNull() ?: 0 }
            for (k in uKeys) {
                val rawRow = dataObj.optString(k, "")
                if (rawRow.isNotEmpty()) {
                    decodeUserCompact(rawRow)?.let { u ->
                        if (!cloudDeleted.contains(u.email.lowercase())) {
                            byEmail[u.email.lowercase()] = u
                        }
                    }
                }
            }

            val bcast = dataObj.optString("bcast", "")
            if (bcast.isNotEmpty()) {
                _latestBroadcast.value = bcast
                prefs?.edit()?.putString(KEY_BROADCAST_MESSAGE, bcast)?.apply()
            }

            Pair(byEmail.values.toList(), cloudDeleted)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun syncFromCloudInternal(): Boolean {
        val primaryResult = fetchCloudUsersFromUrl(PRIMARY_CLOUD_URL)
        val backupResult = if (primaryResult == null || primaryResult.first.isEmpty()) {
            fetchCloudUsersFromUrl(BACKUP_CLOUD_URL)
        } else null

        val result = primaryResult ?: backupResult ?: return false
        val (cloudUsers, cloudDeleted) = result

        deletedEmails.addAll(cloudDeleted)
        saveDeletedEmailsLocally()

        val mergedMap = linkedMapOf<String, AuthUser>()
        // Keep local users that are not deleted
        for (lu in _managedUsers.value) {
            val em = lu.email.lowercase()
            if (!deletedEmails.contains(em)) {
                mergedMap[em] = lu
            }
        }
        // Cloud users override local state so Admin updates (password, approval, ban, active) propagate everywhere
        for (cu in cloudUsers) {
            val em = cu.email.lowercase()
            if (!deletedEmails.contains(em)) {
                mergedMap[em] = cu
            }
        }
        // Ensure default seeded users are present unless explicitly deleted by Admin
        for (du in DEFAULT_SEEDED_USERS) {
            val em = du.email.lowercase()
            if (!deletedEmails.contains(em) && !mergedMap.containsKey(em)) {
                mergedMap[em] = du
            }
        }

        val finalList = mergedMap.values.toList()
        _managedUsers.value = finalList
        saveUsersLocallyOnly(finalList)
        _cloudSyncStatus.value = "✓ ক্লাউড সিঙ্ক সম্পন্ন (${finalList.size} ইউজার)"

        // Enforce real-time revocation if current logged-in user was banned, deactivated, or unapproved by Admin
        val current = _currentEmail.value.trim().lowercase()
        if (_isLoggedIn.value && current.isNotEmpty() && current != ADMIN_EMAIL.lowercase()) {
            val myRecord = finalList.firstOrNull { it.email.equals(current, ignoreCase = true) }
            if (myRecord != null && (myRecord.isBanned || !myRecord.isActive || !myRecord.isVerified || !myRecord.isApproved)) {
                logout()
            }
        }

        return true
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
            obj.put("isVerified", u.isVerified)
            obj.put("isApproved", u.isApproved)
            obj.put("approvalCode", u.approvalCode.ifEmpty { generateApprovalCode(u.email, u.passwordHash) })
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

    private fun buildCloudPutPayload(list: List<AuthUser>): JSONObject {
        val dataObj = JSONObject()

        // 1. Write each user into its own compact key u0, u1, u2... (<150 chars each, never hits 500-char limit!)
        list.forEachIndexed { idx, user ->
            dataObj.put("u$idx", encodeUserCompact(user))
        }

        // 2. Also build a compact users_json (<420 chars) for backwards-compatibility with older APK installs
        val compactArr = JSONArray()
        // Prioritize custom non-default users first in users_json so newly created users always fit
        val prioritized = list.sortedByDescending { u ->
            DEFAULT_SEEDED_USERS.none { it.email.equals(u.email, ignoreCase = true) }
        }
        for (u in prioritized) {
            if (u.isActive && !u.isBanned && u.isVerified && u.isApproved) {
                val mini = JSONObject().apply {
                    put("email", u.email)
                    put("password", u.passwordHash)
                    put("name", u.name.take(12))
                }
                val candidateStr = compactArr.toString()
                if (candidateStr.length + mini.toString().length < 410) {
                    compactArr.put(mini)
                }
            }
        }
        dataObj.put("users_json", compactArr.toString())

        if (deletedEmails.isNotEmpty()) {
            dataObj.put("del", deletedEmails.take(15).joinToString(",").take(350))
        }

        val bcast = _latestBroadcast.value
        if (!bcast.isNullOrBlank()) {
            dataObj.put("bcast", bcast.take(300))
        }

        return JSONObject().apply {
            put("name", "work_shortcut_authorized_users")
            put("data", dataObj)
        }
    }

    private fun pushPayloadToUrl(url: String, payloadStr: String): Boolean {
        return try {
            val body = payloadStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val req = okhttp3.Request.Builder()
                .url(url)
                .put(body)
                .build()
            val resp = httpClient.newCall(req).execute()
            val ok = resp.isSuccessful
            resp.close()
            ok
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun syncToCloudInternal(localList: List<AuthUser>, priorityEmail: String? = null): Boolean {
        return try {
            // Pull latest cloud state first so we never clobber accounts created from another device
            val cloudPair = fetchCloudUsersFromUrl(PRIMARY_CLOUD_URL) ?: fetchCloudUsersFromUrl(BACKUP_CLOUD_URL)
            val mergedMap = linkedMapOf<String, AuthUser>()

            if (cloudPair != null) {
                val (cloudUsers, cloudDel) = cloudPair
                deletedEmails.addAll(cloudDel)
                if (priorityEmail != null) {
                    deletedEmails.remove(priorityEmail.lowercase())
                }
                for (cu in cloudUsers) {
                    val em = cu.email.lowercase()
                    if (!deletedEmails.contains(em)) {
                        mergedMap[em] = cu
                    }
                }
            }

            // Apply local list on top
            for (lu in localList) {
                val em = lu.email.lowercase()
                if (!deletedEmails.contains(em)) {
                    mergedMap[em] = lu
                }
            }

            val finalList = mergedMap.values.toList()
            _managedUsers.value = finalList
            saveUsersLocallyOnly(finalList)
            saveDeletedEmailsLocally()

            val payloadStr = buildCloudPutPayload(finalList).toString()
            val okPrimary = pushPayloadToUrl(PRIMARY_CLOUD_URL, payloadStr)
            val okBackup = pushPayloadToUrl(BACKUP_CLOUD_URL, payloadStr)
            val success = okPrimary || okBackup
            _cloudSyncStatus.value = if (success) {
                "✓ ক্লাউড সিঙ্ক সফল (${finalList.size} ইউজার)"
            } else {
                "⚠️ ক্লাউড সিঙ্ক অপেক্ষমান (পুনরায় চেষ্টা করুন)"
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    private fun syncToCloud(list: List<AuthUser>, priorityEmail: String? = null) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            val t = Thread { syncToCloudInternal(list, priorityEmail) }
            t.start()
            try {
                t.join(3500)
            } catch (_: Exception) {}
        } else {
            syncToCloudInternal(list, priorityEmail)
        }
    }

    private fun saveUsers(list: List<AuthUser>, priorityEmail: String? = null) {
        _managedUsers.value = list
        saveUsersLocallyOnly(list)
        syncToCloud(list, priorityEmail)
    }

    /**
     * Checks credentials against Admin master account OR Admin-Verified & Approved users.
     */
    fun login(emailInput: String, passInput: String): Pair<Boolean, String> {
        val email = emailInput.trim().lowercase()
        val pass = passInput.trim()

        if (email.isEmpty() || pass.isEmpty()) {
            return Pair(false, "ইমেইল ও পাসওয়ার্ড প্রদান করুন")
        }

        // 1. Check Master Admin
        if (email == ADMIN_EMAIL.lowercase() && (pass == ADMIN_PASS || pass.equals(ADMIN_PASS, ignoreCase = true))) {
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

        // 2. Always sync from Cloud before checking user credentials so newly created/updated/approved accounts work immediately
        try {
            syncFromCloud()
        } catch (_: Exception) {}

        var user = _managedUsers.value.firstOrNull { it.email.equals(email, ignoreCase = true) }

        // 3. If not found in cloud yet, check if password carries Prime Admin's cryptographic HMAC verification signature
        if (user == null && !deletedEmails.contains(email) && isCryptographicallyVerifiedCredential(email, pass)) {
            val autoName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            val verifiedUser = AuthUser(
                email = email,
                passwordHash = pass,
                name = autoName,
                role = "USER",
                isActive = true,
                isBanned = false,
                isVerified = true,
                isApproved = true,
                approvalCode = generateApprovalCode(email, pass)
            )
            val newList = _managedUsers.value + verifiedUser
            saveUsers(newList, priorityEmail = email)
            user = verifiedUser
        }

        if (user != null) {
            if (user.isBanned) {
                return Pair(false, "🚫 আপনার অ্যাকাউন্টটি এডমিন কর্তৃক ব্যান (Ban) করা হয়েছে! যোগাযোগ: $TELEGRAM_CONTACT")
            }
            if (!user.isVerified) {
                return Pair(false, "🔒 আপনার অ্যাকাউন্টটি এখনো এডমিন ভেরিফাই (Verify) করেননি! যোগাযোগ: $TELEGRAM_CONTACT")
            }
            if (!user.isApproved) {
                return Pair(false, "⏳ আপনার লগইন এখনো এডমিন অনুমোদন (Approve) করেননি! এডমিনের Approval-এর জন্য যোগাযোগ করুন: $TELEGRAM_CONTACT")
            }
            if (!user.isActive) {
                return Pair(false, "⚪ আপনার অ্যাকাউন্টটি নিষ্ক্রিয় (Inactive)। এডমিনের সাথে যোগাযোগ করুন: $TELEGRAM_CONTACT")
            }

            // Support exact match OR case-insensitive match (fixes Android keyboard auto-capitalizing first letter)
            val passMatches = user.passwordHash.trim() == pass ||
                user.passwordHash.trim().equals(pass, ignoreCase = true) ||
                user.approvalCode.equals(pass, ignoreCase = true)

            if (passMatches) {
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
                    if (it.email.equals(email, ignoreCase = true)) updatedUser else it
                }
                saveUsersLocallyOnly(updatedList)
                _managedUsers.value = updatedList
                authScope.launch {
                    syncToCloudInternal(updatedList, priorityEmail = email)
                }

                _isLoggedIn.value = true
                _currentEmail.value = user.email
                _currentUserRole.value = user.role

                prefs?.edit()
                    ?.putBoolean(KEY_IS_LOGGED_IN, true)
                    ?.putString(KEY_LOGGED_IN_EMAIL, user.email)
                    ?.putString(KEY_LOGGED_IN_ROLE, user.role)
                    ?.apply()

                return Pair(true, "✓ ভেরিফাইড ও অনুমোদিত লগইন সফল (${user.name})")
            } else {
                return Pair(false, "ভুল পাসওয়ার্ড! সঠিক পাসওয়ার্ড পেতে যোগাযোগ করুন: $TELEGRAM_CONTACT")
            }
        }

        return Pair(false, "অনুমোদিত অ্যাকাউন্ট নয়! এডমিনের ভেরিফিকেশন ও আইডি-পাসওয়ার্ড পেতে যোগাযোগ করুন: $TELEGRAM_CONTACT")
    }

    fun logout() {
        val current = _currentEmail.value.lowercase()
        if (current.isNotEmpty() && current != ADMIN_EMAIL.lowercase()) {
            val updated = _managedUsers.value.map {
                if (it.email.lowercase() == current) {
                    it.copy(activeDevicesCount = maxOf(0, it.activeDevicesCount - 1))
                } else it
            }
            saveUsersLocallyOnly(updated)
            _managedUsers.value = updated
            authScope.launch {
                syncToCloudInternal(updated)
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
        saveUsers(updated, priorityEmail = email)
    }

    fun updateSubAdminPermissions(email: String, permissions: SubAdminPermissions) {
        if (!isPrimeAdmin()) return
        val updated = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) {
                it.copy(permissions = permissions)
            } else it
        }
        saveUsers(updated, priorityEmail = email)
    }

    /**
     * Admin operation: Creates OR updates a managed user with explicit Verification & Approval flags,
     * and immediately syncs to the Cloud so the user can log in from any device right away.
     */
    fun createManagedUser(
        emailInput: String,
        passInput: String,
        nameInput: String = "User",
        isVerified: Boolean = true,
        isApproved: Boolean = true
    ): Pair<Boolean, String> {
        if (!isAdmin()) return Pair(false, "অনুমতি নেই")
        val rawEmail = emailInput.trim().lowercase()
        val email = if (rawEmail.isNotEmpty() && !rawEmail.contains("@")) "$rawEmail@gmail.com" else rawEmail
        val pass = passInput.trim()

        if (email.isEmpty() || pass.isEmpty()) {
            return Pair(false, "ইমেইল ও পাসওয়ার্ড আবশ্যক")
        }
        if (email == ADMIN_EMAIL.lowercase()) {
            return Pair(false, "এটি মাস্টার এডমিন ইমেইল")
        }

        deletedEmails.remove(email)
        saveDeletedEmailsLocally()

        val displayName = nameInput.trim().ifEmpty {
            email.substringBefore("@").replaceFirstChar { it.uppercase() }
        }
        val code = generateApprovalCode(email, pass)

        val existingIdx = _managedUsers.value.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        val newList = if (existingIdx >= 0) {
            _managedUsers.value.mapIndexed { index, existingUser ->
                if (index == existingIdx) {
                    existingUser.copy(
                        passwordHash = pass,
                        name = if (nameInput.isNotBlank()) displayName else existingUser.name,
                        isActive = true,
                        isBanned = false,
                        isVerified = isVerified,
                        isApproved = isApproved,
                        approvalCode = code
                    )
                } else existingUser
            }
        } else {
            _managedUsers.value + AuthUser(
                email = email,
                passwordHash = pass,
                name = displayName,
                role = "USER",
                isActive = true,
                isBanned = false,
                isVerified = isVerified,
                isApproved = isApproved,
                approvalCode = code,
                loginCount = 0,
                activeDevicesCount = 0
            )
        }

        _managedUsers.value = newList
        saveUsersLocallyOnly(newList)
        val synced = syncToCloudInternalResult(newList, email)

        val statusNote = if (isVerified && isApproved) {
            "ভেরিফাইড ও লগইন অনুমোদিত (Approved)"
        } else {
            "পেন্ডিং অনুমোদন (Approval Pending)"
        }

        return if (synced) {
            Pair(true, "✓ ইউজার তৈরি ও ক্লাউডে সিঙ্ক সফল! [$statusNote]")
        } else {
            Pair(true, "✓ ইউজার তৈরি হয়েছে [$statusNote] (ব্যাগগ্রাউন্ডে ক্লাউড সিঙ্ক হচ্ছে)")
        }
    }

    private fun syncToCloudInternalResult(list: List<AuthUser>, priorityEmail: String): Boolean {
        var result = false
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            val t = Thread {
                result = syncToCloudInternal(list, priorityEmail)
            }
            t.start()
            try {
                t.join(4500)
            } catch (_: Exception) {}
        } else {
            result = syncToCloudInternal(list, priorityEmail)
        }
        return result
    }

    fun toggleUserApproval(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.map {
            if (it.email.equals(email, ignoreCase = true)) {
                val nextApproved = !(it.isVerified && it.isApproved)
                it.copy(
                    isVerified = nextApproved,
                    isApproved = nextApproved,
                    isActive = if (nextApproved) true else it.isActive
                )
            } else it
        }
        saveUsers(newList, priorityEmail = email)
    }

    fun deleteManagedUser(email: String) {
        if (!isAdmin()) return
        deletedEmails.add(email.trim().lowercase())
        saveDeletedEmailsLocally()
        val newList = _managedUsers.value.filter { !it.email.equals(email, ignoreCase = true) }
        saveUsers(newList)
    }

    fun toggleUserBan(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) it.copy(isBanned = !it.isBanned) else it
        }
        saveUsers(newList, priorityEmail = email)
    }

    fun toggleUserStatus(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) it.copy(isActive = !it.isActive) else it
        }
        saveUsers(newList, priorityEmail = email)
    }

    fun resetUserLogins(email: String) {
        if (!isAdmin()) return
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) it.copy(loginCount = 0, activeDevicesCount = 0) else it
        }
        saveUsers(newList, priorityEmail = email)
    }

    fun changeUserPassword(email: String, newPass: String): Boolean {
        if (!isAdmin()) return false
        val cleanPass = newPass.trim()
        if (cleanPass.isEmpty()) return false
        val newList = _managedUsers.value.map {
            if (it.email.lowercase() == email.lowercase()) {
                it.copy(
                    passwordHash = cleanPass,
                    isVerified = true,
                    isApproved = true,
                    approvalCode = generateApprovalCode(it.email, cleanPass)
                )
            } else it
        }
        saveUsers(newList, priorityEmail = email)
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
        saveUsers(newList, priorityEmail = email)
    }

    private const val KEY_BROADCAST_MESSAGE = "admin_broadcast_message"
    private val _latestBroadcast = MutableStateFlow<String?>(null)
    val latestBroadcast: StateFlow<String?> = _latestBroadcast.asStateFlow()

    fun broadcastNotification(message: String) {
        if (!isAdmin()) return
        prefs?.edit()?.putString(KEY_BROADCAST_MESSAGE, message)?.apply()
        _latestBroadcast.value = message
        syncToCloud(_managedUsers.value)
    }
}
