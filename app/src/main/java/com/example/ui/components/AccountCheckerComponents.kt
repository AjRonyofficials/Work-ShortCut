package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.AccountCheckResult
import com.example.service.AccountCheckerManager
import com.example.service.AccountCheckerMode

private val OldRedColor = Color(0xFFFF5252)
private val OldRedBg = Color(0xFF3B1219)
private val FreshGreenColor = Color(0xFF00E676)
private val FreshGreenBg = Color(0xFF0A2E1F)
private val CyanAccent = Color(0xFF38BDF8)

/**
 * Prominent RED (OLD - Account Exists) or GREEN (NEW/FRESH - No Account) Highlight Badge
 * shown on every provisioned number across all Virtual Number panels.
 */
@Composable
fun AccountStatusHighlightBadge(
    accountStatus: String,
    onRecheckClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isOld = accountStatus.equals("OLD", ignoreCase = true)
    val isFresh = accountStatus.equals("FRESH", ignoreCase = true) || accountStatus.equals("NEW", ignoreCase = true)
    val isChecking = accountStatus.equals("CHECKING", ignoreCase = true)

    val bgColor = when {
        isOld -> OldRedBg
        isFresh -> FreshGreenBg
        isChecking -> Color(0xFF2D2308)
        else -> Color(0xFF152232)
    }

    val borderColor = when {
        isOld -> OldRedColor
        isFresh -> FreshGreenColor
        isChecking -> Color(0xFFFFD600)
        else -> Color(0xFF334155)
    }

    val textColor = when {
        isOld -> OldRedColor
        isFresh -> FreshGreenColor
        isChecking -> Color(0xFFFFD600)
        else -> Color(0xFF94A3B8)
    }

    val labelText = when {
        isOld -> "🔴 OLD (ACCOUNT)"
        isFresh -> "🟢 NEW (FRESH)"
        isChecking -> "⏳ CHECKING..."
        else -> "🔍 CHECK AC"
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .then(
                if (onRecheckClick != null) {
                    Modifier.clickable { onRecheckClick() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = Color(0xFFFFD600),
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = labelText,
                color = textColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Compact bar for VirtualNumbersSection header so the user can see active checker mode,
 * switch between Option 1 (Fresh Only), Option 2 (Old Only), Option 3 (Both),
 * see background retry status, and open the `account_checker.py` dialog.
 */
@Composable
fun VirtualNumberAccountCheckerBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AccountCheckerManager.init(context)
    }
    val checkerState by AccountCheckerManager.state.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2B)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.2.dp,
            if (!checkerState.isEnabled) Color(0xFF263B52)
            else when (checkerState.mode) {
                AccountCheckerMode.FRESH_ONLY -> FreshGreenColor.copy(alpha = 0.65f)
                AccountCheckerMode.OLD_ONLY -> OldRedColor.copy(alpha = 0.65f)
                AccountCheckerMode.BOTH -> CyanAccent.copy(alpha = 0.65f)
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Title + ON/OFF Toggle + checker.py Button
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
                            .background(if (checkerState.isEnabled) FreshGreenColor else Color(0xFF64748B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACCOUNT CHECKER.PY:",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (checkerState.isEnabled) FreshGreenColor.copy(alpha = 0.2f) else Color(0xFF334155),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(
                            1.dp,
                            if (checkerState.isEnabled) FreshGreenColor else Color(0xFF64748B)
                        ),
                        modifier = Modifier
                            .clickable { AccountCheckerManager.toggleEnabled() }
                            .testTag("vn_account_checker_toggle")
                    ) {
                        Text(
                            text = if (checkerState.isEnabled) "ON" else "OFF",
                            color = if (checkerState.isEnabled) FreshGreenColor else Color(0xFFCBD5E1),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Stats Pill (Green = Fresh, Red = Old)
                    Surface(
                        color = Color(0xFF0A121C),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E344B))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🟢 ${checkerState.freshFoundCount}",
                                color = FreshGreenColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🔴 ${checkerState.oldFoundCount}",
                                color = OldRedColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Secured Internal Engine (Locked - Users cannot view/edit/upload script)
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Locked Engine",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🔒 SECURED",
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3 Mode Selection Chips (Highlighted active option: 1. Fresh Only, 2. Old Only, 3. Both)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AccountCheckerMode.entries.forEach { modeItem ->
                    val isSelected = checkerState.isEnabled && checkerState.mode == modeItem
                    val accentColor = when (modeItem) {
                        AccountCheckerMode.FRESH_ONLY -> FreshGreenColor
                        AccountCheckerMode.OLD_ONLY -> OldRedColor
                        AccountCheckerMode.BOTH -> CyanAccent
                    }
                    val chipTitle = when (modeItem) {
                        AccountCheckerMode.FRESH_ONLY -> "1) শুধু Fresh 🟢"
                        AccountCheckerMode.OLD_ONLY -> "2) শুধু Old 🔴"
                        AccountCheckerMode.BOTH -> "3) Old & Fresh ২টাই"
                    }

                    Surface(
                        color = if (isSelected) accentColor.copy(alpha = 0.24f) else Color(0xFF0A111A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else Color(0xFF1E3248)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                AccountCheckerManager.setMode(modeItem)
                                Toast.makeText(
                                    context,
                                    "✓ সিলেক্ট হয়েছে: ${modeItem.titleBn}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .testTag("vn_checker_mode_${modeItem.key}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = chipTitle,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isSelected) "✓ SELECTED" else "Tap to select",
                                color = if (isSelected) accentColor else Color(0xFF64748B),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Legend & Active Mode Explanation
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (checkerState.isEnabled) {
                        when (checkerState.mode) {
                            AccountCheckerMode.FRESH_ONLY -> "সক্রিয়: শুধু 🟢 Fresh (New) নম্বর আসবে (না থাকলে ব্যাকগ্রাউন্ডে অটো রি-ট্রাই দিবে)"
                            AccountCheckerMode.OLD_ONLY -> "সক্রিয়: শুধু 🔴 Old (Account) নম্বর আসবে ও অটো কপি হবে (ব্যাকগ্রাউন্ড রি-ট্রাই)"
                            AccountCheckerMode.BOTH -> "সক্রিয়: Old ও Fresh ২টাই আসবে, কিন্তু 🔴 OLD নাকি 🟢 NEW তা হাইলাইট থাকবে"
                        }
                    } else {
                        "অফ আছে: সেটিংস বা উপরের বাটন থেকে Account Checker অন করুন"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            // Live Background Retry Status Banner
            AnimatedVisibility(visible = checkerState.isCheckingInBackground && !checkerState.retryStatusMessage.isNullOrEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF06283D),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyanAccent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = CyanAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = checkerState.retryStatusMessage ?: "Checking number...",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full Settings Card for `SettingsSection.kt` to turn Account Checker ON/OFF and select Option 1, 2, or 3.
 */
@Composable
fun SettingsAccountCheckerCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AccountCheckerManager.init(context)
    }
    val checkerState by AccountCheckerManager.state.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
        border = BorderStroke(
            1.5.dp,
            if (checkerState.isEnabled) FreshGreenColor.copy(alpha = 0.6f) else Color(0xFF334155)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FreshGreenColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = FreshGreenColor,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Virtual Number Account Checker (checker.py)",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp
                        )
                        Text(
                            text = "একাউন্ট থাকলে 🔴 RED (Old) আর না থাকলে 🟢 GREEN (Fresh/New)",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = checkerState.isEnabled,
                    onCheckedChange = { AccountCheckerManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = FreshGreenColor
                    ),
                    modifier = Modifier.testTag("settings_account_checker_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Color Legend Row (Red = Old, Green = Fresh)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = OldRedBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, OldRedColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🔴 RED = OLD (একাউন্ট আছে)",
                            color = OldRedColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Surface(
                    color = FreshGreenBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, FreshGreenColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🟢 GREEN = NEW (একাউন্ট নেই)",
                            color = FreshGreenColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "যেকোনো একটি অপশন সিলেক্ট করুন (নম্বর সবসময় OLD বা NEW হাইলাইট থাকবে):",
                color = CyanAccent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3 Selectable Mode Cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountCheckerMode.entries.forEach { modeItem ->
                    val isSelected = checkerState.mode == modeItem
                    val modeAccent = when (modeItem) {
                        AccountCheckerMode.FRESH_ONLY -> FreshGreenColor
                        AccountCheckerMode.OLD_ONLY -> OldRedColor
                        AccountCheckerMode.BOTH -> CyanAccent
                    }

                    Surface(
                        color = if (isSelected) modeAccent.copy(alpha = 0.16f) else Color(0xFF0A1018),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) modeAccent else Color(0xFF1E2D40)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AccountCheckerManager.setMode(modeItem)
                                Toast.makeText(
                                    context,
                                    "✓ সিলেক্ট করা হয়েছে: ${modeItem.titleBn}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .testTag("settings_checker_mode_${modeItem.key}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = modeItem.titleBn,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (isSelected) {
                                        Surface(
                                            color = modeAccent,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "SELECTED ✓",
                                                color = Color.Black,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = modeItem.subtitleBn,
                                    color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) modeAccent else Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Background Retry Limit & Script Configuration Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Background Auto-Retry Limit: ${checkerState.maxRetries}x",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active Script: ${checkerState.pythonScriptFileName}",
                        color = FreshGreenColor,
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val nextRetries = if (checkerState.maxRetries >= 10) 3 else checkerState.maxRetries + 2
                            AccountCheckerManager.setMaxRetries(nextRetries)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${checkerState.maxRetries}x Retry", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🔒 Engine Locked", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for uploading/pasting `account checker.py` and live-testing phone numbers.
 */
@Composable
fun AccountCheckerScriptDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val checkerState by AccountCheckerManager.state.collectAsState()
    var scriptText by remember(checkerState.pythonScriptCode) { mutableStateOf(checkerState.pythonScriptCode) }
    var fileName by remember(checkerState.pythonScriptFileName) { mutableStateOf(checkerState.pythonScriptFileName) }
    var testPhoneInput by remember { mutableStateOf("") }
    var isTestingNumber by remember { mutableStateOf(false) }
    var lastTestResult by remember { mutableStateOf<AccountCheckResult?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    scriptText = content
                    fileName = "account_checker.py"
                    AccountCheckerManager.updatePythonScript(content, fileName)
                    Toast.makeText(context, "✓ account_checker.py সফলভাবে লোড হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ফাইল রিড করতে সমস্যা: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1622)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, CyanAccent),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = FreshGreenColor, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "🐍 account_checker.py কনফিগারেশন",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "আপনার checker.py ফাইল আপলোড বা কোড পেস্ট করুন",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Upload .py file or Reset buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload checker.py", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            AccountCheckerManager.resetPythonScriptToDefault()
                            scriptText = AccountCheckerManager.DEFAULT_CHECKER_PY_SCRIPT
                            Toast.makeText(context, "ডিফল্ট checker.py রিসেট হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset Default", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Python Script Editor Box
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    label = { Text("account_checker.py কোড (Python / API Rules)", color = CyanAccent, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FreshGreenColor,
                        unfocusedTextColor = Color(0xFFE2E8F0),
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = Color(0xFF1E3A56),
                        focusedContainerColor = Color(0xFF070C12),
                        unfocusedContainerColor = Color(0xFF070C12)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        AccountCheckerManager.updatePythonScript(scriptText, fileName)
                        Toast.makeText(context, "✓ account_checker.py সেভ ও অ্যাক্টিভ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FreshGreenColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SAVE & ACTIVATE CHECKER.PY", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }

                HorizontalDivider(
                    color = Color(0xFF1E3A56),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Live Test Box
                Text(
                    text = "🔍 লাইভ নম্বর টেস্ট করুন (Test Any Number):",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = testPhoneInput,
                        onValueChange = { testPhoneInput = it },
                        placeholder = { Text("e.g. +237620123456", color = Color(0xFF64748B), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color(0xFF1E3A56)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (testPhoneInput.isBlank()) return@Button
                            isTestingNumber = true
                            AccountCheckerManager.checkNumberAsync(testPhoneInput, forceRefresh = true) { res ->
                                isTestingNumber = false
                                lastTestResult = res
                            }
                        },
                        enabled = !isTestingNumber && testPhoneInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingNumber) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }

                lastTestResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val isOld = res.hasAccount
                    Surface(
                        color = if (isOld) OldRedBg else FreshGreenBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, if (isOld) OldRedColor else FreshGreenColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = res.phoneNumber,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isOld) "একাউন্ট পাওয়া গেছে -> RED (OLD ACCOUNT)" else "কোনো একাউন্ট নেই -> GREEN (FRESH / NEW)",
                                    color = if (isOld) OldRedColor else FreshGreenColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            AccountStatusHighlightBadge(accountStatus = res.status)
                        }
                    }
                }
            }
        }
    }
}
