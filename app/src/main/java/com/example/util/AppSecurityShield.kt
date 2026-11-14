package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Multi-layer Anti-Tamper, Anti-Signature-Killer, Anti-Hook, and Certificate-Bound Cryptographic Vault.
 *
 * Security Architecture:
 * 1. Zero plaintext API URLs, endpoints, headers, or tokens in classes.dex or shared_prefs.
 * 2. Decryption key is mathematically derived at runtime from the live APK X.509 signing certificate
 *    SHA-256 digest + package name. Re-signing the APK in MT Manager / NP Manager / APKTool changes
 *    the certificate digest, causing all decrypted URLs and API keys to turn into unusable garbage
 *    even if an attacker patches boolean checks in smali/dex.
 * 3. Defeats Signature Killers (KillerApplication / PMS Dynamic Proxy / Xposed / LSPosed / Frida)
 *    by cross-checking PackageManager signatures against raw APK ZIP META-INF X.509 certificates,
 *    inspecting ActivityThread.sPackageManager for Proxy classes, and scanning /proc/self/maps.
 * 4. Blocks packet sniffers (HttpCanary / Reqable / Burp / Charles / Fiddler) via Proxy.NO_PROXY
 *    and system proxy property inspection on every OkHttpClient.
 */
object AppSecurityShield {

    // Official package name & signing certificate SHA-256 digest (built via char codes to avoid plain string search)
    private val EXPECTED_PKG: String
        get() = charArrayOf(
            'c','o','m','.','a','i','s','t','u','d','i','o','.',
            'w','o','r','k','s','h','o','r','t','c','u','t','.',
            'k','x','m','p','z','q'
        ).concatToString()

    private val EXPECTED_CERT_SHA256: String
        get() = charArrayOf(
            '7','1','7','b','e','f','8','3','c','9','7','d','f','e','8','5',
            '7','a','2','b','7','6','9','f','9','b','c','1','2','9','f','0',
            'f','c','7','e','8','5','2','8','1','3','d','c','9','e','2','4',
            '7','0','9','b','9','4','6','7','f','5','c','c','8','0','8','9'
        ).concatToString()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedMasterKey: ByteArray? = null

