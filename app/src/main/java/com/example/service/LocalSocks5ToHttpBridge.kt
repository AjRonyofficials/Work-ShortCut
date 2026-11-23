package com.example.service

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified Zero-Leak Local Proxy Bridge (Supports BOTH HTTP and SOCKS5 Upstreams):
 *
 * Accepts local SOCKS5 connections (No-Auth) from hev-socks5-tunnel on 127.0.0.1 and:
 * 1. Kills UDP ASSOCIATE (cmd=3) cleanly -> prevents WebRTC IP leaks and forces instant QUIC -> TCP fallback
 *    so Facebook / FB Lite / Chrome never stall or leak real IPs over UDP.
 * 2. Blocks Android Private DNS (DoT port 853), direct raw TCP DNS (port 53), and Chrome built-in DoH
 *    resolvers (8.8.8.8, 1.1.1.1, dns.google, cloudflare-dns.com) so 100% of DNS queries MUST go through
 *    MapDNS (198.18.0.2) and resolve remotely at the proxy's own country & ISP (ZERO DNS leak!).
 * 3. Blocks raw IPv6 literal targets (atyp=4) and WebRTC STUN/TURN TCP ports (3478, 5349, 19302) so
 *    Facebook / Chrome always stay strictly on the proxy IPv4 identity.
 * 4. Forwards clean TCP streams using exact remote domain names (ATYP=0x03 for SOCKS5 / CONNECT host:port
 *    for HTTP) over protected upstream sockets with 5-minute keep-alive for rock-solid Facebook OTP/MQTT.
 */
object LocalSocks5ToHttpBridge {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    @Volatile private var upstreamHost: String = ""
    @Volatile private var upstreamPort: Int = 0
    @Volatile private var upstreamIsHttp: Boolean = true
    @Volatile private var user: String = ""
    @Volatile private var pass: String = ""

    // Known Public DNS / DoH / DoT IPv4 addresses blocked on ALL ports so neither apps nor browsers can bypass MapDNS
    private val BLOCKED_DNS_BYPASS_IPS = setOf(
        "8.8.8.8",
        "8.8.4.4",
        "1.1.1.1",
        "1.0.0.1",
        "1.1.1.2",
        "1.0.0.2",
        "1.1.1.3",
        "1.0.0.3",
        "9.9.9.9",
        "9.9.9.10",
        "9.9.9.11",
        "149.112.112.112",
        "149.112.112.11",
        "208.67.222.222",
        "208.67.220.220",
        "94.140.14.14",
        "94.140.15.15",
        "76.76.2.0",
        "76.76.10.0",
        "45.90.28.0",
        "45.90.30.0",
        "185.228.168.9",
        "185.228.169.9",
        "223.5.5.5",
        "223.6.6.6"
    )

    // Dedicated DoH / DoT / Private-DNS domain suffixes (never blocks normal google.com/youtube.com/facebook.com!)
    private val BLOCKED_DNS_DOMAIN_SUFFIXES = listOf(
        "dns.google",
        "dns.google.com",
        "dns.google.arpa",
        "8888.google",
        "doh.google",
        "doh.google.com",
        "cloudflare-dns.com",
        "one.one.one.one",
        "quad9.net",
        "doh.opendns.com",
        "dns.opendns.com",
        "dns.umbrella.com",
        "cleanbrowsing.org",
        "doh.dns.sb",
        "alidns.com",
        "doh.pub",
        "dns.adguard.com",
        "adguard-dns.com",
        "nextdns.io",
        "mullvad.net",
        "controld.com",
        "doh.tiar.app",
        "dns.switch.ch"
    )

    private val BLOCKED_WEBRTC_STUN_PORTS = setOf(
        3478, 3479, 5349, 5350,
        19302, 19303, 19304, 19305, 19306, 19307, 19308, 19309
    )

