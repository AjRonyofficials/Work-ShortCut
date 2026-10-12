package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.service.AppUpdateInfo
import com.example.service.AppUpdateManager
import com.example.service.AppUpdateState
import java.util.Locale

@Composable
fun InAppUpdateDialog(
    state: AppUpdateState,
    isDismissed: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val info = when (state) {
        is AppUpdateState.UpdateAvailable -> state.info
        is AppUpdateState.Downloading -> null
        is AppUpdateState.ReadyToInstall -> state.info
        is AppUpdateState.Error -> null
        else -> null
    }

    val isVisible = !isDismissed && (
        state is AppUpdateState.UpdateAvailable ||
        state is AppUpdateState.Downloading ||
        state is AppUpdateState.ReadyToInstall ||
        state is AppUpdateState.Error
    )

    if (!isVisible) return

    val isForced = (state as? AppUpdateState.UpdateAvailable)?.info?.isForced == true ||
            (state as? AppUpdateState.ReadyToInstall)?.info?.isForced == true

    Dialog(
        onDismissRequest = {
            if (!isForced && state !is AppUpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isForced && state !is AppUpdateState.Downloading,
            dismissOnClickOutside = !isForced && state !is AppUpdateState.Downloading
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(Color(0xFF00C6FF), Color(0xFF0072FF), Color(0xFF00E676))
                )
            ),
            shadowElevation = 24.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rocket Glow Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0072FF).copy(alpha = 0.25f), Color(0xFF00E676).copy(alpha = 0.25f))
                            )
                        )
                        .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (state) {
                            is AppUpdateState.ReadyToInstall -> Icons.Default.InstallMobile
                            is AppUpdateState.Downloading -> Icons.Default.Download
                            is AppUpdateState.Error -> Icons.Default.Warning
                            else -> Icons.Default.RocketLaunch
                        },
                        contentDescription = null,
                        tint = if (state is AppUpdateState.Error) Color(0xFFFF5252) else Color(0xFF00E676),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = when (state) {
                        is AppUpdateState.Downloading -> "অ্যাপ আপডেট ডাউনলোড হচ্ছে..."
                        is AppUpdateState.ReadyToInstall -> "আপডেট প্রস্তুত! ইন্সটল করুন"
                        is AppUpdateState.Error -> "আপডেট সমস্যা হয়েছে"
                        else -> "নতুন আপডেট এসেছে! 🚀"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Version comparison pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Current: v${BuildConfig.VERSION_NAME}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (info != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("➔", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "New: v${info.versionName}",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Changelog Card
                if (info != null && info.changelog.isNotBlank()) {
                    Surface(
                        color = Color(0xFF1E293B).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "নতুন কি যুক্ত হয়েছে:",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.changelog,
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Progress Bar if Downloading
                if (state is AppUpdateState.Downloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val percent = (state.progress * 100).toInt()
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF00E676),
                            trackColor = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val downloadedMb = state.downloadedBytes.toDouble() / (1024 * 1024)
                            val totalMb = state.totalBytes.toDouble() / (1024 * 1024)
                            Text(
                                text = if (totalMb > 0) {
                                    String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb)
                                } else {
                                    String.format(Locale.US, "%.1f MB", downloadedMb)
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$percent%",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Error message if any
                if (state is AppUpdateState.Error) {
                    Surface(
                        color = Color(0xFFFF5252).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.message,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isForced && state !is AppUpdateState.Downloading) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text("পরে করব", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            when (state) {
                                is AppUpdateState.UpdateAvailable -> {
                                    AppUpdateManager.startDownload(context, state.info)
                                }
                                is AppUpdateState.ReadyToInstall -> {
                                    AppUpdateManager.installApk(context, state.apkFile)
                                }
                                is AppUpdateState.Error -> {
                                    AppUpdateManager.checkForUpdate(context, silent = false)
                                }
                                else -> {}
                            }
                        },
                        enabled = state !is AppUpdateState.Downloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            disabledContainerColor = Color(0xFF00E676).copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        if (state is AppUpdateState.Downloading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ডাউনলোড হচ্ছে...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else if (state is AppUpdateState.ReadyToInstall) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ইনস্টল করুন", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else if (state is AppUpdateState.Error) {
                            Text("আবার চেষ্টা করুন", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1-Click আপডেট", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
