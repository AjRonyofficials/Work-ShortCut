package com.example.util

data class ParsedProxy(
    val host: String,
    val port: Int,
    val protocol: String = "SOCKS5",
    val username: String = "",
    val password: String = "",
    val countryCode: String = "US",
    val suggestedName: String = "Imported Proxy"
)

object ProxyFormatHelper {

    fun parse(rawInput: String?): ParsedProxy? {
        if (rawInput.isNullOrBlank()) return null
        var str = rawInput.trim()

        var protocol = "SOCKS5"
        if (str.startsWith("socks5://", ignoreCase = true)) {
            protocol = "SOCKS5"
            str = str.substring("socks5://".length)
        } else if (str.startsWith("http://", ignoreCase = true)) {
            protocol = "HTTP"
            str = str.substring("http://".length)
        } else if (str.startsWith("https://", ignoreCase = true)) {
            protocol = "HTTP"
            str = str.substring("https://".length)
        } else if (str.startsWith("socks4://", ignoreCase = true)) {
            protocol = "SOCKS5"
            str = str.substring("socks4://".length)
        }

        var host = ""
        var port = 1080
        var user = ""
        var pass = ""

        if (str.contains("@")) {
            // format: user:pass@host:port
            val parts = str.split("@", limit = 2)
            val authPart = parts[0]
            val netPart = parts[1]

            if (authPart.contains(":")) {
                val a = authPart.split(":", limit = 2)
                user = a[0]
                pass = a[1]
            } else {
                user = authPart
            }

            if (netPart.contains(":")) {
                val n = netPart.split(":", limit = 2)
                host = n[0]
                port = n[1].toIntOrNull() ?: 1080
            } else {
                host = netPart
            }
        } else {
            val parts = str.split(":")
            when (parts.size) {
                2 -> {
                    // host:port
                    host = parts[0]
                    port = parts[1].toIntOrNull() ?: 1080
                }
                4 -> {
                    // host:port:user:pass
                    host = parts[0]
                    port = parts[1].toIntOrNull() ?: 1080
                    user = parts[2]
                    pass = parts[3]
                }
                3 -> {
                    host = parts[0]
                    port = parts[1].toIntOrNull() ?: 1080
                    user = parts[2]
                }
                else -> {
                    return null
                }
            }
        }

        if (host.isBlank() || port <= 0) return null

        // Detect country code from username (e.g. b1309dbab294810bd836_cr.kh, user-zone-custom-region-kh)
        var detectedCountry = "US"
        val crMatch = Regex("""(?:cr\.([a-z]{2})|country[_-]([a-z]{2})|region[_-]([a-z]{2}))""", RegexOption.IGNORE_CASE).find(user)
        if (crMatch != null) {
            val cc = crMatch.groupValues.firstOrNull { g -> g.length == 2 && !g.equals("cr", ignoreCase = true) }?.uppercase()
            if (!cc.isNullOrBlank()) {
                detectedCountry = cc
            }
        }

        val suggestedName = if (user.isNotBlank()) "Proxy [$detectedCountry]" else "Proxy $host:$port"

        return ParsedProxy(
            host = host,
            port = port,
            protocol = protocol,
            username = user,
            password = pass,
            countryCode = detectedCountry,
            suggestedName = suggestedName
        )
    }
}
