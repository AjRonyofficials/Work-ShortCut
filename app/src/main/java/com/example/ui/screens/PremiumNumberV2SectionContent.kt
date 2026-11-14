package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import java.util.Locale
import com.example.service.AuthManager
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.AlertDialog
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
    "💬 WhatsApp",
    "📸 Instagram",
    "🔥 OTP Work",
    "Telegram",
    "Apple",
    "Google",
    "TikTok",
    "Twitter/X"
)

data class V2CountryPreset(
    val code: String,
    val name: String,
    val flag: String,
    val dialCode: String,
    val rateText: String
)

val ALLOCATION_COUNTRIES = listOf(
    V2CountryPreset("IT", "Italy", "🇮🇹", "+39", "$0.001"),
    V2CountryPreset("ET", "Ethiopia", "🇪🇹", "+251", "$0.003"),
    V2CountryPreset("HT", "Haiti", "🇭🇹", "+509", "$0.005"),
    V2CountryPreset("SD", "Sudan", "🇸🇩", "+249", "$0.005"),
    V2CountryPreset("NP", "Nepal", "🇳🇵", "+977", "$0.003"),
    V2CountryPreset("ZM", "Zambia", "🇿🇲", "+260", "$0.005"),
    V2CountryPreset("SY", "Syria", "🇸🇾", "+963", "$0.005"),
    V2CountryPreset("KH", "Cambodia", "🇰🇭", "+855", "$0.005"),
    V2CountryPreset("TZ", "Tanzania", "🇹🇿", "+255", "$0.005"),
    V2CountryPreset("ZW", "Zimbabwe", "🇿🇼", "+263", "$0.005"),
    V2CountryPreset("ANY", "ANY (All Live Pool)", "🌐", "", "POOL")
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

    // Dynamically derive ONLY real active countries from API & Live Pool
    val livePoolCountries = remember(state.liveConsoleFeed, state.activeApiCountries, state.countryRates) {
        val list = mutableListOf<V2CountryPreset>()
        val seen = mutableSetOf<String>()
        val baseRate = PremiumNumberV2Manager.getRateForCountry("ANY")
        list.add(V2CountryPreset("ANY", "ANY (All Live Pool)", "🌐", "", String.format(Locale.US, "%.2f৳", baseRate)))

        // 1. Add active countries with Admin Panel configured rates in BDT
        for (c in state.activeApiCountries) {
            val iso = c.code.uppercase()
            if (!seen.contains(iso)) {
                seen.add(iso)
                val cRate = PremiumNumberV2Manager.getRateForCountry(iso)
                list.add(
                    V2CountryPreset(
                        code = iso,
                        name = c.name,
                        flag = c.flag,
                        dialCode = if (c.prefix.isNotBlank()) "+${c.prefix}" else "",
                        rateText = String.format(Locale.US, "%.2f৳", cRate)
                    )
                )
            }
        }

        // 2. Also incorporate active countries from the live console feed with Admin Panel rates in BDT
        for (item in state.liveConsoleFeed) {
            val iso = item.iso.uppercase()
            if (iso.isNotBlank() && !seen.contains(iso)) {
                seen.add(iso)
                val info = com.example.util.AllCountriesProvider.findByCode(iso)
                    ?: com.example.util.AllCountriesProvider.findByNameOrCode(item.country)
                val dial = info?.dialCode ?: if (item.rangePrefix.isNotBlank()) "+${item.rangePrefix}" else ""
                val cRate = PremiumNumberV2Manager.getRateForCountry(iso)
                list.add(
                    V2CountryPreset(
                        code = iso,
                        name = item.country.ifBlank { info?.name ?: iso },
                        flag = item.flag.ifBlank { info?.flag ?: "🌐" },
                        dialCode = dial,
                        rateText = String.format(Locale.US, "%.2f৳", cRate)
                    )
                )
            }
        }
        if (list.size <= 1) {
            ALLOCATION_COUNTRIES
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
            delay(4000L)
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

    val filteredList = remember(state.activeNumbers, filterTab, nowTick) {
        val base = when (filterTab) {
            "RECEIVED" -> state.activeNumbers.filter { it.status == "success" || !it.otpCode.isNullOrEmpty() }
            "WAITING" -> state.activeNumbers.filter { it.otpCode.isNullOrEmpty() && (it.status == "pending" || it.status == "wait") && nowTick < it.expiresAt }
            "EXPIRED" -> state.activeNumbers.filter { it.status == "failed" || it.status == "expired" || it.status == "cancelled" || (it.otpCode.isNullOrEmpty() && nowTick >= it.expiresAt) }
            else -> state.activeNumbers
        }
        base.sortedByDescending { it.timestamp }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // TOP LUXURY HEADER CARD (PREMIUM NUMBER V2 • ROYAL GOLD / AMBER THEME)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131F)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
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
                            color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PREMIUM NUMBER V2",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(5.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "V2 PRO",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Carrier Live Pool Engine • Instant OTP",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        // Live Pool Active Badge
                        Surface(
                            color = Color(0xFF064E3B).copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
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
                                    text = "LIVE POOL",
                                    color = Color(0xFF34D399),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Sound Alert Toggle Button
                        Surface(
                            color = Color(0xFF151E2E),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (state.isSoundEnabled) Color(0xFFF59E0B).copy(alpha = 0.65f) else Color(0xFF1E2D40)),
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
                                    tint = if (state.isSoundEnabled) Color(0xFFFBBF24) else Color(0xFF64748B),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Reset 24h OTP & Active Numbers Button with Yes/No Confirmation
                        var showV2ResetConfirmDialog by remember { mutableStateOf(false) }

                        if (showV2ResetConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showV2ResetConfirmDialog = false },
                                containerColor = Color(0xFF0E1A29),
                                titleContentColor = Color.White,
                                textContentColor = Color(0xFFCBD5E1),
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("OTP ও নম্বর রিসেট করবেন?", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                },
                                text = {
                                    Text(
                                        "আপনি কি নিশ্চিত যে Premium Number V2-এর বর্তমান অ্যাক্টিভ নম্বর এবং আজকের ২৪ ঘণ্টার OTP লিস্ট রিসেট (Reset) করতে চান?",
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showV2ResetConfirmDialog = false
                                            PremiumNumberV2Manager.resetTodayCycle(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Yes (হ্যাঁ)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(
                                        onClick = { showV2ResetConfirmDialog = false },
                                        border = BorderStroke(1.dp, Color(0xFF334155)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("No (না)", color = Color(0xFFCBD5E1), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            )
                        }

                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
                            modifier = Modifier.clickable { showV2ResetConfirmDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔄 Reset",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5 STAT PILLS ROW (24H TOTAL, SUCCESS OTP, WAITING, FAILED, OTP RATE)
                val effectiveV2TodayOtp = maxOf(state.todayOtpCount, receivedCount)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        color = Color(0xFF141D2B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF26354D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TOTAL (24H)", color = Color(0xFF94A3B8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("$total", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SUCCESS OTP", color = Color(0xFF34D399), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("$effectiveV2TodayOtp", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("WAITING", color = Color(0xFFFBBF24), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("$waitingCount", color = Color(0xFFFBBF24), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("FAILED", color = Color(0xFFF87171), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("$expiredCount", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("OTP RATE", color = Color(0xFFFDE68A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.US, "%.2f", otpRate)}৳", color = Color(0xFFFBBF24), fontSize = 12.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // SUB-TABS: [GET NUMBER (Operator Desk)] & [LIVE CONSOLE (Live OTP)]
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color(0xFF0E131F),
            contentColor = Color(0xFFFBBF24),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = Color(0xFFF59E0B),
                    height = 2.5.dp
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📱", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GET NUMBER (V2 DESK)",
                            fontSize = 11.sp,
                            fontWeight = if (selectedSubTab == 0) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selectedSubTab == 0) Color(0xFFFBBF24) else Color(0xFF94A3B8)
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
                            text = "LIVE V2 OTP STREAM",
                            fontSize = 11.sp,
                            fontWeight = if (selectedSubTab == 1) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selectedSubTab == 1) Color(0xFFFBBF24) else Color(0xFF94A3B8)
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
                                    (1..10).forEach { q ->
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

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = Color(0xFF162D42), thickness = 0.8.dp)

                    // LIVE POOL ACTIVE COUNTRIES & ADMIN RATES (INTEGRATED DIRECTLY IN DESK)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌐", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "লাইভ পুল ও অ্যাডমিন রেট (BDT):",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Selected country rate badge
                        val currentRate = PremiumNumberV2Manager.getRateForCountry(state.selectedCountryCode)
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "রেট: ${state.selectedCountryFlag} ${String.format(Locale.US, "%.2f৳", currentRate)} (BDT)",
                                color = Color(0xFF00E676),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Horizontal chips row for live pool countries with their Admin Panel set rate in BDT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        livePoolCountries.forEach { c ->
                            val isSelected = state.selectedCountryCode.equals(c.code, ignoreCase = true)
                            val cRate = PremiumNumberV2Manager.getRateForCountry(c.code)
                            Surface(
                                color = if (isSelected) Color(0xFF0C2A44) else Color(0xFF0B1626),
                                shape = RoundedCornerShape(7.dp),
                                border = BorderStroke(
                                    if (isSelected) 1.2.dp else 0.8.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF1B314B)
                                ),
                                modifier = Modifier.clickable {
                                    PremiumNumberV2Manager.setSelectedCountry(c.code)
                                    if (c.dialCode.isNotBlank()) {
                                        PremiumNumberV2Manager.setRangePrefix(c.dialCode.replace("+", ""))
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(c.flag, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = c.name.take(9),
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
                                    )
                                    if (c.dialCode.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = c.dialCode, color = Color(0xFF64748B), fontSize = 8.sp)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.25f) else Color(0xFF071422),
                                        shape = RoundedCornerShape(3.dp),
                                        border = BorderStroke(0.5.dp, if (isSelected) Color(0xFF00E676) else Color(0xFF162D42))
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%.2f৳", cRate),
                                            color = if (isSelected) Color(0xFF00E676) else Color(0xFF38BDF8),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Metadata footer: 14m 40s hold, live polling, prefix reset & national format
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⏳ ১৪ মি ৪০ সে মেয়াদ", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                            Text("•", color = Color(0xFF475569), fontSize = 8.sp)
                            Text("⚡ অটো-কপি", color = Color(0xFF38BDF8), fontSize = 8.5.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.rangePrefix.isNotBlank()) {
                                Text(
                                    text = "Reset Prefix (${state.rangePrefix}) ✕",
                                    color = Color(0xFFEF4444),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { PremiumNumberV2Manager.setRangePrefix("") }
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("National:", color = Color(0xFF64748B), fontSize = 8.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Surface(
                                    color = if (state.nationalFormat) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.clickable { PremiumNumberV2Manager.setNationalFormat(!state.nationalFormat) }
                                ) {
                                    Text(
                                        text = if (state.nationalFormat) "ON" else "OFF",
                                        color = if (state.nationalFormat) Color.Black else Color(0xFF94A3B8),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ACTIVE VIRTUAL NUMBERS & LIVE OTP MONITOR
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

                    // Row 1: Filter chips row (সব, পেন্ডিং, ওটিপি, রিটার্ন)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Row 2: Action buttons (24h OTP, Copy All, Clear Failed, মুছুন)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Download 24h OTP Button
                        val activeExportItems = remember(state.activeNumbers) {
                            state.activeNumbers
                                .filter { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
                                .map { num ->
                                    val cInfo = com.example.util.AllCountriesProvider.detectCountry(num.number)
                                        ?: com.example.util.AllCountriesProvider.detectCountry(num.country)
                                    com.example.util.ExportOtpItem(
                                        phoneNumber = num.number,
                                        country = cInfo?.name ?: num.country,
                                        iso = (cInfo?.code ?: num.iso).uppercase(),
                                        flag = cInfo?.flag ?: num.flag ?: "🌐",
                                        serviceOrRange = num.range ?: "Premium V2",
                                        otpCode = num.otpCode ?: "",
                                        otpMessage = num.otpMessage,
                                        timestamp = num.timestamp,
                                        rate = num.rate
                                    )
                                }
                        }
                        val merged24hV2Otps = remember(activeExportItems, state.todayOtpCount) {
                            com.example.util.OtpExportHelper.getMerged24hOtps("Premium Number V2", activeExportItems)
                        }
                        Surface(
                            color = if (merged24hV2Otps.isNotEmpty()) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (merged24hV2Otps.isNotEmpty()) Color(0xFF00E5FF) else Color(0xFF334155)),
                            modifier = Modifier.clickable {
                                val currentExportList = com.example.util.OtpExportHelper.getMerged24hOtps("Premium Number V2", activeExportItems)
                                if (currentExportList.isEmpty()) {
                                    Toast.makeText(context, "গত ২৪ ঘণ্টায় ডাউনলোড করার মতো কোনো সফল OTP নম্বর পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                } else {
                                    com.example.util.OtpExportHelper.exportOtps(context, "Premium Number V2", currentExportList)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = if (merged24hV2Otps.isNotEmpty()) Color(0xFF00E5FF) else Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (merged24hV2Otps.isNotEmpty()) "24h OTP (${merged24hV2Otps.size})" else "24h OTP",
                                    color = if (merged24hV2Otps.isNotEmpty()) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

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
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Copy All", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        // Red Clear Failed / Expired Numbers Button
                        val v2FailedCount = remember(state.activeNumbers, nowTick) {
                            state.activeNumbers.count {
                                it.otpCode.isNullOrEmpty() && (it.status == "expired" || it.status == "cancelled" || nowTick >= it.expiresAt)
                            }
                        }
                        Surface(
                            color = if (v2FailedCount > 0) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFFEF4444).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = if (v2FailedCount > 0) 0.85f else 0.45f)),
                            modifier = Modifier.clickable {
                                PremiumNumberV2Manager.clearExpiredOrCompletedNumbers()
                                Toast.makeText(context, "🗑️ এক্সপায়ার্ড/ফেইল্ড নম্বরগুলো ক্লিয়ার হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (v2FailedCount > 0) "Clear Failed ($v2FailedCount)" else "Clear Failed",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
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
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("মুছুন", color = Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                                        if (AuthManager.isAdmin()) {
                                            Surface(
                                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                                modifier = Modifier.clickable {
                                                    PremiumNumberV2Manager.clearLiveConsoleFeed()
                                                    Toast.makeText(context, "🧹 Premium V2 Live Console ক্লিয়ার হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Clear", color = Color(0xFFEF4444), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
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

                        // Category Filter Chips (Matching Screenshot 2: ALL, Facebook, WhatsApp, Instagram)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("ALL", "Facebook", "WhatsApp", "Instagram").forEach { cat ->
                                val isSel = selectedConsoleCategory.equals(cat, ignoreCase = true)
                                val pillBg = if (isSel) Color(0xFF00E676) else Color(0xFF0E1A29)
                                val pillFg = if (isSel) Color(0xFF041B26) else Color(0xFF94A3B8)
                                val pillBorder = if (isSel) Color(0xFF00E676) else Color(0xFF1E2D40)
                                Surface(
                                    color = pillBg,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, pillBorder),
                                    modifier = Modifier.clickable { selectedConsoleCategory = cat }
                                ) {
                                    Text(
                                        text = cat,
                                        color = pillFg,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        val feedItems = remember(state.liveConsoleFeed, selectedConsoleCategory, liveFeedSearchQuery) {
                            var list = if (selectedConsoleCategory == "ALL") state.liveConsoleFeed
                            else {
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
                            list.sortedWith(
                                compareByDescending<PremiumV2ConsoleItem> { it.isNew || it.timeAgo.contains("just now", true) }
                                    .thenByDescending { it.timestamp }
                            ).take(50)
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
                                    item = item
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 6.dp).horizontalScroll(rememberScrollState())
                ) {
                    val detected = remember(item.number, item.country, item.iso) {
                        com.example.util.AllCountriesProvider.detectCountry(item.number)
                            ?: com.example.util.AllCountriesProvider.findCountry(item.iso)
                            ?: com.example.util.AllCountriesProvider.detectCountry(item.country)
                    }
                    val flag = item.flag ?: detected?.flag ?: "🌐"
                    val countryName = detected?.name ?: item.country
                    val isoShort = (detected?.code ?: item.iso).uppercase()

                    Text(text = flag, fontSize = 16.sp, maxLines = 1, softWrap = false)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$countryName [$isoShort]",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        val displayTag = when {
                            item.range.contains("fb", true) || item.range.contains("face", true) || item.range.contains("clone", true) || item.range.contains("new", true) -> "Facebook"
                            item.range.contains("whatsapp", true) || item.range.contains("wp", true) -> "WhatsApp"
                            item.range.contains("instagram", true) || item.range.contains("ig", true) -> "Instagram"
                            item.range.contains("otp", true) -> "OTP Work"
                            item.range.isNotBlank() && item.range != "Live Range" -> item.range
                            else -> "Facebook"
                        }
                        Text(
                            text = displayTag,
                            color = Color(0xFF00E5FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.number,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )

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
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
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
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
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
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }

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
 * Live OTP Stream Feed Item Card (Strictly Matching Screenshot 2 from slvirtualnumber.com)
 */
@Composable
fun LiveOtpStreamCard(
    item: PremiumV2ConsoleItem
) {
    val context = LocalContext.current
    val fullSnippet = item.rawSnippet.ifBlank { "<#> ***** is your Facebook code\nH29Q+Fsn4Sr" }

    val cInfo = com.example.util.AllCountriesProvider.detectCountry(item.country)
        ?: com.example.util.AllCountriesProvider.detectCountry(item.iso)
        ?: com.example.util.AllCountriesProvider.detectCountry(item.rangePrefix)
    val effectiveFlag = cInfo?.flag ?: item.flag.ifEmpty { "🌐" }
    val effectiveName = cInfo?.name ?: item.country.ifEmpty { "Global" }
    val effectiveIso = (cInfo?.code ?: item.iso).uppercase()
    val displayRange = if (item.rangePrefix.isNotBlank()) "${item.rangePrefix}XXX" else if (item.maskedNumber.length >= 7) "${item.maskedNumber.take(6)}XXX" else item.maskedNumber

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09121D)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, Color(0xFF132F42)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Line 1: [ Range Pill ❐ ] on left, [ Service Tag ] [ Country Flag + Name ISO ] on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Range Pill with 1-click copy
                Surface(
                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        ClipboardHelper.copyToClipboard(context, item.rangePrefix.ifBlank { displayRange }, "Range")
                        Toast.makeText(context, "Range কপি হয়েছে: ${item.rangePrefix.ifBlank { displayRange }}", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayRange,
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Range",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Service Chip with distinct colors
                    val (srvBg, srvFg, srvBorder, srvLabel) = when {
                        item.service.contains("facebook", ignoreCase = true) || item.service.contains("fb", ignoreCase = true) ->
                            listOf(Color(0xFF1877F2).copy(alpha = 0.2f), Color(0xFF60A5FA), Color(0xFF3B82F6), "Facebook")
                        item.service.contains("whatsapp", ignoreCase = true) || item.service.contains("wa", ignoreCase = true) ->
                            listOf(Color(0xFF059669).copy(alpha = 0.2f), Color(0xFF34D399), Color(0xFF10B981), "WhatsApp")
                        item.service.contains("instagram", ignoreCase = true) || item.service.contains("ig", ignoreCase = true) ->
                            listOf(Color(0xFFDB2777).copy(alpha = 0.2f), Color(0xFFF472B6), Color(0xFFEC4899), "Instagram")
                        else ->
                            listOf(Color(0xFFF59E0B).copy(alpha = 0.2f), Color(0xFFFCD34D), Color(0xFFF59E0B), item.service.uppercase())
                    }

                    Surface(
                        color = srvBg as Color,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, srvBorder as Color)
                    ) {
                        Text(
                            text = srvLabel as String,
                            color = srvFg as Color,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }

                    // Country Pill [ 🇪🇹 Ethiopia ET ]
                    Surface(
                        color = Color(0xFF111C28),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, Color(0xFF1E3A56))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = effectiveFlag, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (effectiveIso.isNotBlank()) "$effectiveName $effectiveIso" else effectiveName,
                                color = Color(0xFF2DD4BF),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Line 2: Masked Phone Number & Relative Time (Clean, no redundant OTP copy badges)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Masked Number (+3932••••4885)
                Surface(
                    color = Color(0xFF0F1E2E),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, Color(0xFF1E3A56)),
                    modifier = Modifier.clickable {
                        ClipboardHelper.copyToClipboard(context, item.maskedNumber, "Phone Number")
                        Toast.makeText(context, "নম্বর কপি হয়েছে: ${item.maskedNumber}", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.maskedNumber,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Number",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.timeAgo,
                        color = Color(0xFF00E676),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Line 3: SMS body preview (click to copy full snippet)
            Surface(
                color = Color(0xFF070E18),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.8.dp, Color(0xFF132233)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ClipboardHelper.copyToClipboard(context, fullSnippet, "Full SMS")
                        Toast.makeText(context, "✓ পুরো এসএমএস কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("💬", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = fullSnippet,
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
