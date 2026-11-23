package com.example.util

/**
 * Universal Auto-Format Proxy Parser (Super Proxy Grade):
 * Supports ALL standard global proxy formats from any provider:
 * 1. host:port:username:password
 * 2. username:password@host:port
 * 3. username:password:host:port
 * 4. host:port (unauthenticated / IP-whitelisted)
 * 5. socks5://username:password@host:port
 * 6. http://username:password@host:port
 * 7. https://username:password@host:port
 *
 * Guarantees:
 * - Preserves RAW credentials exactly without altering underscores, dots, or dashes (e.g. __cr.cm).
 * - Intelligently separates host, port (1-65535), and credentials.
 * - Detects common HTTP ports (80, 8080, 3128, 8888) or explicit http(s) prefixes.
 */
data class ParsedProxy(
    val protocol: String = "SOCKS5",
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val countryCode: String = "US",
    val suggestedName: String = "Imported Proxy"
)

object ProxyFormatHelper {

    fun parse(rawInput: String): ParsedProxy? {
        var raw = rawInput.trim()
        if (raw.isEmpty()) return null

        var protocol = "SOCKS5"
        var countryCode = "US"

        // 1. Detect and strip protocol prefixes
        if (raw.startsWith("socks5://", ignoreCase = true)) {
            protocol = "SOCKS5"
            raw = raw.substring(9)
        } else if (raw.startsWith("socks4://", ignoreCase = true)) {
            protocol = "SOCKS5"
            raw = raw.substring(9)
        } else if (raw.startsWith("http://", ignoreCase = true)) {
            protocol = "HTTP"
            raw = raw.substring(7)
        } else if (raw.startsWith("https://", ignoreCase = true)) {
            protocol = "HTTP"
            raw = raw.substring(8)
        }

        // 2. Check for optional hash label / comment (e.g. #US or #DataImpulse)
        if (raw.contains("#")) {
            val hashIdx = raw.indexOf("#")
            val tag = raw.substring(hashIdx + 1).trim().uppercase()
            raw = raw.substring(0, hashIdx).trim()
            if (tag.length == 2 && tag.all { it.isLetter() }) {
                countryCode = tag
            }
        }

        var host = ""
        var port = 1080
        var username = ""
        var password = ""

        // 3. Format: username:password@host:port
        if (raw.contains("@")) {
            val atIdx = raw.lastIndexOf("@")
            val authPart = raw.substring(0, atIdx)
            val hostPart = raw.substring(atIdx + 1).trim()

            if (authPart.contains(":")) {
                val colonIdx = authPart.indexOf(":")
                username = authPart.substring(0, colonIdx)
                password = authPart.substring(colonIdx + 1)
            } else {
                username = authPart
            }

            if (hostPart.contains(":")) {
                val hParts = hostPart.split(":")
                host = hParts[0].trim()
                port = hParts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1080
            } else {
                host = hostPart
            }
        } else {
            // Delimited formats (colon, comma, semicolon, tab)
            val delim = when {
                raw.contains(":") -> ":"
                raw.contains(",") -> ","
                raw.contains(";") -> ";"
                raw.contains("\t") -> "\t"
                else -> ":"
            }
            val parts = raw.split(delim)

            if (parts.size >= 4) {
                val p1 = parts[1].trim().toIntOrNull()
                val p3 = parts[3].trim().toIntOrNull()

                if (p1 != null && p1 in 1..65535) {
                    // Standard: host:port:username:password
                    host = parts[0].trim()
                    port = p1
                    username = parts[2]
                    // Preserve raw password and any colons inside
                    password = parts.subList(3, parts.size).joinToString(delim)
                } else if (p3 != null && p3 in 1..65535) {
                    // Alternative: username:password:host:port
                    username = parts[0]
                    password = parts[1]
                    host = parts[2].trim()
                    port = p3
                } else {
                    host = parts[0].trim()
                    port = p1 ?: 1080
                    username = parts[2]
                    password = parts.subList(3, parts.size).joinToString(delim)
                }
            } else if (parts.size == 3) {
                // host:port:username
                host = parts[0].trim()
                port = parts[1].trim().toIntOrNull() ?: 1080
                username = parts[2]
            } else if (parts.size == 2) {
                // host:port
                host = parts[0].trim()
                port = parts[1].trim().toIntOrNull() ?: 1080
            } else if (parts.size == 1) {
                host = parts[0].trim()
            }
        }

        if (host.isBlank()) return null

        // Detect HTTP for standard web proxy ports if protocol was not explicitly prefixed
        if (protocol == "SOCKS5" && (port == 80 || port == 8080 || port == 3128 || port == 8888 || port == 8000)) {
            protocol = "HTTP"
        }

        // Infer country code from provider username tags if available (e.g. __cr.cm, -country-gb)
        if (username.isNotEmpty()) {
            val lowerUser = username.lowercase()
            val crMatch = Regex("""(?:cr\.([a-z]{2})|country[_-]([a-z]{2}))""").find(lowerUser)
            if (crMatch != null) {
                val cc = crMatch.groupValues.firstOrNull { it.length == 2 && it != "cr" }?.uppercase()
                if (!cc.isNullOrBlank()) {
                    countryCode = cc
                }
            }
        }

        val shortHost = if (host.length > 15) host.take(12) + ".." else host
        val suggestedName = "$protocol $shortHost:$port"

        return ParsedProxy(
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            password = password,
            countryCode = countryCode,
            suggestedName = suggestedName
        )
    }

