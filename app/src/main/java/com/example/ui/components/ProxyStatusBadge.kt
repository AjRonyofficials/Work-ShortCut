package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BrandGreen

@Composable
fun ProxyStatusBadge(
    isConnected: Boolean,
    countryCode: String,
    pingMs: Long,
    ipAddress: String = "",
    protocol: String = "SOCKS5",
    modifier: Modifier = Modifier
) {
    val indicatorColor = if (isConnected) BrandGreen else AlertRed
    val bgColor = if (isConnected) BrandGreen.copy(alpha = 0.12f) else AlertRed.copy(alpha = 0.12f)
    val ipText = if (ipAddress.isNotEmpty()) " [$ipAddress]" else ""

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, indicatorColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isConnected) "Proxy $countryCode$ipText ($pingMs ms)" else "Proxy $countryCode$ipText (Off)",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
