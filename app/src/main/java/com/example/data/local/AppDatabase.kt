package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ExcelRowDao
import com.example.data.local.dao.ProxyProfileDao
import com.example.data.local.dao.TwoFactorDao
import com.example.data.local.model.ExcelRowEntity
import com.example.data.local.model.ProxyProfileEntity
import com.example.data.local.model.TwoFactorKeyEntity

@Database(
    entities = [
        ExcelRowEntity::class,
        ProxyProfileEntity::class,
        TwoFactorKeyEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun excelRowDao(): ExcelRowDao
    abstract fun proxyProfileDao(): ProxyProfileDao
    abstract fun twoFactorDao(): TwoFactorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "work_shortcut_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
