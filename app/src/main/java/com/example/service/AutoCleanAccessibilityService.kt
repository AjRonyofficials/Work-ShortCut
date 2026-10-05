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

            instance?.startActiveRunner()
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

            instance?.startActiveRunner()
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

    private val activeTickRunnable = object : Runnable {
        override fun run() {
            if (!isAutomating) return
            try {
                rootInActiveWindow?.let { root ->
                    processAutomation(root)
                }
            } catch (_: Throwable) {}
            if (isAutomating) {
                mainHandler.postDelayed(this, 120)
            }
        }
    }

    private fun startActiveRunner() {
        mainHandler.removeCallbacks(activeTickRunnable)
        mainHandler.postDelayed(activeTickRunnable, 200)
    }

    private fun stopActiveRunner() {
        mainHandler.removeCallbacks(activeTickRunnable)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        isAutomating = false
        stopActiveRunner()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isAutomating = false
        stopActiveRunner()
    }

    override fun onInterrupt() {
        isAutomating = false
        stopActiveRunner()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (!isAutomating) return
            val currentTarget = targetPackage ?: return

            val now = System.currentTimeMillis()
            if (now - lastEventProcessedTime < 35) return
            lastEventProcessedTime = now

            val pkg = (event?.packageName?.toString() ?: "").lowercase()
            val isTargetPkg = currentTarget.isNotEmpty() && pkg.contains(currentTarget.lowercase())
            // Universal matching for all Android 10-16+ brands: Samsung, Xiaomi, Oppo, Realme, OnePlus, Vivo, iQOO, Transsion, Pixel, Moto, Huawei
            val isKnownTarget = pkg.contains("lite") || pkg.contains("facebook") || pkg.contains("katana") ||
                    pkg.contains("settings") || pkg.contains("samsung") || pkg.contains("miui") ||
                    pkg.contains("securitycenter") || pkg.contains("packageinstaller") ||
                    pkg.contains("systemui") || pkg.contains("coloros") || pkg.contains("oplus") ||
                    pkg.contains("vivo") || pkg.contains("iqoo") || pkg.contains("transsion") ||
                    pkg.contains("phonemaster") || pkg.contains("huawei") || pkg.contains("android") ||
                    pkg.isEmpty()

            if (!isTargetPkg && !isKnownTarget && !isTargetLiteMode) {
                return
            }

            val rootNode = rootInActiveWindow ?: return
            processAutomation(rootNode)
        } catch (_: Throwable) {
            // Absolute crash safety: never let any exception reach system framework
        }
    }

    private fun processAutomation(rootNode: AccessibilityNodeInfo) {
        if (!isAutomating) return
        val currentTarget = targetPackage ?: return

        val now = System.currentTimeMillis()
        if (now - lastActionTime > 12000) {
            isAutomating = false
            targetPackage = null
            stopActiveRunner()
            return
        }

        val windowPkg = (rootNode.packageName?.toString() ?: "").lowercase()
        // If user manually switched away to Home launcher after 3 seconds, stop safely
        val isLauncher = (windowPkg.contains("launcher") || windowPkg.contains("nexuslauncher") ||
                (windowPkg.contains("home") && !windowPkg.contains("settings"))) &&
                !windowPkg.contains("settings") && !windowPkg.contains("systemui") &&
                !windowPkg.contains("facebook") && !windowPkg.contains("lite") &&
                now - lastActionTime > 3000

        if (isLauncher) {
            isAutomating = false
            stopActiveRunner()
            mainHandler.removeCallbacksAndMessages(null)
            return
        }

        val isLiteScreen = isLiteStorageScreen(rootNode)

        // 1. If Facebook Lite storage screen or its popup is active, handle custom Lite flow
        if (isLiteScreen || liteStep in LITE_STEP_SELECTING_ACCOUNTS..LITE_STEP_FINAL_CONFIRM) {
            handleLiteStorageScreenFlow(rootNode)
            return
        }

        // 2. Otherwise handle standard OEM clean / force close flow
        if (currentMode == MODE_FORCE_CLOSE) {
            handleForceCloseStep(rootNode)
        } else {
            handleAutoCleanStep(rootNode)
        }
    }

    private fun isLiteStorageScreen(rootNode: AccessibilityNodeInfo): Boolean {
        if (liteStep in LITE_STEP_SELECTING_ACCOUNTS..LITE_STEP_FINAL_CONFIRM) {
            return true
        }
        val keywords = listOf(
            "facebook lite storage",
            "clear storage on your phone",
            "libera espacio en el teléfono",
            "accounts and settings",
            "accounts and setting",
            "cuentas y configuración",
            "cuentas y configuracion",
            "photo cache",
            "video cache",
            "other cache",
            "remove unnecessary app files to save space",
            "not recommended",
            "no recomendado",
            "অ্যাকাউন্ট এবং সেটিংস",
            "مسح وحدة التخزين على هاتفك",
            "الحسابات والإعدادات",
            "غير موصى به"
        )
        return findNodeByKeywords(rootNode, keywords) != null || findLiteClearButton(rootNode) != null
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
                // Priority 1: Specific OEM resource IDs for storage (Samsung, Xiaomi, Oppo/Realme, Vivo, Transsion, Pixel)
                if (viewId.contains("storage") || viewId.contains("memory") ||
                    viewId.contains("storage_settings") || viewId.contains("storage_use") || viewId.contains("storage_row")) {
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
                        combined.contains("التخزين") || // Arabic
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

    private fun scrollDownToFind(root: AccessibilityNodeInfo) {
        try {
            if (root.isScrollable) {
                root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return
            }
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < 35) {
                val node = queue.removeFirst()
                count++
                if (node.isScrollable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    return
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun findClearDataOrCacheButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeByKeywords(
            root,
            listOf(
                "clear cache", "clear data", "clear storage", "manage space", "manage storage",
                "ক্যাশ মুছুন", "ক্যাশে মুছুন", "সব ডেটা মুছুন", "ডেটা মুছুন", "স্টোরেজ মুছুন",
                "कैश साफ़ करें", "डेटा साफ़ करें", "स्टोरेज साफ़ करें",
                "limpiar caché", "borrar datos", "borrar caché", "borrar almacenamiento",
                "vider le cache", "effacer les données",
                "cache leeren", "daten löschen",
                "очистить кэш", "очистить хранилище", "стереть данные",
                "مسح ذاكرة التخزين المؤقت", "مسح البيانات",
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
        if (now - lastActionTime < 70) return

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
                scrollDownToFind(rootNode)
            }
        }

        // Step 1: In Storage screen -> Execute BOTH Clear Cache and Clear Data sequentially!
        if (step in 1..2 || inStorageScreen) {
            // First: Click "Clear cache" if available and not yet clicked
            if (!clickedClearCache) {
                val clearCacheNode = findNodeByKeywords(
                    rootNode,
                    listOf(
                        "clear cache", "ক্যাশ মুছুন", "ক্যাশে মুছুন", "ক্লিয়ার ক্যাশ", "ক্লিন ক্যাশ",
                        "कैश साफ़ करें", "कैशे साफ़ करें", "limpiar caché", "borrar caché",
                        "vider le cache", "cache leeren", "очистить кэш", "مسح ذاكرة التخزين المؤقت",
                        "limpar cache", "önbelleği temizle", "hapus cache", "清除缓存", "キャッシュを消去", "xóa bộ nhớ đệm"
                    ),
                    resourceIds = listOf(
                        "com.samsung.android.settings:id/clear_cache_button",
                        "com.android.settings:id/clear_cache_button",
                        "com.samsung.android.settings:id/button2",
                        "com.android.settings:id/button2",
                        "com.miui.securitycenter:id/clear_cache",
                        "com.coloros.safecenter:id/clear_cache",
                        "com.oplus.safecenter:id/clear_cache",
                        "com.vivo.safecenter:id/clear_cache",
                        "com.iqoo.secure:id/clear_cache",
                        "com.transsion.phonemaster:id/clear_cache",
                        "com.google.android.settings:id/clear_cache_button",
                        "com.android.settings:id/clear_cache_btn"
                    )
                )
                if (clearCacheNode != null && clearCacheNode.isEnabled) {
                    clickNode(clearCacheNode)
                    clickedClearCache = true
                    lastActionTime = now
                }
            }

            // Next: Click "Clear data" / "Clear all data" / "Clear storage" / "Manage space"
            // Priority A: Xiaomi / HyperOS BottomSheet "Clear all data"
            val clearAllDataNode = findNodeByKeywords(
                rootNode,
                listOf(
                    "clear all data", "সব ডেটা মুছুন", "সব ডাটা মুছুন", "सभी डेटा साफ़ करें",
                    "borrar todos los datos", "effacer toutes les données", "все данные"
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

            // Priority B: Samsung One UI, Pixel, Facebook Main / Katana / FB Lite "Clear Data" / "Clear Storage" / "Manage space"
            val clearDataNode = findNodeByKeywords(
                rootNode,
                listOf(
                    "clear data", "ক্লিয়ার ডেটা", "ডেটা মুছুন", "ডাটা মুছুন", "clear storage",
                    "স্টোরেজ মুছুন", "manage space", "manage storage", "delete data",
                    "ডेटा साफ़ करें", "स्टोरेज साफ़ करें", "स्पेस प्रबंधित करें",
                    "borrar datos", "borrar almacenamiento", "administrar espacio",
                    "effacer les données", "supprimer les données", "gérer l'espace",
                    "daten löschen", "speicherplatz verwalten", "очистить хранилище", "стереть данные",
                    "مسح البيانات", "إدارة المساحة", "limpar dados", "limpar armazenamento", "gerenciar espaço",
                    "verileri temizle", "hapus data", "kelola ruang", "清除数据", "管理空间",
                    "データを消去", "容量を管理", "xóa dữ liệu", "quản lý dung lượng"
                ),
                resourceIds = listOf(
                    "com.samsung.android.settings:id/clear_data_button",
                    "com.android.settings:id/clear_data_button",
                    "com.samsung.android.settings:id/button1",
                    "com.android.settings:id/button1",
                    "com.android.settings:id/clear_data_btn",
                    "com.miui.securitycenter:id/clear_data",
                    "com.coloros.safecenter:id/clear_data",
                    "com.oplus.safecenter:id/clear_data",
                    "com.vivo.safecenter:id/clear_data",
                    "com.iqoo.secure:id/clear_data",
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

            // If Clear Data has not been clicked yet, allow up to 2500ms on storage screen before closing
            if (!clickedClearData && clickedClearCache && now - lastActionTime > 2500) {
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
                autoCloseCleanedSequence()
                return
            } else {
                if (now - lastActionTime > 2500) {
                    step = 4
                    autoCloseCleanedSequence()
                }
            }
        }
    }

    /**
     * Dedicated High-Speed Facebook Lite Handler:
     * 1. Checks "Accounts and settings" checkbox.
     * 2. Exactly 1-second delay (1000ms) before clicking "OK" on confirmation popup,
     *    ensuring the checkbox mark is fully registered by OS and dialog is stable.
     * 3. Taps "CLEAR" button without missing.
     * 4. Confirms final popup and closes settings and app smoothly in milliseconds!
     */
    private fun handleLiteStorageScreenFlow(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 70) return

        // 1. Positive dialog button check (OK / Confirm)
        val okDialogBtn = findLiteOkDialogButton(rootNode)
        if (okDialogBtn != null && okDialogBtn.isEnabled) {
            // If waiting for the accounts popup, enforce the requested 1-second delay
            if (liteStep == LITE_STEP_WAIT_ACCOUNTS_POPUP) {
                val elapsedSinceMark = now - accountsMarkedTime
                if (elapsedSinceMark < 1000L) {
                    // Do not click yet, allow full 1-second stabilization
                    return
                }
            }

            clickNode(okDialogBtn)
            lastActionTime = now

            if (liteStep == LITE_STEP_FINAL_CONFIRM) {
                liteStep = LITE_STEP_DONE
                autoCloseCleanedSequence()
            } else {
                liteStep = LITE_STEP_CLICK_CLEAR
                // Instant follow-up check for CLEAR button without sluggish wait
                mainHandler.postDelayed({
                    rootInActiveWindow?.let { refreshedRoot ->
                        clickClearButtonAndFinish(refreshedRoot)
                    }
                }, 100)
            }
            return
        }

        // 2. Ensure "Accounts and settings" is checked
        val accountsRow = findAccountsAndSettingsRow(rootNode)
        if (accountsRow != null && !accountsRow.isChecked && liteStep < LITE_STEP_CLICK_CLEAR) {
            ensureClearAllChecked(rootNode)
            liteStep = LITE_STEP_WAIT_ACCOUNTS_POPUP
            accountsMarkedTime = now
            lastActionTime = now
            clickNode(accountsRow.clickableTarget)

            // Exactly 1 second (1000ms) delay so user & OS see the mark cleanly before clicking OK popup
            mainHandler.postDelayed({
                rootInActiveWindow?.let { refreshedRoot ->
                    val popupOk = findLiteOkDialogButton(refreshedRoot)
                    if (popupOk != null && popupOk.isEnabled) {
                        clickNode(popupOk)
                        liteStep = LITE_STEP_CLICK_CLEAR
                        lastActionTime = System.currentTimeMillis()
                        // Follow up immediately to click CLEAR
                        mainHandler.postDelayed({
                            rootInActiveWindow?.let { rootAfterOk ->
                                clickClearButtonAndFinish(rootAfterOk)
                            }
                        }, 100)
                    } else {
                        clickClearButtonAndFinish(refreshedRoot)
                    }
                }
            }, 1000L)
            return
        }

        // 3. Accounts and settings is checked -> Click CLEAR
        clickClearButtonAndFinish(rootNode)
    }

    private fun clickClearButtonAndFinish(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        val clearBtn = findLiteClearButton(rootNode)

        if (clearBtn != null && clearBtn.isEnabled) {
            clickNode(clearBtn)
            liteStep = LITE_STEP_FINAL_CONFIRM
            lastActionTime = now

            mainHandler.postDelayed({
                rootInActiveWindow?.let { refreshedRoot ->
                    val finalOk = findLiteOkDialogButton(refreshedRoot)
                    if (finalOk != null && finalOk.isEnabled) {
                        clickNode(finalOk)
                    }
                }
                autoCloseCleanedSequence()
            }, 160)
        } else {
            val finalOk = findLiteOkDialogButton(rootNode)
            if (finalOk != null && finalOk.isEnabled) {
                clickNode(finalOk)
                autoCloseCleanedSequence()
            } else if (now - lastActionTime > 300) {
                autoCloseCleanedSequence()
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
                    lower.contains("الحسابات والإعدادات") || // Arabic
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

        // Structural Fallback for FB Lite across all languages:
        // Cache items (Clear all, Photo, Video, Other) are pre-checked; "Accounts and settings" is the ONLY unchecked checkbox on screen!
        val uncheckedCheckbox = allCheckableNodes.lastOrNull { !it.isChecked } ?: allCheckableNodes.lastOrNull()
        if (uncheckedCheckbox != null) {
            val parent = uncheckedCheckbox.parent
            val targetClickable = if (uncheckedCheckbox.isClickable) uncheckedCheckbox else (parent ?: uncheckedCheckbox)
            return AccountsRowInfo(
                textNode = uncheckedCheckbox,
                checkboxNode = uncheckedCheckbox,
                clickableTarget = targetClickable,
                isChecked = uncheckedCheckbox.isChecked
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

            if (text.contains("clear all") || text.contains("সব মুছুন")) {
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
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isCancel = lower == "cancel" || lower == "বাতিল" || lower == "না" || lower == "no" ||
                    lower == "cancelar" || lower == "annuler" || lower == "abbrechen" || lower == "إلغاء" ||
                    lower == "отмена" || lower == "iptal" || lower == "batal" || lower == "取消"
            if (!isCancel && node.isEnabled) {
                if (lower == "ok" || lower == "okay" || lower == "confirm" ||
                    lower == "ঠিক আছে" || lower == "yes" || lower == "হ্যাঁ" ||
                    lower == "aceptar" || lower == "sí" || lower == "موافق" || lower == "tamam" ||
                    lowerDesc == "ok" || lowerDesc == "confirm" || lowerDesc == "aceptar") {
                    candidates.add(node)
                } else if (viewId.endsWith(":id/button1") || viewId.endsWith(":id/confirm") || viewId.endsWith(":id/ok")) {
                    candidates.add(node)
                }

                val cls = node.className?.toString() ?: ""
                if (node.isClickable && (cls.contains("Button") || cls.contains("TextView")) && text.isNotBlank()) {
                    nonCancelButtons.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        val priorityMatch = candidates.firstOrNull {
            val t = (it.text?.toString() ?: "").trim()
            t.equals("ok", ignoreCase = true) || t.equals("aceptar", ignoreCase = true) ||
                    t == "ঠিক আছে" || t == "موافق" || t.equals("tamam", ignoreCase = true)
        } ?: candidates.firstOrNull()

        if (priorityMatch != null) return priorityMatch

        // Geometric Right-Side Dialog Button Fallback (OK / Aceptar option is on the right side of dialog)
        if (nonCancelButtons.isNotEmpty()) {
            val rect = android.graphics.Rect()
            return nonCancelButtons.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.left
            }
        }

        return null
    }

    private fun findOkOrDeleteConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val nonCancelButtons = mutableListOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty() && count < 80) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim().lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isCancel = text == "cancel" || text == "বাতিল" || text == "না" || text == "no" ||
                    text == "annuler" || text == "abbrechen" || text == "отмена" || text == "إلغاء" ||
                    text == "iptal" || text == "batal" || text == "取消" || text == "キャンセル" ||
                    text == "cancelar"
            if (!isCancel && node.isEnabled) {
                // Priority: Standard Android Dialog Positive Button (universal across all languages & OEM brands)
                if (viewId.endsWith(":id/button1") || viewId.contains("confirm") ||
                    viewId.contains("button_ok") || viewId.contains("positive") ||
                    viewId.contains("btn_confirm") || viewId.contains("btn_ok") ||
                    viewId.contains("alert_dialog_button")) {
                    return node
                }

                val isConfirmText = text == "delete" || text == "মুছুন" || text == "ok" || text == "clear" ||
                        text == "confirm" || text == "ঠিক আছে" || text == "হ্যাঁ" ||
                        text == "clear all data" || text == "সব ডেটা মুছুন" ||
                        text == "हटाएं" || text == "ठीक है" || text == "हाँ" || text == "साफ़ करें" || // Hindi
                        text == "eliminar" || text == "aceptar" || text == "borrar" || text == "sí" || // Spanish
                        text == "supprimer" || text == "effacer" || text == "oui" || // French
                        text == "löschen" || text == "ja" || // German
                        text == "удалить" || text == "ок" || text == "да" || text == "очистить" || // Russian
                        text == "حذف" || text == "موافق" || text == "نعم" || // Arabic
                        text == "excluir" || text == "apagar" || text == "sim" || // Portuguese
                        text == "sil" || text == "tamam" || text == "evet" || // Turkish
                        text == "hapus" || text == "oke" || text == "ya" || // Indonesian
                        text == "删除" || text == "确定" || text == "是" || // Chinese
                        text == "削除" || text == "はい" || // Japanese
                        text == "xóa" || text == "có" // Vietnamese

                if (isConfirmText) {
                    return node
                }

                val cls = node.className?.toString() ?: ""
                if (node.isClickable && (cls.contains("Button") || cls.contains("TextView")) && text.isNotBlank()) {
                    nonCancelButtons.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // Geometric Right-Side Dialog Button Fallback (in custom OEM dialogs, positive option is on the right)
        if (nonCancelButtons.isNotEmpty()) {
            val rect = android.graphics.Rect()
            return nonCancelButtons.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.left
            }
        }

        return null
    }

    private fun findLiteClearButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 80) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val lower = text.lowercase()

            val isExactClear = text.equals("CLEAR", ignoreCase = true) ||
                    text.equals("Clear", ignoreCase = true) ||
                    text.equals("BORRAR", ignoreCase = true) ||
                    text.equals("Borrar", ignoreCase = true) ||
                    text.equals("মুছুন") ||
                    lower == "साफ़ करें" ||
                    lower == "limpiar" ||
                    lower == "borrar" ||
                    lower == "effacer" ||
                    lower == "löschen" ||
                    lower == "очистить" ||
                    lower == "مسح" ||
                    lower == "limpar" ||
                    lower == "temizle" ||
                    lower == "hapus" ||
                    lower == "清除" ||
                    lower == "消去" ||
                    lower == "xóa" ||
                    desc.equals("CLEAR", ignoreCase = true) ||
                    desc.equals("BORRAR", ignoreCase = true)

            val isNotOtherClear = !text.contains("All", ignoreCase = true) &&
                    !text.contains("Todo", ignoreCase = true) &&
                    !text.contains("Phone", ignoreCase = true) &&
                    !text.contains("Teléfono", ignoreCase = true) &&
                    !text.contains("Cache", ignoreCase = true) &&
                    !text.contains("Caché", ignoreCase = true) &&
                    !text.contains("Storage", ignoreCase = true) &&
                    !text.contains("Accounts", ignoreCase = true) &&
                    !text.contains("Cuentas", ignoreCase = true)

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
     * Navigates double-back + home, kills background process, finishes in ~20ms.
     */
    private fun autoCloseCleanedSequence() {
        if (!isAutomating) return
        isAutomating = false
        stopActiveRunner()
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
        }, 30)
    }

    private fun finishAndCloseSettings(message: String) {
        if (!isAutomating) return
        isAutomating = false
        stopActiveRunner()
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
        }, 30)
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
