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

    private const val CHANNEL_ID = "otp_alerts_channel"
    private const val CHANNEL_NAME = "OTP Notifications"

    fun showOtpNotification(
        context: Context,
        phoneNumber: String,
        otpCode: String,
        fullMessage: String
    ) {
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
            }
            notificationManager.createNotificationChannel(channel)
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
}
