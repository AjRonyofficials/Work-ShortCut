package com.example.service

import android.util.Base64
import com.example.util.ProxyTester
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Universal High-Speed Local Proxy Bridge (SOCKS5 + HTTP/HTTPS Dual Engine):
 *
 * Designed specifically for:
 * 1. 1-5 Second Guaranteed Connection Readiness: Pre-warms protected upstream TCP sockets
 *    immediately upon startup so browser page loads and "Continue" button clicks respond instantly.
 * 2. Zero Thread Starvation: Uses a dedicated unbounded CachedThreadPool instead of Kotlin's
 *    64-thread limited Dispatchers.IO, supporting 250+ simultaneous browser/app keep-alive sockets.
 * 3. Automatic Dual-Protocol Healing (SOCKS5 <-> HTTP/HTTPS): Works seamlessly whether the
 *    upstream proxy is SOCKS5 or HTTP/HTTPS (both Rotating and Sticky IPv4). If the selected
 *    protocol fails handshake, it automatically falls back to the alternate protocol in real time.
 * 4. Zero Real IP / DNS / IPv6 Leak: Passes MapDNS resolved domain names directly to the upstream
 *    proxy (remote DNS resolution), fast-rejects IPv6 (ATYP=4), QUIC/UDP, and DoT (port 853) in <0.5ms
 *    for instant IPv4 TCP fallback in Chrome, Vivaldi, Via, Facebook, Instagram, and WhatsApp.
 */
