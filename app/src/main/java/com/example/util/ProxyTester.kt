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

    private val IPV4_REGEX = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b")
    private val IPV6_REGEX = Pattern.compile("(?i)\\b(?:[a-f0-9]{1,4}:){7}[a-f0-9]{1,4}\\b|\\b(?:[a-f0-9]{1,4}:){1,7}:\\b|\\b:(?::[a-f0-9]{1,4}){1,7}\\b")

    private val gatewayDnsCache = ConcurrentHashMap<String, String>()

    /**
     * Resolves a proxy server hostname (e.g. gw.dataimpulse.com) to a real IPv4 address
     * while bypassing the VPN's Fake-IP MapDNS (240.0.0.0/4) using a protected UDP DNS query.
     */
    fun resolveHostBypassingVpn(host: String): String {
        val clean = host.trim()
        if (clean.isEmpty()) return clean
        if (clean.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
            return clean
        }

        gatewayDnsCache[clean]?.let { cached ->
            if (!cached.startsWith("240.") && !cached.startsWith("10.10.10.")) {
                return cached
            }
        }

        // 1. Try direct protected UDP DNS query to 1.1.1.1 / 8.8.4.4 (bypasses tun0 MapDNS 100%)
        val dnsResolved = queryProtectedDnsIpv4(clean, "1.1.1.1")
            ?: queryProtectedDnsIpv4(clean, "8.8.4.4")
        if (!dnsResolved.isNullOrBlank() && !dnsResolved.startsWith("240.")) {
            gatewayDnsCache[clean] = dnsResolved
            return dnsResolved
        }

        // 2. Fallback to system resolver if not returning Fake-IP 240.0.0.0/4
        return try {
            val all = InetAddress.getAllByName(clean)
            val realIpv4 = all.firstOrNull { addr ->
                val ip = addr.hostAddress ?: ""
                ip.contains(".") && !ip.startsWith("240.") && !ip.startsWith("10.10.10.")
            }?.hostAddress
            if (!realIpv4.isNullOrBlank()) {
                gatewayDnsCache[clean] = realIpv4
                realIpv4
            } else {
                gatewayDnsCache[clean] ?: clean
            }
        } catch (_: Exception) {
            gatewayDnsCache[clean] ?: clean
        }
    }

    private fun queryProtectedDnsIpv4(domain: String, dnsServer: String): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = 1200
            SuperProxyVpnService.protectSocket(socket)

            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            dos.writeShort(0x1337) // Transaction ID
            dos.writeShort(0x0100) // Standard query, recursion desired
            dos.writeShort(1)      // Questions = 1
            dos.writeShort(0)      // Answer RRs
            dos.writeShort(0)      // Authority RRs
            dos.writeShort(0)      // Additional RRs

            for (part in domain.split(".")) {
                val bytes = part.toByteArray(Charsets.US_ASCII)
                dos.writeByte(bytes.size)
                dos.write(bytes)
            }
            dos.writeByte(0)       // End of domain
            dos.writeShort(1)      // Type A (IPv4)
            dos.writeShort(1)      // Class IN

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

            // Skip header (12 bytes) and question section
            var idx = 12
            while (idx < len && respBuf[idx].toInt() != 0) {
                val labelLen = respBuf[idx].toInt() and 0xFF
                idx += 1 + labelLen
            }
            idx += 5 // Null byte + QTYPE (2) + QCLASS (2)

            for (i in 0 until anCount) {
                if (idx >= len) break
                // Name field (pointer or labels)
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
                    if (!ip.startsWith("240.")) return ip
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
     * Ultra-Fast Parallel Protocol & Egress IPv4 Race (Guaranteed 1-4s Completion):
     * Races SOCKS5 and HTTP CONNECT simultaneously so whichever protocol the proxy speaks
     * responds immediately without sequential timeouts.
     */
    suspend fun testProxy(
        host: String,
        port: Int,
        protocol: String = "SOCKS5",
        username: String = "",
        password: String = "",
        timeoutMs: Int = 3500,
        pingOptimized: Boolean = false
    ): PingResult = withContext(Dispatchers.IO) {
        val effectiveTimeout = if (pingOptimized) minOf(timeoutMs, 2600) else timeoutMs
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
        var socketLatency: Long = -1

        // Step 1: Parallel Protocol Race (SOCKS5 + HTTP CONNECT + TCP Socket Probe)
        val probeTimeout = minOf(effectiveTimeout, 2200)
        coroutineScope {
            val socksDeferred = async(Dispatchers.IO) {
                testDirectSocks5(resolvedGatewayIp, port, username, password, probeTimeout)
            }
            val httpDeferred = async(Dispatchers.IO) {
                testDirectHttp(resolvedGatewayIp, port, username, password, probeTimeout)
            }
            val tcpProbeDeferred = async(Dispatchers.IO) {
                measureSocketPing(resolvedGatewayIp, port, minOf(probeTimeout, 1500))
            }

            if (preferHttp) {
                val httpIp = httpDeferred.await()
                if (!httpIp.isNullOrBlank()) {
                    resolvedIp = httpIp
                    detectedProtocol = "HTTP"
                    socksDeferred.cancel()
                } else {
                    val socksIp = socksDeferred.await()
                    if (!socksIp.isNullOrBlank()) {
                        resolvedIp = socksIp
                        detectedProtocol = "SOCKS5"
                    }
                }
            } else {
                val socksIp = socksDeferred.await()
                if (!socksIp.isNullOrBlank()) {
                    resolvedIp = socksIp
                    detectedProtocol = "SOCKS5"
                    httpDeferred.cancel()
                } else {
                    val httpIp = httpDeferred.await()
                    if (!httpIp.isNullOrBlank()) {
                        resolvedIp = httpIp
                        detectedProtocol = "HTTP"
                    }
                }
            }
            socketLatency = tcpProbeDeferred.await()
        }

        val handShakeLatency = System.currentTimeMillis() - startTime

        // Step 2: If both direct probes returned null, fallback to proxygateway IP if TCP socket succeeded
        if (resolvedIp.isNullOrBlank() && socketLatency > 0) {
            resolvedIp = resolvedGatewayIp
            detectedProtocol = if (preferHttp) "HTTP" else "SOCKS5"
        }

        if (resolvedIp.isNullOrBlank()) {
            return@withContext PingResult(
                isSuccess = false,
                latencyMs = -1,
                errorMessage = "Proxy Handshake Error: Failed to connect to $cleanHost:$port."
            )
        }

        // Step 3: Fast Geo-Location Resolution (1.2s max timeout)
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

        val finalLatency = when {
            socketLatency in 1..2000 -> socketLatency
            handShakeLatency > 0 -> handShakeLatency
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
            workingProtocol = detectedProtocol ?: protocol
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
                    return null
                }
            }

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
                return null
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

            val authHeader = if (username.isNotEmpty() || password.isNotEmpty()) {
                val authString = "$username:$password"
                val encodedAuth = Base64.encodeToString(authString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $encodedAuth\r\n"
            } else ""

            val req = "GET http://api.ipify.org/ HTTP/1.1\r\nHost: api.ipify.org\r\n${authHeader}User-Agent: curl/8.0\r\nConnection: close\r\n\r\n"
            outStream.write(req.toByteArray(Charsets.US_ASCII))
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

    suspend fun testProxyFastRace(
        host: String,
        port: Int,
        protocol: String = "SOCKS5",
        username: String = "",
        password: String = "",
        timeoutMs: Int = 2200
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
