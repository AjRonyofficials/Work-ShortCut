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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.AuthManager
import com.example.service.DailyOtpSummary
import com.example.service.OtpHistoryManager
import com.example.service.OtpHistoryRecord
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

    var dailyCountdownString by remember { mutableStateOf(OtpHistoryManager.getFormattedDailyResetCountdown()) }
    var sevenDaysCountdownString by remember { mutableStateOf(OtpHistoryManager.getFormatted7DaysRemainingCountdown()) }

    LaunchedEffect(Unit) {
        OtpHistoryManager.init(context)
        while (true) {
            delay(1000L)
            dailyCountdownString = OtpHistoryManager.getFormattedDailyResetCountdown()
            sevenDaysCountdownString = OtpHistoryManager.getFormatted7DaysRemainingCountdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
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
                                    .size(36.dp)
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "USER PROFILE & OTP STATS",
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
                                        Text(text = "Security: Device Locked Session", color = Color(0xFF78909C), fontSize = 10.sp)
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

                        // 2. Primary Metrics Row (Today, Total, 7D Remaining, Today Reset)
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
                                        title = "7-DAY RESET LEFT",
                                        value = sevenDaysCountdownString,
                                        subtitle = "7 Days History Reset",
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

                        // 3. Panel Breakdown Card: Unix SMS vs Zenex SMS
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
                                                text = "PANEL BREAKDOWN (কোন প্যানেলে কত ওটিপি)",
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
                                                text = "Rate: $${String.format(Locale.US, "%.3f", otpState.currentUnixRate)}/OTP",
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

                        // 4. Last 7 Days Daily OTP Breakdown with historical rate
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
                                                text = "7 DAYS DAILY OTP & RATES",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        val sum7Days = otpState.last7DaysSummaries.sumOf { it.totalCount }
                                        Text(
                                            text = "Total 7d: $sum7Days",
                                            color = Color(0xFF81D4FA),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val maxDaily = maxOf(1, otpState.last7DaysSummaries.maxOfOrNull { it.totalCount } ?: 1)

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        otpState.last7DaysSummaries.forEachIndexed { index, day ->
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

                        // 5. Recent OTP Records List
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
                        text = "Unix: ${summary.unixCount} nos ($${String.format(Locale.US, "%.3f", summary.unixRateOnDay)}) • Zenex: ${summary.zenexCount} nos",
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
                            text = "${rec.service} (${if (isUnix) "$${String.format(Locale.US, "%.3f", rec.rate)}" else "Zenex"})",
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
