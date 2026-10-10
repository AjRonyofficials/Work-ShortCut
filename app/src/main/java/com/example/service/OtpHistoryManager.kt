package com.example.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class OtpHistoryRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val phoneNumber: String,
    val otpCode: String,
    val service: String, // e.g. Facebook, Instagram, WhatsApp, Zenex
    val platform: String, // "Unix SMS" or "Zenex"
    val rate: Double = 0.50, // Rate active at time of OTP
    val timestamp: Long = System.currentTimeMillis(),
    val dateKey: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
)

data class DailyOtpSummary(
    val dateKey: String, // "yyyy-MM-dd"
    val displayLabel: String, // "Today (08 Oct)", "Yesterday (07 Oct)", etc.
    val totalCount: Int,
    val unixCount: Int,
    val zenexCount: Int,
    val unixRateOnDay: Double,
    val records: List<OtpHistoryRecord>
)

data class OtpHistoryState(
    val totalOtpsAllTime: Int = 0,
    val unixTotalAllTime: Int = 0,
    val zenexTotalAllTime: Int = 0,
    val todayOtps: Int = 0,
    val unixTodayOtps: Int = 0,
    val zenexTodayOtps: Int = 0,
    val currentUnixRate: Double = 0.50,
    val currentZenexRate: Double = 0.50,
    val last7DaysSummaries: List<DailyOtpSummary> = emptyList(),
    val last30DaysSummaries: List<DailyOtpSummary> = emptyList(),
    val last7DaysOtps: Int = 0,
    val last30DaysOtps: Int = 0,
    val lastMonthOtps: Int = 0,
    val twoMonthsAgoOtps: Int = 0,
    val lastMonthLabel: String = "Previous Month",
    val twoMonthsAgoLabel: String = "2 Months Ago",
    val recentRecords: List<OtpHistoryRecord> = emptyList(),
    val resetCountdownSeconds: Long = 0L,
    val resetTargetEpoch: Long = 0L,
    val monthlyCycleStartEpoch: Long = 0L,
    val monthlyCycleTargetEpoch: Long = 0L
)

object OtpHistoryManager {

    private const val PREFS_NAME = "work_shortcut_otp_history"
    private const val KEY_RECORDS_JSON = "otp_records_json"
    private const val KEY_TOTAL_COUNT = "total_otp_all_time"
    private const val KEY_MONTHLY_CYCLE_START = "monthly_cycle_start_time"
    private const val KEY_CLEANED_DUMMY_V2 = "cleaned_dummy_records_v2"

