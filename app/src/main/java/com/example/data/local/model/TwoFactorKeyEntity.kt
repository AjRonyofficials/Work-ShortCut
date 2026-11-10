package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "two_factor_keys")
data class TwoFactorKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val secretKey: String,
    val issuer: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
