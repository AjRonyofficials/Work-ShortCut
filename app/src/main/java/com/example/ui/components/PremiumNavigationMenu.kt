package com.example.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.VibrationHelper

enum class AppNavTab(
    val title: String,
    val subtitle: String,
    val category: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val gradientColors: List<Color>,
    val accentColor: Color
) {
    VIRTUAL_NUMBERS(
        title = "Virtual Numbers",
        subtitle = "Live OTP & Console",
        category = "COMMUNICATION",
        selectedIcon = Icons.Filled.Phone,
        unselectedIcon = Icons.Outlined.Phone,
        gradientColors = listOf(Color(0xFF00B0FF), Color(0xFF0066FF)),
        accentColor = Color(0xFF00B0FF)
    ),
    PROXY(
        title = "Proxy Switcher",
        subtitle = "IP Rotation & Ping",
        category = "NETWORK",
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
        gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        accentColor = Color(0xFFA78BFA)
    ),
    TWO_FACTOR(
        title = "2FA Authenticator",
        subtitle = "TOTP 6-Digit Codes",
        category = "SECURITY",
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock,
        gradientColors = listOf(Color(0xFF10B981), Color(0xFF047857)),
        accentColor = Color(0xFF34D399)
    ),
    PW_COPY(
        title = "Fast Password",
        subtitle = "Quick Copy & Sync",
        category = "SECURITY",
        selectedIcon = Icons.Filled.Key,
        unselectedIcon = Icons.Outlined.Key,
        gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
        accentColor = Color(0xFFFBBF24)
    ),
    EXCEL(
        title = "Excel Workstation",
        subtitle = "Row by Row Auto-fill",
        category = "PRODUCTIVITY",
        selectedIcon = Icons.Filled.TableChart,
        unselectedIcon = Icons.Outlined.TableChart,
        gradientColors = listOf(Color(0xFF22C55E), Color(0xFF15803D)),
        accentColor = Color(0xFF4ADE80)
    ),
    NAMES(
        title = "Name Generator",
        subtitle = "Bangla & English IDs",
        category = "PRODUCTIVITY",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        gradientColors = listOf(Color(0xFFEC4899), Color(0xFFBE185D)),
        accentColor = Color(0xFFF472B6)
    ),
    SHORTCUTS(
        title = "Apps & Shortcuts",
        subtitle = "Instant Multi-Launch",
        category = "SYSTEM",
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
        accentColor = Color(0xFF60A5FA)
    ),
    CLEAR_DATA(
        title = "System Cleaner",
        subtitle = "1-Tap Cache Reset",
        category = "SYSTEM",
        selectedIcon = Icons.Filled.CleaningServices,
        unselectedIcon = Icons.Outlined.CleaningServices,
        gradientColors = listOf(Color(0xFFEF4444), Color(0xFFB91C1C)),
        accentColor = Color(0xFFF87171)
    ),
    SETTINGS(
        title = "Settings & Overlay",
        subtitle = "Bubble & Preferences",
        category = "SETTINGS",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        gradientColors = listOf(Color(0xFF64748B), Color(0xFF334155)),
        accentColor = Color(0xFF94A3B8)
    ),
    ADMIN_PANEL(
        title = "Admin Panel",
        subtitle = "Premium Numbers & User Accounts",
        category = "ADMIN",
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
        gradientColors = listOf(Color(0xFF6366F1), Color(0xFF4338CA)),
        accentColor = Color(0xFF818CF8)
    )
}

/**
 * Modern Website & App-Style Mega Navigation Menu
 * Features luxury acrylic glass aesthetics, command search, category chips,
 * glowing gradient squircle icons, active neon indicators, and tactile haptic response.
 */