    private val _tamperReason = MutableStateFlow<String?>(null)
    val tamperReason: StateFlow<String?> = _tamperReason.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext ?: context
        verifyIntegrityAndDeriveKey(appContext)
    }

    fun isTampered(): Boolean {
        if (cachedMasterKey == null) {
            verifyIntegrityAndDeriveKey(resolveContext())
        }
        return _tamperReason.value != null
    }

    internal fun getMasterKey(): ByteArray {
        cachedMasterKey?.let { return it }
        return verifyIntegrityAndDeriveKey(resolveContext())
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun resolveContext(): Context? {
        appContext?.let { return it }
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val method = atClass.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            val app = method.invoke(null) as? Context
            if (app != null) {
                appContext = app
            }
            app
        } catch (_: Throwable) {
            null
        }
    }

    private fun isJvmTestEnvironment(): Boolean {
        val fp = Build.FINGERPRINT ?: return true
        return fp.contains("robolectric", ignoreCase = true)
    }

    @Synchronized
    private fun verifyIntegrityAndDeriveKey(context: Context?): ByteArray {
        cachedMasterKey?.let { return it }

        if (isJvmTestEnvironment() || context == null) {
            val seed = "${EXPECTED_CERT_SHA256}:${EXPECTED_PKG}:WS_SHIELD_2026_V9"
            val key = sha256(seed.toByteArray(Charsets.UTF_8))
            cachedMasterKey = key
            return key
        }

        var detectedIssue: String? = null
        val livePkg = context.packageName ?: ""

        // 1. Verify Package Name (Detects APK cloning / package renaming)
        if (livePkg != EXPECTED_PKG) {
            detectedIssue = "ERR_PKG_MISMATCH"
        }

        // 2. Check for PMS Dynamic Proxy Hook & Signature Killers (MT VIP / NP Manager / KillerApplication)
        if (detectedIssue == null && detectSignatureKillerOrPmsHook(context)) {
            detectedIssue = "ERR_SIGNATURE_KILLER_HOOK"
        }

        // 3. Check for Xposed / LSPosed / Frida / Substrate / Debugger in memory & ClassLoader
        if (detectedIssue == null && detectRuntimeHooksAndMemoryInjection()) {
            detectedIssue = "ERR_RUNTIME_HOOK_DETECTED"
        }

        // 4. Extract Live Signing Certificate SHA-256 from PackageManager AND raw APK ZIP
        val pmCertHex = extractPackageManagerCertSha256(context)
        val zipCertHex = extractApkZipCertSha256(context)

        val liveCertHex = when {
            pmCertHex.isNotEmpty() && zipCertHex.isNotEmpty() && pmCertHex != zipCertHex -> {
                // PackageManager returned a spoofed cert that differs from the actual APK META-INF cert!
                detectedIssue = "ERR_CERT_SPOOF_DETECTED"
                zipCertHex
            }
            pmCertHex.isNotEmpty() -> pmCertHex
            zipCertHex.isNotEmpty() -> zipCertHex
            else -> {
                detectedIssue = "ERR_NO_SIGNATURE"
                "0000000000000000000000000000000000000000000000000000000000000000"
            }
        }

        if (liveCertHex != EXPECTED_CERT_SHA256 && detectedIssue == null) {
            detectedIssue = "ERR_INVALID_SIGNATURE"
        }

        _tamperReason.value = detectedIssue

        // Derive the cryptographic master key directly from the live certificate + package state.
        // If any hook/tamper was detected, poison the seed so decrypted API URLs/keys are invalid
        // even if an attacker patches `isTampered()` in smali.
        val effectiveCert = if (detectedIssue == null) liveCertHex else "${liveCertHex}_POISON_${detectedIssue}"
        val seed = "$effectiveCert:$livePkg:WS_SHIELD_2026_V9"
        val key = sha256(seed.toByteArray(Charsets.UTF_8))
        cachedMasterKey = key
        return key
    }

    @SuppressLint("PrivateApi")
    private fun detectSignatureKillerOrPmsHook(context: Context): Boolean {
        try {
            // Check Application class name for KillerApplication / MT / NP wrappers
            val appClassName = context.applicationInfo?.className ?: ""
            val actualAppClass = context.applicationContext?.javaClass?.name ?: ""
            val forbiddenAppTokens = listOf(
                "KillerApplication",
                "bin.mt",
                "cc.binmt",
                "np.manager",
                "ModApplication",
                "HookApplication",
                "SandHook",
                "Pine"
            )
            if (forbiddenAppTokens.any {
                    appClassName.contains(it, ignoreCase = true) ||
                        actualAppClass.contains(it, ignoreCase = true)
                }
            ) {
                return true
            }

            // Check if ActivityThread.sPackageManager is a Proxy class (classic PMS signature killer)
            val atClass = Class.forName("android.app.ActivityThread")
            val getPmMethod = atClass.getDeclaredMethod("getPackageManager")
            getPmMethod.isAccessible = true
            val iPm = getPmMethod.invoke(null)
            if (iPm != null && Proxy.isProxyClass(iPm.javaClass)) {
                return true
            }

            val sPmField = atClass.getDeclaredField("sPackageManager")
            sPmField.isAccessible = true
            val sPm = sPmField.get(null)
            if (sPm != null && Proxy.isProxyClass(sPm.javaClass)) {
                return true
            }

            // Probe ClassLoader for known signature killer / hook classes
            val killerClasses = arrayOf(
                "bin.mt.signature.KillerApplication",
                "cc.binmt.signature.PmsHookApplication",
                "de.robv.android.xposed.XposedBridge",
                "de.robv.android.xposed.XposedHelpers",
                "com.saurik.substrate.MS"
            )
            for (cls in killerClasses) {
                try {
                    Class.forName(cls, false, context.classLoader)
                    return true
                } catch (_: ClassNotFoundException) {
                    // Expected on clean device
                }
            }

            // Check APK sourceDir path for DualSpace / VirtualXposed / Parallel redirects
            val sourceDir = context.applicationInfo?.sourceDir ?: ""
            if (sourceDir.isNotEmpty() && !sourceDir.startsWith("/data/app/") && !sourceDir.startsWith("/system/") && !sourceDir.startsWith("/mnt/asec/")) {
                return true
            }
        } catch (_: Throwable) {
        }
        return false
    }

    private fun detectRuntimeHooksAndMemoryInjection(): Boolean {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return true
            }

            // Inspect stack trace for Xposed / LSPosed / ZjHook / Substrate frames
            val stackTrace = Throwable().stackTrace
            for (element in stackTrace) {
                val cn = element.className
                if (cn.contains("de.robv.android.xposed", ignoreCase = true) ||
                    cn.contains("lsposed", ignoreCase = true) ||
                    cn.contains("com.saurik.substrate", ignoreCase = true) ||
                    cn.contains("bin.mt.signature", ignoreCase = true) ||
                    cn.contains("cc.binmt", ignoreCase = true)
                ) {
                    return true
                }
            }

            // Check /proc/self/status for TracerPid != 0 (ptrace / gdb / ida / frida attach)
            val statusFile = File("/proc/self/status")
            if (statusFile.exists() && statusFile.canRead()) {
                BufferedReader(FileReader(statusFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.startsWith("TracerPid:")) {
                            val pid = l.substringAfter("TracerPid:").trim().toIntOrNull() ?: 0
                            if (pid != 0) return true
                            break
                        }
                    }
                }
            }

            // Scan /proc/self/maps for injected shared libraries (Frida, Xposed, Riru, Zygisk-LSPosed, MT Killer)
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                val suspiciousLibs = arrayOf(
                    "frida-agent",
                    "frida-gadget",
                    "libfrida",
                    "XposedBridge.jar",
                    "libxposed",
                    "libsandhook",
                    "libpine.so",
                    "libkillersignature",
                    "libmt_hook",
                    "bin.mt.signature"
                )
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        for (marker in suspiciousLibs) {
                            if (l.contains(marker, ignoreCase = true)) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return false
    }

    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    private fun extractPackageManagerCertSha256(context: Context): String {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                val sigs = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                    else signingInfo.signingCertificateHistory
                } else null
                sigs?.firstOrNull()?.toByteArray()
            } else {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                info.signatures?.firstOrNull()?.toByteArray()
            }
            if (certBytes != null && certBytes.isNotEmpty()) {
                bytesToHex(sha256(certBytes))
            } else ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun extractApkZipCertSha256(context: Context): String {
        val sourceDir = context.applicationInfo?.sourceDir ?: return ""
        return try {
            val apkFile = File(sourceDir)
            if (!apkFile.exists() || !apkFile.canRead()) return ""
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.uppercase()
                    if (name.startsWith("META-INF/") &&
                        (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))
                    ) {
                        zip.getInputStream(entry).use { input ->
                            val cf = CertificateFactory.getInstance("X.509")
                            val certs = cf.generateCertificates(input)
                            val firstCert = certs.firstOrNull() as? X509Certificate
                            if (firstCert != null) {
                                return bytesToHex(sha256(firstCert.encoded))
                            }
                        }
                    }
                }
            }
            ""
        } catch (_: Throwable) {
            ""
        }
    }

    internal fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString().lowercase()
    }
}

