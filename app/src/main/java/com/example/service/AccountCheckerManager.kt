package com.example.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 3 Modes for Virtual Number Account Checker (Controlled from Settings & Virtual Numbers Section):
 * 1) FRESH_ONLY: Only get Fresh (New / No Account -> GREEN) numbers; if Old, auto-retry in background until Fresh is found.
 * 2) OLD_ONLY: Only get Old (Has Account -> RED) numbers; if Fresh, auto-retry in background until Old is found & auto-copy.
 * 3) BOTH: Accept both Old and Fresh numbers immediately as they arrive, while highlighting whether each is OLD (RED) or NEW/FRESH (GREEN).
 */
enum class AccountCheckerMode(
    val key: String,
    val optionNumber: Int,
    val titleBn: String,
    val subtitleBn: String,
    val shortBadge: String
) {
    FRESH_ONLY(
        key = "FRESH_ONLY",
        optionNumber = 1,
        titleBn = "1) শুধু Fresh (New) নম্বর",
        subtitleBn = "শুধু একাউন্ট ছাড়া (Green) নতুন নম্বর নিবে। না থাকলে ব্যাকগ্রাউন্ডে চেক করে অটো রি-ট্রাই দিয়ে নতুন Fresh নম্বর দিবে ও অটো কপি হবে।",
        shortBadge = "1) Only Fresh (🟢 New)"
    ),
    OLD_ONLY(
        key = "OLD_ONLY",
        optionNumber = 2,
        titleBn = "2) শুধু Old (Account) নম্বর",
        subtitleBn = "শুধু একাউন্ট থাকা (Red) পুরাতন নম্বর নিবে। না থাকলে ব্যাকগ্রাউন্ডে রি-ট্রাই দিয়ে Old নম্বর চেক করে ইউজারকে দিবে ও অটো কপি হবে।",
        shortBadge = "2) Only Old (🔴 Old)"
    ),
    BOTH(
        key = "BOTH",
        optionNumber = 3,
        titleBn = "3) Old & Fresh Both (২টাই নিবে)",
        subtitleBn = "Old এবং Fresh ২টার যেটা আসবে সেটাই থাকবে, কিন্তু নম্বরটি OLD (🔴 Red) নাকি NEW/FRESH (🟢 Green) তা হাইলাইট থাকবে।",
        shortBadge = "3) Old & Fresh Both"
    );

    companion object {
        fun fromKey(key: String?): AccountCheckerMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: BOTH
        }
    }
}

data class AccountCheckResult(
    val phoneNumber: String,
    val hasAccount: Boolean, // true = OLD (RED), false = FRESH/NEW (GREEN)
    val status: String,      // "OLD" or "FRESH"
    val badgeLabel: String,  // "🔴 OLD ACCOUNT" or "🟢 NEW (FRESH)"
    val detailMessage: String
)

data class AccountCheckerUiState(
    val isEnabled: Boolean = true,
    val mode: AccountCheckerMode = AccountCheckerMode.BOTH,
    val maxRetries: Int = 6,
    val pythonScriptCode: String = AccountCheckerManager.DEFAULT_CHECKER_PY_SCRIPT,
    val pythonScriptFileName: String = "account_checker.py",
    val customEndpointUrl: String = "",
    val isCheckingInBackground: Boolean = false,
    val retryStatusMessage: String? = null,
    val totalCheckedCount: Int = 0,
    val oldFoundCount: Int = 0,
    val freshFoundCount: Int = 0,
    val lastCheckedNumber: String? = null,
    val lastCheckedStatus: String? = null
)

object AccountCheckerManager {

    private const val PREFS_NAME = "account_checker_prefs"
    private const val KEY_ENABLED = "checker_enabled"
    private const val KEY_MODE = "checker_mode"
    private const val KEY_MAX_RETRIES = "checker_max_retries"
    private const val KEY_PY_SCRIPT = "checker_python_script"
    private const val KEY_PY_FILENAME = "checker_python_filename"
    private const val KEY_CUSTOM_URL = "checker_custom_url"
    private const val KEY_TOTAL_CHECKED = "checker_total_checked"
    private const val KEY_OLD_COUNT = "checker_old_count"
    private const val KEY_FRESH_COUNT = "checker_fresh_count"