    data class StickySessionResult(
        val username: String,
        val password: String,
        val appliedStickyTag: Boolean,
        val hasStickySession: Boolean,
        val sessionId: String? = null
    )

    private val SESSION_ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val SESSION_DIGITS = "0123456789"
    private val EXISTING_SESSION_REGEX = Regex(
        """((?:;|__|-|_)(?:sessid|session|sid|rand|s)([.\-_]))([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    fun generateSessionId(length: Int = 8, digitsOnly: Boolean = false): String {
        val rng = java.security.SecureRandom()
        val alphabet = if (digitsOnly) SESSION_DIGITS else SESSION_ALPHANUM
        val safeLen = length.coerceIn(6, 16)
        return buildString(safeLen) {
            repeat(safeLen) {
                append(alphabet[rng.nextInt(alphabet.length)])
            }
        }
    }

    /**
     * Generates or rotates a sticky session ID for residential/rotating proxy gateways so:
     * 1. On EVERY new Connect ([forceNewSessionId] = true): requests a FRESH unique IP from the
     *    provider's 2-100+ IP pool instead of repeating the previous IP.
     * 2. Once connected: locks that exact session ID onto the VPN tunnel so Facebook / Chrome / OTP
     *    stay on that ONE single IP for the entire session without mid-session rotation.
     * 3. For unknown/static single-IP providers: leaves credentials untouched so auth never breaks.
     */
    fun applyStickySession(
        host: String,
        port: Int,
        username: String,
        password: String,
        forceNewSessionId: Boolean = false
    ): StickySessionResult {
        val cleanHost = host.trim().lowercase()
        val rawUser = username.trim()
        val rawPass = password.trim()

        if (rawUser.isEmpty() && rawPass.isEmpty()) {
            return StickySessionResult(rawUser, rawPass, appliedStickyTag = false, hasStickySession = false)
        }

        // 1. Check if username or password ALREADY has a sticky session parameter
        val userMatch = EXISTING_SESSION_REGEX.find(rawUser)
        if (userMatch != null) {
            val oldToken = userMatch.groupValues[3]
            if (!forceNewSessionId) {
                return StickySessionResult(
                    username = rawUser,
                    password = rawPass,
                    appliedStickyTag = false,
                    hasStickySession = true,
                    sessionId = oldToken
                )
            }
            val digitsOnly = oldToken.all { it.isDigit() }
            var newId = generateSessionId(oldToken.length.coerceIn(6, 12), digitsOnly)
            if (newId == oldToken) newId = generateSessionId(oldToken.length.coerceIn(6, 12), digitsOnly)
            val updatedUser = rawUser.replaceRange(
                userMatch.groups[3]!!.range,
                newId
            )
            return StickySessionResult(
                username = updatedUser,
                password = rawPass,
                appliedStickyTag = true,
                hasStickySession = true,
                sessionId = newId
            )
        }

        val passMatch = EXISTING_SESSION_REGEX.find(rawPass)
        if (passMatch != null) {
            val oldToken = passMatch.groupValues[3]
            if (!forceNewSessionId) {
                return StickySessionResult(
                    username = rawUser,
                    password = rawPass,
                    appliedStickyTag = false,
                    hasStickySession = true,
                    sessionId = oldToken
                )
            }
            val digitsOnly = oldToken.all { it.isDigit() }
            var newId = generateSessionId(oldToken.length.coerceIn(6, 12), digitsOnly)
            if (newId == oldToken) newId = generateSessionId(oldToken.length.coerceIn(6, 12), digitsOnly)
            val updatedPass = rawPass.replaceRange(
                passMatch.groups[3]!!.range,
                newId
            )
            return StickySessionResult(
                username = rawUser,
                password = updatedPass,
                appliedStickyTag = true,
                hasStickySession = true,
                sessionId = newId
            )
        }

        val newId = generateSessionId(8)

        // 2. DataImpulse format (gw.dataimpulse.com or username containing __cr.xx)
        // Official format: user__cr.us;sessid.<8chars>
        if (cleanHost.contains("dataimpulse") || rawUser.contains("__cr.", ignoreCase = true)) {
            val stickyUser = if (rawUser.contains("__")) {
                "$rawUser;sessid.$newId"
            } else if (rawUser.isNotEmpty()) {
                "${rawUser}__sessid.$newId"
            } else {
                rawUser
            }
            if (stickyUser != rawUser) {
                return StickySessionResult(
                    username = stickyUser,
                    password = rawPass,
                    appliedStickyTag = true,
                    hasStickySession = true,
                    sessionId = newId
                )
            }
        }

        // 3. IPRoyal format (geo.iproyal.com or password with _country- / _city-)
        // Official format: pass_country-us_session-<8chars>_lifetime-30m
        if (cleanHost.contains("iproyal") ||
            rawPass.contains("_country-", ignoreCase = true) ||
            rawPass.contains("_city-", ignoreCase = true)
        ) {
            if (rawPass.isNotEmpty()) {
                val stickyPass = if (rawPass.contains("_lifetime-", ignoreCase = true)) {
                    "${rawPass}_session-$newId"
                } else {
                    "${rawPass}_session-${newId}_lifetime-30m"
                }
                return StickySessionResult(
                    username = rawUser,
                    password = stickyPass,
                    appliedStickyTag = true,
                    hasStickySession = true,
                    sessionId = newId
                )
            }
        }

        // 4. Decodo / Smartproxy format (gate.smartproxy.com / gate.decodo.com or user-...-country-...)
        // Official format: user-xxx-country-us-session-<8chars>-sessionduration-30
        if (cleanHost.contains("smartproxy") ||
            cleanHost.contains("decodo") ||
            (rawUser.startsWith("user-", ignoreCase = true) && rawUser.contains("-country-", ignoreCase = true))
        ) {
            if (rawUser.isNotEmpty()) {
                val stickyUser = if (rawUser.contains("-sessionduration-", ignoreCase = true)) {
                    "$rawUser-session-$newId"
                } else {
                    "$rawUser-session-${newId}-sessionduration-30"
                }
                return StickySessionResult(
                    username = stickyUser,
                    password = rawPass,
                    appliedStickyTag = true,
                    hasStickySession = true,
                    sessionId = newId
                )
            }
        }

        // 5. Oxylabs format (pr.oxylabs.io or customer-...)
        // Official format: customer-xxx-cc-us-sessid-<8chars>-sesstime-30
        if (cleanHost.contains("oxylabs") || rawUser.startsWith("customer-", ignoreCase = true)) {
            if (rawUser.isNotEmpty()) {
                val stickyUser = if (rawUser.contains("-sesstime-", ignoreCase = true)) {
                    "$rawUser-sessid-$newId"
                } else {
                    "$rawUser-sessid-${newId}-sesstime-30"
                }
                return StickySessionResult(
                    username = stickyUser,
                    password = rawPass,
                    appliedStickyTag = true,
                    hasStickySession = true,
                    sessionId = newId
                )
            }
        }

        // 6. BrightData / Luminati format (brd.superproxy.io or brd-customer-...)
        // Official format: brd-customer-xxx-zone-yyy-session-<8chars>
        if (cleanHost.contains("superproxy.io") ||
            cleanHost.contains("luminati") ||
            rawUser.startsWith("brd-customer-", ignoreCase = true)
        ) {
            if (rawUser.isNotEmpty()) {
                return StickySessionResult(
                    username = "$rawUser-session-$newId",
                    password = rawPass,
                    appliedStickyTag = true,
                    hasStickySession = true,
                    sessionId = newId
                )
            }
        }

        // 7. LunaProxy / PIA / 922 / ABCProxy / TabProxy / CherryProxy / PYProxy / ProxySeller
        // Standard format: user-region-us-sessid-<8chars>-sessTime-30 (or -zone-...-region-...)
        if (cleanHost.contains("lunaproxy") ||
            cleanHost.contains("922proxy") ||
            cleanHost.contains("abcproxy") ||
            cleanHost.contains("piaproxy") ||
            cleanHost.contains("tabproxy") ||
            cleanHost.contains("cherryproxy") ||
            cleanHost.contains("pyproxy") ||
            (rawUser.contains("-region-", ignoreCase = true) || rawUser.contains("-zone-", ignoreCase = true))
        ) {
            if (rawUser.isNotEmpty()) {
                val stickyUser = if (rawUser.contains("-sesstime-", ignoreCase = true)) {
                    "$rawUser-sessid-$newId"
                } else {
                    "$rawUser-sessid-${newId}-sessTime-30"
                }
                return StickySessionResult(
                    username = stickyUser,
                    password = rawPass,
                    appliedStickyTag = true,
                    hasStickySession = true,
                    sessionId = newId
                )
            }
        }

        // 8. Webshare rotating endpoint (p.webshare.io / username-rotate -> username-<1..100> if needed, otherwise leave safe)
        if (cleanHost.contains("webshare") && rawUser.endsWith("-rotate", ignoreCase = true)) {
            val baseUser = rawUser.dropLast(7)
            val slot = (java.security.SecureRandom().nextInt(100) + 1)
            return StickySessionResult(
                username = "$baseUser-$slot",
                password = rawPass,
                appliedStickyTag = true,
                hasStickySession = true,
                sessionId = slot.toString()
            )
        }

        // Unknown / static provider: never mutate credentials
        return StickySessionResult(
            username = rawUser,
            password = rawPass,
            appliedStickyTag = false,
            hasStickySession = false,
            sessionId = null
        )
    }
}
