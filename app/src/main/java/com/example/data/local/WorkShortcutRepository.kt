package com.example.data.local

import com.example.data.local.dao.ExcelRowDao
import com.example.data.local.dao.ProxyProfileDao
import com.example.data.local.dao.TwoFactorDao
import com.example.data.local.model.ExcelRowEntity
import com.example.data.local.model.ProxyProfileEntity
import com.example.data.local.model.TwoFactorKeyEntity
import kotlinx.coroutines.flow.Flow

class WorkShortcutRepository(
    private val excelRowDao: ExcelRowDao,
    private val proxyProfileDao: ProxyProfileDao,
    private val twoFactorDao: TwoFactorDao
) {
    // Excel rows
    val allExcelRows: Flow<List<ExcelRowEntity>> = excelRowDao.getAllRows()
    suspend fun getAllExcelRowsList(): List<ExcelRowEntity> = excelRowDao.getAllRowsList()
    suspend fun getExcelRowById(id: Long): ExcelRowEntity? = excelRowDao.getRowById(id)

    suspend fun insertExcelRow(row: ExcelRowEntity): Long = excelRowDao.insertRow(row)
    suspend fun updateExcelRow(row: ExcelRowEntity) = excelRowDao.updateRow(row)
    suspend fun deleteExcelRow(row: ExcelRowEntity) = excelRowDao.deleteRow(row)
    suspend fun clearAllExcelRows() = excelRowDao.clearAll()

    // Proxy Profiles
    val allProxies: Flow<List<ProxyProfileEntity>> = proxyProfileDao.getAllProxies()
    val activeProxy: Flow<ProxyProfileEntity?> = proxyProfileDao.getActiveProxy()

    suspend fun insertProxy(proxy: ProxyProfileEntity): Long = proxyProfileDao.insertProxy(proxy)
    suspend fun updateProxy(proxy: ProxyProfileEntity) = proxyProfileDao.updateProxy(proxy)
    suspend fun deleteProxy(proxy: ProxyProfileEntity) = proxyProfileDao.deleteProxy(proxy)
    suspend fun setActiveProxy(id: Long) {
        proxyProfileDao.deactivateAll()
        proxyProfileDao.setActive(id)
    }
    suspend fun deactivateAllProxies() = proxyProfileDao.deactivateAll()
    suspend fun updateProxyPing(id: Long, pingMs: Long) = proxyProfileDao.updatePing(id, pingMs)
    suspend fun getActiveProxyOnce(): ProxyProfileEntity? = proxyProfileDao.getActiveProxyOnce()

    // 2FA Keys
    val allTwoFactorKeys: Flow<List<TwoFactorKeyEntity>> = twoFactorDao.getAllKeys()
    suspend fun insertTwoFactorKey(key: TwoFactorKeyEntity): Long = twoFactorDao.insertKey(key)
    suspend fun updateTwoFactorKey(key: TwoFactorKeyEntity) = twoFactorDao.updateKey(key)
    suspend fun deleteTwoFactorKey(key: TwoFactorKeyEntity) = twoFactorDao.deleteKey(key)
}
