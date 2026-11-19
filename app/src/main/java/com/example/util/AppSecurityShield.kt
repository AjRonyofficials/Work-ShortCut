package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.Process
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
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.system.exitProcess

data class BlockedModTool(
    val displayName: String,
    val packageName: String,
    val riskCategory: String
)

/**
 * Multi-layer Anti-Tamper, Anti-Signature-Killer, Anti-Hook, Risky Mod-Tool Blocker,
 * Developer/API Cryptographic Integrity Seal, and Certificate-Bound Cryptographic Vault.
 *
 * Security Architecture:
 * 1. Zero plaintext API URLs, endpoints, headers, tokens, or developer branding in classes.dex or shared_prefs.
 * 2. Decryption key is mathematically derived at runtime from the live APK X.509 signing certificate
 *    SHA-256 digest + package name. Re-signing the APK in MT Manager / NP Manager / APKTool changes
 *    the certificate digest, causing all decrypted URLs and API keys to turn into unusable garbage
 *    even if an attacker patches boolean checks in smali/dex.
 * 3. Cryptographic Integrity Seal verifies EVERY API URL, API key, Developer Name, Developer Telegram,
 *    App Display Title, strings.xml app_name, and AndroidManifest label against SHA-256 digests.
 *    Changing even ONE single character or byte anywhere triggers an immediate hard crash.
 * 4. Defeats Signature Killers (KillerApplication / PMS Dynamic Proxy / Xposed / LSPosed / Frida / DPT)
 *    by cross-checking PackageManager signatures against raw APK ZIP META-INF X.509 certificates,
 *    inspecting ActivityThread.sPackageManager for Proxy classes, and scanning /proc/self/maps.
 * 5. Continuously scans and blocks risky APK/DEX/XML editors, unpackers, patchers, hookers, and
 *    packet sniffers (MT Manager, NP Manager, DPT Kill, APKTool M, APK Editor, Lucky Patcher,
 *    HttpCanary, Reqable, PCAPdroid, LSPatch, LSPosed, BlackDex, AntiSplit M, GameGuardian, etc.).
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

    // SHA-256 Seal over ALL 21 decrypted API URLs/keys + ALL 5 Developer/Branding strings + strings.xml + Manifest label
    private val EXPECTED_INTEGRITY_SEAL_SHA256: String
        get() = charArrayOf(
            '0','2','4','7','b','f','4','a','7','a','a','1','5','b','8','e',
            'd','a','3','1','6','8','6','8','0','4','9','f','6','c','6','7',
            '4','b','c','0','a','a','9','b','3','0','a','6','7','c','5','e',
            '4','6','5','c','8','b','9','a','f','f','3','c','0','8','9','c'
        ).concatToString()

    // SHA-256 of rendered MainScreen branding ("Work ShortCut" + 0x1F + "Developed By Ismail Islam Rony")
    private val EXPECTED_MAIN_BRAND_SHA256: String
        get() = charArrayOf(
            '7','e','3','d','1','6','8','8','c','8','b','6','a','4','a','e',
            'f','6','9','5','e','8','a','2','a','d','4','4','3','1','0','3',
            '9','c','f','a','0','8','0','0','f','1','e','f','d','a','5','2',
            'e','6','6','f','4','3','9','c','f','f','1','2','a','e','9','c'
        ).concatToString()

    // SHA-256 of rendered LoginScreen developer branding ("Contact Developer: @ismailislamrony1" + 0x1F + "@ismailislamrony1" + 0x1F + "https://t.me/ismailislamrony1")
    private val EXPECTED_LOGIN_BRAND_SHA256: String
        get() = charArrayOf(
            'e','f','f','2','6','4','d','e','9','6','c','e','6','3','5','0',
            'f','d','5','2','f','6','0','6','4','b','f','5','f','5','e','9',
            '5','d','3','a','c','0','b','6','0','3','2','3','c','c','1','d',
            '3','6','a','7','b','a','d','0','6','3','8','1','7','d','0','0'
        ).concatToString()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedMasterKey: ByteArray? = null

    private val _tamperReason = MutableStateFlow<String?>(null)
    val tamperReason: StateFlow<String?> = _tamperReason.asStateFlow()

    private val _blockedModTools = MutableStateFlow<List<BlockedModTool>>(emptyList())
    val blockedModTools: StateFlow<List<BlockedModTool>> = _blockedModTools.asStateFlow()

    // Exact package registry of risky APK/DEX/XML editors, unpackers, patchers, hookers & packet sniffers
    private data class RiskyPkgRule(
        val packageName: String,
        val defaultName: String,
        val category: String
    )

    private val RISKY_PACKAGE_RULES = arrayOf(
        // APK / DEX / XML Editors & Decompilers
        RiskyPkgRule("bin.mt.plus", "MT Manager", "APK / DEX / XML Editor"),
        RiskyPkgRule("bin.mt.plus.canary", "MT Manager Canary", "APK / DEX / XML Editor"),
        RiskyPkgRule("bin.mt.termex", "MT Manager Ext", "APK / DEX / XML Editor"),
        RiskyPkgRule("cc.binmt.plus", "MT Manager VIP", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.mt.manager", "MT Manager", "APK / DEX / XML Editor"),
        RiskyPkgRule("np.manager", "NP Manager", "APK / DEX / XML Editor"),
        RiskyPkgRule("np.manager.pro", "NP Manager Pro", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.np.manager", "NP Manager", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.nm.manager", "NP Manager", "APK / DEX / XML Editor"),
        RiskyPkgRule("ru.maximoff.apktool", "APKTool M", "APK Decompiler / Editor"),
        RiskyPkgRule("com.gmail.heagoo.apkeditor", "APK Editor", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.gmail.heagoo.apkeditor.pro", "APK Editor Pro", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.gmail.heagoo.apkeditor.parser", "APK Parser / Editor", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.steelworks.apke", "APK Editor", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.apkeditor.pro", "APK Editor Pro", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.apkeditor.plus", "APK Editor+", "APK / DEX / XML Editor"),
        RiskyPkgRule("com.abdurazaaqmohammed.AntiSplit", "AntiSplit M", "APK Split / Mod Tool"),
        RiskyPkgRule("com.abdurazaaqmohammed.antisplit", "AntiSplit M", "APK Split / Mod Tool"),
        RiskyPkgRule("io.github.muntashirakon.AppManager", "App Manager", "APK / Component Inspector"),
        RiskyPkgRule("io.github.muntashirakon.AppManager.debug", "App Manager", "APK / Component Inspector"),
        RiskyPkgRule("com.njlabs.showjava", "Show Java", "DEX Decompiler"),
        RiskyPkgRule("com.mrikso.apkrepacker", "APK Repacker", "APK Decompiler / Repacker"),
        RiskyPkgRule("com.reandroid.apkeditor", "APKEditor", "APK / DEX / XML Editor"),

        // DPT / Unpackers / DEX Dumpers
        RiskyPkgRule("com.luoye.dpt", "DPT Shell / Kill", "DEX Unpacker / Mod Tool"),
        RiskyPkgRule("com.dpt.shell", "DPT Shell / Kill", "DEX Unpacker / Mod Tool"),
        RiskyPkgRule("com.fart.unpacker", "FART Unpacker", "DEX Unpacker"),
        RiskyPkgRule("com.wrbug.dumpdex", "DumpDex", "DEX Dumper"),
        RiskyPkgRule("com.android.dumpdex", "DumpDex", "DEX Dumper"),
        RiskyPkgRule("com.blackdex.app", "BlackDex", "DEX Unpacker / Dumper"),
        RiskyPkgRule("top.niunaijun.blackdexa32", "BlackDex 32", "DEX Unpacker / Dumper"),
        RiskyPkgRule("top.niunaijun.blackdexa64", "BlackDex 64", "DEX Unpacker / Dumper"),

        // Patchers, Cloners & Runtime Hooking Frameworks
        RiskyPkgRule("com.chelpus.lackypatch", "Lucky Patcher", "APK Patcher / Cracker"),
        RiskyPkgRule("com.dimonvideo.luckypatcher", "Lucky Patcher", "APK Patcher / Cracker"),
        RiskyPkgRule("com.forpda.lp", "Lucky Patcher", "APK Patcher / Cracker"),
        RiskyPkgRule("ru.aaaaacay.installer", "LP Installer", "APK Patcher / Cracker"),
        RiskyPkgRule("ru.gxbx.installer", "LP Installer", "APK Patcher / Cracker"),
        RiskyPkgRule("com.android.vending.billing.InAppBillingService.COIN", "Lucky Patcher Proxy", "APK Patcher / Cracker"),
        RiskyPkgRule("com.android.vending.billing.InAppBillingService.LACK", "Lucky Patcher Proxy", "APK Patcher / Cracker"),
        RiskyPkgRule("com.android.vending.billing.InAppBillingService.CLON", "Lucky Patcher Proxy", "APK Patcher / Cracker"),
        RiskyPkgRule("com.android.vending.billing.InAppBillingService.LOCK", "Lucky Patcher Proxy", "APK Patcher / Cracker"),
        RiskyPkgRule("com.applisto.appcloner", "App Cloner", "APK Cloner / Modder"),
        RiskyPkgRule("com.applisto.appcloner.premium", "App Cloner Premium", "APK Cloner / Modder"),
        RiskyPkgRule("org.lsposed.lspatch", "LSPatch", "APK Hook Patcher"),
        RiskyPkgRule("org.lsposed.manager", "LSPosed", "Runtime Hook Framework"),
        RiskyPkgRule("io.github.lsposed.manager", "LSPosed", "Runtime Hook Framework"),
        RiskyPkgRule("de.robv.android.xposed.installer", "Xposed Installer", "Runtime Hook Framework"),
        RiskyPkgRule("org.meowcat.edxposed.manager", "EdXposed", "Runtime Hook Framework"),
        RiskyPkgRule("me.weishu.exp", "TaiChi", "Runtime Hook Framework"),
        RiskyPkgRule("io.va.exposed", "VirtualXposed", "Runtime Hook Framework"),
        RiskyPkgRule("io.va.exposed64", "VirtualXposed 64", "Runtime Hook Framework"),
        RiskyPkgRule("catch_.me_.if_.you_.can_", "GameGuardian", "Memory Hacker"),

        // Packet Sniffers & HTTPS MITM Capture Tools
        RiskyPkgRule("com.guoshi.httpcanary", "HttpCanary", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.guoshi.httpcanary.premium", "HttpCanary Premium", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.guoshi.httpcanary.x", "HttpCanary X", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.reqable.android", "Reqable", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.emanuelef.remote_capture", "PCAPdroid", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.emanuelef.remote_capture.debug", "PCAPdroid", "Packet Sniffer / MITM"),
        RiskyPkgRule("app.greyshirts.sslcapture", "Packet Capture", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.minhui.networkcapture", "NetCapture", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.minhui.networkcapture.pro", "NetCapture Pro", "Packet Sniffer / MITM"),
        RiskyPkgRule("jp.co.taosoftware.android.packetcapture", "tPacketCapture", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.egorovandreyrm.pcapremote", "PCAP Remote", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.packagesniffer", "Package Sniffer", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.telerik.fiddler", "Fiddler", "Packet Sniffer / MITM"),
        RiskyPkgRule("com.charlesproxy.android", "Charles Proxy", "Packet Sniffer / MITM")
    )

    // Keyword/prefix matchers to catch modded, renamed, VIP, or randomized-package clones (e.g. DPT Kill, NP Manager VIP, Lucky Patcher clones)
    private val RISKY_PACKAGE_PREFIXES = arrayOf(
        "bin.mt.",
        "cc.binmt.",
        "np.manager",
        "com.np.manager",
        "com.nm.manager",
        "com.guoshi.httpcanary",
        "com.reqable.",
        "org.lsposed.",
        "com.gmail.heagoo.apkeditor",
        "top.niunaijun.blackdex",
        "com.luoye.dpt",
        "com.dpt."
    )

    private val RISKY_LABEL_KEYWORDS = arrayOf(
        "mt manager",
        "np manager",
        "dpt kill",
        "dpt-kill",
        "dptkill",
        "dpt shell",
        "apktool m",
        "apk editor",
        "apkeditor",
        "antisplit",
        "lucky patcher",
        "lp installer",
        "httpcanary",
        "reqable",
        "pcapdroid",
        "packet capture",
        "ssl capture",
        "netcapture",
        "lspatch",
        "lsposed",
        "edxposed",
        "virtualxposed",
        "blackdex",
        "dumpdex",
        "gameguardian",
        "app cloner"
    )

    fun init(context: Context) {
        val ctx = context.applicationContext ?: context
        appContext = ctx
        verifyIntegrityAndDeriveKey(ctx)
        scanRiskyModTools(ctx)
    }

    fun isTampered(): Boolean {
        if (cachedMasterKey == null) {
            verifyIntegrityAndDeriveKey(resolveContext())
        }
        return _tamperReason.value != null
    }

    fun hasBlockedModTools(): Boolean {
        val ctx = resolveContext()
        if (ctx != null && !isJvmTestEnvironment()) {
            return scanRiskyModTools(ctx).isNotEmpty()
        }
        return _blockedModTools.value.isNotEmpty()
    }

    /**
     * Scans installed apps for any APK/DEX/XML editor, DPT unpacker, signature killer,
     * hook framework, or packet sniffer (both exact package match and renamed-clone launcher label match).
     * Never blocks normal proxy VPNs (SuperProxy / AetherProxy) or regular user apps.
     */
    fun scanRiskyModTools(context: Context): List<BlockedModTool> {
        if (isJvmTestEnvironment()) {
            _blockedModTools.value = emptyList()
            return emptyList()
        }
        val detectedMap = LinkedHashMap<String, BlockedModTool>()
        val pm = context.packageManager

        // 1. Direct package lookup (catches even non-launcher background packages like LSPosed / LP Proxy)
        for (rule in RISKY_PACKAGE_RULES) {
            try {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(rule.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(rule.packageName, 0)
                }
                val label = try {
                    pkgInfo.applicationInfo?.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                        ?: rule.defaultName
                } catch (_: Throwable) {
                    rule.defaultName
                }
                detectedMap[rule.packageName] = BlockedModTool(
                    displayName = label,
                    packageName = rule.packageName,
                    riskCategory = rule.category
                )
            } catch (_: Throwable) {
                // Not installed
            }
        }

        // 2. Launcher intent scan (catches renamed/VIP/randomized-package clones like DPT Kill, NP Manager VIP, Lucky Patcher)
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolvedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
            for (ri in resolvedApps) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == EXPECTED_PKG || detectedMap.containsKey(pkg)) continue
                val pkgLower = pkg.lowercase()
                val label = try {
                    ri.loadLabel(pm)?.toString()?.trim() ?: ""
                } catch (_: Throwable) {
                    ""
                }
                val labelLower = label.lowercase()

                val prefixHit = RISKY_PACKAGE_PREFIXES.any { pkgLower.startsWith(it) }
                val labelHit = RISKY_LABEL_KEYWORDS.any { labelLower.contains(it) }
                val dptHit = pkgLower.contains("dptkill") || pkgLower.contains("dpt_kill") ||
                    (pkgLower.contains("dpt") && (pkgLower.contains("kill") || pkgLower.contains("shell") || pkgLower.contains("unpack")))

                if (prefixHit || labelHit || dptHit) {
                    val category = when {
                        labelLower.contains("canary") || labelLower.contains("reqable") ||
                            labelLower.contains("pcap") || labelLower.contains("capture") -> "Packet Sniffer / MITM"
                        labelLower.contains("dpt") || dptHit || labelLower.contains("dex") -> "DEX Unpacker / Mod Tool"
                        labelLower.contains("patch") || labelLower.contains("xposed") ||
                            labelLower.contains("lsposed") || labelLower.contains("guardian") -> "Patcher / Hook Tool"
                        else -> "APK / DEX / XML Editor"
                    }
                    detectedMap[pkg] = BlockedModTool(
                        displayName = label.ifBlank { pkg },
                        packageName = pkg,
                        riskCategory = category
                    )
                }
            }
        } catch (_: Throwable) {
        }

        val result = detectedMap.values.toList()
        _blockedModTools.value = result
        return result
    }

    /**
     * Immediately kills the tampered process using multiple un-catchable termination paths.
     * Never triggered on clean unmodified official builds or JVM tests.
     */
    fun forceCrashNow(reason: String = "ERR_TAMPER"): Nothing {
        _tamperReason.value = reason
        // Poison master key so even if crash calls are patched out in smali, Vault returns garbage
        cachedMasterKey = sha256("POISONED_${System.nanoTime()}_$reason".toByteArray(Charsets.UTF_8))

        if (isJvmTestEnvironment()) {
            throw SecurityException(reason)
        }

        // Fire an uncaught fatal Error on a separate daemon thread so Activity try/catch blocks cannot swallow it
        try {
            val killerThread = Thread {
                try {
                    Thread.sleep(120L)
                } catch (_: Throwable) {}
                try {
                    Process.killProcess(Process.myPid())
                } catch (_: Throwable) {}
                try {
                    Runtime.getRuntime().halt(1)
                } catch (_: Throwable) {}
                throw AssertionError(reason)
            }
            killerThread.isDaemon = false
            killerThread.start()
        } catch (_: Throwable) {}

        try {
            Process.killProcess(Process.myPid())
        } catch (_: Throwable) {}
        try {
            Runtime.getRuntime().halt(1)
        } catch (_: Throwable) {}
        exitProcess(1)
    }

    /**
     * Verifies rendered MainScreen App Title + Developer Credit text against cryptographic SHA-256 hash.
     * If anyone edits even 1 character of "Developed By Ismail Islam Rony" or "Work ShortCut" in smali/DEX,
     * the app immediately crashes.
     */
    fun verifyRenderedMainBrandingOrCrash(appTitle: String, devCredit: String) {
        if (isJvmTestEnvironment()) return
        if (isTampered()) {
            forceCrashNow(_tamperReason.value ?: "ERR_TAMPER_MAIN")
        }
        val liveHash = bytesToHex(sha256("$appTitle\u001F$devCredit".toByteArray(Charsets.UTF_8)))
        if (liveHash != EXPECTED_MAIN_BRAND_SHA256) {
            forceCrashNow("ERR_DEV_BRAND_MODIFIED")
        }
    }

    /**
     * Verifies rendered LoginScreen Developer Contact + Telegram handle + URL against cryptographic SHA-256 hash.
     * If anyone edits even 1 character of "@ismailislamrony1" or Telegram link in smali/DEX,
     * the app immediately crashes.
     */
    fun verifyRenderedLoginBrandingOrCrash(devContact: String, devHandle: String, devUrl: String) {
        if (isJvmTestEnvironment()) return
        if (isTampered()) {
            forceCrashNow(_tamperReason.value ?: "ERR_TAMPER_LOGIN")
        }
        val liveHash = bytesToHex(sha256("$devContact\u001F$devHandle\u001F$devUrl".toByteArray(Charsets.UTF_8)))
        if (liveHash != EXPECTED_LOGIN_BRAND_SHA256) {
            forceCrashNow("ERR_DEV_CONTACT_MODIFIED")
        }
    }

    /**
     * Live Anti-Sniffer Check called on every network request inside [SecureHttpClient]:
     * 1. Detects active user-installed MITM CA certificates (HttpCanary / Reqable / Burp / Charles / Fiddler / PCAPdroid / mitmproxy).
     * 2. Detects active packet-sniffer capture apps running alongside a non-app VPN tunnel or local MITM proxy.
     * Note: NEVER blocks the app's own SuperProxyVpnService or normal proxy VPNs like AetherProxy.
     */
    fun isPacketSnifferActive(): Boolean {
        if (isJvmTestEnvironment()) return false
        val ctx = resolveContext() ?: return false
        return detectSnifferUserCaCert() || detectActiveSnifferVpnOrProxy(ctx)
    }

    private fun detectSnifferUserCaCert(): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            val snifferCaMarkers = arrayOf(
                "httpcanary",
                "reqable",
                "portswigger",
                "burp",
                "charles proxy",
                "do_not_trust_fiddlerroot",
                "fiddler",
                "mitmproxy",
                "pcapdroid",
                "packet capture",
                "proxyman",
                "sslcapture",
                "netcapture"
            )
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement() ?: continue
                if (!alias.startsWith("user:", ignoreCase = true)) continue
                val cert = ks.getCertificate(alias) as? X509Certificate ?: continue
                val subject = (cert.subjectDN?.name ?: "").lowercase()
                val issuer = (cert.issuerDN?.name ?: "").lowercase()
                val combined = "$subject $issuer"
                for (marker in snifferCaMarkers) {
                    if (combined.contains(marker)) {
                        return true
                    }
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun detectActiveSnifferVpnOrProxy(context: Context): Boolean {
        try {
            val snifferPackages = arrayOf(
                "com.guoshi.httpcanary",
                "com.guoshi.httpcanary.premium",
                "com.guoshi.httpcanary.x",
                "com.reqable.android",
                "com.emanuelef.remote_capture",
                "com.emanuelef.remote_capture.debug",
                "app.greyshirts.sslcapture",
                "com.minhui.networkcapture",
                "com.minhui.networkcapture.pro",
                "jp.co.taosoftware.android.packetcapture",
                "com.egorovandreyrm.pcapremote",
                "com.packagesniffer"
            )
            val pm = context.packageManager
            var hasInstalledSniffer = false
            for (pkg in snifferPackages) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                    hasInstalledSniffer = true
                    break
                } catch (_: Throwable) {
                }
            }

            if (hasInstalledSniffer) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = cm?.activeNetwork
                val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
                val vpnTransportActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                val ourOwnVpnRunning = com.example.service.SuperProxyVpnService.isVpnActive()
                if (vpnTransportActive && !ourOwnVpnRunning) {
                    return true
                }
            }
        } catch (_: Throwable) {
        }
        return false
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
        cachedMasterKey?.let { existing ->
            if (_tamperReason.value != null && !isJvmTestEnvironment()) {
                forceCrashNow(_tamperReason.value ?: "ERR_TAMPER_RECHECK")
            }
            return existing
        }

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

        // 3. Check for Xposed / LSPosed / Frida / Substrate / DPT / Debugger in memory & ClassLoader
        if (detectedIssue == null && detectRuntimeHooksAndMemoryInjection()) {
            detectedIssue = "ERR_RUNTIME_HOOK_DETECTED"
        }

        // 4. Extract Live Signing Certificate SHA-256 from PackageManager AND raw APK ZIP
        val pmCertHex = extractPackageManagerCertSha256(context)
        val zipCertHex = extractApkZipCertSha256(context)

        val liveCertHex = when {
            pmCertHex.isNotEmpty() && zipCertHex.isNotEmpty() && pmCertHex != zipCertHex -> {
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

        if (detectedIssue != null) {
            val poisonedSeed = "${liveCertHex}_POISON_${detectedIssue}:$livePkg:WS_SHIELD_2026_V9"
            cachedMasterKey = sha256(poisonedSeed.toByteArray(Charsets.UTF_8))
            _tamperReason.value = detectedIssue
            forceCrashNow(detectedIssue)
        }

        // Derive the cryptographic master key directly from the live verified certificate + package
        val seed = "$liveCertHex:$livePkg:WS_SHIELD_2026_V9"
        val key = sha256(seed.toByteArray(Charsets.UTF_8))
        cachedMasterKey = key

        // 5. Cryptographic Seal Check over ALL API URLs, API Keys, Developer Name, Telegram, strings.xml & Manifest label!
        //    If even ONE character of any API URL, API key, Developer Name, or XML app_name was modified -> CRASH IMMEDIATELY!
        if (!verifyVaultAndBrandSeal(context)) {
            val sealError = "ERR_API_OR_DEV_MODIFIED"
            cachedMasterKey = sha256("${liveCertHex}_POISON_$sealError:$livePkg".toByteArray(Charsets.UTF_8))
            _tamperReason.value = sealError
            forceCrashNow(sealError)
        }

        _tamperReason.value = null
        return key
    }

    /**
     * Computes live SHA-256 over every decrypted API URL/token/header, every Developer Name/Contact string,
     * strings.xml R.string.app_name, and AndroidManifest application label.
     * Any 1-byte or 1-character change anywhere breaks this seal and immediately kills the app.
     */
    private fun verifyVaultAndBrandSeal(context: Context): Boolean {
        return try {
            val resAppName = context.getString(com.example.R.string.app_name).trim()
            val manifestLabel = context.applicationInfo.loadLabel(context.packageManager).toString().trim()
            val joined = listOf(
                SecureVault.supabaseBaseUrl,
                SecureVault.supabaseKey,
                SecureVault.unixDefaultToken,
                SecureVault.lamixDefaultToken,
                SecureVault.hadiDefaultToken,
                SecureVault.hadiBaseUrl,
                SecureVault.lamixBaseUrl,
                SecureVault.unixBaseUrl,
                SecureVault.zenexDefaultKey,
                SecureVault.zenexGetNumUrl,
                SecureVault.zenexOtpUrl,
                SecureVault.zenexRangesUrl,
                SecureVault.zenexBroadcastUrl,
                SecureVault.zenexHeader,
                SecureVault.mkBaseUrl,
                SecureVault.mkDefaultKey,
                SecureVault.mkHeader,
                SecureVault.slBaseUrl,
                SecureVault.slDefaultKey,
                SecureVault.slHeader,
                SecureVault.adminPass,
                SecureVault.devCreditText,
                SecureVault.devContactText,
                SecureVault.devTelegramHandle,
                SecureVault.devTelegramUrl,
                SecureVault.appDisplayTitle,
                resAppName,
                manifestLabel
            ).joinToString("\u001F")
            val liveSeal = bytesToHex(sha256(joined.toByteArray(Charsets.UTF_8)))
            liveSeal == EXPECTED_INTEGRITY_SEAL_SHA256
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("PrivateApi")
    private fun detectSignatureKillerOrPmsHook(context: Context): Boolean {
        try {
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
                "Pine",
                "DptApplication",
                "luoye.dpt"
            )
            if (forbiddenAppTokens.any {
                    appClassName.contains(it, ignoreCase = true) ||
                        actualAppClass.contains(it, ignoreCase = true)
                }
            ) {
                return true
            }

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
        } catch (_: Throwable) {
        }
        return false
    }

    private fun detectRuntimeHooksAndMemoryInjection(): Boolean {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return true
            }

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
                    "bin.mt.signature",
                    "libdpt",
                    "libDexHelper",
                    "liblspd"
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
 * Cryptographic Vault that decrypts endpoints, headers, API keys, and developer branding
 * on-the-fly using the certificate-bound master key from [AppSecurityShield].
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
    // DEV_CREDIT_TEXT (salt=122)
    private val ENC_DEV_CREDIT_TEXT = intArrayOf(36, 74, 252, 41, 153, 31, 62, 66, 126, 184, 250, 178, 237, 17, 69, 227, 137, 242, 245, 25, 30, 187, 167, 133, 205, 213, 182, 64, 79, 48)
    // DEV_CONTACT_TEXT (salt=123)
    private val ENC_DEV_CONTACT_TEXT = intArrayOf(98, 109, 31, 43, 125, 151, 105, 71, 165, 23, 147, 202, 253, 127, 166, 176, 151, 211, 160, 181, 254, 175, 123, 106, 252, 144, 74, 95, 166, 47, 142, 236, 46, 140, 104, 142)
    // DEV_TELEGRAM_HANDLE (salt=124)
    private val ENC_DEV_TELEGRAM_HANDLE = intArrayOf(122, 159, 240, 36, 199, 59, 8, 118, 118, 153, 242, 181, 50, 195, 254, 105, 89)
    // DEV_TELEGRAM_URL (salt=125)
    private val ENC_DEV_TELEGRAM_URL = intArrayOf(216, 60, 152, 64, 43, 217, 242, 100, 242, 248, 8, 211, 196, 73, 118, 31, 45, 113, 155, 39, 124, 86, 49, 56, 30, 148, 190, 12, 47)
    // APP_DISPLAY_TITLE (salt=126)
    private val ENC_APP_DISPLAY_TITLE = intArrayOf(57, 191, 214, 147, 146, 152, 149, 65, 41, 146, 59, 78, 208)

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

    val devCreditText: String get() = unlock(ENC_DEV_CREDIT_TEXT, 122)
    val devContactText: String get() = unlock(ENC_DEV_CONTACT_TEXT, 123)
    val devTelegramHandle: String get() = unlock(ENC_DEV_TELEGRAM_HANDLE, 124)
    val devTelegramUrl: String get() = unlock(ENC_DEV_TELEGRAM_URL, 125)
    val appDisplayTitle: String get() = unlock(ENC_APP_DISPLAY_TITLE, 126)

    /**
     * Encrypts a custom API token before saving to SharedPreferences so shared_prefs XML files
     * on disk never expose plaintext tokens to rooted users.
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
 * Hardened OkHttpClient factory that enforces Proxy.NO_PROXY, crashes on APK tampering,
 * and blocks requests whenever packet sniffers or risky modding/editing tools are installed.
 */
object SecureHttpClient {

    private val securityInterceptor = Interceptor { chain ->
        if (AppSecurityShield.isTampered()) {
            AppSecurityShield.forceCrashNow("ERR_NET_TAMPER")
        }
        if (AppSecurityShield.hasBlockedModTools() || AppSecurityShield.isPacketSnifferActive()) {
            throw IOException("SEC_SHIELD_BLOCK")
        }
        try {
            if (!System.getProperty("http.proxyHost").isNullOrBlank()) {
                System.clearProperty("http.proxyHost")
                System.clearProperty("http.proxyPort")
            }
            if (!System.getProperty("https.proxyHost").isNullOrBlank()) {
                System.clearProperty("https.proxyHost")
                System.clearProperty("https.proxyPort")
            }
        } catch (_: Throwable) {}
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
