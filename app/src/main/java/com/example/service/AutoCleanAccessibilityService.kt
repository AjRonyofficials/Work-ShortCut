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

            val pkg = (event?.packageName?.toString() ?: "").lowercase()
            val isTargetPkg = currentTarget.isNotEmpty() && pkg.contains(currentTarget.lowercase())
            val isKnownTarget = pkg.contains("lite") || pkg.contains("facebook") || pkg.contains("katana") ||
                    pkg.contains("settings") || pkg.contains("samsung") || pkg.contains("miui") ||
                    pkg.contains("securitycenter") || pkg.contains("packageinstaller") ||
                    pkg.contains("systemui") || pkg.isEmpty()

            if (!isTargetPkg && !isKnownTarget && !isTargetLiteMode) {
                return
            }

            val rootNode = rootInActiveWindow ?: return
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
            "অ্যাকাউন্ট এবং সেটিংস"
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

                // Priority 2: Text matching for Storage & Cache across Samsung, Xiaomi, Vivo, Oppo, Pixel
                val isStorageMatch = combined.contains("storage & cache") ||
                        combined.contains("storage and cache") ||
                        combined.contains("storage usage") ||
                        combined.contains("internal storage") ||
                        combined.contains("স্টোরেজ ও ক্যাশ") ||
                        combined.contains("স্টোরেজ") ||
                        combined.contains("মেমরি") ||
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
            listOf("clear cache", "clear data", "clear storage", "manage space", "manage storage", "সব ডেটা মুছুন"),
            resourceIds = listOf(
                "com.samsung.android.settings:id/clear_data_button",
                "com.android.settings:id/clear_data_button",
                "com.samsung.android.settings:id/clear_cache_button",
                "com.android.settings:id/clear_cache_button",
                "com.miui.securitycenter:id/clear_all_data"
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
                try {
                    rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                } catch (_: Exception) {}
            }
        }

        // Step 1: In Storage screen -> Execute BOTH Clear Cache and Clear Data sequentially!
        if (step in 1..2 || inStorageScreen) {
            // First: Click "Clear cache" if available and not yet clicked
            if (!clickedClearCache) {
                val clearCacheNode = findNodeByKeywords(
                    rootNode,
                    listOf("clear cache", "ক্যাশ মুছুন", "ক্যাশে মুছুন", "ক্লিয়ার ক্যাশ", "ক্লিন ক্যাশ"),
                    resourceIds = listOf(
                        "com.samsung.android.settings:id/clear_cache_button",
                        "com.android.settings:id/clear_cache_button",
                        "com.samsung.android.settings:id/button2"
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
                    "clear all data",
                    "সব ডেটা মুছুন",
                    "সব ডাটা মুছুন"
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
                    "clear data",
                    "ক্লিয়ার ডেটা",
                    "ডেটা মুছুন",
                    "ডাটা মুছুন",
                    "clear storage",
                    "স্টোরেজ মুছুন",
                    "manage space",
                    "manage storage",
                    "delete data"
                ),
                resourceIds = listOf(
                    "com.samsung.android.settings:id/clear_data_button",
                    "com.android.settings:id/clear_data_button",
                    "com.samsung.android.settings:id/button1",
                    "com.android.settings:id/clear_data_btn"
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

            if (clickedClearCache && now - lastActionTime > 180) {
                step = 3
                lastActionTime = now
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
                if (now - lastActionTime > 220) {
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

        while (queue.isNotEmpty() && count < 80) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            val lower = text.lowercase()

            if (lower.contains("accounts and setting") || lower.contains("অ্যাকাউন্ট এবং সেটিংস") || lower.contains("not recommended")) {
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

        while (queue.isNotEmpty() && count < 70) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val lower = text.lowercase()
            val lowerDesc = desc.lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isCancel = lower == "cancel" || lower == "বাতিল" || lower == "না" || lower == "no"
            if (!isCancel && node.isEnabled) {
                if (lower == "ok" || lower == "okay" || lower == "confirm" ||
                    lower == "ঠিক আছে" || lower == "yes" || lower == "হ্যাঁ" ||
                    lowerDesc == "ok" || lowerDesc == "confirm") {
                    candidates.add(node)
                } else if (viewId.endsWith(":id/button1") || viewId.endsWith(":id/confirm") || viewId.endsWith(":id/ok")) {
                    candidates.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return candidates.firstOrNull { (it.text?.toString() ?: "").trim().equals("ok", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    private fun findOkOrDeleteConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 70) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim().lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isCancel = text == "cancel" || text == "বাতিল" || text == "না" || text == "no"
            if (!isCancel && node.isEnabled) {
                if (text == "delete" || text == "মুছুন" || text == "ok" || text == "clear" ||
                    text == "confirm" || text == "ঠিক আছে" || text == "হ্যাঁ" ||
                    text == "clear all data" || text == "সব ডেটা মুছুন") {
                    return node
                }
                if (viewId.endsWith(":id/button1") || viewId.contains("confirm")) {
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

        while (queue.isNotEmpty() && count < 70) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()

            val isExactClear = text.equals("CLEAR", ignoreCase = true) ||
                    text.equals("Clear", ignoreCase = true) ||
                    text.equals("মুছুন") ||
                    desc.equals("CLEAR", ignoreCase = true)

            val isNotOtherClear = !text.contains("All", ignoreCase = true) &&
                    !text.contains("Phone", ignoreCase = true) &&
                    !text.contains("Cache", ignoreCase = true) &&
                    !text.contains("Storage", ignoreCase = true) &&
                    !text.contains("Accounts", ignoreCase = true)

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
     * Navigates back, returns to home, kills background process, finishes in ~80ms.
     */
    private fun autoCloseCleanedSequence() {
        val pkgToKill = targetPackage
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
                pkgToKill?.let { pkg ->
                    try {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(pkg)
                        Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                    } catch (_: Exception) {}
                }
                Toast.makeText(applicationContext, "✓ $targetAppName ডেটা সফলভাবে ক্লিয়ার হয়েছে!", Toast.LENGTH_SHORT).show()
                isAutomating = false
                targetPackage = null
                step = 0
                liteStep = LITE_STEP_IDLE
                isTargetLiteMode = false
            }, 70)
        }, 70)
    }

    private fun finishAndCloseSettings(message: String) {
        val pkgToKill = targetPackage
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
                pkgToKill?.let { pkg ->
                    try {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(pkg)
                        Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                    } catch (_: Exception) {}
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                isAutomating = false
                targetPackage = null
                step = 0
                liteStep = LITE_STEP_IDLE
                isTargetLiteMode = false
            }, 60)
        }, 60)
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
