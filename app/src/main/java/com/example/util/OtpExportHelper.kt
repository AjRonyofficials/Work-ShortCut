package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportOtpItem(
    val phoneNumber: String,
    val country: String = "",
    val iso: String = "",
    val flag: String = "",
    val serviceOrRange: String = "",
    val otpCode: String,
    val otpMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val rate: Double = 0.50
)

object OtpExportHelper {

    fun exportOtps(
        context: Context,
        panelName: String,
        items: List<ExportOtpItem>
    ) {
        if (items.isEmpty()) {
            Toast.makeText(context, "ডাউনলোড করার মতো কোনো সফল ওটিপি নম্বর নেই", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val fileDateSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val nowStr = sdf.format(Date())
        val totalEarnings = items.sumOf { it.rate }

        val sb = StringBuilder()
        sb.append("==================================================\n")
        sb.append("  ⚡ $panelName - SUCCESSFUL OTP EXPORT REPORT\n")
        sb.append("==================================================\n")
        sb.append("Export Date & Time: $nowStr\n")
        sb.append("Total Successful OTPs: ${items.size}\n")
        sb.append("Total Earned: ৳${String.format(Locale.US, "%.2f", totalEarnings)}\n")
        sb.append("--------------------------------------------------\n\n")

        items.forEachIndexed { index, it ->
            val dateFormatted = sdf.format(Date(it.timestamp))
            val isoPart = if (it.iso.isNotBlank()) " [${it.iso.uppercase()}]" else ""
            val flagPart = if (it.flag.isNotBlank()) "${it.flag} " else ""
            sb.append("#${index + 1} | Phone: ${it.phoneNumber}\n")
            sb.append("   Country: $flagPart${it.country}$isoPart\n")
            if (it.serviceOrRange.isNotBlank()) {
                sb.append("   Service / Range: ${it.serviceOrRange}\n")
            }
            sb.append("   OTP Code: ${it.otpCode}\n")
            if (!it.otpMessage.isNullOrBlank()) {
                sb.append("   Message: ${it.otpMessage}\n")
            }
            sb.append("   Received At: $dateFormatted\n")
            sb.append("   Rate: ৳${String.format(Locale.US, "%.2f", it.rate)}\n")
            sb.append("--------------------------------------------------\n")
        }

        val reportText = sb.toString()

        // 1. Copy to clipboard
        ClipboardHelper.copyToClipboard(context, reportText, "$panelName OTP Report")
        VibrationHelper.vibrateSuccess(context)

        // 2. Save file to app cache/downloads
        try {
            val exportDir = File(context.cacheDir, "otp_exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val fileName = "otp_export_${panelName.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${fileDateSdf.format(Date())}.txt"
            val file = File(exportDir, fileName)
            file.writeText(reportText)
        } catch (_: Exception) {}

        // 3. Launch System Share Intent
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "$panelName Successful OTP Report ($nowStr)")
                putExtra(Intent.EXTRA_TEXT, reportText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "$panelName ওটিপি রিপোর্ট সেভ / শেয়ার করুন"))
        } catch (e: Exception) {
            Toast.makeText(context, "রিপোর্ট ক্লিপবোর্ডে কপি হয়েছে!", Toast.LENGTH_SHORT).show()
        }

        Toast.makeText(
            context,
            "✓ ${items.size} টি সফল OTP ডাউনলোড ও ক্লিপবোর্ডে কপি হয়েছে!",
            Toast.LENGTH_LONG
        ).show()
    }
}
