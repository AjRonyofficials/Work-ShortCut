package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AuthManager
import com.example.service.AuthUser
import com.example.service.GlobalCloudSyncService
import com.example.service.OtpHistoryManager
import com.example.service.WithdrawalManager
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

/**
 * Dedicated Agent Dashboard View
 * Shown when an Agent (who is not Prime/Sub Admin) opens the Agent Panel.
 * Allows the Agent ONLY to:
 * 1. Create users (typing just Name auto-generates random @gmail.com + password)
 * 2. View their created users' Total Today OTP, Total OTP, and Total Balance
 * 3. View their Commission rate & earned Today Commission
 * 4. Ban/Unban or Delete their own created users
 */
@Composable
fun AgentDashboardView(
    context: Context,
    agentEmail: String,
    managedUsers: List<AuthUser>,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    val agentUser = remember(managedUsers, agentEmail) {
        managedUsers.find { it.email.equals(agentEmail, ignoreCase = true) }
    }
    val myCreatedUsers = remember(managedUsers, agentEmail) {
        managedUsers.filter { it.createdByAgent.equals(agentEmail, ignoreCase = true) }
    }

    val totalUsersCount = myCreatedUsers.size
    val usersTodayOtp = myCreatedUsers.sumOf { it.todayOtps }
    val usersTotalOtp = myCreatedUsers.sumOf { it.totalOtps }
    val usersTotalBalance = myCreatedUsers.sumOf { it.balanceTk }

    val targetOtp = (agentUser?.agentCommissionOtpTarget ?: 100).coerceAtLeast(1)
    val commissionTkPerTarget = (agentUser?.agentCommissionTkPerTarget ?: 10.0).coerceAtLeast(0.0)
    val todayEarnedCommission = (usersTodayOtp.toDouble() / targetOtp.toDouble()) * commissionTkPerTarget

    var newUserName by remember { mutableStateOf("") }
    var newUserEmail by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    var autoGeneratedMode by remember { mutableStateOf(true) }
    var createdSuccessCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") } // "ALL", "ACTIVE", "BANNED"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090E17))
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Agent Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2E)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF0284C7)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Agent",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🤝 AGENT PANEL",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "OFFICIAL AGENT",
                                        color = Color(0xFF34D399),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${agentUser?.name ?: "Agent"} • $agentEmail",
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                if (isSyncing) return@IconButton
                                isSyncing = true
                                coroutineScope.launch {
                                    try {
                                        val usersJob = async { AuthManager.syncFromCloudAsync() }
                                        val cloudJob = async { GlobalCloudSyncService.pullFromCloud(context) }
                                        val otpJob = async { OtpHistoryManager.syncFromCloud(context) }
                                        val withJob = async { WithdrawalManager.syncFromCloud(context) }
                                        usersJob.await()
                                        cloudJob.await()
                                        otpJob.await()
                                        withJob.await()
                                        VibrationHelper.vibrateSuccess(context)
                                        Toast.makeText(context, "✓ এজেন্ট প্যানেল লাইভ সিঙ্ক সম্পন্ন!", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF34D399),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Color(0xFF34D399), modifier = Modifier.size(19.dp))
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                AuthManager.logout()
                                Toast.makeText(context, "লগআউট করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logout", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Agent Users Stats & Commission Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1829)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📊 আমার ইউজারদের লাইভ রিপোর্ট ও কমিশন",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "কমিশন রেট: প্রতি $targetOtp Today OTP = ৳${String.format(Locale.US, "%.2f", commissionTkPerTarget)}",
                                fontSize = 10.5.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            color = Color(0xFF064E3B).copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("আজকের কমিশন", fontSize = 8.sp, color = Color(0xFFA7F3D0), fontWeight = FontWeight.Bold)
                                Text(
                                    text = "৳${String.format(Locale.US, "%.2f", todayEarnedCommission)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdminStatPill(
                            title = "MY USERS",
                            count = totalUsersCount.toString(),
                            color = Color(0xFF60A5FA),
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatPill(
                            title = "TODAY OTP",
                            count = usersTodayOtp.toString(),
                            color = Color(0xFF00E676),
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatPill(
                            title = "TOTAL OTP",
                            count = usersTotalOtp.toString(),
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.weight(1f)
                        )
                        AdminStatPill(
                            title = "TOTAL BAL",
                            count = "৳${String.format(Locale.US, "%.1f", usersTotalBalance)}",
                            color = Color(0xFFF472B6),
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }
            }
        }

        // Agent Create User Card (Name -> Auto Random Gmail + Password)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CREATE NEW USER (AUTO GMAIL + PASS)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.clickable {
                                val (genEm, genPw) = AuthManager.generateRandomCredentialsFromName(newUserName.ifBlank { "user" })
                                newUserEmail = genEm
                                newUserPass = genPw
                                autoGeneratedMode = true
                            }
                        ) {
                            Text(
                                text = "🎲 নতুন র‍্যান্ডম Gmail",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "শুধুমাত্র ইউজারের নাম লিখলেই অটোমেটিক র‍্যান্ডম @gmail.com এবং পাসওয়ার্ড তৈরি হয়ে যাবে!",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { inputName ->
                            newUserName = inputName
                            if (autoGeneratedMode) {
                                if (inputName.isNotBlank()) {
                                    val (genEm, genPw) = AuthManager.generateRandomCredentialsFromName(inputName)
                                    newUserEmail = genEm
                                    newUserPass = genPw
                                } else {
                                    newUserEmail = ""
                                    newUserPass = ""
                                }
                            }
                        },
                        label = { Text("ইউজারের নাম লিখুন (যেমন: Rahim / Karim)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF34D399),
                            unfocusedBorderColor = Color(0xFF243B55),
                            focusedContainerColor = Color(0xFF090E17),
                            unfocusedContainerColor = Color(0xFF090E17)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newUserEmail,
                            onValueChange = {
                                newUserEmail = it
                                autoGeneratedMode = false
                            },
                            label = { Text("অটো র‍্যান্ডম ইমেইল (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.25f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF38BDF8),
                                unfocusedTextColor = Color(0xFF38BDF8),
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF243B55),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            )
                        )

                        OutlinedTextField(
                            value = newUserPass,
                            onValueChange = {
                                newUserPass = it
                                autoGeneratedMode = false
                            },
                            label = { Text("অটো পাসওয়ার্ড") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFFBBF24),
                                unfocusedTextColor = Color(0xFFFBBF24),
                                focusedBorderColor = Color(0xFFFBBF24),
                                unfocusedBorderColor = Color(0xFF243B55),
                                focusedContainerColor = Color(0xFF090E17),
                                unfocusedContainerColor = Color(0xFF090E17)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (newUserName.isBlank() && newUserEmail.isBlank()) {
                                Toast.makeText(context, "ইউজারের নাম লিখুন!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val (success, msg) = AuthManager.createManagedUser(
                                emailInput = newUserEmail,
                                passInput = newUserPass,
                                nameInput = newUserName
                            )
                            if (success) {
                                val parts = msg.split("|||")
                                val createdEm = parts.getOrNull(0) ?: newUserEmail.trim()
                                val createdPw = parts.getOrNull(1) ?: newUserPass.trim()
                                createdSuccessCredentials = Pair(createdEm, createdPw)
                                Toast.makeText(context, "✓ ইউজার তৈরি সফল: $createdEm", Toast.LENGTH_SHORT).show()
                                newUserName = ""
                                newUserEmail = ""
                                newUserPass = ""
                                autoGeneratedMode = true
                                VibrationHelper.vibrateSuccess(context)
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AUTO GENERATE & CREATE USER ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    createdSuccessCredentials?.let { (em, pw) ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFF064E3B).copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "✓ নতুন ইউজার অ্যাকাউন্ট তৈরি হয়েছে!", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    OutlinedButton(
                                        onClick = {
                                            val text = "Work ShortCut Login Credentials:\nEmail: $em\nPassword: $pw"
                                            ClipboardHelper.copyToClipboard(context, text, "Login Credentials")
                                            Toast.makeText(context, "লগইন তথ্য কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFF34D399)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF34D399))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Info", fontSize = 10.sp, color = Color(0xFF34D399))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Email: $em\nPassword: $pw", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // Agent's Created Users List with Search, Ban & Delete
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2D40)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MY CREATED USERS (${myCreatedUsers.size})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        val activeCount = myCreatedUsers.count { it.isActive && !it.isBanned }
                        val bannedCount = myCreatedUsers.count { it.isBanned }
                        Text(
                            text = "🟢 $activeCount Active  |  🚫 $bannedCount Banned",
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Filter Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("ALL", "ALL (${myCreatedUsers.size})", Color(0xFF38BDF8)),
                            Triple("ACTIVE", "🟢 ACTIVE (${myCreatedUsers.count { it.isActive && !it.isBanned }})", Color(0xFF10B981)),
                            Triple("BANNED", "🚫 BANNED (${myCreatedUsers.count { it.isBanned }})", Color(0xFFEF4444))
                        ).forEach { (key, label, col) ->
                            val isSel = statusFilter == key
                            Surface(
                                color = if (isSel) col.copy(alpha = 0.22f) else Color(0xFF0B1420),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSel) col else Color(0xFF1E2D40)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { statusFilter = key }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ইউজার সার্চ করুন (নাম বা ইমেইল)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF1E2D40),
                            focusedContainerColor = Color(0xFF090E17),
                            unfocusedContainerColor = Color(0xFF090E17)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val filteredUsers = remember(myCreatedUsers, searchQuery, statusFilter) {
                        val q = searchQuery.trim().lowercase()
                        myCreatedUsers.filter { u ->
                            val matchStatus = when (statusFilter) {
                                "ACTIVE" -> u.isActive && !u.isBanned
                                "BANNED" -> u.isBanned
                                else -> true
                            }
                            val matchQ = q.isEmpty() || u.name.lowercase().contains(q) || u.email.lowercase().contains(q)
                            matchStatus && matchQ
                        }
                    }

                    if (filteredUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (myCreatedUsers.isEmpty()) "আপনি এখনো কোনো ইউজার তৈরি করেননি। উপরে নাম লিখে ইউজার তৈরি করুন।"
                                else "সার্চে কোনো ইউজার পাওয়া যায়নি।",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredUsers.forEach { u ->
                                AgentCreatedUserCard(
                                    context = context,
                                    user = u
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AgentCreatedUserCard(
    context: Context,
    user: AuthUser
) {
    var isExpanded by rememberSaveable(user.email) { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF0F172A),
            title = { Text("ইউজার ডিলিট নিশ্চিতকরণ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Text(
                    "আপনি কি নিশ্চিত যে আপনার তৈরি করা ইউজার '${user.name}' (${user.email})-কে ডিলিট করতে চান?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        AuthManager.deleteManagedUser(user.email)
                        Toast.makeText(context, "✓ ইউজার ডিলিট করা হয়েছে: ${user.name}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("হ্যাঁ, ডিলিট করুন", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("বাতিল", color = Color.White, fontSize = 11.sp)
                }
            }
        )
    }

    Surface(
        color = if (user.isBanned) Color(0xFF180A0A) else Color(0xFF091420),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (user.isBanned) Color(0xFFEF4444) else Color(0xFF10B981).copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header row (Always visible + Dropdown toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (user.isBanned) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, if (user.isBanned) Color(0xFFEF4444) else Color(0xFF10B981))
                        ) {
                            Text(
                                text = if (user.isBanned) "🚫 BANNED" else "🟢 ACTIVE",
                                color = if (user.isBanned) Color(0xFFF87171) else Color(0xFF34D399),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = user.email,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Quick inline stats on header so Agent sees Today OTP, Total OTP & Balance immediately
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFF064E3B).copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Today OTP: ${user.todayOtps}",
                                color = Color(0xFF34D399),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Total OTP: ${user.totalOtps}",
                                color = Color(0xFFFBBF24),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(color = Color(0xFF0C2A44), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "৳${String.format(Locale.US, "%.2f", user.balanceTk)}",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = if (isExpanded) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isExpanded) Color(0xFF38BDF8) else Color(0xFF334155)),
                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                ) {
                    Text(
                        text = if (isExpanded) "▲ লুকান" else "▼ অপশন",
                        color = if (isExpanded) Color(0xFF38BDF8) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Dropdown Expanded Details & Ban/Delete Controls
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = Color(0xFF1E293B))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (passwordVisible) "Pass: ${user.passwordHash}" else "Pass: ••••••••",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(22.dp)) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val text = "Email: ${user.email}\nPassword: ${user.passwordHash}"
                                ClipboardHelper.copyToClipboard(context, text, "User Credentials")
                                Toast.makeText(context, "কপি হয়েছে: ${user.email}", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("কপি ইমেইল+পাস", fontSize = 9.5.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Detailed Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdminStatPill(title = "TODAY OTP", count = user.todayOtps.toString(), color = Color(0xFF34D399), modifier = Modifier.weight(1f))
                        AdminStatPill(title = "7 DAYS", count = user.last7DaysOtps.toString(), color = Color(0xFFA78BFA), modifier = Modifier.weight(1f))
                        AdminStatPill(title = "TOTAL OTP", count = user.totalOtps.toString(), color = Color(0xFFFBBF24), modifier = Modifier.weight(1f))
                        AdminStatPill(title = "BALANCE", count = "৳${String.format(Locale.US, "%.2f", user.balanceTk)}", color = Color(0xFF38BDF8), modifier = Modifier.weight(1.1f))
                    }

                    // Ban/Unban & Delete Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                AuthManager.toggleUserBan(user.email)
                                val msg = if (!user.isBanned) "🚫 ${user.name}-কে ব্যান করা হয়েছে" else "✓ ${user.name}-কে আনব্যান করা হয়েছে"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (user.isBanned) Color(0xFF059669) else Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text(
                                text = if (user.isBanned) "✓ UNBAN USER (আনব্যান)" else "🚫 BAN USER (ব্যান করুন)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DELETE USER (ডিলিট)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Prime Admin -> Dedicated Agent Management & Commission Card
 * Allows Prime Admin to:
 * 1. Enter or select any created user's email to automatically make them an Agent
 * 2. Configure Commission rule per Agent (e.g., 100 Today OTP = 10 Tk)
 * 3. See each Agent's created users, their combined Today OTP, Total OTP, Total Balance, and credit commission
 */
@Composable
fun AdminAgentManagerCard(
    context: Context,
    managedUsers: List<AuthUser>
) {
    var agentEmailInput by remember { mutableStateOf("") }
    var targetOtpInput by remember { mutableStateOf("100") }
    var commissionTkInput by remember { mutableStateOf("10") }
    var showUserPickerDropdown by remember { mutableStateOf(false) }
    var agentSearchQuery by remember { mutableStateOf("") }

    val agentsList = remember(managedUsers, agentSearchQuery) {
        val q = agentSearchQuery.trim().lowercase()
        managedUsers.filter { it.isAgent && (q.isEmpty() || it.email.lowercase().contains(q) || it.name.lowercase().contains(q)) }
    }

    val nonAgentUsers = remember(managedUsers, agentEmailInput) {
        val q = agentEmailInput.trim().lowercase()
        managedUsers.filter { !it.isAgent && (q.isEmpty() || it.email.lowercase().contains(q) || it.name.lowercase().contains(q)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Add / Promote User to Agent Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1B2D)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "🤝 AGENT SYSTEM & COMMISSION SETUP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "তৈরি করা ইউজারদের মধ্যে যার ইমেইল এখানে যুক্ত করবেন সে অটোমেটিক এজেন্ট হয়ে যাবে",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Email Input + Quick Picker Toggle
                OutlinedTextField(
                    value = agentEmailInput,
                    onValueChange = {
                        agentEmailInput = it
                        showUserPickerDropdown = true
                    },
                    label = { Text("ইউজারের ইমেইল লিখুন বা নিচের লিস্ট থেকে সিলেক্ট করুন") },
                    trailingIcon = {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { showUserPickerDropdown = !showUserPickerDropdown }
                        ) {
                            Text(
                                text = if (showUserPickerDropdown) "▲ লিস্ট বন্ধ" else "▼ ইউজার লিস্ট",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Collapsible User Picker so Admin can tap any existing user to fill their email
                AnimatedVisibility(visible = showUserPickerDropdown && nonAgentUsers.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("ট্যাপ করে ইমেইল সিলেক্ট করুন:", fontSize = 9.5.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            nonAgentUsers.take(30).forEach { u ->
                                Surface(
                                    color = Color(0xFF111D2E),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            agentEmailInput = u.email
                                            showUserPickerDropdown = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${u.name} (${u.email})", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("সিলেক্ট ✓", color = Color(0xFF34D399), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Commission Rule Inputs (e.g., 100 Today OTP = 10 Tk)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetOtpInput,
                        onValueChange = { targetOtpInput = it },
                        label = { Text("টার্গেট Today OTP (যেমন: 100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF38BDF8),
                            unfocusedTextColor = Color(0xFF38BDF8),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF243B55),
                            focusedContainerColor = Color(0xFF090E17),
                            unfocusedContainerColor = Color(0xFF090E17)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = commissionTkInput,
                        onValueChange = { commissionTkInput = it },
                        label = { Text("কমিশন টাকা ৳ (যেমন: 10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF34D399),
                            unfocusedTextColor = Color(0xFF34D399),
                            focusedBorderColor = Color(0xFF34D399),
                            unfocusedBorderColor = Color(0xFF243B55),
                            focusedContainerColor = Color(0xFF090E17),
                            unfocusedContainerColor = Color(0xFF090E17)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val targetOtp = targetOtpInput.trim().toIntOrNull()?.coerceAtLeast(1) ?: 100
                        val commTk = commissionTkInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 10.0
                        val (ok, msg) = AuthManager.setAgentRoleByEmail(
                            emailInput = agentEmailInput,
                            isAgent = true,
                            commissionOtpTarget = targetOtp,
                            commissionTkPerTarget = commTk
                        )
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (ok) {
                            agentEmailInput = ""
                            showUserPickerDropdown = false
                            VibrationHelper.vibrateSuccess(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🤝 এজেন্ট হিসেবে যুক্ত করুন ও কমিশন সেট করুন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Active Agents List & Their Created Users + Today OTP Commission
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🤝 ACTIVE AGENTS LIST (${managedUsers.count { it.isAgent }})",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    val totalAgentCreatedUsers = managedUsers.count { it.createdByAgent.isNotBlank() }
                    Text(
                        text = "এজেন্টদের মোট ইউজার: $totalAgentCreatedUsers জন",
                        fontSize = 10.5.sp,
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = agentSearchQuery,
                    onValueChange = { agentSearchQuery = it },
                    placeholder = { Text("এজেন্ট সার্চ করুন (নাম বা ইমেইল)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF1E2D40),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (agentsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো এজেন্ট যুক্ত করা হয়নি। উপরে ইউজারের ইমেইল দিয়ে এজেন্ট তৈরি করুন।",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        agentsList.forEach { agent ->
                            val agentUsers = managedUsers.filter { it.createdByAgent.equals(agent.email, ignoreCase = true) }
                            AdminAgentItemCard(
                                context = context,
                                agent = agent,
                                agentUsers = agentUsers
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAgentItemCard(
    context: Context,
    agent: AuthUser,
    agentUsers: List<AuthUser>
) {
    var showUsersDropdown by rememberSaveable(agent.email) { mutableStateOf(false) }
    var editTargetOtp by remember(agent.email, agent.agentCommissionOtpTarget) {
        mutableStateOf(agent.agentCommissionOtpTarget.toString())
    }
    var editCommTk by remember(agent.email, agent.agentCommissionTkPerTarget) {
        mutableStateOf(String.format(Locale.US, "%.2f", agent.agentCommissionTkPerTarget))
    }

    val combinedTodayOtp = agentUsers.sumOf { it.todayOtps }
    val combinedTotalOtp = agentUsers.sumOf { it.totalOtps }
    val combinedBalance = agentUsers.sumOf { it.balanceTk }

    val targetOtp = agent.agentCommissionOtpTarget.coerceAtLeast(1)
    val commTk = agent.agentCommissionTkPerTarget.coerceAtLeast(0.0)
    val calculatedTodayCommission = (combinedTodayOtp.toDouble() / targetOtp.toDouble()) * commTk

    Surface(
        color = Color(0xFF091320),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = agent.name,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "🤝 AGENT",
                                color = Color(0xFF34D399),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "${agent.email} • Agent Wallet: ৳${String.format(Locale.US, "%.2f", agent.balanceTk)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        val (_, msg) = AuthManager.setAgentRoleByEmail(agent.email, false)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("এজেন্ট বাতিল", fontSize = 9.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            }

            // Agent's Created Users Combined Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AdminStatPill(
                    title = "USERS",
                    count = agentUsers.size.toString(),
                    color = Color(0xFF60A5FA),
                    modifier = Modifier.weight(0.9f)
                )
                AdminStatPill(
                    title = "TODAY OTP",
                    count = combinedTodayOtp.toString(),
                    color = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                AdminStatPill(
                    title = "TOTAL OTP",
                    count = combinedTotalOtp.toString(),
                    color = Color(0xFFFBBF24),
                    modifier = Modifier.weight(1f)
                )
                AdminStatPill(
                    title = "USERS BAL",
                    count = "৳${String.format(Locale.US, "%.1f", combinedBalance)}",
                    color = Color(0xFFF472B6),
                    modifier = Modifier.weight(1.1f)
                )
            }

            // Commission Rule Editor & Credit Box
            Surface(
                color = Color(0xFF0F1E30),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💰 কমিশন রুল: $targetOtp Today OTP = ৳${String.format(Locale.US, "%.2f", commTk)}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "আজকের কমিশন: ৳${String.format(Locale.US, "%.2f", calculatedTodayCommission)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF34D399)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editTargetOtp,
                            onValueChange = { editTargetOtp = it },
                            label = { Text("OTP (যেমন 100)", fontSize = 9.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = editCommTk,
                            onValueChange = { editCommTk = it },
                            label = { Text("কমিশন ৳ (যেমন 10)", fontSize = 9.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                val tOtp = editTargetOtp.trim().toIntOrNull()?.coerceAtLeast(1) ?: 100
                                val cTk = editCommTk.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 10.0
                                AuthManager.updateAgentCommissionRule(agent.email, tOtp, cTk)
                                Toast.makeText(context, "✓ ${agent.name}-এর কমিশন রুল আপডেট হয়েছে ($tOtp OTP = ৳$cTk)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("সেভ রুল", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (calculatedTodayCommission <= 0.0) {
                                Toast.makeText(context, "আজকের Today OTP থেকে এখনো কমিশন জমা হয়নি", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            AuthManager.creditAgentCommissionToBalance(agent.email, calculatedTodayCommission) { ok ->
                                if (ok) {
                                    Toast.makeText(
                                        context,
                                        "✓ ৳${String.format(Locale.US, "%.2f", calculatedTodayCommission)} কমিশন ${agent.name}-এর ব্যালেন্সে যোগ করা হয়েছে!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    VibrationHelper.vibrateSuccess(context)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "আজকের কমিশন (৳${String.format(Locale.US, "%.2f", calculatedTodayCommission)}) এজেন্টের ব্যালেন্সে যোগ করুন",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Dropdown to inspect all users created by this Agent
            Surface(
                color = Color(0xFF111D2E),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showUsersDropdown = !showUsersDropdown }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👥 এই এজেন্টের তৈরি করা ইউজার তালিকা (${agentUsers.size} জন)",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (showUsersDropdown) "▲ লুকান" else "▼ ড্রপডাউন দেখুন",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(visible = showUsersDropdown) {
                if (agentUsers.isEmpty()) {
                    Text(
                        text = "এই এজেন্ট এখনো কোনো ইউজার তৈরি করেননি।",
                        color = Color(0xFF64748B),
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        agentUsers.forEach { u ->
                            Surface(
                                color = Color(0xFF0B1420),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (u.isBanned) Color(0xFFEF4444) else Color(0xFF1E2D40)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${u.name} (${u.email})",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Pass: ${u.passwordHash} • Today OTP: ${u.todayOtps} • Total: ${u.totalOtps} • ৳${String.format(Locale.US, "%.2f", u.balanceTk)}",
                                            color = Color(0xFF34D399),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            color = if (u.isBanned) Color(0xFF059669) else Color(0xFFD97706),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable { AuthManager.toggleUserBan(u.email) }
                                        ) {
                                            Text(
                                                text = if (u.isBanned) "Unban" else "Ban",
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFFDC2626),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable { AuthManager.deleteManagedUser(u.email) }
                                        ) {
                                            Text(
                                                text = "Del",
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
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

/**
 * Per-User All-Panel Custom OTP Rate Adjuster Card
 * Allows Admin to increase or decrease OTP rates per user for:
 * 1. Premium Numbers (Unix SMS)
 * 2. Premium Number V2
 * 3. Kop Engine 1 (Zenex API)
 * 4. Kop Engine 2 (Radar API)
 */
@Composable
fun UserPanelRatesAdjusterCard(
    context: Context,
    user: AuthUser,
    defaultUnixRate: Double,
    defaultPremiumV2Rate: Double,
    defaultKop1Rate: Double,
    defaultKop2Rate: Double
) {
    val effectiveUnix = user.customUnixRate ?: defaultUnixRate
    val effectiveV2 = user.customPremiumV2Rate ?: defaultPremiumV2Rate
    val effectiveKop1 = user.customKop1Rate ?: defaultKop1Rate
    val effectiveKop2 = user.customKop2Rate ?: defaultKop2Rate

    var unixRateInput by remember(user.email, user.customUnixRate, defaultUnixRate) {
        mutableStateOf(String.format(Locale.US, "%.2f", effectiveUnix))
    }
    var v2RateInput by remember(user.email, user.customPremiumV2Rate, defaultPremiumV2Rate) {
        mutableStateOf(String.format(Locale.US, "%.2f", effectiveV2))
    }
    var kop1RateInput by remember(user.email, user.customKop1Rate, defaultKop1Rate) {
        mutableStateOf(String.format(Locale.US, "%.2f", effectiveKop1))
    }
    var kop2RateInput by remember(user.email, user.customKop2Rate, defaultKop2Rate) {
        mutableStateOf(String.format(Locale.US, "%.2f", effectiveKop2))
    }

    val hasAnyCustomRate = user.customUnixRate != null ||
            user.customPremiumV2Rate != null ||
            user.customKop1Rate != null ||
            user.customKop2Rate != null

    Surface(
        color = Color(0xFF0C1626),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (hasAnyCustomRate) Color(0xFFF59E0B) else Color(0xFF1E3A5F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "⚡ ইউজারের সকল প্যানেল ওটিপি রেট (কমানো / বাড়ানো):",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFBBF24)
                    )
                }

                if (hasAnyCustomRate) {
                    Surface(
                        color = Color(0xFFDC2626).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.clickable {
                            AuthManager.updateUserPanelRates(user.email, null, null, null, null) {
                                Toast.makeText(context, "✓ ${user.name}-এর সকল প্যানেল রেট ডিফল্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(
                            text = "ডিফল্ট রিসেট",
                            color = Color(0xFFF87171),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 1. Premium Numbers (Unix SMS)
            SinglePanelRateAdjustRow(
                label = "1. Premium Numbers",
                isCustom = user.customUnixRate != null,
                rateText = unixRateInput,
                accentColor = Color(0xFF38BDF8),
                onRateTextChange = { unixRateInput = it },
                onDelta = { delta ->
                    val cur = unixRateInput.toDoubleOrNull() ?: effectiveUnix
                    val next = max(0.01, cur + delta)
                    unixRateInput = String.format(Locale.US, "%.2f", next)
                    AuthManager.updateUserPanelRates(
                        email = user.email,
                        unixRate = next,
                        premiumV2Rate = user.customPremiumV2Rate,
                        kop1Rate = user.customKop1Rate,
                        kop2Rate = user.customKop2Rate
                    )
                }
            )

            // 2. Premium Number V2
            SinglePanelRateAdjustRow(
                label = "2. Premium Number V2",
                isCustom = user.customPremiumV2Rate != null,
                rateText = v2RateInput,
                accentColor = Color(0xFFFBBF24),
                onRateTextChange = { v2RateInput = it },
                onDelta = { delta ->
                    val cur = v2RateInput.toDoubleOrNull() ?: effectiveV2
                    val next = max(0.01, cur + delta)
                    v2RateInput = String.format(Locale.US, "%.2f", next)
                    AuthManager.updateUserPanelRates(
                        email = user.email,
                        unixRate = user.customUnixRate,
                        premiumV2Rate = next,
                        kop1Rate = user.customKop1Rate,
                        kop2Rate = user.customKop2Rate
                    )
                }
            )

            // 3. Kop Engine 1 (Zenex)
            SinglePanelRateAdjustRow(
                label = "3. Kop Engine 1",
                isCustom = user.customKop1Rate != null,
                rateText = kop1RateInput,
                accentColor = Color(0xFF34D399),
                onRateTextChange = { kop1RateInput = it },
                onDelta = { delta ->
                    val cur = kop1RateInput.toDoubleOrNull() ?: effectiveKop1
                    val next = max(0.01, cur + delta)
                    kop1RateInput = String.format(Locale.US, "%.2f", next)
                    AuthManager.updateUserPanelRates(
                        email = user.email,
                        unixRate = user.customUnixRate,
                        premiumV2Rate = user.customPremiumV2Rate,
                        kop1Rate = next,
                        kop2Rate = user.customKop2Rate
                    )
                }
            )

            // 4. Kop Engine 2 (Radar)
            SinglePanelRateAdjustRow(
                label = "4. Kop Engine 2",
                isCustom = user.customKop2Rate != null,
                rateText = kop2RateInput,
                accentColor = Color(0xFFA78BFA),
                onRateTextChange = { kop2RateInput = it },
                onDelta = { delta ->
                    val cur = kop2RateInput.toDoubleOrNull() ?: effectiveKop2
                    val next = max(0.01, cur + delta)
                    kop2RateInput = String.format(Locale.US, "%.2f", next)
                    AuthManager.updateUserPanelRates(
                        email = user.email,
                        unixRate = user.customUnixRate,
                        premiumV2Rate = user.customPremiumV2Rate,
                        kop1Rate = user.customKop1Rate,
                        kop2Rate = next
                    )
                }
            )

            Button(
                onClick = {
                    val rUnix = unixRateInput.trim().toDoubleOrNull()?.coerceAtLeast(0.01) ?: effectiveUnix
                    val rV2 = v2RateInput.trim().toDoubleOrNull()?.coerceAtLeast(0.01) ?: effectiveV2
                    val rKop1 = kop1RateInput.trim().toDoubleOrNull()?.coerceAtLeast(0.01) ?: effectiveKop1
                    val rKop2 = kop2RateInput.trim().toDoubleOrNull()?.coerceAtLeast(0.01) ?: effectiveKop2
                    AuthManager.updateUserPanelRates(
                        email = user.email,
                        unixRate = rUnix,
                        premiumV2Rate = rV2,
                        kop1Rate = rKop1,
                        kop2Rate = rKop2
                    ) {
                        Toast.makeText(
                            context,
                            "✓ ${user.name}-এর সকল প্যানেল ওটিপি রেট সেভ হয়েছে!",
                            Toast.LENGTH_SHORT
                        ).show()
                        VibrationHelper.vibrateSuccess(context)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("✓ এই ইউজারের সকল প্যানেল রেট সেভ করুন", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SinglePanelRateAdjustRow(
    label: String,
    isCustom: Boolean,
    rateText: String,
    accentColor: Color,
    onRateTextChange: (String) -> Unit,
    onDelta: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080F1A), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isCustom) "⚡ কাস্টম রেট (৳$rateText)" else "ডিফল্ট রেট (৳$rateText)",
                color = if (isCustom) accentColor else Color(0xFF64748B),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.clickable { onDelta(-0.05) }
            ) {
                Text(
                    text = "-.05",
                    color = Color(0xFFF87171),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp)
                )
            }
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.clickable { onDelta(-0.01) }
            ) {
                Text(
                    text = "-.01",
                    color = Color(0xFFF87171),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp)
                )
            }

            OutlinedTextField(
                value = rateText,
                onValueChange = onRateTextChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = accentColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0xFF243B55),
                    focusedContainerColor = Color(0xFF050911),
                    unfocusedContainerColor = Color(0xFF050911)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .width(64.dp)
                    .height(42.dp)
            )

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.clickable { onDelta(+0.01) }
            ) {
                Text(
                    text = "+.01",
                    color = Color(0xFF34D399),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp)
                )
            }
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.clickable { onDelta(+0.05) }
            ) {
                Text(
                    text = "+.05",
                    color = Color(0xFF34D399),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp)
                )
            }
        }
    }
}