/**
 * Cryptographic Vault that decrypts endpoints, headers, and API keys on-the-fly using
 * the certificate-bound master key from [AppSecurityShield].
 */
object SecureVault {

    private const val PREF_ENC_PREFIX = "ENC_V9:"

    // SUPABASE_BASE_URL (salt=101)
    private val ENC_SUPABASE_BASE_URL = intArrayOf(224, 93, 70, 53, 130, 238, 158, 137, 88, 155, 51, 114, 78, 235, 97, 132, 215, 77, 168, 228, 23, 211, 103, 115, 88, 8, 2, 141, 172, 224, 161, 92, 9, 107, 115, 22, 244, 26, 178, 41, 49, 171, 2, 16, 176, 212, 130, 246)
    // SUPABASE_KEY (salt=102)
    private val ENC_SUPABASE_KEY = intArrayOf(200, 130, 243, 65, 215, 101, 146, 198, 2, 124, 195, 53, 114, 61, 36, 28, 164, 221, 3, 17, 150, 215, 10, 18, 79, 23, 37, 156, 23, 82, 137, 26, 209, 149, 160, 169, 165, 184, 169, 220, 18, 38, 4, 14, 139, 77)
    // UNIX_DEFAULT_TOKEN (salt=103)
    private val ENC_UNIX_DEFAULT_TOKEN = intArrayOf(159, 167, 91, 117, 115, 58, 173, 8, 197, 251, 107, 148, 154, 221, 170, 244, 124, 95, 186, 253, 214, 217, 157, 82, 125, 224, 241, 220, 78, 51, 99, 94, 218, 98, 15, 105, 73, 73, 179, 111, 71, 175, 172, 177, 177, 225, 193, 13, 116, 166, 144, 199, 182, 178, 85, 7, 185, 144)
    // LAMIX_DEFAULT_TOKEN (salt=104)
    private val ENC_LAMIX_DEFAULT_TOKEN = intArrayOf(43, 55, 44, 28, 54, 73, 81, 196, 182, 91, 206, 236, 232, 79, 254, 85, 153, 182, 34, 29, 107, 69, 57, 188, 83, 11, 137, 89, 129, 124, 203, 99, 122, 43, 25, 0, 73, 40, 151, 178, 162, 70, 192)
    // HADI_DEFAULT_TOKEN (salt=105)
    private val ENC_HADI_DEFAULT_TOKEN = intArrayOf(3, 176, 96, 101, 202, 240, 209, 151, 94, 163, 239, 13, 190, 180, 32, 92, 31, 251, 184, 43, 251, 181, 210, 147, 197, 72, 155, 25, 30, 191, 169, 199, 42, 171, 68, 49, 28, 207, 211, 154, 74, 190, 139, 224, 209, 205, 90, 88, 6, 157, 107, 121, 19, 170, 156, 196)
    // HADI_BASE_URL (salt=106)
    private val ENC_HADI_BASE_URL = intArrayOf(132, 179, 76, 179, 16, 47, 122, 168, 91, 191, 120, 133, 77, 178, 104, 192, 52, 74, 32, 38, 198, 68, 148, 153, 146, 110, 122, 15, 215, 186, 172, 117, 227, 209, 177, 198, 125, 83, 1, 216, 123, 27)
    // LAMIX_BASE_URL (salt=107)
    private val ENC_LAMIX_BASE_URL = intArrayOf(127, 97, 112, 125, 119, 28, 100, 115, 117, 240, 30, 158, 202, 124, 150, 95, 206, 122, 176, 179, 99, 7, 155, 160, 69, 153, 248, 129, 16, 122, 97, 125, 146, 6, 87, 76, 67, 99, 88)
    // UNIX_BASE_URL (salt=108)
    private val ENC_UNIX_BASE_URL = intArrayOf(174, 236, 143, 172, 116, 148, 162, 14, 163, 207, 100, 15, 247, 12, 211, 180, 33, 54, 39, 24, 6, 100, 116, 232, 208, 194, 23, 67, 133, 236, 1, 134, 201, 219, 191, 142)
    // ZENEX_DEFAULT_KEY (salt=109)
    private val ENC_ZENEX_DEFAULT_KEY = intArrayOf(208, 83, 232, 125, 164, 170, 138, 135, 129, 101, 1, 98, 79, 52, 241, 189, 97, 84, 167, 2, 235, 86, 93, 121, 133, 114, 239, 113)
    // ZENEX_GETNUM_URL (salt=110)
    private val ENC_ZENEX_GETNUM_URL = intArrayOf(215, 206, 49, 51, 107, 231, 245, 115, 117, 246, 163, 90, 210, 173, 27, 203, 34, 58, 35, 206, 211, 127, 188, 226, 117, 92, 164, 226, 114, 92, 164, 125, 184, 63, 81, 13, 77, 144)
    // ZENEX_OTP_URL (salt=111)
    private val ENC_ZENEX_OTP_URL = intArrayOf(41, 70, 250, 207, 189, 102, 162, 182, 221, 199, 231, 25, 149, 140, 134, 59, 78, 64, 110, 75, 66, 131, 26, 8, 251, 58, 188, 45, 107, 187, 112, 176, 79, 103, 195, 236, 91, 95, 14, 156, 239, 228, 129, 190, 225, 111, 231)
    // ZENEX_RANGES_URL (salt=112)
    private val ENC_ZENEX_RANGES_URL = intArrayOf(210, 204, 242, 216, 68, 121, 161, 151, 3, 191, 141, 155, 142, 178, 35, 163, 158, 198, 133, 98, 72, 0, 189, 204, 112, 186, 157, 105, 235, 114, 235, 248, 251, 251, 18, 161, 161, 70, 131, 234, 35, 65, 227, 48, 231)
    // ZENEX_BROADCAST_URL (salt=113)
    private val ENC_ZENEX_BROADCAST_URL = intArrayOf(3, 19, 53, 221, 170, 23, 20, 9, 123, 138, 55, 237, 56, 9, 110, 222, 215, 105, 147, 167, 248, 83, 99, 233, 65, 249, 131, 75, 51, 47, 1, 143, 164, 113, 144, 226, 158, 97, 116, 164, 13, 113, 13, 129, 16, 35, 129, 191, 44, 6, 165, 135)
    // ZENEX_HEADER (salt=114)
    private val ENC_ZENEX_HEADER = intArrayOf(53, 216, 51, 83, 209, 155, 159)
    // MK_BASE_URL (salt=115)
    private val ENC_MK_BASE_URL = intArrayOf(194, 90, 54, 141, 66, 65, 165, 78, 111, 68, 81, 158, 118, 130, 143, 91, 149, 215, 203, 202, 90, 246, 169, 181, 189, 132, 154, 133, 39, 155, 181, 33, 255, 108, 14, 116, 50)
    // MK_DEFAULT_KEY (salt=116)
    private val ENC_MK_DEFAULT_KEY = intArrayOf(63, 16, 168, 6, 1, 170, 83, 74, 139, 183, 48, 27, 213, 147, 145, 71, 145, 21, 238, 14, 154, 96, 187, 161)
    // MK_HEADER (salt=117)
    private val ENC_MK_HEADER = intArrayOf(82, 86, 65, 47, 190, 253, 228, 247, 2, 87, 74, 132, 142)
    // SL_BASE_URL (salt=118)
    private val ENC_SL_BASE_URL = intArrayOf(156, 235, 163, 245, 36, 174, 242, 246, 38, 154, 22, 120, 167, 24, 91, 71, 117, 252, 168, 26, 88, 12, 111, 178, 57, 151, 12, 113, 99, 36, 4, 36, 162, 142, 216, 193, 210, 130, 88, 149, 26, 166, 37, 131)
    // SL_DEFAULT_KEY (salt=119)
    private val ENC_SL_DEFAULT_KEY = intArrayOf(180, 188, 5, 117, 231, 75, 102, 120, 93, 110, 161, 22, 113, 79, 144, 199, 88, 241, 224, 20, 99, 219, 28, 71, 234, 51, 99, 253, 37, 7, 128, 106, 222, 145, 136, 26, 13, 42, 23, 53, 117, 223, 192, 240, 87, 60, 224, 239, 190, 199, 93, 110, 20, 248, 51, 162)
    // SL_HEADER (salt=120)
    private val ENC_SL_HEADER = intArrayOf(58, 135, 223, 191, 140, 242, 151, 79, 201)
    // ADMIN_PASS (salt=121)
    private val ENC_ADMIN_PASS = intArrayOf(81, 153, 62, 132, 42, 247, 227, 228, 135, 194, 248)

