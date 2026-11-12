package com.example.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGreen
import com.example.util.AppInfoItem
import com.example.util.AppManagerHelper

data class SimpleInstalledApp(
    val name: String,
    val packageName: String
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppShortcutsSection(
    state: OverlayUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputAppName by remember { mutableStateOf("") }
    var inputPkgName by remember { mutableStateOf("") }
    var appSearchQuery by remember { mutableStateOf("") }
    var showManualForm by remember { mutableStateOf(false) }
    var appToRename by remember { mutableStateOf<com.example.service.CustomAppShortcut?>(null) }
    var newNameInput by remember { mutableStateOf("") }

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
        focusedBorderColor = cyanAccent,
        unfocusedBorderColor = Color(0xFF233856),
        focusedLabelColor = cyanAccent,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = cyanAccent
    )

    val installedApps = remember { mutableStateListOf<AppInfoItem>() }

    LaunchedEffect(Unit) {
        try {
            val apps = AppManagerHelper.getInstalledLauncherApps(context)
            installedApps.clear()
            if (apps.isNotEmpty()) {
                installedApps.addAll(apps)
            } else {
                val pm = context.packageManager
                val fallbackList = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName.contains("chrome") }
                    .map { appInfo ->
                        AppInfoItem(
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            packageName = appInfo.packageName
                        )
                    }
                    .sortedBy { it.appName.lowercase() }
                installedApps.addAll(fallbackList)
            }
        } catch (_: Exception) {
        }
    }

    val activePackages = remember(state.customAppShortcuts) {
        state.customAppShortcuts.map { it.packageName }.toSet()
    }

    val filteredApps = remember(appSearchQuery, installedApps.toList()) {
        if (appSearchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(appSearchQuery, ignoreCase = true) ||
                        it.packageName.contains(appSearchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("app_shortcuts_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, cyanAccent.copy(alpha = 0.4f))
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
                                        listOf(cyanAccent, BrandBlue)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = Color(0xFF041E2B),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Floating Overlay Apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "1-Tap Open & 2s Hold Force-Close from Edge Bar",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = neonGreen.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "${state.customAppShortcuts.size} Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = neonGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Active Overlay Apps Status & Management
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live in Floating Overlay (${state.customAppShortcuts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                TextButton(onClick = { showManualForm = !showManualForm }) {
                    Text(
                        text = if (showManualForm) "Hide Custom Input" else "+ Custom Package",
                        color = cyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active Shortcuts Carousel / List
        if (state.customAppShortcuts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "No apps in floating overlay yet. Tap '+ Add' on any app below to show it instantly!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        } else {
            items(state.customAppShortcuts, key = { it.id }) { shortcut ->
                val cardColor = try {
                    Color(android.graphics.Color.parseColor(shortcut.colorHex))
                } catch (_: Exception) {
                    cyanAccent
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { OverlayStateManager.launchAppShortcut(context, shortcut) },
                            onLongClick = {
                                com.example.util.VibrationHelper.vibrateSuccess(context)
                                Toast.makeText(context, "⚡ ${shortcut.appName} ফোর্স স্টপ ও ক্লোজ করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                OverlayStateManager.closeAppShortcut(context, shortcut)
                            }
                        ),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.2.dp, cardColor.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            com.example.ui.components.AppIconImage(
                                packageName = shortcut.packageName,
                                fallbackText = shortcut.appName,
                                size = 40.dp,
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = shortcut.appName,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap: Open • Hold: Auto Close",
                                    fontSize = 11.sp,
                                    color = neonGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { OverlayStateManager.launchAppShortcut(context, shortcut) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = "Open App", tint = neonGreen, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { OverlayStateManager.closeAppShortcut(context, shortcut) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Auto Close App", tint = AlertRed, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    appToRename = shortcut
                                    newNameInput = shortcut.appName
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename App", tint = cyanAccent, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    OverlayStateManager.removeCustomAppShortcut(shortcut.id)
                                    Toast.makeText(context, "Removed ${shortcut.appName} from overlay", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Optional Manual Package Form (For Clones & Dual Spaces)
        if (showManualForm) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Add Cloned / Custom Package",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputAppName,
                            onValueChange = { inputAppName = it },
                            label = { Text("Display Name (e.g. Dual FB, Work Via)") },
                            colors = textFieldColors,
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputPkgName,
                            onValueChange = { inputPkgName = it },
                            label = { Text("Package Name (e.g. com.facebook.katana)") },
                            colors = textFieldColors,
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (inputAppName.isNotBlank() && inputPkgName.isNotBlank()) {
                                    OverlayStateManager.addCustomAppShortcut(inputAppName, inputPkgName)
                                    Toast.makeText(context, "$inputAppName added to floating overlay! ✓", Toast.LENGTH_SHORT).show()
                                    inputAppName = ""
                                    inputPkgName = ""
                                }
                            },
                            enabled = inputAppName.isNotBlank() && inputPkgName.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF041E2B))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Add to Overlay", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF041E2B))
                        }
                    }
                }
            }
        }

        // Installed Apps Selector Header & Live Search
        item {
            Column {
                Text(
                    text = "Select from Installed Apps (${filteredApps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Tap '+ Add' on any app to show its shortcut button on the floating sidebar instantly",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search installed apps (Via, Chrome, Facebook, Dual)...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = cyanAccent)
                    },
                    trailingIcon = if (appSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { appSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_installed_apps_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Installed Apps List with 1-Tap Overlay Toggle
        items(filteredApps, key = { it.packageName }) { app ->
            val isInOverlay = activePackages.contains(app.packageName)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            OverlayStateManager.toggleAppShortcut(app.appName, app.packageName, !isInOverlay)
                            if (!isInOverlay) {
                                Toast.makeText(context, "${app.appName} added to floating overlay! ✓", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "${app.appName} removed from overlay", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = {
                            com.example.util.VibrationHelper.vibrateSuccess(context)
                            Toast.makeText(context, "⚡ ${app.appName} ফোর্স স্টপ ও ক্লোজ করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                            OverlayStateManager.autoForceClosePackage(context, app.packageName, app.appName)
                        }
                    ),
                shape = RoundedCornerShape(14.dp),
                color = if (isInOverlay) Color(0xFF0A2218) else cardBgColor,
                border = BorderStroke(
                    1.dp,
                    if (isInOverlay) neonGreen.copy(alpha = 0.55f) else cardBorderColor
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        com.example.ui.components.AppIconImage(
                            packageName = app.packageName,
                            fallbackText = app.appName,
                            size = 38.dp,
                            shape = RoundedCornerShape(9.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = app.appName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isInOverlay) {
                        Button(
                            onClick = {
                                OverlayStateManager.removeCustomAppShortcutByPackage(app.packageName)
                                Toast.makeText(context, "${app.appName} removed from overlay", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF042F16))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Added ✓", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF042F16))
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                OverlayStateManager.addCustomAppShortcut(app.appName, app.packageName)
                                Toast.makeText(context, "${app.appName} added to floating overlay! ✓", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.6f)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = cyanAccent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = cyanAccent)
                        }
                    }
                }
            }
        }

        // Usage Tip Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Once added, the app icon shows directly on the floating sidebar! Tap to launch, or HOLD FOR 2 SECONDS to force-stop & close it automatically.",
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }

    if (appToRename != null) {
        val targetApp = appToRename!!
        AlertDialog(
            onDismissRequest = { appToRename = null },
            containerColor = cardBgColor,
            title = {
                Text("শর্টকাট অ্যাপের নাম পরিবর্তন করুন", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("নতুন নাম লিখুন (${targetApp.packageName}):", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        singleLine = true,
                        colors = textFieldColors,
                        placeholder = { Text("যেমন: FB Fresh 1, Clone 2...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newNameInput.trim()
                        if (clean.isNotBlank()) {
                            OverlayStateManager.renameAppShortcut(targetApp.packageName, clean)
                            Toast.makeText(context, "✓ শর্টকাটের নাম পরিবর্তন হয়েছে: $clean", Toast.LENGTH_SHORT).show()
                            appToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cyanAccent)
                ) {
                    Text("Save", fontWeight = FontWeight.ExtraBold, color = Color(0xFF041E2B))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { appToRename = null },
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Cancel", color = Color(0xFFCBD5E1))
                }
            }
        )
    }
}
