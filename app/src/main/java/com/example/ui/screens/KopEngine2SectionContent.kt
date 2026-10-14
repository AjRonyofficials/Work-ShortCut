package com.example.ui.screens

import android.content.Context
import android.widget.Toast
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
                        placeholder = { Text("e.g. 237626XXX, 224655XXX, 261385XXX...", color = Color(0xFF64748B), fontSize = 12.sp) },
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
                            state.activeRanges.forEach { item ->
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
            filteredList.forEachIndexed { index, feedItem ->
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
                        ClipboardHelper.copyToClipboard(context, item.range.ifEmpty { item.number }, "Range")
                        Toast.makeText(context, "Range কপি হয়েছে: ${item.range.ifEmpty { item.number }}", Toast.LENGTH_SHORT).show()
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

                // Tags / Chips Row: [Service] [Country] [Operator] [• NEW]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Service Chip
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, ThemeBorder)
                    ) {
                        Text(
                            text = item.service,
                            color = ThemeCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Country Chip
                    Surface(
                        color = ThemeSurfaceDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, ThemeBorder)
                    ) {
                        Text(
                            text = item.country,
                            color = Color(0xFF2DD4BF),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
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
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: SMS / OTP Code Box in rounded mono container (Screenshot 8)
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
