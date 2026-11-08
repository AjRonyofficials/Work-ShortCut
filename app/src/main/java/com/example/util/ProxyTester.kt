package com.example.util

import android.util.Base64
import com.example.service.SuperProxyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    private val IPV4_REGEX = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b")
    private val IPV6_REGEX = Pattern.compile("(?i)\\b(?:[a-f0-9]{1,4}:){7}[a-f0-9]{1,4}\\b|\\b(?:[a-f0-9]{1,4}:){1,7}:\\b|\\b:(?::[a-f0-9]{1,4}){1,7}\\b")

    private val gatewayDnsCache = ConcurrentHashMap<String, String>()

    // Lightweight, ultra-fast global IP check endpoints (plain-text HTTP first for 0-RTT speed)
    private val IP_CHECK_ENDPOINTS = listOf(
        "http://api.ipify.org",
        "http://icanhazip.com",
        "http://checkip.amazonaws.com",
        "https://api.ipify.org?format=text",
        "https://icanhazip.com",
        "https://ifconfig.me/ip"
    )

    private fun isValidRealIpv4(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        if (!ip.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) return false
        if (ip.startsWith("240.") || ip.startsWith("198.18.") || ip == "0.0.0.0") return false
        return true
    }

    /**
     * Resolves a proxy gateway hostname safely off the main thread so SuperProxyVpnService.startVpn()
     * never throws NetworkOnMainThreadException (which caused the 10s delay in #132).
     */
    fun resolveHostFastBlocking(host: String, timeoutMs: Long = 1500L): String {
        val clean = host.trim()
        if (clean.isEmpty()) return clean
        if (isValidRealIpv4(clean)) return clean

        gatewayDnsCache[clean]?.let { cached ->
            if (isValidRealIpv4(cached)) return cached
        }

        var resolved: String? = null
        val latch = CountDownLatch(1)
        Thread {
            try {
                val all = InetAddress.getAllByName(clean)
                val ipv4 = all.firstOrNull { it is Inet4Address && isValidRealIpv4(it.hostAddress) }?.hostAddress
                    ?: all.firstOrNull { isValidRealIpv4(it.hostAddress) }?.hostAddress
                if (!ipv4.isNullOrBlank()) {
                    gatewayDnsCache[clean] = ipv4
                    resolved = ipv4
                } else {
                    val udpIp = queryProtectedDnsIpv4(clean, "8.8.8.8")
                        ?: queryProtectedDnsIpv4(clean, "1.1.1.1")
                    if (isValidRealIpv4(udpIp)) {
                        gatewayDnsCache[clean] = udpIp!!
                        resolved = udpIp
                    }
                }
            } catch (_: Exception) {
            } finally {
                latch.countDown()
            }
        }.apply { isDaemon = true; start() }

        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {}

        return resolved ?: gatewayDnsCache[clean] ?: clean
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
            dos.writeShort(1)
            dos.writeShort(1)

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
                idx += 1 + (respBuf[idx].toInt() and 0xFF)
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
     * #132 Proven Proxy Test & Real-Time IP + Geo Detection, upgraded with parallel
     * SOCKS5 + HTTP racing so protocol detection finishes in 0.4s - 1.5s instead of 10s!
     */
    suspend fun testProxy(
        host: String,
        port: Int,
        protocol: String = "SOCKS5",
        username: String = "",
        password: String = "",
        timeoutMs: Int = 4500,
        pingOptimized: Boolean = false,
        onProtocolDetected: ((String, String) -> Unit)? = null
    ): PingResult = withContext(Dispatchers.IO) {
        val effectiveTimeout = if (pingOptimized) 3000 else timeoutMs
        val startTime = System.currentTimeMillis()

        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || port <= 0 || port > 65535) {
            return@withContext PingResult(
                isSuccess = false,
                latencyMs = -1,
                errorMessage = "Invalid Host or Port ($cleanHost:$port)"
            )
        }

        // Resolve hostname to real IPv4 first (and cache it for SuperProxyVpnService)
        val connectHost = resolveHostFastBlocking(cleanHost, 1500L)

        // Configure global authenticator for SOCKS5 and HTTP credentials
        if (username.isNotEmpty()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password.toCharArray())
                }
            })
        }

        var resolvedIp: String? = null
        val isHttp = protocol.equals("HTTP", ignoreCase = true) || protocol.equals("HTTPS", ignoreCase = true)
        var detectedProtocol: String? = null
        var handshakeLatency: Long = -1

        // Step 1: Race #132's exact testDirectSocks5 and testDirectHttp IN PARALLEL!
        // Whichever succeeds first returns immediately without waiting for the other protocol to time out.
        val probeTimeout = minOf(effectiveTimeout, 3000)
        coroutineScope {
            val winnerChannel = Channel<Pair<String, String>?>(capacity = 2)

            val socksJob = launch(Dispatchers.IO) {
                val ip = testDirectSocks5(connectHost, port, username, password, probeTimeout)
                if (!ip.isNullOrBlank()) {
                    winnerChannel.trySend("SOCKS5" to ip)
                } else {
                    winnerChannel.trySend(null)
                }
            }

            val httpJob = launch(Dispatchers.IO) {
                val ip = testDirectHttp(connectHost, port, username, password, probeTimeout)
                if (!ip.isNullOrBlank()) {
                    winnerChannel.trySend("HTTP" to ip)
                } else {
                    winnerChannel.trySend(null)
                }
            }

            repeat(2) {
                val res = winnerChannel.receive()
                if (res != null && resolvedIp == null) {
                    detectedProtocol = res.first
                    resolvedIp = res.second
                    handshakeLatency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                    socksJob.cancel()
                    httpJob.cancel()
                    return@repeat
                }
            }
            winnerChannel.close()
        }

        // Step 2: Fallback via Java HttpURLConnection (#132 fallback) if direct socket did not parse
        if (resolvedIp.isNullOrBlank()) {
            val proxyType = if (isHttp) Proxy.Type.HTTP else Proxy.Type.SOCKS
            val javaProxy = Proxy(proxyType, InetSocketAddress(connectHost, port))

            for (endpoint in IP_CHECK_ENDPOINTS) {
                try {
                    val url = URL(endpoint)
                    val conn = url.openConnection(javaProxy) as HttpURLConnection
                    conn.connectTimeout = minOf(effectiveTimeout, 2000)
                    conn.readTimeout = minOf(effectiveTimeout, 2000)
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "curl/7.88.1")

                    if (isHttp && username.isNotEmpty()) {
                        val authString = "$username:$password"
                        val encodedAuth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
                        conn.setRequestProperty("Proxy-Authorization", "Basic $encodedAuth")
                    }

                    if (conn.responseCode in 200..299) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val body = reader.readText().trim()
                        reader.close()

                        val v4 = IPV4_REGEX.matcher(body)
                        val v6 = IPV6_REGEX.matcher(body)
                        if (v4.find()) {
                            resolvedIp = v4.group(0)
                            detectedProtocol = if (isHttp) "HTTP" else "SOCKS5"
                            handshakeLatency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                            conn.disconnect()
                            break
                        } else if (v6.find()) {
                            resolvedIp = v6.group(0)
                            detectedProtocol = if (isHttp) "HTTP" else "SOCKS5"
                            handshakeLatency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                            conn.disconnect()
                            break
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }
        }

        if (resolvedIp.isNullOrBlank()) {
            return@withContext PingResult(
                isSuccess = false,
                latencyMs = -1,
                errorMessage = "Proxy Handshake Error: Failed to connect or authenticate with $cleanHost:$port."
            )
        }

        // Notify caller IMMEDIATELY (at ~0.5s - 1.2s) before running Geo-IP lookup!
        if (detectedProtocol != null && !resolvedIp.isNullOrBlank()) {
            try {
                onProtocolDetected?.invoke(detectedProtocol!!, resolvedIp!!)
            } catch (_: Exception) {}
        }

        // Step 3: Fast Geo-Location Lookup for the resolved Proxy IP (#132 logic)
        var countryCode = "US"
        var countryName = "United States"
        var city = ""
        var isp = ""
        var timezone = ""

        try {
            val ipApiUrl = URL("http://ip-api.com/json/$resolvedIp?fields=status,country,countryCode,city,isp,timezone")
            val conn = ipApiUrl.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                if (json.optString("status") == "success") {
                    countryCode = json.optString("countryCode", "US")
                    countryName = json.optString("country", "United States")
                    city = json.optString("city", "")
                    isp = json.optString("isp", "")
                    timezone = json.optString("timezone", "")
                }
            }
            conn.disconnect()
        } catch (_: Exception) {
            try {
                val fallbackGeoUrl = URL("https://ipwho.is/$resolvedIp")
                val conn = fallbackGeoUrl.openConnection(Proxy.NO_PROXY) as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    if (json.optBoolean("success", false)) {
                        countryCode = json.optString("country_code", "US")
                        countryName = json.optString("country", "United States")
                        city = json.optString("city", "")
                        val connObj = json.optJSONObject("connection")
                        isp = connObj?.optString("isp", "") ?: ""
                        val timeObj = json.optJSONObject("timezone")
                        timezone = timeObj?.optString("id", "") ?: ""
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }

        val totalLatency = System.currentTimeMillis() - startTime
        val finalLatency = if (handshakeLatency > 0) handshakeLatency else totalLatency
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

    fun measureSocketPing(host: String, port: Int, timeoutMs: Int = 2000): Long {
        return try {
            val connectHost = resolveHostFastBlocking(host, 1000L)
            val start = System.currentTimeMillis()
            val socket = Socket()
            socket.tcpNoDelay = true
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(connectHost, port), timeoutMs)
            val latency = System.currentTimeMillis() - start
            socket.close()
            latency
        } catch (_: Exception) {
            -1L
        }
    }

    private fun testDirectSocks5(
        host: String,
        port: Int,
        username: String,
        password: String,
        timeoutMs: Int
    ): String? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            // 1. SOCKS5 Method Negotiation (RFC 1928)
            if (username.isNotEmpty()) {
                outStream.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                outStream.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            outStream.flush()

            val ver = inStream.read()
            val selectedMethod = inStream.read()
            if (ver != 0x05 || selectedMethod == -1 || selectedMethod == 0xFF) {
                return null
            }

            // 2. Subnegotiation (RFC 1929)
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
                if (authStatus != 0x00) {
                    return null
                }
            }

            // 3. Connect request to api.ipify.org:80 (RFC 1928)
            val domain = "api.ipify.org"
            val dBytes = domain.toByteArray(Charsets.US_ASCII)
            val req = ByteArray(4 + 1 + dBytes.size + 2)
            req[0] = 0x05 // SOCKS5
            req[1] = 0x01 // CONNECT
            req[2] = 0x00 // RSV
            req[3] = 0x03 // DOMAINNAME
            req[4] = dBytes.size.toByte()
            System.arraycopy(dBytes, 0, req, 5, dBytes.size)
            req[5 + dBytes.size] = 0 // Port 80 high byte
            req[6 + dBytes.size] = 80 // Port 80 low byte

            outStream.write(req)
            outStream.flush()

            val repVer = inStream.read()
            val rep = inStream.read()
            val rsv = inStream.read()
            val atyp = inStream.read()
            if (rep != 0x00) {
                return null
            }

            when (atyp) {
                0x01 -> { val b = ByteArray(4); readFully(inStream, b) }
                0x03 -> { val len = inStream.read(); if (len > 0) { val b = ByteArray(len); readFully(inStream, b) } }
                0x04 -> { val b = ByteArray(16); readFully(inStream, b) }
            }
            inStream.read(); inStream.read() // Skip port

            // 4. HTTP GET
            val httpReq = "GET / HTTP/1.1\r\nHost: api.ipify.org\r\nUser-Agent: curl/7.88.1\r\nConnection: close\r\n\r\n"
            outStream.write(httpReq.toByteArray(Charsets.US_ASCII))
            outStream.flush()

            val reader = BufferedReader(InputStreamReader(inStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 30) {
                sb.append(line).append("\n")
                count++
            }
            val body = sb.toString()
            val v4 = IPV4_REGEX.matcher(body)
            if (v4.find()) return v4.group(0)
            val v6 = IPV6_REGEX.matcher(body)
            if (v6.find()) return v6.group(0)
            null
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun testDirectHttp(
        host: String,
        port: Int,
        username: String,
        password: String,
        timeoutMs: Int
    ): String? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            SuperProxyVpnService.protectSocket(socket)
            socket.connect(InetSocketAddress(host, port), timeoutMs)

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            val authHeader = if (username.isNotEmpty()) {
                val authString = "$username:$password"
                val encodedAuth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $encodedAuth\r\n"
            } else ""

            val req = "GET http://api.ipify.org/ HTTP/1.1\r\nHost: api.ipify.org\r\n${authHeader}User-Agent: curl/7.88.1\r\nConnection: close\r\n\r\n"
            outStream.write(req.toByteArray(Charsets.US_ASCII))
            outStream.flush()

            val reader = BufferedReader(InputStreamReader(inStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 30) {
                sb.append(line).append("\n")
                count++
            }
            val body = sb.toString()
            val v4 = IPV4_REGEX.matcher(body)
            if (v4.find()) return v4.group(0)
            val v6 = IPV6_REGEX.matcher(body)
            if (v6.find()) return v6.group(0)
            null
        } catch (_: Exception) {
            null
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
}
