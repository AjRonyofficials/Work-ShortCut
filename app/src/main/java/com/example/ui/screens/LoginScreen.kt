package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AuthManager
import com.example.util.VibrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val cloudStatus by AuthManager.cloudSyncStatus.collectAsState()

    // Automatically sync latest Admin-Verified & Approved accounts when LoginScreen opens
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            AuthManager.syncFromCloudInternal()
        }
    }

    val performLogin: () -> Unit = {
        if (!isLoggingIn) {
            isLoggingIn = true
            errorMessage = null
            coroutineScope.launch {
                val normalizedEmail = email.trim().let {
                    if (it.isNotEmpty() && !it.contains("@")) "$it@gmail.com" else it
                }
                val (success, msg) = withContext(Dispatchers.IO) {
                    AuthManager.login(normalizedEmail, password)
                }
                isLoggingIn = false
                if (success) {
                    VibrationHelper.vibrateSuccess(context)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                } else {
                    errorMessage = msg
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070D14),
                        Color(0xFF0C1724),
                        Color(0xFF050A10)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111D2C).copy(alpha = 0.95f)),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF00B0FF).copy(alpha = 0.6f),
                        Color(0xFF673AB7).copy(alpha = 0.4f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shield / Logo Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00B0FF), Color(0xFF0066FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Security",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "WORK SHORTCUT",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Admin Verified & Approved Login System",
                    fontSize = 11.5.sp,
                    color = Color(0xFF80D8FF),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = Color(0xFF0A1929),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))
                ) {
                    Text(
                        text = "☁️ $cloudStatus",
                        fontSize = 10.sp,
                        color = Color(0xFF4ADE80),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Email Address (Gmail)") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF00B0FF))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color(0xFF26415E),
                        focusedContainerColor = Color(0xFF0B141E),
                        unfocusedContainerColor = Color(0xFF0B141E),
                        focusedLabelColor = Color(0xFF00B0FF),
                        unfocusedLabelColor = Color(0xFF78909C)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00B0FF))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Color(0xFF78909C)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { performLogin() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color(0xFF26415E),
                        focusedContainerColor = Color(0xFF0B141E),
                        unfocusedContainerColor = Color(0xFF0B141E),
                        focusedLabelColor = Color(0xFF00B0FF),
                        unfocusedLabelColor = Color(0xFF78909C)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF3B1219),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFFF8A80),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Login Button
                Button(
                    onClick = { performLogin() },
                    enabled = !isLoggingIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0091EA),
                        disabledContainerColor = Color(0xFF0091EA).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "এডমিন ভেরিফিকেশন ও অনুমোদন যাচাই হচ্ছে...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LOGIN (লগইন করুন)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Cloud Refresh Button
                var isSyncing by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        isSyncing = true
                        coroutineScope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                AuthManager.syncFromCloudInternal()
                            }
                            isSyncing = false
                            val toastMsg = if (ok) {
                                "✓ এডমিন সার্ভার থেকে নতুন আইডি/পাস ও Approval সিঙ্ক সম্পন্ন!"
                            } else {
                                "⚠️ সার্ভার কানেকশন চেক করুন"
                            }
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            errorMessage = null
                            VibrationHelper.vibrateSuccess(context)
                        }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00B0FF), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সার্ভার থেকে সিঙ্ক করা হচ্ছে...", fontSize = 11.sp, color = Color(0xFF00B0FF))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00B0FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("নতুন আইডি/পাস সিঙ্ক করুন (SYNC ACCOUNTS)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF00B0FF))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contact Developer (@ismailislamrony1)
                Surface(
                    color = Color(0xFF0088CC).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0088CC).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://t.me/ismailislamrony1")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                com.example.util.ClipboardHelper.copyToClipboard(
                                    context,
                                    "@ismailislamrony1",
                                    "Developer Telegram"
                                )
                                Toast.makeText(
                                    context,
                                    "টেলিগ্রাম ইউজারনেম কপি করা হয়েছে: @ismailislamrony1",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0088CC)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Telegram",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "To get Verified ID / Pass or Login Approval",
                                color = Color(0xFF80D8FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Contact Developer: @ismailislamrony1",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = Color(0xFF1E2E40).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔒 এডমিন ভেরিফাইড ও অ্যাপ্রুভাল সিস্টেম",
                            color = Color(0xFF81D4FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "এডমিনের ভেরিফিকেশন + জেনারেট করা জিমেইল/পাসওয়ার্ড + লগইন Approval ছাড়া কেউ প্রবেশ করতে পারবে না।",
                            color = Color(0xFF90A4AE),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
