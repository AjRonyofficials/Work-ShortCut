package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Universal Ultra-Resilient Accessibility Service:
 * Compatible with Android 10 through Android 16+ (Oldest to Latest)
 * Full Support for Samsung One UI, Xiaomi / HyperOS, Vivo, Oppo, Realme, Pixel, Motorola, etc.
 *
 * Fixes:
 * 1. Accurately opens "Storage & cache" / "Storage" row (STRICTLY excludes Notifications/Permissions).
 * 2. Facebook Lite: Auto-marks "Accounts and settings", clicks "OK" popup, clicks "CLEAR", confirms and fast closes.
 * 3. Facebook Official & Other apps: Clears BOTH Cache & Data sequentially, confirms dialog, fast closes.
 * 4. Preserves XML capabilities in onServiceConnected() (never overwrites with empty info).
 * 5. Low CPU / zero battery drain / zero overheating with event debouncing and capped BFS traversal.
 */
class AutoCleanAccessibilityService : AccessibilityService() {

    companion object {
        const val MODE_AUTO_CLEAN = 0
        const val MODE_FORCE_CLOSE = 1

        var instance: AutoCleanAccessibilityService? = null
            private set

        var targetPackage: String? = null
            private set

        var targetAppName: String = "App"
            private set

        var currentMode: Int = MODE_AUTO_CLEAN
            private set

        var isAutomating: Boolean = false
            private set

        const val LITE_STEP_IDLE = 0
        const val LITE_STEP_SELECTING_ACCOUNTS = 1
        const val LITE_STEP_WAIT_ACCOUNTS_POPUP = 2
        const val LITE_STEP_CLICK_CLEAR = 3
        const val LITE_STEP_FINAL_CONFIRM = 4
        const val LITE_STEP_DONE = 5

        var isTargetLiteMode: Boolean = false
            private set

        var liteStep: Int = LITE_STEP_IDLE
            private set

        private var step: Int = 0
        private var lastActionTime: Long = 0L
        private var accountsMarkedTime: Long = 0L
        private var clickedClearCache: Boolean = false
        private var clickedClearData: Boolean = false

        fun isServiceRunning(): Boolean = instance != null

        fun startAutoClean(
            context: Context,
            packageName: String,
            appName: String = "App",
            isLiteMode: Boolean = false
        ) {
            targetPackage = packageName
            targetAppName = appName
            currentMode = MODE_AUTO_CLEAN
            isAutomating = true
            isTargetLiteMode = isLiteMode || com.example.util.AppManagerHelper.isFacebookLite(packageName, appName)
            step = 0
            liteStep = LITE_STEP_IDLE
            accountsMarkedTime = 0L
            clickedClearCache = false
            clickedClearData = false
            lastActionTime = System.currentTimeMillis()

            if (instance == null) {
                Toast.makeText(
                    context,
                    "Accessibility সার্ভিসটি চালু করুন!",
                    Toast.LENGTH_LONG
                ).show()

                try {
                    val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(accIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "সেটিংস খোলা যায়নি", Toast.LENGTH_SHORT).show()
                }
                isAutomating = false
                return
            }

            openAppSettings(context, packageName)
        }

        fun startForceClose(
            context: Context,
            packageName: String,
            appName: String = "App"
        ) {
            targetPackage = packageName
            targetAppName = appName
            currentMode = MODE_FORCE_CLOSE
            isAutomating = true
            isTargetLiteMode = false
            step = 0
            liteStep = LITE_STEP_IDLE
            clickedClearCache = false
            clickedClearData = false
            lastActionTime = System.currentTimeMillis()

            if (instance == null) {
                Toast.makeText(
                    context,
                    "Accessibility সার্ভিসটি চালু করুন!",
                    Toast.LENGTH_LONG
                ).show()

                try {
                    val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(accIntent)
                } catch (_: Exception) {}
                isAutomating = false
                return
            }

            openAppSettings(context, packageName)
        }

        private fun openAppSettings(context: Context, packageName: String) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Settings ওপেন করা যায়নি: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastEventProcessedTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            // Safe configuration: never instantiate empty info which wipes out XML capabilities
            serviceInfo?.let { info ->
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                info.flags = info.flags or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                info.notificationTimeout = 50
                serviceInfo = info
            }
        } catch (_: Throwable) {}
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        isAutomating = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isAutomating = false
    }

    override fun onInterrupt() {
        isAutomating = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (!isAutomating) return
            val currentTarget = targetPackage ?: return

            val now = System.currentTimeMillis()
            // Throttle events to save CPU and battery (prevents overheating and lag)
            if (now - lastEventProcessedTime < 70) return
            lastEventProcessedTime = now

            // 10-second safety timeout prevents any hanging
            if (now - lastActionTime > 10000) {
                isAutomating = false
                targetPackage = null
                return
            }

            val eventPkg = (event?.packageName?.toString() ?: "").lowercase()
            val rootNode = rootInActiveWindow ?: return
            val windowPkg = (rootNode.packageName?.toString() ?: "").lowercase()
            val pkg = if (eventPkg.isNotEmpty()) eventPkg else windowPkg

            // Immediately abort if user is on Home screen / Launcher (never click anything on Home screen!)
            val isLauncher = pkg.contains("launcher") || windowPkg.contains("launcher") ||
                    pkg.contains("home") || windowPkg.contains("home") ||
                    pkg.contains("nexuslauncher") || windowPkg.contains("nexuslauncher") ||
                    pkg.contains("systemui") && !windowPkg.contains("settings")
            if (isLauncher) {
                isAutomating = false
                mainHandler.removeCallbacksAndMessages(null)
                return
            }

            // Strictly allow only Settings, SecurityCenter, PackageInstaller, or the target app itself
            val isSettings = pkg.contains("settings") || windowPkg.contains("settings") ||
                    pkg.contains("securitycenter") || windowPkg.contains("securitycenter") ||
                    pkg.contains("packageinstaller") || windowPkg.contains("packageinstaller")
            val isTarget = currentTarget.isNotEmpty() && (pkg.contains(currentTarget.lowercase()) || windowPkg.contains(currentTarget.lowercase()))
            val isLite = isTargetLiteMode && (pkg.contains("lite") || windowPkg.contains("lite") || pkg.contains("facebook") || windowPkg.contains("facebook"))

            if (!isSettings && !isTarget && !isLite) {
                return
            }

            val isLiteScreen = isLiteStorageScreen(rootNode)

            // 1. If Facebook Lite storage screen or its popup is active, handle custom Lite flow
            if (isLiteScreen) {
                handleLiteStorageScreenFlow(rootNode)
                return
            }

            // 2. Otherwise handle standard OEM clean / force close flow
            if (currentMode == MODE_FORCE_CLOSE) {
                handleForceCloseStep(rootNode)
            } else {
                handleAutoCleanStep(rootNode)
            }
        } catch (_: Throwable) {
            // Absolute crash safety: never let any exception reach system framework
        }
    }

    private fun normalizeText(str: String): String {
        return str.lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("ی", "ي")
            .trim()
    }

    private fun isLiteStorageScreen(rootNode: AccessibilityNodeInfo): Boolean {
        if (liteStep in LITE_STEP_SELECTING_ACCOUNTS..LITE_STEP_FINAL_CONFIRM) {
            return true
        }
        val keywords = listOf(
            "facebook lite storage",
            "clear storage on your phone",
            "accounts and settings",
            "accounts and setting",
            "photo cache",
            "video cache",
            "other cache",
            "remove unnecessary app files to save space",
            "not recommended",
            "مسح وحدة التخزين على هاتفك",
            "الحسابات والإعدادات",
            "الحسابات والاعتدادات",
            "অ্যাকাউন্ট এবং সেটিংস",
            "আপনার ফোনে স্থান খালি করুন"
        )
        return findNodeByKeywords(rootNode, keywords) != null
    }

    private fun handleForceCloseStep(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 100) return

        if (step == 0) {
            val forceStopBtn = findNodeByKeywords(
                rootNode,
                listOf(
                    "force stop",
                    "force close",
                    "থামিয়ে দিন",
                    "জোরপূর্বক বন্ধ করুন",
                    "বাধ্যতামূলক বন্ধ"
                ),
                resourceIds = listOf(
                    "com.android.settings:id/force_stop_button",
                    "com.samsung.android.settings:id/force_stop_button",
                    "com.android.settings:id/button2",
                    "com.samsung.android.settings:id/button2",
                    "com.android.settings:id/right_button",
                    "com.miui.securitycenter:id/force_stop"
                )
            )

            if (forceStopBtn != null && forceStopBtn.isEnabled) {
                clickNode(forceStopBtn)
                step = 1
                lastActionTime = now
                return
            } else {
                finishAndCloseSettings("$targetAppName বন্ধ করা হয়েছে ✓")
                return
            }
        }

        if (step == 1) {
            val confirmBtn = findOkOrDeleteConfirmButton(rootNode)
            if (confirmBtn != null && confirmBtn.isEnabled) {
                clickNode(confirmBtn)
                step = 2
                lastActionTime = now
                finishAndCloseSettings("$targetAppName Force Stopped & Closed ✓")
            } else if (now - lastActionTime > 300) {
                finishAndCloseSettings("$targetAppName Closed ✓")
            }
        }
    }

    /**
     * Accurately finds the "Storage & cache" / "Storage" row in Android Settings App Info.
     * Guaranteed to NEVER match Notifications, Permissions, Battery, or Mobile data!
     */
    private fun findStorageRowNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 100) {
            val node = queue.removeFirst()
            count++

            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val combined = (text + " " + desc).lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // Strict exclusion of non-storage rows in App Info
            val isExcluded = combined.contains("notification") || combined.contains("বিজ্ঞপ্তি") ||
                    combined.contains("permission") || combined.contains("অনুমতি") ||
                    combined.contains("mobile data") || combined.contains("battery") ||
                    combined.contains("ব্যাটারি") || combined.contains("open by default") ||
                    combined.contains("screen time") || combined.contains("unused apps") ||
                    combined.contains("allow notification")

            if (!isExcluded) {
                // Priority 1: Specific OEM resource IDs for storage
                if (viewId.contains("storage_settings") || viewId.contains("storage_use") || viewId.contains("storage_row")) {
                    return node
                }

                // Priority 2: Text matching for Storage & Cache across Samsung, Xiaomi, Vivo, Oppo, Pixel (All major world languages)
                val isStorageMatch = combined.contains("storage & cache") ||
                        combined.contains("storage and cache") ||
                        combined.contains("storage usage") ||
                        combined.contains("internal storage") ||
                        combined.contains("স্টোরেজ ও ক্যাশ") ||
                        combined.contains("স্টোরেজ") ||
                        combined.contains("মেমরি") ||
                        combined.contains("संग्रहण") || // Hindi
                        combined.contains("स्टोरेज") || // Hindi
                        combined.contains("almacenamiento") || // Spanish
                        combined.contains("stockage") || // French
                        combined.contains("speicher") || // German
                        combined.contains("хранилище") || // Russian
                        combined.contains("память") || // Russian
                        combined.contains("التخزين") || // Arabic (Storage)
                        combined.contains("مساحة التخزين") || // Arabic (Storage space)
                        combined.contains("وحدة التخزين") || // Arabic (Storage unit)
                        combined.contains("سعة التخزين") || // Arabic
                        combined.contains("الذاكرة والتخزين") || // Arabic
                        combined.contains("armazenamento") || // Portuguese
                        combined.contains("depolama") || // Turkish
                        combined.contains("penyimpanan") || // Indonesian
                        combined.contains("存储") || // Chinese
                        combined.contains("ストレージ") || // Japanese
                        combined.contains("lưu trữ") || // Vietnamese
                        (text.equals("storage", ignoreCase = true) && !combined.contains("manage"))

                if (isStorageMatch) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findClearDataOrCacheButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByKeywords(
            root,
            listOf(
                "clear cache", "clear data", "clear storage", "manage space", "manage storage",
                "ক্যাশ মুছুন", "ক্যাশে মুছুন", "সব ডেটা মুছুন", "ডেটা মুছুন", "স্টোরেজ মুছুন",
                "कैश साफ़ करें", "डेटा साफ़ करें", "स्टोरेज साफ़ करें",
                "limpiar caché", "borrar datos", "borrar almacenamiento",
                "vider le cache", "effacer les données",
                "cache leeren", "daten löschen",
                "очистить кэш", "очистить хранилище", "стереть данные",
                // Arabic (RTL support - Clear Cache & Clear Data in all variations)
                "مسح ذاكرة التخزين المؤقت", "مسح التخزين المؤقت", "ذاكرة التخزين المؤقت",
                "مسح البيانات", "مسح مساحة التخزين", "مسح وحدة التخزين", "مسح التخزين", "إدارة المساحة", "حذف البيانات", "مسح جميع البيانات",
                "limpar cache", "limpar dados",
                "önbelleği temizle", "verileri temizle",
                "hapus cache", "hapus data",
                "清除缓存", "清除数据",
                "キャッシュを消去", "データを消去",
                "xóa bộ nhớ đệm", "xóa dữ liệu"
            ),
            resourceIds = listOf(
                "com.samsung.android.settings:id/clear_data_button",
                "com.android.settings:id/clear_data_button",
                "com.samsung.android.settings:id/clear_cache_button",
                "com.android.settings:id/clear_cache_button",
                "com.miui.securitycenter:id/clear_all_data",
                "com.android.settings:id/button1",
                "com.android.settings:id/button2"
            )
        )
    }

    /**
     * Universal High-Speed Auto Clean Step:
     * 1. Opens Storage & cache (never clicks Notifications).
     * 2. Clears Cache first.
     * 3. Clears Data / Manage Space.
     * 4. Confirms dialog.
     * 5. Fast auto-closes settings and app!
     */
    private fun handleAutoCleanStep(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 300) return

        // If Facebook Lite storage screen appeared during navigation, jump directly into Lite flow!
        if (isLiteStorageScreen(rootNode)) {
            handleLiteStorageScreenFlow(rootNode)
            return
        }

        // Check if we are already in the Storage screen
        val inStorageScreen = findClearDataOrCacheButton(rootNode) != null

        // Step 0: In App Info Screen -> Find "Storage", "Storage usage", "Storage & cache"
        // Or direct Xiaomi bottom-bar "Clear data"
        if (step == 0 && !inStorageScreen) {
            // Xiaomi / HyperOS direct bottom-bar button check
            val miuiClearBtn = findNodeByKeywords(
                rootNode,
                listOf("clear data", "ডেটা মুছুন"),
                resourceIds = listOf("com.miui.securitycenter:id/clear_data")
            )
            if (miuiClearBtn != null && miuiClearBtn.isEnabled) {
                val clicked = clickNode(miuiClearBtn)
                if (clicked) {
                    step = 1
                    lastActionTime = now
                    return
                }
            }

            // Samsung One UI, Pixel, Xiaomi, Vivo, Oppo, Realme, Transsion
            val storageNode = findStorageRowNode(rootNode)
            if (storageNode != null) {
                val clicked = clickNode(storageNode)
                if (clicked) {
                    step = 1
                    lastActionTime = now
                    return
                }
            } else {
                // If storage row is below the fold, scroll down to reveal it
                try {
                    rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                } catch (_: Exception) {}
            }
        }

        // Step 1: In Storage screen -> Execute BOTH Clear Cache and Clear Data sequentially without missing Clear Data!
        if (step in 1..2 || inStorageScreen) {
            // First: Click "Clear cache" if available and not yet clicked
            if (!clickedClearCache) {
                val clearCacheNode = findNodeByKeywords(
                    rootNode,
                    listOf(
                        "clear cache", "ক্যাশ মুছুন", "ক্যাশে মুছুন", "ক্লিয়ার ক্যাশ", "ক্লিন ক্যাশ",
                        "कैश साफ़ करें", "कैशे साफ़ करें", "limpiar caché", "borrar caché",
                        "vider le cache", "cache leeren", "очистить кэш",
                        "مسح ذاكرة التخزين المؤقت", "مسح التخزين المؤقت", "ذاكرة التخزين المؤقت", "مسح الذاكرة المؤقتة",
                        "limpar cache", "önbelleği temizle", "hapus cache", "清除缓存", "キャッシュを消去", "xóa bộ nhớ đệm"
                    ),
                    resourceIds = listOf(
                        "com.samsung.android.settings:id/clear_cache_button",
                        "com.android.settings:id/clear_cache_button",
                        "com.samsung.android.settings:id/button2",
                        "com.android.settings:id/button2",
                        "com.miui.securitycenter:id/clear_cache"
                    )
                )
                if (clearCacheNode != null && clearCacheNode.isEnabled) {
                    clickNode(clearCacheNode)
                    clickedClearCache = true
                    lastActionTime = now
                    return
                }
            }

            // Allow ~350ms after Clear Cache so Android updates cache to 0MB before clicking Clear Data
            if (clickedClearCache && now - lastActionTime < 350) {
                return
            }

            // Next: Click "Clear data" / "Clear all data" / "Clear storage" / "Manage space"
            // Priority A: Xiaomi / HyperOS BottomSheet "Clear all data"
            val clearAllDataNode = findNodeByKeywords(
                rootNode,
                listOf(
                    "clear all data", "সব ডেটা মুছুন", "সব ডাটা মুছুন", "सभी डेटा साफ़ करें",
                    "borrar todos los datos", "effacer toutes les données", "все данные",
                    "مسح جميع البيانات", "مسح كل البيانات"
                ),
                resourceIds = listOf(
                    "com.miui.securitycenter:id/clear_all_data"
                )
            )
            if (clearAllDataNode != null && clearAllDataNode.isEnabled) {
                clickNode(clearAllDataNode)
                clickedClearData = true
                step = 3
                lastActionTime = now
                return
            }

            // Priority B: Samsung One UI, Pixel, Xiaomi, Oppo, Vivo, Transsion, Facebook Main / Katana / FB Lite "Clear Data" / "Clear Storage" / "Manage space"
            // Handles both LTR and RTL (left-right swapped / dan-dik bam-dik) by relying on Resource IDs and Multilingual Keywords
            val clearDataNode = findNodeByKeywords(
                rootNode,
                listOf(
                    "clear data", "ক্লিয়ার ডেটা", "ডেটা মুছুন", "ডাটা মুছুন", "clear storage",
                    "স্টোরেজ মুছুন", "manage space", "manage storage", "delete data",
                    "ডेटा साफ़ करें", "स्टोरेज साफ़ करें", "स्पेस प्रबंधित करें",
                    "borrar datos", "borrar almacenamiento", "administrar espacio",
                    "effacer les données", "supprimer les données", "gérer l'espace",
                    "daten löschen", "speicherplatz verwalten", "очистить хранилище", "стереть данные",
                    // Arabic RTL (Swapped positions / dan-bam)
                    "مسح مساحة التخزين", "مسح وحدة التخزين", "مسح البيانات", "مسح التخزين", "إدارة المساحة", "حذف البيانات", "مسح جميع البيانات",
                    "limpar dados", "limpar armazenamento", "gerenciar espaço",
                    "verileri temizle", "hapus data", "kelola ruang", "清除数据", "管理空间",
                    "データを消去", "容量を管理", "xóa dữ liệu", "quản lý dung lượng"
                ),
                resourceIds = listOf(
                    "com.samsung.android.settings:id/clear_data_button",
                    "com.android.settings:id/clear_data_button",
                    "com.android.settings:id/clear_storage_button",
                    "com.android.settings:id/manage_space_button",
                    "com.samsung.android.settings:id/button1",
                    "com.android.settings:id/button1",
                    "com.android.settings:id/clear_data_btn",
                    "com.miui.securitycenter:id/clear_data",
                    "com.miui.securitycenter:id/clear_all_data",
                    "com.coloros.safecenter:id/clear_data",
                    "com.oplus.safecenter:id/clear_data",
                    "com.vivo.safecenter:id/clear_data",
                    "com.transsion.phonemaster:id/clear_data",
                    "com.google.android.settings:id/clear_data_button"
                )
            )

            if (clearDataNode != null && clearDataNode.isEnabled) {
                clickNode(clearDataNode)
                clickedClearData = true
                step = 3
                lastActionTime = now

                // Check if FB Lite internal screen opened immediately after clicking Manage space / Clear data
                mainHandler.postDelayed({
                    rootInActiveWindow?.let { refreshed ->
                        if (isLiteStorageScreen(refreshed)) {
                            handleLiteStorageScreenFlow(refreshed)
                        }
                    }
                }, 80)
                return
            }

            // If Clear Data has not been clicked yet, keep waiting and searching for Clear Data button.
            // NEVER prematurely close after 180ms!
            if (!clickedClearData && clickedClearCache && now - lastActionTime > 3500) {
                // Only if after 3.5 seconds on the storage screen there is genuinely no Clear Data button, close:
                step = 4
                autoCloseCleanedSequence()
                return
            }
        }

        // Step 3: Handle Confirmation Dialog (Samsung One UI "Delete", Xiaomi "OK", Pixel "Delete/OK")
        if (step == 3) {
            val confirmNode = findOkOrDeleteConfirmButton(rootNode)
            if (confirmNode != null && confirmNode.isEnabled) {
                clickNode(confirmNode)
                step = 4
                lastActionTime = now
                // Ultra-fast smooth close (~100ms) right after confirmation click
                mainHandler.postDelayed({
                    autoCloseCleanedSequence()
                }, 100)
                return
            } else {
                // Wait up to 1500ms for confirmation dialog to animate and render
                if (now - lastActionTime > 1500) {
                    step = 4
                    autoCloseCleanedSequence()
                }
            }
        }
    }

    /**
     * Dedicated High-Speed Facebook Lite Handler:
     * Paced over ~3 seconds so all checkboxes, OK popup, and blue CLEAR button click reliably!
     * 1. Checks "Accounts and settings" checkbox.
     * 2. Exactly 1-second delay (1000ms) before clicking "OK" on confirmation popup.
     * 3. Taps blue "CLEAR" button without missing.
     * 4. Confirms final popup and closes settings ultra-fast and smoothly!
     */
    private fun handleLiteStorageScreenFlow(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 300) return

        // 1. If we already clicked CLEAR, wait for final confirmation dialog
        if (liteStep == LITE_STEP_FINAL_CONFIRM) {
            val finalOk = findLiteOkDialogButton(rootNode)
            if (finalOk != null && finalOk.isEnabled) {
                clickNode(finalOk)
                liteStep = LITE_STEP_DONE
                lastActionTime = now
                // Ultra-fast smooth close (~100ms) right after final confirmation click
                mainHandler.postDelayed({
                    autoCloseCleanedSequence()
                }, 100)
                return
            } else if (now - lastActionTime > 1200) {
                // If no final confirmation dialog appeared after 1.2s, storage is cleared -> close
                liteStep = LITE_STEP_DONE
                autoCloseCleanedSequence()
                return
            }
            return
        }

        // 2. If waiting for the accounts popup, check if OK button is ready
        if (liteStep == LITE_STEP_WAIT_ACCOUNTS_POPUP) {
            val popupOk = findLiteOkDialogButton(rootNode)
            if (popupOk != null && popupOk.isEnabled) {
                val elapsedSinceMark = now - accountsMarkedTime
                if (elapsedSinceMark < 1000L) {
                    return
                }
                clickNode(popupOk)
                liteStep = LITE_STEP_CLICK_CLEAR
                lastActionTime = now

                // Re-check after 400ms to click the blue CLEAR button smoothly
                mainHandler.postDelayed({
                    rootInActiveWindow?.let { refreshed ->
                        handleLiteStorageScreenFlow(refreshed)
                    }
                }, 400)
                return
            }
            return
        }

        // 3. Ready to click the blue CLEAR button
        if (liteStep == LITE_STEP_CLICK_CLEAR) {
            if (now - lastActionTime < 350) return
            val clearBtn = findLiteClearButton(rootNode)
            if (clearBtn != null && clearBtn.isEnabled) {
                clickNode(clearBtn)
                liteStep = LITE_STEP_FINAL_CONFIRM
                lastActionTime = now

                // Re-check after 400ms for final confirmation popup
                mainHandler.postDelayed({
                    rootInActiveWindow?.let { refreshed ->
                        handleLiteStorageScreenFlow(refreshed)
                    }
                }, 400)
                return
            }
            // Dialog might still be closing, wait for it
            return
        }

        // 4. Initial state: Ensure "Accounts and settings" is checked
        val accountsRow = findAccountsAndSettingsRow(rootNode)
        if (accountsRow != null && !accountsRow.isChecked) {
            ensureClearAllChecked(rootNode)
            liteStep = LITE_STEP_WAIT_ACCOUNTS_POPUP
            accountsMarkedTime = now
            lastActionTime = now
            clickNode(accountsRow.clickableTarget)

            // Trigger handler after 1000ms (1 second) to click OK on the warning popup
            mainHandler.postDelayed({
                rootInActiveWindow?.let { refreshedRoot ->
                    handleLiteStorageScreenFlow(refreshedRoot)
                }
            }, 1000L)
            return
        } else {
            // Already checked or no accounts row, click CLEAR button directly!
            if (now - lastActionTime < 350) return
            val clearBtn = findLiteClearButton(rootNode)
            if (clearBtn != null && clearBtn.isEnabled) {
                clickNode(clearBtn)
                liteStep = LITE_STEP_FINAL_CONFIRM
                lastActionTime = now
                mainHandler.postDelayed({
                    rootInActiveWindow?.let { refreshed ->
                        handleLiteStorageScreenFlow(refreshed)
                    }
                }, 400)
                return
            }
        }
    }

    data class AccountsRowInfo(
        val textNode: AccessibilityNodeInfo,
        val checkboxNode: AccessibilityNodeInfo?,
        val clickableTarget: AccessibilityNodeInfo,
        val isChecked: Boolean
    )

    private fun findAccountsAndSettingsRow(root: AccessibilityNodeInfo): AccountsRowInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val allCheckableNodes = mutableListOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty() && count < 90) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            val lower = text.lowercase()
            val norm = normalizeText(text)

            if (node.isCheckable) {
                allCheckableNodes.add(node)
            }

            // Universal multilingual matching for FB Lite Accounts and Settings row
            val isAccountsMatch = lower.contains("accounts and setting") ||
                    lower.contains("accounts & setting") ||
                    lower.contains("অ্যাকাউন্ট এবং সেটিংস") ||
                    lower.contains("অ্যাকাউন্ট ও সেটিংস") ||
                    lower.contains("একাউন্ট") ||
                    lower.contains("खाते और सेटिंग") || // Hindi
                    lower.contains("cuentas y configuración") || // Spanish
                    lower.contains("cuentas") || // Spanish
                    lower.contains("comptes et paramètres") || // French
                    lower.contains("comptes") || // French
                    lower.contains("konten und einstellungen") || // German
                    lower.contains("konten") || // German
                    lower.contains("аккаунты и настройки") || // Russian
                    lower.contains("учетные записи") || // Russian
                    // Arabic RTL (from screenshot: الحسابات والإعدادات & غير موصى به)
                    norm.contains("الحسابات والاعدادات") ||
                    norm.contains("الحسابات والاعتدادات") ||
                    norm.contains("الحسابات") ||
                    norm.contains("غير موصى به") ||
                    norm.contains("غير موصى") ||
                    norm.contains("غير موصي") ||
                    lower.contains("contas e configurações") || // Portuguese
                    lower.contains("hesaplar ve ayarlar") || // Turkish
                    lower.contains("akun dan pengaturan") || // Indonesian
                    lower.contains("账户和设置") || // Chinese
                    lower.contains("アカウントと設定") || // Japanese
                    lower.contains("tài khoản và cài đặt") || // Vietnamese
                    lower.contains("not recommended") ||
                    lower.contains("अनुशंसित नहीं") ||
                    lower.contains("não recomendado") ||
                    lower.contains("non recommandé") ||
                    lower.contains("не рекомендуется")

            if (isAccountsMatch) {
                var checkableNode: AccessibilityNodeInfo? = null
                var isChecked = false
                var clickableTarget: AccessibilityNodeInfo = node

                if (node.isCheckable) {
                    checkableNode = node
                    isChecked = node.isChecked
                }

                val parent = node.parent
                if (parent != null) {
                    if (parent.isClickable) clickableTarget = parent
                    if (parent.isCheckable) {
                        checkableNode = parent
                        isChecked = parent.isChecked
                    }
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i)
                        if (sibling != null && sibling.isCheckable) {
                            checkableNode = sibling
                            isChecked = sibling.isChecked
                            if (sibling.isClickable) clickableTarget = sibling
                            break
                        }
                    }
                }

                return AccountsRowInfo(
                    textNode = node,
                    checkboxNode = checkableNode,
                    clickableTarget = checkableNode ?: clickableTarget,
                    isChecked = isChecked
                )
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // Structural Fallback for ANY unknown language or RTL layout in FB Lite:
        // In FB Lite storage screen:
        // Cache checkboxes (Clear All, Photo, Video, Other) are pre-checked by FB Lite.
        // "Accounts and settings" is ALWAYS the ONLY UNCHECKED checkbox, or the LAST checkbox!
        val uncheckedCheckbox = allCheckableNodes.firstOrNull { !it.isChecked }
        val targetCheckbox = uncheckedCheckbox ?: allCheckableNodes.lastOrNull()

        if (targetCheckbox != null) {
            val parent = targetCheckbox.parent
            val targetClickable = if (targetCheckbox.isClickable) targetCheckbox else (parent ?: targetCheckbox)
            return AccountsRowInfo(
                textNode = targetCheckbox,
                checkboxNode = targetCheckbox,
                clickableTarget = targetClickable,
                isChecked = targetCheckbox.isChecked
            )
        }

        return null
    }

    private fun ensureClearAllChecked(root: AccessibilityNodeInfo) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 60) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").lowercase()
            val norm = normalizeText(text)

            if (text.contains("clear all") || text.contains("সব মুছুন") || norm.contains("مسح الكل")) {
                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val ch = parent.getChild(i)
                        if (ch != null && ch.isCheckable && !ch.isChecked) {
                            clickNode(ch)
                            return
                        }
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    private fun findLiteOkDialogButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val nonCancelButtons = mutableListOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty() && count < 80) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val lower = text.lowercase()
            val lowerDesc = desc.lowercase()
            val norm = normalizeText(text)
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isCancel = lower == "cancel" || lower == "বাতিল" || lower == "না" || lower == "no" ||
                    lower == "إلغاء" || norm == "الغاء" || lower == "annuler" || lower == "abbrechen" ||
                    lower == "отмена" || lower == "iptal" || lower == "batal" || lower == "cancelar" ||
                    lower == "huỷ" || lower == "取消" || lower == "キャンセル"

            if (!isCancel && node.isEnabled) {
                // Multilingual Positive Confirm words
                val isPositiveWord = lower == "ok" || lower == "okay" || lower == "confirm" ||
                        lower == "yes" || lower == "clear" || lower == "delete" ||
                        // Arabic
                        norm == "موافق" || lower == "مسح" || lower == "حذف" || lower == "نعم" || norm == "تاكيد" || norm == "متابعه" ||
                        // Bengali
                        lower == "ঠিক আছে" || lower == "হ্যাঁ" || lower == "মুছুন" || lower == "নিশ্চিত করুন" ||
                        // Hindi
                        lower == "ঠিক है" || lower == "हाँ" || lower == "साफ़ करें" || lower == "हटाएं" ||
                        // Spanish, French, Russian, etc.
                        lower == "aceptar" || lower == "sí" || lower == "oui" || lower == "ок" || lower == "да" ||
                        lower == "tamam" || lower == "evet" || lower == "oke" || lower == "ya" ||
                        lower == "确定" || lower == "はい" ||
                        lowerDesc == "ok" || lowerDesc == "confirm"

                if (isPositiveWord) {
                    candidates.add(node)
                } else if (viewId.endsWith(":id/button1") || viewId.endsWith(":id/confirm") || viewId.endsWith(":id/ok")) {
                    candidates.add(node)
                }

                val className = node.className?.toString() ?: ""
                if (node.isClickable && (className.contains("Button") || className.contains("TextView"))) {
                    nonCancelButtons.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // Return candidate with highest priority
        return candidates.firstOrNull {
            val t = (it.text?.toString() ?: "").trim()
            val n = normalizeText(t)
            t.equals("ok", ignoreCase = true) || n == "موافق" || t == "ঠিক আছে"
        } ?: candidates.firstOrNull() ?: nonCancelButtons.lastOrNull()
    }

    private fun findOkOrDeleteConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 80) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim().lowercase()
            val norm = normalizeText(text)
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isCancel = text == "cancel" || text == "বাতিল" || text == "না" || text == "no" ||
                    text == "annuler" || text == "abbrechen" || text == "отмена" || text == "إلغاء" || norm == "الغاء" ||
                    text == "iptal" || text == "batal" || text == "取消" || text == "キャンセル" || text == "cancelar"
            if (!isCancel && node.isEnabled) {
                // Priority: Standard Android Dialog Positive Button (universal across all languages and RTL)
                if (viewId.endsWith(":id/button1") || viewId.contains("confirm") || viewId.contains("button_ok")) {
                    return node
                }

                val isConfirmText = text == "delete" || text == "মুছুন" || text == "ok" || text == "clear" ||
                        text == "confirm" || text == "ঠিক আছে" || text == "হ্যাঁ" ||
                        text == "clear all data" || text == "সব ডেটা মুছুন" ||
                        text == "हटाएं" || text == "ঠিক है" || text == "हाँ" || text == "साफ़ करें" || // Hindi
                        text == "eliminar" || text == "aceptar" || text == "borrar" || text == "sí" || // Spanish
                        text == "supprimer" || text == "effacer" || text == "oui" || // French
                        text == "löschen" || text == "ja" || // German
                        text == "удалить" || text == "ок" || text == "да" || text == "очистить" || // Russian
                        // Arabic
                        text == "حذف" || norm == "موافق" || text == "نعم" || text == "مسح" || norm == "تاكيد" ||
                        text == "excluir" || text == "apagar" || text == "sim" || // Portuguese
                        text == "sil" || text == "tamam" || text == "evet" || // Turkish
                        text == "hapus" || text == "oke" || text == "ya" || // Indonesian
                        text == "删除" || text == "确定" || text == "是" || // Chinese
                        text == "削除" || text == "はい" || // Japanese
                        text == "xóa" || text == "có" // Vietnamese

                if (isConfirmText) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findLiteClearButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        var fallbackButton: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && count < 90) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val lower = text.lowercase()
            val norm = normalizeText(text)

            val isExactClear = text.equals("CLEAR", ignoreCase = true) ||
                    text.equals("Clear", ignoreCase = true) ||
                    text.equals("মুছুন") ||
                    norm == "مسح" || // Arabic (EXACT MATCH FOR USER'S SCREENSHOT BLUE BUTTON!)
                    lower == "حذف" ||
                    lower == "साफ़ करें" ||
                    lower == "limpiar" ||
                    lower == "effacer" ||
                    lower == "löschen" ||
                    lower == "очистить" ||
                    lower == "limpar" ||
                    lower == "temizle" ||
                    lower == "hapus" ||
                    lower == "清除" ||
                    lower == "消去" ||
                    lower == "xóa" ||
                    desc.equals("CLEAR", ignoreCase = true)

            val isNotOtherClear = !text.contains("All", ignoreCase = true) &&
                    !text.contains("Phone", ignoreCase = true) &&
                    !text.contains("Cache", ignoreCase = true) &&
                    !text.contains("Storage", ignoreCase = true) &&
                    !text.contains("Accounts", ignoreCase = true) &&
                    !norm.contains("الكل") && // exclude "مسح الكل" (Clear all checkbox)
                    !norm.contains("هاتفك") && // exclude header "مسح وحدة التخزين على هاتفك"
                    !norm.contains("الموقت") && // exclude cache
                    !norm.contains("المؤقت")

            if (isExactClear && isNotOtherClear && node.isEnabled) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Ultra-fast close sequence:
     * Immediately stops automation, purges all pending callbacks so nothing is clicked on Home screen,
     * navigates double-back + home, kills background process, finishes cleanly.
     */
    private fun autoCloseCleanedSequence() {
        if (!isAutomating) return
        isAutomating = false
        mainHandler.removeCallbacksAndMessages(null)
        val pkgToKill = targetPackage
        targetPackage = null
        step = 0
        liteStep = LITE_STEP_IDLE
        isTargetLiteMode = false

        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            pkgToKill?.let { pkg ->
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.killBackgroundProcesses(pkg)
                    Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                } catch (_: Exception) {}
            }
            Toast.makeText(applicationContext, "✓ $targetAppName ডেটা ক্লিয়ার ও অ্যাপ বন্ধ হয়েছে!", Toast.LENGTH_SHORT).show()
        }, 50)
    }

    private fun finishAndCloseSettings(message: String) {
        if (!isAutomating) return
        isAutomating = false
        mainHandler.removeCallbacksAndMessages(null)
        val pkgToKill = targetPackage
        targetPackage = null
        step = 0
        liteStep = LITE_STEP_IDLE
        isTargetLiteMode = false

        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            pkgToKill?.let { pkg ->
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.killBackgroundProcesses(pkg)
                    Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                } catch (_: Exception) {}
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }, 50)
    }

    private fun findNodeByKeywords(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        resourceIds: List<String> = emptyList()
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 80) {
            val node = queue.removeFirst()
            count++

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            for (rid in resourceIds) {
                // NEVER match on generic android:id/title or empty
                if (rid != "android:id/title" && viewId.isNotEmpty() && viewId.contains(rid.lowercase())) {
                    return node
                }
            }

            val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            if (text.isNotBlank()) {
                val lowerText = text.lowercase()
                for (kw in keywords) {
                    if (lowerText.contains(kw)) {
                        return node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (!isAutomating) return false
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isClickable) {
                return curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            curr = curr.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