    private fun unlock(cipher: IntArray, saltId: Int): String {
        val master = AppSecurityShield.getMasterKey()
        val subSeed = ByteArray(master.size + 2)
        System.arraycopy(master, 0, subSeed, 0, master.size)
        subSeed[master.size] = (saltId and 0xFF).toByte()
        subSeed[master.size + 1] = ((saltId shr 8) and 0xFF).toByte()
        val subKey = AppSecurityShield.sha256(subSeed)

        val out = ByteArray(cipher.size)
        for (i in cipher.indices) {
            val k1 = subKey[i % subKey.size].toInt() and 0xFF
            val k2 = ((i * 73 + saltId * 19) xor 0x5A) and 0xFF
            out[i] = ((cipher[i] xor k1 xor k2) and 0xFF).toByte()
        }
        return String(out, Charsets.UTF_8)
    }

    val supabaseBaseUrl: String get() = unlock(ENC_SUPABASE_BASE_URL, 101)
    val supabaseKey: String get() = unlock(ENC_SUPABASE_KEY, 102)

    val unixDefaultToken: String get() = unlock(ENC_UNIX_DEFAULT_TOKEN, 103)
    val lamixDefaultToken: String get() = unlock(ENC_LAMIX_DEFAULT_TOKEN, 104)
    val hadiDefaultToken: String get() = unlock(ENC_HADI_DEFAULT_TOKEN, 105)
    val hadiBaseUrl: String get() = unlock(ENC_HADI_BASE_URL, 106)
    val lamixBaseUrl: String get() = unlock(ENC_LAMIX_BASE_URL, 107)
    val unixBaseUrl: String get() = unlock(ENC_UNIX_BASE_URL, 108)