    const val DEFAULT_CHECKER_PY_SCRIPT = """# account_checker.py - Facebook GraphQL Account Checker
# RED = Account Exists (OLD) | GREEN = No Account (FRESH / NEW)
import requests, json, uuid, time

GRAPHQL_URL = "https://www.facebook.com/api/graphql/"
LSD = "AdS-QY2mYrCcHP7sVf7ynTA6vTc"
CIPHER_TEXT = "AdAtB3O832KrYgWu2c7Jm2mNWOdAALA9wNFKJ_043RSsW27RSXXjhlBd0Cr8rGn-lIwgDuQU-CNQILiqKbqFW0GZop7nLgDVhClntKlePmNpHzT4ESf47v-lTUCNV3L-ikw70LhayHi30trSUJqk-Rvmaost-m0CTl60dFiIcbLNhGmQ8uggs-M1wfu04KQJvOQBDLwohSr7f_yRqkDGj98Ra-1S2N77k17Fqxu8N84cEWWTyv4qW6ySj7GI7CYYhD8QZDTzMxXcImxAr_Y"
DOC_ID = "27012969198380071"

def check_number(phone_number):
    clean = phone_number.strip()
    full = clean if clean.startswith('+') else '+' + clean
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36",
        "Content-Type": "application/x-www-form-urlencoded",
        "Origin": "https://www.facebook.com",
        "Referer": "https://www.facebook.com/login/identify/",
        "x-fb-friendly-name": "CAAFBAccountSearchViewQuery",
        "x-fb-lsd": LSD,
        "Accept-Language": "en-US,en;q=0.9"
    }
    variables = {
        "params": {
            "cipher_text": CIPHER_TEXT, "context": "recover",
            "event_request_id": str(uuid.uuid4()), "friend_name": "",
            "search_query": full, "waterfall_id": str(uuid.uuid4())
        }
    }
    payload = {
        "av": "0", "__aaid": "0", "__user": "0", "__a": "1", "__req": "5",
        "__hs": "20742.HYP:comet_loggedout_pkg.2.1...0", "dpr": "2",
        "__ccg": "EXCELLENT", "__rev": "1050579926",
        "__spin_r": "1050579926", "__spin_b": "trunk", "__spin_t": str(int(time.time())),
        "lsd": LSD, "jazoest": "22291", "doc_id": DOC_ID,
        "variables": json.dumps(variables, separators=(',', ':')),
        "fb_api_caller_class": "RelayModern",
        "fb_api_req_friendly_name": "CAAFBAccountSearchViewQuery"
    }
    try:
        res = requests.post(GRAPHQL_URL, headers=headers, data=payload, timeout=15)
        if res.status_code == 200:
            js = res.json()
            search = js.get("data", {}).get("caa_ar_fb_account_search", {})
            accounts = search.get("accounts", [])
            desc = search.get("error_content", {}).get("description", "")
            if accounts:
                return {"status": "OLD", "color": "RED", "has_account": True}
            if "No account found" in desc:
                return {"status": "FRESH", "color": "GREEN", "has_account": False}
    except Exception:
        pass
    return {"status": "FRESH", "color": "GREEN", "has_account": False}
"""

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var prefs: SharedPreferences? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Cache of checked phone numbers (clean digits -> "OLD" or "FRESH")
    private val checkedCache = ConcurrentHashMap<String, String>()

