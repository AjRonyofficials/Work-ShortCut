package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.model.TwoFactorKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TwoFactorDao {
    @Query("SELECT * FROM two_factor_keys ORDER BY id DESC")
    fun getAllKeys(): Flow<List<TwoFactorKeyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: TwoFactorKeyEntity): Long

    @Update
    suspend fun updateKey(key: TwoFactorKeyEntity)

    @Delete
    suspend fun deleteKey(key: TwoFactorKeyEntity)
}
