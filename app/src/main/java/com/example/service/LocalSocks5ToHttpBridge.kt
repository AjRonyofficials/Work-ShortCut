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
 * Local SOCKS5-to-HTTP Bridge (#132 / #193 proven architecture):
 * Accepts local SOCKS5 connections (No-Auth) from hev-socks5-tunnel on 127.0.0.1
 * and bridges them to an upstream HTTP/HTTPS proxy using HTTP CONNECT with
 * Proxy-Authorization: Basic <base64>.
 */
object LocalSocks5ToHttpBridge {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    @Volatile private var httpHost: String = ""
    @Volatile private var httpPort: Int = 0
    @Volatile private var user: String = ""
    @Volatile private var pass: String = ""

    fun start(
        httpHost: String,
        httpPort: Int,
        username: String,
        password: String
    ): Int {
        stop()

        this.httpHost = httpHost
        this.httpPort = httpPort
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

    private suspend fun handleClient(client: Socket) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 15000
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

            // Fast-fail DNS-over-TLS (port 853) so Chrome and browsers instantly fallback to port 53 / HTTPS
            if (targetPort == 853) {
                clientOut.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)) // Connection refused
                clientOut.flush()
                client.close()
                return
            }

            if (targetHost.isEmpty() || targetPort <= 0) {
                clientOut.write(byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0)) // Host unreachable
                clientOut.flush()
                client.close()
                return
            }

            // 3. Connect to upstream HTTP proxy (Protected from VPN tunnel)
            upstream = Socket()
            upstream.tcpNoDelay = true
            upstream.keepAlive = true
            upstream.sendBufferSize = 65536
            upstream.receiveBufferSize = 65536
            upstream.soTimeout = 15000

            SuperProxyVpnService.protectSocket(upstream)
            activeSockets.add(upstream)
            upstream.connect(InetSocketAddress(httpHost, httpPort), 8000)

            val upIn = upstream.getInputStream()
            val upOut = upstream.getOutputStream()

            // 4. Send clean standard HTTP CONNECT request with Proxy-Authorization
            val authHeader = if (user.isNotEmpty() && pass.isNotEmpty()) {
                val token = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
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

            // 5. Read HTTP CONNECT response up to \r\n\r\n
            val isSuccess = readHttpConnectResponse(upIn)
            if (!isSuccess) {
                clientOut.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)) // Connection refused
                clientOut.flush()
                client.close()
                upstream.close()
                return
            }

            // 6. Send SOCKS5 Success response to hev-socks5-tunnel
            clientOut.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            // 7. Stable keep-alive with 60s idle timeout
            client.soTimeout = 60000
            upstream.soTimeout = 60000
            client.keepAlive = true
            upstream.keepAlive = true

            // 8. Bidirectional non-blocking data piping
            pipeSocketsNonBlocking(client, upstream)

        } catch (_: Exception) {
        } finally {
            activeSockets.remove(client)
            upstream?.let { activeSockets.remove(it) }
            try { client.close() } catch (_: Exception) {}
            try { upstream?.close() } catch (_: Exception) {}
        }
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
                // Signal half-close to upstream: client finished sending request
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
                // Signal half-close to client: upstream finished sending response
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
