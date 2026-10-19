package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandGreen

@Composable
fun TotpCountdownRing(
    remainingSeconds: Int,
    progress: Float,
    size: Dp = 40.dp,
    strokeWidth: Dp = 3.5.dp,
    modifier: Modifier = Modifier
) {
    val ringColor by animateColorAsState(
        targetValue = when {
            remainingSeconds <= 5 -> AlertRed
            remainingSeconds <= 10 -> BrandAmber
            else -> BrandGreen
        },
        label = "totp_ring_color"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "totp_ring_progress"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Background track
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(size),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            strokeWidth = strokeWidth
        )

        // Active timer track
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(size),
            color = ringColor,
            strokeWidth = strokeWidth
        )

        Text(
            text = "$remainingSeconds",
            color = ringColor,
            fontSize = if (size > 44.dp) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
