package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// ১. নম্বরের ডেটা মডেল (ট্যাগ ও স্ট্যাটাস সহ)
data class PhoneItem(
    val number: String,
    var status: String = "Checking...",
    var tagType: TagType = TagType.PENDING
) {
    enum class TagType {
        FRESH, TAKEN, PENDING, ERROR
    }
}

// ২. মূল চেকার ও ম্যানেজার ক্লাস
object VirtualNumberChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
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
        // সর্বোচ্চ ১০টি নম্বর ফিল্টার করা (আপনার রিকোয়ারমেন্ট অনুযায়ী ১-১০টি)
        return out.take(10)
    }

    // একটি নম্বর লোকাল নেটওয়ার্ক থেকে চেক করার ফাংশন
    fun checkSingleNumber(phone: String): String {
        try {
            val variables = JSONObject().apply {
                put("params", JSONObject().apply {
                    put("cipher_text", CIPHER_TEXT)
                    put("context", "recover")
                    put("event_request_id", UUID.randomUUID().toString())
                    put("friend_name", "")
                    put("search_query", phone)
                    put("waterfall_id", UUID.randomUUID().toString())
                })
            }

            val formBody = StringBuilder().apply {
                append("av=0&")
                append("__user=0&")
                append("__a=1&")
                append("__req=5&")
                append("__hs=20742.HYP:comet_loggedout_pkg.2.1...0&")
                append("dpr=1&")
                append("__ccg=EXCELLENT&")
                append("__rev=1050579926&")
                append("lsd=$LSD&")
                append("jazoest=$JAZOEST&")
                append("fb_api_caller_class=RelayModern&")
                append("fb_api_req_friendly_name=CAAFBAccountSearchViewQuery&")
                append("variables=${java.net.URLEncoder.encode(variables.toString(), "UTF-8")}&")
                append("doc_id=$DOC_ID")
            }.toString()

            val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
            val body = formBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(GRAPHQL_URL)
                .post(body)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Origin", "https://www.facebook.com")
                .addHeader("Referer", "https://www.facebook.com/login/identify/")
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: return "error"

            val json = JSONObject(responseString)
            val data = json.optJSONObject("data") ?: JSONObject()
            val search = data.optJSONObject("caa_ar_fb_account_search") ?: JSONObject()
            val accounts = search.optJSONArray("accounts")
            val err = search.optJSONObject("error_content") ?: JSONObject()
            val desc = err.optString("description", "").trim()

            if (accounts == null || accounts.length() == 0) {
                if (desc.contains("No account found") || desc.isEmpty()) {
                    return "fresh"
                }
            }
            return "taken"
        } catch (e: Exception) {
            return "error"
        }
    }

    // একটি নম্বরের ট্যাগ ও স্ট্যাটাস অ্যাসিনক্রোনাসলি রিটার্ন করার হেল্পার
    suspend fun checkNumberTag(phone: String): Pair<String, PhoneItem.TagType> = withContext(Dispatchers.IO) {
        val formattedNum = if (phone.startsWith("+")) phone else "+$phone"
        val result = checkSingleNumber(formattedNum)
        val tagPair = when (result) {
            "fresh" -> Pair("Fresh (Free)", PhoneItem.TagType.FRESH)
            "taken" -> Pair("Taken (Used)", PhoneItem.TagType.TAKEN)
            else -> Pair("Error", PhoneItem.TagType.ERROR)
        }
        val cleanDigits = phone.replace("[^0-9]".toRegex(), "")
        cachedResults[cleanDigits] = PhoneItem(formattedNum, tagPair.first, tagPair.second)
        tagPair
    }

    // একসাথে ১-১০টি নম্বর চেক করার ব্যাচ প্রসেসর
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
            val result = checkSingleNumber(formattedNum)

            val item = when (result) {
                "fresh" -> PhoneItem(formattedNum, "Fresh (Free)", PhoneItem.TagType.FRESH)
                "taken" -> PhoneItem(formattedNum, "Taken (Used)", PhoneItem.TagType.TAKEN)
                else -> PhoneItem(formattedNum, "Error", PhoneItem.TagType.ERROR)
            }

            val cleanDigits = num.replace("[^0-9]".toRegex(), "")
            cachedResults[cleanDigits] = item
            checkedList.add(item)

            // লাইভ প্রোগ্রেস UI তে পাঠানোর জন্য
            withContext(Dispatchers.Main) {
                onProgress(index + 1, total, item)
            }
        }

        withContext(Dispatchers.Main) {
            onFinished(checkedList)
        }
    }
}