    val zenexDefaultKey: String get() = unlock(ENC_ZENEX_DEFAULT_KEY, 109)
    val zenexGetNumUrl: String get() = unlock(ENC_ZENEX_GETNUM_URL, 110)
    val zenexOtpUrl: String get() = unlock(ENC_ZENEX_OTP_URL, 111)
    val zenexRangesUrl: String get() = unlock(ENC_ZENEX_RANGES_URL, 112)
    val zenexBroadcastUrl: String get() = unlock(ENC_ZENEX_BROADCAST_URL, 113)
    val zenexHeader: String get() = unlock(ENC_ZENEX_HEADER, 114)

    val mkBaseUrl: String get() = unlock(ENC_MK_BASE_URL, 115)
    val mkDefaultKey: String get() = unlock(ENC_MK_DEFAULT_KEY, 116)
    val mkHeader: String get() = unlock(ENC_MK_HEADER, 117)

    val slBaseUrl: String get() = unlock(ENC_SL_BASE_URL, 118)
    val slDefaultKey: String get() = unlock(ENC_SL_DEFAULT_KEY, 119)
    val slHeader: String get() = unlock(ENC_SL_HEADER, 120)

    val adminPass: String get() = unlock(ENC_ADMIN_PASS, 121)

    /**
     * Encrypts a custom API token before saving to SharedPreferences so shared_prefs XML files
     * on disk never expose plaintext tokens to rooted MT Manager users.
     */
    fun encryptPref(plain: String): String {
        if (plain.isBlank()) return ""
        if (plain.startsWith(PREF_ENC_PREFIX)) return plain
        return try {
            val master = AppSecurityShield.getMasterKey()
            val raw = plain.toByteArray(Charsets.UTF_8)
            val enc = ByteArray(raw.size)
            for (i in raw.indices) {
                val k = master[i % master.size].toInt() and 0xFF
                val m = ((i * 41) xor 0xA5) and 0xFF
                enc[i] = ((raw[i].toInt() and 0xFF) xor k xor m).toByte()
            }
            PREF_ENC_PREFIX + Base64.encodeToString(enc, Base64.NO_WRAP)
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Decrypts a token stored in SharedPreferences, or returns [defaultFallback] if empty/default.
     */
    fun decryptPref(stored: String?, defaultFallback: String): String {
        if (stored.isNullOrBlank()) return defaultFallback
        if (!stored.startsWith(PREF_ENC_PREFIX)) {
            return stored
        }
        return try {
            val b64 = stored.removePrefix(PREF_ENC_PREFIX)
            val enc = Base64.decode(b64, Base64.NO_WRAP)
            val master = AppSecurityShield.getMasterKey()
            val dec = ByteArray(enc.size)
            for (i in enc.indices) {
                val k = master[i % master.size].toInt() and 0xFF
                val m = ((i * 41) xor 0xA5) and 0xFF
                dec[i] = ((enc[i].toInt() and 0xFF) xor k xor m).toByte()
            }
            String(dec, Charsets.UTF_8).ifBlank { defaultFallback }
        } catch (_: Throwable) {
            defaultFallback
        }
    }
}

/**
 * Hardened OkHttpClient factory that enforces Proxy.NO_PROXY and blocks requests when
 * packet sniffers (HttpCanary / Reqable / Charles / Burp) or APK tampering are active.
 */
object SecureHttpClient {

    private val securityInterceptor = Interceptor { chain ->
        if (AppSecurityShield.isTampered()) {
            throw IOException("SEC_SHIELD_BLOCK")
        }
        val proxyHost = System.getProperty("http.proxyHost")
        val httpsProxyHost = System.getProperty("https.proxyHost")
        if (!proxyHost.isNullOrBlank() || !httpsProxyHost.isNullOrBlank()) {
            throw IOException("SEC_PROXY_SNIFFER_BLOCK")
        }
        chain.proceed(chain.request())
    }

    fun create(
        connectTimeoutSeconds: Long = 10L,
        readTimeoutSeconds: Long = 10L,
        writeTimeoutSeconds: Long = 10L
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY)
            .addInterceptor(securityInterceptor)
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }
}
