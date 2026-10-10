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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.service.VirtualNumberManager
import com.example.service.WithdrawalManager
import com.example.service.WithdrawalRequest
import com.example.util.AllCountriesProvider

data class AdminCountryInfo(
    val code: String,
    val name: String,
    val flag: String,
    val dialCode: String
)

val ADMIN_PRESET_COUNTRIES = AllCountriesProvider.allCountries

val ADMIN_SERVICES = listOf("Facebook", "Instagram", "WhatsApp")

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
    val zenexOtpRate by VirtualNumberManager.zenexOtpRate.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: SMS Panels, 1: Withdrawals, 2: Users, 3: Live CDR/DLR

    LaunchedEffect(Unit) {
        UnixSmsManager.init(context)
        AuthManager.init(context)
        WithdrawalManager.init(context)
        VirtualNumberManager.init(context)
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
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Live CDR/DLR (${unixState.liveCdrRecords.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        canUploadNumbers = if (isPrimeAdmin) true else subAdminPerms.canUploadNumbers,
                        canDeleteNumbers = if (isPrimeAdmin) true else subAdminPerms.canDeleteNumbers,
                        canUpdateOtpRate = if (isPrimeAdmin) true else subAdminPerms.canUpdateOtpRate
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
                // TAB 3: LIVE CDR & DLR TRAFFIC
                if (isPrimeAdmin || subAdminPerms.canViewLiveCdr) {
                    item {
                        AdminLiveCdrCard(
                            unixState = unixState
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
    zenexRate: Double = 0.014,
    canUploadNumbers: Boolean = true,
    canDeleteNumbers: Boolean = true,
    canUpdateOtpRate: Boolean = true
) {
    var selectedCountry by remember { mutableStateOf(ADMIN_PRESET_COUNTRIES.first()) }
    var selectedService by remember { mutableStateOf(ADMIN_SERVICES.first()) }
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
        var customRateInputText by remember { mutableStateOf("") }

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

                // Country Selector Dropdown with Search Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "1. দেশ নির্বাচন করুন (Select Country):", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
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
                            else -> Color(0xFF25D366) // WhatsApp
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
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
                    Text(text = "3. নাম্বার লিখুন বা ফাইল আপলোড করুন:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)

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
                            service = selectedService
                        )
                        isUploading = false
                        if (addedCount > 0) {
                            Toast.makeText(context, "✓ $addedCount টি নতুন নাম্বার সফলভাবে প্যানেলে যোগ হয়েছে!", Toast.LENGTH_LONG).show()
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

        val availableCount = remember(unixState.uploadedNumbers) {
            unixState.uploadedNumbers.count { !it.isUsed && it.otpCode.isNullOrEmpty() }
        }
        val usedCount = remember(unixState.uploadedNumbers) {
            unixState.uploadedNumbers.count { it.isUsed && it.otpCode.isNullOrEmpty() }
        }
        val otpRcvCount = remember(unixState.uploadedNumbers) {
            unixState.uploadedNumbers.count { !it.otpCode.isNullOrEmpty() }
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
                            Text("${unixState.uploadedNumbers.size}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF94A3B8))
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
                                    UnixSmsManager.deleteSingleNumber(item.id)
                                    Toast.makeText(context, "ডিলিট করা হয়েছে: ${item.number}", Toast.LENGTH_SHORT).show()
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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
    var newUserVerified by remember { mutableStateOf(true) }
    var newUserApproved by remember { mutableStateOf(true) }
    var isCreatingUser by remember { mutableStateOf(false) }
    var isSyncingCloud by remember { mutableStateOf(false) }
    var createdSuccessCredentials by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var broadcastMsg by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val cloudStatus by AuthManager.cloudSyncStatus.collectAsState()

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

        // Create User + Admin Verify + Login Approval Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "VERIFY, GENERATE & APPROVE USER",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "☁️ $cloudStatus",
                                fontSize = 9.5.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // 1-Tap Auto Generate Gmail + Verified Pass Button
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.clickable {
                            val baseName = newUserName.trim().ifEmpty {
                                listOf("Rahim", "Karim", "Sakib", "Tanvir", "Hasan", "Fahim", "Rifat", "Siam").random()
                            }
                            if (newUserName.isBlank()) {
                                newUserName = baseName
                            }
                            val cleanSlug = baseName.lowercase().filter { it.isLetterOrDigit() }.take(8).ifEmpty { "user" }
                            val candidateEmail = if (newUserEmail.isNotBlank() && newUserEmail.contains("@")) {
                                newUserEmail.trim().lowercase()
                            } else {
                                val baseCandidate = "${cleanSlug}2026@gmail.com"
                                if (managedUsers.any { it.email.equals(baseCandidate, ignoreCase = true) }) {
                                    "${cleanSlug}${(10..99).random()}@gmail.com"
                                } else {
                                    baseCandidate
                                }
                            }
                            newUserEmail = candidateEmail
                            newUserPass = AuthManager.generateVerifiedPasswordForEmail(candidateEmail, baseName)
                            newUserVerified = true
                            newUserApproved = true
                            VibrationHelper.vibrateSuccess(context)
                            Toast.makeText(context, "⚡ ভেরিফাইড জিমেইল ও পাসওয়ার্ড অটো-জেনারেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Auto Generate", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "আপনার ভেরিফিকেশন + জেনারেট করা জিমেইল/পাসওয়ার্ড + লগইন Approval ছাড়া কেউ লগইন করতে পারবে না। নিচে ইউজার তৈরি করলে তা সাথে সাথে ক্লাউড সার্ভারে সিঙ্ক হয়ে যাবে:",
                    fontSize = 10.5.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

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
                    label = { Text("জিমেইল (Gmail / Email)") },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newUserPass,
                        onValueChange = { newUserPass = it },
                        label = { Text("পাসওয়ার্ড (Password)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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

                    OutlinedButton(
                        onClick = {
                            val em = newUserEmail.trim().let {
                                if (it.isEmpty()) "user2026@gmail.com"
                                else if (!it.contains("@")) "$it@gmail.com"
                                else it
                            }
                            if (newUserEmail.isBlank()) newUserEmail = em
                            newUserPass = AuthManager.generateVerifiedPasswordForEmail(em, newUserName)
                            Toast.makeText(context, "🔐 ভেরিফাইড পাসওয়ার্ড জেনারেট হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF818CF8)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
                    ) {
                        Text("⚡ Pass", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Admin Verification & Login Approval Gate Toggles
                Surface(
                    color = Color(0xFF09121E),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E324A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "✅ 1. Admin Verification (এডমিন ভেরিফাইড)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (newUserVerified) Color(0xFF34D399) else Color(0xFFFBBF24)
                                )
                                Text(
                                    text = "এডমিন ভেরিফিকেশন ছাড়া এই জিমেইল কাজ করবে না",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Switch(
                                checked = newUserVerified,
                                onCheckedChange = { newUserVerified = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🛡️ 2. Login Approval (লগইন অনুমোদন)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (newUserApproved) Color(0xFF38BDF8) else Color(0xFFFBBF24)
                                )
                                Text(
                                    text = if (newUserApproved) "ইউজার সাথে সাথে লগইন করতে পারবে (Approved)" else "অনুমোদন পেন্ডিং থাকবে (আপনি Approve না দেওয়া পর্যন্ত লগইন বন্ধ)",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Switch(
                                checked = newUserApproved,
                                onCheckedChange = { newUserApproved = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF0284C7)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!isCreatingUser) {
                            val emRaw = newUserEmail.trim().lowercase()
                            val finalEmail = if (emRaw.isNotEmpty() && !emRaw.contains("@")) "$emRaw@gmail.com" else emRaw
                            val finalPass = newUserPass.trim()
                            val finalName = newUserName.trim()
                            val ver = newUserVerified
                            val app = newUserApproved

                            if (finalEmail.isEmpty() || finalPass.isEmpty()) {
                                Toast.makeText(context, "ইমেইল ও পাসওয়ার্ড প্রদান করুন অথবা Auto Generate চাপুন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isCreatingUser = true
                            coroutineScope.launch {
                                val (success, msg) = withContext(Dispatchers.IO) {
                                    AuthManager.createManagedUser(
                                        emailInput = finalEmail,
                                        passInput = finalPass,
                                        nameInput = finalName,
                                        isVerified = ver,
                                        isApproved = app
                                    )
                                }
                                isCreatingUser = false
                                if (success) {
                                    val statusLabel = if (ver && app) "VERIFIED & APPROVED ✅" else "PENDING APPROVAL ⏳"
                                    createdSuccessCredentials = Triple(finalEmail, finalPass, statusLabel)
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    newUserEmail = ""
                                    newUserPass = ""
                                    newUserName = ""
                                    VibrationHelper.vibrateSuccess(context)
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = !isCreatingUser,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    if (isCreatingUser) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ক্লাউড সার্ভারে ভেরিফাই ও সিঙ্ক হচ্ছে...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VERIFY, APPROVE & GENERATE USER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Show Success Copy Box
                createdSuccessCredentials?.let { (em, pw, stLabel) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "✓ অ্যাকাউন্ট তৈরি ও ক্লাউড সিঙ্ক সফল!", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                    Text(text = "Status: $stLabel", color = Color(0xFFA7F3D0), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val text = "Work ShortCut Login Credentials:\nEmail: $em\nPassword: $pw\nStatus: $stLabel"
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

        // List of Created Users with ACTIVE / PENDING APPROVAL / INACTIVE / BANNED Filter
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
                    Column {
                        Text(
                            text = "MANAGED USER ACCOUNTS (${managedUsers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val approvedCount = managedUsers.count { it.isActive && it.isVerified && it.isApproved && !it.isBanned }
                        val pendingCount = managedUsers.count { (!it.isVerified || !it.isApproved) && !it.isBanned }
                        Text(
                            text = "✅ $approvedCount Approved  |  ⏳ $pendingCount Pending Approval",
                            fontSize = 10.sp,
                            color = Color(0xFF81D4FA),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Force Cloud Sync Button for Admin
                    OutlinedButton(
                        onClick = {
                            if (!isSyncingCloud) {
                                isSyncingCloud = true
                                coroutineScope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        AuthManager.syncToCloudInternal(managedUsers)
                                    }
                                    isSyncingCloud = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "✓ সকল ইউজার ক্লাউড সার্ভারে সিঙ্ক সম্পন্ন হয়েছে!" else "⚠️ ক্লাউড সিঙ্ক পুনরায় চেষ্টা করুন",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    VibrationHelper.vibrateSuccess(context)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (isSyncingCloud) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF38BDF8), strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Syncing...", fontSize = 9.5.sp, color = Color(0xFF38BDF8))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cloud Sync", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var userFilterTab by remember { mutableStateOf("ALL") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val activeCount = managedUsers.count { it.isActive && it.isVerified && it.isApproved && !it.isBanned }
                    val pendingCount = managedUsers.count { (!it.isVerified || !it.isApproved) && !it.isBanned }
                    val inactiveCount = managedUsers.count { !it.isActive && !it.isBanned }
                    val bannedCount = managedUsers.count { it.isBanned }

                    listOf(
                        Triple("ALL", "ALL (${managedUsers.size})", Color(0xFF38BDF8)),
                        Triple("ACTIVE", "✅ OK ($activeCount)", Color(0xFF10B981)),
                        Triple("PENDING", "⏳ WAIT ($pendingCount)", Color(0xFFA855F7)),
                        Triple("INACTIVE", "⚪ OFF ($inactiveCount)", Color(0xFFF59E0B)),
                        Triple("BANNED", "🚫 BAN ($bannedCount)", Color(0xFFEF4444))
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
                                fontSize = 8.5.sp,
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
                        "ACTIVE" -> managedUsers.filter { it.isActive && it.isVerified && it.isApproved && !it.isBanned }
                        "PENDING" -> managedUsers.filter { (!it.isVerified || !it.isApproved) && !it.isBanned }
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
                                onToggleApproval = {
                                    AuthManager.toggleUserApproval(user.email)
                                    val nowApproved = !(user.isVerified && user.isApproved)
                                    val msg = if (nowApproved) "✅ ${user.name}-এর ভেরিফিকেশন ও লগইন অনুমোদন (Approval) দেওয়া হয়েছে!"
                                    else "⏸️ ${user.name}-এর লগইন অনুমোদন স্থগিত করা হয়েছে!"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onChangePassword = { newPass ->
                                    if (AuthManager.changeUserPassword(user.email, newPass)) {
                                        Toast.makeText(context, "✓ ${user.name}-এর নতুন পাসওয়ার্ড ক্লাউডে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
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
    onToggleApproval: () -> Unit,
    onChangePassword: (String) -> Unit,
    onToggleBan: () -> Unit,
    onResetLogins: () -> Unit,
    onDelete: () -> Unit,
    onToggleSubAdmin: (Boolean) -> Unit,
    onUpdatePermissions: (com.example.service.SubAdminPermissions) -> Unit,
    onUpdateStats: (total: Int, balance: Double, today: Int, l7: Int, l30: Int) -> Unit,
    onCopy: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showEditStatsDialog by remember { mutableStateOf(false) }
    var showChangePassDialog by remember { mutableStateOf(false) }

    val isSubAdmin = user.role == "SUB_ADMIN"
    val isFullyApproved = user.isVerified && user.isApproved

    Surface(
        color = if (user.isBanned) Color(0xFF180A0A)
                else if (!isFullyApproved) Color(0xFF171124)
                else if (user.isActive) Color(0xFF071B12)
                else Color(0xFF161208),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (user.isBanned) Color(0xFFEF4444)
            else if (!isFullyApproved) Color(0xFFA855F7)
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
                    } else if (!isFullyApproved) {
                        Surface(
                            color = Color(0xFFA855F7).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFA855F7))
                        ) {
                            Text(
                                text = "⏳ PENDING APPROVAL",
                                color = Color(0xFFD8B4FE),
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
                                    text = "✅ APPROVED",
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
                    // Status Toggle Button
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
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
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

                // 1-Tap Admin Verify + Login Approval Toggle Button
                Surface(
                    color = if (isFullyApproved) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFFA855F7).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isFullyApproved) Color(0xFF10B981) else Color(0xFFA855F7)),
                    modifier = Modifier.clickable { onToggleApproval() }
                ) {
                    Text(
                        text = if (isFullyApproved) "✅ ভেরিফাইড ও Approved (বাতিল করুন)" else "🔓 Approve & Verify করুন",
                        color = if (isFullyApproved) Color(0xFF34D399) else Color(0xFFE9D5FF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { showChangePassDialog = true }
                    ) {
                        Text(
                            text = "🔑 নতুন পাসওয়ার্ড",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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

                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier.clickable { showEditStatsDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Edit OTP/Bal", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
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

            if (user.lastLoginAt > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                val timeDiff = (System.currentTimeMillis() - user.lastLoginAt) / 1000
                val timeStr = when {
                    timeDiff < 60 -> "Just now"
                    timeDiff < 3600 -> "${timeDiff / 60}m ago"
                    timeDiff < 86400 -> "${timeDiff / 3600}h ago"
                    else -> "${timeDiff / 86400}d ago"
                }
                Text(
                    text = "Last login: $timeStr ${if (user.lastDeviceName.isNotEmpty()) "• ${user.lastDeviceName}" else ""}",
                    color = Color(0xFF64748B),
                    fontSize = 9.sp
                )
            }
        }
    }

    // Change Password Dialog
    if (showChangePassDialog) {
        var updatedPass by remember { mutableStateOf(user.passwordHash) }
        AlertDialog(
            onDismissRequest = { showChangePassDialog = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Text("🔑 পাসওয়ার্ড পরিবর্তন ও ভেরিফাই", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ইউজার: ${user.email}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    OutlinedTextField(
                        value = updatedPass,
                        onValueChange = { updatedPass = it },
                        label = { Text("নতুন পাসওয়ার্ড") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            updatedPass = AuthManager.generateVerifiedPasswordForEmail(user.email, user.name)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚡ অটো ভেরিফাইড পাসওয়ার্ড জেনারেট", fontSize = 11.sp, color = Color(0xFF34D399))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedPass.isNotBlank()) {
                            onChangePassword(updatedPass.trim())
                            showChangePassDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("সেভ ও সিঙ্ক করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showChangePassDialog = false }) {
                    Text("বাতিল", color = Color.White)
                }
            }
        )
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
                        Text("সাব এডমিন পারমিশন কন্ট্রোল", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(user.email, fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("এই সাব এডমিন কোন কোন কাজ করতে পারবে তা নির্ধারণ করুন:", fontSize = 11.sp, color = Color(0xFFCBD5E1))
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

/**
 * Tab 2: Live CDR Traffic Stream (Admin View)
 */
@Composable
fun AdminLiveCdrCard(
    unixState: com.example.service.UnixSmsState
) {
    var searchFilter by remember { mutableStateOf("") }
    var selectedDlrCountry by remember { mutableStateOf("ALL") }
    var dlrCountryDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            text = "LIVE UNIX SMS STREAM (1s FAST POLL)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }

                    Surface(
                        color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${unixState.liveCdrRecords.size} Records",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DLR Country Selector Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "দেশ নির্বাচন (Select DLR Country):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                Box {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF243B55)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dlrCountryDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayName = if (selectedDlrCountry == "ALL") "🌐 সব দেশ (ALL COUNTRIES)" else {
                                val c = ADMIN_PRESET_COUNTRIES.find { it.code == selectedDlrCountry }
                                "${c?.flag ?: "🌐"} ${c?.name ?: selectedDlrCountry} (${c?.dialCode ?: ""})"
                            }
                            Text(text = displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "▼", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DropdownMenu(
                        expanded = dlrCountryDropdownExpanded,
                        onDismissRequest = { dlrCountryDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF0F1A28))
                    ) {
                        DropdownMenuItem(
                            text = { Text("🌐 সব দেশ (ALL COUNTRIES)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold) },
                            onClick = {
                                selectedDlrCountry = "ALL"
                                dlrCountryDropdownExpanded = false
                            }
                        )

                        // Distinct countries from uploaded numbers or preset countries
                        val distinctCountryCodes = (unixState.uploadedNumbers.map { it.countryCode } + listOf("US", "GB", "BD", "IN", "CA", "DE", "FR", "NL", "MY", "SG", "ID")).distinct()
                        distinctCountryCodes.forEach { code ->
                            val c = ADMIN_PRESET_COUNTRIES.find { it.code == code }
                            if (c != null) {
                                DropdownMenuItem(
                                    text = { Text("${c.flag} ${c.name} (${c.dialCode})", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        selectedDlrCountry = c.code
                                        dlrCountryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("মেসেজ, নাম্বার বা সেন্ডার দিয়ে ফিল্টার করুন...", fontSize = 11.sp, color = Color(0xFF64748B)) },
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
            }
        }

        val filteredRecords = unixState.liveCdrRecords.filter { cdr ->
            val matchesSearch = searchFilter.isBlank() ||
                    cdr.num.contains(searchFilter) ||
                    cdr.cli.contains(searchFilter, ignoreCase = true) ||
                    cdr.message.contains(searchFilter, ignoreCase = true)

            val matchesCountry = if (selectedDlrCountry == "ALL") true else {
                val country = ADMIN_PRESET_COUNTRIES.find { it.code == selectedDlrCountry }
                val dialCodeClean = country?.dialCode?.replace("+", "") ?: ""
                val numClean = cdr.num.replace("+", "").trim()
                val matchesDial = dialCodeClean.isNotEmpty() && numClean.startsWith(dialCodeClean)
                val matchesUploaded = unixState.uploadedNumbers.any { up ->
                    up.countryCode == selectedDlrCountry && (up.number.contains(numClean) || numClean.contains(up.number.replace("+", "")))
                }
                matchesDial || matchesUploaded
            }

            matchesSearch && matchesCountry
        }

        if (filteredRecords.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোনো নতুন SMS রেকর্ড নেই বা লোড হচ্ছে...",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredRecords.forEach { cdr ->
                    AdminCdrItemRow(cdr = cdr)
                }
            }
        }
    }
}

@Composable
fun AdminCdrItemRow(cdr: UnixSmsCdrRecord) {
    Surface(
        color = Color(0xFF101824),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D40)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = cdr.cli.ifEmpty { "SMS" },
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cdr.num,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = cdr.dt,
                    color = Color(0xFF64748B),
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = cdr.message,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
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