@Composable
fun PremiumNavigationMenu(
    isOpen: Boolean,
    currentTab: AppNavTab,
    onSelectTab: (AppNavTab) -> Unit,
    onDismiss: () -> Unit,
    isOverlayActive: Boolean,
    onToggleOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = isOpen) {
        onDismiss()
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(220)) +
                slideInVertically(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                ) { it / 2 },
        exit = fadeOut(animationSpec = tween(180)) +
                slideOutVertically(animationSpec = tween(180)) { it / 2 },
        modifier = modifier.fillMaxSize()
    ) {
        val context = LocalContext.current
        var searchQuery by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("ALL") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Glass Modal Container
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color(0xFF1E3A56).copy(alpha = 0.2f)
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {} // Intercept clicks inside card
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0F1B2C),
                                    Color(0xFF09111C),
                                    Color(0xFF050B12)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 38.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF334A66))
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Header with Branding & Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "WORKSPACE MODULES",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            letterSpacing = 0.6.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "9 LIVE",
                                                color = Color(0xFF00E676),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "যেকোনো সেকশনে সরাসরি যাওয়ার জন্য ট্যাপ করুন",
                                        color = Color(0xFF90A4AE),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Close Button with Frosted Ring
                            Surface(
                                color = Color(0xFF132234),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, Color(0xFF1E3A56)),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { onDismiss() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Menu",
                                        tint = Color(0xFFB0BEC5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Modern Search Bar (Command Palette style)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "মডিউল খুঁজুন (যেমন otp, proxy, 2fa, excel)...",
                                    color = Color(0xFF546E7A),
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF81D4FA),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color(0xFF1A334E),
                                focusedContainerColor = Color(0xFF0A131E),
                                unfocusedContainerColor = Color(0xFF0A131E)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category Chips Filter
                        val categories = listOf("ALL", "COMMUNICATION", "SECURITY", "PRODUCTIVITY", "SYSTEM")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    color = if (isSelected) Color(0xFF0091EA) else Color(0xFF0D1825),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF40C4FF) else Color(0xFF19324B)
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedCategory = cat
                                    }
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else Color(0xFF90A4AE),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Filtered Tabs
                        val filteredTabs = AppNavTab.entries.filter { tab ->
                            val matchSearch = searchQuery.isBlank() ||
                                    tab.title.contains(searchQuery, ignoreCase = true) ||
                                    tab.subtitle.contains(searchQuery, ignoreCase = true) ||
                                    tab.category.contains(searchQuery, ignoreCase = true)

                            val matchCat = selectedCategory == "ALL" || tab.category == selectedCategory
                            matchSearch && matchCat
                        }

                        // 2-Column Responsive Bento Grid of Modules
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredTabs, key = { it.name }) { tab ->
                                val isSelected = currentTab == tab
                                ModuleCard(
                                    tab = tab,
                                    isSelected = isSelected,
                                    onClick = {
                                        VibrationHelper.vibrateClick(context)
                                        onSelectTab(tab)
                                        onDismiss()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFF162B3F), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Footer Quick Action: Floating Edge Overlay Toggle Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isOverlayActive) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF101C2A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isOverlayActive) Color(0xFF00E676) else Color(0xFF1E3A56)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        VibrationHelper.vibrateClick(context)
                                        onToggleOverlay()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = if (isOverlayActive) Color(0xFF00E676) else Color(0xFF81D4FA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isOverlayActive) "Floating Bubble: ON" else "Floating Bubble: OFF",
                                        color = if (isOverlayActive) Color(0xFF00E676) else Color(0xFFB0BEC5),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                color = Color(0xFF102030),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF1F3D5C)),
                                modifier = Modifier.clickable { onDismiss() }
                            ) {
                                Text(
                                    text = "Done",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    tab: AppNavTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) tab.accentColor.copy(alpha = 0.12f) else Color(0xFF0C1623),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.2.dp,
            if (isSelected) tab.accentColor else Color(0xFF182D42)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vibrant Squircle Icon Box
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Brush.linearGradient(tab.gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tab.selectedIcon,
                        contentDescription = tab.title,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (isSelected) {
                    Surface(
                        color = tab.accentColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = Color.Black,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF334A66),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tab.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = tab.subtitle,
                color = Color(0xFF8193A5),
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
