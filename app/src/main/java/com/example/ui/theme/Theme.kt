package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.service.AppThemeMode

@Immutable
data class ResponsiveScreenMetrics(
    val screenWidthDp: Int = 390,
    val screenHeightDp: Int = 844,
    val scaleFactor: Float = 1.0f,
    val isCompactPhone: Boolean = false,
    val isLargePhoneOrTablet: Boolean = false
)

val LocalScreenMetrics = staticCompositionLocalOf { ResponsiveScreenMetrics() }

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF03101E),
    primaryContainer = Color(0xFF0C2740),
    onPrimaryContainer = CyberCyan,
    secondary = NeonEmerald,
    onSecondary = Color(0xFF012110),
    secondaryContainer = Color(0xFF07371C),
    onSecondaryContainer = NeonEmerald,
    tertiary = CyberIndigo,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkSurfaceBorder,
    error = AlertRed
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = BrandBlue,
    secondary = BrandTeal,
    onSecondary = Color.White,
    tertiary = BrandAmber,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightSurfaceBorder,
    error = AlertRed
)

private val EyeFriendlyColorScheme = darkColorScheme(
    primary = WarmPrimary,
    onPrimary = Color(0xFF451A03),
    primaryContainer = WarmSecondary,
    onPrimaryContainer = WarmTextPrimary,
    secondary = Color(0xFFFBBF24),
    onSecondary = Color.Black,
    tertiary = Color(0xFFE2E8F0),
    background = WarmBackground,
    onBackground = WarmTextPrimary,
    surface = WarmSurface,
    onSurface = WarmTextPrimary,
    surfaceVariant = WarmSurfaceElevated,
    onSurfaceVariant = WarmTextSecondary,
    outline = Color(0xFF44382C),
    error = Color(0xFFF87171)
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D25),
    onPrimaryContainer = AmoledPrimary,
    secondary = AmoledSecondary,
    onSecondary = Color.Black,
    tertiary = Color(0xFFFFD600),
    background = AmoledBackground,
    onBackground = AmoledTextPrimary,
    surface = AmoledSurface,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledSurfaceElevated,
    onSurfaceVariant = AmoledTextSecondary,
    outline = AmoledBorder,
    error = Color(0xFFFF1744)
)

@Composable
fun WorkShortcutTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.EYE_FRIENDLY -> EyeFriendlyColorScheme
        AppThemeMode.AMOLED -> AmoledColorScheme
    }

    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current

    // Auto-detect phone dimensions and calculate proportional UI scale factor
    // Reference baseline: 390dp x 820dp modern smartphone
    val (adaptiveDensity, screenMetrics) = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        baseDensity.density
    ) {
        val rawWidth = configuration.screenWidthDp.coerceAtLeast(280)
        val rawHeight = configuration.screenHeightDp.coerceAtLeast(480)
        val smallestWidth = minOf(rawWidth, rawHeight)

        // Never inflate density above 0.95f on phones; slightly compacting dp/sp gives every row generous horizontal breathing room
        val autoScale = when {
            smallestWidth < 340 -> (smallestWidth.toFloat() / 395f).coerceIn(0.80f, 0.88f)
            smallestWidth < 380 -> (smallestWidth.toFloat() / 400f).coerceIn(0.88f, 0.93f)
            smallestWidth < 420 -> 0.94f
            else -> 0.96f
        }

        // Slightly compact fontScale (0.93f) prevents system large-font settings or long country/badge labels from wrapping
        val normalizedFontScale = 0.93f

        val density = Density(
            density = baseDensity.density * autoScale,
            fontScale = normalizedFontScale
        )
        val metrics = ResponsiveScreenMetrics(
            screenWidthDp = rawWidth,
            screenHeightDp = rawHeight,
            scaleFactor = autoScale,
            isCompactPhone = smallestWidth < 360,
            isLargePhoneOrTablet = smallestWidth >= 420
        )
        density to metrics
    }

    CompositionLocalProvider(
        LocalDensity provides adaptiveDensity,
        LocalScreenMetrics provides screenMetrics
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Keep backwards compatible name for template
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    WorkShortcutTheme(content = content)
}
