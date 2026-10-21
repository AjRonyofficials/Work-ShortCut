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
    val flag: String? = null
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