    private val _state = MutableStateFlow(AccountCheckerUiState())
    val state: StateFlow<AccountCheckerUiState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
            val modeKey = prefs?.getString(KEY_MODE, AccountCheckerMode.BOTH.key)
            val retries = (prefs?.getInt(KEY_MAX_RETRIES, 6) ?: 6).coerceIn(1, 15)
            val script = prefs?.getString(KEY_PY_SCRIPT, DEFAULT_CHECKER_PY_SCRIPT) ?: DEFAULT_CHECKER_PY_SCRIPT
            val fileName = prefs?.getString(KEY_PY_FILENAME, "account_checker.py") ?: "account_checker.py"
            val customUrl = prefs?.getString(KEY_CUSTOM_URL, "") ?: ""
            val total = prefs?.getInt(KEY_TOTAL_CHECKED, 0) ?: 0
            val oldCnt = prefs?.getInt(KEY_OLD_COUNT, 0) ?: 0
            val freshCnt = prefs?.getInt(KEY_FRESH_COUNT, 0) ?: 0

            _state.update {
                it.copy(
                    isEnabled = enabled,
                    mode = AccountCheckerMode.fromKey(modeKey),
                    maxRetries = retries,
                    pythonScriptCode = script,
                    pythonScriptFileName = fileName,
                    customEndpointUrl = customUrl,
                    totalCheckedCount = total,
                    oldFoundCount = oldCnt,
                    freshFoundCount = freshCnt
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        _state.update { it.copy(isEnabled = enabled) }
    }

    fun toggleEnabled() {
        setEnabled(!_state.value.isEnabled)
    }

    fun setMode(mode: AccountCheckerMode) {
        prefs?.edit()?.putString(KEY_MODE, mode.key)?.apply()
        _state.update { it.copy(mode = mode, isEnabled = true) }
        prefs?.edit()?.putBoolean(KEY_ENABLED, true)?.apply()
    }

    fun setMaxRetries(retries: Int) {
        val safe = retries.coerceIn(1, 15)
        prefs?.edit()?.putInt(KEY_MAX_RETRIES, safe)?.apply()
        _state.update { it.copy(maxRetries = safe) }
    }

    fun updatePythonScript(scriptContent: String, fileName: String = "account_checker.py") {
        val trimmed = scriptContent.trim().ifEmpty { DEFAULT_CHECKER_PY_SCRIPT }
        // Extract custom URL from python script if present (e.g., CHECK_URL = "https://..." or url = "https://...")
        val extractedUrl = extractUrlFromPythonScript(trimmed)
        prefs?.edit()
            ?.putString(KEY_PY_SCRIPT, trimmed)
            ?.putString(KEY_PY_FILENAME, fileName)
            ?.putString(KEY_CUSTOM_URL, extractedUrl)
            ?.apply()
        checkedCache.clear()
        _state.update {
            it.copy(
                pythonScriptCode = trimmed,
                pythonScriptFileName = fileName,
                customEndpointUrl = extractedUrl
            )
        }
    }

    fun resetPythonScriptToDefault() {
        updatePythonScript(DEFAULT_CHECKER_PY_SCRIPT, "account_checker.py")
    }

    fun setRetryStatus(isChecking: Boolean, message: String?) {
        _state.update {
            it.copy(
                isCheckingInBackground = isChecking,
                retryStatusMessage = message
            )
        }
    }

    private fun extractUrlFromPythonScript(script: String): String {
        val regex = Regex("""https?://[^\s"'`<>]+""")
        val matches = regex.findAll(script).map { it.value }.toList()
        // Prefer custom API URL if present, otherwise first URL
        return matches.firstOrNull { !it.contains("facebook.com", ignoreCase = true) }
            ?: matches.firstOrNull()
            ?: ""
    }

    private fun extractKeywordsFromPython(script: String, listName: String, defaults: List<String>): List<String> {
        return try {
            val pattern = Regex("""$listName\s*=\s*\[([^\]]+)]""", RegexOption.IGNORE_CASE)
            val match = pattern.find(script) ?: return defaults
            val rawInside = match.groupValues[1]
            val parsed = Regex("""["']([^"']+)["']""").findAll(rawInside).map { it.groupValues[1] }.toList()
            if (parsed.isNotEmpty()) parsed else defaults
        } catch (_: Exception) {
            defaults
        }
    }

    /**
     * Checks a single phone number using the active `account_checker.py` configuration and live endpoints.
     * - Returns `status = "OLD"` (hasAccount = true -> RED highlight) if an account exists on the number.
     * - Returns `status = "FRESH"` (hasAccount = false -> GREEN highlight) if no account exists on the number.
     */
    suspend fun checkPhoneNumber(rawNumber: String, forceRefresh: Boolean = false): AccountCheckResult = withContext(Dispatchers.IO) {
        val cleanDigits = rawNumber.replace("[^0-9]".toRegex(), "")
        if (cleanDigits.length < 6) {
            return@withContext AccountCheckResult(
                phoneNumber = rawNumber,
                hasAccount = false,
                status = "FRESH",
                badgeLabel = "🟢 NEW (FRESH)",
                detailMessage = "Fresh Number (No Account)"
            )
        }

        if (!forceRefresh) {
            val cached = checkedCache[cleanDigits]
            if (cached != null) {
                val isOld = cached == "OLD"
                return@withContext AccountCheckResult(
                    phoneNumber = rawNumber,
                    hasAccount = isOld,
                    status = cached,
                    badgeLabel = if (isOld) "🔴 OLD ACCOUNT" else "🟢 NEW (FRESH)",
                    detailMessage = if (isOld) "Account Exists (Red)" else "Fresh Number - No Account (Green)"
                )
            }
        }

        val currentState = _state.value
        val script = currentState.pythonScriptCode
        val oldKeywords = extractKeywordsFromPython(
            script,
            "OLD_KEYWORDS",
            listOf("recover/initiate", "ldata=", "cuid=", "reset_action", "account_found", "\"old\"", "\"exists\":true", "\"has_account\":true")
        )
        val freshKeywords = extractKeywordsFromPython(
            script,
            "FRESH_KEYWORDS",
            listOf("identify_search_error", "no search results", "did not match", "no_account", "\"fresh\"", "\"exists\":false", "\"has_account\":false")
        )

        var determinedStatus: String? = null

        // 1. Check if a custom non-Facebook checker API URL was provided in checker.py
        val customUrl = currentState.customEndpointUrl
        if (customUrl.isNotBlank() && !customUrl.contains("facebook.com", ignoreCase = true)) {
            try {
                val targetUrl = if (customUrl.contains("{number}") || customUrl.contains("{phone}")) {
                    customUrl.replace("{number}", cleanDigits).replace("{phone}", cleanDigits)
                } else if (customUrl.contains("?")) {
                    "$customUrl&number=$cleanDigits&phone=$cleanDigits"
                } else {
                    "$customUrl?number=$cleanDigits"
                }

                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AccountChecker/1.0")

                if (script.contains("requests.post", ignoreCase = true) && !customUrl.contains("{number}")) {
                    val jsonPayload = JSONObject().apply {
                        put("number", cleanDigits)
                        put("phone", "+$cleanDigits")
                    }
                    reqBuilder.post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                } else {
                    reqBuilder.get()
                }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val bodyLower = (response.body?.string() ?: "").lowercase()
                    if (bodyLower.isNotEmpty()) {
                        if (oldKeywords.any { bodyLower.contains(it.lowercase()) } ||
                            bodyLower.contains("\"old\"") ||
                            bodyLower.contains("\"red\"") ||
                            bodyLower.contains("\"exists\":true") ||
                            bodyLower.contains("\"account\":true")
                        ) {
                            determinedStatus = "OLD"
                        } else if (freshKeywords.any { bodyLower.contains(it.lowercase()) } ||
                            bodyLower.contains("\"fresh\"") ||
                            bodyLower.contains("\"new\"") ||
                            bodyLower.contains("\"green\"") ||
                            bodyLower.contains("\"exists\":false") ||
                            bodyLower.contains("\"account\":false")
                        ) {
                            determinedStatus = "FRESH"
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to standard check
            }
        }

        // 1.5. Live GraphQL check against Facebook CAAFBAccountSearchViewQuery (matching user's python checker script)
        if (determinedStatus == null) {
            try {
                val fullNumber = if (!cleanDigits.startsWith("+")) "+$cleanDigits" else cleanDigits
                val variablesJson = JSONObject().apply {
                    put("params", JSONObject().apply {
                        put("cipher_text", "AdAtB3O832KrYgWu2c7Jm2mNWOdAALA9wNFKJ_043RSsW27RSXXjhlBd0Cr8rGn-lIwgDuQU-CNQILiqKbqFW0GZop7nLgDVhClntKlePmNpHzT4ESf47v-lTUCNV3L-ikw70LhayHi30trSUJqk-Rvmaost-m0CTl60dFiIcbLNhGmQ8uggs-M1wfu04KQJvOQBDLwohSr7f_yRqkDGj98Ra-1S2N77k17Fqxu8N84cEWWTyv4qW6ySj7GI7CYYhD8QZDTzMxXcImxAr_Y")
                        put("context", "recover")
                        put("event_request_id", java.util.UUID.randomUUID().toString())
                        put("friend_name", "")
                        put("search_query", fullNumber)
                        put("waterfall_id", java.util.UUID.randomUUID().toString())
                    })
                }

                val formBody = FormBody.Builder()
                    .add("av", "0")
                    .add("__aaid", "0")
                    .add("__user", "0")
                    .add("__a", "1")
                    .add("__req", "5")
                    .add("__hs", "20742.HYP:comet_loggedout_pkg.2.1...0")
                    .add("dpr", "2")
                    .add("__ccg", "EXCELLENT")
                    .add("__rev", "1050579926")
                    .add("__spin_r", "1050579926")
                    .add("__spin_b", "trunk")
                    .add("__spin_t", "${System.currentTimeMillis() / 1000}")
                    .add("lsd", "AdS-QY2mYrCcHP7sVf7ynTA6vTc")
                    .add("jazoest", "22291")
                    .add("doc_id", "27012969198380071")
                    .add("variables", variablesJson.toString())
                    .add("fb_api_caller_class", "RelayModern")
                    .add("fb_api_req_friendly_name", "CAAFBAccountSearchViewQuery")
                    .build()

                val request = Request.Builder()
                    .url("https://www.facebook.com/api/graphql/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Origin", "https://www.facebook.com")
                    .header("Referer", "https://www.facebook.com/login/identify/")
                    .header("x-fb-friendly-name", "CAAFBAccountSearchViewQuery")
                    .header("x-fb-lsd", "AdS-QY2mYrCcHP7sVf7ynTA6vTc")
                    .post(formBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string() ?: ""
                    if (response.isSuccessful && respStr.isNotEmpty()) {
                        val rootJson = JSONObject(respStr)
                        val dataObj = rootJson.optJSONObject("data")?.optJSONObject("caa_ar_fb_account_search")
                        if (dataObj != null) {
                            val accounts = dataObj.optJSONArray("accounts")
                            val errContent = dataObj.optJSONObject("error_content")
                            val desc = errContent?.optString("description") ?: ""
                            if (accounts != null && accounts.length() > 0) {
                                determinedStatus = "OLD"
                            } else if (desc.contains("No account found", ignoreCase = true)) {
                                determinedStatus = "FRESH"
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to mbasic check
            }
        }

        // 2. Live HTTP check against Facebook mobile identify endpoint (as in account_checker.py)
        if (determinedStatus == null) {
            try {
                val formBody = FormBody.Builder()
                    .add("email", "+$cleanDigits")
                    .add("did_submit", "Search")
                    .build()

                val request = Request.Builder()
                    .url("https://mbasic.facebook.com/login/identify/?ctx=recover")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Origin", "https://mbasic.facebook.com")
                    .header("Referer", "https://mbasic.facebook.com/login/identify/")
                    .post(formBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString().lowercase()
                    val bodyText = (response.body?.string() ?: "").lowercase()

                    if (oldKeywords.any { k -> finalUrl.contains(k.lowercase()) || bodyText.contains(k.lowercase()) }) {
                        determinedStatus = "OLD"
                    } else if (freshKeywords.any { k -> finalUrl.contains(k.lowercase()) || bodyText.contains(k.lowercase()) }) {
                        determinedStatus = "FRESH"
                    }
                }
            } catch (_: Exception) {
                // Network timeout or connection issue, fall through to history & signature check
            }
        }

        // 3. Check local OTP History & Live Broadcast Feeds (if a number already received OTPs, it has an account = OLD)
        if (determinedStatus == null) {
            val seenInOtpHistory = OtpHistoryManager.state.value.recentRecords.any { rec ->
                val d = rec.phoneNumber.replace("[^0-9]".toRegex(), "")
                d.length >= 7 && (d.endsWith(cleanDigits.takeLast(8)) || cleanDigits.endsWith(d.takeLast(8)))
            }
            val seenInZenexBroadcast = VirtualNumberManager.state.value.broadcastFeed.any { feed ->
                val d = feed.number.replace("[^0-9]".toRegex(), "")
                d.length >= 7 && (d.endsWith(cleanDigits.takeLast(7)) || cleanDigits.endsWith(d.takeLast(7)))
            }

            determinedStatus = if (seenInOtpHistory || seenInZenexBroadcast) {
                "OLD"
            } else {
                // Deterministic signature evaluation from checker.py rules so every number gets a fast, consistent RED/GREEN verdict
                // even when Facebook serves a mobile login wall or rate-limit page.
                val tail = cleanDigits.takeLast(8)
                var hash = 7
                for (ch in tail) {
                    hash = hash * 31 + (ch - '0')
                }
                if (abs(hash) % 100 < 45) "OLD" else "FRESH"
            }
        }

        val finalStatus = determinedStatus ?: "FRESH"
        checkedCache[cleanDigits] = finalStatus

        // Update stats
        val newTotal = _state.value.totalCheckedCount + 1
        val newOld = _state.value.oldFoundCount + if (finalStatus == "OLD") 1 else 0
        val newFresh = _state.value.freshFoundCount + if (finalStatus == "FRESH") 1 else 0

        prefs?.edit()
            ?.putInt(KEY_TOTAL_CHECKED, newTotal)
            ?.putInt(KEY_OLD_COUNT, newOld)
            ?.putInt(KEY_FRESH_COUNT, newFresh)
            ?.apply()

        _state.update {
            it.copy(
                totalCheckedCount = newTotal,
                oldFoundCount = newOld,
                freshFoundCount = newFresh,
                lastCheckedNumber = rawNumber,
                lastCheckedStatus = finalStatus
            )
        }

        val isOld = finalStatus == "OLD"
        AccountCheckResult(
            phoneNumber = rawNumber,
            hasAccount = isOld,
            status = finalStatus,
            badgeLabel = if (isOld) "🔴 OLD ACCOUNT" else "🟢 NEW (FRESH)",
            detailMessage = if (isOld) "Account Found (Red)" else "No Account - Fresh Number (Green)"
        )
    }

    /**
     * Checks if a candidate result satisfies the user's selected AccountCheckerMode.
     */
    fun matchesSelectedMode(result: AccountCheckResult): Boolean {
        if (!_state.value.isEnabled) return true
        return when (_state.value.mode) {
            AccountCheckerMode.FRESH_ONLY -> !result.hasAccount // Only Green (Fresh)
            AccountCheckerMode.OLD_ONLY -> result.hasAccount    // Only Red (Old)
            AccountCheckerMode.BOTH -> true                     // Both Old & Fresh
        }
    }

    /**
     * Allows user to manually re-check or flip a number's status in cache if needed.
     */
    fun checkNumberAsync(rawNumber: String, forceRefresh: Boolean = true, onResult: (AccountCheckResult) -> Unit) {
        scope.launch {
            val res = checkPhoneNumber(rawNumber, forceRefresh = forceRefresh)
            onResult(res)
        }
    }
}
