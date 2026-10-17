package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
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
    val kop2OtpRate by KopEngine2Manager.kop2OtpRate.collectAsState()
    val overlayState by OverlayStateManager.uiState.collectAsState()

    var quantityDropdownExpanded by remember { mutableStateOf(false) }
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Numbers & Active Feed, 1: Signal (Live Feed)
    var consoleFilter by remember { mutableStateOf("") }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var syncCountdown by remember { mutableIntStateOf(3) }

    // Fast 1s Real-Time Clock Tick & 3s Live API Auto Sync
    LaunchedEffect(Unit) {
        KopEngine2Manager.init(context)
        KopEngine2Manager.startAutoPolling(context, autoCopy = true)
        KopEngine2Manager.fetchActiveRanges()
        KopEngine2Manager.fetchGlobalBroadcast()
        while (true) {
            delay(1000L)
            nowTick = System.currentTimeMillis()
            KopEngine2Manager.checkNumberTimeouts()
            KopEngine2Manager.fetchIncomingOtps(context, autoCopy = true)
            if (syncCountdown <= 1) {
                syncCountdown = 3
                KopEngine2Manager.fetchGlobalBroadcast()
                KopEngine2Manager.fetchActiveRanges()
            } else {
                syncCountdown -= 1
            }
        }
    }

    val total = state.provisionedNumbers.size
    val successCount = state.provisionedNumbers.count { it.status.equals("success", true) || !it.otpCode.isNullOrEmpty() }
    val expiredCount = state.provisionedNumbers.count {
        it.status.equals("expired", true) || it.status.equals("timeout", true) || it.status.equals("failed", true) ||
                (it.otpCode.isNullOrEmpty() && nowTick >= it.expiresAt)
    }
    val successRate = if (total > 0) (successCount.toFloat() / total) * 100f else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Card (KOP ENGINE 2 ACTIVE & STATS)
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, ThemeSky.copy(alpha = 0.5f)),
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
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(ThemeEmerald)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "KOP ENGINE 2 • LIVE CORE",
                            color = ThemeSkyBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    // Today OTP Counter Card
                    Surface(
                        color = ThemeSky.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThemeSky.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "OTP",
                                tint = Color(0xFFFDE047),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "TODAY OTP: ${state.todayOtpCount}",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Row: Total, Success, Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Numbers
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThemeBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "TOTAL", color = ThemeMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "$total", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    // Success Count
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThemeBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "SUCCESS", color = ThemeEmerald, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "$successCount", color = ThemeEmerald, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    // OTP Rate
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ThemeBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "RATE", color = ThemeSkyBright, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.2f", kop2OtpRate)} ৳",
                                color = ThemeSkyBright,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
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

                // 24-Hour Auto Reset Cycle Bar
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = ThemeSkyBright, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "24h Auto Reset: ${KopEngine2Manager.getFormattedCycleCountdown()}",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Reset Now",
                            color = ThemeSkyBright,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                KopEngine2Manager.resetTodayCycle(context)
                            }
                        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TARGET RANGE / PREFIX:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ThemeSkyBright,
                            letterSpacing = 0.5.sp
                        )
                        // Rate Pill
                        Surface(
                            color = ThemeSky.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ThemeSky.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Rate: ${String.format(Locale.US, "%.2f", kop2OtpRate)} ৳ / OTP",
                                color = ThemeSkyBright,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.targetRange,
                        onValueChange = { KopEngine2Manager.setTargetRange(it) },
                        placeholder = { Text("e.g. 260XXX, 26095XXX, 26075XXX, 237XXX, 224XXX...", color = Color(0xFF64748B), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ThemeSkyBright,
                            unfocusedBorderColor = ThemeBorder,
                            focusedContainerColor = ThemeSurfaceDark,
                            unfocusedContainerColor = ThemeSurfaceDark
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Active suggestions
                    if (state.activeRanges.isNotEmpty()) {
                        Text(
                            text = "Live Active Signals / Ranges (ট্যাপ করে সিলেক্ট করুন):",
                            color = ThemeMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            state.activeRanges.take(8).forEach { item ->
                                val isSelected = state.targetRange == item.range
                                Surface(
                                    color = if (isSelected) ThemeSky.copy(alpha = 0.35f) else ThemeSurfaceDark,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) ThemeSkyBright else ThemeBorder
                                    ),
                                    modifier = Modifier.clickable {
                                        KopEngine2Manager.setTargetRange(item.range)
                                    }
                                ) {
                                    val label = if (item.hits > 0) "${item.range} (${item.service} • ${item.hits})" else "${item.range} (${item.service})"
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

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

            // ACTIVE NUMBERS HEADER
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
                            .background(ThemeEmerald)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "ALLOCATED NUMBERS (${state.provisionedNumbers.size})",
                        color = ThemeSkyBright,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = ThemeEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ThemeEmerald.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Auto Poll: 1.2s",
                            color = ThemeEmerald,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
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

            // PROVISIONED NUMBERS LIST (NO RED - USES CYBER SKY / OCEAN BLUE & EMERALD)
            if (state.provisionedNumbers.isEmpty()) {
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
                            text = "উপরে 'GET NUMBER' চাপুন নতুন নাম্বার নিতে (১৫ মিনিট ভ্যালিডিটি)",
                            color = ThemeMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                state.provisionedNumbers.forEach { item ->
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
                syncCountdown = syncCountdown,
                nowTick = nowTick,
                onManualRefresh = {
                    syncCountdown = 3
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
                isExpired -> Color(0xFF1E293B)
                else -> Color(0xFF0F1E2E) // Smooth dark ocean cyan, NOT RED
            }
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.2.dp,
            when {
                isSuccess -> ThemeEmerald
                isExpired -> ThemeBorder
                else -> ThemeSky.copy(alpha = 0.8f) // Clean sky blue, NOT RED
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Line 1: Number + Country | Status Pill + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.number,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            KopEngine2Manager.copyAndRemovePlus(context, item.number)
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.8.dp, ThemeBorder)
                    ) {
                        Text(
                            text = item.country.uppercase(),
                            color = ThemeSkyBright,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            KopEngine2Manager.copyAndRemovePlus(context, item.number)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ThemeSkyBright, modifier = Modifier.size(14.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge (WAITING: Ocean Cyan | SUCCESS: Emerald | EXPIRED: Slate)
                    Surface(
                        color = when {
                            isSuccess -> ThemeEmerald.copy(alpha = 0.2f)
                            isExpired -> ThemeBorder.copy(alpha = 0.4f)
                            else -> ThemeSky.copy(alpha = 0.25f) // NOT RED
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSuccess -> ThemeEmerald
                                isExpired -> ThemeBorder
                                else -> ThemeSkyBright // NOT RED
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
                                else -> ThemeSkyBright // NOT RED
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
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

            // Line 2: Operator & Range
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.operator} • Range: ${item.range}",
                    color = ThemeMuted,
                    fontSize = 11.sp
                )
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
    syncCountdown: Int,
    nowTick: Long,
    onManualRefresh: () -> Unit,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Unified Range Option Card (Zenex-style Dropdown List for Kop Engine 2)
        var rangeOptionExpanded by remember { mutableStateOf(false) }
        val currentSelectedRange = state.targetRange.ifEmpty { "260XXX" }
        val matchingRangeItem = state.activeRanges.firstOrNull { it.range == currentSelectedRange }
            ?: state.activeRanges.firstOrNull()

        Surface(
            color = ThemeCardBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ThemeBorder),
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
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ThemeEmerald)
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
                                    color = ThemeSkyBright,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = ThemeSkyBright.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${matchingRangeItem?.hits ?: 175} Hits",
                                color = ThemeSkyBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                val r = matchingRangeItem?.range ?: currentSelectedRange
                                KopEngine2Manager.setTargetRange(r)
                                ClipboardHelper.copyToClipboard(context, r, "Target Range")
                                Toast.makeText(context, "রেঞ্জ কপি হয়েছে: $r", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Range",
                                tint = ThemeSkyBright,
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

                // Expanded view of selectable Top Hit Ranges (Droplist)
                AnimatedVisibility(visible = rangeOptionExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(color = ThemeBorder, thickness = 0.8.dp)
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
                                color = if (isCurrent) ThemeSkyBright.copy(alpha = 0.2f) else Color(0xFF0B141E),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) ThemeSkyBright else ThemeBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        KopEngine2Manager.setTargetRange(rangeItem.range)
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
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isCurrent) ThemeEmerald else ThemeSkyBright)
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
                                            color = ThemeSkyBright,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Select",
                                            tint = ThemeSkyBright,
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

        // 2. Search & Auto-Sync Bar (Zenex style with 3-second live auto-sync)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = consoleFilter,
                onValueChange = onFilterChange,
                placeholder = {
                    Text(
                        text = "Filter by number, code, service, or country...",
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
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = ThemeSkyBright,
                    unfocusedBorderColor = ThemeBorder,
                    focusedContainerColor = ThemeCardBg,
                    unfocusedContainerColor = ThemeCardBg
                )
            )

            Surface(
                color = ThemeCardBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ThemeBorder),
                modifier = Modifier
                    .height(52.dp)
                    .clickable { onManualRefresh() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ThemeEmerald)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = ThemeSkyBright,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Auto Sync: ${syncCountdown}s",
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Filtered broadcast items
        val filteredList = remember(state.broadcastFeed, consoleFilter) {
            if (consoleFilter.isBlank()) state.broadcastFeed
            else {
                state.broadcastFeed.filter {
                    it.service.contains(consoleFilter, ignoreCase = true) ||
                            it.range.contains(consoleFilter, ignoreCase = true) ||
                            it.number.contains(consoleFilter, ignoreCase = true) ||
                            it.country.contains(consoleFilter, ignoreCase = true) ||
                            it.operator.contains(consoleFilter, ignoreCase = true) ||
                            it.otp.contains(consoleFilter, ignoreCase = true)
                }
            }
        }

        // 3. Stream Header Bar with Total Records badge
        Surface(
            color = ThemeCardBg,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ThemeBorder),
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
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ThemeEmerald)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "Live OTP Stream",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = ThemeSurfaceDark,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.8.dp, ThemeBorder)
                ) {
                    Text(
                        text = "${filteredList.size} records",
                        color = ThemeMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                    )
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
            filteredList.forEachIndexed { index, feedItem ->
                key(feedItem.id) {
                    Kop2LiveOtpTerminalCard(
                        feed = feedItem,
                        context = context
                    )
                }
            }
        }
    }
}

/**
 * Kop Engine 2 Live OTP Feed Card (Zenex Terminal Style powered by MK Network data):
 * Line 1: Real-time Timestamp (Amber Mono) | Operator | | 🌍 Country (Green) | Service Pill (Facebook, Instagram, WhatsApp)
 * Line 2: Phone number with 1-tap copy, Range pill with 1-tap copy & set as target range, and highlighted Green OTP code badge with 1-tap copy.
 * Line 3: Full SMS text body.
 * Tapping the card automatically extracts and copies the OTP code.
 */
@Composable
private fun Kop2LiveOtpTerminalCard(
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

    val extractedOtp = remember(feed.otp) {
        val matcher = java.util.regex.Pattern.compile("\\b\\d{4,8}\\b").matcher(feed.otp)
        if (matcher.find()) matcher.group() else ""
    }

    Surface(
        color = Color(0xFF0B141E),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF182C40)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (extractedOtp.isNotEmpty()) {
                    ClipboardHelper.copyToClipboard(context, extractedOtp, "OTP Code")
                    Toast.makeText(context, "✓ OTP কপি হয়েছে: $extractedOtp", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "নম্বর কপি হয়েছে: ${feed.number}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = Color(0xFF112438),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.7.dp, Color(0xFF204266)),
                        modifier = Modifier.clickable {
                            KopEngine2Manager.setTargetRange(feed.range)
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

                // Extracted OTP code badge with 1-tap copy
                if (extractedOtp.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF00E676),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clickable {
                            ClipboardHelper.copyToClipboard(context, extractedOtp, "OTP Code")
                            Toast.makeText(context, "✓ OTP কপি হয়েছে: $extractedOtp", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = extractedOtp,
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.Black,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Line 3: SMS body
            Text(
                text = feed.otp,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
