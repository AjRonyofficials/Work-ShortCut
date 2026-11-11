package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WorkShortcutRepository
import com.example.data.local.model.TwoFactorKeyEntity
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.ui.components.TotpCountdownRing
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandGreen
import com.example.util.ClipboardHelper
import com.example.util.TotpHelper
import kotlinx.coroutines.launch

@Composable
fun TwoFactorSection(
    state: OverlayUiState,
    savedKeys: List<TwoFactorKeyEntity>,
    repository: WorkShortcutRepository?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputKey by remember(state.twoFactorKey) { mutableStateOf(state.twoFactorKey) }
    var accountTitle by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var editingKey by remember { mutableStateOf<TwoFactorKeyEntity?>(null) }
    val totp = state.totpResult

    val cardBgColor = Color(0xFF0D1726)
    val innerBoxBgColor = Color(0xFF08101C)
    val cardBorderColor = Color(0xFF1C2E4A)
    val cyanAccent = Color(0xFF00E5FF)
    val neonGreen = Color(0xFF00E676)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFE2E8F0),
        focusedContainerColor = innerBoxBgColor,
        unfocusedContainerColor = innerBoxBgColor,
        focusedBorderColor = BrandAmber,
        unfocusedBorderColor = Color(0xFF233856),
        focusedLabelColor = BrandAmber,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = BrandAmber
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("two_factor_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, BrandAmber.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(BrandAmber, Color(0xFFF59E0B))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "2FA Authenticator Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Instant 6-Digit TOTP Code Generator & Vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "${savedKeys.size} Saved",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // ⚡ 1-Tap Paste 2FA Key & Auto-Copy 6-Digit Code Button
        item {
            Button(
                onClick = {
                    val clip = ClipboardHelper.getFromClipboard(context)?.trim()
                    if (!clip.isNullOrEmpty()) {
                        inputKey = clip
                        OverlayStateManager.processGet2FaWithText(context, clip)
                    } else {
                        OverlayStateManager.triggerOverlay2FaPaste(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("one_tap_paste_2fa_key_and_copy_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAmber)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ PASTE KEY & GET 6-DIGIT CODE",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Active TOTP Display Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.5.dp, BrandAmber.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = BrandAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE SECURITY CODE (RFC 6238)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandAmber
                            )
                        }
                        if (totp != null) {
                            TotpCountdownRing(
                                remainingSeconds = totp.remainingSeconds,
                                progress = totp.progress,
                                size = 34.dp,
                                strokeWidth = 3.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6-digit code large text
                    Surface(
                        onClick = {
                            OverlayStateManager.copyCurrentTotpCode(context)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = innerBoxBgColor,
                        border = BorderStroke(1.2.dp, BrandAmber.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("totp_code_display_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = totp?.formattedCode ?: "ENTER 2FA KEY",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp,
                                color = if (totp != null) neonGreen else Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = BrandAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap anywhere to copy 6-digit code",
                                    fontSize = 12.sp,
                                    color = BrandAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1-Click Copy Code Button
                    Button(
                        onClick = {
                            OverlayStateManager.copyCurrentTotpCode(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("copy_2fa_code_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF042F16))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Copy 2FA Code (${totp?.code ?: "------"})",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF042F16)
                        )
                    }
                }
            }
        }

        // Paste 2FA Key Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Paste Facebook / Instagram / Google 2FA Secret Key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = { newKey ->
                                inputKey = newKey
                                OverlayStateManager.setTwoFactorKey(newKey)
                            },
                            placeholder = { Text("e.g. JBSWY3DPEHPK3PXP", fontSize = 12.sp) },
                            colors = textFieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("two_factor_key_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Paste from clipboard button
                        Button(
                            onClick = {
                                val clip = ClipboardHelper.getFromClipboard(context)?.trim()
                                if (!clip.isNullOrEmpty()) {
                                    inputKey = clip
                                    OverlayStateManager.processGet2FaWithText(context, clip)
                                } else {
                                    OverlayStateManager.triggerOverlay2FaPaste(context)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAmber),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("paste_2fa_clipboard_btn")
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Optional Save to accounts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = accountTitle,
                            onValueChange = { accountTitle = it },
                            placeholder = { Text("Account Name (e.g. FB Work, IG Main)", fontSize = 12.sp) },
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputKey.isNotEmpty()) {
                                    scope.launch {
                                        val title = if (accountTitle.isBlank()) "Account #${savedKeys.size + 1}" else accountTitle
                                        repository?.insertTwoFactorKey(
                                            TwoFactorKeyEntity(
                                                title = title,
                                                secretKey = inputKey.trim()
                                            )
                                        )
                                        accountTitle = ""
                                        ClipboardHelper.copyToClipboard(context, totp?.code ?: "", "2FA", "Saved $title & copied code!")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF041E2B))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontWeight = FontWeight.ExtraBold, color = Color(0xFF041E2B))
                        }
                    }
                }
            }
        }

        val filteredKeys = if (searchQuery.isBlank()) savedKeys else savedKeys.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.secretKey.contains(searchQuery, ignoreCase = true)
        }

        // Saved 2FA Accounts List (Option 4 Instant Search)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved 2FA Accounts (${savedKeys.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            if (savedKeys.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 2FA accounts by name or key...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_2fa_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        if (savedKeys.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No saved 2FA keys yet. Paste your key above and tap Save!",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else if (filteredKeys.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "\"$searchQuery\" দিয়ে কোনো 2FA কি পাওয়া যায়নি",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(filteredKeys) { itemKey ->
                val itemTotp = TotpHelper.generateTotp(itemKey.secretKey)
                Surface(
                    onClick = {
                        OverlayStateManager.setTwoFactorKey(itemKey.secretKey, autoGenerateAndCopy = context)
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = itemKey.title,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = itemTotp?.formattedCode ?: "Invalid key",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                color = neonGreen
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (itemTotp != null) {
                                TotpCountdownRing(
                                    remainingSeconds = itemTotp.remainingSeconds,
                                    progress = itemTotp.progress,
                                    size = 28.dp,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            // Tap to load into active generator & copy
                            IconButton(onClick = {
                                OverlayStateManager.setTwoFactorKey(itemKey.secretKey, autoGenerateAndCopy = context)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = BrandAmber
                                )
                            }

                            IconButton(onClick = {
                                editingKey = itemKey
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit 2FA Key in Database",
                                    tint = cyanAccent
                                )
                            }

                            IconButton(onClick = {
                                scope.launch { repository?.deleteTwoFactorKey(itemKey) }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete key",
                                    tint = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingKey != null) {
        val target = editingKey!!
        var editTitle by remember(target) { mutableStateOf(target.title) }
        var editSecret by remember(target) { mutableStateOf(target.secretKey) }

        AlertDialog(
            onDismissRequest = { editingKey = null },
            containerColor = cardBgColor,
            title = {
                Text(
                    text = "এডিট 2FA কি (Edit in Database)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("একাউন্ট টাইটেল (Title)") },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSecret,
                        onValueChange = { editSecret = it },
                        label = { Text("সিক্রেট কি (Secret Key)") },
                        colors = textFieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanSecret = editSecret.trim()
                        val cleanTitle = editTitle.trim().ifEmpty { "2FA Key" }
                        if (cleanSecret.isNotEmpty()) {
                            scope.launch {
                                repository?.updateTwoFactorKey(
                                    target.copy(
                                        title = cleanTitle,
                                        secretKey = cleanSecret
                                    )
                                )
                            }
                        }
                        editingKey = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAmber)
                ) {
                    Text("সেভ করুন (Save)", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { editingKey = null },
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("বাতিল", color = Color(0xFFCBD5E1))
                }
            }
        )
    }
}
