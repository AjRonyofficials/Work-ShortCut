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
                // 1. Clean temporary files in cache directory older than 2 days (never touch code_cache!)
                val cacheDir = context.cacheDir
                if (cacheDir != null && cacheDir.exists()) {
                    cleanOldFiles(cacheDir, maxAgeMs = 48 * 60 * 60 * 1000L)
                }

                // 2. Clean external cache directory if available
                context.externalCacheDir?.let {
                    if (it.exists()) cleanOldFiles(it, maxAgeMs = 48 * 60 * 60 * 1000L)
                }

                Log.d(TAG, "Cache and temporary storage safely checked.")
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
