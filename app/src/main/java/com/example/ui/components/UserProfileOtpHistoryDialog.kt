package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
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
import kotlinx.coroutines.delay
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
    val currentEmail by AuthManager.currentEmail.collectAsState()
    val otpState by OtpHistoryManager.state.collectAsState()
    val withdrawState by WithdrawalManager.state.collectAsState()

    var dailyCountdownString by remember { mutableStateOf(OtpHistoryManager.getFormattedDailyResetCountdown()) }
    var monthlyCountdownString by remember { mutableStateOf(OtpHistoryManager.getFormatted1MonthRemainingCountdown()) }

    // Withdrawal Form State
    var selectedMethod by remember { mutableStateOf("Binance") } // "Binance", "bKash", "Nagad"
    var accountInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var isSubmittingWithdraw by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        OtpHistoryManager.init(context)
        WithdrawalManager.init(context)
        while (true) {
            delay(1000L)
            dailyCountdownString = OtpHistoryManager.getFormattedDailyResetCountdown()
            monthlyCountdownString = OtpHistoryManager.getFormatted1MonthRemainingCountdown()
        }
    }

    val minAmount = WithdrawalManager.getMinimumAmountForMethod(selectedMethod)
    val isMethodUnlocked = withdrawState.availableBalanceTk >= minAmount

    val userWithdrawals = remember(withdrawState.allRequests, currentEmail) {
        WithdrawalManager.getUserRequests(currentEmail)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0C141F),
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color(0xFF00B0FF).copy(alpha = 0.6f), Color(0xFF1E3A56))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF00B0FF), Color(0xFF0066FF)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PROFILE, OTP STATS & WALLET",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = currentEmail.ifEmpty { "User Account" },
                                    fontSize = 11.sp,
                                    color = Color(0xFF81D4FA)
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
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF90A4AE), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1B2C3F))
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Account Info Card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF101C2B)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E354F)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = "Status: ", fontSize = 11.sp, color = Color(0xFF90A4AE))
                                            Surface(
                                                color = Color(0xFF00E676).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (AuthManager.isAdmin()) "ADMIN MASTER" else "AUTHENTICATED USER",
                                                    color = Color(0xFF00E676),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "Daily OTP Auto-Reset: 1 Month (30 Days)", color = Color(0xFF78909C), fontSize = 10.sp)
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

                        // 2. Wallet & Balance Overview Card (Tk and OTP Balance)
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1929)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))),
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
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Surface(
                                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "WITHDRAWABLE",
                                                color = Color(0xFF00E676),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
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
                                                text = "Total Earned: ৳${String.format(Locale.US, "%.2f", withdrawState.totalEarnedTk)} (Pending: ৳${String.format(Locale.US, "%.2f", withdrawState.pendingWithdrawTk)})",
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }

                                        // 30 Days Download / Export History Button
                                        Button(
                                            onClick = {
                                                OtpHistoryManager.exportOtpHistory(context)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Primary Metrics Row (Today, Total, 30D Remaining, Today Reset)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProfileMetricPill(
                                        title = "TODAY OTP",
                                        value = otpState.todayOtps.toString(),
                                        subtitle = "All Panels Today",
                                        color = Color(0xFF00E676),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ProfileMetricPill(
                                        title = "TOTAL OTP",
                                        value = otpState.totalOtpsAllTime.toString(),
                                        subtitle = "All-Time Total",
                                        color = Color(0xFF00B0FF),
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

                        // 4. WITHDRAW OPTION CARD (Binance, bKash, Nagad)
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
                                    Text(
                                        text = "পেমেন্ট মাধ্যম বেছে নিন: Binance (মিনিমাম ২০ টাকা), bKash/Nagad (মিনিমাম ৫০ টাকা)",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Payment Method Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            Triple("Binance", "Binance Pay", "Min: ৳20"),
                                            Triple("bKash", "বিকাশ", "Min: ৳50"),
                                            Triple("Nagad", "নগদ", "Min: ৳50")
                                        ).forEach { (mKey, mTitle, minLabel) ->
                                            val isSel = selectedMethod == mKey
                                            val methodColor = when (mKey) {
                                                "Binance" -> Color(0xFFF0B90B)
                                                "bKash" -> Color(0xFFE2136E)
                                                else -> Color(0xFFF7931A) // Nagad
                                            }

                                            Surface(
                                                color = if (isSel) methodColor.copy(alpha = 0.25f) else Color(0xFF090E17),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.2.dp, if (isSel) methodColor else Color(0xFF1E2D40)),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        selectedMethod = mKey
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = mTitle,
                                                        color = if (isSel) Color.White else Color(0xFF90A4AE),
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = minLabel,
                                                        color = if (isSel) methodColor else Color(0xFF64748B),
                                                        fontSize = 8.sp,
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
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                                    Text(text = if (chip == "All") "সব টাকা" else "৳$chip", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Submit / Withdraw Button
                                    Button(
                                        onClick = {
                                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                                            if (accountInput.isBlank()) {
                                                Toast.makeText(context, "একাউন্ট নাম্বার বা Binance ID দিন", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (amt <= 0.0) {
                                                Toast.makeText(context, "সঠিক টাকার পরিমাণ দিন", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            isSubmittingWithdraw = true
                                            val (success, msg) = WithdrawalManager.submitWithdrawal(
                                                context = context,
                                                userEmail = currentEmail,
                                                method = selectedMethod,
                                                accountNumber = accountInput,
                                                amount = amt
                                            )
                                            isSubmittingWithdraw = false
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            if (success) {
                                                amountInput = ""
                                                accountInput = ""
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

                        // 5. WITHDRAW HISTORY LIST (User's requests)
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
                                                text = "WITHDRAW HISTORY (${userWithdrawals.size})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Text(text = "Status Alerts", color = Color(0xFF94A3B8), fontSize = 10.sp)
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
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            userWithdrawals.take(10).forEach { req ->
                                                WithdrawalHistoryItemRow(req = req)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Panel Breakdown Card: Unix SMS vs Zenex SMS
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
                                                text = "PANEL BREAKDOWN (প্যানেল ওটিপি)",
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
                                                text = "Unix: $${String.format(Locale.US, "%.3f", otpState.currentUnixRate)} • Zenex: $${String.format(Locale.US, "%.3f", otpState.currentZenexRate)}",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Unix SMS Box
                                        Surface(
                                            color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "1. Unix SMS Panel",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Today: ${otpState.unixTodayOtps} OTPs",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "Total: ${otpState.unixTotalAllTime} OTPs",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        // Zenex SMS Box
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "2. Zenex SMS Panel",
                                                    color = Color(0xFF34D399),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Today: ${otpState.zenexTodayOtps} OTPs",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "Total: ${otpState.zenexTotalAllTime} OTPs",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Last 30 Days Daily OTP Breakdown
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

                        // 8. Recent OTP Records List
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
                                        Text(
                                            text = "RECENT RECEIVED OTPS (${otpState.recentRecords.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )

                                        Text(
                                            text = "1-Tap Copy",
                                            color = Color(0xFF00E676),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (otpState.recentRecords.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "কোনো ওটিপি এখনও রিসিভ হয়নি",
                                                color = Color(0xFF64748B),
                                                fontSize = 11.sp
                                            )
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            otpState.recentRecords.take(15).forEach { rec ->
                                                RecentOtpLogItem(rec = rec, context = context)
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
                        text = "Unix: ${summary.unixCount} • Zenex: ${summary.zenexCount}",
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
    val isUnix = rec.platform.contains("Unix", ignoreCase = true)

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
            Column {
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
                        color = if (isUnix) Color(0xFF0091EA).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "${rec.service} (${rec.platform}: +৳${String.format(Locale.US, "%.2f", rec.rate * 120.0)})",
                            color = if (isUnix) Color(0xFF81D4FA) else Color(0xFF34D399),
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
