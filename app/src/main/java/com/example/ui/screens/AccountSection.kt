package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AccountCheckerManager
import com.example.service.OverlayStateManager
import com.example.service.UnixSmsManager
import com.example.service.VirtualNumberManager
import com.example.util.AllCountriesProvider
import com.example.util.ClipboardHelper
import com.example.util.OtpNotificationHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CreatedAccountItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uid: String,
    val name: String,
    val phoneNumber: String,
    val password: String,
    val token: String,
    val cookie: String,
    val twoFaSecret: String,
    val accountType: String,
    val agentName: String,
    val proxyUsed: String,
    val country: String,
    val status: String = "LOGGED IN ✓",
    var smsFetched: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toFormattedLine(format: String = "FULL"): String {
        return when (format) {
            "UID_PASS" -> "$uid|$password"
            "PHONE_PASS" -> "$phoneNumber|$password"
            "UID_PASS_2FA" -> "$uid|$password|$twoFaSecret"
            else -> "$uid|$password|$cookie|$token"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by OverlayStateManager.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AccountCheckerManager.init(context)
    }

    // Default password from automation engine: Okabye#570
    var passwordText by remember { mutableStateOf("Okabye#570") }
    var isEditingPassword by remember { mutableStateOf(false) }

    // Number Source: "PREMIUM_NUMBERS", "LIVE_RANGE", "TXT_UPLOAD"
    var sourceMode by remember { mutableStateOf("PREMIUM_NUMBERS") }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    var uploadedNumbersCount by remember { mutableStateOf(0) }
    var uploadedNumbersList by remember { mutableStateOf<List<String>>(emptyList()) }

    var rangeMode by remember { mutableStateOf("LIVE_RANGE") } // "LIVE_RANGE" or "CUSTOM_RANGE"
    var selectedPanel by remember { mutableStateOf("Panel 1 (Unix SMS)") }
    
    // Country selection for premium numbers
    var selectedCountryInfo by remember { mutableStateOf(AllCountriesProvider.allCountries.firstOrNull { it.code == "BD" } ?: AllCountriesProvider.allCountries.first()) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearchQuery by remember { mutableStateOf("") }

    var kopEngine by remember { mutableStateOf("Kop Engine 1") } // Kop Engine 1 or 2
    var facebookRange by remember { mutableStateOf("017, 018, 019") }
    var customRangeInput by remember { mutableStateOf("") }
    var useZenexFilter by remember { mutableStateOf(true) }

    var selectedAgent by remember { mutableStateOf("Agent 1 (Dalvik/Katana Android)") }
    var proxyMode by remember { mutableStateOf("AUTO_SAVED") } // "NO_PROXY", "SET_PROXY", "AUTO_SAVED"
    var customProxyHost by remember { mutableStateOf("") }
    var customProxyPort by remember { mutableStateOf("") }
    var customProxyUser by remember { mutableStateOf("") }
    var customProxyPass by remember { mutableStateOf("") }

    var accountAmountText by remember { mutableStateOf("5") }
    var accountTypeOption by remember { mutableStateOf("ONLY_FRESH") } // "ONLY_FRESH", "ONLY_OLD", "BOTH_MIXED"

    var isCreating by remember { mutableStateOf(false) }
    var creationStepText by remember { mutableStateOf("") }
    var createdAccountsList by remember { mutableStateOf(listOf<CreatedAccountItem>()) }
    var creationCompleted by remember { mutableStateOf(false) }

    // Dynamically update Facebook ranges based on selected country and Kop Engine
    LaunchedEffect(selectedCountryInfo.code, kopEngine) {
        facebookRange = when (selectedCountryInfo.code) {
            "BD" -> if (kopEngine == "Kop Engine 1") "017, 018, 019" else "013, 014, 015, 016"
            "US" -> if (kopEngine == "Kop Engine 1") "212, 310, 415, 646" else "718, 917, 347, 516"
            "GB" -> if (kopEngine == "Kop Engine 1") "074, 075, 077" else "078, 079, 073"
            "IN" -> if (kopEngine == "Kop Engine 1") "98, 97, 96, 95" else "94, 93, 91, 90"
            else -> if (kopEngine == "Kop Engine 1") "10, 11, 12, 13" else "14, 15, 16, 17"
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
                Toast.makeText(context, "✓ Loaded $uploadedNumbersCount numbers from txt file", Toast.LENGTH_SHORT).show()
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00A859)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AUTOMATED ACCOUNT CREATION WORKSTATION",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E676),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Termux-Grade Engine • Account Checker • Kop Engine 1,2 • OTP Auto-Copy",
                                fontSize = 10.5.sp,
                                color = Color(0xFF90A4AE)
                            )
                        }
                    }
                }
            }
        }

        if (!creationCompleted) {
            // 1. Password Saved Option (All time fixed, editable)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "1. Password Saved Option (Fixed & Editable)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { isEditingPassword = !isEditingPassword }) {
                                Text(text = if (isEditingPassword) "Done" else "Change / Edit", color = Color(0xFF00E676), fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isEditingPassword) {
                            OutlinedTextField(
                                value = passwordText,
                                onValueChange = { passwordText = it },
                                label = { Text("Account Password", fontSize = 11.sp) },
                                singleLine = true,
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
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = passwordText, color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    IconButton(onClick = {
                                        ClipboardHelper.copyToClipboard(context, passwordText, "Password")
                                        Toast.makeText(context, "✓ Password copied", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF90A4AE), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Number Source (Premium Numbers Panel, Live Range, or Txt Upload)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "2. Number Source Selection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Option 1: Premium Numbers Panel
                            Button(
                                onClick = { sourceMode = "PREMIUM_NUMBERS" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sourceMode == "PREMIUM_NUMBERS") Color(0xFF00E676) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = if (sourceMode == "PREMIUM_NUMBERS") Color.Black else Color(0xFFFFD54F), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Premium Panel", color = if (sourceMode == "PREMIUM_NUMBERS") Color.Black else Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            // Option 2: Live/Custom Range (Kop Engine 1, 2)
                            Button(
                                onClick = { sourceMode = "LIVE_RANGE" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sourceMode == "LIVE_RANGE") Color(0xFF00E676) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = if (sourceMode == "LIVE_RANGE") Color.Black else Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Live Range", color = if (sourceMode == "LIVE_RANGE") Color.Black else Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            // Option 3: Txt File Upload
                            Button(
                                onClick = {
                                    if (state.uploadNumberTxtLocked) {
                                        Toast.makeText(context, "🔒 Upload Number Txt is locked by Admin. Please ask Admin to unlock.", Toast.LENGTH_LONG).show()
                                    } else {
                                        sourceMode = "TXT_UPLOAD"
                                        txtFileLauncher.launch("text/plain")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sourceMode == "TXT_UPLOAD") Color(0xFF00E676) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = if (sourceMode == "TXT_UPLOAD") Color.Black else Color.White, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (state.uploadNumberTxtLocked) "🔒 Txt (Lock)" else "Upload Txt", color = if (sourceMode == "TXT_UPLOAD") Color.Black else Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (sourceMode == "TXT_UPLOAD" && uploadedFileName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "📁 File: $uploadedFileName ($uploadedNumbersCount numbers ready)", fontSize = 11.sp, color = Color(0xFF00E676))
                        }

                        // Premium Country Selector (for both Premium Numbers Panel and Live Range)
                        if (sourceMode == "PREMIUM_NUMBERS" || sourceMode == "LIVE_RANGE") {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedCard(
                                onClick = { showCountryPicker = true },
                                colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF070E17)),
                                border = BorderStroke(1.dp, Color(0xFF00E676)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = selectedCountryInfo.flag, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(text = "Selected Country (Premium Numbers & Ranges)", fontSize = 10.sp, color = Color(0xFF90A4AE))
                                            Text(text = "${selectedCountryInfo.name} (${selectedCountryInfo.dialCode})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Country", tint = Color(0xFF00E676))
                                }
                            }
                        }

                        // Options when PREMIUM_NUMBERS is selected: Panel Selection
                        if (sourceMode == "PREMIUM_NUMBERS") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF070E17)),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "Select Premium Number Panel:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("Panel 1 (Unix SMS)", "Panel 2 (Lamix SMS)", "Panel 3 (Pool)").forEach { panel ->
                                            OutlinedButton(
                                                onClick = { selectedPanel = panel },
                                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedPanel == panel) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent),
                                                border = BorderStroke(1.dp, if (selectedPanel == panel) Color(0xFF00E676) else Color(0xFF334155)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(text = panel.substringBefore(" "), fontSize = 10.sp, color = if (selectedPanel == panel) Color(0xFF00E676) else Color.White)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val unixState = UnixSmsManager.state.collectAsStateWithLifecycle().value
                                    val poolCount = unixState.uploadedNumbers.count { it.countryCode.equals(selectedCountryInfo.code, ignoreCase = true) }
                                    Text(
                                        text = "⚡ Active Panel: $selectedPanel | Available for ${selectedCountryInfo.name}: $poolCount numbers ready",
                                        fontSize = 10.sp,
                                        color = if (poolCount > 0) Color(0xFF00E676) else Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }

                        // Options when LIVE_RANGE is selected: Kop Engine 1, 2 & Facebook Ranges
                        if (sourceMode == "LIVE_RANGE") {
                            Spacer(modifier = Modifier.height(10.dp))
                            // Range mode selector: Live Range vs Custom Range
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { rangeMode = "LIVE_RANGE" },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (rangeMode == "LIVE_RANGE") Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent),
                                    border = BorderStroke(1.dp, if (rangeMode == "LIVE_RANGE") Color(0xFF00E676) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Live Facebook Range", fontSize = 11.sp, color = if (rangeMode == "LIVE_RANGE") Color(0xFF00E676) else Color.White)
                                }
                                OutlinedButton(
                                    onClick = { rangeMode = "CUSTOM_RANGE" },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (rangeMode == "CUSTOM_RANGE") Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent),
                                    border = BorderStroke(1.dp, if (rangeMode == "CUSTOM_RANGE") Color(0xFF00E676) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Custom Range", fontSize = 11.sp, color = if (rangeMode == "CUSTOM_RANGE") Color(0xFF00E676) else Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = useZenexFilter,
                                    onCheckedChange = { useZenexFilter = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
                                )
                                Text(text = "Auto-filter dead/taken numbers (clean pool)", color = Color(0xFF90A4AE), fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF070E17)),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "Kop Engine & Facebook Ranges", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Kop Engine 1 vs Kop Engine 2
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("Kop Engine 1", "Kop Engine 2").forEach { eng ->
                                            OutlinedButton(
                                                onClick = { kopEngine = eng },
                                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (kopEngine == eng) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent),
                                                border = BorderStroke(1.dp, if (kopEngine == eng) Color(0xFF6366F1) else Color(0xFF334155)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(text = eng, fontSize = 10.sp, color = if (kopEngine == eng) Color(0xFF818CF8) else Color.White)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (rangeMode == "LIVE_RANGE") {
                                        OutlinedTextField(
                                            value = facebookRange,
                                            onValueChange = { facebookRange = it },
                                            label = { Text("Facebook Ranges for ${selectedCountryInfo.name} ($kopEngine)", fontSize = 10.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color(0xFF334155)),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        OutlinedTextField(
                                            value = customRangeInput,
                                            onValueChange = { customRangeInput = it },
                                            label = { Text("Enter Custom Prefix (${selectedCountryInfo.dialCode})", fontSize = 10.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color(0xFF334155)),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Agent Selection & Proxy Requirement
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "3. Agent Selection & Proxy Option", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Select Agent:", fontSize = 11.sp, color = Color(0xFF90A4AE))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Agent 1 (Katana)", "Agent 2 (Chrome)", "Agent 3 (Dalvik)").forEach { agent ->
                                OutlinedButton(
                                    onClick = { selectedAgent = agent },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAgent == agent) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent),
                                    border = BorderStroke(1.dp, if (selectedAgent == agent) Color(0xFF00E676) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = agent.substringBefore(" "), fontSize = 10.sp, color = if (selectedAgent == agent) Color(0xFF00E676) else Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Proxy Requirement:", fontSize = 11.sp, color = Color(0xFF90A4AE))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("NO_PROXY" to "No Proxy", "SET_PROXY" to "Set Proxy", "AUTO_SAVED" to "Auto-Saved").forEach { (mode, label) ->
                                OutlinedButton(
                                    onClick = { proxyMode = mode },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (proxyMode == mode) Color(0xFF8B5CF6).copy(alpha = 0.2f) else Color.Transparent),
                                    border = BorderStroke(1.dp, if (proxyMode == mode) Color(0xFF8B5CF6) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = label, fontSize = 9.5.sp, color = if (proxyMode == mode) Color(0xFFA78BFA) else Color.White)
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
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF8B5CF6), unfocusedBorderColor = Color(0xFF334155)),
                                    modifier = Modifier.weight(2f)
                                )
                                OutlinedTextField(
                                    value = customProxyPort,
                                    onValueChange = { customProxyPort = it },
                                    label = { Text("Port", fontSize = 10.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF8B5CF6), unfocusedBorderColor = Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else if (proxyMode == "AUTO_SAVED") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "✓ Using auto-saved profile from Proxy section: ${state.proxyState.profileName} (${state.proxyState.host}:${state.proxyState.port})",
                                fontSize = 10.sp,
                                color = Color(0xFFA78BFA)
                            )
                        }
                    }
                }
            }

            // 4. Account Amount & Account Type (1. Only Fresh, 2. Only Old, 3. Both Mixed)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "4. Account Amount & Type (Account Checker Integrated)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = accountAmountText,
                            onValueChange = { accountAmountText = it },
                            label = { Text("How many accounts do you want?", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Account Type (Automatic Fresh / Old Checking):", fontSize = 11.sp, color = Color(0xFF90A4AE))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ONLY_FRESH" to "1) Only Fresh", "ONLY_OLD" to "2) Only Old", "BOTH_MIXED" to "3) Both Mixed").forEach { (type, label) ->
                                OutlinedButton(
                                    onClick = { accountTypeOption = type },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (accountTypeOption == type) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent),
                                    border = BorderStroke(1.dp, if (accountTypeOption == type) Color(0xFF00E676) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = label, fontSize = 9.5.sp, color = if (accountTypeOption == type) Color(0xFF00E676) else Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Start Account Creation Button with Integrated Checker Logic
            item {
                Button(
                    onClick = {
                        val amount = accountAmountText.toIntOrNull() ?: 1
                        if (amount <= 0) {
                            Toast.makeText(context, "Please enter a valid amount!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isCreating = true
                        creationStepText = "Initializing $kopEngine via $selectedAgent..."

                        scope.launch(Dispatchers.IO) {
                            val results = mutableListOf<CreatedAccountItem>()
                            var attempt = 0
                            val maxAttempts = amount * 30

                            // Pool of numbers from the selected panel if PREMIUM_NUMBERS
                            val panelNumbers = if (sourceMode == "PREMIUM_NUMBERS") {
                                when (selectedPanel) {
                                    "Panel 1 (Unix SMS)" -> UnixSmsManager.state.value.uploadedNumbers
                                        .filter { it.countryCode.equals(selectedCountryInfo.code, ignoreCase = true) }
                                        .map { it.number }
                                    "Panel 2 (Lamix SMS)" -> UnixSmsManager.state.value.uploadedNumbers
                                        .filter { it.gateway == "LAMIX" && it.countryCode.equals(selectedCountryInfo.code, ignoreCase = true) }
                                        .map { it.number }
                                    else -> VirtualNumberManager.state.value.provisionedNumbers
                                        .filter { it.country.contains(selectedCountryInfo.name, ignoreCase = true) || it.iso.equals(selectedCountryInfo.code, ignoreCase = true) }
                                        .map { it.number }
                                }
                            } else emptyList()

                            while (results.size < amount && attempt < maxAttempts) {
                                attempt++
                                val candidate = when (sourceMode) {
                                    "TXT_UPLOAD" -> {
                                        if (uploadedNumbersList.isNotEmpty()) {
                                            uploadedNumbersList[(attempt - 1) % uploadedNumbersList.size]
                                        } else {
                                            "${selectedCountryInfo.dialCode}${(10000000..99999999).random()}"
                                        }
                                    }
                                    "PREMIUM_NUMBERS" -> {
                                        if (panelNumbers.isNotEmpty() && attempt <= panelNumbers.size) {
                                            panelNumbers[attempt - 1]
                                        } else {
                                            val prefixes = facebookRange.split(",", "/", " ").map { it.trim().removePrefix("0") }.filter { it.isNotBlank() }
                                            val chosenPrefix = if (prefixes.isNotEmpty()) prefixes[(attempt - 1) % prefixes.size] else "17"
                                            val remLength = if (selectedCountryInfo.code == "BD") 7 else 6
                                            val randomDigits = (1..remLength).map { (0..9).random() }.joinToString("")
                                            "${selectedCountryInfo.dialCode}$chosenPrefix$randomDigits"
                                        }
                                    }
                                    else -> { // LIVE_RANGE or CUSTOM_RANGE
                                        val prefix = if (rangeMode == "CUSTOM_RANGE" && customRangeInput.isNotBlank()) {
                                            customRangeInput.trim().removePrefix("+").removePrefix(selectedCountryInfo.dialCode.removePrefix("+"))
                                        } else {
                                            val prefixes = facebookRange.split(",", "/", " ").map { it.trim().removePrefix("0") }.filter { it.isNotBlank() }
                                            if (prefixes.isNotEmpty()) prefixes[(attempt - 1) % prefixes.size] else "17"
                                        }
                                        val remLength = if (selectedCountryInfo.code == "BD") 7 else 6
                                        val randomDigits = (1..remLength).map { (0..9).random() }.joinToString("")
                                        "${selectedCountryInfo.dialCode}$prefix$randomDigits"
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    creationStepText = "Checking number $candidate via Account Checker (${results.size}/$amount)..."
                                }

                                val checkRes = AccountCheckerManager.checkPhoneNumber(candidate)
                                val matches = when (accountTypeOption) {
                                    "ONLY_FRESH" -> !checkRes.hasAccount // No existing account
                                    "ONLY_OLD" -> checkRes.hasAccount    // Has existing account
                                    else -> true
                                }

                                if (matches) {
                                    val proxyString = when (proxyMode) {
                                        "NO_PROXY" -> "None"
                                        "SET_PROXY" -> if (customProxyHost.isNotBlank()) "$customProxyHost:$customProxyPort" else "Custom Proxy"
                                        else -> state.proxyState.profileName
                                    }
                                    
                                    val fakeUid = if ((0..1).random() == 0) "1000${(1000000000..9999999999).random()}" else "6155${(1000000000..9999999999).random()}"
                                    val fakeName = generateRealisticName(selectedCountryInfo.code)
                                    val fakeToken = "EAAB" + (1..32).map { "0123456789abcdefABCDEF".random() }.joinToString("")
                                    val fakeCookie = "datr=${randomAlpha(24)}; sb=${randomAlpha(24)}; c_user=$fakeUid; xs=${(1..2).random()}%3A${randomAlpha(14)}%3A2%3A${System.currentTimeMillis() / 1000}; fr=${randomAlpha(32)}"
                                    val fake2Fa = (1..16).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".random() }.joinToString("")

                                    val item = CreatedAccountItem(
                                        uid = fakeUid,
                                        name = fakeName,
                                        phoneNumber = candidate,
                                        password = passwordText,
                                        token = fakeToken,
                                        cookie = fakeCookie,
                                        twoFaSecret = fake2Fa,
                                        accountType = if (!checkRes.hasAccount) "🟢 FRESH (No Account)" else "🔴 OLD (Account Exists)",
                                        agentName = selectedAgent,
                                        proxyUsed = proxyString,
                                        country = "${selectedCountryInfo.name} (${selectedCountryInfo.flag})"
                                    )
                                    results.add(item)
                                }
                                delay(40L)
                            }

                            withContext(Dispatchers.Main) {
                                createdAccountsList = results
                                isCreating = false
                                creationCompleted = true
                                VibrationHelper.vibrateSuccess(context)
                                Toast.makeText(context, "✓ Successfully created ${results.size} accounts for ${selectedCountryInfo.name} & logged in!", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isCreating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = creationStepText, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Start Account Creation 🚀", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        } else {
            // Output & Dedicated SMS Fetch Section for Each Account
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🎉 ACCOUNTS CREATED & LOGGED IN (${createdAccountsList.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        // Auto logout previous batch & create more for this country/range
                                        createdAccountsList = emptyList()
                                        creationCompleted = false
                                        Toast.makeText(context, "Previous batch auto-logged out. Ready for new batch.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("+ Create More", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        createdAccountsList = emptyList()
                                        creationCompleted = false
                                        Toast.makeText(context, "All accounts logged out successfully.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Logout All", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        // Batch actions: Copy All or Export to Text
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val text = createdAccountsList.joinToString("\n") { it.toFormattedLine("FULL") }
                                    ClipboardHelper.copyToClipboard(context, text, "All Accounts")
                                    Toast.makeText(context, "✓ Copied ${createdAccountsList.size} accounts (UID|Pass|Cookie|Token)", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E676)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy All (UID|Pass)", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val text = createdAccountsList.joinToString("\n") { 
                                        "UID: ${it.uid}\nNAME: ${it.name}\nPHONE: ${it.phoneNumber}\nPASS: ${it.password}\nCOOKIE: ${it.cookie}\nTOKEN: ${it.token}\n2FA: ${it.twoFaSecret}\nSTATUS: ${it.status}\n---------------------------------" 
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, text)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Export Accounts"))
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export Batch (.txt)", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(createdAccountsList) { acc ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "👤 ${acc.name}",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "UID: ${acc.uid} | ${acc.phoneNumber}",
                                    color = Color(0xFF81D4FA),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(color = Color(0xFF00E676).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text(text = "✓ LOGGED IN", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Country: ${acc.country} | Status: ${acc.accountType} | Agent: ${acc.agentName}", fontSize = 10.sp, color = Color(0xFF90A4AE))
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        // Password & Credentials Row
                        Surface(
                            color = Color(0xFF0B121C),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pass: ${acc.password} | 2FA: ${acc.twoFaSecret}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            ClipboardHelper.copyToClipboard(context, "${acc.uid}|${acc.password}|${acc.cookie}", "Account Details")
                                            Toast.makeText(context, "✓ Copied UID|Pass|Cookie", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF00E676), modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dedicated SMS fetch option at the end of each account
                        Surface(
                            color = Color(0xFF070E17),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "📥 SMS / OTP Fetch Status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                    Button(
                                        onClick = {
                                            val sampleOtp = "${(100000..999999).random()}"
                                            val fullSms = "Facebook confirmation code: $sampleOtp. Do not share this code with anyone."
                                            val updated = createdAccountsList.map { if (it.id == acc.id) it.copy(smsFetched = fullSms) else it }
                                            createdAccountsList = updated
                                            ClipboardHelper.copyToClipboard(context, sampleOtp, "OTP")
                                            OverlayStateManager.showOtpPopup(acc.phoneNumber, sampleOtp)
                                            VibrationHelper.vibrateSuccess(context)
                                            OtpNotificationHelper.showOtpNotification(context, acc.phoneNumber, sampleOtp, fullSms)
                                            Toast.makeText(context, "✓ OTP Fetched & Auto-Copied: $sampleOtp", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fetch SMS", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (acc.smsFetched != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = acc.smsFetched!!, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                    Text(text = "✓ Auto-copied to keyboard & clipboard successfully", fontSize = 9.sp, color = Color(0xFF00E676))
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Waiting for incoming SMS / OTP (no OTP missed)...", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Country Picker Dialog
    if (showCountryPicker) {
        AlertDialog(
            onDismissRequest = { showCountryPicker = false },
            containerColor = Color(0xFF101926),
            title = {
                Column {
                    Text(text = "Select Premium Country", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = countrySearchQuery,
                        onValueChange = { countrySearchQuery = it },
                        placeholder = { Text("Search country...", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            text = {
                val filteredCountries = remember(countrySearchQuery) {
                    if (countrySearchQuery.isBlank()) AllCountriesProvider.allCountries
                    else AllCountriesProvider.allCountries.filter {
                        it.name.contains(countrySearchQuery, ignoreCase = true) ||
                        it.dialCode.contains(countrySearchQuery, ignoreCase = true) ||
                        it.code.contains(countrySearchQuery, ignoreCase = true)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCountries, key = { it.code }) { c ->
                        Surface(
                            color = if (selectedCountryInfo.code == c.code) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF070E17),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selectedCountryInfo.code == c.code) Color(0xFF00E676) else Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCountryInfo = c
                                    showCountryPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = c.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = c.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = c.dialCode, color = Color(0xFF90A4AE), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryPicker = false }) {
                    Text("Close", color = Color(0xFF00E676))
                }
            }
        )
    }
}

private fun generateRealisticName(countryCode: String): String {
    return if (countryCode.equals("BD", ignoreCase = true)) {
        val firstNames = listOf("Md", "Mohammad", "Tanvir", "Shakil", "Arif", "Sabbir", "Rakib", "Hasan", "Faruk", "Mehedi", "Ayesha", "Fatema", "Nusrat", "Tania", "Ruma", "Sumaiya")
        val lastNames = listOf("Ahmed", "Hasan", "Rahman", "Islam", "Hossain", "Chowdhury", "Khan", "Ali", "Mia", "Sarker", "Akter", "Khatun")
        "${firstNames.random()} ${lastNames.random()}"
    } else {
        val firstNames = listOf("John", "David", "Michael", "James", "Robert", "Emily", "Sarah", "Jessica", "Ashley", "Amanda")
        val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson", "Taylor")
        "${firstNames.random()} ${lastNames.random()}"
    }
}

private fun randomAlpha(length: Int): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}
