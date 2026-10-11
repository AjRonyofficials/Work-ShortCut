package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.model.ProxyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyProfileDao {
    @Query("SELECT * FROM proxy_profiles ORDER BY id DESC")
    fun getAllProxies(): Flow<List<ProxyProfileEntity>>

    @Query("SELECT * FROM proxy_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProxy(): Flow<ProxyProfileEntity?>

    @Query("SELECT * FROM proxy_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProxyOnce(): ProxyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyProfileEntity): Long

    @Update
    suspend fun updateProxy(proxy: ProxyProfileEntity)

    @Delete
    suspend fun deleteProxy(proxy: ProxyProfileEntity)

    @Query("UPDATE proxy_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE proxy_profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("UPDATE proxy_profiles SET lastPingMs = :pingMs WHERE id = :id")
    suspend fun updatePing(id: Long, pingMs: Long)
}
