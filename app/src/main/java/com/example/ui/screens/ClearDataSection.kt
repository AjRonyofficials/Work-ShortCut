package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.runtime.collectAsState
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.service.UnixSmsManager
import com.example.service.OtpHistoryManager
import com.example.service.WithdrawalManager
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandAmber
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
    val unixState by UnixSmsManager.state.collectAsState()
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("clear_data_section"),
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BrandRose.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = BrandRose,
                                modifier = Modifier.size(22.dp)
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
                                text = "Select apps for fast 1-click Clear Storage & Cache",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Floating Overlay Notice Card
        item {
            val isAccRunning = com.example.service.AutoCleanAccessibilityService.isServiceRunning()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccRunning) BrandTeal.copy(alpha = 0.08f) else BrandRose.copy(alpha = 0.08f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAccRunning) BrandTeal.copy(alpha = 0.4f) else BrandRose.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                imageVector = if (isAccRunning) Icons.Default.Info else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isAccRunning) BrandTeal else BrandRose,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccRunning) "Zero-Touch Cleaner: Active ✓" else "Zero-Touch Cleaner: Needs Permission",
                                fontWeight = FontWeight.Bold,
                                color = if (isAccRunning) BrandTeal else BrandRose,
                                fontSize = 13.sp
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
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAccRunning)
                            "Zero Cleaner অটোমেশন সক্রিয় রয়েছে। ওভারলে বাবল থেকে ট্যাপ করলেই Clear Cache ও Clear Data স্বয়ংক্রিয়ভাবে সম্পন্ন হবে এবং সাথে সাথে অ্যাপ বন্ধ হয়ে যাবে!"
                        else
                            "এক্সেসিবিলিটি সেটিংসে গিয়ে Work ShortCut চালু করুন। যদি স্ক্রিনে 'Not working' লেখা থাকে, তবে টগলটি একবার Off করে আবার On করলেই সাথে সাথে Active হয়ে যাবে!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
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
                    .height(48.dp)
                    .testTag("self_clear_cache_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed.copy(alpha = 0.85f))
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear Work Shortcut Cache & Drafts (Background)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Premium Numbers Database Clear (By Country with Cloud Live Auto Sync)
        item {
            var selectedCountryToClear by remember { mutableStateOf("ALL") }
            var countryDropdownOpen by remember { mutableStateOf(false) }
            var showConfirmDialog by remember { mutableStateOf(false) }

            val uploadedNumbers = unixState.uploadedNumbers
            val distinctCountries = remember(uploadedNumbers) {
                uploadedNumbers.map { it.countryCode to it.countryName }.distinct()
            }
            val targetCount = remember(uploadedNumbers, selectedCountryToClear) {
                if (selectedCountryToClear == "ALL") uploadedNumbers.size
                else uploadedNumbers.count { it.countryCode.equals(selectedCountryToClear, ignoreCase = true) }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = BrandSky,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Premium Numbers Database Clear",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = BrandSky.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Live Auto Sync",
                                color = BrandSky,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "দেশ নির্বাচন করে ডাটাবেস থেকে নাম্বার ক্লিয়ার করুন। সাথে সাথে ক্লাউড ডাটাবেস ও সকল ইউজারের অ্যাপ থেকেও অটো সিঙ্ক হয়ে মুছে যাবে।",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Country Selector Box
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { countryDropdownOpen = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedCountryToClear == "ALL") "🌐 ALL COUNTRIES (${uploadedNumbers.size})"
                                        else "🏳️ $selectedCountryToClear ($targetCount nos)",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = countryDropdownOpen,
                                onDismissRequest = { countryDropdownOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "🌐 সব দেশের নাম্বার (ALL) - ${uploadedNumbers.size} টি",
                                            fontWeight = FontWeight.Bold,
                                            color = AlertRed
                                        )
                                    },
                                    onClick = {
                                        selectedCountryToClear = "ALL"
                                        countryDropdownOpen = false
                                    }
                                )
                                distinctCountries.forEach { (code, name) ->
                                    val count = uploadedNumbers.count { it.countryCode.equals(code, ignoreCase = true) }
                                    DropdownMenuItem(
                                        text = {
                                            Text("$name ($code) - $count টি")
                                        },
                                        onClick = {
                                            selectedCountryToClear = code
                                            countryDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // Clear Action Button
                        Button(
                            onClick = {
                                if (targetCount > 0) {
                                    showConfirmDialog = true
                                } else {
                                    Toast.makeText(context, "সিলেক্টেড দেশে কোনো নাম্বার নেই", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = targetCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Delete Confirmation Dialog
                    if (showConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDialog = false },
                            title = {
                                Text(
                                    text = "⚠️ নাম্বার ডিলিট ও লাইভ সিঙ্ক",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            },
                            text = {
                                Text(
                                    text = if (selectedCountryToClear == "ALL") {
                                        "আপনি কি নিশ্চিত যে সমস্ত ($targetCount টি) আপলোড করা নাম্বার ডাটাবেস ও সকল ইউজারদের অ্যাপ থেকে সম্পূর্ণ ডিলিট ও লাইভ সিঙ্ক করতে চান?"
                                    } else {
                                        "আপনি কি নিশ্চিত যে $selectedCountryToClear দেশের সকল ($targetCount টি) নাম্বার ডাটাবেস ও সকল ইউজারদের অ্যাপ থেকে মুছে দিতে চান?"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val targetCountry = selectedCountryToClear
                                        showConfirmDialog = false
                                        UnixSmsManager.deleteNumbersByCountry(targetCountry) { success ->
                                            Toast.makeText(
                                                context,
                                                if (success) "✓ $targetCountry এর নাম্বার ডাটাবেস ও সকল ইউজারদের অ্যাপ থেকে মুছে লাইভ সিঙ্ক সম্পন্ন হয়েছে!"
                                                else "✓ লোকাল ও ক্লাউড ডাটাবেস আপডেট হয়েছে",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                                ) {
                                    Text("হ্যাঁ, ডিলিট করুন")
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { showConfirmDialog = false }) {
                                    Text("বাতিল")
                                }
                            }
                        )
                    }
                }
            }
        }

        // Database Maintenance & 60+ Days History Auto-Cleanup
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandAmber.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = BrandAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto Clean 60+ Days Data & Garbage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = BrandAmber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Maintenance",
                                color = BrandAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "২ মাসের (৬০ দিন) পুরোনো OTP হিস্ট্রি, উইথড্র রেকর্ড ও ভুল/উল্টাপাল্টা ডাটা এক ক্লিকে মুছে ফেলুন।",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Purge Corrupted Data
                        Button(
                            onClick = {
                                val pOtp = OtpHistoryManager.purgeCorruptedOtpRecords()
                                val pWith = WithdrawalManager.purgeCorruptedWithdrawals()
                                val pNum = UnixSmsManager.purgeInvalidUploadedNumbers()
                                Toast.makeText(
                                    context,
                                    "⚡ $pOtp টি OTP, $pWith টি উইথড্র ও $pNum টি নাম্বার ক্লিন করা হয়েছে!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAmber),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("উল্টাপাল্টা ডাটা ক্লিন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Purge 60+ Days Old Data
                        Button(
                            onClick = {
                                val oOtp = OtpHistoryManager.purgeOldOtpRecords(60)
                                val oWith = WithdrawalManager.purgeOldWithdrawalRequests(60)
                                Toast.makeText(
                                    context,
                                    "✓ ২ মাসের পুরোনো $oOtp টি OTP ও $oWith টি উইথড্র ডিলিট হয়েছে!",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("২+ মাসের ডাটা মুছুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Selected Apps Chips List
        item {
            Column {
                Text(
                    text = "Active Quick Apps (${state.selectedClearDataApps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (state.selectedClearDataApps.isEmpty()) {
                    Text(
                        text = "No apps selected. Check apps from the list below to add them to your Quick Clear list.",
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
                                color = BrandRose.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRose.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    com.example.ui.components.AppIconImage(
                                        packageName = itemApp.packageName,
                                        fallbackText = itemApp.appName,
                                        size = 38.dp,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
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
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Mode Switcher Button (FB Lite vs Standard)
                                        Surface(
                                            onClick = {
                                                val nextMode = !itemApp.isLiteStorageApp
                                                OverlayStateManager.toggleAppLiteStorageMode(itemApp.packageName, nextMode)
                                                Toast.makeText(
                                                    context,
                                                    if (nextMode) "${itemApp.appName}: FB Lite Flow Enabled (Accounts & Settings ➔ OK ➔ Clear)"
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
                                                text = if (itemApp.isLiteStorageApp) "⚡ Flow: FB Lite (Accounts ➔ OK ➔ Clear)" else "⚙️ Flow: Standard Clear Data",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (itemApp.isLiteStorageApp) BrandSky else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Clear Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                                modifier = Modifier.size(18.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select from Installed Apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        color = BrandSky.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandSky.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            val workKeywords = listOf("whatsapp", "telegram", "chrome", "facebook", "katana", "orca", "instagram", "imo")
                            val matchedApps = allInstalledApps.filter { app ->
                                val pkg = app.packageName.lowercase()
                                val name = app.appName.lowercase()
                                workKeywords.any { kw -> pkg.contains(kw) || name.contains(kw) }
                            }
                            if (matchedApps.isEmpty()) {
                                Toast.makeText(context, "কোনো কমন ওয়ার্ক অ্যাপস পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                            } else {
                                matchedApps.forEach { app ->
                                    val isLite = app.isLiteStorageApp || AppManagerHelper.isLiteOrModdedApp(app.packageName, app.appName)
                                    OverlayStateManager.toggleClearDataApp(app.copy(isLiteStorageApp = isLite), true, isLite)
                                }
                                Toast.makeText(context, "✓ ${matchedApps.size}টি ওয়ার্ক অ্যাপ সিলেক্ট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = BrandSky, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⚡ 1-Tap Select Work Apps", color = BrandSky, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

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
                            "${app.appName} added to Clean Tab! " + if (isLiteDetected) "(FB Lite Flow ⚡)" else "",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "${app.appName} removed from Clean Tab", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                color = if (isChecked) (if (isLiteDetected) BrandSky.copy(alpha = 0.12f) else BrandRose.copy(alpha = 0.12f))
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isChecked) (if (isLiteDetected) BrandSky.copy(alpha = 0.6f) else BrandRose.copy(alpha = 0.5f))
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    com.example.ui.components.AppIconImage(
                        packageName = app.packageName,
                        fallbackText = app.appName,
                        size = 32.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
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
                                if (isLiteDetected) "Live on Overlay Clean Tab ✓ • FB Lite Flow ⚡"
                                else "Live on Overlay Clean Tab ✓ • Standard Flow"
                            } else app.packageName,
                            fontSize = 11.sp,
                            color = if (isChecked) (if (isLiteDetected) BrandSky else BrandRose) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            OverlayStateManager.toggleClearDataApp(app.copy(isLiteStorageApp = isLiteDetected), checked, isLiteDetected)
                            if (checked) {
                                Toast.makeText(
                                    context,
                                    "${app.appName} added to Clean Tab! " + if (isLiteDetected) "(FB Lite Flow ⚡)" else "",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "${app.appName} removed from Clean Tab", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = if (isLiteDetected) BrandSky else BrandRose)
                    )
                }
            }
        }
    }
}
