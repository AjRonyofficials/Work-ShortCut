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
import java.util.regex.Pattern

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
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Numbers & Active Feed, 1: Live Console
    var consoleFilter by remember { mutableStateOf("") }
    var syncCountdown by remember { mutableIntStateOf(1) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Ultra-Fast 1-Second Auto Background Fetch & 15-Minute Expiry Countdown Engine
    LaunchedEffect(Unit) {
        KopEngine2Manager.init(context)
        KopEngine2Manager.startAutoPolling(context, autoCopy = true)
        while (true) {
            delay(1000L)
            nowTick = System.currentTimeMillis()
            KopEngine2Manager.checkNumberTimeouts()
            if (syncCountdown <= 1) {
                syncCountdown = 1
                KopEngine2Manager.fetchGlobalBroadcast()
                KopEngine2Manager.fetchIncomingOtps(context, autoCopy = true)
            } else {
                syncCountdown -= 1
            }
        }
    }

    val total = state.provisionedNumbers.size
    val successCount = state.provisionedNumbers.count { it.status.equals("success", true) || !it.otpCode.isNullOrEmpty() }
    val waitCount = state.provisionedNumbers.count {
        it.otpCode.isNullOrEmpty() && (it.status.equals("pending", true) || it.status.equals("wait", true)) && nowTick < it.expiresAt
    }
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16112C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF3B2968)),
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
                                .background(Color(0xFFA78BFA))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KOP ENGINE 2 ACTIVE",
                            color = Color(0xFFA78BFA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    // Today OTP Counter Card
                    Surface(
                        color = Color(0xFF7C3AED).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "OTP",
                                tint = Color(0xFFC4B5FD),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TODAY OTP: ${state.todayOtpCount}",
                                color = Color(0xFFEDE9FE),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4 Stat Cards: TOTAL, SUCCESS, WAIT, EXPIRED (15 min)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPillKop2(label = "TOTAL", value = total.toString(), color = Color(0xFFE0E0E0), modifier = Modifier.weight(1f))
                    StatPillKop2(label = "SUCCESS", value = successCount.toString(), color = Color(0xFF34D399), modifier = Modifier.weight(1f))
                    StatPillKop2(label = "WAIT (15m)", value = waitCount.toString(), color = Color(0xFFFFD600), modifier = Modifier.weight(1f))
                    StatPillKop2(label = "FAILED", value = expiredCount.toString(), color = Color(0xFFFF5252), modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Smart Automation Toggles (Option 1 & Option 6) + Option 5 Clear Failed (15m)
                Surface(
                    color = Color(0xFF16112C),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF3F2D6B)),
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
                                color = if (overlayState.autoCopyOtp) Color(0xFF34D399).copy(alpha = 0.2f) else Color(0xFF281E48),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (overlayState.autoCopyOtp) Color(0xFF34D399) else Color(0xFF6B589C)
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
                                        color = if (overlayState.autoCopyOtp) Color(0xFF34D399) else Color(0xFFFF5252)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Option 6 Chip: Sound/Vibe
                            Surface(
                                color = if (overlayState.otpSoundVibrationEnabled) Color(0xFFFFD600).copy(alpha = 0.2f) else Color(0xFF281E48),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (overlayState.otpSoundVibrationEnabled) Color(0xFFFFD600) else Color(0xFF6B589C)
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
                                        color = if (overlayState.otpSoundVibrationEnabled) Color(0xFFFFD600) else Color(0xFFFF5252)
                                    )
                                }
                            }
                        }

                        // Option 5: Clear Failed (15m) Button
                        if (expiredCount > 0) {
                            Surface(
                                color = Color(0xFFFF5252).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                modifier = Modifier.clickable { KopEngine2Manager.clearFailedNumbers(context) }
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
                        color = Color(0xFF34D399),
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
                    color = Color(0xFFA78BFA),
                    trackColor = Color(0xFF281E48)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 24-HOUR AUTO RESET CYCLE BAR
                Surface(
                    color = Color(0xFF1E1738),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF3F2D6B)),
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
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "24h Auto Reset: ${KopEngine2Manager.getFormattedCycleCountdown()}",
                                color = Color(0xFFDDD6FE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Auto Clear",
                            color = Color(0xFFA78BFA),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Subtabs: Get Number vs Live Console
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color(0xFF0F0B1E),
            contentColor = Color(0xFFA78BFA),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = Color(0xFFA78BFA),
                    height = 2.5.dp
                )
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Get Number", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Console", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (selectedSubTab == 0) {
            // TAB 0: GET NUMBER CARD & PROVISIONED NUMBERS
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16112C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF3B2968)),
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC4B5FD)
                        )

                        // Rate Pill (shows Admin rate setting)
                        Surface(
                            color = Color(0xFF7C3AED).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6))
                        ) {
                            Text(
                                text = "Rate: ${String.format(Locale.US, "%.2f", kop2OtpRate)} ৳ / OTP",
                                color = Color(0xFFC4B5FD),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.targetRange,
                        onValueChange = { KopEngine2Manager.setTargetRange(it) },
                        placeholder = { Text("e.g. 447700XXX, 336123XXX, 491512XXX...", color = Color(0xFF6B7280), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFA78BFA),
                            unfocusedBorderColor = Color(0xFF3B2968),
                            focusedContainerColor = Color(0xFF0C0917),
                            unfocusedContainerColor = Color(0xFF0C0917)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Active suggestions
                    if (state.activeRanges.isNotEmpty()) {
                        Text(
                            text = "Active Suggestions (ট্যাপ করে সিলেক্ট করুন):",
                            color = Color(0xFF9CA3AF),
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.activeRanges.take(4).forEach { item ->
                                Surface(
                                    color = if (state.targetRange == item.range) Color(0xFF7C3AED) else Color(0xFF241A42),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        KopEngine2Manager.setTargetRange(item.range)
                                    }
                                ) {
                                    Text(
                                        text = "${item.range} (${item.service})",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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
                                checked = state.isNational,
                                onCheckedChange = { KopEngine2Manager.setNational(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFA78BFA),
                                    uncheckedColor = Color(0xFF6B7280)
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("National", color = Color(0xFFD1D5DB), fontSize = 11.sp)

                            Spacer(modifier = Modifier.width(10.dp))

                            Checkbox(
                                checked = state.removePlus,
                                onCheckedChange = { KopEngine2Manager.setRemovePlus(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFA78BFA),
                                    uncheckedColor = Color(0xFF6B7280)
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("No (+)", color = Color(0xFFD1D5DB), fontSize = 11.sp)
                        }

                        // QUANTITY DROPDOWN (1 to 10 numbers)
                        Box {
                            Surface(
                                color = Color(0xFF0C0917),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF3B2968)),
                                modifier = Modifier.clickable { quantityDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Qty: ${state.requestCount}",
                                        color = Color(0xFFC4B5FD),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color(0xFFC4B5FD),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = quantityDropdownExpanded,
                                onDismissRequest = { quantityDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF16112C))
                            ) {
                                (1..10).forEach { count ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "$count টি নম্বর",
                                                color = if (state.requestCount == count) Color(0xFF34D399) else Color.White,
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
                            containerColor = Color(0xFF7C3AED),
                            disabledContainerColor = Color(0xFF7C3AED).copy(alpha = 0.4f)
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
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFA78BFA))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "KOP ENGINE 2 ACTIVE NUMBERS (${state.provisionedNumbers.size})",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF1E1738),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF3F2D6B))
                    ) {
                        Text(
                            text = "Auto Poll: 1s",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ACTIVE PROVISIONED NUMBERS LIST (WITH 15-MINUTE EXPIRY TIMER)
            if (state.provisionedNumbers.isEmpty()) {
                Surface(
                    color = Color(0xFF130E24),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF2C1F4D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "কোনো একটিভ নাম্বার নেই",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "উপরে 'GET NUMBER' চাপুন নতুন নাম্বার নিতে (১৫ মিনিট ভ্যালিডিটি)",
                            color = Color(0xFF9CA3AF),
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
            // TAB 1: LIVE CONSOLE (IDENTICAL TO ZENEX & KOP ENGINE)
            Kop2LiveConsoleTab(
                state = state,
                consoleFilter = consoleFilter,
                onFilterChange = { consoleFilter = it },
                syncCountdown = syncCountdown,
                onManualRefresh = {
                    syncCountdown = 1
                    KopEngine2Manager.fetchGlobalBroadcast()
                    KopEngine2Manager.fetchIncomingOtps(context, autoCopy = true)
                },
                context = context
            )
        }
    }
}

/**
 * Kop Engine 2 Number Card with 15-Minute Countdown Timer & OTP Display
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
                isSuccess -> Color(0xFF0F261B)
                isExpired -> Color(0xFF281116)
                else -> Color(0xFF16112C)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when {
                isSuccess -> Color(0xFF10B981)
                isExpired -> Color(0xFFEF4444).copy(alpha = 0.6f)
                else -> Color(0xFF3B2968)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Line 1: Number + Country | Status Pill + Elapsed + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.number,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            KopEngine2Manager.copyAndRemovePlus(context, item.number)
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = Color(0xFF271C46),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.country.uppercase(),
                            color = Color(0xFFC4B5FD),
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
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFFA78BFA), modifier = Modifier.size(14.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge
                    Surface(
                        color = when {
                            isSuccess -> Color(0xFF10B981).copy(alpha = 0.2f)
                            isExpired -> Color(0xFFEF4444).copy(alpha = 0.2f)
                            else -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSuccess -> Color(0xFF10B981)
                                isExpired -> Color(0xFFEF4444)
                                else -> Color(0xFFF59E0B)
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
                                isSuccess -> Color(0xFF34D399)
                                isExpired -> Color(0xFFF87171)
                                else -> Color(0xFFFBBF24)
                            },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            KopEngine2Manager.deleteNumber(item.id)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                    }
                }
            }

            // Subline: Operator & Creation Time Elapsed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${item.operator} • Range: ${item.range}",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = formatTimeElapsed(item.timestamp, nowTick),
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp
                )
            }

            // 15-MINUTE COUNTDOWN TIMER CARD (WHEN WAITING/PENDING)
            if (!isSuccess && !isExpired) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF191233),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
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
                                    text = "15-Min OTP Window:",
                                    color = Color(0xFFFFD600),
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
                            color = Color(0xFFA78BFA),
                            trackColor = Color(0xFF281E48)
                        )
                    }
                }
            } else if (isExpired && !isSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF381515).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ ১৫ মিনিট অতিবাহিত হওয়ায় নম্বরটির ওটিপি মেয়াদ শেষ হয়েছে।",
                            color = Color(0xFFFCA5A5),
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
                    color = Color(0xFF064E3B).copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF059669)),
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
                            Text(text = "OTP RECEIVED:", fontSize = 9.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
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
 * Tab 1: Live Console (Rich SS-style Unified Range Card, Search Bar, and Live Stream Terminal)
 */
@Composable
private fun Kop2LiveConsoleTab(
    state: com.example.service.VirtualNumbersUiState,
    consoleFilter: String,
    onFilterChange: (String) -> Unit,
    syncCountdown: Int,
    onManualRefresh: () -> Unit,
    context: Context
) {
    var rangeOptionExpanded by remember { mutableStateOf(false) }
    val currentSelectedRange = state.targetRange.ifEmpty { "447700XXX" }
    val matchingRangeItem = state.activeRanges.firstOrNull { it.range == currentSelectedRange }
        ?: state.activeRanges.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Unified Range Option Card (All ranges in one single compact option)
        Surface(
            color = Color(0xFF130E24),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF3B2968)),
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
                                .background(Color(0xFFA78BFA))
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
                                color = Color(0xFF281E48),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${matchingRangeItem.service} • ${matchingRangeItem.tag}",
                                    color = Color(0xFFC4B5FD),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF7C3AED).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${matchingRangeItem?.hits ?: 28} Hits",
                                color = Color(0xFFC4B5FD),
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
                                tint = Color(0xFFA78BFA),
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
                                tint = Color(0xFF9CA3AF),
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
                        HorizontalDivider(color = Color(0xFF3B2968), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "TOP HIT RANGES (LAST 30M) — ট্যাপ করে রেঞ্জ সিলেক্ট করুন:",
                            color = Color(0xFF9CA3AF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        state.activeRanges.forEach { rangeItem ->
                            val isCurrent = rangeItem.range == state.targetRange
                            Surface(
                                color = if (isCurrent) Color(0xFF7C3AED).copy(alpha = 0.25f) else Color(0xFF1E1738),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) Color(0xFFA78BFA) else Color(0xFF3B2968)
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
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isCurrent) Color(0xFF00E676) else Color(0xFFA78BFA))
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
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${rangeItem.hits} Hits",
                                            color = Color(0xFFC4B5FD),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Select",
                                            tint = Color(0xFFA78BFA),
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

        // 2. Search & Auto-Sync Bar (SS Style with 1-Second Auto Refresh)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = consoleFilter,
                onValueChange = onFilterChange,
                placeholder = { Text("Filter by number or code...", color = Color(0xFF6B7280), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFA78BFA),
                    unfocusedBorderColor = Color(0xFF3B2968),
                    focusedContainerColor = Color(0xFF0C0917),
                    unfocusedContainerColor = Color(0xFF0C0917)
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Surface(
                color = Color(0xFF16112C),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF3B2968)),
                modifier = Modifier
                    .height(52.dp)
                    .clickable { onManualRefresh() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = Color(0xFFC4B5FD),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Auto Sync: ${syncCountdown}s",
                        color = Color(0xFFDDD6FE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // STREAMING HEADER
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
                        .background(Color(0xFF00E676))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GLOBAL LIVE OTP STREAM (1s AUTO REFRESH)",
                    color = Color(0xFF00E676),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "${state.broadcastFeed.size} events",
                color = Color(0xFF9CA3AF),
                fontSize = 10.sp
            )
        }

        // 3. Global Live OTP SMS List (Styled identically to Zenex & Kop Engine)
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
            Surface(
                color = Color(0xFF130E24),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2C1F4D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "লাইভ এসএমএস লোডিং... (১ সেকেন্ডে অটো ফেচ হচ্ছে)",
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            filteredBroadcast.forEach { feed ->
                key(feed.id) {
                    Kop2BroadcastTerminalCard(feed = feed, context = context)
                }
            }
        }
    }
}

/**
 * Kop Engine 2 Live Broadcast Card (matching Zenex Terminal Card)
 */
@Composable
private fun Kop2BroadcastTerminalCard(
    feed: BroadcastFeedItem,
    context: Context
) {
    val timeStr = remember(feed.time) {
        try {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.US)
            sdf.format(Date(feed.time))
        } catch (_: Exception) {
            "12:00:00 PM"
        }
    }

    Surface(
        color = Color(0xFF100C22),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF2B1D4E)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val regex = Pattern.compile("\\b\\d{4,8}\\b")
                val matcher = regex.matcher(feed.otp)
                if (matcher.find()) {
                    val code = matcher.group()
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

                // Service Badge (FACEBOOK / INSTAGRAM / WHATSAPP / TELEGRAM)
                val upperService = feed.service.uppercase()
                val (badgeBg, badgeFg) = when {
                    upperService.contains("FACEBOOK") || upperService.contains("FB") -> Pair(Color(0xFF132B4A), Color(0xFF60A5FA))
                    upperService.contains("INSTAGRAM") || upperService.contains("IG") -> Pair(Color(0xFF421028), Color(0xFFF472B6))
                    upperService.contains("WHATSAPP") || upperService.contains("WA") -> Pair(Color(0xFF0A3322), Color(0xFF34D399))
                    upperService.contains("TELEGRAM") || upperService.contains("TG") -> Pair(Color(0xFF183852), Color(0xFF38BDF8))
                    else -> Pair(Color(0xFF231742), Color(0xFFA78BFA))
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
                        color = Color(0xFF1E1738),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.7.dp, Color(0xFF3B2968)),
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
                                color = Color(0xFFA78BFA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Range",
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                // Extracted OTP Highlight Pill
                val regex = Pattern.compile("\\b\\d{4,8}\\b")
                val matcher = regex.matcher(feed.otp)
                if (matcher.find()) {
                    val code = matcher.group()
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

            Spacer(modifier = Modifier.height(5.dp))

            // Line 3: SMS Payload preview
            Text(
                text = feed.otp,
                color = Color(0xFFB0BEC5),
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatPillKop2(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1F173B),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF3B2968)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = Color(0xFF9CA3AF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
