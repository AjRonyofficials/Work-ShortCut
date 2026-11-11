package com.example.ui.screens

import android.widget.Toast
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

    val cardBgColor = Color(0xFF0D1726)
    val innerBoxBgColor = Color(0xFF08101C)
    val cardBorderColor = Color(0xFF1C2E4A)
    val purpleAccent = Color(0xFFA855F7)
    val cyanAccent = Color(0xFF00E5FF)
    val neonGreen = Color(0xFF00E676)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFE2E8F0),
        focusedContainerColor = innerBoxBgColor,
        unfocusedContainerColor = innerBoxBgColor,
        focusedBorderColor = purpleAccent,
        unfocusedBorderColor = Color(0xFF233856),
        focusedLabelColor = purpleAccent,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = purpleAccent
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("pw_copy_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.2.dp, purpleAccent.copy(alpha = 0.45f))
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
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(purpleAccent, Color(0xFF6A1B9A))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Password Copy",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PW Copy & Password Studio",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    Text(
                        text = "সংরক্ষিত পাসওয়ার্ড অথবা শক্তিশালী র‍্যান্ডম পাসওয়ার্ড জেনারেটর ১-ট্যাপে সরাসরি কপি করুন",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 2. Mode Selector: Saved PW vs Random Strong Generator
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Saved Password Mode Button
                Surface(
                    onClick = { OverlayStateManager.setRandomPasswordMode(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (!state.isRandomPasswordMode) purpleAccent.copy(alpha = 0.22f) else innerBoxBgColor,
                    border = BorderStroke(1.2.dp, if (!state.isRandomPasswordMode) purpleAccent else Color(0xFF1E3A56))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = if (!state.isRandomPasswordMode) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Saved PW (সেভ)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            color = if (!state.isRandomPasswordMode) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }

                // Random Generator Mode Button
                Surface(
                    onClick = { OverlayStateManager.setRandomPasswordMode(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.isRandomPasswordMode) cyanAccent.copy(alpha = 0.2f) else innerBoxBgColor,
                    border = BorderStroke(1.2.dp, if (state.isRandomPasswordMode) cyanAccent else Color(0xFF1E3A56))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (state.isRandomPasswordMode) cyanAccent else Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Random Generator",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            color = if (state.isRandomPasswordMode) cyanAccent else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // 3. IF SAVED PASSWORD MODE: Display Saved Password Management Card
        if (!state.isRandomPasswordMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, purpleAccent.copy(alpha = 0.45f))
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
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (state.savedPasswordText.isNotEmpty()) neonGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (state.savedPasswordText.isNotEmpty()) neonGreen.copy(alpha = 0.45f) else Color.Gray)
                        ) {
                            Text(
                                text = if (state.savedPasswordText.isNotEmpty()) "সক্রিয় ✓" else "খালি",
                                color = if (state.savedPasswordText.isNotEmpty()) neonGreen else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
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
                                        contentDescription = "Toggle Visibility",
                                        tint = Color(0xFF94A3B8)
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
                                        tint = cyanAccent
                                    )
                                }
                            }
                        },
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_saved_password")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                OverlayStateManager.setSavedPasswordText(context, passwordInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_save_password")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PW", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                if (passwordInput.isNotEmpty()) {
                                    ClipboardHelper.copyToClipboard(context, passwordInput, "Saved Password")
                                    VibrationHelper.vibrateSuccess(context)
                                    Toast.makeText(context, "✓ ক্লিপবোর্ডে কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            border = BorderStroke(1.2.dp, neonGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_test_copy_password")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = neonGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Now", fontWeight = FontWeight.ExtraBold, color = neonGreen)
                        }
                    }
                }
            }
        }

        // 4. IF RANDOM STRONG GENERATOR MODE: Display Random Password Generator
        if (state.isRandomPasswordMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, cyanAccent.copy(alpha = 0.45f))
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
                                tint = cyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Random Strong Password Generator",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
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
                                tint = cyanAccent
                            )
                        }
                    }

                    // A. Length Selection: 8, 12, 16 Digits
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "পাসওয়ার্ডের দৈর্ঘ্য (Digit Length):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
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
                                            text = "$len Digits",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
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
                                        selectedContainerColor = cyanAccent.copy(alpha = 0.2f),
                                        selectedLabelColor = cyanAccent,
                                        selectedLeadingIconColor = cyanAccent,
                                        containerColor = innerBoxBgColor,
                                        labelColor = Color(0xFFCBD5E1)
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
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.randomPwIncludeSymbols,
                                onClick = { OverlayStateManager.setRandomPasswordSymbols(true) },
                                label = {
                                    Text(
                                        text = "With Symbols (!@#$)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp
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
                                    selectedContainerColor = cyanAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = cyanAccent,
                                    selectedLeadingIconColor = cyanAccent,
                                    containerColor = innerBoxBgColor,
                                    labelColor = Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = !state.randomPwIncludeSymbols,
                                onClick = { OverlayStateManager.setRandomPasswordSymbols(false) },
                                label = {
                                    Text(
                                        text = "No Symbols (A-Z,0-9)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp
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
                                    selectedContainerColor = cyanAccent.copy(alpha = 0.2f),
                                    selectedLabelColor = cyanAccent,
                                    selectedLeadingIconColor = cyanAccent,
                                    containerColor = innerBoxBgColor,
                                    labelColor = Color(0xFFCBD5E1)
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
                        shape = RoundedCornerShape(14.dp),
                        color = innerBoxBgColor,
                        border = BorderStroke(1.5.dp, cyanAccent.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ক্লিক করলেই কপি হবে এবং নতুন পাসওয়ার্ড আসবে:",
                                fontSize = 11.sp,
                                color = cyanAccent
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = activePw,
                                fontSize = 21.sp,
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
                                    tint = neonGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tap to Copy & Generate Next ➔",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = neonGreen
                                )
                            }
                        }
                    }

                    // D. Big Action Copy & Auto-Regenerate Next Button
                    Button(
                        onClick = {
                            OverlayStateManager.copyRandomPasswordToClipboard(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF041E2B)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Copy & Auto Generate Next PW",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF041E2B)
                        )
                    }
                }
            }
        }

        // 5. Floating Overlay Integration Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.dp, cardBorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💡 ফ্লোয়েটিং বাবল ও পাসওয়ার্ড কপি গাইড:",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = cyanAccent
                )

                Text(
                    text = "• মোড সিলেক্টর থেকে আপনি চাইলে 'Saved PW' অথবা 'Random Generator' যেকোনোটি বেছে নিতে পারেন।\n" +
                            "• 'Random Generator' মোডে থাকলে প্রতিবার বাটন বা পাসওয়ার্ডে চাপ দিলে পাসওয়ার্ড কীবোর্ডে কপি হবে এবং সাথে সাথে নতুন আরেকটি ফ্রেশ পাসওয়ার্ড জেনারেট হয়ে যাবে।\n" +
                            "• ফ্লোয়েটিং বাবলের 'PW Copy' বাটনে চাপ দিলেও আপনার নির্বাচিত মোড (সেভ করা বা র‍্যান্ডম পাসওয়ার্ড) সরাসরি কীবোর্ডে কপি হয়ে যাবে!",
                    fontSize = 12.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
