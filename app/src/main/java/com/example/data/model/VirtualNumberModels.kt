package com.example.data.model

data class ProvisionedNumber(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val country: String = "United Kingdom",
    val operator: String = "Mobile",
    val iso: String = "gb",
    val status: String = "pending", // "pending", "success", "failed", "expired"
    val otpCode: String? = null,
    val otpMessage: String? = null,
    val range: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 1_200_000L,
    val failReason: String? = null,
    val requestId: Long? = null,
    val gateway: String = "LAMIX",
    val rate: Double = 0.50,
    val rentalId: String? = null,
    val flag: String? = null,
    val fbStatus: String? = null,
    val fbTagType: com.example.service.PhoneItem.TagType = com.example.service.PhoneItem.TagType.PENDING,
    val userEmail: String = "",
    val isMarked: Boolean = false
)

data class ActiveRangeItem(
    val range: String,
    val service: String = "General",
    val tag: String = "Premium",
    val hits: Int = 0
)

data class BroadcastFeedItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val range: String = "",
    val service: String = "WHATSAPP",
    val country: String = "United Kingdom",
    val operator: String = "Mobile",
    val otp: String = "",
    val time: Long = System.currentTimeMillis(),
    val isNew: Boolean = false
) {
    val fullTimeFormatted: String
        get() {
            return try {
                val sdf = java.text.SimpleDateFormat("hh:mm:ss a · dd MMM yyyy", java.util.Locale.US)
                val ms = if (time in 1..9_999_999_999L) time * 1000L else time
                sdf.format(java.util.Date(if (ms > 0L) ms else System.currentTimeMillis()))
            } catch (_: Exception) {
                "04:11:24 PM · 03 Nov 2026"
            }
        }

    val elapsedText: String
        get() {
            val ms = if (time in 1..9_999_999_999L) time * 1000L else time
            val diffSec = maxOf(0L, (System.currentTimeMillis() - ms) / 1000L)
            return when {
                diffSec < 60 -> "${diffSec}s ago"
                diffSec < 3600 -> "${diffSec / 60}m ${diffSec % 60}s ago"
                else -> "${diffSec / 3600}h ago"
            }
        }
}

