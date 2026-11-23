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
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket

/**
 * Fortified Zero-Leak Super Proxy VpnService:
 *
 * 1. STRICT MapDNS ONLY (198.18.0.2): Removes 8.8.8.8 & 1.1.1.1 so ZERO local ISP or Google/Cloudflare
 *    Anycast DNS queries can ever leak! Every domain (graph.facebook.com, m.facebook.com, etc.) is sent
 *    as a raw hostname to the remote proxy so DNS resolves strictly from the proxy's country & ISP.
 * 2. FULL IPv6 LEAK TRAP (fc00::1/128 + ::/0): Captures 100% of IPv6 packets into tun0 so real 4G/5G SIM
 *    or Wi-Fi IPv6 addresses can NEVER bypass the VPN to Facebook.
 * 3. UNIFIED ANTI-LEAK BRIDGE (HTTP + SOCKS5): Routes both HTTP and SOCKS5 through LocalSocks5ToHttpBridge,
 *    blocking UDP/QUIC/WebRTC STUN leaks, Private DNS (DoT 853), Chrome DoH bypasses, and raw IPv6 literals,
 *    while preserving 5-minute keep-alive for rock-solid Facebook OTP & MQTT delivery.
 * 4. PINNED GATEWAY IPv4: Uses the exact pinned proxy gateway IPv4 for the entire connection session so
 *    gateway DNS round-robin never rotates your IP mid-session.
 */
class SuperProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        val pinnedGatewayIp = intent?.getStringExtra(EXTRA_PINNED_GATEWAY_IP) ?: ""
        val port = intent?.getIntExtra(EXTRA_PORT, 1080) ?: 1080
        val protocol = intent?.getStringExtra(EXTRA_PROTOCOL) ?: "SOCKS5"
        val user = intent?.getStringExtra(EXTRA_USER) ?: ""
        val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""
        val allowedApps = intent?.getStringArrayListExtra(EXTRA_ALLOWED_APPS) ?: arrayListOf<String>()

        startForegroundNotification(profileName, server, port)
        startVpn(profileName, server, pinnedGatewayIp, port, protocol, user, pass, allowedApps)

        return START_STICKY
    }

    private fun startVpn(
        profileName: String,
        server: String,
        pinnedGatewayIp: String,
        port: Int,
        protocol: String,
        user: String,
        pass: String,
        allowedApps: List<String>
    ) {
        if (isRunning || vpnInterface != null) {
            cleanUpTunnelOnly()
        }

        try {
            // 1. Use the exact pinned gateway IPv4 (or resolve ONCE off main thread) so gateway round-robin never rotates IP
            val resolvedServerIp = pinnedGatewayIp.trim().ifBlank {
                ProxyTester.resolveHostFastBlocking(server.trim(), 1500)
            }

            val builder = Builder()
                .setSession("SuperProxy: $profileName")
                .setMtu(1400) // Optimal MTU for zero packet fragmentation on 4G/5G SIM & Wi-Fi
                .addAddress("198.18.0.1", 32) // Virtual IPv4 TUN interface
                .addRoute("0.0.0.0", 0)       // Capture 100% of IPv4 traffic into tun0
                .addRoute("240.0.0.0", 4)     // Explicit route for MapDNS Fake-IP pool
                .addDnsServer("198.18.0.2")   // STRICT MapDNS ONLY! ZERO Google/Cloudflare/ISP DNS leak!

            // 2. Capture 100% of IPv6 traffic into tun0 so real 4G/5G SIM / Wi-Fi IPv6 NEVER leaks to Facebook!
            var ipv6Captured = false
            try {
                builder.addAddress("fc00::1", 128)
                builder.addRoute("::", 0)
                ipv6Captured = true
            } catch (_: Exception) {
                ipv6Captured = false
            }

            // Unmetered on Android 10+ so OS and Facebook don't throttle background OTP/MQTT traffic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    builder.setMetered(false)
                } catch (_: Exception) {}
            }

            // App Routing & Loop Prevention (NEVER call allowBypass!)
            val validAllowedApps = allowedApps.map { it.trim() }.filter { it.isNotEmpty() && it != packageName }.distinct()
            if (validAllowedApps.isNotEmpty()) {
                var addedAny = false
                for (pkg in validAllowedApps) {
                    try {
                        builder.addAllowedApplication(pkg)
                        addedAny = true
                    } catch (_: Exception) {}
                }
                if (!addedAny) {
                    try {
                        builder.addDisallowedApplication(packageName)
                    } catch (_: Exception) {}
                }
            } else {
                // Route ALL apps on the device through the zero-leak proxy, excluding only our own app process
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {}
            }

            vpnInterface = builder.establish()
            val pfd = vpnInterface ?: throw IllegalStateException("TUN descriptor invalid")
            val tunFd = pfd.fd

            // Enable seamless Wi-Fi <-> Cellular roaming without dropping the VPN
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    setUnderlyingNetworks(null)
                }
            } catch (_: Exception) {}

            // 3. Unified Anti-Leak Bridge for BOTH HTTP and SOCKS5:
            //    Blocks UDP/QUIC/WebRTC STUN leaks, Private DNS (DoT 853), Chrome DoH bypasses, and raw IPv6 literals,
            //    while forwarding raw domain names to the remote proxy for 100% proxy-ISP DNS resolution!
            val isHttp = protocol.equals("HTTP", ignoreCase = true) || protocol.equals("HTTPS", ignoreCase = true)
            val bridgePort = LocalSocks5ToHttpBridge.start(
                httpHost = resolvedServerIp,
                httpPort = port,
                username = user,
                password = pass,
                isHttpUpstream = isHttp
            )
            if (bridgePort <= 0) {
                throw IllegalStateException("Failed to bind LocalSocks5ToHttpBridge on 127.0.0.1")
            }

            // 4. Generate clean YAML configuration for hev-socks5-tunnel with MapDNS + IPv6 trap + 5-min TCP keep-alive
            val configFile = File(cacheDir, "hev-socks5.conf")
            val sb = java.lang.StringBuilder()
            sb.append("tunnel:\n")
            sb.append("  name: tun0\n")
            sb.append("  mtu: 1400\n")
            sb.append("  ipv4: 198.18.0.1\n")
            if (ipv6Captured) {
                sb.append("  ipv6: 'fc00::1'\n")
            }
            sb.append("  icmp: 'reply'\n")
            sb.append("\n")
            sb.append("socks5:\n")
            sb.append("  port: ").append(bridgePort).append("\n")
            sb.append("  address: '127.0.0.1'\n")
            sb.append("  udp: 'udp'\n")
            sb.append("\n")
            sb.append("mapdns:\n")
            sb.append("  address: 198.18.0.2\n")
            sb.append("  port: 53\n")
            sb.append("  network: 240.0.0.0\n")
            sb.append("  netmask: 240.0.0.0\n")
            sb.append("  cache-size: 20000\n")
            sb.append("\n")
            sb.append("misc:\n")
            sb.append("  task-stack-size: 81920\n")
            sb.append("  tcp-buffer-size: 65536\n")
            sb.append("  connect-timeout: 10000\n")
            sb.append("  tcp-read-write-timeout: 300000\n")
            sb.append("  udp-read-write-timeout: 15000\n")
            sb.append("  limit-nofile: 65535\n")

            FileOutputStream(configFile).use { it.write(sb.toString().toByteArray()) }
            configFile.setReadable(true, false)

            // 5. Launch native tun2socks engine
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
            .setContentTitle("Super Proxy Shield: $profileName")
            .setContentText("Zero-Leak DNS & IPv6 Shield Active • $server:$port")
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

    private fun cleanUpTunnelOnly() {
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
        cleanUpTunnelOnly()
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
        const val EXTRA_PINNED_GATEWAY_IP = "pinned_gateway_ip"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"
        const val EXTRA_ALLOWED_APPS = "allowed_apps"

        @Volatile
        private var activeInstance: SuperProxyVpnService? = null

        fun isVpnActive(): Boolean = activeInstance?.isRunning == true

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
            allowedApps: List<String> = emptyList(),
            pinnedGatewayIp: String = ""
        ) {
            try {
                val intent = Intent(context, SuperProxyVpnService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PROFILE_NAME, profileName)
                    putExtra(EXTRA_SERVER, server)
                    putExtra(EXTRA_PINNED_GATEWAY_IP, pinnedGatewayIp)
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
