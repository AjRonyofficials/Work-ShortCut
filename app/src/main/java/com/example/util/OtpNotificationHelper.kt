package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object OtpNotificationHelper {

    private const val CHANNEL_ID_OTP = "channel_otp_alerts"
    private const val CHANNEL_NAME_OTP = "OTP & Verification Alerts"
    private const val CHANNEL_ID_WITHDRAW = "channel_withdraw_alerts"
    private const val CHANNEL_NAME_WITHDRAW = "Withdrawal Updates"

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            
            val otpChannel = NotificationChannel(
                CHANNEL_ID_OTP,
                CHANNEL_NAME_OTP,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for received OTP codes"
                enableVibration(true)
            }
            manager.createNotificationChannel(otpChannel)

            val withdrawChannel = NotificationChannel(
                CHANNEL_ID_WITHDRAW,
                CHANNEL_NAME_WITHDRAW,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for withdrawal requests and status"
            }
            manager.createNotificationChannel(withdrawChannel)
        }
    }

    fun showOtpNotification(
        context: Context,
        phoneNumber: String,
        otpCode: String,
        fullMessage: String
    ) {
        try {
            ensureChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_OTP)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("⚡ OTP Received: $otpCode")
                .setContentText("From $phoneNumber: $fullMessage")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Number: $phoneNumber\nOTP: $otpCode\n\n$fullMessage"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (_: Exception) {}
    }

    fun showWithdrawNotification(
        context: Context,
        title: String,
        message: String
    ) {
        try {
            ensureChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_WITHDRAW)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (_: Exception) {}
    }
}
