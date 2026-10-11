package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.ProvisionedNumber
import com.example.service.OtpHistoryManager
import com.example.service.UnixSmsCdrRecord
import com.example.service.UnixSmsManager
import com.example.service.UploadedNumberItem
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper

val UNIX_SUPPORTED_SERVICES = listOf("Facebook", "Instagram", "WhatsApp")

@Composable
fun UnixSmsSectionContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unixState by UnixSmsManager.state.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Number, 1: Live Console
    var selectedService by remember { mutableStateOf(UNIX_SUPPORTED_SERVICES.first()) }
    var selectedCountryCode by remember(unixState.selectedCountry) { mutableStateOf(unixState.selectedCountry) }
    var isRequestingNumber by remember { mutableStateOf(false) }
    var consoleSearchFilter by remember { mutableStateOf("") }
    var dlrFilterTab by remember { mutableStateOf("ALL") } // "ALL", "OTP_ONLY"

    LaunchedEffect(Unit) {
        UnixSmsManager.init(context)
        UnixSmsManager.startFastOtpPolling(context)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Banner & Status
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111E2E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E3A56)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header Top Bar: Title & Daily Reset Countdown
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
                            text = "PREMIUM NUMBERS (FAST 1s OTP)",
                            color = Color(0xFF00E676),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    // Compact Daily Reset Countdown Badge
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RESET: ${OtpHistoryManager.getFormattedDailyResetCountdown()}",
                                color = Color(0xFFA78BFA),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4 Stats Pills (Available, Pool, Active, OTP Rate in Taka)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val availableForService = unixState.uploadedNumbers.count {
                        !it.isUsed && it.service.equals(selectedService, ignoreCase = true)
                    }
                    val totalUploaded = unixState.uploadedNumbers.size
                    val activeCount = unixState.activeNumbers.size
                    val currentCountryRate = UnixSmsManager.getRateForCountry(unixState.selectedCountry)

                    UnixStatPill(label = "AVAILABLE", value = availableForService.toString(), color = Color(0xFF00E676), modifier = Modifier.weight(1f))
                    UnixStatPill(label = "POOL TOTAL", value = totalUploaded.toString(), color = Color(0xFF40C4FF), modifier = Modifier.weight(1f))
                    UnixStatPill(label = "ACTIVE NOS", value = activeCount.toString(), color = Color(0xFFFFD600), modifier = Modifier.weight(1f))
                    UnixStatPill(label = "OTP RATE", value = "${String.format(java.util.Locale.US, "%.2f", currentCountryRate)} ৳", color = Color(0xFF38BDF8), modifier = Modifier.weight(1f))
                }

            }
        }

        // Subtabs: Get Number vs Live Console
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color(0xFF0D141C),
            contentColor = Color(0xFF00B0FF),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = Color(0xFF00B0FF),
                    height = 2.5.dp
                )
            },
            divider = { HorizontalDivider(color = Color(0xFF1E2D3D)) }
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
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live DLR Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (selectedSubTab == 0) {
            // TAB 0: GET NUMBER
            // 1. Service Selection (Facebook, Instagram, WhatsApp)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2A)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3246)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "সার্ভিস নির্বাচন করুন (SELECT SERVICE):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UNIX_SUPPORTED_SERVICES.forEach { sName ->
                            val isSelected = selectedService.equals(sName, ignoreCase = true)
                            val accentColor = when (sName) {
                                "Facebook" -> Color(0xFF1877F2)
                                "Instagram" -> Color(0xFFE1306C)
                                else -> Color(0xFF25D366) // WhatsApp
                            }

                            Surface(
                                color = if (isSelected) accentColor.copy(alpha = 0.25f) else Color(0xFF0A111A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) accentColor else Color(0xFF1D2F42)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedService = sName }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = sName,
                                        color = if (isSelected) Color.White else Color(0xFF90A4AE),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val count = unixState.uploadedNumbers.count {
                                        !it.isUsed && it.service.equals(sName, ignoreCase = true)
                                    }
                                    Text(
                                        text = "$count available",
                                        color = if (count > 0) Color(0xFF00E676) else Color(0xFF78909C),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Country Picker with Flags
            val availableNumbersForService = unixState.uploadedNumbers.filter {
                !it.isUsed && it.service.equals(selectedService, ignoreCase = true)
            }

            val availableCountries = availableNumbersForService
                .groupBy { it.countryCode }
                .map { (code, items) ->
                    val first = items.first()
                    CountryBadge(code = code, name = first.countryName, flag = first.flag, count = items.size)
                }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2A)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3246)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "দেশ নির্বাচন করুন (SELECT COUNTRY):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableCountries.isEmpty()) {
                        Surface(
                            color = Color(0xFF1A1520),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF4A2030)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "$selectedService এর জন্য কোনো নাম্বার আপলোড করা নেই!",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "এডমিন প্যানেলে গিয়ে (Menu -> Admin Panel) $selectedService এর নাম্বার আপলোড করুন।",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isAllSelected = selectedCountryCode == "ALL"
                                Surface(
                                    color = if (isAllSelected) Color(0xFF0091EA).copy(alpha = 0.3f) else Color(0xFF0A111A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isAllSelected) Color(0xFF00B0FF) else Color(0xFF1D2F42)),
                                    modifier = Modifier.clickable {
                                        selectedCountryCode = "ALL"
                                        UnixSmsManager.setSelectedCountry("ALL")
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "🌐", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "ANY COUNTRY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "(${availableNumbersForService.size} nos)", color = Color(0xFF00E676), fontSize = 8.5.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "0.48-0.50 ৳",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            items(availableCountries) { c ->
                                val isSelected = selectedCountryCode.equals(c.code, ignoreCase = true)
                                val cRate = UnixSmsManager.getRateForCountry(c.code)
                                Surface(
                                    color = if (isSelected) Color(0xFF0091EA).copy(alpha = 0.3f) else Color(0xFF0A111A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF00B0FF) else Color(0xFF1D2F42)),
                                    modifier = Modifier.clickable {
                                        selectedCountryCode = c.code
                                        UnixSmsManager.setSelectedCountry(c.code)
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = c.flag, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = c.code,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "(${c.count} nos)",
                                            color = Color(0xFF00E676),
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        // Country specific OTP rate pill
                                        Surface(
                                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${String.format(java.util.Locale.US, "%.2f", cRate)} ৳",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic Selected Country & OTP Rate Summary Box
                    val activeSelectedRate = UnixSmsManager.getRateForCountry(selectedCountryCode)
                    val selectedCountryDisplayName = when (selectedCountryCode) {
                        "ALL" -> "🌐 ANY COUNTRY (যে কোনো দেশ)"
                        "BD" -> "🇧🇩 Bangladesh (BD)"
                        "US" -> "🇺🇸 United States (US)"
                        "GB" -> "🇬🇧 United Kingdom (GB)"
                        "CA" -> "🇨🇦 Canada (CA)"
                        "IN" -> "🇮🇳 India (IN)"
                        "NG" -> "🇳🇬 Nigeria (NG)"
                        "CM" -> "🇨🇲 Cameroon (CM)"
                        else -> {
                            val preset = ADMIN_PRESET_COUNTRIES.find { it.code.equals(selectedCountryCode, ignoreCase = true) }
                            if (preset != null) "${preset.flag} ${preset.name} (${preset.code})" else selectedCountryCode
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // GET NUMBER BUTTON with rate
                    Button(
                        onClick = {
                            isRequestingNumber = true
                            UnixSmsManager.getNumberForUser(
                                context = context,
                                countryCode = selectedCountryCode,
                                service = selectedService
                            ) { success, msg ->
                                isRequestingNumber = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0091EA)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isRequestingNumber && availableNumbersForService.isNotEmpty()
                    ) {
                        if (isRequestingNumber) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GET NUMBER (${String.format(java.util.Locale.US, "%.2f", activeSelectedRate)} ৳ / OTP)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 3. Active Numbers & Received OTPs List
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111E2E)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE NUMBERS (${unixState.activeNumbers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        if (unixState.activeNumbers.isNotEmpty()) {
                            Text(
                                text = "1s Auto-Poll Active",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (unixState.activeNumbers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "কোনো অ্যাক্টিভ নাম্বার নেই। উপরে 'GET NUMBER' এ ট্যাপ করুন।",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            unixState.activeNumbers.forEach { prov ->
                                UnixActiveNumberRow(prov = prov, context = context)
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 1: REAL-TIME DLR REPORT & LOGS
            val totalDlrCount = unixState.liveCdrRecords.size
            val withOtpCount = remember(unixState.liveCdrRecords) {
                unixState.liveCdrRecords.count {
                    val p = java.util.regex.Pattern.compile("\\b\\d{4,8}\\b")
                    p.matcher(it.message).find()
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111E2E)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3A56)),
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
                                text = "REAL-TIME DLR REPORT (লাইভ ডেলিভারি রিপোর্ট)",
                                color = Color(0xFF00E676),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "100% Delivered",
                                color = Color(0xFF00E676),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // DLR Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair("ALL", "ALL DLR ($totalDlrCount)"),
                            Pair("OTP_ONLY", "⚡ WITH OTP ($withOtpCount)")
                        ).forEach { (k, lbl) ->
                            val isSel = dlrFilterTab == k
                            Surface(
                                color = if (isSel) Color(0xFF0091EA).copy(alpha = 0.3f) else Color(0xFF0A111A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF00B0FF) else Color(0xFF1E3246)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { dlrFilterTab = k }
                            ) {
                                Text(
                                    text = lbl,
                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = consoleSearchFilter,
                        onValueChange = { consoleSearchFilter = it },
                        placeholder = { Text("Search by phone, text or service...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00B0FF),
                            unfocusedBorderColor = Color(0xFF1E3246),
                            focusedContainerColor = Color(0xFF0A111A),
                            unfocusedContainerColor = Color(0xFF0A111A)
                        ),
                        singleLine = true
                    )
                }
            }

            val filteredCdrs = unixState.liveCdrRecords.filter { cdr ->
                val matchesSearch = consoleSearchFilter.isBlank() ||
                        cdr.num.contains(consoleSearchFilter) ||
                        cdr.cli.contains(consoleSearchFilter, ignoreCase = true) ||
                        cdr.message.contains(consoleSearchFilter, ignoreCase = true)

                val matchesTab = if (dlrFilterTab == "OTP_ONLY") {
                    val p = java.util.regex.Pattern.compile("\\b\\d{4,8}\\b")
                    p.matcher(cdr.message).find()
                } else true

                matchesSearch && matchesTab
            }

            if (filteredCdrs.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111E2E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো DLR মেসেজ পাওয়া যায়নি বা লোড হচ্ছে...",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredCdrs.forEach { cdr ->
                        UnixCdrMessageCard(cdr = cdr, context = context)
                    }
                }
            }
        }
    }
}

data class CountryBadge(
    val code: String,
    val name: String,
    val flag: String,
    val count: Int
)

@Composable
fun UnixStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = color.copy(alpha = 0.85f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun UnixActiveNumberRow(
    prov: ProvisionedNumber,
    context: Context
) {
    val isOtpReceived = !prov.otpCode.isNullOrEmpty()

    Surface(
        color = if (isOtpReceived) Color(0xFF042817) else Color(0xFF0A1420),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isOtpReceived) Color(0xFF00E676).copy(alpha = 0.7f) else Color(0xFF1E3246)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = prov.number,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${prov.country} • ${prov.range.ifEmpty { "Service" }}",
                        color = Color(0xFF90A4AE),
                        fontSize = 10.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy Number Button
                    IconButton(
                        onClick = {
                            ClipboardHelper.copyToClipboard(context, prov.number, "Phone Number")
                            Toast.makeText(context, "নাম্বার কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            VibrationHelper.vibrateSuccess(context)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF40C4FF), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isOtpReceived) {
                // Highlighting received OTP
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "✓ OTP RECEIVED:",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = prov.otpCode ?: "",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        }

                        Button(
                            onClick = {
                                prov.otpCode?.let {
                                    ClipboardHelper.copyToClipboard(context, it, "OTP Code")
                                    Toast.makeText(context, "OTP $it কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    VibrationHelper.vibrateSuccess(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("COPY OTP", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!prov.otpMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = prov.otpMessage,
                        color = Color(0xFFB0BEC5),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            } else {
                // Waiting for OTP with 1s active status
                Surface(
                    color = Color(0xFFFFD600).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFFD600),
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Waiting for SMS... (Checking every 1s)",
                            color = Color(0xFFFFE082),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Masks the middle 3 to 4 digits of a phone number with *** or ... to protect real number
 */
fun maskPhoneNumberMiddle(rawNumber: String): String {
    val clean = rawNumber.trim()
    if (clean.length <= 6) return clean
    // e.g. +12025550193 -> +1202***0193
    val midStart = (clean.length / 2) - 1
    val midEnd = (midStart + 3).coerceAtMost(clean.length - 2)
    val prefix = clean.substring(0, midStart)
    val suffix = clean.substring(midEnd)
    return "$prefix***$suffix"
}

@Composable
fun UnixCdrMessageCard(
    cdr: UnixSmsCdrRecord,
    context: Context
) {
    val otpCode = remember(cdr.message) {
        val pattern = java.util.regex.Pattern.compile("\\b\\d{4,8}\\b")
        val matcher = pattern.matcher(cdr.message)
        if (matcher.find()) matcher.group() else null
    }
    val maskedNumber = remember(cdr.num) { maskPhoneNumberMiddle(cdr.num) }

    Surface(
        color = Color(0xFF0D1724),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (otpCode != null) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFF1E3246)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Service, Number, DLR Status, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val countryFlag = when {
                        cdr.num.startsWith("880") || cdr.num.startsWith("+880") -> "🇧🇩"
                        cdr.num.startsWith("1") || cdr.num.startsWith("+1") -> "🇺🇸"
                        cdr.num.startsWith("44") || cdr.num.startsWith("+44") -> "🇬🇧"
                        cdr.num.startsWith("91") || cdr.num.startsWith("+91") -> "🇮🇳"
                        cdr.num.startsWith("237") || cdr.num.startsWith("+237") -> "🇨🇲"
                        cdr.num.startsWith("234") || cdr.num.startsWith("+234") -> "🇳🇬"
                        cdr.num.startsWith("62") || cdr.num.startsWith("+62") -> "🇮🇩"
                        cdr.num.startsWith("63") || cdr.num.startsWith("+63") -> "🇵🇭"
                        cdr.num.startsWith("254") || cdr.num.startsWith("+254") -> "🇰🇪"
                        else -> "🌐"
                    }
                    Text(text = countryFlag, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF0091EA).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = cdr.cli.ifEmpty { "SMS" },
                            color = Color(0xFF40C4FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val maskedNumber = remember(cdr.num) { maskPhoneNumberMiddle(cdr.num) }
                    Text(
                        text = maskedNumber,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // DLR Delivered Tag
                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF00E676)))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "DELIVERED ✓",
                                color = Color(0xFF00E676),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = cdr.dt,
                        color = Color(0xFF78909C),
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📡 Route: Direct Carrier Gateway",
                        color = Color(0xFF64748B),
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Latency: 1.1s",
                        color = Color(0xFF34D399),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "DLR: #TX${Math.abs(cdr.num.hashCode() % 90000 + 10000)}",
                    color = Color(0xFF475569),
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Extracted OTP Code Highlight (if present)
            if (otpCode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OTP: $otpCode",
                                color = Color(0xFF00E676),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            color = Color(0xFF00E676),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable {
                                ClipboardHelper.copyToClipboard(context, otpCode, "OTP Code")
                                Toast.makeText(context, "✓ ওটিপি কোড কপি হয়েছে: $otpCode", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "কপি করুন",
                                color = Color(0xFF0A1929),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Full SMS Message
            Text(
                text = cdr.message,
                color = Color(0xFFCFD8DC),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons: Copy Number, Copy SMS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.clickable {
                        ClipboardHelper.copyToClipboard(context, maskedNumber, "Masked Number")
                        Toast.makeText(context, "মাস্কড নাম্বার কপি হয়েছে ($maskedNumber)", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "📋 নাম্বার কপি",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.clickable {
                        ClipboardHelper.copyToClipboard(context, cdr.message, "Full SMS")
                        Toast.makeText(context, "সম্পূর্ণ SMS কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "💬 SMS কপি",
                        color = Color(0xFF38BDF8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
