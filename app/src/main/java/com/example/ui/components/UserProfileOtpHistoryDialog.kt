package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.AuthManager
import com.example.service.DailyOtpSummary
import com.example.service.OtpHistoryManager
import com.example.service.OtpHistoryRecord
import com.example.service.WithdrawalManager
import com.example.service.WithdrawalRequest
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserProfileOtpHistoryDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentEmail by AuthManager.currentEmail.collectAsState()
    val otpState by OtpHistoryManager.state.collectAsState()
    val withdrawState by WithdrawalManager.state.collectAsState()
    val limits by WithdrawalManager.limits.collectAsState()

    var dailyCountdownString by remember { mutableStateOf(OtpHistoryManager.getFormattedDailyResetCountdown()) }
    var monthlyCountdownString by remember { mutableStateOf(OtpHistoryManager.getFormatted1MonthRemainingCountdown()) }

    // Section Filter Tab: "ALL", "OTP", "WITHDRAW", "ACCOUNT"
    var activeSectionTab by remember { mutableStateOf("ALL") }

    // User Payout Profile Accounts (Binance, bKash, Nagad, Rocket)
    var binanceProfileInput by remember { mutableStateOf("") }
    var bkashProfileInput by remember { mutableStateOf("") }
    var nagadProfileInput by remember { mutableStateOf("") }
    var rocketProfileInput by remember { mutableStateOf("") }

    // Withdrawal Form State
    var selectedMethod by remember { mutableStateOf("Binance") } // "Binance", "bKash", "Nagad", "Rocket"
    var accountInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var isSubmittingWithdraw by remember { mutableStateOf(false) }

    // 4-Digit Security PIN States
    var showPinDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinSetupInput by remember { mutableStateOf("") }
    var pinSetupConfirmInput by remember { mutableStateOf("") }
    var pendingWithdrawAmount by remember { mutableStateOf(0.0) }
    var pendingWithdrawAccount by remember { mutableStateOf("") }
    var pendingWithdrawMethod by remember { mutableStateOf("") }

    LaunchedEffect(currentEmail) {
        val cleanEmail = currentEmail.trim().lowercase()
        if (cleanEmail.isNotEmpty()) {
            OtpHistoryManager.onUserLoggedIn(cleanEmail)
            WithdrawalManager.onUserLoggedIn(cleanEmail)
        }
        val accs = WithdrawalManager.getSavedAccounts(currentEmail)
        binanceProfileInput = accs.binanceId
        bkashProfileInput = accs.bkashNumber
        nagadProfileInput = accs.nagadNumber
        rocketProfileInput = accs.rocketNumber
        if (accountInput.isBlank()) {
            accountInput = when (selectedMethod.lowercase()) {
                "binance" -> accs.binanceId
                "bkash" -> accs.bkashNumber
                "nagad" -> accs.nagadNumber
                "rocket" -> accs.rocketNumber
                else -> ""
            }
        }
    }

    LaunchedEffect(Unit) {
        OtpHistoryManager.init(context)
        WithdrawalManager.init(context)
        val cleanEmail = currentEmail.trim().lowercase()
        if (cleanEmail.isNotEmpty()) {
            OtpHistoryManager.onUserLoggedIn(cleanEmail)
            WithdrawalManager.onUserLoggedIn(cleanEmail)
        }
        AuthManager.syncCurrentUserProfileFromCloud(context)
        WithdrawalManager.triggerLiveCloudSync()
        OtpHistoryManager.triggerLiveCloudSync()
        var tick = 0
        while (true) {
            delay(1000L)
            dailyCountdownString = OtpHistoryManager.getFormattedDailyResetCountdown()
            monthlyCountdownString = OtpHistoryManager.getFormatted1MonthRemainingCountdown()
            tick++
            if (tick % 3 == 0) {
                AuthManager.syncCurrentUserProfileFromCloud(context)
                OtpHistoryManager.triggerLiveCloudSync()
                WithdrawalManager.triggerLiveCloudSync()
            }
        }
    }

    val minAmount = WithdrawalManager.getMinimumAmountForMethod(selectedMethod)
    val isWithdrawalAllowed = AuthManager.isSectionAllowedForCurrentUser("WITHDRAWAL")
    val isMethodUnlocked = isWithdrawalAllowed && withdrawState.availableBalanceTk >= minAmount

    val userWithdrawals = remember(withdrawState.allRequests, currentEmail) {
        WithdrawalManager.getUserRequests(currentEmail)
    }

    // Strict Per-User OTP Filter: Only OTPs received for numbers taken by this logged-in user's email
    val filteredRecentRecords = remember(otpState.recentRecords, currentEmail) {
        val clean = currentEmail.trim().lowercase()
        if (clean.isEmpty()) {
            emptyList()
        } else {
            otpState.recentRecords.filter {
                it.userEmail.trim().equals(clean, ignoreCase = true) &&
                !it.phoneNumber.startsWith("+120255501") &&
                !it.phoneNumber.startsWith("+2376991122") &&
                it.otpCode.isNotBlank() && it.otpCode != "null"
            }.take(50)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.93f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF070D17),
                border = BorderStroke(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF00E676).copy(alpha = 0.55f),
                            Color(0xFF38BDF8).copy(alpha = 0.4f),
                            Color(0xFF1E293B)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Top App Bar Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF00E676), Color(0xFF0284C7))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Profile",
                                    tint = Color(0xFF04131D),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "আমার প্রোফাইল ও ওয়ালেট",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = when {
                                            AuthManager.isPrimeAdmin() -> Color(0xFFFFB300).copy(alpha = 0.2f)
                                            AuthManager.isSubAdmin() -> Color(0xFF818CF8).copy(alpha = 0.2f)
                                            else -> Color(0xFF00E676).copy(alpha = 0.2f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                AuthManager.isPrimeAdmin() -> "👑 ADMIN"
                                                AuthManager.isSubAdmin() -> "🛡️ SUB ADMIN"
                                                else -> "👤 USER"
                                            },
                                            color = when {
                                                AuthManager.isPrimeAdmin() -> Color(0xFFFFB300)
                                                AuthManager.isSubAdmin() -> Color(0xFF818CF8)
                                                else -> Color(0xFF00E676)
                                            },
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = currentEmail.ifEmpty { "User Account" },
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live Sync Button
                            Surface(
                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                                modifier = Modifier.clickable {
                                    scope.launch(Dispatchers.IO) {
                                        AuthManager.syncCurrentUserProfileFromCloud(context)
                                        OtpHistoryManager.syncFromCloud(context)
                                        WithdrawalManager.syncFromCloud(context)
                                    }
                                    Toast.makeText(context, "🔄 আপনার প্রোফাইল ও ব্যালেন্স সিঙ্ক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Sync",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF162536))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF1B2C3F))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Navigation Option Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "📊 সব অপশন",
                            "OTP" to "📩 আমার ওটিপি (${filteredRecentRecords.size})",
                            "WITHDRAW" to "💸 উইথড্র",
                            "ACCOUNT" to "💳 পেমেন্ট ও পিন"
                        ).forEach { (tabKey, tabLabel) ->
                            val isSelected = activeSectionTab == tabKey
                            Surface(
                                color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF0F1926),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E676) else Color(0xFF1E3248)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeSectionTab = tabKey }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tabLabel,
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Account Identity & Isolation Guarantee Card
                        if (activeSectionTab == "ALL" || activeSectionTab == "ACCOUNT") {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1826)),
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(5.dp),
                                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                                                    ) {
                                                        Text(
                                                            text = "🟢 সক্রিয় একাউন্ট (ACTIVE)",
                                                            color = Color(0xFF34D399),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(5.dp)
                                                    ) {
                                                        Text(
                                                            text = "🔒 ১০০% ব্যক্তিগত ওটিপি ডাটা",
                                                            color = Color(0xFF38BDF8),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(5.dp))
                                                Text(
                                                    text = "ইউজার মেইল: ${currentEmail.ifEmpty { "Unknown" }}",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "শুধুমাত্র আপনার মেইলে নেওয়া নাম্বারের ওটিপি এবং ব্যালেন্স এখানে কাউন্ট হবে।",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 9.5.sp
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    AuthManager.logout()
                                                    onDismiss()
                                                    Toast.makeText(context, "লগআউট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                                            ) {
                                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Logout", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Wallet & Balance Overview Card (Tk and OTP Balance)
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1727)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.5.dp,
                                    Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))
                                ),
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
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "TOTAL OTP BALANCE (মোট ব্যালেন্স)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }

                                        Surface(
                                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "WITHDRAWABLE",
                                                color = Color(0xFF00E676),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "৳ ${String.format(Locale.US, "%.2f", withdrawState.availableBalanceTk)}",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF00E676),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "মোট আয়: ৳${String.format(Locale.US, "%.2f", withdrawState.totalEarnedTk)} • পেন্ডিং: ৳${String.format(Locale.US, "%.2f", withdrawState.pendingWithdrawTk)} • উত্তোলিত: ৳${String.format(Locale.US, "%.2f", withdrawState.approvedWithdrawTk)}",
                                                fontSize = 9.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }

                                        if (activeSectionTab != "WITHDRAW") {
                                            Surface(
                                                color = Color(0xFF00E676),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.clickable { activeSectionTab = "WITHDRAW" }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "💸 টাকা তুলুন",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF04131D)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Primary Metrics (Today, 7-Day, 30-Day, Total, Reset Countdowns)
                        if (activeSectionTab == "ALL" || activeSectionTab == "OTP") {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ProfileMetricPill(
                                            title = "TODAY",
                                            value = otpState.todayOtps.toString(),
                                            subtitle = "আজকের OTP",
                                            color = Color(0xFF00E676),
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProfileMetricPill(
                                            title = "7 DAYS",
                                            value = otpState.last7DaysOtps.toString(),
                                            subtitle = "গত ৭ দিন",
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProfileMetricPill(
                                            title = "30 DAYS",
                                            value = otpState.last30DaysOtps.toString(),
                                            subtitle = "চলতি মাস",
                                            color = Color(0xFFFBBF24),
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProfileMetricPill(
                                            title = "TOTAL",
                                            value = otpState.totalOtpsAllTime.toString(),
                                            subtitle = "সর্বমোট OTP",
                                            color = Color(0xFFA855F7),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ProfileMetricPill(
                                            title = "1-MONTH RESET LEFT",
                                            value = monthlyCountdownString,
                                            subtitle = "30 Days History Reset",
                                            color = Color(0xFFA855F7),
                                            isSmallText = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProfileMetricPill(
                                            title = "TODAY RESET IN",
                                            value = dailyCountdownString,
                                            subtitle = "Midnight 24:00",
                                            color = Color(0xFFFFD600),
                                            isSmallText = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. RECENT RECEIVED OTPS (Moved higher so user immediately sees their own received OTPs!)
                        if (activeSectionTab == "ALL" || activeSectionTab == "OTP") {
                            item {
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "আমার রিসিভকৃত ওটিপি (${filteredRecentRecords.size})",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "🔒 শুধুমাত্র আপনার মেইলে ($currentEmail) নেওয়া নাম্বারের ওটিপি",
                                                    fontSize = 8.5.sp,
                                                    color = Color(0xFF38BDF8),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                                                    modifier = Modifier.clickable { OtpHistoryManager.exportOtpHistory(context) }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = "TXT", tint = Color(0xFFFBBF24), modifier = Modifier.size(11.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(text = "TXT", color = Color(0xFFFBBF24), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Surface(
                                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
                                                    modifier = Modifier.clickable { OtpHistoryManager.exportToCsv(context) }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Download, contentDescription = "CSV", tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(text = "CSV", color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Surface(
                                                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
                                                    modifier = Modifier.clickable { OtpHistoryManager.copyRecentOtpsToClipboard(context) }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy All", tint = Color(0xFF00E676), modifier = Modifier.size(11.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(text = "Copy", color = Color(0xFF00E676), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (filteredRecentRecords.isEmpty()) {
                                            Surface(
                                                color = Color(0xFF09121D),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFF19293B)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 18.dp, horizontal = 12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "আপনার নেওয়া কোনো নাম্বারে এখনও ওটিপি আসেনি",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "প্যানেল থেকে নাম্বার নিলে এবং সেই নাম্বারে ওটিপি আসলে সাথে সাথে এখানে যুক্ত হবে।",
                                                        color = Color(0xFF64748B),
                                                        fontSize = 9.5.sp
                                                    )
                                                }
                                            }
                                        } else {
                                            val displayLimit = if (activeSectionTab == "OTP") 40 else 15
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                filteredRecentRecords.take(displayLimit).forEach { rec ->
                                                    RecentOtpLogItem(rec = rec, context = context)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. WITHDRAW OPTION CARD (Binance, bKash, Nagad, Rocket)
                        if (activeSectionTab == "ALL" || activeSectionTab == "WITHDRAW") {
                            item {
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
                                                    imageVector = if (isMethodUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = if (isMethodUnlocked) Color(0xFF00E676) else Color(0xFFEF4444),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "WITHDRAW OPTION (টাকা তুলুন)",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }

                                            Surface(
                                                color = if (isMethodUnlocked) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isMethodUnlocked) "UNLOCKED (উন্মুক্ত)" else "LOCKED (লক)",
                                                    color = if (isMethodUnlocked) Color(0xFF00E676) else Color(0xFFF87171),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (!isWithdrawalAllowed) {
                                            Surface(
                                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = null,
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "এডমিন আপনার একাউন্টের জন্য উইথড্র অপশন অফ করে রেখেছেন।",
                                                        color = Color(0xFFFCA5A5),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "পেমেন্ট মাধ্যম বেছে নিন: Binance (মিনিমাম ৳${String.format(Locale.US, "%.0f", limits.minBinance)}), bKash (মিনিমাম ৳${String.format(Locale.US, "%.0f", limits.minBkash)}), Nagad (মিনিমাম ৳${String.format(Locale.US, "%.0f", limits.minNagad)}), Rocket (মিনিমাম ৳${String.format(Locale.US, "%.0f", limits.minRocket)})",
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Payment Method Buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                Triple("Binance", "Binance Pay", "Min: ৳${String.format(Locale.US, "%.0f", limits.minBinance)}"),
                                                Triple("bKash", "বিকাশ", "Min: ৳${String.format(Locale.US, "%.0f", limits.minBkash)}"),
                                                Triple("Nagad", "নগদ", "Min: ৳${String.format(Locale.US, "%.0f", limits.minNagad)}"),
                                                Triple("Rocket", "রকেট", "Min: ৳${String.format(Locale.US, "%.0f", limits.minRocket)}")
                                            ).forEach { (mKey, mTitle, minLabel) ->
                                                val isSel = selectedMethod == mKey
                                                val methodColor = when (mKey) {
                                                    "Binance" -> Color(0xFFF0B90B)
                                                    "bKash" -> Color(0xFFE2136E)
                                                    "Nagad" -> Color(0xFFF7931A)
                                                    else -> Color(0xFF8C30F5) // Rocket
                                                }

                                                Surface(
                                                    color = if (isSel) methodColor.copy(alpha = 0.25f) else Color(0xFF090E17),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.2.dp, if (isSel) methodColor else Color(0xFF1E2D40)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            selectedMethod = mKey
                                                            val savedAcc = when (mKey) {
                                                                "Binance" -> binanceProfileInput
                                                                "bKash" -> bkashProfileInput
                                                                "Nagad" -> nagadProfileInput
                                                                "Rocket" -> rocketProfileInput
                                                                else -> ""
                                                            }
                                                            if (savedAcc.isNotBlank()) {
                                                                accountInput = savedAcc
                                                            }
                                                        }
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = mTitle,
                                                            color = if (isSel) Color.White else Color(0xFF90A4AE),
                                                            fontSize = 10.5.sp,
                                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = minLabel,
                                                            color = if (isSel) methodColor else Color(0xFF64748B),
                                                            fontSize = 7.5.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Account / Number Field
                                        OutlinedTextField(
                                            value = accountInput,
                                            onValueChange = { accountInput = it },
                                            label = {
                                                Text(
                                                    text = if (selectedMethod == "Binance") "Binance Pay ID / USDT BEP-20" else "$selectedMethod নাম্বার (01XXXXXXXXX)"
                                                )
                                            },
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

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Amount Field
                                        OutlinedTextField(
                                            value = amountInput,
                                            onValueChange = { amountInput = it },
                                            label = { Text("উইথড্র পরিমাণ টাকা (Amount in BDT)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Quick Amount Chips
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf("20", "50", "100", "All").forEach { chip ->
                                                Surface(
                                                    color = Color(0xFF1E293B),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            if (chip == "All") {
                                                                amountInput = String.format(Locale.US, "%.0f", withdrawState.availableBalanceTk)
                                                            } else {
                                                                amountInput = chip
                                                            }
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                                                        Text(
                                                            text = if (chip == "All") "সব টাকা" else "৳$chip",
                                                            color = Color(0xFFE2E8F0),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Submit / Withdraw Button
                                        Button(
                                            onClick = {
                                                if (!isWithdrawalAllowed) {
                                                    Toast.makeText(context, "এডমিন আপনার একাউন্টের জন্য উইথড্র অফ করে রেখেছেন", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                val amt = amountInput.toDoubleOrNull() ?: 0.0
                                                if (accountInput.isBlank()) {
                                                    Toast.makeText(context, "একাউন্ট নাম্বার বা Binance ID দিন", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                if (amt <= 0.0) {
                                                    Toast.makeText(context, "সঠিক টাকার পরিমাণ দিন", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }

                                                pendingWithdrawAmount = amt
                                                pendingWithdrawAccount = accountInput.trim()
                                                pendingWithdrawMethod = selectedMethod

                                                if (!WithdrawalManager.hasSecurityPin(currentEmail)) {
                                                    pinSetupInput = ""
                                                    pinSetupConfirmInput = ""
                                                    showPinSetupDialog = true
                                                } else {
                                                    pinInput = ""
                                                    showPinDialog = true
                                                }
                                            },
                                            enabled = isMethodUnlocked && !isSubmittingWithdraw,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF00E676),
                                                disabledContainerColor = Color(0xFF1E293B)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isMethodUnlocked) Icons.Default.ArrowForward else Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = if (isMethodUnlocked) Color.Black else Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isMethodUnlocked) "SUBMIT WITHDRAW REQUEST (উইথড্র করুন)" else "লক করা (মিনিমাম ৳$minAmount প্রয়োজন)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isMethodUnlocked) Color.Black else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }

                            // 5.5 WITHDRAW HISTORY LIST (User's requests)
                            item {
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
                                                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "WITHDRAW HISTORY (উইথড্র হিস্টোরি)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }

                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                                                modifier = Modifier.clickable {
                                                    WithdrawalManager.exportWithdrawalHistory(context, currentEmail)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Download (Last 5)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF38BDF8)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (userWithdrawals.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "এখনও কোনো উইথড্র রিকোয়েস্ট করেননি", color = Color(0xFF64748B), fontSize = 11.sp)
                                            }
                                        } else {
                                            val historyLimit = if (activeSectionTab == "WITHDRAW") 10 else 3
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                userWithdrawals.take(historyLimit).forEach { req ->
                                                    WithdrawalHistoryItemRow(req = req)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. PAYOUT ACCOUNTS PROFILE & SECURITY PIN CARD (Binance, bKash, Nagad, Rocket + PIN)
                        if (activeSectionTab == "ALL" || activeSectionTab == "ACCOUNT") {
                            item {
                                val hasPin = WithdrawalManager.hasSecurityPin(currentEmail)
                                val pinResetPending = WithdrawalManager.isPinResetRequested(currentEmail)
                                var localResetPending by remember(pinResetPending) { mutableStateOf(pinResetPending) }

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
                                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "পেমেন্ট একাউন্ট ও সিকিউরিটি পিন (PAYOUT PROFILE)",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }

                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "SAVED PROFILE",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "আপনার Binance, bKash, Nagad এবং Rocket একাউন্ট নাম্বারগুলো সেভ রাখুন যাতে উইথড্র করার সময় বারবার লিখতে না হয়:",
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8),
                                            lineHeight = 14.sp
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Binance Pay ID / USDT
                                        OutlinedTextField(
                                            value = binanceProfileInput,
                                            onValueChange = { binanceProfileInput = it },
                                            label = { Text("Binance Pay ID / USDT Address", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFF0B90B),
                                                unfocusedBorderColor = Color(0xFF1E2D40),
                                                focusedContainerColor = Color(0xFF090E17),
                                                unfocusedContainerColor = Color(0xFF090E17)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // bKash Number
                                        OutlinedTextField(
                                            value = bkashProfileInput,
                                            onValueChange = { bkashProfileInput = it },
                                            label = { Text("bKash নাম্বার (01XXXXXXXXX)", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFE2136E),
                                                unfocusedBorderColor = Color(0xFF1E2D40),
                                                focusedContainerColor = Color(0xFF090E17),
                                                unfocusedContainerColor = Color(0xFF090E17)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Nagad Number
                                        OutlinedTextField(
                                            value = nagadProfileInput,
                                            onValueChange = { nagadProfileInput = it },
                                            label = { Text("Nagad নাম্বার (01XXXXXXXXX)", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFF7931A),
                                                unfocusedBorderColor = Color(0xFF1E2D40),
                                                focusedContainerColor = Color(0xFF090E17),
                                                unfocusedContainerColor = Color(0xFF090E17)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Rocket Number
                                        OutlinedTextField(
                                            value = rocketProfileInput,
                                            onValueChange = { rocketProfileInput = it },
                                            label = { Text("Rocket নাম্বার (01XXXXXXXXX)", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF8C30F5),
                                                unfocusedBorderColor = Color(0xFF1E2D40),
                                                focusedContainerColor = Color(0xFF090E17),
                                                unfocusedContainerColor = Color(0xFF090E17)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Button(
                                            onClick = {
                                                WithdrawalManager.saveAccounts(
                                                    email = currentEmail,
                                                    binance = binanceProfileInput,
                                                    bkash = bkashProfileInput,
                                                    nagad = nagadProfileInput,
                                                    rocket = rocketProfileInput
                                                )
                                                if (selectedMethod == "Binance" && binanceProfileInput.isNotBlank()) accountInput = binanceProfileInput
                                                if (selectedMethod == "bKash" && bkashProfileInput.isNotBlank()) accountInput = bkashProfileInput
                                                if (selectedMethod == "Nagad" && nagadProfileInput.isNotBlank()) accountInput = nagadProfileInput
                                                if (selectedMethod == "Rocket" && rocketProfileInput.isNotBlank()) accountInput = rocketProfileInput
                                                Toast.makeText(context, "✓ পেমেন্ট একাউন্টসমূহ সফলভাবে প্রোফাইলে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                                                VibrationHelper.vibrateSuccess(context)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                        ) {
                                            Text("💾 প্রোফাইলে পেমেন্ট একাউন্ট সেভ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = Color(0xFF1E2D40))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Security PIN Status & Option
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = if (hasPin) Color(0xFF00E676) else Color(0xFFFBBF24),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = if (hasPin) "৪-ডিজিট সিকিউরিটি পিন সক্রিয় আছে" else "৪-ডিজিট সিকিউরিটি পিন সেট করা নেই",
                                                        color = Color.White,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (hasPin) "উইথড্র করার সময় এই পিন দিয়ে ভেরিফাই করা হয়" else "নিরাপত্তার জন্য ৪ ডিজিট পিন সেট করুন",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }

                                            if (!hasPin) {
                                                Surface(
                                                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                                                    modifier = Modifier.clickable {
                                                        pinSetupInput = ""
                                                        pinSetupConfirmInput = ""
                                                        pendingWithdrawAmount = 0.0
                                                        showPinSetupDialog = true
                                                    }
                                                ) {
                                                    Text(
                                                        text = "🔐 পিন সেট করুন",
                                                        color = Color(0xFF00E676),
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    color = if (localResetPending) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.18f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, if (localResetPending) Color(0xFFF59E0B) else Color(0xFF38BDF8)),
                                                    modifier = Modifier.clickable(enabled = !localResetPending) {
                                                        WithdrawalManager.requestPinReset(currentEmail)
                                                        localResetPending = true
                                                        Toast.makeText(context, "✓ অ্যাডমিনের কাছে পিন রিসেট রিকোয়েস্ট পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Text(
                                                        text = if (localResetPending) "⏳ রিসেট পেন্ডিং" else "🔄 পিন রিসেট রিকোয়েস্ট",
                                                        color = if (localResetPending) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Panel Breakdown Card: Premium, Kop 1, Kop 2, Prem V2
                        if (activeSectionTab == "ALL" || activeSectionTab == "OTP") {
                            item {
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
                                                Icon(Icons.Default.PieChart, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "PANEL BREAKDOWN (প্যানেল ওটিপি হিসাব)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }

                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Unix: ${String.format(Locale.US, "%.2f", otpState.currentUnixRate)}৳ • Kop 1: ${String.format(Locale.US, "%.2f", otpState.currentZenexRate)}৳ • Kop 2: ${String.format(Locale.US, "%.2f", otpState.currentKop2Rate)}৳ • V2: ${String.format(Locale.US, "%.2f", otpState.currentV2Rate)}৳",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // 2x2 Grid of Platforms
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Row 1: Premium & Kop 1
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(
                                                            text = "1. Premium",
                                                            color = Color(0xFF38BDF8),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Rate: ${String.format(Locale.US, "%.2f", otpState.currentUnixRate)}৳",
                                                            color = Color(0xFF67E8F9),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Today: ${otpState.unixTodayOtps}",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                            text = "Total: ${otpState.unixTotalAllTime}",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(
                                                            text = "2. Kop 1",
                                                            color = Color(0xFF34D399),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Rate: ${String.format(Locale.US, "%.2f", otpState.currentZenexRate)}৳",
                                                            color = Color(0xFFA7F3D0),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Today: ${otpState.zenexTodayOtps}",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                            text = "Total: ${otpState.zenexTotalAllTime}",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }

                                            // Row 2: Kop 2 & Premium V2
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(
                                                            text = "3. Kop 2",
                                                            color = Color(0xFFA78BFA),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Rate: ${String.format(Locale.US, "%.2f", otpState.currentKop2Rate)}৳",
                                                            color = Color(0xFFDDD6FE),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Today: ${otpState.kop2TodayOtps}",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                            text = "Total: ${otpState.kop2TotalAllTime}",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(
                                                            text = "4. Prem V2",
                                                            color = Color(0xFFFBBF24),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Rate: ${String.format(Locale.US, "%.2f", otpState.currentV2Rate)}৳",
                                                            color = Color(0xFFFDE68A),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Today: ${otpState.v2TodayOtps}",
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                            text = "Total: ${otpState.v2TotalAllTime}",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 9.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 8. PAST 2 MONTHS HISTORY CARD (গত বা আগের ২ মাসের ওটিপি হিস্টোরি)
                            item {
                                var showPastMonthsDetails by remember { mutableStateOf(false) }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1927)),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E3A5A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = "PAST 2 MONTHS OTP HISTORY",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "আগের ২ মাসের ওটিপি হিস্টোরি ও রেকর্ড",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                                                modifier = Modifier.clickable { showPastMonthsDetails = !showPastMonthsDetails }
                                            ) {
                                                Text(
                                                    text = if (showPastMonthsDetails) "সংকুচিত করুন ▲" else "হিস্ট্রি দেখুন ▼",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "১ মাস আগে (Last Month)",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                    Text(
                                                        text = otpState.lastMonthLabel,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF38BDF8),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "${otpState.lastMonthOtps} OTP",
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "২ মাস আগে (2 Mo Ago)",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                    Text(
                                                        text = otpState.twoMonthsAgoLabel,
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF818CF8),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "${otpState.twoMonthsAgoOtps} OTP",
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        if (showPastMonthsDetails) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Surface(
                                                color = Color(0xFF070E18),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "📊 মাসিক সাইকেল ও অটো-রিসেট বিবরণ:",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF38BDF8)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "• প্রতি ৩০ দিন পর পর মাসিক ওটিপি সাইকেল স্বয়ংক্রিয়ভাবে আপডেট হয়।\n• বর্তমান চলতি মাস: ${otpState.last30DaysOtps} টি OTP রিসিভ হয়েছে।\n• গত মাস (${otpState.lastMonthLabel}): ${otpState.lastMonthOtps} টি OTP।\n• তার আগের মাস (${otpState.twoMonthsAgoLabel}): ${otpState.twoMonthsAgoOtps} টি OTP।\n• মোট সর্বমোট (All-Time): ${otpState.totalOtpsAllTime} টি OTP।",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFFCBD5E1),
                                                        lineHeight = 15.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 9. Last 30 Days Daily OTP Breakdown
                            item {
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
                                                Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF40C4FF), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "30 DAYS DAILY OTP HISTORY (১ মাসের রেকর্ড)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }

                                            val sum30Days = otpState.last30DaysSummaries.sumOf { it.totalCount }
                                            Text(
                                                text = "Total 30d: $sum30Days",
                                                color = Color(0xFF81D4FA),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        val maxDaily = maxOf(1, otpState.last30DaysSummaries.maxOfOrNull { it.totalCount } ?: 1)

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            otpState.last30DaysSummaries.take(10).forEachIndexed { index, day ->
                                                DailyOtpRowItem(
                                                    summary = day,
                                                    maxCount = maxDaily,
                                                    isToday = index == 0
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
        }
    }

    // 1. PIN Setup Dialog (১ম বার ৪ ডিজিট পিন সেট)
    if (showPinSetupDialog) {
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("উইথড্রয়াল সিকিউরিটি পিন সেট করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "নিরাপত্তার স্বার্থে আপনার একাউন্টে ৪ ডিজিটের একটি গোপন পিন সেট করুন। পরবর্তী প্রতিবার টাকা তোলার সময় এই পিনটি প্রয়োজন হবে।",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = pinSetupInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinSetupInput = it },
                        label = { Text("নতুন ৪ ডিজিট পিন") },
                        placeholder = { Text("e.g. 1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinSetupConfirmInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinSetupConfirmInput = it },
                        label = { Text("পিন পুনরায় দিন (Confirm PIN)") },
                        placeholder = { Text("e.g. 1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinSetupInput.length != 4) {
                            Toast.makeText(context, "অবশ্যই ৪ ডিজিটের পিন হতে হবে", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pinSetupInput != pinSetupConfirmInput) {
                            Toast.makeText(context, "দুইটি পিন মেলেনি! পুনরায় চেক করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val success = WithdrawalManager.setSecurityPin(pinSetupInput, currentEmail)
                        if (success) {
                            Toast.makeText(context, "✓ ৪ ডিজিট সিকিউরিটি পিন সফলভাবে সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                            showPinSetupDialog = false
                            if (pendingWithdrawAmount > 0.0) {
                                pinInput = ""
                                showPinDialog = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("সংরক্ষণ ও নিশ্চিত করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPinSetupDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2. PIN Verification & Final Confirmation Dialog (উইথড্রয়াল কনফার্মেশন ও পিন ইনপুট)
    if (showPinDialog) {
        val isResetRequested = remember(showPinDialog) { WithdrawalManager.isPinResetRequested(currentEmail) }
        var resetRequestedState by remember { mutableStateOf(isResetRequested) }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingWithdraw) showPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("উইথড্রয়াল কনফার্মেশন", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("পেমেন্ট মেথড:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(pendingWithdrawMethod, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("একাউন্ট নম্বর:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(pendingWithdrawAccount, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("টাকার পরিমাণ:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text("৳${String.format(Locale.US, "%.2f", pendingWithdrawAmount)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00E676))
                            }
                        }
                    }

                    Text("উইথড্র নিশ্চিত করতে আপনার ৪ ডিজিটের সিকিউরিটি পিন দিন:", fontSize = 11.sp, color = Color(0xFFCBD5E1))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        placeholder = { Text("৪ ডিজিট পিন (PIN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Pin Reset Request to Admin button
                    Surface(
                        color = if (resetRequestedState) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color(0xFF0284C7).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (resetRequestedState) Color(0xFFF59E0B).copy(alpha = 0.4f) else Color(0xFF0284C7).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !resetRequestedState) {
                                WithdrawalManager.requestPinReset(currentEmail)
                                resetRequestedState = true
                                Toast.makeText(context, "✓ অ্যাডমিনের কাছে পিন রিসেট রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন অনুমোদন করলে নতুন পিন সেট করতে পারবেন।", Toast.LENGTH_LONG).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (resetRequestedState) Icons.Default.Refresh else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (resetRequestedState) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (resetRequestedState) "⏳ পিন রিসেট রিকোয়েস্ট অ্যাডমিনের কাছে পাঠানো হয়েছে" else "🔐 পিন ভুলে গেছেন? অ্যাডমিনকে রিসেট রিকোয়েস্ট পাঠান",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (resetRequestedState) Color(0xFFFBBF24) else Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 4) {
                            Toast.makeText(context, "৪ ডিজিটের সঠিক পিন দিন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val isPinValid = WithdrawalManager.verifySecurityPin(pinInput, currentEmail)
                        if (!isPinValid) {
                            Toast.makeText(context, "ভুল সিকিউরিটি পিন! সঠিক পিন দিন অথবা নিচে 'রিসেট রিকোয়েস্ট' পাঠান", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        isSubmittingWithdraw = true
                        val (success, msg) = WithdrawalManager.submitWithdrawal(
                            context = context,
                            userEmail = currentEmail,
                            method = pendingWithdrawMethod,
                            accountNumber = pendingWithdrawAccount,
                            amount = pendingWithdrawAmount
                        )
                        isSubmittingWithdraw = false
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            amountInput = ""
                            accountInput = ""
                            showPinDialog = false
                        }
                    },
                    enabled = !isSubmittingWithdraw && pinInput.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text(if (isSubmittingWithdraw) "প্রসেস হচ্ছে..." else "উইথড্র সম্পন্ন করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPinDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun WithdrawalHistoryItemRow(req: WithdrawalRequest) {
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(req.requestTimestamp))
    val statusColor = when (req.status) {
        "APPROVED" -> Color(0xFF00E676)
        "REJECTED" -> Color(0xFFEF4444)
        else -> Color(0xFFFFD600) // PENDING
    }

    Surface(
        color = Color(0xFF0B1420),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF1C2C3E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "৳${String.format(Locale.US, "%.2f", req.amount)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${req.method}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${req.accountNumber} • $dateStr",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )
            }

            Surface(
                color = statusColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = when (req.status) {
                        "APPROVED" -> "✓ APPROVED"
                        "REJECTED" -> "✕ REJECTED"
                        else -> "⏳ PENDING"
                    },
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileMetricPill(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    isSmallText: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = color,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = if (isSmallText) 11.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = if (isSmallText) FontFamily.Monospace else FontFamily.Default
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = Color(0xFF90A4AE),
                fontSize = 7.sp
            )
        }
    }
}

@Composable
fun DailyOtpRowItem(
    summary: DailyOtpSummary,
    maxCount: Int,
    isToday: Boolean
) {
    val progress = (summary.totalCount.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)

    Surface(
        color = if (isToday) Color(0xFF00E676).copy(alpha = 0.08f) else Color(0xFF0B1420),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isToday) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF1B2A3B)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = summary.displayLabel,
                        color = if (isToday) Color(0xFF00E676) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        text = "Unix: ${summary.unixCount} • Kop 1: ${summary.zenexCount} • Kop 2: ${summary.kop2Count} • V2: ${summary.v2Count}",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp
                    )
                }

                Surface(
                    color = if (summary.totalCount > 0) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF162536),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${summary.totalCount} OTP",
                        color = if (summary.totalCount > 0) Color(0xFF00E676) else Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isToday) Color(0xFF00E676) else Color(0xFF00B0FF),
                trackColor = Color(0xFF162536)
            )
        }
    }
}

@Composable
fun RecentOtpLogItem(
    rec: OtpHistoryRecord,
    context: Context
) {
    val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(rec.timestamp))
    val isUnix = rec.platform.contains("Unix", ignoreCase = true) || rec.platform.contains("Lamix", ignoreCase = true)
    val isV2 = rec.platform.contains("V2", ignoreCase = true) || rec.platform.contains("Premium Number V2", ignoreCase = true) || rec.platform.contains("Premium V2", ignoreCase = true)
    val isKop2 = rec.platform.contains("Kop 2", ignoreCase = true) || rec.platform.contains("Kop Engine 2", ignoreCase = true) || rec.platform.contains("Kop2", ignoreCase = true)

    val badgeBg = when {
        isV2 -> Color(0xFFF59E0B).copy(alpha = 0.2f)
        isUnix -> Color(0xFF0091EA).copy(alpha = 0.2f)
        isKop2 -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
        else -> Color(0xFF10B981).copy(alpha = 0.2f)
    }
    val badgeTextColor = when {
        isV2 -> Color(0xFFFBBF24)
        isUnix -> Color(0xFF81D4FA)
        isKop2 -> Color(0xFFDDD6FE)
        else -> Color(0xFF34D399)
    }
    val displayRate = if (rec.rate > 0.0) rec.rate else 0.50

    Surface(
        color = Color(0xFF09121D),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF19293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rec.otpCode,
                        color = Color(0xFF00E676),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "${rec.service} (${rec.platform}: +৳${String.format(Locale.US, "%.2f", displayRate)})",
                            color = badgeTextColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "${rec.phoneNumber} • $timeStr (${rec.platform})",
                    color = Color(0xFF64748B),
                    fontSize = 9.sp
                )
            }

            IconButton(
                onClick = {
                    ClipboardHelper.copyToClipboard(context, rec.otpCode, "OTP Code")
                    Toast.makeText(context, "OTP ${rec.otpCode} কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    VibrationHelper.vibrateSuccess(context)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF40C4FF), modifier = Modifier.size(13.dp))
            }
        }
    }
}
