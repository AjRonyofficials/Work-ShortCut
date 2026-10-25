package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import com.example.service.PhoneItem
import com.example.service.VirtualNumberChecker
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.OverlayStateManager
import com.example.ui.components.KopRangeSelectorBox
import com.example.ui.components.UserProfileOtpHistoryDialog
import com.example.util.VibrationHelper
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.model.ActiveRangeItem
import com.example.data.model.BroadcastFeedItem
import com.example.data.model.ProvisionedNumber
import com.example.service.VirtualNumberManager
import com.example.util.ClipboardHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VirtualNumbersSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by VirtualNumberManager.state.collectAsState()
    val overlayState by OverlayStateManager.uiState.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Number, 1: Live Console
    var filterTab by remember { mutableStateOf("ALL") } // "ALL", "SUCCESS", "PENDING", "FAILED"
    var consoleFilter by remember { mutableStateOf("") }
    var quantityDropdownExpanded by remember { mutableStateOf(false) }
    val selectedPanel by VirtualNumberManager.selectedPanel.collectAsState()
    val zenexOtpRate by VirtualNumberManager.zenexOtpRate.collectAsState()
    var panelDropdownExpanded by remember { mutableStateOf(false) }
    var showProfileHistoryDialog by remember { mutableStateOf(false) }
    var showFreshCheckerDialog by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "✓ নোটিফিকেশন অন হয়েছে! ওটিপি সরাসরি নোটিফিকেশনে আসবে ও কী-বোর্ডে অটো কপি হবে।", Toast.LENGTH_SHORT).show()
        }
    }

    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        VirtualNumberManager.init(context)
        VirtualNumberManager.startAutoPolling(context, autoCopy = false)
        VirtualNumberManager.fetchActiveRanges()
        VirtualNumberManager.fetchGlobalBroadcast()
        var pollCounter = 0
        while (true) {
            delay(4000L)
            nowTick = System.currentTimeMillis()
            pollCounter++
            if (pollCounter >= 3) {
                pollCounter = 0
                VirtualNumberManager.fetchActiveRanges()
                VirtualNumberManager.fetchGlobalBroadcast()
            }
        }
    }

    val total = state.provisionedNumbers.size
    val successCount = state.provisionedNumbers.count { it.status == "success" || !it.otpCode.isNullOrEmpty() }
    val waitCount = state.provisionedNumbers.count {
        it.otpCode.isNullOrEmpty() && (it.status == "pending" || it.status == "wait") && nowTick < it.expiresAt
    }
    val failedCount = state.provisionedNumbers.count {
        it.status == "failed" || it.status == "expired" || (it.otpCode.isNullOrEmpty() && nowTick >= it.expiresAt)
    }
    val successRate = if (total > 0) (successCount.toFloat() / total.toFloat()) * 100f else 0f

    val filteredList = when (filterTab) {
        "FRESH" -> state.provisionedNumbers.filter { it.fbTagType == PhoneItem.TagType.FRESH }
        "SUCCESS" -> state.provisionedNumbers.filter { it.status == "success" || !it.otpCode.isNullOrEmpty() }
        "PENDING" -> state.provisionedNumbers.filter { it.otpCode.isNullOrEmpty() && (it.status == "pending" || it.status == "wait") && nowTick < it.expiresAt }
        "FAILED" -> state.provisionedNumbers.filter { it.status == "failed" || it.status == "expired" || (it.otpCode.isNullOrEmpty() && nowTick >= it.expiresAt) }
        else -> state.provisionedNumbers
    }

    UserProfileOtpHistoryDialog(
        isOpen = showProfileHistoryDialog,
        onDismiss = { showProfileHistoryDialog = false }
    )

    VirtualNumberFreshCheckerDialog(
        isOpen = showFreshCheckerDialog,
        onDismiss = { showFreshCheckerDialog = false }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D141C))
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TOP PANEL SELECTOR (1. Unix SMS, 2. Zenex SMS) + 7D OTP HISTORY BUTTON
        item {
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131F2E)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A56)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SELECT PANEL:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF90A4AE),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Surface(
                                color = if (selectedPanel == 0) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF10B981).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selectedPanel == 0) Color(0xFF38BDF8) else Color(0xFF34D399)
                                ),
                                modifier = Modifier.clickable { panelDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedPanel == 0) {
                                        Text(
                                            text = "Premium Numbers",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    } else if (selectedPanel == 3) {
                                        Text(
                                            text = "Premium Number V2",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    } else if (selectedPanel == 2) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Kop Engine 2 ",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "(Api Number)",
                                                color = Color(0xFFC4B5FD),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Kop Engine ",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "(Api Number)",
                                                color = Color(0xFFA7F3D0),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "▼", color = Color(0xFF81D4FA), fontSize = 10.sp)
                                }
                            }

                            DropdownMenu(
                                expanded = panelDropdownExpanded,
                                onDismissRequest = { panelDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF0F1A28))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Premium Numbers", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFF00E676).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(text = "FAST 1s", color = Color(0xFF00E676), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        VirtualNumberManager.setSelectedPanel(0)
                                        panelDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Premium Number V2", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(text = "V2 LIVE POOL", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        VirtualNumberManager.setSelectedPanel(3)
                                        panelDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Kop Engine ", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = "(Api Number)", color = Color(0xFF6EE7B7), fontSize = 10.sp, fontWeight = FontWeight.Normal)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFF34D399).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(text = "NETWORK", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        VirtualNumberManager.setSelectedPanel(1)
                                        panelDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Kop Engine 2 ", color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = "(Api Number)", color = Color(0xFFC4B5FD), fontSize = 10.sp, fontWeight = FontWeight.Normal)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(text = "NEW API", color = Color(0xFFC4B5FD), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        VirtualNumberManager.setSelectedPanel(2)
                                        panelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // User Profile & History Button
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.clickable { showProfileHistoryDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "👤 প্রোফাইল (PROFILE)",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        if (!hasNotificationPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF281E0F)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFB300).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "নোটিফিকেশন পারমিশন অন করুন",
                                    color = Color(0xFFFFE082),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "ওটিপি আসলে নোটিফিকেশনে দেখতে ও কী-বোর্ডে অটো-কপি হতে পারমিশন চালু করুন",
                                    color = Color(0xFFCFD8DC),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB300),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("অন করুন", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        val isPanelAllowed = when (selectedPanel) {
            0 -> com.example.service.AuthManager.isSectionAllowedForCurrentUser("UNIX_SMS")
            3 -> com.example.service.AuthManager.isSectionAllowedForCurrentUser("PREMIUM_V2")
            2 -> com.example.service.AuthManager.isSectionAllowedForCurrentUser("KOP_2")
            else -> com.example.service.AuthManager.isSectionAllowedForCurrentUser("KOP_1")
        }

        if (!isPanelAllowed) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E151A)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("🚫 এক্সেস সীমিত (ACCESS RESTRICTED)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "এডমিন কর্তৃক এই প্যানেলটির এক্সেস আপনার অ্যাকাউন্টের জন্য সাময়িকভাবে বন্ধ রাখা হয়েছে। পুনরায় চালু করার জন্য এডমিনের সাথে যোগাযোগ করুন।",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (selectedPanel == 0) {
            item {
                UnixSmsSectionContent()
            }
        } else if (selectedPanel == 3) {
            item {
                PremiumNumberV2SectionContent()
            }
        } else if (selectedPanel == 2) {
            item {
                KopEngine2SectionContent()
            }
        } else {
        item {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. ZENEX HEADER & TODAY OTP COUNTER
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13202E)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A56)),
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
                                text = "ZENEX NETWORK ACTIVE",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        // Prominent Today OTP Counter Card
                        Surface(
                            color = Color(0xFF0091EA).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B0FF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "OTP",
                                    tint = Color(0xFF40C4FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "TODAY OTP: ${state.todayOtpCount}",
                                    color = Color(0xFFE1F5FE),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Stat Cards: TOTAL, SUCCESS, WAIT, FAILED
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill(
                            label = "TOTAL",
                            value = total.toString(),
                            color = Color(0xFFE0E0E0),
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "SUCCESS",
                            value = successCount.toString(),
                            color = Color(0xFF00E676),
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "WAIT (20m)",
                            value = waitCount.toString(),
                            color = Color(0xFFFFD600),
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "FAILED",
                            value = failedCount.toString(),
                            color = Color(0xFFFF5252),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Smart Automation Toggles (Option 1 & Option 6) + Option 5 Clear Failed
                    Surface(
                        color = Color(0xFF0F1A26),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A56)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Option 1 Chip: Auto-Copy
                                Surface(
                                    color = if (overlayState.autoCopyOtp) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF263238),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (overlayState.autoCopyOtp) Color(0xFF00E676) else Color(0xFF546E7A)
                                    ),
                                    modifier = Modifier.clickable { OverlayStateManager.toggleAutoCopyOtp() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "1. Auto-Copy: ",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (overlayState.autoCopyOtp) "ON" else "OFF",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (overlayState.autoCopyOtp) Color(0xFF00E676) else Color(0xFFFF5252)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Option 6 Chip: Sound/Vibration
                                Surface(
                                    color = if (overlayState.otpSoundVibrationEnabled) Color(0xFFFFB300).copy(alpha = 0.2f) else Color(0xFF263238),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (overlayState.otpSoundVibrationEnabled) Color(0xFFFFB300) else Color(0xFF546E7A)
                                    ),
                                    modifier = Modifier.clickable { OverlayStateManager.toggleOtpSoundVibrationEnabled() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "6. Sound/Vibe: ",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (overlayState.otpSoundVibrationEnabled) "ON" else "OFF",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (overlayState.otpSoundVibrationEnabled) Color(0xFFFFB300) else Color(0xFFFF5252)
                                        )
                                    }
                                }
                            }

                            // Option 5: Clear Failed (20m) Button
                            if (failedCount > 0) {
                                Surface(
                                    color = Color(0xFFFF5252).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                                    modifier = Modifier.clickable { VirtualNumberManager.clearFailedNumbers(context) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Clear Failed",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF5252)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // SUCCESS RATE PROGRESS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↗ SUCCESS RATE",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f%%", successRate),
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (successRate / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00E676),
                        trackColor = Color(0xFF1E2E3E)
                    )

                    Spacer(modifier = Modifier.height(10.dp))



                    // 24H DATA AUTO-RESET COUNTDOWN BANNER
                    Spacer(modifier = Modifier.height(12.dp))
                    val cycleStart = state.resetCycleStartTime
                    val isCycleRunning = cycleStart > 0L
                    val remainingMs = if (isCycleRunning) {
                        ((cycleStart + 24 * 60 * 60 * 1000L) - nowTick).coerceAtLeast(0L)
                    } else 0L

                    val resetHours = (remainingMs / (1000 * 60 * 60)).toInt()
                    val resetMinutes = ((remainingMs / (1000 * 60)) % 60).toInt()
                    val resetSeconds = ((remainingMs / 1000) % 60).toInt()

                    Surface(
                        color = Color(0xFF0F1A26),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCycleRunning) Color(0xFF00B0FF).copy(alpha = 0.5f) else Color(0xFF263238)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = if (isCycleRunning) Color(0xFF00E5FF) else Color(0xFF78909C),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "24H DATA AUTO-RESET",
                                        color = if (isCycleRunning) Color(0xFF00E5FF) else Color(0xFF90A4AE),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = if (isCycleRunning) {
                                            "নম্বর ও OTP ২৪ ঘণ্টা পর স্বয়ংক্রিয়ভাবে মুছে যাবে"
                                        } else {
                                            "১ম নম্বর/OTP নেওয়ার পর থেকে ২৪ ঘণ্টার টাইমার চালু হবে"
                                        },
                                        color = Color(0xFF78909C),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }

                            if (isCycleRunning) {
                                Surface(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%02dh %02dm %02ds", resetHours, resetMinutes, resetSeconds),
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Ready (24h)",
                                    color = Color(0xFF546E7A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // SUB-NAVIGATION TABS (Get Number vs Live Console)
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color(0xFF111D2A),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = Color(0xFF00B0FF),
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1D3247), RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GET NUMBER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LIVE CONSOLE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
            }
        }

        // TAB CONTENT
        if (selectedSubTab == 0) {
            // 2. GET NUMBER CONTROL CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13202E)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A56)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        KopRangeSelectorBox(
                            title = "TARGET RANGE / LIVE POOL (KOP 1)",
                            engineName = "Kop Engine",
                            currentRange = state.targetRange,
                            onRangeSelected = { VirtualNumberManager.setTargetRange(it) },
                            activeRanges = state.activeRanges,
                            broadcastFeed = state.broadcastFeed,
                            onRefreshLivePool = {
                                VirtualNumberManager.fetchActiveRanges()
                                VirtualNumberManager.fetchGlobalBroadcast()
                            },
                            accentColor = Color(0xFF00B0FF)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Checkboxes & Quantity Dropdown in Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // National & No (+) checkboxes
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = state.isNational,
                                    onCheckedChange = {
                                        VirtualNumberManager.setOptions(it, state.removePlus)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00B0FF),
                                        uncheckedColor = Color(0xFF546E7A)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("National", color = Color(0xFFB0BEC5), fontSize = 11.sp)

                                Spacer(modifier = Modifier.width(10.dp))

                                Checkbox(
                                    checked = state.removePlus,
                                    onCheckedChange = {
                                        VirtualNumberManager.setOptions(state.isNational, it)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00B0FF),
                                        uncheckedColor = Color(0xFF546E7A)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("No (+)", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                            }

                            // QUANTITY DROPDOWN (1 to 10 numbers)
                            Box {
                                Surface(
                                    color = Color(0xFF0B141E),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26415E)),
                                    modifier = Modifier.clickable { quantityDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Qty: ${state.requestCount}",
                                            color = Color(0xFF40C4FF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF40C4FF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = quantityDropdownExpanded,
                                    onDismissRequest = { quantityDropdownExpanded = false },
                                    properties = androidx.compose.ui.window.PopupProperties(
                                        focusable = true,
                                        dismissOnClickOutside = true,
                                        dismissOnBackPress = true
                                    ),
                                    modifier = Modifier.background(Color(0xFF13202E))
                                ) {
                                    (1..10).forEach { count ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "$count টি নম্বর",
                                                    color = if (state.requestCount == count) Color(0xFF00E676) else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (state.requestCount == count) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                quantityDropdownExpanded = false
                                                VirtualNumberManager.setRequestCount(count)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // GET NUMBER ACTION BUTTON
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                VirtualNumberManager.provisionNumbers(context)
                            },
                            enabled = !state.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0091EA),
                                disabledContainerColor = Color(0xFF0091EA).copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PROVISIONING...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.requestCount > 1) "GET ${state.requestCount} NUMBERS (${String.format(Locale.US, "%.2f", zenexOtpRate * state.requestCount)} ৳)" else "GET NUMBER (${String.format(Locale.US, "%.2f", zenexOtpRate)} ৳ / OTP)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }

                        // FB FRESH CHECKER & BATCH CHECK ACTION ROW
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = Color(0xFF0F2B1D),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clickable { showFreshCheckerDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🔍 FRESH CHECKER (1-10)", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                color = Color(0xFF13202E),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A56)),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .clickable {
                                        val pendingNums = state.provisionedNumbers.filter { it.fbStatus != "Fresh (Free)" && it.fbStatus != "Taken (Used)" }
                                        if (pendingNums.isEmpty()) {
                                            Toast.makeText(context, "সব নম্বর ইতিমধ্যেই চেক করা হয়েছে", Toast.LENGTH_SHORT).show()
                                        } else {
                                            VirtualNumberManager.checkFbStatusForNumbers(pendingNums.map { it.id to it.number })
                                            Toast.makeText(context, "${pendingNums.size} টি নম্বর চেক শুরু হয়েছে...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00B0FF), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CHECK ALL FB", color = Color(0xFF00B0FF), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 3. FEED HEADER & FILTERS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00B0FF))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FEED (${filteredList.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val successfulKop1Numbers = remember(state.provisionedNumbers) {
                            state.provisionedNumbers.filter { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (successfulKop1Numbers.isNotEmpty()) Color(0xFF0091EA).copy(alpha = 0.25f) else Color(0xFF142434),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (successfulKop1Numbers.isNotEmpty()) Color(0xFF00B0FF) else Color(0xFF26415E)),
                            modifier = Modifier.clickable {
                                if (successfulKop1Numbers.isEmpty()) {
                                    Toast.makeText(context, "ডাউনলোড করার মতো কোনো সফল OTP নম্বর পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                } else {
                                    val exportItems = successfulKop1Numbers.map { num ->
                                        val cInfo = com.example.util.AllCountriesProvider.detectCountry(num.number)
                                            ?: com.example.util.AllCountriesProvider.detectCountry(num.range ?: "")
                                            ?: com.example.util.AllCountriesProvider.detectCountry(num.country)
                                        com.example.util.ExportOtpItem(
                                            phoneNumber = num.number,
                                            country = cInfo?.name ?: num.country,
                                            iso = (cInfo?.code ?: num.iso).uppercase(),
                                            flag = cInfo?.flag ?: "🌐",
                                            serviceOrRange = num.range ?: "Kop 1",
                                            otpCode = num.otpCode ?: "",
                                            otpMessage = num.otpMessage,
                                            timestamp = num.timestamp,
                                            rate = zenexOtpRate
                                        )
                                    }
                                    com.example.util.OtpExportHelper.exportOtps(context, "Kop Engine 1", exportItems)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = if (successfulKop1Numbers.isNotEmpty()) Color(0xFF00B0FF) else Color(0xFF90A4AE), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (successfulKop1Numbers.isNotEmpty()) "📥 ডাউনলোড (${successfulKop1Numbers.size})" else "📥 ডাউনলোড",
                                    fontSize = 9.sp,
                                    color = if (successfulKop1Numbers.isNotEmpty()) Color.White else Color(0xFF90A4AE),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                VirtualNumberManager.fetchIncomingOtps(context, autoCopy = true)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Feed Filters: ALL, FRESH, SUCCESS, PENDING, FAILED
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "FRESH", "SUCCESS", "PENDING", "FAILED").forEach { filter ->
                            val isSelected = filterTab == filter
                            val filterColor = if (filter == "FRESH") Color(0xFF00E676) else Color(0xFF0091EA)
                            Surface(
                                color = if (isSelected) filterColor else Color(0xFF142434),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { filterTab = filter }
                            ) {
                                Text(
                                    text = filter,
                                    color = if (isSelected) (if (filter == "FRESH") Color.Black else Color.White) else (if (filter == "FRESH") Color(0xFF00E676) else Color(0xFF90A4AE)),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. PROVISIONED NUMBERS FEED LIST
            if (filteredList.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111D2A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "কোনো নম্বর নেই",
                                color = Color(0xFF78909C),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "GET NUMBER বাটনে ক্লিক করে ভার্চুয়াল নম্বর যুক্ত করুন",
                                color = Color(0xFF546E7A),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    ProvisionedNumberCard(item = item, now = nowTick, context = context)
                }
            }
        } else {
            // TAB 2: LIVE CONSOLE (SS-style Unified Range Option & Global SMS Feed)
            // 1. Unified Range Option Card (All ranges in one single compact option)
            item {
                var rangeOptionExpanded by remember { mutableStateOf(false) }
                val currentSelectedRange = state.targetRange.ifEmpty { "237627XXX" }
                val matchingRangeItem = state.activeRanges.firstOrNull { it.range == currentSelectedRange }
                    ?: state.activeRanges.firstOrNull()

                Surface(
                    color = Color(0xFF0D1724),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A56)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rangeOptionExpanded = !rangeOptionExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00B0FF))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = matchingRangeItem?.range ?: currentSelectedRange,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (matchingRangeItem != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFF18324E),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${matchingRangeItem.service} • ${matchingRangeItem.tag}",
                                            color = Color(0xFF64B5F6),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF00B0FF).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${matchingRangeItem?.hits ?: 17} Hits",
                                        color = Color(0xFF40C4FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = {
                                        val r = matchingRangeItem?.range ?: currentSelectedRange
                                        VirtualNumberManager.setTargetRange(r)
                                        ClipboardHelper.copyToClipboard(context, r, "Target Range")
                                        Toast.makeText(context, "রেঞ্জ কপি হয়েছে: $r", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy Range",
                                        tint = Color(0xFF81D4FA),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { rangeOptionExpanded = !rangeOptionExpanded },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (rangeOptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle Ranges",
                                        tint = Color(0xFF90A4AE),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Expanded view of selectable Top Hit Ranges
                        AnimatedVisibility(visible = rangeOptionExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                HorizontalDivider(color = Color(0xFF1E3A56), thickness = 0.8.dp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "TOP HIT RANGES (LAST 30M) — ট্যাপ করে রেঞ্জ সিলেক্ট করুন:",
                                    color = Color(0xFF78909C),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )

                                state.activeRanges.forEach { rangeItem ->
                                    val isCurrent = rangeItem.range == state.targetRange
                                    Surface(
                                        color = if (isCurrent) Color(0xFF0091EA).copy(alpha = 0.2f) else Color(0xFF0B141E),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isCurrent) Color(0xFF00B0FF) else Color(0xFF1B3248)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                VirtualNumberManager.setTargetRange(rangeItem.range)
                                                ClipboardHelper.copyToClipboard(context, rangeItem.range, "Range")
                                                Toast.makeText(context, "সিলেক্ট হয়েছে: ${rangeItem.range}", Toast.LENGTH_SHORT).show()
                                                rangeOptionExpanded = false
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isCurrent) Color(0xFF00E676) else Color(0xFF00B0FF))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = rangeItem.range,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${rangeItem.service} • ${rangeItem.tag}",
                                                    color = Color(0xFF78909C),
                                                    fontSize = 10.sp
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${rangeItem.hits} Hits",
                                                    color = Color(0xFF40C4FF),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Select",
                                                    tint = Color(0xFF00B0FF),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Search & Auto-Sync Bar (SS Style)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = consoleFilter,
                        onValueChange = { consoleFilter = it },
                        placeholder = { Text("Filter by number or code...", color = Color(0xFF546E7A), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF546E7A), modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00B0FF),
                            unfocusedBorderColor = Color(0xFF1E3A56),
                            focusedContainerColor = Color(0xFF0B141E),
                            unfocusedContainerColor = Color(0xFF0B141E)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    LiveConsoleSyncBadge()
                }
            }

            // 3. Global Live OTP SMS List (Styled exactly as in the screenshots)
            val filteredBroadcast = if (consoleFilter.isBlank()) {
                state.broadcastFeed
            } else {
                state.broadcastFeed.filter {
                    it.number.contains(consoleFilter, ignoreCase = true) ||
                            it.otp.contains(consoleFilter, ignoreCase = true) ||
                            it.service.contains(consoleFilter, ignoreCase = true) ||
                            it.range.contains(consoleFilter, ignoreCase = true) ||
                            it.country.contains(consoleFilter, ignoreCase = true)
                }
            }

            if (filteredBroadcast.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C27)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "লাইভ এসএমএস লোডিং...",
                            color = Color(0xFF546E7A),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(filteredBroadcast, key = { it.id }) { feed ->
                    BroadcastTerminalCard(feed = feed, context = context)
                }
            }
        }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0B141E),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A334B)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color(0xFF78909C),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

fun formatTimeElapsed(timestamp: Long, now: Long): String {
    val diffMs = (now - timestamp).coerceAtLeast(0)
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 45 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        else -> if (days == 1L) "1 day ago" else "$days days ago"
    }
}

fun formatTimeRemaining(expiresAt: Long, now: Long): String {
    val remainingMs = (expiresAt - now).coerceAtLeast(0)
    val totalSeconds = remainingMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
private fun ProvisionedNumberCard(
    item: ProvisionedNumber,
    now: Long,
    context: Context
) {
    val cardContainerColor = when (item.status) {
        "success" -> Color(0xFF0A2E1F)
        else -> Color(0xFF111D2A)
    }

    val cardBorderColor = when (item.status) {
        "success" -> Color(0xFF00E676).copy(alpha = 0.6f)
        "failed" -> Color(0xFFFF5252).copy(alpha = 0.4f)
        else -> Color(0xFF1E3A56)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Left (Number + Country badge) | Right (Status Pill + Time Ago + Mobile)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Number & Country Badge & Status subtext
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.number,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                ClipboardHelper.copyToClipboard(context, item.number, "Phone Number")
                            }
                        )
                        val countryInfo = remember(item.number, item.range, item.country, item.iso) {
                            com.example.util.AllCountriesProvider.detectCountry(item.number)
                                ?: com.example.util.AllCountriesProvider.detectCountry(item.range ?: "")
                                ?: com.example.util.AllCountriesProvider.findCountry(item.iso)
                                ?: com.example.util.AllCountriesProvider.detectCountry(item.country)
                        }
                        val flag = countryInfo?.flag ?: "🌐"
                        val countryName = countryInfo?.name ?: item.country
                        val isoShort = (countryInfo?.code ?: item.iso).uppercase()

                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF1E2D3B),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF26415E))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = flag, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$countryName [$isoShort]",
                                    color = Color(0xFF81D4FA),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                ClipboardHelper.copyToClipboard(context, item.number, "Phone Number")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Number",
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Facebook Fresh / Taken Badge
                    val fbTag = item.fbTagType
                    val isChecking = item.fbStatus == "Checking..."
                    val (chipBg, chipFg, chipBorder, chipText) = when (fbTag) {
                        PhoneItem.TagType.FRESH -> listOf(
                            Color(0xFF0F3B20),
                            Color(0xFF00E676),
                            Color(0xFF00E676).copy(alpha = 0.6f),
                            "🟢 FRESH (FREE)"
                        )
                        PhoneItem.TagType.TAKEN -> listOf(
                            Color(0xFF381515),
                            Color(0xFFFF5252),
                            Color(0xFFFF5252).copy(alpha = 0.6f),
                            "🔴 TAKEN (USED)"
                        )
                        PhoneItem.TagType.UNKNOWN -> listOf(
                            Color(0xFF262615),
                            Color(0xFFFFD54F),
                            Color(0xFFFFD54F).copy(alpha = 0.5f),
                            "❓ UNKNOWN"
                        )
                        PhoneItem.TagType.ERROR -> listOf(
                            Color(0xFF2E1C0A),
                            Color(0xFFFF9800),
                            Color(0xFFFF9800).copy(alpha = 0.5f),
                            "⚠️ CHECK ERROR"
                        )
                        else -> if (isChecking) {
                            listOf(
                                Color(0xFF1E2835),
                                Color(0xFF81D4FA),
                                Color(0xFF26415E),
                                "CHECKING FB..."
                            )
                        } else {
                            listOf(
                                Color(0xFF13202E),
                                Color(0xFF90CAF9),
                                Color(0xFF1E3A56),
                                "🔍 CHECK FB FRESH"
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Surface(
                            color = chipBg as Color,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, chipBorder as Color),
                            modifier = Modifier.clickable {
                                if (!isChecking) {
                                    val cleanNum = item.number.replace("[^0-9]".toRegex(), "")
                                    val cached = VirtualNumberChecker.cachedResults[cleanNum]
                                    if (cached?.description?.isNotBlank() == true) {
                                        Toast.makeText(context, "${item.number}: ${cached.description}", Toast.LENGTH_SHORT).show()
                                    }
                                    VirtualNumberManager.checkFbStatusForNumber(item.id, item.number)
                                    com.example.service.UnixSmsManager.checkFbStatusForNumber(item.id, item.number)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        color = chipFg as Color,
                                        modifier = Modifier.size(9.dp),
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = chipText as String,
                                    color = chipFg as Color,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Left Side Description by Status
                    when (item.status) {
                        "pending" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFD600))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Waiting...",
                                    color = Color(0xFFFFD600),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        "failed" -> {
                            Text(
                                text = item.failReason ?: "Timeout",
                                color = Color(0xFFFF5252),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        "success" -> {
                            Text(
                                text = item.otpMessage ?: "Facebook: Your code is ${item.otpCode}",
                                color = Color(0xFFB0BEC5),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right: Status Pill + Elapsed Time + Mobile
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val (statusBg, statusFg, statusText) = when (item.status) {
                        "success" -> Triple(Color(0xFF0F3320), Color(0xFF00E676), "SUCCESS")
                        "failed" -> Triple(Color(0xFF381515), Color(0xFFFF5252), "FAILED")
                        else -> Triple(Color(0xFF332A00), Color(0xFFFFD600), "PENDING")
                    }

                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, statusFg.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusText,
                            color = statusFg,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    // Time elapsed: "kotokhon aga neowa hoyse"
                    Text(
                        text = formatTimeElapsed(item.timestamp, now),
                        color = Color(0xFF90A4AE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Mobile badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = Color(0xFF607D8B),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = item.operator.uppercase(),
                            color = Color(0xFF78909C),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // PENDING STATUS: TIME REMAINING COUNTDOWN CARD (Live expiresAt - now)
            if (item.status == "pending") {
                Spacer(modifier = Modifier.height(8.dp))
                PendingCardCountdown(expiresAt = item.expiresAt)
            }

            // SUCCESS STATUS: OTP RESULT BOX
            if (item.status == "success" && !item.otpCode.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            ClipboardHelper.copyToClipboard(context, item.otpCode, "OTP Code")
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF00E676),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = item.otpCode,
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OTP Code",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy OTP",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastTerminalCard(
    feed: BroadcastFeedItem,
    context: Context
) {
    val timeStr = remember(feed.time) {
        try {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).apply {
                timeZone = TimeZone.getDefault()
            }
            val ms = if (feed.time in 1..9_999_999_999L) feed.time * 1000L else feed.time
            sdf.format(Date(if (ms > 0L) ms else System.currentTimeMillis()))
        } catch (_: Exception) {
            "05:16:58 PM"
        }
    }

    Surface(
        color = Color(0xFF0B141E),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF182C40)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val code = com.example.util.OtpExtractionHelper.extractOtp(feed.otp)
                if (code != null) {
                    ClipboardHelper.copyToClipboard(context, code, "OTP Code")
                    Toast.makeText(context, "✓ OTP কপি হয়েছে: $code", Toast.LENGTH_SHORT).show()
                } else {
                    ClipboardHelper.copyToClipboard(context, feed.otp, "SMS Body")
                    Toast.makeText(context, "এসএমএস কপি হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            // Line 1: Timestamp | Operator | Pipe | Country with Globe | Service Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        color = Color(0xFFFFB300),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = feed.operator,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "|",
                        color = Color(0xFF475569),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "🌍 ${feed.country.uppercase()}",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Service Badge (FACEBOOK / INSTAGRAM / WHATSAPP)
                val upperService = feed.service.uppercase()
                val (badgeBg, badgeFg) = when {
                    upperService.contains("FACEBOOK") || upperService.contains("FB") -> Pair(Color(0xFF132B4A), Color(0xFF60A5FA))
                    upperService.contains("INSTAGRAM") || upperService.contains("IG") -> Pair(Color(0xFF421028), Color(0xFFF472B6))
                    upperService.contains("WHATSAPP") || upperService.contains("WA") -> Pair(Color(0xFF0A3322), Color(0xFF34D399))
                    else -> Pair(Color(0xFF1A2634), Color(0xFF90A4AE))
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = upperService,
                        color = badgeFg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 2: Phone Number, Range & OTP Extraction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = feed.number,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            ClipboardHelper.copyToClipboard(context, feed.number, "Phone")
                            Toast.makeText(context, "নম্বর কপি হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = Color(0xFF112438),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(0.7.dp, Color(0xFF204266)),
                        modifier = Modifier.clickable {
                            VirtualNumberManager.setTargetRange(feed.range)
                            ClipboardHelper.copyToClipboard(context, feed.range, "Range")
                            Toast.makeText(context, "রেঞ্জ কপি ও সেট হয়েছে: ${feed.range}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = feed.range,
                                color = Color(0xFF60A5FA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Range",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                // Extracted OTP Highlight Pill
                val code = com.example.util.OtpExtractionHelper.extractOtp(feed.otp)
                if (code != null) {
                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E676)),
                        modifier = Modifier.clickable {
                            ClipboardHelper.copyToClipboard(context, code, "OTP Code")
                            Toast.makeText(context, "✓ OTP কপি হয়েছে: $code", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "OTP: $code",
                                color = Color(0xFF00E676),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 3: FULL SMS Message (not truncated, complete readable message body)
            Surface(
                color = Color(0xFF070E16),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.6.dp, Color(0xFF1E2F42)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = feed.otp,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveConsoleSyncBadge() {
    var syncCountdown by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            if (syncCountdown <= 1) {
                syncCountdown = 3
                VirtualNumberManager.fetchGlobalBroadcast()
                VirtualNumberManager.fetchActiveRanges()
            } else {
                syncCountdown -= 1
            }
        }
    }

    Surface(
        color = Color(0xFF0D1B2A),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A3959)),
        modifier = Modifier
            .height(52.dp)
            .clickable {
                syncCountdown = 3
                VirtualNumberManager.fetchGlobalBroadcast()
                VirtualNumberManager.fetchActiveRanges()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Sync",
                tint = Color(0xFF81D4FA),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Auto Sync: ${syncCountdown}s",
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PendingCardCountdown(expiresAt: Long) {
    var localNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (expiresAt > System.currentTimeMillis()) {
            delay(1000L)
            localNow = System.currentTimeMillis()
        }
    }
    val remainingMs = (expiresAt - localNow).coerceAtLeast(0)
    val remainingStr = formatTimeRemaining(expiresAt, localNow)
    val progressRatio = (remainingMs.toFloat() / 1_200_000f).coerceIn(0f, 1f)

    Surface(
        color = Color(0xFF131D24),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFD600).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFFFD600),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Time Remaining:",
                        color = Color(0xFFFFE082),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = remainingStr,
                    color = if (remainingMs > 60_000L) Color(0xFFFFD600) else Color(0xFFFF5252),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progressRatio },
                color = if (remainingMs > 60_000L) Color(0xFFFFD600) else Color(0xFFFF5252),
                trackColor = Color(0xFF1C2731),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun VirtualNumberFreshCheckerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var rawInput by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0 to 0) }
    var results by remember { mutableStateOf<List<PhoneItem>>(emptyList()) }

    Dialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A26)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF00B0FF).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("FB", color = Color(0xFF00E676), fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Facebook Fresh Checker",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "১-১০টি নম্বর চেক করুন (Fresh vs Taken)",
                                color = Color(0xFF81D4FA),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isChecking,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF90A4AE))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input box
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("নম্বর লিখুন বা পেস্ট করুন (১-১০টি)", fontSize = 11.sp) },
                    placeholder = { Text("e.g. 237627112233\n+237627445566, 447123456789", fontSize = 11.sp, color = Color(0xFF546E7A)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFECEFF1),
                        focusedContainerColor = Color(0xFF0A121C),
                        unfocusedContainerColor = Color(0xFF0A121C),
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color(0xFF1E3A56)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Helper Buttons (Paste & Load Current)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF142434),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF1E3A56)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val clip = ClipboardHelper.getFromClipboard(context).orEmpty()
                                if (clip.isNotBlank()) {
                                    rawInput = if (rawInput.isBlank()) clip else "$rawInput\n$clip"
                                    Toast.makeText(context, "ক্লিপবোর্ড থেকে পেস্ট হয়েছে", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ক্লিপবোর্ডে কোনো টেক্সট নেই", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF81D4FA), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পেস্ট করুন", color = Color(0xFF81D4FA), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = Color(0xFF142434),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF1E3A56)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val currentNums = VirtualNumberManager.state.value.provisionedNumbers.map { it.number }
                                if (currentNums.isNotEmpty()) {
                                    rawInput = currentNums.take(10).joinToString("\n")
                                    Toast.makeText(context, "${minOf(10, currentNums.size)} টি বর্তমান নম্বর লোড হয়েছে", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "বর্তমানে কোনো নম্বর নেই", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("বর্তমান নম্বর লোড", color = Color(0xFF00E676), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Check Button
                Button(
                    onClick = {
                        val parsed = VirtualNumberChecker.parseNumbers(rawInput)
                        if (parsed.isEmpty()) {
                            Toast.makeText(context, "অনুগ্রহ করে কমপক্ষে একটি বৈধ ফোন নম্বর লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isChecking = true
                        results = emptyList()
                        coroutineScope.launch {
                            val tempResults = mutableListOf<PhoneItem>()
                            VirtualNumberChecker.processVirtualNumbers(
                                rawNumbersInput = rawInput,
                                onProgress = { cur, tot, item ->
                                    currentProgress = cur to tot
                                    tempResults.removeAll { it.number == item.number }
                                    tempResults.add(item)
                                    results = tempResults.toList()
                                },
                                onFinished = { finalItems ->
                                    results = finalItems
                                    isChecking = false
                                    VibrationHelper.vibrateSuccess(context)
                                    val freshCount = finalItems.count { it.tagType == PhoneItem.TagType.FRESH }
                                    Toast.makeText(context, "চেক সম্পন্ন! Fresh: $freshCount / ${finalItems.size}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        disabledContainerColor = Color(0xFF00E676).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "চেক হচ্ছে (${currentProgress.first}/${currentProgress.second})...",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CHECK FRESH / TAKEN NOW",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Results section
                if (results.isNotEmpty() || isChecking) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "ফলাফল (${results.size} টি নম্বর)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        results.forEach { item ->
                            val (badgeBg, badgeFg, badgeBorder, badgeText) = when (item.tagType) {
                                PhoneItem.TagType.FRESH -> listOf(
                                    Color(0xFF0A2E1F),
                                    Color(0xFF00E676),
                                    Color(0xFF00E676).copy(alpha = 0.5f),
                                    "🟢 Fresh (Free)"
                                )
                                PhoneItem.TagType.TAKEN -> listOf(
                                    Color(0xFF381515),
                                    Color(0xFFFF5252),
                                    Color(0xFFFF5252).copy(alpha = 0.5f),
                                    "🔴 Taken (Used)"
                                )
                                PhoneItem.TagType.UNKNOWN -> listOf(
                                    Color(0xFF262615),
                                    Color(0xFFFFD54F),
                                    Color(0xFFFFD54F).copy(alpha = 0.5f),
                                    "❓ Unknown"
                                )
                                PhoneItem.TagType.ERROR -> listOf(
                                    Color(0xFF2E1C0A),
                                    Color(0xFFFF9800),
                                    Color(0xFFFF9800).copy(alpha = 0.5f),
                                    "⚠️ Error"
                                )
                                else -> listOf(
                                    Color(0xFF1E2835),
                                    Color(0xFFFFD600),
                                    Color(0xFF26415E),
                                    "Checking..."
                                )
                            }

                            Surface(
                                color = Color(0xFF111D2A),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder as Color),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ClipboardHelper.copyToClipboard(context, item.number, "Phone Number")
                                        val detail = if (item.description.isNotBlank()) " • ${item.description}" else ""
                                        Toast.makeText(context, "${item.number} কপি হয়েছে$detail", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.number,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            tint = Color(0xFF81D4FA),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Surface(
                                        color = badgeBg as Color,
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.6.dp, badgeFg as Color)
                                    ) {
                                        Text(
                                            text = badgeText as String,
                                            color = badgeFg as Color,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.5.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Copy all Fresh numbers button
                    val freshNumbers = results.filter { it.tagType == PhoneItem.TagType.FRESH }
                    if (freshNumbers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val text = freshNumbers.joinToString("\n") { it.number.replace("+", "").trim() }
                                ClipboardHelper.copyToClipboard(context, text, "Fresh Numbers")
                                Toast.makeText(context, "${freshNumbers.size} টি Fresh নম্বর কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "কপি করুন সব FRESH নম্বর (${freshNumbers.size}টি)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}



