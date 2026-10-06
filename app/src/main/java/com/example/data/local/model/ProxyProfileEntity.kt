package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_profiles")
data class ProxyProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "Default Proxy",
    val protocol: String = "SOCKS5", // SOCKS5 or HTTP
    val host: String = "127.0.0.1",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val countryCode: String = "US",
    val isActive: Boolean = false,
    val lastPingMs: Long = -1,
    val lastConnectedTime: Long = 0
)
