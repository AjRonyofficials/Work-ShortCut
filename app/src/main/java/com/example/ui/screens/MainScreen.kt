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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    VIRTUAL_NUMBERS("Virtual Numbers", Icons.Filled.Phone, Icons.Outlined.Phone),
    NAMES("Names", Icons.Filled.Person, Icons.Outlined.Person),
    SHORTCUTS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    PROXY("Proxy", Icons.Filled.Security, Icons.Outlined.Security),
    TWO_FACTOR("2FA", Icons.Filled.Lock, Icons.Outlined.Lock),
    PW_COPY("PW Copy", Icons.Filled.Key, Icons.Outlined.Key),
    EXCEL("Excel", Icons.Filled.TableChart, Icons.Outlined.TableChart),
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
    var selectedTab by remember { mutableStateOf(AppNavTab.VIRTUAL_NUMBERS) }
    var sectionsMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showBottomDeveloperCredit by remember { mutableStateOf(false) }
    var hasRunStartupFlow by rememberSaveable { mutableStateOf(false) }

    // Sequential startup flow:
    // Only shows subtle bottom developer credit for 3 seconds on fresh app launch
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasRunStartupFlow) {
            hasRunStartupFlow = true
            if (!state.isOverlayActive) {
                showBottomDeveloperCredit = true
                kotlinx.coroutines.delay(3000L)
                showBottomDeveloperCredit = false
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        OverlayStateManager.requestedAppTab.collect { tabName ->
            when (tabName) {
                "VIRTUAL_NUMBERS" -> selectedTab = AppNavTab.VIRTUAL_NUMBERS
                "PROXY" -> selectedTab = AppNavTab.PROXY
                "NAMES" -> selectedTab = AppNavTab.NAMES
                "EXCEL" -> selectedTab = AppNavTab.EXCEL
                "TWO_FACTOR" -> selectedTab = AppNavTab.TWO_FACTOR
                "APPS" -> selectedTab = AppNavTab.SHORTCUTS
                "CLEAR_DATA" -> selectedTab = AppNavTab.CLEAR_DATA
                "SETTINGS" -> selectedTab = AppNavTab.SETTINGS
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Work ShortCut",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = selectedTab.title,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
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

                        // 3-Dot Menu for All Sections ("Sob gula section a 3dot menute convert kore felo")
                        Box {
                            IconButton(
                                onClick = { sectionsMenuExpanded = true },
                                modifier = Modifier.testTag("appbar_3dot_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Sections Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = sectionsMenuExpanded,
                                onDismissRequest = { sectionsMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                AppNavTab.entries.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = tab.title,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 14.sp
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedTab = tab
                                            sectionsMenuExpanded = false
                                        },
                                        modifier = Modifier.testTag("menu_section_${tab.name}")
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                when (selectedTab) {
                    AppNavTab.VIRTUAL_NUMBERS -> VirtualNumbersSection()
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
