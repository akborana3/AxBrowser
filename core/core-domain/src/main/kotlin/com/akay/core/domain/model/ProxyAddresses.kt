package com.akay.core.domain.model

/**
 * Host/credential formatting shared by WebView proxy rules, OkHttp, and yt-dlp.
 * WebView's proxy grammar is `scheme://host:port` (IPv6 must be bracketed) and
 * rejects userinfo, so credentials never belong in [ProxyServer.toProxyRule].
 */
object ProxyAddresses {

    /** Pulls a bracketed or single-colon port out of a host field without touching the port column. */
    fun extract(raw: String): Pair<String, Int?> {
        var value = raw.trim()
        if (value.endsWith('.')) value = value.dropLast(1)
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            if (end > 1) {
                val host = value.substring(1, end).substringBefore('%').trim()
                return host to portSuffix(value.substring(end + 1))
            }
        }
        val looksLikeIpv6 = value.count { it == ':' } >= 2 && !value.contains('.')
        if (looksLikeIpv6) {
            return value.substringBefore('%').trim() to null
        }
        val colon = value.lastIndexOf(':')
        if (colon > 0) {
            val port = portSuffix(value.substring(colon))
            if (port != null) return value.substring(0, colon).trim() to port
        }
        return value to null
    }

    /** Host token for a URL: bare hostname/IPv4, or `[ipv6]`. */
    fun literal(host: String): String {
        val clean = extract(host).first
        return if (clean.contains(':')) "[$clean]" else clean
    }

    /**
     * True when an HTTP auth challenge is for this proxy. A missing host is not
     * treated as a match — that used to send the proxy password to ordinary
     * site login prompts.
     */
    fun matchesChallenge(proxyHost: String, challengeHost: String?): Boolean {
        if (challengeHost.isNullOrBlank()) return false
        val expected = extract(proxyHost).first.lowercase()
        if (expected.isEmpty()) return false
        val raw = challengeHost.trim()
        val candidates = mutableListOf(raw)
        if (raw.startsWith("[") && raw.contains("]")) {
            candidates += raw.removePrefix("[").substringBefore("]")
        } else if (raw.count { it == ':' } == 1) {
            candidates += raw.substringBefore(":")
        }
        return candidates.any { extract(it).first.equals(expected, ignoreCase = true) }
    }

    /** Percent-encode a username or password for a proxy URL. `+` stays `+` (it is not a space). */
    fun encodeUserInfo(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val b = byte.toInt() and 0xFF
            if (b.isUnreservedUserInfo()) append(b.toChar())
            else append('%').append(b.toString(16).uppercase().padStart(2, '0'))
        }
    }

    private fun portSuffix(rest: String): Int? {
        if (!rest.startsWith(":")) return null
        val digits = rest.substring(1)
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return null
        return digits.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    private fun Int.isUnreservedUserInfo(): Boolean {
        val c = this
        return c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
    }
}
