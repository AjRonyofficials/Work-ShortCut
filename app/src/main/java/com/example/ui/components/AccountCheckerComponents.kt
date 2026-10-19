package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AccountCheckerManager

private val OldRedColor = Color(0xFFFF5252)
private val OldRedBg = Color(0xFF3B1219)
private val FreshGreenColor = Color(0xFF00E676)
private val FreshGreenBg = Color(0xFF0A2E1F)
private val CyanAccent = Color(0xFF38BDF8)

/**
 * Prominent RED (OLD - Account Exists) or GREEN (NEW/FRESH - No Account) Highlight Badge
 * shown on every provisioned number in the Virtual Numbers section.
 */
@Composable
fun AccountStatusHighlightBadge(
    accountStatus: String,
    onRecheckClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isOld = accountStatus.equals("OLD", ignoreCase = true)
    val isFresh = accountStatus.equals("FRESH", ignoreCase = true) || accountStatus.equals("NEW", ignoreCase = true)
    val isChecking = accountStatus.equals("CHECKING", ignoreCase = true)

    val bgColor = when {
        isOld -> OldRedBg
        isFresh -> FreshGreenBg
        isChecking -> Color(0xFF2D2308)
        else -> Color(0xFF152232)
    }

    val borderColor = when {
        isOld -> OldRedColor
        isFresh -> FreshGreenColor
        isChecking -> Color(0xFFFFD600)
        else -> Color(0xFF334155)
    }

    val textColor = when {
        isOld -> OldRedColor
        isFresh -> FreshGreenColor
        isChecking -> Color(0xFFFFD600)
        else -> Color(0xFF94A3B8)
    }

    val labelText = when {
        isOld -> "🔴 OLD (ACCOUNT)"
        isFresh -> "🟢 NEW (FRESH)"
        isChecking -> "⏳ CHECKING..."
        else -> "🔍 CHECK AC"
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .then(
                if (onRecheckClick != null) {
                    Modifier.clickable { onRecheckClick() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = Color(0xFFFFD600),
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = labelText,
                color = textColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Compact bar for VirtualNumbersSection header so the user can see automatic checker status.
 * Numbers with accounts are automatically marked RED (OLD), and numbers with no account are marked GREEN (FRESH).
 */
@Composable
fun VirtualNumberAccountCheckerBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AccountCheckerManager.init(context)
    }
    val checkerState by AccountCheckerManager.state.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2B)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, FreshGreenColor.copy(alpha = 0.65f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(FreshGreenColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACCOUNT CHECKER (AUTO):",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = FreshGreenColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, FreshGreenColor)
                    ) {
                        Text(
                            text = "AUTOMATIC",
                            color = FreshGreenColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF0A121C),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E344B))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🟢 Fresh: ${checkerState.freshFoundCount}",
                                color = FreshGreenColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🔴 Account: ${checkerState.oldFoundCount}",
                                color = OldRedColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "অটোমেটিক চেক সক্রিয়: নম্বর এলে একাউন্ট থাকলে 🔴 RED এবং ফ্রেশ/নতুন হলে 🟢 GREEN দেখাবে।",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )

            AnimatedVisibility(visible = checkerState.isCheckingInBackground && !checkerState.retryStatusMessage.isNullOrEmpty()) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF06283D),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyanAccent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = CyanAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = checkerState.retryStatusMessage ?: "Checking number...",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
