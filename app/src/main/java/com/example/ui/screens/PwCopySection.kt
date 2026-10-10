package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGreen
import com.example.ui.theme.BrandSky
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper

@Composable
fun PwCopySection(
    state: OverlayUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var passwordInput by remember(state.savedPasswordText) { mutableStateOf(state.savedPasswordText) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("pw_copy_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFAB47BC), Color(0xFF6A1B9A))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Password Copy",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PW Copy (পাসওয়ার্ড কপি)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "সংরক্ষিত পাসওয়ার্ড অথবা শক্তিশালী র‍্যান্ডম পাসওয়ার্ড জেনারেটর ১-ট্যাপে সরাসরি কপি করুন",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 2. Mode Selector: Saved PW vs Random Strong Generator
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Saved Password Mode Button
                Surface(
                    onClick = { OverlayStateManager.setRandomPasswordMode(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (!state.isRandomPasswordMode) Color(0xFF7B1FA2).copy(alpha = 0.15f) else Color.Transparent,
                    border = if (!state.isRandomPasswordMode) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7B1FA2)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = if (!state.isRandomPasswordMode) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Saved PW (সেভ)",
                            fontWeight = if (!state.isRandomPasswordMode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (!state.isRandomPasswordMode) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Random Generator Mode Button
                Surface(
                    onClick = { OverlayStateManager.setRandomPasswordMode(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (state.isRandomPasswordMode) Color(0xFF0091EA).copy(alpha = 0.15f) else Color.Transparent,
                    border = if (state.isRandomPasswordMode) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0091EA)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (state.isRandomPasswordMode) Color(0xFF0091EA) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Random Generator",
                            fontWeight = if (state.isRandomPasswordMode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (state.isRandomPasswordMode) Color(0xFF0091EA) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. IF SAVED PASSWORD MODE: Display Saved Password Management Card
        if (!state.isRandomPasswordMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "সংরক্ষিত পাসওয়ার্ড / ফিক্সড টেক্সট",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (state.savedPasswordText.isNotEmpty()) BrandGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (state.savedPasswordText.isNotEmpty()) "সক্রিয় ✓" else "খালি",
                                color = if (state.savedPasswordText.isNotEmpty()) BrandGreen else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("পাসওয়ার্ড বা টেক্সট লিখুন") },
                        placeholder = { Text("যেমন: MySecretPass123@#") },
                        singleLine = false,
                        maxLines = 3,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrEmpty()) {
                                            passwordInput = clip
                                            VibrationHelper.vibrateClick(context)
                                            Toast.makeText(context, "ক্লিপবোর্ড থেকে পেস্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from Clipboard",
                                        tint = BrandBlue
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7B1FA2),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_saved_password")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Save Button
                        Button(
                            onClick = {
                                OverlayStateManager.setSavedPasswordText(context, passwordInput)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7B1FA2)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_password")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PW", fontWeight = FontWeight.Bold)
                        }

                        // Test Copy Button
                        OutlinedButton(
                            onClick = {
                                if (passwordInput.isNotEmpty()) {
                                    ClipboardHelper.copyToClipboard(context, passwordInput, "Saved Password")
                                    VibrationHelper.vibrateSuccess(context)
                                    Toast.makeText(context, "✓ ক্লিপবোর্ডে কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_copy_password")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Now")
                        }
                    }
                }
            }
        }

        // 4. IF RANDOM STRONG GENERATOR MODE: Display Random Password Generator
        if (state.isRandomPasswordMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF0091EA),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Random Strong Password Generator",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                OverlayStateManager.regenerateRandomPassword()
                                VibrationHelper.vibrateClick(context)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = Color(0xFF0091EA)
                            )
                        }
                    }

                    // A. Length Selection: 8, 12, 16 Digits
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "পাসওয়ার্ডের দৈর্ঘ্য (Digit Length):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(8, 12, 16).forEach { len ->
                                val isSelected = state.randomPwLength == len
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { OverlayStateManager.setRandomPasswordLength(len) },
                                    label = {
                                        Text(
                                            text = "$len Digits" + if (len == 12) " (Recommended)" else "",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0091EA).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFF0091EA)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // B. Symbol Option: With Symbol vs Without Symbol
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "সিম্বল সেটিংস (Symbol Option):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // With Symbols
                            FilterChip(
                                selected = state.randomPwIncludeSymbols,
                                onClick = { OverlayStateManager.setRandomPasswordSymbols(true) },
                                label = {
                                    Text(
                                        text = "With Symbols (!@#$)",
                                        fontWeight = if (state.randomPwIncludeSymbols) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (state.randomPwIncludeSymbols) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0091EA).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF0091EA)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // Without Symbols
                            FilterChip(
                                selected = !state.randomPwIncludeSymbols,
                                onClick = { OverlayStateManager.setRandomPasswordSymbols(false) },
                                label = {
                                    Text(
                                        text = "Without Symbols (A-Z, 0-9)",
                                        fontWeight = if (!state.randomPwIncludeSymbols) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (!state.randomPwIncludeSymbols) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0091EA).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF0091EA)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // C. Live Generated Password Display Card (Tap-to-Copy)
                    val activePw = if (state.currentRandomPassword.isNotBlank()) state.currentRandomPassword
                    else OverlayStateManager.generateStrongPassword(state.randomPwLength, state.randomPwIncludeSymbols)

                    Surface(
                        onClick = {
                            OverlayStateManager.copyRandomPasswordToClipboard(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF102027),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0091EA).copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ক্লিক করলেই কপি হবে এবং নতুন পাসওয়ার্ড আসবে:",
                                fontSize = 11.sp,
                                color = Color(0xFF80D8FF)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = activePw,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tap to Copy & Generate Next ➔",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                            }
                        }
                    }

                    // D. Big Action Copy & Auto-Regenerate Next Button
                    Button(
                        onClick = {
                            OverlayStateManager.copyRandomPasswordToClipboard(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0091EA)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Copy & Auto Generate Next PW",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 5. Floating Overlay Integration Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1B2333)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💡 ফ্লোয়েটিং বাবল ও পাসওয়ার্ড কপি গাইড:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF80D8FF)
                )

                Text(
                    text = "• মোড সিলেক্টর থেকে আপনি চাইলে 'Saved PW' অথবা 'Random Generator' যেকোনোটি বেছে নিতে পারেন।\n" +
                            "• 'Random Generator' মোডে থাকলে প্রতিবার বাটন বা পাসওয়ার্ডে চাপ দিলে পাসওয়ার্ড কীবোর্ডে কপি হবে এবং সাথে সাথে নতুন আরেকটি ফ্রেশ পাসওয়ার্ড জেনারেট হয়ে যাবে।\n" +
                            "• ফ্লোয়েটিং বাবলের 'PW Copy' বাটনে চাপ দিলেও আপনার নির্বাচিত মোড (সেভ করা বা র‍্যান্ডম পাসওয়ার্ড) সরাসরি কীবোর্ডে কপি হয়ে যাবে!",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
