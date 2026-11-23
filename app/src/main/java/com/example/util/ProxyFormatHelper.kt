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
    private val EXISTING_SESSION_REGEX = Regex(
        """((?:;|__|-|_)(?:sessid|session|sid)([.\-]))([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    fun generateSessionId(length: Int = 8): String {
        val rng = java.security.SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(SESSION_ALPHANUM[rng.nextInt(SESSION_ALPHANUM.length)])
            }
        }
    }

    /**
     * Locks known rotating residential proxy formats (DataImpulse, IPRoyal, Smartproxy/Decodo,
     * Oxylabs, BrightData) onto a single sticky IP session at connection time so the IP shown on
     * Connect stays pinned for Facebook / Chrome / OTP without rotating on every TCP request.
     *
     * - If credentials ALREADY contain a sticky session tag and [forceNewSessionId] is false:
     *   preserves the exact existing session ID untouched (ZERO rotation on connect!).
     * - If [forceNewSessionId] is true (user tapped Rotate IP): rotates ONLY the session ID token.
     * - For unknown/static providers: returns original credentials unmodified so auth never breaks.
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
            if (!forceNewSessionId) {
                return StickySessionResult(
                    username = rawUser,
                    password = rawPass,
                    appliedStickyTag = false,
                    hasStickySession = true,
                    sessionId = userMatch.groupValues[3]
                )
            }
            val newId = generateSessionId(8)
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
            if (!forceNewSessionId) {
                return StickySessionResult(
                    username = rawUser,
                    password = rawPass,
                    appliedStickyTag = false,
                    hasStickySession = true,
                    sessionId = passMatch.groupValues[3]
                )
            }
            val newId = generateSessionId(8)
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

        // 2. DataImpulse format (gw.dataimpulse.com or username containing __cr.xx / __)
        // Official format: user__cr.us;sessid.<8chars> (or user__sessid.<8chars>)
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
