package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.model.ExcelRowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcelRowDao {
    @Query("SELECT * FROM excel_rows ORDER BY id ASC")
    fun getAllRows(): Flow<List<ExcelRowEntity>>

    @Query("SELECT * FROM excel_rows ORDER BY id ASC")
    suspend fun getAllRowsList(): List<ExcelRowEntity>

    @Query("SELECT * FROM excel_rows WHERE id = :id LIMIT 1")
    suspend fun getRowById(id: Long): ExcelRowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRow(row: ExcelRowEntity): Long

    @Update
    suspend fun updateRow(row: ExcelRowEntity)

    @Delete
    suspend fun deleteRow(row: ExcelRowEntity)

    @Query("DELETE FROM excel_rows")
    suspend fun clearAll()
}
