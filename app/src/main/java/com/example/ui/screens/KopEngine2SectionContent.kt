package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import com.example.ui.components.KopRangeSelectorBox
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BroadcastFeedItem
import com.example.data.model.ProvisionedNumber
import com.example.service.KopEngine2Manager
import com.example.service.OverlayStateManager
import com.example.util.ClipboardHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Modern Cyber Slate & Electric Cyan Palette (No violet / beguni, No red for waiting)
private val ThemeBgDark = Color(0xFF0B132B)
private val ThemeCardBg = Color(0xFF0F172A)
private val ThemeSurfaceDark = Color(0xFF1E293B)
private val ThemeBorder = Color(0xFF334155)
private val ThemeCyan = Color(0xFF06B6D4)
private val ThemeSky = Color(0xFF0284C7)
private val ThemeSkyBright = Color(0xFF38BDF8)
private val ThemeEmerald = Color(0xFF10B981)
private val ThemeAmber = Color(0xFFF59E0B)
private val ThemeMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KopEngine2SectionContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by KopEngine2Manager.state.collectAsState()
    val baseKop2OtpRate by KopEngine2Manager.kop2OtpRate.collectAsState()
    val managedUsersForRate by com.example.service.AuthManager.managedUsers.collectAsState()
    val kop2OtpRate = remember(baseKop2OtpRate, managedUsersForRate) {
        com.example.service.AuthManager.getEffectiveKop2Rate(baseKop2OtpRate)
    }
    val overlayState by OverlayStateManager.uiState.collectAsState()

    var quantityDropdownExpanded by remember { mutableStateOf(false) }
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Numbers & Active Feed, 1: Signal (Live Feed)
    var consoleFilter by remember { mutableStateOf("") }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Throttled clock tick & radar data sync (preserves 60+ FPS UI smoothness)
    LaunchedEffect(Unit) {
        KopEngine2Manager.init(context)
        KopEngine2Manager.startAutoPolling(context, autoCopy = true)
        KopEngine2Manager.fetchRadarData()
        while (true) {
            delay(4000L)
            nowTick = System.currentTimeMillis()
        }
    }

    var kop2FilterTab by remember { mutableStateOf("ALL") }
    val total = state.provisionedNumbers.size
    val successCount = state.provisionedNumbers.count { it.status.equals("success", true) || !it.otpCode.isNullOrEmpty() }
    val waitCount = state.provisionedNumbers.count {
        it.otpCode.isNullOrEmpty() && (it.status.equals("pending", true) || it.status.equals("wait", true)) && nowTick < it.expiresAt
    }
    val expiredCount = state.provisionedNumbers.count {
        val hasOtp = it.status.equals("success", true) || !it.otpCode.isNullOrEmpty()
        !hasOtp && (it.status.equals("expired", true) || it.status.equals("timeout", true) || it.status.equals("failed", true) || nowTick >= it.expiresAt)
    }
    val successRate = if (total > 0) (successCount.toFloat() / total) * 100f else 0f
    val filteredKop2Numbers = remember(state.provisionedNumbers, kop2FilterTab, nowTick) {
        when (kop2FilterTab) {
            "SUCCESS" -> state.provisionedNumbers.filter { it.status.equals("success", true) || !it.otpCode.isNullOrEmpty() }
            "PENDING" -> state.provisionedNumbers.filter { it.otpCode.isNullOrEmpty() && (it.status.equals("pending", true) || it.status.equals("wait", true)) && nowTick < it.expiresAt }
            "FAILED" -> state.provisionedNumbers.filter {
                val hasOtp = it.status.equals("success", true) || !it.otpCode.isNullOrEmpty()
                !hasOtp && (it.status.equals("expired", true) || it.status.equals("timeout", true) || it.status.equals("failed", true) || nowTick >= it.expiresAt)
            }
            else -> state.provisionedNumbers
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Card (KOP ENGINE 2 • ELECTRIC VIOLET & SAPPHIRE LUXURY CARD)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1024)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.55f)),
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
                            color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.55f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🔥", fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "KOP ENGINE 2",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    color = Color(0xFF6366F1).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(5.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF818CF8).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "RADAR CORE",
                                        color = Color(0xFFC4B5FD),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Live Range Stream • High-Speed Radar API",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Today OTP Counter Card
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "OTP",
                                tint = Color(0xFFFDE047),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TODAY OTP: ${maxOf(state.todayOtpCount, successCount)}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4 Stats Row: Total, Received, Expired, Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF151932),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E365F)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "TOTAL", color = ThemeMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(text = "$total", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Surface(
                        color = ThemeEmerald.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThemeEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "RECEIVED", color = ThemeEmerald, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(text = "$successCount", color = ThemeEmerald, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "FAILED", color = Color(0xFFF87171), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(text = "$expiredCount", color = Color(0xFFEF4444), fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.55f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "OTP RATE", color = Color(0xFFC4B5FD), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format(Locale.US, "%.2f", kop2OtpRate)}৳",
                                color = Color(0xFFA78BFA),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Overlay & Auto Copy Controls Bar
                Surface(
                    color = ThemeSurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ThemeBorder),
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
                            // Auto-Copy Chip
                            Surface(
                                color = if (overlayState.autoCopyOtp) ThemeEmerald.copy(alpha = 0.2f) else ThemeBgDark,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (overlayState.autoCopyOtp) ThemeEmerald else ThemeBorder
                                ),
                                modifier = Modifier.clickable { OverlayStateManager.toggleAutoCopyOtp() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Auto-Copy: ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (overlayState.autoCopyOtp) "ON" else "OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (overlayState.autoCopyOtp) ThemeEmerald else ThemeAmber
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Sound/Vibe Chip
                            Surface(
                                color = if (overlayState.otpSoundVibrationEnabled) ThemeSky.copy(alpha = 0.2f) else ThemeBgDark,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (overlayState.otpSoundVibrationEnabled) ThemeSky else ThemeBorder
                                ),
                                modifier = Modifier.clickable { OverlayStateManager.toggleOtpSoundVibrationEnabled() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sound/Vibe: ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (overlayState.otpSoundVibrationEnabled) "ON" else "OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (overlayState.otpSoundVibrationEnabled) ThemeSkyBright else ThemeMuted
                                    )
                                }
                            }
                        }

                        // Clear Failed Button
                        if (expiredCount > 0) {
                            Surface(
                                color = ThemeSurfaceDark,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, ThemeMuted),
                                modifier = Modifier.clickable { KopEngine2Manager.clearFailedNumbers(context) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = ThemeMuted, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Clear Failed",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ThemeMuted
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Success Rate Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "↗ SUCCESS RATE",
                        color = ThemeMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", successRate),
                        color = ThemeEmerald,
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
                    color = ThemeEmerald,
                    trackColor = ThemeSurfaceDark
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 24-Hour Auto Reset Cycle Bar with Yes/No Confirmation Popup
                var showKop2ResetConfirmDialog by remember { mutableStateOf(false) }

                if (showKop2ResetConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showKop2ResetConfirmDialog = false },
                        containerColor = ThemeCardBg,
                        titleContentColor = Color.White,
                        textContentColor = Color(0xFFCBD5E1),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = ThemeSkyBright, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("OTP ও নম্বর রিসেট করবেন?", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        },
                        text = {
                            Text(
                                "আপনি কি নিশ্চিত যে Kop Engine 2-এর বর্তমান অ্যাক্টিভ নম্বর এবং আজকের ২৪ ঘণ্টার OTP লিস্ট রিসেট (Reset) করতে চান?",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showKop2ResetConfirmDialog = false
                                    KopEngine2Manager.resetTodayCycle(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Yes (হ্যাঁ)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showKop2ResetConfirmDialog = false },
                                border = BorderStroke(1.dp, ThemeBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("No (না)", color = Color(0xFFCBD5E1), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    )
                }

                Surface(
                    color = ThemeSurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ThemeBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = ThemeSkyBright, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Reset: ${KopEngine2Manager.getFormattedCycleCountdown()}",
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            val kop2TopFailedCount = state.provisionedNumbers.count {
                                it.otpCode.isNullOrEmpty() && (it.status.equals("failed", true) || it.status.equals("expired", true) || System.currentTimeMillis() >= it.expiresAt)
                            }
                            Surface(
                                color = Color(0xFFDC2626),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                modifier = Modifier.clickable {
                                    KopEngine2Manager.clearFailedNumbers(context)
                                }
                            ) {
                                Text(
                                    text = if (kop2TopFailedCount > 0) "🗑️ Clear Failed ($kop2TopFailedCount)" else "🗑️ Clear Failed",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
                                modifier = Modifier.clickable {
                                    showKop2ResetConfirmDialog = true
                                }
                            ) {
                                Text(
                                    text = "🔄 Reset Now",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Navigation Tab Row (Sub-Tabs)
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = ThemeCardBg,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = ThemeSkyBright,
                    height = 2.5.dp
                )
            },
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ThemeBorder)
                )
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (selectedSubTab == 0) ThemeSkyBright else ThemeMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Get Numbers (${state.provisionedNumbers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSubTab == 0) ThemeSkyBright else ThemeMuted
                        )
                    }
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            tint = if (selectedSubTab == 1) ThemeEmerald else ThemeMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Signal (Live Feed)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSubTab == 1) ThemeEmerald else ThemeMuted
                        )
                    }
                }
            )
        }

        if (selectedSubTab == 0) {
            // TAB 0: GET NUMBER CARD & PROVISIONED NUMBERS
            Card(
                colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.2.dp, ThemeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    KopRangeSelectorBox(
                        title = "TARGET RANGE / LIVE RADAR (KOP 2)",
                        engineName = "Kop Engine 2",
                        currentRange = state.targetRange,
                        onRangeSelected = { KopEngine2Manager.setTargetRange(it) },
                        activeRanges = state.activeRanges,
                        broadcastFeed = state.broadcastFeed,
                        onRefreshLivePool = {
                            KopEngine2Manager.fetchRadarData()
                        },
                        accentColor = ThemeSkyBright
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
                                checked = state.removePlus,
                                onCheckedChange = { KopEngine2Manager.setRemovePlus(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ThemeSkyBright,
                                    uncheckedColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("No (+)", color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                        }

                        // QUANTITY DROPDOWN (1 to 10 numbers)
                        Box {
                            Surface(
                                color = ThemeSurfaceDark,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ThemeBorder),
                                modifier = Modifier.clickable { quantityDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Qty: ${state.requestCount}",
                                        color = ThemeSkyBright,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = ThemeSkyBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = quantityDropdownExpanded,
                                onDismissRequest = { quantityDropdownExpanded = false },
                                modifier = Modifier.background(ThemeCardBg)
                            ) {
                                (1..10).forEach { count ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "$count টি নম্বর",
                                                color = if (state.requestCount == count) ThemeEmerald else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (state.requestCount == count) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            quantityDropdownExpanded = false
                                            KopEngine2Manager.setRequestCount(count)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // GET NUMBER ACTION BUTTON
                    val totalCost = kop2OtpRate * state.requestCount
                    Button(
                        onClick = {
                            KopEngine2Manager.provisionNumbers(context)
                        },
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeSky,
                            disabledContainerColor = ThemeSky.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ALLOCATING FROM MK NETWORK...", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        } else {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.requestCount > 1) "GET ${state.requestCount} NUMBERS (${String.format(Locale.US, "%.2f", totalCost)} ৳)"
                                else "GET NUMBER (${String.format(Locale.US, "%.2f", kop2OtpRate)} ৳ / OTP)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ACTIVE NUMBERS HEADER (Split into clean non-wrapping rows)
            val activeExportItems = remember(state.provisionedNumbers, kop2OtpRate) {
                state.provisionedNumbers
                    .filter { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
                    .map { num ->
                        val cInfo = com.example.util.AllCountriesProvider.detectCountry(num.number)
                            ?: com.example.util.AllCountriesProvider.detectCountry(num.range ?: "")
                            ?: com.example.util.AllCountriesProvider.detectCountry(num.country)
                        com.example.util.ExportOtpItem(
                            phoneNumber = num.number,
                            country = cInfo?.name ?: num.country,
                            iso = (cInfo?.code ?: num.iso).uppercase(),
                            flag = cInfo?.flag ?: "🌐",
                            serviceOrRange = num.range ?: "Kop 2",
                            otpCode = num.otpCode ?: "",
                            otpMessage = num.otpMessage,
                            timestamp = num.timestamp,
                            rate = kop2OtpRate
                        )
                    }
            }
            val kop2ExpiredCount = remember(state.provisionedNumbers, nowTick) {
                state.provisionedNumbers.count { prov ->
                    val hasOtp = !prov.otpCode.isNullOrEmpty() || prov.status.equals("success", true)
                    !hasOtp && (prov.status.equals("expired", true) || prov.status.equals("failed", true) || nowTick >= prov.expiresAt)
                }
            }
            val merged24hKop2Otps = remember(activeExportItems, state.resetCycleStartTime, state.todayOtpCount) {
                com.example.util.OtpExportHelper.getMerged24hOtps("Kop Engine 2", activeExportItems)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ThemeEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NUMBERS (${state.provisionedNumbers.size})",
                        color = ThemeSkyBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ThemeEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ThemeEmerald.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "⚡ 1.2s Poll",
                            color = ThemeEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Red Clear Failed / Expired Button
                    Surface(
                        color = if (kop2ExpiredCount > 0) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFFEF4444).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = if (kop2ExpiredCount > 0) 0.85f else 0.45f)),
                        modifier = Modifier.clickable {
                            KopEngine2Manager.clearExpiredAndFailedNumbers(context)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (kop2ExpiredCount > 0) "Clear Failed ($kop2ExpiredCount)" else "Clear Failed",
                                fontSize = 9.sp,
                                color = Color(0xFFFCA5A5),
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        color = if (merged24hKop2Otps.isNotEmpty()) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, if (merged24hKop2Otps.isNotEmpty()) Color(0xFF38BDF8) else Color(0xFF475569)),
                        modifier = Modifier.clickable {
                            val currentExportList = com.example.util.OtpExportHelper.getMerged24hOtps("Kop Engine 2", activeExportItems)
                            if (currentExportList.isEmpty()) {
                                Toast.makeText(context, "গত ২৪ ঘণ্টায় ডাউনলোড করার মতো কোনো সফল OTP নম্বর পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                            } else {
                                com.example.util.OtpExportHelper.exportOtps(context, "Kop Engine 2", currentExportList)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = if (merged24hKop2Otps.isNotEmpty()) Color(0xFF38BDF8) else Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (merged24hKop2Otps.isNotEmpty()) "24h OTP (${merged24hKop2Otps.size})" else "24h OTP",
                                fontSize = 9.sp,
                                color = if (merged24hKop2Otps.isNotEmpty()) Color.White else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            KopEngine2Manager.fetchIncomingOtps(context, autoCopy = true)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = ThemeSkyBright,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 24h Filter Chips Row: ALL, SUCCESS, PENDING, FAILED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to "ALL ($total)",
                    "SUCCESS" to "SUCCESS ($successCount)",
                    "PENDING" to "WAIT ($waitCount)",
                    "FAILED" to "FAILED ($expiredCount)"
                ).forEach { (keyTab, labelTab) ->
                    val isSelected = kop2FilterTab == keyTab
                    Surface(
                        color = if (isSelected) ThemeSky else ThemeSurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isSelected) ThemeSkyBright else ThemeBorder),
                        modifier = Modifier.clickable { kop2FilterTab = keyTab }
                    ) {
                        Text(
                            text = labelTab,
                            color = if (isSelected) Color.White else ThemeMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // PROVISIONED NUMBERS LIST (ALL 24H NUMBERS)
            if (filteredKop2Numbers.isEmpty()) {
                Surface(
                    color = ThemeCardBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ThemeBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "কোনো একটিভ নাম্বার নেই",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "উপরে 'GET NUMBER' চাপুন নতুন নাম্বার নিতে (২৪ ঘণ্টা হিস্ট্রি)",
                            color = ThemeMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                filteredKop2Numbers.forEach { item ->
                    key(item.id) {
                        Kop2ProvisionedNumberCard(
                            item = item,
                            nowTick = nowTick,
                            context = context
                        )
                    }
                }
            }
        } else {
            // TAB 1: SIGNAL (MK NETWORK LIVE OTP FEED TEMPLATE)
            Kop2LiveConsoleTab(
                state = state,
                consoleFilter = consoleFilter,
                onFilterChange = { consoleFilter = it },
                nowTick = nowTick,
                onManualRefresh = {
                    KopEngine2Manager.fetchGlobalBroadcast()
                    KopEngine2Manager.fetchActiveRanges()
                    KopEngine2Manager.fetchIncomingOtps(context, autoCopy = true)
                },
                context = context
            )
        }
    }
}

/**
 * Kop Engine 2 Number Card:
 * Changed waiting state away from red to sleek Ocean Slate / Cyan Blue.
 * Success is Emerald Green. Expired is neutral slate.
 */
@Composable
private fun Kop2ProvisionedNumberCard(
    item: ProvisionedNumber,
    nowTick: Long,
    context: Context
) {
    val remainingMs = (item.expiresAt - nowTick).coerceAtLeast(0L)
    val isExpired = (nowTick >= item.expiresAt || item.status.equals("expired", true) || item.status.equals("timeout", true)) && item.otpCode.isNullOrEmpty()
    val isSuccess = item.status.equals("success", true) || !item.otpCode.isNullOrEmpty()
    val totalDurationMs = 15 * 60 * 1000f // 15 Minutes
    val progressRatio = (remainingMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSuccess -> Color(0xFF063529)
                isExpired -> Color(0xFF280C12) // Red failed/expired background
                else -> Color(0xFF0F1E2E)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.4.dp,
            when {
                isSuccess -> ThemeEmerald
                isExpired -> Color(0xFFEF4444).copy(alpha = 0.85f) // Vivid Red border for expired/failed
                else -> ThemeSky.copy(alpha = 0.8f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Line 1: Number + Country | Status Pill + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.number,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clickable {
                                KopEngine2Manager.copyAndRemovePlus(context, item.number)
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                KopEngine2Manager.copyAndRemovePlus(context, item.number)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ThemeSkyBright, modifier = Modifier.size(13.dp))
                        }
                    }

                    val countryInfo = remember(item.number, item.range, item.country, item.iso) {
                        com.example.util.AllCountriesProvider.detectCountry(item.number)
                            ?: com.example.util.AllCountriesProvider.detectCountry(item.range ?: "")
                            ?: com.example.util.AllCountriesProvider.findCountry(item.iso)
                            ?: com.example.util.AllCountriesProvider.detectCountry(item.country)
                    }
                    val flag = countryInfo?.flag ?: "🌐"
                    val countryName = countryInfo?.name ?: item.country
                    val isoShort = (countryInfo?.code ?: item.iso).uppercase()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Surface(
                            color = ThemeSurfaceDark,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, ThemeBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = flag, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$countryName [$isoShort]",
                                    color = ThemeSkyBright,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Fresh / Taken Tag Badge
                        when (item.fbTagType) {
                            com.example.service.PhoneItem.TagType.FRESH -> {
                                Surface(
                                    color = Color(0xFF0F3B20),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF00E676))
                                ) {
                                    Text(
                                        text = "✨ FRESH",
                                        color = Color(0xFF00E676),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            com.example.service.PhoneItem.TagType.TAKEN -> {
                                Surface(
                                    color = Color(0xFF381515),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFFFF5252))
                                ) {
                                    Text(
                                        text = "👥 TAKEN",
                                        color = Color(0xFFFF5252),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            com.example.service.PhoneItem.TagType.PENDING -> {
                                if (item.fbStatus == "Checking...") {
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.6.dp, Color(0xFF38BDF8))
                                    ) {
                                        Text(
                                            text = "⏳ CHECKING...",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge (WAITING: Ocean Cyan | SUCCESS: Emerald | EXPIRED: Red)
                    Surface(
                        color = when {
                            isSuccess -> ThemeEmerald.copy(alpha = 0.2f)
                            isExpired -> Color(0xFFEF4444).copy(alpha = 0.25f)
                            else -> ThemeSky.copy(alpha = 0.25f)
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSuccess -> ThemeEmerald
                                isExpired -> Color(0xFFEF4444)
                                else -> ThemeSkyBright
                            }
                        )
                    ) {
                        Text(
                            text = when {
                                isSuccess -> "SUCCESS"
                                isExpired -> "EXPIRED"
                                else -> "WAITING"
                            },
                            color = when {
                                isSuccess -> ThemeEmerald
                                isExpired -> ThemeMuted
                                else -> ThemeSkyBright
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            KopEngine2Manager.deleteNumber(item.id)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = ThemeMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Line 2: Operator & Range + Red/Green Account Status Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pTag = when {
                        item.range.contains("whatsapp", ignoreCase = true) || item.range.contains("wp", ignoreCase = true) -> "💬 WhatsApp"
                        item.range.contains("instagram", ignoreCase = true) || item.range.contains("ig", ignoreCase = true) -> "📸 Instagram"
                        item.range.contains("otp", ignoreCase = true) -> "🔥 OTP Work"
                        item.range.contains("fb", ignoreCase = true) || item.range.contains("face", ignoreCase = true) || item.range.contains("clone", ignoreCase = true) || item.range.contains("new", ignoreCase = true) -> "Facebook"
                        item.range.isNotBlank() && item.range != "Live Range" -> item.range
                        else -> "Facebook"
                    }
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.6.dp, ThemeSkyBright.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = pTag,
                            color = ThemeSkyBright,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${item.operator} • Range: ${item.range}",
                        color = ThemeMuted,
                        fontSize = 11.sp
                    )
                }
                if (item.requestId != null) {
                    Text(
                        text = "Req #${item.requestId}",
                        color = ThemeSkyBright.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 15-Minute Countdown Progress
            if (!isSuccess && !isExpired) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = ThemeSurfaceDark,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, ThemeBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
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
                                        .background(ThemeSkyBright)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "MK Network Polling Active (1.2s)",
                                    color = ThemeSkyBright,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val mins = (remainingMs / 60000).toInt()
                            val secs = ((remainingMs % 60000) / 1000).toInt()
                            Text(
                                text = "⏳ ${String.format(Locale.US, "%02d:%02d", mins, secs)} বাকি",
                                color = Color(0xFFFDE047),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = ThemeSkyBright,
                            trackColor = ThemeBgDark
                        )
                    }
                }
            } else if (isExpired && !isSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = ThemeSurfaceDark,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, ThemeBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ℹ️ ১৫ মিনিট অতিবাহিত হওয়ায় নম্বরটির ওটিপি মেয়াদ শেষ হয়েছে।",
                            color = ThemeMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // OTP Display Box (WHEN SUCCESSFUL)
            if (!item.otpCode.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF042F24),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ThemeEmerald),
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
                            Text(text = "OTP RECEIVED:", fontSize = 9.sp, color = ThemeEmerald, fontWeight = FontWeight.Bold)
                            Text(
                                text = item.otpCode ?: "",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (!item.otpMessage.isNullOrEmpty()) {
                                Text(
                                    text = item.otpMessage ?: "",
                                    fontSize = 10.sp,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = {
                                KopEngine2Manager.copyOtpCode(context, item.otpCode ?: "")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeEmerald),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy OTP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Signal (Live OTP Feed following MK Network Screenshot 8 template)
 * Live Pause and fake MK header removed as requested.
 */
@Composable
private fun Kop2LiveConsoleTab(
    state: com.example.service.VirtualNumbersUiState,
    consoleFilter: String,
    onFilterChange: (String) -> Unit,
    nowTick: Long,
    onManualRefresh: () -> Unit,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Stream Header Bar with Total Records badge and Auto Sync Indicator
        Surface(
            color = ThemeCardBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ThemeBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ThemeEmerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live OTP Feed",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Live 3s Auto-Sync Badge
                    Kop2AutoSyncBadge()
                    Spacer(modifier = Modifier.width(6.dp))

                    // Records Count Badge
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, ThemeBorder)
                    ) {
                        Text(
                            text = "${state.broadcastFeed.size} records",
                            color = ThemeMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onManualRefresh,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = ThemeSkyBright,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = consoleFilter,
            onValueChange = onFilterChange,
            placeholder = {
                Text(
                    text = "Search by service, range, country, or operator...",
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = ThemeSkyBright,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ThemeSkyBright,
                unfocusedBorderColor = ThemeBorder,
                focusedContainerColor = ThemeCardBg,
                unfocusedContainerColor = ThemeCardBg
            )
        )

        // Filtered broadcast items
        val filteredList = remember(state.broadcastFeed, consoleFilter) {
            if (consoleFilter.isBlank()) state.broadcastFeed
            else {
                state.broadcastFeed.filter {
                    it.service.contains(consoleFilter, ignoreCase = true) ||
                            it.range.contains(consoleFilter, ignoreCase = true) ||
                            it.number.contains(consoleFilter, ignoreCase = true) ||
                            it.country.contains(consoleFilter, ignoreCase = true) ||
                            it.operator.contains(consoleFilter, ignoreCase = true)
                }
            }
        }

        if (filteredList.isEmpty()) {
            Surface(
                color = ThemeCardBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ThemeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("কোনো লাইভ ওটিপি রেকর্ড পাওয়া যায়নি (Syncing...)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            filteredList.take(50).forEachIndexed { index, feedItem ->
                key(feedItem.id) {
                    MkLiveOtpFeedCard(
                        item = feedItem,
                        isNew = index == 0,
                        nowTick = nowTick,
                        context = context
                    )
                }
            }
        }
    }
}

/**
 * MK Network Live OTP Feed Card:
 * Real-time clock syncing, dynamic second-by-second countdown, and live feed metadata.
 */
@Composable
private fun MkLiveOtpFeedCard(
    item: BroadcastFeedItem,
    isNew: Boolean,
    nowTick: Long,
    context: Context
) {
    val timeFormat = remember {
        SimpleDateFormat("hh:mm:ss a · dd MMM yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
    }
    val timeMs = remember(item.time) {
        if (item.time in 1..9_999_999_999L) item.time * 1000L else item.time
    }
    val formattedTime = remember(timeMs) {
        timeFormat.format(Date(if (timeMs > 0L) timeMs else System.currentTimeMillis()))
    }
    val diffMs = (nowTick - timeMs).coerceAtLeast(0L)
    val elapsedSeconds = diffMs / 1000L
    val elapsedText = when {
        elapsedSeconds <= 3L -> "Just now"
        elapsedSeconds < 60L -> "${elapsedSeconds}s ago"
        elapsedSeconds < 3600L -> "${elapsedSeconds / 60L}m ${elapsedSeconds % 60L}s ago"
        else -> "${elapsedSeconds / 3600L}h ago"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ThemeBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Line 1: [ 260XXX ❐ ] Badge + Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Range Pill with Copy icon (Screenshot 8)
                Surface(
                    color = ThemeSky.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ThemeSky.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        val selectedR = item.range.ifEmpty { item.number }
                        KopEngine2Manager.setTargetRange(selectedR)
                        ClipboardHelper.copyToClipboard(context, selectedR, "Range")
                        Toast.makeText(context, "✓ Range কপি ও সিলেক্ট হয়েছে: $selectedR", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.range.ifEmpty { item.number },
                            color = ThemeSkyBright,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Range",
                            tint = ThemeSkyBright,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Tags / Chips Row: [Service] [Country] [Operator] [• NEW] (Scrollable & non-wrapping)
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Service Chip with Emoji Platform Tag
                    val liveServiceTag = when {
                        item.service.contains("Insta", ignoreCase = true) || item.operator.contains("Insta", ignoreCase = true) || item.service.contains("IG", ignoreCase = true) -> "📸 Instagram"
                        item.service.contains("WhatsApp", ignoreCase = true) || item.service.contains("WA", ignoreCase = true) || item.service.contains("WP", ignoreCase = true) -> "💬 WhatsApp"
                        item.service.contains("OTP", ignoreCase = true) -> "🔥 OTP Work"
                        item.service.contains("FB", ignoreCase = true) || item.service.contains("Face", ignoreCase = true) || item.operator.contains("Clone", ignoreCase = true) || item.operator.contains("New", ignoreCase = true) || item.service.contains("New", ignoreCase = true) -> "Facebook"
                        item.service.isNotBlank() && item.service != "MK Network" -> item.service
                        else -> "Facebook"
                    }
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, ThemeBorder)
                    ) {
                        Text(
                            text = liveServiceTag,
                            color = ThemeCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Country Chip [ 🇲🇬 Madagascar MG ]
                    val cInfo = com.example.util.AllCountriesProvider.detectCountry(item.country)
                        ?: com.example.util.AllCountriesProvider.detectCountry(item.number)
                        ?: com.example.util.AllCountriesProvider.detectCountry(item.range)
                    val flagEmoji = cInfo?.flag ?: "🌐"
                    val countryName = cInfo?.name ?: item.country.ifBlank { "Global" }
                    val isoCode = (cInfo?.code ?: "").uppercase()

                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, ThemeBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = flagEmoji, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isoCode.isNotBlank()) "$countryName $isoCode" else countryName,
                                color = Color(0xFF2DD4BF),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Operator Chip
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, ThemeBorder)
                    ) {
                        Text(
                            text = item.operator,
                            color = ThemeAmber,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // • NEW Badge
                    if (isNew) {
                        Surface(
                            color = ThemeEmerald.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, ThemeEmerald)
                        ) {
                            Text(
                                text = "• NEW",
                                color = ThemeEmerald,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Body: Full SMS Container (click to copy full message)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = ThemeBgDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ThemeBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ClipboardHelper.copyToClipboard(context, item.otp, "SMS Body")
                        Toast.makeText(context, "এসএমএস কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Text(
                    text = item.otp,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Line 3: Bottom metadata (Timestamp and Elapsed time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = ThemeMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = formattedTime,
                        color = ThemeMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Green Elapsed Time Badge (e.g. "52s ago")
                Surface(
                    color = ThemeEmerald.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, ThemeEmerald.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = elapsedText,
                        color = ThemeEmerald,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Kop2AutoSyncBadge() {
    var syncCountdown by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            if (syncCountdown <= 1) {
                syncCountdown = 3
                KopEngine2Manager.fetchGlobalBroadcast()
                KopEngine2Manager.fetchActiveRanges()
            } else {
                syncCountdown -= 1
            }
        }
    }
    Surface(
        color = ThemeEmerald.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, ThemeEmerald.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(ThemeEmerald)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Auto Sync: ${syncCountdown}s",
                color = ThemeEmerald,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

