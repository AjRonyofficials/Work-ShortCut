package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import com.example.util.Gender
import com.example.util.NameGenerator
import com.example.util.ProxyTester
import com.example.util.TotpHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class BubbleSize(val title: String, val dpSize: Int) {
    SMALL("Small (44dp)", 44),
    MEDIUM("Medium (56dp)", 56),
    LARGE("Large (68dp)", 68)
}

enum class AppThemeMode(val title: String) {
    DARK("Dark Modern"),
    LIGHT("Light Clean"),
    EYE_FRIENDLY("Eye Friendly (Warm)"),
    AMOLED("AMOLED Pitch Black")
}

data class ExcelDraftRow(
    val values: Map<String, String> = mapOf("A" to "", "B" to "", "C" to "", "D" to "", "E" to "", "F" to ""),
    val duplicateColumn: String? = null,
    val duplicateConflictWith: String? = null,
    val duplicateValue: String? = null
)

data class CustomAppShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val appName: String,
    val packageName: String,
    val colorHex: String = "#0288D1"
)

data class ProxyConnectionState(
    val isConnected: Boolean = false,
    val isTesting: Boolean = false,
    val profileName: String = "Primary Proxy",
    val protocol: String = "SOCKS5",
    val host: String = "104.244.72.115",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val ipAddress: String = "104.244.72.115",
    val ipVersion: String = "IPv4",
    val countryCode: String = "US",
    val countryName: String = "United States",
    val city: String = "",
    val isp: String = "",
    val pingMs: Long = 42,
    val statusText: String = "Disconnected",
    val connectedDurationSeconds: Long = 0,
    val allowedApps: List<String> = emptyList()
)

data class OverlayUiState(
    val isOverlayActive: Boolean = false,
    val isOverlayExpanded: Boolean = false,
    val bubbleSize: BubbleSize = BubbleSize.MEDIUM,
    val appTheme: AppThemeMode = AppThemeMode.DARK,
    val selectedCountry: String = "BD",
    val selectedGender: Gender = Gender.ANY,
    val columnCount: Int = 6,
    val columnRowMap: Map<String, Int> = mapOf("A" to 1, "B" to 1, "C" to 1, "D" to 1, "E" to 1, "F" to 1),
    val currentSheetRowIndex: Int = 1,
    val draftRow: ExcelDraftRow = ExcelDraftRow(),
    val duplicateHighlightRow: Long? = null,
    val duplicateHighlightCol: String? = null,
    val duplicateText: String? = null,
    val twoFactorKey: String = "JBSWY3DPEHPK3PXP", // standard demo key
    val savedPasswordText: String = "Pass@123456",
    val savedPasswordSlots: List<String> = listOf("Pass@123456", "Secure@2026#", "MyKey#99"),
    val totpResult: TotpHelper.TotpResult? = null,
    val proxyState: ProxyConnectionState = ProxyConnectionState(),
    val lowPowerMode: Boolean = false,
    val pingOptimization: Boolean = true,
    val autoReconnect: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val lastGeneratedName: String = "",
    val isRandomPasswordMode: Boolean = false,
    val randomPwLength: Int = 12,
    val randomPwIncludeSymbols: Boolean = true,
    val currentRandomPassword: String = "",
    val selectedClearDataApps: List<com.example.util.AppInfoItem> = emptyList(),
    val isClearDataOverlayExpanded: Boolean = false,
    val backgroundDataCaching: Boolean = true,
    val isDockedLeft: Boolean = true,
    val isEdgeBarMinimized: Boolean = false,
    val customAppShortcuts: List<CustomAppShortcut> = emptyList(),
    val showOverlayProxy: Boolean = true,
    val showOverlayName: Boolean = true,
    val showOverlayExcel: Boolean = true,
    val showOverlay2Fa: Boolean = true,
    val showOverlayPwCopy: Boolean = true,
    val showOverlayApps: Boolean = true,
    val showOverlayClean: Boolean = true,
    val showOverlayVirtualNumbers: Boolean = true,
    val isVirtualNumbersOverlayExpanded: Boolean = false
)

object OverlayStateManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var totpTickerJob: Job? = null
    private var pingTickerJob: Job? = null
    private var prefs: SharedPreferences? = null
    private var repository: com.example.data.local.WorkShortcutRepository? = null

    fun setRepository(repo: com.example.data.local.WorkShortcutRepository) {
        repository = repo
    }

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private val _alertEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val alertEvents: SharedFlow<String> = _alertEvents.asSharedFlow()

    private val _requestedAppTab = MutableStateFlow<String?>(null)
    val requestedAppTab: StateFlow<String?> = _requestedAppTab.asStateFlow()

    fun requestTabNavigation(tabName: String) {
        _requestedAppTab.value = tabName
    }

    fun requestTab(tabName: String) {
        _requestedAppTab.value = tabName
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("work_shortcut_prefs", Context.MODE_PRIVATE)

        if (repository == null) {
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            repository = com.example.data.local.WorkShortcutRepository(
                excelRowDao = db.excelRowDao(),
                proxyProfileDao = db.proxyProfileDao(),
                twoFactorDao = db.twoFactorDao()
            )
        }

        prefs?.let { p ->
            val country = p.getString("selected_country", "BD") ?: "BD"
            val genderName = p.getString("selected_gender", Gender.ANY.name) ?: Gender.ANY.name
            val colCount = p.getInt("column_count", 6)
            val savedRowIndex = p.getInt("current_sheet_row_index", 1)
            val bubbleSizeName = p.getString("bubble_size", BubbleSize.MEDIUM.name) ?: BubbleSize.MEDIUM.name
            val themeName = p.getString("app_theme", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
            val lowPower = p.getBoolean("low_power", false)
            val pingOpt = p.getBoolean("ping_opt", true)
            val autoReconn = p.getBoolean("auto_reconn", true)
            val vibEnabled = p.getBoolean("vib_enabled", true)
            val saved2faKey = p.getString("saved_2fa_key", "JBSWY3DPEHPK3PXP") ?: "JBSWY3DPEHPK3PXP"
            val savedPwText = p.getString("saved_password_text", "Pass@123456") ?: "Pass@123456"
            val proxyHost = p.getString("proxy_host", "127.0.0.1") ?: "127.0.0.1"
            val proxyPort = p.getInt("proxy_port", 1080)
            val proxyProtocol = p.getString("proxy_protocol", "SOCKS5") ?: "SOCKS5"
            val proxyCountry = p.getString("proxy_country", "US") ?: "US"

            val initialDraft = ExcelDraftRow(
                values = (0 until colCount).associate { ('A' + it).toString() to "" }
            )

            val savedAppsString = p.getString("clear_data_apps_list", null)
            val loadedApps = if (!savedAppsString.isNullOrEmpty()) {
                savedAppsString.split(";;").mapNotNull { entry ->
                    val parts = entry.split("::")
                    if (parts.size >= 2) {
                        val appName = parts[0]
                        val pkg = parts[1]
                        val isLite = if (parts.size >= 3) {
                            parts[2].toBoolean()
                        } else {
                            com.example.util.AppManagerHelper.isLiteOrModdedApp(pkg, appName)
                        }
                        com.example.util.AppInfoItem(
                            appName = appName,
                            packageName = pkg,
                            isSelected = true,
                            isLiteStorageApp = isLite
                        )
                    } else null
                }
            } else {
                listOf(
                    com.example.util.AppInfoItem("Chrome", "com.android.chrome", true, isLiteStorageApp = false),
                    com.example.util.AppInfoItem("Facebook Lite", "com.facebook.lite", true, isLiteStorageApp = true),
                    com.example.util.AppInfoItem("Facebook", "com.facebook.katana", true, isLiteStorageApp = false),
                    com.example.util.AppInfoItem("Instagram", "com.instagram.android", true, isLiteStorageApp = false)
                )
            }

            val bgDataCaching = p.getBoolean("bg_data_caching", true)
            val profileName = p.getString("proxy_profile_name", "Primary Proxy") ?: "Primary Proxy"
            val proxyPassword = p.getString("proxy_password", "") ?: ""
            val proxyAllowedApps = p.getStringSet("proxy_allowed_apps", emptySet())?.toList() ?: emptyList()
            val isRandomPwMode = p.getBoolean("is_random_pw_mode", false)
            val randomPwLen = p.getInt("random_pw_length", 12)
            val randomPwSymbols = p.getBoolean("random_pw_symbols", true)
            val initialRandomPw = generateStrongPassword(randomPwLen, randomPwSymbols)

            val showProxy = p.getBoolean("show_overlay_proxy", true)
            val showName = p.getBoolean("show_overlay_name", true)
            val showExcel = p.getBoolean("show_overlay_excel", true)
            val show2Fa = p.getBoolean("show_overlay_2fa", true)
            val showPw = p.getBoolean("show_overlay_pw", true)
            val showApps = p.getBoolean("show_overlay_apps", true)
            val showClean = p.getBoolean("show_overlay_clean", true)
            val showVirtual = p.getBoolean("show_overlay_virtual", true)

            val savedShortcutsString = p.getString("custom_app_shortcuts", null)
            val loadedShortcuts = if (savedShortcutsString != null) {
                if (savedShortcutsString.isNotEmpty()) {
                    savedShortcutsString.split(";;").mapNotNull { entry ->
                        val parts = entry.split("::")
                        if (parts.size >= 4) {
                            CustomAppShortcut(id = parts[0], appName = parts[1], packageName = parts[2], colorHex = parts[3])
                        } else if (parts.size >= 3) {
                            CustomAppShortcut(id = parts[0], appName = parts[1], packageName = parts[2])
                        } else null
                    }
                } else {
                    emptyList()
                }
            } else {
                listOf(
                    CustomAppShortcut(appName = "FB", packageName = "com.facebook.katana", colorHex = "#1877F2"),
                    CustomAppShortcut(appName = "Via", packageName = "mark.via.gp", colorHex = "#4CAF50")
                )
            }

            val rowA = p.getInt("sheet_row_A", 1)
            val rowB = p.getInt("sheet_row_B", 1)
            val rowC = p.getInt("sheet_row_C", 1)
            val rowD = p.getInt("sheet_row_D", 1)
            val rowE = p.getInt("sheet_row_E", 1)
            val rowF = p.getInt("sheet_row_F", 1)
            val colRowMap = mapOf("A" to rowA, "B" to rowB, "C" to rowC, "D" to rowD, "E" to rowE, "F" to rowF)

            _uiState.update {
                it.copy(
                    selectedCountry = country,
                    selectedGender = try { Gender.valueOf(genderName) } catch (_: Exception) { Gender.ANY },
                    columnCount = colCount,
                    columnRowMap = colRowMap,
                    currentSheetRowIndex = savedRowIndex,
                    bubbleSize = try { BubbleSize.valueOf(bubbleSizeName) } catch (_: Exception) { BubbleSize.MEDIUM },
                    appTheme = try { AppThemeMode.valueOf(themeName) } catch (_: Exception) { AppThemeMode.DARK },
                    lowPowerMode = lowPower,
                    pingOptimization = pingOpt,
                    autoReconnect = autoReconn,
                    vibrationEnabled = vibEnabled,
                    twoFactorKey = saved2faKey,
                    savedPasswordText = savedPwText,
                    isRandomPasswordMode = isRandomPwMode,
                    randomPwLength = randomPwLen,
                    randomPwIncludeSymbols = randomPwSymbols,
                    currentRandomPassword = initialRandomPw,
                    draftRow = initialDraft,
                    selectedClearDataApps = loadedApps,
                    backgroundDataCaching = bgDataCaching,
                    customAppShortcuts = loadedShortcuts,
                    showOverlayProxy = showProxy,
                    showOverlayName = showName,
                    showOverlayExcel = showExcel,
                    showOverlay2Fa = show2Fa,
                    showOverlayPwCopy = showPw,
                    showOverlayApps = showApps,
                    showOverlayClean = showClean,
                    showOverlayVirtualNumbers = showVirtual,
                    proxyState = it.proxyState.copy(
                        profileName = profileName,
                        host = proxyHost,
                        port = proxyPort,
                        protocol = proxyProtocol,
                        countryCode = proxyCountry,
                        password = proxyPassword,
                        allowedApps = proxyAllowedApps
                    )
                )
            }
        }

        startTotpTicker()
        startPeriodicPingTester()
        com.example.worker.BatteryEfficientProxyWorker.schedule(context)
        com.example.worker.AutomatedCacheCleanerWorker.schedule(context)
    }

    fun generateStrongPassword(length: Int, includeSymbols: Boolean): String {
        val uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lowercase = "abcdefghijkmnopqrstuvwxyz"
        val numbers = "23456789"
        val symbols = "!@#$%&*?"

        val safeLength = length.coerceIn(8, 16)
        val charPool = if (includeSymbols) uppercase + lowercase + numbers + symbols else uppercase + lowercase + numbers

        val sb = java.lang.StringBuilder()
        // Guarantee at least one of each class
        sb.append(uppercase[kotlin.random.Random.nextInt(uppercase.length)])
        sb.append(lowercase[kotlin.random.Random.nextInt(lowercase.length)])
        sb.append(numbers[kotlin.random.Random.nextInt(numbers.length)])
        if (includeSymbols) {
            sb.append(symbols[kotlin.random.Random.nextInt(symbols.length)])
        }

        while (sb.length < safeLength) {
            sb.append(charPool[kotlin.random.Random.nextInt(charPool.length)])
        }

        // Shuffle characters
        val list = sb.toString().toList().shuffled()
        return list.joinToString("")
    }

    fun setOverlaySectionVisibility(section: String, visible: Boolean) {
        _uiState.update {
            when (section.uppercase()) {
                "PROXY" -> it.copy(showOverlayProxy = visible)
                "NAME" -> it.copy(showOverlayName = visible)
                "EXCEL" -> it.copy(showOverlayExcel = visible)
                "2FA" -> it.copy(showOverlay2Fa = visible)
                "PW" -> it.copy(showOverlayPwCopy = visible)
                "APPS" -> it.copy(showOverlayApps = visible)
                "CLEAN" -> it.copy(showOverlayClean = visible)
                "VIRTUAL" -> it.copy(showOverlayVirtualNumbers = visible)
                else -> it
            }
        }
        val prefKey = when (section.uppercase()) {
            "PROXY" -> "show_overlay_proxy"
            "NAME" -> "show_overlay_name"
            "EXCEL" -> "show_overlay_excel"
            "2FA" -> "show_overlay_2fa"
            "PW" -> "show_overlay_pw"
            "APPS" -> "show_overlay_apps"
            "CLEAN" -> "show_overlay_clean"
            "VIRTUAL" -> "show_overlay_virtual"
            else -> null
        }
        prefKey?.let { prefs?.edit()?.putBoolean(it, visible)?.apply() }
    }

    fun toggleVirtualNumbersOverlay() {
        _uiState.update { it.copy(isVirtualNumbersOverlayExpanded = !it.isVirtualNumbersOverlayExpanded) }
    }

    fun setRandomPasswordMode(enabled: Boolean) {
        _uiState.update { it.copy(isRandomPasswordMode = enabled) }
        prefs?.edit()?.putBoolean("is_random_pw_mode", enabled)?.apply()
    }

    fun setRandomPasswordLength(length: Int) {
        val safeLen = length.coerceIn(8, 16)
        val newPw = generateStrongPassword(safeLen, _uiState.value.randomPwIncludeSymbols)
        _uiState.update { it.copy(randomPwLength = safeLen, currentRandomPassword = newPw) }
        prefs?.edit()?.putInt("random_pw_length", safeLen)?.apply()
    }

    fun setRandomPasswordSymbols(include: Boolean) {
        val newPw = generateStrongPassword(_uiState.value.randomPwLength, include)
        _uiState.update { it.copy(randomPwIncludeSymbols = include, currentRandomPassword = newPw) }
        prefs?.edit()?.putBoolean("random_pw_symbols", include)?.apply()
    }

    fun regenerateRandomPassword(): String {
        val pw = generateStrongPassword(_uiState.value.randomPwLength, _uiState.value.randomPwIncludeSymbols)
        _uiState.update { it.copy(currentRandomPassword = pw) }
        return pw
    }

    fun copyRandomPasswordToClipboard(context: Context): String {
        val state = _uiState.value
        val pwToCopy = if (state.currentRandomPassword.isNotBlank()) state.currentRandomPassword
        else generateStrongPassword(state.randomPwLength, state.randomPwIncludeSymbols)

        com.example.util.ClipboardHelper.copyToClipboard(context, pwToCopy, "Random Password")
        VibrationHelper.vibrateSuccess(context)
        Toast.makeText(context, "✓ Copied ($pwToCopy) & New PW Generated!", Toast.LENGTH_SHORT).show()

        // Generate immediate next password ready for next tap!
        val nextPw = generateStrongPassword(state.randomPwLength, state.randomPwIncludeSymbols)
        _uiState.update { it.copy(currentRandomPassword = nextPw) }
        return pwToCopy
    }

    fun setSavedPasswordText(context: Context, newPassword: String) {
        val clean = newPassword.trim()
        _uiState.update { it.copy(savedPasswordText = clean) }
        prefs?.edit()?.putString("saved_password_text", clean)?.apply()
        VibrationHelper.vibrateSuccess(context)
        Toast.makeText(context, "✓ Password সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    fun copySavedPasswordToClipboard(context: Context, customText: String? = null) {
        if (_uiState.value.isRandomPasswordMode && customText == null) {
            copyRandomPasswordToClipboard(context)
            return
        }

        val textToCopy = customText ?: _uiState.value.savedPasswordText
        if (textToCopy.isNotEmpty()) {
            com.example.util.ClipboardHelper.copyToClipboard(context, textToCopy, "Saved Password")
            VibrationHelper.vibrateSuccess(context)
            val preview = if (textToCopy.length > 5) "${textToCopy.take(3)}***" else "****"
            Toast.makeText(context, "✓ PW Copied ($preview)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "কোন পাসওয়ার্ড সেভ করা নেই!", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleBackgroundDataCaching(context: Context? = null) {
        val current = _uiState.value.backgroundDataCaching
        val updated = !current
        _uiState.update { it.copy(backgroundDataCaching = updated) }
        prefs?.edit()?.putBoolean("bg_data_caching", updated)?.apply()
        context?.let { ctx ->
            if (updated) {
                com.example.worker.BatteryEfficientProxyWorker.schedule(ctx)
                com.example.worker.AutomatedCacheCleanerWorker.schedule(ctx)
                Toast.makeText(ctx, "Background caching enabled", Toast.LENGTH_SHORT).show()
            } else {
                com.example.worker.BatteryEfficientProxyWorker.cancel(ctx)
                Toast.makeText(ctx, "Battery savings mode: background caching off", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setOverlayActive(active: Boolean) {
        _uiState.update { it.copy(isOverlayActive = active) }
    }

    fun setOverlayExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isOverlayExpanded = expanded) }
    }

    fun toggleOverlayExpanded() {
        _uiState.update { it.copy(isOverlayExpanded = !it.isOverlayExpanded) }
    }

    fun setSelectedCountry(countryCode: String) {
        _uiState.update { it.copy(selectedCountry = countryCode) }
        prefs?.edit()?.putString("selected_country", countryCode)?.apply()
    }

    fun setSelectedGender(gender: Gender) {
        _uiState.update { it.copy(selectedGender = gender) }
        prefs?.edit()?.putString("selected_gender", gender.name)?.apply()
    }

    fun setBubbleSize(size: BubbleSize) {
        _uiState.update { it.copy(bubbleSize = size) }
        prefs?.edit()?.putString("bubble_size", size.name)?.apply()
    }

    fun setAppTheme(theme: AppThemeMode) {
        _uiState.update { it.copy(appTheme = theme) }
        prefs?.edit()?.putString("app_theme", theme.name)?.apply()
    }

    fun setLowPowerMode(enabled: Boolean) {
        _uiState.update { it.copy(lowPowerMode = enabled) }
        prefs?.edit()?.putBoolean("low_power", enabled)?.apply()
        // Restart ping tester with new intervals
        startPeriodicPingTester()
    }

    fun setPingOptimization(enabled: Boolean) {
        _uiState.update { it.copy(pingOptimization = enabled) }
        prefs?.edit()?.putBoolean("ping_opt", enabled)?.apply()
    }

    fun setAutoReconnect(enabled: Boolean) {
        _uiState.update { it.copy(autoReconnect = enabled) }
        prefs?.edit()?.putBoolean("auto_reconn", enabled)?.apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
        prefs?.edit()?.putBoolean("vib_enabled", enabled)?.apply()
    }

    fun toggleClearDataApp(item: com.example.util.AppInfoItem, isSelected: Boolean, isLiteMode: Boolean? = null) {
        val current = _uiState.value.selectedClearDataApps.toMutableList()
        val effectiveIsLite = isLiteMode ?: item.isLiteStorageApp ?: com.example.util.AppManagerHelper.isLiteOrModdedApp(item.packageName, item.appName)
        if (isSelected) {
            val existingIndex = current.indexOfFirst { it.packageName == item.packageName }
            if (existingIndex >= 0) {
                current[existingIndex] = current[existingIndex].copy(isSelected = true, isLiteStorageApp = effectiveIsLite)
            } else {
                current.add(item.copy(isSelected = true, isLiteStorageApp = effectiveIsLite))
            }
        } else {
            current.removeAll { it.packageName == item.packageName }
        }
        _uiState.update { it.copy(selectedClearDataApps = current) }
        val serialized = current.joinToString(";;") { "${it.appName}::${it.packageName}::${it.isLiteStorageApp}" }
        prefs?.edit()?.putString("clear_data_apps_list", serialized)?.apply()
    }

    fun toggleAppLiteStorageMode(packageName: String, isLiteMode: Boolean) {
        val current = _uiState.value.selectedClearDataApps.map {
            if (it.packageName == packageName) it.copy(isLiteStorageApp = isLiteMode) else it
        }
        _uiState.update { it.copy(selectedClearDataApps = current) }
        val serialized = current.joinToString(";;") { "${it.appName}::${it.packageName}::${it.isLiteStorageApp}" }
        prefs?.edit()?.putString("clear_data_apps_list", serialized)?.apply()
    }

    fun toggleClearDataOverlayExpanded() {
        _uiState.update { it.copy(isClearDataOverlayExpanded = !it.isClearDataOverlayExpanded) }
    }

    fun setClearDataOverlayExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isClearDataOverlayExpanded = expanded) }
    }

    fun executeClearDataForApp(context: Context, item: com.example.util.AppInfoItem) {
        VibrationHelper.vibrateTactileClick(context)
        Toast.makeText(context, "🧹 ${item.appName} Clear Data & Cache শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
        com.example.util.AppManagerHelper.openAppDetailsForClearData(
            context = context,
            packageName = item.packageName,
            appName = item.appName,
            isLiteStorageMode = item.isLiteStorageApp
        )
    }

    fun executeSelfClearData(context: Context) {
        com.example.util.AppManagerHelper.clearSelfCache(context)
        clearDraftRow()
        com.example.util.ClipboardHelper.copyToClipboard(context, "", "Clean", "Work Shortcut cache & history cleared!")
    }

    fun setColumnCount(count: Int) {
        val safeCount = count.coerceIn(2, 6)
        val currentDraft = _uiState.value.draftRow.values.toMutableMap()
        val newValues = (0 until safeCount).associate { index ->
            val colKey = ('A' + index).toString()
            colKey to (currentDraft[colKey] ?: "")
        }
        _uiState.update {
            it.copy(
                columnCount = safeCount,
                draftRow = it.draftRow.copy(values = newValues)
            )
        }
        prefs?.edit()?.putInt("column_count", safeCount)?.apply()
    }

    /**
     * Feature 1: Generate Fake Name & copy to clipboard
     */
    fun generateAndCopyName(context: Context): String {
        val state = _uiState.value
        val name = NameGenerator.generateName(state.selectedCountry, state.selectedGender)
        _uiState.update { it.copy(lastGeneratedName = name) }
        com.example.util.ClipboardHelper.copyToClipboard(
            context = context,
            text = name,
            label = "Name ${state.selectedCountry}",
            toastMessage = "Copied name: $name (${state.selectedCountry})"
        )
        return name
    }

    fun generateAndCopyRealtimeName(context: Context): String = generateAndCopyName(context)

    fun toggleDockSide() {
        val current = _uiState.value.isDockedLeft
        _uiState.update { it.copy(isDockedLeft = !current) }
        prefs?.edit()?.putBoolean("docked_left", !current)?.apply()
    }

    fun toggleEdgeBarMinimized() {
        val current = _uiState.value.isEdgeBarMinimized
        _uiState.update { it.copy(isEdgeBarMinimized = !current) }
    }

    fun triggerOverlayColumnPaste(context: Context, columnKey: String) {
        val direct = com.example.util.ClipboardHelper.getFromClipboard(context)?.trim()
        if (!direct.isNullOrEmpty()) {
            executeSheetColumnPaste(context, columnKey, direct)
        } else {
            com.example.util.ClipboardReaderActivity.triggerPaste(context, columnKey)
        }
    }

    fun triggerOverlay2FaPaste(context: Context) {
        val direct = com.example.util.ClipboardHelper.getFromClipboard(context)?.trim()
        if (!direct.isNullOrEmpty()) {
            processGet2FaWithText(context, direct)
        } else {
            com.example.util.ClipboardReaderActivity.triggerPaste(context, "2FA")
        }
    }

    fun setColumnRow(columnKey: String, rowNumber: Int) {
        val safeRow = rowNumber.coerceAtLeast(1)
        val currentMap = _uiState.value.columnRowMap.toMutableMap()
        currentMap[columnKey.uppercase()] = safeRow
        _uiState.update { it.copy(columnRowMap = currentMap) }
        prefs?.edit()?.putInt("sheet_row_${columnKey.uppercase()}", safeRow)?.apply()
    }

    fun resetAllColumnRowsToOne() {
        val resetMap = mapOf("A" to 1, "B" to 1, "C" to 1, "D" to 1, "E" to 1, "F" to 1)
        _uiState.update { it.copy(columnRowMap = resetMap) }
        prefs?.edit()?.apply {
            listOf("A", "B", "C", "D", "E", "F").forEach { col ->
                putInt("sheet_row_$col", 1)
            }
        }?.apply()
    }

    fun clearDuplicateHighlight() {
        _uiState.update {
            it.copy(
                duplicateHighlightRow = null,
                duplicateHighlightCol = null,
                duplicateText = null
            )
        }
    }

    fun setCurrentSheetRow(rowNumber: Int) {
        setColumnRow("A", rowNumber)
    }

    fun incrementSheetRow() {
        val current = _uiState.value.columnRowMap["A"] ?: 1
        setColumnRow("A", current + 1)
    }

    fun decrementSheetRow() {
        val current = _uiState.value.columnRowMap["A"] ?: 1
        setColumnRow("A", (current - 1).coerceAtLeast(1))
    }

    /**
     * Executes paste for `columnKey`:
     * 1. Checks Room database if `clipText` already exists in ANY row/column.
     * 2. If DUPLICATE:
     *    - Marks red duplicate alert in UI state.
     *    - Vibrates alert.
     *    - Automatically opens/navigates to the Sheet tab in MainActivity.
     * 3. If NOT DUPLICATE:
     *    - Saves directly to Room database at row targetRow.
     *    - Auto-increments that column's row counter (e.g. A1 -> A2)!
     *    - Gives tactile feedback and fast toast.
     */
    fun executeSheetColumnPaste(context: Context, columnKey: String, rawText: String) {
        val clipText = rawText.trim()
        if (clipText.isEmpty()) {
            Toast.makeText(context, "Clipboard empty! Copy text first.", Toast.LENGTH_SHORT).show()
            return
        }

        val col = columnKey.uppercase()
        val targetRow = _uiState.value.columnRowMap[col] ?: 1

        scope.launch {
            val allRows = repository?.getAllExcelRowsList() ?: emptyList()
            var isDuplicate = false
            var conflictRowId = -1L
            var conflictCol = ""

            for (r in allRows) {
                if (r.colA.equals(clipText, ignoreCase = true) && r.colA.isNotEmpty()) {
                    isDuplicate = true; conflictRowId = r.id; conflictCol = "A"; break
                }
                if (r.colB.equals(clipText, ignoreCase = true) && r.colB.isNotEmpty()) {
                    isDuplicate = true; conflictRowId = r.id; conflictCol = "B"; break
                }
                if (r.colC.equals(clipText, ignoreCase = true) && r.colC.isNotEmpty()) {
                    isDuplicate = true; conflictRowId = r.id; conflictCol = "C"; break
                }
                if (r.colD.equals(clipText, ignoreCase = true) && r.colD.isNotEmpty()) {
                    isDuplicate = true; conflictRowId = r.id; conflictCol = "D"; break
                }
                if (r.colE.equals(clipText, ignoreCase = true) && r.colE.isNotEmpty()) {
                    isDuplicate = true; conflictRowId = r.id; conflictCol = "E"; break
                }
                if (r.colF.equals(clipText, ignoreCase = true) && r.colF.isNotEmpty()) {
                    isDuplicate = true; conflictRowId = r.id; conflictCol = "F"; break
                }
            }

            if (isDuplicate) {
                // Update duplicate highlight in state
                _uiState.update {
                    it.copy(
                        duplicateHighlightRow = conflictRowId,
                        duplicateHighlightCol = conflictCol,
                        duplicateText = clipText
                    )
                }

                if (_uiState.value.vibrationEnabled) {
                    com.example.util.VibrationHelper.vibrateDuplicateAlert(context)
                }
                Toast.makeText(context, "⚠️ Duplicate detected: \"$clipText\"! Opening Sheet...", Toast.LENGTH_LONG).show()

                // Auto-navigate to Sheet section in the app!
                requestTab("EXCEL")
                try {
                    val intent = Intent(context, com.example.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("NAVIGATE_TO_SHEET", true)
                        putExtra("HIGHLIGHT_ROW", conflictRowId)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("OverlayStateManager", "Failed to launch MainActivity: ${e.message}")
                }
            } else {
                // Save to Room DB at targetRow
                val existing = repository?.getExcelRowById(targetRow.toLong())
                val updated = if (existing != null) {
                    when (col) {
                        "A" -> existing.copy(colA = clipText, hasDuplicateWarning = false)
                        "B" -> existing.copy(colB = clipText, hasDuplicateWarning = false)
                        "C" -> existing.copy(colC = clipText, hasDuplicateWarning = false)
                        "D" -> existing.copy(colD = clipText, hasDuplicateWarning = false)
                        "E" -> existing.copy(colE = clipText, hasDuplicateWarning = false)
                        "F" -> existing.copy(colF = clipText, hasDuplicateWarning = false)
                        else -> existing.copy(colA = clipText)
                    }
                } else {
                    com.example.data.local.model.ExcelRowEntity(
                        id = targetRow.toLong(),
                        colA = if (col == "A") clipText else "",
                        colB = if (col == "B") clipText else "",
                        colC = if (col == "C") clipText else "",
                        colD = if (col == "D") clipText else "",
                        colE = if (col == "E") clipText else "",
                        colF = if (col == "F") clipText else "",
                        hasDuplicateWarning = false
                    )
                }
                repository?.insertExcelRow(updated)

                if (_uiState.value.vibrationEnabled) {
                    com.example.util.VibrationHelper.vibrateTactileClick(context)
                }
                Toast.makeText(context, "✓ $col$targetRow Pasted", Toast.LENGTH_SHORT).show()

                // AUTO-ADVANCE ROW FOR THIS COLUMN!
                setColumnRow(col, targetRow + 1)
            }
        }
    }

    fun pasteToColumnDirect(context: Context, columnKey: String, textToPaste: String): Boolean {
        executeSheetColumnPaste(context, columnKey, textToPaste)
        return true
    }

    fun fastPasteToSheetColumn(context: Context, columnKey: String) {
        triggerOverlayColumnPaste(context, columnKey)
    }

    fun copyColumnRecords(context: Context, columnKey: String, rows: List<com.example.data.local.model.ExcelRowEntity>) {
        val col = columnKey.uppercase()
        val values = rows.sortedBy { it.id }.mapNotNull { row ->
            val v = when (col) {
                "A" -> row.colA
                "B" -> row.colB
                "C" -> row.colC
                "D" -> row.colD
                "E" -> row.colE
                "F" -> row.colF
                else -> row.colA
            }.trim()
            if (v.isNotEmpty()) v else null
        }

        if (values.isEmpty()) {
            Toast.makeText(context, "Column $col is empty!", Toast.LENGTH_SHORT).show()
            return
        }

        val text = values.joinToString("\n")
        com.example.util.ClipboardHelper.copyToClipboard(
            context = context,
            text = text,
            label = "Column $col Data",
            toastMessage = "✅ Copied Column $col (${values.size} rows)"
        )
    }

    fun copyAllRowsAsCsv(context: Context, rows: List<com.example.data.local.model.ExcelRowEntity>) {
        if (rows.isEmpty()) {
            Toast.makeText(context, "No rows to export!", Toast.LENGTH_SHORT).show()
            return
        }

        val sorted = rows.sortedBy { it.id }
        val tsvLines = sorted.map { row ->
            listOf(row.colA, row.colB, row.colC, row.colD, row.colE, row.colF).joinToString("\t")
        }
        val header = "Col A\tCol B\tCol C\tCol D\tCol E\tCol F"
        val fullTsv = (listOf(header) + tsvLines).joinToString("\n")

        com.example.util.ClipboardHelper.copyToClipboard(
            context = context,
            text = fullTsv,
            label = "Sheet Data",
            toastMessage = "✅ Copied all ${rows.size} rows as Excel table!"
        )
    }

    fun autoPasteClipboardToColumn(context: Context, columnKey: String) {
        triggerOverlayColumnPaste(context, columnKey)
    }

    fun pasteToColumn(context: Context, columnKey: String, textToPaste: String): Boolean {
        executeSheetColumnPaste(context, columnKey, textToPaste)
        return true
    }

    fun setColumnValueDirectly(columnKey: String, value: String) {
        val currentValues = _uiState.value.draftRow.values.toMutableMap()
        currentValues[columnKey] = value
        _uiState.update {
            it.copy(
                draftRow = it.draftRow.copy(
                    values = currentValues,
                    duplicateColumn = null
                )
            )
        }
    }

    fun clearDraftRow() {
        val count = _uiState.value.columnCount
        val emptyValues = (0 until count).associate { ('A' + it).toString() to "" }
        _uiState.update {
            it.copy(
                draftRow = ExcelDraftRow(values = emptyValues)
            )
        }
    }

    /**
     * Copy All columns formatted as Tab-Separated Values (TSV) for direct Excel paste
     */
    fun copyAllColumns(context: Context): String {
        val values = _uiState.value.draftRow.values
        val sortedKeys = values.keys.sorted()
        val rowText = sortedKeys.joinToString(separator = "\t") { values[it] ?: "" }
        com.example.util.ClipboardHelper.copyToClipboard(
            context = context,
            text = rowText,
            label = "Excel Row TSV",
            toastMessage = "Copied all columns ($sortedKeys) to clipboard!"
        )
        return rowText
    }

    fun copySingleColumn(context: Context, columnKey: String): String {
        val value = _uiState.value.draftRow.values[columnKey] ?: ""
        com.example.util.ClipboardHelper.copyToClipboard(
            context = context,
            text = value,
            label = "Column $columnKey",
            toastMessage = "Copied Column $columnKey"
        )
        return value
    }

    /**
     * Feature 3: 2FA Secret Key Management & Generation
     */
    fun setTwoFactorKey(key: String, autoGenerateAndCopy: Context? = null) {
        val cleanKey = key.trim().replace(" ", "").replace("-", "").uppercase()
        _uiState.update { it.copy(twoFactorKey = cleanKey) }
        prefs?.edit()?.putString("saved_2fa_key", cleanKey)?.apply()
        updateTotpCode()

        if (autoGenerateAndCopy != null) {
            val code = _uiState.value.totpResult?.code
            if (code != null) {
                com.example.util.ClipboardHelper.copyToClipboard(
                    context = autoGenerateAndCopy,
                    text = code,
                    label = "2FA Code",
                    toastMessage = "Copied 2FA Code: $code"
                )
            }
        }
    }

    fun copyCurrentTotpCode(context: Context) {
        val code = _uiState.value.totpResult?.code
        if (!code.isNullOrEmpty()) {
            com.example.util.ClipboardHelper.copyToClipboard(
                context = context,
                text = code,
                label = "2FA Code",
                toastMessage = "Copied 2FA Code: $code"
            )
        } else {
            // try to generate from current key
            updateTotpCode()
            val newCode = _uiState.value.totpResult?.code
            if (!newCode.isNullOrEmpty()) {
                com.example.util.ClipboardHelper.copyToClipboard(
                    context = context,
                    text = newCode,
                    label = "2FA Code",
                    toastMessage = "Copied 2FA Code: $newCode"
                )
            }
        }
    }

    fun processGet2FaFromClipboard(context: Context) {
        triggerOverlay2FaPaste(context)
    }

    fun processGet2FaWithText(context: Context, rawClipboardText: String) {
        val extractedKey = TotpHelper.extractSecretKey(rawClipboardText)
        val targetKey = if (extractedKey.length >= 8) {
            extractedKey
        } else {
            _uiState.value.twoFactorKey
        }

        if (targetKey.isEmpty()) {
            Toast.makeText(context, "📋 Keyboard-এ 2FA Key কপি করা নেই! আগে কী কপি করুন।", Toast.LENGTH_SHORT).show()
            return
        }

        val totp = TotpHelper.generateTotp(targetKey)
        if (totp != null) {
            if (targetKey != _uiState.value.twoFactorKey) {
                _uiState.update { it.copy(twoFactorKey = targetKey, totpResult = totp) }
                prefs?.edit()?.putString("saved_2fa_key", targetKey)?.apply()
            } else {
                _uiState.update { it.copy(totpResult = totp) }
            }

            // AUTO-COPY 6-DIGIT CODE TO USER'S KEYBOARD CLIPBOARD!
            com.example.util.ClipboardHelper.copyToClipboard(
                context = context,
                text = totp.code,
                label = "2FA Code",
                toastMessage = "⚡ 2FA Code [${totp.formattedCode}] copied to keyboard!"
            )
            if (_uiState.value.vibrationEnabled) {
                com.example.util.VibrationHelper.vibrateTactileClick(context)
            }
        } else {
            Toast.makeText(context, "⚠️ Invalid 2FA secret key in keyboard!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTotpCode() {
        val key = _uiState.value.twoFactorKey
        val result = TotpHelper.generateTotp(key)
        _uiState.update { it.copy(totpResult = result) }
    }

    private fun startTotpTicker() {
        totpTickerJob?.cancel()
        totpTickerJob = scope.launch {
            while (isActive) {
                updateTotpCode()
                delay(1000)
            }
        }
    }

    /**
     * Feature 4: Super Proxy Configuration & Quick Switcher
     * Note: Proxy section does NOT copy anything to clipboard
     */
    private var proxyDurationJob: Job? = null

    fun setProxyAllowedApps(packages: List<String>) {
        _uiState.update {
            it.copy(proxyState = it.proxyState.copy(allowedApps = packages))
        }
        prefs?.edit()?.putStringSet("proxy_allowed_apps", packages.toSet())?.apply()
    }

    fun toggleProxyAllowedApp(packageName: String, shouldAllow: Boolean) {
        val current = _uiState.value.proxyState.allowedApps.toMutableList()
        if (shouldAllow) {
            if (!current.contains(packageName)) current.add(packageName)
        } else {
            current.remove(packageName)
        }
        setProxyAllowedApps(current)
    }

    fun updateSuperProxyProfile(
        profileName: String,
        server: String,
        port: Int,
        protocol: String = "SOCKS5",
        countryCode: String = "",
        username: String = "",
        password: String = ""
    ) {
        val crMatch = Regex("""(?:cr\.([a-z]{2})|country[_-]([a-z]{2}))""", RegexOption.IGNORE_CASE).find(username)
        val extractedCountry = crMatch?.groupValues?.firstOrNull { it.length == 2 && !it.equals("cr", ignoreCase = true) }?.uppercase()

        val effectiveCountry = if (countryCode.isNotBlank() && countryCode != "BD") {
            countryCode.uppercase()
        } else if (!extractedCountry.isNullOrBlank()) {
            extractedCountry
        } else if (_uiState.value.proxyState.countryCode.isNotBlank() && _uiState.value.proxyState.countryCode != "BD") {
            _uiState.value.proxyState.countryCode
        } else {
            "US"
        }

        val countryOpt = com.example.util.NameGenerator.getCountryOption(effectiveCountry)

        _uiState.update {
            it.copy(
                proxyState = it.proxyState.copy(
                    profileName = profileName,
                    host = server,
                    port = port,
                    ipAddress = server,
                    protocol = protocol,
                    countryCode = effectiveCountry,
                    countryName = countryOpt.name,
                    username = username,
                    password = password
                )
            )
        }
        prefs?.edit()
            ?.putString("proxy_profile_name", profileName)
            ?.putString("proxy_host", server)
            ?.putInt("proxy_port", port)
            ?.putString("proxy_protocol", protocol)
            ?.putString("proxy_country", effectiveCountry)
            ?.putString("proxy_username", username)
            ?.putString("proxy_password", password)
            ?.apply()
    }

    fun updateProxyConfig(
        host: String,
        port: Int,
        protocol: String,
        countryCode: String,
        username: String = ""
    ) {
        _uiState.update {
            it.copy(
                proxyState = it.proxyState.copy(
                    host = host,
                    port = port,
                    ipAddress = host,
                    protocol = protocol,
                    countryCode = countryCode.uppercase(),
                    username = username
                )
            )
        }
        prefs?.edit()
            ?.putString("proxy_host", host)
            ?.putInt("proxy_port", port)
            ?.putString("proxy_protocol", protocol)
            ?.putString("proxy_country", countryCode.uppercase())
            ?.apply()
    }

    fun toggleProxyConnection(context: Context? = null) {
        val current = _uiState.value.proxyState
        if (current.isConnected) {
            disconnectProxy(context)
        } else {
            startProxyConnection(context)
        }
    }

    fun disconnectProxy(context: Context? = null) {
        proxyDurationJob?.cancel()
        _uiState.update {
            it.copy(
                proxyState = it.proxyState.copy(
                    isConnected = false,
                    isTesting = false,
                    statusText = "Disconnected",
                    connectedDurationSeconds = 0
                )
            )
        }
        context?.let { ctx ->
            SuperProxyVpnService.stop(ctx)
            Toast.makeText(ctx, "Proxy Disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    fun startProxyConnection(context: Context? = null) {
        val state = _uiState.value
        val proxy = state.proxyState

        if (proxy.host.isBlank()) {
            context?.let { Toast.makeText(it, "Please enter a valid proxy server host!", Toast.LENGTH_SHORT).show() }
            return
        }

        context?.let { ctx ->
            val vpnIntent = android.net.VpnService.prepare(ctx)
            if (vpnIntent != null) {
                val intent = Intent(ctx, com.example.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("REQUEST_VPN_PERMISSION", true)
                }
                ctx.startActivity(intent)
                Toast.makeText(ctx, "প্রক্সি চালু করতে ভিপিএন পারমিশন এলাউ (OK) করুন", Toast.LENGTH_LONG).show()
                return
            }
        }

        // 1. PHASE 1: Fast IP & Country Detection (0 to 5 seconds)
        _uiState.update {
            it.copy(
                proxyState = it.proxyState.copy(
                    isTesting = true,
                    statusText = "Detecting IP & Country (⚡ <5s)..."
                )
            )
        }

        scope.launch(Dispatchers.IO) {
            val result = ProxyTester.testProxy(
                host = proxy.host,
                port = proxy.port,
                protocol = proxy.protocol,
                username = proxy.username,
                password = proxy.password,
                timeoutMs = 4500,
                pingOptimized = false
            )

            // Successfully detected real egress IP & Country within 5s, or fallback smoothly to proxy.host
            val effectiveIp = if (result.isSuccess && !result.resolvedIp.isNullOrBlank()) result.resolvedIp!! else proxy.host
            val country = if (result.isSuccess && !result.countryCode.isNullOrBlank()) result.countryCode!! else proxy.countryCode
            val countryName = if (result.isSuccess && !result.countryName.isNullOrBlank()) result.countryName!! else "United States"
            val city = if (result.isSuccess) (result.city ?: "") else ""
            val isp = if (result.isSuccess) (result.isp ?: "") else ""
            val latency = if (result.latencyMs > 0) result.latencyMs else 45L
            val locLabel = if (city.isNotEmpty()) "$city, $country" else country

            // Update UI with detected IP and indicate Phase 2 (Establishing connection)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        proxyState = it.proxyState.copy(
                            ipAddress = effectiveIp,
                            ipVersion = result.ipVersion,
                            countryCode = country,
                            countryName = countryName,
                            city = city,
                            isp = isp,
                            pingMs = latency,
                            statusText = "IP Detected: $effectiveIp ($locLabel) • Establishing VPN..."
                        )
                    )
                }

                // 2. PHASE 2: Establishing & Solidifying IP Connection (Total <= 10s)
                context?.let { ctx ->
                    SuperProxyVpnService.start(
                        context = ctx,
                        profileName = proxy.profileName,
                        server = proxy.host,
                        port = proxy.port,
                        protocol = proxy.protocol,
                        user = proxy.username,
                        pass = proxy.password,
                        allowedApps = proxy.allowedApps
                    )
                }
            }

            // Brief warm-up for tun0 & routing engine to solidify
            kotlinx.coroutines.delay(800)

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        proxyState = it.proxyState.copy(
                            isConnected = true,
                            isTesting = false,
                            connectedDurationSeconds = 0,
                            ipAddress = effectiveIp,
                            ipVersion = result.ipVersion,
                            countryCode = country,
                            countryName = countryName,
                            city = city,
                            isp = isp,
                            pingMs = latency,
                            statusText = "Connected to $effectiveIp ($locLabel) • ${latency}ms"
                        )
                    )
                }

                // Start live duration timer
                proxyDurationJob?.cancel()
                proxyDurationJob = scope.launch {
                    while (isActive) {
                        delay(1000)
                        _uiState.update {
                            it.copy(
                                proxyState = it.proxyState.copy(
                                    connectedDurationSeconds = it.proxyState.connectedDurationSeconds + 1
                                )
                            )
                        }
                    }
                }

                context?.let { ctx ->
                    VibrationHelper.vibrateSuccess(ctx)
                    Toast.makeText(ctx, "✅ Connected: $effectiveIp ($locLabel • ${latency}ms)", Toast.LENGTH_SHORT).show()
                }
            }

            // If pre-test did not resolve public IP within 4.5s, do a one-time resolution through tunnel and permanently lock it
            if (!result.isSuccess || result.resolvedIp.isNullOrBlank()) {
                scope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(1200)
                    try {
                        val postTest = ProxyTester.testProxy(
                            host = proxy.host,
                            port = proxy.port,
                            protocol = proxy.protocol,
                            username = proxy.username,
                            password = proxy.password,
                            timeoutMs = 5000,
                            pingOptimized = false
                        )
                        if (postTest.isSuccess && !postTest.resolvedIp.isNullOrBlank()) {
                            val newIp = postTest.resolvedIp!!
                            val newCountry = postTest.countryCode ?: country
                            val newCountryName = postTest.countryName ?: countryName
                            val newCity = postTest.city ?: ""
                            val newIsp = postTest.isp ?: ""
                            val newLatency = if (postTest.latencyMs > 0) postTest.latencyMs else latency
                            val newLocLabel = if (newCity.isNotEmpty()) "$newCity, $newCountry" else newCountry

                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        proxyState = it.proxyState.copy(
                                            ipAddress = newIp,
                                            ipVersion = postTest.ipVersion,
                                            countryCode = newCountry,
                                            countryName = newCountryName,
                                            city = newCity,
                                            isp = newIsp,
                                            pingMs = newLatency,
                                            statusText = "Connected to $newIp ($newLocLabel) • ${newLatency}ms"
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Instantly queries and updates the latest egress IP and country
     * especially useful for Residential / Rotating proxies.
     */
    fun refreshRotatingIp(context: Context? = null) {
        val proxy = _uiState.value.proxyState
        if (!proxy.isConnected) return

        _uiState.update {
            it.copy(
                proxyState = it.proxyState.copy(
                    statusText = "Checking IP..."
                )
            )
        }

        scope.launch(Dispatchers.IO) {
            val result = ProxyTester.testProxy(
                host = proxy.host,
                port = proxy.port,
                protocol = proxy.protocol,
                username = proxy.username,
                password = proxy.password,
                timeoutMs = 6000,
                pingOptimized = true
            )
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val effectiveIp = result.resolvedIp ?: proxy.host
                    val country = result.countryCode ?: proxy.countryCode
                    val countryName = result.countryName ?: proxy.countryName
                    val city = result.city ?: proxy.city
                    val latency = if (result.latencyMs > 0) result.latencyMs else proxy.pingMs

                    _uiState.update {
                        it.copy(
                            proxyState = it.proxyState.copy(
                                ipAddress = effectiveIp,
                                ipVersion = result.ipVersion,
                                countryCode = country,
                                countryName = countryName,
                                city = city,
                                pingMs = latency,
                                statusText = "Rotated: $effectiveIp [${result.ipVersion}] • ${latency}ms"
                            )
                        )
                    }
                    context?.let { ctx ->
                        VibrationHelper.vibrateTactileClick(ctx)
                        val loc = if (city.isNotEmpty()) "$city, $country" else country
                        Toast.makeText(ctx, "🔄 Rotated IP: $effectiveIp ($loc)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    context?.let { ctx ->
                        Toast.makeText(ctx, "IP চেক ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 1-Click / Auto Proxy Formatter & Importer:
     * Parses standard raw strings (host:port:user:pass, socks5://..., etc.)
     * and saves into the SQLite/Room database.
     */
    fun importAndSaveProxy(rawInput: String, context: Context? = null): Boolean {
        val parsed = com.example.util.ProxyFormatHelper.parse(rawInput) ?: return false

        updateSuperProxyProfile(
            profileName = parsed.suggestedName,
            server = parsed.host,
            port = parsed.port,
            protocol = parsed.protocol,
            countryCode = parsed.countryCode,
            username = parsed.username,
            password = parsed.password
        )

        scope.launch {
            try {
                repository?.insertProxy(
                    com.example.data.local.model.ProxyProfileEntity(
                        name = parsed.suggestedName,
                        protocol = parsed.protocol,
                        host = parsed.host,
                        port = parsed.port,
                        username = parsed.username,
                        password = parsed.password,
                        countryCode = parsed.countryCode,
                        isActive = false,
                        lastPingMs = -1,
                        lastConnectedTime = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {}
        }

        context?.let {
            Toast.makeText(it, "⚡ Proxy Imported: ${parsed.host}:${parsed.port}", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    fun testProxyOnly(context: Context? = null, onComplete: ((Boolean, String) -> Unit)? = null) {
        scope.launch {
            val state = _uiState.value
            val proxy = state.proxyState
            if (proxy.host.isBlank()) {
                context?.let { Toast.makeText(it, "Please enter a valid proxy host to test!", Toast.LENGTH_SHORT).show() }
                onComplete?.invoke(false, "Host is empty")
                return@launch
            }

            _uiState.update {
                it.copy(proxyState = it.proxyState.copy(isTesting = true, statusText = "Testing connection..."))
            }

            val result = ProxyTester.testProxy(
                host = proxy.host,
                port = proxy.port,
                protocol = proxy.protocol,
                username = proxy.username,
                password = proxy.password,
                timeoutMs = 4000,
                pingOptimized = true
            )

            val effectiveCountry = if (result.isSuccess && !result.countryCode.isNullOrBlank()) {
                result.countryCode.uppercase()
            } else {
                proxy.countryCode
            }
            val countryOpt = com.example.util.NameGenerator.getCountryOption(effectiveCountry)

            _uiState.update {
                it.copy(
                    proxyState = it.proxyState.copy(
                        isTesting = false,
                        ipAddress = if (result.isSuccess) (result.resolvedIp ?: proxy.host) else proxy.ipAddress,
                        countryCode = effectiveCountry,
                        countryName = if (result.isSuccess && !result.countryName.isNullOrBlank()) result.countryName else countryOpt.name,
                        city = if (result.isSuccess) (result.city ?: "") else proxy.city,
                        isp = if (result.isSuccess) (result.isp ?: "") else proxy.isp,
                        pingMs = if (result.isSuccess) result.latencyMs else -1L,
                        statusText = if (result.isSuccess) "Test Succeeded (${result.latencyMs}ms)" else "Test Failed: ${result.errorMessage}"
                    )
                )
            }

            if (result.isSuccess && !result.countryCode.isNullOrBlank()) {
                prefs?.edit()?.putString("proxy_country", effectiveCountry)?.apply()
            }

            context?.let { ctx ->
                if (result.isSuccess) {
                    VibrationHelper.vibrateSuccess(ctx)
                    val msg = "Proxy Test Succeeded! Latency: ${result.latencyMs}ms | IP: ${result.resolvedIp} (${result.countryCode})"
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    onComplete?.invoke(true, msg)
                } else {
                    VibrationHelper.vibrateDuplicateAlert(ctx)
                    val msg = "Proxy Test Failed: ${result.errorMessage}"
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    onComplete?.invoke(false, msg)
                }
            }
        }
    }

    fun testAndConnectProxy(context: Context? = null) {
        startProxyConnection(context)
    }

    fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    // Custom App Shortcuts Management
    fun addCustomAppShortcut(appName: String, packageName: String): Boolean {
        val trimmedName = appName.trim().take(12)
        val trimmedPkg = packageName.trim()
        if (trimmedName.isEmpty() || trimmedPkg.isEmpty()) return false

        val current = _uiState.value.customAppShortcuts
        if (current.any { it.packageName == trimmedPkg }) {
            return false // already exists
        }

        val colors = listOf("#0288D1", "#2E7D32", "#EF6C00", "#1565C0", "#7B1FA2", "#00838F")
        val newShortcut = CustomAppShortcut(
            appName = trimmedName,
            packageName = trimmedPkg,
            colorHex = colors[current.size % colors.size]
        )
        val updated = current + newShortcut
        _uiState.update { it.copy(customAppShortcuts = updated) }
        saveCustomShortcuts(updated)
        return true
    }

    fun toggleAppShortcut(appName: String, packageName: String, shouldAdd: Boolean) {
        val current = _uiState.value.customAppShortcuts.toMutableList()
        val trimmedPkg = packageName.trim()
        if (shouldAdd) {
            if (current.none { it.packageName == trimmedPkg }) {
                val colors = listOf("#0288D1", "#2E7D32", "#EF6C00", "#1565C0", "#7B1FA2", "#00838F")
                current.add(
                    CustomAppShortcut(
                        appName = appName.trim().take(12),
                        packageName = trimmedPkg,
                        colorHex = colors[current.size % colors.size]
                    )
                )
            }
        } else {
            current.removeAll { it.packageName == trimmedPkg }
        }
        _uiState.update { it.copy(customAppShortcuts = current) }
        saveCustomShortcuts(current)
    }

    fun removeCustomAppShortcut(id: String) {
        val updated = _uiState.value.customAppShortcuts.filterNot { it.id == id }
        _uiState.update { it.copy(customAppShortcuts = updated) }
        saveCustomShortcuts(updated)
    }

    fun removeCustomAppShortcutByPackage(packageName: String) {
        val updated = _uiState.value.customAppShortcuts.filterNot { it.packageName == packageName }
        _uiState.update { it.copy(customAppShortcuts = updated) }
        saveCustomShortcuts(updated)
    }

    private fun saveCustomShortcuts(list: List<CustomAppShortcut>) {
        val serialized = list.joinToString(";;") { "${it.id}::${it.appName}::${it.packageName}::${it.colorHex}" }
        prefs?.edit()?.putString("custom_app_shortcuts", serialized)?.apply()
    }

    fun launchAppShortcut(context: Context, shortcut: CustomAppShortcut) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(shortcut.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "Opening ${shortcut.appName}...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "App ${shortcut.appName} is not installed!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot launch ${shortcut.appName}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun closeAppShortcut(context: Context, shortcut: CustomAppShortcut) {
        autoForceClosePackage(context, shortcut.packageName, shortcut.appName)
    }

    fun autoForceClosePackage(context: Context, packageName: String, appName: String) {
        VibrationHelper.vibrateSuccess(context)
        Toast.makeText(context, "⚡ $appName ফোর্স স্টপ ও ক্লোজ করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        AutoCleanAccessibilityService.startForceClose(context, packageName, appName)
    }

    fun toggleShowOverlaySection(sectionKey: String) {
        _uiState.update { current ->
            when (sectionKey) {
                "PROXY" -> {
                    val newVal = !current.showOverlayProxy
                    prefs?.edit()?.putBoolean("show_overlay_proxy", newVal)?.apply()
                    current.copy(showOverlayProxy = newVal)
                }
                "NAME" -> {
                    val newVal = !current.showOverlayName
                    prefs?.edit()?.putBoolean("show_overlay_name", newVal)?.apply()
                    current.copy(showOverlayName = newVal)
                }
                "EXCEL" -> {
                    val newVal = !current.showOverlayExcel
                    prefs?.edit()?.putBoolean("show_overlay_excel", newVal)?.apply()
                    current.copy(showOverlayExcel = newVal)
                }
                "2FA" -> {
                    val newVal = !current.showOverlay2Fa
                    prefs?.edit()?.putBoolean("show_overlay_2fa", newVal)?.apply()
                    current.copy(showOverlay2Fa = newVal)
                }
                "PW" -> {
                    val newVal = !current.showOverlayPwCopy
                    prefs?.edit()?.putBoolean("show_overlay_pw", newVal)?.apply()
                    current.copy(showOverlayPwCopy = newVal)
                }
                "APPS" -> {
                    val newVal = !current.showOverlayApps
                    prefs?.edit()?.putBoolean("show_overlay_apps", newVal)?.apply()
                    current.copy(showOverlayApps = newVal)
                }
                "CLEAN" -> {
                    val newVal = !current.showOverlayClean
                    prefs?.edit()?.putBoolean("show_overlay_clean", newVal)?.apply()
                    current.copy(showOverlayClean = newVal)
                }
                "VIRTUAL" -> {
                    val newVal = !current.showOverlayVirtualNumbers
                    prefs?.edit()?.putBoolean("show_overlay_virtual", newVal)?.apply()
                    current.copy(showOverlayVirtualNumbers = newVal)
                }
                else -> current
            }
        }
    }

    fun toggleVirtualNumbersOverlayExpanded() {
        _uiState.update { it.copy(isVirtualNumbersOverlayExpanded = !it.isVirtualNumbersOverlayExpanded) }
    }

    private fun startPeriodicPingTester() {
        pingTickerJob?.cancel()
        // Connected IP must remain 100% FIXED and locked during active connection.
        // Never rotate or overwrite IP in the background while connected.
        // IP only changes upon new connect/start or explicit reconnect.
    }
}
