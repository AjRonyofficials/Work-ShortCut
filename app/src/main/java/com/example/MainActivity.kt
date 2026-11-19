package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.local.WorkShortcutRepository
import com.example.service.OverlayStateManager
import com.example.ui.screens.MainScreen
import com.example.ui.theme.WorkShortcutTheme
import com.example.util.AppSecurityShield
import com.example.util.BlockedModTool
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var packageWatchReceiver: BroadcastReceiver? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "✓ নোটিফিকেশন পারমিশন চালু হয়েছে! ওটিপি আসলে নোটিফিকেশনে পাবেন।", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            OverlayStateManager.startProxyConnection(this)
        } else {
            Toast.makeText(this, "ভিপিএন পারমিশন ছাড়া প্রক্সি ট্র্যাফিক রুট করা সম্ভব নয়!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkVpnPermissionRequest(intent: Intent?) {
        if (intent?.getBooleanExtra("REQUEST_VPN_PERMISSION", false) == true) {
            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                OverlayStateManager.startProxyConnection(this)
            }
        }
    }

    private fun registerPackageWatcher() {
        if (packageWatchReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val detected = AppSecurityShield.scanRiskyModTools(this@MainActivity)
                if (detected.isNotEmpty()) {
                    try {
                        stopService(Intent(this@MainActivity, com.example.service.FloatingOverlayService::class.java))
                    } catch (_: Throwable) {}
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
            packageWatchReceiver = receiver
        } catch (_: Throwable) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("NAVIGATE_TO_SHEET", false)) {
            OverlayStateManager.requestTab("EXCEL")
        }
        checkVpnPermissionRequest(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSecurityShield.init(this)
        if (AppSecurityShield.isTampered()) {
            AppSecurityShield.forceCrashNow("ERR_ONCREATE_TAMPER")
        }
        enableEdgeToEdge()
        registerPackageWatcher()

        val initialBlocked = AppSecurityShield.scanRiskyModTools(this)

        if (!AppSecurityShield.isTampered()) {
            try { OverlayStateManager.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.UnixSmsManager.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.VirtualNumberManager.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.KopEngine2Manager.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.PremiumNumberV2Manager.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.AuthManager.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.WithdrawalManager.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.OtpHistoryManager.init(this) } catch (e: Throwable) { e.printStackTrace() }
        }
        val database = AppDatabase.getDatabase(this)
        val repository = WorkShortcutRepository(
            excelRowDao = database.excelRowDao(),
            proxyProfileDao = database.proxyProfileDao(),
            twoFactorDao = database.twoFactorDao()
        )

        if (!AppSecurityShield.isTampered() && initialBlocked.isEmpty()) {
            try {
                if (intent?.getBooleanExtra("NAVIGATE_TO_SHEET", false) == true) {
                    OverlayStateManager.requestTab("EXCEL")
                }
                checkVpnPermissionRequest(intent)
                checkNotificationPermission()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            try { com.example.util.AppMemoryOptimizer.autoOptimize(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.GlobalCloudSyncService.init(this) } catch (e: Throwable) { e.printStackTrace() }
            try { com.example.service.AppUpdateManager.init(this) } catch (e: Throwable) { e.printStackTrace() }
        }

        setContent {
            val tamperReason by AppSecurityShield.tamperReason.collectAsStateWithLifecycle()
            val blockedTools by AppSecurityShield.blockedModTools.collectAsStateWithLifecycle()
            val state by OverlayStateManager.uiState.collectAsStateWithLifecycle()
            val isLoggedIn by com.example.service.AuthManager.isLoggedIn.collectAsStateWithLifecycle()
            val savedExcelRows by repository.allExcelRows.collectAsStateWithLifecycle(initialValue = emptyList())
            val savedProxies by repository.allProxies.collectAsStateWithLifecycle(initialValue = emptyList())
            val savedTwoFactorKeys by repository.allTwoFactorKeys.collectAsStateWithLifecycle(initialValue = emptyList())
            val updateState by com.example.service.AppUpdateManager.state.collectAsStateWithLifecycle()
            val isUpdateDismissed by com.example.service.AppUpdateManager.isDismissedForSession.collectAsStateWithLifecycle()

            // Continuous background monitor: re-checks every 4 seconds for newly installed risky modding/editing tools
            LaunchedEffect(Unit) {
                while (true) {
                    if (AppSecurityShield.isTampered()) {
                        AppSecurityShield.forceCrashNow("ERR_LIVE_TAMPER")
                    }
                    val detected = AppSecurityShield.scanRiskyModTools(this@MainActivity)
                    if (detected.isNotEmpty()) {
                        try {
                            stopService(Intent(this@MainActivity, com.example.service.FloatingOverlayService::class.java))
                        } catch (_: Throwable) {}
                    }
                    delay(4000L)
                }
            }

            WorkShortcutTheme(themeMode = state.appTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (tamperReason != null) {
                        LaunchedEffect(tamperReason) {
                            AppSecurityShield.forceCrashNow(tamperReason ?: "ERR_UI_TAMPER")
                        }
                    } else if (blockedTools.isNotEmpty()) {
                        BlockedModToolsWarningScreen(
                            blockedTools = blockedTools,
                            onRecheck = {
                                val remaining = AppSecurityShield.scanRiskyModTools(this@MainActivity)
                                if (remaining.isNotEmpty()) {
                                    val names = remaining.take(3).joinToString(", ") { it.displayName }
                                    Toast.makeText(
                                        this@MainActivity,
                                        "⚠️ এখনো আনইনস্টল করা হয়নি: $names",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "✓ নিরাপত্তা যাচাই সফল! অ্যাপ এখন ব্যবহার করতে পারবেন।",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    } else if (!isLoggedIn) {
                        com.example.ui.screens.LoginScreen(
                            onLoginSuccess = { /* Automatically refreshes state */ }
                        )
                    } else {
                        MainScreen(
                            state = state,
                            savedExcelRows = savedExcelRows,
                            savedProxies = savedProxies,
                            savedTwoFactorKeys = savedTwoFactorKeys,
                            repository = repository
                        )
                    }

                    if (blockedTools.isEmpty() && tamperReason == null) {
                        com.example.ui.components.InAppUpdateDialog(
                            state = updateState,
                            isDismissed = isUpdateDismissed,
                            onDismiss = { com.example.service.AppUpdateManager.dismissDialog() }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.example.util.AppLifecycleTracker.onActivityStarted()
    }

    override fun onResume() {
        super.onResume()
        AppSecurityShield.init(this)
        if (AppSecurityShield.isTampered()) {
            AppSecurityShield.forceCrashNow("ERR_RESUME_TAMPER")
        }
        val detected = AppSecurityShield.scanRiskyModTools(this)
        if (detected.isNotEmpty()) {
            try {
                stopService(Intent(this, com.example.service.FloatingOverlayService::class.java))
            } catch (_: Throwable) {}
            return
        }
        com.example.util.AppLifecycleTracker.onActivityStarted()
        try { com.example.service.VirtualNumberManager.init(this); com.example.service.VirtualNumberManager.fetchActiveRanges() } catch (_: Throwable) {}
        try { com.example.service.KopEngine2Manager.init(this); com.example.service.KopEngine2Manager.fetchActiveRanges() } catch (_: Throwable) {}
        try { com.example.service.PremiumNumberV2Manager.init(this); com.example.service.PremiumNumberV2Manager.refreshLiveConsole() } catch (_: Throwable) {}
        try {
            if (OverlayStateManager.isOverlayPersistentEnabled() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Settings.canDrawOverlays(this)
            ) {
                com.example.service.FloatingOverlayService.startService(this)
            }
        } catch (_: Throwable) {}
    }

    override fun onStop() {
        super.onStop()
        com.example.util.AppLifecycleTracker.onActivityStopped()
    }

    override fun onDestroy() {
        packageWatchReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Throwable) {}
        }
        packageWatchReceiver = null
        super.onDestroy()
    }
}

@Composable
private fun BlockedModToolsWarningScreen(
    blockedTools: List<BlockedModTool>,
    onRecheck: () -> Unit
) {
    val context = LocalContext.current
    var alertPulseCount by remember { mutableIntStateOf(1) }

    // Repeated periodic warning toast while any risky APK editor/modder/sniffer remains installed
    LaunchedEffect(blockedTools) {
        while (blockedTools.isNotEmpty()) {
            val names = blockedTools.take(3).joinToString(", ") { it.displayName }
            Toast.makeText(
                context,
                "⚠️ নিরাপত্তা সতর্কতা (#$alertPulseCount): $names আনইনস্টল করুন!",
                Toast.LENGTH_SHORT
            ).show()
            delay(7000L)
            alertPulseCount++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A060B), Color(0xFF0E0712), Color(0xFF070A12))
                )
            )
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Blocked",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "নিরাপত্তা সতর্কতা — অ্যাপ ব্লক করা হয়েছে!",
                color = Color(0xFFFF6B6B),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFFEF4444).copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "আপনার ফোনে ঝুঁকিপূর্ণ APK এডিটর / মডিং / স্নিফার অ্যাপ পাওয়া গেছে (${blockedTools.size}টি)। নিচের সবগুলো অ্যাপ আনইনস্টল না করা পর্যন্ত Work ShortCut চালু হবে না।",
                        color = Color(0xFFFDE8E8),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(blockedTools, key = { it.packageName }) { tool ->
                    Surface(
                        color = Color(0xFF161B28),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.displayName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tool.riskCategory,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = tool.packageName,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val uninstallIntent = Intent(
                                            Intent.ACTION_DELETE,
                                            Uri.parse("package:${tool.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(uninstallIntent)
                                    } catch (_: Throwable) {
                                        try {
                                            val settingsIntent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:${tool.packageName}")
                                            ).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(settingsIntent)
                                        } catch (_: Throwable) {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("uninstall_${tool.packageName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Uninstall",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Uninstall",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onRecheck,
                border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("security_recheck_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Re-check security",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "আনইনস্টল করেছি — পুনরায় চেক করুন",
                    color = Color(0xFF38BDF8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
