package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * High-Performance System App Icon Loader:
 * Extracts real application icons directly from Android's PackageManager,
 * caches bitmaps in memory for instant reuse, and falls back gracefully to
 * stylized gradient badges if uninstalled or custom cloned package.
 */
object AppIconCache {
    val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    fun getOrLoad(context: Context, packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        iconCache[packageName]?.let { return it }

        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bmp = drawable.toBitmap(
                width = 96,
                height = 96,
                config = Bitmap.Config.ARGB_8888
            )
            val imgBitmap = bmp.asImageBitmap()
            iconCache[packageName] = imgBitmap
            imgBitmap
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
    fallbackText: String = "",
    size: Dp = 36.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) {
        mutableStateOf(AppIconCache.iconCache[packageName])
    }

    LaunchedEffect(packageName) {
        if (bitmap == null && packageName.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val loaded = AppIconCache.getOrLoad(context, packageName)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    bitmap = loaded
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = fallbackText.ifBlank { packageName },
            modifier = modifier
                .size(size)
                .clip(shape)
        )
    } else {
        // Fallback Stylized Initials Badge with Cyber Depth
        val initial = (fallbackText.ifBlank { packageName }).trim().take(2).uppercase()
        val bgGradient = remember(packageName) {
            val hash = packageName.hashCode()
            val colors = when (kotlin.math.abs(hash) % 5) {
                0 -> listOf(Color(0xFF1877F2), Color(0xFF0D47A1)) // Blue / FB
                1 -> listOf(Color(0xFFE1306C), Color(0xFF833AB4)) // Purple / IG
                2 -> listOf(Color(0xFF25D366), Color(0xFF128C7E)) // Green / WA
                3 -> listOf(Color(0xFF00E5FF), Color(0xFF0288D1)) // Cyan / Browser
                else -> listOf(Color(0xFFFF9100), Color(0xFFFF3D00)) // Amber / Tool
            }
            Brush.verticalGradient(colors)
        }

        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(bgGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial.ifBlank { "APP" },
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.38f).coerceAtLeast(9f).sp
            )
        }
    }
}
