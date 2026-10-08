package com.example.service

import android.content.Context
import android.content.SharedPreferences
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
    val rate: Double = 0.014, // Rate active at time of OTP
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
    val currentUnixRate: Double = 0.014,
    val last7DaysSummaries: List<DailyOtpSummary> = emptyList(),
    val recentRecords: List<OtpHistoryRecord> = emptyList(),
    val resetCountdownSeconds: Long = 0L,
    val resetTargetEpoch: Long = 0L,
    val sevenDayCycleStartEpoch: Long = 0L,
    val sevenDayCycleTargetEpoch: Long = 0L
)

object OtpHistoryManager {

    private const val PREFS_NAME = "work_shortcut_otp_history"
    private const val KEY_RECORDS_JSON = "otp_records_json"
    private const val KEY_TOTAL_COUNT = "total_otp_all_time"
    private const val KEY_7DAY_CYCLE_START = "seven_day_cycle_start_time"

    private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

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
                        rate = o.optDouble("rate", 0.014),
                        timestamp = ts,
                        dateKey = o.optString("dateKey", dateFormat.format(Date(ts)))
                    )
                )
            }
        } catch (_: Exception) {}

        val total = prefs?.getInt(KEY_TOTAL_COUNT, list.size) ?: list.size

        var cycleStart = prefs?.getLong(KEY_7DAY_CYCLE_START, 0L) ?: 0L
        val now = System.currentTimeMillis()
        if (cycleStart == 0L || now - cycleStart >= SEVEN_DAYS_MS) {
            cycleStart = now
            prefs?.edit()?.putLong(KEY_7DAY_CYCLE_START, cycleStart)?.apply()
        }

        recalculateSummaries(list, total, cycleStart)
    }

    private fun saveRecords(list: List<OtpHistoryRecord>, totalAllTime: Int) {
        val arr = JSONArray()
        val trimmed = list.take(300)
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
        rate: Double = 0.014
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

        val cycleStart = _state.value.sevenDayCycleStartEpoch.let {
            if (it == 0L) now else it
        }

        recalculateSummaries(updatedRecords, newTotal, cycleStart)
        saveRecords(updatedRecords, newTotal)
    }

    fun recalculateSummaries(
        records: List<OtpHistoryRecord>,
        totalAllTime: Int,
        cycleStartEpoch: Long = _state.value.sevenDayCycleStartEpoch
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

        val target7DayEpoch = cycleStartEpoch + SEVEN_DAYS_MS

        val currentRate = UnixSmsManager.state.value.otpRatePerSms

        // Compute 7 days breakdown: Today (Day 0), Day -1, Day -2, ..., Day -6
        val daysList = mutableListOf<DailyOtpSummary>()
        val checkCal = Calendar.getInstance()

        for (i in 0 until 7) {
            val dKey = dateFormat.format(checkCal.time)
            val label = when (i) {
                0 -> "Today (${displayFormat.format(checkCal.time)})"
                1 -> "Yesterday (${displayFormat.format(checkCal.time)})"
                else -> displayFormat.format(checkCal.time)
            }
            val matched = records.filter { it.dateKey == dKey }
            val unixCount = matched.count { it.platform.contains("Unix", ignoreCase = true) }
            val zenexCount = matched.count { !it.platform.contains("Unix", ignoreCase = true) }

            // Rate on that day: average or latest recorded rate, or current rate if none
            val dayRate = matched.firstOrNull { it.platform.contains("Unix", ignoreCase = true) }?.rate ?: currentRate

            daysList.add(
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

        val todaySummary = daysList.firstOrNull()
        val todayTotal = todaySummary?.totalCount ?: 0
        val todayUnix = todaySummary?.unixCount ?: 0
        val todayZenex = todaySummary?.zenexCount ?: 0

        val unixTotal = records.count { it.platform.contains("Unix", ignoreCase = true) }
        val zenexTotal = records.count { !it.platform.contains("Unix", ignoreCase = true) }

        _state.update {
            it.copy(
                totalOtpsAllTime = totalAllTime,
                unixTotalAllTime = unixTotal,
                zenexTotalAllTime = zenexTotal,
                todayOtps = todayTotal,
                unixTodayOtps = todayUnix,
                zenexTodayOtps = todayZenex,
                currentUnixRate = currentRate,
                last7DaysSummaries = daysList,
                recentRecords = records,
                resetCountdownSeconds = secondsUntilReset,
                resetTargetEpoch = midnightEpoch,
                sevenDayCycleStartEpoch = cycleStartEpoch,
                sevenDayCycleTargetEpoch = target7DayEpoch
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

    fun getFormatted7DaysRemainingCountdown(): String {
        val now = System.currentTimeMillis()
        var target = _state.value.sevenDayCycleTargetEpoch
        if (target <= now) {
            val cycleStart = now
            prefs?.edit()?.putLong(KEY_7DAY_CYCLE_START, cycleStart)?.apply()
            target = cycleStart + SEVEN_DAYS_MS
            _state.update {
                it.copy(sevenDayCycleStartEpoch = cycleStart, sevenDayCycleTargetEpoch = target)
            }
        }

        val diff = maxOf(0L, target - now)
        val days = diff / (1000 * 60 * 60 * 24)
        val hours = (diff / (1000 * 60 * 60)) % 24
        val mins = (diff / (1000 * 60)) % 60
        val secs = (diff / 1000) % 60

        return if (days > 0) {
            String.format(Locale.US, "%dd %02dh %02dm %02ds", days, hours, mins, secs)
        } else {
            String.format(Locale.US, "%02dh %02dm %02ds", hours, mins, secs)
        }
    }
}
