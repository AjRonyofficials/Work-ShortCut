package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.util.ProxyTester
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket

/**
 * Super Proxy style VpnService based on the proven #132 architecture,
 * upgraded with off-main-thread DNS pre-resolution and pure MapDNS (198.18.0.2)
 * to eliminate the 10-second initial website loading delay.
 */
class SuperProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val vpnMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        val profileName = intent?.getStringExtra(EXTRA_PROFILE_NAME) ?: "Proxy"
        val server = intent?.getStringExtra(EXTRA_SERVER) ?: "127.0.0.1"
        val port = intent?.getIntExtra(EXTRA_PORT, 1080) ?: 1080
        val protocol = intent?.getStringExtra(EXTRA_PROTOCOL) ?: "SOCKS5"
        val user = intent?.getStringExtra(EXTRA_USER) ?: ""
        val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""
        val allowedApps = intent?.getStringArrayListExtra(EXTRA_ALLOWED_APPS) ?: arrayListOf<String>()

        startForegroundNotification(profileName, server, port)
        serviceScope.launch {
            vpnMutex.withLock {
                startVpnInternal(profileName, server, port, protocol, user, pass, allowedApps)
            }
        }

        return START_STICKY
    }

    private fun startVpnInternal(
        profileName: String,
        server: String,
        port: Int,
        protocol: String,
        user: String,
        pass: String,
        allowedApps: List<String>
    ) {
        if (isRunning || vpnInterface != null) {
            cleanUpTunnel()
        }

        try {
            // 1. Resolve server hostname to IPv4 on IO thread BEFORE establishing VPN!
            // In #132 this ran on the main thread and threw NetworkOnMainThreadException, causing a 10s delay.
            val cleanServer = server.trim()
            val resolvedServerIp = try {
                val all = InetAddress.getAllByName(cleanServer)
                all.firstOrNull { it is Inet4Address }?.hostAddress
                    ?: all.firstOrNull()?.hostAddress
                    ?: ProxyTester.resolveHostBypassingVpn(cleanServer)
            } catch (_: Exception) {
                ProxyTester.resolveHostBypassingVpn(cleanServer)
            }

            // 2. Proven #132 TUN configuration, using ONLY 198.18.0.2 (MapDNS) so Android
            // never stalls for 10 seconds probing 8.8.8.8 / 1.1.1.1 outside MapDNS.
            val builder = Builder()
                .setSession("SuperProxy: $profileName")
                .setMtu(1400)
                .addAddress("198.18.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addRoute("240.0.0.0", 4)
                .addDnsServer("198.18.0.2")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    builder.setMetered(false)
                } catch (_: Exception) {}
            }

            // 3. App Routing (Split Tunneling) & Loop Prevention
            val cleanAllowed = allowedApps.map { it.trim() }.filter { it.isNotEmpty() && it != packageName }.distinct()
            if (cleanAllowed.isNotEmpty()) {
                var addedCount = 0
                for (pkg in cleanAllowed) {
                    try {
                        builder.addAllowedApplication(pkg)
                        addedCount++
                    } catch (_: Exception) {}
                }
                if (addedCount == 0) {
                    try {
                        builder.addDisallowedApplication(packageName)
                    } catch (_: Exception) {}
                }
            } else {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {}
            }

            vpnInterface = builder.establish()
            val pfd = vpnInterface ?: throw IllegalStateException("TUN descriptor invalid")
            val tunFd = pfd.fd

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    setUnderlyingNetworks(null)
                }
            } catch (_: Exception) {}

            // 4. Protocol & Authentication Handling (#132 architecture):
            // Direct native C connection for SOCKS5; high-speed CachedThreadPool bridge for HTTP/HTTPS.
            val isHttp = protocol.equals("HTTP", ignoreCase = true) || protocol.equals("HTTPS", ignoreCase = true)
            val finalServerIp: String
            val finalPort: Int
            val finalUser: String
            val finalPass: String

            if (isHttp) {
                val bridgePort = LocalSocks5ToHttpBridge.start(
                    httpHost = resolvedServerIp,
                    httpPort = port,
                    username = user,
                    password = pass
                )
                if (bridgePort <= 0) {
                    throw IllegalStateException("Failed to bind LocalSocks5ToHttpBridge on 127.0.0.1")
                }
                finalServerIp = "127.0.0.1"
                finalPort = bridgePort
                finalUser = ""
                finalPass = ""
            } else {
                LocalSocks5ToHttpBridge.stop()
                finalServerIp = resolvedServerIp
                finalPort = port
                finalUser = user
                finalPass = pass
            }

            // 5. Generate #132 YAML configuration for hev-socks5-tunnel with 5000ms connect-timeout
            val configFile = File(cacheDir, "hev-socks5.conf")
            val sb = java.lang.StringBuilder()
            sb.append("tunnel:\n")
            sb.append("  name: tun0\n")
            sb.append("  mtu: 1400\n")
            sb.append("  ipv4: 198.18.0.1\n")
            sb.append("  icmp: 'reply'\n")
            sb.append("\n")
            sb.append("socks5:\n")
            sb.append("  port: ").append(finalPort).append("\n")
            sb.append("  address: '").append(finalServerIp).append("'\n")
            sb.append("  udp: 'udp'\n")
            if (finalUser.isNotBlank() && finalPass.isNotBlank()) {
                val safeUser = finalUser.replace("'", "''")
                val safePass = finalPass.replace("'", "''")
                sb.append("  username: '").append(safeUser).append("'\n")
                sb.append("  password: '").append(safePass).append("'\n")
            }
            sb.append("\n")
            sb.append("mapdns:\n")
            sb.append("  address: 198.18.0.2\n")
            sb.append("  port: 53\n")
            sb.append("  network: 240.0.0.0\n")
            sb.append("  netmask: 240.0.0.0\n")
            sb.append("  cache-size: 10000\n")
            sb.append("\n")
            sb.append("misc:\n")
            sb.append("  task-stack-size: 81920\n")
            sb.append("  tcp-buffer-size: 65536\n")
            sb.append("  connect-timeout: 5000\n")
            sb.append("  tcp-read-write-timeout: 120000\n")
            sb.append("  udp-read-write-timeout: 15000\n")
            sb.append("  limit-nofile: 65535\n")

            FileOutputStream(configFile).use { it.write(sb.toString().toByteArray()) }
            configFile.setReadable(true, false)

            // 6. Launch native tun2socks engine
            serviceScope.launch {
                try {
                    TProxyService.TProxyStartService(configFile.absolutePath, tunFd)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopVpn()
        }
    }

    private fun startForegroundNotification(profileName: String, server: String, port: Int) {
        val channelId = "super_proxy_vpn_channel"
        val channelName = "Super Proxy Tunnel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active Super Proxy connection"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, SuperProxyVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPending = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Super Proxy Active: $profileName")
            .setContentText("Routing device traffic through $server:$port")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPending)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(102, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(102, notification)
            } catch (_: Exception) {}
        }
    }

    private fun cleanUpTunnel() {
        isRunning = false
        LocalSocks5ToHttpBridge.stop()
        try {
            TProxyService.TProxyStopService()
        } catch (_: Throwable) {}
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}
    }

    private fun stopVpn() {
        if (!isRunning && vpnInterface == null) return
        cleanUpTunnel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
        stopVpn()
    }

    companion object {
        const val ACTION_START = "com.example.service.SuperProxyVpnService.START"
        const val ACTION_STOP = "com.example.service.SuperProxyVpnService.STOP"

        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_SERVER = "server"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"
        const val EXTRA_ALLOWED_APPS = "allowed_apps"

        @Volatile
        private var activeInstance: SuperProxyVpnService? = null

        fun isTunnelActive(): Boolean = activeInstance?.isRunning == true

        fun protectSocket(socket: Socket?): Boolean {
            if (socket == null) return false
            return try {
                activeInstance?.protect(socket) ?: false
            } catch (_: Throwable) {
                false
            }
        }

        fun protectSocket(socket: DatagramSocket?): Boolean {
            if (socket == null) return false
            return try {
                activeInstance?.protect(socket) ?: false
            } catch (_: Throwable) {
                false
            }
        }

        fun protectSocket(socketFd: Int): Boolean {
            if (socketFd <= 0) return false
            return try {
                activeInstance?.protect(socketFd) ?: false
            } catch (_: Throwable) {
                false
            }
        }

        fun start(
            context: Context,
            profileName: String,
            server: String,
            port: Int,
            protocol: String = "SOCKS5",
            user: String = "",
            pass: String = "",
            allowedApps: List<String> = emptyList()
        ) {
            try {
                val intent = Intent(context, SuperProxyVpnService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PROFILE_NAME, profileName)
                    putExtra(EXTRA_SERVER, server)
                    putExtra(EXTRA_PORT, port)
                    putExtra(EXTRA_PROTOCOL, protocol)
                    putExtra(EXTRA_USER, user)
                    putExtra(EXTRA_PASS, pass)
                    putStringArrayListExtra(EXTRA_ALLOWED_APPS, ArrayList(allowedApps))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SuperProxyVpnService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
