package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// ১. নম্বরের ডেটা মডেল (ট্যাগ ও স্ট্যাটাস সহ)
data class PhoneItem(
    val number: String,
    var status: String = "Checking...",
    var tagType: TagType = TagType.PENDING,
    var description: String = ""
) {
    enum class TagType {
        FRESH, TAKEN, PENDING, ERROR, UNKNOWN
    }
}

// ২. মূল চেকার ও ম্যানেজার ক্লাস (হুবহু fb_checker.py এর অ্যালগরিদম ও পেলোড অনুযায়ী)
object VirtualNumberChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val GRAPHQL_URL = "https://www.facebook.com/api/graphql/"
    private const val LSD = "AdS-QY2mYrCcHP7sVf7ynTA6vTc"
    private const val CIPHER_TEXT = "AdAtB3O832KrYgWu2c7Jm2mNWOdAALA9wNFKJ_043RSsW27RSXXjhlBd0Cr8rGn-lIwgDuQU-CNQILiqKbqFW0GZop7nLgDVhClntKlePmNpHzT4ESf47v-lTUCNV3L-ikw70LhayHi30trSUJqk-Rvmaost-m0CTl60dFiIcbLNhGmQ8uggs-M1wfu04KQJvOQBDLwohSr7f_yRqkDGj98Ra-1S2N77k17Fqxu8N84cEWWTyv4qW6ySj7GI7CYYhD8QZDTzMxXcImxAr_Y"
    private const val JAZOEST = "22291"
    private const val DOC_ID = "27012969198380071"

    // Checked status cache for instant UI rendering across recompositions
    val cachedResults = ConcurrentHashMap<String, PhoneItem>()

    // ইউজার যেভাবেই নম্বর নিক না কেন (কমা, স্পেস বা লাইন), তা থেকে সঠিক নম্বরগুলো আলাদা করার পার্সার
    fun parseNumbers(rawInput: String): List<String> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        val matcher = Pattern.compile("\\d+").matcher(rawInput)
        while (matcher.find()) {
            val d = matcher.group()
            if (d.length >= 7 && !seen.contains(d)) {
                seen.add(d)
                out.add(d)
            }
        }
        // সর্বোচ্চ ১০টি নম্বর ফিল্টার করা (১-১০টি)
        return out.take(10)
    }

    data class CheckResult(
        val status: String, // "free", "taken", "unknown", "error"
        val tagType: PhoneItem.TagType,
        val description: String
    )

    // একটি নম্বর লোকাল নেটওয়ার্ক থেকে চেক করার ফাংশন (fb_checker.py স্পেসিফিকেশন)
    fun checkSingleNumberDirect(phone: String): CheckResult {
        try {
            val formattedPhone = if (phone.startsWith("+")) phone else "+$phone"
            val variablesObj = JSONObject().apply {
                put("params", JSONObject().apply {
                    put("cipher_text", CIPHER_TEXT)
                    put("context", "recover")
                    put("event_request_id", UUID.randomUUID().toString())
                    put("friend_name", "")
                    put("search_query", formattedPhone)
                    put("waterfall_id", UUID.randomUUID().toString())
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
                .add("__s", "o3r701:e35cp2:a8v994")
                .add("__hsi", "7697203363611462860")
                .add("__dyn", "7xe6E5q5U5ObwKBAg5S1Dxu13w8CewSwMwNw9G2S0im3y4o0B-q1ew6ywaq0yE6ucw5Mx62G3i1ywOwv89k2C1Fwc60D85m1mzXwae4UaEW0LobrwmE2eUlwhE2FBwxw4BwqEGdwtU2ewbS1LwTwKG2-0VE6O1FwgWzU6PhA6bwlV4A2K7E5y0_8464o1oE0ye3mdw9-")
                .add("__csr", "gGR8qxcyAV1W4Ebv7OkytOluZ25Vp48CB9z8x4Qp194pAmF_GXqbH8V6VmF48lprALCJaXCaByi9pAF9d4JUORpFtxm4uA1bwPVU46vyUN4yem3yy12m2h1quFUK4UaLwFxa2y0IpoqxS581mEc8CA08Qguw44xG6Q0cUwWw9u78ekx0O217wm83wwYw7uw0h_uteQu0su07j80ZS0bCDgixihw0MxAw34E0LOi04y8013wFzw0jD202Oo09D60oqbw0F7w0ilF807q-ii19w61wewU8U0r3wywbVw1QOa85oO")
                .add("__hsdp", "gebY6HgNAH1211wa26C22K8c0rZe0JA9wywx80X80we08Iw8i09Ew0BDw2D81wo")
                .add("__hblp", "0dS0jS0Ao1ckU31wgp83JwfK360PVo2-wdu0Bonwr814Eqwg83Vw1460nS0vW0o62q0QU1O81wobUG2q1axe0vK0Fo6m0oS4U2Bw")
                .add("__sjsp", "gebYuwD4onpaMgwgo2Xo1-82SgC2a24w")
                .add("__comet_req", "15")
                .add("lsd", LSD)
                .add("jazoest", JAZOEST)
                .add("__spin_r", "1050579926")
                .add("__spin_b", "trunk")
                .add("__spin_t", (System.currentTimeMillis() / 1000L).toString())
                .add("__crn", "comet.fbweb.CometCAAAccountSearchRoute")
                .add("qpl_active_flow_ids", "516759801")
                .add("fb_api_caller_class", "RelayModern")
                .add("fb_api_req_friendly_name", "CAAFBAccountSearchViewQuery")
                .add("server_timestamps", "true")
                .add("variables", variablesObj.toString())
                .add("doc_id", DOC_ID)
                .add("fb_api_analytics_tags", "[\"qpl_active_flow_ids=516759801\"]")
                .build()

            val request = Request.Builder()
                .url(GRAPHQL_URL)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("sec-ch-ua-full-version-list", "\"Chromium\";v=\"155.0.8059.43\", \"Not(A:Brand\";v=\"24.0.0.0\"")
                .addHeader("sec-ch-ua-platform", "\"Windows\"")
                .addHeader("x-fb-navigation-chain", "CAAFBAccountSearchRoot.react,comet.caa.account_recovery.account_search,via_cold_start,1792144816098,553013,,,")
                .addHeader("sec-ch-ua", "\"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"")
                .addHeader("x-fb-friendly-name", "CAAFBAccountSearchViewQuery")
                .addHeader("sec-ch-ua-mobile", "?0")
                .addHeader("sec-ch-ua-model", "\"itel S665L\"")
                .addHeader("x-asbd-id", "359341")
                .addHeader("x-fb-lsd", LSD)
                .addHeader("sec-ch-prefers-color-scheme", "light")
                .addHeader("sec-ch-ua-platform-version", "\"12.0.0\"")
                .addHeader("origin", "https://www.facebook.com")
                .addHeader("x-requested-with", "mark.via.gp")
                .addHeader("sec-fetch-site", "same-origin")
                .addHeader("sec-fetch-mode", "cors")
                .addHeader("sec-fetch-dest", "empty")
                .addHeader("referer", "https://www.facebook.com/login/identify/")
                .addHeader("accept-language", "en-US,en;q=0.9,my-MM;q=0.8,my;q=0.7,fr-FR;q=0.6,fr;q=0.5")
                .addHeader("priority", "u=1, i")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return CheckResult("error", PhoneItem.TagType.ERROR, "HTTP ${response.code}")
            }
            val responseString = response.body?.string() ?: return CheckResult("error", PhoneItem.TagType.ERROR, "Empty response")

            val json = JSONObject(responseString)
            val data = json.optJSONObject("data") ?: JSONObject()
            val search = data.optJSONObject("caa_ar_fb_account_search") ?: JSONObject()
            val accounts = search.optJSONArray("accounts")
            val err = search.optJSONObject("error_content") ?: JSONObject()
            val desc = err.optString("description", "").trim()

            val accountCount = accounts?.length() ?: 0

            if (accountCount == 0 && (desc.contains("No account found", ignoreCase = true) || desc.contains("couldn't find", ignoreCase = true))) {
                return CheckResult("free", PhoneItem.TagType.FRESH, desc.ifEmpty { "No account found (Free)" })
            }
            if (accountCount > 0) {
                return CheckResult("taken", PhoneItem.TagType.TAKEN, "$accountCount account(s)")
            }
            if (desc.isNotEmpty()) {
                return CheckResult("taken", PhoneItem.TagType.TAKEN, desc)
            }
            return CheckResult("unknown", PhoneItem.TagType.UNKNOWN, "No clear result")
        } catch (e: Exception) {
            return CheckResult("error", PhoneItem.TagType.ERROR, e.message ?: "Network error")
        }
    }

    fun checkSingleNumber(phone: String): String {
        val res = checkSingleNumberDirect(phone)
        return when (res.tagType) {
            PhoneItem.TagType.FRESH -> "fresh"
            PhoneItem.TagType.TAKEN -> "taken"
            PhoneItem.TagType.UNKNOWN -> "unknown"
            else -> "error"
        }
    }

    // একটি নম্বরের ট্যাগ ও স্ট্যাটাস অ্যাসিনক্রোনাসলি রিটার্ন করার হেল্পার (১ বার retry সহ)
    suspend fun checkNumberTag(phone: String): Pair<String, PhoneItem.TagType> = withContext(Dispatchers.IO) {
        val formattedNum = if (phone.startsWith("+")) phone else "+$phone"
        
        var result = checkSingleNumberDirect(formattedNum)
        if (result.tagType == PhoneItem.TagType.ERROR) {
            delay(1500L)
            result = checkSingleNumberDirect(formattedNum)
        }

        val tagPair = when (result.tagType) {
            PhoneItem.TagType.FRESH -> Pair("Fresh (Free)", PhoneItem.TagType.FRESH)
            PhoneItem.TagType.TAKEN -> Pair("Taken (Used)", PhoneItem.TagType.TAKEN)
            PhoneItem.TagType.UNKNOWN -> Pair("Unknown", PhoneItem.TagType.UNKNOWN)
            else -> Pair("Error", PhoneItem.TagType.ERROR)
        }
        val cleanDigits = phone.replace("[^0-9]".toRegex(), "")
        cachedResults[cleanDigits] = PhoneItem(formattedNum, tagPair.first, tagPair.second, result.description)
        tagPair
    }

    // একসাথে ১-১০টি নম্বর চেক করার ব্যাচ প্রসেসর (delay 400ms + 1 retry per fb_checker.py)
    suspend fun processVirtualNumbers(
        rawNumbersInput: String,
        onProgress: (Int, Int, PhoneItem) -> Unit,
        onFinished: (List<PhoneItem>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val numbers = parseNumbers(rawNumbersInput)
        val checkedList = mutableListOf<PhoneItem>()
        val total = numbers.size

        for ((index, num) in numbers.withIndex()) {
            val formattedNum = if (num.startsWith("+")) num else "+$num"
            
            var result = checkSingleNumberDirect(formattedNum)
            if (result.tagType == PhoneItem.TagType.ERROR) {
                delay(1500L)
                result = checkSingleNumberDirect(formattedNum)
            }

            val (statusText, tagType) = when (result.tagType) {
                PhoneItem.TagType.FRESH -> Pair("Fresh (Free)", PhoneItem.TagType.FRESH)
                PhoneItem.TagType.TAKEN -> Pair("Taken (Used)", PhoneItem.TagType.TAKEN)
                PhoneItem.TagType.UNKNOWN -> Pair("Unknown", PhoneItem.TagType.UNKNOWN)
                else -> Pair("Error", PhoneItem.TagType.ERROR)
            }

            val item = PhoneItem(formattedNum, statusText, tagType, result.description)
            val cleanDigits = num.replace("[^0-9]".toRegex(), "")
            cachedResults[cleanDigits] = item
            checkedList.add(item)

            // লাইভ প্রোগ্রেস UI তে পাঠানোর জন্য
            withContext(Dispatchers.Main) {
                onProgress(index + 1, total, item)
            }

            if (index < total - 1) {
                delay(400L) // 0.4s delay as in fb_checker.py
            }
        }

        withContext(Dispatchers.Main) {
            onFinished(checkedList)
        }
    }
}
