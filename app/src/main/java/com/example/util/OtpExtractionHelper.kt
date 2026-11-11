package com.example.util

import java.util.regex.Pattern

/**
 * Universal, robust 5–8 digit OTP extractor for all SMS Gateways
 * (Unix SMS, Lamix SMS, Hadi SMS, Premium V2, Kop Engine 1 & 2).
 * Handles:
 * - Hidden Unicode characters, RTL/LTR marks (U+200E, U+200F), zero-width spaces (U+200B-U+200D), BOM (U+FEFF)
 * - Arabic-Indic and Persian/Eastern digits (٠-٩) normalized to ASCII (0-9)
 * - Leading, trailing, and middle 5–8 digit OTP codes (e.g. "167331 <#>Facebook ...", "FB-12345", "G-123456")
 * - Hyphenated or spaced 5–8 digit codes (e.g. "123-456" -> "123456", "1234-5678" -> "12345678")
 * - Smart phone number filtering to prevent extracting parts of the user's phone number
 */
object OtpExtractionHelper {

    // Match 5 to 8 consecutive digits (not part of a longer 9+ digit number) using non-consuming lookarounds
    private val DIGIT_5_TO_8_PATTERN = Pattern.compile("(?<![0-9])([0-9]{5,8})(?![0-9])")
    // Match prefixed codes like FB-12345, G-123456, WA-123456
    private val PREFIXED_OTP_PATTERN = Pattern.compile("(?:FB|G|WA|IG|CODE|OTP|PIN)[-:\\s]+([0-9]{5,8})(?![0-9])", Pattern.CASE_INSENSITIVE)
    // Match hyphenated or spaced 6 to 8 digit OTPs (e.g. "123-456", "123 456", "1234-5678")
    private val HYPHEN_OTP_PATTERN = Pattern.compile("(?<![0-9])([0-9]{3,4}[- ][0-9]{3,4})(?![0-9])")

    fun extractOtp(message: String?, phoneDigits: String = ""): String? {
        if (message.isNullOrBlank()) return null

        // 1. Strip zero-width, bidi, and hidden unicode formatting characters
        val sanitized = message
            .replace("[\u200B-\u200F\u202A-\u202E\uFEFF]".toRegex(), " ")
            .replace("\u00A0", " ")
            .trim()

        // 2. Normalize Arabic-Indic and Persian digits to standard ASCII 0-9
        val sb = StringBuilder()
        for (ch in sanitized) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append((ch - '\u0660').toString())
                in '\u06F0'..'\u06F9' -> sb.append((ch - '\u06F0').toString())
                else -> sb.append(ch)
            }
        }
        val normalized = sb.toString()

        val cleanPhone = phoneDigits.replace("[^0-9]".toRegex(), "")

        // 3. Check for explicit prefixed 5–8 digit OTP (e.g. "FB-12345", "G-123456", "code: 123456")
        val prefixMatcher = PREFIXED_OTP_PATTERN.matcher(normalized)
        while (prefixMatcher.find()) {
            val code = prefixMatcher.group(1) ?: continue
            if (code.length in 5..8 && (cleanPhone.isEmpty() || !cleanPhone.contains(code))) {
                return code
            }
        }

        // 4. Check for hyphenated or spaced 6–8 digit OTP (e.g. "123-456" or "1234-5678")
        val hyphenMatcher = HYPHEN_OTP_PATTERN.matcher(normalized)
        while (hyphenMatcher.find()) {
            val hypCode = hyphenMatcher.group(1)?.replace("[- ]".toRegex(), "") ?: continue
            if (hypCode.length in 5..8 && (cleanPhone.isEmpty() || !cleanPhone.contains(hypCode))) {
                return hypCode
            }
        }

        // 5. Extract consecutive digits strictly 5 to 8 characters long
        val matcher = DIGIT_5_TO_8_PATTERN.matcher(normalized)
        val candidates = mutableListOf<String>()
        while (matcher.find()) {
            val code = matcher.group(1) ?: continue
            if (code.length in 5..8) {
                candidates.add(code)
            }
        }

        val valid = if (cleanPhone.isNotEmpty()) {
            candidates.filter { cand -> !cleanPhone.contains(cand) }
        } else candidates

        // Prioritize 6-digit, then 5-digit, then 7-8 digit codes (all within 5..8 digits)
        return valid.firstOrNull { it.length == 6 }
            ?: valid.firstOrNull { it.length == 5 }
            ?: valid.firstOrNull { it.length in 7..8 }
            ?: candidates.firstOrNull { it.length in 5..8 }
    }
}
