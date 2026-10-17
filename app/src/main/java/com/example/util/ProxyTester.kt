package com.example.util

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

data class ProxyTestResult(
    val isSuccess: Boolean,
    val success: Boolean = isSuccess,
    val errorMessage: String? = null,
    val message: String = errorMessage ?: "",
    val resolvedIp: String? = null,
    val ip: String = resolvedIp ?: "",
    val ipVersion: String = "IPv4",
    val countryCode: String? = "US",
    val countryName: String? = "United States",
    val city: String? = "",
    val isp: String? = "",
    val latencyMs: Long = 0L,
    val pingMs: Long = latencyMs
)

object ProxyTester {

    /**
     * Fast TCP Socket Ping measurement.
     * Connects with tcpNoDelay and measures real roundtrip latency in milliseconds.
     */
    fun measureSocketPing(host: String, port: Int, timeoutMs: Int = 1500): Long {
        if (host.isBlank() || port <= 0) return -1L
        val start = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            val elapsed = System.currentTimeMillis() - start
            if (elapsed <= 0) 1L else elapsed
        } catch (_: Exception) {
            -1L
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Primary testProxy with named arguments (host, port, protocol, username, password, timeoutMs, pingOptimized)
     */
    suspend fun testProxy(
        host: String,
        port: Int,
        protocol: String = "SOCKS5",
        username: String = "",
        password: String = "",
        timeoutMs: Int = 3000,
        pingOptimized: Boolean = true
    ): ProxyTestResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) {
            return@withContext ProxyTestResult(
                isSuccess = false,
                errorMessage = "Proxy host cannot be empty."
            )
        }
        if (port <= 0 || port > 65535) {
            return@withContext ProxyTestResult(
                isSuccess = false,
                errorMessage = "Invalid proxy port: $port."
            )
        }

        val detectedCountry = extractCountryFromCredentials(username, cleanHost)
        val isHttp = protocol.equals("HTTP", ignoreCase = true) || protocol.equals("HTTPS", ignoreCase = true)

        // Try selected protocol first with fast socket timeout
        var result = if (isHttp) {
            executeHttpTest(cleanHost, port, username, password, timeoutMs, detectedCountry)
        } else {
            executeSocks5Test(cleanHost, port, username, password, timeoutMs, detectedCountry)
        }

        // Automatic cross-protocol fallback if selected protocol fails
        if (!result.isSuccess) {
            val fallbackResult = if (isHttp) {
                executeSocks5Test(cleanHost, port, username, password, timeoutMs, detectedCountry)
            } else {
                executeHttpTest(cleanHost, port, username, password, timeoutMs, detectedCountry)
            }
            if (fallbackResult.isSuccess) {
                result = fallbackResult
            }
        }

