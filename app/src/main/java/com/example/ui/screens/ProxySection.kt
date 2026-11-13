package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WorkShortcutRepository
import com.example.data.local.model.ProxyProfileEntity
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGreen
import com.example.util.ClipboardHelper
import com.example.util.NameGenerator
import com.example.util.ProxyFormatHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProxySection(
    state: OverlayUiState,
    savedProxies: List<ProxyProfileEntity>,
    repository: WorkShortcutRepository?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val proxy = state.proxyState

    var isFastestSearching by remember { mutableStateOf(false) }
    var profileName by remember(proxy.profileName) { mutableStateOf(proxy.profileName) }
    var serverHost by remember(proxy.host) { mutableStateOf(proxy.host) }
    var serverPort by remember(proxy.port) { mutableStateOf(proxy.port.toString()) }
    var protocol by remember(proxy.protocol) { mutableStateOf(proxy.protocol) }
    var countryCode by remember(proxy.countryCode) { mutableStateOf(proxy.countryCode) }
    var username by remember(proxy.username) { mutableStateOf(proxy.username) }
    var password by remember(proxy.password) { mutableStateOf(proxy.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var quickPasteInput by remember { mutableStateOf("") }

    var testStatusText by remember { mutableStateOf<String?>(null) }
    var isTestingActive by remember { mutableStateOf(false) }

    var appSearchQuery by remember { mutableStateOf("") }
    var isAppSelectionExpanded by remember { mutableStateOf(false) }

    val proxyPrefs = remember { context.getSharedPreferences("proxy_settings_prefs", Context.MODE_PRIVATE) }

    // Persistent Auto-load: Keep selected proxy profile active without needing to re-select
    LaunchedEffect(savedProxies) {
        if (savedProxies.isNotEmpty()) {
            val lastId = proxyPrefs.getLong("last_selected_proxy_id", -1L)
            val target = savedProxies.firstOrNull { it.id == lastId }
                ?: savedProxies.firstOrNull { it.isActive }
                ?: savedProxies.firstOrNull()

            if (target != null) {
                profileName = target.name
                serverHost = target.host
                serverPort = target.port.toString()
                protocol = target.protocol
                username = target.username
                password = target.password
                countryCode = target.countryCode

                OverlayStateManager.updateSuperProxyProfile(
                    profileName = target.name,
                    server = target.host,
                    port = target.port,
                    protocol = target.protocol,
                    countryCode = target.countryCode,
                    username = target.username,
                    password = target.password
                )
            }
        }
    }

    val installedApps = remember {
        try {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    appInfo.packageName != context.packageName && (
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                            pm.getLaunchIntentForPackage(appInfo.packageName) != null
                        )
                }
                .map { appInfo ->
                    SimpleInstalledApp(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = appInfo.packageName
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Standard Android VPN Permission Launcher (Super Proxy style)
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            OverlayStateManager.startProxyConnection(context)
        } else {
            Toast.makeText(context, "VPN Permission is required to route traffic through proxy!", Toast.LENGTH_LONG).show()
        }
    }

    val triggerStartOrStop = {
        val portInt = serverPort.toIntOrNull() ?: 1080
        OverlayStateManager.updateSuperProxyProfile(
            profileName = profileName,
            server = serverHost,
            port = portInt,
            protocol = protocol,
            countryCode = countryCode,
            username = username.trim(),
            password = password.trim()
        )

        if (proxy.isConnected) {
            OverlayStateManager.disconnectProxy(context)
        } else {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                OverlayStateManager.startProxyConnection(context)
            }
        }
    }

    // Check if current form inputs match an existing saved profile
    val matchingSavedProfile = remember(savedProxies, profileName) {
        savedProxies.firstOrNull { it.name.trim().equals(profileName.trim(), ignoreCase = true) }
    }
    val isProfileSavedInList = matchingSavedProfile != null

    val cardBgColor = Color(0xFF0D1726)
    val innerBoxBgColor = Color(0xFF08101C)
    val cardBorderColor = Color(0xFF1C2E4A)
    val cyanAccent = Color(0xFF00E5FF)
    val electricBlue = Color(0xFF2563EB)
    val neonGreen = Color(0xFF00E676)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFE2E8F0),
        focusedContainerColor = innerBoxBgColor,
        unfocusedContainerColor = innerBoxBgColor,
        focusedBorderColor = cyanAccent,
        unfocusedBorderColor = Color(0xFF233856),
        focusedLabelColor = cyanAccent,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = cyanAccent,
        focusedLeadingIconColor = cyanAccent,
        unfocusedLeadingIconColor = Color(0xFF64748B),
        focusedTrailingIconColor = cyanAccent,
        unfocusedTrailingIconColor = Color(0xFF94A3B8)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("proxy_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Quick Profile Selector Carousel (Auto-loaded & persistent)
        if (savedProxies.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A121E))
                        .border(1.dp, Color(0xFF192B44), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "SELECT PROFILE (AUTO-LOAD ACTIVE)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cyanAccent,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF15253B)
                        ) {
                            Text(
                                text = "${savedProxies.size} Saved",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(savedProxies) { index, prof ->
                            val isSelected = profileName.trim().equals(prof.name.trim(), ignoreCase = true)
                            Surface(
                                onClick = {
                                    profileName = prof.name
                                    serverHost = prof.host
                                    serverPort = prof.port.toString()
                                    protocol = prof.protocol
                                    username = prof.username
                                    password = prof.password
                                    countryCode = prof.countryCode
                                    testStatusText = null

                                    proxyPrefs.edit().putLong("last_selected_proxy_id", prof.id).apply()
                                    scope.launch { repository?.setActiveProxy(prof.id) }

                                    OverlayStateManager.updateSuperProxyProfile(
                                        profileName = prof.name,
                                        server = prof.host,
                                        port = prof.port,
                                        protocol = prof.protocol,
                                        countryCode = prof.countryCode,
                                        username = prof.username,
                                        password = prof.password
                                    )
                                    Toast.makeText(context, "Loaded: ${prof.name} ✓", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) cyanAccent.copy(alpha = 0.16f) else Color(0xFF101C2E),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isSelected) cyanAccent else Color(0xFF223652)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val cOpt = NameGenerator.getCountryOption(prof.countryCode)
                                    Text(cOpt.flag, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${index + 1}. ${prof.name.take(15)}",
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 11.5.sp,
                                        color = if (isSelected) cyanAccent else Color(0xFFE2E8F0),
                                        maxLines = 1
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = cyanAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Super Proxy Live Status & Power Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (proxy.isConnected) Color(0xFF0A201A) else cardBgColor
                ),
                border = BorderStroke(
                    1.4.dp,
                    if (proxy.isConnected) neonGreen else cardBorderColor
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            if (proxy.isConnected) {
                                                listOf(Color(0xFF00E676), Color(0xFF00B248))
                                            } else {
                                                listOf(Color(0xFF2563EB), Color(0xFF0284C7))
                                            }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (proxy.isConnected) Icons.Default.Security else Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (proxy.isTesting) "Detecting IP & Connecting..."
                                               else if (proxy.isConnected) "Super Proxy Connected"
                                               else "Super Proxy Disconnected",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (proxy.isTesting) Color(0xFF38BDF8)
                                                else if (proxy.isConnected) neonGreen
                                                else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (proxy.isTesting) proxy.statusText
                                           else if (proxy.isConnected) "${proxy.profileName} • IP Locked: ${proxy.ipAddress}"
                                           else "Ready to connect (⚡ 1-5s fast IP detect • High speed)",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (proxy.isConnected) neonGreen.copy(alpha = 0.16f) else Color(0xFF15253B),
                            border = BorderStroke(
                                1.dp,
                                if (proxy.isConnected) neonGreen.copy(alpha = 0.5f) else Color(0xFF263B59)
                            )
                        ) {
                            Text(
                                text = if (proxy.isConnected) "ONLINE" else protocol.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (proxy.isConnected) neonGreen else cyanAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Big Super Proxy START / STOP Button (1-Click Fast Connect)
                    Button(
                        onClick = triggerStartOrStop,
                        enabled = !proxy.isTesting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("super_proxy_start_stop_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (proxy.isTesting) Color(0xFF0288D1)
                                            else if (proxy.isConnected) AlertRed
                                            else Color(0xFF00C853)
                        )
                    ) {
                        if (proxy.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "CONNECTING (⚡ DETECTING IP)...",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                        } else if (proxy.isConnected) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(21.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STOP PROXY (CONNECTED)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "START PROXY",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp,
                                letterSpacing = 0.5.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Live Metrics when connected (Real External IP, City, Country, ISP, Time, Latency)
                    if (proxy.isConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val opt = NameGenerator.getCountryOption(proxy.countryCode)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = innerBoxBgColor,
                            border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(opt.flag, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            val locText = if (proxy.city.isNotBlank()) "${proxy.city}, ${proxy.countryName}" else proxy.countryName
                                            Text(
                                                text = locText,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = if (proxy.isp.isNotBlank()) "ISP: ${proxy.isp}" else "Protocol: ${proxy.protocol} • Real Egress",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (proxy.pingMs in 1..150) neonGreen.copy(alpha = 0.18f) else Color(0xFFFFB300).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "${proxy.pingMs}ms",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (proxy.pingMs in 1..150) neonGreen else Color(0xFFFFB300),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Divider(color = Color(0xFF1C2E4A))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("REAL PUBLIC IP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                        Text(
                                            text = proxy.ipAddress,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = cyanAccent
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("LIVE ACTIVE TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                        Text(
                                            OverlayStateManager.formatDuration(proxy.connectedDurationSeconds),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = neonGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Super Proxy Profile Configuration Card (Direct inputs with Username & Password right below Host/Port)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Card Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(electricBlue.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = cyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Proxy Profile Settings",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Configure host, port & credentials",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        // Add New Profile button to start a fresh profile
                        Surface(
                            onClick = {
                                val nextNum = savedProxies.size + 1
                                profileName = "Profile $nextNum"
                                serverHost = ""
                                serverPort = "1080"
                                protocol = "SOCKS5"
                                username = ""
                                password = ""
                                countryCode = "US"
                                testStatusText = null
                                Toast.makeText(context, "New blank profile ready. Enter details and Save!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = Color(0xFF122238),
                            border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("btn_new_profile")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New Profile",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cyanAccent,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1-Click / Auto Proxy Formatter & Quick Importer Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = innerBoxBgColor,
                        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "1-Click Proxy Auto-Formatter",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = cyanAccent
                                    )
                                }

                                Surface(
                                    onClick = {
                                        val clipText = ClipboardHelper.getFromClipboard(context)
                                        if (!clipText.isNullOrBlank()) {
                                            quickPasteInput = clipText.trim()
                                            val parsed = ProxyFormatHelper.parse(quickPasteInput)
                                            if (parsed != null) {
                                                serverHost = parsed.host
                                                serverPort = parsed.port.toString()
                                                protocol = parsed.protocol
                                                username = parsed.username
                                                password = parsed.password
                                                countryCode = parsed.countryCode
                                                profileName = parsed.suggestedName
                                                testStatusText = null
                                                OverlayStateManager.importAndSaveProxy(quickPasteInput, context)
                                            } else {
                                                Toast.makeText(context, "Could not auto-detect format. Enter manually.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(7.dp),
                                    color = cyanAccent.copy(alpha = 0.14f),
                                    border = BorderStroke(0.8.dp, cyanAccent.copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Paste Clipboard",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = cyanAccent,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Paste any format: host:port:user:pass • user:pass@host:port • socks5://...",
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = quickPasteInput,
                                    onValueChange = { quickPasteInput = it },
                                    placeholder = { Text("host:port:user:pass", fontSize = 12.sp, color = Color(0xFF64748B)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("proxy_quick_paste_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = textFieldColors
                                )

                                Button(
                                    onClick = {
                                        if (quickPasteInput.isNotBlank()) {
                                            val parsed = ProxyFormatHelper.parse(quickPasteInput)
                                            if (parsed != null) {
                                                serverHost = parsed.host
                                                serverPort = parsed.port.toString()
                                                protocol = parsed.protocol
                                                username = parsed.username
                                                password = parsed.password
                                                countryCode = parsed.countryCode
                                                profileName = parsed.suggestedName
                                                testStatusText = null
                                                OverlayStateManager.importAndSaveProxy(quickPasteInput, context)
                                            } else {
                                                Toast.makeText(context, "Unrecognized format. Please check input.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Please enter a proxy string to import", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(52.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = electricBlue)
                                ) {
                                    Text("Import", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Protocol Selection Tabs (SOCKS5 / HTTP)
                    Text(
                        text = "PROTOCOL (SOCKS5 / HTTP)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("SOCKS5", "HTTP").forEach { proto ->
                            val isSelected = protocol.equals(proto, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { protocol = proto },
                                label = {
                                    Text(
                                        text = proto,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = innerBoxBgColor,
                                    labelColor = Color(0xFF94A3B8),
                                    selectedContainerColor = electricBlue,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color(0xFF233856),
                                    selectedBorderColor = cyanAccent,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.2.dp
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("chip_proto_$proto")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Profile Name
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it
                            testStatusText = null
                        },
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g. Primary Proxy, US SOCKS5") },
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(11.dp),
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_proxy_profile_name")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Server (Host) & 3. Port
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = serverHost,
                            onValueChange = {
                                serverHost = it
                                testStatusText = null
                            },
                            label = { Text("Server Host / IP") },
                            placeholder = { Text("rp.realproxy.net") },
                            leadingIcon = {
                                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(11.dp),
                            colors = textFieldColors,
                            modifier = Modifier
                                .weight(1.85f)
                                .testTag("input_proxy_server")
                        )

                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = {
                                serverPort = it.filter { ch -> ch.isDigit() }
                                testStatusText = null
                            },
                            label = { Text("Port") },
                            placeholder = { Text("1080") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(11.dp),
                            colors = textFieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_proxy_port")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Overlay 3s Hold Edit Field Selector (Only Username OR Only Password)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = innerBoxBgColor,
                        border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OVERLAY 3s HOLD EDIT BOX",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cyanAccent,
                                    letterSpacing = 0.4.sp
                                )
                                Text(
                                    text = if (state.proxyOverlayEditField == "PASSWORD") "🔒 শুধু Password" else "👤 শুধু Username",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = neonGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "USERNAME" to "👤 শুধু Username",
                                    "PASSWORD" to "🔒 শুধু Password"
                                ).forEach { (fieldKey, labelText) ->
                                    val isSelectedField = state.proxyOverlayEditField.equals(fieldKey, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelectedField,
                                        onClick = {
                                            OverlayStateManager.setProxyOverlayEditField(fieldKey)
                                            Toast.makeText(
                                                context,
                                                "Overlay Hold: $labelText সেট করা হয়েছে ✓",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        label = {
                                            Text(
                                                text = labelText,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelectedField) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        },
                                        leadingIcon = if (isSelectedField) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(9.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFF0D1726),
                                            labelColor = Color(0xFF94A3B8),
                                            selectedContainerColor = electricBlue,
                                            selectedLabelColor = Color.White,
                                            selectedLeadingIconColor = Color.White
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelectedField,
                                            borderColor = Color(0xFF233856),
                                            selectedBorderColor = cyanAccent,
                                            borderWidth = 1.dp,
                                            selectedBorderWidth = 1.2.dp
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("chip_overlay_edit_field_$fieldKey")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Direct Username & Password inputs (No toggle switch needed)
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            testStatusText = null
                            val crMatch = Regex("""(?:cr\.([a-z]{2})|country[_-]([a-z]{2}))""", RegexOption.IGNORE_CASE).find(it)
                            if (crMatch != null) {
                                val cc = crMatch.groupValues.firstOrNull { g -> g.length == 2 && !g.equals("cr", ignoreCase = true) }?.uppercase()
                                if (!cc.isNullOrBlank()) {
                                    countryCode = cc
                                }
                            }
                        },
                        label = { Text("Username") },
                        placeholder = { Text("Enter proxy username (optional)") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(11.dp),
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_proxy_user")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            testStatusText = null
                        },
                        label = { Text("Password") },
                        placeholder = { Text("Enter proxy password (optional)") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(11.dp),
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_proxy_pass")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dynamic Action Buttons (Save/Update Profile, Start/Stop Connection, Test Proxy)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Save / Update Profile Button
                        Button(
                            onClick = {
                                val portInt = serverPort.toIntOrNull() ?: 1080
                                val cleanName = profileName.ifBlank { "Profile ${savedProxies.size + 1}" }
                                val cleanUser = username.trim()
                                val cleanPass = password.trim()
                                scope.launch {
                                    val existing = savedProxies.firstOrNull { it.name.trim().equals(cleanName.trim(), ignoreCase = true) }
                                    val entity = ProxyProfileEntity(
                                        id = existing?.id ?: 0L,
                                        name = cleanName,
                                        protocol = protocol,
                                        host = serverHost.ifBlank { "127.0.0.1" },
                                        port = portInt,
                                        username = cleanUser,
                                        password = cleanPass,
                                        countryCode = countryCode.ifBlank { "US" },
                                        isActive = true
                                    )
                                    if (existing != null) {
                                        repository?.updateProxy(entity)
                                        proxyPrefs.edit().putLong("last_selected_proxy_id", existing.id).apply()
                                    } else {
                                        val newId = repository?.insertProxy(entity)
                                        if (newId != null && newId > 0) {
                                            repository.setActiveProxy(newId)
                                            proxyPrefs.edit().putLong("last_selected_proxy_id", newId).apply()
                                        }
                                    }
                                    OverlayStateManager.updateSuperProxyProfile(
                                        profileName = entity.name,
                                        server = entity.host,
                                        port = entity.port,
                                        protocol = entity.protocol,
                                        countryCode = entity.countryCode,
                                        username = entity.username,
                                        password = entity.password
                                    )
                                    Toast.makeText(context, "Saved to profile list! ✓", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(46.dp)
                                .testTag("btn_save_proxy_profile"),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = electricBlue)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isProfileSavedInList) "Update Profile" else "Save Profile",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 2. Start / Stop Connection if profile is saved
                        if (isProfileSavedInList) {
                            Button(
                                onClick = triggerStartOrStop,
                                enabled = !proxy.isTesting,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(46.dp)
                                    .testTag("btn_start_proxy_from_form"),
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (proxy.isTesting) Color(0xFF0288D1)
                                                    else if (proxy.isConnected) AlertRed
                                                    else Color(0xFF00C853)
                                )
                            ) {
                                if (proxy.isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Wait...", fontSize = 12.sp, color = Color.White, maxLines = 1)
                                } else {
                                    Icon(
                                        imageVector = if (proxy.isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (proxy.isConnected) "Stop" else "Start",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 3. Test Proxy Button
                        OutlinedButton(
                            onClick = {
                                val portInt = serverPort.toIntOrNull() ?: 1080
                                OverlayStateManager.updateSuperProxyProfile(
                                    profileName = profileName,
                                    server = serverHost,
                                    port = portInt,
                                    protocol = protocol,
                                    countryCode = countryCode,
                                    username = username.trim(),
                                    password = password.trim()
                                )
                                isTestingActive = true
                                testStatusText = "Testing connection..."
                                OverlayStateManager.testProxyOnly(context) { _, msg ->
                                    isTestingActive = false
                                    testStatusText = msg
                                    countryCode = OverlayStateManager.uiState.value.proxyState.countryCode
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_test_proxy"),
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.2.dp, cyanAccent.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = innerBoxBgColor,
                                contentColor = cyanAccent
                            )
                        ) {
                            if (isTestingActive) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), color = cyanAccent, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Test Proxy",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cyanAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Test Feedback Banner if tested
                    if (!testStatusText.isNullOrBlank()) {
                        val isSuccessTest = testStatusText!!.contains("Succeeded", ignoreCase = true) ||
                                testStatusText!!.contains("Connected", ignoreCase = true) ||
                                testStatusText!!.contains("✓")
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSuccessTest) neonGreen.copy(alpha = 0.12f) else innerBoxBgColor,
                            border = BorderStroke(
                                1.dp,
                                if (isSuccessTest) neonGreen.copy(alpha = 0.45f) else Color(0xFFEF4444).copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = testStatusText!!,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSuccessTest) neonGreen else Color(0xFFF87171)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Saved Proxy Profiles (Serial List View like Super Proxy App)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(electricBlue.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(17.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Saved Proxies (${savedProxies.size})",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        if (savedProxies.isNotEmpty()) {
                            Button(
                                onClick = {
                                    isFastestSearching = true
                                    scope.launch(Dispatchers.IO) {
                                        var bestProxy: ProxyProfileEntity? = null
                                        var lowestPing = Long.MAX_VALUE

                                        for (item in savedProxies) {
                                            val ping = com.example.util.ProxyTester.measureSocketPing(item.host, item.port, 2000)
                                            if (ping in 1 until lowestPing) {
                                                lowestPing = ping
                                                bestProxy = item
                                            }
                                        }

                                        withContext(Dispatchers.Main) {
                                            isFastestSearching = false
                                            if (bestProxy != null) {
                                                profileName = bestProxy.name
                                                serverHost = bestProxy.host
                                                serverPort = bestProxy.port.toString()
                                                protocol = bestProxy.protocol
                                                username = bestProxy.username
                                                password = bestProxy.password
                                                countryCode = bestProxy.countryCode

                                                OverlayStateManager.updateSuperProxyProfile(
                                                    profileName = bestProxy.name,
                                                    server = bestProxy.host,
                                                    port = bestProxy.port,
                                                    protocol = bestProxy.protocol,
                                                    countryCode = bestProxy.countryCode,
                                                    username = bestProxy.username,
                                                    password = bestProxy.password
                                                )
                                                repository?.setActiveProxy(bestProxy.id)
                                                com.example.util.VibrationHelper.vibrateSuccess(context)
                                                Toast.makeText(context, "⚡ ফাস্টেস্ট প্রক্সি সিলেক্টেড: ${bestProxy.name} (${lowestPing}ms)", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "প্রক্সি পিং পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(9.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (isFastestSearching) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("টেস্ট...", fontSize = 10.5.sp, color = Color.Black, fontWeight = FontWeight.ExtraBold)
                                } else {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("⚡ ফাস্টেস্ট সিলেক্ট", fontSize = 10.5.sp, color = Color.Black, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        } else {
                            Text(
                                text = "Tap to load",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (savedProxies.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = innerBoxBgColor,
                            border = BorderStroke(1.dp, Color(0xFF1A2B44)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No saved profiles yet. Enter details above and click 'Save Profile' to build your list!",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedProxies.forEachIndexed { index, profileItem ->
                                val isSelected = profileName.trim().equals(profileItem.name.trim(), ignoreCase = true)
                                Surface(
                                    onClick = {
                                        profileName = profileItem.name
                                        serverHost = profileItem.host
                                        serverPort = profileItem.port.toString()
                                        protocol = profileItem.protocol
                                        username = profileItem.username
                                        password = profileItem.password
                                        countryCode = profileItem.countryCode
                                        testStatusText = null

                                        OverlayStateManager.updateSuperProxyProfile(
                                            profileName = profileItem.name,
                                            server = profileItem.host,
                                            port = profileItem.port,
                                            protocol = profileItem.protocol,
                                            countryCode = profileItem.countryCode,
                                            username = profileItem.username,
                                            password = profileItem.password
                                        )
                                        scope.launch {
                                            repository?.setActiveProxy(profileItem.id)
                                            proxyPrefs.edit().putLong("last_selected_proxy_id", profileItem.id).apply()
                                        }
                                        Toast.makeText(context, "Loaded: ${profileItem.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF10233E) else innerBoxBgColor,
                                    border = BorderStroke(
                                        1.2.dp,
                                        if (isSelected) cyanAccent else Color(0xFF1E324E)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("proxy_profile_item_${profileItem.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = if (isSelected) electricBlue else Color(0xFF1A2B44),
                                                shape = CircleShape,
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        color = Color.White,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = profileItem.name,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.5.sp,
                                                        color = if (isSelected) cyanAccent else Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = if (profileItem.protocol.equals("HTTP", ignoreCase = true)) Color(0xFF00B0FF).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = profileItem.protocol,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (profileItem.protocol.equals("HTTP", ignoreCase = true)) Color(0xFF38BDF8) else Color(0xFFFFB300),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${profileItem.host}:${profileItem.port}",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF94A3B8),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = cyanAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        repository?.deleteProxy(profileItem)
                                                        Toast.makeText(context, "Deleted ${profileItem.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                                                    modifier = Modifier.size(17.dp)
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

        // 4. App Routing Section (Super Proxy Feature: Select Allowed Apps)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Routing Apps (Super Proxy Mode)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val allowedCount = proxy.allowedApps.size
                            Text(
                                text = if (allowedCount == 0) "🛡️ All apps routed through proxy (Default)"
                                       else "⚡ $allowedCount apps exclusively routed through proxy",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (allowedCount > 0) neonGreen else Color(0xFF94A3B8)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (proxy.allowedApps.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        OverlayStateManager.setProxyAllowedApps(emptyList(), context)
                                        Toast.makeText(context, "Reset to route All Apps by default", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.45f)),
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("All Apps", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cyanAccent, maxLines = 1)
                                }
                            }

                            Button(
                                onClick = { isAppSelectionExpanded = !isAppSelectionExpanded },
                                shape = RoundedCornerShape(9.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (isAppSelectionExpanded) "Done" else "Filter Apps",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF04101E),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (isAppSelectionExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("Search installed apps (Via, Chrome, FB, IG, WhatsApp)...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(11.dp),
                            colors = textFieldColors
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val filtered = installedApps.filter {
                            it.name.contains(appSearchQuery, ignoreCase = true) ||
                                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                        }.sortedWith(
                            compareByDescending<SimpleInstalledApp> { proxy.allowedApps.contains(it.packageName) }
                                .thenBy { it.name.lowercase() }
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filtered.take(50).forEach { app ->
                                val isChecked = proxy.allowedApps.contains(app.packageName)
                                Surface(
                                    onClick = {
                                        OverlayStateManager.toggleProxyAllowedApp(app.packageName, !isChecked, context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isChecked) Color(0xFF10233E) else innerBoxBgColor,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChecked) cyanAccent.copy(alpha = 0.6f) else Color(0xFF1C2E4A)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = if (isChecked) cyanAccent else Color.White
                                            )
                                            Text(
                                                text = app.packageName,
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                OverlayStateManager.toggleProxyAllowedApp(app.packageName, checked, context)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = cyanAccent,
                                                checkmarkColor = Color(0xFF04101E),
                                                uncheckedColor = Color(0xFF64748B)
                                            )
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
