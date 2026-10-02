package com.example.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.util.ClipboardHelper

/**
 * Ultra-Premium, Glassmorphic Floating Overlay UI:
 * 1. Gorgeous frosted dark sapphire glass chassis dock with glowing cyber border.
 * 2. 3D tactile glossy buttons with icons, multi-stop depth gradients & specular highlights.
 * 3. In-place operations (no dragging into main app).
 * 4. Tap app to open, Press & Hold (Long-press) to instantly auto-close!
 * 5. Proxy country/IP/time pill appears strictly when proxy is actively connected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingOverlayWindowContent(
    state: OverlayUiState,
    onDragStart: (Float, Float) -> Unit = { _, _ -> },
    onDragDelta: (Float, Float) -> Unit = { _, _ -> },
    onToggleExpand: () -> Unit = {},
    onCloseOverlay: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLeft = state.isDockedLeft

    // Dock chassis shape (curved outer corners)
    val dockChassisShape = if (isLeft) {
        RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }

    // Individual button capsule shape
    val tabShape = RoundedCornerShape(10.dp)

    // Multi-stop 3D Depth Gradients for Glossy Tactile Buttons
    val gradProxy = Brush.verticalGradient(
        listOf(Color(0xFF40C4FF), Color(0xFF0091EA), Color(0xFF01579B))
    )
    val gradName = Brush.verticalGradient(
        listOf(Color(0xFF69F0AE), Color(0xFF00C853), Color(0xFF1B5E20))
    )
    val gradDual = Brush.verticalGradient(
        listOf(Color(0xFFFFB74D), Color(0xFFFF6D00), Color(0xFFE65100))
    )
    val gradFb = Brush.verticalGradient(
        listOf(Color(0xFF82B1FF), Color(0xFF1E88E5), Color(0xFF0D47A1))
    )
    val gradColC = Brush.verticalGradient(
        listOf(Color(0xFFEA80FC), Color(0xFFAA00FF), Color(0xFF4A148C))
    )
    val grad2Fa = Brush.verticalGradient(
        listOf(Color(0xFFFF5252), Color(0xFFD50000), Color(0xFFB71C1C))
    )
    val gradClean = Brush.verticalGradient(
        listOf(Color(0xFF18FFFF), Color(0xFF00B8D4), Color(0xFF006064))
    )
    val gradSwitch = Brush.verticalGradient(
        listOf(Color(0xFF78909C), Color(0xFF37474F), Color(0xFF212121))
    )
    val gradClose = Brush.verticalGradient(
        listOf(Color(0xFFFF5252), Color(0xFFC62828), Color(0xFF880E4F))
    )

    Box(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onDragStart(offset.x, offset.y) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .testTag("floating_overlay_root")
    ) {
        if (state.isEdgeBarMinimized) {
            // Main Floating Bubble with Proxy Info directly underneath (Messenger style!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF40C4FF), Color(0xFF1E88E5), Color(0xFF0D47A1))
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(Color(0xFF80D8FF), Color(0xFF0091EA))
                            ),
                            CircleShape
                        )
                        .shadow(10.dp, CircleShape)
                        .combinedClickable(
                            onClick = { OverlayStateManager.toggleEdgeBarMinimized() },
                            onLongClick = { OverlayStateManager.toggleDockSide() }
                        )
                        .testTag("floating_main_bubble")
                ) {
                    // Inner glowing core
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF2979FF), Color(0xFF1565C0))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Work Shortcut",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Proxy status: ONLY shows when proxy is actively connected!
                if (state.proxyState.isConnected) {
                    ProxyInfoStatusPill(state = state)
                }
            }
        } else {
            // Elegant Frosted Dark Glass Dock Chassis / Container
            Surface(
                shape = dockChassisShape,
                color = Color.Transparent,
                shadowElevation = 14.dp,
                modifier = Modifier
                    .wrapContentSize()
                    .clip(dockChassisShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF20F1626),
                                Color(0xEB131B2E),
                                Color(0xF20B101C)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color(0x9900E5FF),
                                Color(0x442979FF),
                                Color(0x6600E5FF)
                            )
                        ),
                        dockChassisShape
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(min = 124.dp, max = 138.dp)
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("floating_edge_tabs_column")
                ) {
                    // Sleek Drag Grip Header Handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // 1. PROXY TAB (Original Rectangular Tactile Button)
                    GlossyTactileButton(
                        title = if (state.proxyState.isConnected) "Proxy ✓" else "Proxy",
                        icon = Icons.Default.Bolt,
                        brush = gradProxy,
                        shape = tabShape,
                        onClick = {
                            OverlayStateManager.toggleProxyConnection(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "tab_proxy"
                    )

                    // 2. NAME GENERATOR TAB (Original Rectangular Tactile Button)
                    GlossyTactileButton(
                        title = "Name",
                        icon = Icons.Default.Person,
                        brush = gradName,
                        shape = tabShape,
                        onClick = {
                            OverlayStateManager.generateAndCopyRealtimeName(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "tab_name"
                    )

                    // 3. EXCEL COLUMNS IN 3-COLUMN CIRCULAR GRID ("gol boler moto")
                    // Supports up to 6 columns: Row 1 has A, B, C; Row 2 has D, E, F
                    val rowA = state.columnRowMap["A"] ?: 1
                    val rowB = state.columnRowMap["B"] ?: 1
                    val rowC = state.columnRowMap["C"] ?: 1
                    val rowD = state.columnRowMap["D"] ?: 1
                    val rowE = state.columnRowMap["E"] ?: 1
                    val rowF = state.columnRowMap["F"] ?: 1

                    // Grid Row 1: Columns A, B, C
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SheetCircularButton(
                            title = "A$rowA",
                            onClick = {
                                OverlayStateManager.fastPasteToSheetColumn(context, "A")
                            },
                            testTag = "tab_col_a"
                        )
                        SheetCircularButton(
                            title = "B$rowB",
                            onClick = {
                                OverlayStateManager.fastPasteToSheetColumn(context, "B")
                            },
                            testTag = "tab_col_b"
                        )
                        if (state.columnCount >= 3) {
                            SheetCircularButton(
                                title = "C$rowC",
                                onClick = {
                                    OverlayStateManager.fastPasteToSheetColumn(context, "C")
                                },
                                testTag = "tab_col_c"
                            )
                        }
                    }

                    // Grid Row 2: Columns D, E, F (if 4+ columns selected)
                    if (state.columnCount >= 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SheetCircularButton(
                                title = "D$rowD",
                                onClick = {
                                    OverlayStateManager.fastPasteToSheetColumn(context, "D")
                                },
                                testTag = "tab_col_d"
                            )
                            if (state.columnCount >= 5) {
                                SheetCircularButton(
                                    title = "E$rowE",
                                    onClick = {
                                        OverlayStateManager.fastPasteToSheetColumn(context, "E")
                                    },
                                    testTag = "tab_col_e"
                                )
                            }
                            if (state.columnCount >= 6) {
                                SheetCircularButton(
                                    title = "F$rowF",
                                    onClick = {
                                        OverlayStateManager.fastPasteToSheetColumn(context, "F")
                                    },
                                    testTag = "tab_col_f"
                                )
                            }
                        }
                    }

                    // 4. 2FA TAB (Displays '2FA' + code + timer cleanly on one line)
                    val totpFormatted = state.totpResult?.formattedCode
                    val totpSec = state.totpResult?.remainingSeconds
                    val is2FaActive = !totpFormatted.isNullOrEmpty() && totpSec != null
                    val title2Fa = if (is2FaActive) {
                        "2FA ${totpFormatted!!.replace(" ", "")} (${totpSec}s)"
                    } else {
                        "2FA"
                    }
                    GlossyTactileButton(
                        title = title2Fa,
                        icon = Icons.Default.Lock,
                        brush = grad2Fa,
                        shape = tabShape,
                        fontSize = if (is2FaActive) 9.2.sp else 12.sp,
                        horizontalPadding = if (is2FaActive) 4.dp else 9.dp,
                        onClick = {
                            OverlayStateManager.triggerOverlay2FaPaste(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "tab_2fa"
                    )

                    // 5. PW COPY TAB (User requirement: Place PW Copy directly under 2FA)
                    val gradPwCopy = Brush.verticalGradient(
                        listOf(Color(0xFFAB47BC), Color(0xFF7B1FA2), Color(0xFF4A148C))
                    )
                    GlossyTactileButton(
                        title = "PW Copy",
                        icon = Icons.Default.Key,
                        brush = gradPwCopy,
                        shape = tabShape,
                        fontSize = 12.sp,
                        horizontalPadding = 8.dp,
                        onClick = {
                            OverlayStateManager.copySavedPasswordToClipboard(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "tab_pw_copy"
                    )

                    // 5. CUSTOM USER APPS (Via, Dual, FB, Multiple Space)
                    if (state.customAppShortcuts.isNotEmpty()) {
                        if (state.customAppShortcuts.size > 1) {
                            AppShortcutsGridBox(
                                shortcuts = state.customAppShortcuts,
                                context = context,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val shortcut = state.customAppShortcuts[0]
                            val baseColor = try {
                                Color(android.graphics.Color.parseColor(shortcut.colorHex))
                            } catch (_: Exception) {
                                Color(0xFF00ACC1)
                            }
                            val customBrush = Brush.verticalGradient(
                                listOf(
                                    baseColor.copy(alpha = 0.9f),
                                    baseColor,
                                    Color(0xFF102027)
                                )
                            )
                            GlossyTactileButton(
                                title = shortcut.appName,
                                iconLabel = "🚀",
                                brush = customBrush,
                                shape = tabShape,
                                onClick = {
                                    OverlayStateManager.launchAppShortcut(context, shortcut)
                                },
                                onLongClick = {
                                    OverlayStateManager.closeAppShortcut(context, shortcut)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "tab_custom_${shortcut.appName}"
                            )
                        }
                    }

                    // 6. CLEAR DATA / CLEAN TAB (Original Rectangular Tactile Button)
                    if (state.selectedClearDataApps.isNotEmpty()) {
                        if (state.selectedClearDataApps.size > 1) {
                            ClearDataGridBox(
                                apps = state.selectedClearDataApps,
                                brush = gradClean,
                                context = context,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val appItem = state.selectedClearDataApps[0]
                            GlossyTactileButton(
                                title = appItem.appName.take(10),
                                iconLabel = "🧹",
                                brush = gradClean,
                                shape = tabShape,
                                onClick = {
                                    OverlayStateManager.executeClearDataForApp(context, appItem)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "tab_app_clean_${appItem.packageName}"
                            )
                        }
                    } else {
                        GlossyTactileButton(
                            title = "Clean",
                            iconLabel = "🧹",
                            brush = gradClean,
                            shape = tabShape,
                            onClick = {
                                OverlayStateManager.executeSelfClearData(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "tab_clean"
                        )
                    }

                    // 7. DOCK SIDE SWITCHER (⇄) & CLOSE BUTTON (✕) SIDE BY SIDE IN 2 COLUMNS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GlossyTactileButton(
                            title = "⇄",
                            icon = Icons.Default.SwapHoriz,
                            brush = gradSwitch,
                            shape = tabShape,
                            onClick = {
                                OverlayStateManager.toggleDockSide()
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "tab_switch_side"
                        )

                        GlossyTactileButton(
                            title = "✕",
                            icon = Icons.Default.Close,
                            brush = gradClose,
                            shape = tabShape,
                            onClick = {
                                OverlayStateManager.toggleEdgeBarMinimized()
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "tab_close"
                        )
                    }

                    // Proxy status: country, IP & connection duration under tabs ONLY when connected!
                    if (state.proxyState.isConnected) {
                        Spacer(modifier = Modifier.height(3.dp))
                        ProxyInfoStatusPill(state = state)
                    }
                }
            }
        }
    }
}

/**
 * 3D Tactile Glossy Button with specular top shine, icons, neon border, and tactile depth.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlossyTactileButton(
    title: String,
    brush: Brush,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconLabel: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    horizontalPadding: androidx.compose.ui.unit.Dp = 9.dp,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .widthIn(min = 36.dp, max = 120.dp)
            .shadow(4.dp, shape = shape)
            .clip(shape)
            .background(brush)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .testTag(testTag)
    ) {
        // Specular Top Shine Overlay (Glass reflection)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            } else if (iconLabel != null) {
                Text(
                    text = iconLabel,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                letterSpacing = if (fontSize < 12.sp) (-0.3).sp else 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Micro status pill showing Proxy Country, IP, and live Connection Time.
 * Strictly shown ONLY when proxy is actively connected!
 */
