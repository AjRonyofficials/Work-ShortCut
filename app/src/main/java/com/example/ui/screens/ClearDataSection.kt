package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.service.ClearDataMode
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandGreen
import com.example.ui.theme.BrandRose
import com.example.ui.theme.BrandSky
import com.example.ui.theme.BrandTeal
import com.example.util.AppInfoItem
import com.example.util.AppManagerHelper

@Composable
fun ClearDataSection(
    state: OverlayUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val allInstalledApps = remember { mutableStateListOf<AppInfoItem>() }

    LaunchedEffect(Unit) {
        val apps = AppManagerHelper.getInstalledLauncherApps(context)
        allInstalledApps.clear()
        allInstalledApps.addAll(apps)
    }

    val selectedPackages = remember(state.selectedClearDataApps) {
        state.selectedClearDataApps.map { it.packageName }.toSet()
    }

    val filteredApps = remember(searchQuery, allInstalledApps.toList()) {
        if (searchQuery.isBlank()) {
            allInstalledApps
        } else {
            allInstalledApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val isAccRunning = com.example.service.AutoCleanAccessibilityService.isServiceRunning()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("clear_data_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BrandRose.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = BrandRose,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Clear Data & Cache Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Universal automated clearing • Android 10 to 16+",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Mode Switcher: Single Mode vs Batch Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Clearing Mode Selection",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Single Mode Option
                        val isSingle = state.cleanSectionMode == ClearDataMode.SINGLE
                        Surface(
                            onClick = {
                                OverlayStateManager.setCleanSectionMode(ClearDataMode.SINGLE)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSingle) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = if (isSingle) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Single Mode",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSingle) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSingle) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Batch Mode Option
                        val isBatch = state.cleanSectionMode == ClearDataMode.BATCH
                        Surface(
                            onClick = {
                                OverlayStateManager.setCleanSectionMode(ClearDataMode.BATCH)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isBatch) BrandRose else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = if (isBatch) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Batch Mode (Multi)",
                                    fontSize = 12.sp,
                                    fontWeight = if (isBatch) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isBatch) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state.cleanSectionMode == ClearDataMode.SINGLE)
                            "⚡ Single Mode: নিচের তালিকা থেকে যেকোনো অ্যাপের 'Clear Data' বাটনে চাপ দিয়ে তাৎক্ষণিক ১টি অ্যাপ পরিষ্কার করুন।"
                        else
                            "🚀 Batch Mode: একাধিক অ্যাপ নির্বাচন করুন এবং একসাথে সবগুলোর জন্য স্বয়ংক্রিয় ধারাবাহিক (Sequential) ক্লিয়ার ডেটা চালান।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Active Batch Running Card
        if (state.cleanSectionMode == ClearDataMode.BATCH && state.isBatchRunning && state.batchRunningSection == "CLEAN") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandRose.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandRose)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(BrandRose)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sequential Batch In Progress...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BrandRose
                                )
                            }
                            Button(
                                onClick = { OverlayStateManager.stopBatchClear(context) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        val progress = if (state.batchTotalApps > 0)
                            state.batchCurrentAppIndex.toFloat() / state.batchTotalApps.toFloat()
                        else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BrandRose,
                            trackColor = BrandRose.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Clearing ${state.batchCurrentAppIndex}/${state.batchTotalApps}: ${state.batchCurrentAppName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandRose
                            )
                        }
                    }
                }
            }
        }

        // Batch Action Launcher (When in Batch Mode and not currently running)
        if (state.cleanSectionMode == ClearDataMode.BATCH && (!state.isBatchRunning || state.batchRunningSection != "CLEAN")) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandRose.copy(alpha = 0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandRose.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Batch Queue Controls",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${state.selectedClearDataApps.size} apps queued for batch",
                                    fontSize = 11.sp,
                                    color = BrandRose,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        OverlayStateManager.selectAllAppsForClear(allInstalledApps)
                                        Toast.makeText(context, "সমস্ত অ্যাপ সিলেক্ট করা হয়েছে (${allInstalledApps.size} টি)", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Select All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        OverlayStateManager.clearAllSelectedApps()
                                        Toast.makeText(context, "সিলেকশন ক্লিয়ার করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Clear All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                OverlayStateManager.startCleanSectionBatch(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRose),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start Batch Clear (${state.selectedClearDataApps.size} Apps)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Android 10-16+ Compatibility & Anti-Crash Guarantee
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandTeal.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Android 10 - 16+ Tested • Sequential 300ms Queue • 10s Fail-Safe Timeout • Zero-Lag & 100% Crash-Free",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandTeal,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Accessibility Service Status Notice Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccRunning) BrandTeal.copy(alpha = 0.08f) else BrandRose.copy(alpha = 0.08f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAccRunning) BrandTeal.copy(alpha = 0.4f) else BrandRose.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isAccRunning) BrandTeal else BrandRose,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccRunning) "Zero-Touch Cleaner: Active ✓" else "Zero-Touch Cleaner: Needs Permission",
                                fontWeight = FontWeight.Bold,
                                color = if (isAccRunning) BrandTeal else BrandRose,
                                fontSize = 12.sp
                            )
                        }

                        if (!isAccRunning) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRose),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAccRunning)
                            "Zero Cleaner অটোমেশন সক্রিয়। Samsung, Xiaomi/HyperOS, Vivo, Oppo, Realme, Pixel (Android 10-16+) সব ফোনে ল্যাগ ছাড়া নির্ভুলভাবে কাজ করবে।"
                        else
                            "এক্সেসিবিলিটি সেটিংসে গিয়ে Work ShortCut চালু করুন। যদি 'Not working' লেখা থাকে, টগলটি একবার Off করে On করলেই সাথে সাথে Active হয়ে যাবে!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Self Clean Button
        item {
            Button(
                onClick = {
                    OverlayStateManager.executeSelfClearData(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("self_clear_cache_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.85f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear Work Shortcut Cache & Drafts (Background)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Active Quick Apps Chips List
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active Selected Apps (${state.selectedClearDataApps.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.clearDataMode == ClearDataMode.BATCH) "Batch Queue" else "Single List",
                        fontSize = 11.sp,
                        color = BrandRose,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (state.selectedClearDataApps.isEmpty()) {
                    Text(
                        text = "No apps selected. Check apps from the list below to add them to your Clear list.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.selectedClearDataApps.forEach { itemApp ->
                            Surface(
                                onClick = {
                                    OverlayStateManager.executeClearDataForApp(context, itemApp)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = BrandRose.copy(alpha = 0.10f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRose.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = itemApp.appName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (itemApp.isLiteStorageApp) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = BrandSky.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "FB LITE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = BrandSky,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = itemApp.packageName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        // Mode Switcher Button (FB Lite vs Standard)
                                        Surface(
                                            onClick = {
                                                val nextMode = !itemApp.isLiteStorageApp
                                                OverlayStateManager.toggleAppLiteStorageMode(itemApp.packageName, nextMode)
                                                Toast.makeText(
                                                    context,
                                                    if (nextMode) "${itemApp.appName}: FB Lite Flow Enabled (Accounts & Settings ➔ 1s Delay ➔ OK ➔ Clear)"
                                                    else "${itemApp.appName}: Standard Clear Flow Enabled",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (itemApp.isLiteStorageApp) BrandSky.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (itemApp.isLiteStorageApp) BrandSky.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Text(
                                                text = if (itemApp.isLiteStorageApp) "⚡ Flow: FB Lite (Accounts ➔ 1s Delay ➔ OK ➔ Clear)" else "⚙️ Flow: Standard Clear Data",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (itemApp.isLiteStorageApp) BrandSky else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = {
                                                OverlayStateManager.executeClearDataForApp(context, itemApp)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (itemApp.isLiteStorageApp) BrandSky else BrandRose),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = {
                                                OverlayStateManager.toggleClearDataApp(itemApp, false)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // Search Bar for installed apps
        item {
            Column {
                Text(
                    text = "Select from Installed Apps",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps (e.g. Chrome, Facebook)...") },
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

        // Installed Apps List
        items(filteredApps, key = { it.packageName }) { app ->
            val isChecked = selectedPackages.contains(app.packageName)
            val isLiteDetected = app.isLiteStorageApp || AppManagerHelper.isLiteOrModdedApp(app.packageName, app.appName)

            Surface(
                onClick = {
                    val willBeChecked = !isChecked
                    OverlayStateManager.toggleClearDataApp(app.copy(isLiteStorageApp = isLiteDetected), willBeChecked, isLiteDetected)
                    if (willBeChecked) {
                        Toast.makeText(
                            context,
                            "${app.appName} added to Queue! " + if (isLiteDetected) "(FB Lite Flow ⚡)" else "",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "${app.appName} removed from Queue", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                color = if (isChecked) (if (isLiteDetected) BrandSky.copy(alpha = 0.12f) else BrandRose.copy(alpha = 0.12f))
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isChecked) (if (isLiteDetected) BrandSky.copy(alpha = 0.6f) else BrandRose.copy(alpha = 0.5f))
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.appName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            if (isLiteDetected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BrandSky.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "⚡ LITE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BrandSky,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isChecked) {
                                if (isLiteDetected) "In Queue ✓ • FB Lite Flow (Accounts & Settings ➔ 1s Delay ➔ OK ➔ Clear)"
                                else "In Queue ✓ • Standard Flow (Storage ➔ Clear Cache ➔ Data)"
                            } else app.packageName,
                            fontSize = 11.sp,
                            color = if (isChecked) (if (isLiteDetected) BrandSky else BrandRose) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.clearDataMode == ClearDataMode.SINGLE) {
                            Button(
                                onClick = {
                                    OverlayStateManager.executeClearDataForApp(
                                        context,
                                        app.copy(isLiteStorageApp = isLiteDetected)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isLiteDetected) BrandSky else BrandRose),
                                modifier = Modifier
                                    .height(30.dp)
                                    .padding(end = 6.dp)
                            ) {
                                Text("Clear", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                OverlayStateManager.toggleClearDataApp(app.copy(isLiteStorageApp = isLiteDetected), checked, isLiteDetected)
                                if (checked) {
                                    Toast.makeText(
                                        context,
                                        "${app.appName} added to Queue! " + if (isLiteDetected) "(FB Lite Flow ⚡)" else "",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, "${app.appName} removed from Queue", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = if (isLiteDetected) BrandSky else BrandRose)
                        )
                    }
                }
            }
        }
    }
}
