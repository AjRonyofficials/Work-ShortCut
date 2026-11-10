package com.example.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "excel_rows")
data class ExcelRowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val colA: String = "",
    val colB: String = "",
    val colC: String = "",
    val colD: String = "",
    val colE: String = "",
    val colF: String = "",
    val hasDuplicateWarning: Boolean = false,
    val duplicateDetails: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
