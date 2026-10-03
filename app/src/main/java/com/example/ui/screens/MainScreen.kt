package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.local.WorkShortcutRepository
import com.example.data.local.model.ExcelRowEntity
import com.example.data.local.model.ProxyProfileEntity
import com.example.data.local.model.TwoFactorKeyEntity
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.ui.overlay.FloatingOverlayWindowContent
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandGreen
import com.example.ui.theme.BrandSky
import kotlin.math.roundToInt

enum class AppNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SHORTCUTS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    PROXY("Proxy", Icons.Filled.Security, Icons.Outlined.Security),
    TWO_FACTOR("2FA", Icons.Filled.Lock, Icons.Outlined.Lock),
    PW_COPY("PW Copy", Icons.Filled.Key, Icons.Outlined.Key),
    EXCEL("Excel", Icons.Filled.TableChart, Icons.Outlined.TableChart),
    NAMES("Names", Icons.Filled.Person, Icons.Outlined.Person),
    CLEAR_DATA("Clean", Icons.Filled.CleaningServices, Icons.Outlined.CleaningServices),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: OverlayUiState,
    savedExcelRows: List<ExcelRowEntity>,
    savedProxies: List<ProxyProfileEntity>,
    savedTwoFactorKeys: List<TwoFactorKeyEntity>,
    repository: WorkShortcutRepository?,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(AppNavTab.NAMES) }
    val context = LocalContext.current
    var showBottomDeveloperCredit by remember { mutableStateOf(false) }
    var showDeveloperNoticeDialog by remember { mutableStateOf(false) }
    var noticeOkCountdown by remember { mutableStateOf(3) }
    var hasRunStartupFlow by rememberSaveable { mutableStateOf(false) }

    // Sequential startup flow:
    // Only runs on fresh app launch when overlay is NOT active
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasRunStartupFlow) {
            hasRunStartupFlow = true
            // If overlay is off and entering app fresh, trigger startup flow
            if (!state.isOverlayActive) {
                // 1. First show animated bottom developer credit for 3 seconds
                showBottomDeveloperCredit = true
                kotlinx.coroutines.delay(3000L)
                showBottomDeveloperCredit = false

                // Small delay for smooth exit animation before dialog opens
                kotlinx.coroutines.delay(300L)

                // 2. Then show the main notice popup
                showDeveloperNoticeDialog = true
            }
        }
    }

    // 3-second countdown before OK button becomes clickable in Notice Dialog
    androidx.compose.runtime.LaunchedEffect(showDeveloperNoticeDialog) {
        if (showDeveloperNoticeDialog) {
            noticeOkCountdown = 3
            while (noticeOkCountdown > 0) {
                kotlinx.coroutines.delay(1000L)
                noticeOkCountdown--
            }
        }
    }

    // Main Notice Dialog with Revised Text
    if (showDeveloperNoticeDialog) {
        val isOkActive = noticeOkCountdown == 0
        AlertDialog(
            onDismissRequest = {
                if (isOkActive) showDeveloperNoticeDialog = false
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BrandSky.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = BrandSky,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "📢 Developer Notice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "কোনো পরিবর্তন চাইলে ডেভলপারের সাথে যোগাযোগ করেন। কোনো সমস্যা বা আপডেটের জন্য 'Contact Developer' বাটনে ক্লিক করে সরাসরি যোগাযোগ করতে পারেন।",
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isOkActive) showDeveloperNoticeDialog = false
                    },
                    enabled = isOkActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOkActive) MaterialTheme.colorScheme.primary else Color.Gray
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isOkActive) "OK / ঠিক আছে" else "অপেক্ষা করুন (${noticeOkCountdown}s)",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        try {
                            val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ismailislamrony1")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(telegramIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Telegram: @ismailislamrony1", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BrandSky
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Contact Developer", color = BrandSky, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        OverlayStateManager.requestedAppTab.collect { tabName ->
            when (tabName) {
                "PROXY" -> selectedTab = AppNavTab.PROXY
                "NAMES" -> selectedTab = AppNavTab.NAMES
                "EXCEL" -> selectedTab = AppNavTab.EXCEL
                "TWO_FACTOR" -> selectedTab = AppNavTab.TWO_FACTOR
                "APPS" -> selectedTab = AppNavTab.SHORTCUTS
                "CLEAR_DATA" -> selectedTab = AppNavTab.CLEAR_DATA
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Work ShortCut",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        // Quick Toggle for Floating Bubble Overlay
                        IconButton(
                            onClick = {
                                OverlayStateManager.toggleOverlayExpanded()
                            },
                            modifier = Modifier.testTag("appbar_bubble_toggle")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.proxyState.isConnected) {
                                        Badge(containerColor = BrandGreen)
                                    } else if (state.draftRow.duplicateColumn != null) {
                                        Badge(containerColor = AlertRed)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Floating Bubble",
                                    tint = BrandSky
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    AppNavTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val badgeCount = when (tab) {
                            AppNavTab.EXCEL -> if (state.draftRow.duplicateColumn != null) "!" else null
                            AppNavTab.PROXY -> if (state.proxyState.isConnected) "ON" else null
                            else -> null
                        }

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                if (badgeCount != null) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = if (badgeCount == "!") AlertRed else BrandGreen
                                            ) {
                                                Text(badgeCount, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                }
                            },
                            label = { Text(tab.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name}")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                when (selectedTab) {
                    AppNavTab.SHORTCUTS -> AppShortcutsSection(state = state)
                    AppNavTab.NAMES -> NameGeneratorSection(state = state)
                    AppNavTab.EXCEL -> ExcelCollectorSection(
                        state = state,
                        savedRows = savedExcelRows,
                        repository = repository
                    )
                    AppNavTab.TWO_FACTOR -> TwoFactorSection(
                        state = state,
                        savedKeys = savedTwoFactorKeys,
                        repository = repository
                    )
                    AppNavTab.PW_COPY -> PwCopySection(state = state)
                    AppNavTab.PROXY -> ProxySection(
                        state = state,
                        savedProxies = savedProxies,
                        repository = repository
                    )
                    AppNavTab.CLEAR_DATA -> ClearDataSection(state = state)
                    AppNavTab.SETTINGS -> SettingsSection(state = state)
                }
            }
        }

        // Subtle Tiny Animated Developer Credit at Bottom of App (Matched to App UI)
        androidx.compose.animation.AnimatedVisibility(
            visible = showBottomDeveloperCredit,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(350)) +
                    androidx.compose.animation.slideInVertically(
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 380f)
                    ) { it } +
                    androidx.compose.animation.expandVertically(
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f)
                    ),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400)) +
                    androidx.compose.animation.slideOutVertically(animationSpec = androidx.compose.animation.core.tween(400)) { it } +
                    androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(400)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0B1220).copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF00C6FF), Color(0xFF0072FF), Color(0xFFAB47BC))
                    )
                ),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
                        )
                    }

                    Text(
                        text = "Developed By Ismail Islam Rony",
                        fontSize = 7.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}
