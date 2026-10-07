package com.example.util

import android.util.Base64
import com.example.service.SuperProxyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.Socket
import java.net.URL
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
        val errorMessage: String? = null
    )

    private val IPV4_REGEX = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b")
    private val IPV6_REGEX = Pattern.compile("(?i)\\b(?:[a-f0-9]{1,4}:){7}[a-f0-9]{1,4}\\b|\\b(?:[a-f0-9]{1,4}:){1,7}:\\b|\\b:(?::[a-f0-9]{1,4}){1,7}\\b")

    // Fast, reliable global IP check endpoints with sequential fallback (lightweight plain text endpoints first)
    private val IP_CHECK_ENDPOINTS = listOf(
        "http://icanhazip.com",
        "http://api.ipify.org",
        "http://checkip.amazonaws.com",
        "https://ifconfig.me/ip"
    )

    /**
     * Multi-Endpoint Real-Time IP & Geo Detection (Super Proxy Grade):
     * 1. Queries external IP directly through the configured proxy (fast 2.5s connect timeout).
     * 2. Extracts real public IP within 3-5 seconds.
     * 3. Non-blocking Geo-Location lookup for instant UI response.
     */
    suspend fun testProxy(
        host: String,
        port: Int,
        protocol: String = "SOCKS5",
        username: String = "",
        password: String = "",
        timeoutMs: Int = 3500,
        pingOptimized: Boolean = true
    ): PingResult = withContext(Dispatchers.IO) {
        val effectiveTimeout = if (pingOptimized) 2500 else timeoutMs
        val startTime = System.currentTimeMillis()

        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || port <= 0 || port > 65535) {
            return@withContext PingResult(
                isSuccess = false,
                latencyMs = -1,
                errorMessage = "Invalid Host or Port ($cleanHost:$port)"
            )
        }

        // Configure global authenticator for SOCKS5 and HTTP credentials
        if (username.isNotEmpty()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password.toCharArray())
                }
            })
        }

        // Configure Proxy object based on protocol
        val isHttp = protocol.equals("HTTP", ignoreCase = true) || protocol.equals("HTTPS", ignoreCase = true)
        val proxyType = if (isHttp) Proxy.Type.HTTP else Proxy.Type.SOCKS
        val javaProxy = Proxy(proxyType, InetSocketAddress(cleanHost, port))

        var resolvedIp: String? = null

        // Sequential fallback IP detection directly through configured proxy
        for (endpoint in IP_CHECK_ENDPOINTS) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection(javaProxy) as HttpURLConnection
                conn.connectTimeout = effectiveTimeout
                conn.readTimeout = effectiveTimeout
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "curl/7.88.1")

                // Add Proxy-Authorization header if HTTP proxy with credentials
                if (isHttp && username.isNotEmpty()) {
                    val authString = "$username:$password"
                    val encodedAuth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
                    conn.setRequestProperty("Proxy-Authorization", "Basic $encodedAuth")
                }

                if (conn.responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = reader.readText().trim()
                    reader.close()

                    var detectedIpVersion = "IPv4"
                    val v4 = IPV4_REGEX.matcher(body)
                    val v6 = IPV6_REGEX.matcher(body)
                    if (v4.find()) {
                        resolvedIp = v4.group(0)
                        detectedIpVersion = "IPv4"
                        conn.disconnect()
                        break
                    } else if (v6.find()) {
                        resolvedIp = v6.group(0)
                        detectedIpVersion = "IPv6"
                        conn.disconnect()
                        break
                    } else if (body.isNotBlank() && !body.contains("<") && body.length < 65) {
                        resolvedIp = body
                        detectedIpVersion = if (body.contains(":")) "IPv6" else "IPv4"
                        conn.disconnect()
                        break
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Endpoint failed or timed out, immediately fall back to next endpoint
            }
        }

        // If fallback endpoints failed through proxy:
        if (resolvedIp.isNullOrBlank()) {
            return@withContext PingResult(
                isSuccess = false,
                latencyMs = -1,
                errorMessage = "Proxy Handshake Error: Failed to resolve external IP through $cleanHost:$port. Verify host, port, and credentials."
            )
        }

        val ipFetchLatency = System.currentTimeMillis() - startTime

        // Fast Geo-Location (Country, City, ISP) using ip-api / ipwho.is with short 1.5s timeout
        var countryCode = "US"
        var countryName = "United States"
        var city = ""
        var isp = ""
        var timezone = ""

        try {
            val ipApiUrl = URL("http://ip-api.com/json/$resolvedIp?fields=query,status,country,countryCode,city,isp,timezone")
            val conn = ipApiUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
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
                val geoUrl = URL("https://ipwho.is/$resolvedIp")
                val conn = geoUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
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

        val detectedVersion = if (resolvedIp?.contains(":") == true) "IPv6" else "IPv4"

        PingResult(
            isSuccess = true,
            latencyMs = ipFetchLatency,
            resolvedIp = resolvedIp,
            ipVersion = detectedVersion,
            countryCode = countryCode,
            countryName = countryName,
            city = city,
            isp = isp,
            timezone = timezone
        )
    }
}
