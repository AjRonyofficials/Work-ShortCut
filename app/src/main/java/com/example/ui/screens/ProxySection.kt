package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlinx.coroutines.launch

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

    var profileName by remember(proxy.profileName) { mutableStateOf(proxy.profileName) }
    var serverHost by remember(proxy.host) { mutableStateOf(proxy.host) }
    var serverPort by remember(proxy.port) { mutableStateOf(proxy.port.toString()) }
    var protocol by remember(proxy.protocol) { mutableStateOf(proxy.protocol) }
    var countryCode by remember(proxy.countryCode) { mutableStateOf(proxy.countryCode) }
    var username by remember(proxy.username) { mutableStateOf(proxy.username) }
    var password by remember(proxy.password) { mutableStateOf(proxy.password) }
    var requiresAuth by remember(proxy.username, proxy.password) {
        mutableStateOf(proxy.username.isNotBlank() || proxy.password.isNotBlank())
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var quickPasteInput by remember { mutableStateOf("") }

    var testStatusText by remember { mutableStateOf<String?>(null) }
    var isTestingActive by remember { mutableStateOf(false) }

    var appSearchQuery by remember { mutableStateOf("") }
    var isAppSelectionExpanded by remember { mutableStateOf(false) }

    val installedApps = remember {
        try {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName.contains("chrome") }
                .map { appInfo ->
                    SimpleInstalledApp(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = appInfo.packageName
                    )
                }
                .sortedBy { it.name }
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
        val effectiveUser = if (requiresAuth) username else ""
        val effectivePass = if (requiresAuth) password else ""
        OverlayStateManager.updateSuperProxyProfile(
            profileName = profileName,
            server = serverHost,
            port = portInt,
            protocol = protocol,
            countryCode = countryCode,
            username = effectiveUser,
            password = effectivePass
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("proxy_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Super Proxy Live Status & Power Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (proxy.isConnected) BrandGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (proxy.isConnected) BrandGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (proxy.isConnected) BrandGreen else Color(0xFF1E88E5)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (proxy.isConnected) Icons.Default.Security else Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (proxy.isConnected) "Super Proxy Connected ✓" else "Super Proxy Disconnected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (proxy.isConnected) BrandGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (proxy.isConnected) "${proxy.profileName} • Real-time VPN active" else "Ready to connect (1-click fast routing)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big Super Proxy START / STOP Button (1-Click Fast Connect)
                    Button(
                        onClick = triggerStartOrStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("super_proxy_start_stop_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (proxy.isConnected) AlertRed else Color(0xFF00C853)
                        )
                    ) {
                        if (proxy.isConnected) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STOP PROXY (CONNECTED)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "START PROXY",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Live Metrics when connected (Real External IP, City, Country, ISP, Time, Latency)
                    if (proxy.isConnected) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val opt = NameGenerator.getCountryOption(proxy.countryCode)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreen.copy(alpha = 0.35f)),
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
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (proxy.isp.isNotBlank()) "ISP: ${proxy.isp}" else "Protocol: ${proxy.protocol} • Real Egress",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (proxy.pingMs in 1..150) BrandGreen.copy(alpha = 0.18f) else Color(0xFFFFB300).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "${proxy.pingMs}ms",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (proxy.pingMs in 1..150) BrandGreen else Color(0xFFFFB300),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("REAL PUBLIC IP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(proxy.ipAddress, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = BrandBlue)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("LIVE ACTIVE TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            OverlayStateManager.formatDuration(proxy.connectedDurationSeconds),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = BrandGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Super Proxy Profile Configuration Card (Inputs load automatically when serial profile is clicked)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Proxy Profile Settings",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Add New Profile button to start a fresh profile
                        OutlinedButton(
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
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_new_profile")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Profile", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1-Click / Auto Proxy Formatter & Quick Importer Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "1-Click Proxy Auto-Formatter",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BrandBlue
                                    )
                                }

                                TextButton(
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
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Paste Clipboard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Paste any format: host:port:user:pass • user:pass@host:port • socks5://...",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    placeholder = { Text("host:port:user:pass", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("proxy_quick_paste_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
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
                                                requiresAuth = parsed.username.isNotBlank() || parsed.password.isNotBlank()
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
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                                ) {
                                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Protocol Selection Tabs (SOCKS5 / HTTP)
                    Text(
                        text = "Protocol (SOCKS5 / HTTP)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
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
                                    selectedContainerColor = BrandBlue,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chip_proto_$proto")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Profile Name
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it
                            testStatusText = null
                        },
                        label = { Text("Profile Name") },
                        placeholder = { Text("e.g. Singapore SOCKS5, US Proxy 1") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_proxy_profile_name")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Server (Host) & 3. Port
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = serverHost,
                            onValueChange = {
                                serverHost = it
                                testStatusText = null
                            },
                            label = { Text("Server Host / IP") },
                            placeholder = { Text("104.244.72.115") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(2f)
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_proxy_port")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Authentication Section (Dedicated Super Proxy / RootProxy style)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (requiresAuth) Color(0xFF26A69A).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF26A69A).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = Color(0xFF26A69A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Authentication",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Requires authentication",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Username and password protection",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = requiresAuth,
                                    onCheckedChange = { isChecked ->
                                        requiresAuth = isChecked
                                        if (!isChecked) {
                                            username = ""
                                            password = ""
                                        }
                                    }
                                )
                            }

                            AnimatedVisibility(visible = requiresAuth) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
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
                                        placeholder = { Text("e.g. defa891ce67dcbeaddcb__cr.it") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_proxy_user")
                                    )

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = {
                                            password = it
                                            testStatusText = null
                                        },
                                        label = { Text("Password") },
                                        placeholder = { Text("Enter proxy password") },
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
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_proxy_pass")
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Buttons (Save Profile, Start Connection, Test Proxy)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Save Profile Button (Saves to Room DB list)
                        Button(
                            onClick = {
                                val portInt = serverPort.toIntOrNull() ?: 1080
                                val cleanName = profileName.ifBlank { "Profile ${savedProxies.size + 1}" }
                                val effectiveUser = if (requiresAuth) username else ""
                                val effectivePass = if (requiresAuth) password else ""
                                scope.launch {
                                    val existing = savedProxies.firstOrNull { it.name.trim().equals(cleanName.trim(), ignoreCase = true) }
                                    val entity = ProxyProfileEntity(
                                        id = existing?.id ?: 0L,
                                        name = cleanName,
                                        protocol = protocol,
                                        host = serverHost.ifBlank { "127.0.0.1" },
                                        port = portInt,
                                        username = effectiveUser,
                                        password = effectivePass,
                                        countryCode = countryCode.ifBlank { "US" },
                                        isActive = true
                                    )
                                    if (existing != null) {
                                        repository?.updateProxy(entity)
                                    } else {
                                        val newId = repository?.insertProxy(entity)
                                        if (newId != null && newId > 0) {
                                            repository.setActiveProxy(newId)
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
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_save_proxy_profile"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isProfileSavedInList) "Update Profile" else "Save Profile",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // 2. Start / Stop Connection if profile is saved or user wants immediate connect
                        if (isProfileSavedInList) {
                            Button(
                                onClick = triggerStartOrStop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("btn_start_proxy_from_form"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (proxy.isConnected) AlertRed else Color(0xFF00C853)
                                )
                            ) {
                                Icon(
                                    imageVector = if (proxy.isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (proxy.isConnected) "Stop" else "Start",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // 3. Test Proxy Button
                        OutlinedButton(
                            onClick = {
                                val portInt = serverPort.toIntOrNull() ?: 1080
                                val effectiveUser = if (requiresAuth) username else ""
                                val effectivePass = if (requiresAuth) password else ""
                                OverlayStateManager.updateSuperProxyProfile(
                                    profileName = profileName,
                                    server = serverHost,
                                    port = portInt,
                                    protocol = protocol,
                                    countryCode = countryCode,
                                    username = effectiveUser,
                                    password = effectivePass
                                )
                                isTestingActive = true
                                testStatusText = "Testing connection..."
                                OverlayStateManager.testProxyOnly(context) { success, msg ->
                                    isTestingActive = false
                                    testStatusText = msg
                                    countryCode = OverlayStateManager.uiState.value.proxyState.countryCode
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_test_proxy"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isTestingActive) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Proxy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Test Feedback Banner if tested
                    if (!testStatusText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = testStatusText!!,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (testStatusText!!.contains("Succeeded", ignoreCase = true)) BrandGreen else MaterialTheme.colorScheme.error
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Saved Proxy Profiles (${savedProxies.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Tap to load",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (savedProxies.isEmpty()) {
                        Text(
                            text = "No saved profiles yet. Enter details above and click 'Save Profile' to build your list!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedProxies.forEachIndexed { index, profileItem ->
                                val isSelected = profileName.trim().equals(profileItem.name.trim(), ignoreCase = true)
                                Surface(
                                    onClick = {
                                        // Super Proxy style: Tap profile from list -> automatically load into top inputs!
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
                                        }
                                        Toast.makeText(context, "Loaded: ${profileItem.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) BrandBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("proxy_profile_item_${profileItem.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Serial Number (#1, #2, etc.)
                                            Surface(
                                                color = if (isSelected) BrandBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = profileItem.name,
                                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = if (isSelected) BrandBlue else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = if (profileItem.protocol.equals("HTTP", ignoreCase = true)) Color(0xFF00B0FF).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = profileItem.protocol,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (profileItem.protocol.equals("HTTP", ignoreCase = true)) Color(0xFF0091EA) else Color(0xFFFF8F00),
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${profileItem.host}:${profileItem.port}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = BrandBlue,
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
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val allowedCount = proxy.allowedApps.size
                            Text(
                                text = if (allowedCount == 0) "🛡️ All apps routed through proxy (Default: Chrome, browser, system)" else "$allowedCount apps exclusively routed through proxy",
                                fontSize = 12.sp,
                                color = if (allowedCount > 0) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (proxy.allowedApps.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        OverlayStateManager.setProxyAllowedApps(emptyList())
                                        Toast.makeText(context, "Reset to route All Apps by default", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("All Apps", fontSize = 11.sp)
                                }
                            }

                            Button(
                                onClick = { isAppSelectionExpanded = !isAppSelectionExpanded },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isAppSelectionExpanded) "Done" else "Filter Apps", fontSize = 12.sp)
                            }
                        }
                    }

                    if (isAppSelectionExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("Search installed apps (Via, Chrome, FB)...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val filtered = installedApps.filter {
                            it.name.contains(appSearchQuery, ignoreCase = true) ||
                                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filtered.take(30).forEach { app ->
                                val isChecked = proxy.allowedApps.contains(app.packageName)
                                Surface(
                                    onClick = {
                                        OverlayStateManager.toggleProxyAllowedApp(app.packageName, !isChecked)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isChecked) BrandBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isChecked) BrandBlue.copy(alpha = 0.5f) else Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(app.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                OverlayStateManager.toggleProxyAllowedApp(app.packageName, checked)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = BrandBlue)
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
