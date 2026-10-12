package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class CopyOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val otpCode = intent?.getStringExtra("OTP_CODE") ?: return
        ClipboardHelper.copyToClipboard(context, otpCode, "OTP Code")
        Toast.makeText(context, "✓ OTP কপি হয়েছে: $otpCode", Toast.LENGTH_SHORT).show()
        VibrationHelper.vibrateSuccess(context)
    }
}

object OtpNotificationHelper {

    private const val CHANNEL_ID = "otp_heads_up_channel_v5"
    private const val CHANNEL_NAME = "OTP Incoming Alerts"

    // Deduplication cache: Prevents the same phone + OTP from firing multiple notifications
    private val recentlyNotifiedSignatures = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, Long>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 150
            }
        }
    )

    fun showOtpNotification(
        context: Context,
        phoneNumber: String,
        otpCode: String,
        fullMessage: String
    ) {
        val cleanOtp = otpCode.trim()
        if (cleanOtp.isBlank()) return

        // 0. Deduplication guard: ignore repeated calls for same number + OTP within 5 minutes
        val cleanPhone = phoneNumber.replace("[^0-9]".toRegex(), "")
        val signature = "${cleanPhone}_$cleanOtp"
        val now = System.currentTimeMillis()
        val lastNotified = recentlyNotifiedSignatures[signature]
        if (lastNotified != null && (now - lastNotified) < 300_000L) {
            return
        }
        recentlyNotifiedSignatures[signature] = now

        // 1. Immediate Auto-Copy to Clipboard
        ClipboardHelper.copyToClipboard(context, cleanOtp, "OTP Code")

        // 2. Immediate Toast & In-App Top Popup Banner (safely posted to Main Thread)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, "⚡ OTP কপি হয়েছে: $cleanOtp", Toast.LENGTH_SHORT).show()
                VibrationHelper.vibrateSuccess(context)
            } catch (_: Exception) {}
        }
        com.example.service.OverlayStateManager.showOtpPopup(phoneNumber, cleanOtp)

        // 3. System Heads-Up Notification Popup
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live Virtual Number OTP Incoming Alerts"
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // Tap notification to open main app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO_TAB", "VIRTUAL_NUMBERS")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            otpCode.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1-Tap Copy Action
        val copyIntent = Intent(context, CopyOtpReceiver::class.java).apply {
            putExtra("OTP_CODE", otpCode)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            (otpCode + "_copy").hashCode(),
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚡ OTP Received: $otpCode")
            .setContentText("$phoneNumber: $fullMessage")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Phone: $phoneNumber\nOTP: $otpCode\nMessage: $fullMessage")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_save,
                "COPY OTP ($otpCode)",
                copyPendingIntent
            )
            .build()

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    fun showWithdrawNotification(
        context: Context,
        title: String,
        message: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live Notifications & Alerts"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 50000).toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val notificationId = (System.currentTimeMillis() % 100000).toInt() + 1
        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }
}

