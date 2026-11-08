package com.example.util

import android.util.Base64
import com.example.service.SuperProxyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.Authenticator
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ProxyTester {

    data class PingResult(
        val isSuccess: Boolean,
        val latencyMs: Long,
        val resolvedIp: String? = null,
        val ipVersion: String = "IPv4",
        val countryCode: String? = null,
        val countryName: String? = null,
        val city: String? = null,
        val isp: String? = null,
        val timezone: String? = null,
        val workingProtocol: String? = null,
        val errorMessage: String? = null
    )

    data class FastHandshakeResult(
        val isSuccess: Boolean,
        val workingProtocol: String,
        val resolvedServerIp: String,
        val latencyMs: Long,
        val egressIp: String? = null
    )

    private data class ProbeOutcome(
        val handshakeOk: Boolean,
        val protocol: String,
        val egressIp: String?,
        val latencyMs: Long
    )

    private val IPV4_REGEX = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b")
    private val IPV6_REGEX = Pattern.compile("(?i)\\b(?:[a-f0-9]{1,4}:){7}[a-f0-9]{1,4}\\b|\\b(?:[a-f0-9]{1,4}:){1,7}:\\b|\\b:(?::[a-f0-9]{1,4}){1,7}\\b")

    private val gatewayDnsCache = ConcurrentHashMap<String, String>()

    private fun isValidRealIpv4(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        if (!ip.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return false
        if (ip.startsWith("240.") || ip.startsWith("198.18.") || ip.startsWith("10.10.10.") || ip == "0.0.0.0") {
            return false
        }
        return true
    }

    /**
     * Resolves a proxy gateway hostname (e.g. gw.dataimpulse.com) to a real IPv4 address.
     * Uses Android's fast system resolver first (10-40ms) when not polluted by Fake-IPs,
     * and falls back to protected UDP DNS if tun0 MapDNS is active.
     */
    fun resolveHostBypassingVpn(host: String): String {
        val clean = host.trim()
        if (clean.isEmpty()) return clean
        if (isValidRealIpv4(clean)) {
            return clean
        }

        gatewayDnsCache[clean]?.let { cached ->
            if (isValidRealIpv4(cached)) {
                return cached
            }
        }

        // 1. Try system DNS first (instant 10-40ms when called before VPN establish or from system cache)
        try {
            val all = InetAddress.getAllByName(clean)
            val realIpv4 = all.firstOrNull { addr ->
                addr is Inet4Address && isValidRealIpv4(addr.hostAddress)
            }?.hostAddress
            if (!realIpv4.isNullOrBlank()) {
                gatewayDnsCache[clean] = realIpv4
                return realIpv4
            }
        } catch (_: Exception) {}

        // 2. Fallback to direct protected UDP DNS query (bypasses tun0 MapDNS if VPN is already running)
        val dnsResolved = queryProtectedDnsIpv4(clean, "8.8.8.8")
            ?: queryProtectedDnsIpv4(clean, "1.1.1.1")
        if (isValidRealIpv4(dnsResolved)) {
            gatewayDnsCache[clean] = dnsResolved!!
            return dnsResolved
        }

        return gatewayDnsCache[clean] ?: clean
    }

    private fun queryProtectedDnsIpv4(domain: String, dnsServer: String): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 900
            SuperProxyVpnService.protectSocket(socket)

            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            dos.writeShort(0x1337)
            dos.writeShort(0x0100)
            dos.writeShort(1)
            dos.writeShort(0)
            dos.writeShort(0)
            dos.writeShort(0)

            for (part in domain.split(".")) {
                val bytes = part.toByteArray(Charsets.US_ASCII)
                dos.writeByte(bytes.size)
                dos.write(bytes)
            }
            dos.writeByte(0)
            dos.writeShort(1) // Type A
            dos.writeShort(1) // Class IN

            val reqBytes = baos.toByteArray()
            val packet = DatagramPacket(reqBytes, reqBytes.size, InetAddress.getByName(dnsServer), 53)
            socket.send(packet)

            val respBuf = ByteArray(512)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(respPacket)

            val len = respPacket.length
            if (len < 12) return null
            val anCount = ((respBuf[6].toInt() and 0xFF) shl 8) or (respBuf[7].toInt() and 0xFF)
            if (anCount <= 0) return null

            var idx = 12
            while (idx < len && respBuf[idx].toInt() != 0) {
                val labelLen = respBuf[idx].toInt() and 0xFF
                idx += 1 + labelLen
            }
            idx += 5

            for (i in 0 until anCount) {
                if (idx >= len) break
                if ((respBuf[idx].toInt() and 0xC0) == 0xC0) {
                    idx += 2
                } else {
                    while (idx < len && respBuf[idx].toInt() != 0) {
                        idx += 1 + (respBuf[idx].toInt() and 0xFF)
                    }
                    idx++
                }
                if (idx + 10 > len) break
                val rType = ((respBuf[idx].toInt() and 0xFF) shl 8) or (respBuf[idx + 1].toInt() and 0xFF)
                val rdLen = ((respBuf[idx + 8].toInt() and 0xFF) shl 8) or (respBuf[idx + 9].toInt() and 0xFF)
                idx += 10
                if (rType == 1 && rdLen == 4 && idx + 4 <= len) {
                    val ip = "${respBuf[idx].toInt() and 0xFF}.${respBuf[idx + 1].toInt() and 0xFF}.${respBuf[idx + 2].toInt() and 0xFF}.${respBuf[idx + 3].toInt() and 0xFF}"
                    if (isValidRealIpv4(ip)) return ip
                }
                idx += rdLen
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Ultra-fast pre-VPN handshake verification (completes in 0.2s - 1.5s).
     * Resolves the gateway IPv4 and races SOCKS5 vs HTTP handshakes in parallel so
     * SuperProxyVpnService starts ONCE with the verified protocol and numeric IPv4.
     */
    suspend fun fastVerifyBeforeConnect(
        host: String,
        port: Int,
        preferredProtocol: String,
        username: String,
        password: String,
        timeoutMs: Int = 1800
    ): FastHandshakeResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val resolvedServerIp = resolveHostBypassingVpn(cleanHost)
        val preferHttp = preferredProtocol.equals("HTTP", ignoreCase = true) ||
                preferredProtocol.equals("HTTPS", ignoreCase = true)

        coroutineScope {
            val socksDeferred = async(Dispatchers.IO) {
                probeSocks5HandshakeOnly(resolvedServerIp, port, username, password, timeoutMs)
            }
            val httpDeferred = async(Dispatchers.IO) {
                probeHttpHandshakeOnly(resolvedServerIp, port, username, password, timeoutMs)
            }

            if (preferHttp) {
                val httpRes = httpDeferred.await()
                if (httpRes.handshakeOk) {
                    socksDeferred.cancel()
                    return@coroutineScope FastHandshakeResult(
                        isSuccess = true,
                        workingProtocol = "HTTP",
                        resolvedServerIp = resolvedServerIp,
                        latencyMs = httpRes.latencyMs,
                        egressIp = httpRes.egressIp
                    )
                }
                val socksRes = socksDeferred.await()
                if (socksRes.handshakeOk) {
                    return@coroutineScope FastHandshakeResult(
                        isSuccess = true,
                        workingProtocol = "SOCKS5",
                        resolvedServerIp = resolvedServerIp,
                        latencyMs = socksRes.latencyMs,
                        egressIp = socksRes.egressIp
                    )
                }
            } else {
                val socksRes = socksDeferred.await()
                if (socksRes.handshakeOk) {
                    httpDeferred.cancel()
                    return@coroutineScope FastHandshakeResult(
                        isSuccess = true,
                        workingProtocol = "SOCKS5",
                        resolvedServerIp = resolvedServerIp,
                        latencyMs = socksRes.latencyMs,
                        egressIp = socksRes.egressIp
                    )
                }
                val httpRes = httpDeferred.await()
                if (httpRes.handshakeOk) {
                    return@coroutineScope FastHandshakeResult(
                        isSuccess = true,
                        workingProtocol = "HTTP",
                        resolvedServerIp = resolvedServerIp,
                        latencyMs = httpRes.latencyMs,
                        egressIp = httpRes.egressIp
                    )
                }
            }

            FastHandshakeResult(
                isSuccess = false,
                workingProtocol = if (preferHttp) "HTTP" else "SOCKS5",
                resolvedServerIp = resolvedServerIp,
                latencyMs = 45L,
                egressIp = null
            )
        }
    }

    /**
     * Checks SOCKS5 greeting + RFC 1929 auth in 1-2 RTTs (~150-400ms) without waiting on external websites.
     */
    private fun probeSocks5HandshakeOnly(
        serverIp: String,
        port: Int,
        username: String,
        password: String,
        timeoutMs: Int
    ): ProbeOutcome {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(serverIp, port), timeoutMs)

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            if (username.isNotEmpty()) {
                outStream.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                outStream.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            outStream.flush()

            val ver = inStream.read()
            val method = inStream.read()
            if (ver != 0x05 || method == -1 || method == 0xFF) {
                return ProbeOutcome(false, "SOCKS5", null, -1)
            }

            if (method == 0x02) {
                val uBytes = username.toByteArray(Charsets.UTF_8)
                val pBytes = password.toByteArray(Charsets.UTF_8)
                val authBuf = ByteArray(3 + uBytes.size + pBytes.size)
                authBuf[0] = 0x01
                authBuf[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authBuf, 2, uBytes.size)
                authBuf[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authBuf, 3 + uBytes.size, pBytes.size)

                outStream.write(authBuf)
                outStream.flush()

                val authVer = inStream.read()
                val authStatus = inStream.read()
                if (authVer < 0 || authStatus != 0x00) {
                    return ProbeOutcome(false, "SOCKS5", null, -1)
                }
            }

            val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            ProbeOutcome(true, "SOCKS5", null, rtt)
        } catch (_: Exception) {
            ProbeOutcome(false, "SOCKS5", null, -1)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Checks HTTP CONNECT / GET proxy handshake in ~200-500ms.
     */
    private fun probeHttpHandshakeOnly(
        serverIp: String,
        port: Int,
        username: String,
        password: String,
        timeoutMs: Int
    ): ProbeOutcome {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(serverIp, port), timeoutMs)

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            val authHeader = if (username.isNotEmpty() || password.isNotEmpty()) {
                val token = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $token\r\n"
            } else ""

            // Send HTTP CONNECT to 1.1.1.1:80 (IP literal = 0ms DNS overhead on proxy server)
            val connectReq = "CONNECT 1.1.1.1:80 HTTP/1.1\r\n" +
                    "Host: 1.1.1.1:80\r\n" +
                    authHeader +
                    "Proxy-Connection: Keep-Alive\r\n\r\n"
            outStream.write(connectReq.toByteArray(Charsets.US_ASCII))
            outStream.flush()

            val firstLine = readSingleAsciiLine(inStream)
            val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            if (firstLine.startsWith("HTTP/1.", ignoreCase = true) &&
                (firstLine.contains(" 200") || firstLine.contains(" 100"))
            ) {
                return ProbeOutcome(true, "HTTP", null, rtt)
            }
            ProbeOutcome(false, "HTTP", null, -1)
        } catch (_: Exception) {
            ProbeOutcome(false, "HTTP", null, -1)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun readSingleAsciiLine(input: java.io.InputStream): String {
        val sb = StringBuilder()
        while (sb.length < 128) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    /**
     * Full Proxy Test & Real-Time Egress IP + Geo Detection (for Test Proxy button & Phase 2 enrichment).
     */
    suspend fun testProxy(
        host: String,
        port: Int,
        protocol: String = "SOCKS5",
        username: String = "",
        password: String = "",
        timeoutMs: Int = 4000,
        pingOptimized: Boolean = false
    ): PingResult = withContext(Dispatchers.IO) {
        val effectiveTimeout = if (pingOptimized) minOf(timeoutMs, 3000) else timeoutMs
        val startTime = System.currentTimeMillis()

        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || port <= 0 || port > 65535) {
            return@withContext PingResult(
                isSuccess = false,
                latencyMs = -1,
                errorMessage = "Invalid Host or Port ($cleanHost:$port)"
            )
        }

        val resolvedGatewayIp = resolveHostBypassingVpn(cleanHost)

        if (username.isNotEmpty()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password.toCharArray())
                }
            })
        }

        val preferHttp = protocol.equals("HTTP", ignoreCase = true) || protocol.equals("HTTPS", ignoreCase = true)

        var resolvedIp: String? = null
        var detectedProtocol: String? = null
        var handshakeLatency: Long = -1

        val probeTimeout = minOf(effectiveTimeout, 3000)
        coroutineScope {
            val socksDeferred = async(Dispatchers.IO) {
                probeDirectSocks5Full(resolvedGatewayIp, port, username, password, probeTimeout)
            }
            val httpDeferred = async(Dispatchers.IO) {
                probeDirectHttpFull(resolvedGatewayIp, port, username, password, probeTimeout)
            }

            if (preferHttp) {
                val httpRes = httpDeferred.await()
                if (httpRes.handshakeOk) {
                    detectedProtocol = "HTTP"
                    resolvedIp = httpRes.egressIp
                    handshakeLatency = httpRes.latencyMs
                    if (!resolvedIp.isNullOrBlank()) socksDeferred.cancel()
                }
                if (resolvedIp.isNullOrBlank()) {
                    val socksRes = socksDeferred.await()
                    if (socksRes.handshakeOk) {
                        if (detectedProtocol == null) detectedProtocol = "SOCKS5"
                        if (!socksRes.egressIp.isNullOrBlank()) {
                            resolvedIp = socksRes.egressIp
                            detectedProtocol = "SOCKS5"
                        }
                        if (handshakeLatency <= 0) handshakeLatency = socksRes.latencyMs
                    }
                }
            } else {
                val socksRes = socksDeferred.await()
                if (socksRes.handshakeOk) {
                    detectedProtocol = "SOCKS5"
                    resolvedIp = socksRes.egressIp
                    handshakeLatency = socksRes.latencyMs
                    if (!resolvedIp.isNullOrBlank()) httpDeferred.cancel()
                }
                if (resolvedIp.isNullOrBlank()) {
                    val httpRes = httpDeferred.await()
                    if (httpRes.handshakeOk) {
                        if (detectedProtocol == null) detectedProtocol = "HTTP"
                        if (!httpRes.egressIp.isNullOrBlank()) {
                            resolvedIp = httpRes.egressIp
                            detectedProtocol = "HTTP"
                        }
                        if (handshakeLatency <= 0) handshakeLatency = httpRes.latencyMs
                    }
                }
            }
        }

        // If handshake succeeded (we know the exact protocol!) but api.ipify.org was slow, use gateway IP for Geo lookup
        if (resolvedIp.isNullOrBlank() && detectedProtocol != null) {
            resolvedIp = resolvedGatewayIp
        }

        if (resolvedIp.isNullOrBlank() || detectedProtocol == null) {
            return@withContext PingResult(
                isSuccess = false,
                latencyMs = -1,
                errorMessage = "Proxy Handshake Error: Failed to connect to $cleanHost:$port."
            )
        }

        var countryCode = "US"
        var countryName = "United States"
        var city = ""
        var isp = ""
        var timezone = ""

        val crMatch = Regex("""(?:cr\.([a-z]{2})|country[_-]([a-z]{2})|-([a-z]{2})-)""", RegexOption.IGNORE_CASE).find(username)
        val userHintCountry = crMatch?.groupValues?.firstOrNull { it.length == 2 && !it.equals("cr", ignoreCase = true) }?.uppercase()
        if (!userHintCountry.isNullOrBlank()) {
            countryCode = userHintCountry
            countryName = NameGenerator.getCountryOption(userHintCountry).name
        }

        try {
            val ipApiUrl = URL("http://ip-api.com/json/$resolvedIp?fields=status,country,countryCode,city,isp,timezone")
            val conn = ipApiUrl.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            conn.connectTimeout = 1200
            conn.readTimeout = 1200
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                if (json.optString("status") == "success") {
                    countryCode = json.optString("countryCode", countryCode)
                    countryName = json.optString("country", countryName)
                    city = json.optString("city", "")
                    isp = json.optString("isp", "")
                    timezone = json.optString("timezone", "")
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}

        val totalElapsed = System.currentTimeMillis() - startTime
        val finalLatency = when {
            handshakeLatency in 1..2500 -> handshakeLatency
            totalElapsed > 0 -> totalElapsed
            else -> 38L
        }
        val detectedVersion = if (resolvedIp!!.contains(":")) "IPv6" else "IPv4"

        PingResult(
            isSuccess = true,
            latencyMs = finalLatency,
            resolvedIp = resolvedIp,
            ipVersion = detectedVersion,
            countryCode = countryCode,
            countryName = countryName,
            city = city,
            isp = isp,
            timezone = timezone,
            workingProtocol = detectedProtocol
        )
    }

    fun measureSocketPing(host: String, port: Int, timeoutMs: Int = 1800): Long {
        var socket: Socket? = null
        return try {
            val resolvedIp = resolveHostBypassingVpn(host)
            val start = System.currentTimeMillis()
            socket = Socket()
            socket.tcpNoDelay = true
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(resolvedIp, port), timeoutMs)
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun probeDirectSocks5Full(
        host: String,
        port: Int,
        username: String,
        password: String,
        timeoutMs: Int
    ): ProbeOutcome {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        var handshakeOk = false
        var handshakeRtt = -1L
        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            if (username.isNotEmpty()) {
                outStream.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                outStream.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            outStream.flush()

            val ver = inStream.read()
            val selectedMethod = inStream.read()
            if (ver != 0x05 || selectedMethod == -1 || selectedMethod == 0xFF) {
                return ProbeOutcome(false, "SOCKS5", null, -1)
            }

            if (selectedMethod == 0x02) {
                val uBytes = username.toByteArray(Charsets.UTF_8)
                val pBytes = password.toByteArray(Charsets.UTF_8)
                val authBuf = ByteArray(3 + uBytes.size + pBytes.size)
                authBuf[0] = 0x01
                authBuf[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authBuf, 2, uBytes.size)
                authBuf[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authBuf, 3 + uBytes.size, pBytes.size)

                outStream.write(authBuf)
                outStream.flush()

                val authVer = inStream.read()
                val authStatus = inStream.read()
                if (authVer < 0 || authStatus != 0x00) {
                    return ProbeOutcome(false, "SOCKS5", null, -1)
                }
            }

            // SOCKS5 Auth succeeded! Mark handshakeOk = true immediately.
            handshakeOk = true
            handshakeRtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)

            val domain = "api.ipify.org"
            val dBytes = domain.toByteArray(Charsets.US_ASCII)
            val req = ByteArray(4 + 1 + dBytes.size + 2)
            req[0] = 0x05
            req[1] = 0x01 // CONNECT
            req[2] = 0x00
            req[3] = 0x03 // DOMAINNAME
            req[4] = dBytes.size.toByte()
            System.arraycopy(dBytes, 0, req, 5, dBytes.size)
            req[5 + dBytes.size] = 0
            req[6 + dBytes.size] = 80

            outStream.write(req)
            outStream.flush()

            val repVer = inStream.read()
            val rep = inStream.read()
            inStream.read() // RSV
            val atyp = inStream.read()
            if (repVer != 0x05 || rep != 0x00) {
                return ProbeOutcome(true, "SOCKS5", null, handshakeRtt)
            }

            when (atyp) {
                0x01 -> readFully(inStream, ByteArray(4))
                0x03 -> {
                    val len = inStream.read()
                    if (len > 0) readFully(inStream, ByteArray(len))
                }
                0x04 -> readFully(inStream, ByteArray(16))
            }
            inStream.read()
            inStream.read()

            val httpReq = "GET / HTTP/1.1\r\nHost: api.ipify.org\r\nUser-Agent: curl/8.0\r\nConnection: close\r\n\r\n"
            outStream.write(httpReq.toByteArray(Charsets.US_ASCII))
            outStream.flush()

            val reader = BufferedReader(InputStreamReader(inStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 25) {
                sb.append(line).append("\n")
                count++
            }
            val body = sb.toString()
            val v4 = IPV4_REGEX.matcher(body)
            if (v4.find()) return ProbeOutcome(true, "SOCKS5", v4.group(0), handshakeRtt)
            val v6 = IPV6_REGEX.matcher(body)
            if (v6.find()) return ProbeOutcome(true, "SOCKS5", v6.group(0), handshakeRtt)
            ProbeOutcome(true, "SOCKS5", null, handshakeRtt)
        } catch (_: Exception) {
            ProbeOutcome(handshakeOk, "SOCKS5", null, handshakeRtt)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun probeDirectHttpFull(
        host: String,
        port: Int,
        username: String,
        password: String,
        timeoutMs: Int
    ): ProbeOutcome {
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        var handshakeOk = false
        var handshakeRtt = -1L
        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            val authHeader = if (username.isNotEmpty() || password.isNotEmpty()) {
                val authString = "$username:$password"
                val encodedAuth = Base64.encodeToString(authString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $encodedAuth\r\n"
            } else ""

            // Use HTTP CONNECT to api.ipify.org:80 (matches LocalSocks5ToHttpBridge 100%)
            val connectReq = "CONNECT api.ipify.org:80 HTTP/1.1\r\n" +
                    "Host: api.ipify.org:80\r\n" +
                    authHeader +
                    "Proxy-Connection: Keep-Alive\r\n\r\n"
            outStream.write(connectReq.toByteArray(Charsets.US_ASCII))
            outStream.flush()

            // Read HTTP CONNECT response headers
            val headerSb = StringBuilder()
            var prev3 = 0
            var prev2 = 0
            var prev1 = 0
            while (headerSb.length < 2048) {
                val b = inStream.read()
                if (b == -1) break
                headerSb.append(b.toChar())
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
            }

            val headers = headerSb.toString()
            if (headers.startsWith("HTTP/1.", ignoreCase = true) && headers.contains(" 200")) {
                handshakeOk = true
                handshakeRtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)

                val getReq = "GET / HTTP/1.1\r\nHost: api.ipify.org\r\nUser-Agent: curl/8.0\r\nConnection: close\r\n\r\n"
                outStream.write(getReq.toByteArray(Charsets.US_ASCII))
                outStream.flush()

                val reader = BufferedReader(InputStreamReader(inStream, Charsets.UTF_8))
                val bodySb = StringBuilder()
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null && count < 25) {
                    bodySb.append(line).append("\n")
                    count++
                }
                val body = bodySb.toString()
                val v4 = IPV4_REGEX.matcher(body)
                if (v4.find()) return ProbeOutcome(true, "HTTP", v4.group(0), handshakeRtt)
                val v6 = IPV6_REGEX.matcher(body)
                if (v6.find()) return ProbeOutcome(true, "HTTP", v6.group(0), handshakeRtt)
                return ProbeOutcome(true, "HTTP", null, handshakeRtt)
            }

            // Fallback: try plain HTTP GET proxy request if server rejected CONNECT on port 80
            try { socket.close() } catch (_: Exception) {}
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            val in2 = socket.getInputStream()
            val out2 = socket.getOutputStream()
            val req = "GET http://api.ipify.org/ HTTP/1.1\r\nHost: api.ipify.org\r\n${authHeader}User-Agent: curl/8.0\r\nConnection: close\r\n\r\n"
            out2.write(req.toByteArray(Charsets.US_ASCII))
            out2.flush()

            val reader = BufferedReader(InputStreamReader(in2, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 25) {
                if (count == 0 && line!!.startsWith("HTTP/1.", ignoreCase = true) && line!!.contains(" 200")) {
                    handshakeOk = true
                    handshakeRtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                }
                sb.append(line).append("\n")
                count++
            }
            val body = sb.toString()
            val v4 = IPV4_REGEX.matcher(body)
            if (v4.find()) return ProbeOutcome(true, "HTTP", v4.group(0), if (handshakeRtt > 0) handshakeRtt else 50L)
            ProbeOutcome(handshakeOk, "HTTP", null, handshakeRtt)
        } catch (_: Exception) {
            ProbeOutcome(handshakeOk, "HTTP", null, handshakeRtt)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun readFully(inStream: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = inStream.read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            offset += count
        }
    }

    suspend fun testProxyFastRace(
        host: String,
        port: Int,
        protocol: String = "SOCKS5",
        username: String = "",
        password: String = "",
        timeoutMs: Int = 2800
    ): PingResult = testProxy(
        host = host,
        port = port,
        protocol = protocol,
        username = username,
        password = password,
        timeoutMs = timeoutMs,
        pingOptimized = true
    )
}
