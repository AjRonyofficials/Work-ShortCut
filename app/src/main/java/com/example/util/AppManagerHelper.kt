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
        val isOfficialKatana = p == "com.facebook.katana" || p == "com.facebook.wakizashi" || (a == "facebook" && !a.contains("lite"))
        val isExplicitLite = p.contains("lite") || a.contains("lite") || p.contains("fblite") || a.contains("fblite") ||
                p.contains("com.facebook.lite") || p.contains("lite96") || p.contains("lite_f") || p.contains("aerofacebook")
        return isExplicitLite && !isOfficialKatana
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
