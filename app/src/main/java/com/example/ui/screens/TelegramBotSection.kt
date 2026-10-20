package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.service.FloatingOverlayService
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.service.TelegramChatMessage
import com.example.service.TelegramBotManager
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelegramBotSection(
    state: OverlayUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tgState by TelegramBotManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedViewTab by remember { mutableIntStateOf(0) } // 0: Telegram Chat, 1: Bot Settings & Logs
    var messageInputText by remember { mutableStateOf("") }
    var showCommandsMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        TelegramBotManager.init(context)
    }

    LaunchedEffect(tgState.chatMessages.size) {
        if (tgState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(tgState.chatMessages.size - 1)
        }
    }

    val activeBot = tgState.currentSelectedBot
    val activeBotsCount = tgState.activeBots.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B141F))
    ) {
        // 1. Telegram App Top Bar
        Surface(
            color = Color(0xFF17212B),
            border = BorderStroke(0.5.dp, Color(0xFF242F3D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Telegram Bot Avatar with Neon Ring
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2AABEE), Color(0xFF229ED9))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeBot?.name ?: "Telegram Bot",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF0088CC).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "bot",
                                    color = Color(0xFF64B5F6),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (activeBotsCount > 0) Color(0xFF00E676) else Color(0xFFFF5252))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (activeBotsCount > 0) "online • $activeBotsCount bot(s) running" else "idle • admin selection required",
                                color = if (activeBotsCount > 0) Color(0xFF00E676) else Color(0xFFFF5252),
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // Overlay Trigger & Quick Mode Toggle
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Overlay Launch Button
                    Surface(
                        color = if (state.isOverlayActive) Color(0xFFDC2626) else Color(0xFF0088CC),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Display over other apps পারমিশন দিন", Toast.LENGTH_LONG).show()
                                    return@clickable
                                }
                                if (state.isOverlayActive) {
                                    FloatingOverlayService.stopService(context)
                                    Toast.makeText(context, "ওভারলে বন্ধ হয়েছে", Toast.LENGTH_SHORT).show()
                                } else {
                                    FloatingOverlayService.startService(context)
                                    Toast.makeText(context, "✓ ফ্লোটিং বাবল চালু হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                                VibrationHelper.vibrateSuccess(context)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.isOverlayActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (state.isOverlayActive) "STOP" else "BUBBLE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Navigation Tabs (Telegram Chat vs Bot Overview / Settings)
        TabRow(
            selectedTabIndex = selectedViewTab,
            containerColor = Color(0xFF131D28),
            contentColor = Color(0xFF0088CC),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedViewTab]),
                    color = Color(0xFF0088CC),
                    height = 2.dp
                )
            }
        ) {
            Tab(
                selected = selectedViewTab == 0,
                onClick = { selectedViewTab = 0 },
                text = {
                    Text(
                        text = "💬 TELEGRAM CHAT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedViewTab == 0) Color(0xFF0088CC) else Color(0xFF78909C)
                    )
                }
            )
            Tab(
                selected = selectedViewTab == 1,
                onClick = { selectedViewTab = 1 },
                text = {
                    Text(
                        text = "⚙️ BOTS OVERVIEW (${tgState.bots.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedViewTab == 1) Color(0xFF0088CC) else Color(0xFF78909C)
                    )
                }
            )
        }

        if (selectedViewTab == 0) {
            // TAB 0: FULL TELEGRAM CHAT EXPERIENCE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0E1621))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Date Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = Color(0xFF18222D).copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "TODAY",
                                    color = Color(0xFF708499),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(tgState.chatMessages, key = { it.id }) { msg ->
                        TelegramMessageBubble(
                            msg = msg,
                            context = context
                        )
                    }
                }
            }

            // Quick Commands Bar (Telegram Keyboard Markup)
            Surface(
                color = Color(0xFF131D28),
                border = BorderStroke(0.5.dp, Color(0xFF242F3D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    // Command Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "/otp" to "⚡ OTP",
                            "/balance" to "💰 Balance",
                            "/numbers" to "📱 Numbers",
                            "/start" to "🚀 Start",
                            "/stats" to "📊 Stats",
                            "/clear" to "🧹 Clear"
                        ).forEach { (cmd, label) ->
                            Surface(
                                color = Color(0xFF1E2C3A),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(0.8.dp, Color(0xFF2B5278)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        TelegramBotManager.sendUserMessage(cmd, context)
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = Color(0xFF81D4FA),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Telegram Chat Input Bar
            Surface(
                color = Color(0xFF17212B),
                border = BorderStroke(0.5.dp, Color(0xFF242F3D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Command Menu Toggle
                    IconButton(
                        onClick = { showCommandsMenu = !showCommandsMenu },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "/",
                            color = if (showCommandsMenu) Color(0xFF0088CC) else Color(0xFF64748B),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = messageInputText,
                        onValueChange = { messageInputText = it },
                        placeholder = {
                            Text(
                                text = "Message or command (e.g. /otp)...",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF0088CC),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color(0xFF0E1621),
                            unfocusedContainerColor = Color(0xFF0E1621)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Telegram Send Button (Circular Blue Button)
                    Surface(
                        color = Color(0xFF0088CC),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                if (messageInputText.isNotBlank()) {
                                    val text = messageInputText
                                    messageInputText = ""
                                    TelegramBotManager.sendUserMessage(text, context)
                                    VibrationHelper.vibrateSuccess(context)
                                }
                            }
                            .testTag("tg_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // TAB 1: BOTS OVERVIEW & ADMIN CONFIG
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Admin Selection Notice Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131F2E)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF0288D1))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF29B6F6), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ADMIN BOT SELECTION",
                                    color = Color(0xFF29B6F6),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "এডমিন প্যানেল থেকে আপনি যেই বট(গুলো) সিলেক্ট ও চালু (Enabled) করবেন, শুধুমাত্র সেই বটেই ওটিপি ফরওয়ার্ড হবে এবং কমান্ড রান করবে।",
                                color = Color(0xFFB0BEC5),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "CONFIGURED TELEGRAM BOTS (${tgState.bots.size})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(tgState.bots, key = { it.id }) { bot ->
                    val isRunning = bot.isEnabled && bot.botToken.isNotBlank() && bot.chatId.isNotBlank()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isRunning) Color(0xFF101B27) else Color(0xFF161B22)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isRunning) Color(0xFF00E676).copy(alpha = 0.6f) else Color(0xFF242F3D))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bot.name,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (bot.chatId.isNotBlank()) "Chat ID: ${bot.chatId}" else "Chat ID: Not set",
                                        color = Color(0xFF81D4FA),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Surface(
                                    color = if (isRunning) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, if (isRunning) Color(0xFF00E676) else Color(0xFF64748B))
                                ) {
                                    Text(
                                        text = if (isRunning) "● ACTIVE & RUNNING" else "● PAUSED",
                                        color = if (isRunning) Color(0xFF00E676) else Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E2D3D), thickness = 0.8.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Auto-Forward OTPs: ${if (bot.autoForwardOtp) "ON ✓" else "OFF"}",
                                    color = if (bot.autoForwardOtp) Color(0xFF00E676) else Color(0xFF90A4AE),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Button(
                                    onClick = {
                                        TelegramBotManager.testBot(bot) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3858)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("TEST BOT", color = Color(0xFF81D4FA), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "RECENT TELEGRAM LOGS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Surface(
                        color = Color(0xFF050B14),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF1A2736)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (tgState.botLogs.isEmpty()) {
                                Text("No logs recorded yet.", color = Color(0xFF64748B), fontSize = 11.sp)
                            } else {
                                tgState.botLogs.take(12).forEach { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("✓")) Color(0xFF00E676) else if (log.contains("✕")) Color(0xFFFF5252) else Color(0xFF90A4AE),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
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

@Composable
fun TelegramMessageBubble(
    msg: TelegramChatMessage,
    context: Context
) {
    val isUser = msg.sender == "user"
    val isSystem = msg.sender == "system"

    val timeStr = remember(msg.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    }

    if (isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color(0xFF18222D).copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color(0xFF2B5278))
            ) {
                Text(
                    text = msg.text,
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2AABEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = if (isUser) Color(0xFF2B5278) else Color(0xFF182533),
            border = BorderStroke(
                0.8.dp,
                if (msg.isOtpCard) Color(0xFF00E676).copy(alpha = 0.8f)
                else if (isUser) Color(0xFF4A76A8)
                else Color(0xFF2B3A4C)
            ),
            modifier = Modifier.fillMaxWidth(if (msg.isOtpCard) 0.92f else 0.82f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (!isUser) {
                    Text(
                        text = msg.senderName,
                        color = Color(0xFF2AABEE),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (msg.isOtpCard) {
                    // OTP Highlighted Card layout
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NEW OTP RECEIVED",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "📱 Phone: ${msg.phoneNumber ?: "N/A"}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🔑 OTP: ",
                            color = Color(0xFF81D4FA),
                            fontSize = 12.sp
                        )
                        Text(
                            text = msg.otpCode ?: "------",
                            color = Color(0xFFFFD600),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (!msg.service.isNullOrBlank()) {
                        Text(
                            text = "🏷 Service: ${msg.service}",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Instant Copy OTP Button
                    Button(
                        onClick = {
                            msg.otpCode?.let { code ->
                                ClipboardHelper.copyToClipboard(context, code, "OTP Code")
                                VibrationHelper.vibrateSuccess(context)
                                Toast.makeText(context, "✓ ওটিপি কোড $code কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COPY OTP CODE", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = msg.text,
                        color = Color.White,
                        fontSize = 12.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        color = Color(0xFF708499),
                        fontSize = 9.5.sp
                    )
                    if (isUser) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "✓✓",
                            color = Color(0xFF2AABEE),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