    fun start(
        httpHost: String,
        httpPort: Int,
        username: String,
        password: String,
        isHttpUpstream: Boolean = true
    ): Int {
        stop()

        this.upstreamHost = httpHost
        this.upstreamPort = httpPort
        this.upstreamIsHttp = isHttpUpstream
        this.user = username
        this.pass = password

        return try {
            val server = ServerSocket(0, 256, InetAddress.getByName("127.0.0.1"))
            serverSocket = server
            isRunning.set(true)

            val localPort = server.localPort

            scope.launch {
                while (isRunning.get()) {
                    try {
                        val client = server.accept()
                        client.tcpNoDelay = true
                        client.keepAlive = true
                        client.sendBufferSize = 65536
                        client.receiveBufferSize = 65536
                        activeSockets.add(client)
                        scope.launch { handleClient(client) }
                    } catch (_: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }

            localPort
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun isGooglePublicDnsIpRange(ip: String): Boolean {
        // Google Public DNS additional CIDR ranges: 8.34.208.0/20 and 8.35.192.0/20
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        val c = parts[2].toIntOrNull() ?: return false
        if (a != 8) return false
        return (b == 34 && c in 208..223) || (b == 35 && c in 192..207)
    }

    private fun shouldBlockForAntiLeak(atyp: Int, targetHost: String, targetPort: Int): Boolean {
        // 1. Block Android Private DNS (DoT port 853) & direct raw TCP DNS (port 53)
        //    so Android/Chrome/Facebook immediately use MapDNS (198.18.0.2) remote resolution
        if (targetPort == 853 || targetPort == 53) return true

        // 2. Block WebRTC STUN/TURN ports to prevent real IP leakage in browsers & apps
        if (targetPort in BLOCKED_WEBRTC_STUN_PORTS) return true

        // 3. Block raw IPv6 literals so apps & websites never stall or leak IPv6 and cleanly stay on proxy IPv4
        if (atyp == 4) return true

        val hostLower = targetHost.trim().trimEnd('.').lowercase()

        // 4. Block Google LLC & public DNS IPv4 endpoints on ALL ports (kills Chrome Secure DNS / App hardcoded DNS)
        if (hostLower in BLOCKED_DNS_BYPASS_IPS || isGooglePublicDnsIpRange(hostLower)) {
            return true
        }

        // 5. Block Chrome/Browser/Android DoH & DoT domains + subdomains (dns.google, *.dns.google, *.cloudflare-dns.com, etc.)
        if (targetPort == 443 || targetPort == 80 || targetPort == 853 || targetPort == 53) {
            for (suffix in BLOCKED_DNS_DOMAIN_SUFFIXES) {
                if (hostLower == suffix || hostLower.endsWith(".$suffix")) {
                    return true
                }
            }
        }

        return false
    }

    private suspend fun handleClient(client: Socket) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 15000
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            // 1. Local SOCKS5 Method Negotiation (with hev-socks5-tunnel)
            val ver = clientIn.read()
            if (ver != 5) {
                client.close()
                return
            }
            val nMethods = clientIn.read()
            if (nMethods <= 0) {
                client.close()
                return
            }
            val methods = ByteArray(nMethods)
            readFully(clientIn, methods)

            // Select method 0 (No Auth required locally on loopback)
            clientOut.write(byteArrayOf(5, 0))
            clientOut.flush()

            // 2. Local SOCKS5 Request
            val reqVer = clientIn.read()
            val cmd = clientIn.read()
            clientIn.read() // RSV
            val atyp = clientIn.read()

            if (reqVer != 5 || cmd != 1) { // Only TCP CONNECT (cmd=1); cleanly reject UDP ASSOCIATE (cmd=3)
                clientOut.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0)) // Command not supported
                clientOut.flush()
                client.close()
                return
            }

            var rawIpv4Bytes: ByteArray? = null
            var rawDomainBytes: ByteArray? = null

            val targetHost = when (atyp) {
                1 -> { // IPv4
                    val ip = ByteArray(4)
                    readFully(clientIn, ip)
                    rawIpv4Bytes = ip
                    InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                3 -> { // Domain name (resolved remotely by proxy via MapDNS!)
                    val len = clientIn.read()
                    if (len <= 0) "" else {
                        val domainBytes = ByteArray(len)
                        readFully(clientIn, domainBytes)
                        rawDomainBytes = domainBytes
                        String(domainBytes, Charsets.US_ASCII)
                    }
                }
                4 -> { // IPv6
                    val ip6 = ByteArray(16)
                    readFully(clientIn, ip6)
                    InetAddress.getByAddress(ip6).hostAddress ?: ""
                }
                else -> ""
            }

            val p1 = clientIn.read()
            val p2 = clientIn.read()
            val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

            if (targetHost.isEmpty() || targetPort <= 0) {
                clientOut.write(byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0)) // Host unreachable
                clientOut.flush()
                client.close()
                return
            }

            // Anti-Leak Firewall (DoT / DoH / Raw DNS / IPv6 / WebRTC STUN)
            if (shouldBlockForAntiLeak(atyp, targetHost, targetPort)) {
                val repCode: Byte = if (atyp == 4) 4 else 5 // Host unreachable for IPv6, Refused for DoT/DoH/STUN
                clientOut.write(byteArrayOf(5, repCode, 0, 1, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                client.close()
                return
            }

            // 3. Connect to pinned upstream proxy with 1 automatic fast retry for transient residential proxy resets
            var connectedUpstream: Socket? = null
            var handshakeOk = false
            for (attempt in 1..2) {
                var candidate: Socket? = null
                try {
                    candidate = Socket()
                    candidate.tcpNoDelay = true
                    candidate.keepAlive = true
                    candidate.sendBufferSize = 65536
                    candidate.receiveBufferSize = 65536
                    candidate.soTimeout = 15000

                    SuperProxyVpnService.protectSocket(candidate)
                    activeSockets.add(candidate)
                    candidate.connect(InetSocketAddress(upstreamHost, upstreamPort), 9000)

                    val upIn = candidate.getInputStream()
                    val upOut = candidate.getOutputStream()

                    val ok = if (upstreamIsHttp) {
                        performHttpUpstreamHandshake(upIn, upOut, targetHost, targetPort)
                    } else {
                        performSocks5UpstreamHandshake(upIn, upOut, atyp, rawIpv4Bytes, rawDomainBytes, targetHost, targetPort)
                    }

                    if (ok) {
                        connectedUpstream = candidate
                        handshakeOk = true
                        break
                    } else {
                        activeSockets.remove(candidate)
                        try { candidate.close() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    candidate?.let {
                        activeSockets.remove(it)
                        try { it.close() } catch (_: Exception) {}
                    }
                }
                if (attempt == 1 && isRunning.get()) {
                    try { kotlinx.coroutines.delay(140L) } catch (_: Exception) {}
                }
            }

            upstream = connectedUpstream
            if (!handshakeOk || upstream == null) {
                clientOut.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)) // Connection refused
                clientOut.flush()
                client.close()
                try { upstream?.close() } catch (_: Exception) {}
                return
            }

            // 5. Send SOCKS5 Success response to hev-socks5-tunnel
            clientOut.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            // 6. Long-lived keep-alive (5 minutes idle timeout) so Facebook HTTPS / MQTT / OTP sessions never drop
            client.soTimeout = 300000
            upstream.soTimeout = 300000
            client.keepAlive = true
            upstream.keepAlive = true

            // 7. Bidirectional non-blocking data piping
            pipeSocketsNonBlocking(client, upstream)

        } catch (_: Exception) {
        } finally {
            activeSockets.remove(client)
            upstream?.let { activeSockets.remove(it) }
            try { client.close() } catch (_: Exception) {}
            try { upstream?.close() } catch (_: Exception) {}
        }
    }

    private fun performHttpUpstreamHandshake(
        upIn: InputStream,
        upOut: java.io.OutputStream,
        targetHost: String,
        targetPort: Int
    ): Boolean {
        val authHeader = if (user.isNotEmpty() || pass.isNotEmpty()) {
            val token = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "Proxy-Authorization: Basic $token\r\n"
        } else ""

        val formattedHost = if (targetHost.contains(":") && !targetHost.startsWith("[")) "[$targetHost]" else targetHost
        val connectMsg = "CONNECT $formattedHost:$targetPort HTTP/1.1\r\n" +
                "Host: $formattedHost:$targetPort\r\n" +
                authHeader +
                "Proxy-Connection: Keep-Alive\r\n" +
                "Connection: Keep-Alive\r\n\r\n"

        upOut.write(connectMsg.toByteArray(Charsets.US_ASCII))
        upOut.flush()
        return readHttpConnectResponse(upIn)
    }

    private fun performSocks5UpstreamHandshake(
        upIn: InputStream,
        upOut: java.io.OutputStream,
        atyp: Int,
        rawIpv4Bytes: ByteArray?,
        rawDomainBytes: ByteArray?,
        targetHost: String,
        targetPort: Int
    ): Boolean {
        // 1. Method Negotiation (RFC 1928)
        val hasAuth = user.isNotEmpty() || pass.isNotEmpty()
        if (hasAuth) {
            upOut.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            upOut.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        upOut.flush()

        val ver = upIn.read()
        val method = upIn.read()
        if (ver != 0x05 || method == -1 || method == 0xFF) return false

        // 2. Username/Password Subnegotiation (RFC 1929)
        if (method == 0x02) {
            val uBytes = user.toByteArray(Charsets.UTF_8)
            val pBytes = pass.toByteArray(Charsets.UTF_8)
            if (uBytes.size > 255 || pBytes.size > 255) return false
            val authBuf = ByteArray(3 + uBytes.size + pBytes.size)
            authBuf[0] = 0x01
            authBuf[1] = uBytes.size.toByte()
            System.arraycopy(uBytes, 0, authBuf, 2, uBytes.size)
            authBuf[2 + uBytes.size] = pBytes.size.toByte()
            System.arraycopy(pBytes, 0, authBuf, 3 + uBytes.size, pBytes.size)
            upOut.write(authBuf)
            upOut.flush()

            val authVer = upIn.read()
            val authStatus = upIn.read()
            if (authVer == -1 || authStatus != 0x00) return false
        }

        // 3. CONNECT Request (Preserve exact ATYP=0x03 domain so remote proxy resolves DNS!)
        val req: ByteArray = if (atyp == 3) {
            val dBytes = rawDomainBytes ?: targetHost.toByteArray(Charsets.US_ASCII)
            if (dBytes.isEmpty() || dBytes.size > 255) return false
            ByteArray(4 + 1 + dBytes.size + 2).also { buf ->
                buf[0] = 0x05
                buf[1] = 0x01 // CONNECT
                buf[2] = 0x00
                buf[3] = 0x03 // DOMAINNAME -> Remote DNS at Proxy ISP!
                buf[4] = dBytes.size.toByte()
                System.arraycopy(dBytes, 0, buf, 5, dBytes.size)
                buf[5 + dBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
                buf[6 + dBytes.size] = (targetPort and 0xFF).toByte()
            }
        } else {
            val ip4 = rawIpv4Bytes ?: InetAddress.getByName(targetHost).address
            if (ip4.size != 4) return false
            ByteArray(10).also { buf ->
                buf[0] = 0x05
                buf[1] = 0x01 // CONNECT
                buf[2] = 0x00
                buf[3] = 0x01 // IPv4
                System.arraycopy(ip4, 0, buf, 4, 4)
                buf[8] = ((targetPort shr 8) and 0xFF).toByte()
                buf[9] = (targetPort and 0xFF).toByte()
            }
        }

        upOut.write(req)
        upOut.flush()

        // 4. Read Upstream SOCKS5 Reply
        val repVer = upIn.read()
        val repCode = upIn.read()
        upIn.read() // RSV
        val repAtyp = upIn.read()
        if (repVer != 0x05 || repCode != 0x00) return false

        when (repAtyp) {
            0x01 -> readFully(upIn, ByteArray(4))
            0x03 -> {
                val len = upIn.read()
                if (len > 0) readFully(upIn, ByteArray(len))
            }
            0x04 -> readFully(upIn, ByteArray(16))
            else -> return false
        }
        // Consume 2-byte BND.PORT
        val bp1 = upIn.read()
        val bp2 = upIn.read()
        return bp1 != -1 && bp2 != -1
    }

    private suspend fun pipeSocketsNonBlocking(s1: Socket, s2: Socket) {
        val in1 = s1.getInputStream()
        val out1 = s1.getOutputStream()
        val in2 = s2.getInputStream()
        val out2 = s2.getOutputStream()

        val job1 = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            try {
                while (true) {
                    val len = in1.read(buffer)
                    if (len <= 0) break
                    out2.write(buffer, 0, len)
                    out2.flush()
                }
            } catch (_: Exception) {
            } finally {
                try {
                    if (!s2.isClosed && !s2.isOutputShutdown) {
                        s2.shutdownOutput()
                    }
                } catch (_: Exception) {}
            }
        }

        val job2 = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            try {
                while (true) {
                    val len = in2.read(buffer)
                    if (len <= 0) break
                    out1.write(buffer, 0, len)
                    out1.flush()
                }
            } catch (_: Exception) {
            } finally {
                try {
                    if (!s1.isClosed && !s1.isOutputShutdown) {
                        s1.shutdownOutput()
                    }
                } catch (_: Exception) {}
            }
        }

        joinAll(job1, job2)
        try { s1.close() } catch (_: Exception) {}
        try { s2.close() } catch (_: Exception) {}
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw java.io.EOFException()
            offset += count
        }
    }

    private fun readHttpConnectResponse(input: InputStream): Boolean {
        val headerBytes = ByteArray(4096)
        var totalRead = 0
        var endOfHeader = false

        while (totalRead < headerBytes.size && !endOfHeader) {
            val b = input.read()
            if (b == -1) break
            headerBytes[totalRead++] = b.toByte()

            if (totalRead >= 4 &&
                headerBytes[totalRead - 4] == '\r'.code.toByte() &&
                headerBytes[totalRead - 3] == '\n'.code.toByte() &&
                headerBytes[totalRead - 2] == '\r'.code.toByte() &&
                headerBytes[totalRead - 1] == '\n'.code.toByte()
            ) {
                endOfHeader = true
            }
        }

        if (!endOfHeader) return false
        val statusLine = String(headerBytes, 0, minOf(totalRead, 64), Charsets.US_ASCII)
        return statusLine.contains(" 200") || statusLine.contains(" 200 ")
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        for (sock in activeSockets) {
            try { sock.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
    }
}
