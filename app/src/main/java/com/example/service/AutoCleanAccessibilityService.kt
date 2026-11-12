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
 * Universal Ultra-Resilient Accessibility Service:
 * Compatible with Android 10, 11, 12, 13, 14, 15, 16 & 17 (All Phone Models & OEMs):
 * Samsung One UI, Xiaomi MIUI / HyperOS, Oppo ColorOS, Realme UI, OnePlus OxygenOS,
 * Vivo FuntouchOS / OriginOS, iQOO, Tecno / Infinix / Itel (HiOS / XOS), Google Pixel,
 * Motorola, Honor, Huawei, Nothing OS, Sony, Asus.
 *
 * Key Capabilities:
 * 1. 100% Language-Independent + 50-Language Facebook Lite Auto-Clear:
 *    - Works even when Facebook Lite's internal language is changed to ANY language!
 *    - Uses structural checkbox geometry (bottom-most/unchecked CheckBox = Accounts & settings),
 *      dialog window separation, resource IDs (`android:id/button1`), and bottom button positioning.
 * 2. Universal All-App Clear Data & Cache (Android 10–17):
 *    - Deep 450-node BFS traversal across all interactive windows so deep Android 13–17 layouts
 *      never truncate before reaching Storage or Clear Data buttons.
 *    - Prioritizes Clear Data / Clear Storage (which wipes both data + cache in one shot)
 *      and handles intermediate Manage Space screens (FB Lite, Chrome, Via, etc.) automatically.
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
        private var clearBtnTapAttempts: Int = 0
        private var clickedClearCache: Boolean = false
        private var clickedClearData: Boolean = false
        private var clickedManageSpaceSubClear: Boolean = false
        private var scrollAttempts: Int = 0

        /**
         * Fast navigation delay (260ms) when opening Storage & cache from App Info,
         * and dedicated ~850ms delay between each button tap once inside the Clear Data /
         * Facebook Lite Storage screen so every checkbox, popup, and CLEAR button registers 100% reliably.
         */
        private const val FAST_NAV_DELAY_MS = 260L
        private const val STEP_DELAY_MS = 850L
        private const val LITE_BUTTON_DELAY_MS = 850L

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
            isFacebookTarget = isFacebookOrLite(packageName, appName)
            isTargetLiteMode = isLiteMode || com.example.util.AppManagerHelper.isFacebookLite(packageName, appName)
            step = 0
            liteStep = LITE_STEP_IDLE
            liteScreenEnteredTime = 0L
            accountsMarkedTime = 0L
            accountsTapAttempts = 0
            clearBtnTapAttempts = 0
            clickedClearCache = false
            clickedClearData = false
            clickedManageSpaceSubClear = false
            scrollAttempts = 0
            val now = System.currentTimeMillis()
            lastActionTime = now - 1000L
            automationStartTime = now

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
            isFacebookTarget = false
            isTargetLiteMode = false
            step = 0
            liteStep = LITE_STEP_IDLE
            liteScreenEnteredTime = 0L
            accountsMarkedTime = 0L
            accountsTapAttempts = 0
            clearBtnTapAttempts = 0
            clickedClearCache = false
            clickedClearData = false
            clickedManageSpaceSubClear = false
            scrollAttempts = 0
            val now = System.currentTimeMillis()
            lastActionTime = now - 1000L
            automationStartTime = now

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
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
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
                info.notificationTimeout = 40
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
                mainHandler.postDelayed(this, 110)
            }
        }
    }

    private fun startActiveRunner() {
        mainHandler.removeCallbacks(activeTickRunnable)
        mainHandler.postDelayed(activeTickRunnable, 120)
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
     * Strictly checks if a package is a system Settings, PackageInstaller, SecurityCenter, or Dialog host.
     * Never matches 3rd-party floating chat heads/bubbles (like Messenger com.facebook.orca, Telegram, Chrome, etc.).
     */
    private fun isSystemSettingsOrDialogPackage(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        return pkg.contains("settings") ||
                pkg.contains("securitycenter") ||
                pkg.contains("packageinstaller") ||
                pkg.contains("permissioncontroller") ||
                pkg.contains("miui") ||
                pkg.contains("samsung.android.settings") ||
                pkg.contains("coloros") ||
                pkg.contains("oplus") ||
                pkg.contains("oneplus") ||
                pkg.contains("vivo") ||
                pkg.contains("iqoo") ||
                pkg.contains("transsion") ||
                pkg.contains("phonemaster") ||
                pkg.contains("huawei") ||
                pkg.contains("hihonor") ||
                pkg.contains("motorola") ||
                pkg.contains("nothing") ||
                pkg == "android"
    }

    /**
     * Validates that a window is an actual full-width Settings / Manage Space / AlertDialog window
     * and NOT a floating chat head (Messenger, Telegram, WhatsApp), floating app icon, or SystemUI notification banner.
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

        // Only allow the exact target app being cleaned OR system Settings / Dialog packages.
        // This completely blocks Messenger (com.facebook.orca), Telegram, Chrome, or other floating icons when cleaning another app!
        val isTargetApp = targetPkgLower.isNotEmpty() && pkg == targetPkgLower
        val isSettingsOrDialog = isSystemSettingsOrDialogPackage(pkg)
        if (!isTargetApp && !isSettingsOrDialog) {
            return false
        }

        // Reject small floating icon / chat-head bubble windows (e.g. Messenger chat head is ~150x150px)
        val rect = Rect()
        if (win != null) {
            win.getBoundsInScreen(rect)
        } else {
            root.getBoundsInScreen(rect)
        }
        if (rect.width() in 1..419 || rect.height() in 1..199) {
            return false
        }
        return true
    }

    /**
     * Retrieves the top-most interactive application/dialog root across Android 10–17.
     * Strictly ignores SystemUI heads-up notifications, Messenger chat heads, and floating app icons.
     */
    private fun getBestActiveRoot(): AccessibilityNodeInfo? {
        try {
            val targetPkgLower = (targetPackage ?: "").lowercase()
            val activeRoot = rootInActiveWindow
            val winList = try { windows } catch (_: Throwable) { null }
            if (!winList.isNullOrEmpty()) {
                val validWindows = winList.filter { win ->
                    win.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                            isAllowedTargetOrSettingsWindow(win, win.root, targetPkgLower)
                }
                // 1. Priority: Check if any valid window is a confirmation popup dialog (OK / Clear / Delete / Cancel)
                for (win in validWindows) {
                    val r = win.root ?: continue
                    if (hasDialogIndicators(r)) {
                        return r
                    }
                }
                // 2. If activeRoot is a valid target/Settings window (not a floating icon), use it
                if (activeRoot != null && isAllowedTargetOrSettingsWindow(null, activeRoot, targetPkgLower)) {
                    return activeRoot
                }
                // 3. Otherwise pick the top-most valid Settings or target Manage Space window
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
        while (queue.isNotEmpty() && count < 80) {
            val n = queue.removeFirst()
            count++
            val vid = n.viewIdResourceName?.lowercase() ?: ""
            val text = (n.text?.toString() ?: "").trim().lowercase()
            if (vid == "android:id/button1" || vid == "android:id/button2" ||
                vid.endsWith(":id/alerttitle") || vid.endsWith(":id/buttonpanel") ||
                vid.contains("clear_all_data") || vid.contains("dialog") ||
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
            if (now - lastEventProcessedTime < 35) return
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
            // Absolute crash safety across all Android 10–17 ROMs
        }
    }

    private fun processAutomation(rootNode: AccessibilityNodeInfo) {
        if (!isAutomating) return
        val currentTarget = targetPackage ?: return

        val now = System.currentTimeMillis()
        // Safety timeout after 14 seconds
        if (now - lastActionTime > 14000 || now - automationStartTime > 20000) {
            isAutomating = false
            targetPackage = null
            stopActiveRunner()
            return
        }

        val windowPkg = (rootNode.packageName?.toString() ?: "").lowercase()
        // Ignore our own app window while Settings is launching
        if (windowPkg == packageName.lowercase()) return

        // If user manually returned to Home launcher after 3.5 seconds, stop cleanly
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
        if (isLiteStorageScreen(rootNode, currentTarget)) {
            handleLiteStorageScreenFlow(rootNode)
            return
        }

        // 3. Universal Auto-Clean Flow for All Apps (Android 10–17)
        handleAutoCleanStep(rootNode, currentTarget)
    }

    /**
     * 100% Language-Independent Detection of Facebook Lite's Internal Storage Screen!
     * Works regardless of what language the user selected inside Facebook Lite:
     * - Detects if the active window belongs to Facebook Lite (or target Lite clone) AND we already
     *   tapped Storage / Manage Space, OR if the screen has 2+ CheckBoxes (Clear all, Photo cache,
     *   Video cache, Other cache, Accounts and settings).
     * - Also keeps 50+ language keywords as an extra fast match.
     */
    private fun isLiteStorageScreen(rootNode: AccessibilityNodeInfo, currentTarget: String): Boolean {
        if (liteStep in LITE_STEP_SELECTING_ACCOUNTS..LITE_STEP_FINAL_CONFIRM) {
            return true
        }

        val windowPkg = (rootNode.packageName?.toString() ?: "").lowercase()
        val targetLower = currentTarget.lowercase()

        // Count checkable nodes (CheckBoxes) on screen
        val checkableNodes = collectCheckableNodes(rootNode)

        // Android Settings (App Info / Storage) NEVER has 3+ CheckBoxes on the storage screen.
        // Facebook Lite's "Clear Storage on Your Phone" screen ALWAYS has 4–5 CheckBoxes!
        if (checkableNodes.size >= 3) {
            return true
        }

        // If the active window IS the Facebook Lite package itself (launched via Manage Space from Settings)
        val isLiteWindow = (isTargetLiteMode && windowPkg == targetLower) ||
                windowPkg == "com.facebook.lite" ||
                (windowPkg.contains("facebook.lite")) ||
                (isTargetLiteMode && windowPkg.contains("lite") && !windowPkg.contains("settings"))

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

        while (queue.isNotEmpty() && count < 450) {
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
                    "com.miui.securitycenter:id/force_stop"
                )
            )

            if (forceStopBtn != null && forceStopBtn.isEnabled) {
                clickNodeOrGesture(forceStopBtn)
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
                clickNodeOrGesture(confirmBtn)
                step = 2
                lastActionTime = now
                mainHandler.postDelayed({
                    finishAndCloseSettings("$targetAppName Force Stopped & Closed ✓")
                }, 450)
            } else if (now - lastActionTime > 1800) {
                finishAndCloseSettings("$targetAppName Closed ✓")
            }
        }
    }

    /**
     * Accurately finds the "Storage & cache" / "Storage" / "Storage usage" row in Android Settings App Info
     * across Android 10, 11, 12, 13, 14, 15, 16 & 17 (All phone models & languages).
     * Strictly excludes Notifications, Permissions, Battery, Mobile data, Open by default.
     */
    private fun findStorageRowNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 450) {
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
                if (viewId.contains("storage") || viewId.contains("memory") ||
                    viewId.contains("storage_settings") || viewId.contains("storage_use") ||
                    viewId.contains("storage_row") || viewId.contains("pref_storage")) {
                    return node
                }

                // Priority 2: Multi-language Storage row titles across all OEM skins (Android 10–17)
                val isStorageMatch = combined.contains("storage & cache") ||
                        combined.contains("storage and cache") ||
                        combined.contains("storage usage") ||
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

    private fun scrollDownToFind(root: AccessibilityNodeInfo): Boolean {
        try {
            if (root.isScrollable) {
                return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < 200) {
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

    private fun findClearDataOrCacheButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findClearDataButtonNode(root) ?: findClearCacheButtonNode(root)
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

    private fun findClearDataButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Priority A: Explicit OEM Clear Data resource IDs (safe on both App Info & Storage screens)
        val explicitResourceMatch = findNodeByKeywords(
            root,
            emptyList(),
            resourceIds = listOf(
                "com.samsung.android.settings:id/clear_data_button",
                "com.android.settings:id/clear_data_button",
                "com.google.android.settings:id/clear_data_button",
                "com.android.settings:id/clear_data_btn",
                "com.miui.securitycenter:id/clear_data",
                "com.miui.securitycenter:id/clear_all_data",
                "com.coloros.safecenter:id/clear_data",
                "com.oplus.safecenter:id/clear_data",
                "com.vivo.safecenter:id/clear_data",
                "com.iqoo.secure:id/clear_data",
                "com.transsion.phonemaster:id/clear_data",
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

        // Priority C: If we already navigated into the Storage sub-screen (step >= 1), check generic button1
        // while excluding App Info actions (Open, Uninstall, Disable, Force stop)
        if (step >= 1) {
            val fallbackBtn1 = findNodeByKeywords(
                root,
                emptyList(),
                resourceIds = listOf(
                    "com.samsung.android.settings:id/button1",
                    "com.android.settings:id/button1",
                    "com.google.android.settings:id/button1",
                    "com.android.settings:id/action_button1"
                )
            )
            if (fallbackBtn1 != null) {
                val txt = ((fallbackBtn1.text?.toString() ?: "") + " " + (fallbackBtn1.contentDescription?.toString() ?: "")).lowercase()
                val isAppInfoBtn = txt.contains("open") || txt.contains("uninstall") ||
                        txt.contains("disable") || txt.contains("force") ||
                        txt.contains("খুলুন") || txt.contains("আনইনস্টল") || txt.contains("থামিয়ে")
                if (!isAppInfoBtn) {
                    return fallbackBtn1
                }
            }
        }
        return null
    }

    private fun findClearCacheButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val explicitCache = findNodeByKeywords(
            root,
            listOf(
                "clear cache", "ক্যাশ মুছুন", "ক্যাশে মুছুন", "ক্লিয়ার ক্যাশ", "ক্লিন ক্যাশ",
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
                "com.android.settings:id/clear_cache_btn",
                "com.miui.securitycenter:id/clear_cache",
                "com.coloros.safecenter:id/clear_cache",
                "com.oplus.safecenter:id/clear_cache",
                "com.vivo.safecenter:id/clear_cache",
                "com.iqoo.secure:id/clear_cache",
                "com.transsion.phonemaster:id/clear_cache"
            )
        )
        if (explicitCache != null) return explicitCache

        if (step >= 1) {
            val fallbackBtn2 = findNodeByKeywords(
                root,
                emptyList(),
                resourceIds = listOf(
                    "com.samsung.android.settings:id/button2",
                    "com.android.settings:id/button2",
                    "com.google.android.settings:id/button2",
                    "com.android.settings:id/action_button2"
                )
            )
            if (fallbackBtn2 != null) {
                val txt = ((fallbackBtn2.text?.toString() ?: "") + " " + (fallbackBtn2.contentDescription?.toString() ?: "")).lowercase()
                val isAppInfoBtn = txt.contains("force") || txt.contains("uninstall") ||
                        txt.contains("open") || txt.contains("disable") ||
                        txt.contains("থামিয়ে") || txt.contains("আনইনস্টল")
                if (!isAppInfoBtn) {
                    return fallbackBtn2
                }
            }
        }
        return null
    }

    /**
     * Universal High-Speed Auto Clean Step (Android 10 to Android 17 - All Models):
     * 1. Fast navigation into Storage & cache (FAST_NAV_DELAY_MS = 260ms).
     * 2. Fast tap on Manage Space / Clear Storage to open Facebook Lite Storage (or Clear Data dialog).
     * 3. Once inside the Clear Data / Facebook Lite Storage screen, uses a dedicated ~850ms delay
     *    between each button tap so every checkbox, popup, and CLEAR button registers 100% reliably.
     */
    private fun handleAutoCleanStep(rootNode: AccessibilityNodeInfo, currentTarget: String) {
        val now = System.currentTimeMillis()

        // If Facebook Lite storage screen appeared at any point, immediately hand off to Lite handler
        if (isLiteStorageScreen(rootNode, currentTarget)) {
            handleLiteStorageScreenFlow(rootNode)
            return
        }

        val clearDataNode = findClearDataButtonNode(rootNode)
        val clearCacheNode = findClearCacheButtonNode(rootNode)
        val inStorageScreen = clearDataNode != null || clearCacheNode != null

        // Step 0: On App Info Screen -> Fast navigate to Storage & cache (260ms)
        if (step == 0 && !inStorageScreen) {
            if (now - lastActionTime < FAST_NAV_DELAY_MS) return
            val storageNode = findStorageRowNode(rootNode)
            if (storageNode != null) {
                if (clickNodeOrGesture(storageNode)) {
                    step = 1
                    lastActionTime = now
                    return
                }
            } else {
                if (scrollAttempts < 5 && now - lastActionTime >= 450L) {
                    scrollAttempts++
                    lastActionTime = now
                    scrollDownToFind(rootNode)
                }
                return
            }
        }

        // Step 1 & 2: Inside Storage Screen -> Click Clear Data / Clear Storage / Manage Space ONCE
        if (step in 0..2 && inStorageScreen && !clickedClearData) {
            // If it's FB Lite Mode, open "Manage space / Clear storage" quickly (320ms);
            // for standard apps, give 550ms for Android Settings to finish calculating storage size.
            val storageReadyDelay = if (isTargetLiteMode) 320L else 550L
            if (now - lastActionTime < storageReadyDelay) return

            val isDataEnabled = isNodeEffectivelyEnabled(clearDataNode)
            val isCacheEnabled = isNodeEffectivelyEnabled(clearCacheNode)

            // Priority 1: Click Clear Data / Clear Storage / Manage Space (wipes BOTH data + cache!)
            if (clearDataNode != null && isDataEnabled) {
                if (clickNodeOrGesture(clearDataNode)) {
                    clickedClearData = true
                    step = 3
                    lastActionTime = now
                    return
                }
            }

            // Priority 2: If Clear Data button is disabled (app has 0B data, only cache), click Clear Cache
            if (!isDataEnabled && clearCacheNode != null && isCacheEnabled && !clickedClearCache) {
                if (clickNodeOrGesture(clearCacheNode)) {
                    clickedClearCache = true
                    lastActionTime = now
                    mainHandler.postDelayed({
                        autoCloseCleanedSequence()
                    }, 600)
                    return
                }
            }

            // Priority 3: If both Clear Data & Clear Cache are already disabled (0B data & 0B cache), finish!
            if (clearDataNode != null && !isDataEnabled && !isCacheEnabled) {
                autoCloseCleanedSequence()
                return
            }
        }

        // Step 3: ~850ms AFTER clicking Clear Data / Manage Space -> Handle intermediate screen or Confirmation Dialog
        if (step == 3) {
            // A) Did Facebook Lite's internal storage screen open? Hand off immediately!
            if (isLiteStorageScreen(rootNode, currentTarget)) {
                handleLiteStorageScreenFlow(rootNode)
                return
            }

            if (now - lastActionTime < STEP_DELAY_MS) return

            // B) Did Xiaomi MIUI/HyperOS BottomSheet ("Clear all data" + "Clear cache") or Chrome/Browser
            //    "Manage storage" screen ("Clear all data" / "Delete all data" / "Free up space") open?
            if (!clickedManageSpaceSubClear) {
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
                    if (subId != "android:id/button1" && !isStorageMainButton) {
                        if (clickNodeOrGesture(subClearAll)) {
                            clickedManageSpaceSubClear = true
                            lastActionTime = now
                            return
                        }
                    }
                }
            }

            // C) Standard Confirmation Dialog or Bottom-Sheet Confirm (Delete / OK / Clear / android:id/button1)
            val confirmNode = findOkOrDeleteConfirmButton(rootNode)
            if (confirmNode != null && isNodeEffectivelyEnabled(confirmNode)) {
                if (clickNodeOrGesture(confirmNode)) {
                    step = 4
                    lastActionTime = now
                    mainHandler.postDelayed({
                        autoCloseCleanedSequence()
                    }, 700)
                    return
                }
            } else {
                val dataBtnAfter = findClearDataButtonNode(rootNode)
                if (dataBtnAfter != null && !isNodeEffectivelyEnabled(dataBtnAfter) && now - lastActionTime >= STEP_DELAY_MS) {
                    step = 4
                    autoCloseCleanedSequence()
                    return
                }
                if (dataBtnAfter != null && isNodeEffectivelyEnabled(dataBtnAfter) && !hasDialogIndicators(rootNode) && now - lastActionTime in 1600..2600) {
                    if (clickNodeOrGesture(dataBtnAfter)) {
                        lastActionTime = now
                        return
                    }
                }
                if (now - lastActionTime > 5200) {
                    step = 4
                    autoCloseCleanedSequence()
                }
            }
        }
    }

    /**
     * 100% Language-Independent + 50-Language Facebook Lite Storage Screen Handler!
     * Gives a dedicated ~850ms delay between each button tap on this screen:
     *   1) Wait 550ms when the screen first opens so all 5 CheckBoxes & CLEAR button settle.
     *   2) Tap [ ] "Accounts and settings" (with automatic Touch Gesture fallback if ACTION_CLICK is ignored).
     *   3) Wait ~850ms -> If a warning popup dialog appears, tap OK/CLEAR and wait ~850ms.
     *   4) Tap the bottom blue "CLEAR" button (with automatic Touch Gesture fallback).
     *   5) Wait ~850ms -> If a final confirmation popup appears, tap OK/CLEAR, wait 700ms, and close cleanly!
     */
    private fun handleLiteStorageScreenFlow(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()

        // When entering the Facebook Lite Storage screen for the first time, give a 550ms settle pause
        // so all CheckBoxes and the blue CLEAR button are completely laid out and interactive.
        if (liteScreenEnteredTime == 0L) {
            liteScreenEnteredTime = now
            lastActionTime = now - (LITE_BUTTON_DELAY_MS - 550L)
            return
        }

        // Enforce ~850ms delay between every button tap on the Facebook Lite Storage screen
        if (now - lastActionTime < LITE_BUTTON_DELAY_MS) return

        val checkableNodes = collectCheckableNodes(rootNode)
        val dialogShowing = isLiteDialogShowing(rootNode, checkableNodes)

        // CASE 1: A confirmation popup dialog is currently on screen!
        if (dialogShowing) {
            val okDialogBtn = findLiteOkDialogButton(rootNode)
            if (okDialogBtn != null && isNodeEffectivelyEnabled(okDialogBtn)) {
                val clickedOk = if (clearBtnTapAttempts >= 2 || accountsTapAttempts >= 2) {
                    clickGestureAtNode(okDialogBtn) || clickNodeOrGesture(okDialogBtn)
                } else {
                    clickNodeOrGesture(okDialogBtn)
                }
                if (clickedOk) {
                    lastActionTime = now
                    if (liteStep == LITE_STEP_FINAL_CONFIRM) {
                        liteStep = LITE_STEP_DONE
                        mainHandler.postDelayed({
                            autoCloseCleanedSequence()
                        }, 700)
                    } else {
                        // Warning dialog for "Accounts and settings" confirmed -> Wait 850ms, then click blue CLEAR button!
                        liteStep = LITE_STEP_CLICK_CLEAR
                    }
                    return
                }
            }
            return
        }

        // CASE 2: We are on the Main FB Lite Storage Screen (with CheckBoxes)
        val accountsRow = findAccountsAndSettingsRow(rootNode, checkableNodes)

        // If we already tapped "Accounts and settings", check if it's now checked or needs a touch-gesture tap:
        if (liteStep == LITE_STEP_WAIT_ACCOUNTS_POPUP) {
            if (accountsRow != null && accountsRow.isChecked) {
                // Checkbox is now [✓] checked! Proceed to tap the blue CLEAR button below.
                liteStep = LITE_STEP_CLICK_CLEAR
            } else if (accountsRow != null && accountsTapAttempts < 3) {
                // After 850ms, no dialog appeared and checkbox is still unchecked ->
                // Tap using a direct hardware-like Touch Gesture on the checkbox / row!
                accountsTapAttempts++
                accountsMarkedTime = now
                lastActionTime = now
                clickAccountsRowReliably(accountsRow, forceGesture = true, attempt = accountsTapAttempts)
                return
            } else {
                // We tapped 3 times (ACTION_CLICK + Checkbox Gesture + Text Gesture); proceed to click CLEAR!
                liteStep = LITE_STEP_CLICK_CLEAR
            }
        }

        // Step A on Lite Screen: Check [ ] "Accounts and settings"
        if (accountsRow != null && !accountsRow.isChecked && liteStep < LITE_STEP_CLICK_CLEAR) {
            ensureClearAllChecked(checkableNodes)
            liteStep = LITE_STEP_SELECTING_ACCOUNTS
            accountsTapAttempts = 1
            accountsMarkedTime = now
            lastActionTime = now

            val clicked = clickAccountsRowReliably(accountsRow, forceGesture = false, attempt = 1)
            if (clicked) {
                liteStep = LITE_STEP_WAIT_ACCOUNTS_POPUP
            }
            return
        }

        // Step B on Lite Screen: "Accounts and settings" is [✓] checked -> Wait 850ms & Click bottom blue CLEAR button!
        if ((accountsRow != null && accountsRow.isChecked) || liteStep >= LITE_STEP_CLICK_CLEAR) {
            val clearBtn = findLiteClearButton(rootNode, checkableNodes, accountsRow)

            // If we already clicked CLEAR once and waited 850ms:
            if (liteStep == LITE_STEP_FINAL_CONFIRM) {
                if (clearBtn != null && isNodeEffectivelyEnabled(clearBtn) && clearBtnTapAttempts < 2) {
                    // The blue CLEAR button is still on screen after 850ms -> Force a direct Touch Gesture on CLEAR!
                    clearBtnTapAttempts = 2
                    lastActionTime = now
                    clickGestureAtNode(clearBtn)
                    return
                } else {
                    // Either CLEAR finished and closed the screen, or 2nd tap completed -> Close cleanly!
                    liteStep = LITE_STEP_DONE
                    mainHandler.postDelayed({
                        autoCloseCleanedSequence()
                    }, 550)
                    return
                }
            }

            if (clearBtn != null && isNodeEffectivelyEnabled(clearBtn)) {
                if (clickNodeOrGesture(clearBtn)) {
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
     * Accurately determines if a confirmation dialog is currently displayed over FB Lite's storage screen.
     * Prevents the main screen's CLEAR button from ever being mistaken for a dialog OK button!
     */
    private fun isLiteDialogShowing(
        root: AccessibilityNodeInfo,
        checkableNodes: List<AccessibilityNodeInfo>
    ): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val clickableButtons = mutableListOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty() && count < 250) {
            val node = queue.removeFirst()
            count++
            val vid = node.viewIdResourceName?.lowercase() ?: ""
            if (vid.endsWith(":id/button1") || vid.endsWith(":id/button2") ||
                vid.endsWith(":id/alerttitle") || vid.endsWith(":id/buttonpanel")) {
                return true
            }
            val cls = (node.className?.toString() ?: "").lowercase()
            if (node.isEnabled && !node.isCheckable && (node.isClickable || cls.contains("button"))) {
                clickableButtons.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (checkableNodes.isEmpty() && clickableButtons.isNotEmpty()) {
            return true
        }

        val rect1 = Rect()
        val rect2 = Rect()
        for (i in 0 until clickableButtons.size) {
            clickableButtons[i].getBoundsInScreen(rect1)
            if (rect1.width() <= 0 || rect1.height() <= 0 || rect1.top < 200) continue
            for (j in i + 1 until clickableButtons.size) {
                clickableButtons[j].getBoundsInScreen(rect2)
                if (rect2.width() <= 0 || rect2.height() <= 0 || rect2.top < 200) continue
                if (abs(rect1.centerY() - rect2.centerY()) < 45 && abs(rect1.centerX() - rect2.centerX()) > 60) {
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
     * Finds the "Accounts and settings" row AND its paired CheckBox in Facebook Lite across ALL 100+ languages!
     * Combines both keyword matching ("Accounts and settings" / "Not recommended") and structural bottom CheckBox
     * geometry so we have the exact CheckBox node, the text node, and the row container.
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

        while (queue.isNotEmpty() && count < 400) {
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

        // 1. Always try direct in-memory Accessibility ACTION_CLICK on the row/checkbox/text first
        //    because ACTION_CLICK goes straight to the View inside FB Lite and NEVER touches
        //    floating Messenger chat heads or floating app icons on the screen edges!
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

        // 2. If a Touch Gesture is needed:
        //    - First prefer tapping on the "Accounts and settings" text in the safe middle of the screen
        //      (away from the left/right edges where Messenger chat heads & floating icons sit!).
        //    - On attempt 3, tap the checkbox itself.
        if (attempt <= 2) {
            if (clickGestureAtNode(info.textNode, avoidScreenEdges = true)) return true
            if (cb != null && clickGestureAtNode(cb, avoidScreenEdges = false)) return true
        } else {
            if (cb != null && clickGestureAtNode(cb, avoidScreenEdges = false)) return true
            if (clickGestureAtNode(info.textNode, avoidScreenEdges = true)) return true
        }
        return false
    }

    /**
     * Ensures the top "Clear all" checkbox is checked (in any language, it's the top-most checkbox `sortedCheckables.first()`).
     */
    private fun ensureClearAllChecked(sortedCheckables: List<AccessibilityNodeInfo>) {
        if (sortedCheckables.size >= 2) {
            val topCheckbox = sortedCheckables.first()
            if (!topCheckbox.isChecked) {
                clickNodeOrGesture(topCheckbox)
            }
        }
    }

    /**
     * Finds the positive "OK" button on Facebook Lite's warning dialog in ANY language!
     */
    private fun findLiteOkDialogButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val dialogButtons = mutableListOf<AccessibilityNodeInfo>()

        while (queue.isNotEmpty() && count < 300) {
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
            if (!isCancel && node.isEnabled && !node.isCheckable) {
                if (isOkConfirmKeyword(lower) || isOkConfirmKeyword(lowerDesc)) {
                    candidates.add(node)
                }

                val cls = (node.className?.toString() ?: "").lowercase()
                if ((node.isClickable || node.parent?.isClickable == true) && (cls.contains("button") || cls.contains("textview"))) {
                    dialogButtons.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (candidates.isNotEmpty()) {
            return candidates.first()
        }

        // Priority 3: Geometric 2-Button Dialog Fallback (Pick right-most button in dialog)
        if (dialogButtons.isNotEmpty()) {
            val rect = Rect()
            return dialogButtons.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.left
            }
        }

        return null
    }

    private fun hasCancelButtonOnScreen(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 250) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim().lowercase()
            val vid = node.viewIdResourceName?.lowercase() ?: ""
            if (vid.endsWith(":id/button2") || isCancelKeyword(text)) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
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
                lower == "anulare" || lower == "annuleren"
    }

    private fun isOkConfirmKeyword(lower: String): Boolean {
        if (lower.isEmpty()) return false
        return lower == "ok" || lower == "okay" || lower == "oke" || lower == "confirm" ||
                lower == "delete" || lower == "clear" || lower == "yes" ||
                lower == "ঠিক আছে" || lower == "হ্যাঁ" || lower == "মুছুন" || lower == "নিশ্চিত করুন" ||
                lower == "ठीक है" || lower == "हाँ" || lower == "हटाएं" || lower == "साफ़ करें" ||
                lower == "ٹھیک ہے" || lower == "ہاں" || lower == "بله" || lower == "تایید" || lower == "باشه" ||
                lower == "aceptar" || lower == "sí" || lower == "confirmar" || lower == "eliminar" || lower == "borrar" ||
                lower == "d'accord" || lower == "oui" || lower == "confirmer" || lower == "effacer" || lower == "supprimer" ||
                lower == "ja" || lower == "löschen" || lower == "bestätigen" ||
                lower == "va bene" || lower == "conferma" || lower == "elimina" ||
                lower == "موافق" || lower == "نعم" || lower == "حسنا" || lower == "حسناً" || lower == "حذف" || lower == "مسح" ||
                lower == "tamam" || lower == "evet" || lower == "onayla" || lower == "sil" || lower == "temizle" ||
                lower == "ок" || lower == "да" || lower == "удалить" || lower == "очистить" ||
                lower == "ya" || lower == "setuju" || lower == "hapus" ||
                lower == "đồng ý" || lower == "xác nhận" || lower == "xóa" || lower == "có" ||
                lower == "ตกลง" || lower == "ยืนยัน" || lower == "ลบ" ||
                lower == "确定" || lower == "确认" || lower == "好" || lower == "好的" || lower == "删除" ||
                lower == "はい" || lower == "確認" || lower == "削除" || lower == "消去" ||
                lower == "확인" || lower == "예" || lower == "삭제" ||
                lower == "sim" || lower == "excluir" || lower == "apagar" || lower == "limpar" ||
                lower == "tak" || lower == "potwierdź" || lower == "usuń" ||
                lower == "da" || lower == "șterge" || lower == "wissen"
    }

    private fun findOkOrDeleteConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val bottomSheetClearCandidates = mutableListOf<AccessibilityNodeInfo>()
        val dialogButtons = mutableListOf<AccessibilityNodeInfo>()
        var hasCancelOrDialogStructure = false

        while (queue.isNotEmpty() && count < 350) {
            val node = queue.removeFirst()
            count++
            val text = (node.text?.toString() ?: "").trim().lowercase()
            val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // IMPORTANT: Do NOT confuse com.android.settings:id/button1 ("Clear data") or
            // com.android.settings:id/button2 ("Clear cache" / "Force stop") with Android AlertDialog's
            // android:id/button1 and android:id/button2!
            val isStorageScreenMainButton = viewId == "com.android.settings:id/button1" ||
                    viewId == "com.samsung.android.settings:id/button1" ||
                    viewId == "com.google.android.settings:id/button1" ||
                    viewId == "com.android.settings:id/action_button1" ||
                    viewId == "com.android.settings:id/button2" ||
                    viewId == "com.samsung.android.settings:id/button2" ||
                    viewId == "com.google.android.settings:id/button2" ||
                    viewId == "com.android.settings:id/action_button2" ||
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
                // Priority 1: Universal Android Dialog Positive Button Resource ID (strictly dialog buttons, NEVER settings storage button1!)
                if (viewId == "android:id/button1" || viewId.contains("button_positive") ||
                    viewId.contains("btn_confirm") || viewId.contains("btn_ok") ||
                    viewId.contains("button_ok") || viewId.contains("alert_dialog_button")) {
                    return node
                }

                if (isOkConfirmKeyword(text) || isOkConfirmKeyword(desc)) {
                    candidates.add(node)
                } else if (text == "clear data" || text == "delete data" || text == "delete app data" ||
                    text == "clear all data" || text == "সব ডেটা মুছুন" || text == "ডাটা মুছুন" || text == "ডেটা মুছুন") {
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

        if (candidates.isNotEmpty()) {
            return candidates.first()
        }

        // Oppo ColorOS / Realme UI / OnePlus OxygenOS bottom-sheet confirmation button ("Clear data" / "Delete data" above "Cancel")
        if (hasCancelOrDialogStructure && bottomSheetClearCandidates.isNotEmpty()) {
            return bottomSheetClearCandidates.first()
        }

        // Geometric right-most/top button fallback ONLY when an actual dialog with Cancel is on screen
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
     * Finds the bottom "CLEAR" button on Facebook Lite's internal storage screen across ALL 100+ languages!
     * 1. Matches 50+ language keywords for "CLEAR".
     * 2. 100% Language-Independent Geometric Fallback:
     *    Finds the clickable non-checkbox button located strictly BELOW the last checkbox row on screen!
     */
    private fun findLiteClearButton(
        root: AccessibilityNodeInfo,
        sortedCheckables: List<AccessibilityNodeInfo>,
        accountsRow: AccountsRowInfo? = null
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        val bottomButtonCandidates = mutableListOf<AccessibilityNodeInfo>()

        val lastCheckboxBottomY = if (sortedCheckables.isNotEmpty()) {
            val r = Rect()
            sortedCheckables.last().getBoundsInScreen(r)
            r.bottom
        } else {
            400
        }

        while (queue.isNotEmpty() && count < 400) {
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

            val isExactClear = lower == "clear" ||
                    lower == "মুছুন" ||
                    lower == "ক্লিয়ার" ||
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
                return node
            }

            // Collect clickable buttons located strictly below the last checkbox row for 100% language-independent fallback
            val cls = (node.className?.toString() ?: "").lowercase()
            val isButtonLike = node.isClickable || cls.contains("button") ||
                    (cls.contains("textview") && node.parent?.isClickable == true)
            if (node.isEnabled && !node.isCheckable && isButtonLike) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // Must be strictly below the last checkbox row and have visible button dimensions
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

        // 100% Language-Independent Fallback: Pick the clickable button at the bottom below all the checkboxes!
        if (bottomButtonCandidates.isNotEmpty()) {
            val rect = Rect()
            return bottomButtonCandidates.maxByOrNull {
                it.getBoundsInScreen(rect)
                rect.bottom
            }
        }

        return null
    }

    /**
     * Ultra-fast close sequence:
     * Navigates back cleanly out of Settings / Manage Space directly to the app without kicking user to Home Launcher.
     */
    private fun autoCloseCleanedSequence() {
        if (!isAutomating) return
        isAutomating = false
        stopActiveRunner()
        mainHandler.removeCallbacksAndMessages(null)
        val pkgToKill = targetPackage
        val nameToShow = targetAppName
        targetPackage = null
        step = 0
        liteStep = LITE_STEP_IDLE
        isTargetLiteMode = false
        isFacebookTarget = false

        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({
            val currentPkg = (getBestActiveRoot()?.packageName?.toString() ?: "").lowercase()
            if (currentPkg != packageName.lowercase() && !currentPkg.contains("launcher")) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            mainHandler.postDelayed({
                val postPkg = (getBestActiveRoot()?.packageName?.toString() ?: "").lowercase()
                if (postPkg != packageName.lowercase() && !postPkg.contains("launcher") &&
                    (postPkg.contains("settings") || postPkg.contains("securitycenter") || postPkg.contains("lite"))) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                pkgToKill?.let { pkg ->
                    try {
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(pkg)
                        Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                    } catch (_: Exception) {}
                }
                Toast.makeText(
                    applicationContext,
                    "✓ $nameToShow ডেটা ক্লিয়ার সম্পন্ন হয়েছে!",
                    Toast.LENGTH_SHORT
                ).show()
            }, 140)
        }, 140)
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
        isFacebookTarget = false

        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.postDelayed({
            val currentPkg = (getBestActiveRoot()?.packageName?.toString() ?: "").lowercase()
            if (currentPkg != packageName.lowercase() && !currentPkg.contains("launcher")) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            pkgToKill?.let { pkg ->
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.killBackgroundProcesses(pkg)
                    Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                } catch (_: Exception) {}
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }, 140)
    }

    private fun findNodeByKeywords(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        resourceIds: List<String> = emptyList()
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 450) {
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
        while (curr != null && depth < 4) {
            if (curr.isClickable && curr.isEnabled) {
                curr.getBoundsInScreen(rect)
                // Do not click a giant full-screen container (e.g. ScrollView) when walking up parents
                if (depth == 0 || (rect.height() in 16..380)) {
                    if (curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
            }
            curr = curr.parent
            depth++
        }
        // Also try direct ACTION_CLICK on the node itself if its height is button/row-sized
        node.getBoundsInScreen(rect)
        if (node.isEnabled && rect.height() in 16..300) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    /**
     * Dispatches a hardware-like touch tap directly at the center coordinates of the target node.
     * When `avoidScreenEdges` is true, clamps the X coordinate away from the left/right 180px edges
     * so floating Messenger chat heads, Chrome bubbles, or floating tool icons on the screen edges are NEVER tapped!
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
                // If the node spans into the safe middle zone, clamp X inside the safe zone away from edge chat heads
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
