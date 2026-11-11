package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AutoCleanAccessibilityService
import com.example.service.OtpHistoryManager
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.service.UnixSmsManager
import com.example.service.WithdrawalManager
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandRose
import com.example.util.AppInfoItem
import com.example.util.AppManagerHelper
import kotlinx.coroutines.delay

@Composable
fun ClearDataSection(
    state: OverlayUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val allInstalledApps = remember { mutableStateListOf<AppInfoItem>() }
    var appToRename by remember { mutableStateOf<AppInfoItem?>(null) }
    var newNameInput by remember { mutableStateOf("") }
    var isAccRunning by remember { mutableStateOf(AutoCleanAccessibilityService.isServiceRunning()) }

    LaunchedEffect(Unit) {
        val apps = AppManagerHelper.getInstalledLauncherApps(context)
        allInstalledApps.clear()
        allInstalledApps.addAll(apps)
        while (true) {
            isAccRunning = AutoCleanAccessibilityService.isServiceRunning()
            delay(1000L)
        }
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
        focusedLeadingIconColor = cyanAccent,
        unfocusedLeadingIconColor = Color(0xFF64748B),
        cursorColor = cyanAccent
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("clear_data_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Section Header Card
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(BrandRose, Color(0xFFE11D48))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clear Data & Cache Manager",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Android 10–17 All Models • All-Language FB Lite Auto-Clean",
                                    fontSize = 11.sp,
                                    color = cyanAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAccRunning) neonGreen.copy(alpha = 0.16f) else BrandRose.copy(alpha = 0.16f),
                            border = BorderStroke(
                                1.dp,
                                if (isAccRunning) neonGreen.copy(alpha = 0.5f) else BrandRose.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = if (isAccRunning) "ACTIVE ✓" else "OFF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAccRunning) neonGreen else BrandRose,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Zero-Touch Accessibility Engine Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccRunning) Color(0xFF0A201A) else Color(0xFF221019)
                ),
                border = BorderStroke(
                    1.2.dp,
                    if (isAccRunning) neonGreen.copy(alpha = 0.6f) else BrandRose.copy(alpha = 0.6f)
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
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isAccRunning) neonGreen else BrandRose,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAccRunning) "Zero-Touch Cleaner: Active ✓ (Android 10–17)"
                                       else "Zero-Touch Cleaner: Permission Required",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAccRunning) neonGreen else BrandRose,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isAccRunning) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRose),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAccRunning)
                            "✓ Android 10–17 সকল মডেলের ফোনে সকল অ্যাপের Clear Data এবং Facebook Lite যেকোনো ভাষায় (Language পরিবর্তন করলেও) স্বয়ংক্রিয়ভাবে Accounts & Settings মার্ক করে ক্লিয়ার সম্পন্ন করবে!"
                        else
                            "এক্সেসিবিলিটি সেটিংসে গিয়ে Work ShortCut চালু করুন। যদি স্ক্রিনে 'Not working' লেখা থাকে, তবে টগলটি একবার Off করে আবার On করলেই সাথে সাথে Active হয়ে যাবে!",
                        fontSize = 11.5.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }

        // 3. Quick Self Clean Button
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear Work Shortcut Cache & Drafts (Background)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4. Selected Quick Clear Apps List
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
                        Text(
                            text = "Active Quick Clear Apps (${state.selectedClearDataApps.size})",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = cyanAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "1-Tap Auto Clear",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cyanAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.selectedClearDataApps.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = innerBoxBgColor,
                            border = BorderStroke(1.dp, Color(0xFF1A2B44)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "কোনো অ্যাপ সিলেক্ট করা নেই। নিচের তালিকা থেকে অ্যাপ টিক দিয়ে Quick Clear লিস্টে যোগ করুন।",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.selectedClearDataApps.forEach { itemApp ->
                                Surface(
                                    onClick = {
                                        OverlayStateManager.executeClearDataForApp(context, itemApp)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = innerBoxBgColor,
                                    border = BorderStroke(
                                        1.2.dp,
                                        if (itemApp.isLiteStorageApp) cyanAccent.copy(alpha = 0.6f) else BrandRose.copy(alpha = 0.55f)
                                    ),
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
                                            shape = RoundedCornerShape(9.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = itemApp.appName,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.5.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (itemApp.isLiteStorageApp) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = cyanAccent.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "FB LITE",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = cyanAccent,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = itemApp.packageName,
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(5.dp))
                                            // Mode Switcher Pill (FB Lite All-Language vs Standard Android 10-17)
                                            Surface(
                                                onClick = {
                                                    val nextMode = !itemApp.isLiteStorageApp
                                                    OverlayStateManager.toggleAppLiteStorageMode(itemApp.packageName, nextMode)
                                                    Toast.makeText(
                                                        context,
                                                        if (nextMode) "${itemApp.appName}: FB Lite (All-Language Auto Clear) চালু হয়েছে"
                                                        else "${itemApp.appName}: Standard Clear Data চালু হয়েছে",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (itemApp.isLiteStorageApp) cyanAccent.copy(alpha = 0.14f) else Color(0xFF16243A),
                                                border = BorderStroke(
                                                    0.8.dp,
                                                    if (itemApp.isLiteStorageApp) cyanAccent.copy(alpha = 0.55f) else Color(0xFF283D5C)
                                                )
                                            ) {
                                                Text(
                                                    text = if (itemApp.isLiteStorageApp) "⚡ FB Lite Mode (All Languages)"
                                                           else "⚙️ Standard Clear (Android 10–17)",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (itemApp.isLiteStorageApp) cyanAccent else Color(0xFFCBD5E1),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    appToRename = itemApp
                                                    newNameInput = itemApp.appName
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Rename App",
                                                    tint = cyanAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    OverlayStateManager.executeClearDataForApp(context, itemApp)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (itemApp.isLiteStorageApp) Color(0xFF0284C7) else BrandRose
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                            }

                                            IconButton(
                                                onClick = {
                                                    OverlayStateManager.toggleClearDataApp(itemApp, false)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove",
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

        // 5. Database Maintenance & 60+ Days History Auto-Cleanup
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, BrandAmber.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
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
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("উল্টাপাল্টা ডাটা ক্লিন", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, maxLines = 1)
                        }

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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("২+ মাসের ডাটা মুছুন", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 7. Search Bar for Installed Apps
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select from Installed Apps (${filteredApps.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Surface(
                        color = cyanAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.45f)),
                        modifier = Modifier.clickable {
                            val workKeywords = listOf("whatsapp", "telegram", "chrome", "facebook", "katana", "lite", "orca", "instagram", "imo", "via")
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
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⚡ 1-Tap Select Work Apps", color = cyanAccent, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps (e.g. Facebook Lite, Chrome, Via)...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_installed_apps_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors
                )
            }
        }

        // 8. Installed Apps List
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
                            "${app.appName} added to Clean Tab! " + if (isLiteDetected) "(FB Lite All-Language Flow ⚡)" else "",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "${app.appName} removed from Clean Tab", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isChecked) Color(0xFF10233E) else cardBgColor,
                border = BorderStroke(
                    1.1.dp,
                    if (isChecked) (if (isLiteDetected) cyanAccent else BrandRose) else cardBorderColor
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    com.example.ui.components.AppIconImage(
                        packageName = app.packageName,
                        fallbackText = app.appName,
                        size = 34.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isLiteDetected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = cyanAccent.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "⚡ LITE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = cyanAccent,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isChecked) {
                                if (isLiteDetected) "Live on Overlay Clean Tab ✓ • FB Lite (All Languages) ⚡"
                                else "Live on Overlay Clean Tab ✓ • Android 10–17 Auto Flow"
                            } else app.packageName,
                            fontSize = 11.sp,
                            color = if (isChecked) (if (isLiteDetected) cyanAccent else BrandRose) else Color(0xFF94A3B8),
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            OverlayStateManager.toggleClearDataApp(app.copy(isLiteStorageApp = isLiteDetected), checked, isLiteDetected)
                            if (checked) {
                                Toast.makeText(
                                    context,
                                    "${app.appName} added to Clean Tab! " + if (isLiteDetected) "(FB Lite All-Language Flow ⚡)" else "",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "${app.appName} removed from Clean Tab", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = if (isLiteDetected) cyanAccent else BrandRose,
                            checkmarkColor = Color(0xFF04101E),
                            uncheckedColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    }

    if (appToRename != null) {
        val targetApp = appToRename!!
        AlertDialog(
            onDismissRequest = { appToRename = null },
            title = {
                Text("অ্যাপের নাম পরিবর্তন করুন", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("নতুন নাম লিখুন (${targetApp.packageName}):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        singleLine = true,
                        placeholder = { Text("যেমন: FB Fresh 1, FB Clone 2...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newNameInput.trim()
                        if (clean.isNotBlank()) {
                            OverlayStateManager.renameClearDataApp(targetApp.packageName, clean)
                            Toast.makeText(context, "✓ নাম পরিবর্তন হয়েছে: $clean", Toast.LENGTH_SHORT).show()
                            appToRename = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { appToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
