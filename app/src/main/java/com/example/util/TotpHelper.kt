package com.example.util

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

object TotpHelper {

    private const val TIME_STEP_SECONDS = 30L
    private const val DIGITS = 6
    private const val CRYPTO_ALGO = "HmacSHA1"

    data class TotpResult(
        val code: String,
        val formattedCode: String,
        val remainingSeconds: Int,
        val progress: Float
    )

    fun extractSecretKey(rawText: String): String {
        val text = rawText.trim()
        if (text.isEmpty()) return ""

        // 1. otpauth URI check
        if (text.contains("secret=", ignoreCase = true)) {
            val extracted = text.substringAfter("secret=").substringBefore("&").substringBefore(" ").trim()
            val cleaned = cleanBase32(extracted)
            if (cleaned.length >= 8) return cleaned
        }

        // 2. Extract potential key after prefixes
        var candidate = text
        listOf("key:", "secret:", "2fa:", "code:", "totp:").forEach { prefix ->
            if (candidate.startsWith(prefix, ignoreCase = true)) {
                candidate = candidate.substring(prefix.length).trim()
            }
        }

        // 3. Regex match for base32 string (8 to 64 chars of Base32 A-Z, 2-7)
        val cleanCandidate = cleanBase32(candidate)
        if (cleanCandidate.length >= 8) {
            return cleanCandidate
        }

        // 4. Scan within raw text for contiguous Base32 blocks
        val base32Pattern = Regex("[A-Za-z2-7]{8,64}")
        val match = base32Pattern.find(text.replace(" ", "").replace("-", ""))
        if (match != null) {
            return match.value.uppercase()
        }

        return cleanBase32(text)
    }

    fun cleanBase32(key: String): String {
        return key.replace(" ", "")
            .replace("-", "")
            .replace("=", "")
            .uppercase()
            .filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" }
    }

    fun generateTotp(secretKey: String, timeMillis: Long = System.currentTimeMillis()): TotpResult? {
        val cleanSecret = extractSecretKey(secretKey)
        if (cleanSecret.isEmpty()) return null

        val keyBytes = try {
            base32Decode(cleanSecret)
        } catch (_: Exception) {
            return null
        }

        if (keyBytes.isEmpty()) return null

        val currentSecond = timeMillis / 1000L
        val timeIndex = currentSecond / TIME_STEP_SECONDS
        val remainingSeconds = (TIME_STEP_SECONDS - (currentSecond % TIME_STEP_SECONDS)).toInt()
        val progress = remainingSeconds.toFloat() / TIME_STEP_SECONDS.toFloat()

        return try {
            val data = ByteBuffer.allocate(8).putLong(timeIndex).array()
            val signKey = SecretKeySpec(keyBytes, CRYPTO_ALGO)
            val mac = Mac.getInstance(CRYPTO_ALGO)
            mac.init(signKey)
            val hash = mac.doFinal(data)

            val offset = (hash[hash.size - 1] and 0x0F).toInt()
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val otp = binary % 1_000_000
            val code = otp.toString().padStart(DIGITS, '0')
            val formatted = "${code.substring(0, 3)} ${code.substring(3)}"
            TotpResult(code = code, formattedCode = formatted, remainingSeconds = remainingSeconds, progress = progress)
        } catch (_: Exception) {
            null
        }
    }

    private fun base32Decode(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = base32.trimEnd('=')
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>()

        for (c in clean) {
            val charVal = alphabet.indexOf(c)
            if (charVal < 0) {
                // Ignore illegal characters or throw
                continue
            }
            buffer = (buffer shl 5) or charVal
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}