object LocalSocks5ToHttpBridge {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)

    // Unbounded daemon thread pool: never blocks or starves when browsers open 30+ parallel sockets
    private val ioExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "SuperProxy-Bridge-IO").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
        }
    }

    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    private data class WarmSocket(val socket: Socket, val createdAt: Long)
    private val warmSocketPool = ConcurrentLinkedQueue<WarmSocket>()

    @Volatile
    private var upstreamHost: String = ""
    @Volatile
    private var resolvedUpstreamIp: String = ""
    @Volatile
    private var upstreamPort: Int = 0
    @Volatile
    private var upstreamUser: String = ""
    @Volatile
    private var upstreamPass: String = ""

    private val activeProtocol = AtomicReference("SOCKS5")

    var localPort: Int = 0
        private set

    fun setWorkingProtocol(protocol: String) {
        if (protocol.equals("HTTP", ignoreCase = true) || protocol.equals("HTTPS", ignoreCase = true)) {
            activeProtocol.set("HTTP")
        } else {
            activeProtocol.set("SOCKS5")
        }
    }

    fun getWorkingProtocol(): String = activeProtocol.get()

    fun start(
        httpHost: String,
        httpPort: Int,
        username: String = "",
        password: String = "",
        protocol: String = "SOCKS5"
    ): Int {
        stop()

        upstreamHost = httpHost.trim()
        resolvedUpstreamIp = ProxyTester.resolveHostBypassingVpn(upstreamHost)
        upstreamPort = httpPort
        upstreamUser = username
        upstreamPass = password
        setWorkingProtocol(protocol)

        try {
            val server = ServerSocket()
            server.reuseAddress = true
            server.receiveBufferSize = 131072
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 512)
            serverSocket = server
            localPort = server.localPort
            isRunning.set(true)

            // Immediately pre-warm upstream sockets in background for 1-5s instant readiness
            replenishWarmPool(4)

            // Accept loop on dedicated high-priority daemon thread
            ioExecutor.execute {
                while (isRunning.get() && !server.isClosed) {
                    try {
                        val client = server.accept()
                        client.tcpNoDelay = true
                        client.keepAlive = true
                        client.sendBufferSize = 131072
                        client.receiveBufferSize = 131072
                        activeSockets.add(client)

                        ioExecutor.execute {
                            handleClient(client)
                        }
                    } catch (_: Exception) {
                        if (!isRunning.get() || server.isClosed) break
                    }
                }
            }
            return localPort
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    private fun replenishWarmPool(targetCount: Int = 3) {
        if (!isRunning.get()) return
        ioExecutor.execute {
            try {
                // Evict stale sockets older than 8 seconds
                val now = System.currentTimeMillis()
                val iter = warmSocketPool.iterator()
                while (iter.hasNext()) {
                    val item = iter.next()
                    if (now - item.createdAt > 8000L || item.socket.isClosed || !item.socket.isConnected) {
                        iter.remove()
                        try { item.socket.close() } catch (_: Exception) {}
                    }
                }

                while (isRunning.get() && warmSocketPool.size < targetCount) {
                    val sock = createConnectedUpstreamSocket(3500) ?: break
                    warmSocketPool.offer(WarmSocket(sock, System.currentTimeMillis()))
                }
            } catch (_: Exception) {}
        }
    }

    private fun acquireUpstreamSocket(timeoutMs: Int = 4500): Socket? {
        val now = System.currentTimeMillis()
        while (true) {
            val warm = warmSocketPool.poll() ?: break
            val s = warm.socket
            if (now - warm.createdAt <= 7500L && !s.isClosed && s.isConnected && !s.isInputShutdown && !s.isOutputShutdown) {
                replenishWarmPool(3)
                return s
            } else {
                try { s.close() } catch (_: Exception) {}
            }
        }
        replenishWarmPool(2)
        return createConnectedUpstreamSocket(timeoutMs)
    }

    private fun createConnectedUpstreamSocket(timeoutMs: Int): Socket? {
        return try {
            val targetIp = if (resolvedUpstreamIp.isNotBlank() && !resolvedUpstreamIp.startsWith("240.")) {
                resolvedUpstreamIp
            } else {
                ProxyTester.resolveHostBypassingVpn(upstreamHost).also { resolvedUpstreamIp = it }
            }

            val sock = Socket()
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.sendBufferSize = 131072
            sock.receiveBufferSize = 131072
            sock.soTimeout = 12000
            SuperProxyVpnService.protectSocket(sock)
            sock.connect(InetSocketAddress(targetIp, upstreamPort), timeoutMs)
            activeSockets.add(sock)
            sock
        } catch (_: Exception) {
            null
        }
    }

    private fun handleClient(client: Socket) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 12000
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

            // Reply No-Auth locally on 127.0.0.1
            clientOut.write(byteArrayOf(5, 0))
            clientOut.flush()

            // 2. Local SOCKS5 Request Header
            val reqVer = clientIn.read()
            val cmd = clientIn.read()
            clientIn.read() // RSV
            val atyp = clientIn.read()

            // Fast-reject non-TCP commands (e.g., QUIC UDP_ASSOCIATE / hev FWD_UDP) so Chrome/apps use TCP in <0.5ms
            if (reqVer != 5 || cmd != 1) {
                clientOut.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0)) // Command not supported
                clientOut.flush()
                client.close()
                return
            }

            val targetHost = when (atyp) {
                1 -> { // IPv4
                    val ip = ByteArray(4)
                    readFully(clientIn, ip)
                    InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                3 -> { // Domain name (resolved via MapDNS Fake-IP table)
                    val len = clientIn.read()
                    if (len <= 0) "" else {
                        val domainBytes = ByteArray(len)
                        readFully(clientIn, domainBytes)
                        String(domainBytes, Charsets.US_ASCII)
                    }
                }
                4 -> { // IPv6: Fast-reject so Dual-Stack apps instantly fall back to IPv4 without leaking or hanging
                    val ip6 = ByteArray(16)
                    readFully(clientIn, ip6)
                    clientIn.read()
                    clientIn.read()
                    clientOut.write(byteArrayOf(5, 8, 0, 1, 0, 0, 0, 0, 0, 0)) // Address type not supported
                    clientOut.flush()
                    client.close()
                    return
                }
                else -> ""
            }

            val p1 = clientIn.read()
            val p2 = clientIn.read()
            val targetPort = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)

            // Fast-fail DNS-over-TLS (port 853) so browsers immediately use port 53 MapDNS
            if (targetPort == 853 || targetHost.isEmpty() || targetPort <= 0) {
                clientOut.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)) // Connection refused
                clientOut.flush()
                client.close()
                return
            }

            // 3. Connect to Upstream Proxy (with automatic protocol failover if needed)
            val currentProto = activeProtocol.get()
            val preferHttp = currentProto.equals("HTTP", ignoreCase = true)

            upstream = acquireUpstreamSocket(4500)
            if (upstream == null) {
                clientOut.write(byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                client.close()
                return
            }

            var connected = if (preferHttp) {
                performUpstreamHttpConnect(upstream, targetHost, targetPort, upstreamUser, upstreamPass)
            } else {
                performUpstreamSocks5Connect(upstream, targetHost, targetPort, upstreamUser, upstreamPass)
            }

            // Automatic Protocol Healing: If preferred protocol handshake failed on port, immediately try alternate protocol
            if (!connected) {
                try { upstream.close() } catch (_: Exception) {}
                activeSockets.remove(upstream)

                upstream = createConnectedUpstreamSocket(4000)
                if (upstream != null) {
                    connected = if (preferHttp) {
                        val ok = performUpstreamSocks5Connect(upstream, targetHost, targetPort, upstreamUser, upstreamPass)
                        if (ok) activeProtocol.set("SOCKS5")
                        ok
                    } else {
                        val ok = performUpstreamHttpConnect(upstream, targetHost, targetPort, upstreamUser, upstreamPass)
                        if (ok) activeProtocol.set("HTTP")
                        ok
                    }
                }
            }

            if (!connected || upstream == null) {
                clientOut.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                client.close()
                try { upstream?.close() } catch (_: Exception) {}
                return
            }

            // 4. Notify hev-socks5-tunnel that tunnel connection is established
            clientOut.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            // 5. Configure high-throughput streaming timeouts
            client.soTimeout = 90000
            upstream.soTimeout = 90000
            client.keepAlive = true
            upstream.keepAlive = true

            // 6. Bidirectional full-duplex streaming with proper TCP half-close
            pipeSocketsFullDuplex(client, upstream)
        } catch (_: Exception) {
        } finally {
            activeSockets.remove(client)
            upstream?.let { activeSockets.remove(it) }
            try { client.close() } catch (_: Exception) {}
            try { upstream?.close() } catch (_: Exception) {}
        }
    }

    private fun performUpstreamSocks5Connect(
        upstream: Socket,
        targetHost: String,
        targetPort: Int,
        user: String,
        pass: String
    ): Boolean {
        return try {
            upstream.soTimeout = 8000
            val upIn = upstream.getInputStream()
            val upOut = upstream.getOutputStream()

            // 1. Greeting
            if (user.isNotEmpty()) {
                upOut.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                upOut.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            upOut.flush()

            val ver = upIn.read()
            val method = upIn.read()
            if (ver != 0x05 || method == -1 || method == 0xFF) {
                return false
            }

            // 2. RFC 1929 Username/Password Authentication
            if (method == 0x02) {
                val uBytes = user.toByteArray(Charsets.UTF_8)
                val pBytes = pass.toByteArray(Charsets.UTF_8)
                val authPacket = ByteArray(3 + uBytes.size + pBytes.size)
                authPacket[0] = 0x01
                authPacket[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authPacket, 2, uBytes.size)
                authPacket[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authPacket, 3 + uBytes.size, pBytes.size)

                upOut.write(authPacket)
                upOut.flush()

                val authVer = upIn.read()
                val authStatus = upIn.read()
                if (authVer < 0 || authStatus != 0x00) {
                    return false
                }
            }

            // 3. SOCKS5 CONNECT Request (Always use ATYP 0x03 Domain Name for hostnames so proxy resolves DNS remotely)
            val isIpv4Literal = targetHost.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
            if (isIpv4Literal) {
                val ipBytes = InetAddress.getByName(targetHost).address
                val req = ByteArray(10)
                req[0] = 0x05
                req[1] = 0x01 // CONNECT
                req[2] = 0x00
                req[3] = 0x01 // IPv4
                System.arraycopy(ipBytes, 0, req, 4, 4)
                req[8] = ((targetPort shr 8) and 0xFF).toByte()
                req[9] = (targetPort and 0xFF).toByte()
                upOut.write(req)
            } else {
                val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
                val req = ByteArray(7 + hostBytes.size)
                req[0] = 0x05
                req[1] = 0x01 // CONNECT
                req[2] = 0x00
                req[3] = 0x03 // DOMAINNAME
                req[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
                req[5 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
                req[6 + hostBytes.size] = (targetPort and 0xFF).toByte()
                upOut.write(req)
            }
            upOut.flush()

            val repVer = upIn.read()
            val repStatus = upIn.read()
            upIn.read() // RSV
            val repAtyp = upIn.read()
            if (repVer != 0x05 || repStatus != 0x00) {
                return false
            }

            when (repAtyp) {
                0x01 -> readFully(upIn, ByteArray(4))
                0x03 -> {
                    val len = upIn.read()
                    if (len > 0) readFully(upIn, ByteArray(len))
                }
                0x04 -> readFully(upIn, ByteArray(16))
                else -> return false
            }
            readFully(upIn, ByteArray(2)) // BND.PORT
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun performUpstreamHttpConnect(
        upstream: Socket,
        targetHost: String,
        targetPort: Int,
        user: String,
        pass: String
    ): Boolean {
        return try {
            upstream.soTimeout = 8000
            val upIn = upstream.getInputStream()
            val upOut = upstream.getOutputStream()

            val authHeader = if (user.isNotEmpty() || pass.isNotEmpty()) {
                val token = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $token\r\n"
            } else ""

            val formattedHost = if (targetHost.contains(":") && !targetHost.startsWith("[")) "[$targetHost]" else targetHost
            val connectReq = "CONNECT $formattedHost:$targetPort HTTP/1.1\r\n" +
                    "Host: $formattedHost:$targetPort\r\n" +
                    authHeader +
                    "Proxy-Connection: Keep-Alive\r\n" +
                    "Connection: Keep-Alive\r\n\r\n"

            upOut.write(connectReq.toByteArray(Charsets.US_ASCII))
            upOut.flush()

            readHttpConnectResponse(upIn)
        } catch (_: Exception) {
            false
        }
    }

    private fun pipeSocketsFullDuplex(client: Socket, upstream: Socket) {
        val clientIn = client.getInputStream()
        val clientOut = client.getOutputStream()
        val upIn = upstream.getInputStream()
        val upOut = upstream.getOutputStream()

        val futureUpstreamToClient = ioExecutor.submit {
            val buffer = ByteArray(65536)
            try {
                while (true) {
                    val len = upIn.read(buffer)
                    if (len < 0) break
                    if (len > 0) {
                        clientOut.write(buffer, 0, len)
                        if (upIn.available() == 0) {
                            clientOut.flush()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { client.shutdownOutput() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
        }

        // Run Client -> Upstream on current worker thread (saves 1 thread per socket!)
        val buffer = ByteArray(65536)
        try {
            while (true) {
                val len = clientIn.read(buffer)
                if (len < 0) break
                if (len > 0) {
                    upOut.write(buffer, 0, len)
                    if (clientIn.available() == 0) {
                        upOut.flush()
                    }
                }
            }
            // Half-close upstream output so server can finish sending response for POST/"Continue" clicks
            try { upstream.shutdownOutput() } catch (_: Exception) {}
        } catch (_: Exception) {
            try { upstream.close() } catch (_: Exception) {}
        }

        try {
            futureUpstreamToClient.get()
        } catch (_: Exception) {}

        try { client.close() } catch (_: Exception) {}
        try { upstream.close() } catch (_: Exception) {}
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
        return statusLine.contains(" 200")
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        while (true) {
            val warm = warmSocketPool.poll() ?: break
            try { warm.socket.close() } catch (_: Exception) {}
        }

        for (sock in activeSockets) {
            try { sock.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
    }
}
