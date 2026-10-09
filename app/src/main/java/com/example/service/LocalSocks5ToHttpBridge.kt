package com.example.service

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-Time Local SOCKS5-to-HTTP CONNECT Bridge:
 * Converts native SOCKS5 requests from hev-socks5-tunnel into standard HTTP CONNECT requests
 * with real-time Proxy-Authorization (Basic base64(username:password)).
 *
 * This guarantees that HTTP/HTTPS proxies requiring username/password authentication
 * work seamlessly for every application on the Android device.
 */
object LocalSocks5ToHttpBridge {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    var localPort: Int = 0
        private set

    fun start(
        httpHost: String,
        httpPort: Int,
        username: String = "",
        password: String = ""
    ): Int {
        stop()

        try {
            val server = ServerSocket(0, 100, InetAddress.getByName("127.0.0.1"))
            serverSocket = server
            localPort = server.localPort
            isRunning.set(true)

            scope.launch {
                while (isRunning.get() && !server.isClosed) {
                    try {
                        val client = server.accept()
                        activeSockets.add(client)
                        launch(Dispatchers.IO) {
                            handleClient(client, httpHost, httpPort, username, password)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            return localPort
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    private fun handleClient(
        client: Socket,
        httpHost: String,
        httpPort: Int,
        user: String,
        pass: String
    ) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 20000
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            // 1. SOCKS5 Method Negotiation
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

            // 2. SOCKS5 Request
            val reqVer = clientIn.read()
            val cmd = clientIn.read()
            clientIn.read() // RSV
            val atyp = clientIn.read()

            if (reqVer != 5 || cmd != 1) { // 1 = CONNECT
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
                3 -> { // Domain name
                    val len = clientIn.read()
                    if (len <= 0) "" else {
                        val domainBytes = ByteArray(len)
                        readFully(clientIn, domainBytes)
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

            // 3. Connect to upstream HTTP proxy (Protected from VPN tunnel)
            upstream = Socket()
            upstream.tcpNoDelay = true
            upstream.soTimeout = 20000
            SuperProxyVpnService.protectSocket(upstream)
            activeSockets.add(upstream)
            upstream.connect(InetSocketAddress(httpHost, httpPort), 12000)

            val upIn = upstream.getInputStream()
            val upOut = upstream.getOutputStream()

            // 4. Send HTTP CONNECT request with Proxy-Authorization
            val authHeader = if (user.isNotEmpty() && pass.isNotEmpty()) {
                val token = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $token\r\n"
            } else ""

            val formattedHost = if (targetHost.contains(":") && !targetHost.startsWith("[")) "[$targetHost]" else targetHost
            val connectMsg = "CONNECT $formattedHost:$targetPort HTTP/1.1\r\n" +
                    "Host: $formattedHost:$targetPort\r\n" +
                    authHeader +
                    "Proxy-Connection: Keep-Alive\r\n" +
                    "User-Agent: Mozilla/5.0 (Android)\r\n\r\n"

            upOut.write(connectMsg.toByteArray(Charsets.US_ASCII))
            upOut.flush()

            // 5. Read HTTP response line
            val statusLine = readLine(upIn)
            if (statusLine == null || (!statusLine.contains(" 200") && !statusLine.contains(" 200 "))) {
                clientOut.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)) // Connection refused
                clientOut.flush()
                client.close()
                upstream.close()
                return
            }

            // Consume remaining headers up to empty line (\r\n\r\n)
            while (true) {
                val header = readLine(upIn)
                if (header.isNullOrEmpty()) break
            }

            // 6. Send SOCKS5 Success response to hev-socks5-tunnel
            clientOut.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            client.soTimeout = 0
            upstream.soTimeout = 0

            // 7. Bidirectional data piping
            pipeSockets(client, upstream)
        } catch (_: Exception) {
        } finally {
            activeSockets.remove(client)
            upstream?.let { activeSockets.remove(it) }
            try { client.close() } catch (_: Exception) {}
            try { upstream?.close() } catch (_: Exception) {}
        }
    }

    private fun pipeSockets(s1: Socket, s2: Socket) {
        val in1 = s1.getInputStream()
        val out1 = s1.getOutputStream()
        val in2 = s2.getInputStream()
        val out2 = s2.getOutputStream()

        val latch = java.util.concurrent.CountDownLatch(2)

        Thread {
            try {
                val buffer = ByteArray(32768)
                var len: Int
                while (in1.read(buffer).also { len = it } != -1) {
                    out2.write(buffer, 0, len)
                    out2.flush()
                }
            } catch (_: Exception) {}
            try { s2.shutdownOutput() } catch (_: Exception) { try { s2.close() } catch (_: Exception) {} }
            latch.countDown()
        }.apply { isDaemon = true; start() }

        Thread {
            try {
                val buffer = ByteArray(32768)
                var len: Int
                while (in2.read(buffer).also { len = it } != -1) {
                    out1.write(buffer, 0, len)
                    out1.flush()
                }
            } catch (_: Exception) {}
            try { s1.shutdownOutput() } catch (_: Exception) { try { s1.close() } catch (_: Exception) {} }
            latch.countDown()
        }.apply { isDaemon = true; start() }

        try { latch.await(180, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) {}
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw java.io.EOFException()
            offset += count
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (input.read().also { c = it } != -1) {
            if (c == '\n'.code) break
            if (c != '\r'.code) {
                sb.append(c.toChar())
            }
        }
        return if (sb.isEmpty() && c == -1) null else sb.toString()
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
