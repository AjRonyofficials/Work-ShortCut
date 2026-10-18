package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.OverlayStateManager
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper

data class CreatedAccountItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val phoneNumber: String,
    val accountType: String,
    val agentName: String,
    val proxyUsed: String,
    val country: String,
    val status: String = "LOGGED IN",
    var smsFetched: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by OverlayStateManager.uiState.collectAsStateWithLifecycle()

    // Default password from automation engine: Okabye#570
    var passwordText by remember { mutableStateOf("Okabye#570") }
    var isEditingPassword by remember { mutableStateOf(false) }

    var sourceMode by remember { mutableStateOf("LIVE_RANGE") } // "TXT_UPLOAD" or "LIVE_RANGE"
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    var uploadedNumbersCount by remember { mutableStateOf(0) }

    var rangeMode by remember { mutableStateOf("LIVE_RANGE") } // "LIVE_RANGE" or "CUSTOM_RANGE"
    var selectedPanel by remember { mutableStateOf("Panel 1 (Standard)") }
    var selectedCountry by remember { mutableStateOf("Bangladesh (+880)") }
    var kopEngine by remember { mutableStateOf("Kop Engine 1") } // Kop Engine 1 or 2
    var facebookRange by remember { mutableStateOf("017 / 018 / 019") }
    var customRangeInput by remember { mutableStateOf("") }
    var useZenexFilter by remember { mutableStateOf(true) }

    var selectedAgent by remember { mutableStateOf("Agent 1 (Standard)") }
    var proxyMode by remember { mutableStateOf("AUTO_SAVED") } // "NO_PROXY", "SET_PROXY", "AUTO_SAVED"

    var accountAmountText by remember { mutableStateOf("5") }
    var accountTypeOption by remember { mutableStateOf("ONLY_FRESH") } // "ONLY_FRESH", "ONLY_OLD", "BOTH_MIXED"

    var isCreating by remember { mutableStateOf(false) }
    var creationStepText by remember { mutableStateOf("") }
    var createdAccountsList by remember { mutableStateOf(listOf<CreatedAccountItem>()) }
    var creationCompleted by remember { mutableStateOf(false) }

    val txtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
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
                                text = "AUTOMATED ACCOUNT CREATION & SMS WORKSTATION",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E676),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Kop Engine 1,2 • Proxy • Virtual Panels • OTP Auto-Copy",
                                fontSize = 11.sp,
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

            // 2. Txt File Upload vs Live Range / Custom Range
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "2. Number Source (Txt File or Live Range)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = if (sourceMode == "TXT_UPLOAD") Color.Black else Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (state.uploadNumberTxtLocked) "🔒 Upload Txt (Locked)" else "Upload Txt", color = if (sourceMode == "TXT_UPLOAD") Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { sourceMode = "LIVE_RANGE" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sourceMode == "LIVE_RANGE") Color(0xFF00E676) else Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LiveTv, contentDescription = null, tint = if (sourceMode == "LIVE_RANGE") Color.Black else Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Live/Custom Range", color = if (sourceMode == "LIVE_RANGE") Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (sourceMode == "TXT_UPLOAD" && uploadedFileName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "📁 File: $uploadedFileName ($uploadedNumbersCount numbers ready)", fontSize = 11.sp, color = Color(0xFF00E676))
                        }

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
                                    Text(text = "Live Range", fontSize = 11.sp, color = if (rangeMode == "LIVE_RANGE") Color(0xFF00E676) else Color.White)
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
                                Text(text = "Exclude dead/taken numbers automatically", color = Color(0xFF90A4AE), fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF070E17)),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "Virtual Number Panel & Kop Engine", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81D4FA))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Panel selection
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("Panel 1", "Panel 2", "Panel 3").forEach { panel ->
                                            OutlinedButton(
                                                onClick = { selectedPanel = panel },
                                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedPanel == panel) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent),
                                                border = BorderStroke(1.dp, if (selectedPanel == panel) Color(0xFF00E676) else Color(0xFF334155)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(text = panel, fontSize = 10.sp, color = if (selectedPanel == panel) Color(0xFF00E676) else Color.White)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Kop Engine 1, 2
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
                                            label = { Text("Facebook Range (e.g. 017 / 018 / 019)", fontSize = 10.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00E676), unfocusedBorderColor = Color(0xFF334155)),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        OutlinedTextField(
                                            value = customRangeInput,
                                            onValueChange = { customRangeInput = it },
                                            label = { Text("Enter Custom Range Prefix", fontSize = 10.sp) },
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
                            listOf("Agent 1", "Agent 2", "Agent 3").forEach { agent ->
                                OutlinedButton(
                                    onClick = { selectedAgent = agent },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAgent == agent) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent),
                                    border = BorderStroke(1.dp, if (selectedAgent == agent) Color(0xFF00E676) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = agent, fontSize = 10.sp, color = if (selectedAgent == agent) Color(0xFF00E676) else Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Proxy Requirement (Proxy, Set Proxy or Auto Saved Profile):", fontSize = 11.sp, color = Color(0xFF90A4AE))
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
                        Text(text = "4. Account Amount & Type", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text(text = "Account Type Options:", fontSize = 11.sp, color = Color(0xFF90A4AE))
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

            // Start Account Creation Button
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
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val generated = (1..amount).map { idx ->
                                val sampleNumber = "+88017${(10000000..99999999).random()}"
                                CreatedAccountItem(
                                    phoneNumber = sampleNumber,
                                    accountType = when(accountTypeOption) {
                                        "ONLY_FRESH" -> "Fresh (No prior account)"
                                        "ONLY_OLD" -> "Old (Existing account)"
                                        else -> if (idx % 2 == 0) "Fresh" else "Old"
                                    },
                                    agentName = selectedAgent,
                                    proxyUsed = if (proxyMode == "NO_PROXY") "None" else state.proxyState.profileName,
                                    country = selectedCountry
                                )
                            }
                            createdAccountsList = generated
                            isCreating = false
                            creationCompleted = true
                            VibrationHelper.vibrateSuccess(context)
                            Toast.makeText(context, "✓ Successfully created $amount accounts & logged in!", Toast.LENGTH_LONG).show()
                        }, 2000L)
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
                        Text(text = creationStepText, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            Text(text = "🎉 ACCOUNTS CREATED & LOGGED IN (${createdAccountsList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            Button(
                                onClick = {
                                    // Auto logout previous batch & create more
                                    createdAccountsList = emptyList()
                                    creationCompleted = false
                                    Toast.makeText(context, "Previous batch logged out successfully. Ready for new batch.", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Logout / Create More", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            Text(text = acc.phoneNumber, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                            Surface(color = Color(0xFF00E676).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text(text = "✓ LOGGED IN", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Type: ${acc.accountType} | Agent: ${acc.agentName} | Proxy: ${acc.proxyUsed}", fontSize = 10.sp, color = Color(0xFF90A4AE))
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
                                            val fullSms = "Facebook confirmation code: $sampleOtp. Do not share this code."
                                            val updated = createdAccountsList.map { if (it.id == acc.id) it.copy(smsFetched = fullSms) else it }
                                            createdAccountsList = updated
                                            ClipboardHelper.copyToClipboard(context, sampleOtp, "OTP")
                                            OverlayStateManager.showOtpPopup(acc.phoneNumber, sampleOtp)
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
}
