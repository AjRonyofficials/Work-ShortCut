package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ProvisionedNumber
import com.example.service.PremiumNumberV2Manager
import com.example.service.PremiumV2ConsoleItem
import com.example.util.AllCountriesProvider
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.delay

val ALLOCATION_SERVICES = listOf(
    "ANY (All Services)",
    "Facebook",
    "WhatsApp",
    "Apple",
    "Telegram",
    "Google",
    "Instagram",
    "TikTok",
    "Twitter/X",
    "Discord"
)

data class V2CountryPreset(
    val code: String,
    val name: String,
    val flag: String,
    val dialCode: String,
    val rateText: String
)

val ALLOCATION_COUNTRIES = listOf(
    V2CountryPreset("ZW", "Zimbabwe", "🇿🇼", "+263", "USDT 0.005"),
    V2CountryPreset("FR", "France", "🇫🇷", "+33", "USDT 0.006"),
    V2CountryPreset("SD", "Sudan", "🇸🇩", "+249", "USDT 0.005"),
    V2CountryPreset("ZM", "Zambia", "🇿🇲", "+260", "USDT 0.005"),
    V2CountryPreset("IT", "Italy", "🇮🇹", "+39", "USDT 0.005"),
    V2CountryPreset("GB", "United Kingdom", "🇬🇧", "+44", "USDT 0.006"),
    V2CountryPreset("US", "United States", "🇺🇸", "+1", "USDT 0.005"),
    V2CountryPreset("ANY", "ANY (All Countries)", "🌐", "", "LIVE POOL")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumNumberV2SectionContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by PremiumNumberV2Manager.state.collectAsState()
    val otpRate by PremiumNumberV2Manager.otpRate.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Number (Operator Desk), 1: Live Console / Live OTP
    var filterTab by remember { mutableStateOf("ALL") } // "ALL", "WAITING", "RECEIVED", "EXPIRED"

    // Dropdown expanded states for Allocation Desk
    var serviceDropdownExpanded by remember { mutableStateOf(false) }
    var countryDropdownExpanded by remember { mutableStateOf(false) }
    var quantityDropdownExpanded by remember { mutableStateOf(false) }

    // Live console filter chip state
    var selectedConsoleCategory by remember { mutableStateOf("ALL") } // "ALL", "Facebook", "WhatsApp", "Instagram", "Telegram", "Other"
    var liveFeedSearchQuery by remember { mutableStateOf("") }

    // Dynamically derive ONLY countries present in Live Pool
    val livePoolCountries = remember(state.liveConsoleFeed) {
        val list = mutableListOf<V2CountryPreset>()
        val seen = mutableSetOf<String>()
        list.add(V2CountryPreset("ANY", "ANY (All Live Pool)", "🌐", "", "POOL"))
        for (item in state.liveConsoleFeed) {
            val iso = item.iso.uppercase()
            if (iso.isNotBlank() && !seen.contains(iso)) {
                seen.add(iso)
                val info = com.example.util.AllCountriesProvider.findByCode(iso)
                    ?: com.example.util.AllCountriesProvider.findByNameOrCode(item.country)
                val dial = info?.dialCode ?: if (item.rangePrefix.isNotBlank()) "+${item.rangePrefix}" else ""
                list.add(
                    V2CountryPreset(
                        code = iso,
                        name = item.country.ifBlank { info?.name ?: iso },
                        flag = item.flag.ifBlank { info?.flag ?: "🌐" },
                        dialCode = dial,
                        rateText = "LIVE"
                    )
                )
            }
        }
        if (list.size <= 1) {
            listOf(
                V2CountryPreset("ANY", "ANY (All Live Pool)", "🌐", "", "POOL"),
                V2CountryPreset("ZW", "Zimbabwe", "🇿🇼", "+263", "LIVE"),
                V2CountryPreset("SD", "Sudan", "🇸🇩", "+249", "LIVE"),
                V2CountryPreset("FR", "France", "🇫🇷", "+33", "LIVE"),
                V2CountryPreset("ZM", "Zambia", "🇿🇲", "+260", "LIVE")
            )
        } else list
    }

    // Dynamic Live Prefixes available in feed
    val livePrefixes = remember(state.liveConsoleFeed) {
        state.liveConsoleFeed
            .map { it.rangePrefix }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }

    // Auto-select valid Live Pool country if current one is invalid
    LaunchedEffect(livePoolCountries) {
        val currentCode = state.selectedCountryCode
        if (currentCode != "ANY" && livePoolCountries.none { it.code.equals(currentCode, ignoreCase = true) }) {
            val firstReal = livePoolCountries.firstOrNull { it.code != "ANY" } ?: livePoolCountries.firstOrNull()
            if (firstReal != null) {
                PremiumNumberV2Manager.setSelectedCountry(firstReal.code)
            }
        }
    }

    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        PremiumNumberV2Manager.init(context)
        while (true) {
            delay(1000L)
            nowTick = System.currentTimeMillis()
        }
    }

    val total = state.activeNumbers.size
    val receivedCount = state.activeNumbers.count { it.status == "success" || !it.otpCode.isNullOrEmpty() }
    val waitingCount = state.activeNumbers.count {
        it.otpCode.isNullOrEmpty() && (it.status == "pending" || it.status == "wait") && nowTick < it.expiresAt
    }
    val expiredCount = state.activeNumbers.count {
        it.status == "failed" || it.status == "expired" || it.status == "cancelled" || (it.otpCode.isNullOrEmpty() && nowTick >= it.expiresAt)
    }

    val filteredList = when (filterTab) {
        "RECEIVED" -> state.activeNumbers.filter { it.status == "success" || !it.otpCode.isNullOrEmpty() }
        "WAITING" -> state.activeNumbers.filter { it.otpCode.isNullOrEmpty() && (it.status == "pending" || it.status == "wait") && nowTick < it.expiresAt }
        "EXPIRED" -> state.activeNumbers.filter { it.status == "failed" || it.status == "expired" || it.status == "cancelled" || (it.otpCode.isNullOrEmpty() && nowTick >= it.expiresAt) }
        else -> state.activeNumbers
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // TOP PLATFORM BAR (SL VIRTUAL NUMBER USER • REAL-TIME • BALANCE)
        Surface(
            color = Color(0xFF09121D),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF162A3E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⚡", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SL VIRTUAL NUMBER",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "USER",
                                    color = Color(0xFF34D399),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Carrier Live Pool Engine",
                            color = Color(0xFF64748B),
                            fontSize = 9.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    // API Health Connection Pill (Live Pool status)
                    Surface(
                        color = if (state.isApiConnected) Color(0xFF064E3B).copy(alpha = 0.45f) else Color(0xFF7F1D1D).copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (state.isApiConnected) Color(0xFF059669) else Color(0xFFEF4444))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isApiConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (state.isApiConnected) "LIVE POOL (3s)" else "OFFLINE",
                                color = if (state.isApiConnected) Color(0xFF34D399) else Color(0xFFF87171),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sound Alert Toggle Button
                    Surface(
                        color = Color(0xFF0E1A29),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (state.isSoundEnabled) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color(0xFF1E2D40)),
                        modifier = Modifier.clickable {
                            val next = !state.isSoundEnabled
                            PremiumNumberV2Manager.setSoundEnabled(next)
                            Toast.makeText(context, if (next) "🔔 ওটিপি সাউন্ড অ্যালার্ট চালু" else "🔕 ওটিপি সাউন্ড অ্যালার্ট বন্ধ", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Box(modifier = Modifier.padding(5.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                if (state.isSoundEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = "OTP Sound Alert",
                                tint = if (state.isSoundEnabled) Color(0xFF00E5FF) else Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // User OTP Rate
                    Surface(
                        color = Color(0xFF0F2537),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rate: ", color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format(Locale.US, "%.2f", otpRate)}৳",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // SUB-TABS: [GET NUMBER (Operator Desk)] & [LIVE CONSOLE (Live OTP)]
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color(0xFF0B1420),
            contentColor = Color(0xFF00E5FF),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = Color(0xFF00E5FF),
                    height = 2.5.dp
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF162A3E), RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📱", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GET NUMBER (DESK)",
                            fontSize = 11.sp,
                            fontWeight = if (selectedSubTab == 0) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selectedSubTab == 0) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        )
                    }
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = {
                    selectedSubTab = 1
                    PremiumNumberV2Manager.refreshLiveConsole()
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📡", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE OTP STREAM",
                            fontSize = 11.sp,
                            fontWeight = if (selectedSubTab == 1) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selectedSubTab == 1) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        )
                    }
                }
            )
        }

        // ==================== TAB 0: GET NUMBER (OPERATOR NUMBER ALLOCATION DESK) ====================
        if (selectedSubTab == 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09121D)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.2.dp, Color(0xFF162D42)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header Desk Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📡", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OPERATOR NUMBER ALLOCATION DESK",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "LIVE POOL",
                                color = Color(0xFF00E5FF),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // SERVICE, COUNTRY (LIVE POOL ONLY), COMPACT QTY, & GET NUMBER ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. SERVICE Dropdown
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text("SERVICE", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))

                            Box {
                                Surface(
                                    color = Color(0xFF0B1624),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { serviceDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = state.selectedService,
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = serviceDropdownExpanded,
                                    onDismissRequest = { serviceDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF091422))
                                ) {
                                    ALLOCATION_SERVICES.forEach { srv ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = srv,
                                                    color = if (state.selectedService == srv) Color(0xFF00E5FF) else Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (state.selectedService == srv) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                PremiumNumberV2Manager.setSelectedService(srv)
                                                serviceDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 2. COUNTRY Dropdown (ONLY COUNTRIES IN LIVE POOL)
                        Column(modifier = Modifier.weight(1.35f)) {
                            Text("COUNTRY (POOL)", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))

                            Box {
                                Surface(
                                    color = Color(0xFF0B1624),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { countryDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Text(text = state.selectedCountryFlag, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = state.selectedCountryName,
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = countryDropdownExpanded,
                                    onDismissRequest = { countryDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF091422))
                                ) {
                                    livePoolCountries.forEach { cp ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(text = cp.flag, fontSize = 13.sp)
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(
                                                            text = cp.name,
                                                            color = if (state.selectedCountryCode.equals(cp.code, ignoreCase = true)) Color(0xFF00E5FF) else Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (state.selectedCountryCode.equals(cp.code, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (cp.dialCode.isNotBlank()) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(text = cp.dialCode, color = Color(0xFF64748B), fontSize = 9.5.sp)
                                                        }
                                                    }
                                                    Surface(
                                                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(3.dp)
                                                    ) {
                                                        Text(
                                                            text = cp.rateText,
                                                            color = Color(0xFF38BDF8),
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                PremiumNumberV2Manager.setSelectedCountry(cp.code)
                                                countryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Compact Qty Button (Styled small like other panels)
                        Column {
                            Text("QTY", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))

                            Box {
                                Surface(
                                    color = Color(0xFF0F1A28),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                                    modifier = Modifier.clickable { quantityDropdownExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Qty: ${state.requestCount}",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = quantityDropdownExpanded,
                                    onDismissRequest = { quantityDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF091422))
                                ) {
                                    listOf(1, 2, 3, 5, 10).forEach { q ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Qty: $q",
                                                    color = if (state.requestCount == q) Color(0xFF00E5FF) else Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (state.requestCount == q) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                PremiumNumberV2Manager.setRequestCount(q)
                                                quantityDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 4. GET NUMBER BUTTON
                        Column(modifier = Modifier.padding(top = 15.dp)) {
                            Button(
                                onClick = { PremiumNumberV2Manager.acquireNumbers(context) },
                                enabled = !state.isLoading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF),
                                    contentColor = Color(0xFF041B26),
                                    disabledContainerColor = Color(0xFF1E3A56),
                                    disabledContentColor = Color(0xFF64748B)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("btn_get_number_v2")
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF041B26),
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Get",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // SPATIOUS LIVE PREFIX SECTION (BEAUTIFUL & NEVER SQUEEZED)
                    Surface(
                        color = Color(0xFF070E18),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, Color(0xFF162D42)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎯", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE RANGE PREFIX (ঐচ্ছিক)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (state.rangePrefix.isNotBlank()) {
                                    Text(
                                        text = "মুছুন ✕",
                                        color = Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { PremiumNumberV2Manager.setRangePrefix("") }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = state.rangePrefix,
                                onValueChange = { PremiumNumberV2Manager.setRangePrefix(it) },
                                placeholder = { Text("Prefix লিখুন যেমন: 263, 249, 33 (বা নিচের থেকে ট্যাপ করুন)", fontSize = 10.sp, color = Color(0xFF64748B)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color(0xFF1E3A56),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            )

                            // Quick select chips for live prefixes from the pool
                            if (livePrefixes.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Live Pool Prefixes:", color = Color(0xFF64748B), fontSize = 8.5.sp)
                                    livePrefixes.forEach { pfx ->
                                        val isSel = state.rangePrefix == pfx
                                        Surface(
                                            color = if (isSel) Color(0xFF00E5FF) else Color(0xFF0F1E2E),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(0.6.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF1E3A56)),
                                            modifier = Modifier.clickable { PremiumNumberV2Manager.setRangePrefix(pfx) }
                                        ) {
                                            Text(
                                                text = "+$pfx",
                                                color = if (isSel) Color(0xFF041B26) else Color(0xFF38BDF8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // INDICATORS ROW (Screenshot 1: 15-min hold, Live polling 3s, Auto-copied, National Format)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🕒", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("15-min exclusive hold", color = Color(0xFF94A3B8), fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("((o))", fontSize = 9.sp, color = Color(0xFF00E5FF))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Live polling 3s", color = Color(0xFF94A3B8), fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Auto-copied", color = Color(0xFF94A3B8), fontSize = 9.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌐 National:", color = Color(0xFF64748B), fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Surface(
                                color = if (state.nationalFormat) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.clickable { PremiumNumberV2Manager.setNationalFormat(!state.nationalFormat) }
                            ) {
                                Text(
                                    text = if (state.nationalFormat) "ON" else "OFF",
                                    color = if (state.nationalFormat) Color.Black else Color(0xFF94A3B8),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ACTIVE VIRTUAL NUMBERS & LIVE OTP MONITOR (Screenshot 1)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09121D)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF162D42)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("((o))", fontSize = 11.sp, color = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTIVE VIRTUAL NUMBERS & LIVE OTP MONITOR (${state.activeNumbers.size})",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Realtime Stream • Instant Wallet Credit",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp
                        )
                    }

                    // Filter chips row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                "ALL" to "সব ($total)",
                                "WAITING" to "পেন্ডিং ($waitingCount)",
                                "RECEIVED" to "ওটিপি ($receivedCount)",
                                "EXPIRED" to "রিটার্ন ($expiredCount)"
                            ).forEach { (fKey, fLabel) ->
                                val isSelected = filterTab == fKey
                                Surface(
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF0B1420),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E3A56)),
                                    modifier = Modifier.clickable { filterTab = fKey }
                                ) {
                                    Text(
                                        text = fLabel,
                                        color = if (isSelected) Color(0xFF041B26) else Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Copy All Numbers Button
                            if (filteredList.isNotEmpty()) {
                                Surface(
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable {
                                        val allText = filteredList.joinToString("\n") { it.number }
                                        ClipboardHelper.copyToClipboard(context, allText, "Active Numbers")
                                        Toast.makeText(context, "✓ ${filteredList.size} টি নম্বর ক্লিপবোর্ডে কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Copy All", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Smart Clear Expired / Completed Numbers Button
                            if (state.activeNumbers.any { it.status == "expired" || it.status == "cancelled" || !it.otpCode.isNullOrEmpty() || nowTick >= it.expiresAt }) {
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable {
                                        PremiumNumberV2Manager.clearExpiredOrCompletedNumbers()
                                        Toast.makeText(context, "✓ এক্সপায়ার্ড নম্বরগুলো ক্লিয়ার হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("এক্সপায়ার্ড", color = Color(0xFFF59E0B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Clear All Button
                            if (state.activeNumbers.isNotEmpty()) {
                                Surface(
                                    color = Color(0xFF7F1D1D).copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        PremiumNumberV2Manager.clearAllNumbers()
                                        Toast.makeText(context, "সব নম্বর ক্লিয়ার হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("মুছুন", color = Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Empty or numbers list (Screenshot 1 text)
                    if (filteredList.isEmpty()) {
                        Surface(
                            color = Color(0xFF0B1420),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF162A3E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No active virtual numbers allocated right now.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Click \"Get Number\" above to allocate a number and earn revenue on incoming OTPs.",
                                    color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                                    fontSize = 10.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        filteredList.forEach { item ->
                            PremiumV2NumberCard(
                                item = item,
                                nowTick = nowTick,
                                onCopy = {
                                    ClipboardHelper.copyToClipboard(context, item.number, "Phone Number")
                                    Toast.makeText(context, "✓ নম্বর কপি হয়েছে: ${item.number}", Toast.LENGTH_SHORT).show()
                                },
                                onCopyOtp = {
                                    if (!item.otpCode.isNullOrEmpty()) {
                                        ClipboardHelper.copyToClipboard(context, item.otpCode, "OTP")
                                        Toast.makeText(context, "✓ ওটিপি কপি হয়েছে: ${item.otpCode}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onReturn = {
                                    val rId = item.rentalId ?: item.id
                                    PremiumNumberV2Manager.cancelRental(rId, context, isAutoReturn = false)
                                },
                                onDelete = {
                                    PremiumNumberV2Manager.removeNumber(item.id)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ==================== TAB 1: LIVE CONSOLE / LIVE OTP (HUBU SAME AS SCREENSHOT 2) ====================
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header: SL Virtual Number Bot • Live Traffic Stream
                Surface(
                    color = Color(0xFF09121D),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF162D42)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📡", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "SL Virtual Number Bot",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Live Traffic Stream",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF064E3B).copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "REAL-TIME",
                                        color = Color(0xFF34D399),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Hero: Global Signal Network
                        Surface(
                            color = Color(0xFF0B1726),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF1B3550)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🌐", fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Global Signal Network",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Live OTP relay across 35+ countries",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = Color(0xFF064E3B).copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            color = Color(0xFF34D399),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text("((o)) ${state.liveConsoleFeed.size} events", color = Color(0xFF38BDF8), fontSize = 9.sp)
                                }
                            }
                        }

                        val activeCountriesInFeed = remember(state.liveConsoleFeed) {
                            state.liveConsoleFeed.map { it.flag to it.country }.distinctBy { it.second }
                        }
                        val activeServicesInFeed = remember(state.liveConsoleFeed) {
                            state.liveConsoleFeed.map { it.service }.distinct()
                        }

                        // LIVE POOL ACTIVE REAL-TIME METRICS (UPDATES DYNAMICALLY)
                        Surface(
                            color = Color(0xFF0B1726),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.9.dp, Color(0xFF1B3550)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                .background(Color(0xFF00E5FF))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "LIVE POOL REAL-TIME STATUS",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "((o)) ${state.liveConsoleFeed.size} LIVE EVENTS",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Active Countries Box
                                    Surface(
                                        color = Color(0xFF070E18),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFF162D42)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("ACTIVE COUNTRIES", color = Color(0xFF94A3B8), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                Text("${activeCountriesInFeed.size}", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                activeCountriesInFeed.forEach { (flag, cName) ->
                                                    Surface(
                                                        color = Color(0xFF0F1E2E),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = BorderStroke(0.6.dp, Color(0xFF1E3A56))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(flag, fontSize = 9.5.sp)
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(cName, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Medium)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Active Services Box
                                    Surface(
                                        color = Color(0xFF070E18),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFF162D42)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("ACTIVE SERVICES", color = Color(0xFF94A3B8), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                Text("${activeServicesInFeed.size}", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                activeServicesInFeed.forEach { srv ->
                                                    Surface(
                                                        color = Color(0xFF063321),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = BorderStroke(0.6.dp, Color(0xFF059669).copy(alpha = 0.5f))
                                                    ) {
                                                        Text(
                                                            text = srv,
                                                            color = Color(0xFF86EFAC),
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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

                // LIVE OTP STREAMING FEED
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF09121D)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF162D42)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💬", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE OTP",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "streaming",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.clickable {
                                        PremiumNumberV2Manager.refreshLiveConsole()
                                        Toast.makeText(context, "লাইভ ওটিপি ফিড রিফ্রেশ হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.padding(3.dp).size(14.dp)
                                    )
                                }
                            }
                        }

                        // Search Input for Live OTP Feed
                        OutlinedTextField(
                            value = liveFeedSearchQuery,
                            onValueChange = { liveFeedSearchQuery = it },
                            placeholder = { Text("সার্ভিস, দেশ বা নম্বর খুঁজুন...", color = Color(0xFF64748B), fontSize = 11.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                            },
                            trailingIcon = {
                                if (liveFeedSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { liveFeedSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF1E3A56),
                                focusedContainerColor = Color(0xFF070E18),
                                unfocusedContainerColor = Color(0xFF070E18)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )

                        // Scrollable Category Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf("ALL", "WhatsApp", "Telegram", "Facebook", "Apple", "Google", "Instagram", "TikTok", "Other").forEach { cat ->
                                val isSel = selectedConsoleCategory.equals(cat, ignoreCase = true)
                                Surface(
                                    color = if (isSel) Color(0xFF00E5FF) else Color(0xFF0E1A29),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF1E3A56)),
                                    modifier = Modifier.clickable { selectedConsoleCategory = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSel) Color(0xFF041B26) else Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        val feedItems = remember(state.liveConsoleFeed, selectedConsoleCategory, liveFeedSearchQuery) {
                            var list = if (selectedConsoleCategory == "ALL") state.liveConsoleFeed
                            else if (selectedConsoleCategory == "Other") {
                                state.liveConsoleFeed.filter {
                                    !it.service.contains("facebook", ignoreCase = true) &&
                                    !it.service.contains("whatsapp", ignoreCase = true) &&
                                    !it.service.contains("instagram", ignoreCase = true) &&
                                    !it.service.contains("telegram", ignoreCase = true) &&
                                    !it.service.contains("apple", ignoreCase = true) &&
                                    !it.service.contains("google", ignoreCase = true)
                                }
                            } else {
                                state.liveConsoleFeed.filter { it.service.contains(selectedConsoleCategory, ignoreCase = true) }
                            }

                            if (liveFeedSearchQuery.isNotBlank()) {
                                val q = liveFeedSearchQuery.trim().lowercase()
                                list = list.filter {
                                    it.country.lowercase().contains(q) ||
                                    it.service.lowercase().contains(q) ||
                                    it.maskedNumber.contains(q) ||
                                    it.rangePrefix.contains(q) ||
                                    it.rawSnippet.lowercase().contains(q)
                                }
                            }
                            list
                        }

                        if (feedItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("কোনো লাইভ ওটিপি ইভেন্ট পাওয়া যায়নি", color = Color(0xFF64748B), fontSize = 11.sp)
                            }
                        } else {
                            feedItems.forEach { item ->
                                LiveOtpStreamCard(
                                    item = item,
                                    onGetNumber = {
                                        PremiumNumberV2Manager.rentDirectFromConsoleItem(item, context)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Active Number Card with 15-Minute Countdown Timer & Return Button
 */
@Composable
fun PremiumV2NumberCard(
    item: ProvisionedNumber,
    nowTick: Long,
    onCopy: () -> Unit,
    onCopyOtp: () -> Unit,
    onReturn: () -> Unit,
    onDelete: () -> Unit
) {
    val hasOtp = !item.otpCode.isNullOrEmpty()
    val isExpired = !hasOtp && nowTick >= item.expiresAt
    val isCancelled = item.status == "cancelled"

    val msRemaining = (item.expiresAt - nowTick).coerceAtLeast(0L)
    val mins = msRemaining / 60000L
    val secs = (msRemaining % 60000L) / 1000L
    val timeLabel = String.format(Locale.US, "%02d:%02d", mins, secs)

    val cardBorder = when {
        hasOtp -> BorderStroke(1.2.dp, Color(0xFF00E676))
        isExpired || isCancelled -> BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
    }

    val cardBg = when {
        hasOtp -> Color(0xFF062319)
        isExpired || isCancelled -> Color(0xFF1E1417)
        else -> Color(0xFF0B1726)
    }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(12.dp),
        border = cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Flag, Country, Service, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.flag ?: "🌐", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.country,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.range ?: "ALL",
                            color = Color(0xFF00E5FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Countdown Timer Pill (15-min limit)
                Surface(
                    color = when {
                        hasOtp -> Color(0xFF00E676).copy(alpha = 0.2f)
                        isExpired || isCancelled -> Color(0xFFEF4444).copy(alpha = 0.2f)
                        else -> Color(0xFF0284C7).copy(alpha = 0.25f)
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        0.8.dp,
                        when {
                            hasOtp -> Color(0xFF00E676)
                            isExpired || isCancelled -> Color(0xFFEF4444)
                            else -> Color(0xFF00E5FF)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (hasOtp) Color(0xFF00E676) else if (isExpired || isCancelled) Color(0xFFEF4444) else Color(0xFF00E5FF),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (hasOtp) "OTP RECEIVED" else if (isExpired) "EXPIRED" else if (isCancelled) "RETURNED" else timeLabel,
                            color = if (hasOtp) Color(0xFF00E676) else if (isExpired || isCancelled) Color(0xFFEF4444) else Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Phone Number Row with Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.number,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        modifier = Modifier.clickable { onCopy() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("নম্বর কপি", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!hasOtp && !isExpired && !isCancelled) {
                        Surface(
                            color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            modifier = Modifier.clickable { onReturn() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ফেরত দিন", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onDelete() }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.padding(5.dp).size(13.dp))
                        }
                    }
                }
            }

            // OTP Received Card
            if (hasOtp) {
                Surface(
                    color = Color(0xFF02381F),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopyOtp() }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔐 ওটিপি কোড:", color = Color(0xFF86EFAC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.otpCode ?: "",
                                    color = Color(0xFF00E676),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }

                            Surface(
                                color = Color(0xFF00E676),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "কপি করুন ✓",
                                    color = Color.Black,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (!item.otpMessage.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.otpMessage,
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else if (!isExpired && !isCancelled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ওটিপির জন্য অপেক্ষা করা হচ্ছে... (১৫ মিনিটে না আসলে অটো ফেরত হবে)",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Live OTP Stream Feed Item Card (Hubu same as Screenshot 2)
 */
@Composable
fun LiveOtpStreamCard(
    item: PremiumV2ConsoleItem,
    onGetNumber: () -> Unit
) {
    Surface(
        color = Color(0xFF0B1726),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.9.dp, Color(0xFF162D42)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Top Row: [Flag ZW]  ● FACEBOOK  +2637••••0889    [ ⚙️ GET NUMBER ]  just now
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Country Badge: Flag + ISO
                    Surface(
                        color = Color(0xFF132237),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.6.dp, Color(0xFF1E3A56))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = item.flag, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = item.iso,
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Service Pill: ● FACEBOOK
                    val srvColor = when {
                        item.service.contains("facebook", ignoreCase = true) -> Color(0xFF38BDF8)
                        item.service.contains("apple", ignoreCase = true) -> Color(0xFF00E5FF)
                        item.service.contains("whatsapp", ignoreCase = true) -> Color(0xFF10B981)
                        else -> Color(0xFFA78BFA)
                    }
                    Surface(
                        color = srvColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(srvColor)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = item.service,
                                color = srvColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Masked Phone Number
                    Text(
                        text = item.maskedNumber,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Right: [ ⚙️ GET NUMBER ] Button & Time
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        modifier = Modifier.clickable { onGetNumber() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", fontSize = 9.sp, color = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "GET NUMBER",
                                color = Color(0xFF00E5FF),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = item.timeAgo,
                        color = Color(0xFF64748B),
                        fontSize = 9.sp
                    )
                }
            }

            // Bottom Snippet Row: 💬 <#> ****** is your Facebook code ...
            if (item.rawSnippet.isNotBlank()) {
                Surface(
                    color = Color(0xFF070E18),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💬", fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.rawSnippet,
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