    // 1 Month = 30 Days in Milliseconds
    private const val ONE_MONTH_MS = 30L * 24L * 60L * 60L * 1000L

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(OtpHistoryState())
    val state: StateFlow<OtpHistoryState> = _state.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("dd MMM", Locale.US)

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadHistory()
        }
    }

    private fun loadHistory() {
        val json = prefs?.getString(KEY_RECORDS_JSON, "[]") ?: "[]"
        val list = mutableListOf<OtpHistoryRecord>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ts = o.optLong("timestamp", System.currentTimeMillis())
                list.add(
                    OtpHistoryRecord(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        phoneNumber = o.optString("phoneNumber", ""),
                        otpCode = o.optString("otpCode", ""),
                        service = o.optString("service", "SMS"),
                        platform = o.optString("platform", "Unix SMS"),
                        rate = let {
                            val r = o.optDouble("rate", 0.50)
                            if (r < 0.1) 0.50 else r
                        },
                        timestamp = ts,
                        dateKey = o.optString("dateKey", dateFormat.format(Date(ts)))
                    )
                )
            }
        } catch (_: Exception) {}

        // One-time cleanup of legacy dummy sample records (+120255501...)
        val hasCleaned = prefs?.getBoolean(KEY_CLEANED_DUMMY_V2, false) ?: false
        val sanitizedList = if (!hasCleaned) {
            val filtered = list.filterNot { it.phoneNumber.startsWith("+120255501") || it.phoneNumber.startsWith("+2376991122") }
            prefs?.edit()
                ?.putBoolean(KEY_CLEANED_DUMMY_V2, true)
                ?.putInt(KEY_TOTAL_COUNT, filtered.size)
                ?.apply()
            saveRecords(filtered, filtered.size)
            filtered
        } else {
            list
        }

        val total = prefs?.getInt(KEY_TOTAL_COUNT, sanitizedList.size) ?: sanitizedList.size

        var cycleStart = prefs?.getLong(KEY_MONTHLY_CYCLE_START, 0L) ?: 0L
        val now = System.currentTimeMillis()
        if (cycleStart == 0L || now - cycleStart >= ONE_MONTH_MS) {
            // 1 Month cycle expired: reset records older than 30 days
            cycleStart = now
            prefs?.edit()?.putLong(KEY_MONTHLY_CYCLE_START, cycleStart)?.apply()
        }

        // Keep records within 30 days
        val cutoff = now - ONE_MONTH_MS
        val validRecords = sanitizedList.filter { it.timestamp >= cutoff }

        recalculateSummaries(validRecords, total, cycleStart)
    }

    private fun saveRecords(list: List<OtpHistoryRecord>, totalAllTime: Int) {
        val arr = JSONArray()
        val trimmed = list.take(1000)
        for (item in trimmed) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("phoneNumber", item.phoneNumber)
            o.put("otpCode", item.otpCode)
            o.put("service", item.service)
            o.put("platform", item.platform)
            o.put("rate", item.rate)
            o.put("timestamp", item.timestamp)
            o.put("dateKey", item.dateKey)
            arr.put(o)
        }
        prefs?.edit()
            ?.putString(KEY_RECORDS_JSON, arr.toString())
            ?.putInt(KEY_TOTAL_COUNT, totalAllTime)
            ?.apply()
    }

    /**
     * Record arrived OTP from Unix SMS or Zenex
     */
    fun recordOtp(
        phoneNumber: String,
        otpCode: String,
        service: String,
        platform: String,
        rate: Double = 0.50
    ) {
        val now = System.currentTimeMillis()
        val record = OtpHistoryRecord(
            phoneNumber = phoneNumber,
            otpCode = otpCode,
            service = service,
            platform = platform,
            rate = rate,
            timestamp = now,
            dateKey = dateFormat.format(Date(now))
        )

        val updatedRecords = listOf(record) + _state.value.recentRecords
        val newTotal = _state.value.totalOtpsAllTime + 1

        val cycleStart = _state.value.monthlyCycleStartEpoch.let {
            if (it == 0L) now else it
        }

        recalculateSummaries(updatedRecords, newTotal, cycleStart)
        saveRecords(updatedRecords, newTotal)
        WithdrawalManager.refreshBalances()
    }

    fun recalculateSummaries(
        records: List<OtpHistoryRecord>,
        totalAllTime: Int,
        cycleStartEpoch: Long = _state.value.monthlyCycleStartEpoch
    ) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        // Compute midnight of next day (reset time for Today OTP)
        cal.set(Calendar.HOUR_OF_DAY, 24)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val midnightEpoch = cal.timeInMillis
        val secondsUntilReset = maxOf(0L, (midnightEpoch - now) / 1000L)

        val targetMonthlyEpoch = cycleStartEpoch + ONE_MONTH_MS

        val currentUnixRate = UnixSmsManager.state.value.otpRatePerSms
        val currentZenexRate = VirtualNumberManager.zenexOtpRate.value

        // Compute 30 days breakdown (Today = Day 0, ..., Day -29)
        val days30List = mutableListOf<DailyOtpSummary>()
        val checkCal = Calendar.getInstance()

        for (i in 0 until 30) {
            val dKey = dateFormat.format(checkCal.time)
            val label = when (i) {
                0 -> "Today (${displayFormat.format(checkCal.time)})"
                1 -> "Yesterday (${displayFormat.format(checkCal.time)})"
                else -> displayFormat.format(checkCal.time)
            }
            val matched = records.filter { it.dateKey == dKey }
            val unixCount = matched.count { it.platform.contains("Unix", ignoreCase = true) }
            val zenexCount = matched.count { !it.platform.contains("Unix", ignoreCase = true) }

            val dayRate = matched.firstOrNull { it.platform.contains("Unix", ignoreCase = true) }?.rate ?: currentUnixRate

            days30List.add(
                DailyOtpSummary(
                    dateKey = dKey,
                    displayLabel = label,
                    totalCount = matched.size,
                    unixCount = unixCount,
                    zenexCount = zenexCount,
                    unixRateOnDay = dayRate,
                    records = matched
                )
            )
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val days7List = days30List.take(7)

        val todaySummary = days30List.firstOrNull()
        val todayTotal = todaySummary?.totalCount ?: 0
        val todayUnix = todaySummary?.unixCount ?: 0
        val todayZenex = todaySummary?.zenexCount ?: 0

        val unixTotal = records.count { it.platform.contains("Unix", ignoreCase = true) }
        val zenexTotal = records.count { !it.platform.contains("Unix", ignoreCase = true) }

        val sum7Days = days7List.sumOf { it.totalCount }
        val sum30Days = days30List.sumOf { it.totalCount }

        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)
        val sixtyDaysAgo = now - (60L * 24 * 60 * 60 * 1000L)
        val ninetyDaysAgo = now - (90L * 24 * 60 * 60 * 1000L)

        val lastMonthCount = records.count { it.timestamp in (sixtyDaysAgo until thirtyDaysAgo) }
        val twoMonthsAgoCount = records.count { it.timestamp in (ninetyDaysAgo until sixtyDaysAgo) }

        val m1Cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val m2Cal = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }
        val monthNameFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        val m1Label = monthNameFormat.format(m1Cal.time)
        val m2Label = monthNameFormat.format(m2Cal.time)

        _state.update {
            it.copy(
                totalOtpsAllTime = totalAllTime,
                unixTotalAllTime = unixTotal,
                zenexTotalAllTime = zenexTotal,
                todayOtps = todayTotal,
                unixTodayOtps = todayUnix,
                zenexTodayOtps = todayZenex,
                currentUnixRate = currentUnixRate,
                currentZenexRate = currentZenexRate,
                last7DaysSummaries = days7List,
                last30DaysSummaries = days30List,
                last7DaysOtps = sum7Days,
                last30DaysOtps = sum30Days,
                lastMonthOtps = lastMonthCount,
                twoMonthsAgoOtps = twoMonthsAgoCount,
                lastMonthLabel = m1Label,
                twoMonthsAgoLabel = m2Label,
                recentRecords = records,
                resetCountdownSeconds = secondsUntilReset,
                resetTargetEpoch = midnightEpoch,
                monthlyCycleStartEpoch = cycleStartEpoch,
                monthlyCycleTargetEpoch = targetMonthlyEpoch
            )
        }
    }

    fun getFormattedDailyResetCountdown(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 24)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val diff = maxOf(0L, cal.timeInMillis - System.currentTimeMillis())
        val hours = diff / (1000 * 60 * 60)
        val mins = (diff / (1000 * 60)) % 60
        val secs = (diff / 1000) % 60
        return String.format(Locale.US, "%02dh %02dm %02ds", hours, mins, secs)
    }

    fun getFormatted1MonthRemainingCountdown(): String {
        val now = System.currentTimeMillis()
        var target = _state.value.monthlyCycleTargetEpoch
        if (target <= now) {
            val cycleStart = now
            prefs?.edit()?.putLong(KEY_MONTHLY_CYCLE_START, cycleStart)?.apply()
            target = cycleStart + ONE_MONTH_MS
            _state.update {
                it.copy(monthlyCycleStartEpoch = cycleStart, monthlyCycleTargetEpoch = target)
            }
        }

        val diff = maxOf(0L, target - now)
        val days = diff / (1000 * 60 * 60 * 24)
        val hours = (diff / (1000 * 60 * 60)) % 24
        val mins = (diff / (1000 * 60)) % 60
        val secs = (diff / 1000) % 60

        return if (days > 0) {
            String.format(Locale.US, "%dd %02dh %02dm", days, hours, mins)
        } else {
            String.format(Locale.US, "%02dh %02dm %02ds", hours, mins, secs)
        }
    }

    /**
     * Download / Export 30 Days OTP History
     */
    fun exportOtpHistory(context: Context) {
        val records = _state.value.recentRecords
        val total = _state.value.totalOtpsAllTime
        val today = _state.value.todayOtps
        val timeNowStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())

        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("  WORK SHORTCUT - 30 DAYS OTP HISTORY\n")
        sb.append("  Export Date: $timeNowStr\n")
        sb.append("  Total OTPs: $total (Today: $today)\n")
        sb.append("=========================================\n\n")

        if (records.isEmpty()) {
            sb.append("No OTP records available yet.\n")
        } else {
            sb.append("DATE & TIME       | PLATFORM   | SERVICE    | NUMBER          | OTP CODE\n")
            sb.append("-------------------------------------------------------------------------\n")
            records.forEach { r ->
                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(r.timestamp))
                val platformPadded = r.platform.padEnd(10)
                val servicePadded = r.service.padEnd(10)
                val numPadded = r.phoneNumber.padEnd(15)
                sb.append("$timeStr | $platformPadded | $servicePadded | $numPadded | ${r.otpCode}\n")
            }
            sb.append("\nTotal Records Exported: ${records.size}\n")
            sb.append("=========================================\n")
        }

        val exportText = sb.toString()

        // 1. Copy to clipboard
        ClipboardHelper.copyToClipboard(context, exportText, "OTP 30-Day History")

        // 2. Open Android Share / Save Sheet
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, exportText)
                putExtra(Intent.EXTRA_SUBJECT, "Work ShortCut - OTP History ($timeNowStr)")
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val shareIntent = Intent.createChooser(sendIntent, "Download / Share OTP History").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
            Toast.makeText(context, "✓ OTP হিস্টোরি কপি ও ডাউনলোডের জন্য প্রস্তুত!", Toast.LENGTH_LONG).show()
            VibrationHelper.vibrateSuccess(context)
        } catch (_: Exception) {
            Toast.makeText(context, "✓ OTP হিস্টোরি ক্লিপবোর্ডে কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }
}
