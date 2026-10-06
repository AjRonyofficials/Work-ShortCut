package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.service.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = BrandSky,
    onPrimary = Color.Black,
    primaryContainer = BrandBlue,
    onPrimaryContainer = Color.White,
    secondary = BrandTeal,
    onSecondary = Color.Black,
    tertiary = BrandAmber,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Keep backwards compatible name for template
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    WorkShortcutTheme(content = content)
}