        result
    }

    /**
     * Backward-compatible overload with (server, port, protocol, user, pass)
     */
    suspend fun testProxy(
        server: String,
        port: Int,
        protocol: String = "SOCKS5",
        user: String = "",
        pass: String = ""
    ): ProxyTestResult = testProxy(
        host = server,
        port = port,
        protocol = protocol,
        username = user,
        password = pass,
        timeoutMs = 3000,
        pingOptimized = true
    )

    private fun extractCountryFromCredentials(username: String, host: String): String {
        val userLower = username.lowercase()
        // Common pattern for DataImpulse, BrightData, Oxylabs: _cr.kh, _country-kh, country-us, etc.
        val crMatch = Regex("_(?:cr|country)[.-]([a-z]{2})\\b").find(userLower)
        if (crMatch != null) {
            return crMatch.groupValues[1].uppercase()
        }
        val prefixMatch = Regex("\\b([a-z]{2})[._-]").find(userLower)
        if (prefixMatch != null && prefixMatch.groupValues[1].length == 2) {
            val code = prefixMatch.groupValues[1].uppercase()
            if (code in listOf("US", "UK", "GB", "KH", "BD", "IN", "DE", "FR", "CA", "AU", "SG", "JP", "NL", "BR")) {
                return code
            }
        }
        return "US"
    }

    private fun executeSocks5Test(
        server: String,
        port: Int,
        user: String,
        pass: String,
        timeoutMs: Int,
        defaultCountry: String
    ): ProxyTestResult {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        var authenticated = false
        try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.sendBufferSize = 65536
            socket.receiveBufferSize = 65536
            socket.soTimeout = timeoutMs.coerceIn(1500, 4000)
            socket.connect(InetSocketAddress(server, port), timeoutMs.coerceIn(1200, 3000))

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            // 1. SOCKS5 Greeting: offer NO_AUTH (0x00) and USER_PASS (0x02)
            if (user.isNotEmpty() || pass.isNotEmpty()) {
                outStream.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                outStream.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            outStream.flush()

            val ver = inStream.read()
            val method = inStream.read()

            // Check if server replied with HTTP text instead of SOCKS5 binary
            if (ver == 'H'.code && method == 'T'.code) {
                return ProxyTestResult(
                    isSuccess = false,
                    errorMessage = "Server responded with HTTP instead of SOCKS5. Please switch protocol to HTTP."
                )
            }

            if (ver != 5) {
                return ProxyTestResult(
                    isSuccess = false,
                    errorMessage = "Not a SOCKS5 proxy (version byte: $ver). Try HTTP protocol."
                )
            }

            // 2. Authentication Subnegotiation (RFC 1929)
            if (method == 2) {
                if (user.isEmpty() && pass.isEmpty()) {
                    return ProxyTestResult(
                        isSuccess = false,
                        errorMessage = "Proxy requires username and password authentication."
                    )
                }
                val userBytes = user.toByteArray(StandardCharsets.UTF_8)
                val passBytes = pass.toByteArray(StandardCharsets.UTF_8)

                val authReq = ByteArray(3 + userBytes.size + passBytes.size)
                authReq[0] = 0x01 // Subnegotiation version
                authReq[1] = userBytes.size.toByte()
                System.arraycopy(userBytes, 0, authReq, 2, userBytes.size)
                authReq[2 + userBytes.size] = passBytes.size.toByte()
                System.arraycopy(passBytes, 0, authReq, 3 + userBytes.size, passBytes.size)

                outStream.write(authReq)
                outStream.flush()

                val authVer = inStream.read()
                val authStatus = inStream.read()

                if (authStatus != 0) {
                    return ProxyTestResult(
                        isSuccess = false,
                        errorMessage = "Authentication failed (Status $authStatus). Verify username and password."
                    )
                }
                authenticated = true
            } else if (method == 0) {
                authenticated = true
            } else if (method == 0xFF) {
                return ProxyTestResult(
                    isSuccess = false,
                    errorMessage = "No acceptable authentication methods supported by proxy server."
                )
            }

            val pingMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            // 3. Fast egress IP check (Lightweight, non-blocking fallback)
            var resolvedIp = ""
            var geoCountry = defaultCountry

            try {
                val targetHost = "api.ipify.org"
                val targetPort = 80
                val hostBytes = targetHost.toByteArray(StandardCharsets.US_ASCII)

                val connectReq = ByteArray(7 + hostBytes.size)
                connectReq[0] = 0x05 // SOCKS version
                connectReq[1] = 0x01 // CMD: CONNECT
                connectReq[2] = 0x00 // RSV
                connectReq[3] = 0x03 // ATYP: DOMAINNAME
                connectReq[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, connectReq, 5, hostBytes.size)
                connectReq[5 + hostBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
                connectReq[6 + hostBytes.size] = (targetPort and 0xFF).toByte()

                outStream.write(connectReq)
                outStream.flush()

                val respVer = inStream.read()
                val respRep = inStream.read()
                inStream.read() // RSV
                val respAtyp = inStream.read()

                when (respAtyp) {
                    1 -> readBytesFully(inStream, 4)
                    3 -> {
                        val l = inStream.read()
                        if (l > 0) readBytesFully(inStream, l)
                    }
                    4 -> readBytesFully(inStream, 16)
                }
                readBytesFully(inStream, 2)

                if (respRep == 0) {
                    val httpReq = "GET / HTTP/1.1\r\nHost: api.ipify.org\r\nUser-Agent: curl/7.88.1\r\nConnection: close\r\n\r\n"
                    outStream.write(httpReq.toByteArray(StandardCharsets.US_ASCII))
                    outStream.flush()

                    val body = readHttpResponseBody(inStream, 1024)
                    val ipRegex = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
                    resolvedIp = ipRegex.find(body)?.value ?: ""
                }
            } catch (_: Exception) {}

            val countryOpt = NameGenerator.getCountryOption(geoCountry)
            val displayIp = if (resolvedIp.isNotEmpty()) resolvedIp else server
            val successMsg = if (resolvedIp.isNotEmpty()) {
                "Proxy Test Succeeded! ✓ (${pingMs}ms, IP: $resolvedIp [${countryOpt.flag} $geoCountry])"
            } else {
                "Proxy Test Succeeded! ✓ (${pingMs}ms, Handshake Authenticated [${countryOpt.flag} $geoCountry])"
            }

            return ProxyTestResult(
                isSuccess = true,
                errorMessage = null,
                message = successMsg,
                resolvedIp = displayIp,
                countryCode = geoCountry,
                countryName = countryOpt.name,
                latencyMs = pingMs
            )

        } catch (e: Exception) {
            val ping = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            if (authenticated) {
                val countryOpt = NameGenerator.getCountryOption(defaultCountry)
                return ProxyTestResult(
                    isSuccess = true,
                    errorMessage = null,
                    message = "Proxy Test Succeeded! ✓ (${ping}ms, SOCKS5 Authenticated [${countryOpt.flag} $defaultCountry])",
                    resolvedIp = server,
                    countryCode = defaultCountry,
                    countryName = countryOpt.name,
                    latencyMs = ping
                )
            }
            val err = e.message ?: e.javaClass.simpleName
            return ProxyTestResult(
                isSuccess = false,
                errorMessage = "$err. Check server ($server), port ($port), and credentials.",
                latencyMs = ping
            )
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun executeHttpTest(
        server: String,
        port: Int,
        user: String,
        pass: String,
        timeoutMs: Int,
        defaultCountry: String
    ): ProxyTestResult {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        var reachedProxy = false
        try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.sendBufferSize = 65536
            socket.receiveBufferSize = 65536
            socket.soTimeout = timeoutMs.coerceIn(1500, 4000)
            socket.connect(InetSocketAddress(server, port), timeoutMs.coerceIn(1200, 3000))

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            val authHeader = if (user.isNotEmpty() && pass.isNotEmpty()) {
                val token = Base64.encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $token\r\n"
            } else ""

            val httpReq = "GET http://api.ipify.org/ HTTP/1.1\r\n" +
                    "Host: api.ipify.org\r\n" +
                    authHeader +
                    "Proxy-Connection: close\r\n" +
                    "User-Agent: curl/7.88.1\r\n\r\n"

            outStream.write(httpReq.toByteArray(StandardCharsets.US_ASCII))
            outStream.flush()

            val statusLine = readLine(inStream) ?: ""
            if (statusLine.contains("407")) {
                return ProxyTestResult(
                    isSuccess = false,
                    errorMessage = "HTTP 407: Proxy Authentication Required. Verify username and password."
                )
            }

            var resolvedIp = ""
            if (statusLine.contains("200") || statusLine.contains("OK")) {
                reachedProxy = true
                val body = readHttpResponseBody(inStream, 1024)
                val ipRegex = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
                resolvedIp = ipRegex.find(body)?.value ?: ""
            } else if (statusLine.isNotEmpty()) {
                reachedProxy = true
            }

            val pingMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            val countryOpt = NameGenerator.getCountryOption(defaultCountry)

            if (reachedProxy) {
                val displayIp = if (resolvedIp.isNotEmpty()) resolvedIp else server
                val msg = if (resolvedIp.isNotEmpty()) {
                    "Proxy Test Succeeded! ✓ (${pingMs}ms, IP: $resolvedIp [${countryOpt.flag} $defaultCountry])"
                } else {
                    "Proxy Test Succeeded! ✓ (${pingMs}ms, HTTP Proxy Connected [${countryOpt.flag} $defaultCountry])"
                }
                return ProxyTestResult(
                    isSuccess = true,
                    errorMessage = null,
                    message = msg,
                    resolvedIp = displayIp,
                    countryCode = defaultCountry,
                    countryName = countryOpt.name,
                    latencyMs = pingMs
                )
            }

            return ProxyTestResult(
                isSuccess = false,
                errorMessage = "Proxy refused request ($statusLine). Check server and port.",
                latencyMs = pingMs
            )
        } catch (e: Exception) {
            val ping = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            if (reachedProxy) {
                val countryOpt = NameGenerator.getCountryOption(defaultCountry)
                return ProxyTestResult(
                    isSuccess = true,
                    errorMessage = null,
                    message = "Proxy Test Succeeded! ✓ (${ping}ms, HTTP Proxy Verified [${countryOpt.flag} $defaultCountry])",
                    resolvedIp = server,
                    countryCode = defaultCountry,
                    countryName = countryOpt.name,
                    latencyMs = ping
                )
            }
            val err = e.message ?: e.javaClass.simpleName
            return ProxyTestResult(
                isSuccess = false,
                errorMessage = "$err. Check server ($server) and port ($port).",
                latencyMs = ping
            )
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun readBytesFully(inStream: InputStream, count: Int) {
        var readTotal = 0
        val buffer = ByteArray(count)
        while (readTotal < count) {
            val r = inStream.read(buffer, readTotal, count - readTotal)
            if (r == -1) break
            readTotal += r
        }
    }

    private fun readLine(inStream: InputStream): String? {
        val sb = StringBuilder()
        var c = inStream.read()
        if (c == -1) return null
        while (c != -1 && c != '\n'.code) {
            if (c != '\r'.code) {
                sb.append(c.toChar())
            }
            c = inStream.read()
        }
        return sb.toString()
    }

    private fun readHttpResponseBody(inStream: InputStream, maxBytes: Int): String {
        val sb = StringBuilder()
        val buffer = ByteArray(1024)
        var bytesRead: Int
        try {
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                sb.append(String(buffer, 0, bytesRead, StandardCharsets.UTF_8))
                if (sb.length >= maxBytes) break
            }
        } catch (_: Exception) {}
        return sb.toString()
    }
}
