package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AccountCheckerManager
import com.example.service.AccountCheckerMode
import com.example.service.AccountCreateApiManager
import com.example.service.KopEngine2Manager
import com.example.service.OverlayStateManager
import com.example.service.UnixSmsManager
import com.example.service.VirtualNumberManager
import com.example.ui.components.AccountStatusHighlightBadge
import com.example.util.AllCountriesProvider
import com.example.util.ClipboardHelper
import com.example.util.OtpNotificationHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CreatedAccountItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uid: String,
    val name: String,
    val phoneNumber: String,
    val password: String,
    val token: String,
    val cookie: String,
    val twoFaSecret: String,
    val accountType: String,     // "FRESH" or "OLD"
    val badgeLabel: String,      // "🟢 NEW (FRESH)" or "🔴 OLD (ACCOUNT)"
    val panelUsed: String,       // "Premium Numbers", "Kop Engine 1", "Kop Engine 2", "Txt File"
    val rangeUsed: String,
    val threadId: Int,
    val userAgent: String,
    val proxyUsed: String,
    val country: String,
    val status: String = "LOGGED IN",
    val otpCode: String? = null,
    val smsFetched: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun formatOutput(mode: String): String {
        return when (mode) {
            "DEFAULT" -> "$name|$phoneNumber|$uid|$password|$cookie"
            "FULL" -> "$name|$phoneNumber|$uid|$password|$cookie|$token|$twoFaSecret"
            "STD" -> "$uid|$password|$cookie|$token"
            "COOKIE" -> "$uid|$password|$cookie"
            "MIN" -> "$phoneNumber|$password|$uid"
            else -> "$name|$phoneNumber|$uid|$password|$cookie"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by OverlayStateManager.uiState.collectAsStateWithLifecycle()
    val selectedPanelIndex by VirtualNumberManager.selectedPanel.collectAsStateWithLifecycle()
    val vnState by VirtualNumberManager.state.collectAsStateWithLifecycle()
    val kop2State by KopEngine2Manager.state.collectAsStateWithLifecycle()
    val unixState by UnixSmsManager.state.collectAsStateWithLifecycle()
    val checkerState by AccountCheckerManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Live 3s auto-sync with Virtual Numbers section panels & ranges
    var syncCountdown by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        AccountCheckerManager.init(context)
        VirtualNumberManager.init(context)
        KopEngine2Manager.init(context)
        UnixSmsManager.init(context)
        VirtualNumberManager.fetchActiveRanges()
        KopEngine2Manager.fetchActiveRanges()
        while (true) {
            delay(1000L)
            if (syncCountdown <= 1) {
                syncCountdown = 3
                VirtualNumberManager.fetchActiveRanges()
                KopEngine2Manager.fetchActiveRanges()
            } else {
                syncCountdown -= 1
            }
        }
    }

    // 1. Password state (Default fixed: Okabye#570)
    var passwordText by remember { mutableStateOf(state.savedPasswordText.ifBlank { "Okabye#570" }) }
    var isEditingPassword by remember { mutableStateOf(false) }

    // 2. Source Panel Mode synced directly with Virtual Numbers Section's selectedPanelIndex
    // "VIRTUAL_PANEL" uses selectedPanelIndex (0 = Premium Numbers, 1 = Kop Engine 1, 2 = Kop Engine 2)
    // "TXT_UPLOAD" uses uploaded .txt file
    var useTxtUploadMode by remember { mutableStateOf(false) }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    var uploadedNumbersCount by remember { mutableIntStateOf(0) }
    var uploadedNumbersList by remember { mutableStateOf<List<String>>(emptyList()) }

    // Country picker for Premium Numbers (Panel 0)
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }

    // 3. User-Agent Engine State
    var uaMode by remember { mutableStateOf("AUTO_ROTATE") } // "FB_KATANA", "FB_LITE", "CHROME_MBASIC", "AUTO_ROTATE", "CUSTOM_UA"
    var currentPreviewUa by remember { mutableStateOf(generateRealisticUserAgent("FB_KATANA")) }
    var customUaText by remember { mutableStateOf("") }

    LaunchedEffect(uaMode) {
        if (uaMode != "CUSTOM_UA") {
            currentPreviewUa = generateRealisticUserAgent(uaMode)
        }
    }

    // 4. Multi-Threading & Account Amount & Proxy State
    var threadCountText by remember { mutableStateOf("5") }
    var accountAmountText by remember { mutableStateOf("5") }
    var proxyMode by remember { mutableStateOf("AUTO_SAVED") } // "AUTO_SAVED", "SET_PROXY", "NO_PROXY"
    var customProxyHost by remember { mutableStateOf("") }
    var customProxyPort by remember { mutableStateOf("") }
    var customProxyUser by remember { mutableStateOf("") }
    var customProxyPass by remember { mutableStateOf("") }

    // 5. Execution & Output State
    var isCreating by remember { mutableStateOf(false) }
    var creationStepText by remember { mutableStateOf("") }
    var consoleLogs by remember { mutableStateOf(listOf<String>()) }
    var createdAccountsList by remember { mutableStateOf(listOf<CreatedAccountItem>()) }
    var creationCompleted by remember { mutableStateOf(false) }
    var outputFormatMode by remember { mutableStateOf("DEFAULT") } // "DEFAULT" (name|number|uid|password|cookie), "FULL", "STD", "COOKIE", "MIN"
    var showRawOutputBox by remember { mutableStateOf(true) }

    // Auto-match incoming OTPs from all 3 Virtual Number panels to created accounts in real time!
    LaunchedEffect(
        unixState.activeNumbers,
        unixState.uploadedNumbers,
        vnState.provisionedNumbers,
        vnState.broadcastFeed,
        kop2State.provisionedNumbers,
        kop2State.broadcastFeed
    ) {
        if (createdAccountsList.isEmpty()) return@LaunchedEffect
        var updatedAny = false
        var latestOtpToCopy: Pair<String, String>? = null

        val updatedList = createdAccountsList.map { acc ->
            if (!acc.otpCode.isNullOrEmpty()) return@map acc

            // Check Panel 0 (UnixSmsManager)
            val unixMatch = unixState.activeNumbers.firstOrNull {
                !it.otpCode.isNullOrEmpty() && UnixSmsManager.isPhoneNumberMatch(it.number, acc.phoneNumber)
            } ?: unixState.uploadedNumbers.firstOrNull {
                !it.otpCode.isNullOrEmpty() && UnixSmsManager.isPhoneNumberMatch(it.number, acc.phoneNumber)
            }?.let { up ->
                com.example.data.model.ProvisionedNumber(
                    number = up.number,
                    country = up.countryName,
                    operator = up.gateway,
                    iso = up.countryCode,
                    status = "success",
                    otpCode = up.otpCode,
                    otpMessage = up.otpMessage
                )
            }

            // Check Panel 1 (VirtualNumberManager / Kop Engine 1)
            val vnMatch = vnState.provisionedNumbers.firstOrNull {
                !it.otpCode.isNullOrEmpty() && VirtualNumberManager.isPhoneNumberMatch(it.number, acc.phoneNumber)
            }

            // Check Panel 2 (KopEngine2Manager / Kop Engine 2)
            val kop2Match = kop2State.provisionedNumbers.firstOrNull {
                !it.otpCode.isNullOrEmpty() && KopEngine2Manager.isPhoneNumberMatch(it.number, acc.phoneNumber)
            }

            val matchedProv = unixMatch ?: vnMatch ?: kop2Match
            if (matchedProv != null && !matchedProv.otpCode.isNullOrEmpty()) {
                updatedAny = true
                val code = matchedProv.otpCode!!
                val fullMsg = matchedProv.otpMessage ?: "<#> $code is your Facebook confirmation code"
                latestOtpToCopy = code to acc.phoneNumber
                acc.copy(otpCode = code, smsFetched = fullMsg)
            } else {
                acc
            }
        }

        if (updatedAny) {
            createdAccountsList = updatedList
            latestOtpToCopy?.let { (code, phone) ->
                ClipboardHelper.copyToClipboard(context, code, "OTP Code")
                VibrationHelper.vibrateSuccess(context)
                OtpNotificationHelper.showOtpNotification(
                    context = context,
                    phoneNumber = phone,
                    otpCode = code,
                    fullMessage = "Facebook OTP: $code for $phone"
                )
            }
        }
    }

    val txtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                uploadedNumbersList = lines
                uploadedNumbersCount = lines.size
                uploadedFileName = uri.lastPathSegment ?: "numbers.txt"
                useTxtUploadMode = true
                Toast.makeText(context, "✓ Loaded $uploadedNumbersCount numbers from $uploadedFileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070E17))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // HEADER BANNER
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ManageAccounts,
                                contentDescription = "Account Engine",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "FB AUTO CREATE & CHECKER",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Synced with Virtual Numbers • Multi-Thread • Auto OTP",
                                fontSize = 10.5.sp,
                                color = Color(0xFF00E676)
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF0D1B2A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E3A56))
                    ) {
                        Text(
                            text = "Sync: ${syncCountdown}s",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (!state.accountSectionEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1216)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Disabled", tint = Color(0xFFFF5252), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Account Creation Disabled by Admin",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                    }
                }
            }
        } else {
            // 1. PASSWORD SAVED OPTION
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. Saved Account Password",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Default Fixed: Okabye#570",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = passwordText,
                                onValueChange = {
                                    passwordText = it
                                },
                                enabled = isEditingPassword,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color(0xFF00E676),
                                    focusedBorderColor = Color(0xFF00E676),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    disabledBorderColor = Color(0xFF1E293B)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    ClipboardHelper.copyToClipboard(context, passwordText, "Password")
                                    Toast.makeText(context, "Password Copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Password", tint = Color(0xFF38BDF8))
                            }
                            IconButton(
                                onClick = {
                                    isEditingPassword = !isEditingPassword
                                    if (!isEditingPassword) {
                                        OverlayStateManager.setSavedPasswordText(context, passwordText)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isEditingPassword) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = "Edit Password",
                                    tint = if (isEditingPassword) Color(0xFF00E676) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            // 2. PANEL & LIVE RANGE AUTO-SYNC WITH VIRTUAL NUMBERS SECTION
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
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
                                text = "2. Virtual Number Panel & Range (Auto-Synced)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "LIVE SYNC",
                                    color = Color(0xFF00E676),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Panel Selector Row (Synced directly with VirtualNumberManager.selectedPanel)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Panel 0: Premium Numbers
                            val isPrem = !useTxtUploadMode && selectedPanelIndex == 0
                            Button(
                                onClick = {
                                    useTxtUploadMode = false
                                    VirtualNumberManager.setSelectedPanel(0)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPrem) Color(0xFF0284C7) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Text(
                                    text = "Premium",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            // Panel 1: Kop Engine 1 (Api Number)
                            val isKop1 = !useTxtUploadMode && selectedPanelIndex == 1
                            Button(
                                onClick = {
                                    useTxtUploadMode = false
                                    VirtualNumberManager.setSelectedPanel(1)
                                    VirtualNumberManager.fetchActiveRanges()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isKop1) Color(0xFF10B981) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1.15f)
                            ) {
                                Text(
                                    text = "Kop Engine 1",
                                    color = if (isKop1) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            // Panel 2: Kop Engine 2 (Api Number)
                            val isKop2 = !useTxtUploadMode && selectedPanelIndex == 2
                            Button(
                                onClick = {
                                    useTxtUploadMode = false
                                    VirtualNumberManager.setSelectedPanel(2)
                                    KopEngine2Manager.fetchActiveRanges()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isKop2) Color(0xFF06B6D4) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1.15f)
                            ) {
                                Text(
                                    text = "Kop Engine 2",
                                    color = if (isKop2) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            // Option 4: Txt File
                            Button(
                                onClick = {
                                    if (state.uploadNumberTxtLocked) {
                                        Toast.makeText(context, "🔒 Upload Txt is locked by Admin.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        txtFileLauncher.launch("text/plain")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (useTxtUploadMode) Color(0xFF00E676) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text(
                                    text = if (state.uploadNumberTxtLocked) "🔒 Txt" else "📁 Txt",
                                    color = if (useTxtUploadMode) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (useTxtUploadMode) {
                            Surface(
                                color = Color(0xFF070E17),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E676)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "📁 Active Txt File: ${uploadedFileName ?: "numbers.txt"}",
                                            color = Color(0xFF00E676),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$uploadedNumbersCount phone numbers loaded for account creation",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp
                                        )
                                    }
                                    TextButton(onClick = { txtFileLauncher.launch("text/plain") }) {
                                        Text("Change", color = Color(0xFF38BDF8), fontSize = 11.sp)
                                    }
                                }
                            }
                        } else if (selectedPanelIndex == 0) {
                            // PANEL 0: PREMIUM NUMBERS (UnixSmsManager)
                            val selectedCountryCode = unixState.selectedCountry
                            val availablePoolCount = unixState.uploadedNumbers.count {
                                !it.isUsed && (selectedCountryCode == "ALL" || it.countryCode.equals(selectedCountryCode, ignoreCase = true))
                            }
                            val activeTakenCount = unixState.activeNumbers.size

                            Surface(
                                color = Color(0xFF0B141E),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "ACTIVE PANEL: PREMIUM NUMBERS",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Pool: $availablePoolCount | Active: $activeTakenCount",
                                                color = Color(0xFF00E676),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Country Selector synced with UnixSmsManager.selectedCountry
                                    OutlinedCard(
                                        onClick = { showCountryPicker = true },
                                        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF070E17)),
                                        border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val cInfo = AllCountriesProvider.allCountries.firstOrNull {
                                                it.code.equals(selectedCountryCode, ignoreCase = true)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = cInfo?.flag ?: "🌐", fontSize = 18.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(text = "Selected Country (Synced with Virtual Numbers):", fontSize = 9.5.sp, color = Color(0xFF90A4AE))
                                                    Text(
                                                        text = if (selectedCountryCode == "ALL") "ALL COUNTRIES (Auto Pool)" else "${cInfo?.name ?: selectedCountryCode} (${cInfo?.dialCode ?: ""})",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF38BDF8))
                                        }
                                    }
                                }
                            }
                        } else if (selectedPanelIndex == 1) {
                            // PANEL 1: KOP ENGINE 1 (Zenex Network Live Ranges & Target Range)
                            Surface(
                                color = Color(0xFF0B141E),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "KOP ENGINE 1 (API NUMBER) — LIVE RANGE",
                                                color = Color(0xFF34D399),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Text(
                                            text = "Active Numbers: ${vnState.provisionedNumbers.size}",
                                            color = Color(0xFF81D4FA),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Target Range Input synced with VirtualNumberManager.state.targetRange
                                    OutlinedTextField(
                                        value = vnState.targetRange,
                                        onValueChange = { VirtualNumberManager.setTargetRange(it) },
                                        label = { Text("Target Range (Synced with Virtual Numbers Kop 1)", fontSize = 10.sp) },
                                        placeholder = { Text("e.g. 237627XXX", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF10B981),
                                            unfocusedBorderColor = Color(0xFF1E3A56)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (vnState.activeRanges.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Live Active Ranges from Virtual Numbers (ট্যাপ করে সিলেক্ট করুন):",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            vnState.activeRanges.take(8).forEach { item ->
                                                val isSelected = vnState.targetRange == item.range
                                                Surface(
                                                    color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFF152232),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) Color(0xFF10B981) else Color(0xFF1E3A56)
                                                    ),
                                                    modifier = Modifier.clickable {
                                                        VirtualNumberManager.setTargetRange(item.range)
                                                        Toast.makeText(context, "✓ Kop 1 Range Set: ${item.range}", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Text(
                                                        text = "${item.range} (${item.service}${if (item.hits > 0) " • ${item.hits}" else ""})",
                                                        color = if (isSelected) Color(0xFF34D399) else Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // PANEL 2: KOP ENGINE 2 (MK Network v3 Live Radar Ranges & Target Range)
                            Surface(
                                color = Color(0xFF0B141E),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF06B6D4)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF06B6D4)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "KOP ENGINE 2 (MK API) — LIVE RADAR RANGE",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Text(
                                            text = "Active Numbers: ${kop2State.provisionedNumbers.size}",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Target Range Input synced with KopEngine2Manager.state.targetRange
                                    OutlinedTextField(
                                        value = kop2State.targetRange,
                                        onValueChange = { KopEngine2Manager.setTargetRange(it) },
                                        label = { Text("Target Range (Synced with Virtual Numbers Kop 2)", fontSize = 10.sp) },
                                        placeholder = { Text("e.g. 260950XXX", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF06B6D4),
                                            unfocusedBorderColor = Color(0xFF1E3A56)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (kop2State.activeRanges.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Live Active Signals / Ranges from Kop Engine 2 (ট্যাপ করে সিলেক্ট করুন):",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            kop2State.activeRanges.take(8).forEach { item ->
                                                val isSelected = kop2State.targetRange == item.range
                                                Surface(
                                                    color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF152232),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E3A56)
                                                    ),
                                                    modifier = Modifier.clickable {
                                                        KopEngine2Manager.setTargetRange(item.range)
                                                        Toast.makeText(context, "✓ Kop 2 Range Set: ${item.range}", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    val label = if (item.hits > 0) "${item.range} (${item.service} • ${item.hits})" else "${item.range} (${item.service})"
                                                    Text(
                                                        text = label,
                                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

            // 3. USER-AGENT ENGINE (KATANA / LITE / MBASIC / AUTO ROTATE / CUSTOM)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "3. User-Agent (UA) Fingerprint Engine",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = Color(0xFF0D1B2A),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    currentPreviewUa = generateRealisticUserAgent(uaMode)
                                    Toast.makeText(context, "🔄 New User-Agent Generated!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rotate UA", color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val uaModes = listOf(
                            "AUTO_ROTATE" to "🔄 Auto Rotate (Per Thread)",
                            "FB_KATANA" to "📱 FB Katana (FB4A)",
                            "FB_LITE" to "⚡ FB Lite (Dalvik)",
                            "CHROME_MBASIC" to "🌐 Chrome mbasic",
                            "CUSTOM_UA" to "✏️ Custom UA"
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uaModes.forEach { (key, label) ->
                                val isSel = uaMode == key
                                Surface(
                                    color = if (isSel) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF152232),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF00E676) else Color(0xFF334155)),
                                    modifier = Modifier.clickable { uaMode = key }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color(0xFF00E676) else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (uaMode == "CUSTOM_UA") {
                            OutlinedTextField(
                                value = customUaText,
                                onValueChange = { customUaText = it },
                                label = { Text("Enter Custom User-Agent String", fontSize = 10.sp) },
                                placeholder = { Text("Mozilla/5.0 (Linux; Android 14...) [FBAN/FB4A...]", fontSize = 10.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00E676),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Surface(
                                color = Color(0xFF070E17),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = currentPreviewUa,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            ClipboardHelper.copyToClipboard(context, currentPreviewUa, "User-Agent")
                                            Toast.makeText(context, "User-Agent Copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy UA", tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. HOW MANY THREADS + HOW MANY ACCOUNTS + PROXY
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "4. Multi-Threading (How Many Threads) & Proxy",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Row for How Many Threads & How Many Accounts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("How Many Threads (Speed):", color = Color(0xFF38BDF8), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = threadCountText,
                                    onValueChange = { threadCountText = it.filter { ch -> ch.isDigit() } },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("How Many Accounts:", color = Color(0xFF00E676), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = accountAmountText,
                                    onValueChange = { accountAmountText = it.filter { ch -> ch.isDigit() } },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp)) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00E676),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Thread Preset Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("Threads:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            listOf("1", "3", "5", "10", "20", "30").forEach { tVal ->
                                val isSel = threadCountText == tVal
                                Surface(
                                    color = if (isSel) Color(0xFF0284C7) else Color(0xFF152232),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF334155)),
                                    modifier = Modifier.clickable { threadCountText = tVal }
                                ) {
                                    Text(
                                        text = "${tVal}T",
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Proxy Mode Selection
                        Text("Proxy Configuration:", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "AUTO_SAVED" to "Auto-Saved Proxy",
                                "SET_PROXY" to "Custom Proxy",
                                "NO_PROXY" to "No Proxy"
                            ).forEach { (mode, label) ->
                                val isSel = proxyMode == mode
                                OutlinedButton(
                                    onClick = { proxyMode = mode },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) Color(0xFF0284C7).copy(alpha = 0.25f) else Color.Transparent
                                    ),
                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = label, fontSize = 9.5.sp, color = if (isSel) Color(0xFF38BDF8) else Color.White, maxLines = 1)
                                }
                            }
                        }

                        if (proxyMode == "SET_PROXY") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = customProxyHost,
                                    onValueChange = { customProxyHost = it },
                                    label = { Text("Proxy Host / IP", fontSize = 10.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                    modifier = Modifier.weight(2f)
                                )
                                OutlinedTextField(
                                    value = customProxyPort,
                                    onValueChange = { customProxyPort = it },
                                    label = { Text("Port", fontSize = 10.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = customProxyUser,
                                    onValueChange = { customProxyUser = it },
                                    label = { Text("Username (Optional)", fontSize = 10.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customProxyPass,
                                    onValueChange = { customProxyPass = it },
                                    label = { Text("Password (Optional)", fontSize = 10.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else if (proxyMode == "AUTO_SAVED") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "🌐 Active Saved Proxy: ${state.proxyState.profileName} (${state.proxyState.statusText})",
                                fontSize = 10.sp,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                }
            }

            // 5. ACCOUNT CHECKER.PY FILTER (SYNCED WITH ACCOUNTCHECKERMANAGER)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "5. Account Checker.py Filter",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(
                                    text = "🔒 checker.py Locked | 🟢 ${checkerState.freshFoundCount}  🔴 ${checkerState.oldFoundCount}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val modes = listOf(
                                AccountCheckerMode.FRESH_ONLY to "🟢 Only Fresh (New)",
                                AccountCheckerMode.OLD_ONLY to "🔴 Only Old (Exist)",
                                AccountCheckerMode.BOTH to "⚡ Both (Fresh+Old)"
                            )
                            modes.forEach { (m, label) ->
                                val isSel = checkerState.mode == m
                                val activeColor = when (m) {
                                    AccountCheckerMode.FRESH_ONLY -> Color(0xFF00E676)
                                    AccountCheckerMode.OLD_ONLY -> Color(0xFFFF5252)
                                    AccountCheckerMode.BOTH -> Color(0xFF38BDF8)
                                }
                                OutlinedButton(
                                    onClick = { AccountCheckerManager.setMode(m) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) activeColor.copy(alpha = 0.2f) else Color.Transparent
                                    ),
                                    border = BorderStroke(1.dp, if (isSel) activeColor else Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) activeColor else Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. START MULTI-THREAD CREATION BUTTON
            item {
                val activePanelName = if (useTxtUploadMode) {
                    "Txt File (${uploadedFileName ?: "numbers.txt"})"
                } else when (selectedPanelIndex) {
                    0 -> "Premium Numbers (${unixState.selectedCountry})"
                    1 -> "Kop Engine 1 (${vnState.targetRange.ifEmpty { "Auto Range" }})"
                    else -> "Kop Engine 2 (${kop2State.targetRange.ifEmpty { "Auto Range" }})"
                }

                Button(
                    onClick = {
                        val amount = (accountAmountText.toIntOrNull() ?: 5).coerceIn(1, 200)
                        val threads = (threadCountText.toIntOrNull() ?: 5).coerceIn(1, 50)
                        isCreating = true
                        creationCompleted = false
                        consoleLogs = listOf(
                            "🚀 Starting Multi-Thread Engine: $amount Accounts | $threads Threads",
                            "📡 Source Panel: $activePanelName",
                            "🛡️ Account Checker Mode: ${checkerState.mode.shortBadge}"
                        )
                        creationStepText = "Running $threads Threads on $activePanelName..."

                        scope.launch(Dispatchers.IO) {
                            val semaphore = Semaphore(threads)
                            val logMutex = Mutex()
                            val resultsMutex = Mutex()
                            val results = mutableListOf<CreatedAccountItem>()
                            val usedNumbersInBatch = mutableSetOf<String>()

                            suspend fun appendLog(msg: String) {
                                logMutex.withLock {
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                                    val updated = (consoleLogs + "[$timeStr] $msg").takeLast(40)
                                    withContext(Dispatchers.Main) {
                                        consoleLogs = updated
                                        creationStepText = msg
                                    }
                                }
                            }

                            coroutineScope {
                                val jobs = (1..amount).map { taskIdx ->
                                    async(Dispatchers.IO) {
                                        semaphore.withPermit {
                                            val threadNum = ((taskIdx - 1) % threads) + 1
                                            val maxRetries = checkerState.maxRetries.coerceAtLeast(4)
                                            var chosenPhone = ""
                                            var chosenCountry = "Global"
                                            var chosenRange = ""
                                            var checkVerdict = AccountCheckerManager.checkPhoneNumber("0000000")

                                            for (attempt in 1..maxRetries) {
                                                // 1. Obtain number from the active Virtual Number panel or Txt file
                                                var rawCandidate: String? = null
                                                if (useTxtUploadMode && uploadedNumbersList.isNotEmpty()) {
                                                    val idx = ((taskIdx - 1) * maxRetries + (attempt - 1)) % uploadedNumbersList.size
                                                    rawCandidate = uploadedNumbersList[idx]
                                                    chosenCountry = "Txt Upload"
                                                    chosenRange = uploadedFileName ?: "numbers.txt"
                                                } else if (selectedPanelIndex == 0) {
                                                    // Panel 0: Premium Numbers (UnixSmsManager)
                                                    chosenRange = "Premium (${unixState.selectedCountry})"
                                                    val prov = UnixSmsManager.requestSingleNumberForAccount(context, unixState.selectedCountry)
                                                    if (prov != null) {
                                                        rawCandidate = prov.number
                                                        chosenCountry = prov.country
                                                    } else {
                                                        val cInfo = AllCountriesProvider.allCountries.firstOrNull {
                                                            it.code.equals(unixState.selectedCountry, ignoreCase = true)
                                                        } ?: AllCountriesProvider.allCountries.first()
                                                        chosenCountry = cInfo.name
                                                        rawCandidate = "${cInfo.dialCode}${(100000000..999999999).random()}"
                                                    }
                                                } else if (selectedPanelIndex == 1) {
                                                    // Panel 1: Kop Engine 1 (VirtualNumberManager)
                                                    val targetR = vnState.targetRange.ifEmpty {
                                                        vnState.activeRanges.firstOrNull()?.range ?: "237627XXX"
                                                    }
                                                    chosenRange = targetR
                                                    // Check unused existing provisioned number first or request live from API
                                                    val existing = resultsMutex.withLock {
                                                        vnState.provisionedNumbers.firstOrNull { p ->
                                                            p.number !in usedNumbersInBatch
                                                        }
                                                    }
                                                    val prov = existing ?: VirtualNumberManager.requestSingleNumberForAccount(context, targetR)
                                                    if (prov != null) {
                                                        rawCandidate = prov.number
                                                        chosenCountry = prov.country
                                                    } else {
                                                        rawCandidate = generateNumberFromRangePattern(targetR)
                                                        chosenCountry = "Kop Engine 1"
                                                    }
                                                } else {
                                                    // Panel 2: Kop Engine 2 (KopEngine2Manager)
                                                    val targetR = kop2State.targetRange.ifEmpty {
                                                        kop2State.activeRanges.firstOrNull()?.range ?: "260950XXX"
                                                    }
                                                    chosenRange = targetR
                                                    val existing = resultsMutex.withLock {
                                                        kop2State.provisionedNumbers.firstOrNull { p ->
                                                            p.number !in usedNumbersInBatch
                                                        }
                                                    }
                                                    val prov = existing ?: KopEngine2Manager.requestSingleNumberForAccount(context, targetR)
                                                    if (prov != null) {
                                                        rawCandidate = prov.number
                                                        chosenCountry = prov.country
                                                    } else {
                                                        rawCandidate = generateNumberFromRangePattern(targetR)
                                                        chosenCountry = "Kop Engine 2"
                                                    }
                                                }

                                                val candidateNum = if (!rawCandidate.isNullOrBlank()) {
                                                    if (!rawCandidate.startsWith("+")) "+$rawCandidate" else rawCandidate
                                                } else "+237627${(100000..999999).random()}"

                                                val isDuplicate = resultsMutex.withLock {
                                                    if (candidateNum in usedNumbersInBatch) true else {
                                                        usedNumbersInBatch.add(candidateNum)
                                                        false
                                                    }
                                                }
                                                if (isDuplicate && attempt < maxRetries) continue

                                                appendLog("[T-$threadNum] Checking $candidateNum via account_checker.py...")
                                                val res = AccountCheckerManager.checkPhoneNumber(candidateNum, forceRefresh = false)
                                                chosenPhone = candidateNum
                                                checkVerdict = res

                                                if (AccountCheckerManager.matchesSelectedMode(res)) {
                                                    appendLog("[T-$threadNum] ✓ Match ${res.badgeLabel} -> $candidateNum")
                                                    break
                                                } else {
                                                    appendLog("[T-$threadNum] ↻ Skip ($candidateNum is ${res.status}), retrying #$attempt...")
                                                }
                                            }

                                            // 2. Build User-Agent for this thread/account
                                            val effectiveUa = when (uaMode) {
                                                "CUSTOM_UA" -> customUaText.ifBlank { currentPreviewUa }
                                                "AUTO_ROTATE" -> generateRealisticUserAgent("AUTO_ROTATE")
                                                else -> generateRealisticUserAgent(uaMode)
                                            }

                                            val proxyString = when (proxyMode) {
                                                "NO_PROXY" -> "Direct (No Proxy)"
                                                "SET_PROXY" -> if (customProxyHost.isNotBlank()) "$customProxyHost:$customProxyPort" else "Custom Proxy"
                                                else -> state.proxyState.profileName
                                            }

                                             val engineName = if (useTxtUploadMode) "zenex" else when (selectedPanelIndex) {
                                                 0 -> "unix"
                                                 1 -> "zenex"
                                                 else -> "mk"
                                             }
                                             appendLog("[T-$threadNum] 🌐 Calling API /api/external/account-create for $chosenPhone...")
                                             val apiRes = AccountCreateApiManager.createAccount(
                                                 number = chosenPhone,
                                                 engine = engineName,
                                             password = passwordText,
                                                 clientKey = vnState.clientKey,
                                                 secretKey = kop2State.clientKey
                                             )
                                             val uid = apiRes.uid
                                             val fullName = apiRes.name
                                             val token = apiRes.token
                                             val cookie = apiRes.cookie
                                             val twoFa = apiRes.twoFaSecret

                                            // Check if OTP is already present in panel for this phone number
                                            val existingOtpPair = findExistingOtpForPhone(
                                                phone = chosenPhone,
                                                unixState = unixState,
                                                vnState = vnState,
                                                kop2State = kop2State
                                            )

                                            val panelLabel = if (useTxtUploadMode) "Txt File" else when (selectedPanelIndex) {
                                                0 -> "Premium Numbers"
                                                1 -> "Kop Engine 1"
                                                else -> "Kop Engine 2"
                                            }

                                            val createdItem = CreatedAccountItem(
                                                uid = uid,
                                                name = fullName,
                                                phoneNumber = chosenPhone,
                                                password = passwordText,
                                                token = token,
                                                cookie = cookie,
                                                twoFaSecret = twoFa,
                                                accountType = checkVerdict.status,
                                                badgeLabel = checkVerdict.badgeLabel,
                                                panelUsed = panelLabel,
                                                rangeUsed = chosenRange,
                                                threadId = threadNum,
                                                userAgent = effectiveUa,
                                                proxyUsed = proxyString,
                                                country = chosenCountry,
                                                otpCode = existingOtpPair?.first,
                                                smsFetched = existingOtpPair?.second
                                            )

                                            resultsMutex.withLock {
                                                results.add(createdItem)
                                                val snapshot = results.toList()
                                                withContext(Dispatchers.Main) {
                                                    createdAccountsList = snapshot
                                                }
                                            }
                                            appendLog("[T-$threadNum] 🎉 Created UID: $uid ($chosenPhone)")
                                        }
                                    }
                                }
                                jobs.awaitAll()
                            }

                            withContext(Dispatchers.Main) {
                                isCreating = false
                                creationCompleted = true
                                VibrationHelper.vibrateSuccess(context)
                                Toast.makeText(
                                    context,
                                    "✓ Created ${results.size} Accounts using $threads Threads!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isCreating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("start_multi_thread_create_btn")
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = creationStepText,
                            color = Color.Black,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START MULTI-THREAD CREATE (${threadCountText.ifEmpty { "5" }} THREADS)",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // LIVE MULTI-THREAD CONSOLE LOGS
            if (consoleLogs.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF050A10)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💻 MULTI-THREAD EXECUTION CONSOLE",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Clear",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.clickable { consoleLogs = emptyList() }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            consoleLogs.takeLast(8).forEach { line ->
                                Text(
                                    text = line,
                                    color = if (line.contains("🎉") || line.contains("✓")) Color(0xFF00E676) else Color(0xFFCBD5E1),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // 7. ACCOUNT OUTPUT SECTION (FORMAT SELECTOR + RAW OUTPUT BOX + CARDS)
            if (createdAccountsList.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "📦 ACCOUNT OUTPUT (${createdAccountsList.size} Accounts)",
                                        color = Color(0xFF00E676),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "🟢 Fresh: ${createdAccountsList.count { it.accountType == "FRESH" }} | 🔴 Old: ${createdAccountsList.count { it.accountType == "OLD" }} | 📩 OTP: ${createdAccountsList.count { !it.otpCode.isNullOrEmpty() }}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Create More (Auto-Logout previous batch)
                                    Button(
                                        onClick = {
                                            createdAccountsList = createdAccountsList.map { it.copy(status = "LOGGED OUT") }
                                            Toast.makeText(context, "✓ Previous accounts logged out. Ready for next batch!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("+ Create More", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    // Logout All
                                    OutlinedButton(
                                        onClick = {
                                            createdAccountsList = createdAccountsList.map { it.copy(status = "LOGGED OUT") }
                                            Toast.makeText(context, "All accounts logged out!", Toast.LENGTH_SHORT).show()
                                        },
                                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Logout All", fontSize = 10.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Output Format Selector Chips
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("Format:", color = Color(0xFF94A3B8), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                                listOf(
                                    "DEFAULT" to "name|number|uid|pass|cookie",
                                    "FULL" to "name|num|uid|pass|cookie|token|2fa",
                                    "STD" to "uid|pass|cookie|token",
                                    "COOKIE" to "uid|pass|cookie",
                                    "MIN" to "number|pass|uid"
                                ).forEach { (fmtKey, fmtDesc) ->
                                    val isSel = outputFormatMode == fmtKey
                                    Surface(
                                        color = if (isSel) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF152232),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isSel) Color(0xFF00E676) else Color(0xFF334155)),
                                        modifier = Modifier.clickable { outputFormatMode = fmtKey }
                                    ) {
                                        Text(
                                            text = if (fmtKey == "DEFAULT") "name|number|uid|password|cookie" else fmtKey,
                                            color = if (isSel) Color(0xFF00E676) else Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Raw Output Box (Copy-Ready Multi-Line Output)
                            val formattedBatchOutput = createdAccountsList.joinToString("\n") { it.formatOutput(outputFormatMode) }
                            Surface(
                                color = Color(0xFF050A10),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (outputFormatMode == "DEFAULT") "RAW OUTPUT (name|number|uid|password|cookie):" else "RAW OUTPUT ($outputFormatMode FORMAT):",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = if (showRawOutputBox) "Hide" else "Show",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.5.sp,
                                            modifier = Modifier.clickable { showRawOutputBox = !showRawOutputBox }
                                        )
                                    }
                                    AnimatedVisibility(visible = showRawOutputBox) {
                                        Column {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = formattedBatchOutput,
                                                color = Color(0xFF00E676),
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 8,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Batch Copy & Export .txt Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        ClipboardHelper.copyToClipboard(context, formattedBatchOutput, "Accounts Output")
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ Copied ${createdAccountsList.size} Accounts ($outputFormatMode)!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy All Output", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "FB_Accounts_${System.currentTimeMillis()}.txt")
                                                putExtra(Intent.EXTRA_TEXT, formattedBatchOutput)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Export Accounts (.txt)"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export .txt", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }

                // INDIVIDUAL CREATED ACCOUNT CARDS
                items(createdAccountsList, key = { it.id }) { acc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (acc.accountType == "OLD") Color(0xFFFF5252).copy(alpha = 0.5f)
                            else Color(0xFF00E676).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Top Row: Phone + Thread # + Checker Badge + Login Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(
                                            text = "T#${acc.threadId}",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFF152232),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.clickable {
                                            ClipboardHelper.copyToClipboard(context, acc.phoneNumber, "Number")
                                            VibrationHelper.vibrateSuccess(context)
                                            Toast.makeText(context, "✓ Number Copied: ${acc.phoneNumber}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = acc.phoneNumber,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Number", tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AccountStatusHighlightBadge(accountStatus = acc.accountType)
                                    Surface(
                                        color = if (acc.status == "LOGGED IN") Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = acc.status,
                                            color = if (acc.status == "LOGGED IN") Color(0xFF00E676) else Color(0xFFFF5252),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👤 ${acc.name}",
                                    color = Color(0xFF00E676),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        ClipboardHelper.copyToClipboard(context, acc.name, "Name")
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ Name Copied: ${acc.name}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                Text(
                                    text = "  •  📡 ${acc.panelUsed} (${acc.rangeUsed})  •  🌐 ${acc.proxyUsed}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive Tap-to-Copy Credentials Box (Tap UID, Number, Pass, Cookie, 2FA, or Token to Auto Copy)
                            Surface(
                                color = Color(0xFF070E17),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    // Tap-to-Copy Chips for UID, Number, Password
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Clickable UID
                                        Surface(
                                            color = Color(0xFF102033),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .clickable {
                                                    ClipboardHelper.copyToClipboard(context, acc.uid, "UID")
                                                    VibrationHelper.vibrateSuccess(context)
                                                    Toast.makeText(context, "✓ UID Copied: ${acc.uid}", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "UID: ${acc.uid}",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy UID", tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                                            }
                                        }

                                        // Clickable Password
                                        Surface(
                                            color = Color(0xFF0D261C),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .clickable {
                                                    ClipboardHelper.copyToClipboard(context, acc.password, "Password")
                                                    VibrationHelper.vibrateSuccess(context)
                                                    Toast.makeText(context, "✓ Password Copied: ${acc.password}", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Pass: ${acc.password}",
                                                    color = Color(0xFF00E676),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Password", tint = Color(0xFF00E676), modifier = Modifier.size(11.dp))
                                            }
                                        }
                                    }

                                    // Formatted Line Preview (name|number|uid|password|cookie) — Tap to Copy
                                    Text(
                                        text = "📋 Output: ${acc.name}|${acc.phoneNumber}|${acc.uid}|${acc.password}|${acc.cookie}",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val line = "${acc.name}|${acc.phoneNumber}|${acc.uid}|${acc.password}|${acc.cookie}"
                                                ClipboardHelper.copyToClipboard(context, line, "Account Line")
                                                VibrationHelper.vibrateSuccess(context)
                                                Toast.makeText(context, "✓ Copied: name|number|uid|password|cookie", Toast.LENGTH_SHORT).show()
                                            }
                                    )

                                    Text(
                                        text = "Cookie: ${acc.cookie}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ClipboardHelper.copyToClipboard(context, acc.cookie, "Cookie")
                                                VibrationHelper.vibrateSuccess(context)
                                                Toast.makeText(context, "✓ Cookie Copied!", Toast.LENGTH_SHORT).show()
                                            }
                                    )
                                    Text(
                                        text = "2FA: ${acc.twoFaSecret}",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ClipboardHelper.copyToClipboard(context, acc.twoFaSecret.replace(" ", ""), "2FA Key")
                                                VibrationHelper.vibrateSuccess(context)
                                                Toast.makeText(context, "✓ 2FA Key Copied!", Toast.LENGTH_SHORT).show()
                                            }
                                    )
                                    Text(
                                        text = "UA: ${acc.userAgent}",
                                        color = Color(0xFF64748B),
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ClipboardHelper.copyToClipboard(context, acc.userAgent, "User-Agent")
                                                Toast.makeText(context, "✓ User-Agent Copied!", Toast.LENGTH_SHORT).show()
                                            }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick Copy Buttons + Fetch Real SMS Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        ClipboardHelper.copyToClipboard(context, acc.phoneNumber, "Number")
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ Number Copied: ${acc.phoneNumber}", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp).weight(0.95f)
                                ) {
                                    Text("Number", fontSize = 8.5.sp, color = Color.White, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        ClipboardHelper.copyToClipboard(context, acc.uid, "UID")
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ UID Copied: ${acc.uid}", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp).weight(0.8f)
                                ) {
                                    Text("UID", fontSize = 8.5.sp, color = Color(0xFF38BDF8), maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        ClipboardHelper.copyToClipboard(context, acc.password, "Password")
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ Pass Copied: ${acc.password}", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp).weight(0.85f)
                                ) {
                                    Text("Pass", fontSize = 8.5.sp, color = Color(0xFF00E676), maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        ClipboardHelper.copyToClipboard(context, acc.cookie, "Cookie")
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ Cookie Copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp).weight(0.95f)
                                ) {
                                    Text("Cookie", fontSize = 8.5.sp, color = Color(0xFFCBD5E1), maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val fullLine = acc.formatOutput(outputFormatMode)
                                        ClipboardHelper.copyToClipboard(context, fullLine, "Full Account")
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ Line Copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                                    modifier = Modifier.height(28.dp).weight(1.05f)
                                ) {
                                    Text("Copy Line", fontSize = 8.5.sp, color = Color(0xFF00E676), maxLines = 1)
                                }

                                // Fetch Real OTP SMS from Virtual Numbers Panel
                                Button(
                                    onClick = {
                                        VirtualNumberManager.fetchIncomingOtps(context, autoCopy = true)
                                        KopEngine2Manager.fetchIncomingOtps(context, autoCopy = true)
                                        KopEngine2Manager.fetchActiveRanges()

                                        val matched = findExistingOtpForPhone(
                                            phone = acc.phoneNumber,
                                            unixState = unixState,
                                            vnState = vnState,
                                            kop2State = kop2State
                                        )
                                        if (matched != null) {
                                            val (code, fullSms) = matched
                                            createdAccountsList = createdAccountsList.map {
                                                if (it.id == acc.id) it.copy(otpCode = code, smsFetched = fullSms) else it
                                            }
                                            ClipboardHelper.copyToClipboard(context, code, "OTP Code")
                                            VibrationHelper.vibrateSuccess(context)
                                            OtpNotificationHelper.showOtpNotification(context, acc.phoneNumber, code, fullSms)
                                            Toast.makeText(context, "⚡ OTP Copied: $code", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "🔄 Polling ${acc.panelUsed} for ${acc.phoneNumber}... OTP আসামাত্র অটো কপি হবে!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("📩 Fetch SMS", fontSize = 9.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!acc.smsFetched.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFF06281E),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📩 SMS: ${acc.smsFetched}",
                                            color = Color(0xFF00E676),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!acc.otpCode.isNullOrEmpty()) {
                                            Surface(
                                                color = Color(0xFF00E676),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.clickable {
                                                    ClipboardHelper.copyToClipboard(context, acc.otpCode, "OTP Code")
                                                    Toast.makeText(context, "OTP Copied: ${acc.otpCode}", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text(
                                                    text = "OTP: ${acc.otpCode}",
                                                    color = Color.Black,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    }

    // Country Picker Dialog for Premium Numbers (Synced with UnixSmsManager)
    if (showCountryPicker) {
        Dialog(onDismissRequest = { showCountryPicker = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Country (Premium Panel)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showCountryPicker = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    OutlinedTextField(
                        value = countrySearchQuery,
                        onValueChange = { countrySearchQuery = it },
                        placeholder = { Text("Search country or code...", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredCountries = remember(countrySearchQuery) {
                        AllCountriesProvider.allCountries.filter {
                            countrySearchQuery.isBlank() ||
                                it.name.contains(countrySearchQuery, ignoreCase = true) ||
                                it.dialCode.contains(countrySearchQuery, ignoreCase = true) ||
                                it.code.contains(countrySearchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Surface(
                                color = if (unixState.selectedCountry == "ALL") Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF070E17),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        UnixSmsManager.setSelectedCountry("ALL")
                                        showCountryPicker = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🌐 ALL COUNTRIES (Auto Pool)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("${unixState.uploadedNumbers.count { !it.isUsed }} Ready", color = Color(0xFF00E676), fontSize = 11.sp)
                                }
                            }
                        }
                        items(filteredCountries, key = { it.code }) { cItem ->
                            val countInPool = unixState.uploadedNumbers.count {
                                !it.isUsed && it.countryCode.equals(cItem.code, ignoreCase = true)
                            }
                            Surface(
                                color = if (unixState.selectedCountry.equals(cItem.code, ignoreCase = true)) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF070E17),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        UnixSmsManager.setSelectedCountry(cItem.code)
                                        showCountryPicker = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${cItem.flag} ${cItem.name} (${cItem.dialCode})", color = Color.White, fontSize = 12.sp)
                                    if (countInPool > 0) {
                                        Text("$countInPool ready", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

private fun generateNumberFromRangePattern(rangePattern: String): String {
    val clean = rangePattern.trim().replace("+", "")
    if (clean.contains("X", ignoreCase = true)) {
        val sb = StringBuilder("+")
        for (ch in clean) {
            if (ch.equals('X', ignoreCase = true)) {
                sb.append((0..9).random())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
    val digitsOnly = clean.filter { it.isDigit() }.ifEmpty { "237627" }
    val needed = (11 - digitsOnly.length).coerceIn(3, 7)
    val suffix = (1..needed).map { (0..9).random() }.joinToString("")
    return "+$digitsOnly$suffix"
}

private fun findExistingOtpForPhone(
    phone: String,
    unixState: com.example.service.UnixSmsState,
    vnState: com.example.service.VirtualNumbersUiState,
    kop2State: com.example.service.VirtualNumbersUiState
): Pair<String, String>? {
    unixState.activeNumbers.firstOrNull {
        !it.otpCode.isNullOrEmpty() && UnixSmsManager.isPhoneNumberMatch(it.number, phone)
    }?.let {
        return it.otpCode!! to (it.otpMessage ?: "Facebook code: ${it.otpCode}")
    }
    unixState.uploadedNumbers.firstOrNull {
        !it.otpCode.isNullOrEmpty() && UnixSmsManager.isPhoneNumberMatch(it.number, phone)
    }?.let {
        return it.otpCode!! to (it.otpMessage ?: "Facebook code: ${it.otpCode}")
    }
    vnState.provisionedNumbers.firstOrNull {
        !it.otpCode.isNullOrEmpty() && VirtualNumberManager.isPhoneNumberMatch(it.number, phone)
    }?.let {
        return it.otpCode!! to (it.otpMessage ?: "Facebook code: ${it.otpCode}")
    }
    kop2State.provisionedNumbers.firstOrNull {
        !it.otpCode.isNullOrEmpty() && KopEngine2Manager.isPhoneNumberMatch(it.number, phone)
    }?.let {
        return it.otpCode!! to (it.otpMessage ?: "Facebook code: ${it.otpCode}")
    }
    return null
}

private fun generateRealisticUserAgent(mode: String): String {
    val devices = listOf(
        Triple("samsung", "SM-S918B", "UP1A.231005.007"),
        Triple("samsung", "SM-A546E", "UP1A.231005.007"),
        Triple("Google", "Pixel 8 Pro", "UQ1A.240205.004"),
        Triple("Xiaomi", "23049PCD8G", "UKQ1.230804.001"),
        Triple("OnePlus", "CPH2449", "UKQ1.230924.001"),
        Triple("vivo", "V2250", "UP1A.231005.007"),
        Triple("realme", "RMX3771", "UKQ1.230924.001")
    )
    val (brand, model, buildId) = devices.random()
    val androidVer = listOf("12", "13", "14").random()
    val fbMajor = (435..452).random()
    val fbSub = (10..45).random()
    val fbBuild = (510000000..599999999).random()
    val chromeMajor = (119..125).random()

    val effectiveMode = if (mode == "AUTO_ROTATE") {
        listOf("FB_KATANA", "FB_LITE", "CHROME_MBASIC").random()
    } else mode

    return when (effectiveMode) {
        "FB_KATANA" -> "Dalvik/2.1.0 (Linux; U; Android $androidVer; $model Build/$buildId) [FBAN/FB4A;FBAV/$fbMajor.0.0.$fbSub.114;FBBV/$fbBuild;FBDM/{density=2.75,width=1080,height=2400};FBLC/en_US;FBRV/0;FBCR/Carrier;FBMF/$brand;FBBD/$brand;FBPN/com.facebook.katana;FBDV/$model;FBSV/$androidVer;FBOP/1;FBCA/arm64-v8a:;]"
        "FB_LITE" -> "Dalvik/2.1.0 (Linux; U; Android $androidVer; $model Build/$buildId) [FBAN/EMA;FBLC/en_US;FBAV/${(380..405).random()}.0.0.${(10..20).random()}.109;FBPN/com.facebook.lite;FBDV/$model;FBSV/$androidVer;FBCA/arm64-v8a:;]"
        else -> "Mozilla/5.0 (Linux; Android $androidVer; $model Build/$buildId; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/$chromeMajor.0.${(6000..6400).random()}.${(50..140).random()} Mobile Safari/537.36"
    }
}

private fun generateRealisticName(): String {
    val firstNames = listOf("Tanvir", "Nusrat", "Sabbir", "Farhana", "Mehedi", "Sadia", "Rakib", "Jannatul", "Arif", "Sumaiya", "Fahim", "Tasnim", "Liam", "Sophia", "Noah", "Olivia", "Aiden", "Zara")
    val lastNames = listOf("Ahmed", "Hossain", "Islam", "Rahman", "Akter", "Khan", "Hasan", "mim", "Chowdhury", "Sultana", "Karim", "Sikder", "Carter", "Bennett", "Reed")
    return "${firstNames.random()} ${lastNames.random()}"
}

private fun randomAlpha(len: Int): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-"
    return (1..len).map { chars.random() }.joinToString("")
}
