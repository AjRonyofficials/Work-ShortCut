package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val totp = state.totpResult

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("two_factor_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BrandAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = BrandAmber,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "2FA Authenticator Generator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-generates & copies 6-digit TOTP code instantly",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    .height(54.dp)
                    .testTag("one_tap_paste_2fa_key_and_copy_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAmber)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ PASTE KEY & GET 6-DIGIT CODE",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color.Black
                )
            }
        }

        // Active TOTP Display Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandAmber.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SECURITY CODE (RFC 6238)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandAmber
                        )
                        if (totp != null) {
                            TotpCountdownRing(
                                remainingSeconds = totp.remainingSeconds,
                                progress = totp.progress,
                                size = 36.dp,
                                strokeWidth = 3.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6-digit code large text
                    Surface(
                        onClick = {
                            OverlayStateManager.copyCurrentTotpCode(context)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandAmber.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("totp_code_display_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = totp?.formattedCode ?: "ENTER 2FA KEY",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp,
                                color = if (totp != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
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
                                    text = "Tap to copy code to clipboard",
                                    fontSize = 12.sp,
                                    color = BrandAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAmber)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Copy 2FA Code (${totp?.code ?: ""})",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Paste Facebook / Instagram 2FA Secret Key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
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
                            placeholder = { Text("e.g. JBSWY3DPEHPK3PXP") },
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
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("paste_2fa_clipboard_btn")
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Optional Save to accounts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = accountTitle,
                            onValueChange = { accountTitle = it },
                            placeholder = { Text("Account Name (e.g. FB Work, IG Main)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }

        // Saved 2FA Accounts List
        item {
            Text(
                text = "Saved 2FA Accounts (${savedKeys.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (savedKeys.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No saved 2FA keys yet. Paste your key above and tap Save!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(savedKeys) { itemKey ->
                val itemTotp = TotpHelper.generateTotp(itemKey.secretKey)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = itemKey.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = itemTotp?.formattedCode ?: "Invalid key",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = BrandAmber
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
                                Spacer(modifier = Modifier.width(8.dp))
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
                                scope.launch { repository?.deleteTwoFactorKey(itemKey) }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete key",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
