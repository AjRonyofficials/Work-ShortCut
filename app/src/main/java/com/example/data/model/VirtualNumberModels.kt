package com.example.data.model

data class ProvisionedNumber(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val country: String = "United Kingdom",
    val operator: String = "Mobile",
    val iso: String = "gb",
    val status: String = "pending", // "pending", "success", "failed"
    val otpCode: String? = null,
    val otpMessage: String? = null,
    val range: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 1_200_000L, // 20 minutes timeout (Zenex panel standard)
    val failReason: String? = null // "Timeout"
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
    val time: Long = System.currentTimeMillis()
)