@Composable
fun ProxyInfoStatusPill(state: OverlayUiState) {
    val proxy = state.proxyState
    if (!proxy.isConnected) return
    val context = androidx.compose.ui.platform.LocalContext.current

    val countryOpt = com.example.util.NameGenerator.getCountryOption(proxy.countryCode)
    val countryStr = "${countryOpt.flag} ${countryOpt.code}"
    val timeStr = OverlayStateManager.formatDuration(proxy.connectedDurationSeconds)
    val ipStr = proxy.ipAddress.ifEmpty { proxy.host }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF20A101D),
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF00E676)
        ),
        modifier = Modifier
            .testTag("overlay_proxy_status_pill")
            .clickable {
                OverlayStateManager.refreshRotatingIp(context)
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$countryStr • ${proxy.ipVersion} • $ipStr • $timeStr",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Ultra-Premium, Original Cosmic Obsidian & Jewel-Rimmed Circular Ball Button.
 * Distinctive, luxurious multi-layered sphere:
 * - Liquid Obsidian dark titanium spherical gradient core.
 * - Dynamic jewel-tone neon glowing rim uniquely accented per column (A: Cyan, B: Gold, C: Amethyst, D: Emerald, E: Coral, F: Sapphire).
 * - Specular curved crescent glass reflection at top.
 * - Matching vivid glowing typography for column names & active row indices (A1, B1, C1...).
 */
@Composable
fun SheetCircularButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    // Dynamic luxury theme per column letter
    val colKey = title.take(1).uppercase()
    val (accentGlow, rimBorder, textColor) = when (colKey) {
        "A" -> Triple(
            Color(0xFF00E5FF),
            Brush.sweepGradient(
                listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF80D8FF),
                    Color(0xFF0091EA),
                    Color(0xFF00E5FF)
                )
            ),
            Color(0xFF00F0FF) // Electric Cyber Cyan
        )
        "B" -> Triple(
            Color(0xFFFFD700),
            Brush.sweepGradient(
                listOf(
                    Color(0xFFFFD700),
                    Color(0xFFFFEA00),
                    Color(0xFFFF8F00),
                    Color(0xFFFFD700)
                )
            ),
            Color(0xFFFFD700) // Pure Solar Gold
        )
        "C" -> Triple(
            Color(0xFFE040FB),
            Brush.sweepGradient(
                listOf(
                    Color(0xFFE040FB),
                    Color(0xFFEA80FC),
                    Color(0xFFAA00FF),
                    Color(0xFFE040FB)
                )
            ),
            Color(0xFFF06292) // Luminous Rose Amethyst
        )
        "D" -> Triple(
            Color(0xFF00E676),
            Brush.sweepGradient(
                listOf(
                    Color(0xFF00E676),
                    Color(0xFFB9F6CA),
                    Color(0xFF00C853),
                    Color(0xFF00E676)
                )
            ),
            Color(0xFF00E676) // Radiant Cyber Emerald
        )
        "E" -> Triple(
            Color(0xFFFF5252),
            Brush.sweepGradient(
                listOf(
                    Color(0xFFFF5252),
                    Color(0xFFFF8A80),
                    Color(0xFFD50000),
                    Color(0xFFFF5252)
                )
            ),
            Color(0xFFFF5252) // Vivid Sunset Coral
        )
        "F" -> Triple(
            Color(0xFF448AFF),
            Brush.sweepGradient(
                listOf(
                    Color(0xFF448AFF),
                    Color(0xFF82B1FF),
                    Color(0xFF2979FF),
                    Color(0xFF448AFF)
                )
            ),
            Color(0xFF448AFF) // Royal Azure Sapphire
        )
        else -> Triple(
            Color(0xFF00E5FF),
            Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))),
            Color(0xFF00E5FF)
        )
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .shadow(5.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF263238),
                        Color(0xFF19222D),
                        Color(0xFF101720),
                        Color(0xFF0A0F16)
                    )
                )
            )
            .border(1.6.dp, rimBorder, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Specular top light reflex (Glass sphere reflection)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.72f)
                .height(11.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )

        // Subtle ambient inner glow from column accent
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(accentGlow.copy(alpha = 0.12f))
        )

        Text(
            text = title,
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = 13.5.sp,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Ultra-Premium Utility Circular Button for Row 3 of the 3x3 Grid (2FA, Name, Clean).
 * Matches the 38dp circular aesthetic with colored neon accents and specular glass sheen.
 */
@Composable
fun UtilityCircularButton(
    title: String,
    accentColor: Color,
    gradient: Brush,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconLabel: String? = null,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(gradient)
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.9f),
                        Color.White.copy(alpha = 0.8f),
                        accentColor.copy(alpha = 0.6f)
                    )
                ),
                CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Specular top highlight
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.7f)
                .height(11.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(2.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 8.5.sp,
                    lineHeight = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            } else if (iconLabel != null) {
                Text(
                    text = iconLabel,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 8.5.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 2-Column Serial Grid Box for Custom Apps (Via, Dual, FB, Lite, etc.)
 * Displays apps in a sleek cyber-bordered box with 2 columns, preventing vertical overflow.
 */
@Composable
fun AppShortcutsGridBox(
    shortcuts: List<com.example.service.CustomAppShortcut>,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE60A1324), Color(0xF20F1D33))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.75f),
                        Color(0xFF0288D1).copy(alpha = 0.35f)
                    )
                ),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("app_shortcuts_grid_box")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Micro Header Label with Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚀 APPS",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${shortcuts.size}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF80DEEA)
                )
            }

            // 2-Column Grid in Serial Order
            shortcuts.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CompactAppGridButton(
                        shortcut = pair[0],
                        modifier = Modifier.weight(1f),
                        onClick = { OverlayStateManager.launchAppShortcut(context, pair[0]) },
                        onLongClick = { OverlayStateManager.closeAppShortcut(context, pair[0]) }
                    )
                    if (pair.size > 1) {
                        CompactAppGridButton(
                            shortcut = pair[1],
                            modifier = Modifier.weight(1f),
                            onClick = { OverlayStateManager.launchAppShortcut(context, pair[1]) },
                            onLongClick = { OverlayStateManager.closeAppShortcut(context, pair[1]) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 2-Column Serial Grid Box for Selected Clear Data Apps (Lite, Lite 96, Lite F, etc.)
 * Displays clean apps in a sleek crimson-amber bordered box with 2 columns, preventing vertical overflow.
 */
@Composable
fun ClearDataGridBox(
    apps: List<com.example.util.AppInfoItem>,
    brush: Brush,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE6240A10), Color(0xF2330F19))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFF5252).copy(alpha = 0.75f),
                        Color(0xFFC2185B).copy(alpha = 0.35f)
                    )
                ),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("clear_data_grid_box")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Micro Header Label with Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧹 CLEAN",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF5252),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${apps.size}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF8A80)
                )
            }

            // 2-Column Grid in Serial Order
            apps.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    CompactCleanGridButton(
                        appItem = pair[0],
                        brush = brush,
                        modifier = Modifier.weight(1f),
                        onClick = { OverlayStateManager.executeClearDataForApp(context, pair[0]) }
                    )
                    if (pair.size > 1) {
                        CompactCleanGridButton(
                            appItem = pair[1],
                            brush = brush,
                            modifier = Modifier.weight(1f),
                            onClick = { OverlayStateManager.executeClearDataForApp(context, pair[1]) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Compact Tactile Button for 2-column App shortcut grid.
 * Tap = Open App, Long press = Zero-touch force close!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactAppGridButton(
    shortcut: com.example.service.CustomAppShortcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val baseColor = try {
        Color(android.graphics.Color.parseColor(shortcut.colorHex))
    } catch (_: Exception) {
        Color(0xFF00ACC1)
    }
    val brush = Brush.verticalGradient(
        listOf(
            baseColor.copy(alpha = 0.95f),
            baseColor,
            Color(0xFF0D1B2A)
        )
    )
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .height(31.dp)
            .shadow(3.dp, shape = shape)
            .clip(shape)
            .background(brush)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("tab_custom_${shortcut.appName}")
    ) {
        // Specular top highlight
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(13.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = shortcut.appName.take(7),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact Tactile Button for 2-column Clean App grid.
 * Tap = Zero-touch clear data & auto close!
 */
@Composable
fun CompactCleanGridButton(
    appItem: com.example.util.AppInfoItem,
    brush: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .height(31.dp)
            .shadow(3.dp, shape = shape)
            .clip(shape)
            .background(brush)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("tab_app_clean_${appItem.packageName}")
    ) {
        // Specular top highlight
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(13.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (appItem.isLiteStorageApp) "⚡" else "🧹",
                fontSize = 9.sp
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = appItem.appName.take(7),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

