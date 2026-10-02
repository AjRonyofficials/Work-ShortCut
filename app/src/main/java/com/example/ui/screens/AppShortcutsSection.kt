package com.example.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

    val installedApps = remember { mutableStateListOf<AppInfoItem>() }

    LaunchedEffect(Unit) {
        try {
            val apps = AppManagerHelper.getInstalledLauncherApps(context)
            installedApps.clear()
            if (apps.isNotEmpty()) {
                installedApps.addAll(apps)
            } else {
                // Fallback to getInstalledApplications
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
            // Gracefully handle query failure
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
            .padding(16.dp)
            .testTag("app_shortcuts_section"),
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BrandBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = BrandBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating Overlay Apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Select any app to instantly show on your floating edge bar!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // App Clean Mode Quick Controls (Single & Batch Mode)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = com.example.ui.theme.BrandRose,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "App Clean Controls",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Mode Selector Pills
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val isSingle = state.clearDataMode == com.example.service.ClearDataMode.SINGLE
                            Surface(
                                onClick = { OverlayStateManager.setClearDataMode(com.example.service.ClearDataMode.SINGLE) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSingle) MaterialTheme.colorScheme.primary else Color.Transparent
                            ) {
                                Text(
                                    text = "Single",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSingle) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSingle) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            val isBatch = state.clearDataMode == com.example.service.ClearDataMode.BATCH
                            Surface(
                                onClick = { OverlayStateManager.setClearDataMode(com.example.service.ClearDataMode.BATCH) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isBatch) com.example.ui.theme.BrandRose else Color.Transparent
                            ) {
                                Text(
                                    text = "Batch",
                                    fontSize = 11.sp,
                                    fontWeight = if (isBatch) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isBatch) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.clearDataMode == com.example.service.ClearDataMode.BATCH) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Batch Queue: ${state.selectedClearDataApps.size} apps queued",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (state.isBatchRunning) {
                                Button(
                                    onClick = { OverlayStateManager.stopBatchClear(context) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Stop (${state.batchCurrentAppIndex}/${state.batchTotalApps})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { OverlayStateManager.startBatchClear(context) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.BrandRose),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("▶ Start Batch (${state.selectedClearDataApps.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Single Mode: Tap any app card to clean individually.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { showManualForm = !showManualForm }) {
                    Text(if (showManualForm) "Hide Custom Input" else "+ Custom Package")
                }
            }
        }

        // Active Shortcuts Carousel / List
        if (state.customAppShortcuts.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "No apps in floating overlay yet. Tap '+ Add to Overlay' on any app below to show it instantly!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.customAppShortcuts, key = { it.id }) { shortcut ->
                val cardColor = try {
                    Color(android.graphics.Color.parseColor(shortcut.colorHex))
                } catch (_: Exception) {
                    BrandBlue
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (state.clearDataMode == com.example.service.ClearDataMode.SINGLE) {
                                    val isLite = com.example.util.AppManagerHelper.isFacebookLite(shortcut.packageName, shortcut.appName)
                                    val appItem = com.example.util.AppInfoItem(
                                        appName = shortcut.appName,
                                        packageName = shortcut.packageName,
                                        isSelected = true,
                                        isLiteStorageApp = isLite
                                    )
                                    OverlayStateManager.executeClearDataForApp(context, appItem)
                                } else {
                                    OverlayStateManager.launchAppShortcut(context, shortcut)
                                }
                            },
                            onLongClick = {
                                com.example.util.VibrationHelper.vibrateSuccess(context)
                                Toast.makeText(context, "⚡ ${shortcut.appName} ফোর্স স্টপ ও ক্লোজ করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                OverlayStateManager.closeAppShortcut(context, shortcut)
                            }
                        ),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cardColor,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = shortcut.appName.take(3).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = shortcut.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (state.clearDataMode == com.example.service.ClearDataMode.SINGLE)
                                        "Single Mode: Tap to Clean Data 🧹"
                                    else
                                        "Tap: Open • Hold: Auto Close",
                                    fontSize = 11.sp,
                                    color = if (state.clearDataMode == com.example.service.ClearDataMode.SINGLE) com.example.ui.theme.BrandRose else BrandGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Test Open
                            IconButton(
                                onClick = { OverlayStateManager.launchAppShortcut(context, shortcut) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Launch, contentDescription = "Open App", tint = BrandGreen, modifier = Modifier.size(18.dp))
                            }

                            // Clean App Data
                            IconButton(
                                onClick = {
                                    val isLite = com.example.util.AppManagerHelper.isFacebookLite(shortcut.packageName, shortcut.appName)
                                    val appItem = com.example.util.AppInfoItem(
                                        appName = shortcut.appName,
                                        packageName = shortcut.packageName,
                                        isSelected = true,
                                        isLiteStorageApp = isLite
                                    )
                                    OverlayStateManager.executeClearDataForApp(context, appItem)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = "Clear Data", tint = com.example.ui.theme.BrandRose, modifier = Modifier.size(18.dp))
                            }

                            // Test Close
                            IconButton(
                                onClick = { OverlayStateManager.closeAppShortcut(context, shortcut) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Auto Close App", tint = AlertRed, modifier = Modifier.size(18.dp))
                            }

                            // Remove from Overlay
                            IconButton(
                                onClick = {
                                    OverlayStateManager.removeCustomAppShortcut(shortcut.id)
                                    Toast.makeText(context, "Removed ${shortcut.appName} from overlay", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Add Cloned / Custom Package", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputAppName,
                            onValueChange = { inputAppName = it },
                            label = { Text("Display Name (e.g. Dual FB, Work Via)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = inputPkgName,
                            onValueChange = { inputPkgName = it },
                            label = { Text("Package Name (e.g. com.facebook.katana)") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Add to Overlay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Installed Apps Selector Header & Live Search
        item {
            Column {
                Text(
                    text = "Select from Installed Apps",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap '+ Add to Overlay' on any app to show its glossy button on the floating sidebar instantly!",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search installed apps (Via, Chrome, Facebook, Dual)...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
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
                shape = RoundedCornerShape(12.dp),
                color = if (isInOverlay) BrandGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isInOverlay) BrandGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                        Surface(
                            shape = CircleShape,
                            color = if (isInOverlay) BrandGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = app.appName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isInOverlay) BrandGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = app.appName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = app.packageName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isInOverlay) {
                        Button(
                            onClick = {
                                OverlayStateManager.removeCustomAppShortcutByPackage(app.packageName)
                                Toast.makeText(context, "${app.appName} removed from overlay", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("In Overlay ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                OverlayStateManager.addCustomAppShortcut(app.appName, app.packageName)
                                Toast.makeText(context, "${app.appName} added to floating overlay! ✓", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Usage Tip Card
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandBlue.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Once added, the app tab shows up directly on the floating sidebar! Simply TAP to launch, or HOLD FOR 2 SECONDS to auto-close it instantly!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
