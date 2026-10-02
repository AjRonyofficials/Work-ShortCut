package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

data class AppInfoItem(
    val appName: String,
    val packageName: String,
    val isSelected: Boolean = false,
    val isLiteStorageApp: Boolean = false
)

object AppManagerHelper {

    /**
     * Specifically identifies Facebook Lite and modified Lite variants
     * which feature internal "Clear Storage on Your Phone" with "Accounts and settings" checkbox.
     */
    fun isFacebookLite(packageName: String, appName: String): Boolean {
        val p = packageName.lowercase()
        val a = appName.lowercase()
        val isExplicitLite = p.contains("lite") || p.contains("fblite") || a.contains("lite")
        val isFacebook = p.contains("facebook") || a.contains("facebook")
        // Exclude official full Facebook app
        val isOfficial = p == "com.facebook.katana" || p == "com.facebook.wakizashi" || (a == "facebook" && !a.contains("lite"))
        return (isExplicitLite && isFacebook) || (p.contains("com.facebook.lite") || p.contains("lite96") || p.contains("lite_f")) && !isOfficial
    }

    /**
     * Specifically identifies official Facebook full app (com.facebook.katana)
     */
    fun isFacebookOfficial(packageName: String, appName: String): Boolean {
        val p = packageName.lowercase()
        val a = appName.lowercase()
        return p == "com.facebook.katana" || p == "com.facebook.wakizashi" || (a == "facebook" && !a.contains("lite"))
    }

    fun isLiteOrModdedApp(packageName: String, appName: String): Boolean {
        return isFacebookLite(packageName, appName)
    }

    fun getInstalledLauncherApps(context: Context): List<AppInfoItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        return resolveInfos.mapNotNull { info ->
            try {
                val pkgName = info.activityInfo.packageName
                // exclude self
                if (pkgName == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm).toString()
                AppInfoItem(
                    appName = label,
                    packageName = pkgName,
                    isLiteStorageApp = isFacebookLite(pkgName, label)
                )
            } catch (_: Exception) {
                null
            }
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    /**
     * Triggers zero-touch automated clearing of data and cache via AccessibilityService
     */
    fun openAppDetailsForClearData(
        context: Context,
        packageName: String,
        appName: String = "App",
        isLiteStorageMode: Boolean = false
    ) {
        val effectiveIsLite = isLiteStorageMode || isFacebookLite(packageName, appName)
        com.example.service.AutoCleanAccessibilityService.startAutoClean(
            context = context,
            packageName = packageName,
            appName = appName,
            isLiteMode = effectiveIsLite
        )
    }

    /**
     * Sequentially clears data and cache for multiple selected applications
     * Works smoothly on all Android 10-16+ devices (Samsung, Xiaomi, Vivo, Oppo, Realme, Pixel)
     */
    fun startBatchClearData(
        context: Context,
        apps: List<AppInfoItem>
    ) {
        if (apps.isEmpty()) return
        com.example.service.AutoCleanAccessibilityService.startBatchClean(context, apps)
    }

    fun stopBatchClearData() {
        com.example.service.AutoCleanAccessibilityService.stopBatchClean()
    }

    /**
     * Clears local application cache files in the background
     */
    fun clearSelfCache(context: Context): Boolean {
        return try {
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
            true
        } catch (_: Exception) {
            false
        }
    }
}
