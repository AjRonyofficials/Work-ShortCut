package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
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
import java.util.regex.Pattern

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VirtualNumbersSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by VirtualNumberManager.state.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Number, 1: Live Console
    var filterTab by remember { mutableStateOf("ALL") } // "ALL", "SUCCESS", "PENDING", "FAILED"
    var consoleFilter by remember { mutableStateOf("") }
    var quantityDropdownExpanded by remember { mutableStateOf(false) }

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
        while (true) {
            delay(1000L)
            nowTick = System.currentTimeMillis()
        }
    }

    val total = state.provisionedNumbers.size
    val successCount = state.provisionedNumbers.count { it.status == "success" }
    val waitCount = state.provisionedNumbers.count { it.status == "pending" }
    val failedCount = state.provisionedNumbers.count { it.status == "failed" }
    val successRate = if (total > 0) (successCount.toFloat() / total.toFloat()) * 100f else 0f

    val filteredList = when (filterTab) {
        "SUCCESS" -> state.provisionedNumbers.filter { it.status == "success" }
        "PENDING" -> state.provisionedNumbers.filter { it.status == "pending" }
        "FAILED" -> state.provisionedNumbers.filter { it.status == "failed" }
        else -> state.provisionedNumbers
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D141C))
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

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
                            label = "WAIT",
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
                        Text(
                            text = "TARGET RANGE / CODE",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = state.targetRange,
                            onValueChange = { VirtualNumberManager.setTargetRange(it) },
                            placeholder = { Text("e.g. 237620XXX", color = Color(0xFF546E7A)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color(0xFF26415E),
                                focusedContainerColor = Color(0xFF0B141E),
                                unfocusedContainerColor = Color(0xFF0B141E)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Range Chips from Active Ranges
                        if (state.activeRanges.isNotEmpty()) {
                            Text(
                                text = "Active Suggestions:",
                                color = Color(0xFF78909C),
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
                                        color = if (state.targetRange == item.range) Color(0xFF0091EA) else Color(0xFF192C3D),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable {
                                            VirtualNumberManager.setTargetRange(item.range)
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
                                    text = if (state.requestCount > 1) "GET ${state.requestCount} NUMBERS" else "GET NUMBER",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
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

                    // Feed Filters: ALL, SUCCESS, PENDING, FAILED
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "SUCCESS", "PENDING", "FAILED").forEach { filter ->
                            val isSelected = filterTab == filter
                            Surface(
                                color = if (isSelected) Color(0xFF0091EA) else Color(0xFF142434),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { filterTab = filter }
                            ) {
                                Text(
                                    text = filter,
                                    color = if (isSelected) Color.White else Color(0xFF90A4AE),
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

                    var syncCountdown by remember { mutableIntStateOf(2) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1000L)
                            if (syncCountdown <= 1) {
                                syncCountdown = 2
                                VirtualNumberManager.fetchGlobalBroadcast()
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
                                syncCountdown = 2
                                VirtualNumberManager.fetchGlobalBroadcast()
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111D2A)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (item.status) {
                "success" -> Color(0xFF00E676).copy(alpha = 0.5f)
                "failed" -> Color(0xFFFF5252).copy(alpha = 0.4f)
                else -> Color(0xFF1E3A56)
            }
        ),
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
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                ClipboardHelper.copyToClipboard(context, item.number, "Phone Number")
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF1E2D3B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.country.uppercase(),
                                color = Color(0xFF90A4AE),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
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

                    Spacer(modifier = Modifier.height(4.dp))

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
                val remainingMs = (item.expiresAt - now).coerceAtLeast(0)
                val remainingStr = formatTimeRemaining(item.expiresAt, now)
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
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.US)
            sdf.format(Date(feed.time))
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

            // Line 2: Phone Number | [Range 📋] | ➔ | Monospace SMS with green <#>
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "➔",
                    color = Color(0xFF546E7A),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                val otpText = feed.otp
                if (otpText.startsWith("<#>")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = "<#>",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = otpText.removePrefix("<#>").trim(),
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = otpText,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
