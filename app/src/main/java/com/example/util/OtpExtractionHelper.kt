package com.example.util

import java.util.regex.Pattern

/**
 * Universal, robust OTP extractor for all SMS Gateways (Unix SMS, Lamix, Premium V2, Kop Engine, Zenex).
 * Handles:
 * - Hidden Unicode characters, RTL/LTR marks (U+200E, U+200F), zero-width spaces (U+200B-U+200D), BOM (U+FEFF)
 * - Arabic-Indic and Persian/Eastern digits (٠-٩) normalized to ASCII (0-9)
 * - Leading, trailing, and middle OTP codes (e.g. Arabic Facebook SMS format: "167331 <#>Facebook ...")
 * - Hyphenated codes (e.g. "123-456" -> "123456")
 * - Smart phone number filtering to prevent extracting parts of the user's phone number
 */
object OtpExtractionHelper {

    private val DIGIT_BLOCK_PATTERN = Pattern.compile("(?:^|[^0-9])([0-9]{4,8})(?:[^0-9]|$)")
    private val HYPHEN_OTP_PATTERN = Pattern.compile("(?:^|[^0-9])([0-9]{3}[- ][0-9]{3})(?:[^0-9]|$)")

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

        // 3. Check for hyphenated or spaced 6-digit OTP (e.g. "123-456" or "123 456")
        val hyphenMatcher = HYPHEN_OTP_PATTERN.matcher(normalized)
        if (hyphenMatcher.find()) {
            val hypCode = hyphenMatcher.group(1)?.replace("[- ]".toRegex(), "")
            if (!hypCode.isNullOrBlank() && (cleanPhone.isEmpty() || !cleanPhone.contains(hypCode))) {
                return hypCode
            }
        }

        // 4. Extract consecutive digits 4 to 8 characters long
        val matcher = DIGIT_BLOCK_PATTERN.matcher(normalized)
        val candidates = mutableListOf<String>()
        while (matcher.find()) {
            val code = matcher.group(1) ?: continue
            candidates.add(code)
        }

        val valid = if (cleanPhone.isNotEmpty()) {
            candidates.filter { cand -> !cleanPhone.contains(cand) }
        } else candidates

        // Prioritize standard 6-digit OTP (Facebook, WhatsApp, Google, etc.), then 5-8, then 4
        return valid.firstOrNull { it.length == 6 }
            ?: valid.firstOrNull { it.length in 5..8 }
            ?: valid.firstOrNull { it.length == 4 }
            ?: candidates.firstOrNull { it.length in 4..8 }
    }
}
