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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.KopEngine2Manager
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KopEngine2SectionContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by KopEngine2Manager.state.collectAsState()
    val kop2OtpRate by KopEngine2Manager.kop2OtpRate.collectAsState()

    var quantityDropdownExpanded by remember { mutableStateOf(false) }
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Numbers & Active Feed, 1: Live Console

    LaunchedEffect(Unit) {
        KopEngine2Manager.init(context)
        KopEngine2Manager.fetchIncomingOtps(context, autoCopy = false)
    }

    val total = state.provisionedNumbers.size
    val successCount = state.provisionedNumbers.count { it.status == "SUCCESS" }
    val waitCount = state.provisionedNumbers.count { it.status == "WAIT" }
    val failedCount = state.provisionedNumbers.count { it.status == "FAILED" }
    val successRate = if (total > 0) (successCount.toFloat() / total) * 100f else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Card
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

                // 4 Stat Cards: TOTAL, SUCCESS, WAIT, FAILED
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPillKop2(label = "TOTAL", value = total.toString(), color = Color(0xFFE0E0E0), modifier = Modifier.weight(1f))
                    StatPillKop2(label = "SUCCESS", value = successCount.toString(), color = Color(0xFF34D399), modifier = Modifier.weight(1f))
                    StatPillKop2(label = "WAIT", value = waitCount.toString(), color = Color(0xFFFFD600), modifier = Modifier.weight(1f))
                    StatPillKop2(label = "FAILED", value = failedCount.toString(), color = Color(0xFFFF5252), modifier = Modifier.weight(1f))
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
            // GET NUMBER CARD
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

                        // Rate Pill
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
                        placeholder = { Text("e.g. 249, 1202, 447...", color = Color(0xFF6B7280), fontSize = 12.sp) },
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
                            text = "Active Suggestions:",
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

            // FEED HEADER
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
                        text = "KOP ENGINE 2 FEED (${state.provisionedNumbers.size})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
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

            // ACTIVE NUMBERS LIST
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
                            text = "উপরে 'GET NUMBER' চাপুন নতুন নাম্বার নিতে",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                state.provisionedNumbers.forEach { item ->
                    key(item.id) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.status == "SUCCESS") Color(0xFF0F1E19) else Color(0xFF16112C)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (item.status == "SUCCESS") Color(0xFF10B981) else Color(0xFF3B2968)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.number,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        IconButton(
                                            onClick = {
                                                KopEngine2Manager.copyAndRemovePlus(context, item.number)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFFA78BFA), modifier = Modifier.size(15.dp))
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = when (item.status) {
                                                "SUCCESS" -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                "WAIT" -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                                else -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                when (item.status) {
                                                    "SUCCESS" -> Color(0xFF10B981)
                                                    "WAIT" -> Color(0xFFF59E0B)
                                                    else -> Color(0xFFEF4444)
                                                }
                                            )
                                        ) {
                                            Text(
                                                text = item.status,
                                                color = when (item.status) {
                                                    "SUCCESS" -> Color(0xFF34D399)
                                                    "WAIT" -> Color(0xFFFBBF24)
                                                    else -> Color(0xFFF87171)
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

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

                                // OTP Display Box
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
                }
            }
        } else {
            // TAB 1: LIVE CONSOLE
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0917)),
                shape = RoundedCornerShape(14.dp),
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
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LIVE CDR & DLR CONSOLE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text("Realtime Stream", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.broadcastFeed.isEmpty()) {
                        Text(
                            text = "এখনো কোনো লাইভ ওটিপি বা সিডিআর ইভেন্ট নেই...",
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp
                        )
                    } else {
                        state.broadcastFeed.take(30).forEach { feed ->
                            Text(
                                text = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(feed.time))}] ${feed.service} - ${feed.number}: ${feed.otp}",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
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
