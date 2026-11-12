package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.service.OtpHistoryManager
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

    private const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L

    /**
     * Collects and deduplicates strictly last-24h OTPs (after any manual panel reset)
     * from both the panel's active number list and 24h OTP history for that panel.
     */
    fun getMerged24hOtps(
        panelName: String,
        activeItems: List<ExportOtpItem>
    ): List<ExportOtpItem> {
        val now = System.currentTimeMillis()
        val cutoff24h = now - TWENTY_FOUR_HOURS_MS
        val resetTime = maxOf(
            OtpHistoryManager.getPanelLastResetTime(panelName),
            OtpHistoryManager.getPanelLastResetTime("ALL")
        )
        val effectiveCutoff = maxOf(cutoff24h, resetTime)

        val history24h = OtpHistoryManager.get24hOtpItemsForPanel(panelName)
        return (activeItems + history24h)
            .filter {
                it.otpCode.isNotBlank() &&
                it.phoneNumber.isNotBlank() &&
                it.timestamp >= effectiveCutoff &&
                (now - it.timestamp) <= TWENTY_FOUR_HOURS_MS
            }
            .distinctBy {
                val cleanNum = it.phoneNumber.replace("[^0-9]".toRegex(), "")
                "${cleanNum}_${it.otpCode.trim()}"
            }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Exports strictly last-24h OTPs with Phone Numbers for a specific panel (or all panels).
     */
    fun exportOtps(
        context: Context,
        panelName: String,
        items: List<ExportOtpItem>
    ) {
        val valid24hItems = getMerged24hOtps(panelName, items)

        if (valid24hItems.isEmpty()) {
            Toast.makeText(
                context,
                "গত ২৪ ঘণ্টায় ডাউনলোড করার মতো কোনো সফল OTP পাওয়া যায়নি",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val fileDateSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val sb = StringBuilder()
        valid24hItems.forEach { item ->
            val cleanPhone = item.phoneNumber.trim()
            val cleanOtp = item.otpCode.trim()
            if (cleanPhone.isNotBlank() && cleanOtp.isNotBlank()) {
                sb.append("$cleanPhone | $cleanOtp\n")
            }
        }

        val reportText = sb.toString().trimEnd()

        // 1. Copy to clipboard
        ClipboardHelper.copyToClipboard(context, reportText, "$panelName OTPs")
        VibrationHelper.vibrateSuccess(context)

        // 2. Save file to app cache
        try {
            val exportDir = File(context.cacheDir, "otp_exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val fileName = "otp_${panelName.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${fileDateSdf.format(Date())}.txt"
            val file = File(exportDir, fileName)
            file.writeText(reportText)
        } catch (_: Exception) {}

        // 3. Launch System Share / Save Intent
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "$panelName OTP ($nowStr)")
                putExtra(Intent.EXTRA_TEXT, reportText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "$panelName ওটিপি ডাউনলোড / সেভ করুন"))
        } catch (e: Exception) {
            Toast.makeText(context, "নম্বর ও ওটিপি ক্লিপবোর্ডে কপি হয়েছে!", Toast.LENGTH_SHORT).show()
        }

        Toast.makeText(
            context,
            "✓ মোট ${valid24hItems.size}টি নাম্বার ও OTP ডাউনলোড হয়েছে!",
            Toast.LENGTH_LONG
        ).show()
    }
}
