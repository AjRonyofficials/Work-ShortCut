package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SmartToy
import com.example.service.AppUpdateManager
import com.example.service.KopEngine2Manager
import com.example.service.OverlayStateManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AuthManager
import com.example.service.AuthUser
import com.example.service.UnixSmsCdrRecord
import com.example.service.UnixSmsManager
import com.example.service.UploadedNumberItem
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import com.example.service.VirtualNumberManager
import com.example.service.WithdrawalManager
import com.example.service.WithdrawalRequest
import com.example.service.OtpHistoryManager
import com.example.service.OtpHistoryRecord
import com.example.service.GlobalCloudSyncService
import kotlinx.coroutines.isActive
import com.example.util.AllCountriesProvider

data class AdminCountryInfo(
    val code: String,
    val name: String,
    val flag: String,
    val dialCode: String
)

val ADMIN_PRESET_COUNTRIES = AllCountriesProvider.allCountries

val ADMIN_SERVICES = listOf("Facebook", "WhatsApp", "Instagram", "OTP Work")

@Composable
fun AdminPanelSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isAdmin = AuthManager.isAdmin()
    val isPrimeAdmin = AuthManager.isPrimeAdmin()
    val isSubAdmin = AuthManager.isSubAdmin()
    val subAdminPerms = AuthManager.getCurrentSubAdminPermissions()
    val currentAdminEmail by AuthManager.currentEmail.collectAsState()

    val unixState by UnixSmsManager.state.collectAsState()
    val managedUsers by AuthManager.managedUsers.collectAsState()
    val withdrawState by WithdrawalManager.state.collectAsState()
    val otpHistoryState by OtpHistoryManager.state.collectAsState()
    val zenexOtpRate by VirtualNumberManager.zenexOtpRate.collectAsState()
    val kop2OtpRate by KopEngine2Manager.kop2OtpRate.collectAsState()
    val premiumV2OtpRate by com.example.service.PremiumNumberV2Manager.otpRate.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: SMS Panels, 1: Withdrawals, 2: Users, 3: Live CDR/DLR
    var isLiveSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        UnixSmsManager.init(context)
        AuthManager.init(context)
        WithdrawalManager.init(context)
        OtpHistoryManager.init(context)
        VirtualNumberManager.init(context)
        KopEngine2Manager.init(context)
        com.example.service.PremiumNumberV2Manager.init(context)

        // Initial instant sync
        GlobalCloudSyncService.pullFromCloud(context)
        WithdrawalManager.syncFromCloud(context)
        OtpHistoryManager.syncFromCloud(context)
        AuthManager.syncFromCloud()

        // 4-5s real-time live sync loop for Admin Panel
        while (isActive) {
            delay(4000L)
            try {
                GlobalCloudSyncService.pullFromCloud(context)
                WithdrawalManager.syncFromCloud(context)
                OtpHistoryManager.syncFromCloud(context)
                AuthManager.syncFromCloud()
            } catch (_: Exception) {}
        }
    }

    if (!isAdmin) {
        // Non-Admin Locked Screen
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0A0F18))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C28)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Lock",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "🔒 এডমিন এক্সেস সংরক্ষিত",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "এই সেকশনটি শুধুমাত্র মাস্টার এডমিন (${AuthManager.ADMIN_EMAIL}) এবং অনুমোদিত সাব এডমিনদের জন্য নির্ধারিত।",
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { AuthManager.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("লগআউট করে এডমিন লগইন করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Admin Dashboard
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090E17))
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Admin Header Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isPrimeAdmin) Color(0xFFD97706) else Color(0xFF4F46E5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPrimeAdmin) {
                                        Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF3B82F6)))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPrimeAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Security,
                                contentDescription = "Admin",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isPrimeAdmin) "👑 PRIME ADMIN PANEL" else "🛡️ SUB ADMIN PANEL",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPrimeAdmin) Color(0xFFFBBF24) else Color(0xFFA5B4FC),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isPrimeAdmin) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF6366F1).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isPrimeAdmin) "PRIME MASTER" else "SUB ADMIN",
                                        color = if (isPrimeAdmin) Color(0xFFFBBF24) else Color(0xFFA5B4FC),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (currentAdminEmail.isNotBlank()) currentAdminEmail else AuthManager.ADMIN_EMAIL,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                if (isLiveSyncing) return@IconButton
                                isLiveSyncing = true
                                coroutineScope.launch {
                                    try {
                                        val usersJob = async { AuthManager.syncFromCloudAsync() }
                                        val cloudPullJob = async { GlobalCloudSyncService.pullFromCloud(context) }
                                        val withJob = async { WithdrawalManager.syncFromCloud(context) }
                                        val otpJob = async { OtpHistoryManager.syncFromCloud(context) }
                                        val userCount = usersJob.await()
                                        cloudPullJob.await()
                                        withJob.await()
                                        otpJob.await()
                                        com.example.util.VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "⚡ লাইভ সিঙ্ক সম্পন্ন! ($userCount জন ইউজার ডাটা আপডেট হয়েছে)", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "⚠️ সিঙ্ক ত্রুটি: ${e.message ?: "ব্যর্থ"}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLiveSyncing = false
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isLiveSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Live Sync", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                AuthManager.logout()
                                Toast.makeText(context, "লগআউট করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Modern Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D1522),
                contentColor = Color(0xFF818CF8),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF818CF8),
                        height = 3.dp
                    )
                },
                divider = { HorizontalDivider(color = Color(0xFF1E2D42)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("SMS Panels (${unixState.uploadedNumbers.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                if (isPrimeAdmin || subAdminPerms.canManageWithdrawals) {
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val pendingCount = withdrawState.allRequests.count { it.status == "PENDING" }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (pendingCount > 0) Color(0xFFFFD600) else Color(0xFF818CF8))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (pendingCount > 0) "Withdraw ($pendingCount)" else "Withdraw",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pendingCount > 0) Color(0xFFFFD600) else Color.Unspecified
                                )
                            }
                        }
                    )
                }
                if (isPrimeAdmin) {
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Users (${managedUsers.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
                if (isPrimeAdmin || subAdminPerms.canViewLiveCdr) {
                    val onlineUsersCount = managedUsers.count { it.isOnline || (System.currentTimeMillis() - it.lastActive) < 120_000L }
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF00E676))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Sessions ($onlineUsersCount Online)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
                if (isPrimeAdmin) {
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF00E676))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("App Update", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // TAB 0: UNIX & ZENEX SMS NUMBER MANAGEMENT
                item {
                    AdminUnixSmsCard(
                        context = context,
                        unixState = unixState,
                        zenexRate = zenexOtpRate,
                        kop2Rate = kop2OtpRate,
                        premiumV2Rate = premiumV2OtpRate,
                        canUploadNumbers = if (isPrimeAdmin) true else subAdminPerms.canUploadNumbers,
                        canDeleteNumbers = if (isPrimeAdmin) true else subAdminPerms.canDeleteNumbers,
                        canUpdateOtpRate = if (isPrimeAdmin) true else subAdminPerms.canUpdateOtpRate
                    )
                }
                item {
                    AdminDataMaintenanceCard(
                        context = context,
                        otpRecordCount = otpHistoryState.recentRecords.size,
                        totalOtpAllTime = otpHistoryState.totalOtpsAllTime,
                        withdrawalCount = withdrawState.allRequests.size,
                        uploadedNumberCount = unixState.uploadedNumbers.size,
                        canManage = if (isPrimeAdmin) true else subAdminPerms.canDeleteNumbers
                    )
                }
            }
            1 -> {
                // TAB 1: WITHDRAW REQUESTS MANAGEMENT
                if (isPrimeAdmin || subAdminPerms.canManageWithdrawals) {
                    item {
                        AdminWithdrawRequestsCard(
                            context = context,
                            withdrawState = withdrawState
                        )
                    }
                }
            }
            2 -> {
                // TAB 2: USER ACCOUNT MANAGEMENT (PRIME ADMIN ONLY)
                if (isPrimeAdmin) {
                    item {
                        AdminUserManagerCard(
                            context = context,
                            managedUsers = managedUsers
                        )
                    }
                }
            }
            3 -> {
                // TAB 3: LIVE DEVICE SESSIONS & SYSTEM HEALTH (REPLACING OLD LIVE CDR)
                if (isPrimeAdmin || subAdminPerms.canViewLiveCdr) {
                    item {
                        AdminDeviceAndSystemMonitorCard(
                            context = context,
                            managedUsers = managedUsers
                        )
                    }
                }
            }
            4 -> {
                // TAB 4: APP IN-APP REMOTE UPDATE MANAGER (PRIME ADMIN)
                if (isPrimeAdmin) {
                    item {
                        AdminAppUpdateCard(
                            context = context
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Tab 0: Unix SMS Management Card (Upload by text / File, Delete by Country, Token config)
 */
@Composable
fun AdminUnixSmsCard(
    context: Context,
    unixState: com.example.service.UnixSmsState,
    zenexRate: Double = 0.50,
    kop2Rate: Double = 0.50,
    premiumV2Rate: Double = 0.50,
    canUploadNumbers: Boolean = true,
    canDeleteNumbers: Boolean = true,
    canUpdateOtpRate: Boolean = true
) {
    var selectedCountry by remember { mutableStateOf(ADMIN_PRESET_COUNTRIES.first()) }
    var selectedService by remember { mutableStateOf(ADMIN_SERVICES.first()) }
    var selectedNumberType by remember { mutableStateOf("Fresh") } // "Fresh" (New Fresh Number) or "PC Clone" (PC Clone Number)
    var numbersInput by remember { mutableStateOf("") }
    var countryDropdownExpanded by remember { mutableStateOf(false) }
    var showCountrySearchDialog by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    // Clear by country
    var deleteCountryDropdownExpanded by remember { mutableStateOf(false) }
    var deleteTargetCountry by remember { mutableStateOf("ALL") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Search filter for uploaded list
    var filterQuery by remember { mutableStateOf("") }
    var selectedUploadedCountryFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") } // "ALL", "AVAILABLE", "USED", "OTP_RCV"

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isNotEmpty()) {
                    numbersInput = if (numbersInput.isBlank()) content else numbersInput + "\n" + content
                    Toast.makeText(context, "✓ ফাইল সফলভাবে লোড হয়েছে!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ফাইলটি খালি ছিল", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ফাইল পড়তে সমস্যা: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Stats Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val total = unixState.uploadedNumbers.size
            val available = unixState.uploadedNumbers.count { !it.isUsed }
            val used = unixState.uploadedNumbers.count { it.isUsed }

            AdminStatPill(title = "TOTAL", count = total.toString(), color = Color(0xFF60A5FA), modifier = Modifier.weight(1f))
            AdminStatPill(title = "AVAILABLE", count = available.toString(), color = Color(0xFF34D399), modifier = Modifier.weight(1f))
            AdminStatPill(title = "USED", count = used.toString(), color = Color(0xFFFBBF24), modifier = Modifier.weight(1f))
            AdminStatPill(title = "TODAY OTP", count = unixState.todayOtpCount.toString(), color = Color(0xFFF472B6), modifier = Modifier.weight(1f))
        }

        // 1.5 USER OTP RATE CONTROL CARD: BOTH UNIX SMS & ZENEX SMS (IN TAKA ৳ WITH UNLIMITED CUSTOM ADJUSTER)
        var showCustomUnixRateDialog by remember { mutableStateOf(false) }
        var showCustomZenexRateDialog by remember { mutableStateOf(false) }
        var showCustomKop2RateDialog by remember { mutableStateOf(false) }
        var showCustomPremiumV2RateDialog by remember { mutableStateOf(false) }
        var selectedV2CountryCode by remember { mutableStateOf("ALL") }
        var showAllWorldCountriesDialog by remember { mutableStateOf(false) }
        var showV2AllWorldCountriesSearchDialog by remember { mutableStateOf(false) }
        var v2AllCountriesSearchQuery by remember { mutableStateOf("") }
        var showApiKeyConfigDialog by remember { mutableStateOf(false) }
        var allCountriesSearchQuery by remember { mutableStateOf("") }
        var customRateInputText by remember { mutableStateOf("") }
        var customApiKeyInputText by remember { mutableStateOf("") }

        if (showCustomUnixRateDialog) {
            AlertDialog(
                onDismissRequest = { showCustomUnixRateDialog = false },
                containerColor = Color(0xFF101C2B),
                title = {
                    Text("PREMIUM NUMBERS রেট পরিবর্তন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("যেকোনো ওটিপি রেট লিখুন (টাকা / ৳):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        OutlinedTextField(
                            value = customRateInputText,
                            onValueChange = { customRateInputText = it },
                            placeholder = { Text("যেমন: 0.50 বা 0.48 বা 1.00", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF1E2D40),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val r = customRateInputText.toDoubleOrNull()
                            if (r != null && r >= 0.01) {
                                UnixSmsManager.setOtpRate(r)
                                Toast.makeText(context, "Premium Numbers ওটিপি রেট সেট হয়েছে: ${String.format(java.util.Locale.US, "%.2f", r)} ৳", Toast.LENGTH_SHORT).show()
                                showCustomUnixRateDialog = false
                            } else {
                                Toast.makeText(context, "সঠিক রেট লিখুন (কমপক্ষে 0.01)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("সেভ করুন", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomUnixRateDialog = false }) {
                        Text("বাতিল", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        if (showCustomZenexRateDialog) {
            AlertDialog(
                onDismissRequest = { showCustomZenexRateDialog = false },
                containerColor = Color(0xFF101C2B),
                title = {
                    Text("KOP ENGINE (Api Number) রেট পরিবর্তন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("যেকোনো ওটিপি রেট লিখুন (টাকা / ৳):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        OutlinedTextField(
                            value = customRateInputText,
                            onValueChange = { customRateInputText = it },
                            placeholder = { Text("যেমন: 0.50 বা 0.48 বা 1.00", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF34D399),
                                unfocusedBorderColor = Color(0xFF1E2D40),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val r = customRateInputText.toDoubleOrNull()
                            if (r != null && r >= 0.01) {
                                VirtualNumberManager.setZenexOtpRate(r)
                                Toast.makeText(context, "Kop Engine ওটিপি রেট সেট হয়েছে: ${String.format(java.util.Locale.US, "%.2f", r)} ৳", Toast.LENGTH_SHORT).show()
                                showCustomZenexRateDialog = false
                            } else {
                                Toast.makeText(context, "সঠিক রেট লিখুন (কমপক্ষে 0.01)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("সেভ করুন", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomZenexRateDialog = false }) {
                        Text("বাতিল", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        if (showCustomKop2RateDialog) {
            AlertDialog(
                onDismissRequest = { showCustomKop2RateDialog = false },
                containerColor = Color(0xFF101C2B),
                title = {
                    Text("KOP ENGINE 2 (Api Number) রেট পরিবর্তন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("যেকোনো ওটিপি রেট লিখুন (টাকা / ৳):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        OutlinedTextField(
                            value = customRateInputText,
                            onValueChange = { customRateInputText = it },
                            placeholder = { Text("যেমন: 0.50 বা 0.40 বা 1.00", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFA78BFA),
                                unfocusedBorderColor = Color(0xFF1E2D40),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val r = customRateInputText.toDoubleOrNull()
                            if (r != null && r >= 0.01) {
                                com.example.service.KopEngine2Manager.setKop2OtpRate(r)
                                com.example.service.WithdrawalManager.refreshBalances()
                                Toast.makeText(context, "Kop Engine 2 ওটিপি রেট সেট হয়েছে: ${String.format(java.util.Locale.US, "%.2f", r)} ৳", Toast.LENGTH_SHORT).show()
                                showCustomKop2RateDialog = false
                            } else {
                                Toast.makeText(context, "সঠিক রেট লিখুন (কমপক্ষে 0.01)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("সেভ করুন", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomKop2RateDialog = false }) {
                        Text("বাতিল", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        if (showCustomPremiumV2RateDialog) {
            val targetCountryLabel = if (selectedV2CountryCode == "ALL") "All Countries (Default)" else selectedV2CountryCode
            AlertDialog(
                onDismissRequest = { showCustomPremiumV2RateDialog = false },
                containerColor = Color(0xFF101C2B),
                title = {
                    Text("PREMIUM NUMBER V2 রেট ($targetCountryLabel)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$targetCountryLabel এর জন্য ওটিপি রেট লিখুন (টাকা / ৳):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        OutlinedTextField(
                            value = customRateInputText,
                            onValueChange = { customRateInputText = it },
                            placeholder = { Text("যেমন: 0.50 বা 0.40 বা 1.00", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF1E2D40),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val r = customRateInputText.toDoubleOrNull()
                            if (r != null && r >= 0.01) {
                                if (selectedV2CountryCode == "ALL") {
                                    com.example.service.PremiumNumberV2Manager.setOtpRate(r)
                                } else {
                                    com.example.service.PremiumNumberV2Manager.setRateForCountry(selectedV2CountryCode, r)
                                }
                                com.example.service.WithdrawalManager.refreshBalances()
                                Toast.makeText(context, "$targetCountryLabel রেট সেট হয়েছে: ${String.format(java.util.Locale.US, "%.2f", r)} ৳", Toast.LENGTH_SHORT).show()
                                showCustomPremiumV2RateDialog = false
                            } else {
                                Toast.makeText(context, "সঠিক রেট লিখুন (কমপক্ষে 0.01)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("সেভ করুন", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomPremiumV2RateDialog = false }) {
                        Text("বাতিল", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        if (showV2AllWorldCountriesSearchDialog) {
            val allWorldList: List<AdminCountryInfo> = remember { AllCountriesProvider.allCountries }
            val filteredV2Countries: List<AdminCountryInfo> = remember(v2AllCountriesSearchQuery, allWorldList) {
                if (v2AllCountriesSearchQuery.isBlank()) {
                    allWorldList
                } else {
                    val q = v2AllCountriesSearchQuery.trim().lowercase(Locale.US)
                    allWorldList.filter { country ->
                        country.name.lowercase(Locale.US).contains(q) ||
                        country.code.lowercase(Locale.US).contains(q) ||
                        country.dialCode.contains(q)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showV2AllWorldCountriesSearchDialog = false },
                containerColor = Color(0xFF0D1826),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("বিশ্বের সকল দেশ - Premium V2 (${allWorldList.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("যেকোনো দেশ সিলেক্ট করে তার নির্দিষ্ট OTP রেট সেট করুন", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.height(440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = v2AllCountriesSearchQuery,
                            onValueChange = { v2AllCountriesSearchQuery = it },
                            placeholder = { Text("দেশ খুঁজুন (যেমন Zimbabwe, France, +263, +33)...", color = Color(0xFF64748B), fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF1E2D40),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("পাওয়া গেছে: ${filteredV2Countries.size} টি দেশ", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(items = filteredV2Countries, key = { it.code }) { country ->
                                val isSelected = country.code.equals(selectedV2CountryCode, ignoreCase = true)
                                val currentRate = com.example.service.PremiumNumberV2Manager.getRateForCountry(country.code)
                                Surface(
                                    color = if (isSelected) Color(0xFF0369A1) else Color(0xFF132237),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E3A56)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    selectedV2CountryCode = country.code
                                                    customRateInputText = String.format(Locale.US, "%.2f", currentRate)
                                                    showV2AllWorldCountriesSearchDialog = false
                                                    showCustomPremiumV2RateDialog = true
                                                }
                                        ) {
                                            Text(country.flag, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(country.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("${country.code} • ${country.dialCode}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                selectedV2CountryCode = country.code
                                                customRateInputText = String.format(Locale.US, "%.2f", currentRate)
                                                showV2AllWorldCountriesSearchDialog = false
                                                showCustomPremiumV2RateDialog = true
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("${String.format(Locale.US, "%.2f", currentRate)}৳ ✏️", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showV2AllWorldCountriesSearchDialog = false }) {
                        Text("বন্ধ করুন", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showAllWorldCountriesDialog) {
            val filteredWorldCountries = remember(allCountriesSearchQuery) {
                if (allCountriesSearchQuery.isBlank()) {
                    ADMIN_PRESET_COUNTRIES
                } else {
                    val q = allCountriesSearchQuery.trim().lowercase(java.util.Locale.US)
                    ADMIN_PRESET_COUNTRIES.filter {
                        it.name.lowercase(java.util.Locale.US).contains(q) ||
                        it.code.lowercase(java.util.Locale.US).contains(q) ||
                        it.dialCode.contains(q)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showAllWorldCountriesDialog = false },
                containerColor = Color(0xFF0D1826),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("বিশ্বের সকল দেশ - Premium Numbers (${ADMIN_PRESET_COUNTRIES.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("নম্বর আপলোডের জন্য দেশ সিলেক্ট করুন বা নির্দিষ্ট দেশের রেট আপডেট করুন", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.height(440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = allCountriesSearchQuery,
                            onValueChange = { allCountriesSearchQuery = it },
                            placeholder = { Text("দেশ খুঁজুন (যেমন Bangladesh, United States, +880, +1)...", color = Color(0xFF64748B), fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF1E2D40),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("পাওয়া গেছে: ${filteredWorldCountries.size} টি দেশ", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredWorldCountries.size) { idx ->
                                val country = filteredWorldCountries[idx]
                                val isSelected = country.code.equals(selectedCountry.code, ignoreCase = true)
                                val currentRate = UnixSmsManager.getRateForCountry(country.code)
                                Surface(
                                    color = if (isSelected) Color(0xFF0369A1) else Color(0xFF132237),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E3A56)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    selectedCountry = country
                                                    Toast.makeText(context, "✓ ${country.flag} ${country.name} আপলোডের জন্য নির্বাচিত হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    showAllWorldCountriesDialog = false
                                                }
                                        ) {
                                            Text(country.flag, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(country.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("ISO: ${country.code} • Dial: ${country.dialCode}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Country Rate Pill
                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(0.8.dp, Color(0xFF38BDF8))
                                            ) {
                                                Text(
                                                    text = "${String.format(java.util.Locale.US, "%.2f", currentRate)} ৳",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }

                                            // Quick 0.48, 0.49, 0.50 rate buttons for this country
                                            listOf(0.48, 0.49, 0.50).forEach { rVal ->
                                                Surface(
                                                    color = if (Math.abs(currentRate - rVal) < 0.005) Color(0xFF00E676) else Color(0xFF0F1A28),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                                    modifier = Modifier.clickable {
                                                        UnixSmsManager.setCountryRate(country.code, rVal)
                                                        Toast.makeText(context, "${country.flag} ${country.code} রেট: ${rVal} ৳", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Text(
                                                        text = String.format(java.util.Locale.US, "%.2f", rVal),
                                                        color = if (Math.abs(currentRate - rVal) < 0.005) Color.Black else Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            // Select Button
                                            Surface(
                                                color = if (isSelected) Color(0xFF00E676) else Color(0xFF0284C7),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.clickable {
                                                    selectedCountry = country
                                                    Toast.makeText(context, "✓ ${country.flag} ${country.name} আপলোডের জন্য নির্বাচিত হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    showAllWorldCountriesDialog = false
                                                }
                                            ) {
                                                Text(
                                                    text = if (isSelected) "ACTIVE" else "সিলেক্ট",
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAllWorldCountriesDialog = false }) {
                        Text("বন্ধ করুন", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showApiKeyConfigDialog) {
            val v2State by com.example.service.PremiumNumberV2Manager.state.collectAsState()
            AlertDialog(
                onDismissRequest = { showApiKeyConfigDialog = false },
                containerColor = Color(0xFF101C2B),
                title = {
                    Text("SL Virtual Number API Key সেটিংস", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("slvirtualnumber.com এর সক্রিয় ডেভেলপার API Key দিন:", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                        OutlinedTextField(
                            value = customApiKeyInputText,
                            onValueChange = { customApiKeyInputText = it },
                            placeholder = { Text("sl_live_...", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF1E2D40),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("বর্তমান Key: ${v2State.apiKey.take(15)}...", color = Color(0xFF64748B), fontSize = 10.5.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customApiKeyInputText.isNotBlank()) {
                                com.example.service.PremiumNumberV2Manager.setApiKey(customApiKeyInputText)
                                Toast.makeText(context, "✓ API Key সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                showApiKeyConfigDialog = false
                            } else {
                                Toast.makeText(context, "API Key লিখুন", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("সেভ করুন", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyConfigDialog = false }) {
                        Text("বাতিল", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101C2B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 1. UNIX SMS Rate Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1. PREMIUM NUMBERS OTP RATE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.2f", unixState.otpRatePerSms)} ৳ / OTP",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (canUpdateOtpRate) {
                    // Quick adjusters row (-0.10, -0.01, Display Box, +0.01, +0.10)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                UnixSmsManager.adjustOtpRate(-0.10)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("-0.10", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        }

                        Button(
                            onClick = {
                                UnixSmsManager.adjustOtpRate(-0.01)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("-0.01", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        }

                        Surface(
                            color = Color(0xFF090E17),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .clickable {
                                    customRateInputText = String.format(java.util.Locale.US, "%.2f", unixState.otpRatePerSms)
                                    showCustomUnixRateDialog = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.2f", unixState.otpRatePerSms)} ৳",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                UnixSmsManager.adjustOtpRate(+0.01)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("+0.01", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                        }

                        Button(
                            onClick = {
                                UnixSmsManager.adjustOtpRate(+0.10)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("+0.10", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Chips and Custom Edit Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(0.30, 0.40, 0.48, 0.49, 0.50, 1.00).forEach { presetRate ->
                            val isSel = Math.abs(unixState.otpRatePerSms - presetRate) < 0.005
                            Surface(
                                color = if (isSel) Color(0xFF0284C7) else Color(0xFF0B141E),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D40)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        UnixSmsManager.setOtpRate(presetRate)
                                        WithdrawalManager.refreshBalances()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.2f", presetRate)}৳",
                                        color = if (isSel) Color.White else Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Custom rate dialog launcher chip
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.clickable {
                                customRateInputText = String.format(java.util.Locale.US, "%.2f", unixState.otpRatePerSms)
                                showCustomUnixRateDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("লিখুন", color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // World Countries Button for Premium Numbers (Rate & Upload Selection)
                    Button(
                        onClick = {
                            allCountriesSearchQuery = ""
                            showAllWorldCountriesDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🌍 বিশ্বের সকল দেশ (${ADMIN_PRESET_COUNTRIES.size} Countries - রেট ও আপলোড নির্বাচন)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Premium Numbers Rate: ${String.format(java.util.Locale.US, "%.2f", unixState.otpRatePerSms)} ৳", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = "🔒 রেট পরিবর্তন লক", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF1E2D40))
                Spacer(modifier = Modifier.height(14.dp))

                // 2. ZENEX SMS Rate Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2. KOP ENGINE (Api Number) OTP RATE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.2f", zenexRate)} ৳ / OTP",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick adjusters row for Zenex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            VirtualNumberManager.adjustZenexOtpRate(-0.10)
                            WithdrawalManager.refreshBalances()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("-0.10", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    }

                    Button(
                        onClick = {
                            VirtualNumberManager.adjustZenexOtpRate(-0.01)
                            WithdrawalManager.refreshBalances()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("-0.01", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    }

                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp)
                            .clickable {
                                customRateInputText = String.format(java.util.Locale.US, "%.2f", zenexRate)
                                showCustomZenexRateDialog = true
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.2f", zenexRate)} ৳",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            VirtualNumberManager.adjustZenexOtpRate(+0.01)
                            WithdrawalManager.refreshBalances()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("+0.01", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }

                    Button(
                        onClick = {
                            VirtualNumberManager.adjustZenexOtpRate(+0.10)
                            WithdrawalManager.refreshBalances()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("+0.10", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preset Chips and Custom Edit Button for Zenex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.30, 0.40, 0.48, 0.49, 0.50, 1.00).forEach { presetRate ->
                        val isSel = Math.abs(zenexRate - presetRate) < 0.005
                        Surface(
                            color = if (isSel) Color(0xFF059669) else Color(0xFF0B141E),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF34D399) else Color(0xFF1E2D40)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    VirtualNumberManager.setZenexOtpRate(presetRate)
                                    WithdrawalManager.refreshBalances()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.2f", presetRate)}৳",
                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF34D399)),
                        modifier = Modifier.clickable {
                            customRateInputText = String.format(java.util.Locale.US, "%.2f", zenexRate)
                            showCustomZenexRateDialog = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("লিখুন", color = Color(0xFF34D399), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF1E2D40))
                Spacer(modifier = Modifier.height(14.dp))

                // 3. KOP ENGINE 2 Rate Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "3. KOP ENGINE 2 (Virtual Engine 2) OTP RATE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = Color(0xFF7C3AED).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.2f", kop2Rate)} ৳ / OTP",
                            color = Color(0xFFA78BFA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (canUpdateOtpRate) {
                    // Quick adjusters row for Kop Engine 2 (-0.10, -0.01, Display Box, +0.01, +0.10)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                KopEngine2Manager.adjustKop2OtpRate(-0.10)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("-0.10", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        }

                        Button(
                            onClick = {
                                KopEngine2Manager.adjustKop2OtpRate(-0.01)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("-0.01", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        }

                        Surface(
                            color = Color(0xFF090E17),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6)),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .clickable {
                                    customRateInputText = String.format(java.util.Locale.US, "%.2f", kop2Rate)
                                    showCustomKop2RateDialog = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.2f", kop2Rate)} ৳",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                KopEngine2Manager.adjustKop2OtpRate(+0.01)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("+0.01", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                        }

                        Button(
                            onClick = {
                                KopEngine2Manager.adjustKop2OtpRate(+0.10)
                                WithdrawalManager.refreshBalances()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("+0.10", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Chips and Custom Edit Button for Kop Engine 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(0.30, 0.40, 0.48, 0.50, 0.70, 1.00).forEach { presetRate ->
                            val isSel = Math.abs(kop2Rate - presetRate) < 0.005
                            Surface(
                                color = if (isSel) Color(0xFF6D28D9) else Color(0xFF0B141E),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFFA78BFA) else Color(0xFF1E2D40)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        KopEngine2Manager.setKop2OtpRate(presetRate)
                                        WithdrawalManager.refreshBalances()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.2f", presetRate)}৳",
                                        color = if (isSel) Color.White else Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFA78BFA)),
                            modifier = Modifier.clickable {
                                customRateInputText = String.format(java.util.Locale.US, "%.2f", kop2Rate)
                                showCustomKop2RateDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("লিখুন", color = Color(0xFFA78BFA), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Kop Engine 2 Rate: ${String.format(java.util.Locale.US, "%.2f", kop2Rate)} ৳", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = "🔒 রেট পরিবর্তন লক", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF1E2D40))
                Spacer(modifier = Modifier.height(14.dp))

                // 4. PREMIUM NUMBER V2 (LIVE POOL) Rate Header
                val v2State by com.example.service.PremiumNumberV2Manager.state.collectAsState()
                val livePoolCountries = remember(v2State.liveConsoleFeed) {
                    v2State.liveConsoleFeed.map { 
                        Triple(it.flag, it.country, it.iso.uppercase(java.util.Locale.US)) 
                    }.distinctBy { it.third }
                }

                val activeEffectiveRate = if (selectedV2CountryCode == "ALL") {
                    premiumV2Rate
                } else {
                    com.example.service.PremiumNumberV2Manager.getRateForCountry(selectedV2CountryCode)
                }

                val selectedCountryInfo = remember(selectedV2CountryCode, livePoolCountries) {
                    livePoolCountries.find { it.third == selectedV2CountryCode }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "4. PREMIUM NUMBER V2 (LIVE POOL) OTP RATE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.2f", activeEffectiveRate)} ৳ / OTP",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (selectedV2CountryCode != "ALL") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF0B253A),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎯 নির্বাচিত দেশ: ${selectedCountryInfo?.first ?: "🌐"} ${selectedCountryInfo?.second ?: selectedV2CountryCode} ($selectedV2CountryCode)",
                                color = Color(0xFF7DD3FC),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "সব দেশে ফিরুন ✕",
                                color = Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { selectedV2CountryCode = "ALL" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (canUpdateOtpRate) {
                    // Quick Increments for Premium V2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val deltas = listOf(-0.10, -0.05, -0.01, 0.01, 0.05, 0.10)
                        val deltaColors = listOf(
                            Color(0xFFEF4444),
                            Color(0xFFF87171),
                            Color(0xFFFCA5A5),
                            Color(0xFF6EE7B7),
                            Color(0xFF34D399),
                            Color(0xFF10B981)
                        )
                        deltas.forEachIndexed { idx, delta ->
                            val label = if (delta > 0) "+${String.format(java.util.Locale.US, "%.2f", delta)}" else String.format(java.util.Locale.US, "%.2f", delta)
                            Button(
                                onClick = {
                                    val currentRate = if (selectedV2CountryCode == "ALL") premiumV2Rate else com.example.service.PremiumNumberV2Manager.getRateForCountry(selectedV2CountryCode)
                                    val newRate = Math.round((currentRate + delta).coerceAtLeast(0.01) * 100.0) / 100.0
                                    if (selectedV2CountryCode == "ALL") {
                                        com.example.service.PremiumNumberV2Manager.setOtpRate(newRate)
                                    } else {
                                        com.example.service.PremiumNumberV2Manager.setRateForCountry(selectedV2CountryCode, newRate)
                                    }
                                    com.example.service.WithdrawalManager.refreshBalances()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = deltaColors[idx])
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Chips and Custom Edit Button for Premium V2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(0.30, 0.40, 0.48, 0.50, 0.70, 1.00).forEach { presetRate ->
                            val isSel = Math.abs(activeEffectiveRate - presetRate) < 0.005
                            Surface(
                                color = if (isSel) Color(0xFF0284C7) else Color(0xFF0B141E),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D40)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (selectedV2CountryCode == "ALL") {
                                            com.example.service.PremiumNumberV2Manager.setOtpRate(presetRate)
                                        } else {
                                            com.example.service.PremiumNumberV2Manager.setRateForCountry(selectedV2CountryCode, presetRate)
                                        }
                                        com.example.service.WithdrawalManager.refreshBalances()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.2f", presetRate)}৳",
                                        color = if (isSel) Color.White else Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.clickable {
                                customRateInputText = String.format(java.util.Locale.US, "%.2f", activeEffectiveRate)
                                showCustomPremiumV2RateDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("লিখুন", color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Range Pool Active Countries for V2
                    Surface(
                        color = Color(0xFF081526),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌐", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Active Live Pool Countries",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (livePoolCountries.isNotEmpty()) "${livePoolCountries.size} Active (ক্লিক করে এডিট করুন)" else "Scanning...",
                                    color = Color(0xFF7DD3FC),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (livePoolCountries.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // First chip: All Countries
                                    val isAllSel = selectedV2CountryCode == "ALL"
                                    Surface(
                                        color = if (isAllSel) Color(0xFF0284C7) else Color(0xFF0B243B),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isAllSel) Color(0xFF38BDF8) else Color(0xFF0284C7).copy(alpha = 0.6f)),
                                        modifier = Modifier.clickable {
                                            selectedV2CountryCode = "ALL"
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🌐", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "সব দেশ (${String.format(java.util.Locale.US, "%.2f", premiumV2Rate)}৳)",
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isAllSel) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Each active live pool country
                                    livePoolCountries.forEach { (flag, country, iso) ->
                                        val isSel = selectedV2CountryCode == iso
                                        val cRate = com.example.service.PremiumNumberV2Manager.getRateForCountry(iso)
                                        Surface(
                                            color = if (isSel) Color(0xFF0284C7) else Color(0xFF0B243B),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF0284C7).copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable {
                                                selectedV2CountryCode = iso
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(flag, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "$country (${String.format(java.util.Locale.US, "%.2f", cRate)}৳)",
                                                    color = Color.White,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "লাইভ পুল স্ক্যান হচ্ছে... শুধুমাত্র সক্রিয় লাইভ পুলের দেশসমূহ রেট তালিকায় থাকবে",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 লাইভ পুলে থাকা যেকোনো দেশে ক্লিক করে তার একক রেট সেট করতে পারেন। এটি স্থায়ীভাবে সেভ থাকবে।",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search all world countries button to set any country rate
                    Button(
                        onClick = {
                            v2AllCountriesSearchQuery = ""
                            showV2AllWorldCountriesSearchDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🔍 বিশ্বের সকল দেশ খুঁজুন ও ওটিপি রেট সেট করুন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Premium Number V2 Rate: ${String.format(java.util.Locale.US, "%.2f", premiumV2Rate)} ৳", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = "🔒 রেট পরিবর্তন লক", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 2. Upload Numbers Card
        if (canUploadNumbers) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E324A)),
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UPLOAD PREMIUM NUMBERS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Gateway Selector: Lamix SMS vs Unix SMS
                var selectedGateway by remember { mutableStateOf(unixState.selectedUploadGateway) }

                Text(text = "1. SMS Gateway API নির্বাচন করুন (Select Gateway):", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isLamix = selectedGateway == "LAMIX"
                    Surface(
                        color = if (isLamix) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isLamix) Color(0xFF38BDF8) else Color(0xFF243B55)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedGateway = "LAMIX"
                                UnixSmsManager.setSelectedUploadGateway("LAMIX")
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚡ Lamix SMS",
                                color = if (isLamix) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = "Lamix Panel",
                                color = if (isLamix) Color(0xFF7DD3FC) else Color(0xFF64748B),
                                fontSize = 8.sp
                            )
                        }
                    }

                    val isUnix = selectedGateway == "UNIX"
                    Surface(
                        color = if (isUnix) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isUnix) Color(0xFF38BDF8) else Color(0xFF243B55)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedGateway = "UNIX"
                                UnixSmsManager.setSelectedUploadGateway("UNIX")
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🌐 Unix SMS",
                                color = if (isUnix) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = "Unix API",
                                color = if (isUnix) Color(0xFF7DD3FC) else Color(0xFF64748B),
                                fontSize = 8.sp
                            )
                        }
                    }

                    val isHadi = selectedGateway == "HADI"
                    Surface(
                        color = if (isHadi) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isHadi) Color(0xFF00E5FF) else Color(0xFF243B55)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedGateway = "HADI"
                                UnixSmsManager.setSelectedUploadGateway("HADI")
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🔥 Hadi SMS",
                                color = if (isHadi) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = "CR Hadi (1s)",
                                color = if (isHadi) Color(0xFF7DD3FC) else Color(0xFF64748B),
                                fontSize = 8.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Country Selector Dropdown with Search Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "2. দেশ নির্বাচন করুন (Select Country):", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = {
                            countrySearchQuery = ""
                            showCountrySearchDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Search A-Z (${ADMIN_PRESET_COUNTRIES.size})", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                val currentCountryRate = UnixSmsManager.getRateForCountry(selectedCountry.code)
                Box {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF243B55)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCountrySearchDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = selectedCountry.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${selectedCountry.name} (${selectedCountry.dialCode})",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Country Code: ${selectedCountry.code}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8))
                                ) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.2f", currentCountryRate)} ৳ / OTP",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Change 🔍", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick OTP Rate Adjuster for Selected Country (0.48, 0.49, 0.50 ৳)
                Surface(
                    color = Color(0xFF0C1622),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E3248)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎯 ${selectedCountry.name} ওটিপি রেট ফিক্স করুন:",
                                color = Color(0xFF81D4FA),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.2f", currentCountryRate)} ৳",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -0.01 button
                            Surface(
                                color = Color(0xFF162332),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF283E56)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val newRate = Math.round(maxOf(0.01, currentCountryRate - 0.01) * 100.0) / 100.0
                                        UnixSmsManager.setCountryRate(selectedCountry.code, newRate)
                                        VibrationHelper.vibrateClick(context)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("− 0.01", color = Color(0xFFF87171), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 0.48, 0.49, 0.50 preset buttons
                            listOf(0.48, 0.49, 0.50).forEach { r ->
                                val isSelected = Math.abs(currentCountryRate - r) < 0.005
                                Surface(
                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0F1B28),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF233950)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            UnixSmsManager.setCountryRate(selectedCountry.code, r)
                                            VibrationHelper.vibrateClick(context)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 5.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.2f", r)} ৳",
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // +0.01 button
                            Surface(
                                color = Color(0xFF162332),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF283E56)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val newRate = Math.round((currentCountryRate + 0.01) * 100.0) / 100.0
                                        UnixSmsManager.setCountryRate(selectedCountry.code, newRate)
                                        VibrationHelper.vibrateClick(context)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("+ 0.01", color = Color(0xFF34D399), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Search All Countries Dialog
                if (showCountrySearchDialog) {
                    val filteredList = remember(countrySearchQuery) {
                        AllCountriesProvider.search(countrySearchQuery)
                    }

                    AlertDialog(
                        onDismissRequest = { showCountrySearchDialog = false },
                        containerColor = Color(0xFF0F1A28),
                        title = {
                            Text(text = "দেশ নির্বাচন করুন (All Countries)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = countrySearchQuery,
                                    onValueChange = { countrySearchQuery = it },
                                    placeholder = { Text("Search by country name or dial code (+880, +1...)", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF1E2D40),
                                        focusedContainerColor = Color(0xFF090E17),
                                        unfocusedContainerColor = Color(0xFF090E17)
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredList) { c ->
                                        val cRate = UnixSmsManager.getRateForCountry(c.code)
                                        Surface(
                                            color = if (selectedCountry.code == c.code) Color(0xFF0284C7).copy(alpha = 0.3f) else Color(0xFF0B1420),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (selectedCountry.code == c.code) Color(0xFF38BDF8) else Color(0xFF1C2C3E)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCountry = c
                                                    showCountrySearchDialog = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = c.flag, fontSize = 18.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = c.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                Text(text = c.dialCode, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "${String.format(java.util.Locale.US, "%.2f", cRate)} ৳",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showCountrySearchDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))) {
                                Text("Close", color = Color.White)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Service Selector: Facebook, Instagram, WhatsApp
                Text(text = "2. সার্ভিস নির্বাচন করুন (Service):", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ADMIN_SERVICES.forEach { serviceName ->
                        val isSelected = selectedService == serviceName
                        val serviceColor = when (serviceName) {
                            "Facebook" -> Color(0xFF1877F2)
                            "Instagram" -> Color(0xFFE1306C)
                            "WhatsApp" -> Color(0xFF25D366)
                            else -> Color(0xFFFF9100) // OTP Work
                        }

                        Surface(
                            color = if (isSelected) serviceColor.copy(alpha = 0.25f) else Color(0xFF090E17),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) serviceColor else Color(0xFF243B55)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedService = serviceName }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serviceName,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Number Type Selector: New Fresh Number vs PC Clone Number
                Text(text = "3. নাম্বার টাইপ নির্বাচন করুন (Fresh vs PC Clone):", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Pair("Fresh", "🟢 New Fresh Number"),
                        Pair("PC Clone", "🖥️ PC Clone Number")
                    ).forEach { (tKey, tLabel) ->
                        val isSel = selectedNumberType == tKey
                        val tColor = if (tKey == "Fresh") Color(0xFF00E676) else Color(0xFF38BDF8)
                        Surface(
                            color = if (isSel) tColor.copy(alpha = 0.22f) else Color(0xFF090E17),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.2.dp, if (isSel) tColor else Color(0xFF243B55)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedNumberType = tKey }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tLabel,
                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Number Input + File Upload
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "4. নাম্বার লিখুন বা ফাইল আপলোড করুন:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)

                    // File upload button
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch("text/*")
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload .txt/.csv", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = numbersInput,
                    onValueChange = { numbersInput = it },
                    placeholder = {
                        Text(
                            text = "প্রতি লাইনে একটি নাম্বার দিন বা কমা দিয়ে পেস্ট করুন:\n+12025550199\n+12025550188\nবা 12025550177, 12025550166...",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Upload Button
                Button(
                    onClick = {
                        if (numbersInput.isBlank()) {
                            Toast.makeText(context, "নাম্বার দিন অথবা ফাইল আপলোড করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isUploading = true
                        val addedCount = UnixSmsManager.addNumbersBatch(
                            rawContent = numbersInput,
                            countryCode = selectedCountry.code,
                            countryName = selectedCountry.name,
                            flag = selectedCountry.flag,
                            service = selectedService,
                            gateway = selectedGateway,
                            customRate = currentCountryRate,
                            numberType = selectedNumberType
                        )
                        isUploading = false
                        if (addedCount > 0) {
                            val typeLabel = if (selectedNumberType == "Fresh") "Fresh" else "PC Clone"
                            Toast.makeText(context, "✓ $addedCount টি নতুন $selectedCountry.name [$typeLabel] নাম্বার সফলভাবে আপলোড হয়েছে!", Toast.LENGTH_LONG).show()
                            numbersInput = ""
                            VibrationHelper.vibrateSuccess(context)
                        } else {
                            Toast.makeText(context, "কোনো নতুন বৈধ নাম্বার পাওয়া যায়নি বা ইতিমধ্যে আছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    enabled = !isUploading
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPLOAD NUMBERS TO UNIX SMS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

        // 3. Clear / Delete Numbers Card
        if (canDeleteNumbers) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF332029)),
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CLEAR / DELETE NUMBERS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "কোন দেশের নাম্বার ডিলিট করবেন নির্বাচন করুন:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Target Country Selector
                Box {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF4C1D2F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteCountryDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayName = if (deleteTargetCountry == "ALL") "সব দেশের নাম্বার (ALL COUNTRIES)" else {
                                val c = ADMIN_PRESET_COUNTRIES.find { it.code == deleteTargetCountry }
                                "${c?.flag ?: "🌐"} ${c?.name ?: deleteTargetCountry}"
                            }
                            Text(text = displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "▼", color = Color(0xFF64748B), fontSize = 10.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = deleteCountryDropdownExpanded,
                        onDismissRequest = { deleteCountryDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF0F1A28))
                    ) {
                        DropdownMenuItem(
                            text = { Text("🌐 সব দেশের নাম্বার (ALL COUNTRIES)", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold) },
                            onClick = {
                                deleteTargetCountry = "ALL"
                                deleteCountryDropdownExpanded = false
                            }
                        )

                        val activeCountryCodes = unixState.uploadedNumbers.map { it.countryCode }.distinct()
                        activeCountryCodes.forEach { code ->
                            val c = ADMIN_PRESET_COUNTRIES.find { it.code == code }
                            DropdownMenuItem(
                                text = { Text("${c?.flag ?: "🌐"} ${c?.name ?: code} (${unixState.uploadedNumbers.count { it.countryCode == code }} nos)", color = Color.White) },
                                onClick = {
                                    deleteTargetCountry = code
                                    deleteCountryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (deleteTargetCountry == "ALL") "CLEAR ALL NUMBERS" else "CLEAR $deleteTargetCountry NUMBERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                containerColor = Color(0xFF141D2B),
                title = {
                    Text(text = "⚠️ নাম্বার ডিলিট নিশ্চিতকরণ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Text(
                        text = if (deleteTargetCountry == "ALL") {
                            "আপনি কি নিশ্চিত যে আপনি সমস্ত আপলোড করা নাম্বার সম্পূর্ণ মুছে ফেলতে চান?"
                        } else {
                            "আপনি কি নিশ্চিত যে $deleteTargetCountry এর সকল নাম্বার মুছে ফেলতে চান?"
                        },
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            UnixSmsManager.deleteNumbersByCountry(deleteTargetCountry)
                            showDeleteConfirmDialog = false
                            Toast.makeText(context, "✓ নাম্বার সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                    ) {
                        Text("হ্যাঁ, ডিলিট করুন")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("বাতিল", color = Color.White)
                    }
                }
            )
        }
    }

        // 4. Uploaded Numbers List Card
        val addedCountryCodes = remember(unixState.uploadedNumbers) {
            unixState.uploadedNumbers.map { it.countryCode }.distinct()
        }

        val countryNumbers = remember(unixState.uploadedNumbers, selectedUploadedCountryFilter) {
            if (selectedUploadedCountryFilter == "ALL") unixState.uploadedNumbers
            else unixState.uploadedNumbers.filter { it.countryCode.equals(selectedUploadedCountryFilter, ignoreCase = true) }
        }
        val availableCount = remember(countryNumbers) {
            countryNumbers.count { !it.isUsed && it.otpCode.isNullOrEmpty() }
        }
        val usedCount = remember(countryNumbers) {
            countryNumbers.count { it.isUsed && it.otpCode.isNullOrEmpty() }
        }
        val otpRcvCount = remember(countryNumbers) {
            countryNumbers.count { !it.otpCode.isNullOrEmpty() }
        }

        val filteredNumbers = remember(unixState.uploadedNumbers, filterQuery, selectedUploadedCountryFilter, statusFilter) {
            unixState.uploadedNumbers.filter { item ->
                val matchesCountry = selectedUploadedCountryFilter == "ALL" ||
                        item.countryCode.equals(selectedUploadedCountryFilter, ignoreCase = true)

                val matchesStatus = when (statusFilter) {
                    "AVAILABLE" -> !item.isUsed && item.otpCode.isNullOrEmpty()
                    "USED" -> item.isUsed && item.otpCode.isNullOrEmpty()
                    "OTP_RCV" -> !item.otpCode.isNullOrEmpty()
                    else -> true
                }

                val matchesQuery = if (filterQuery.isBlank()) true else {
                    item.number.contains(filterQuery) ||
                            item.countryName.contains(filterQuery, ignoreCase = true) ||
                            item.countryCode.contains(filterQuery, ignoreCase = true) ||
                            item.service.contains(filterQuery, ignoreCase = true) ||
                            (item.otpCode?.contains(filterQuery) == true)
                }

                matchesCountry && matchesStatus && matchesQuery
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "UPLOADED NUMBERS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${unixState.uploadedNumbers.size}",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (selectedUploadedCountryFilter != "ALL" || statusFilter != "ALL" || filterQuery.isNotEmpty()) {
                        Text(
                            text = "Filtered: ${filteredNumbers.size}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Search Bar with Clear Icon
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("নাম্বার বা সার্ভিস খুঁজুন (যেমন: +1, Facebook)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (filterQuery.isNotEmpty()) {
                            IconButton(onClick = { filterQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Auto-Show Countries Chips (Horizontal Scroll)
                Text(
                    text = "দেশ নির্বাচন করুন (যেসব দেশের নাম্বার যুক্ত আছে):",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ALL Countries Chip
                    val isAllSelected = selectedUploadedCountryFilter == "ALL"
                    Surface(
                        color = if (isAllSelected) Color(0xFF38BDF8).copy(alpha = 0.25f) else Color(0xFF0D1522),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isAllSelected) Color(0xFF38BDF8) else Color(0xFF1E2D40)),
                        modifier = Modifier.clickable { selectedUploadedCountryFilter = "ALL" }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌐 ALL", fontSize = 11.sp, fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal, color = if (isAllSelected) Color.White else Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("(${unixState.uploadedNumbers.size})", fontSize = 10.sp, color = if (isAllSelected) Color(0xFF38BDF8) else Color(0xFF64748B))
                        }
                    }

                    // Individual Country Chips
                    addedCountryCodes.forEach { code ->
                        val isSelected = selectedUploadedCountryFilter == code
                        val sampleItem = unixState.uploadedNumbers.firstOrNull { it.countryCode == code }
                        val flag = sampleItem?.flag ?: "🌐"
                        val count = unixState.uploadedNumbers.count { it.countryCode == code }

                        Surface(
                            color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.3f) else Color(0xFF0D1522),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color(0xFF1E2D40)),
                            modifier = Modifier.clickable {
                                selectedUploadedCountryFilter = if (selectedUploadedCountryFilter == code) "ALL" else code
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(flag, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    code,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("($count)", fontSize = 10.sp, color = if (isSelected) Color(0xFF818CF8) else Color(0xFF64748B))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Status Filters: ALL, AVAILABLE (Green), USED (Red), OTP RCV (Yellow)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status ALL
                    Surface(
                        color = if (statusFilter == "ALL") Color(0xFF1E2D40) else Color(0xFF090E17),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (statusFilter == "ALL") Color(0xFF38BDF8) else Color(0xFF1E2D40)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { statusFilter = "ALL" }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ALL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${countryNumbers.size}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF94A3B8))
                        }
                    }

                    // Status AVAILABLE (Green)
                    Surface(
                        color = if (statusFilter == "AVAILABLE") Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF090E17),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (statusFilter == "AVAILABLE") Color(0xFF10B981) else Color(0xFF1E2D40)),
                        modifier = Modifier
                            .weight(1.2f)
                            .clickable { statusFilter = "AVAILABLE" }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🟢 AVAILABLE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                            Text("$availableCount", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                        }
                    }

                    // Status USED (Red)
                    Surface(
                        color = if (statusFilter == "USED") Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFF090E17),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (statusFilter == "USED") Color(0xFFEF4444) else Color(0xFF1E2D40)),
                        modifier = Modifier
                            .weight(1.1f)
                            .clickable { statusFilter = "USED" }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔴 USED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                            Text("$usedCount", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF87171))
                        }
                    }

                    // Status OTP RCV (Yellow)
                    Surface(
                        color = if (statusFilter == "OTP_RCV") Color(0xFFEAB308).copy(alpha = 0.25f) else Color(0xFF090E17),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (statusFilter == "OTP_RCV") Color(0xFFEAB308) else Color(0xFF1E2D40)),
                        modifier = Modifier
                            .weight(1.2f)
                            .clickable { statusFilter = "OTP_RCV" }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🟡 OTP RCV", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFACC15))
                            Text("$otpRcvCount", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFACC15))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3.5. Batch Deletion & Quick Maintenance Actions
                var showCountryDeleteConfirmDialog by remember { mutableStateOf(false) }
                var showClearAllConfirmDialog by remember { mutableStateOf(false) }

                val targetDeleteCount = remember(selectedUploadedCountryFilter, unixState.uploadedNumbers) {
                    if (selectedUploadedCountryFilter == "ALL") unixState.uploadedNumbers.size
                    else unixState.uploadedNumbers.count { it.countryCode.equals(selectedUploadedCountryFilter, ignoreCase = true) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Delete for selected country (or all if ALL selected)
                    Button(
                        onClick = {
                            if (targetDeleteCount > 0) {
                                showCountryDeleteConfirmDialog = true
                            } else {
                                Toast.makeText(context, "ডিলিট করার মতো কোনো নাম্বার নেই", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = canDeleteNumbers && targetDeleteCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1.3f).height(38.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (selectedUploadedCountryFilter == "ALL") "ক্লিয়ার অল ($targetDeleteCount)"
                            else "ডিলিট $selectedUploadedCountryFilter ($targetDeleteCount)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Auto Purge Corrupted / Invalid Numbers
                    OutlinedButton(
                        onClick = {
                            val purged = UnixSmsManager.purgeInvalidUploadedNumbers { ok ->
                                Toast.makeText(context, "✓ ডাটাবেস ও ক্লাউড সিঙ্ক সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                            Toast.makeText(context, "⚡ $purged টি ভুল/নাল নাম্বার মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        enabled = canDeleteNumbers,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1.1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("অটো ক্লিন ভুল", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Clear ALL numbers button (if not already selecting ALL)
                    if (selectedUploadedCountryFilter != "ALL") {
                        Button(
                            onClick = { showClearAllConfirmDialog = true },
                            enabled = canDeleteNumbers && unixState.uploadedNumbers.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1.1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("সব ক্লিয়ার", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Batch Deletion Confirmation Dialogs
                if (showCountryDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showCountryDeleteConfirmDialog = false },
                        title = {
                            Text(
                                text = "⚠️ নাম্বার ডিলিট ও ডাটাবেস লাইভ সিঙ্ক",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        },
                        text = {
                            Text(
                                text = if (selectedUploadedCountryFilter == "ALL") {
                                    "আপনি কি নিশ্চিত যে সমস্ত ($targetDeleteCount টি) নাম্বার ডাটাবেস ও সকল ইউজারদের অ্যাপ থেকে সম্পূর্ণ ডিলিট করতে চান?"
                                } else {
                                    "আপনি কি নিশ্চিত যে $selectedUploadedCountryFilter দেশের সকল ($targetDeleteCount টি) নাম্বার ডাটাবেস ও সকল ইউজারদের থেকে মুছে ফেলতে চান?"
                                },
                                fontSize = 12.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val targetFilter = selectedUploadedCountryFilter
                                    showCountryDeleteConfirmDialog = false
                                    UnixSmsManager.deleteNumbersByCountry(targetFilter) { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "✓ $targetFilter এর সকল নাম্বার ডাটাবেস ও সকল ইউজারদের থেকে মুছে লাইভ সিঙ্ক করা হয়েছে!"
                                            else "✓ নাম্বার রিমুভ ও ক্লাউডে পুশ করা হয়েছে",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("হ্যাঁ, ডিলিট করুন")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showCountryDeleteConfirmDialog = false }) {
                                Text("বাতিল")
                            }
                        }
                    )
                }

                if (showClearAllConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearAllConfirmDialog = false },
                        title = {
                            Text(
                                text = "🚨 সমস্ত নাম্বার ক্লিয়ার (Clear ALL)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFEF4444)
                            )
                        },
                        text = {
                            Text(
                                text = "আপনি কি নিশ্চিত যে ডাটাবেস থেকে সব দেশের মোট ${unixState.uploadedNumbers.size} টি আপলোড করা নাম্বার সম্পূর্ণ মুছে ফেলতে চান? সকল ইউজারের অ্যাপ থেকেও সাথে সাথে মুছে যাবে।",
                                fontSize = 12.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showClearAllConfirmDialog = false
                                    UnixSmsManager.clearAllUploadedNumbers { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "✓ সমস্ত নাম্বার ডাটাবেস ও সকল ডিভাইস থেকে সম্পূর্ণ ক্লিয়ার হয়েছে!"
                                            else "✓ সমস্ত নাম্বার লোকাল ও ক্লাউড থেকে ক্লিয়ার করা হয়েছে",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("হ্যাঁ, সব ক্লিয়ার করুন")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showClearAllConfirmDialog = false }) {
                                Text("বাতিল")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Numbers List
                if (filteredNumbers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unixState.uploadedNumbers.isEmpty()) "কোনো নাম্বার নেই। উপরে নাম্বার আপলোড করুন।"
                            else "কোনো ম্যাচিং নাম্বার পাওয়া যায়নি।",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredNumbers.take(60).forEach { item ->
                            UploadedNumberRowItem(
                                item = item,
                                canDelete = canDeleteNumbers,
                                onDelete = {
                                    UnixSmsManager.deleteSingleNumber(item.id) { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "✓ ডাটাবেস ও ইউজারদের থেকে মুছে লাইভ সিঙ্ক হয়েছে: ${item.number}"
                                            else "ডিলিট করা হয়েছে: ${item.number}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onCopy = {
                                    ClipboardHelper.copyToClipboard(context, item.number, "Phone Number")
                                    Toast.makeText(context, "নাম্বার কপি করা হয়েছে: ${item.number}", Toast.LENGTH_SHORT).show()
                                },
                                onCopyOtp = {
                                    item.otpCode?.let { otp ->
                                        ClipboardHelper.copyToClipboard(context, otp, "OTP Code")
                                        Toast.makeText(context, "✓ OTP কোড কপি করা হয়েছে: $otp", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        if (filteredNumbers.size > 60) {
                            Text(
                                text = "... আরও ${filteredNumbers.size - 60} টি নাম্বার রয়েছে",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDataMaintenanceCard(
    context: Context,
    otpRecordCount: Int,
    totalOtpAllTime: Int,
    withdrawalCount: Int,
    uploadedNumberCount: Int,
    canManage: Boolean = true
) {
    var showConfirmOtpPurge by remember { mutableStateOf(false) }
    var showConfirmWithdrawPurge by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ডাটাবেস ও হিস্ট্রি ক্লিনিং (Data Maintenance)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Auto-Cleaner",
                        color = Color(0xFFF59E0B),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "২ মাসের বেশি পুরোনো ডাটা এবং ভুল/উল্টাপাল্টা ডাটা স্বয়ংক্রিয়ভাবে মুছে ডাটাবেসকে ফাস্ট ও হালকা রাখুন।",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Current Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("OTP হিস্ট্রি", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text("$otpRecordCount টি", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    }
                }
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("উইথড্র হিস্ট্রি", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text("$withdrawalCount টি", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ডাটাবেস নাম্বার", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text("$uploadedNumberCount টি", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. One-Tap Auto Purge Corrupted/Invalid Data
            Button(
                onClick = {
                    val pOtp = OtpHistoryManager.purgeCorruptedOtpRecords()
                    val pWith = WithdrawalManager.purgeCorruptedWithdrawals()
                    val pNum = UnixSmsManager.purgeInvalidUploadedNumbers()
                    Toast.makeText(
                        context,
                        "⚡ উল্টাপাল্টা ডাটা ক্লিন সম্পন্ন! (OTP: $pOtp, উইথড্র: $pWith, নাম্বার: $pNum)",
                        Toast.LENGTH_LONG
                    ).show()
                },
                enabled = canManage,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("⚡ অটো ক্লিন উল্টাপাল্টা ও করাপ্টেড ডাটা", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 60+ Days Old Cleanups (OTP & Withdraw)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete OTPs > 2 Months (60 days)
                OutlinedButton(
                    onClick = { showConfirmOtpPurge = true },
                    enabled = canManage,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("২+ মাসের OTP মুছুন", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                // Delete Withdraws > 2 Months (60 days)
                OutlinedButton(
                    onClick = { showConfirmWithdrawPurge = true },
                    enabled = canManage,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("২+ মাসের উইথড্র মুছুন", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Confirm Dialogs
            if (showConfirmOtpPurge) {
                AlertDialog(
                    onDismissRequest = { showConfirmOtpPurge = false },
                    title = { Text("⚠️ ২ মাসের পুরোনো OTP হিস্ট্রি ডিলিট") },
                    text = { Text("আপনি কি নিশ্চিত যে ৬০ দিন (২ মাস) এর বেশি পুরোনো সকল OTP হিস্টোরি স্থায়ীভাবে মুছে ফেলতে চান?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val removed = OtpHistoryManager.purgeOldOtpRecords(60)
                                showConfirmOtpPurge = false
                                Toast.makeText(context, "✓ $removed টি পুরোনো OTP রেকর্ড ডিলিট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) { Text("হ্যাঁ, ডিলিট করুন") }
                    },
                    dismissButton = { OutlinedButton(onClick = { showConfirmOtpPurge = false }) { Text("বাতিল") } }
                )
            }

            if (showConfirmWithdrawPurge) {
                AlertDialog(
                    onDismissRequest = { showConfirmWithdrawPurge = false },
                    title = { Text("⚠️ ২ মাসের পুরোনো উইথড্র হিস্ট্রি ডিলিট") },
                    text = { Text("আপনি কি নিশ্চিত যে ৬০ দিন (২ মাস) এর বেশি পুরোনো সমাপ্ত/অনুমোদিত উইথড্র রেকর্ড মুছে ফেলতে চান? (পেন্ডিং রিকুয়েস্ট সংরক্ষিত থাকবে)") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val removed = WithdrawalManager.purgeOldWithdrawalRequests(60)
                                showConfirmWithdrawPurge = false
                                Toast.makeText(context, "✓ $removed টি পুরোনো উইথড্র রেকর্ড ডিলিট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) { Text("হ্যাঁ, ডিলিট করুন") }
                    },
                    dismissButton = { OutlinedButton(onClick = { showConfirmWithdrawPurge = false }) { Text("বাতিল") } }
                )
            }
        }
    }
}

@Composable
fun UploadedNumberRowItem(
    item: UploadedNumberItem,
    canDelete: Boolean = true,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCopyOtp: () -> Unit
) {
    val hasOtp = !item.otpCode.isNullOrEmpty()
    val isUsed = item.isUsed

    // Color definitions according to state:
    // Available -> Green
    // Used -> Red mark
    // OTP Rcv -> Yellow mark
    val borderColor = when {
        hasOtp -> Color(0xFFEAB308)
        isUsed -> Color(0xFFEF4444).copy(alpha = 0.6f)
        else -> Color(0xFF10B981).copy(alpha = 0.5f)
    }

    val backgroundColor = when {
        hasOtp -> Color(0xFF1C1905)
        isUsed -> Color(0xFF160B0D)
        else -> Color(0xFF061410)
    }

    val numberTextColor = when {
        hasOtp -> Color(0xFFFDE047)
        isUsed -> Color(0xFFFCA5A5)
        else -> Color(0xFF34D399)
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.flag, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = item.number,
                            color = numberTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${item.service} • ${item.countryCode}",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            // Gateway Badge
                            Surface(
                                color = if (item.gateway.equals("LAMIX", ignoreCase = true)) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF8B5CF6).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (item.gateway.equals("LAMIX", ignoreCase = true)) Color(0xFF38BDF8) else Color(0xFFA78BFA))
                            ) {
                                Text(
                                    text = if (item.gateway.equals("LAMIX", ignoreCase = true)) "⚡ LAMIX" else "🌐 UNIX",
                                    color = if (item.gateway.equals("LAMIX", ignoreCase = true)) Color(0xFF38BDF8) else Color(0xFFA78BFA),
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            if (item.customRate != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.2f", item.customRate)} ৳",
                                    color = Color(0xFF34D399),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))

                            // Badges
                            when {
                                hasOtp -> {
                                    Surface(
                                        color = Color(0xFFEAB308).copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEAB308))
                                    ) {
                                        Text(
                                            text = "🟡 OTP RCV: ${item.otpCode}",
                                            color = Color(0xFFFACC15),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                isUsed -> {
                                    Surface(
                                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                                    ) {
                                        Text(
                                            text = "🔴 USED",
                                            color = Color(0xFFF87171),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, Color(0xFF10B981))
                                    ) {
                                        Text(
                                            text = "🟢 AVAILABLE",
                                            color = Color(0xFF34D399),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasOtp) {
                        IconButton(onClick = onCopyOtp, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Bolt, contentDescription = "Copy OTP", tint = Color(0xFFFACC15), modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF43F5E), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // If OTP received, show full OTP row
            if (hasOtp) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF2E2405),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAB308).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "OTP:", color = Color(0xFFFACC15), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${item.otpCode}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Button(
                            onClick = onCopyOtp,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCA8A04)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("COPY OTP", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Withdraw Requests Management Card
 */
@Composable
fun AdminWithdrawRequestsCard(
    context: Context,
    withdrawState: com.example.service.WithdrawalState
) {
    var filterStatus by remember { mutableStateOf("ALL") } // "ALL", "PENDING", "APPROVED", "REJECTED"
    var searchQuery by remember { mutableStateOf("") }

    val limits by com.example.service.WithdrawalManager.limits.collectAsState()
    var editLimitsModalOpen by remember { mutableStateOf(false) }
    var binanceLimitInput by remember(limits) { mutableStateOf(String.format(java.util.Locale.US, "%.0f", limits.minBinance)) }
    var bkashLimitInput by remember(limits) { mutableStateOf(String.format(java.util.Locale.US, "%.0f", limits.minBkash)) }
    var nagadLimitInput by remember(limits) { mutableStateOf(String.format(java.util.Locale.US, "%.0f", limits.minNagad)) }
    var rocketLimitInput by remember(limits) { mutableStateOf(String.format(java.util.Locale.US, "%.0f", limits.minRocket)) }

    val pendingCount = withdrawState.allRequests.count { it.status == "PENDING" }
    val approvedCount = withdrawState.allRequests.count { it.status == "APPROVED" }
    val rejectedCount = withdrawState.allRequests.count { it.status == "REJECTED" }
    val totalPendingTk = withdrawState.allRequests.filter { it.status == "PENDING" }.sumOf { it.amount }

    val filteredList = remember(withdrawState.allRequests, filterStatus, searchQuery) {
        withdrawState.allRequests.filter { req ->
            val matchesStatus = when (filterStatus) {
                "PENDING" -> req.status == "PENDING"
                "APPROVED" -> req.status == "APPROVED"
                "REJECTED" -> req.status == "REJECTED"
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                req.userEmail.contains(searchQuery, ignoreCase = true) ||
                        req.accountNumber.contains(searchQuery, ignoreCase = true) ||
                        req.method.contains(searchQuery, ignoreCase = true)
            }
            matchesStatus && matchesSearch
        }
    }

    if (editLimitsModalOpen) {
        AlertDialog(
            onDismissRequest = { editLimitsModalOpen = false },
            containerColor = Color(0xFF0F1A28),
            shape = RoundedCornerShape(16.dp),
            title = { Text("উইথড্র লিমিট পরিবর্তন (Set Limits)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = binanceLimitInput,
                        onValueChange = { binanceLimitInput = it },
                        label = { Text("Binance সর্বনিম্ন টাকা (BDT)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFF0B90B), unfocusedBorderColor = Color(0xFF334155))
                    )
                    OutlinedTextField(
                        value = bkashLimitInput,
                        onValueChange = { bkashLimitInput = it },
                        label = { Text("bKash সর্বনিম্ন টাকা (BDT)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFE2136E), unfocusedBorderColor = Color(0xFF334155))
                    )
                    OutlinedTextField(
                        value = nagadLimitInput,
                        onValueChange = { nagadLimitInput = it },
                        label = { Text("Nagad সর্বনিম্ন টাকা (BDT)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFF7931A), unfocusedBorderColor = Color(0xFF334155))
                    )
                    OutlinedTextField(
                        value = rocketLimitInput,
                        onValueChange = { rocketLimitInput = it },
                        label = { Text("Rocket সর্বনিম্ন টাকা (BDT)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF8C30F5), unfocusedBorderColor = Color(0xFF334155))
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val b = binanceLimitInput.toDoubleOrNull() ?: limits.minBinance
                        val bk = bkashLimitInput.toDoubleOrNull() ?: limits.minBkash
                        val n = nagadLimitInput.toDoubleOrNull() ?: limits.minNagad
                        val r = rocketLimitInput.toDoubleOrNull() ?: limits.minRocket
                        com.example.service.WithdrawalManager.updateLimits(b, bk, n, r)
                        editLimitsModalOpen = false
                        Toast.makeText(context, "উইথড্র লিমিট সফলভাবে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("সেভ করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editLimitsModalOpen = false }) {
                    Text("বাতিল", color = Color.White)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 0. Withdrawal Limits Control Card (Admin can increase/decrease minimum withdraw limits)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A28)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF1E354F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "উইথড্র লিমিট নিয়ন্ত্রণ (WITHDRAW LIMITS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = { editLimitsModalOpen = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8))
                    ) {
                        Text("কাস্টম লিমিট ✏️", fontSize = 9.5.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("Binance", limits.minBinance, Color(0xFFF0B90B)),
                        Triple("bKash", limits.minBkash, Color(0xFFE2136E)),
                        Triple("Nagad", limits.minNagad, Color(0xFFF7931A)),
                        Triple("Rocket", limits.minRocket, Color(0xFF8C30F5))
                    ).forEach { (mName, mMin, mCol) ->
                        Surface(
                            color = Color(0xFF070E18),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, mCol.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = mName, fontSize = 10.sp, color = mCol, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "৳${String.format(java.util.Locale.US, "%.0f", mMin)}",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        color = Color(0xFF1E2D40),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.size(22.dp).clickable {
                                            val newBinance = if (mName == "Binance") (limits.minBinance - 5).coerceAtLeast(1.0) else limits.minBinance
                                            val newBkash = if (mName == "bKash") (limits.minBkash - 5).coerceAtLeast(1.0) else limits.minBkash
                                            val newNagad = if (mName == "Nagad") (limits.minNagad - 5).coerceAtLeast(1.0) else limits.minNagad
                                            val newRocket = if (mName == "Rocket") (limits.minRocket - 5).coerceAtLeast(1.0) else limits.minRocket
                                            com.example.service.WithdrawalManager.updateLimits(newBinance, newBkash, newNagad, newRocket)
                                            Toast.makeText(context, "$mName লিমিট কমানো হয়েছে: ৳${String.format(java.util.Locale.US, "%.0f", if (mName == "Binance") newBinance else if (mName == "bKash") newBkash else if (mName == "Nagad") newNagad else newRocket)}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("-5", color = Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Surface(
                                        color = Color(0xFF1E2D40),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.size(22.dp).clickable {
                                            val newBinance = if (mName == "Binance") (limits.minBinance + 5) else limits.minBinance
                                            val newBkash = if (mName == "bKash") (limits.minBkash + 5) else limits.minBkash
                                            val newNagad = if (mName == "Nagad") (limits.minNagad + 5) else limits.minNagad
                                            val newRocket = if (mName == "Rocket") (limits.minRocket + 5) else limits.minRocket
                                            com.example.service.WithdrawalManager.updateLimits(newBinance, newBkash, newNagad, newRocket)
                                            Toast.makeText(context, "$mName লিমিট বাড়ানো হয়েছে: ৳${String.format(java.util.Locale.US, "%.0f", if (mName == "Binance") newBinance else if (mName == "bKash") newBkash else if (mName == "Nagad") newNagad else newRocket)}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("+5", color = Color(0xFF00E676), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Summary Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminStatPill(
                title = "PENDING",
                count = "$pendingCount (৳${String.format(java.util.Locale.US, "%.0f", totalPendingTk)})",
                color = Color(0xFFFFD600),
                modifier = Modifier.weight(1.3f)
            )
            AdminStatPill(
                title = "APPROVED",
                count = approvedCount.toString(),
                color = Color(0xFF00E676),
                modifier = Modifier.weight(1f)
            )
            AdminStatPill(
                title = "REJECTED",
                count = rejectedCount.toString(),
                color = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
        }

        // Main Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101C2B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E354F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "USER WITHDRAW REQUESTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = Color(0xFFFFD600).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${filteredList.size} REQUESTS",
                            color = Color(0xFFFFD600),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ইউজার ইমেইল বা নাম্বার দিয়ে খুঁজুন...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17),
                        focusedBorderColor = Color(0xFFFFD600),
                        unfocusedBorderColor = Color(0xFF1E2D40),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Buttons (ALL, PENDING, APPROVED, REJECTED)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf("ALL" to "সকল", "PENDING" to "পেন্ডিং ($pendingCount)", "APPROVED" to "অনুমোদিত", "REJECTED" to "বাতিল")
                    tabs.forEach { (statusKey, label) ->
                        val isSelected = filterStatus == statusKey
                        val chipColor = when (statusKey) {
                            "PENDING" -> Color(0xFFFFD600)
                            "APPROVED" -> Color(0xFF00E676)
                            "REJECTED" -> Color(0xFFEF4444)
                            else -> Color(0xFF818CF8)
                        }
                        Surface(
                            color = if (isSelected) chipColor.copy(alpha = 0.25f) else Color(0xFF162536),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) chipColor else Color(0xFF23384D)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { filterStatus = statusKey }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) chipColor else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List of requests
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (filterStatus == "PENDING") "কোন পেন্ডিং রিকোয়েস্ট নেই ✓" else "কোন উইথড্র হিস্ট্রি পাওয়া যায়নি",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        filteredList.forEach { req ->
                            AdminWithdrawalItemRow(
                                context = context,
                                request = req,
                                onApprove = {
                                    val success = WithdrawalManager.approveRequest(context, req.id)
                                    if (success) {
                                        Toast.makeText(context, "✓ রিকোয়েস্ট অনুমোদন করা হয়েছে এবং নোটিফিকেশন পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onReject = {
                                    val success = WithdrawalManager.rejectRequest(context, req.id, "Admin canceled request")
                                    if (success) {
                                        Toast.makeText(context, "রিকোয়েস্ট বাতিল করা হয়েছে। ইউজারের ব্যালেন্স ফেরত দেওয়া হয়েছে।", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminWithdrawalItemRow(
    context: Context,
    request: WithdrawalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val methodColor = when (request.method.lowercase()) {
        "binance" -> Color(0xFFF0B90B)
        "bkash" -> Color(0xFFE2136E)
        "nagad" -> Color(0xFFF7941D)
        else -> Color(0xFF00B0FF)
    }

    val statusColor = when (request.status) {
        "APPROVED" -> Color(0xFF00E676)
        "REJECTED" -> Color(0xFFEF4444)
        else -> Color(0xFFFFD600)
    }

    val sdf = remember { java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()) }
    val dateStr = remember(request.requestTimestamp) { sdf.format(java.util.Date(request.requestTimestamp)) }

    Surface(
        color = Color(0xFF090E17),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (request.status == "PENDING") Color(0xFFFFD600).copy(alpha = 0.5f) else Color(0xFF1C2C3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // User Email & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = request.userEmail,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                }

                Text(
                    text = dateStr,
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Method, Account & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = methodColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, methodColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = request.method.uppercase(),
                            color = methodColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = request.accountNumber,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = {
                            ClipboardHelper.copyToClipboard(context, request.accountNumber)
                            Toast.makeText(context, "নাম্বার কপি করা হয়েছে: ${request.accountNumber}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                    }
                }

                // Amount
                Text(
                    text = "৳${String.format(java.util.Locale.US, "%.2f", request.amount)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status or Action Buttons
            if (request.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("APPROVE (অনুমোদন)", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    }

                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("REJECT (বাতিল)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (request.status == "APPROVED") "✓ APPROVED" else "✗ REJECTED",
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (request.note.isNotBlank()) {
                        Text(
                            text = request.note,
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: User Accounts Manager Card
 */
@Composable
fun AdminUserManagerCard(
    context: Context,
    managedUsers: List<AuthUser>
) {
    var newUserEmail by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    var newUserName by remember { mutableStateOf("") }
    var createdSuccessCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }
    var broadcastMsg by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Broadcast Notification / Push Message Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BROADCAST NOTIFICATION / PUSH MESSAGE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "সকল সাধারণ ইউজার ও সাব এডমিনদের কাছে জরুরি নোটিশ বা ব্রডকাস্ট মেসেজ পাঠান:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = broadcastMsg,
                    onValueChange = { broadcastMsg = it },
                    placeholder = { Text("বার্তা লিখুন (যেমন: প্যানেল মেইনটেনেন্স চলছে / নতুন রেট আপডেট)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E2D40),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (broadcastMsg.isNotBlank()) {
                            AuthManager.broadcastNotification(broadcastMsg.trim())
                            Toast.makeText(context, "✓ ব্রডকাস্ট নোটিফিকেশন পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                            broadcastMsg = ""
                            VibrationHelper.vibrateSuccess(context)
                        } else {
                            Toast.makeText(context, "মেসেজ লিখুন", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SEND BROADCAST MESSAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Create User Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E324A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CREATE USER LOGIN CREDENTIALS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ইউজাররা নিজে রেজিস্টার করতে পারবে না। আপনি এখান থেকে ইমেইল ও পাসওয়ার্ড তৈরি করে তাদের দিলে তারা লগইন করতে পারবে।",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newUserName,
                    onValueChange = { newUserName = it },
                    label = { Text("ইউজারের নাম (User Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newUserEmail,
                    onValueChange = { newUserEmail = it },
                    label = { Text("ইমেইল (Email)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newUserPass,
                    onValueChange = { newUserPass = it },
                    label = { Text("পাসওয়ার্ড (Password)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val (success, msg) = AuthManager.createManagedUser(
                            emailInput = newUserEmail,
                            passInput = newUserPass,
                            nameInput = newUserName
                        )
                        if (success) {
                            createdSuccessCredentials = Pair(newUserEmail.trim().lowercase(), newUserPass.trim())
                            Toast.makeText(context, "✓ $msg", Toast.LENGTH_SHORT).show()
                            newUserEmail = ""
                            newUserPass = ""
                            newUserName = ""
                            VibrationHelper.vibrateSuccess(context)
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GENERATE & CREATE USER ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Show Success Copy Box
                createdSuccessCredentials?.let { (em, pw) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "✓ নতুন অ্যাকাউন্ট তৈরি সফল!", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedButton(
                                    onClick = {
                                        val text = "Work ShortCut Login Credentials:\nEmail: $em\nPassword: $pw"
                                        ClipboardHelper.copyToClipboard(context, text, "Login Credentials")
                                        Toast.makeText(context, "লগইন তথ্য কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF34D399))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF34D399))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Info", fontSize = 10.sp, color = Color(0xFF34D399))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Email: $em\nPassword: $pw", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // List of Created Users with ACTIVE vs INACTIVE Filter
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isUserRefreshing by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "MANAGED USER ACCOUNTS (${managedUsers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = {
                                if (isUserRefreshing) return@IconButton
                                isUserRefreshing = true
                                coroutineScope.launch {
                                    try {
                                        val count = AuthManager.syncFromCloudAsync()
                                        com.example.util.VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "⚡ $count জন ইউজার ডাটা আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "সিঙ্ক ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isUserRefreshing = false
                                    }
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (isUserRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh User Data",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    val activeCount = managedUsers.count { it.isActive && !it.isBanned }
                    val inactiveCount = managedUsers.count { !it.isActive && !it.isBanned }
                    Text(
                        text = "🟢 $activeCount Active  |  ⚪ $inactiveCount Inactive",
                        fontSize = 10.sp,
                        color = Color(0xFF81D4FA),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                var showWipeConfirmDialog by remember { mutableStateOf(false) }

                if (showWipeConfirmDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showWipeConfirmDialog = false },
                        title = { Text("সকল ব্যালেন্স ও হিস্টোরি রিসেট", fontWeight = FontWeight.Bold, color = Color.White) },
                        text = {
                            Text(
                                "আপনি কি নিশ্চিত যে সকল ইউজারের বর্তমান ব্যালেন্স, ওটিপি কাউন্ট এবং ওটিপি হিস্টোরি রিসেট করতে চান? ইউজারের অ্যাকাউন্ট বা পাসওয়ার্ড অক্ষত থাকবে এবং এখন থেকে প্রতিটি ইউজারের নিজস্ব ইমেইলে আলাদাভাবে কাউন্ট ও ব্যালেন্স যোগ হবে।",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showWipeConfirmDialog = false
                                    AuthManager.wipeAllUserBalancesAndOtpHistory(context)
                                    com.example.util.VibrationHelper.vibrateSuccess(context)
                                    Toast.makeText(context, "✓ সকল ইউজারের ব্যালেন্স ও হিস্টোরি সফলভাবে রিসেট হয়েছে!", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Text("হ্যাঁ, রিসেট করুন", color = Color.White)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showWipeConfirmDialog = false }) {
                                Text("বাতিল", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF0F1A2B),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                Button(
                    onClick = { showWipeConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🧹 সকল ইউজারের ব্যালেন্স ও হিস্টোরি রিসেট করুন", fontSize = 11.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                var userFilterTab by remember { mutableStateOf("ALL") } // "ALL", "ACTIVE", "INACTIVE", "BANNED"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val activeCount = managedUsers.count { it.isActive && !it.isBanned }
                    val inactiveCount = managedUsers.count { !it.isActive && !it.isBanned }
                    val bannedCount = managedUsers.count { it.isBanned }

                    listOf(
                        Triple("ALL", "ALL (${managedUsers.size})", Color(0xFF38BDF8)),
                        Triple("ACTIVE", "🟢 ACTIVE ($activeCount)", Color(0xFF10B981)),
                        Triple("INACTIVE", "⚪ INACTIVE ($inactiveCount)", Color(0xFFF59E0B)),
                        Triple("BANNED", "🚫 BANNED ($bannedCount)", Color(0xFFEF4444))
                    ).forEach { (tabKey, tabLabel, tabColor) ->
                        val isTabSelected = userFilterTab == tabKey
                        Surface(
                            color = if (isTabSelected) tabColor.copy(alpha = 0.25f) else Color(0xFF0B1420),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isTabSelected) tabColor else Color(0xFF1E2D40)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { userFilterTab = tabKey }
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isTabSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val displayedUsers = remember(managedUsers, userFilterTab) {
                    when (userFilterTab) {
                        "ACTIVE" -> managedUsers.filter { it.isActive && !it.isBanned }
                        "INACTIVE" -> managedUsers.filter { !it.isActive && !it.isBanned }
                        "BANNED" -> managedUsers.filter { it.isBanned }
                        else -> managedUsers
                    }
                }

                if (displayedUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (managedUsers.isEmpty()) "কোনো ইউজার অ্যাকাউন্ট তৈরি করা হয়নি। উপরে ইউজার তৈরি করুন।"
                            else "এই ফিল্টারে কোনো ইউজার নেই।",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayedUsers.forEach { user ->
                            AdminUserItemRow(
                                user = user,
                                onToggleStatus = { AuthManager.toggleUserStatus(user.email) },
                                onToggleBan = { AuthManager.toggleUserBan(user.email) },
                                onResetLogins = { AuthManager.resetUserLogins(user.email) },
                                onDelete = { AuthManager.deleteManagedUser(user.email) },
                                onToggleSubAdmin = { isSub ->
                                    AuthManager.setSubAdminRole(user.email, isSub)
                                    val msg = if (isSub) "✓ ${user.name}-কে সাব এডমিন ট্যাগ দেওয়া হয়েছে" else "✓ সাব এডমিন ট্যাগ বাতিল করা হয়েছে"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onUpdatePermissions = { newPerms ->
                                    AuthManager.updateSubAdminPermissions(user.email, newPerms)
                                    Toast.makeText(context, "✓ পারমিশন আপডেট সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                onUpdateStats = { tot, bal, td, l7, l30 ->
                                    AuthManager.updateUserOtpAndBalance(user.email, tot, bal, td, l7, l30)
                                    Toast.makeText(context, "✓ ${user.name}-এর OTP ও ব্যালেন্স আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                onCopy = {
                                    val text = "Email: ${user.email}\nPassword: ${user.passwordHash}"
                                    ClipboardHelper.copyToClipboard(context, text, "User Credentials")
                                    Toast.makeText(context, "কপি করা হয়েছে: ${user.email}", Toast.LENGTH_SHORT).show()
                                },
                                onResetDeviceLock = {
                                    AuthManager.resetUserDeviceBinding(user.email) { ok ->
                                        if (ok) {
                                            Toast.makeText(context, "✓ ${user.name}-এর ডিভাইস লক সফলভাবে রিসেট হয়েছে", Toast.LENGTH_SHORT).show()
                                            com.example.util.VibrationHelper.vibrateSuccess(context)
                                        } else {
                                            Toast.makeText(context, "⚠️ ডিভাইস রিসেট ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUserItemRow(
    user: AuthUser,
    onToggleStatus: () -> Unit,
    onToggleBan: () -> Unit,
    onResetLogins: () -> Unit,
    onDelete: () -> Unit,
    onToggleSubAdmin: (Boolean) -> Unit,
    onUpdatePermissions: (com.example.service.SubAdminPermissions) -> Unit,
    onUpdateStats: (total: Int, balance: Double, today: Int, l7: Int, l30: Int) -> Unit,
    onCopy: () -> Unit,
    onResetDeviceLock: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showEditStatsDialog by remember { mutableStateOf(false) }
    var showOtpHistoryDialog by remember { mutableStateOf(false) }
    var showUserWithdrawalsDialog by remember { mutableStateOf(false) }
    var showResetDeviceConfirmDialog by remember { mutableStateOf(false) }

    val isSubAdmin = user.role == "SUB_ADMIN"

    Surface(
        color = if (user.isBanned) Color(0xFF180A0A)
                else if (user.isActive) Color(0xFF071B12)
                else Color(0xFF161208),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (user.isBanned) Color(0xFFEF4444)
            else if (isSubAdmin) Color(0xFF818CF8)
            else if (user.isActive) Color(0xFF10B981).copy(alpha = 0.6f)
            else Color(0xFFF59E0B).copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Role Badge
                    if (isSubAdmin) {
                        Surface(
                            color = Color(0xFF6366F1).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF818CF8))
                        ) {
                            Text(
                                text = "🛡️ SUB ADMIN",
                                color = Color(0xFFA5B4FC),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFF334155).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "👤 USER",
                                color = Color(0xFF94A3B8),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    if (user.isBanned) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Text(
                                text = "🚫 BANNED",
                                color = Color(0xFFF87171),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (user.isActive) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF00E676)))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "🟢 ACTIVE",
                                    color = Color(0xFF34D399),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "⚪ INACTIVE",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onToggleBan, modifier = Modifier.size(28.dp)) {
                        Text(
                            text = if (user.isBanned) "Unban" else "Ban",
                            color = if (user.isBanned) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Status Toggle Button with Clear Bengali / English Label
                    Surface(
                        color = if (user.isActive) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (user.isActive) Color(0xFFF59E0B) else Color(0xFF10B981)),
                        modifier = Modifier.clickable { onToggleStatus() }
                    ) {
                        Text(
                            text = if (user.isActive) "নিষ্ক্রিয়" else "সক্রিয়",
                            color = if (user.isActive) Color(0xFFFBBF24) else Color(0xFF34D399),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (user.isBanned) {
                        Surface(
                            color = Color(0xFFDC2626).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.clickable { onDelete() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete All User Data", tint = Color(0xFFF87171), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Delete User Data", color = Color(0xFFF87171), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.email,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Text(
                    text = if (user.isBanned) "🚫 অ্যাকাউন্ট ব্লকড"
                           else if (user.isActive) "🟢 ইউজার সক্রিয় (লগইন সচল)"
                           else "⚪ ইউজার নিষ্ক্রিয় (লগইন বন্ধ)",
                    color = if (user.isBanned) Color(0xFFF87171)
                            else if (user.isActive) Color(0xFF34D399)
                            else Color(0xFFFBBF24),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (passwordVisible) "Pass: ${user.passwordHash}" else "Pass: ••••••••",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Logins count and active devices badge
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Logins: ${user.loginCount}",
                            color = Color(0xFF60A5FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• Active: ${user.activeDevicesCount}",
                            color = if (user.activeDevicesCount > 0) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // User OTP & Balance Stats Bar (Total, Balance, Today, 7D, 30D)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xFF0D1522),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 ওটিপি ও ব্যালেন্স তথ্য:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                modifier = Modifier.clickable { showOtpHistoryDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(9.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("OTP হিস্ট্রি", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                            }

                            Surface(
                                color = Color(0xFFFFD600).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.6f)),
                                modifier = Modifier.clickable { showUserWithdrawalsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(9.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("উইথড্র", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD600))
                                }
                            }

                            Surface(
                                color = Color(0xFF818CF8).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF818CF8)),
                                modifier = Modifier.clickable { showEditStatsDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(9.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("এডিট", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL", fontSize = 7.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                Text("${user.totalOtps}", fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BALANCE", fontSize = 7.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                Text("৳${String.format(Locale.US, "%.1f", user.balanceTk)}", fontSize = 10.5.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TODAY", fontSize = 7.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                                Text("${user.todayOtps}", fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Surface(
                            color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("7 DAYS", fontSize = 7.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                                Text("${user.last7DaysOtps}", fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Surface(
                            color = Color(0xFFEC4899).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("30 DAYS", fontSize = 7.sp, color = Color(0xFFF472B6), fontWeight = FontWeight.Bold)
                                Text("${user.last30DaysOtps}", fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // Sub Admin Role Tag & Permissions Controls
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (isSubAdmin) Color(0xFF4F46E5).copy(alpha = 0.5f) else Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSubAdmin) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡️ সাব এডমিন পারমিশন:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { showPermissionsDialog = true },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA)),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("অনুমতি এডিট", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (user.permissions.canUploadNumbers) {
                                    Text("Upload ✓", fontSize = 8.sp, color = Color(0xFF34D399))
                                }
                                if (user.permissions.canDeleteNumbers) {
                                    Text("Delete ✓", fontSize = 8.sp, color = Color(0xFF34D399))
                                }
                                if (user.permissions.canUpdateOtpRate) {
                                    Text("Rate ✓", fontSize = 8.sp, color = Color(0xFF38BDF8))
                                }
                                if (user.permissions.canManageWithdrawals) {
                                    Text("Withdraw ✓", fontSize = 8.sp, color = Color(0xFFFBBF24))
                                }
                                if (user.permissions.canViewLiveCdr) {
                                    Text("CDR ✓", fontSize = 8.sp, color = Color(0xFFA78BFA))
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onToggleSubAdmin(false) },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("ট্যাগ রিমুভ", fontSize = 9.sp, color = Color(0xFFF87171))
                        }
                    } else {
                        Text(
                            text = "সাধারণ ইউজার (এডমিন প্যানেল এক্সেস নেই)",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B)
                        )

                        Button(
                            onClick = { onToggleSubAdmin(true) },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Make Sub Admin", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            val isUserLiveOnline = user.isOnline && (System.currentTimeMillis() - user.lastActive) < 120_000L && user.lastActive > 0L
            Surface(
                color = if (isUserLiveOnline) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFF1E293B).copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (isUserLiveOnline) Color(0xFF10B981) else Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isUserLiveOnline) Color(0xFF00E676) else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isUserLiveOnline) "🟢 লাইভ অনলাইন (সক্রিয়)" else "⚪ অফলাইন",
                            color = if (isUserLiveOnline) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    val dev = user.lastDeviceName.ifEmpty { "Mobile" }
                    val timeDiff = (System.currentTimeMillis() - if (user.lastActive > 0L) user.lastActive else user.lastLoginAt) / 1000
                    val timeStr = when {
                        timeDiff < 60 -> "Just now"
                        timeDiff < 3600 -> "${timeDiff / 60}m ago"
                        timeDiff < 86400 -> "${timeDiff / 3600}h ago"
                        else -> "${timeDiff / 86400}d ago"
                    }
                    Text(
                        text = "📱 $dev • $timeStr",
                        color = Color(0xFFCBD5E1),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 1-Phone 1-Account Bound Device Status Card
            Surface(
                color = if (!user.boundDeviceId.isNullOrBlank()) Color(0xFF1E1B0F) else Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (!user.boundDeviceId.isNullOrBlank()) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (!user.boundDeviceId.isNullOrBlank()) Icons.Default.Lock else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (!user.boundDeviceId.isNullOrBlank()) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (!user.boundDeviceId.isNullOrBlank()) "🔒 ১-ফোন ১-মেইল লক সক্রিয়" else "🔓 ডিভাইস আনলকড (লক মুক্ত)",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!user.boundDeviceId.isNullOrBlank()) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                                )
                            }
                            val bModel = user.boundDeviceModel?.ifBlank { null } ?: user.lastDeviceName.ifEmpty { "Android Device" }
                            val bIdText = if (!user.boundDeviceId.isNullOrBlank()) "ID: ${user.boundDeviceId?.take(12)}..." else "নতুন ডিভাইসে প্রথম লগইনে লক হবে"
                            Text(
                                text = "📱 $bModel • $bIdText",
                                fontSize = 8.5.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    if (AuthManager.isPrimeAdmin() && onResetDeviceLock != null && !user.boundDeviceId.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFDC2626).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.clickable { showResetDeviceConfirmDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "ডিভাইস রিসেট",
                                    color = Color(0xFFF87171),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Withdrawal Security PIN & Reset Request Card
            Surface(
                color = if (user.isPinResetRequested) Color(0xFF2D1515) else Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (user.isPinResetRequested) Color(0xFFEF4444) else Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (user.isPinResetRequested) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (user.isPinResetRequested) "⚠️ পিন রিসেট রিকোয়েস্ট পেন্ডিং" else "🔐 উইথড্রয়াল সিকিউরিটি পিন",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (user.isPinResetRequested) Color(0xFFFCA5A5) else Color(0xFFE2E8F0)
                            )
                            Text(
                                text = if (user.isPinResetRequested) "ইউজার পিন ভুলে গেছেন এবং রিসেট চেয়েছেন" else "উইথড্রয়ালের ৪ ডিজিট সিকিউরিটি পিন",
                                fontSize = 8.5.sp,
                                color = if (user.isPinResetRequested) Color(0xFFF87171) else Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (AuthManager.isAdmin()) {
                        Surface(
                            color = if (user.isPinResetRequested) Color(0xFFDC2626) else Color(0xFF334155),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable {
                                AuthManager.resetUserPin(user.email) { success ->
                                    Toast.makeText(
                                        context,
                                        if (success) "✓ ${user.email} এর পিন রিসেট হয়েছে! ইউজার এখন নতুন পিন সেট করতে পারবেন"
                                        else "✓ পিন রিসেট সম্পন্ন হয়েছে",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (user.isPinResetRequested) "পিন রিসেট করুন" else "রিসেট পিন",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Device Sessions & ID Detection Controls
            Spacer(modifier = Modifier.height(6.dp))
            var showDeviceSessions by remember { mutableStateOf(false) }
            val onlineDevCount = user.deviceSessions.count { it.isOnline && !it.isRevoked }
            val totalDevCount = user.deviceSessions.size

            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ডিভাইস সেশন ($onlineDevCount অনলাইন / $totalDevCount মোট)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showDeviceSessions = !showDeviceSessions }
                        ) {
                            Text(
                                text = if (showDeviceSessions) "লুকান ▲" else "ম্যানেজ করুন ▼",
                                color = Color(0xFF38BDF8),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (showDeviceSessions) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (user.deviceSessions.isEmpty()) {
                            Text(
                                text = "কোনো সংরক্ষিত ডিভাইস সেশন নেই (লগইন করলে স্বয়ংক্রিয়ভাবে ডিটেক্ট হবে)",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                user.deviceSessions.forEach { session ->
                                    Surface(
                                        color = if (session.isRevoked) Color(0xFF7F1D1D).copy(alpha = 0.2f)
                                                else if (session.isOnline) Color(0xFF064E3B).copy(alpha = 0.3f)
                                                else Color(0xFF1E293B).copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, if (session.isRevoked) Color(0xFFEF4444) else if (session.isOnline) Color(0xFF10B981) else Color(0xFF475569)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (session.isRevoked) "🚫 বাতিল" else if (session.isOnline) "🟢 অনলাইন" else "⚪ অফলাইন",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (session.isRevoked) Color(0xFFF87171) else if (session.isOnline) Color(0xFF34D399) else Color(0xFF94A3B8)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = session.deviceModel.ifEmpty { "Android" },
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                                Text(
                                                    text = "ID: ${session.deviceId.take(16)}...",
                                                    fontSize = 8.sp,
                                                    color = Color(0xFF94A3B8),
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }

                                            if (session.isRevoked) {
                                                OutlinedButton(
                                                    onClick = { AuthManager.restoreDeviceSession(user.email, session.deviceId) },
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                                    modifier = Modifier.height(22.dp)
                                                ) {
                                                    Text("রিস্টোর", fontSize = 8.5.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Button(
                                                    onClick = { AuthManager.revokeDeviceSession(user.email, session.deviceId) },
                                                    shape = RoundedCornerShape(4.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 1.dp),
                                                    modifier = Modifier.height(22.dp)
                                                ) {
                                                    Text("সেশন বাতিল", fontSize = 8.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section Access / Option Toggles for User
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "🔒 সেকশন এক্সেস নিয়ন্ত্রণ (অন / অফ করতে ট্যাপ করুন):",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    val sections = listOf(
                        "VIRTUAL_NUMBERS" to "অল নাম্বার 📱",
                        "UNIX_SMS" to "Unix SMS 🔥",
                        "PREMIUM_V2" to "Prem V2 🚀",
                        "KOP_1" to "Kop 1 🌐",
                        "KOP_2" to "Kop 2 ⚡",
                        "WITHDRAWAL" to "উইথড্র ৳",
                        "OVERLAY" to "ওভারলে 📱",
                        "PROXY" to "প্রক্সি/VPN 🛡️",
                        "TWO_FACTOR" to "2FA কোড 🔑",
                        "PW_COPY" to "পাসওয়ার্ড 📋",
                        "NAME_GENERATOR" to "নাম জেন 👤",
                        "EXCEL_COLLECTOR" to "শিট 📊",
                        "CLEAR_DATA" to "ডাটা ক্লিয়ার 🗑️",
                        "APP_SHORTCUTS" to "শর্টকাট ⚡"
                    )
                    sections.chunked(3).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chunk.forEach { (key, label) ->
                                val isAllowed = !user.disabledSections.contains(key)
                                Surface(
                                    color = if (isAllowed) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, if (isAllowed) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { AuthManager.toggleUserSectionPermission(user.email, key) }
                                ) {
                                    Text(
                                        text = if (isAllowed) "$label ✓" else "$label ✕",
                                        color = if (isAllowed) Color(0xFF34D399) else Color(0xFFF87171),
                                        fontSize = 8.2.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                            // Fill remaining slots if chunk has fewer than 3
                            repeat(3 - chunk.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    // Sub Admin Permissions Edit Dialog
    if (showPermissionsDialog) {
        var pUpload by remember { mutableStateOf(user.permissions.canUploadNumbers) }
        var pDelete by remember { mutableStateOf(user.permissions.canDeleteNumbers) }
        var pRate by remember { mutableStateOf(user.permissions.canUpdateOtpRate) }
        var pWithdraw by remember { mutableStateOf(user.permissions.canManageWithdrawals) }
        var pCdr by remember { mutableStateOf(user.permissions.canViewLiveCdr) }
        var pNotify by remember { mutableStateOf(user.permissions.canBroadcastNotify) }

        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("🛡️ সাব এডমিন অনুমতি নির্ধারণ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(user.name, fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "প্রাইম এডমিন হিসেবে আপনি যেকোনো সময় এই সাব এডমিনের কাজের অনুমতি অন/অফ করতে পারেন:",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    PermissionToggleRow(label = "নাম্বার আপলোড (Upload Numbers)", checked = pUpload, onCheckedChange = { pUpload = it })
                    PermissionToggleRow(label = "নাম্বার ডিলিট (Delete Numbers)", checked = pDelete, onCheckedChange = { pDelete = it })
                    PermissionToggleRow(label = "OTP রেট আপডেট (Unix/Zenex Rate)", checked = pRate, onCheckedChange = { pRate = it })
                    PermissionToggleRow(label = "উইথড্রয়াল ম্যানেজমেন্ট (Withdraw Requests)", checked = pWithdraw, onCheckedChange = { pWithdraw = it })
                    PermissionToggleRow(label = "লাইভ CDR/DLR ট্রাফিক দেখা (Traffic Stream)", checked = pCdr, onCheckedChange = { pCdr = it })
                    PermissionToggleRow(label = "ব্রডকাস্ট নোটিফিকেশন পাঠানো (Broadcast)", checked = pNotify, onCheckedChange = { pNotify = it })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newPerms = com.example.service.SubAdminPermissions(
                            canUploadNumbers = pUpload,
                            canDeleteNumbers = pDelete,
                            canUpdateOtpRate = pRate,
                            canManageWithdrawals = pWithdraw,
                            canViewLiveCdr = pCdr,
                            canBroadcastNotify = pNotify
                        )
                        onUpdatePermissions(newPerms)
                        showPermissionsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionsDialog = false }) {
                    Text("বাতিল", color = Color.White)
                }
            }
        )
    }

    // Edit User OTP & Balance Dialog
    if (showEditStatsDialog) {
        var editTotal by remember { mutableStateOf(user.totalOtps.toString()) }
        var editBal by remember { mutableStateOf(user.balanceTk.toString()) }
        var editToday by remember { mutableStateOf(user.todayOtps.toString()) }
        var edit7d by remember { mutableStateOf(user.last7DaysOtps.toString()) }
        var edit30d by remember { mutableStateOf(user.last30DaysOtps.toString()) }

        AlertDialog(
            onDismissRequest = { showEditStatsDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("✏️ ওটিপি ও ব্যালেন্স সম্পাদন", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(user.name, fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("এই ইউজারের মোট OTP, ওয়ালেট ব্যালেন্স এবং দৈনিক/সাপ্তাহিক ওটিপি আপডেট করুন:", fontSize = 11.sp, color = Color(0xFFCBD5E1))

                    OutlinedTextField(
                        value = editTotal,
                        onValueChange = { editTotal = it },
                        label = { Text("Total OTPs (সর্বমোট ওটিপি)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editBal,
                        onValueChange = { editBal = it },
                        label = { Text("Balance in BDT (ব্যালেন্স টাকা)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = editToday,
                            onValueChange = { editToday = it },
                            label = { Text("Today OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = edit7d,
                            onValueChange = { edit7d = it },
                            label = { Text("7d OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = edit30d,
                            onValueChange = { edit30d = it },
                            label = { Text("30d OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tot = editTotal.toIntOrNull() ?: user.totalOtps
                        val bal = editBal.toDoubleOrNull() ?: user.balanceTk
                        val td = editToday.toIntOrNull() ?: user.todayOtps
                        val l7 = edit7d.toIntOrNull() ?: user.last7DaysOtps
                        val l30 = edit30d.toIntOrNull() ?: user.last30DaysOtps
                        onUpdateStats(tot, bal, td, l7, l30)
                        showEditStatsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditStatsDialog = false }) {
                    Text("বাতিল", color = Color.White)
                }
            }
        )
    }

    if (showOtpHistoryDialog) {
        AdminUserOtpHistoryDialog(
            user = user,
            onDismiss = { showOtpHistoryDialog = false }
        )
    }

    if (showUserWithdrawalsDialog) {
        AdminUserWithdrawalsDialog(
            user = user,
            onDismiss = { showUserWithdrawalsDialog = false }
        )
    }

    if (showResetDeviceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetDeviceConfirmDialog = false },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(14.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚠️ ডিভাইস আনলক / রিসেট", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিত যে '${user.name}' (${user.email})-এর ডিভাইস লক রিসেট করতে চান?\n\nরিসেট করার পর ইউজার যেকোনো নতুন ডিভাইসে লগইন করতে পারবেন এবং তখন অ্যাকাউন্টটি ওই নতুন ডিভাইসে ১-টু-১ লক হয়ে যাবে।",
                    fontSize = 11.5.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDeviceConfirmDialog = false
                        onResetDeviceLock?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("হ্যাঁ, ডিভাইস রিসেট করুন", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDeviceConfirmDialog = false }) {
                    Text("বাতিল", color = Color.White, fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
fun PermissionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 11.sp, color = Color.White, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4F46E5),
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFF0F172A)
                )
            )
        }
    }
}

@Composable
fun AdminUserOtpHistoryDialog(
    user: AuthUser,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<OtpHistoryRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(user.email, refreshTrigger) {
        isLoading = true
        records = OtpHistoryManager.fetchUserOtpRecords(user.email)
        isLoading = false
    }

    val filteredRecords = remember(records, searchQuery) {
        if (searchQuery.isBlank()) records else {
            records.filter {
                it.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                it.service.contains(searchQuery, ignoreCase = true) ||
                it.otpCode.contains(searchQuery, ignoreCase = true) ||
                it.platform.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C1420),
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("📜 ওটিপি হিস্ট্রি (Live OTPs)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(user.name + " (${user.email})", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                    }
                }
                IconButton(
                    onClick = { refreshTrigger++ },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("নাম্বার, ওটিপি বা সার্ভিস দিয়ে খুঁজুন...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E2D40),
                        focusedContainerColor = Color(0xFF070B12),
                        unfocusedContainerColor = Color(0xFF070B12)
                    ),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp)) }
                )

                // Summary Row
                Surface(
                    color = Color(0xFF111C2B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E324A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("মোট রিসিভড: ${records.size} টি", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        val totalEarned = records.sumOf { it.rate }
                        Text("মোট অর্জিত: ৳${String.format(Locale.US, "%.2f", totalEarned)}", fontSize = 10.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(26.dp))
                    }
                } else if (filteredRecords.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (records.isEmpty()) "এই ইউজারের কোনো ওটিপি রেকর্ড পাওয়া যায়নি।" else "খুঁজে পাওয়া যায়নি।",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredRecords) { rec ->
                            Surface(
                                color = Color(0xFF090E17),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1B2A3D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = rec.service,
                                                color = Color(0xFF38BDF8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = Color(0xFF1E293B),
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = rec.platform,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 7.5.sp,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = rec.phoneNumber,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        val timeStr = try {
                                            SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(rec.timestamp))
                                        } catch (_: Exception) { "" }
                                        Text(
                                            text = "$timeStr • ৳${String.format(Locale.US, "%.2f", rec.rate)}",
                                            color = Color(0xFF64748B),
                                            fontSize = 8.5.sp
                                        )
                                    }

                                    // OTP Code Box
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                                        modifier = Modifier.clickable {
                                            ClipboardHelper.copyToClipboard(context, rec.otpCode, "OTP Code")
                                            Toast.makeText(context, "OTP কোড কপি করা হয়েছে: ${rec.otpCode}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = rec.otpCode,
                                                color = Color(0xFF34D399),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("বন্ধ করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun AdminUserWithdrawalsDialog(
    user: AuthUser,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val withdrawState by WithdrawalManager.state.collectAsState()
    val userRequests = remember(withdrawState.allRequests, user.email) {
        WithdrawalManager.getUserRequests(user.email)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C1420),
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("💳 উইথড্র রিকোয়েস্ট হিস্ট্রি", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(user.name + " (${user.email})", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Summary bar
                val pendingTotal = userRequests.filter { it.status == "PENDING" }.sumOf { it.amount }
                val approvedTotal = userRequests.filter { it.status == "APPROVED" }.sumOf { it.amount }
                Surface(
                    color = Color(0xFF111C2B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E324A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("পেন্ডিং: ৳${String.format(Locale.US, "%.0f", pendingTotal)}", fontSize = 10.sp, color = Color(0xFFFFD600), fontWeight = FontWeight.Bold)
                        Text("অনুমোদিত: ৳${String.format(Locale.US, "%.0f", approvedTotal)}", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        Text("ব্যালেন্স: ৳${String.format(Locale.US, "%.1f", user.balanceTk)}", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }

                if (userRequests.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text("এই ইউজারের কোনো উইথড্র রিকোয়েস্ট নেই।", color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(userRequests) { req ->
                            Surface(
                                color = Color(0xFF090E17),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (req.status == "PENDING") Color(0xFFFFD600).copy(alpha = 0.5f) else Color(0xFF1B2A3D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(req.method, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = when (req.status) {
                                                    "PENDING" -> Color(0xFFFFD600).copy(alpha = 0.2f)
                                                    "APPROVED" -> Color(0xFF00E676).copy(alpha = 0.2f)
                                                    else -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = when (req.status) {
                                                        "PENDING" -> "🟡 PENDING"
                                                        "APPROVED" -> "🟢 APPROVED"
                                                        else -> "🔴 REJECTED"
                                                    },
                                                    color = when (req.status) {
                                                        "PENDING" -> Color(0xFFFFD600)
                                                        "APPROVED" -> Color(0xFF00E676)
                                                        else -> Color(0xFFEF4444)
                                                    },
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "৳${String.format(Locale.US, "%.0f", req.amount)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF00E676),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Acc: ${req.accountNumber}",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        val dateStr = try {
                                            SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(req.requestTimestamp))
                                        } catch (_: Exception) { "" }
                                        Text(dateStr, color = Color(0xFF64748B), fontSize = 8.5.sp)
                                    }

                                    if (req.status == "PENDING") {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val success = WithdrawalManager.approveRequest(context, req.id)
                                                    if (success) {
                                                        Toast.makeText(context, "✓ রিকোয়েস্ট অনুমোদন সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("অনুমোদন করুন ✓", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val success = WithdrawalManager.rejectRequest(context, req.id, "Admin rejected")
                                                    if (success) {
                                                        Toast.makeText(context, "বাতিল করা হয়েছে।", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                                modifier = Modifier.weight(1f).height(28.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("বাতিল ✕", color = Color(0xFFEF4444), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("বন্ধ করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Tab 3: Live Device Sessions & System Health Monitor (Replaces Live CDR)
 */
@Composable
fun AdminDeviceAndSystemMonitorCard(
    context: Context,
    managedUsers: List<AuthUser>
) {
    var searchFilter by remember { mutableStateOf("") }
    var statusFilterTab by remember { mutableStateOf("ALL") } // "ALL", "ONLINE", "OFFLINE"
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000L)
            now = System.currentTimeMillis()
        }
    }

    val onlineUsers = managedUsers.filter { it.isOnline || (now - it.lastActive) < 120_000L }
    val offlineUsers = managedUsers.filter { !it.isOnline && (now - it.lastActive) >= 120_000L }

    fun formatOfflineTime(lastActive: Long): String {
        if (lastActive <= 0L) return "কখনও লগইন করেনি"
        val diff = (now - lastActive).coerceAtLeast(0L)
        val mins = diff / (60 * 1000L)
        val hours = diff / (60 * 60 * 1000L)
        val days = diff / (24 * 60 * 60 * 1000L)
        return when {
            mins < 2 -> "সবেমাত্র অফলাইন"
            mins < 60 -> "$mins মিনিট আগে"
            hours < 24 -> "$hours ঘণ্টা আগে"
            days < 30 -> "$days দিন আগে"
            else -> "${days / 30} মাস আগে"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. System Health & Device Telemetry Overview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E324A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE SESSIONS & SYSTEM HEALTH",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }

                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "🟢 ${onlineUsers.size} ONLINE",
                            color = Color(0xFF00E676),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stat Pills: Online, Offline, Performance, Battery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ONLINE", fontSize = 8.5.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text("${onlineUsers.size}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00E676))
                        }
                    }

                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF64748B).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("OFFLINE", fontSize = 8.5.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text("${offlineUsers.size}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF94A3B8))
                        }
                    }

                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RAM / HEAT", fontSize = 8.5.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text("COOL (A10-17)", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Actions Row: RAM/Cache Optimize & Cloud Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            System.gc()
                            Toast.makeText(context, "✓ ক্যাশ ও অপ্রয়োজনীয় মেমরি সফলভাবে ক্লিয়ার হয়েছে!", Toast.LENGTH_SHORT).show()
                            VibrationHelper.vibrateSuccess(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ক্যাশ ও র‍্যাম ক্লিয়ার", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            AuthManager.syncFromCloud()
                            Toast.makeText(context, "✓ ডাটাবেস লাইভ সিঙ্ক সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                            VibrationHelper.vibrateSuccess(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ডাটাবেস সিঙ্ক", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 2. Search & Tab Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { searchFilter = it },
                placeholder = { Text("ইউজার, ইমেইল বা ডিভাইস খুঁজুন...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF243B55),
                    focusedContainerColor = Color(0xFF090E17),
                    unfocusedContainerColor = Color(0xFF090E17)
                ),
                singleLine = true
            )

            listOf(
                Pair("ALL", "ALL"),
                Pair("ONLINE", "🟢 ON"),
                Pair("OFFLINE", "⚪ OFF")
            ).forEach { (tKey, tLabel) ->
                val isSel = statusFilterTab == tKey
                Surface(
                    color = if (isSel) Color(0xFF38BDF8).copy(alpha = 0.25f) else Color(0xFF0B1420),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D40)),
                    modifier = Modifier.clickable { statusFilterTab = tKey }
                ) {
                    Text(
                        text = tLabel,
                        color = if (isSel) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 3. User Device Sessions List
        val filteredUsers = managedUsers.filter { u ->
            val isOnline = u.isOnline || (now - u.lastActive) < 120_000L
            val matchTab = when (statusFilterTab) {
                "ONLINE" -> isOnline
                "OFFLINE" -> !isOnline
                else -> true
            }
            val matchSearch = searchFilter.isBlank() ||
                    u.name.contains(searchFilter, ignoreCase = true) ||
                    u.email.contains(searchFilter, ignoreCase = true) ||
                    u.lastDeviceName.contains(searchFilter, ignoreCase = true)

            matchTab && matchSearch
        }

        if (filteredUsers.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("কোনো ইউজার সেশন পাওয়া যায়নি", color = Color(0xFF64748B), fontSize = 12.sp)
                }
            }
        } else {
            filteredUsers.forEach { user ->
                val isOnline = user.isOnline || (now - user.lastActive) < 120_000L
                Surface(
                    color = Color(0xFF101824),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isOnline) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF1E2D40)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) Color(0xFF00E676) else Color(0xFF64748B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = user.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${user.email})",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Surface(
                                color = if (isOnline) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (isOnline) Color(0xFF00E676) else Color(0xFF475569))
                            ) {
                                Text(
                                    text = if (isOnline) "🟢 ONLINE" else "⚪ ${formatOfflineTime(user.lastActive)}",
                                    color = if (isOnline) Color(0xFF00E676) else Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = user.lastDeviceName.ifEmpty { "Android Device" },
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = "লগইন: ${user.loginCount} বার",
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total OTP: ${user.totalOtps}  |  Balance: ৳${String.format(Locale.US, "%.2f", user.balanceTk)}",
                                fontSize = 10.sp,
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold
                            )

                            if (user.lastLoginAt > 0L) {
                                val dateStr = try {
                                    SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(user.lastLoginAt))
                                } catch (_: Exception) { "" }
                                Text(
                                    text = "Last: $dateStr",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Display device sessions with revoke buttons in monitor card
                        if (user.deviceSessions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "📱 লগইনকৃত ডিভাইস সেশন (${user.deviceSessions.size}টি):",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            user.deviceSessions.forEach { dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (dev.isRevoked) "🚫" else if (dev.isOnline) "🟢" else "⚪",
                                            fontSize = 9.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column {
                                            Text(
                                                text = "${dev.deviceModel} (ID: ${dev.deviceId.take(8)}...)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (dev.isRevoked) Color(0xFFF87171) else Color(0xFFCBD5E1)
                                            )
                                            if (dev.isRevoked) {
                                                Text(
                                                    text = "সেশন বাতিলকৃত (Blocked)",
                                                    fontSize = 7.5.sp,
                                                    color = Color(0xFFF87171)
                                                )
                                            }
                                        }
                                    }
                                    if (dev.isRevoked) {
                                        OutlinedButton(
                                            onClick = { AuthManager.restoreDeviceSession(user.email, dev.deviceId) },
                                            shape = RoundedCornerShape(3.dp),
                                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(20.dp)
                                        ) {
                                            Text("রিস্টোর", fontSize = 8.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { AuthManager.revokeDeviceSession(user.email, dev.deviceId) },
                                            shape = RoundedCornerShape(3.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(20.dp)
                                        ) {
                                            Text("Revoke", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminStatPill(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = color.copy(alpha = 0.9f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Tab 4: App In-App Remote Update Manager (No Google Services needed)
 * Allows Admin to broadcast APK updates so all users can update with 1 click
 */
@Composable
fun AdminAppUpdateCard(
    context: Context
) {
    var newVersionCodeText by remember { mutableStateOf("2") }
    var newVersionNameText by remember { mutableStateOf("1.1") }
    var apkDownloadUrlText by remember { mutableStateOf("") }
    var changelogText by remember {
        mutableStateOf("• ফাস্ট ১-সেকেন্ড SMS নোটিফিকেশন ফিক্স\n• ব্যাটারি সেভার অপ্টিমাইজেশন\n• পারফরম্যান্স উন্নত করা হয়েছে")
    }
    var isForceUpdate by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D)),
        border = BorderStroke(1.2.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "1-Click Remote In-App Update",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "গুগল প্লে স্টোর ছাড়াই রিমোট আপডেট ব্রডকাস্ট",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = "Current: v${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE})",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Info Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF00E676).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "আপনি এখানে নতুন APK লিংক ও ভার্সন দিয়ে 'Publish Update' বাটনে চাপলে সমস্ত ইউজারের স্ক্রিনে ১-ক্লিক আপডেট ডায়লগ চলে যাবে। ইউজার শুধু 'Update Now' চাপলেই সরাসরি নতুন APK ইন্সটল হয়ে যাবে!",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inputs: Version Code & Version Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = newVersionCodeText,
                    onValueChange = { newVersionCodeText = it },
                    label = { Text("Build Version Code", fontSize = 11.sp) },
                    placeholder = { Text("2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = newVersionNameText,
                    onValueChange = { newVersionNameText = it },
                    label = { Text("Version Name", fontSize = 11.sp) },
                    placeholder = { Text("1.1") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct APK Download Link
            OutlinedTextField(
                value = apkDownloadUrlText,
                onValueChange = { apkDownloadUrlText = it },
                label = { Text("Direct APK Download URL (Google Drive / GitHub / Direct Link)", fontSize = 11.sp) },
                placeholder = { Text("https://example.com/WorkShortCut_v1.1.apk") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Changelog
            OutlinedTextField(
                value = changelogText,
                onValueChange = { changelogText = it },
                label = { Text("Update Changelog / নোটস (ইউজারদের কী মেসেজ দেখাবে)", fontSize = 11.sp) },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Force update toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Force Mandatory Update (বাধ্যতামূলক আপডেট)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "অন রাখলে আপডেট না করা পর্যন্ত ইউজার অ্যাপ ব্যবহার করতে পারবে না",
                        color = Color(0xFF90A4AE),
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = isForceUpdate,
                    onCheckedChange = { isForceUpdate = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00E676)
                    )
                )
            }

            // Status message
            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = if (isSuccess) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = if (isSuccess) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isPublishing = true
                        statusMessage = null
                        AppUpdateManager.clearPublishedUpdate(
                            onSuccess = {
                                isPublishing = false
                                isSuccess = true
                                statusMessage = "✓ এক্টিভ আপডেট নোটিফিকেশন সফলভাবে বন্ধ করা হয়েছে।"
                                Toast.makeText(context, "আপডেট ব্রডকাস্ট মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isPublishing = false
                                isSuccess = false
                                statusMessage = "ত্রুটি: $err"
                            }
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("আপডেট মুছুন", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val vCode = newVersionCodeText.toIntOrNull()
                        if (vCode == null || vCode <= 0) {
                            Toast.makeText(context, "অনুগ্রহ করে সঠিক Version Code দিন (যেমন 2)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (apkDownloadUrlText.isBlank()) {
                            Toast.makeText(context, "অনুগ্রহ করে APK ডাউনলোড লিংক দিন!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isPublishing = true
                        statusMessage = null
                        AppUpdateManager.publishUpdate(
                            versionCode = vCode,
                            versionName = newVersionNameText.ifBlank { "1.1" },
                            apkUrl = apkDownloadUrlText.trim(),
                            changelog = changelogText.ifBlank { "Performance improvements" },
                            isForced = isForceUpdate,
                            onSuccess = {
                                isPublishing = false
                                isSuccess = true
                                statusMessage = "✓ সফলভাবে রিমোট আপডেট ব্রডকাস্ট হয়েছে! সমস্ত ব্যবহারকারী ১-ক্লিকে আপডেট পাবেন।"
                                Toast.makeText(context, "✓ আপডেট ব্রডকাস্ট সফল!", Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isPublishing = false
                                isSuccess = false
                                statusMessage = "আপডেট পাবলিশ ব্যর্থ: $err"
                            }
                        )
                    },
                    enabled = !isPublishing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        disabledContainerColor = Color(0xFF00E676).copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(44.dp)
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("পাবলিশ হচ্ছে...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish Update 🚀", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Admin Control Toggles for Account Section & Txt Upload Lock
            val overlayState by OverlayStateManager.uiState.collectAsStateWithLifecycle()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚙️ TXT UPLOAD ADMIN CONTROLS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Upload Number Txt Lock", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Lock Txt file upload until admin unlocks", color = Color(0xFF90A4AE), fontSize = 9.sp)
                        }
                        Switch(
                            checked = overlayState.uploadNumberTxtLocked,
                            onCheckedChange = { OverlayStateManager.setUploadNumberTxtLocked(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00E676))
                        )
                    }
                }
            }
        }
    }
}


