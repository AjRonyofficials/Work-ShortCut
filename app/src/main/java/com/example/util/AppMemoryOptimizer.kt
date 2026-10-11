package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages RAM & ROM optimization:
 * - Automatically cleans obsolete temporary cache files so app size stays minimal.
 * - Prevents memory bloat and keeps device performance snappy.
 */
object AppMemoryOptimizer {
    private const val TAG = "MemoryOptimizer"

    fun autoOptimize(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Clean app internal cache directory
                cleanDirectory(context.cacheDir)

                // 2. Clean external cache directory if available
                context.externalCacheDir?.let { cleanDirectory(it) }

                // 3. Clean temporary files in code_cache older than 1 day
                val codeCache = File(context.applicationInfo.dataDir, "code_cache")
                if (codeCache.exists() && codeCache.isDirectory) {
                    cleanOldFiles(codeCache, maxAgeMs = 24 * 60 * 60 * 1000L)
                }

                Log.d(TAG, "Cache and temporary storage successfully optimized.")
            } catch (e: Exception) {
                Log.w(TAG, "Optimization exception: ${e.message}")
            }
        }
    }

    private fun cleanDirectory(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            try {
                if (file.isDirectory) {
                    cleanDirectory(file)
                    file.delete()
                } else {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun cleanOldFiles(dir: File, maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        val files = dir.listFiles() ?: return
        for (f in files) {
            try {
                if (f.isFile && (now - f.lastModified() > maxAgeMs)) {
                    f.delete()
                }
            } catch (_: Exception) {}
        }
    }
}
