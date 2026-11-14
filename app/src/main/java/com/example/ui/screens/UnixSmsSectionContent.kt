package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import java.util.Locale

val UNIX_SUPPORTED_SERVICES = listOf(
    "✨ FB Fresh",
    "👥 FB Clone",
    "📸 Instagram (IG)",
    "💬 WhatsApp",
    "🔥 OTP Work"
)

fun isNumberMatchingUnixService(item: UploadedNumberItem, serviceLabel: String): Boolean {
    return UnixSmsManager.isNumberMatchingService(item, serviceLabel)
}

@Composable
fun UnixSmsSectionContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unixState by UnixSmsManager.state.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Get Number, 1: Live DLR Logs
    var selectedService by remember(unixState.selectedService) { mutableStateOf(unixState.selectedService) }
    var selectedCountryCode by remember(unixState.selectedCountry) { mutableStateOf(unixState.selectedCountry) }
    var isRequestingNumber by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }
    var consoleSearchFilter by remember { mutableStateOf("") }
    var dlrGatewayFilter by remember { mutableStateOf("ALL") } // "ALL", "LAMIX", "HADI", "UNIX"
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    if (showResetConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = Color(0xFF0F1A2A),
            title = {
                Text(
                    text = "⚠️ OTP ও নাম্বার রিসেট করবেন?",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিত যে Premium Numbers প্যানেলের অ্যাক্টিভ নাম্বার ও ২৪ ঘণ্টার ওটিপি রিসেট করতে চান?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        UnixSmsManager.resetTodayCycle(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Yes (হ্যাঁ)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showResetConfirmDialog = false }
                ) {
                    Text("No (না)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        UnixSmsManager.init(context)
        UnixSmsManager.startFastOtpPolling(context)
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Strict per-service filtering: only unused numbers belonging to the selected canonical service
    val availableNumbersForService = remember(unixState.uploadedNumbers, selectedService) {
        unixState.uploadedNumbers.filter {
            isNumberMatchingUnixService(it, selectedService)
        }
    }

    // Group strictly by countryCode for the selected service (supports 15-20+ countries cleanly)
    val availableCountries = remember(availableNumbersForService) {
        availableNumbersForService
            .groupBy { it.countryCode.uppercase().trim() }
            .map { (code, items) ->
                val first = items.first()
                val canonSrv = UnixSmsManager.canonicalService(first.service, first.numberType)
                val pTag = when (canonSrv) {
                    "FB Fresh" -> "✨ FB Fresh"
                    "FB Clone" -> "👥 FB Clone"
                    "Instagram" -> "📸 Instagram"
                    "WhatsApp" -> "💬 WhatsApp"
                    else -> "🔥 OTP Work"
                }
                CountryBadge(
                    code = code,
                    name = first.countryName,
                    flag = first.flag,
                    count = items.size,
                    numberType = first.numberType,
                    platformTag = pTag
                )
            }
            .sortedByDescending { it.count }
    }

    // Ensure selectedCountryCode always points to an available country for the selected service (never "ALL")
    LaunchedEffect(availableCountries, selectedService) {
        if (availableCountries.isNotEmpty()) {
            val exists = availableCountries.any { it.code.equals(selectedCountryCode, ignoreCase = true) }
            if (!exists || selectedCountryCode.equals("ALL", ignoreCase = true)) {
                val firstCode = availableCountries.first().code
                selectedCountryCode = firstCode
                UnixSmsManager.setSelectedCountry(firstCode)
            }
        }
    }

    val selectedCountryBadge = remember(availableCountries, selectedCountryCode) {
        availableCountries.find { it.code.equals(selectedCountryCode, ignoreCase = true) }
            ?: availableCountries.firstOrNull()
    }

    val effectiveCountryCode = selectedCountryBadge?.code ?: selectedCountryCode.ifEmpty { "US" }
    val selectedCountryStock = selectedCountryBadge?.count ?: 0
    val currentCountryRate = UnixSmsManager.getRateForCountry(effectiveCountryCode)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Cyber-Luxury Header Banner & Separate Per-Service / Per-Country Stats
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1522)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, Color(0xFF1E3A5F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF102136), Color(0xFF0B1522))
                        )
                    )
                    .padding(14.dp)
            ) {
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
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Column {
                            Text(
                                text = "PREMIUM NUMBERS",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = "Lamix • Hadi • Unix (1s Ultra-Fast OTP)",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFA78BFA)),
                        modifier = Modifier.clickable { showResetConfirmDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🔄 RESET: ${OtpHistoryManager.getFormattedDailyResetCountdown()}",
                                color = Color(0xFFE9D5FF),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val unixSuccessCount = unixState.activeNumbers.count { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
                val unixEffectiveTodayOtp = maxOf(unixState.todayOtpCount, unixSuccessCount)

                // 5 Separate Stats Pills (Including 24h Active Nos & Today OTP)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    UnixStatPill(
                        label = "STOCK ($effectiveCountryCode)",
                        value = "$selectedCountryStock",
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f)
                    )
                    UnixStatPill(
                        label = "24H NOS",
                        value = "${unixState.activeNumbers.size}",
                        color = Color(0xFFFFD600),
                        modifier = Modifier.weight(1f)
                    )
                    UnixStatPill(
                        label = "SUCCESS OTP",
                        value = "$unixEffectiveTodayOtp",
                        color = Color(0xFF00E676),
                        modifier = Modifier.weight(1f)
                    )
                    UnixStatPill(
                        label = "OTP RATE",
                        value = "৳${String.format(Locale.US, "%.2f", currentCountryRate)}",
                        color = Color(0xFFF472B6),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Subtabs: Get Number vs Live DLR Logs
        Surface(
            color = Color(0xFF0B131E),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF1B2E44)),
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF38BDF8),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = Color(0xFF00E676),
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = if (selectedSubTab == 0) Color(0xFF00E676) else Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Get Number",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selectedSubTab == 0) Color.White else Color(0xFF94A3B8)
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
                                tint = if (selectedSubTab == 1) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live DLR Logs (${unixState.liveCdrRecords.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selectedSubTab == 1) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                )
            }
        }

        if (selectedSubTab == 0) {
            // =========================================================================
            // TAB 0: GET NUMBER (STRICT SERVICE & COUNTRY SEPARATION)
            // =========================================================================

            // 1. Service Selection Card (2 + 3 Grid so all 5 services are clearly visible & separate)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E344E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "১. সার্ভিস নির্বাচন করুন (SELECT SERVICE):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "প্রতিটি সার্ভিসের স্টক সম্পূর্ণ আলাদা",
                            fontSize = 9.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 1: Facebook Services (FB Fresh & FB Clone)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UNIX_SUPPORTED_SERVICES.take(2).forEach { sName ->
                            val isSelected = UnixSmsManager.canonicalService(selectedService) == UnixSmsManager.canonicalService(sName)
                            val accentColor = if (sName.contains("Clone")) Color(0xFFF59E0B) else Color(0xFF00E676)
                            val sCount = unixState.uploadedNumbers.count { isNumberMatchingUnixService(it, sName) }
                            val cCount = unixState.uploadedNumbers
                                .filter { isNumberMatchingUnixService(it, sName) }
                                .map { it.countryCode.uppercase() }
                                .distinct()
                                .size

                            Surface(
                                color = if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF08101A),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accentColor else Color(0xFF1D2F44)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedService = sName
                                        UnixSmsManager.setSelectedService(sName)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = sName,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$sCount টি নাম্বার",
                                            color = if (sCount > 0) accentColor else Color(0xFF64748B),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (cCount > 0) {
                                            Text(
                                                text = " • $cCount দেশ",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Instagram, WhatsApp, OTP Work (Normal Services)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UNIX_SUPPORTED_SERVICES.drop(2).forEach { sName ->
                            val isSelected = UnixSmsManager.canonicalService(selectedService) == UnixSmsManager.canonicalService(sName)
                            val accentColor = when {
                                sName.contains("Instagram") -> Color(0xFFE1306C)
                                sName.contains("WhatsApp") -> Color(0xFF25D366)
                                else -> Color(0xFFFF6D00)
                            }
                            val sCount = unixState.uploadedNumbers.count { isNumberMatchingUnixService(it, sName) }

                            Surface(
                                color = if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF08101A),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accentColor else Color(0xFF1D2F44)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedService = sName
                                        UnixSmsManager.setSelectedService(sName)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = sName.replace(" (IG)", ""),
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$sCount টি নাম্বার",
                                        color = if (sCount > 0) accentColor else Color(0xFF64748B),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Country Selection & Get Number Card (Supports 15-20+ countries with strict per-country isolation)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E344E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "২. দেশ নির্বাচন করুন (${availableCountries.size} টি দেশ উপলব্ধ):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, Color(0xFF00E676).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = UnixSmsManager.canonicalService(selectedService),
                                color = Color(0xFF00E676),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableCountries.isEmpty()) {
                        Surface(
                            color = Color(0xFF16121E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF4A2030)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "$selectedService এর জন্য কোনো নাম্বার স্টকে নেই!",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "অ্যাডমিন প্যানেলে গিয়ে $selectedService স্লটে নাম্বার আপলোড করুন।",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Show quick search bar when more than 5 countries are uploaded
                        if (availableCountries.size > 5) {
                            OutlinedTextField(
                                value = countrySearchQuery,
                                onValueChange = { countrySearchQuery = it },
                                placeholder = { Text("দেশ খুঁজুন (যেমন US, BD, Nigeria, Italy)...", fontSize = 10.5.sp, color = Color(0xFF64748B)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF1D2F44),
                                    focusedContainerColor = Color(0xFF08101A),
                                    unfocusedContainerColor = Color(0xFF08101A)
                                )
                            )
                        }

                        val displayedCountries = remember(availableCountries, countrySearchQuery) {
                            if (countrySearchQuery.isBlank()) availableCountries
                            else availableCountries.filter {
                                it.name.contains(countrySearchQuery, ignoreCase = true) ||
                                it.code.contains(countrySearchQuery, ignoreCase = true)
                            }
                        }

                        // Multi-country horizontal cards with full country name, ISO, stock count, and Admin BDT rate
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(displayedCountries, key = { it.code }) { c ->
                                val isSelected = effectiveCountryCode.equals(c.code, ignoreCase = true)
                                val cRate = UnixSmsManager.getRateForCountry(c.code)
                                val tagColor = when {
                                    c.platformTag.contains("Clone") -> Color(0xFFF59E0B)
                                    c.platformTag.contains("Fresh") -> Color(0xFF00E676)
                                    c.platformTag.contains("WhatsApp") -> Color(0xFF25D366)
                                    c.platformTag.contains("Instagram") -> Color(0xFFE1306C)
                                    else -> Color(0xFFFF6D00)
                                }

                                Surface(
                                    color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.28f) else Color(0xFF08101A),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.6.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1D2F44)
                                    ),
                                    modifier = Modifier
                                        .width(122.dp)
                                        .clickable {
                                            selectedCountryCode = c.code
                                            UnixSmsManager.setSelectedCountry(c.code)
                                            UnixSmsManager.setSelectedService(selectedService)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(text = c.flag, fontSize = 19.sp)
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = c.code,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = c.name,
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = tagColor.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(5.dp)
                                        ) {
                                            Text(
                                                text = "${c.count} টি খালি",
                                                color = tagColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(5.dp),
                                            border = BorderStroke(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "৳${String.format(Locale.US, "%.2f", cRate)} / OTP",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Selected Country + Admin Rate + Quantity Bar (Inside Number Pool, right above Get Number)
                    Surface(
                        color = Color(0xFF08111C),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF1F3652)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedCountryBadge?.let { "${it.flag} ${it.name} (${it.code})" } ?: "দেশ নির্বাচন করুন",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFF00E676).copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(
                                            text = "৳${String.format(Locale.US, "%.2f", currentCountryRate)}",
                                            color = Color(0xFF00E676),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${UnixSmsManager.canonicalService(selectedService)} • এই দেশে স্টক: $selectedCountryStock টি",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Quantity Dropdown (1 to 10)
                            var unixQtyExpanded by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    color = Color(0xFF112236),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable { unixQtyExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Qty: ${unixState.requestCount}",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = unixQtyExpanded,
                                    onDismissRequest = { unixQtyExpanded = false },
                                    modifier = Modifier.background(Color(0xFF13202E))
                                ) {
                                    for (count in 1..10) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "$count টি নম্বর",
                                                    color = if (unixState.requestCount == count) Color(0xFF00E676) else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (unixState.requestCount == count) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                unixQtyExpanded = false
                                                UnixSmsManager.setRequestCount(count)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // GET NUMBER BUTTON (Strictly pulls from effectiveCountryCode & selectedService)
                    Button(
                        onClick = {
                            isRequestingNumber = true
                            UnixSmsManager.getNumbersForUser(
                                context = context,
                                countryCode = effectiveCountryCode,
                                service = selectedService,
                                count = unixState.requestCount,
                                numberType = "ALL"
                            ) { _, msg ->
                                isRequestingNumber = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            disabledContainerColor = Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isRequestingNumber && selectedCountryStock > 0
                    ) {
                        if (isRequestingNumber) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (unixState.requestCount > 1) {
                                    "GET ${unixState.requestCount} ${effectiveCountryCode} NUMBERS (৳${String.format(Locale.US, "%.2f", currentCountryRate)} / OTP)"
                                } else {
                                    "GET ${effectiveCountryCode} NUMBER (${UnixSmsManager.canonicalService(selectedService)} • ৳${String.format(Locale.US, "%.2f", currentCountryRate)})"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 3. Active Numbers & Received OTPs List
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E344E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val expiredOrFailedCount = remember(unixState.activeNumbers, nowMillis) {
                        unixState.activeNumbers.count { prov ->
                            val hasOtp = !prov.otpCode.isNullOrEmpty() || prov.status.equals("success", true)
                            !hasOtp && (prov.status.equals("expired", true) || prov.status.equals("failed", true) || nowMillis >= prov.expiresAt)
                        }
                    }

                    val unix24hExportItems = remember(unixState.activeNumbers, nowMillis) {
                        val activeOtpItems = unixState.activeNumbers
                            .filter { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
                            .map { num ->
                                val cInfo = com.example.util.AllCountriesProvider.detectCountry(num.number)
                                    ?: com.example.util.AllCountriesProvider.detectCountry(num.country)
                                com.example.util.ExportOtpItem(
                                    phoneNumber = num.number,
                                    country = cInfo?.name ?: num.country,
                                    iso = (cInfo?.code ?: num.iso).uppercase(),
                                    flag = cInfo?.flag ?: "🌐",
                                    serviceOrRange = num.range.ifEmpty { "Premium Number" },
                                    otpCode = num.otpCode ?: "",
                                    otpMessage = num.otpMessage,
                                    timestamp = num.timestamp,
                                    rate = num.rate
                                )
                            }
                        com.example.util.OtpExportHelper.getMerged24hOtps("Premium Numbers", activeOtpItems)
                    }

                    // Top Header Row: Title + Live Capture Badge on left, Action buttons in scrollable row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = "ACTIVE NUMBERS (${unixState.activeNumbers.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (unixState.activeNumbers.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF00E676).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "⚡ 1s Live",
                                        color = Color(0xFF00E676),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            // Red Clear Failed / Expired Button
                            Surface(
                                color = if (expiredOrFailedCount > 0) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFFEF4444).copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = if (expiredOrFailedCount > 0) 0.85f else 0.45f)),
                                modifier = Modifier.clickable {
                                    UnixSmsManager.clearExpiredAndFailedNumbers(context)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (expiredOrFailedCount > 0) "Clear Failed ($expiredOrFailedCount)" else "Clear Failed",
                                        fontSize = 9.sp,
                                        color = Color(0xFFFCA5A5),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Surface(
                                color = if (unix24hExportItems.isNotEmpty()) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (unix24hExportItems.isNotEmpty()) Color(0xFF38BDF8) else Color(0xFF334155)),
                                modifier = Modifier.clickable {
                                    com.example.util.OtpExportHelper.exportOtps(context, "Premium Numbers", unix24hExportItems)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        tint = if (unix24hExportItems.isNotEmpty()) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (unix24hExportItems.isNotEmpty()) "24h OTP (${unix24hExportItems.size})" else "24h OTP",
                                        fontSize = 9.sp,
                                        color = if (unix24hExportItems.isNotEmpty()) Color.White else Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    var unixFilterTab by remember { mutableStateOf("ALL") }
                    val unixTotalCount = unixState.activeNumbers.size
                    val unixSuccessCount = remember(unixState.activeNumbers) {
                        unixState.activeNumbers.count { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
                    }
                    val unixPendingCount = remember(unixState.activeNumbers, nowMillis) {
                        unixState.activeNumbers.count { prov ->
                            val hasOtp = !prov.otpCode.isNullOrEmpty() || prov.status.equals("success", true)
                            !hasOtp && !prov.status.equals("expired", true) && !prov.status.equals("failed", true) && nowMillis < prov.expiresAt
                        }
                    }
                    val unixFailedCount = remember(unixState.activeNumbers, nowMillis) {
                        unixState.activeNumbers.count { prov ->
                            val hasOtp = !prov.otpCode.isNullOrEmpty() || prov.status.equals("success", true)
                            !hasOtp && (prov.status.equals("expired", true) || prov.status.equals("failed", true) || nowMillis >= prov.expiresAt)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 24h Filter Tabs: ALL, SUCCESS, PENDING, FAILED (Horizontally scrollable, never squeezed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "ALL ($unixTotalCount)",
                            "SUCCESS" to "SUCCESS ($unixSuccessCount)",
                            "PENDING" to "WAIT ($unixPendingCount)",
                            "FAILED" to "FAILED ($unixFailedCount)"
                        ).forEach { (fKey, fLabel) ->
                            val isSelected = unixFilterTab == fKey
                            Surface(
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF08111C),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1F3652)),
                                modifier = Modifier.clickable { unixFilterTab = fKey }
                            ) {
                                Text(
                                    text = fLabel,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val filteredUnixActive = remember(unixState.activeNumbers, unixFilterTab, nowMillis) {
                        val base = when (unixFilterTab) {
                            "SUCCESS" -> unixState.activeNumbers.filter { !it.otpCode.isNullOrEmpty() || it.status.equals("success", true) }
                            "PENDING" -> unixState.activeNumbers.filter { prov ->
                                val hasOtp = !prov.otpCode.isNullOrEmpty() || prov.status.equals("success", true)
                                !hasOtp && !prov.status.equals("expired", true) && !prov.status.equals("failed", true) && nowMillis < prov.expiresAt
                            }
                            "FAILED" -> unixState.activeNumbers.filter { prov ->
                                val hasOtp = !prov.otpCode.isNullOrEmpty() || prov.status.equals("success", true)
                                !hasOtp && (prov.status.equals("expired", true) || prov.status.equals("failed", true) || nowMillis >= prov.expiresAt)
                            }
                            else -> unixState.activeNumbers
                        }
                        base.sortedByDescending { it.timestamp }
                    }

                    if (filteredUnixActive.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "কোনো অ্যাক্টিভ নাম্বার নেই। উপরে দেশ সিলেক্ট করে 'GET NUMBER' এ ট্যাপ করুন।",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredUnixActive.forEach { prov ->
                                UnixActiveNumberRow(prov = prov, nowMillis = nowMillis, context = context)
                            }
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // TAB 1: LIVE DLR LOGS (ALL 3 GATEWAYS: LAMIX, HADI, UNIX - ZERO DB STORAGE)
            // =========================================================================
            val totalDlrCount = unixState.liveCdrRecords.size
            val lamixCount = unixState.liveCdrRecords.count { it.gateway.equals("LAMIX", true) }
            val hadiCount = unixState.liveCdrRecords.count { it.gateway.equals("HADI", true) }
            val unixCount = unixState.liveCdrRecords.count { it.gateway.equals("UNIX", true) }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E344E)),
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
                            Column {
                                Text(
                                    text = "LIVE DLR STREAM (UNIX • HADI • LAMIX)",
                                    color = Color(0xFF00E676),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "শুধুমাত্র অ্যাড করা দেশ ও সার্ভিসের রিয়েল-টাইম SMS (No DB Save)",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
                            modifier = Modifier.clickable {
                                UnixSmsManager.clearLiveDlrLogs()
                                Toast.makeText(context, "লাইভ DLR কনসোল ক্লিয়ার করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "ক্লিয়ার",
                                color = Color(0xFFF87171),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gateway Filter Chips (ALL, LAMIX, HADI, UNIX)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("ALL", "ALL ($totalDlrCount)", Color(0xFF38BDF8)),
                            Triple("LAMIX", "⚡ Lamix ($lamixCount)", Color(0xFF00E5FF)),
                            Triple("HADI", "🔥 Hadi ($hadiCount)", Color(0xFFF59E0B)),
                            Triple("UNIX", "🌐 Unix ($unixCount)", Color(0xFFA78BFA))
                        ).forEach { (key, label, col) ->
                            val isSel = dlrGatewayFilter == key
                            Surface(
                                color = if (isSel) col.copy(alpha = 0.24f) else Color(0xFF08101A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSel) col else Color(0xFF1D2F44)),
                                modifier = Modifier.clickable { dlrGatewayFilter = key }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = consoleSearchFilter,
                        onValueChange = { consoleSearchFilter = it },
                        placeholder = { Text("দেশ, সার্ভিস বা নম্বর দিয়ে খুঁজুন...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF1D2F44),
                            focusedContainerColor = Color(0xFF08101A),
                            unfocusedContainerColor = Color(0xFF08101A)
                        ),
                        singleLine = true
                    )
                }
            }

            val filteredCdrs = remember(unixState.liveCdrRecords, consoleSearchFilter, dlrGatewayFilter) {
                unixState.liveCdrRecords.filter { cdr ->
                    val matchesGateway = dlrGatewayFilter == "ALL" || cdr.gateway.equals(dlrGatewayFilter, ignoreCase = true)
                    val matchesSearch = consoleSearchFilter.isBlank() ||
                        cdr.num.contains(consoleSearchFilter, ignoreCase = true) ||
                        cdr.countryName.contains(consoleSearchFilter, ignoreCase = true) ||
                        cdr.countryCode.contains(consoleSearchFilter, ignoreCase = true) ||
                        cdr.serviceName.contains(consoleSearchFilter, ignoreCase = true) ||
                        cdr.cli.contains(consoleSearchFilter, ignoreCase = true) ||
                        cdr.message.contains(consoleSearchFilter, ignoreCase = true)
                    matchesGateway && matchesSearch
                }.take(60)
            }

            if (filteredCdrs.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E344E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "অ্যাড করা দেশ ও সার্ভিসের লাইভ DLR SMS স্ক্যান হচ্ছে...",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
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
    val count: Int,
    val numberType: String = "Fresh",
    val platformTag: String = "✨ FB Fresh"
)

@Composable
fun UnixStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.11f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = color.copy(alpha = 0.9f),
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun UnixActiveNumberRow(
    prov: ProvisionedNumber,
    nowMillis: Long = System.currentTimeMillis(),
    context: Context
) {
    val isOtpReceived = !prov.otpCode.isNullOrEmpty()
    val isFbClone = prov.range.contains("Clone", ignoreCase = true)
    val isMarkedClone = isFbClone && prov.isMarked
    val remainingSec = remember(prov.expiresAt, nowMillis) {
        ((prov.expiresAt - nowMillis) / 1000L).coerceAtLeast(0L)
    }
    val isExpiredOrFailed = !isOtpReceived && (
        remainingSec <= 0L ||
        prov.status.equals("expired", ignoreCase = true) ||
        prov.status.equals("failed", ignoreCase = true)
    )
    val mins = remainingSec / 60
    val secs = remainingSec % 60
    val timerText = String.format(Locale.US, "%02d:%02d", mins, secs)

    Surface(
        color = when {
            isOtpReceived -> Color(0xFF05291A)
            isExpiredOrFailed -> Color(0xFF280C12) // Red failed/expired card background
            isMarkedClone -> Color(0xFF2B1C06) // Warm Yellow/Orange Highlight Card Background
            else -> Color(0xFF09131F)
        },
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(
            width = if (isMarkedClone || isOtpReceived || isExpiredOrFailed) 1.5.dp else 1.2.dp,
            color = when {
                isOtpReceived -> Color(0xFF00E676).copy(alpha = 0.8f)
                isExpiredOrFailed -> Color(0xFFEF4444).copy(alpha = 0.85f) // Vivid Red Border for expired/failed
                isMarkedClone -> Color(0xFFFFB300) // Vivid Amber-Yellow/Orange Border when marked
                else -> Color(0xFF1F3652)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = prov.number,
                            color = when {
                                isExpiredOrFailed -> Color(0xFFFCA5A5)
                                isMarkedClone -> Color(0xFFFFE082)
                                else -> Color.White
                            },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (isOtpReceived) {
                            Surface(
                                color = Color(0xFF00E676),
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    text = "OTP RCV ✓",
                                    color = Color.Black,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else if (isExpiredOrFailed) {
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(0.8.dp, Color(0xFFEF4444))
                            ) {
                                Text(
                                    text = "✕ EXPIRED",
                                    color = Color(0xFFF87171),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = Color(0xFF0284C7).copy(alpha = 0.22f),
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "⏱ $timerText",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // STRICTLY ONLY FOR FB CLONE NUMBERS: Interactive Mark / Unmark Tag (Yellow/Orange Highlight)
                        if (isFbClone) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (prov.isMarked) Color(0xFFFFB300) else Color(0xFFF59E0B).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (prov.isMarked) Color(0xFFFFE082) else Color(0xFFF59E0B).copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.clickable {
                                    UnixSmsManager.toggleMarkActiveNumber(prov.id)
                                    VibrationHelper.vibrateClick(context)
                                }
                            ) {
                                Text(
                                    text = if (prov.isMarked) "★ MARKED" else "☆ MARK",
                                    color = if (prov.isMarked) Color.Black else Color(0xFFFFB300),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        val countryInfo = remember(prov.iso, prov.country) {
                            com.example.util.AllCountriesProvider.findByCode(prov.iso)
                                ?: com.example.util.AllCountriesProvider.findByNameOrCode(prov.country)
                        }
                        val flag = countryInfo?.flag ?: "🌐"
                        val countryName = countryInfo?.name ?: prov.country
                        val isoShort = prov.iso.uppercase()

                        // Country badge
                        Surface(
                            color = Color(0xFF132338),
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(0.6.dp, Color(0xFF284264))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = flag, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$countryName [$isoShort]",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Service badge
                        val srvLabel = prov.range.ifEmpty { "FB Fresh" }
                        val srvColor = when {
                            srvLabel.contains("Clone", true) -> Color(0xFFF59E0B)
                            srvLabel.contains("Fresh", true) -> Color(0xFF00E676)
                            srvLabel.contains("Instagram", true) -> Color(0xFFE1306C)
                            srvLabel.contains("WhatsApp", true) -> Color(0xFF25D366)
                            else -> Color(0xFF38BDF8)
                        }
                        Surface(
                            color = srvColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(0.7.dp, srvColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = srvLabel,
                                color = srvColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Rate badge
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Text(
                                text = "৳${String.format(Locale.US, "%.2f", prov.rate)}",
                                color = Color(0xFF00E676),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            ClipboardHelper.copyToClipboard(context, prov.number.replace("+", ""), "Phone Number")
                            Toast.makeText(context, "নাম্বার কপি হয়েছে: ${prov.number}", Toast.LENGTH_SHORT).show()
                            VibrationHelper.vibrateSuccess(context)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            UnixSmsManager.removeActiveNumber(prov.id)
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = if (isExpiredOrFailed) Color(0xFFEF4444) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isOtpReceived) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(9.dp),
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
                                text = "✓ OTP RECEIVED (+৳${String.format(Locale.US, "%.2f", prov.rate)} Added):",
                                color = Color(0xFF00E676),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = prov.otpCode ?: "",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.8.sp
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
                            Text("COPY OTP", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                if (!prov.otpMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = prov.otpMessage,
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            } else if (isExpiredOrFailed) {
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✕ সময় শেষ (Expired / Failed) — কোনো OTP আসেনি",
                            color = Color(0xFFFCA5A5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = Color(0xFFEF4444),
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.clickable {
                                UnixSmsManager.removeActiveNumber(prov.id)
                            }
                        ) {
                            Text(
                                text = "মুছুন",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = Color(0xFF0E1D2F),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, Color(0xFF1F3B5C))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Waiting for 5–8 Digit OTP on Lamix • Hadi • Unix...",
                            color = Color(0xFF93C5FD),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

fun maskPhoneNumberMiddle(rawNumber: String): String {
    return UnixSmsManager.maskPhoneNumberMiddle4(rawNumber)
}

@Composable
fun UnixCdrMessageCard(
    cdr: UnixSmsCdrRecord,
    context: Context
) {
    val maskedNumber = remember(cdr.num) { maskPhoneNumberMiddle(cdr.num) }
    val resolvedCountry = remember(cdr.num, cdr.countryName, cdr.countryFlag, cdr.countryCode) {
        val info = if (cdr.countryCode.isNotBlank()) {
            com.example.util.AllCountriesProvider.findByCode(cdr.countryCode)
        } else {
            com.example.util.AllCountriesProvider.findByPhoneNumber(cdr.num)
                ?: com.example.util.AllCountriesProvider.detectCountry(cdr.countryName)
        }
        Triple(
            info?.flag ?: cdr.countryFlag.ifEmpty { "🌐" },
            info?.name ?: cdr.countryName.ifEmpty { "Global" },
            (info?.code ?: cdr.countryCode).uppercase()
        )
    }
    val (countryFlag, countryName, isoCode) = resolvedCountry

    val srvText = cdr.serviceName.ifEmpty { cdr.cli.ifEmpty { "SMS" } }
    val srvColor = when {
        srvText.contains("Facebook", true) || srvText.contains("FB", true) -> Color(0xFF1877F2)
        srvText.contains("WhatsApp", true) || srvText.contains("WA", true) -> Color(0xFF25D366)
        srvText.contains("Instagram", true) || srvText.contains("IG", true) -> Color(0xFFE1306C)
        else -> Color(0xFF00E5FF)
    }

    val gwLabel = when (cdr.gateway.uppercase()) {
        "LAMIX" -> "⚡ LAMIX"
        "HADI" -> "🔥 HADI"
        else -> "🌐 UNIX"
    }
    val gwColor = when (cdr.gateway.uppercase()) {
        "LAMIX" -> Color(0xFF00E5FF)
        "HADI" -> Color(0xFFF59E0B)
        else -> Color(0xFFA78BFA)
    }

    Surface(
        color = Color(0xFF0B1420),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.1.dp, Color(0xFF1D334D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Masked Number Pill + Service Badge + Country Badge + Gateway Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Masked Number Pill
                Surface(
                    color = Color(0xFF062335),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00B8D4)),
                    modifier = Modifier.clickable {
                        ClipboardHelper.copyToClipboard(context, maskedNumber, "Number")
                        Toast.makeText(context, "নাম্বার কপি হয়েছে: $maskedNumber", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = maskedNumber,
                            color = Color(0xFF00E5FF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Service Badge
                    Surface(
                        color = srvColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, srvColor.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = srvText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Country Badge
                    Surface(
                        color = Color(0xFF132235),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, Color(0xFF284264))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = countryFlag, fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isoCode.isNotBlank()) "$countryName $isoCode" else countryName,
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Second Row: Gateway Badge + Sender CLI + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = gwColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(0.8.dp, gwColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = gwLabel,
                            color = gwColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = "● LIVE DLR",
                            color = Color(0xFF00E676),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }

                Text(
                    text = cdr.dt.ifEmpty { "just now" },
                    color = Color(0xFF00E676),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Full SMS Message Box (Clean, high-contrast full SMS view)
            Surface(
                color = Color(0xFF070D16),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, Color(0xFF16263B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cdr.message,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
