package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val isForced: Boolean = false,
    val publishedAt: Long = System.currentTimeMillis()
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : AppUpdateState
    data class ReadyToInstall(val apkFile: File, val info: AppUpdateInfo) : AppUpdateState
    data object Installing : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val SUPABASE_BASE_URL = "https://fbtqjpuclmnsiqehalow.supabase.co/rest/v1"
    private const val SUPABASE_KEY = "sb_publishable_zBqxIMDPy4yHEuAdJJUlxg_wSCRF9u5"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _isDismissedForSession = MutableStateFlow(false)
    val isDismissedForSession: StateFlow<Boolean> = _isDismissedForSession.asStateFlow()

    private var activeDownloadedFile: File? = null

    fun init(context: Context) {
        scope.launch {
            checkForUpdate(context, silent = true)
        }
    }

    fun dismissDialog() {
        _isDismissedForSession.value = true
    }

    fun resetDismissed() {
        _isDismissedForSession.value = false
    }

    /**
     * Checks Supabase cloud app_settings for any published remote app update
     */
    fun checkForUpdate(context: Context, silent: Boolean = false) {
        scope.launch {
            if (!silent) {
                _state.value = AppUpdateState.Checking
            }
            try {
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=eq.1&select=notification_title,notification_message,notification_timestamp")
                    .header("apikey", SUPABASE_KEY)
                    .get()
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val row = arr.getJSONObject(0)
                        val title = row.optString("notification_title", "")
                        val message = row.optString("notification_message", "")

                        if (title.equals("APP_UPDATE", ignoreCase = true) && message.isNotBlank()) {
                            try {
                                val json = JSONObject(message)
                                val remoteVersionCode = json.optInt("versionCode", 1)
                                val remoteVersionName = json.optString("versionName", "1.0")
                                val apkUrl = json.optString("apkUrl", "")
                                val changelog = json.optString("changelog", "Performance improvements & bug fixes")
                                val isForced = json.optBoolean("forceUpdate", false)
                                val publishedAt = row.optLong("notification_timestamp", System.currentTimeMillis())

                                val currentCode = BuildConfig.VERSION_CODE

                                if (remoteVersionCode > currentCode && apkUrl.isNotBlank()) {
                                    val updateInfo = AppUpdateInfo(
                                        versionCode = remoteVersionCode,
                                        versionName = remoteVersionName,
                                        apkUrl = apkUrl,
                                        changelog = changelog,
                                        isForced = isForced,
                                        publishedAt = publishedAt
                                    )
                                    _state.value = AppUpdateState.UpdateAvailable(updateInfo)
                                    _isDismissedForSession.value = false
                                    return@launch
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing update JSON: ${e.message}")
                            }
                        }
                    }
                }

                if (!silent) {
                    _state.value = AppUpdateState.Idle
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "✓ আপনার অ্যাপটি লেটেস্ট ভার্সনে আছে (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                    }
                } else if (_state.value is AppUpdateState.Checking) {
                    _state.value = AppUpdateState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check update failed: ${e.message}")
                if (!silent) {
                    _state.value = AppUpdateState.Error("আপডেট চেক ব্যর্থ হয়েছে: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "আপডেট চেক করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Downloads the APK file directly with real-time percentage progress
     */
    fun startDownload(context: Context, info: AppUpdateInfo) {
        scope.launch {
            try {
                _state.value = AppUpdateState.Downloading(0f, 0L, 0L)
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updatesDir, "WorkShortCut_v${info.versionName}.apk")
                activeDownloadedFile = apkFile

                val request = Request.Builder()
                    .url(info.apkUrl)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ডাউনলোড লিঙ্ক কাজ করছে না")
                }

                val body = response.body ?: throw Exception("ফাইল ডাউনলোডে কোনো ডাটা পাওয়া যায়নি")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || downloadedBytes == totalBytes) {
                                lastProgressUpdate = now
                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                                _state.value = AppUpdateState.Downloading(progress, downloadedBytes, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                _state.value = AppUpdateState.ReadyToInstall(apkFile, info)

                // Immediately trigger installation on Main thread
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _state.value = AppUpdateState.Error("ডাউনলোড ব্যর্থ হয়েছে: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "ডাউনলোড ব্যর্থ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Opens Android Package Installer with 1 click using FileProvider
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "APK ফাইল পাওয়া যায়নি, আবার ডাউনলোড করুন", Toast.LENGTH_SHORT).show()
                return
            }

            // Android 8.0+ Unknown App Sources Permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
                if (!hasInstallPermission) {
                    Toast.makeText(context, "অ্যাপ আপডেট ইন্সটল করতে 'Install Unknown Apps' পারমিশন অন করুন", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            _state.value = AppUpdateState.Installing

        } catch (e: Exception) {
            Log.e(TAG, "Installation failed: ${e.message}", e)
            Toast.makeText(context, "ইন্সটলেশন শুরু করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
            _state.value = AppUpdateState.Error("ইন্সটলেশন সমস্যা: ${e.message}")
        }
    }

    /**
     * Admin tool: Publishes an update broadcast to Supabase app_settings so all users receive it instantly
     */
    fun publishUpdate(
        versionCode: Int,
        versionName: String,
        apkUrl: String,
        changelog: String,
        isForced: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("versionCode", versionCode)
                    put("versionName", versionName)
                    put("apkUrl", apkUrl.trim())
                    put("changelog", changelog.trim())
                    put("forceUpdate", isForced)
                }

                val patchPayload = JSONObject().apply {
                    put("notification_title", "APP_UPDATE")
                    put("notification_message", json.toString())
                    put("notification_timestamp", System.currentTimeMillis())
                }

                val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=eq.1")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("সার্ভার রেসপন্স কোড: ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "অজানা ত্রুটি")
                }
            }
        }
    }

    /**
     * Admin tool: Clears the published update broadcast
     */
    fun clearPublishedUpdate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                val patchPayload = JSONObject().apply {
                    put("notification_title", "")
                    put("notification_message", "")
                    put("notification_timestamp", 0L)
                }

                val body = patchPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$SUPABASE_BASE_URL/app_settings?id=eq.1")
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        _state.value = AppUpdateState.Idle
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("সার্ভার রেসপন্স কোড: ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "অজানা ত্রুটি")
                }
            }
        }
    }
}
