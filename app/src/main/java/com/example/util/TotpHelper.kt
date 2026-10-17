package com.example.util

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpHelper {

    data class TotpResult(
        val code: String,
        val formattedCode: String,
        val remainingSeconds: Int,
        val progress: Float
    )

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun cleanSecretKey(key: String): String {
        return key.trim().replace(" ", "").replace("-", "").uppercase()
    }

    fun extractSecretKey(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            val secretParam = Regex("[?&]secret=([A-Za-z2-7=]+)", RegexOption.IGNORE_CASE)
            val match = secretParam.find(trimmed)
            if (match != null) {
                return cleanSecretKey(match.groupValues[1])
            }
        }
        val cleanCandidate = cleanSecretKey(trimmed)
        if (cleanCandidate.all { it in BASE32_ALPHABET || it == '=' } && cleanCandidate.length >= 8) {
            return cleanCandidate
        }
        return cleanCandidate
    }

    fun generateTotp(secretKey: String, period: Int = 30): TotpResult? {
        val clean = cleanSecretKey(secretKey)
        if (clean.length < 8) return null

        return try {
            val decodedKey = decodeBase32(clean) ?: return null
            val currentTimeSeconds = System.currentTimeMillis() / 1000L
            val timeStep = currentTimeSeconds / period
            val remainingSeconds = (period - (currentTimeSeconds % period)).toInt()
            val progress = remainingSeconds.toFloat() / period.toFloat()

            val codeNumber = generateTotpNumber(decodedKey, timeStep, 6)
            val codeStr = String.format("%06d", codeNumber)
            val formatted = "${codeStr.substring(0, 3)} ${codeStr.substring(3)}"

            TotpResult(
                code = codeStr,
                formattedCode = formatted,
                remainingSeconds = remainingSeconds,
                progress = progress
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun generateTotpNumber(key: ByteArray, timeStep: Long, digits: Int): Long {
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()
        val mac = Mac.getInstance("HmacSHA1")
        val signKey = SecretKeySpec(key, "RAW")
        mac.init(signKey)
        val hash = mac.doFinal(data)

        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val truncatedHash = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulus = 10.0.pow(digits.toDouble()).toLong()
        return (truncatedHash.toLong() and 0x7FFFFFFF) % modulus
    }

    private fun decodeBase32(input: String): ByteArray? {
        val sanitized = input.trim().uppercase().replace("=", "")
        if (sanitized.isEmpty()) return null

        var buffer = 0
        var bitsLeft = 0
        val out = mutableListOf<Byte>()

        for (ch in sanitized) {
            val index = BASE32_ALPHABET.indexOf(ch)
            if (index == -1) return null
            buffer = (buffer shl 5) or index
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }

        return out.toByteArray()
    }
}

typealias TotpResult = TotpHelper.TotpResult
typealias TotpModel = TotpHelper.TotpResult
