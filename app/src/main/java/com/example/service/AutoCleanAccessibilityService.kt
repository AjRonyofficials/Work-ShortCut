package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import kotlin.math.abs

/**
 * Zero Cleaner-Grade Universal Reactive Accessibility Engine:
 * Compatible with Android 10, 11, 12, 13, 14, 15, 16 & 17 across ALL Phone Brands & UI Models:
 * - Samsung One UI (2.0 – 7.0)
 * - Xiaomi / Redmi / POCO (MIUI 11 – 14 & HyperOS 1.0 – 2.0)
 * - Oppo ColorOS / Realme UI / OnePlus OxygenOS (Android 10 – 17)
 * - Vivo FuntouchOS / OriginOS & iQOO
 * - Tecno / Infinix / Itel (HiOS & XOS / Transsion PhoneMaster)
 * - Huawei EMUI & Honor MagicOS
 * - Google Pixel, Motorola MyUX, Nothing OS, Sony, Asus, Nokia (AOSP Stock Android 10 – 17)
 *
 * Core Pipeline:
 * 1. Reactive Screen-Driven State Machine (Never gets stuck on a screen if an OEM animation swallows a tap).
 * 2. Strict "1st Clear Data -> Confirm (OK/Delete) -> 2nd Clear Cache" sequence for all apps.
 * 3. 100% Language-Independent + 60-Language Facebook Lite Flow:
 *    "Check All Boxes (Clear All + Cache + Accounts & Settings) -> Confirm OK -> Tap CLEAR -> System Clear Cache".
 * 4. Zero-Extra-Tap Post-Clean Destination:
 *    Immediately jumps to Home Screen (`GLOBAL_ACTION_HOME`) or Auto-Opens the Cleaned App based on user setting.
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
        const val LITE_STEP_WAIT_SYSTEM_CACHE = 5
        const val LITE_STEP_DONE = 6

        var isTargetLiteMode: Boolean = false
            private set

        var isFacebookTarget: Boolean = false
            private set

        fun isFacebookOrLite(packageName: String?, appName: String?): Boolean {
            val pkg = (packageName ?: "").lowercase()
            val name = (appName ?: "").lowercase()
            return pkg == "com.facebook.lite" ||
                    pkg == "com.facebook.katana" ||
                    pkg.contains("facebook") ||
                    (pkg.contains("lite") && name.contains("facebook")) ||
                    name.contains("facebook") ||
                    name.contains("fb lite")
        }

        var liteStep: Int = LITE_STEP_IDLE
            private set

        private var step: Int = 0
        private var lastActionTime: Long = 0L
        private var automationStartTime: Long = 0L
        private var liteScreenEnteredTime: Long = 0L
        private var accountsMarkedTime: Long = 0L
        private var accountsTapAttempts: Int = 0
        private var okDialogTapAttempts: Int = 0
        private var clearBtnTapAttempts: Int = 0
        private var hasClickedLiteClearBtn: Boolean = false
        private var returnedFromLiteForCache: Boolean = false
        private var liteBackToStorageTime: Long = 0L

        // Reactive 2-Stage Pipeline Tracking (1st Clear Data -> Confirm -> 2nd Clear Cache)
        private var clickedStorageRowCount: Int = 0
        private var clickedClearData: Boolean = false
        private var clearDataTapAttempts: Int = 0
        private var clickedManageSpaceSubClear: Boolean = false
        private var dataClearConfirmed: Boolean = false
        private var dataClearConfirmedTime: Long = 0L
        private var clickedClearCache: Boolean = false
        private var scrollAttempts: Int = 0

        private const val FAST_NAV_DELAY_MS = 250L
        private const val STEP_DELAY_MS = 620L
        private const val LITE_BUTTON_DELAY_MS = 720L

        // Matches file-size strings like "0MB", "14.2 KB", "0 B", "120 МБ", "০ মেগাবাইট" so they are never mistaken for dialog buttons
        private val FILE_SIZE_REGEX = Regex(
            """^\s*[\d.,٠-٩۰-۹०-९০-৯]+\s*(b|kb|mb|gb|byte|bytes|ko|mo|go|кб|мб|гб|ب|ك\.ب|م\.ب|কেবি|এমবি|মেগাবাইট|কিলোবাইট|केबी|एमबी)\s*$""",
            RegexOption.IGNORE_CASE
        )

        fun isServiceRunning(): Boolean = instance != null

        private fun resetAutomationFlags(
            pkg: String,
            appName: String,
            mode: Int,
            isLite: Boolean
        ) {
            targetPackage = pkg
            targetAppName = appName
            currentMode = mode
            isAutomating = true
            isFacebookTarget = isFacebookOrLite(pkg, appName)
            isTargetLiteMode = isLite || com.example.util.AppManagerHelper.isFacebookLite(pkg, appName)
            step = 0
            liteStep = LITE_STEP_IDLE
            liteScreenEnteredTime = 0L
            accountsMarkedTime = 0L
            accountsTapAttempts = 0
            okDialogTapAttempts = 0
            clearBtnTapAttempts = 0
            hasClickedLiteClearBtn = false
            returnedFromLiteForCache = false
            liteBackToStorageTime = 0L
            clickedStorageRowCount = 0
            clickedClearData = false
            clearDataTapAttempts = 0
            clickedManageSpaceSubClear = false
            dataClearConfirmed = false
            dataClearConfirmedTime = 0L
            clickedClearCache = false
            scrollAttempts = 0
            val now = System.currentTimeMillis()
            lastActionTime = now - 1000L
            automationStartTime = now
        }

        fun startAutoClean(
            context: Context,
            packageName: String,
            appName: String = "App",
            isLiteMode: Boolean = false
        ) {
            resetAutomationFlags(packageName, appName, MODE_AUTO_CLEAN, isLiteMode)

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
            resetAutomationFlags(packageName, appName, MODE_FORCE_CLOSE, false)

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
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
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
            serviceInfo?.let { info ->
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                info.flags = info.flags or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                info.notificationTimeout = 35
                serviceInfo = info
            }
        } catch (_: Throwable) {}
    }

    private val activeTickRunnable = object : Runnable {
        override fun run() {
            if (!isAutomating) return
            try {
                val primaryRoot = getBestActiveRoot()
                if (primaryRoot != null) {
                    processAutomation(primaryRoot)
                }
            } catch (_: Throwable) {}
            if (isAutomating) {
                mainHandler.postDelayed(this, 95)
            }
        }
    }

    private fun startActiveRunner() {
        mainHandler.removeCallbacks(activeTickRunnable)
        mainHandler.postDelayed(activeTickRunnable, 100)
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

    /**
     * Strictly checks if a package is a system Settings, PackageInstaller, SecurityCenter, or OEM Dialog host
     * across all Android 10–17 brands.
     */
    private fun isSystemSettingsOrDialogPackage(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        return pkg.contains("settings") ||
                pkg.contains("securitycenter") ||
                pkg.contains("safecenter") ||
                pkg.contains("packageinstaller") ||
                pkg.contains("permissioncontroller") ||
                pkg.contains("miui") ||
                pkg.contains("xiaomi") ||
                pkg.contains("hyperos") ||
                pkg.contains("samsung.android.settings") ||
                pkg.contains("sec.android") ||
                pkg.contains("coloros") ||
                pkg.contains("oplus") ||
                pkg.contains("oneplus") ||
                pkg.contains("realme") ||
                pkg.contains("vivo") ||
                pkg.contains("iqoo") ||
                pkg.contains("transsion") ||
                pkg.contains("phonemaster") ||
                pkg.contains("huawei") ||
                pkg.contains("hihonor") ||
                pkg.contains("systemmanager") ||
                pkg.contains("motorola") ||
                pkg.contains("nothing") ||
                pkg.contains("asus") ||
                pkg.contains("sony") ||
                pkg == "android"
    }

    /**
     * Validates that a window belongs to either the target app being cleaned (e.g. Facebook Lite ManageSpaceActivity)
     * or the system Settings / OEM dialog host, while rejecting small floating chat-head bubbles.
     */
    private fun isAllowedTargetOrSettingsWindow(
        win: AccessibilityWindowInfo?,
        root: AccessibilityNodeInfo?,
        targetPkgLower: String
    ): Boolean {
        if (root == null) return false
        val pkg = (root.packageName?.toString() ?: "").lowercase()
        if (pkg.isEmpty() || pkg == packageName.lowercase()) return false
        if (pkg.contains("systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains("launcher")) {
            return false
        }

        val isTargetApp = targetPkgLower.isNotEmpty() && pkg == targetPkgLower
        val isSettingsOrDialog = isSystemSettingsOrDialogPackage(pkg)
        if (!isTargetApp && !isSettingsOrDialog) {
            return false
        }

        // Allow compact OEM confirmation dialogs (width >= 220, height >= 95) while rejecting tiny floating bubbles
        val rect = Rect()
        if (win != null) {
            win.getBoundsInScreen(rect)
        } else {
            root.getBoundsInScreen(rect)
        }
        if (rect.width() in 1..219 || rect.height() in 1..94) {
            return false
        }
        return true
    }

    /**
     * Retrieves the top-most interactive application or confirmation dialog root across Android 10–17.
     * Prioritizes confirmation dialogs and bottom-sheets so popups are clicked immediately!
     */
    private fun getBestActiveRoot(): AccessibilityNodeInfo? {
        try {
            val targetPkgLower = (targetPackage ?: "").lowercase()
            val activeRoot = rootInActiveWindow
            val winList = try { windows } catch (_: Throwable) { null }
            if (!winList.isNullOrEmpty()) {
                val validWindows = winList.filter { win ->
                    (win.type == AccessibilityWindowInfo.TYPE_APPLICATION ||
                            win.type == AccessibilityWindowInfo.TYPE_SYSTEM) &&
                            isAllowedTargetOrSettingsWindow(win, win.root, targetPkgLower)
                }
                // 1. Highest Priority: If any valid window is an active confirmation popup/bottom-sheet dialog, return it first!
                for (win in validWindows) {
                    val r = win.root ?: continue
                    if (hasDialogIndicators(r)) {
                        return r
                    }
                }
                // 2. If activeRoot is a valid target/Settings window, use it
                if (activeRoot != null && isAllowedTargetOrSettingsWindow(null, activeRoot, targetPkgLower)) {
                    return activeRoot
                }
                // 3. Otherwise pick the top-most valid Settings or target ManageSpaceActivity window
                for (win in validWindows) {
                    val r = win.root ?: continue
                    return r
                }
            }
            if (activeRoot != null && isAllowedTargetOrSettingsWindow(null, activeRoot, targetPkgLower)) {
                return activeRoot
            }
            return null
        } catch (_: Throwable) {
            return try { rootInActiveWindow } catch (_: Throwable) { null }
        }
    }

    private fun hasDialogIndicators(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 100) {
            val n = queue.removeFirst()
            count++
            val vid = n.viewIdResourceName?.lowercase() ?: ""
            val text = (n.text?.toString() ?: "").trim().lowercase()
            if (vid == "android:id/button1" || vid == "android:id/button2" ||
                vid.endsWith(":id/alerttitle") || vid.endsWith(":id/buttonpanel") ||
                vid.contains("clear_all_data") || vid.contains("dialog") ||
                vid.contains("button_positive") || vid.contains("button_negative") ||
                isCancelKeyword(text)) {
                return true
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (!isAutomating) return
            val currentTarget = targetPackage ?: return

            val now = System.currentTimeMillis()
            if (now - lastEventProcessedTime < 30) return
            lastEventProcessedTime = now

            val pkg = (event?.packageName?.toString() ?: "").lowercase()
            if (pkg == packageName.lowercase()) return

            val isTargetPkg = currentTarget.isNotEmpty() && pkg == currentTarget.lowercase()
            val isKnownSettings = isSystemSettingsOrDialogPackage(pkg) || pkg.isEmpty()

            if (!isTargetPkg && !isKnownSettings) {
                return
            }

            val rootNode = getBestActiveRoot() ?: return
            processAutomation(rootNode)
        } catch (_: Throwable) {
            // Crash-proof across all Android 10–17 ROMs
        }
    }

    private fun processAutomation(rootNode: AccessibilityNodeInfo) {
        if (!isAutomating) return
        val currentTarget = targetPackage ?: return

        val now = System.currentTimeMillis()
        // Safety timeout after 15 seconds
        if (now - lastActionTime > 15000 || now - automationStartTime > 22000) {
            isAutomating = false
            targetPackage = null
            stopActiveRunner()
            return
        }

        val windowPkg = (rootNode.packageName?.toString() ?: "").lowercase()
        if (windowPkg == packageName.lowercase()) return

        // If user manually exited to Home launcher after 3.5 seconds, stop cleanly
        val isLauncher = (windowPkg.contains("launcher") || windowPkg.contains("nexuslauncher") ||
                (windowPkg.contains("home") && !windowPkg.contains("settings"))) &&
                !windowPkg.contains("settings") && !windowPkg.contains("systemui") &&
                !windowPkg.contains("facebook") && !windowPkg.contains("lite") &&
                !windowPkg.contains(currentTarget.lowercase()) &&
                now - automationStartTime > 3500

        if (isLauncher) {
            isAutomating = false
            stopActiveRunner()
            mainHandler.removeCallbacksAndMessages(null)
            return
        }

        // 1. Force Close Mode
        if (currentMode == MODE_FORCE_CLOSE) {
            handleForceCloseStep(rootNode)
            return
        }

        // 2. Facebook Lite Internal "Clear Storage on Your Phone" Screen (100% Language-Independent + Multi-language)
        if (!returnedFromLiteForCache && isLiteStorageScreen(rootNode, currentTarget)) {
            handleLiteStorageScreenFlow(rootNode)
            return
        }

        // 3. Universal Zero-Cleaner Reactive Auto-Clean Flow for All Apps (Android 10–17)
        handleAutoCleanStep(rootNode, currentTarget)
    }

    /**
     * 100% Language-Independent Detection of Facebook Lite's Internal Storage Screen (`ManageSpaceActivity`).
     * Works regardless of what language the user selected inside Facebook Lite!
     */
    private fun isLiteStorageScreen(rootNode: AccessibilityNodeInfo, currentTarget: String): Boolean {
        val windowPkg = (rootNode.packageName?.toString() ?: "").lowercase()
        val targetLower = currentTarget.lowercase()

        val isLiteWindow = (isTargetLiteMode && windowPkg == targetLower) ||
                windowPkg == "com.facebook.lite" ||
                windowPkg.contains("facebook.lite") ||
                (isTargetLiteMode && windowPkg.contains("lite") && !windowPkg.contains("settings"))

        if (liteStep in LITE_STEP_SELECTING_ACCOUNTS..LITE_STEP_FINAL_CONFIRM) {
            if (isLiteWindow || (!windowPkg.contains("settings") && !windowPkg.contains("securitycenter") && !windowPkg.contains("safecenter"))) {
                return true
            }
        }

        val checkableNodes = collectCheckableNodes(rootNode)

        // Android Settings (App Info / Storage) never has 3+ CheckBoxes on the storage screen.
        // Facebook Lite's "Clear Storage on Your Phone" screen ALWAYS has 4–5 CheckBoxes!
        if (checkableNodes.size >= 3) {
            return true
        }

        if (isLiteWindow && (checkableNodes.isNotEmpty() || clickedClearData || step >= 1)) {
            return true
        }

        val keywords = listOf(
            "facebook lite storage",
            "clear storage on your phone",
            "accounts and settings",
            "accounts and setting",
            "accounts & settings",
            "photo cache",
            "video cache",
            "other cache",
            "remove unnecessary app files",
            "not recommended",
            // Bengali / Assamese
            "অ্যাকাউন্ট এবং সেটিংস",
            "অ্যাকাউন্ট ও সেটিংস",
            "ফটো ক্যাশ",
            "ভিডিও ক্যাশ",
            // Hindi / Marathi / Nepali
            "खाते और सेटिंग",
            "फ़ोटो कैश",
            "वीडियो कैश",
            "अनुशंसित नहीं",
            // Urdu / Arabic / Persian
            "الحسابات والإعدادات",
            "مسح وحدة التخزين على هاتفك",
            "غير موصى به",
            "اکاؤنٹس اور سیٹنگز",
            "حساب‌ها و تنظیمات",
            // Spanish / Portuguese
            "libera espacio en el teléfono",
            "cuentas y configuración",
            "cuentas y configuracion",
            "no recomendado",
            "contas e configurações",
            "contas e definições",
            "não recomendado",
            // French / German / Italian / Dutch
            "comptes et paramètres",
            "non recommandé",
            "konten und einstellungen",
            "nicht empfohlen",
            "account e impostazioni",
            "non consigliato",
            "accounts en instellingen",
            // Indonesian / Malay / Filipino / Vietnamese
            "akun dan pengaturan",
            "tidak disarankan",
            "akaun dan tetapan",
            "mga account at setting",
            "tài khoản và cài đặt",
            "không khuyến nghị",
            // Turkish / Russian / Polish / Romanian
            "hesaplar ve ayarlar",
            "önerilmez",
            "аккаунты и настройки",
            "учетные записи и настройки",
            "не рекомендуется",
            "konta i ustawienia",
            "conturi și setări",
            // Thai / Chinese / Japanese / Korean
            "บัญชีและการตั้งค่า",
            "账户和设置",
            "帳號和設定",
            "アカウントと設定",
            "계정 및 설정"
        )
        return findNodeByKeywords(rootNode, keywords) != null
    }

    /**
     * Collects all CheckBox / checkable nodes on screen, sorted top-to-bottom by vertical Y position.
     */
    private fun collectCheckableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val list = mutableListOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty() && count < 500) {
            val node = queue.removeFirst()
            count++
            val cls = (node.className?.toString() ?: "").lowercase()
            if (node.isCheckable || cls.contains("checkbox") || cls.contains("compoundbutton") || cls.contains("checkedtextview")) {
                list.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        val rect = Rect()
        return list.sortedBy {
            it.getBoundsInScreen(rect)
            rect.centerY()
        }
    }

    private fun handleForceCloseStep(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < STEP_DELAY_MS) return

        val confirmBtn = findOkOrDeleteConfirmButton(rootNode)
        if (confirmBtn != null && isNodeEffectivelyEnabled(confirmBtn)) {
            clickNodeOrGesture(confirmBtn)
            lastActionTime = now
            mainHandler.postDelayed({
                finishAndCloseSettings("$targetAppName Force Stopped ✓")
            }, 400)
            return
        }

        if (step == 0) {
            val forceStopBtn = findNodeByKeywords(
                rootNode,
                listOf(
                    "force stop", "force close", "থামিয়ে দিন", "জোরপূর্বক বন্ধ করুন", "বাধ্যতামূলক বন্ধ",
                    "रोकें", "बलपूर्वक रोकें", "detener", "forzar detención", "forcer l'arrêt",
                    "beenden erzwingen", "остановить", "إيقاف إجباري", "forçar parada", "durmaya zorla",
                    "paksa berhenti", "buộc dừng", "บังคับหยุด", "强行停止", "強制停止", "강제 종료"
                ),
                resourceIds = listOf(
                    "com.android.settings:id/force_stop_button",
                    "com.samsung.android.settings:id/force_stop_button",
                    "com.android.settings:id/button2",
                    "com.samsung.android.settings:id/button2",
                    "com.android.settings:id/right_button",
                    "com.miui.securitycenter:id/force_stop",
                    "com.coloros.safecenter:id/force_stop",
                    "com.oplus.safecenter:id/force_stop",
                    "com.vivo.safecenter:id/force_stop"
                )
            )

            if (forceStopBtn != null && isNodeEffectivelyEnabled(forceStopBtn)) {
                clickNodeOrGesture(forceStopBtn)
                step = 1
                lastActionTime = now
                return
            } else {
                finishAndCloseSettings("$targetAppName বন্ধ করা হয়েছে ✓")
                return
            }
        }

        if (step == 1 && now - lastActionTime > 1500) {
            finishAndCloseSettings("$targetAppName Closed ✓")
        }
    }

    /**
     * Accurately finds the "Storage & cache" / "Storage" / "Storage usage" row in Android Settings App Info
     * across Android 10–17 (All phone brands, models & languages).
     * Strictly excludes Notifications, Permissions, Battery, Mobile data, Open by default.
     */
    private fun findStorageRowNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 500) {
            val node = queue.removeFirst()
            count++

            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val combined = "$text $desc".lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isExcluded = combined.contains("notification") || combined.contains("বিজ্ঞপ্তি") ||
                    combined.contains("permission") || combined.contains("অনুমতি") ||
                    combined.contains("mobile data") || combined.contains("battery") ||
                    combined.contains("ব্যাটারি") || combined.contains("open by default") ||
                    combined.contains("screen time") || combined.contains("unused apps") ||
                    combined.contains("allow notification") || combined.contains("data usage")

            if (!isExcluded) {
                // Priority 1: OEM Resource IDs for Storage row
                if (viewId.contains("storage_settings") || viewId.contains("storage_use") ||
                    viewId.contains("storage_row") || viewId.contains("pref_storage") ||
                    viewId.endsWith(":id/storage") || viewId.endsWith(":id/memory")) {
                    return node
                }

                // Priority 2: Multi-language Storage row titles across all OEM skins (Android 10–17)
                val isStorageMatch = combined.contains("storage & cache") ||
                        combined.contains("storage and cache") ||
                        combined.contains("storage usage") ||
                        combined.contains("storage & data") ||
                        combined.contains("internal storage") ||
                        combined.contains("স্টোরেজ ও ক্যাশ") ||
                        combined.contains("স্টোরেজ এবং ক্যাশ") ||
                        combined.contains("স্টোরেজ ব্যবহার") ||
                        combined.contains("স্টোরেজ") ||
                        combined.contains("মেমরি") ||
                        combined.contains("संग्रहण") ||
                        combined.contains("स्टोरेज") ||
                        combined.contains("মেমোরি") ||
                        combined.contains("اسٹوریج") ||
                        combined.contains("ذخیره‌سازی") ||
                        combined.contains("حافظه") ||
                        combined.contains("almacenamiento") ||
                        combined.contains("stockage") ||
                        combined.contains("speicher") ||
                        combined.contains("archiviazione") ||
                        combined.contains("memoria") ||
                        combined.contains("хранилище") ||
                        combined.contains("память") ||
                        combined.contains("التخزين") ||
                        combined.contains("مكان التخزين") ||
                        combined.contains("armazenamento") ||
                        combined.contains("depolama") ||
                        combined.contains("penyimpanan") ||
                        combined.contains("storan") ||
                        combined.contains("存储") ||
                        combined.contains("储存") ||
                        combined.contains("儲存空間") ||
                        combined.contains("ストレージ") ||
                        combined.contains("저장공간") ||
                        combined.contains("lưu trữ") ||
                        combined.contains("bộ nhớ") ||
                        combined.contains("พื้นที่เก็บข้อมูล") ||
                        combined.contains("pamięć") ||
                        combined.contains("stocare") ||
                        combined.contains("opslag") ||
                        (text.equals("storage", ignoreCase = true) && !combined.contains("manage") && !combined.contains("clear"))

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

    private fun scrollDownToFind(root: AccessibilityNodeInfo): Boolean {
        try {
            if (root.isScrollable) {
                return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < 250) {
                val node = queue.removeFirst()
                count++
                val cls = (node.className?.toString() ?: "").lowercase()
                if (node.isScrollable || cls.contains("recyclerview") || cls.contains("scrollview") || cls.contains("listview")) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                        return true
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isNodeEffectivelyEnabled(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (!node.isEnabled) return false
        var curr = node.parent
        var depth = 0
        while (curr != null && depth < 5) {
            if (curr.isClickable && !curr.isEnabled) {
                return false
            }
            curr = curr.parent
            depth++
        }
        return true
    }

    /**
     * Finds the "Clear data" / "Clear storage" / "Manage space" button across all Android 10–17 OEM skins.
     */
    private fun findClearDataButtonNode(root: AccessibilityNodeInfo, isOnStorageScreen: Boolean = true): AccessibilityNodeInfo? {
        // Priority A: Explicit OEM Clear Data resource IDs
        val explicitResourceMatch = findNodeByKeywords(
            root,
            emptyList(),
            resourceIds = listOf(
                "com.samsung.android.settings:id/clear_data_button",
                "com.android.settings:id/clear_data_button",
                "com.google.android.settings:id/clear_data_button",
                "com.oplus.settings:id/clear_data_button",
                "com.android.settings:id/clear_data_btn",
                "com.miui.securitycenter:id/clear_data",
                "com.coloros.safecenter:id/clear_data",
                "com.oplus.safecenter:id/clear_data",
                "com.vivo.safecenter:id/clear_data",
                "com.iqoo.secure:id/clear_data",
                "com.transsion.phonemaster:id/clear_data",
                "com.huawei.systemmanager:id/clear_data",
                "com.hihonor.systemmanager:id/clear_data",
                "com.android.chrome:id/delete_all_data",
                "com.sec.android.app.sbrowser:id/delete_all_data"
            )
        )
        if (explicitResourceMatch != null) {
            return explicitResourceMatch
        }

        // Priority B: Multi-language "Clear data" / "Clear storage" / "Manage space" / "Clear all data"
        val keywordMatch = findNodeByKeywords(
            root,
            listOf(
                "clear all data", "delete all data", "clear data", "clear storage",
                "manage space", "manage storage", "delete app data", "delete data", "free up space",
                "সব ডেটা মুছুন", "সব ডাটা মুছুন", "ক্লিয়ার ডেটা", "ডেটা মুছুন", "ডাটা মুছুন",
                "স্টোরেজ মুছুন", "স্পেস পরিচালনা", "স্থান পরিচালনা",
                "सभी डेटा साफ़ करें", "डेटा साफ़ करें", "स्टोरेज साफ़ करें", "स्पेस प्रबंधित करें", "डेटा मिटाएं",
                "ڈیٹا صاف کریں", "اسٹوریج صاف کریں", "پاک کردن داده‌ها", "مدیریت فضا",
                "borrar todos los datos", "eliminar todos los datos", "borrar datos", "borrar almacenamiento",
                "liberar espacio", "administrar espacio", "gestionar espacio",
                "effacer toutes les données", "effacer les données", "effacer le stockage", "supprimer les données", "gérer l'espace",
                "alle daten löschen", "daten löschen", "speicherinhalt löschen", "speicherplatz verwalten", "speicher verwalten",
                "cancella dati", "cancella spazio", "gestisci spazio",
                "очистить все данные", "очистить хранилище", "стереть данные", "очистить данные", "управление местом",
                "مسح جميع البيانات", "مسح البيانات", "مسح التخزين", "مسح مساحة التخزين", "إدارة المساحة",
                "limpar todos os dados", "limpar dados", "limpar armazenamento", "gerenciar espaço", "gerir espaço",
                "tüm verileri temizle", "verileri temizle", "depolama alanını temizle", "alanı yönet",
                "hapus semua data", "hapus data", "hapus penyimpanan", "kelola ruang", "urus ruang",
                "清除全部数据", "清除数据", "清除存储空间", "管理空间", "清除資料", "清除儲存空間",
                "データを消去", "ストレージを消去", "容量を管理",
                "데이터 삭제", "저장공간 삭제", "저장공간 관리",
                "xóa tất cả dữ liệu", "xóa dữ liệu", "xóa bộ nhớ", "quản lý dung lượng",
                "ล้างข้อมูลทั้งหมด", "ล้างข้อมูล", "ล้างพื้นที่เก็บข้อมูล", "จัดการพื้นที่",
                "wyczyść dane", "wyczyść pamięć", "zarządzaj miejscem",
                "șterge datele", "gestionare spațiu", "gegevens wissen", "ruimte beheren"
            )
        )
        if (keywordMatch != null) {
            return keywordMatch
        }

        // Priority C: Generic Settings button1 when on the Storage sub-screen (excluding App Info Open/Uninstall/Force stop buttons)
        if (isOnStorageScreen || step >= 1) {
            val fallbackBtn1 = findNodeByKeywords(
                root,
                emptyList(),
                resourceIds = listOf(
                    "com.samsung.android.settings:id/button1",
                    "com.android.settings:id/button1",
                    "com.google.android.settings:id/button1",
                    "com.oplus.settings:id/button1",
                    "com.android.settings:id/action_button1",
                    "com.android.settings:id/left_button"
                )
            )
            if (fallbackBtn1 != null) {
                val txt = ((fallbackBtn1.text?.toString() ?: "") + " " + (fallbackBtn1.contentDescription?.toString() ?: "")).lowercase()
                val isAppInfoBtn = txt.contains("open") || txt.contains("uninstall") ||
                        txt.contains("disable") || txt.contains("force") ||
                        txt.contains("খুলুন") || txt.contains("আনইনস্টল") || txt.contains("থামিয়ে") ||
                        txt.contains("archive")
                if (!isAppInfoBtn) {
                    return fallbackBtn1
                }
            }
        }
        return null
    }

    /**
     * Finds the "Clear cache" button across all Android 10–17 OEM skins.
     */
    private fun findClearCacheButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val explicitCache = findNodeByKeywords(
            root,
            listOf(
                "clear cache", "delete cache", "ক্যাশ মুছুন", "ক্যাশে মুছুন", "ক্লিয়ার ক্যাশ", "ক্লিন ক্যাশ",
                "कैश साफ़ करें", "कैशे साफ़ करें", "کیشے صاف کریں", "پاک کردن حافظه پنهان",
                "limpiar caché", "borrar caché", "vider le cache", "cache leeren", "svuota cache",
                "очистить кэш", "مسح ذاكرة التخزين المؤقت", "مسح التخزين المؤقت",
                "limpar cache", "önbelleği temizle", "hapus cache", "hapus memori singgahan",
                "清除缓存", "清除快取", "キャッシュを消去", "캐시 삭제",
                "xóa bộ nhớ đệm", "ล้างแคช", "wyczyść pamięć podręczną", "golire cache", "cache wissen"
            ),
            resourceIds = listOf(
                "com.samsung.android.settings:id/clear_cache_button",
                "com.android.settings:id/clear_cache_button",
                "com.google.android.settings:id/clear_cache_button",
                "com.oplus.settings:id/clear_cache_button",
                "com.android.settings:id/clear_cache_btn",
                "com.miui.securitycenter:id/clear_cache",
                "com.coloros.safecenter:id/clear_cache",
                "com.oplus.safecenter:id/clear_cache",
                "com.vivo.safecenter:id/clear_cache",
                "com.iqoo.secure:id/clear_cache",
                "com.transsion.phonemaster:id/clear_cache",
                "com.huawei.systemmanager:id/clear_cache",
                "com.hihonor.systemmanager:id/clear_cache"
            )
        )
        if (explicitCache != null) return explicitCache

        if (step >= 1 || clickedStorageRowCount > 0) {
            val fallbackBtn2 = findNodeByKeywords(
                root,
                emptyList(),
                resourceIds = listOf(
                    "com.samsung.android.settings:id/button2",
                    "com.android.settings:id/button2",
                    "com.google.android.settings:id/button2",
                    "com.oplus.settings:id/button2",
                    "com.android.settings:id/action_button2",
                    "com.android.settings:id/right_button"
                )
            )
            if (fallbackBtn2 != null) {
                val txt = ((fallbackBtn2.text?.toString() ?: "") + " " + (fallbackBtn2.contentDescription?.toString() ?: "")).lowercase()
                val isAppInfoBtn = txt.contains("force") || txt.contains("uninstall") ||
                        txt.contains("open") || txt.contains("disable") ||
                        txt.contains("থামিয়ে") || txt.contains("আনইনস্টল") ||
                        txt.contains("archive")
                if (!isAppInfoBtn) {
                    return fallbackBtn2
                }
            }
        }
        return null
    }

    /**
     * Zero Cleaner-Style Reactive Screen-Driven Handler (Android 10–17 All Brands):
     * Strictly executes:
     *   1) Navigate into Storage & cache (if not already showing Clear Data / Clear Cache).
     *   2) STAGE 1: Tap Clear Data / Clear Storage -> Handle Xiaomi/ColorOS/Chrome sub-sheet ("Clear all data") -> Tap Confirm (OK / Delete).
     *   3) STAGE 2: Once Clear Data is confirmed (or if Clear Data is already disabled at 0B), check Clear Cache;
     *      if Clear Cache is enabled, tap Clear Cache!
     *   4) STAGE 3: Immediately execute Post-Clean Action (Home Screen or Auto-Open App) with zero extra taps!
     */
    private fun handleAutoCleanStep(rootNode: AccessibilityNodeInfo, currentTarget: String) {
        val now = System.currentTimeMillis()

        // 0. If we just returned from Facebook Lite's ManageSpaceActivity to check System Clear Cache:
        if (returnedFromLiteForCache) {
            if (now - liteBackToStorageTime < 420L) return
            val cacheBtnAfterLite = findClearCacheButtonNode(rootNode)
            if (cacheBtnAfterLite != null && isNodeEffectivelyEnabled(cacheBtnAfterLite) && !clickedClearCache) {
                if (clickNodeOrGesture(cacheBtnAfterLite)) {
                    clickedClearCache = true
                    lastActionTime = now
                    mainHandler.postDelayed({
                        autoCloseCleanedSequence()
                    }, 380L)
                    return
                }
            }
            // System cache is already 0B (disabled) or we already tapped it -> Complete immediately!
            autoCloseCleanedSequence()
            return
        }

        // 1. If Facebook Lite's internal storage screen is active, hand off to the all-language Lite handler
        if (isLiteStorageScreen(rootNode, currentTarget)) {
            handleLiteStorageScreenFlow(rootNode)
            return
        }

        // 2. Priority Dialog & OEM BottomSheet Check (Works at ANY step without getting stuck!)
        //    2A) Xiaomi MIUI/HyperOS BottomSheet ("Clear all data" + "Clear cache") or Browser "Manage storage" ("Clear all data" / "Delete all data")
        if (clickedClearData && !clickedManageSpaceSubClear) {
            val subClearAll = findNodeByKeywords(
                rootNode,
                listOf(
                    "clear all data", "delete all data", "free up space",
                    "সব ডেটা মুছুন", "সব ডাটা মুছুন", "सभी डेटा साफ़ करें",
                    "borrar todos los datos", "eliminar todos los datos",
                    "effacer toutes les données", "alle daten löschen", "очистить все данные",
                    "مسح جميع البيانات", "limpar todos os dados", "tüm verileri temizle",
                    "hapus semua data", "xóa tất cả dữ liệu", "ล้างข้อมูลทั้งหมด", "清除全部数据"
                ),
                resourceIds = listOf(
                    "com.miui.securitycenter:id/clear_all_data",
                    "com.android.chrome:id/delete_all_data",
                    "com.sec.android.app.sbrowser:id/delete_all_data"
                )
            )
            if (subClearAll != null && isNodeEffectivelyEnabled(subClearAll)) {
                val subId = subClearAll.viewIdResourceName?.lowercase() ?: ""
                val isStorageMainButton = subId.contains("clear_data_button") ||
                        subId == "com.android.settings:id/button1" ||
                        subId == "com.samsung.android.settings:id/button1" ||
                        subId == "com.google.android.settings:id/button1"
                if (subId != "android:id/button1" && !isStorageMainButton && now - lastActionTime >= 350L) {
                    if (clickNodeOrGesture(subClearAll)) {
                        clickedManageSpaceSubClear = true
                        lastActionTime = now
                        return
                    }
                }
            }
        }

        //    2B) Standard Confirmation Dialog or ColorOS/Realme/OneUI/MIUI Confirm Popup (OK / Delete / Clear data)
        val confirmNode = findOkOrDeleteConfirmButton(rootNode)
        if (confirmNode != null && isNodeEffectivelyEnabled(confirmNode)) {
            if (now - lastActionTime < 350L) return
            if (clickNodeOrGesture(confirmNode)) {
                clickedClearData = true
                dataClearConfirmed = true
                dataClearConfirmedTime = now
                step = 4
                lastActionTime = now
                return
            }
        }

        val clearDataNode = findClearDataButtonNode(rootNode, isOnStorageScreen = (step >= 1 || clickedStorageRowCount > 0))
        val clearCacheNode = findClearCacheButtonNode(rootNode)
        val inStorageScreen = clearDataNode != null || clearCacheNode != null

        // 3. STAGE 2 (After 1st Clear Data is Confirmed): Check & Tap 2nd Clear Cache if still enabled, then exit!
        if (dataClearConfirmed) {
            if (now - dataClearConfirmedTime < 480L) return

            // If a second confirmation dialog popped up (e.g. after a 2-level Manage Space sub-screen), confirm it above;
            // otherwise check if Clear Cache button is still enabled on the Storage screen:
            if (clearCacheNode != null && isNodeEffectivelyEnabled(clearCacheNode) && !clickedClearCache) {
                if (clickNodeOrGesture(clearCacheNode)) {
                    clickedClearCache = true
                    lastActionTime = now
                    mainHandler.postDelayed({
                        autoCloseCleanedSequence()
                    }, 380L)
                    return
                }
            }

            // Both Clear Data and Clear Cache are now done -> Exit immediately to Home Screen or Auto-Open App!
            autoCloseCleanedSequence()
            return
        }

        // 4. STAGE 1 (Inside Storage Screen or MIUI App Info with Clear Data):
        //    "1st Clear Data -> Confirm -> 2nd Clear Cache"
        if (inStorageScreen) {
            val storageReadyDelay = if (isTargetLiteMode) 280L else 420L
            if (now - lastActionTime < storageReadyDelay) return

            val isDataEnabled = isNodeEffectivelyEnabled(clearDataNode)
            val isCacheEnabled = isNodeEffectivelyEnabled(clearCacheNode)

            // If we haven't tapped Clear Data yet (or if a previous tap didn't open the dialog after 900ms, retry!)
            if (clearDataNode != null && isDataEnabled) {
                val shouldTapData = !clickedClearData ||
                        (clearDataTapAttempts < 3 && now - lastActionTime >= 900L && !hasDialogIndicators(rootNode))
                if (shouldTapData) {
                    clearDataTapAttempts++
                    val clicked = if (clearDataTapAttempts >= 2) {
                        clickGestureAtNode(clearDataNode, avoidScreenEdges = true) || clickNodeOrGesture(clearDataNode)
                    } else {
                        clickNodeOrGesture(clearDataNode)
                    }
                    if (clicked) {
                        clickedClearData = true
                        step = 3
                        lastActionTime = now
                        return
                    }
                }
                return
            }

            // If Clear Data button is disabled (0B user data, or just turned disabled after wipe), move to 2nd Clear Cache!
            if (!isDataEnabled) {
                if (clearCacheNode != null && isCacheEnabled && !clickedClearCache) {
                    if (clickNodeOrGesture(clearCacheNode)) {
                        clickedClearCache = true
                        dataClearConfirmed = true
                        dataClearConfirmedTime = now
                        lastActionTime = now
                        mainHandler.postDelayed({
                            autoCloseCleanedSequence()
                        }, 380L)
                        return
                    }
                }
                // Both Clear Data and Clear Cache are disabled (0B Data & 0B Cache!) -> Complete immediately!
                if (clearDataNode != null || clearCacheNode != null) {
                    autoCloseCleanedSequence()
                    return
                }
            }
        }

        // 5. STAGE 0 (On App Info Screen, not yet in Storage Screen):
        //    Reactive! Even if step was incremented earlier, if we are STILL on App Info (`!inStorageScreen`),
        //    find and tap "Storage & cache" (or scroll down if hidden) so it NEVER gets stuck on App Info!
        if (!inStorageScreen) {
            val navDelay = if (clickedStorageRowCount == 0) FAST_NAV_DELAY_MS else 600L
            if (now - lastActionTime < navDelay) return

            val storageNode = findStorageRowNode(rootNode)
            if (storageNode != null) {
                val clicked = if (clickedStorageRowCount >= 2) {
                    clickGestureAtNode(storageNode, avoidScreenEdges = true) || clickNodeOrGesture(storageNode)
                } else {
                    clickNodeOrGesture(storageNode)
                }
                if (clicked) {
                    clickedStorageRowCount++
                    step = 1
                    lastActionTime = now
                    return
                }
            } else {
                if (scrollAttempts < 6 && now - lastActionTime >= 420L) {
                    scrollAttempts++
                    lastActionTime = now
                    scrollDownToFind(rootNode)
                }
            }
        }
    }

    /**
     * 100% Language-Independent + 60-Language Facebook Lite Storage Screen Handler!
     * Strictly follows user's pipeline:
     *   "Check all box -> Than clear data -> Than OK -> Than clear cache"
     *   1) Wait 450ms when the screen first opens so all CheckBoxes & CLEAR button settle.
     *   2) If a confirmation popup ("Clear profiles and settings?" CANCEL | OK) is showing, tap OK.
     *   3) Check ALL unchecked CheckBoxes on the screen (top "Clear all" / cache boxes + bottom "Accounts and settings").
     *   4) Once all boxes are checked, tap the blue "CLEAR" button.
     *   5) Once Facebook Lite's data is cleared, press 1 BACK to return to Android Settings Storage screen,
     *      tap system "Clear Cache" if still enabled, and exit directly to Home Screen or Auto-Open App!
     */
    private fun handleLiteStorageScreenFlow(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()

        if (liteScreenEnteredTime == 0L) {
            liteScreenEnteredTime = now
            lastActionTime = now - (LITE_BUTTON_DELAY_MS - 450L)
            return
        }

        if (now - lastActionTime < LITE_BUTTON_DELAY_MS) return

        val checkableNodes = collectCheckableNodes(rootNode)
        val dialogShowing = isLiteDialogShowing(rootNode, checkableNodes)

        // =========================================================================
        // STAGE 1: Confirmation Popup ("Clear profiles and settings?" CANCEL | OK)
        // =========================================================================
        if (dialogShowing) {
            val okDialogBtn = findLiteOkDialogButton(rootNode, checkableNodes)
            if (okDialogBtn != null && isNodeEffectivelyEnabled(okDialogBtn)) {
                okDialogTapAttempts++
                val clickedOk = clickLiteDialogOkReliably(okDialogBtn, okDialogTapAttempts)
                if (clickedOk) {
                    lastActionTime = now
                    if (hasClickedLiteClearBtn) {
                        // Popup appeared AFTER tapping the blue CLEAR button -> Now back out 1 step to clear system cache!
                        transitionFromLiteToSystemCacheCheck()
                    } else {
                        // Popup appeared after checking [ ] Accounts and settings -> Tapping OK marks it [✓]!
                        liteStep = LITE_STEP_CLICK_CLEAR
                    }
                    return
                }
            }
            return
        }

        okDialogTapAttempts = 0
        val accountsRow = findAccountsAndSettingsRow(rootNode, checkableNodes)

        // =========================================================================
        // STAGE 2: We ALREADY tapped the blue CLEAR button (`hasClickedLiteClearBtn == true`)
        //          Verify wipe completed, then back out 1 step to Settings Storage to tap Clear Cache!
        // =========================================================================
        if (hasClickedLiteClearBtn) {
            // If [ ] "Accounts and settings" reset to UNCHECKED (0MB after wipe), CLEAR succeeded!
            if (accountsRow != null && !accountsRow.isChecked) {
                transitionFromLiteToSystemCacheCheck()
                return
            }

            // If [✓] "Accounts and settings" is STILL checked, retry tapping the blue CLEAR button via Touch Gesture
            val clearBtn = findLiteClearButton(rootNode, checkableNodes, accountsRow)
            if (clearBtn != null && isNodeEffectivelyEnabled(clearBtn) && clearBtnTapAttempts < 3) {
                clearBtnTapAttempts++
                lastActionTime = now
                clickLiteClearButtonReliably(clearBtn, forceGesture = true, attempt = clearBtnTapAttempts)
                return
            }

            // Transition back to Settings Storage to check/tap Clear Cache and finish!
            transitionFromLiteToSystemCacheCheck()
            return
        }

        // =========================================================================
        // STAGE 3: Check ALL CheckBoxes (Top "Clear all" + Cache boxes + Bottom "Accounts and settings"),
        //          then tap the blue CLEAR button!
        // =========================================================================

        // 3A: Ensure top "Clear all" and any upper cache checkboxes are checked
        if (checkableNodes.size >= 2) {
            val topCheckbox = checkableNodes.first()
            if (!topCheckbox.isChecked) {
                clickNodeOrGesture(topCheckbox)
            }
            // Also check any middle cache checkboxes that might still be unchecked
            for (i in 1 until checkableNodes.size - 1) {
                val midCb = checkableNodes[i]
                if (!midCb.isChecked) {
                    clickNodeOrGesture(midCb)
                }
            }
        }

        // 3B: Check bottom [ ] "Accounts and settings" if it is currently UNCHECKED
        if (accountsRow != null && !accountsRow.isChecked && accountsTapAttempts < 5) {
            accountsTapAttempts++
            liteStep = LITE_STEP_WAIT_ACCOUNTS_POPUP
            accountsMarkedTime = now
            lastActionTime = now
            clickAccountsRowReliably(
                info = accountsRow,
                forceGesture = (accountsTapAttempts >= 2),
                attempt = accountsTapAttempts
            )
            return
        }

        // 3C: Once all boxes (including [✓] "Accounts and settings") are checked, tap the blue CLEAR button!
        if ((accountsRow != null && accountsRow.isChecked) || accountsTapAttempts >= 5 || liteStep >= LITE_STEP_CLICK_CLEAR) {
            val clearBtn = findLiteClearButton(rootNode, checkableNodes, accountsRow)
            if (clearBtn != null && isNodeEffectivelyEnabled(clearBtn)) {
                if (clickLiteClearButtonReliably(clearBtn, forceGesture = false, attempt = 1)) {
                    hasClickedLiteClearBtn = true
                    clearBtnTapAttempts = 1
                    liteStep = LITE_STEP_FINAL_CONFIRM
                    lastActionTime = now
                    return
                }
            } else if (scrollAttempts < 3 && now - lastActionTime >= LITE_BUTTON_DELAY_MS) {
                scrollAttempts++
                lastActionTime = now
                scrollDownToFind(rootNode)
            }
        }
    }

    /**
     * After Facebook Lite's internal CLEAR finishes, navigates back 1 step to the Android Settings
     * Storage screen so we can also tap the system "Clear cache" button (if enabled) before exiting!
     */
    private fun transitionFromLiteToSystemCacheCheck() {
        if (returnedFromLiteForCache) return
        returnedFromLiteForCache = true
        liteStep = LITE_STEP_WAIT_SYSTEM_CACHE
        val now = System.currentTimeMillis()
        liteBackToStorageTime = now
        lastActionTime = now
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Checks if a node shares a horizontal row (`centerY`) with any CheckBox on screen.
     * On Facebook Lite's main storage screen, row labels ("Photo cache", "Accounts and settings")
     * and file sizes ("0MB", "84KB") share their Y-coordinate with a CheckBox—so excluding them
     * completely prevents false-positive dialog detection in ANY language!
     */
    private fun isNodeOnAnyCheckboxRow(
        node: AccessibilityNodeInfo,
        checkboxBounds: List<Rect>
    ): Boolean {
        if (checkboxBounds.isEmpty()) return false
        val r = Rect()
        node.getBoundsInScreen(r)
        val cy = r.centerY()
        for (cbRect in checkboxBounds) {
            if (abs(cy - cbRect.centerY()) < 62) {
                return true
            }
        }
        return false
    }

    /**
     * Accurately determines if Facebook Lite's confirmation popup ("Clear profiles and settings?" CANCEL | OK)
     * is currently displayed over FB Lite's storage screen—in ANY language!
     * Strictly excludes checkbox row labels and "0MB" size strings from ever triggering false positives.
     */
    private fun isLiteDialogShowing(
        root: AccessibilityNodeInfo,
        checkableNodes: List<AccessibilityNodeInfo>
    ): Boolean {
        val checkboxBounds = checkableNodes.map { cb ->
            Rect().also { cb.getBoundsInScreen(it) }
        }
        val lastCheckboxBottomY = checkboxBounds.maxOfOrNull { it.bottom } ?: 0

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val dialogActionCandidates = mutableListOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty() && count < 400) {
            val node = queue.removeFirst()
            count++
            val vid = node.viewIdResourceName?.lowercase() ?: ""
            if (vid.endsWith(":id/button1") || vid.endsWith(":id/button2") ||
                vid.endsWith(":id/alerttitle") || vid.endsWith(":id/buttonpanel") ||
                vid.contains("dialog")) {
                return true
            }

            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val lower = text.lowercase()
            val lowerDesc = desc.lowercase()
            val combined = "$lower $lowerDesc"

            // 1. Explicit CANCEL or strict OK button on screen (never present on FB Lite's main storage screen)
            if (!node.isCheckable && (isCancelKeyword(lower) || isCancelKeyword(lowerDesc) ||
                        isStrictOkOnlyKeyword(lower) || isStrictOkOnlyKeyword(lowerDesc))) {
                return true
            }

            // 2. Match Facebook Lite's "Clear profiles and settings?" popup title/message in 60+ languages
            if (combined.contains("clear profiles and settings") ||
                combined.contains("clear profile") ||
                combined.contains("all accounts and passwords") ||
                combined.contains("passwords will be removed") ||
                combined.contains("phone number and password to log in") ||
                combined.contains("প্রোফাইল এবং সেটিংস") ||
                combined.contains("পাসওয়ার্ড") ||
                combined.contains("प्रोफ़ाइल और सेटिंग") ||
                combined.contains("पासवर्ड हटा") ||
                combined.contains("perfiles y configuración") ||
                combined.contains("perfis e configurações") ||
                combined.contains("profils et paramètres") ||
                combined.contains("ملفات تعريف") ||
                combined.contains("كلمات المرور") ||
                combined.contains("profil dan pengaturan") ||
                combined.contains("kata sandi") ||
                combined.contains("xóa hồ sơ và cài đặt") ||
                combined.contains("mật khẩu") ||
                combined.contains("ล้างโปรไฟล์และการตั้งค่า") ||
                combined.contains("รหัสผ่าน") ||
                combined.contains("प्रोफाइल") ||
                combined.contains("پروفائل") ||
                combined.contains("профили и настройки") ||
                combined.contains("пароли") ||
                combined.contains("profilleri ve ayarları")) {
                return true
            }

            // Collect non-checkbox, non-file-size clickable button candidates that are NOT on any checkbox row
            val cls = (node.className?.toString() ?: "").lowercase()
            val isExplicitlyClickable = node.isClickable || node.parent?.isClickable == true || cls.contains("button")
            val isFileSizeLabel = FILE_SIZE_REGEX.matches(text)
            if (node.isEnabled && !node.isCheckable && isExplicitlyClickable && !isFileSizeLabel &&
                text.length in 1..24 && !isNodeOnAnyCheckboxRow(node, checkboxBounds)) {
                dialogActionCandidates.add(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // 3. If the active window has NO checkboxes at all and has dialog buttons (separate floating dialog window!)
        if (checkableNodes.isEmpty() && dialogActionCandidates.isNotEmpty()) {
            return true
        }

        // 4. 100% Language-Independent Geometric Check:
        //    Two clickable non-checkbox buttons (CANCEL | OK) sitting side-by-side on the same horizontal Y row
        //    (strictly excluding any checkbox rows and file size labels!)
        val rect1 = Rect()
        val rect2 = Rect()
        for (i in 0 until dialogActionCandidates.size) {
            dialogActionCandidates[i].getBoundsInScreen(rect1)
            if (rect1.width() <= 0 || rect1.height() <= 0 || rect1.top < 320) continue
            for (j in i + 1 until dialogActionCandidates.size) {
                dialogActionCandidates[j].getBoundsInScreen(rect2)
                if (rect2.width() <= 0 || rect2.height() <= 0 || rect2.top < 320) continue
                val sameRow = abs(rect1.centerY() - rect2.centerY()) < 45
                val sideBySide = abs(rect1.centerX() - rect2.centerX()) > 65
                val notAboveCheckboxes = checkableNodes.isEmpty() || rect1.centerY() > (lastCheckboxBottomY - 120)
                if (sameRow && sideBySide && notAboveCheckboxes) {
                    return true
                }
            }
        }

        return false
    }

    data class AccountsRowInfo(
        val textNode: AccessibilityNodeInfo,
        val checkboxNode: AccessibilityNodeInfo?,
        val clickableTarget: AccessibilityNodeInfo,
        val isChecked: Boolean
    )

    /**
     * Finds the "Accounts and settings" row AND its paired CheckBox in Facebook Lite across ALL languages!
     * Uses both 60+ language keywords AND structural bottom-most CheckBox geometry (`sortedCheckables.lastOrNull()`),
     * so even if Facebook Lite is set to an unknown language, the bottom CheckBox is always found and checked!
     */
    private fun findAccountsAndSettingsRow(
        root: AccessibilityNodeInfo,
        sortedCheckables: List<AccessibilityNodeInfo>
    ): AccountsRowInfo? {
        val bottomCheckbox = sortedCheckables.lastOrNull()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        var matchedTextNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && count < 450) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            val lower = text.lowercase()

            val isAccountsMatch = lower.contains("accounts and setting") ||
                    lower.contains("accounts & setting") ||
                    lower.contains("অ্যাকাউন্ট এবং সেটিংস") ||
                    lower.contains("অ্যাকাউন্ট ও সেটিংস") ||
                    lower.contains("একাউন্ট") ||
                    lower.contains("खाते और सेटिंग") ||
                    lower.contains("اکاؤنٹس اور سیٹنگز") ||
                    lower.contains("حساب‌ها و تنظیمات") ||
                    lower.contains("cuentas y configuración") ||
                    lower.contains("cuentas y configuracion") ||
                    lower.contains("contas e configurações") ||
                    lower.contains("contas e definições") ||
                    lower.contains("comptes et paramètres") ||
                    lower.contains("konten und einstellungen") ||
                    lower.contains("account e impostazioni") ||
                    lower.contains("accounts en instellingen") ||
                    lower.contains("аккаунты и настройки") ||
                    lower.contains("учетные записи") ||
                    lower.contains("الحسابات والإعدادات") ||
                    lower.contains("hesaplar ve ayarlar") ||
                    lower.contains("akun dan pengaturan") ||
                    lower.contains("akaun dan tetapan") ||
                    lower.contains("mga account at setting") ||
                    lower.contains("tài khoản và cài đặt") ||
                    lower.contains("บัญชีและการตั้งค่า") ||
                    lower.contains("账户和设置") ||
                    lower.contains("帳號和設定") ||
                    lower.contains("アカウントと設定") ||
                    lower.contains("계정 및 설정") ||
                    lower.contains("konta i ustawienia") ||
                    lower.contains("conturi și setări") ||
                    lower.contains("not recommended") ||
                    lower.contains("अनुशंसित नहीं") ||
                    lower.contains("não recomendado") ||
                    lower.contains("no recomendado") ||
                    lower.contains("non recommandé") ||
                    lower.contains("nicht empfohlen") ||
                    lower.contains("не рекомендуется") ||
                    lower.contains("غير موصى به") ||
                    lower.contains("tidak disarankan") ||
                    lower.contains("không khuyến nghị") ||
                    lower.contains("önerilmez")

            if (isAccountsMatch) {
                matchedTextNode = node
                break
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (matchedTextNode != null) {
            var pairedCheckbox: AccessibilityNodeInfo? = if (matchedTextNode.isCheckable) matchedTextNode else null
            val parent = matchedTextNode.parent
            if (pairedCheckbox == null && parent != null) {
                if (parent.isCheckable) {
                    pairedCheckbox = parent
                } else {
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i)
                        if (sibling != null && sibling.isCheckable) {
                            pairedCheckbox = sibling
                            break
                        }
                    }
                }
            }
            if (pairedCheckbox == null) {
                pairedCheckbox = bottomCheckbox
            }

            val isChecked = (pairedCheckbox?.isChecked == true) ||
                    matchedTextNode.isChecked ||
                    (parent?.isChecked == true)

            val parentRect = Rect()
            parent?.getBoundsInScreen(parentRect)
            val validClickableParent = if (parent != null && parent.isClickable && parentRect.height() in 20..300) {
                parent
            } else {
                null
            }

            val clickableTarget = when {
                pairedCheckbox != null && pairedCheckbox.isClickable -> pairedCheckbox
                validClickableParent != null -> validClickableParent
                matchedTextNode.isClickable -> matchedTextNode
                else -> pairedCheckbox ?: matchedTextNode
            }

            return AccountsRowInfo(
                textNode = matchedTextNode,
                checkboxNode = pairedCheckbox,
                clickableTarget = clickableTarget,
                isChecked = isChecked
            )
        }

        if (bottomCheckbox != null) {
            val parent = bottomCheckbox.parent
            val parentRect = Rect()
            parent?.getBoundsInScreen(parentRect)
            val validClickableParent = if (parent != null && parent.isClickable && parentRect.height() in 20..300) {
                parent
            } else {
                null
            }
            val clickableTarget = when {
                bottomCheckbox.isClickable -> bottomCheckbox
                validClickableParent != null -> validClickableParent
                else -> bottomCheckbox
            }
            return AccountsRowInfo(
                textNode = bottomCheckbox,
                checkboxNode = bottomCheckbox,
                clickableTarget = clickableTarget,
                isChecked = bottomCheckbox.isChecked
            )
        }

        return null
    }

    private fun clickAccountsRowReliably(
        info: AccountsRowInfo,
        forceGesture: Boolean = false,
        attempt: Int = 1
    ): Boolean {
        val cb = info.checkboxNode

        if (!forceGesture) {
            if (cb != null && cb.isEnabled && cb.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            if (info.clickableTarget.isEnabled) {
                val r = Rect()
                info.clickableTarget.getBoundsInScreen(r)
                if (r.height() in 18..320 && info.clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            if (info.textNode.isEnabled && info.textNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            val rowParent = info.textNode.parent
            if (rowParent != null && rowParent.isEnabled) {
                val pr = Rect()
                rowParent.getBoundsInScreen(pr)
                if (pr.height() in 18..320 && rowParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        }

        if (attempt <= 2) {
            if (clickGestureAtNode(info.textNode, avoidScreenEdges = true)) return true
            if (cb != null && clickGestureAtNode(cb, avoidScreenEdges = false)) return true
        } else {
            if (cb != null && clickGestureAtNode(cb, avoidScreenEdges = false)) return true
            if (clickGestureAtNode(info.textNode, avoidScreenEdges = true)) return true
        }
        return false
    }

    private fun clickLiteDialogOkReliably(
        okBtn: AccessibilityNodeInfo,
        attempt: Int
    ): Boolean {
        if (attempt == 1) {
            if (clickNode(okBtn)) return true
            return clickGestureAtNode(okBtn, avoidScreenEdges = false)
        } else {
            val g = clickGestureAtNode(okBtn, avoidScreenEdges = false)
            val c = clickNode(okBtn)
            return g || c
        }
    }

    private fun clickLiteClearButtonReliably(
        clearBtn: AccessibilityNodeInfo,
        forceGesture: Boolean,
        attempt: Int
    ): Boolean {
        if (!forceGesture && attempt == 1) {
            var curr: AccessibilityNodeInfo? = clearBtn
            var depth = 0
            val rect = Rect()
            while (curr != null && depth < 3) {
                if (curr.isClickable && curr.isEnabled) {
                    curr.getBoundsInScreen(rect)
                    if (rect.height() in 20..260 && curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
                curr = curr.parent
                depth++
            }
            if (clickGestureAtNode(clearBtn, avoidScreenEdges = true)) return true
            return clickNode(clearBtn)
        } else {
            val g = clickGestureAtNode(clearBtn, avoidScreenEdges = (attempt <= 2))
            val c = clickNode(clearBtn)
            return g || c
        }
    }

    /**
     * Finds the positive "OK" button on Facebook Lite's warning dialog ("Clear profiles and settings?" CANCEL | OK)
     * in ANY language!
     * Strictly excludes checkbox row labels, file size strings ("0MB"), and the background "CLEAR" button.
     */
    private fun findLiteOkDialogButton(
        root: AccessibilityNodeInfo,
        checkableNodes: List<AccessibilityNodeInfo> = emptyList()
    ): AccessibilityNodeInfo? {
        val checkboxBounds = checkableNodes.map { cb ->
            Rect().also { cb.getBoundsInScreen(it) }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val strictOkCandidates = mutableListOf<AccessibilityNodeInfo>()
        val fallbackCandidates = mutableListOf<AccessibilityNodeInfo>()
        val dialogButtons = mutableListOf<AccessibilityNodeInfo>()
        var cancelNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && count < 400) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val lower = text.lowercase()
            val lowerDesc = desc.lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // Priority 1: Universal Android Positive Button Resource ID (works in 100% of languages!)
            if (node.isEnabled && (viewId.endsWith(":id/button1") || viewId.contains("button_positive") ||
                        viewId.endsWith(":id/confirm") || viewId.endsWith(":id/ok") || viewId.endsWith(":id/btn_ok"))) {
                return node
            }

            val isCancel = isCancelKeyword(lower) || isCancelKeyword(lowerDesc) || viewId.endsWith(":id/button2")
            if (isCancel && cancelNode == null) {
                cancelNode = node
            }

            if (!isCancel && node.isEnabled && !node.isCheckable &&
                !FILE_SIZE_REGEX.matches(text) && !isNodeOnAnyCheckboxRow(node, checkboxBounds)) {

                // Priority 2A: Strict OK / Confirm keywords (NEVER includes "clear")
                if (isStrictOkOnlyKeyword(lower) || isStrictOkOnlyKeyword(lowerDesc)) {
                    strictOkCandidates.add(node)
                } else if (checkableNodes.isEmpty() && (isOkConfirmKeyword(lower) || isOkConfirmKeyword(lowerDesc))) {
                    fallbackCandidates.add(node)
                }

                val cls = (node.className?.toString() ?: "").lowercase()
                if ((node.isClickable || node.parent?.isClickable == true || cls.contains("button")) &&
                    (text.isNotBlank() || desc.isNotBlank())) {
                    dialogButtons.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (strictOkCandidates.isNotEmpty()) {
            val rect = Rect()
            return strictOkCandidates.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.bottom * 2 + rect.right
            }
        }

        // Priority 2B: 100% Language-Independent Side-by-Side Pairing with CANCEL!
        if (cancelNode != null) {
            val cancelRect = Rect()
            cancelNode.getBoundsInScreen(cancelRect)
            if (cancelRect.width() > 0 && cancelRect.height() > 0) {
                val rect = Rect()
                val pairedRightBtn = dialogButtons.filter { btn ->
                    btn.getBoundsInScreen(rect)
                    rect.width() > 0 && rect.height() > 0 &&
                            abs(rect.centerY() - cancelRect.centerY()) < 60 &&
                            rect.centerX() > cancelRect.centerX() + 35
                }.maxByOrNull {
                    it.getBoundsInScreen(rect)
                    rect.right
                }
                if (pairedRightBtn != null) {
                    return pairedRightBtn
                }
            }
        }

        // Priority 2C: Any two side-by-side dialog buttons on the same Y row -> pick the right-hand button (OK)
        val r1 = Rect()
        val r2 = Rect()
        for (i in 0 until dialogButtons.size) {
            dialogButtons[i].getBoundsInScreen(r1)
            for (j in i + 1 until dialogButtons.size) {
                dialogButtons[j].getBoundsInScreen(r2)
                if (abs(r1.centerY() - r2.centerY()) < 45 && abs(r1.centerX() - r2.centerX()) > 60) {
                    return if (r1.centerX() > r2.centerX()) dialogButtons[i] else dialogButtons[j]
                }
            }
        }

        if (fallbackCandidates.isNotEmpty()) {
            val rect = Rect()
            return fallbackCandidates.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.bottom
            }
        }

        if (checkableNodes.isEmpty() && dialogButtons.isNotEmpty()) {
            val rect = Rect()
            return dialogButtons.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.bottom * 2 + rect.left
            }
        }

        return null
    }

    private fun isCancelKeyword(lower: String): Boolean {
        if (lower.isEmpty()) return false
        return lower == "cancel" || lower == "বাতিল" || lower == "না" || lower == "no" ||
                lower == "cancelar" || lower == "annuler" || lower == "abbrechen" || lower == "annulla" ||
                lower == "إلغاء" || lower == "الغاء" || lower == "لا" || lower == "отмена" ||
                lower == "нет" || lower == "iptal" || lower == "hayır" || lower == "batal" ||
                lower == "tidak" || lower == "hủy" || lower == "không" || lower == "ยกเลิก" ||
                lower == "रद्द करें" || lower == "नहीं" || lower == "منسوخ کریں" || lower == "لغو" ||
                lower == "取消" || lower == "キャンセル" || lower == "취소" || lower == "anuluj" ||
                lower == "anulare" || lower == "annuleren" || lower == "zrušit" || lower == "mégse"
    }

    /**
     * Matches strictly dialog positive confirmation words ("OK", "Confirm", "Yes") in 60+ languages,
     * WITHOUT matching "Clear", so it never mistakes Facebook Lite's main "CLEAR" button for a dialog "OK" button!
     */
    private fun isStrictOkOnlyKeyword(lower: String): Boolean {
        if (lower.isEmpty()) return false
        return lower == "ok" || lower == "okay" || lower == "oke" || lower == "confirm" || lower == "yes" ||
                lower == "ঠিক আছে" || lower == "হ্যাঁ" || lower == "নিশ্চিত করুন" || lower == "ওকে" ||
                lower == "ठीक है" || lower == "हाँ" || lower == "पुष्टि करें" ||
                lower == "ٹھیک ہے" || lower == "ہاں" || lower == "بله" || lower == "تایید" || lower == "باشه" ||
                lower == "aceptar" || lower == "sí" || lower == "confirmar" ||
                lower == "d'accord" || lower == "oui" || lower == "confirmer" ||
                lower == "ja" || lower == "bestätigen" ||
                lower == "va bene" || lower == "conferma" ||
                lower == "موافق" || lower == "نعم" || lower == "حسنا" || lower == "حسناً" || lower == "تأكيد" ||
                lower == "tamam" || lower == "evet" || lower == "onayla" ||
                lower == "ок" || lower == "да" || lower == "подтвердить" ||
                lower == "ya" || lower == "setuju" ||
                lower == "đồng ý" || lower == "xác nhận" || lower == "có" ||
                lower == "ตกลง" || lower == "ยืนยัน" || lower == "ใช่" ||
                lower == "确定" || lower == "确认" || lower == "好" || lower == "好的" || lower == "確定" ||
                lower == "はい" || lower == "確認" ||
                lower == "확인" || lower == "예" ||
                lower == "sim" || lower == "tak" || lower == "potwierdź" || lower == "da"
    }

    private fun isOkConfirmKeyword(lower: String): Boolean {
        if (lower.isEmpty()) return false
        return isStrictOkOnlyKeyword(lower) ||
                lower == "delete" || lower == "clear" ||
                lower == "মুছুন" || lower == "ডিলিট" || lower == "ক্লিয়ার" ||
                lower == "हटाएं" || lower == "साफ़ करें" || lower == "मिटाएं" ||
                lower == "eliminar" || lower == "borrar" ||
                lower == "effacer" || lower == "supprimer" ||
                lower == "löschen" || lower == "elimina" ||
                lower == "حذف" || lower == "مسح" ||
                lower == "sil" || lower == "temizle" ||
                lower == "удалить" || lower == "очистить" ||
                lower == "hapus" || lower == "xóa" || lower == "ลบ" ||
                lower == "删除" || lower == "削除" || lower == "消去" ||
                lower == "삭제" || lower == "excluir" || lower == "apagar" || lower == "limpar" ||
                lower == "usuń" || lower == "șterge" || lower == "wissen"
    }

    /**
     * Finds the positive confirmation button in Android Settings / OEM AlertDialogs & BottomSheets
     * across Samsung One UI, Xiaomi MIUI/HyperOS, Oppo ColorOS, Realme UI, OnePlus OxygenOS, Vivo FuntouchOS,
     * Transsion HiOS/XOS, Huawei/Honor, and Stock Android 10–17.
     */
    private fun findOkOrDeleteConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val bottomSheetClearCandidates = mutableListOf<AccessibilityNodeInfo>()
        val dialogButtons = mutableListOf<AccessibilityNodeInfo>()
        var hasCancelOrDialogStructure = false

        while (queue.isNotEmpty() && count < 400) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim().lowercase()
            val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // Never confuse the Storage screen's main buttons with AlertDialog's android:id/button1!
            val isStorageScreenMainButton = viewId == "com.android.settings:id/button1" ||
                    viewId == "com.samsung.android.settings:id/button1" ||
                    viewId == "com.google.android.settings:id/button1" ||
                    viewId == "com.oplus.settings:id/button1" ||
                    viewId == "com.android.settings:id/action_button1" ||
                    viewId == "com.android.settings:id/left_button" ||
                    viewId == "com.android.settings:id/button2" ||
                    viewId == "com.samsung.android.settings:id/button2" ||
                    viewId == "com.google.android.settings:id/button2" ||
                    viewId == "com.oplus.settings:id/button2" ||
                    viewId == "com.android.settings:id/action_button2" ||
                    viewId == "com.android.settings:id/right_button" ||
                    viewId.contains("clear_data_button") ||
                    viewId.contains("clear_cache_button") ||
                    viewId.contains("force_stop_button")

            val isDialogNegativeId = viewId == "android:id/button2" ||
                    viewId.contains("button_negative") ||
                    viewId.contains("btn_cancel")

            if (isDialogNegativeId || viewId.endsWith(":id/alerttitle") ||
                viewId.endsWith(":id/buttonpanel") || isCancelKeyword(text) || isCancelKeyword(desc)) {
                hasCancelOrDialogStructure = true
            }

            val isCancel = isCancelKeyword(text) || isCancelKeyword(desc) || isDialogNegativeId
            if (!isCancel && !isStorageScreenMainButton && node.isEnabled) {
                // Priority 1: Universal Android Dialog Positive Button Resource ID
                if (viewId == "android:id/button1" || viewId.contains("button_positive") ||
                    viewId.contains("btn_confirm") || viewId.contains("btn_ok") ||
                    viewId.contains("button_ok") || viewId.contains("alert_dialog_button")) {
                    return node
                }

                if (isOkConfirmKeyword(text) || isOkConfirmKeyword(desc)) {
                    candidates.add(node)
                } else if (text == "clear data" || text == "delete data" || text == "delete app data" ||
                    text == "clear all data" || text == "clear storage" ||
                    text == "সব ডেটা মুছুন" || text == "ডাটা মুছুন" || text == "ডেটা মুছুন" ||
                    text == "डेटा साफ़ करें" || text == "सभी डेटा साफ़ करें") {
                    bottomSheetClearCandidates.add(node)
                }

                val cls = (node.className?.toString() ?: "").lowercase()
                if ((node.isClickable || node.parent?.isClickable == true) &&
                    (cls.contains("button") || cls.contains("textview")) && text.isNotBlank()) {
                    dialogButtons.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // Only match text-based "Delete" / "OK" / "Clear" candidates when an actual dialog or Cancel button is on screen
        if (hasCancelOrDialogStructure && candidates.isNotEmpty()) {
            return candidates.first()
        }

        // Also allow strict "OK" candidates even if Cancel is rendered as an icon/custom view
        val strictOk = candidates.firstOrNull {
            val t = (it.text?.toString() ?: "").trim().lowercase()
            isStrictOkOnlyKeyword(t)
        }
        if (strictOk != null) {
            return strictOk
        }

        // Oppo ColorOS / Realme UI / OnePlus OxygenOS / Vivo bottom-sheet confirmation button ("Clear data" / "Delete data" above "Cancel")
        if (hasCancelOrDialogStructure && bottomSheetClearCandidates.isNotEmpty()) {
            return bottomSheetClearCandidates.first()
        }

        // Geometric right-most button fallback ONLY when an actual dialog with Cancel is on screen
        if (hasCancelOrDialogStructure && dialogButtons.isNotEmpty()) {
            val rect = Rect()
            return dialogButtons.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.left
            }
        }

        return null
    }

    /**
     * Finds the center blue "CLEAR" button on Facebook Lite's internal storage screen across ALL 100+ languages!
     * 1. Matches 60+ language keywords for "CLEAR".
     * 2. 100% Language-Independent Geometric Fallback:
     *    Finds the clickable non-checkbox button located strictly BELOW the last checkbox row on screen.
     */
    private fun findLiteClearButton(
        root: AccessibilityNodeInfo,
        sortedCheckables: List<AccessibilityNodeInfo>,
        accountsRow: AccountsRowInfo? = null
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val exactClearMatches = mutableListOf<AccessibilityNodeInfo>()
        val bottomButtonCandidates = mutableListOf<AccessibilityNodeInfo>()

        val lastCheckboxBottomY = if (sortedCheckables.isNotEmpty()) {
            val r = Rect()
            sortedCheckables.last().getBoundsInScreen(r)
            r.bottom
        } else {
            400
        }

        while (queue.isNotEmpty() && count < 450) {
            val node = queue.removeFirst()
            count++

            if (node == accountsRow?.checkboxNode || node == accountsRow?.clickableTarget || node == accountsRow?.textNode) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                continue
            }

            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val lower = text.lowercase()
            val lowerDesc = desc.lowercase()

            if (isCancelKeyword(lower) || isCancelKeyword(lowerDesc) ||
                isStrictOkOnlyKeyword(lower) || isStrictOkOnlyKeyword(lowerDesc) ||
                FILE_SIZE_REGEX.matches(text)) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                continue
            }

            val isExactClear = lower == "clear" ||
                    lower == "মুছুন" ||
                    lower == "ক্লিয়ার" ||
                    lower == "পরিষ্কার করুন" ||
                    lower == "साफ़ करें" ||
                    lower == "मिटाएं" ||
                    lower == "हटाएं" ||
                    lower == "صاف کریں" ||
                    lower == "پاک کردن" ||
                    lower == "borrar" ||
                    lower == "limpiar" ||
                    lower == "eliminar" ||
                    lower == "limpar" ||
                    lower == "apagar" ||
                    lower == "effacer" ||
                    lower == "vider" ||
                    lower == "supprimer" ||
                    lower == "löschen" ||
                    lower == "leeren" ||
                    lower == "pulisci" ||
                    lower == "cancella" ||
                    lower == "очистить" ||
                    lower == "удалить" ||
                    lower == "стереть" ||
                    lower == "مسح" ||
                    lower == "حذف" ||
                    lower == "temizle" ||
                    lower == "sil" ||
                    lower == "hapus" ||
                    lower == "bersihkan" ||
                    lower == "xóa" ||
                    lower == "dọn dẹp" ||
                    lower == "ลบ" ||
                    lower == "ล้าง" ||
                    lower == "清除" ||
                    lower == "清理" ||
                    lower == "消去" ||
                    lower == "クリア" ||
                    lower == "지우기" ||
                    lower == "삭제" ||
                    lower == "wyczyść" ||
                    lower == "șterge" ||
                    lower == "curăță" ||
                    lower == "wissen" ||
                    lower == "futa" ||
                    lowerDesc == "clear" ||
                    lowerDesc == "borrar" ||
                    lowerDesc == "limpar" ||
                    lowerDesc == "effacer" ||
                    lowerDesc == "hapus" ||
                    lowerDesc == "xóa" ||
                    lowerDesc == "مسح"

            if (isExactClear && node.isEnabled && !node.isCheckable) {
                exactClearMatches.add(node)
            }

            // Collect clickable buttons located strictly below the last checkbox row for 100% language-independent fallback
            val cls = (node.className?.toString() ?: "").lowercase()
            val isButtonLike = node.isClickable || cls.contains("button") ||
                    (cls.contains("textview") && node.parent?.isClickable == true)
            if (node.isEnabled && !node.isCheckable && isButtonLike) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 60 && rect.height() > 28 &&
                    rect.top >= lastCheckboxBottomY + 5 &&
                    rect.centerY() >= lastCheckboxBottomY + 35) {
                    bottomButtonCandidates.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (exactClearMatches.isNotEmpty()) {
            val rect = Rect()
            val belowCheckboxes = exactClearMatches.filter {
                it.getBoundsInScreen(rect)
                rect.centerY() >= lastCheckboxBottomY
            }
            return belowCheckboxes.firstOrNull { it.isClickable }
                ?: belowCheckboxes.firstOrNull()
                ?: exactClearMatches.first()
        }

        if (bottomButtonCandidates.isNotEmpty()) {
            val rect = Rect()
            return bottomButtonCandidates.minByOrNull {
                it.getBoundsInScreen(rect)
                rect.top
            }
        }

        return null
    }

    /**
     * Zero-Extra-Tap Post-Clean Completion:
     * Immediately stops all automation ticks and executes the user's chosen Post-Clean Action:
     * - "HOME_SCREEN" (Default): Single `performGlobalAction(GLOBAL_ACTION_HOME)` directly to Home Screen!
     * - "AUTO_OPEN_APP": Directly launches the cleaned target app via PackageManager LaunchIntent!
     * Never fires blind repeated BACK taps!
     */
    private fun autoCloseCleanedSequence() {
        if (!isAutomating) return
        isAutomating = false
        stopActiveRunner()
        mainHandler.removeCallbacksAndMessages(null)

        val cleanedPkg = targetPackage
        val nameToShow = targetAppName
        val wasLiteMode = isTargetLiteMode

        targetPackage = null
        step = 0
        liteStep = LITE_STEP_IDLE
        hasClickedLiteClearBtn = false
        returnedFromLiteForCache = false
        isTargetLiteMode = false
        isFacebookTarget = false

        executeZeroExtraTapExit(
            cleanedPkg = cleanedPkg,
            wasLiteMode = wasLiteMode,
            toastMessage = "✓ $nameToShow Clear Data & Cache সম্পন্ন!"
        )
    }

    private fun finishAndCloseSettings(message: String) {
        if (!isAutomating) return
        isAutomating = false
        stopActiveRunner()
        mainHandler.removeCallbacksAndMessages(null)

        val cleanedPkg = targetPackage
        targetPackage = null
        step = 0
        liteStep = LITE_STEP_IDLE
        hasClickedLiteClearBtn = false
        returnedFromLiteForCache = false
        isTargetLiteMode = false
        isFacebookTarget = false

        executeZeroExtraTapExit(
            cleanedPkg = cleanedPkg,
            wasLiteMode = false,
            toastMessage = message,
            forceHomeOnly = true
        )
    }

    private fun executeZeroExtraTapExit(
        cleanedPkg: String?,
        wasLiteMode: Boolean,
        toastMessage: String,
        forceHomeOnly: Boolean = false
    ) {
        if (!wasLiteMode) {
            cleanedPkg?.let { pkg ->
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.killBackgroundProcesses(pkg)
                } catch (_: Exception) {}
            }
        }

        val postAction = OverlayStateManager.getPostCleanAction()
        val shouldAutoOpenApp = !forceHomeOnly &&
                postAction.equals("AUTO_OPEN_APP", ignoreCase = true) &&
                !cleanedPkg.isNullOrBlank()

        Toast.makeText(applicationContext, toastMessage, Toast.LENGTH_SHORT).show()

        if (shouldAutoOpenApp && cleanedPkg != null) {
            // Go to Home first so Settings is cleanly dismissed, then immediately launch the cleaned app
            performGlobalAction(GLOBAL_ACTION_HOME)
            mainHandler.postDelayed({
                try {
                    val launchIntent = applicationContext.packageManager
                        .getLaunchIntentForPackage(cleanedPkg)
                        ?.apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            )
                        }
                    if (launchIntent != null) {
                        applicationContext.startActivity(launchIntent)
                    }
                } catch (_: Exception) {}
            }, 240L)
        } else {
            // Single direct jump to Home Screen — ZERO extra taps!
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun findNodeByKeywords(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        resourceIds: List<String> = emptyList()
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 500) {
            val node = queue.removeFirst()
            count++

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            for (rid in resourceIds) {
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
        var depth = 0
        val rect = Rect()
        while (curr != null && depth < 5) {
            if (curr.isClickable && curr.isEnabled) {
                curr.getBoundsInScreen(rect)
                if (depth == 0 || (rect.height() in 16..380)) {
                    if (curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
            }
            curr = curr.parent
            depth++
        }
        node.getBoundsInScreen(rect)
        if (node.isEnabled && rect.height() in 16..300) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    /**
     * Dispatches a precision hardware-like touch tap directly at the center coordinates of the target node.
     * When `avoidScreenEdges` is true, clamps the X coordinate away from the left/right 180px edges
     * so floating chat heads or edge icons are never tapped.
     */
    private fun clickGestureAtNode(
        node: AccessibilityNodeInfo,
        avoidScreenEdges: Boolean = true
    ): Boolean {
        return try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() <= 0) return false

            val screenWidth = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(720f)
            var cx = rect.exactCenterX()
            val cy = rect.exactCenterY()

            if (avoidScreenEdges) {
                val safeMinX = 185f
                val safeMaxX = screenWidth - 185f
                if (rect.right > safeMinX && rect.left < safeMaxX) {
                    cx = cx.coerceIn(
                        maxOf(rect.left + 12f, safeMinX),
                        minOf(rect.right - 12f, safeMaxX)
                    )
                }
            }

            if (cx > 0f && cy > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val path = Path().apply { moveTo(cx, cy) }
                val stroke = GestureDescription.StrokeDescription(path, 0L, 55L)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Clicks the node via standard Accessibility ACTION_CLICK and, if that returns false,
     * dispatches a real touch gesture at the node's safe center coordinates (Android 10–17 universal).
     */
    private fun clickNodeOrGesture(node: AccessibilityNodeInfo): Boolean {
        if (clickNode(node)) return true
        return clickGestureAtNode(node, avoidScreenEdges = true)
    }
}
